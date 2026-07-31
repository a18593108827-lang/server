package com.mes.route.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mes.common.AssertUtil;
import com.mes.common.PageResult;
import com.mes.route.dto.MesRouteCreateDTO;
import com.mes.route.dto.MesRouteQuery;
import com.mes.route.dto.MesRouteStepsSaveDTO;
import com.mes.route.dto.MesRouteUpgradeDTO;
import com.mes.route.entity.MesRoute;
import com.mes.route.entity.MesRouteEdge;
import com.mes.route.entity.MesRouteStep;
import com.mes.route.entity.MesRouteVersion;
import com.mes.route.entity.MesStep;
import com.mes.route.mapper.MesRouteEdgeMapper;
import com.mes.route.mapper.MesRouteMapper;
import com.mes.route.mapper.MesRouteStepMapper;
import com.mes.route.mapper.MesRouteVersionMapper;
import com.mes.route.mapper.MesStepMapper;
import com.mes.route.service.MesRouteService;
import com.mes.route.vo.MesRouteEdgeVO;
import com.mes.route.vo.MesRouteStepVO;
import com.mes.route.vo.MesRouteVO;
import com.mes.route.vo.MesRouteVersionDetailVO;
import com.mes.route.vo.MesRouteVersionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 工艺路线服务实现：版本生命周期与草稿步骤维护
 */
@Service
@RequiredArgsConstructor
public class MesRouteServiceImpl implements MesRouteService {

    /** 草稿 */
    public static final String STATUS_DRAFT = "draft";
    /** 生效（同路线至多一个） */
    public static final String STATUS_ACTIVE = "active";
    /** 归档 */
    public static final String STATUS_ARCHIVED = "archived";

    /** 回流边：Track 走 /track/rework，带次数上限 */
    public static final String EDGE_REWORK = "rework";
    /** 默认出边：与步骤 next_sort_no 对齐，TrackOut 无 resultCode 时走这条 */
    public static final String EDGE_NORMAL = "normal";
    /** 条件分支边：TrackOut 带 resultCode 时按 condition_code 匹配 */
    public static final String EDGE_BRANCH = "branch";

    private final MesRouteMapper mesRouteMapper;
    private final MesRouteVersionMapper mesRouteVersionMapper;
    private final MesRouteStepMapper mesRouteStepMapper;
    private final MesRouteEdgeMapper mesRouteEdgeMapper;
    private final MesStepMapper mesStepMapper;

    @Override
    public PageResult<MesRouteVO> page(MesRouteQuery query) {
        long pageNo = query.getPage() <= 0 ? 1 : query.getPage();
        long pageSize = query.getSize() <= 0 ? 20 : query.getSize();

        Page<MesRoute> page = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<MesRoute> qw = new LambdaQueryWrapper<>();
        // 模糊查询
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            qw.and(w -> w.like(MesRoute::getRouteCode, keyword)
                    .or().like(MesRoute::getRouteName, keyword)
                    .or().like(MesRoute::getProductCode, keyword));
        }
        if (query.getStatus() != null) {
            qw.eq(MesRoute::getStatus, query.getStatus());
        }
        qw.orderByAsc(MesRoute::getRouteCode);

        Page<MesRoute> result = mesRouteMapper.selectPage(page, qw);
        List<MesRoute> routes = result.getRecords();
        if (routes.isEmpty()) {
            return PageResult.of(Collections.emptyList(), result.getTotal(), pageNo, pageSize);
        }

        List<Long> routeIds = routes.stream().map(MesRoute::getId).toList();
        Map<Long, MesRouteVersion> activeMap = mesRouteVersionMapper.selectList(new LambdaQueryWrapper<MesRouteVersion>()
                        .in(MesRouteVersion::getRouteId, routeIds)
                        .eq(MesRouteVersion::getStatus, STATUS_ACTIVE))
                .stream()
                .collect(Collectors.toMap(MesRouteVersion::getRouteId, v -> v, (a, b) -> a));

