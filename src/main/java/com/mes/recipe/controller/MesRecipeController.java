package com.mes.recipe.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mes.common.PageResult;
import com.mes.common.R;
import com.mes.common.annotation.OperLog;
import com.mes.recipe.dto.MesRecipeCreateDTO;
import com.mes.recipe.dto.MesRecipeEnabledDTO;
import com.mes.recipe.dto.MesRecipeQuery;
import com.mes.recipe.dto.MesRecipeUpdateDTO;
import com.mes.recipe.service.MesRecipeService;
import com.mes.recipe.vo.MesRecipeVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 配方：主数据 CRUD / 启停
 */
@RestController
@RequestMapping("/recipes")
@RequiredArgsConstructor
public class MesRecipeController {

    private final MesRecipeService mesRecipeService;

    /** 分页列表 */
    @SaCheckPermission("recipe:view")
    @GetMapping
    public R<PageResult<MesRecipeVO>> page(MesRecipeQuery query) {
        return R.ok(mesRecipeService.page(query));
    }

    /** 详情 */
    @SaCheckPermission("recipe:view")
    @GetMapping("/{id}")
    public R<MesRecipeVO> get(@PathVariable Long id) {
        return R.ok(mesRecipeService.get(id));
    }

    /** 新建（默认启用；recipeCode 唯一） */
    @SaCheckPermission("recipe:edit")
    @OperLog(module = "Recipe", action = "新建配方")
    @PostMapping
    public R<MesRecipeVO> create(@Valid @RequestBody MesRecipeCreateDTO dto) {
        return R.ok(mesRecipeService.create(dto));
    }

    /** 改主数据（不含编码） */
    @SaCheckPermission("recipe:edit")
    @OperLog(module = "Recipe", action = "编辑配方")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody MesRecipeUpdateDTO dto) {
        mesRecipeService.update(id, dto);
        return R.ok();
    }

    /** 启停 */
    @SaCheckPermission("recipe:edit")
    @OperLog(module = "Recipe", action = "配方启停")
    @PutMapping("/{id}/enabled")
    public R<Void> updateEnabled(@PathVariable Long id, @Valid @RequestBody MesRecipeEnabledDTO dto) {
        mesRecipeService.updateEnabled(id, dto.getEnabled());
        return R.ok();
    }
}
