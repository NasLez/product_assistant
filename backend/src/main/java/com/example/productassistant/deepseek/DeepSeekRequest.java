package com.example.productassistant.deepseek;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DeepSeekRequest(
        String model,
        List<Message> messages,
        boolean stream,
        Thinking thinking,
        @JsonProperty("max_tokens") int maxTokens,
        @JsonProperty("response_format") ResponseFormat responseFormat) {

    public record Message(String role, String content) {
    }

    public record ResponseFormat(String type) {
    }

    public record Thinking(String type) {
    }

    public static DeepSeekRequest jsonRequest(String model, String systemPrompt, String userPrompt) {
        return new DeepSeekRequest(
                model,
                List.of(new Message("system", systemPrompt), new Message("user", userPrompt)),
                false,
                new Thinking("disabled"),
                4096,
                new ResponseFormat("json_object"));
    }
}
