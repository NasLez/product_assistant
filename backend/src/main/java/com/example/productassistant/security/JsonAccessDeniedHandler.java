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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public JsonAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        ApiErrorCode code = ApiErrorCode.ACCESS_DENIED;
        response.setStatus(code.getStatus().value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        objectMapper.writeValue(response.getOutputStream(),
                ApiResponse.error(code.name(), code.getDefaultMessage(), requestId));
    }
}
