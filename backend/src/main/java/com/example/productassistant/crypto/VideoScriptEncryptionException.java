package com.example.productassistant.crypto;

/**
 * Deliberately contains no cryptographic details that could leak to API clients.
 */
public class VideoScriptEncryptionException extends RuntimeException {

    public VideoScriptEncryptionException() {
        super("Unable to process the encrypted video script");
    }

    public VideoScriptEncryptionException(Throwable cause) {
        super("Unable to process the encrypted video script", cause);
    }
}
