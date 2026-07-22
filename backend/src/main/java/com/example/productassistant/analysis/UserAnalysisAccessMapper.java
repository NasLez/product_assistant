package com.example.productassistant.analysis;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserAnalysisAccessMapper extends BaseMapper<UserAnalysisAccessEntity> {

    @Select("""
            SELECT EXISTS(
                SELECT 1
                FROM user_analysis_access
                WHERE user_id = #{userId}
                  AND analysis_result_id = #{analysisResultId}
            )
            """)
    boolean hasAccess(
            @Param("userId") long userId,
            @Param("analysisResultId") long analysisResultId);

    @Insert("""
            INSERT INTO user_analysis_access (
                user_id,
                analysis_result_id,
                point_transaction_id,
                request_id
            ) VALUES (
                #{userId},
                #{analysisResultId},
                #{pointTransactionId},
                #{requestId}
            )
            """)
    int grantAccess(
            @Param("userId") long userId,
            @Param("analysisResultId") long analysisResultId,
            @Param("pointTransactionId") long pointTransactionId,
            @Param("requestId") String requestId);
}
