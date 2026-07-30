package com.mes.track.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mes.common.AssertUtil;
import com.mes.dispatch.service.DispatchService;
import com.mes.equipment.service.MesEqpService;
import com.mes.hold.service.HoldService;
import com.mes.lot.entity.MesLot;
import com.mes.lot.mapper.MesLotMapper;
import com.mes.lot.vo.MesLotStepVO;
import com.mes.recipe.facade.RecipeFacade;
import com.mes.recipe.vo.RecipeResolveVO;
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
import com.mes.system.entity.SysUser;
import com.mes.system.mapper.SysUserMapper;
import com.mes.track.entity.MesTxLog;
import com.mes.track.mapper.MesTxLogMapper;
import com.mes.track.service.TrackService;
import com.mes.track.vo.MesTxLogVO;
import com.mes.track.vo.TrackContextVO;
import com.mes.track.vo.TrackReleaseResultVO;
import com.mes.track.vo.TrackReworkOptionVO;
import com.mes.track.vo.TrackTxnResultVO;
import com.mes.wip.service.WipProjectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Track 执行引擎：Release / TrackIn / TrackOut（Out 内含自动进站，不单独暴露 Move）
 */
@Service
@RequiredArgsConstructor
public class TrackServiceImpl implements TrackService {

    public static final String STATUS_CREATED = "created";
    public static final String STATUS_WAIT = "wait";
    public static final String STATUS_PROCESSING = "processing";
    public static final String STATUS_COMPLETED = "completed";
    public static final String ROUTE_ACTIVE = "active";

    public static final String TX_RELEASE = "RELEASE";
    public static final String TX_TRACK_IN = "TRACK_IN";
    public static final String TX_TRACK_OUT = "TRACK_OUT";
    public static final String TX_REWORK = "REWORK";
    public static final String EDGE_REWORK = "rework";

    private final MesLotMapper mesLotMapper;
    private final MesRouteMapper mesRouteMapper;
    private final MesRouteVersionMapper mesRouteVersionMapper;
    private final MesRouteStepMapper mesRouteStepMapper;
    private final MesRouteEdgeMapper mesRouteEdgeMapper;
    private final MesStepMapper mesStepMapper;
    private final MesTxLogMapper mesTxLogMapper;
    private final SysUserMapper sysUserMapper;
    private final WipProjectionService wipProjectionService;
    private final HoldService holdService;
    private final MesEqpService mesEqpService;
    private final DispatchService dispatchService;
    private final RecipeFacade recipeFacade;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TrackReleaseResultVO release(Long lotId) {
        MesLot lot = mesLotMapper.selectById(lotId);
        AssertUtil.notNull(lot, "批次不存在");
        AssertUtil.isTrue(STATUS_CREATED.equals(lot.getStatus()), "仅未放行批次可放行");
        AssertUtil.notNull(lot.getRouteId(), "请先指定工艺路线");

        MesRoute route = mesRouteMapper.selectById(lot.getRouteId());
        AssertUtil.notNull(route, "路线不存在");
        AssertUtil.isTrue(route.getStatus() != null && route.getStatus() == 1, "路线已停用");

        MesRouteVersion active = mesRouteVersionMapper.selectOne(new LambdaQueryWrapper<MesRouteVersion>()
                .eq(MesRouteVersion::getRouteId, route.getId())
                .eq(MesRouteVersion::getStatus, ROUTE_ACTIVE)
                .last("LIMIT 1"));
        AssertUtil.notNull(active, "路线无生效版本，无法放行");

        MesRouteStep firstStep = mesRouteStepMapper.selectOne(new LambdaQueryWrapper<MesRouteStep>()
                .eq(MesRouteStep::getVersionId, active.getId())
                .orderByAsc(MesRouteStep::getSortNo)
                .last("LIMIT 1"));
        AssertUtil.notNull(firstStep, "生效版本无步骤，无法放行");

        String fromStatus = lot.getStatus();
        Integer fromSortNo = lot.getCurrentSortNo();

        lot.setRouteVersionId(active.getId());
        lot.setStatus(STATUS_WAIT);
        lot.setCurrentSortNo(firstStep.getSortNo());
        lot.setCurrentStepId(firstStep.getStepId());
        lot.setCurrentEqpId(null);
        lot.setReworkCounts(null);
        lot.setUpdateBy(StpUtil.getLoginIdAsLong());
        int rows = mesLotMapper.updateById(lot);
        AssertUtil.isTrue(rows > 0, "数据已被他人修改，请刷新后重试");
        wipProjectionService.syncFromLot(lot);

        writeTxLog(lot, TX_RELEASE, fromStatus, STATUS_WAIT, fromSortNo, firstStep.getSortNo(),
                firstStep.getStepId(), null, active.getId(), "放行进首站", null, null);

        TrackReleaseResultVO vo = new TrackReleaseResultVO();
        vo.setLotId(lot.getId());
        vo.setLotNo(lot.getLotNo());
        vo.setRouteVersionId(active.getId());
        vo.setRouteVersionNo(active.getVersionNo());
        vo.setStatus(STATUS_WAIT);
        vo.setCurrentSortNo(firstStep.getSortNo());
        vo.setCurrentStepId(firstStep.getStepId());
        return vo;
    }

