package com.example.productassistant.analysis;

public record AnalysisCacheKey(
        String amazonDomain,
        String asin,
        String model,
        String promptVersion) {

    @Override
    public String toString() {
        return amazonDomain + ":" + asin + ":" + model + ":" + promptVersion;
    }
}

