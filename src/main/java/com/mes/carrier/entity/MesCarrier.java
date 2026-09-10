package com.mes.carrier.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.mes.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_carrier")
public class MesCarrier extends BaseEntity {

    public static final String STATUS_AVAILABLE = "AVAILABLE";
    public static final String STATUS_IN_USE = "IN_USE";
    public static final String STATUS_QUARANTINE = "QUARANTINE";
    public static final String STATUS_SCRAPPED = "SCRAPPED";

    public static final String TYPE_FOUP = "FOUP";

    public static final String CLEAN_UNKNOWN = "UNKNOWN";
    public static final String CLEAN_CLEAN = "CLEAN";
    public static final String CLEAN_DIRTY = "DIRTY";

    public static final String LOC_NONE = "NONE";

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String carrierCode;
    private String carrierType;
    private Integer capacity;
    private String status;
    private String cleanStatus;
    private String locationType;
    private String locationRef;
    private String remark;

    @Version
    private Integer version;

    private Long createBy;
    private Long updateBy;
}
