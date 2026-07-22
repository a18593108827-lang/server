package com.mes.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mes.common.PageResult;
import com.mes.common.R;
import com.mes.system.dto.SysUserQuery;
import com.mes.system.service.SysUserService;
import com.mes.system.vo.SysUserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统用户接口
 */
@RestController
@RequestMapping("/system/users")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService sysUserService;

    /** 用户分页列表（需 user:list） */
    @SaCheckPermission("user:list")
    @GetMapping
    public R<PageResult<SysUserVO>> page(SysUserQuery query) {
        return R.ok(sysUserService.page(query));
    }
}
