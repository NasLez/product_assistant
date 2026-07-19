package com.example.productassistant.analysis;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.example.productassistant.deepseek.DeepSeekClient;
import com.example.productassistant.deepseek.DeepSeekException;
import com.example.productassistant.deepseek.DeepSeekResponse;
import com.example.productassistant.product.NormalizedProduct;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

@Service
public class AnalysisGenerationService {

    private final DeepSeekClient deepSeekClient;
    private final AnalysisOutputValidator validator;
    private final ObjectMapper objectMapper;
    private final String systemPrompt;

    public AnalysisGenerationService(
            DeepSeekClient deepSeekClient,
            AnalysisOutputValidator validator,
            ObjectMapper objectMapper) {
        this.deepSeekClient = deepSeekClient;
        this.validator = validator;
        this.objectMapper = objectMapper;
        this.systemPrompt = loadPrompt();
    }

    public GeneratedAnalysis generate(NormalizedProduct product) {
        String productFacts = serializeFacts(product);
        DeepSeekResponse first = deepSeekClient.complete(
                systemPrompt,
                "请分析以下商品事实：\n" + productFacts);
        ProductAnalysis firstAnalysis = null;
        List<String> errors;
        try {
            firstAnalysis = parse(first.content());
            errors = validator.validate(firstAnalysis);
        } catch (DeepSeekException exception) {
            if (exception.getKind() != DeepSeekException.Kind.INVALID_RESPONSE) {
                throw exception;
            }
            errors = List.of(exception.getMessage());
        }
        if (errors.isEmpty()) {
            return new GeneratedAnalysis(firstAnalysis, first.raw());
        }

        String repairPrompt = "请修复下面 JSON 的结构和内容约束。不得改变商品事实，只返回修复后的 JSON。"
                + "\n商品事实：\n" + productFacts
                + "\n校验错误：\n- " + String.join("\n- ", errors)
                + "\n待修复输出：\n" + stripCodeFence(first.content());
        DeepSeekResponse repaired = deepSeekClient.complete(systemPrompt, repairPrompt);
        ProductAnalysis repairedAnalysis = parse(repaired.content());
        List<String> repairedErrors = validator.validate(repairedAnalysis);
        if (!repairedErrors.isEmpty()) {
            throw new DeepSeekException(
                    DeepSeekException.Kind.INVALID_RESPONSE,
                    "DeepSeek 输出不符合内容约束：" + String.join("；", repairedErrors));
        }
        return new GeneratedAnalysis(repairedAnalysis, repaired.raw());
    }

    private ProductAnalysis parse(String content) {
        try {
            return objectMapper.readValue(stripCodeFence(content), ProductAnalysis.class);
        } catch (JsonProcessingException exception) {
            throw new DeepSeekException(
                    DeepSeekException.Kind.INVALID_RESPONSE,
                    "DeepSeek 输出不是合法 JSON",
                    exception);
        }
    }

    private String serializeFacts(NormalizedProduct product) {
        try {
            return objectMapper.writeValueAsString(product);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化商品事实", exception);
        }
    }

    private String stripCodeFence(String content) {
        String value = content == null ? "" : content.trim();
        value = value.replaceFirst("(?is)^```(?:json)?\\s*", "");
        value = value.replaceFirst("(?is)\\s*```$", "");
        return value.trim();
    }

    private String loadPrompt() {
        try {
            return StreamUtils.copyToString(
                    new ClassPathResource("prompts/product-analysis-v1.txt").getInputStream(),
                    StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("无法加载产品分析提示词", exception);
        }
    }
}
