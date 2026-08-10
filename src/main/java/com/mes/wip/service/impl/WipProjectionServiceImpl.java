package com.mes.wip.service.impl;

import com.mes.lot.entity.MesLot;
import com.mes.wip.entity.MesWipLot;
import com.mes.wip.mapper.MesWipLotMapper;
import com.mes.wip.service.WipProjectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class WipProjectionServiceImpl implements WipProjectionService {

    public static final String STATUS_WAIT = "wait";
    public static final String STATUS_PROCESSING = "processing";
    public static final String STATUS_HELD = "held";

    private static final Set<String> WIP_STATUSES = Set.of(STATUS_WAIT, STATUS_PROCESSING, STATUS_HELD);

    private final MesWipLotMapper mesWipLotMapper;

    @Override
    public void syncFromLot(MesLot lot) {
        if (lot == null || lot.getId() == null) {
            return;
        }
        if (!WIP_STATUSES.contains(lot.getStatus())) {
            remove(lot.getId());
            return;
        }
        MesWipLot row = toRow(lot);
        MesWipLot existing = mesWipLotMapper.selectById(lot.getId());
        if (existing == null) {
            mesWipLotMapper.insert(row);
        } else {
            mesWipLotMapper.updateById(row);
        }
    }

    @Override
    public void remove(Long lotId) {
        if (lotId != null) {
            mesWipLotMapper.deleteById(lotId);
        }
    }

    private static MesWipLot toRow(MesLot lot) {
        MesWipLot row = new MesWipLot();
        row.setLotId(lot.getId());
        row.setLotNo(lot.getLotNo());
        row.setProductCode(lot.getProductCode());
        row.setQty(lot.getQty());
        row.setPriority(lot.getPriority());
        row.setHotFlag(lot.getHotFlag() == null ? 0 : lot.getHotFlag());
        row.setCustomerLot(lot.getCustomerLot());
        row.setStatus(lot.getStatus());
        row.setCurrentSortNo(lot.getCurrentSortNo());
        row.setCurrentStepId(lot.getCurrentStepId());
        row.setCurrentEqpId(lot.getCurrentEqpId());
        row.setRouteId(lot.getRouteId());
        row.setRouteVersionId(lot.getRouteVersionId());
        row.setUpdateTime(lot.getUpdateTime() != null ? lot.getUpdateTime() : LocalDateTime.now());
        return row;
    }
}
