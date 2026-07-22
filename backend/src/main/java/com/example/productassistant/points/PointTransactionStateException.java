package com.example.productassistant.points;

public class PointTransactionStateException extends RuntimeException {

    public PointTransactionStateException(long transactionId) {
        super("Point transaction cannot be settled: " + transactionId);
    }
}
