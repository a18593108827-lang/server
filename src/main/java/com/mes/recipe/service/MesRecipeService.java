package com.mes.recipe.service;

import com.mes.common.PageResult;
import com.mes.recipe.dto.MesRecipeCreateDTO;
import com.mes.recipe.dto.MesRecipeQuery;
import com.mes.recipe.dto.MesRecipeUpdateDTO;
import com.mes.recipe.vo.MesRecipeVO;

/** 配方主数据：CRUD / 启停 */
public interface MesRecipeService {

    PageResult<MesRecipeVO> page(MesRecipeQuery query);

    MesRecipeVO get(Long id);

    MesRecipeVO create(MesRecipeCreateDTO dto);

    /** 改名称/备注；不改 recipeCode */
    void update(Long id, MesRecipeUpdateDTO dto);

    void updateEnabled(Long id, Integer enabled);
}
