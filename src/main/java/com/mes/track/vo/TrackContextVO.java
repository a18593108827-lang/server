package com.mes.track.vo;

import com.mes.lot.vo.MesLotStepVO;
import lombok.Data;

/** 现场台执行上下文（只读） */
@Data
public class TrackContextVO {
    private Long lotId;
    private String lotNo;
    private String status;
    private Long routeId;
    private Long routeVersionId;
    private Integer routeVersionNo;
    private Integer currentSortNo;
    private Long currentStepId;
    private Long currentEqpId;
    private MesLotStepVO currentStep;
    private MesLotStepVO nextStep;
    private Boolean canTrackIn;
    private Boolean canTrackOut;
    private Boolean completed;
}
