package com.mes.track.vo;

import lombok.Data;

/** TrackIn / TrackOut 结果 */
@Data
public class TrackTxnResultVO {
    private Long lotId;
    private String lotNo;
    private String txType;
    private String status;
    private Integer currentSortNo;
    private Long currentStepId;
    private Long currentEqpId;
    private Long routeVersionId;
    /** 是否已完工 */
    private Boolean completed;
    /** Rework 后当前触发站累计次数 */
    private Integer reworkCount;
    private Integer maxReworkCount;
}
