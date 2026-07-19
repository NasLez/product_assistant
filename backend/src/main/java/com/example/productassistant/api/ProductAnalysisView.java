package com.example.productassistant.api;

import java.math.BigDecimal;
import java.util.List;

public record ProductAnalysisView(
        Long id,
        String source,
        Product product,
        Analysis analysis) {

    public ProductAnalysisView withSource(String newSource) {
        return new ProductAnalysisView(id, newSource, product, analysis);
    }

    public record Product(
            String amazonDomain,
            String asin,
            String title,
            String brand,
            String categoryPath,
            Price price,
            String mainImageUrl,
            List<String> features,
            List<Specification> specifications) {
    }

    public record Price(BigDecimal amount, String currency, String display) {
    }

    public record Specification(String name, String value) {
    }

    public record Analysis(
            List<String> targetUsers,
            List<String> useCases,
            List<String> painPoints,
            List<SellingPoint> coreSellingPoints,
            String videoScript) {
    }

    public record SellingPoint(String claim, String evidence) {
    }
}

