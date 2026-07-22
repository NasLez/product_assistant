package com.example.productassistant.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.example.productassistant.api.ApiErrorCode;
import com.example.productassistant.api.ApiResponse;
import com.example.productassistant.observability.RequestIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JsonAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authenticationException) throws IOException {
        writeError(response, ApiErrorCode.AUTHENTICATION_REQUIRED);
    }

    private void writeError(HttpServletResponse response, ApiErrorCode code) throws IOException {
        response.setStatus(code.getStatus().value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        objectMapper.writeValue(response.getOutputStream(),
                ApiResponse.error(code.name(), code.getDefaultMessage(), requestId));
    }
}
