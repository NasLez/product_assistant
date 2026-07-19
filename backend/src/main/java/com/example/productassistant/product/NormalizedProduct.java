package com.example.productassistant.product;

import java.math.BigDecimal;
import java.util.List;

public record NormalizedProduct(
        String amazonDomain,
        String asin,
        String sourceUrl,
        String title,
        String brand,
        String categoryPath,
        Price price,
        String mainImageUrl,
        String description,
        List<String> features,
        List<Specification> specifications) {

    public NormalizedProduct {
        features = features == null ? List.of() : List.copyOf(features);
        specifications = specifications == null ? List.of() : List.copyOf(specifications);
    }

    public record Price(BigDecimal amount, String currency, String display) {
    }

    public record Specification(String name, String value) {
    }
}

