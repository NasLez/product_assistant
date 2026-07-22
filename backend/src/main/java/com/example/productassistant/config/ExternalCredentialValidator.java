package com.example.productassistant.config;

import java.net.URI;

import com.example.productassistant.crypto.VideoScriptEncryptionProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("prod")
public class ExternalCredentialValidator {

    private final AppProperties properties;
    private final VideoScriptEncryptionProperties encryptionProperties;
    private final boolean secureSessionCookie;

    public ExternalCredentialValidator(
            AppProperties properties,
            VideoScriptEncryptionProperties encryptionProperties,
            @Value("${server.servlet.session.cookie.secure:false}") boolean secureSessionCookie) {
        this.properties = properties;
        this.encryptionProperties = encryptionProperties;
        this.secureSessionCookie = secureSessionCookie;
    }

    @PostConstruct
    public void validate() {
        if (isMissing(properties.getRainforest().getApiKey())) {
            throw new IllegalStateException("RAINFOREST_API_KEY is required in the prod profile");
        }
        if (isMissing(properties.getDeepseek().getApiKey())) {
            throw new IllegalStateException("DEEPSEEK_API_KEY is required in the prod profile");
        }
        requireHttpsBaseUrl(properties.getRainforest().getBaseUrl(), "RAINFOREST_BASE_URL");
        requireHttpsBaseUrl(properties.getDeepseek().getBaseUrl(), "DEEPSEEK_BASE_URL");
        if (!secureSessionCookie) {
            throw new IllegalStateException("SESSION_COOKIE_SECURE must be true in the prod profile");
        }
        if (isMissing(encryptionProperties.getActiveKeyVersion())) {
            throw new IllegalStateException(
                    "VIDEO_SCRIPT_ACTIVE_KEY_VERSION is required in the prod profile");
        }
        if (!containsActiveEncryptionKey(
                encryptionProperties.getKeys(), encryptionProperties.getActiveKeyVersion())) {
            throw new IllegalStateException(
                    "VIDEO_SCRIPT_ENCRYPTION_KEYS must include the active key version in the prod profile");
        }
    }

    private boolean isMissing(String value) {
        return !StringUtils.hasText(value) || "replace_me".equalsIgnoreCase(value.trim());
    }

    private boolean containsActiveEncryptionKey(String configuredKeys, String activeVersion) {
        if (!StringUtils.hasText(configuredKeys)) {
            return false;
        }
        String expectedVersion = activeVersion.trim();
        for (String entry : configuredKeys.split(",")) {
            int separator = entry.indexOf('=');
            if (separator > 0 && expectedVersion.equals(entry.substring(0, separator).trim())) {
                return StringUtils.hasText(entry.substring(separator + 1));
            }
        }
        return false;
    }

    private void requireHttpsBaseUrl(String value, String configurationName) {
        try {
            URI uri = URI.create(value == null ? "" : value.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || !StringUtils.hasText(uri.getHost())
                    || uri.getUserInfo() != null) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    configurationName + " must be an HTTPS URL without user info in the prod profile");
        }
    }
}
