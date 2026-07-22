package com.example.productassistant.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

public interface VideoScriptCipher {

    EncryptedText encrypt(String plaintext, String associatedData);

    String decrypt(EncryptedText encrypted, String associatedData);
}

@Component
final class AesGcmVideoScriptCipher implements VideoScriptCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_LENGTH_BYTES = 32;
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final Pattern KEY_VERSION_PATTERN = Pattern.compile("[A-Za-z0-9._-]{1,32}");

    private final String activeKeyVersion;
    private final Map<String, SecretKeySpec> keys;
    private final SecureRandom secureRandom = new SecureRandom();

    AesGcmVideoScriptCipher(VideoScriptEncryptionProperties properties) {
        this.activeKeyVersion = normalizeActiveVersion(properties.getActiveKeyVersion());
        this.keys = parseKeys(properties.getKeys());
        if (!keys.containsKey(activeKeyVersion)) {
            throw new EncryptionConfigurationException(
                    "The active video-script encryption key version is not configured");
        }
    }

    @Override
    public EncryptedText encrypt(String plaintext, String associatedData) {
        if (plaintext == null) {
            throw new IllegalArgumentException("plaintext must not be null");
        }
        byte[] iv = new byte[IV_LENGTH_BYTES];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = initializedCipher(
                    Cipher.ENCRYPT_MODE,
                    keys.get(activeKeyVersion),
                    iv,
                    associatedData);
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return new EncryptedText(ciphertext, iv, activeKeyVersion);
        } catch (GeneralSecurityException exception) {
            throw new VideoScriptEncryptionException(exception);
        }
    }

    @Override
    public String decrypt(EncryptedText encrypted, String associatedData) {
        SecretKeySpec key = keys.get(encrypted.keyVersion());
        if (key == null || encrypted.iv().length != IV_LENGTH_BYTES) {
            throw new VideoScriptEncryptionException();
        }
        try {
            Cipher cipher = initializedCipher(
                    Cipher.DECRYPT_MODE,
                    key,
                    encrypted.iv(),
                    associatedData);
            byte[] plaintext = cipher.doFinal(encrypted.ciphertext());
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw new VideoScriptEncryptionException(exception);
        }
    }

    private Cipher initializedCipher(
            int mode,
            SecretKeySpec key,
            byte[] iv,
            String associatedData) throws GeneralSecurityException {
        if (associatedData == null) {
            throw new IllegalArgumentException("associatedData must not be null");
        }
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(mode, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
        cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
        return cipher;
    }

    private static String normalizeActiveVersion(String version) {
        String normalized = version == null ? "" : version.trim();
        if (!KEY_VERSION_PATTERN.matcher(normalized).matches()) {
            throw new EncryptionConfigurationException(
                    "A valid active video-script encryption key version is required");
        }
        return normalized;
    }

    private static Map<String, SecretKeySpec> parseKeys(String configuredKeys) {
        if (!StringUtils.hasText(configuredKeys)) {
            throw new EncryptionConfigurationException("Video-script encryption keys are required");
        }

        Map<String, SecretKeySpec> parsed = new LinkedHashMap<>();
        for (String entry : configuredKeys.split(",")) {
            String candidate = entry.trim();
            int separator = candidate.indexOf('=');
            if (separator <= 0 || separator == candidate.length() - 1) {
                throw new EncryptionConfigurationException(
                        "Video-script encryption keys must use version=base64Key entries");
            }
            String version = candidate.substring(0, separator).trim();
            String encodedKey = candidate.substring(separator + 1).trim();
            if (!KEY_VERSION_PATTERN.matcher(version).matches()) {
                throw new EncryptionConfigurationException("An encryption key version is invalid");
            }
            byte[] keyBytes;
            try {
                keyBytes = Base64.getDecoder().decode(encodedKey);
            } catch (IllegalArgumentException exception) {
                throw new EncryptionConfigurationException("An encryption key is not valid Base64", exception);
            }
            if (keyBytes.length != KEY_LENGTH_BYTES) {
                throw new EncryptionConfigurationException("Every video-script encryption key must be 32 bytes");
            }
            if (parsed.putIfAbsent(version, new SecretKeySpec(keyBytes, "AES")) != null) {
                throw new EncryptionConfigurationException("Duplicate video-script encryption key version");
            }
        }
        return Map.copyOf(parsed);
    }
}
