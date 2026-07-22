package com.example.productassistant.product;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import com.example.productassistant.amazon.AmazonProductKey;
import com.example.productassistant.config.AppProperties;
import com.example.productassistant.config.CacheConfig;
import com.example.productassistant.persistence.JsonStringCodec;
import com.example.productassistant.rainforest.DemoRainforestResponseProvider;
import com.example.productassistant.rainforest.RainforestClient;
import com.example.productassistant.rainforest.RainforestException;
import com.example.productassistant.rainforest.RainforestResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class ProductSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(ProductSnapshotService.class);

    private final ProductSnapshotMapper mapper;
    private final ProductNormalizer normalizer;
    private final RainforestClient rainforestClient;
    private final DemoRainforestResponseProvider demoResponseProvider;
    private final Semaphore rainforestSemaphore;
    private final AppProperties properties;
    private final JsonStringCodec jsonCodec;
    private final Cache productCache;

    public ProductSnapshotService(
            ProductSnapshotMapper mapper,
            ProductNormalizer normalizer,
            RainforestClient rainforestClient,
            DemoRainforestResponseProvider demoResponseProvider,
            @Qualifier("rainforestSemaphore") Semaphore rainforestSemaphore,
            AppProperties properties,
            JsonStringCodec jsonCodec,
            CacheManager cacheManager) {
        this.mapper = mapper;
        this.normalizer = normalizer;
        this.rainforestClient = rainforestClient;
        this.demoResponseProvider = demoResponseProvider;
        this.rainforestSemaphore = rainforestSemaphore;
        this.properties = properties;
        this.jsonCodec = jsonCodec;
        this.productCache = Objects.requireNonNull(cacheManager.getCache(CacheConfig.PRODUCT_CACHE));
    }

    public ProductSnapshotResult getOrFetch(AmazonProductKey key) {
        if (demoResponseProvider.supports(key)) {
            return loadDemoSnapshot(key);
        }

        ProductSnapshotResult cached = productCache.get(key.cacheKey(), ProductSnapshotResult.class);
        if (cached != null) {
            return cached;
        }

        ProductSnapshotResult stored = findFreshStored(key);
        if (stored != null) {
            productCache.put(key.cacheKey(), stored);
            return stored;
        }

        boolean acquired = false;
        try {
            acquired = rainforestSemaphore.tryAcquire(
                    properties.getRainforest().getQueueTimeout().toMillis(),
                    TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new RainforestException(
                        RainforestException.Kind.BUSY,
                        "商品服务繁忙，请稍后重试");
            }

            ProductSnapshotResult doubleChecked = productCache.get(key.cacheKey(), ProductSnapshotResult.class);
            if (doubleChecked != null) {
                return doubleChecked;
            }
            doubleChecked = findFreshStored(key);
            if (doubleChecked != null) {
                productCache.put(key.cacheKey(), doubleChecked);
                return doubleChecked;
            }

            long started = System.nanoTime();
            RainforestResponse response = rainforestClient.fetchProduct(key);
            NormalizedProduct product = normalizer.normalize(key, response.root());
            ProductSnapshotEntity entity = saveSnapshot(product, response);
            ProductSnapshotResult result = new ProductSnapshotResult(entity, product);
            productCache.put(key.cacheKey(), result);

            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            log.info("Rainforest product fetched asin={} domain={} credits={} elapsedMs={} upstreamRequestId={}",
                    key.asin(), key.amazonDomain(), response.creditsUsed(), elapsedMs, response.upstreamRequestId());
            return result;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RainforestException(
                    RainforestException.Kind.BUSY,
                    "等待商品服务时请求被中断",
                    exception);
        } finally {
            if (acquired) {
                rainforestSemaphore.release();
            }
        }
    }

    private ProductSnapshotResult loadDemoSnapshot(AmazonProductKey key) {
        RainforestResponse response = demoResponseProvider.getResponse();
        NormalizedProduct product = normalizer.normalize(key, response.root());
        ProductSnapshotEntity entity = saveSnapshot(product, response);
        ProductSnapshotResult result = new ProductSnapshotResult(entity, product);
        productCache.put(key.cacheKey(), result);
        log.info("Local demo product loaded asin={} domain={} source={}",
                key.asin(), key.amazonDomain(), response.upstreamRequestId());
        return result;
    }

    private ProductSnapshotResult findFreshStored(AmazonProductKey key) {
        ProductSnapshotEntity entity = mapper.findByProductKey(key.amazonDomain(), key.asin());
        if (entity == null || entity.getFetchedAt() == null) {
            return null;
        }
        Duration ttl = properties.getCache().getProductTtl();
        if (ttl.isZero() || ttl.isNegative()) {
            return null;
        }
        try {
            LocalDateTime cutoff = LocalDateTime.now(ZoneOffset.UTC).minus(ttl);
            if (entity.getFetchedAt().isBefore(cutoff)) {
                return null;
            }
        } catch (DateTimeException | ArithmeticException overflow) {
            // Positive TTL values beyond LocalDateTime's range intentionally do not expire.
        }
        NormalizedProduct product = jsonCodec.read(entity.getNormalizedJson(), NormalizedProduct.class);
        return new ProductSnapshotResult(entity, product);
    }

    private ProductSnapshotEntity saveSnapshot(NormalizedProduct product, RainforestResponse response) {
        ProductSnapshotEntity entity = mapper.findByProductKey(product.amazonDomain(), product.asin());
        boolean insert = entity == null;
        if (insert) {
            entity = new ProductSnapshotEntity();
            entity.setAmazonDomain(product.amazonDomain());
            entity.setAsin(product.asin());
        }

        entity.setSourceUrl(product.sourceUrl());
        entity.setTitle(product.title());
        entity.setBrand(product.brand());
        entity.setCategoryPath(product.categoryPath());
        entity.setPriceAmount(product.price() == null ? null : product.price().amount());
        entity.setCurrency(product.price() == null ? null : product.price().currency());
        entity.setMainImageUrl(product.mainImageUrl());
        entity.setNormalizedJson(jsonCodec.write(product));
        entity.setRawJson(jsonCodec.write(response.root()));
        entity.setFetchedAt(LocalDateTime.now(ZoneOffset.UTC));

        if (insert) {
            try {
                mapper.insert(entity);
                return entity;
            } catch (DuplicateKeyException exception) {
                ProductSnapshotEntity concurrent = mapper.findByProductKey(product.amazonDomain(), product.asin());
                if (concurrent == null) {
                    throw exception;
                }
                entity.setId(concurrent.getId());
            }
        }
        mapper.updateById(entity);
        return entity;
    }
}
