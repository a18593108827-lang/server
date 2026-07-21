package com.mes.auth.service;

import com.mes.auth.dto.LoginDTO;
import com.mes.auth.vo.LoginVO;
import com.mes.auth.vo.UserInfoVO;

/**
 * 认证服务
 */
public interface AuthService {

    /** 登录 */
    LoginVO login(LoginDTO dto);

    /** 退出 */
    void logout();

    /** 当前用户信息 */
    UserInfoVO getInfo();
}
