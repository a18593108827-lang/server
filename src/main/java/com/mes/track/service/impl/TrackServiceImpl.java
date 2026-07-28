package com.mes.track.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mes.common.AssertUtil;
import com.mes.hold.service.HoldService;
import com.mes.lot.entity.MesLot;
import com.mes.lot.mapper.MesLotMapper;
import com.mes.lot.vo.MesLotStepVO;
import com.mes.route.entity.MesRoute;
import com.mes.route.entity.MesRouteStep;
import com.mes.route.entity.MesRouteVersion;
import com.mes.route.entity.MesStep;
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
import com.mes.track.vo.TrackTxnResultVO;
import com.mes.wip.service.WipProjectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
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

    private final MesLotMapper mesLotMapper;
    private final MesRouteMapper mesRouteMapper;
    private final MesRouteVersionMapper mesRouteVersionMapper;
    private final MesRouteStepMapper mesRouteStepMapper;
    private final MesStepMapper mesStepMapper;
    private final MesTxLogMapper mesTxLogMapper;
    private final SysUserMapper sysUserMapper;
    private final WipProjectionService wipProjectionService;
    private final HoldService holdService;

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
        lot.setUpdateBy(StpUtil.getLoginIdAsLong());
        int rows = mesLotMapper.updateById(lot);
        AssertUtil.isTrue(rows > 0, "数据已被他人修改，请刷新后重试");
        wipProjectionService.syncFromLot(lot);

        writeTxLog(lot, TX_RELEASE, fromStatus, STATUS_WAIT, fromSortNo, firstStep.getSortNo(),
                firstStep.getStepId(), null, active.getId(), "放行进首站");

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
        AssertUtil.isTrue(STATUS_WAIT.equals(lot.getStatus()), "仅等待加工状态可开工");
        AssertUtil.notNull(lot.getCurrentSortNo(), "当前站未知，无法开工");
        AssertUtil.notNull(lot.getRouteVersionId(), "未绑定路线版本");

        MesRouteStep current = requireCurrentStep(lot);
        String fromStatus = lot.getStatus();
        Integer fromSortNo = lot.getCurrentSortNo();

        lot.setStatus(STATUS_PROCESSING);
        lot.setCurrentEqpId(eqpId);
        lot.setUpdateBy(StpUtil.getLoginIdAsLong());
        int rows = mesLotMapper.updateById(lot);
        AssertUtil.isTrue(rows > 0, "数据已被他人修改，请刷新后重试");
        wipProjectionService.syncFromLot(lot);

        writeTxLog(lot, TX_TRACK_IN, fromStatus, STATUS_PROCESSING, fromSortNo, fromSortNo,
                current.getStepId(), eqpId, lot.getRouteVersionId(), "开工");

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
                toStepId, fromEqpId, lot.getRouteVersionId(), remark);

        return toTxnVo(lot, TX_TRACK_OUT, completed);
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
        return vo;
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

    /** 写入 Track 事务履历 */
    private void writeTxLog(MesLot lot, String txType, String fromStatus, String toStatus,
                            Integer fromSortNo, Integer toSortNo, Long stepId, Long eqpId,
                            Long routeVersionId, String remark) {
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
        log.setRouteVersionId(routeVersionId);
        log.setRemark(remark);
        log.setOperUserId(userId);
        log.setOperUserName(user != null ? user.getUserName() : null);
        log.setCreateTime(LocalDateTime.now());
        mesTxLogMapper.insert(log);
    }
}
