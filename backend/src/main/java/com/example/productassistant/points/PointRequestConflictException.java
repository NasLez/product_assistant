package com.example.productassistant.points;

public class PointRequestConflictException extends RuntimeException {

    public enum Kind {
        IN_PROGRESS,
        ALREADY_FAILED
    }

    private final Kind kind;

    public PointRequestConflictException(Kind kind) {
        super(kind == Kind.IN_PROGRESS ? "相同请求正在处理中" : "相同请求此前已失败");
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }
}
