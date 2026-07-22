package com.mes.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mes.common.PageResult;
import com.mes.system.dto.SysUserQuery;
import com.mes.system.dto.UserRoleCodeRow;
import com.mes.system.entity.SysUser;
import com.mes.system.mapper.SysPermissionMapper;
import com.mes.system.mapper.SysUserMapper;
import com.mes.system.service.SysUserService;
import com.mes.system.vo.SysUserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 系统用户服务实现
 */
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl implements SysUserService {

    private final SysUserMapper sysUserMapper;
    private final SysPermissionMapper sysPermissionMapper;

    @Override
    public PageResult<SysUserVO> page(SysUserQuery query) {
        long pageNo = query.getPage() <= 0 ? 1 : query.getPage();
        long pageSize = query.getSize() <= 0 ? 10 : query.getSize();

        Page<SysUser> page = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<SysUser> qw = new LambdaQueryWrapper<>();
        qw.select(
                SysUser::getId,
                SysUser::getUserCode,
                SysUser::getUserName,
                SysUser::getStatus,
                SysUser::getMustChangePwd,
                SysUser::getCreateTime,
                SysUser::getUpdateTime
        );
        if (StringUtils.hasText(query.getUserCode())) {
            qw.like(SysUser::getUserCode, query.getUserCode().trim());
        }
        if (StringUtils.hasText(query.getUserName())) {
            qw.like(SysUser::getUserName, query.getUserName().trim());
        }
        if (query.getStatus() != null) {
            qw.eq(SysUser::getStatus, query.getStatus());
        }
        qw.orderByDesc(SysUser::getUpdateTime);

        Page<SysUser> result = sysUserMapper.selectPage(page, qw);
        List<SysUser> users = result.getRecords();
        if (users.isEmpty()) {
            return PageResult.of(Collections.emptyList(), result.getTotal(), pageNo, pageSize);
        }

        List<Long> userIds = users.stream().map(SysUser::getId).toList();
        Map<Long, List<String>> roleMap = sysPermissionMapper.selectRoleCodesByUserIds(userIds).stream()
                .collect(Collectors.groupingBy(
                        UserRoleCodeRow::getUserId,
                        Collectors.mapping(UserRoleCodeRow::getRoleCode, Collectors.toList())
                ));

        List<SysUserVO> records = new ArrayList<>(users.size());
        for (SysUser user : users) {
            SysUserVO vo = new SysUserVO();
            vo.setId(user.getId());
            vo.setUserCode(user.getUserCode());
            vo.setUserName(user.getUserName());
            vo.setStatus(user.getStatus());
            vo.setMustChangePwd(user.getMustChangePwd());
            vo.setRoles(roleMap.getOrDefault(user.getId(), Collections.emptyList()));
            vo.setCreateTime(user.getCreateTime());
            vo.setUpdateTime(user.getUpdateTime());
            records.add(vo);
        }
        return PageResult.of(records, result.getTotal(), pageNo, pageSize);
    }
}
