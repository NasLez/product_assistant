package com.example.productassistant.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateAnalysisRequest(
        @NotBlank(message = "Amazon 商品链接不能为空")
        @Size(max = 2048, message = "Amazon 商品链接过长")
        String amazonUrl) {
}

