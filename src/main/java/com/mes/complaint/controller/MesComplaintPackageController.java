package com.mes.complaint.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mes.common.R;
import com.mes.complaint.facade.ComplaintPackageFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客诉追溯包 HTTP。CP-1 仅暴露开关探测；preview/build/export/contain 见后续切片。
 */
@RestController
@RequestMapping("/complaint-packages")
@RequiredArgsConstructor
public class MesComplaintPackageController {

    private final ComplaintPackageFacade complaintPackageFacade;

    /** 前端/联调用：是否开启（不因 false 抛错） */
    @SaCheckPermission("complaint:view")
    @GetMapping("/enabled")
    public R<Boolean> enabled() {
        return R.ok(complaintPackageFacade.isEnabled());
    }
}
