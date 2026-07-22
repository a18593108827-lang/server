package com.mes.auth.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mes.auth.dto.LoginDTO;
import com.mes.auth.dto.RegisterDTO;
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
    public void register(RegisterDTO dto) {
        Long count = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUserCode, dto.getUserCode()));
        AssertUtil.isTrue(count == 0, "用户编码已存在");

        SysUser user = new SysUser();
        user.setUserCode(dto.getUserCode());
        user.setUserName(dto.getUserName());
        user.setPassword(PasswordUtil.encode(dto.getPassword()));
        user.setStatus(1);
        user.setMustChangePwd(0);
        user.setSource("local");
        sysUserMapper.insert(user);
    }

    @Override
    public LoginVO login(LoginDTO dto) {
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUserCode, dto.getUserCode()));
        AssertUtil.notNull(user, "用户编码或密码错误");
        AssertUtil.isTrue(PasswordUtil.matches(dto.getPassword(), user.getPassword()), "用户编码或密码错误");
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
        vo.setUserCode(user.getUserCode());
        vo.setUserName(user.getUserName());
        return vo;
    }
}
