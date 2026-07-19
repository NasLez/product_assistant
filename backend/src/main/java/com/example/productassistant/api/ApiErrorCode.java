package com.example.productassistant.api;

import org.springframework.http.HttpStatus;

public enum ApiErrorCode {
    INVALID_AMAZON_URL(HttpStatus.BAD_REQUEST, "Amazon 商品链接无效"),
    ANALYSIS_NOT_FOUND(HttpStatus.NOT_FOUND, "分析记录不存在"),
    RAINFOREST_BUSY(HttpStatus.CONFLICT, "商品服务繁忙，请稍后重试"),
    PRODUCT_DATA_INCOMPLETE(HttpStatus.UNPROCESSABLE_ENTITY, "商品信息不足，无法分析"),
    UPSTREAM_QUOTA_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "外部服务额度不足或请求受限"),
    RAINFOREST_ERROR(HttpStatus.BAD_GATEWAY, "获取商品信息失败"),
    DEEPSEEK_AUTHENTICATION_FAILED(HttpStatus.BAD_GATEWAY, "DeepSeek API Key 无效或没有模型访问权限"),
    DEEPSEEK_INVALID_REQUEST(HttpStatus.BAD_GATEWAY, "DeepSeek 模型或请求参数配置无效"),
    DEEPSEEK_ERROR(HttpStatus.BAD_GATEWAY, "生成产品分析失败"),
    UPSTREAM_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "外部服务请求超时"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "系统内部错误");

    private final HttpStatus status;
    private final String defaultMessage;

    ApiErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getStatus() { return status; }
    public String getDefaultMessage() { return defaultMessage; }
}
