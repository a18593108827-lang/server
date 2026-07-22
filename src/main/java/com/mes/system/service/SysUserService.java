package com.mes.system.service;

import com.mes.common.PageResult;
import com.mes.system.dto.SysUserQuery;
import com.mes.system.vo.SysUserVO;

/**
 * 系统用户服务
 */
public interface SysUserService {

    /** 分页查询用户 */
    PageResult<SysUserVO> page(SysUserQuery query);
}
