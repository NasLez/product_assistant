package com.example.productassistant.crypto;

public class EncryptionConfigurationException extends RuntimeException {

    public EncryptionConfigurationException(String message) {
        super(message);
    }

    public EncryptionConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
