package com.example.productassistant.analysis;

import java.util.List;

public record ProductAnalysis(
        List<String> targetUsers,
        List<String> useCases,
        List<String> painPoints,
        List<SellingPoint> coreSellingPoints,
        String videoScript) {

    public ProductAnalysis {
        targetUsers = targetUsers == null ? List.of() : List.copyOf(targetUsers);
        useCases = useCases == null ? List.of() : List.copyOf(useCases);
        painPoints = painPoints == null ? List.of() : List.copyOf(painPoints);
        coreSellingPoints = coreSellingPoints == null ? List.of() : List.copyOf(coreSellingPoints);
    }

    public record SellingPoint(String claim, String evidence) {
    }
}

