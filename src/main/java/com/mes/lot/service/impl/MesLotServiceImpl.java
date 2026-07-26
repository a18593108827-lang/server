package com.mes.lot.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mes.common.AssertUtil;
import com.mes.common.PageResult;
import com.mes.lot.dto.MesLotCreateDTO;
import com.mes.lot.dto.MesLotQuery;
import com.mes.lot.dto.MesLotUpdateDTO;
import com.mes.lot.entity.MesLot;
import com.mes.lot.mapper.MesLotMapper;
import com.mes.lot.service.MesLotService;
import com.mes.lot.vo.MesLotStepVO;
import com.mes.lot.vo.MesLotVO;
import com.mes.route.entity.MesRoute;
import com.mes.route.entity.MesRouteStep;
import com.mes.route.entity.MesRouteVersion;
import com.mes.route.entity.MesStep;
import com.mes.route.mapper.MesRouteMapper;
import com.mes.route.mapper.MesRouteStepMapper;
import com.mes.route.mapper.MesRouteVersionMapper;
import com.mes.route.mapper.MesStepMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 批次服务：创建 / 改属性 / Release 绑 active 版本快照
 */
@Service
@RequiredArgsConstructor
public class MesLotServiceImpl implements MesLotService {

    /** 已创建（未放行） */
    public static final String STATUS_CREATED = "created";
    /** 已放行 */
    public static final String STATUS_RELEASED = "released";
    /** Route 生效版本状态 */
    public static final String ROUTE_ACTIVE = "active";

    private final MesLotMapper mesLotMapper;
    private final MesRouteMapper mesRouteMapper;
    private final MesRouteVersionMapper mesRouteVersionMapper;
    private final MesRouteStepMapper mesRouteStepMapper;
    private final MesStepMapper mesStepMapper;

    @Override
    public PageResult<MesLotVO> page(MesLotQuery query) {
        long pageNo = query.getPage() <= 0 ? 1 : query.getPage();
        long pageSize = query.getSize() <= 0 ? 20 : query.getSize();

        Page<MesLot> page = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<MesLot> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            qw.and(w -> w.like(MesLot::getLotNo, keyword)
                    .or().like(MesLot::getProductCode, keyword)
                    .or().like(MesLot::getCustomerLot, keyword));
        }
        if (StringUtils.hasText(query.getStatus())) {
            qw.eq(MesLot::getStatus, query.getStatus().trim());
        }
        qw.orderByDesc(MesLot::getCreateTime);

        Page<MesLot> result = mesLotMapper.selectPage(page, qw);
        List<MesLot> lots = result.getRecords();
        if (lots.isEmpty()) {
            return PageResult.of(Collections.emptyList(), result.getTotal(), pageNo, pageSize);
        }

