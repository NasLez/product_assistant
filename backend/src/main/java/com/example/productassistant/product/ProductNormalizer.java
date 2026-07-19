package com.example.productassistant.product;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.example.productassistant.amazon.AmazonProductKey;
import com.example.productassistant.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ProductNormalizer {

    private final AppProperties properties;

    public ProductNormalizer(AppProperties properties) {
        this.properties = properties;
    }

    public NormalizedProduct normalize(AmazonProductKey key, JsonNode root) {
        JsonNode product = root.path("product");
        String title = truncate(text(product.path("title")), 1024);
        if (!StringUtils.hasText(title)) {
            throw new ProductDataIncompleteException("商品信息缺少标题");
        }

        String categoryPath = text(product.path("categories_flat"));
        if (!StringUtils.hasText(categoryPath)) {
            categoryPath = joinCategories(product.path("categories"));
        }

        JsonNode priceNode = product.path("buybox_winner").path("price");
        BigDecimal amount = decimal(priceNode.path("value"));
        NormalizedProduct.Price price = new NormalizedProduct.Price(
                amount,
                truncate(text(priceNode.path("currency")), 8),
                truncate(text(priceNode.path("raw")), 128));

        return new NormalizedProduct(
                key.amazonDomain(),
                key.asin(),
                key.sourceUrl(),
                title,
                truncate(text(product.path("brand")), 255),
                truncate(categoryPath, 1024),
                price,
                truncate(text(product.path("main_image").path("link")), 2048),
                truncate(text(product.path("description")), 5000),
                readFeatures(product.path("feature_bullets")),
                readSpecifications(product.path("specifications")));
    }

    private List<String> readFeatures(JsonNode node) {
        int maxCount = properties.getAnalysis().getMaxFeatureCount();
        List<String> values = new ArrayList<>();
        if (!node.isArray()) {
            return values;
        }
        for (JsonNode item : node) {
            if (values.size() >= maxCount) {
                break;
            }
            String value = truncate(text(item), properties.getAnalysis().getMaxItemLength());
            if (StringUtils.hasText(value)) {
                values.add(value);
            }
        }
        return values;
    }

    private List<NormalizedProduct.Specification> readSpecifications(JsonNode node) {
        int maxCount = properties.getAnalysis().getMaxSpecificationCount();
        List<NormalizedProduct.Specification> values = new ArrayList<>();
        if (!node.isArray()) {
            return values;
        }
        for (JsonNode item : node) {
            if (values.size() >= maxCount) {
                break;
            }
            String name = truncate(text(item.path("name")), 160);
            String value = truncate(text(item.path("value")), properties.getAnalysis().getMaxItemLength());
            if (StringUtils.hasText(name) && StringUtils.hasText(value)) {
                values.add(new NormalizedProduct.Specification(name, value));
            }
        }
        return values;
    }

    private String joinCategories(JsonNode node) {
        List<String> names = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode category : node) {
                String name = text(category.path("name"));
                if (StringUtils.hasText(name)) {
                    names.add(name);
                }
            }
        }
        return String.join(" > ", names);
    }

    private BigDecimal decimal(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        try {
            return new BigDecimal(node.asText());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || !node.isValueNode()) {
            return "";
        }
        return node.asText("").trim();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}

