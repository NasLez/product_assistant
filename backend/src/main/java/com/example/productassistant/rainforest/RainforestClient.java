package com.example.productassistant.rainforest;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

import com.example.productassistant.amazon.AmazonProductKey;
import com.example.productassistant.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Component
public class RainforestClient {

    private final WebClient webClient;
    private final AppProperties properties;

    public RainforestClient(
            @Qualifier("rainforestWebClient") WebClient webClient,
            AppProperties properties) {
        this.webClient = webClient;
        this.properties = properties;
    }

    public RainforestResponse fetchProduct(AmazonProductKey key) {
        try {
            JsonNode root = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/request")
                            .queryParam("api_key", properties.getRainforest().getApiKey())
                            .queryParam("type", "product")
                            .queryParam("amazon_domain", key.amazonDomain())
                            .queryParam("asin", key.asin())
                            .build())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .map(body -> fromHttpStatus(response.statusCode().value(), body)))
                    .bodyToMono(JsonNode.class)
                    .block();

            if (root == null || !root.isObject()) {
                throw new RainforestException(
                        RainforestException.Kind.INVALID_RESPONSE,
                        "Rainforest 返回了空响应");
            }

            JsonNode requestInfo = root.path("request_info");
            if (!requestInfo.path("success").asBoolean(false)) {
                throw classifyMessage(firstText(
                        requestInfo.path("message"),
                        root.path("error").path("message"),
                        root.path("message")));
            }
            if (!root.path("product").isObject()) {
                throw new RainforestException(
                        RainforestException.Kind.NOT_FOUND,
                        "Rainforest 未返回商品信息");
            }

            String requestId = firstText(
                    root.path("request_metadata").path("id"),
                    root.path("request_metadata").path("created_at"));
            int credits = requestInfo.path("credits_used_this_request")
                    .asInt(requestInfo.path("credits_used").asInt(0));
            return new RainforestResponse(root, requestId, credits);
        } catch (RainforestException exception) {
            throw exception;
        } catch (WebClientResponseException exception) {
            throw fromHttpStatus(exception.getStatusCode().value(), exception.getResponseBodyAsString());
        } catch (WebClientRequestException exception) {
            if (hasTimeoutCause(exception)) {
                throw new RainforestException(
                        RainforestException.Kind.TIMEOUT,
                        "Rainforest 请求超时",
                        exception);
            }
            throw new RainforestException(
                    RainforestException.Kind.UPSTREAM,
                    "无法连接 Rainforest",
                    exception);
        }
    }

    private RainforestException fromHttpStatus(int status, String body) {
        if (status == 404) {
            return new RainforestException(RainforestException.Kind.NOT_FOUND, "Rainforest 未找到商品");
        }
        if (status == 429) {
            return new RainforestException(RainforestException.Kind.QUOTA, "Rainforest 请求受限或额度不足");
        }
        return classifyMessage(body);
    }

    private RainforestException classifyMessage(String message) {
        String safeMessage = message == null || message.isBlank() ? "Rainforest 服务返回错误" : message;
        String normalized = safeMessage.toLowerCase(Locale.ROOT);
        if (normalized.contains("credit") || normalized.contains("quota") || normalized.contains("limit")) {
            return new RainforestException(RainforestException.Kind.QUOTA, "Rainforest 请求受限或额度不足");
        }
        if (normalized.contains("not found") || normalized.contains("no product")) {
            return new RainforestException(RainforestException.Kind.NOT_FOUND, "Rainforest 未找到商品");
        }
        return new RainforestException(RainforestException.Kind.UPSTREAM, "Rainforest 服务返回错误");
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

    private String firstText(JsonNode... nodes) {
        for (JsonNode node : nodes) {
            if (node != null && node.isValueNode() && !node.asText().isBlank()) {
                return node.asText();
            }
        }
        return "";
    }
}

