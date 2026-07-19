package com.example.productassistant.rainforest;

import java.io.IOException;
import java.io.InputStream;

import com.example.productassistant.amazon.AmazonProductKey;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class DemoRainforestResponseProvider {

    public static final String DEMO_PRODUCT_URL =
            "https://www.amazon.com/dp/B073JYC4XM?th=1&psc=1";

    private final JsonNode demoResponse;

    public DemoRainforestResponseProvider(ObjectMapper objectMapper) {
        ClassPathResource resource = new ClassPathResource("demo.json");
        try (InputStream inputStream = resource.getInputStream()) {
            JsonNode loaded = objectMapper.readTree(inputStream);
            if (loaded == null || !loaded.isObject() || !loaded.path("product").isObject()) {
                throw new IllegalStateException("demo.json 缺少有效的 product 对象");
            }
            this.demoResponse = loaded;
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取 classpath 中的 demo.json", exception);
        }
    }

    public boolean supports(AmazonProductKey productKey) {
        return productKey != null && DEMO_PRODUCT_URL.equals(productKey.sourceUrl());
    }

    public RainforestResponse getResponse() {
        return new RainforestResponse(demoResponse.deepCopy(), "local-demo-json", 0);
    }
}

