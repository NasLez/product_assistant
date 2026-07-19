package com.example.productassistant.analysis;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AnalysisResultMapper extends BaseMapper<AnalysisResultEntity> {

    default AnalysisResultEntity findByIdentity(Long productId, String model, String promptVersion) {
        return selectOne(new LambdaQueryWrapper<AnalysisResultEntity>()
                .eq(AnalysisResultEntity::getProductSnapshotId, productId)
                .eq(AnalysisResultEntity::getModel, model)
                .eq(AnalysisResultEntity::getPromptVersion, promptVersion)
                .last("LIMIT 1"));
    }
}

