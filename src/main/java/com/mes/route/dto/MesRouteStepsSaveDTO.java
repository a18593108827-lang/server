package com.mes.route.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/** 覆盖保存草稿版本步骤 */
@Data
public class MesRouteStepsSaveDTO {

    /** 步骤列表（可空数组，表示清空草稿步骤） */
    @NotNull(message = "步骤列表不能为空")
    @Valid
    private List<Item> steps;

    /** 单个步骤项 */
    @Data
    public static class Item {
        @NotNull(message = "工序不能为空")
        private Long stepId;

        @NotNull(message = "顺序号不能为空")
        private Integer sortNo;

        /** 下一站顺序号；null 表示结束 */
        private Integer nextSortNo;
    }
}
