package com.example.productassistant.analysis;

import java.util.Locale;
import java.util.UUID;

import com.example.productassistant.api.ProductAnalysisView;
import com.example.productassistant.points.PointReservation;
import com.example.productassistant.points.PointsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AnalysisSubmissionService {

    private final PointsService pointsService;
    private final AnalysisApplicationService analysisService;
    private final UserAnalysisAccessMapper accessMapper;
    private final TransactionTemplate transactionTemplate;

    public AnalysisSubmissionService(
            PointsService pointsService,
            AnalysisApplicationService analysisService,
            UserAnalysisAccessMapper accessMapper,
            TransactionTemplate transactionTemplate) {
        this.pointsService = pointsService;
        this.analysisService = analysisService;
        this.accessMapper = accessMapper;
        this.transactionTemplate = transactionTemplate;
    }

    public AnalysisSubmissionView submit(long userId, String idempotencyKey, String amazonUrl) {
        UUID requestId = parseIdempotencyKey(idempotencyKey);
        PointReservation reservation = pointsService.reserve(userId, requestId);
        if (!reservation.requiresAnalysis()) {
            if (reservation.state() != PointReservation.State.SETTLED
                    || reservation.analysisResultId() == null
                    || !accessMapper.hasAccess(userId, reservation.analysisResultId())) {
                throw new IllegalStateException("Settled analysis request has no matching access record");
            }
            ProductAnalysisView existing = analysisService.getById(reservation.analysisResultId());
            return new AnalysisSubmissionView(existing, reservation.remainingPoints());
        }

        try {
            ProductAnalysisView result = analysisService.analyze(amazonUrl);
            int[] remainingPoints = {reservation.remainingPoints()};
            transactionTemplate.executeWithoutResult(status -> {
                pointsService.settle(reservation.transactionId(), result.id());
                if (accessMapper.grantAccess(
                        userId,
                        result.id(),
                        reservation.transactionId(),
                        requestId.toString()) != 1) {
                    throw new IllegalStateException("Unable to grant analysis access");
                }
                remainingPoints[0] = pointsService.currentBalance(userId);
            });
            return new AnalysisSubmissionView(result, remainingPoints[0]);
        } catch (RuntimeException | Error failure) {
            try {
                pointsService.refund(reservation.transactionId());
            } catch (RuntimeException refundFailure) {
                failure.addSuppressed(refundFailure);
            }
            throw failure;
        }
    }

    public ProductAnalysisView getById(long userId, long analysisResultId) {
        if (!accessMapper.hasAccess(userId, analysisResultId)) {
            throw new AnalysisNotFoundException("分析记录不存在");
        }
        return analysisService.getById(analysisResultId);
    }

    private UUID parseIdempotencyKey(String value) {
        if (value == null) {
            throw new InvalidIdempotencyKeyException();
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        UUID parsed;
        try {
            parsed = UUID.fromString(normalized);
        } catch (IllegalArgumentException exception) {
            throw new InvalidIdempotencyKeyException();
        }
        if (!parsed.toString().equals(normalized)) {
            throw new InvalidIdempotencyKeyException();
        }
        return parsed;
    }
}
