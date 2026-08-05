package com.mes.track.service;

import com.mes.track.vo.TrackContextVO;
import com.mes.track.vo.TrackReleaseResultVO;
import com.mes.track.vo.TrackTxnResultVO;
import com.mes.track.vo.MesTxLogVO;

import java.util.List;

/** Track 执行引擎 */
public interface TrackService {

    /** 放行：绑 active 版本，进首站 wait */
    TrackReleaseResultVO release(Long lotId);

    /** 开工：wait → processing */
    TrackTxnResultVO trackIn(Long lotId, Long eqpId);

    /**
     * 完工：processing → 下一站 wait，或末站 completed。
     * 一期不单独暴露 Move，由本事务自动推进站点。
     */
    TrackTxnResultVO trackOut(Long lotId, String resultCode);

    /** 返工回流：wait|processing → wait(目标站) */
    TrackTxnResultVO rework(Long lotId, Integer toSortNo, String reasonCode, String remark);

    /** 前向跳站：仅 wait → wait(目标站)；processing 拒绝 */
    TrackTxnResultVO skip(Long lotId, Integer toSortNo, String reasonCode, String remark);

    /** 执行上下文：当前站 / 下一站 / 可操作标记 */
    TrackContextVO context(Long lotId);

    /** 按 Lot 查事务履历（时间升序） */
    List<MesTxLogVO> history(Long lotId);
}
