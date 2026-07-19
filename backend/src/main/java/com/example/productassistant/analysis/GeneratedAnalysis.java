package com.example.productassistant.analysis;

import com.fasterxml.jackson.databind.JsonNode;

public record GeneratedAnalysis(ProductAnalysis analysis, JsonNode rawResponse) {
}

