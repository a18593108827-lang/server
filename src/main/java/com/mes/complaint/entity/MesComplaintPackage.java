package com.mes.complaint.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 客诉追溯包头（审计；履历真相仍在 tx_log） */
@Data
@TableName("mes_complaint_package")
public class MesComplaintPackage {

    public static final String STATUS_READY = "READY";
    public static final String STATUS_CONTAINING = "CONTAINING";
    public static final String STATUS_CONTAINED = "CONTAINED";
    public static final String STATUS_VOID = "VOID";

    public static final String DIR_UP = "up";
    public static final String DIR_DOWN = "down";
    public static final String DIR_BOTH = "both";

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String packageNo;
    private Long anchorLotId;
    private String anchorLotNo;
    private String direction;
    private Integer depth;
    private Integer memberCount;
    /** 0/1 */
    private Integer truncated;
    private String reasonCode;
    private String remark;
    private String status;

    private Long createBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    private Long containBy;
    private LocalDateTime containTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
