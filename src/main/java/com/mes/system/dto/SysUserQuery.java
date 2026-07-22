package com.mes.system.dto;

import lombok.Data;

/**
 * 用户分页查询
 */
@Data
public class SysUserQuery {

    /** 用户编码（模糊） */
    private String userCode;

    /** 姓名（模糊） */
    private String userName;

    /** 状态：1正常 0禁用 */
    private Integer status;

    /** 页码，默认 1 */
    private long page = 1;

    /** 每页条数，默认 10 */
    private long size = 10;
}
