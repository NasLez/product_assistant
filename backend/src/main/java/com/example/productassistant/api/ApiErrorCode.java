package com.example.productassistant.api;

import org.springframework.http.HttpStatus;

public enum ApiErrorCode {
    INVALID_AMAZON_URL(HttpStatus.BAD_REQUEST, "Amazon 商品链接无效"),
    INVALID_IDEMPOTENCY_KEY(HttpStatus.BAD_REQUEST, "幂等请求键缺失或格式无效"),
    INVALID_REGISTRATION(HttpStatus.BAD_REQUEST, "邮箱或密码格式无效"),
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "请先登录"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "邮箱或密码错误"),
    INSUFFICIENT_POINTS(HttpStatus.PAYMENT_REQUIRED, "积分不足"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "请求未通过安全校验"),
    ANALYSIS_NOT_FOUND(HttpStatus.NOT_FOUND, "分析记录不存在"),
    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "该邮箱已注册"),
    REQUEST_IN_PROGRESS(HttpStatus.CONFLICT, "相同请求正在处理中"),
    REQUEST_ALREADY_FAILED(HttpStatus.CONFLICT, "相同请求此前已失败，请使用新的请求键重试"),
    RAINFOREST_BUSY(HttpStatus.CONFLICT, "商品服务繁忙，请稍后重试"),
    AUTH_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "认证请求过于频繁，请稍后重试"),
    PRODUCT_DATA_INCOMPLETE(HttpStatus.UNPROCESSABLE_ENTITY, "商品信息不足，无法分析"),
    UPSTREAM_QUOTA_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "外部服务额度不足或请求受限"),
    RAINFOREST_ERROR(HttpStatus.BAD_GATEWAY, "获取商品信息失败"),
    DEEPSEEK_AUTHENTICATION_FAILED(HttpStatus.BAD_GATEWAY, "DeepSeek API Key 无效或没有模型访问权限"),
    DEEPSEEK_INVALID_REQUEST(HttpStatus.BAD_GATEWAY, "DeepSeek 模型或请求参数配置无效"),
    DEEPSEEK_ERROR(HttpStatus.BAD_GATEWAY, "生成产品分析失败"),
    UPSTREAM_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "外部服务请求超时"),
    ENCRYPTION_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "加密数据处理失败"),
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
