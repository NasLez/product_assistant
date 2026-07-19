package com.example.productassistant.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("prod")
public class ExternalCredentialValidator {

    private final AppProperties properties;

    public ExternalCredentialValidator(AppProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void validate() {
        if (isMissing(properties.getRainforest().getApiKey())) {
            throw new IllegalStateException("RAINFOREST_API_KEY is required in the prod profile");
        }
        if (isMissing(properties.getDeepseek().getApiKey())) {
            throw new IllegalStateException("DEEPSEEK_API_KEY is required in the prod profile");
        }
    }

    private boolean isMissing(String value) {
        return !StringUtils.hasText(value) || "replace_me".equalsIgnoreCase(value.trim());
    }
}
