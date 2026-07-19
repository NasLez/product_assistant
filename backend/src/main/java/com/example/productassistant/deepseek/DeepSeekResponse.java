package com.example.productassistant.deepseek;

import com.fasterxml.jackson.databind.JsonNode;

public record DeepSeekResponse(JsonNode raw, String content) {
}

