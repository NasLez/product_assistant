package com.example.productassistant.api;

public record ApiResponse<T>(String code, String message, T data, String requestId) {

    public static <T> ApiResponse<T> success(T data, String requestId) {
        return new ApiResponse<>("OK", "success", data, requestId);
    }

    public static ApiResponse<Void> error(String code, String message, String requestId) {
        return new ApiResponse<>(code, message, null, requestId);
    }
}