    /**
     * 修改批次，扭转批次状态
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TrackTxnResultVO trackIn(Long lotId, Long eqpId) {
        MesLot lot = requireExecutableLot(lotId);
        holdService.assertNoActive(lotId);
        mesEqpService.assertUsable(eqpId);
        dispatchService.assertReserveMatch(lotId, eqpId);// 预约匹配
        AssertUtil.isTrue(STATUS_WAIT.equals(lot.getStatus()), "仅等待加工状态可开工");
        AssertUtil.notNull(lot.getCurrentSortNo(), "当前站未知，无法开工");
        AssertUtil.notNull(lot.getRouteVersionId(), "未绑定路线版本");

        MesRouteStep current = requireCurrentStep(lot);
        recipeFacade.assertQualified(current.getStepId(), eqpId);
        RecipeResolveVO recipe = recipeFacade.resolve(current.getStepId(), eqpId);
        String fromStatus = lot.getStatus();
        Integer fromSortNo = lot.getCurrentSortNo();

        lot.setStatus(STATUS_PROCESSING);
        lot.setCurrentEqpId(eqpId);
        lot.setUpdateBy(StpUtil.getLoginIdAsLong());
        int rows = mesLotMapper.updateById(lot);
        AssertUtil.isTrue(rows > 0, "数据已被他人修改，请刷新后重试");
        wipProjectionService.syncFromLot(lot);

        Long txId = writeTxLog(lot, TX_TRACK_IN, fromStatus, STATUS_PROCESSING, fromSortNo, fromSortNo,
                current.getStepId(), eqpId, lot.getRouteVersionId(), "开工",
                recipe != null ? recipe.getRecipeId() : null,
                recipe != null ? recipe.getVersionId() : null);
        dispatchService.consumeOnTrackIn(lotId, eqpId, txId);// 消费预约

        return toTxnVo(lot, TX_TRACK_IN, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TrackTxnResultVO trackOut(Long lotId) {
        MesLot lot = requireExecutableLot(lotId);
        holdService.assertNoActive(lotId);
        AssertUtil.isTrue(STATUS_PROCESSING.equals(lot.getStatus()), "仅加工中状态可完工");
        AssertUtil.notNull(lot.getCurrentSortNo(), "当前站未知，无法完工");
        AssertUtil.notNull(lot.getRouteVersionId(), "未绑定路线版本");

        MesRouteStep current = requireCurrentStep(lot);
        String fromStatus = lot.getStatus();
        Integer fromSortNo = lot.getCurrentSortNo();
        Long fromEqpId = lot.getCurrentEqpId();

        boolean completed;
        Integer toSortNo;
        Long toStepId;
        String toStatus;
        String remark;

        if (current.getNextSortNo() == null) {
            completed = true;
            toStatus = STATUS_COMPLETED;
            toSortNo = fromSortNo;
            toStepId = current.getStepId();
            lot.setStatus(STATUS_COMPLETED);
            lot.setCurrentEqpId(null);
            remark = "末站完工";
        } else {
            MesRouteStep next = mesRouteStepMapper.selectOne(new LambdaQueryWrapper<MesRouteStep>()
                    .eq(MesRouteStep::getVersionId, lot.getRouteVersionId())
                    .eq(MesRouteStep::getSortNo, current.getNextSortNo())
                    .last("LIMIT 1"));
            AssertUtil.notNull(next, "下一站不存在，路线数据异常");

            completed = false;
            toStatus = STATUS_WAIT;
            toSortNo = next.getSortNo();
            toStepId = next.getStepId();
            lot.setStatus(STATUS_WAIT);
            lot.setCurrentSortNo(next.getSortNo());
            lot.setCurrentStepId(next.getStepId());
            lot.setCurrentEqpId(null);
            remark = "完工并进入下一站";
        }

        lot.setUpdateBy(StpUtil.getLoginIdAsLong());
        int rows = mesLotMapper.updateById(lot);
        AssertUtil.isTrue(rows > 0, "数据已被他人修改，请刷新后重试");
        wipProjectionService.syncFromLot(lot);

        writeTxLog(lot, TX_TRACK_OUT, fromStatus, toStatus, fromSortNo, toSortNo,
                toStepId, fromEqpId, lot.getRouteVersionId(), remark, null, null);

        return toTxnVo(lot, TX_TRACK_OUT, completed);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TrackTxnResultVO rework(Long lotId, Integer toSortNo, String reasonCode, String remark) {
        MesLot lot = requireExecutableLot(lotId);
        holdService.assertNoActive(lotId);
        AssertUtil.isTrue(STATUS_WAIT.equals(lot.getStatus()) || STATUS_PROCESSING.equals(lot.getStatus()),
                "仅等待或加工中状态可返工");
        AssertUtil.notNull(lot.getCurrentSortNo(), "当前站未知，无法返工");
        AssertUtil.notNull(toSortNo, "回流目标站不能为空");

        Integer fromSortNo = lot.getCurrentSortNo();
        MesRouteEdge edge = mesRouteEdgeMapper.selectOne(new LambdaQueryWrapper<MesRouteEdge>()
                .eq(MesRouteEdge::getVersionId, lot.getRouteVersionId())
                .eq(MesRouteEdge::getFromSortNo, fromSortNo)
                .eq(MesRouteEdge::getToSortNo, toSortNo)
                .eq(MesRouteEdge::getEdgeType, EDGE_REWORK)
                .last("LIMIT 1"));
        AssertUtil.notNull(edge, "当前站未配置回流至目标站");
        AssertUtil.notNull(edge.getMaxReworkCount(), "回流次数上限未配置");
        AssertUtil.isTrue(edge.getMaxReworkCount() >= 1, "回流次数上限异常");

        String reason = reasonCode == null ? null : reasonCode.trim();
        if (StringUtils.hasText(edge.getReasonCodes())) {
            AssertUtil.notBlank(reason, "返工原因不能为空");
            Set<String> allowed = Arrays.stream(edge.getReasonCodes().split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toSet());
            AssertUtil.isTrue(allowed.contains(reason), "返工原因不匹配");
        }

        int used = getReworkCount(lot, fromSortNo);
        AssertUtil.isTrue(used + 1 <= edge.getMaxReworkCount(), "返工次数已达上限");

        MesRouteStep target = mesRouteStepMapper.selectOne(new LambdaQueryWrapper<MesRouteStep>()
                .eq(MesRouteStep::getVersionId, lot.getRouteVersionId())
                .eq(MesRouteStep::getSortNo, toSortNo)
                .last("LIMIT 1"));
        AssertUtil.notNull(target, "回流目标站不存在");

        String fromStatus = lot.getStatus();
        Long fromEqpId = lot.getCurrentEqpId();
        int newCount = used + 1;
        putReworkCount(lot, fromSortNo, newCount);

        lot.setStatus(STATUS_WAIT);
        lot.setCurrentSortNo(target.getSortNo());
        lot.setCurrentStepId(target.getStepId());
        lot.setCurrentEqpId(null);
        lot.setUpdateBy(StpUtil.getLoginIdAsLong());
        int rows = mesLotMapper.updateById(lot);
        AssertUtil.isTrue(rows > 0, "数据已被他人修改，请刷新后重试");
        wipProjectionService.syncFromLot(lot);

        String logRemark = StringUtils.hasText(remark) ? remark.trim() : "返工回流";
        if (StringUtils.hasText(reason)) {
            logRemark = logRemark + " | reason=" + reason;
        }
        logRemark = logRemark + " | count=" + newCount + "/" + edge.getMaxReworkCount()
                + " | edgeId=" + edge.getId();

        writeTxLog(lot, TX_REWORK, fromStatus, STATUS_WAIT, fromSortNo, target.getSortNo(),
                target.getStepId(), fromEqpId, lot.getRouteVersionId(), logRemark, null, null);

        TrackTxnResultVO vo = toTxnVo(lot, TX_REWORK, false);
        vo.setReworkCount(newCount);
        vo.setMaxReworkCount(edge.getMaxReworkCount());
        return vo;
    }

    @Override
    public TrackContextVO context(Long lotId) {
        MesLot lot = mesLotMapper.selectById(lotId);
        AssertUtil.notNull(lot, "批次不存在");

        TrackContextVO vo = new TrackContextVO();
        vo.setLotId(lot.getId());
        vo.setLotNo(lot.getLotNo());
        vo.setStatus(lot.getStatus());
        vo.setRouteId(lot.getRouteId());
        vo.setRouteVersionId(lot.getRouteVersionId());
        vo.setCurrentSortNo(lot.getCurrentSortNo());
        vo.setCurrentStepId(lot.getCurrentStepId());
        vo.setCurrentEqpId(lot.getCurrentEqpId());
        vo.setCompleted(STATUS_COMPLETED.equals(lot.getStatus()));
        vo.setCanTrackIn(STATUS_WAIT.equals(lot.getStatus()));
        vo.setCanTrackOut(STATUS_PROCESSING.equals(lot.getStatus()));
        vo.setReworkOptions(Collections.emptyList());
        vo.setCanRework(false);
        vo.setReworkCount(lot.getCurrentSortNo() != null ? getReworkCount(lot, lot.getCurrentSortNo()) : 0);

        if (lot.getRouteVersionId() != null) {
            MesRouteVersion version = mesRouteVersionMapper.selectById(lot.getRouteVersionId());
            if (version != null) {
                vo.setRouteVersionNo(version.getVersionNo());
            }
        }

        if (lot.getRouteVersionId() == null || lot.getCurrentSortNo() == null) {
            return vo;
        }

        MesRouteStep current = mesRouteStepMapper.selectOne(new LambdaQueryWrapper<MesRouteStep>()
                .eq(MesRouteStep::getVersionId, lot.getRouteVersionId())
                .eq(MesRouteStep::getSortNo, lot.getCurrentSortNo())
                .last("LIMIT 1"));
        if (current == null) {
            return vo;
        }
        vo.setCurrentStep(toStepVo(current));

        if (current.getNextSortNo() != null) {
            MesRouteStep next = mesRouteStepMapper.selectOne(new LambdaQueryWrapper<MesRouteStep>()
                    .eq(MesRouteStep::getVersionId, lot.getRouteVersionId())
                    .eq(MesRouteStep::getSortNo, current.getNextSortNo())
                    .last("LIMIT 1"));
            if (next != null) {
                vo.setNextStep(toStepVo(next));
            }
        }

        List<MesRouteEdge> reworkEdges = mesRouteEdgeMapper.selectList(new LambdaQueryWrapper<MesRouteEdge>()
                .eq(MesRouteEdge::getVersionId, lot.getRouteVersionId())
                .eq(MesRouteEdge::getFromSortNo, lot.getCurrentSortNo())
                .eq(MesRouteEdge::getEdgeType, EDGE_REWORK)
                .orderByAsc(MesRouteEdge::getSortNo));
        if (!reworkEdges.isEmpty()) {
            Set<Integer> toSorts = reworkEdges.stream().map(MesRouteEdge::getToSortNo).collect(Collectors.toSet());
            Map<Integer, MesRouteStep> stepBySort = mesRouteStepMapper.selectList(new LambdaQueryWrapper<MesRouteStep>()
                            .eq(MesRouteStep::getVersionId, lot.getRouteVersionId())
                            .in(MesRouteStep::getSortNo, toSorts))
                    .stream()
                    .collect(Collectors.toMap(MesRouteStep::getSortNo, s -> s, (a, b) -> a));
            Set<Long> stepIds = stepBySort.values().stream().map(MesRouteStep::getStepId).collect(Collectors.toSet());
            Map<Long, MesStep> stepMap = stepIds.isEmpty()
                    ? Collections.emptyMap()
                    : mesStepMapper.selectBatchIds(stepIds).stream()
                    .collect(Collectors.toMap(MesStep::getId, s -> s, (a, b) -> a));

            List<TrackReworkOptionVO> options = new ArrayList<>();
            int used = getReworkCount(lot, lot.getCurrentSortNo());
            for (MesRouteEdge edge : reworkEdges) {
                int remain = Math.max(0, edge.getMaxReworkCount() - used);
                if (remain <= 0) {
                    continue;
                }
                TrackReworkOptionVO opt = new TrackReworkOptionVO();
                opt.setToSortNo(edge.getToSortNo());
                opt.setMaxReworkCount(edge.getMaxReworkCount());
                opt.setRemainCount(remain);
                if (StringUtils.hasText(edge.getReasonCodes())) {
                    opt.setReasonCodes(Arrays.stream(edge.getReasonCodes().split(","))
                            .map(String::trim)
                            .filter(StringUtils::hasText)
                            .toList());
                } else {
                    opt.setReasonCodes(Collections.emptyList());
                }
                MesRouteStep target = stepBySort.get(edge.getToSortNo());
                if (target != null) {
                    MesStep step = stepMap.get(target.getStepId());
                    if (step != null) {
                        opt.setToStepCode(step.getStepCode());
                        opt.setToStepName(step.getStepName());
                    }
                }
                options.add(opt);
            }
            vo.setReworkOptions(options);
            boolean statusOk = STATUS_WAIT.equals(lot.getStatus()) || STATUS_PROCESSING.equals(lot.getStatus());
            vo.setCanRework(statusOk && !options.isEmpty() && StpUtil.hasPermission("track:rework"));
        }
        return vo;
    }

    /*
     * 获取返工次数
     */
    private int getReworkCount(MesLot lot, Integer fromSortNo) {
        if (fromSortNo == null || !StringUtils.hasText(lot.getReworkCounts())) {
            return 0;
        }
        try {
            JSONObject obj = JSONUtil.parseObj(lot.getReworkCounts());
            return obj.getInt(String.valueOf(fromSortNo), 0);
        } catch (Exception e) {
            return 0;
        }
    }

