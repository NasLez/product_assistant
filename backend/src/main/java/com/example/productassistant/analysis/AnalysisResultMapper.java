package com.example.productassistant.analysis;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AnalysisResultMapper extends BaseMapper<AnalysisResultEntity> {

    @Select("""
            SELECT id, product_snapshot_id, model, prompt_version, video_script
            FROM analysis_result
            WHERE id > #{afterId}
              AND video_script_ciphertext IS NULL
              AND video_script IS NOT NULL
            ORDER BY id
            LIMIT #{limit}
            """)
    List<AnalysisResultEntity> findPlaintextBatch(
            @Param("afterId") long afterId,
            @Param("limit") int limit);

    @Update("""
            UPDATE analysis_result
            SET video_script_ciphertext = #{ciphertext},
                video_script_iv = #{iv},
                video_script_key_version = #{keyVersion},
                video_script = NULL,
                ai_raw_json = JSON_OBJECT('legacyResponseContentRemoved', TRUE)
            WHERE id = #{id}
              AND video_script_ciphertext IS NULL
              AND video_script IS NOT NULL
            """)
    int storeEncryptedVideoScript(
            @Param("id") long id,
            @Param("ciphertext") byte[] ciphertext,
            @Param("iv") byte[] iv,
            @Param("keyVersion") String keyVersion);

    default AnalysisResultEntity findByIdentity(Long productId, String model, String promptVersion) {
        return selectOne(new LambdaQueryWrapper<AnalysisResultEntity>()
                .eq(AnalysisResultEntity::getProductSnapshotId, productId)
                .eq(AnalysisResultEntity::getModel, model)
                .eq(AnalysisResultEntity::getPromptVersion, promptVersion)
                .last("LIMIT 1"));
    }
}
