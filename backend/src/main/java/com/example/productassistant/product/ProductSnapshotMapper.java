package com.example.productassistant.product;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductSnapshotMapper extends BaseMapper<ProductSnapshotEntity> {

    default ProductSnapshotEntity findByProductKey(String amazonDomain, String asin) {
        return selectOne(new LambdaQueryWrapper<ProductSnapshotEntity>()
                .eq(ProductSnapshotEntity::getAmazonDomain, amazonDomain)
                .eq(ProductSnapshotEntity::getAsin, asin)
                .last("LIMIT 1"));
    }
}

