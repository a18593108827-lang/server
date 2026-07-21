package com.mes.auth.vo;

import lombok.Data;

/**
 * 当前登录用户信息
 */
@Data
public class UserInfoVO {

    /** 用户ID */
    private Long id;

    /** 用户编码 */
    private String userCode;

    /** 姓名 */
    private String userName;
}
