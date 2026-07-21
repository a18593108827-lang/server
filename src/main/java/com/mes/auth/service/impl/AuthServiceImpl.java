package com.mes.auth.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mes.auth.dto.LoginDTO;
import com.mes.auth.service.AuthService;
import com.mes.auth.vo.LoginVO;
import com.mes.auth.vo.UserInfoVO;
import com.mes.common.AssertUtil;
import com.mes.common.PasswordUtil;
import com.mes.system.entity.SysUser;
import com.mes.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 认证服务实现
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper sysUserMapper;

    @Override
    public LoginVO login(LoginDTO dto) {
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, dto.getUsername()));
        AssertUtil.notNull(user, "用户名或密码错误");
        AssertUtil.isTrue(PasswordUtil.matches(dto.getPassword(), user.getPassword()), "用户名或密码错误");
        AssertUtil.isTrue(user.getStatus() != null && user.getStatus() == 1, "账号已禁用");

        StpUtil.login(user.getId());

        LoginVO vo = new LoginVO();
        vo.setToken(StpUtil.getTokenValue());
        vo.setTokenName(StpUtil.getTokenName());
        return vo;
    }

    @Override
    public void logout() {
        StpUtil.logout();
    }

    @Override
    public UserInfoVO getInfo() {
        long userId = StpUtil.getLoginIdAsLong();
        SysUser user = sysUserMapper.selectById(userId);
        AssertUtil.notNull(user, "用户不存在");

        UserInfoVO vo = new UserInfoVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        return vo;
    }
}
