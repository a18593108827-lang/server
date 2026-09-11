package com.mes.carrier.facade.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mes.carrier.dto.CarrierCreateDTO;
import com.mes.carrier.dto.CarrierQuery;
import com.mes.carrier.dto.CarrierUpdateDTO;
import com.mes.carrier.entity.MesCarrier;
import com.mes.carrier.entity.MesCarrierBinding;
import com.mes.carrier.facade.CarrierFacade;
import com.mes.carrier.mapper.MesCarrierBindingMapper;
import com.mes.carrier.mapper.MesCarrierMapper;
import com.mes.carrier.vo.CarrierVO;
import com.mes.common.AssertUtil;
import com.mes.common.BusinessException;
import com.mes.common.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CarrierFacadeImpl implements CarrierFacade {

    /** 载具不存在 */
    public static final String ERR_NOT_FOUND = "CARRIER_NOT_FOUND";
    /** 状态不合法 / 迁态不允许 */
    public static final String ERR_STATUS = "CARRIER_STATUS_INVALID";
    /** 模块总开关关闭 */
    public static final String ERR_DISABLED = "CARRIER_DISABLED";
    /** 乐观锁冲突 */
    public static final String ERR_CONCURRENT = "CARRIER_CONCURRENT_MOD";
    /** 载具编码重复 */
    public static final String ERR_CODE_DUP = "CARRIER_CODE_DUP";

    /** 台账合法状态集合 */
    private static final Set<String> ALL_STATUSES = Set.of(
            MesCarrier.STATUS_AVAILABLE,
            MesCarrier.STATUS_IN_USE,
            MesCarrier.STATUS_QUARANTINE,
            MesCarrier.STATUS_SCRAPPED);

    /** 洁净状态合法集合 */
    private static final Set<String> CLEAN_STATUSES = Set.of(
            MesCarrier.CLEAN_UNKNOWN,
            MesCarrier.CLEAN_CLEAN,
            MesCarrier.CLEAN_DIRTY);

    private final MesCarrierMapper carrierMapper;
    private final MesCarrierBindingMapper bindingMapper;

    @Value("${mes.carrier.enabled:true}")
    private boolean carrierEnabled;

    /** 新建载具台账，初始 AVAILABLE */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CarrierVO create(CarrierCreateDTO dto) {
        assertEnabled();
        String code = dto.getCarrierCode().trim();
        Long exists = carrierMapper.selectCount(new LambdaQueryWrapper<MesCarrier>()
                .eq(MesCarrier::getCarrierCode, code));
        AssertUtil.isTrue(exists == 0, ERR_CODE_DUP + ": 载具编码已存在");

        long userId = StpUtil.getLoginIdAsLong();
        MesCarrier row = new MesCarrier();
        row.setCarrierCode(code);
        row.setCarrierType(StringUtils.hasText(dto.getCarrierType())
                ? dto.getCarrierType().trim() : MesCarrier.TYPE_FOUP);
        row.setCapacity(normalizeCapacity(dto.getCapacity()));
        row.setStatus(MesCarrier.STATUS_AVAILABLE);
        row.setCleanStatus(normalizeClean(dto.getCleanStatus()));
        row.setLocationType(StringUtils.hasText(dto.getLocationType())
                ? dto.getLocationType().trim() : MesCarrier.LOC_NONE);
        row.setLocationRef(blankToNull(dto.getLocationRef()));
        row.setRemark(blankToNull(dto.getRemark()));
        row.setCreateBy(userId);
        row.setUpdateBy(userId);
        try {
            carrierMapper.insert(row);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ERR_CODE_DUP + ": 载具编码已存在");
        }
        return toVo(row);
    }

    /** 更新台账字段（不含编码、状态、绑定） */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CarrierVO update(Long id, CarrierUpdateDTO dto) {
        assertEnabled();
        MesCarrier row = require(id);
        if (dto.getCapacity() != null) {
            row.setCapacity(normalizeCapacity(dto.getCapacity()));
        }
        if (dto.getCleanStatus() != null) {
            row.setCleanStatus(normalizeClean(dto.getCleanStatus()));
        }
        if (dto.getLocationType() != null) {
            row.setLocationType(StringUtils.hasText(dto.getLocationType())
                    ? dto.getLocationType().trim() : MesCarrier.LOC_NONE);
        }
        if (dto.getLocationRef() != null) {
            row.setLocationRef(blankToNull(dto.getLocationRef()));
        }
        if (dto.getRemark() != null) {
            row.setRemark(blankToNull(dto.getRemark()));
        }
        row.setUpdateBy(StpUtil.getLoginIdAsLong());
        int n = carrierMapper.updateById(row);
        AssertUtil.isTrue(n > 0, ERR_CONCURRENT + ": 数据已被他人修改，请刷新后重试");
        return toVo(carrierMapper.selectById(id));
    }

    /** 台账改态；禁手改 IN_USE；隔离/报废前须无绑定 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CarrierVO changeStatus(Long id, String toStatus, String remark) {
        assertEnabled();
        AssertUtil.notBlank(toStatus, ERR_STATUS + ": 目标状态不能为空");
        String next = toStatus.trim();
        AssertUtil.isTrue(ALL_STATUSES.contains(next), ERR_STATUS + ": 状态不合法");
        AssertUtil.isTrue(!MesCarrier.STATUS_IN_USE.equals(next),
                ERR_STATUS + ": IN_USE 仅能由绑定产生，禁止手改");

        MesCarrier row = require(id);
        String from = row.getStatus();
        if (Objects.equals(from, next)) {
            return toVo(row);
        }
        AssertUtil.isTrue(!MesCarrier.STATUS_SCRAPPED.equals(from),
                ERR_STATUS + ": 已报废不可再改态");

        if (MesCarrier.STATUS_QUARANTINE.equals(next) || MesCarrier.STATUS_SCRAPPED.equals(next)) {
            assertNoBinding(id);
        }

        if (MesCarrier.STATUS_IN_USE.equals(from)) {
            throw new BusinessException(ERR_STATUS + ": 使用中请先解绑再改态");
        }

        boolean ok = switch (from) {
            case MesCarrier.STATUS_AVAILABLE ->
                    MesCarrier.STATUS_QUARANTINE.equals(next) || MesCarrier.STATUS_SCRAPPED.equals(next);
            case MesCarrier.STATUS_QUARANTINE ->
                    MesCarrier.STATUS_AVAILABLE.equals(next) || MesCarrier.STATUS_SCRAPPED.equals(next);
            default -> false;
        };
        AssertUtil.isTrue(ok, ERR_STATUS + ": 不允许 " + from + " → " + next);

        row.setStatus(next);
        if (remark != null) {
            row.setRemark(blankToNull(remark));
        }
        row.setUpdateBy(StpUtil.getLoginIdAsLong());
        int n = carrierMapper.updateById(row);
        AssertUtil.isTrue(n > 0, ERR_CONCURRENT + ": 数据已被他人修改，请刷新后重试");
        return toVo(carrierMapper.selectById(id));
    }

    /** 按 id 查载具 */
    @Override
    public CarrierVO get(Long id) {
        return toVo(require(id));
    }

    /** 按编码查载具 */
    @Override
    public CarrierVO getByCode(String carrierCode) {
        AssertUtil.notBlank(carrierCode, ERR_NOT_FOUND + ": 载具编码不能为空");
        MesCarrier row = carrierMapper.selectOne(new LambdaQueryWrapper<MesCarrier>()
                .eq(MesCarrier::getCarrierCode, carrierCode.trim()));
        AssertUtil.notNull(row, ERR_NOT_FOUND + ": 载具不存在");
        return toVo(row);
    }

    /** 分页列表 */
    @Override
    public PageResult<CarrierVO> list(CarrierQuery query) {
        long pageNo = query.getPage() <= 0 ? 1 : query.getPage();
        long pageSize = query.getSize() <= 0 ? 20 : query.getSize();

        LambdaQueryWrapper<MesCarrier> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            String kw = query.getKeyword().trim();
            qw.and(w -> w.like(MesCarrier::getCarrierCode, kw)
                    .or().like(MesCarrier::getRemark, kw));
        }
        if (StringUtils.hasText(query.getStatus())) {
            qw.eq(MesCarrier::getStatus, query.getStatus().trim());
        }
        if (StringUtils.hasText(query.getCarrierType())) {
            qw.eq(MesCarrier::getCarrierType, query.getCarrierType().trim());
        }
        qw.orderByAsc(MesCarrier::getCarrierCode);

        Page<MesCarrier> result = carrierMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        List<CarrierVO> records = new ArrayList<>(result.getRecords().size());
        for (MesCarrier row : result.getRecords()) {
            records.add(toVo(row));
        }
        return PageResult.of(records, result.getTotal(), pageNo, pageSize);
    }

    /** 模块总开关 */
    private void assertEnabled() {
        AssertUtil.isTrue(carrierEnabled, ERR_DISABLED + ": 载具模块已关闭");
    }

    /** 按 id 取实体，不存在则抛错 */
    private MesCarrier require(Long id) {
        AssertUtil.notNull(id, ERR_NOT_FOUND + ": 载具不存在");
        MesCarrier row = carrierMapper.selectById(id);
        AssertUtil.notNull(row, ERR_NOT_FOUND + ": 载具不存在");
        return row;
    }

    /** 隔离/报废前确认无 Lot 绑定 */
    private void assertNoBinding(Long carrierId) {
        Long cnt = bindingMapper.selectCount(new LambdaQueryWrapper<MesCarrierBinding>()
                .eq(MesCarrierBinding::getCarrierId, carrierId));
        AssertUtil.isTrue(cnt == 0, ERR_STATUS + ": 仍有绑定，请先解绑");
    }

    /** 容量默认 25，须 > 0 */
    private static int normalizeCapacity(Integer capacity) {
        int c = capacity == null ? 25 : capacity;
        AssertUtil.isTrue(c > 0, "容量必须大于0");
        return c;
    }

    /** 洁净状态默认 UNKNOWN */
    private static String normalizeClean(String cleanStatus) {
        if (!StringUtils.hasText(cleanStatus)) {
            return MesCarrier.CLEAN_UNKNOWN;
        }
        String v = cleanStatus.trim();
        AssertUtil.isTrue(CLEAN_STATUSES.contains(v), "洁净状态不合法");
        return v;
    }

    /** 空白串转 null */
    private static String blankToNull(String v) {
        if (!StringUtils.hasText(v)) {
            return null;
        }
        return v.trim();
    }

    /** 实体转 VO */
    private static CarrierVO toVo(MesCarrier row) {
        CarrierVO vo = new CarrierVO();
        vo.setId(row.getId());
        vo.setCarrierCode(row.getCarrierCode());
        vo.setCarrierType(row.getCarrierType());
        vo.setCapacity(row.getCapacity());
        vo.setStatus(row.getStatus());
        vo.setCleanStatus(row.getCleanStatus());
        vo.setLocationType(row.getLocationType());
        vo.setLocationRef(row.getLocationRef());
        vo.setRemark(row.getRemark());
        vo.setVersion(row.getVersion());
        vo.setCreateTime(row.getCreateTime());
        vo.setUpdateTime(row.getUpdateTime());
        return vo;
    }
}
