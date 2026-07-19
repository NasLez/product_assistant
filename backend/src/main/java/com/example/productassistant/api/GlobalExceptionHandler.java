package com.example.productassistant.api;

import com.example.productassistant.amazon.InvalidAmazonUrlException;
import com.example.productassistant.analysis.AnalysisNotFoundException;
import com.example.productassistant.deepseek.DeepSeekException;
import com.example.productassistant.observability.RequestIdFilter;
import com.example.productassistant.product.ProductDataIncompleteException;
import com.example.productassistant.rainforest.RainforestException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({InvalidAmazonUrlException.class, ConstraintViolationException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(RuntimeException exception) {
        return error(ApiErrorCode.INVALID_AMAZON_URL, exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().isEmpty()
                ? ApiErrorCode.INVALID_AMAZON_URL.getDefaultMessage()
                : exception.getBindingResult().getFieldErrors().getFirst().getDefaultMessage();
        return error(ApiErrorCode.INVALID_AMAZON_URL, message);
    }

    @ExceptionHandler(AnalysisNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(AnalysisNotFoundException exception) {
        return error(ApiErrorCode.ANALYSIS_NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(ProductDataIncompleteException.class)
    public ResponseEntity<ApiResponse<Void>> handleIncomplete(ProductDataIncompleteException exception) {
        return error(ApiErrorCode.PRODUCT_DATA_INCOMPLETE, exception.getMessage());
    }

    @ExceptionHandler(RainforestException.class)
    public ResponseEntity<ApiResponse<Void>> handleRainforest(RainforestException exception) {
        ApiErrorCode code = switch (exception.getKind()) {
            case BUSY -> ApiErrorCode.RAINFOREST_BUSY;
            case QUOTA -> ApiErrorCode.UPSTREAM_QUOTA_EXCEEDED;
            case NOT_FOUND, INVALID_RESPONSE -> ApiErrorCode.PRODUCT_DATA_INCOMPLETE;
            case TIMEOUT -> ApiErrorCode.UPSTREAM_TIMEOUT;
            case UPSTREAM -> ApiErrorCode.RAINFOREST_ERROR;
        };
        return error(code, code.getDefaultMessage());
    }

    @ExceptionHandler(DeepSeekException.class)
    public ResponseEntity<ApiResponse<Void>> handleDeepSeek(DeepSeekException exception) {
        log.warn("DeepSeek request failed: kind={}, upstreamStatus={}, reason={}",
                exception.getKind(), exception.getUpstreamStatus(), exception.getMessage());
        ApiErrorCode code = switch (exception.getKind()) {
            case AUTHENTICATION -> ApiErrorCode.DEEPSEEK_AUTHENTICATION_FAILED;
            case INVALID_REQUEST -> ApiErrorCode.DEEPSEEK_INVALID_REQUEST;
            case QUOTA -> ApiErrorCode.UPSTREAM_QUOTA_EXCEEDED;
            case TIMEOUT -> ApiErrorCode.UPSTREAM_TIMEOUT;
            case UPSTREAM, INVALID_RESPONSE -> ApiErrorCode.DEEPSEEK_ERROR;
        };
        return error(code, code.getDefaultMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception) {
        log.error("Unhandled request failure", exception);
        return error(ApiErrorCode.INTERNAL_ERROR, ApiErrorCode.INTERNAL_ERROR.getDefaultMessage());
    }

    private ResponseEntity<ApiResponse<Void>> error(ApiErrorCode code, String message) {
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.error(code.name(), message, requestId));
    }
}
