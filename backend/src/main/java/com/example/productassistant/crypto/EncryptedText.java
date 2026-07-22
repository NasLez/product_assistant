package com.example.productassistant.crypto;

import java.util.Objects;

/**
 * Authenticated ciphertext and the metadata required to decrypt it.
 */
public record EncryptedText(byte[] ciphertext, byte[] iv, String keyVersion) {

    public EncryptedText {
        ciphertext = Objects.requireNonNull(ciphertext, "ciphertext").clone();
        iv = Objects.requireNonNull(iv, "iv").clone();
        keyVersion = Objects.requireNonNull(keyVersion, "keyVersion");
    }

    @Override
    public byte[] ciphertext() {
        return ciphertext.clone();
    }

    @Override
    public byte[] iv() {
        return iv.clone();
    }
}
