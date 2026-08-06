package com.mes.track.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.json.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mes.common.AssertUtil;
import com.mes.common.BusinessException;
import com.mes.dispatch.service.DispatchService;
import com.mes.equipment.entity.MesEqp;
import com.mes.equipment.mapper.MesEqpMapper;
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
import com.mes.route.mapper.MesRouteMapper;
import com.mes.route.mapper.MesRouteStepMapper;
import com.mes.route.mapper.MesRouteVersionMapper;
import com.mes.route.mapper.MesStepMapper;
import com.mes.route.support.RouteEdgeResolver;
import com.mes.route.support.RouteEdgeTypes;
import com.mes.route.support.RouteTrackOutDecision;
import com.mes.route.support.StepEqpTypeGuard;
import com.mes.system.entity.SysUser;
import com.mes.system.mapper.SysUserMapper;
import com.mes.track.entity.MesTxLog;
import com.mes.track.mapper.MesTxLogMapper;
import com.mes.track.service.TrackService;
import com.mes.track.support.OffFlowCountStore;
import com.mes.track.support.ReworkCountStore;
import com.mes.track.vo.MesTxLogVO;
import com.mes.track.vo.TrackBranchOptionVO;
import com.mes.track.vo.TrackContextVO;
import com.mes.track.vo.TrackOffFlowOptionVO;
import com.mes.track.vo.TrackReleaseResultVO;
import com.mes.track.vo.TrackReworkOptionVO;
import com.mes.track.vo.TrackSkipOptionVO;
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
    public static final String TX_SKIP = "SKIP";
    public static final String TX_OFF_FLOW = "OFF_FLOW";
    public static final String TX_OFF_FLOW_RESUME = "OFF_FLOW_RESUME";

    private final MesLotMapper mesLotMapper;
    private final MesRouteMapper mesRouteMapper;
    private final MesRouteVersionMapper mesRouteVersionMapper;
    private final MesRouteStepMapper mesRouteStepMapper;
    private final MesStepMapper mesStepMapper;
    private final MesTxLogMapper mesTxLogMapper;
    private final SysUserMapper sysUserMapper;
    private final WipProjectionService wipProjectionService;
    private final HoldService holdService;
    private final MesEqpService mesEqpService;
    private final MesEqpMapper mesEqpMapper;
    private final DispatchService dispatchService;
    private final RecipeFacade recipeFacade;
    private final RouteEdgeResolver routeEdgeResolver;
    private final ReworkCountStore reworkCountStore;
    private final OffFlowCountStore offFlowCountStore;
    private final StepEqpTypeGuard stepEqpTypeGuard;

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
        lot.setOffFlowCounts(null);
        clearOffFlow(lot);
        lot.setUpdateBy(StpUtil.getLoginIdAsLong());
        int rows = mesLotMapper.updateById(lot);
        AssertUtil.isTrue(rows > 0, "数据已被他人修改，请刷新后重试");
        wipProjectionService.syncFromLot(lot);

        writeTxLog(lot, TX_RELEASE, fromStatus, STATUS_WAIT, fromSortNo, firstStep.getSortNo(),
                firstStep.getStepId(), null, active.getId(), "放行进首站", null, null, null);

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
        dispatchService.assertNotOffFlowAnchored(eqpId, lotId);// Off-Flow 锚点占台
        AssertUtil.isTrue(STATUS_WAIT.equals(lot.getStatus()), "仅等待加工状态可开工");
        AssertUtil.notNull(lot.getCurrentSortNo(), "当前站未知，无法开工");
        AssertUtil.notNull(lot.getRouteVersionId(), "未绑定路线版本");

        MesRouteStep current = requireCurrentStep(lot);
        stepEqpTypeGuard.requireMatch(lot, eqpId);// 设备类型必须匹配
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
                recipe != null ? recipe.getVersionId() : null, buildTrackInExt(lot, eqpId));
        dispatchService.consumeOnTrackIn(lotId, eqpId, txId);// 消耗预约

        return toTxnVo(lot, TX_TRACK_IN, false);
    }

    /**
     * 构建 TrackIn 扩展信息
     */
    private String buildTrackInExt(MesLot lot, Long eqpId) {
        JSONObject ext = new JSONObject();
        String required = stepEqpTypeGuard.resolveRequired(lot);
        if (StringUtils.hasText(required)) {
            ext.set("eqpTypeRequired", required);
        }
        MesEqp eqp = mesEqpMapper.selectById(eqpId);
        if (eqp != null && StringUtils.hasText(eqp.getEqpType())) {
            ext.set("eqpTypeActual", eqp.getEqpType().trim());
        }
        if (eqpId != null) {
            ext.set("eqpId", String.valueOf(eqpId));
        }
        return ext.isEmpty() ? null : ext.toString();
    }

    /**
     * 完工选边：
     * - 有 resultCode → 只匹配当前站 branch，未命中直接失败（不降级走默认）
     * - 无 resultCode → 走 normal；旧快照无边表则回退步骤 next_sort_no
     * - 永不走 rework（返工必须调 /track/rework）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TrackTxnResultVO trackOut(Long lotId, String resultCode) {
        MesLot lot = requireExecutableLot(lotId);
        holdService.assertNoActive(lotId);
        AssertUtil.isTrue(STATUS_PROCESSING.equals(lot.getStatus()), "仅加工中状态可完工");
        AssertUtil.notNull(lot.getCurrentSortNo(), "当前站未知，无法完工");
        AssertUtil.notNull(lot.getRouteVersionId(), "未绑定路线版本");

        MesRouteStep current = requireCurrentStep(lot);
        String fromStatus = lot.getStatus();
        Integer fromSortNo = lot.getCurrentSortNo();
        Long fromEqpId = lot.getCurrentEqpId();

        RouteTrackOutDecision decision = routeEdgeResolver.resolveTrackOut(
                lot.getRouteVersionId(), current, resultCode);

        // 旁路末站无下一站：不完工，走 Resume 回锚点
        if (decision.isCompleted() && isOffFlow(lot)) {
            return doResumeOffFlow(lot, fromStatus, fromSortNo, fromEqpId, true, null);
        }

        String toStatus;
        if (decision.isCompleted()) {
            toStatus = STATUS_COMPLETED;
            lot.setStatus(STATUS_COMPLETED);
            lot.setCurrentEqpId(null);
        } else {
            toStatus = STATUS_WAIT;
            lot.setStatus(STATUS_WAIT);
            lot.setCurrentSortNo(decision.getToSortNo());
            lot.setCurrentStepId(decision.getToStepId());
            lot.setCurrentEqpId(null);
        }

        lot.setUpdateBy(StpUtil.getLoginIdAsLong());
        int rows = mesLotMapper.updateById(lot);
        AssertUtil.isTrue(rows > 0, "数据已被他人修改，请刷新后重试");
        wipProjectionService.syncFromLot(lot);

        writeTxLog(lot, TX_TRACK_OUT, fromStatus, toStatus, fromSortNo, decision.getToSortNo(),
                decision.getToStepId(), fromEqpId, lot.getRouteVersionId(), decision.getRemark(),
                null, null, decision.toExtJson());

        return toTxnVo(lot, TX_TRACK_OUT, decision.isCompleted());
    }

    /**
     * 回流：按快照 rework 边跳回前序站，按触发站累计次数，超限拒绝。
     * 与 TrackOut 分支不同：独立事务 + track:rework 权限。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TrackTxnResultVO rework(Long lotId, Integer toSortNo, String reasonCode, String remark) {
        MesLot lot = requireExecutableLot(lotId);
        holdService.assertNoActive(lotId);
        AssertUtil.isTrue(STATUS_WAIT.equals(lot.getStatus()) || STATUS_PROCESSING.equals(lot.getStatus()),
                "仅等待或加工中状态可返工");
        AssertUtil.isFalse(isOffFlow(lot), "Off-Flow 中不可返工");
        AssertUtil.notNull(lot.getCurrentSortNo(), "当前站未知，无法返工");
        AssertUtil.notNull(toSortNo, "回流目标站不能为空");

        Integer fromSortNo = lot.getCurrentSortNo();
        MesRouteEdge edge = routeEdgeResolver.findRework(lot.getRouteVersionId(), fromSortNo, toSortNo);
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

        int used = reworkCountStore.get(lot, fromSortNo);
        AssertUtil.isTrue(used + 1 <= edge.getMaxReworkCount(), "返工次数已达上限");

        MesRouteStep target = routeEdgeResolver.findStep(lot.getRouteVersionId(), toSortNo);
        AssertUtil.notNull(target, "回流目标站不存在");

        String fromStatus = lot.getStatus();
        Long fromEqpId = lot.getCurrentEqpId();
        int newCount = used + 1;
        reworkCountStore.put(lot, fromSortNo, newCount);

        lot.setStatus(STATUS_WAIT);
        lot.setCurrentSortNo(target.getSortNo());
        lot.setCurrentStepId(target.getStepId());
        lot.setCurrentEqpId(null);
        lot.setUpdateBy(StpUtil.getLoginIdAsLong());
        int rows = mesLotMapper.updateById(lot);
        AssertUtil.isTrue(rows > 0, "数据已被他人修改，请刷新后重试");
        wipProjectionService.syncFromLot(lot);

        String logRemark = StringUtils.hasText(remark) ? remark.trim() : null;
        JSONObject ext = new JSONObject();
        if (StringUtils.hasText(reason)) {
            ext.set("reasonCode", reason);
        }
        ext.set("reworkCount", newCount);
        ext.set("maxReworkCount", edge.getMaxReworkCount());
        ext.set("edgeId", String.valueOf(edge.getId()));

        writeTxLog(lot, TX_REWORK, fromStatus, STATUS_WAIT, fromSortNo, target.getSortNo(),
                target.getStepId(), fromEqpId, lot.getRouteVersionId(), logRemark, null, null, ext.toString());

        TrackTxnResultVO vo = toTxnVo(lot, TX_REWORK, false);
        vo.setReworkCount(newCount);
        vo.setMaxReworkCount(edge.getMaxReworkCount());
        return vo;
    }

    /**
     * 前向跳站：仅 wait（未开工）可跳；命中 skip_allow，中间站记履历，落到目标 wait。
     * processing 须先 TrackOut（后置可加 Abort）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TrackTxnResultVO skip(Long lotId, Integer toSortNo, String reasonCode, String remark) {
        MesLot lot = requireExecutableLot(lotId);
        holdService.assertNoActive(lotId);
        AssertUtil.isTrue(STATUS_WAIT.equals(lot.getStatus()),
                "仅等待状态可跳站，加工中请先完工");
        AssertUtil.isFalse(isOffFlow(lot), "Off-Flow 中不可跳站");
        AssertUtil.notNull(lot.getCurrentSortNo(), "当前站未知，无法跳站");
        AssertUtil.notNull(toSortNo, "跳站目标站不能为空");

        Integer fromSortNo = lot.getCurrentSortNo();
        MesRouteEdge edge = routeEdgeResolver.findSkip(lot.getRouteVersionId(), fromSortNo, toSortNo);
        AssertUtil.notNull(edge, "当前站未配置跳至目标站");
        // 获取跳站目标站可跳过的中间站
        List<Integer> skipped = routeEdgeResolver.computeSkippedSortNos(
                lot.getRouteVersionId(), fromSortNo, toSortNo);
        AssertUtil.notNull(skipped, "跳站目标不可沿主路径前向到达");
        // 获取跳站目标站不可跳过的中间站
        Integer bad = routeEdgeResolver.findDisallowedSkipSort(
                lot.getRouteVersionId(), fromSortNo, toSortNo, skipped);
        AssertUtil.isTrue(bad == null, "跳站路径含不可跳站: sortNo=" + bad);

        String reason = reasonCode == null ? null : reasonCode.trim();
        if (StringUtils.hasText(edge.getReasonCodes())) {
            AssertUtil.notBlank(reason, "跳站原因不能为空");
            Set<String> allowed = Arrays.stream(edge.getReasonCodes().split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toSet());
            AssertUtil.isTrue(allowed.contains(reason), "跳站原因不匹配");
        }

        MesRouteStep target = routeEdgeResolver.findStep(lot.getRouteVersionId(), toSortNo);
        AssertUtil.notNull(target, "跳站目标站不存在");

        String fromStatus = lot.getStatus();
        Long fromEqpId = lot.getCurrentEqpId();
        lot.setStatus(STATUS_WAIT);
        lot.setCurrentSortNo(target.getSortNo());
        lot.setCurrentStepId(target.getStepId());
        lot.setCurrentEqpId(null);
        lot.setUpdateBy(StpUtil.getLoginIdAsLong());
        int rows = mesLotMapper.updateById(lot);
        AssertUtil.isTrue(rows > 0, "数据已被他人修改，请刷新后重试");
        wipProjectionService.syncFromLot(lot);

        String logRemark = StringUtils.hasText(remark) ? remark.trim() : null;
        JSONObject ext = new JSONObject();
        ext.set("edgeType", RouteEdgeTypes.SKIP_ALLOW);
        ext.set("edgeId", String.valueOf(edge.getId()));
        ext.set("fromSortNo", fromSortNo);
        ext.set("toSortNo", toSortNo);
        ext.set("skippedSortNos", skipped);
        if (StringUtils.hasText(reason)) {
            ext.set("reasonCode", reason);
        }

        writeTxLog(lot, TX_SKIP, fromStatus, STATUS_WAIT, fromSortNo, target.getSortNo(),
                target.getStepId(), fromEqpId, lot.getRouteVersionId(), logRemark, null, null, ext.toString());

        return toTxnVo(lot, TX_SKIP, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TrackTxnResultVO enterOffFlow(Long lotId, Integer toSortNo, String reasonCode, String remark) {
        MesLot lot = requireExecutableLot(lotId);
        holdService.assertNoActive(lotId);
        AssertUtil.isTrue(STATUS_WAIT.equals(lot.getStatus()) || STATUS_PROCESSING.equals(lot.getStatus()),
                "当前状态不可进入 Off-Flow");
        AssertUtil.isFalse(isOffFlow(lot), "已在 Off-Flow 中");
        AssertUtil.notNull(lot.getCurrentSortNo(), "当前站未知，无法进入 Off-Flow");
        AssertUtil.notNull(toSortNo, "Off-Flow 目标站不能为空");

        Integer fromSortNo = lot.getCurrentSortNo();
        MesRouteEdge edge = routeEdgeResolver.findOffFlow(lot.getRouteVersionId(), fromSortNo, toSortNo);
        AssertUtil.notNull(edge, "当前站未配置 Off-Flow");

        String reason = reasonCode == null ? null : reasonCode.trim();
        if (StringUtils.hasText(edge.getReasonCodes())) {
            AssertUtil.notBlank(reason, "Off-Flow 原因不能为空");
            Set<String> allowed = Arrays.stream(edge.getReasonCodes().split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toSet());
            AssertUtil.isTrue(allowed.contains(reason), "Off-Flow 原因不匹配");
        }

        MesRouteStep target = routeEdgeResolver.findStep(lot.getRouteVersionId(), toSortNo);
        AssertUtil.notNull(target, "Off-Flow 入口站不存在");
        int max = edge.getMaxReworkCount() != null && edge.getMaxReworkCount() >= 1
                ? edge.getMaxReworkCount() : 1;

        int used = offFlowCountStore.get(lot, fromSortNo);
        AssertUtil.isTrue(used + 1 <= max, "Off-Flow 次数已达上限");

        String fromStatus = lot.getStatus();
        Long fromEqpId = lot.getCurrentEqpId();
        int newCount = used + 1;
        offFlowCountStore.put(lot, fromSortNo, newCount);

        lot.setOffFlow(1);
        lot.setOffFlowAnchorSort(fromSortNo);
        lot.setOffFlowAnchorStepId(lot.getCurrentStepId());
        lot.setOffFlowAnchorEqpId(fromEqpId);
        lot.setOffFlowAnchorStatus(fromStatus);
        lot.setStatus(STATUS_WAIT);
        lot.setCurrentSortNo(target.getSortNo());
        lot.setCurrentStepId(target.getStepId());
        lot.setCurrentEqpId(null);
        lot.setUpdateBy(StpUtil.getLoginIdAsLong());
        int rows = mesLotMapper.updateById(lot);
        AssertUtil.isTrue(rows > 0, "数据已被他人修改，请刷新后重试");
        wipProjectionService.syncFromLot(lot);

        String logRemark = StringUtils.hasText(remark) ? remark.trim() : null;
        JSONObject ext = new JSONObject();
        ext.set("edgeType", RouteEdgeTypes.OFF_FLOW);
        ext.set("edgeId", String.valueOf(edge.getId()));
        ext.set("fromSortNo", fromSortNo);
        ext.set("toSortNo", toSortNo);
        ext.set("anchorSortNo", fromSortNo);
        ext.set("anchorStatus", fromStatus);
        if (fromEqpId != null) {
            ext.set("anchorEqpId", String.valueOf(fromEqpId));
        }
        if (StringUtils.hasText(reason)) {
            ext.set("reasonCode", reason);
        }
        ext.set("offFlowCount", newCount);
        ext.set("maxOffFlowCount", max);

        writeTxLog(lot, TX_OFF_FLOW, fromStatus, STATUS_WAIT, fromSortNo, target.getSortNo(),
                target.getStepId(), fromEqpId, lot.getRouteVersionId(), logRemark, null, null, ext.toString());

        TrackTxnResultVO vo = toTxnVo(lot, TX_OFF_FLOW, false);
        vo.setOffFlowCount(newCount);
        vo.setMaxOffFlowCount(max);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TrackTxnResultVO resumeOffFlow(Long lotId, String remark) {
        MesLot lot = requireExecutableLot(lotId);
        holdService.assertNoActive(lotId);
        AssertUtil.isTrue(isOffFlow(lot), "不在 Off-Flow 中");
        AssertUtil.isTrue(STATUS_WAIT.equals(lot.getStatus()), "仅旁路等待状态可回主路径，加工中请先完工");
        AssertUtil.notNull(lot.getCurrentSortNo(), "当前站未知");
        AssertUtil.isTrue(routeEdgeResolver.isOffFlowTerminal(lot.getRouteVersionId(), lot.getCurrentSortNo()),
                "仅旁路末站可回主路径");

        String fromStatus = lot.getStatus();
        Integer fromSortNo = lot.getCurrentSortNo();
        Long fromEqpId = lot.getCurrentEqpId();
        return doResumeOffFlow(lot, fromStatus, fromSortNo, fromEqpId, false, remark);
    }

    /**
     * Off-Flow 回主路径：Lot 回到进入时锚点站，尝试恢复锚点状态/机台。
     * 锚点机台不可用或已被别批占用时降级为 wait；viaTrackOut=true 表示旁路末站 TrackOut 自动触发。
     */
    private TrackTxnResultVO doResumeOffFlow(MesLot lot, String fromStatus, Integer fromSortNo,
                                             Long fromEqpId, boolean viaTrackOut, String remark) {
        AssertUtil.isTrue(isOffFlow(lot), "不在 Off-Flow 中");
        AssertUtil.notNull(lot.getOffFlowAnchorSort(), "Off-Flow 锚点缺失");

        MesRouteStep anchor = routeEdgeResolver.findStep(lot.getRouteVersionId(), lot.getOffFlowAnchorSort());
        AssertUtil.notNull(anchor, "Off-Flow 锚点站不存在");

        String restoreStatus = StringUtils.hasText(lot.getOffFlowAnchorStatus())
                ? lot.getOffFlowAnchorStatus() : STATUS_WAIT;
        AssertUtil.isTrue(STATUS_WAIT.equals(restoreStatus) || STATUS_PROCESSING.equals(restoreStatus),
                "Off-Flow 锚点状态异常");
        Long restoreEqp = STATUS_PROCESSING.equals(restoreStatus) ? lot.getOffFlowAnchorEqpId() : null;
        Integer anchorSort = lot.getOffFlowAnchorSort();
        boolean degraded = false;

        if (STATUS_PROCESSING.equals(restoreStatus) && restoreEqp != null) {
            try {
                mesEqpService.assertUsable(restoreEqp);
            } catch (BusinessException ex) {
                restoreStatus = STATUS_WAIT;
                restoreEqp = null;
                degraded = true;
            }
            if (!degraded) {
                MesLot other = mesLotMapper.selectOne(new LambdaQueryWrapper<MesLot>()
                        .eq(MesLot::getCurrentEqpId, restoreEqp)
                        .eq(MesLot::getStatus, STATUS_PROCESSING)
                        .ne(MesLot::getId, lot.getId())
                        .last("LIMIT 1"));
                if (other != null) {
                    restoreStatus = STATUS_WAIT;
                    restoreEqp = null;
                    degraded = true;
                }
            }
        }

        clearOffFlow(lot);
        lot.setStatus(restoreStatus);
        lot.setCurrentSortNo(anchor.getSortNo());
        lot.setCurrentStepId(anchor.getStepId());
        lot.setCurrentEqpId(restoreEqp);
        lot.setUpdateBy(StpUtil.getLoginIdAsLong());
        int rows = mesLotMapper.updateById(lot);
        AssertUtil.isTrue(rows > 0, "数据已被他人修改，请刷新后重试");
        wipProjectionService.syncFromLot(lot);

        String logRemark = StringUtils.hasText(remark) ? remark.trim() : null;
        JSONObject ext = new JSONObject();
        ext.set("fromSortNo", fromSortNo);
        ext.set("anchorSortNo", anchorSort);
        ext.set("restoredStatus", restoreStatus);
        if (restoreEqp != null) {
            ext.set("restoredEqpId", String.valueOf(restoreEqp));
        }
        if (degraded) {
            ext.set("degraded", true);
        }
        if (viaTrackOut) {
            ext.set("viaTrackOut", true);
        }

        writeTxLog(lot, TX_OFF_FLOW_RESUME, fromStatus, restoreStatus, fromSortNo, anchor.getSortNo(),
                anchor.getStepId(), fromEqpId, lot.getRouteVersionId(), logRemark, null, null, ext.toString());

        return toTxnVo(lot, TX_OFF_FLOW_RESUME, false);
    }

    /** 现场台上下文：当前站 + 默认下一站 + 可选分支/回流/跳站/Off-Flow 列表 */
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
        vo.setBranchOptions(Collections.emptyList());
        vo.setSkipOptions(Collections.emptyList());
        vo.setOffFlowOptions(Collections.emptyList());
        vo.setCanRework(false);
        vo.setCanSkip(false);
        vo.setCanEnterOffFlow(false);
        vo.setCanResumeOffFlow(false);
        vo.setOffFlow(isOffFlow(lot));
        vo.setOffFlowAnchorSortNo(lot.getOffFlowAnchorSort());
        vo.setReworkCount(lot.getCurrentSortNo() != null ? reworkCountStore.get(lot, lot.getCurrentSortNo()) : 0);

        if (lot.getRouteVersionId() != null) {
            MesRouteVersion version = mesRouteVersionMapper.selectById(lot.getRouteVersionId());
            if (version != null) {
                vo.setRouteVersionNo(version.getVersionNo());
            }
        }

        if (lot.getRouteVersionId() == null || lot.getCurrentSortNo() == null) {
            return vo;
        }

        MesRouteStep current = routeEdgeResolver.findStep(lot.getRouteVersionId(), lot.getCurrentSortNo());
        if (current == null) {
            return vo;
        }
        vo.setCurrentStep(toStepVo(current));

        MesRouteStep next = routeEdgeResolver.resolveDefaultNext(lot.getRouteVersionId(), current);
        if (next != null) {
            vo.setNextStep(toStepVo(next));
        }

        List<MesRouteEdge> outEdges = routeEdgeResolver.listNormalAndBranch(
                lot.getRouteVersionId(), lot.getCurrentSortNo());
        List<MesRouteEdge> branchEdges = outEdges.stream()
                .filter(e -> RouteEdgeTypes.BRANCH.equals(e.getEdgeType()))
                .toList();
        if (!branchEdges.isEmpty()) {
            Set<Integer> toSorts = branchEdges.stream().map(MesRouteEdge::getToSortNo).collect(Collectors.toSet());
            Map<Integer, MesRouteStep> stepBySort = routeEdgeResolver.mapRouteStepsBySort(
                    lot.getRouteVersionId(), toSorts);
            Map<Long, MesStep> stepMap = routeEdgeResolver.mapStepsById(
                    stepBySort.values().stream().map(MesRouteStep::getStepId).collect(Collectors.toSet()));
            List<TrackBranchOptionVO> branchOptions = new ArrayList<>();
            for (MesRouteEdge edge : branchEdges) {
                TrackBranchOptionVO opt = new TrackBranchOptionVO();
                opt.setConditionCode(edge.getConditionCode());
                opt.setToSortNo(edge.getToSortNo());
                fillStepLabels(opt, stepBySort.get(edge.getToSortNo()), stepMap);
                branchOptions.add(opt);
            }
            vo.setBranchOptions(branchOptions);
        }

        List<MesRouteEdge> reworkEdges = routeEdgeResolver.listRework(
                lot.getRouteVersionId(), lot.getCurrentSortNo());
        if (!reworkEdges.isEmpty()) {
            Set<Integer> toSorts = reworkEdges.stream().map(MesRouteEdge::getToSortNo).collect(Collectors.toSet());
            Map<Integer, MesRouteStep> stepBySort = routeEdgeResolver.mapRouteStepsBySort(
                    lot.getRouteVersionId(), toSorts);
            Map<Long, MesStep> stepMap = routeEdgeResolver.mapStepsById(
                    stepBySort.values().stream().map(MesRouteStep::getStepId).collect(Collectors.toSet()));

            List<TrackReworkOptionVO> options = new ArrayList<>();
            int used = reworkCountStore.get(lot, lot.getCurrentSortNo());
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
                fillStepLabels(opt, stepBySort.get(edge.getToSortNo()), stepMap);
                options.add(opt);
            }
            vo.setReworkOptions(options);
            boolean statusOk = !isOffFlow(lot)
                    && (STATUS_WAIT.equals(lot.getStatus()) || STATUS_PROCESSING.equals(lot.getStatus()));
            vo.setCanRework(statusOk && !options.isEmpty() && StpUtil.hasPermission("track:rework"));
        }

        List<MesRouteEdge> skipEdges = routeEdgeResolver.listSkip(
                lot.getRouteVersionId(), lot.getCurrentSortNo());
        if (!skipEdges.isEmpty() && !isOffFlow(lot)) {
            Set<Integer> toSorts = skipEdges.stream().map(MesRouteEdge::getToSortNo).collect(Collectors.toSet());
            Map<Integer, MesRouteStep> stepBySort = routeEdgeResolver.mapRouteStepsBySort(
                    lot.getRouteVersionId(), toSorts);
            Map<Long, MesStep> stepMap = routeEdgeResolver.mapStepsById(
                    stepBySort.values().stream().map(MesRouteStep::getStepId).collect(Collectors.toSet()));
            List<TrackSkipOptionVO> skipOptions = new ArrayList<>();
            for (MesRouteEdge edge : skipEdges) {
                List<Integer> skipped = routeEdgeResolver.computeSkippedSortNos(
                        lot.getRouteVersionId(), lot.getCurrentSortNo(), edge.getToSortNo());
                if (skipped == null) {
                    continue;
                }
                Integer bad = routeEdgeResolver.findDisallowedSkipSort(
                        lot.getRouteVersionId(), lot.getCurrentSortNo(), edge.getToSortNo(), skipped);
                if (bad != null) {
                    continue;
                }
                TrackSkipOptionVO opt = new TrackSkipOptionVO();
                opt.setToSortNo(edge.getToSortNo());
                opt.setSkippedSortNos(skipped);
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
                skipOptions.add(opt);
            }
            vo.setSkipOptions(skipOptions);
            vo.setCanSkip(STATUS_WAIT.equals(lot.getStatus())
                    && !skipOptions.isEmpty()
                    && StpUtil.hasPermission("track:skip"));
        }

        // 主路径：组装可进 Off-Flow 的旁路入口；已在旁路：只算能否 Resume
        if (!isOffFlow(lot)) {
            List<MesRouteEdge> offEdges = routeEdgeResolver.listOffFlow(
                    lot.getRouteVersionId(), lot.getCurrentSortNo());
            if (!offEdges.isEmpty()) {
                Set<Integer> toSorts = offEdges.stream().map(MesRouteEdge::getToSortNo).collect(Collectors.toSet());
                Map<Integer, MesRouteStep> stepBySort = routeEdgeResolver.mapRouteStepsBySort(
                        lot.getRouteVersionId(), toSorts);
                Map<Long, MesStep> stepMap = routeEdgeResolver.mapStepsById(
                        stepBySort.values().stream().map(MesRouteStep::getStepId).collect(Collectors.toSet()));
                List<TrackOffFlowOptionVO> offOptions = new ArrayList<>();
                int used = offFlowCountStore.get(lot, lot.getCurrentSortNo());
                for (MesRouteEdge edge : offEdges) {
                    int max = edge.getMaxReworkCount() != null ? edge.getMaxReworkCount() : 1;
                    int remain = Math.max(0, max - used);
                    if (remain <= 0) {
                        continue;
                    }
                    TrackOffFlowOptionVO opt = new TrackOffFlowOptionVO();
                    opt.setToSortNo(edge.getToSortNo());
                    opt.setMaxOffFlowCount(max);
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
                    offOptions.add(opt);
                }
                vo.setOffFlowOptions(offOptions);
                boolean statusOk = STATUS_WAIT.equals(lot.getStatus()) || STATUS_PROCESSING.equals(lot.getStatus());
                vo.setCanEnterOffFlow(statusOk && !offOptions.isEmpty()
                        && StpUtil.hasPermission("track:off-flow"));
            }
        } else {
            // 旁路末站 + wait + 有权限 → 可回主路径
            boolean terminal = routeEdgeResolver.isOffFlowTerminal(
                    lot.getRouteVersionId(), lot.getCurrentSortNo());
            vo.setCanResumeOffFlow(STATUS_WAIT.equals(lot.getStatus()) && terminal
                    && StpUtil.hasPermission("track:off-flow"));
        }
        return vo;
    }

    private static void fillStepLabels(TrackBranchOptionVO opt, MesRouteStep target, Map<Long, MesStep> stepMap) {
        if (target == null) {
            return;
        }
        MesStep step = stepMap.get(target.getStepId());
        if (step != null) {
            opt.setToStepCode(step.getStepCode());
            opt.setToStepName(step.getStepName());
        }
    }

    private static void fillStepLabels(TrackReworkOptionVO opt, MesRouteStep target, Map<Long, MesStep> stepMap) {
        if (target == null) {
            return;
        }
        MesStep step = stepMap.get(target.getStepId());
        if (step != null) {
            opt.setToStepCode(step.getStepCode());
            opt.setToStepName(step.getStepName());
        }
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
            item.setExtJson(row.getExtJson());
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
        stepVo.setEqpType(row.getEqpType());
        stepVo.setStepType(row.getStepType());
        MesStep step = mesStepMapper.selectById(row.getStepId());
        if (step != null) {
            stepVo.setStepCode(step.getStepCode());
            stepVo.setStepName(step.getStepName());
            if (!StringUtils.hasText(stepVo.getEqpType())) {
                stepVo.setEqpType(step.getEqpType());
            }
            if (stepVo.getStepType() == null) {
                stepVo.setStepType(step.getStepType());
            }
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

    /** 获取当前批次的站点 */
    private MesRouteStep requireCurrentStep(MesLot lot) {
        MesRouteStep current = routeEdgeResolver.findStep(lot.getRouteVersionId(), lot.getCurrentSortNo());
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
        vo.setOffFlow(isOffFlow(lot));
        return vo;
    }

    /** 是否批次是否在off-flow */
    private static boolean isOffFlow(MesLot lot) {
        return Integer.valueOf(1).equals(lot.getOffFlow());
    }

    private static void clearOffFlow(MesLot lot) {
        lot.setOffFlow(0);
        lot.setOffFlowAnchorSort(null);
        lot.setOffFlowAnchorStepId(null);
        lot.setOffFlowAnchorEqpId(null);
        lot.setOffFlowAnchorStatus(null);
    }

    /** 写履历；extJson 放分支/回流结构化字段，remark 只留人工备注 */
    private Long writeTxLog(MesLot lot, String txType, String fromStatus, String toStatus,
                            Integer fromSortNo, Integer toSortNo, Long stepId, Long eqpId,
                            Long routeVersionId, String remark, Long recipeId, Long recipeVersionId,
                            String extJson) {
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
        log.setExtJson(extJson);
        log.setOperUserId(userId);
        log.setOperUserName(user != null ? user.getUserName() : null);
        log.setCreateTime(LocalDateTime.now());
        mesTxLogMapper.insert(log);
        return log.getId();
    }
}
