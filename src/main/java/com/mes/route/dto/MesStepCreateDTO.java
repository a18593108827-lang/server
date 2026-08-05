package com.mes.route.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 新增工序 */
@Data
public class MesStepCreateDTO {

    @NotBlank(message = "工序编码不能为空")
    private String stepCode;

    @NotBlank(message = "工序名称不能为空")
    private String stepName;

    /** 类型：1加工 2量测 3其它 */
    @NotNull(message = "工序类型不能为空")
    private Integer stepType;

    private String eqpType;
    private Integer allowSkip;
    private Integer maxQueueMin;
    private String remark;
}
