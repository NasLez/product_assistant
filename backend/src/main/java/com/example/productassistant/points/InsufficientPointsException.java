package com.example.productassistant.points;

public class InsufficientPointsException extends RuntimeException {

    public InsufficientPointsException() {
        super("积分不足");
    }
}
