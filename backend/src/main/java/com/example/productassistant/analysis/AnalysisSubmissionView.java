package com.example.productassistant.analysis;

import com.example.productassistant.api.ProductAnalysisView;

public record AnalysisSubmissionView(ProductAnalysisView result, int remainingPoints) {
}
