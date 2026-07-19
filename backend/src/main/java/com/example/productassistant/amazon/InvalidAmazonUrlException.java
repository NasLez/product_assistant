package com.example.productassistant.amazon;

public class InvalidAmazonUrlException extends RuntimeException {

    public InvalidAmazonUrlException(String message) {
        super(message);
    }
}

