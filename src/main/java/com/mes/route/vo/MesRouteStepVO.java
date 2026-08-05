package com.mes.route.vo;

import lombok.Data;

/** 版本内步骤（含工序展示字段） */
@Data
public class MesRouteStepVO {
    private Long id;
    private Long stepId;
    private String stepCode;
    private String stepName;
    private Integer stepType;
    private String eqpType;
    private Integer allowSkip;
    private Integer maxQueueMin;
    private Integer sortNo;
    /** 下一站顺序号；null 表示结束 */
    private Integer nextSortNo;
}
