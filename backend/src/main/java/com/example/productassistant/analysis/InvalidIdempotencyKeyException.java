package com.example.productassistant.analysis;

public class InvalidIdempotencyKeyException extends RuntimeException {

    public InvalidIdempotencyKeyException() {
        super("X-Idempotency-Key must be a valid UUID");
    }
}
