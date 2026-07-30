package com.mes.recipe.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 配方主数据 */
@Data
public class MesRecipeVO {
    private Long id;
    private String recipeCode;
    private String recipeName;
    private Integer enabled;
    private String remark;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
