package com.example.productassistant.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AuthenticationRateLimiter {

    private final int maximumAttempts;
    private final Duration window;
    private final Cache<String, AttemptWindow> attempts;

    public AuthenticationRateLimiter(
            @Value("${app.auth.rate-limit-attempts:10}") int maximumAttempts,
            @Value("${app.auth.rate-limit-window:10m}") Duration window) {
        if (maximumAttempts < 1 || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("Authentication rate limit configuration is invalid");
        }
        this.maximumAttempts = maximumAttempts;
        this.window = window;
        this.attempts = Caffeine.newBuilder()
                .maximumSize(20_000)
                .expireAfterAccess(window.multipliedBy(2))
                .build();
    }

    public void check(String operation, String remoteAddress, String email) {
        Instant now = Instant.now();
        String normalizedOperation = operation.toLowerCase(Locale.ROOT);
        String normalizedAddress = String.valueOf(remoteAddress).trim();
        AttemptWindow ipWindow = increment(
                key(normalizedOperation, "ip", normalizedAddress),
                now);
        int ipLimit = "register".equals(normalizedOperation)
                ? maximumAttempts
                : (int) Math.min(100_000L, (long) maximumAttempts * 5);
        if (ipWindow.count() > ipLimit) {
            throw new AuthenticationRateLimitException();
        }

        AttemptWindow identityWindow = increment(
                key(
                        normalizedOperation,
                        "identity",
                        normalizedAddress + '\n' + normalizeEmail(email)),
                now);
        if (identityWindow.count() > maximumAttempts) {
            throw new AuthenticationRateLimitException();
        }
    }

    private AttemptWindow increment(String key, Instant now) {
        return attempts.asMap().compute(key, (ignored, current) -> {
            if (current == null || !now.isBefore(current.startedAt().plus(window))) {
                return new AttemptWindow(now, 1);
            }
            return new AttemptWindow(current.startedAt(), current.count() + 1);
        });
    }

    public void reset(String operation, String remoteAddress, String email) {
        String normalizedOperation = operation.toLowerCase(Locale.ROOT);
        String normalizedAddress = String.valueOf(remoteAddress).trim();
        attempts.invalidate(key(
                normalizedOperation,
                "identity",
                normalizedAddress + '\n' + normalizeEmail(email)));
    }

    private String normalizeEmail(String email) {
        return Normalizer.normalize(String.valueOf(email).trim(), Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
    }

    private String key(String operation, String scope, String value) {
        String canonical = operation + '\n' + scope + '\n' + value;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record AttemptWindow(Instant startedAt, int count) {}
}