    /*
     * 设置返工次数
     */
    private void putReworkCount(MesLot lot, Integer fromSortNo, int count) {
        JSONObject obj;
        if (StringUtils.hasText(lot.getReworkCounts())) {
            try {
                obj = JSONUtil.parseObj(lot.getReworkCounts());
            } catch (Exception e) {
                obj = new JSONObject();
            }
        } else {
            obj = new JSONObject();
        }
        obj.set(String.valueOf(fromSortNo), count);
        lot.setReworkCounts(obj.toString());
    }

    @Override
    public List<MesTxLogVO> history(Long lotId) {
        MesLot lot = mesLotMapper.selectById(lotId);
        AssertUtil.notNull(lot, "批次不存在");

        List<MesTxLog> rows = mesTxLogMapper.selectList(new LambdaQueryWrapper<MesTxLog>()
                .eq(MesTxLog::getLotId, lotId)
                .orderByAsc(MesTxLog::getCreateTime)
                .orderByAsc(MesTxLog::getId));
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> stepIds = rows.stream()
                .map(MesTxLog::getStepId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> stepNameMap = stepIds.isEmpty()
                ? Collections.emptyMap()
                : mesStepMapper.selectBatchIds(stepIds).stream()
                .collect(Collectors.toMap(MesStep::getId, MesStep::getStepName, (a, b) -> a));

        List<MesTxLogVO> list = new ArrayList<>(rows.size());
        for (MesTxLog row : rows) {
            MesTxLogVO item = new MesTxLogVO();
            item.setId(row.getId());
            item.setLotId(row.getLotId());
            item.setLotNo(row.getLotNo());
            item.setTxType(row.getTxType());
            item.setFromStatus(row.getFromStatus());
            item.setToStatus(row.getToStatus());
            item.setFromSortNo(row.getFromSortNo());
            item.setToSortNo(row.getToSortNo());
            item.setStepId(row.getStepId());
            item.setStepName(row.getStepId() != null ? stepNameMap.get(row.getStepId()) : null);
            item.setEqpId(row.getEqpId());
            item.setRecipeId(row.getRecipeId());
            item.setRecipeVersionId(row.getRecipeVersionId());
            item.setRouteVersionId(row.getRouteVersionId());
            item.setRemark(row.getRemark());
            item.setOperUserId(row.getOperUserId());
            item.setOperUserName(row.getOperUserName());
            item.setCreateTime(row.getCreateTime());
            list.add(item);
        }
        return list;
    }

    private MesLotStepVO toStepVo(MesRouteStep row) {
        MesLotStepVO stepVo = new MesLotStepVO();
        stepVo.setStepId(row.getStepId());
        stepVo.setSortNo(row.getSortNo());
        stepVo.setNextSortNo(row.getNextSortNo());
        MesStep step = mesStepMapper.selectById(row.getStepId());
        if (step != null) {
            stepVo.setStepCode(step.getStepCode());
            stepVo.setStepName(step.getStepName());
        }
        return stepVo;
    }

    /** 获取有效批次 */
    private MesLot requireExecutableLot(Long lotId) {
        MesLot lot = mesLotMapper.selectById(lotId);
        AssertUtil.notNull(lot, "批次不存在");
        AssertUtil.notNull(lot.getRouteVersionId(), "批次未放行");
        return lot;
    }

    private MesRouteStep requireCurrentStep(MesLot lot) {
        MesRouteStep current = mesRouteStepMapper.selectOne(new LambdaQueryWrapper<MesRouteStep>()
                .eq(MesRouteStep::getVersionId, lot.getRouteVersionId())
                .eq(MesRouteStep::getSortNo, lot.getCurrentSortNo())
                .last("LIMIT 1"));
        AssertUtil.notNull(current, "当前站不在路线快照中");
        AssertUtil.isTrue(Objects.equals(current.getStepId(), lot.getCurrentStepId())
                        || lot.getCurrentStepId() == null,
                "当前工序与快照不一致");
        if (lot.getCurrentStepId() == null) {
            lot.setCurrentStepId(current.getStepId());
        }
        return current;
    }

    private TrackTxnResultVO toTxnVo(MesLot lot, String txType, boolean completed) {
        TrackTxnResultVO vo = new TrackTxnResultVO();
        vo.setLotId(lot.getId());
        vo.setLotNo(lot.getLotNo());
        vo.setTxType(txType);
        vo.setStatus(lot.getStatus());
        vo.setCurrentSortNo(lot.getCurrentSortNo());
        vo.setCurrentStepId(lot.getCurrentStepId());
        vo.setCurrentEqpId(lot.getCurrentEqpId());
        vo.setRouteVersionId(lot.getRouteVersionId());
        vo.setCompleted(completed);
        return vo;
    }

    /** 写入 Track 事务履历，返回履历 ID */
    private Long writeTxLog(MesLot lot, String txType, String fromStatus, String toStatus,
                            Integer fromSortNo, Integer toSortNo, Long stepId, Long eqpId,
                            Long routeVersionId, String remark, Long recipeId, Long recipeVersionId) {
        long userId = StpUtil.getLoginIdAsLong();
        SysUser user = sysUserMapper.selectById(userId);
        MesTxLog log = new MesTxLog();
        log.setLotId(lot.getId());
        log.setLotNo(lot.getLotNo());
        log.setTxType(txType);
        log.setFromStatus(fromStatus);
        log.setToStatus(toStatus);
        log.setFromSortNo(fromSortNo);
        log.setToSortNo(toSortNo);
        log.setStepId(stepId);
        log.setEqpId(eqpId);
        log.setRecipeId(recipeId);
        log.setRecipeVersionId(recipeVersionId);
        log.setRouteVersionId(routeVersionId);
        log.setRemark(remark);
        log.setOperUserId(userId);
        log.setOperUserName(user != null ? user.getUserName() : null);
        log.setCreateTime(LocalDateTime.now());
        mesTxLogMapper.insert(log);
        return log.getId();
    }
}
