package com.example.productassistant.points;

public record PointReservation(
        State state,
        long transactionId,
        Long analysisResultId,
        int remainingPoints) {

    public enum State {
        NEW,
        SETTLED,
        RESERVED,
        REFUNDED
    }

    public boolean requiresAnalysis() {
        return state == State.NEW;
    }
}
