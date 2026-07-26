package com.mes.lot.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 新建批次（状态 created，未绑版本） */
@Data
public class MesLotCreateDTO {

    @NotBlank(message = "批次号不能为空")
    private String lotNo;

    /** 产品编码（可选） */
    private String productCode;

    @NotNull(message = "数量不能为空")
    @Min(value = 0, message = "数量不能小于0")
    private Integer qty;

    /** 优先级 1–100，空则默认 50 */
    @Min(value = 1, message = "优先级范围为1-100")
    @Max(value = 100, message = "优先级范围为1-100")
    private Integer priority;

    /** 客户侧批次号（可选） */
    private String customerLot;

    /** 预填目标路线（可选，放行前可改） */
    private Long routeId;

    private String remark;
}
