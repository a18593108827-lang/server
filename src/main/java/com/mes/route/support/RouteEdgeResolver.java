package com.mes.route.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mes.common.AssertUtil;
import com.mes.route.entity.MesRouteEdge;
import com.mes.route.entity.MesRouteStep;
import com.mes.route.entity.MesStep;
import com.mes.route.mapper.MesRouteEdgeMapper;
import com.mes.route.mapper.MesRouteStepMapper;
import com.mes.route.mapper.MesStepMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 运行时选边：TrackOut / context / rework 共用，避免 Impl 里抄三遍。
 */
@Component
@RequiredArgsConstructor
public class RouteEdgeResolver {

    private final MesRouteEdgeMapper mesRouteEdgeMapper;
    private final MesRouteStepMapper mesRouteStepMapper;
    private final MesStepMapper mesStepMapper;

    public List<MesRouteEdge> listNormalAndBranch(Long versionId, Integer fromSortNo) {
        return mesRouteEdgeMapper.selectList(new LambdaQueryWrapper<MesRouteEdge>()
                .eq(MesRouteEdge::getVersionId, versionId)
                .eq(MesRouteEdge::getFromSortNo, fromSortNo)
                .in(MesRouteEdge::getEdgeType, RouteEdgeTypes.NORMAL, RouteEdgeTypes.BRANCH)
                .orderByAsc(MesRouteEdge::getSortNo));
    }

    public List<MesRouteEdge> listRework(Long versionId, Integer fromSortNo) {
        return mesRouteEdgeMapper.selectList(new LambdaQueryWrapper<MesRouteEdge>()
                .eq(MesRouteEdge::getVersionId, versionId)
                .eq(MesRouteEdge::getFromSortNo, fromSortNo)
                .eq(MesRouteEdge::getEdgeType, RouteEdgeTypes.REWORK)
                .orderByAsc(MesRouteEdge::getSortNo));
    }

    public MesRouteEdge findRework(Long versionId, Integer fromSortNo, Integer toSortNo) {
        return mesRouteEdgeMapper.selectOne(new LambdaQueryWrapper<MesRouteEdge>()
                .eq(MesRouteEdge::getVersionId, versionId)
                .eq(MesRouteEdge::getFromSortNo, fromSortNo)
                .eq(MesRouteEdge::getToSortNo, toSortNo)
                .eq(MesRouteEdge::getEdgeType, RouteEdgeTypes.REWORK)
                .last("LIMIT 1"));
    }

    public MesRouteStep findStep(Long versionId, Integer sortNo) {
        if (versionId == null || sortNo == null) {
            return null;
        }
        return mesRouteStepMapper.selectOne(new LambdaQueryWrapper<MesRouteStep>()
                .eq(MesRouteStep::getVersionId, versionId)
                .eq(MesRouteStep::getSortNo, sortNo)
                .last("LIMIT 1"));
    }

    public Map<Integer, MesRouteStep> mapRouteStepsBySort(Long versionId, Collection<Integer> sortNos) {
        if (sortNos == null || sortNos.isEmpty()) {
            return Collections.emptyMap();
        }
        return mesRouteStepMapper.selectList(new LambdaQueryWrapper<MesRouteStep>()
                        .eq(MesRouteStep::getVersionId, versionId)
                        .in(MesRouteStep::getSortNo, sortNos))
                .stream()
                .collect(Collectors.toMap(MesRouteStep::getSortNo, s -> s, (a, b) -> a));
    }

    public Map<Long, MesStep> mapStepsById(Collection<Long> stepIds) {
        if (stepIds == null || stepIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return mesStepMapper.selectBatchIds(stepIds).stream()
                .collect(Collectors.toMap(MesStep::getId, s -> s, (a, b) -> a));
    }

    /** context 默认下一站：normal 优先，否则步骤 next */
    public MesRouteStep resolveDefaultNext(Long versionId, MesRouteStep current) {
        List<MesRouteEdge> outEdges = listNormalAndBranch(versionId, current.getSortNo());
        MesRouteEdge normal = findNormal(outEdges);
        Integer nextSort = normal != null ? normal.getToSortNo() : current.getNextSortNo();
        return findStep(versionId, nextSort);
    }

    /**
     * TrackOut 选边：
     * 有 resultCode → 只匹配 branch；无 → normal / next；永不走 rework。
     */
    public RouteTrackOutDecision resolveTrackOut(Long versionId, MesRouteStep current, String resultCode) {
        String code = StringUtils.hasText(resultCode) ? resultCode.trim().toUpperCase() : null;
        List<MesRouteEdge> outEdges = listNormalAndBranch(versionId, current.getSortNo());

        if (StringUtils.hasText(code)) {
            MesRouteEdge matched = outEdges.stream()
                    .filter(e -> RouteEdgeTypes.BRANCH.equals(e.getEdgeType())
                            && code.equalsIgnoreCase(e.getConditionCode()))
                    .findFirst()
                    .orElse(null);
            AssertUtil.notNull(matched, "未配置分支条件: " + code);
            MesRouteStep next = findStep(versionId, matched.getToSortNo());
            AssertUtil.notNull(next, "分支目标站不存在");
            return new RouteTrackOutDecision(false, next.getSortNo(), next.getStepId(),
                    "完工并分支进入下一站", matched, code);
        }

        MesRouteEdge normal = findNormal(outEdges);
        Integer nextSort = normal != null ? normal.getToSortNo() : current.getNextSortNo();
        if (nextSort == null) {
            return new RouteTrackOutDecision(true, current.getSortNo(), current.getStepId(),
                    "末站完工", normal, null);
        }
        MesRouteStep next = findStep(versionId, nextSort);
        AssertUtil.notNull(next, "下一站不存在，路线数据异常");
        return new RouteTrackOutDecision(false, next.getSortNo(), next.getStepId(),
                "完工并进入下一站", normal, null);
    }

    private static MesRouteEdge findNormal(List<MesRouteEdge> outEdges) {
        return outEdges.stream()
                .filter(e -> RouteEdgeTypes.NORMAL.equals(e.getEdgeType()))
                .findFirst()
                .orElse(null);
    }
}
