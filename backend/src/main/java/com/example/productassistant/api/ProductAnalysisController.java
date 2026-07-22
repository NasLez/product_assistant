package com.example.productassistant.api;

import com.example.productassistant.analysis.AnalysisSubmissionService;
import com.example.productassistant.analysis.AnalysisSubmissionView;
import com.example.productassistant.observability.RequestIdFilter;
import com.example.productassistant.user.AuthenticatedUser;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/product-analyses")
public class ProductAnalysisController {

    private final AnalysisSubmissionService submissionService;

    public ProductAnalysisController(AnalysisSubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AnalysisSubmissionView>> analyze(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestHeader(name = "X-Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateAnalysisRequest request) {
        AnalysisSubmissionView view = submissionService.submit(
                user.getId(),
                idempotencyKey,
                request.amazonUrl());
        return ResponseEntity.ok(ApiResponse.success(view, requestId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductAnalysisView>> getById(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id) {
        ProductAnalysisView view = submissionService.getById(user.getId(), id);
        return ResponseEntity.ok(ApiResponse.success(view, requestId()));
    }

    private String requestId() {
        return MDC.get(RequestIdFilter.MDC_KEY);
    }
}
