package com.example.productassistant.points;

import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PointTransactionMapper extends BaseMapper<PointTransactionEntity> {

    @Select("""
            SELECT *
            FROM point_transaction
            WHERE user_id = #{userId} AND request_id = #{requestId}
            LIMIT 1
            """)
    PointTransactionEntity findByUserAndRequest(
            @Param("userId") long userId,
            @Param("requestId") String requestId);

    @Select("SELECT * FROM point_transaction WHERE id = #{transactionId} LIMIT 1")
    PointTransactionEntity findByTransactionId(@Param("transactionId") long transactionId);

    @Update("""
            UPDATE point_transaction
            SET status = 'SETTLED',
                analysis_result_id = #{analysisResultId},
                settled_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{transactionId}
              AND transaction_type = 'ANALYSIS_DEBIT'
              AND status = 'RESERVED'
            """)
    int settleReserved(
            @Param("transactionId") long transactionId,
            @Param("analysisResultId") long analysisResultId);

    @Update("""
            UPDATE point_transaction
            SET status = 'REFUNDED',
                refunded_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{transactionId}
              AND transaction_type = 'ANALYSIS_DEBIT'
              AND status = 'RESERVED'
            """)
    int markRefunded(@Param("transactionId") long transactionId);

    @Select("""
            SELECT id
            FROM point_transaction
            WHERE transaction_type = 'ANALYSIS_DEBIT'
              AND status = 'RESERVED'
              AND created_at < #{cutoff}
            ORDER BY id
            LIMIT #{limit}
            """)
    List<Long> findStaleReservationIds(
            @Param("cutoff") LocalDateTime cutoff,
            @Param("limit") int limit);
}