        // 批量补路线 / 版本摘要，避免 N+1
        Map<Long, MesRoute> routeMap = loadRoutes(lots.stream().map(MesLot::getRouteId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<Long, MesRouteVersion> versionMap = loadVersions(lots.stream().map(MesLot::getRouteVersionId).filter(Objects::nonNull).collect(Collectors.toSet()));

        List<MesLotVO> records = new ArrayList<>(lots.size());
        for (MesLot lot : lots) {
            records.add(toVo(lot, routeMap.get(lot.getRouteId()), versionMap.get(lot.getRouteVersionId()), null));
        }
        return PageResult.of(records, result.getTotal(), pageNo, pageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(MesLotCreateDTO dto) {
        String lotNo = dto.getLotNo().trim();
        Long count = mesLotMapper.selectCount(new LambdaQueryWrapper<MesLot>().eq(MesLot::getLotNo, lotNo));
        AssertUtil.isTrue(count == 0, "批次号已存在");

        if (dto.getRouteId() != null) {
            assertRouteUsable(dto.getRouteId());
        }

        int priority = dto.getPriority() == null ? 50 : dto.getPriority();
        AssertUtil.isTrue(priority >= 1 && priority <= 100, "优先级范围为1-100");

        long userId = StpUtil.getLoginIdAsLong();
        MesLot lot = new MesLot();
        lot.setLotNo(lotNo);
        lot.setProductCode(blankToNull(dto.getProductCode()));
        lot.setQty(dto.getQty());
        lot.setPriority(priority);
        lot.setCustomerLot(blankToNull(dto.getCustomerLot()));
        lot.setRouteId(dto.getRouteId());
        lot.setRouteVersionId(null);
        lot.setStatus(STATUS_CREATED);
        lot.setRemark(blankToNull(dto.getRemark()));
        lot.setVersion(0);
        lot.setCreateBy(userId);
        lot.setUpdateBy(userId);
        mesLotMapper.insert(lot);
        return lot.getId();
    }

    @Override
    public MesLotVO get(Long id) {
        MesLot lot = mesLotMapper.selectById(id);
        AssertUtil.notNull(lot, "批次不存在");

        MesRoute route = lot.getRouteId() == null ? null : mesRouteMapper.selectById(lot.getRouteId());
        MesRouteVersion version = lot.getRouteVersionId() == null ? null : mesRouteVersionMapper.selectById(lot.getRouteVersionId());
        // 已放行：带出快照步骤供 UI / 后续 Track 参考
        List<MesLotStepVO> steps = null;
        if (lot.getRouteVersionId() != null) {
            steps = loadSteps(lot.getRouteVersionId());
        }
        return toVo(lot, route, version, steps);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, MesLotUpdateDTO dto) {
        MesLot lot = mesLotMapper.selectById(id);
        AssertUtil.notNull(lot, "批次不存在");
        AssertUtil.isTrue(STATUS_CREATED.equals(lot.getStatus()) || STATUS_RELEASED.equals(lot.getStatus()),
                "当前状态不可编辑");

        if (STATUS_CREATED.equals(lot.getStatus())) {
            if (dto.getRouteId() != null) {
                assertRouteUsable(dto.getRouteId());
            }
            lot.setRouteId(dto.getRouteId());
        } else {
            // released：禁止改路线（route_version_id 始终不可通过本接口改）
            AssertUtil.isFalse(dto.getRouteId() != null && !Objects.equals(dto.getRouteId(), lot.getRouteId()),
                    "已放行不可修改路线");
        }

        lot.setProductCode(blankToNull(dto.getProductCode()));
        lot.setQty(dto.getQty());
        lot.setPriority(dto.getPriority());
        lot.setCustomerLot(blankToNull(dto.getCustomerLot()));
        lot.setRemark(blankToNull(dto.getRemark()));
        lot.setUpdateBy(StpUtil.getLoginIdAsLong());
        int rows = mesLotMapper.updateById(lot);
        AssertUtil.isTrue(rows > 0, "数据已被他人修改，请刷新后重试");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void release(Long id) {
        MesLot lot = mesLotMapper.selectById(id);
        AssertUtil.notNull(lot, "批次不存在");
        AssertUtil.isTrue(STATUS_CREATED.equals(lot.getStatus()), "仅未放行批次可放行");
        AssertUtil.notNull(lot.getRouteId(), "请先指定工艺路线");

        MesRoute route = assertRouteUsable(lot.getRouteId());
        // 只认当时 active；之后 Route 升版不影响本 Lot
        MesRouteVersion active = mesRouteVersionMapper.selectOne(new LambdaQueryWrapper<MesRouteVersion>()
                .eq(MesRouteVersion::getRouteId, route.getId())
                .eq(MesRouteVersion::getStatus, ROUTE_ACTIVE)
                .last("LIMIT 1"));
        AssertUtil.notNull(active, "路线无生效版本，无法放行");

        Long stepCount = mesRouteStepMapper.selectCount(new LambdaQueryWrapper<MesRouteStep>()
                .eq(MesRouteStep::getVersionId, active.getId()));
        AssertUtil.isTrue(stepCount != null && stepCount > 0, "生效版本无步骤，无法放行");

        lot.setRouteVersionId(active.getId());
        lot.setStatus(STATUS_RELEASED);
        lot.setUpdateBy(StpUtil.getLoginIdAsLong());
        int rows = mesLotMapper.updateById(lot);
        AssertUtil.isTrue(rows > 0, "数据已被他人修改，请刷新后重试");
    }

    /** 路线存在且未停用 */
    private MesRoute assertRouteUsable(Long routeId) {
        MesRoute route = mesRouteMapper.selectById(routeId);
        AssertUtil.notNull(route, "路线不存在");
        AssertUtil.isTrue(route.getStatus() != null && route.getStatus() == 1, "路线已停用");
        return route;
    }

    private Map<Long, MesRoute> loadRoutes(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return mesRouteMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(MesRoute::getId, r -> r, (a, b) -> a));
    }

    private Map<Long, MesRouteVersion> loadVersions(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return mesRouteVersionMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(MesRouteVersion::getId, v -> v, (a, b) -> a));
    }

    /** 按版本加载有序步骤 + 工序编码名称 */
    private List<MesLotStepVO> loadSteps(Long versionId) {
        List<MesRouteStep> rows = mesRouteStepMapper.selectList(new LambdaQueryWrapper<MesRouteStep>()
                .eq(MesRouteStep::getVersionId, versionId)
                .orderByAsc(MesRouteStep::getSortNo));
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> stepIds = rows.stream().map(MesRouteStep::getStepId).collect(Collectors.toSet());
        Map<Long, MesStep> stepMap = mesStepMapper.selectBatchIds(stepIds).stream()
                .collect(Collectors.toMap(MesStep::getId, s -> s, (a, b) -> a));
        List<MesLotStepVO> list = new ArrayList<>(rows.size());
        for (MesRouteStep row : rows) {
            MesLotStepVO vo = new MesLotStepVO();
            vo.setStepId(row.getStepId());
            vo.setSortNo(row.getSortNo());
            vo.setNextSortNo(row.getNextSortNo());
            MesStep step = stepMap.get(row.getStepId());
            if (step != null) {
                vo.setStepCode(step.getStepCode());
                vo.setStepName(step.getStepName());
            }
            list.add(vo);
        }
        return list;
    }

    private MesLotVO toVo(MesLot lot, MesRoute route, MesRouteVersion version, List<MesLotStepVO> steps) {
        MesLotVO vo = new MesLotVO();
        vo.setId(lot.getId());
        vo.setLotNo(lot.getLotNo());
        vo.setProductCode(lot.getProductCode());
        vo.setQty(lot.getQty());
        vo.setPriority(lot.getPriority());
        vo.setCustomerLot(lot.getCustomerLot());
        vo.setRouteId(lot.getRouteId());
        vo.setRouteVersionId(lot.getRouteVersionId());
        vo.setStatus(lot.getStatus());
        vo.setRemark(lot.getRemark());
        vo.setVersion(lot.getVersion());
        vo.setCreateTime(lot.getCreateTime());
        vo.setUpdateTime(lot.getUpdateTime());
        if (route != null) {
            vo.setRouteCode(route.getRouteCode());
            vo.setRouteName(route.getRouteName());
        }
        if (version != null) {
            vo.setRouteVersionNo(version.getVersionNo());
        }
        vo.setSteps(steps);
        return vo;
    }

    private static String blankToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
