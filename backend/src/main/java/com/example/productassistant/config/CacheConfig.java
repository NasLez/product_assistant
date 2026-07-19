package com.example.productassistant.config;

import java.util.List;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CacheConfig {

    public static final String PRODUCT_CACHE = "rainforestProducts";
    public static final String ANALYSIS_CACHE = "analyses";

    @Bean
    public CacheManager cacheManager(AppProperties properties) {
        AppProperties.Cache config = properties.getCache();

        CaffeineCache products = new CaffeineCache(PRODUCT_CACHE, Caffeine.newBuilder()
                .maximumSize(config.getProductMaximumSize())
                .expireAfterWrite(config.getProductTtl())
                .build());
        CaffeineCache analyses = new CaffeineCache(ANALYSIS_CACHE, Caffeine.newBuilder()
                .maximumSize(config.getAnalysisMaximumSize())
                .expireAfterWrite(config.getAnalysisTtl())
                .build());

        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(products, analyses));
        return manager;
    }
}

