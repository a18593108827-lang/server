package com.mes.alarm.service.impl;

import com.mes.hold.dto.MesHoldCreateDTO;
import com.mes.hold.service.HoldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alarm → Hold：独立事务挂锁，失败只回滚本事务，不连带 mes_alarm OPEN。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlarmHoldOnRaiseExecutor {

    private final HoldService holdService;

    /**
     * 已 active 则跳过；否则 create。异常抛给调用方 catch，本事务已回滚。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void holdLot(Long lotId, String reasonCode, String remark) {
        if (holdService.hasActive(lotId)) {
            log.info("[ALARM] HOLD_LOT 跳过：批次已有生效锁批 lotId={}", lotId);
            return;
        }
        MesHoldCreateDTO dto = new MesHoldCreateDTO();
        dto.setLotId(lotId);
        dto.setReasonCode(reasonCode);
        dto.setRemark(remark);
        holdService.create(dto);
    }
}
