package com.example.productassistant.rainforest;

public class RainforestException extends RuntimeException {

    public enum Kind {
        BUSY,
        QUOTA,
        NOT_FOUND,
        TIMEOUT,
        UPSTREAM,
        INVALID_RESPONSE
    }

    private final Kind kind;

    public RainforestException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public RainforestException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }
}

