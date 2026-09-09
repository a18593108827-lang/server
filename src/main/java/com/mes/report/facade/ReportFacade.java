package com.mes.report.facade;

import com.mes.report.vo.ReportMoveVO;

import java.time.LocalDate;

/**
 * 报表只读门面。不写业务表；状态真相在 Track / Hold。
 * 鉴权在 Controller（report:view）。
 */
public interface ReportFacade {

    /**
     * Move 过站汇总（TRACK_OUT）。
     *
     * @param from 含；null 则按 to 回推默认窗
     * @param to   含；null 则今天
     */
    ReportMoveVO moveSummary(LocalDate from, LocalDate to);
}
