package com.mes.complaint.facade.impl;

import com.mes.common.AssertUtil;
import com.mes.complaint.facade.ComplaintPackageFacade;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 客诉追溯包门面实现（CP-1：仅开关） */
@Service
public class ComplaintPackageFacadeImpl implements ComplaintPackageFacade {

    /** 模块关闭业务错码 */
    public static final String ERR_DISABLED = "COMPLAINT_PACKAGE_DISABLED";

    /** mes.complaint-package.enabled；默认 false */
    @Value("${mes.complaint-package.enabled:false}")
    private boolean enabled;

    /** 配置开关是否打开 */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /** 开关关则抛 COMPLAINT_PACKAGE_DISABLED */
    @Override
    public void assertEnabled() {
        AssertUtil.isTrue(enabled, ERR_DISABLED + ": 客诉追溯包已关闭");
    }
}
