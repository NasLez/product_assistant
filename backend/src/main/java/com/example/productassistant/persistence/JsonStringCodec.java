package com.example.productassistant.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class JsonStringCodec {

    private final ObjectMapper objectMapper;

    public JsonStringCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String write(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("JSON value must not be null");
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize JSON value", exception);
        }
    }

    public <T> T read(String json, Class<T> type) {
        validate(json);
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to deserialize stored JSON", exception);
        }
    }

    public <T> T read(String json, TypeReference<T> type) {
        validate(json);
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to deserialize stored JSON", exception);
        }
    }

    public void validate(String json) {
        if (!StringUtils.hasText(json)) {
            throw new IllegalArgumentException("JSON text must not be blank");
        }
        try {
            objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("JSON text is invalid", exception);
        }
    }
}

