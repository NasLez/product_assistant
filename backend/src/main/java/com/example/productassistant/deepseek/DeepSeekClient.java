package com.example.productassistant.deepseek;

import java.util.Locale;
import java.util.concurrent.TimeoutException;

import com.example.productassistant.config.AppProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Component
public class DeepSeekClient {

    private final WebClient webClient;
    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    public DeepSeekClient(
            @Qualifier("deepSeekWebClient") WebClient webClient,
            AppProperties properties,
            ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public DeepSeekResponse complete(String systemPrompt, String userPrompt) {
        DeepSeekRequest request = DeepSeekRequest.jsonRequest(
                properties.getDeepseek().getModel(),
                systemPrompt,
                userPrompt);
        try {
            RawResponse response = exchange(request);
            if (response.status() >= 400) {
                throw fromHttpStatus(response.status(), response.body());
            }

            JsonNode root = parseResponse(response.body());
            if (root == null || !root.isObject()) {
                throw new DeepSeekException(
                        DeepSeekException.Kind.INVALID_RESPONSE,
                        "DeepSeek 返回了空响应");
            }
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            if (!StringUtils.hasText(content)) {
                throw new DeepSeekException(
                        DeepSeekException.Kind.INVALID_RESPONSE,
                        "DeepSeek 响应缺少内容");
            }
            return new DeepSeekResponse(root, content);
        } catch (DeepSeekException exception) {
            throw exception;
        } catch (WebClientResponseException exception) {
            throw fromHttpStatus(exception.getStatusCode().value(), exception.getResponseBodyAsString());
        } catch (WebClientRequestException exception) {
            if (hasTimeoutCause(exception)) {
                throw new DeepSeekException(
                        DeepSeekException.Kind.TIMEOUT,
                        "DeepSeek 请求超时",
                        exception);
            }
            throw new DeepSeekException(
                    DeepSeekException.Kind.UPSTREAM,
                    "无法连接 DeepSeek",
                    exception);
        }
    }

    private RawResponse exchange(DeepSeekRequest request) {
        RawResponse response = webClient.post()
                .uri("/chat/completions")
                .bodyValue(request)
                .exchangeToMono(clientResponse -> clientResponse.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(body -> new RawResponse(clientResponse.statusCode().value(), body)))
                .block();
        if (response == null) {
            throw new DeepSeekException(
                    DeepSeekException.Kind.INVALID_RESPONSE,
                    "DeepSeek 返回了空响应");
        }
        return response;
    }

    private JsonNode parseResponse(String body) {
        if (!StringUtils.hasText(body)) {
            return null;
        }
        try {
            return objectMapper.readTree(body);
        } catch (JsonProcessingException exception) {
            throw new DeepSeekException(
                    DeepSeekException.Kind.INVALID_RESPONSE,
                    "DeepSeek 返回的响应不是合法 JSON",
                    exception);
        }
    }

    private DeepSeekException fromHttpStatus(int status, String body) {
        String upstreamMessage = extractUpstreamMessage(body);
        String diagnostic = StringUtils.hasText(upstreamMessage)
                ? "DeepSeek HTTP " + status + "：" + upstreamMessage
                : "DeepSeek HTTP " + status;

        if (status == 400 || status == 404 || status == 422) {
            return new DeepSeekException(
                    DeepSeekException.Kind.INVALID_REQUEST,
                    diagnostic,
                    status);
        }
        if (status == 401 || status == 403) {
            return new DeepSeekException(
                    DeepSeekException.Kind.AUTHENTICATION,
                    diagnostic,
                    status);
        }
        if (status == 402 || status == 429) {
            return new DeepSeekException(
                    DeepSeekException.Kind.QUOTA,
                    diagnostic,
                    status);
        }
        if (status == 408 || status == 504) {
            return new DeepSeekException(
                    DeepSeekException.Kind.TIMEOUT,
                    diagnostic,
                    status);
        }
        return new DeepSeekException(
                DeepSeekException.Kind.UPSTREAM,
                diagnostic,
                status);
    }

    private String extractUpstreamMessage(String body) {
        if (!StringUtils.hasText(body)) {
            return "";
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            String message = root.path("error").path("message").asText("");
            if (!StringUtils.hasText(message)) {
                message = root.path("message").asText("");
            }
            if (!StringUtils.hasText(message)) {
                return "";
            }
            String normalized = message.replaceAll("[\\r\\n\\t]+", " ").trim();
            return normalized.length() <= 300 ? normalized : normalized.substring(0, 300);
        } catch (JsonProcessingException ignored) {
            return "";
        }
    }

    private boolean hasTimeoutCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof TimeoutException
                    || current.getClass().getSimpleName().toLowerCase(Locale.ROOT).contains("timeout")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private record RawResponse(int status, String body) {
    }
}
