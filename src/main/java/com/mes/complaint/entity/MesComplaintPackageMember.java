package com.mes.complaint.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 客诉追溯包成员快照 */
@Data
@TableName("mes_complaint_package_member")
public class MesComplaintPackageMember {

    public static final String REL_ANCHOR = "ANCHOR";
    public static final String REL_ANCESTOR = "ANCESTOR";
    public static final String REL_DESCENDANT = "DESCENDANT";

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long packageId;
    private Long lotId;
    private String lotNo;
    private String relation;
    private Integer depthFromAnchor;
    private Integer qtySnapshot;
    private String statusSnapshot;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
