package com.example.productassistant.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Rainforest rainforest = new Rainforest();
    private final DeepSeek deepseek = new DeepSeek();
    private final Cache cache = new Cache();
    private final Analysis analysis = new Analysis();

    public Rainforest getRainforest() {
        return rainforest;
    }

    public DeepSeek getDeepseek() {
        return deepseek;
    }

    public Cache getCache() {
        return cache;
    }

    public Analysis getAnalysis() {
        return analysis;
    }

    public static class Rainforest {
        private String baseUrl = "https://api.rainforestapi.com";
        private String apiKey = "";
        private Duration connectTimeout = Duration.ofSeconds(10);
        private Duration readTimeout = Duration.ofSeconds(60);
        private Duration queueTimeout = Duration.ofSeconds(10);

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public Duration getConnectTimeout() { return connectTimeout; }
        public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
        public Duration getReadTimeout() { return readTimeout; }
        public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
        public Duration getQueueTimeout() { return queueTimeout; }
        public void setQueueTimeout(Duration queueTimeout) { this.queueTimeout = queueTimeout; }
    }

    public static class DeepSeek {
        private String baseUrl = "https://api.deepseek.com";
        private String apiKey = "";
        private String model = "deepseek-v4-pro";
        private Duration connectTimeout = Duration.ofSeconds(10);
        private Duration readTimeout = Duration.ofSeconds(60);

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public Duration getConnectTimeout() { return connectTimeout; }
        public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
        public Duration getReadTimeout() { return readTimeout; }
        public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
    }

    public static class Cache {
        private long productMaximumSize = 100;
        private Duration productTtl = Duration.ofHours(6);
        private long analysisMaximumSize = 200;
        private Duration analysisTtl = Duration.ofHours(24);

        public long getProductMaximumSize() { return productMaximumSize; }
        public void setProductMaximumSize(long productMaximumSize) { this.productMaximumSize = productMaximumSize; }
        public Duration getProductTtl() { return productTtl; }
        public void setProductTtl(Duration productTtl) { this.productTtl = productTtl; }
        public long getAnalysisMaximumSize() { return analysisMaximumSize; }
        public void setAnalysisMaximumSize(long analysisMaximumSize) { this.analysisMaximumSize = analysisMaximumSize; }
        public Duration getAnalysisTtl() { return analysisTtl; }
        public void setAnalysisTtl(Duration analysisTtl) { this.analysisTtl = analysisTtl; }
    }

    public static class Analysis {
        private String promptVersion = "v1";
        private int maxFeatureCount = 12;
        private int maxSpecificationCount = 24;
        private int maxItemLength = 500;

        public String getPromptVersion() { return promptVersion; }
        public void setPromptVersion(String promptVersion) { this.promptVersion = promptVersion; }
        public int getMaxFeatureCount() { return maxFeatureCount; }
        public void setMaxFeatureCount(int maxFeatureCount) { this.maxFeatureCount = maxFeatureCount; }
        public int getMaxSpecificationCount() { return maxSpecificationCount; }
        public void setMaxSpecificationCount(int maxSpecificationCount) { this.maxSpecificationCount = maxSpecificationCount; }
        public int getMaxItemLength() { return maxItemLength; }
        public void setMaxItemLength(int maxItemLength) { this.maxItemLength = maxItemLength; }
    }
}