        List<MesRouteVO> records = new ArrayList<>(routes.size());
        for (MesRoute route : routes) {
            MesRouteVO vo = toRouteVo(route);
            MesRouteVersion active = activeMap.get(route.getId());
            if (active != null) {
                vo.setActiveVersionId(active.getId());
                vo.setActiveVersionNo(active.getVersionNo());
            }
            records.add(vo);
        }
        return PageResult.of(records, result.getTotal(), pageNo, pageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(MesRouteCreateDTO dto) {
        String routeCode = dto.getRouteCode().trim();
        Long count = mesRouteMapper.selectCount(new LambdaQueryWrapper<MesRoute>()
                .eq(MesRoute::getRouteCode, routeCode));
        AssertUtil.isTrue(count == 0, "路线编码已存在");

        MesRoute route = new MesRoute();
        route.setRouteCode(routeCode);
        route.setRouteName(dto.getRouteName().trim());
        route.setProductCode(blankToNull(dto.getProductCode()));
        route.setRemark(blankToNull(dto.getRemark()));
        route.setStatus(1);
        mesRouteMapper.insert(route);

        MesRouteVersion version = new MesRouteVersion();
        version.setRouteId(route.getId());
        version.setVersionNo(1);
        version.setStatus(STATUS_DRAFT);
        mesRouteVersionMapper.insert(version);
        return route.getId();
    }

    @Override
    public MesRouteVO get(Long id) {
        MesRoute route = mesRouteMapper.selectById(id);
        AssertUtil.notNull(route, "路线不存在");
        MesRouteVO vo = toRouteVo(route);
        MesRouteVersion active = mesRouteVersionMapper.selectOne(new LambdaQueryWrapper<MesRouteVersion>()
                .eq(MesRouteVersion::getRouteId, id)
                .eq(MesRouteVersion::getStatus, STATUS_ACTIVE)
                .last("LIMIT 1"));
        if (active != null) {
            vo.setActiveVersionId(active.getId());
            vo.setActiveVersionNo(active.getVersionNo());
        }
        return vo;
    }

    @Override
    public List<MesRouteVersionVO> listVersions(Long routeId) {
        MesRoute route = mesRouteMapper.selectById(routeId);
        AssertUtil.notNull(route, "路线不存在");
        List<MesRouteVersion> versions = mesRouteVersionMapper.selectList(new LambdaQueryWrapper<MesRouteVersion>()
                .eq(MesRouteVersion::getRouteId, routeId)
                .orderByDesc(MesRouteVersion::getVersionNo));
        List<MesRouteVersionVO> list = new ArrayList<>(versions.size());
        for (MesRouteVersion v : versions) {
            list.add(toVersionVo(v));
        }
        return list;
    }

    @Override
    public MesRouteVersionDetailVO getVersion(Long versionId) {
        MesRouteVersion version = mesRouteVersionMapper.selectById(versionId);
        AssertUtil.notNull(version, "版本不存在");
        MesRoute route = mesRouteMapper.selectById(version.getRouteId());
        AssertUtil.notNull(route, "路线不存在");

        List<MesRouteStep> steps = mesRouteStepMapper.selectList(new LambdaQueryWrapper<MesRouteStep>()
                .eq(MesRouteStep::getVersionId, versionId)
                .orderByAsc(MesRouteStep::getSortNo));

        Map<Long, MesStep> stepMap = Collections.emptyMap();//定义空Map
        if (!steps.isEmpty()) {
            List<Long> stepIds = steps.stream().map(MesRouteStep::getStepId).distinct().toList();
            stepMap = mesStepMapper.selectBatchIds(stepIds).stream()
                    .collect(Collectors.toMap(MesStep::getId, s -> s, (a, b) -> a));
        }

        MesRouteVersionDetailVO vo = new MesRouteVersionDetailVO();
        vo.setId(version.getId());
        vo.setRouteId(route.getId());
        vo.setRouteCode(route.getRouteCode());
        vo.setRouteName(route.getRouteName());
        vo.setVersionNo(version.getVersionNo());
        vo.setStatus(version.getStatus());
        vo.setPublishedAt(version.getPublishedAt());
        vo.setPublishedBy(version.getPublishedBy());
        vo.setRemark(version.getRemark());

        List<MesRouteStepVO> stepVos = new ArrayList<>(steps.size());
        for (MesRouteStep rs : steps) {
            MesRouteStepVO svo = new MesRouteStepVO();
            svo.setId(rs.getId());
            svo.setStepId(rs.getStepId());
            svo.setSortNo(rs.getSortNo());
            svo.setNextSortNo(rs.getNextSortNo());
            MesStep step = stepMap.get(rs.getStepId());
            if (step != null) {
                svo.setStepCode(step.getStepCode());
                svo.setStepName(step.getStepName());
                svo.setStepType(step.getStepType());
            }
            stepVos.add(svo);
        }
        vo.setSteps(stepVos);
        vo.setEdges(listEdgeVos(versionId));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveDraftSteps(Long versionId, MesRouteStepsSaveDTO dto) {
        MesRouteVersion version = mesRouteVersionMapper.selectById(versionId);
        AssertUtil.notNull(version, "版本不存在");
        AssertUtil.isTrue(STATUS_DRAFT.equals(version.getStatus()), "仅草稿版本可编辑步骤");

        List<MesRouteStepsSaveDTO.Item> items = dto.getSteps() == null ? List.of() : dto.getSteps();
        validateStepItems(items, true);

        // 物理删除：逻辑删除会占住 uk_ver_sort(version_id, sort_no)
        mesRouteStepMapper.physicalDeleteByVersionId(versionId);

        for (MesRouteStepsSaveDTO.Item item : items) {
            MesRouteStep row = new MesRouteStep();
            row.setVersionId(versionId);
            row.setStepId(item.getStepId());
            row.setSortNo(item.getSortNo());
            row.setNextSortNo(item.getNextSortNo());
            mesRouteStepMapper.insert(row);
        }

        // ---------- 边表维护 ----------
        // normal 一律由步骤 next 生成，不信前端传的 normal，避免与线性顺序打架
        // edges != null：整表覆盖（先清空再写 branch/rework + 重建 normal）
        // edges == null：只重建 normal，保留已有 branch/rework（兼容旧客户端）
        if (dto.getEdges() != null) {
            Set<Integer> sortNos = items.stream().map(MesRouteStepsSaveDTO.Item::getSortNo)
                    .collect(Collectors.toSet());
            // 前端可能误传 normal，这里滤掉，后面统一 insertNormalEdgesFromSteps
            List<MesRouteStepsSaveDTO.EdgeItem> nonNormal = dto.getEdges().stream()
                    .filter(e -> e.getEdgeType() != null
                            && !EDGE_NORMAL.equalsIgnoreCase(e.getEdgeType().trim()))
                    .toList();
            validateEdgeItems(nonNormal, sortNos);
            mesRouteEdgeMapper.physicalDeleteByVersionId(versionId);
            insertNormalEdgesFromSteps(versionId, items);
            int i = 0;
            for (MesRouteStepsSaveDTO.EdgeItem edge : nonNormal) {
                MesRouteEdge row = toEdgeEntity(versionId, edge, i++);
                mesRouteEdgeMapper.insert(row);
            }
        } else {
            mesRouteEdgeMapper.physicalDeleteNormalByVersionId(versionId);
            insertNormalEdgesFromSteps(versionId, items);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long versionId) {
        MesRouteVersion version = mesRouteVersionMapper.selectById(versionId);
        AssertUtil.notNull(version, "版本不存在");
        AssertUtil.isTrue(STATUS_DRAFT.equals(version.getStatus()), "仅草稿可发布");

        List<MesRouteStep> steps = mesRouteStepMapper.selectList(new LambdaQueryWrapper<MesRouteStep>()
                .eq(MesRouteStep::getVersionId, versionId)
                .orderByAsc(MesRouteStep::getSortNo));
        AssertUtil.notEmpty(steps, "至少配置一个步骤才能发布");
        validatePersistedSteps(steps);

        // 发布瞬间再刷一遍 normal：旧草稿可能只有 rework、缺默认边，否则校验过不了
        mesRouteEdgeMapper.physicalDeleteNormalByVersionId(versionId);
        int ni = 0;
        for (MesRouteStep step : steps) {
            if (step.getNextSortNo() == null) {
                continue; // 终点站没有默认出边
            }
            MesRouteEdge row = new MesRouteEdge();
            row.setVersionId(versionId);
            row.setFromSortNo(step.getSortNo());
            row.setToSortNo(step.getNextSortNo());
            row.setEdgeType(EDGE_NORMAL);
            row.setSortNo(ni++);
            mesRouteEdgeMapper.insert(row);
        }

        Set<Integer> sortNos = steps.stream().map(MesRouteStep::getSortNo).collect(Collectors.toSet());
        List<MesRouteEdge> edges = mesRouteEdgeMapper.selectList(new LambdaQueryWrapper<MesRouteEdge>()
                .eq(MesRouteEdge::getVersionId, versionId));
        validatePersistedEdges(edges, sortNos);
        validateNormalAndBranchRules(steps, edges); // 每站 1 条 normal、branch 条件不重复
        validateReworkCanReachMainEnd(steps, edges); // 回流目标沿 next 必须能走到终点

        // 旧版进行归档处理（修改状态）
        mesRouteVersionMapper.update(null, new LambdaUpdateWrapper<MesRouteVersion>()
                .eq(MesRouteVersion::getRouteId, version.getRouteId())
                .eq(MesRouteVersion::getStatus, STATUS_ACTIVE)
                .set(MesRouteVersion::getStatus, STATUS_ARCHIVED));

        version.setStatus(STATUS_ACTIVE);
        version.setPublishedAt(LocalDateTime.now());
        version.setPublishedBy(StpUtil.getLoginIdAsLong());
        mesRouteVersionMapper.updateById(version);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long upgrade(Long routeId, MesRouteUpgradeDTO dto) {
        MesRoute route = mesRouteMapper.selectById(routeId);
        AssertUtil.notNull(route, "路线不存在");

        MesRouteVersion from = mesRouteVersionMapper.selectById(dto.getFromVersionId());
        AssertUtil.notNull(from, "源版本不存在");
        AssertUtil.isTrue(Objects.equals(from.getRouteId(), routeId), "源版本不属于该路线");

        Long draftCount = mesRouteVersionMapper.selectCount(new LambdaQueryWrapper<MesRouteVersion>()
                .eq(MesRouteVersion::getRouteId, routeId)
                .eq(MesRouteVersion::getStatus, STATUS_DRAFT));
        AssertUtil.isTrue(draftCount == 0, "已有草稿版本，请先发布或继续编辑现有草稿");

        // 获取最大版本号
        Integer maxNo = mesRouteVersionMapper.selectList(new LambdaQueryWrapper<MesRouteVersion>()
                        .eq(MesRouteVersion::getRouteId, routeId)
                        .orderByDesc(MesRouteVersion::getVersionNo)
                        .last("LIMIT 1"))
                .stream()
                .map(MesRouteVersion::getVersionNo)
                .findFirst()
                .orElse(0);

        MesRouteVersion draft = new MesRouteVersion();
        draft.setRouteId(routeId);
        draft.setVersionNo(maxNo + 1);
        draft.setStatus(STATUS_DRAFT);
        draft.setRemark("基于 v" + from.getVersionNo() + " 升版");
        mesRouteVersionMapper.insert(draft);

        List<MesRouteStep> fromSteps = mesRouteStepMapper.selectList(new LambdaQueryWrapper<MesRouteStep>()
                .eq(MesRouteStep::getVersionId, from.getId())
                .orderByAsc(MesRouteStep::getSortNo));
        for (MesRouteStep src : fromSteps) {
            MesRouteStep row = new MesRouteStep();
            row.setVersionId(draft.getId());
            row.setStepId(src.getStepId());
            row.setSortNo(src.getSortNo());
            row.setNextSortNo(src.getNextSortNo());
            mesRouteStepMapper.insert(row);
        }

        // 边：只拷 branch/rework；normal 丢弃后按步骤 next 重建（与保存/发布同一策略）
        List<MesRouteEdge> fromEdges = mesRouteEdgeMapper.selectList(new LambdaQueryWrapper<MesRouteEdge>()
                .eq(MesRouteEdge::getVersionId, from.getId())
                .orderByAsc(MesRouteEdge::getSortNo));
        for (MesRouteEdge src : fromEdges) {
            if (EDGE_NORMAL.equals(src.getEdgeType())) {
                continue;
            }
            MesRouteEdge row = new MesRouteEdge();
            row.setVersionId(draft.getId());
            row.setFromSortNo(src.getFromSortNo());
            row.setToSortNo(src.getToSortNo());
            row.setEdgeType(src.getEdgeType());
            row.setMaxReworkCount(src.getMaxReworkCount());
            row.setReasonCodes(src.getReasonCodes());
            row.setConditionCode(src.getConditionCode());
            row.setSortNo(src.getSortNo());
            mesRouteEdgeMapper.insert(row);
        }
        // 按源步骤的 next 生成新草稿的默认出边
        int ni = 0;
        for (MesRouteStep src : fromSteps) {
            if (src.getNextSortNo() == null) {
                continue;
            }
            MesRouteEdge row = new MesRouteEdge();
            row.setVersionId(draft.getId());
            row.setFromSortNo(src.getSortNo());
            row.setToSortNo(src.getNextSortNo());
            row.setEdgeType(EDGE_NORMAL);
            row.setSortNo(ni++);
            mesRouteEdgeMapper.insert(row);
        }
        return draft.getId();
    }

    /**
     * 获取当前版本的所有边
     */
    private List<MesRouteEdgeVO> listEdgeVos(Long versionId) {
        List<MesRouteEdge> edges = mesRouteEdgeMapper.selectList(new LambdaQueryWrapper<MesRouteEdge>()
                .eq(MesRouteEdge::getVersionId, versionId)
                .orderByAsc(MesRouteEdge::getFromSortNo)
                .orderByAsc(MesRouteEdge::getSortNo));
        List<MesRouteEdgeVO> list = new ArrayList<>(edges.size());
        for (MesRouteEdge e : edges) {
            MesRouteEdgeVO vo = new MesRouteEdgeVO();
            vo.setId(e.getId());
            vo.setFromSortNo(e.getFromSortNo());
            vo.setToSortNo(e.getToSortNo());
            vo.setEdgeType(e.getEdgeType());
            vo.setMaxReworkCount(e.getMaxReworkCount());
            vo.setReasonCodes(e.getReasonCodes());
            vo.setConditionCode(e.getConditionCode());
            vo.setSortNo(e.getSortNo());
            list.add(vo);
        }
        return list;
    }

    /** 把步骤链表 next_sort_no 落成 normal 边，供 TrackOut 默认选边 */
    private void insertNormalEdgesFromSteps(Long versionId, List<MesRouteStepsSaveDTO.Item> items) {
        int i = 0;
        for (MesRouteStepsSaveDTO.Item item : items) {
            if (item.getNextSortNo() == null) {
                continue;
            }
            MesRouteEdge row = new MesRouteEdge();
            row.setVersionId(versionId);
            row.setFromSortNo(item.getSortNo());
            row.setToSortNo(item.getNextSortNo());
            row.setEdgeType(EDGE_NORMAL);
            row.setSortNo(i++);
            mesRouteEdgeMapper.insert(row);
        }
    }

    /** DTO → 实体；branch 的 conditionCode 统一转大写，方便 TrackOut 匹配 */
    private MesRouteEdge toEdgeEntity(Long versionId, MesRouteStepsSaveDTO.EdgeItem edge, int defaultSort) {
        String type = edge.getEdgeType().trim().toLowerCase();
        MesRouteEdge row = new MesRouteEdge();
        row.setVersionId(versionId);
        row.setFromSortNo(edge.getFromSortNo());
        row.setToSortNo(edge.getToSortNo());
        row.setEdgeType(type);
        row.setMaxReworkCount(edge.getMaxReworkCount());
        row.setReasonCodes(blankToNull(edge.getReasonCodes()));
        if (EDGE_BRANCH.equals(type) && StringUtils.hasText(edge.getConditionCode())) {
            row.setConditionCode(edge.getConditionCode().trim().toUpperCase());
        } else {
            row.setConditionCode(null);
        }
        row.setSortNo(edge.getSortNo() != null ? edge.getSortNo() : defaultSort);
        return row;
    }

    /**
     * 保存入参校验（不含 normal，normal 服务端自建）。
     * rework 要次数上限；branch 要条件码且同站不重复。
     */
    private void validateEdgeItems(List<MesRouteStepsSaveDTO.EdgeItem> edges, Set<Integer> sortNos) {
        Set<String> uniq = new HashSet<>();
        Set<String> branchConds = new HashSet<>();
        for (MesRouteStepsSaveDTO.EdgeItem edge : edges) {
            AssertUtil.notNull(edge.getFromSortNo(), "边起点不能为空");
            AssertUtil.notNull(edge.getToSortNo(), "边终点不能为空");
            AssertUtil.notBlank(edge.getEdgeType(), "边类型不能为空");
            String type = edge.getEdgeType().trim().toLowerCase();
            AssertUtil.isTrue(EDGE_BRANCH.equals(type) || EDGE_REWORK.equals(type) || EDGE_NORMAL.equals(type),
                    "不支持的边类型: " + type);
            AssertUtil.isTrue(sortNos.contains(edge.getFromSortNo()),
                    "边起点顺序号不存在: " + edge.getFromSortNo());
            AssertUtil.isTrue(sortNos.contains(edge.getToSortNo()),
                    "边终点顺序号不存在: " + edge.getToSortNo());
            AssertUtil.isFalse(Objects.equals(edge.getFromSortNo(), edge.getToSortNo()),
                    "边不能指向自身");
            String key = edge.getFromSortNo() + "-" + edge.getToSortNo() + "-" + type;
            AssertUtil.isTrue(uniq.add(key), "边重复: " + key);
            if (EDGE_REWORK.equals(type)) {
                AssertUtil.notNull(edge.getMaxReworkCount(), "回流边须配置次数上限");
                AssertUtil.isTrue(edge.getMaxReworkCount() >= 1, "回流次数上限须≥1");
                AssertUtil.isTrue(!StringUtils.hasText(edge.getConditionCode()), "回流边不能配置条件码");
            }
            if (EDGE_BRANCH.equals(type)) {
                AssertUtil.notBlank(edge.getConditionCode(), "分支边须配置条件码");
                String cond = edge.getConditionCode().trim().toUpperCase();
                String ck = edge.getFromSortNo() + "-" + cond;
                AssertUtil.isTrue(branchConds.add(ck), "同站分支条件码重复: " + cond);
            }
            if (EDGE_NORMAL.equals(type)) {
                AssertUtil.isTrue(!StringUtils.hasText(edge.getConditionCode()), "默认边不能配置条件码");
            }
        }
    }

    /** 校验已保存的边合法性 */
    private void validatePersistedEdges(List<MesRouteEdge> edges, Set<Integer> sortNos) {
        for (MesRouteEdge edge : edges) {
            AssertUtil.isTrue(sortNos.contains(edge.getFromSortNo()),
                    "边起点顺序号不存在: " + edge.getFromSortNo());
            AssertUtil.isTrue(sortNos.contains(edge.getToSortNo()),
                    "边终点顺序号不存在: " + edge.getToSortNo());
            if (EDGE_REWORK.equals(edge.getEdgeType())) {
                AssertUtil.notNull(edge.getMaxReworkCount(), "回流边须配置次数上限");
                AssertUtil.isTrue(edge.getMaxReworkCount() >= 1, "回流次数上限须≥1");
                AssertUtil.isFalse(Objects.equals(edge.getFromSortNo(), edge.getToSortNo()),
                        "回流边不能指向自身");
            }
            if (EDGE_BRANCH.equals(edge.getEdgeType())) {
                AssertUtil.notBlank(edge.getConditionCode(), "分支边须配置条件码");
                AssertUtil.isFalse(Objects.equals(edge.getFromSortNo(), edge.getToSortNo()),
                        "分支边不能指向自身");
            }
        }
    }

    /**
     * 发布结构校验：
     * - 有 next 的站：必须恰好 1 条 normal，且 to == next_sort_no
     * - 终点（next=null）：不能有 normal
     * - 同站 branch 的 condition_code 不重复
     */
    private void validateNormalAndBranchRules(List<MesRouteStep> steps, List<MesRouteEdge> edges) {
        Map<Integer, List<MesRouteEdge>> normalsByFrom = edges.stream()
                .filter(e -> EDGE_NORMAL.equals(e.getEdgeType()))
                .collect(Collectors.groupingBy(MesRouteEdge::getFromSortNo));
        Map<Integer, List<MesRouteEdge>> branchesByFrom = edges.stream()
                .filter(e -> EDGE_BRANCH.equals(e.getEdgeType()))
                .collect(Collectors.groupingBy(MesRouteEdge::getFromSortNo));

        for (MesRouteStep step : steps) {
            List<MesRouteEdge> normals = normalsByFrom.getOrDefault(step.getSortNo(), List.of());
            if (step.getNextSortNo() == null) {
                AssertUtil.isTrue(normals.isEmpty(),
                        "终点站不能配置默认出边: " + step.getSortNo());
            } else {
                AssertUtil.isTrue(normals.size() == 1,
                        "非终点站须恰好一条默认出边: " + step.getSortNo());
                AssertUtil.isTrue(Objects.equals(normals.get(0).getToSortNo(), step.getNextSortNo()),
                        "默认出边与步骤下一站不一致: " + step.getSortNo());
            }
            List<MesRouteEdge> branches = branchesByFrom.getOrDefault(step.getSortNo(), List.of());
            Set<String> conds = new HashSet<>();
            for (MesRouteEdge b : branches) {
                String cond = b.getConditionCode().trim().toUpperCase();
                AssertUtil.isTrue(conds.add(cond),
                        "同站分支条件码重复: " + step.getSortNo() + "/" + cond);
            }
        }
    }

    /**
     * 回流可达性：从 rework 的目标站出发，只沿步骤 next 走，必须碰到终点（next=null）。
     * 中途断链或 next 成环 → 发布失败，避免 Lot 回流后卡死。
     */
    private void validateReworkCanReachMainEnd(List<MesRouteStep> steps, List<MesRouteEdge> edges) {
        Map<Integer, Integer> nextMap = new HashMap<>();
        for (MesRouteStep step : steps) {
            nextMap.put(step.getSortNo(), step.getNextSortNo());
        }
        for (MesRouteEdge edge : edges) {
            if (!EDGE_REWORK.equals(edge.getEdgeType())) {
                continue;
            }
            Integer cur = edge.getToSortNo();
            Set<Integer> visited = new HashSet<>();
            boolean reachedEnd = false;
            while (cur != null) {
                AssertUtil.isTrue(nextMap.containsKey(cur),
                        "回流后主路径断链，无法到达终点: " + edge.getFromSortNo() + "→" + edge.getToSortNo()
                                + "（断于 " + cur + "）");
                AssertUtil.isTrue(visited.add(cur),
                        "回流后无法到达终点（主路径成环）: " + edge.getFromSortNo() + "→" + edge.getToSortNo());
                Integer next = nextMap.get(cur);
                if (next == null) {
                    reachedEnd = true;
                    break;
                }
                cur = next;
            }
            AssertUtil.isTrue(reachedEnd,
                    "回流后无法到达主路径终点: " + edge.getFromSortNo() + "→" + edge.getToSortNo());
        }
    }

    /** 校验保存入参：顺序号唯一、下一站合法、工序存在且启用 */
    private void validateStepItems(List<MesRouteStepsSaveDTO.Item> items, boolean requireEnabledStep) {
        if (items.isEmpty()) {
            return;
        }
        Set<Integer> sortNos = new HashSet<>();
        for (MesRouteStepsSaveDTO.Item item : items) {
            AssertUtil.notNull(item.getStepId(), "工序不能为空");
            AssertUtil.notNull(item.getSortNo(), "顺序号不能为空");
            AssertUtil.isTrue(sortNos.add(item.getSortNo()), "顺序号重复: " + item.getSortNo());
        }
        for (MesRouteStepsSaveDTO.Item item : items) {
            if (item.getNextSortNo() != null) {
                AssertUtil.isTrue(sortNos.contains(item.getNextSortNo()),
                        "下一站顺序号不存在: " + item.getNextSortNo());
                AssertUtil.isFalse(Objects.equals(item.getSortNo(), item.getNextSortNo()),
                        "下一站不能指向自身");
            }
        }

        List<Long> stepIds = items.stream().map(MesRouteStepsSaveDTO.Item::getStepId).distinct().toList();
        Map<Long, MesStep> stepMap = mesStepMapper.selectBatchIds(stepIds).stream()
                .collect(Collectors.toMap(MesStep::getId, s -> s, (a, b) -> a));
        AssertUtil.isTrue(stepMap.size() == stepIds.size(), "存在无效工序");
        if (requireEnabledStep) {
            for (Long stepId : stepIds) {
                MesStep step = stepMap.get(stepId);
                AssertUtil.isTrue(step.getStatus() != null && step.getStatus() == 1,
                        "工序已禁用，不能挂到草稿: " + step.getStepCode());
            }
        }
    }

    /** 发布前校验已落库步骤的顺序与下一站 */
    private void validatePersistedSteps(List<MesRouteStep> steps) {
        Set<Integer> sortNos = new HashSet<>();
        for (MesRouteStep step : steps) {
            AssertUtil.isTrue(sortNos.add(step.getSortNo()), "顺序号重复: " + step.getSortNo());
        }
        for (MesRouteStep step : steps) {
            if (step.getNextSortNo() != null) {
                AssertUtil.isTrue(sortNos.contains(step.getNextSortNo()),
                        "下一站顺序号不存在: " + step.getNextSortNo());
            }
        }
    }

    private static MesRouteVO toRouteVo(MesRoute route) {
        MesRouteVO vo = new MesRouteVO();
        vo.setId(route.getId());
        vo.setRouteCode(route.getRouteCode());
        vo.setRouteName(route.getRouteName());
        vo.setProductCode(route.getProductCode());
        vo.setStatus(route.getStatus());
        vo.setRemark(route.getRemark());
        vo.setCreateTime(route.getCreateTime());
        vo.setUpdateTime(route.getUpdateTime());
        return vo;
    }

    private static MesRouteVersionVO toVersionVo(MesRouteVersion v) {
        MesRouteVersionVO vo = new MesRouteVersionVO();
        vo.setId(v.getId());
        vo.setRouteId(v.getRouteId());
        vo.setVersionNo(v.getVersionNo());
        vo.setStatus(v.getStatus());
        vo.setPublishedAt(v.getPublishedAt());
        vo.setPublishedBy(v.getPublishedBy());
        vo.setRemark(v.getRemark());
        vo.setCreateTime(v.getCreateTime());
        vo.setUpdateTime(v.getUpdateTime());
        return vo;
    }

    private static String blankToNull(String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }
}
