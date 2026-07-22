package com.example.productassistant.security;

public class AuthenticationRateLimitException extends RuntimeException {

    public AuthenticationRateLimitException() {
        super("认证请求过于频繁，请稍后重试");
    }
}
