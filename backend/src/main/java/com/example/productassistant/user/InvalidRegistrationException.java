package com.example.productassistant.user;

public class InvalidRegistrationException extends RuntimeException {

    public InvalidRegistrationException(String message) {
        super(message);
    }
}
