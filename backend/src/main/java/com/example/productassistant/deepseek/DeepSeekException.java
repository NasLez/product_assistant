package com.example.productassistant.deepseek;

public class DeepSeekException extends RuntimeException {

    public enum Kind {
        AUTHENTICATION,
        INVALID_REQUEST,
        QUOTA,
        TIMEOUT,
        UPSTREAM,
        INVALID_RESPONSE
    }

    private final Kind kind;
    private final Integer upstreamStatus;

    public DeepSeekException(Kind kind, String message) {
        super(message);
        this.kind = kind;
        this.upstreamStatus = null;
    }

    public DeepSeekException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
        this.upstreamStatus = null;
    }

    public DeepSeekException(Kind kind, String message, int upstreamStatus) {
        super(message);
        this.kind = kind;
        this.upstreamStatus = upstreamStatus;
    }

    public Kind getKind() {
        return kind;
    }

    public Integer getUpstreamStatus() {
        return upstreamStatus;
    }
}
