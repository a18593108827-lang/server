package com.mes.history.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mes.history.vo.HistoryDailyCountVO;
import com.mes.history.vo.HistoryStepCountVO;
import com.mes.track.entity.MesTxLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/** 履历只读 Mapper，表仍是 mes_tx_log。约定禁止 insert/update/delete，写只走 Track。 */
@Mapper
public interface HistoryTxLogMapper extends BaseMapper<MesTxLog> {

    @Select("""
            SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS day, COUNT(*) AS count
            FROM mes_tx_log
            WHERE tx_type = #{txType}
              AND create_time >= #{from}
              AND create_time < #{toExclusive}
            GROUP BY DATE_FORMAT(create_time, '%Y-%m-%d')
            ORDER BY day
            """)
    List<HistoryDailyCountVO> countDailyByTxType(@Param("txType") String txType,
                                                 @Param("from") LocalDateTime from,
                                                 @Param("toExclusive") LocalDateTime toExclusive);

    @Select("""
            SELECT step_id AS stepId, COUNT(*) AS count
            FROM mes_tx_log
            WHERE tx_type = #{txType}
              AND create_time >= #{from}
              AND create_time < #{toExclusive}
            GROUP BY step_id
            ORDER BY count DESC
            """)
    List<HistoryStepCountVO> countByStepAndTxType(@Param("txType") String txType,
                                                  @Param("from") LocalDateTime from,
                                                  @Param("toExclusive") LocalDateTime toExclusive);
}
