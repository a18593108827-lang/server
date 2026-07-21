package com.mes.auth.vo;

import lombok.Data;

/**
 * 当前登录用户信息
 */
@Data
public class UserInfoVO {

    /** 用户ID */
    private Long id;

    /** 工号 */
    private String username;

    /** 昵称 */
    private String nickname;
}
