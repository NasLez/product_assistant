package com.example.productassistant.api;

import com.example.productassistant.amazon.InvalidAmazonUrlException;
import com.example.productassistant.analysis.InvalidIdempotencyKeyException;
import com.example.productassistant.analysis.AnalysisNotFoundException;
import com.example.productassistant.auth.InvalidCredentialsException;
import com.example.productassistant.crypto.EncryptionConfigurationException;
import com.example.productassistant.crypto.VideoScriptEncryptionException;
import com.example.productassistant.deepseek.DeepSeekException;
import com.example.productassistant.observability.RequestIdFilter;
import com.example.productassistant.points.InsufficientPointsException;
import com.example.productassistant.points.PointRequestConflictException;
import com.example.productassistant.product.ProductDataIncompleteException;
import com.example.productassistant.rainforest.RainforestException;
import com.example.productassistant.security.AuthenticationRateLimitException;
import com.example.productassistant.user.EmailAlreadyRegisteredException;
import com.example.productassistant.user.InvalidRegistrationException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
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

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableRequest(
            HttpMessageNotReadableException exception,
            jakarta.servlet.http.HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.endsWith("/auth/login")) {
            return error(ApiErrorCode.INVALID_CREDENTIALS,
                    ApiErrorCode.INVALID_CREDENTIALS.getDefaultMessage());
        }
        if (path.endsWith("/auth/register")) {
            return error(ApiErrorCode.INVALID_REGISTRATION,
                    ApiErrorCode.INVALID_REGISTRATION.getDefaultMessage());
        }
        return error(ApiErrorCode.INVALID_AMAZON_URL,
                ApiErrorCode.INVALID_AMAZON_URL.getDefaultMessage());
    }

    @ExceptionHandler(InvalidIdempotencyKeyException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidIdempotencyKey(
            InvalidIdempotencyKeyException exception) {
        return error(ApiErrorCode.INVALID_IDEMPOTENCY_KEY,
                ApiErrorCode.INVALID_IDEMPOTENCY_KEY.getDefaultMessage());
    }

    @ExceptionHandler(InvalidRegistrationException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidRegistration(
            InvalidRegistrationException exception) {
        return error(ApiErrorCode.INVALID_REGISTRATION, exception.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidCredentials(
            InvalidCredentialsException exception) {
        return error(ApiErrorCode.INVALID_CREDENTIALS,
                ApiErrorCode.INVALID_CREDENTIALS.getDefaultMessage());
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<ApiResponse<Void>> handleEmailAlreadyRegistered(
            EmailAlreadyRegisteredException exception) {
        return error(ApiErrorCode.EMAIL_ALREADY_REGISTERED,
                ApiErrorCode.EMAIL_ALREADY_REGISTERED.getDefaultMessage());
    }

    @ExceptionHandler(AuthenticationRateLimitException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationRateLimit(
            AuthenticationRateLimitException exception) {
        return error(ApiErrorCode.AUTH_RATE_LIMITED,
                ApiErrorCode.AUTH_RATE_LIMITED.getDefaultMessage());
    }

    @ExceptionHandler(InsufficientPointsException.class)
    public ResponseEntity<ApiResponse<Void>> handleInsufficientPoints(
            InsufficientPointsException exception) {
        return error(ApiErrorCode.INSUFFICIENT_POINTS,
                ApiErrorCode.INSUFFICIENT_POINTS.getDefaultMessage());
    }

    @ExceptionHandler(PointRequestConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handlePointRequestConflict(
            PointRequestConflictException exception) {
        ApiErrorCode code = exception.getKind() == PointRequestConflictException.Kind.IN_PROGRESS
                ? ApiErrorCode.REQUEST_IN_PROGRESS
                : ApiErrorCode.REQUEST_ALREADY_FAILED;
        return error(code, code.getDefaultMessage());
    }

    @ExceptionHandler({EncryptionConfigurationException.class, VideoScriptEncryptionException.class})
    public ResponseEntity<ApiResponse<Void>> handleEncryption(RuntimeException exception) {
        log.error("Video script encryption operation failed: type={}",
                exception.getClass().getSimpleName());
        return error(ApiErrorCode.ENCRYPTION_ERROR,
                ApiErrorCode.ENCRYPTION_ERROR.getDefaultMessage());
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
