package com.mes.lot.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mes.common.PageResult;
import com.mes.common.R;
import com.mes.common.annotation.OperLog;
import com.mes.lot.dto.MesLotCreateDTO;
import com.mes.lot.dto.MesLotQuery;
import com.mes.lot.dto.MesLotUpdateDTO;
import com.mes.lot.service.MesLotService;
import com.mes.lot.vo.MesLotCreateResultVO;
import com.mes.lot.vo.MesLotVO;
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
 * 批次接口：创建 / 改属性 / 放行绑 Route 版本快照
 */
@RestController
@RequestMapping("/lots")
@RequiredArgsConstructor
public class MesLotController {

    private final MesLotService mesLotService;

    /** 分页列表 */
    @SaCheckPermission("lot:list")
    @GetMapping
    public R<PageResult<MesLotVO>> page(MesLotQuery query) {
        return R.ok(mesLotService.page(query));
    }

    /** 新建批次（lotNo 空则自动生成） */
    @SaCheckPermission("lot:add")
    @OperLog(module = "Lot", action = "新建批次")
    @PostMapping
    public R<MesLotCreateResultVO> create(@Valid @RequestBody MesLotCreateDTO dto) {
        return R.ok(mesLotService.create(dto));
    }

    /** 详情（含路线 / 快照步骤） */
    @SaCheckPermission("lot:list")
    @GetMapping("/{id}")
    public R<MesLotVO> get(@PathVariable Long id) {
        return R.ok(mesLotService.get(id));
    }

    /** 改属性 */
    @SaCheckPermission("lot:edit")
    @OperLog(module = "Lot", action = "改批次属性")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody MesLotUpdateDTO dto) {
        mesLotService.update(id, dto);
        return R.ok();
    }

    /** 放行：写死当时 active 版本 */
    @SaCheckPermission("lot:release")
    @OperLog(module = "Lot", action = "批次放行")
    @PostMapping("/{id}/release")
    public R<Void> release(@PathVariable Long id) {
        mesLotService.release(id);
        return R.ok();
    }
}
