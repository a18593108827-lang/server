package com.mes.route.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mes.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 路线版本边（normal / rework 等） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_route_edge")
public class MesRouteEdge extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long versionId;
    private Integer fromSortNo;
    private Integer toSortNo;
    /** normal / branch / rework / skip_allow */
    private String edgeType;
    private Integer maxReworkCount;
    /** 逗号分隔；空=任意 */
    private String reasonCodes;
    private Integer sortNo;
}
