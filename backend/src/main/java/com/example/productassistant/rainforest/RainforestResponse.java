package com.example.productassistant.rainforest;

import com.fasterxml.jackson.databind.JsonNode;

public record RainforestResponse(JsonNode root, String upstreamRequestId, int creditsUsed) {
}

