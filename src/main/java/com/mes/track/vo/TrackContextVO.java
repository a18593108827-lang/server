package com.mes.track.vo;

import com.mes.hold.vo.MesFutureHoldVO;
import com.mes.lot.vo.MesLotStepVO;
import lombok.Data;

import java.util.List;

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
    private Boolean canRework;
    private Boolean canSkip;
    private Boolean canEnterOffFlow;
    private Boolean canResumeOffFlow;
    private Boolean offFlow;
    private Integer offFlowAnchorSortNo;
    private Integer reworkCount;
    private List<TrackReworkOptionVO> reworkOptions;
    private List<TrackBranchOptionVO> branchOptions;
    private List<TrackSkipOptionVO> skipOptions;
    private List<TrackOffFlowOptionVO> offFlowOptions;
    private Boolean completed;
    /** 未生效预约锁批（pending） */
    private List<MesFutureHoldVO> pendingFutureHolds;
}
