package com.mes.carrier.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mes_carrier_binding")
public class MesCarrierBinding {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long carrierId;
    private Long lotId;
    private LocalDateTime bindTime;
    private Long bindBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
