package com.mes.complaint.facade;

/**
 * 客诉追溯包对外门面。CP-1 仅开关；装配/导出/遏制见 CP-2+。
 * 编排只读依赖 Lot 谱系 / History / Hold；禁止本包直查业务 Mapper 以外的表当 SSOT。
 */
public interface ComplaintPackageFacade {

    /** 配置开关是否打开 */
    boolean isEnabled();

    /** 开关关则抛 COMPLAINT_PACKAGE_DISABLED */
    void assertEnabled();
}
