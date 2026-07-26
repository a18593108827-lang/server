package com.mes.lot.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 批次列表 / 详情 */
@Data
public class MesLotVO {
    private Long id;
    private String lotNo;
    private String productCode;
    private Integer qty;
    /** 优先级 1–100，越大越急 */
    private Integer priority;
    private String customerLot;
    private Long routeId;
    private String routeCode;
    private String routeName;
    /** 放行快照版本 ID */
    private Long routeVersionId;
    /** 放行快照版本号 */
    private Integer routeVersionNo;
    /** created / released / completed / scrapped */
    private String status;
    private String remark;
    /** 乐观锁版本号 */
    private Integer version;
    /** 详情时返回快照步骤；列表为 null */
    private List<MesLotStepVO> steps;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
