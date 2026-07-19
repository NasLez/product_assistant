package com.example.productassistant.api;

import com.example.productassistant.analysis.AnalysisApplicationService;
import com.example.productassistant.observability.RequestIdFilter;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/product-analyses")
public class ProductAnalysisController {

    private final AnalysisApplicationService applicationService;

    public ProductAnalysisController(AnalysisApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProductAnalysisView>> analyze(
            @Valid @RequestBody CreateAnalysisRequest request) {
        ProductAnalysisView view = applicationService.analyze(request.amazonUrl());
        return ResponseEntity.ok(ApiResponse.success(view, requestId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductAnalysisView>> getById(@PathVariable Long id) {
        ProductAnalysisView view = applicationService.getById(id);
        return ResponseEntity.ok(ApiResponse.success(view, requestId()));
    }

    private String requestId() {
        return MDC.get(RequestIdFilter.MDC_KEY);
    }
}

