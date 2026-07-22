package com.example.productassistant.analysis;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import com.example.productassistant.amazon.AmazonProductKey;
import com.example.productassistant.amazon.AmazonUrlParser;
import com.example.productassistant.api.ProductAnalysisView;
import com.example.productassistant.config.AppProperties;
import com.example.productassistant.config.CacheConfig;
import com.example.productassistant.crypto.EncryptedText;
import com.example.productassistant.crypto.VideoScriptCipher;
import com.example.productassistant.crypto.VideoScriptEncryptionException;
import com.example.productassistant.crypto.VideoScriptEncryptionProperties;
import com.example.productassistant.persistence.JsonStringCodec;
import com.example.productassistant.product.NormalizedProduct;
import com.example.productassistant.product.ProductSnapshotEntity;
import com.example.productassistant.product.ProductSnapshotMapper;
import com.example.productassistant.product.ProductSnapshotResult;
import com.example.productassistant.product.ProductSnapshotService;
import com.example.productassistant.rainforest.DemoRainforestResponseProvider;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AnalysisApplicationService {

    private final AmazonUrlParser amazonUrlParser;
    private final ProductSnapshotService productSnapshotService;
    private final ProductSnapshotMapper productMapper;
    private final AnalysisResultMapper analysisMapper;
    private final AnalysisGenerationService generationService;
    private final DemoRainforestResponseProvider demoResponseProvider;
    private final AppProperties properties;
    private final VideoScriptEncryptionProperties encryptionProperties;
    private final VideoScriptCipher videoScriptCipher;
    private final JsonStringCodec jsonCodec;
    private final TransactionTemplate transactionTemplate;
    private final Cache analysisCache;
    private final ConcurrentHashMap<AnalysisCacheKey, ReentrantLock> generationLocks = new ConcurrentHashMap<>();

    public AnalysisApplicationService(
            AmazonUrlParser amazonUrlParser,
            ProductSnapshotService productSnapshotService,
            ProductSnapshotMapper productMapper,
            AnalysisResultMapper analysisMapper,
            AnalysisGenerationService generationService,
            DemoRainforestResponseProvider demoResponseProvider,
            AppProperties properties,
            VideoScriptEncryptionProperties encryptionProperties,
            VideoScriptCipher videoScriptCipher,
            JsonStringCodec jsonCodec,
            TransactionTemplate transactionTemplate,
            CacheManager cacheManager) {
        this.amazonUrlParser = amazonUrlParser;
        this.productSnapshotService = productSnapshotService;
        this.productMapper = productMapper;
        this.analysisMapper = analysisMapper;
        this.generationService = generationService;
        this.demoResponseProvider = demoResponseProvider;
        this.properties = properties;
        this.encryptionProperties = encryptionProperties;
        this.videoScriptCipher = videoScriptCipher;
        this.jsonCodec = jsonCodec;
        this.transactionTemplate = transactionTemplate;
        this.analysisCache = Objects.requireNonNull(cacheManager.getCache(CacheConfig.ANALYSIS_CACHE));
    }

    public ProductAnalysisView analyze(String amazonUrl) {
        AmazonProductKey productKey = amazonUrlParser.parse(amazonUrl);
        AnalysisCacheKey cacheKey = cacheKey(productKey);
        boolean useLocalDemo = demoResponseProvider.supports(productKey);

        ProductAnalysisView cached = null;
        ProductAnalysisView stored = null;
        if (!useLocalDemo) {
            cached = analysisCache.get(cacheKey.toString(), ProductAnalysisView.class);
            if (cached != null) {
                return cached.withSource("CACHE");
            }

            stored = findStoredView(productKey, "DATABASE");
            if (stored != null) {
                analysisCache.put(cacheKey.toString(), stored);
                return stored;
            }
        }

        ReentrantLock lock = generationLocks.computeIfAbsent(cacheKey, ignored -> new ReentrantLock());
        lock.lock();
        try {
            if (!useLocalDemo) {
                cached = analysisCache.get(cacheKey.toString(), ProductAnalysisView.class);
                if (cached != null) {
                    return cached.withSource("CACHE");
                }
                stored = findStoredView(productKey, "DATABASE");
                if (stored != null) {
                    analysisCache.put(cacheKey.toString(), stored);
                    return stored;
                }
            }

            ProductSnapshotResult snapshot = productSnapshotService.getOrFetch(productKey);
            if (!useLocalDemo) {
                AnalysisResultEntity concurrentResult = analysisMapper.findByIdentity(
                        snapshot.entity().getId(),
                        properties.getDeepseek().getModel(),
                        properties.getAnalysis().getPromptVersion());
                if (isFresh(concurrentResult)) {
                    ProductAnalysisView view = toView(
                            concurrentResult.getId(),
                            "DATABASE",
                            snapshot.product(),
                            decodeAnalysis(concurrentResult));
                    analysisCache.put(cacheKey.toString(), view);
                    return view;
                }
            }

            GeneratedAnalysis generated = generationService.generate(snapshot.product());
            AnalysisResultEntity saved = saveAnalysis(snapshot.entity().getId(), generated);
            ProductAnalysisView view = toView(
                    saved.getId(),
                    useLocalDemo ? "LOCAL_DEMO" : "LIVE",
                    snapshot.product(),
                    generated.analysis());
            analysisCache.put(cacheKey.toString(), view);
            return view;
        } finally {
            lock.unlock();
            generationLocks.remove(cacheKey, lock);
        }
    }

    public ProductAnalysisView getById(Long id) {
        AnalysisResultEntity analysis = analysisMapper.selectById(id);
        if (analysis == null) {
            throw new AnalysisNotFoundException("分析记录不存在");
        }
        ProductSnapshotEntity product = productMapper.selectById(analysis.getProductSnapshotId());
        if (product == null) {
            throw new AnalysisNotFoundException("分析记录关联的商品不存在");
        }
        return toView(
                analysis.getId(),
                "DATABASE",
                jsonCodec.read(product.getNormalizedJson(), NormalizedProduct.class),
                decodeAnalysis(analysis));
    }

    private ProductAnalysisView findStoredView(AmazonProductKey key, String source) {
        ProductSnapshotEntity product = productMapper.findByProductKey(key.amazonDomain(), key.asin());
        if (product == null) {
            return null;
        }
        AnalysisResultEntity analysis = analysisMapper.findByIdentity(
                product.getId(),
                properties.getDeepseek().getModel(),
                properties.getAnalysis().getPromptVersion());
        if (!isFresh(analysis)) {
            return null;
        }
        return toView(
                analysis.getId(),
                source,
                jsonCodec.read(product.getNormalizedJson(), NormalizedProduct.class),
                decodeAnalysis(analysis));
    }

    private AnalysisResultEntity saveAnalysis(Long productId, GeneratedAnalysis generated) {
        return transactionTemplate.execute(status -> {
            AnalysisResultEntity existing = analysisMapper.findByIdentity(
                    productId,
                    properties.getDeepseek().getModel(),
                    properties.getAnalysis().getPromptVersion());
            AnalysisResultEntity entity = existing == null ? new AnalysisResultEntity() : existing;
            entity.setProductSnapshotId(productId);
            entity.setModel(properties.getDeepseek().getModel());
            entity.setPromptVersion(properties.getAnalysis().getPromptVersion());
            entity.setTargetUsers(jsonCodec.write(generated.analysis().targetUsers()));
            entity.setUseCases(jsonCodec.write(generated.analysis().useCases()));
            entity.setPainPoints(jsonCodec.write(generated.analysis().painPoints()));
            entity.setCoreSellingPoints(jsonCodec.write(generated.analysis().coreSellingPoints()));
            String keyVersion = encryptionProperties.getActiveKeyVersion().trim();
            EncryptedText encryptedScript = videoScriptCipher.encrypt(
                    generated.analysis().videoScript(),
                    associatedData(productId, entity.getModel(), entity.getPromptVersion(), keyVersion));
            entity.setVideoScript(null);
            entity.setVideoScriptCiphertext(encryptedScript.ciphertext());
            entity.setVideoScriptIv(encryptedScript.iv());
            entity.setVideoScriptKeyVersion(encryptedScript.keyVersion());
            entity.setAiRawJson(jsonCodec.write(aiAuditMetadata(generated.rawResponse())));

            if (existing != null) {
                analysisMapper.updateById(entity);
                return entity;
            }
            try {
                analysisMapper.insert(entity);
                return entity;
            } catch (DuplicateKeyException exception) {
                AnalysisResultEntity concurrent = analysisMapper.findByIdentity(
                        productId,
                        properties.getDeepseek().getModel(),
                        properties.getAnalysis().getPromptVersion());
                if (concurrent == null) {
                    throw exception;
                }
                return concurrent;
            }
        });
    }

    private ProductAnalysis decodeAnalysis(AnalysisResultEntity entity) {
        List<String> targetUsers = jsonCodec.read(entity.getTargetUsers(), new TypeReference<List<String>>() {});
        List<String> useCases = jsonCodec.read(entity.getUseCases(), new TypeReference<List<String>>() {});
        List<String> painPoints = jsonCodec.read(entity.getPainPoints(), new TypeReference<List<String>>() {});
        List<ProductAnalysis.SellingPoint> points = jsonCodec.read(
                entity.getCoreSellingPoints(),
                new TypeReference<List<ProductAnalysis.SellingPoint>>() {});
        return new ProductAnalysis(targetUsers, useCases, painPoints, points, readVideoScript(entity));
    }

    private String readVideoScript(AnalysisResultEntity entity) {
        byte[] ciphertext = entity.getVideoScriptCiphertext();
        byte[] iv = entity.getVideoScriptIv();
        String keyVersion = entity.getVideoScriptKeyVersion();
        if (ciphertext != null) {
            if (iv == null || keyVersion == null || keyVersion.isBlank()) {
                throw new VideoScriptEncryptionException();
            }
            EncryptedText encrypted = new EncryptedText(ciphertext, iv, keyVersion);
            return videoScriptCipher.decrypt(
                    encrypted,
                    associatedData(
                            entity.getProductSnapshotId(),
                            entity.getModel(),
                            entity.getPromptVersion(),
                            keyVersion));
        }
        if (entity.getVideoScript() != null) {
            return entity.getVideoScript();
        }
        throw new VideoScriptEncryptionException();
    }

    public static String associatedData(Long productId, String model, String promptVersion, String keyVersion) {
        return "analysis-result:%d:%s:%s:%s".formatted(
                productId,
                model,
                promptVersion,
                keyVersion);
    }

    private Map<String, Object> aiAuditMetadata(JsonNode rawResponse) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("responseContentStored", false);
        copyTextMetadata(rawResponse, metadata, "id", "providerResponseId");
        copyTextMetadata(rawResponse, metadata, "model", "providerModel");
        copyTextMetadata(rawResponse, metadata, "system_fingerprint", "systemFingerprint");
        if (rawResponse != null && rawResponse.path("usage").isObject()) {
            metadata.put("usage", rawResponse.path("usage"));
        }
        if (rawResponse != null && rawResponse.path("choices").isArray()) {
            List<String> finishReasons = new java.util.ArrayList<>();
            for (JsonNode choice : rawResponse.path("choices")) {
                String finishReason = choice.path("finish_reason").asText("");
                if (!finishReason.isBlank()) {
                    finishReasons.add(finishReason);
                }
            }
            if (!finishReasons.isEmpty()) {
                metadata.put("finishReasons", List.copyOf(finishReasons));
            }
        }
        return metadata;
    }

    private void copyTextMetadata(
            JsonNode source,
            Map<String, Object> target,
            String sourceName,
            String targetName) {
        if (source == null) {
            return;
        }
        String value = source.path(sourceName).asText("");
        if (!value.isBlank()) {
            target.put(targetName, value);
        }
    }

    private ProductAnalysisView toView(
            Long id,
            String source,
            NormalizedProduct product,
            ProductAnalysis analysis) {
        ProductAnalysisView.Price price = product.price() == null ? null : new ProductAnalysisView.Price(
                product.price().amount(), product.price().currency(), product.price().display());
        List<ProductAnalysisView.Specification> specifications = product.specifications().stream()
                .map(item -> new ProductAnalysisView.Specification(item.name(), item.value()))
                .toList();
        List<ProductAnalysisView.SellingPoint> points = analysis.coreSellingPoints().stream()
                .map(item -> new ProductAnalysisView.SellingPoint(item.claim(), item.evidence()))
                .toList();

        return new ProductAnalysisView(
                id,
                source,
                new ProductAnalysisView.Product(
                        product.amazonDomain(),
                        product.asin(),
                        product.title(),
                        product.brand(),
                        product.categoryPath(),
                        price,
                        product.mainImageUrl(),
                        product.features(),
                        specifications),
                new ProductAnalysisView.Analysis(
                        analysis.targetUsers(),
                        analysis.useCases(),
                        analysis.painPoints(),
                        points,
                        analysis.videoScript()));
    }

    private AnalysisCacheKey cacheKey(AmazonProductKey productKey) {
        return new AnalysisCacheKey(
                productKey.amazonDomain(),
                productKey.asin(),
                properties.getDeepseek().getModel(),
                properties.getAnalysis().getPromptVersion());
    }

    private boolean isFresh(AnalysisResultEntity entity) {
        if (entity == null) {
            return false;
        }
        LocalDateTime timestamp = entity.getUpdatedAt() != null
                ? entity.getUpdatedAt()
                : entity.getCreatedAt();
        if (timestamp == null) {
            return false;
        }
        Duration ttl = properties.getCache().getAnalysisTtl();
        if (ttl.isZero() || ttl.isNegative()) {
            return false;
        }
        try {
            LocalDateTime cutoff = LocalDateTime.now(ZoneOffset.UTC).minus(ttl);
            return !timestamp.isBefore(cutoff);
        } catch (DateTimeException | ArithmeticException overflow) {
            // Positive TTL values beyond LocalDateTime's range intentionally do not expire.
            return true;
        }
    }
}
