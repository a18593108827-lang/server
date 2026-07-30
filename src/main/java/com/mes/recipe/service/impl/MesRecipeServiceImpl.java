package com.mes.recipe.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mes.common.AssertUtil;
import com.mes.common.PageResult;
import com.mes.recipe.dto.MesRecipeCreateDTO;
import com.mes.recipe.dto.MesRecipeQuery;
import com.mes.recipe.dto.MesRecipeUpdateDTO;
import com.mes.recipe.entity.MesRecipe;
import com.mes.recipe.mapper.MesRecipeMapper;
import com.mes.recipe.service.MesRecipeService;
import com.mes.recipe.vo.MesRecipeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/** 配方主数据 CRUD / 启停 */
@Service
@RequiredArgsConstructor
public class MesRecipeServiceImpl implements MesRecipeService {

    public static final int ENABLED = 1;
    public static final int DISABLED = 0;

    private final MesRecipeMapper mesRecipeMapper;

    @Override
    public PageResult<MesRecipeVO> page(MesRecipeQuery query) {
        long pageNo = query.getPage() <= 0 ? 1 : query.getPage();
        long pageSize = query.getSize() <= 0 ? 20 : query.getSize();

        LambdaQueryWrapper<MesRecipe> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            String kw = query.getKeyword().trim();
            qw.and(w -> w.like(MesRecipe::getRecipeCode, kw).or().like(MesRecipe::getRecipeName, kw));
        }
        if (query.getEnabled() != null) {
            qw.eq(MesRecipe::getEnabled, query.getEnabled());
        }
        qw.orderByAsc(MesRecipe::getRecipeCode);

        Page<MesRecipe> result = mesRecipeMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        List<MesRecipeVO> records = new ArrayList<>(result.getRecords().size());
        for (MesRecipe row : result.getRecords()) {
            records.add(toVo(row));
        }
        return PageResult.of(records, result.getTotal(), pageNo, pageSize);
    }

    @Override
    public MesRecipeVO get(Long id) {
        MesRecipe row = mesRecipeMapper.selectById(id);
        AssertUtil.notNull(row, "配方不存在");
        return toVo(row);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesRecipeVO create(MesRecipeCreateDTO dto) {
        String code = dto.getRecipeCode().trim();
        Long count = mesRecipeMapper.selectCount(new LambdaQueryWrapper<MesRecipe>().eq(MesRecipe::getRecipeCode, code));
        AssertUtil.isTrue(count == 0, "配方编码已存在");

        long userId = StpUtil.getLoginIdAsLong();
        MesRecipe row = new MesRecipe();
        row.setRecipeCode(code);
        row.setRecipeName(dto.getRecipeName().trim());
        row.setRemark(blankToNull(dto.getRemark()));
        row.setEnabled(ENABLED);
        row.setCreateBy(userId);
        row.setUpdateBy(userId);
        mesRecipeMapper.insert(row);
        return toVo(row);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, MesRecipeUpdateDTO dto) {
        MesRecipe row = mesRecipeMapper.selectById(id);
        AssertUtil.notNull(row, "配方不存在");

        row.setRecipeName(dto.getRecipeName().trim());
        row.setRemark(blankToNull(dto.getRemark()));
        row.setUpdateBy(StpUtil.getLoginIdAsLong());
        int n = mesRecipeMapper.updateById(row);
        AssertUtil.isTrue(n > 0, "数据已被他人修改，请刷新后重试");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateEnabled(Long id, Integer enabled) {
        MesRecipe row = mesRecipeMapper.selectById(id);
        AssertUtil.notNull(row, "配方不存在");
        AssertUtil.isTrue(enabled != null && (enabled == ENABLED || enabled == DISABLED), "启停只能为0或1");

        row.setEnabled(enabled);
        row.setUpdateBy(StpUtil.getLoginIdAsLong());
        int n = mesRecipeMapper.updateById(row);
        AssertUtil.isTrue(n > 0, "数据已被他人修改，请刷新后重试");
    }

    private static String blankToNull(String v) {
        if (!StringUtils.hasText(v)) {
            return null;
        }
        return v.trim();
    }

    private MesRecipeVO toVo(MesRecipe row) {
        MesRecipeVO vo = new MesRecipeVO();
        vo.setId(row.getId());
        vo.setRecipeCode(row.getRecipeCode());
        vo.setRecipeName(row.getRecipeName());
        vo.setEnabled(row.getEnabled());
        vo.setRemark(row.getRemark());
        vo.setVersion(row.getVersion());
        vo.setCreateTime(row.getCreateTime());
        vo.setUpdateTime(row.getUpdateTime());
        return vo;
    }
}
