package com.example.productassistant.points;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class StalePointReservationRecoveryJob {

    private static final Logger log = LoggerFactory.getLogger(StalePointReservationRecoveryJob.class);
    private static final int BATCH_SIZE = 100;

    private final PointTransactionMapper transactionMapper;
    private final PointsService pointsService;
    private final Duration reservationTimeout;

    public StalePointReservationRecoveryJob(
            PointTransactionMapper transactionMapper,
            PointsService pointsService,
            @Value("${app.points.reservation-timeout:15m}") Duration reservationTimeout) {
        this.transactionMapper = transactionMapper;
        this.pointsService = pointsService;
        if (reservationTimeout.isZero() || reservationTimeout.isNegative()) {
            throw new IllegalArgumentException("Point reservation timeout must be positive");
        }
        this.reservationTimeout = reservationTimeout;
    }

    @Scheduled(fixedDelayString = "${POINT_RESERVATION_RECOVERY_INTERVAL:1m}")
    public void recoverStaleReservations() {
        LocalDateTime cutoff = LocalDateTime.ofInstant(
                java.time.Instant.now().minus(reservationTimeout), ZoneOffset.UTC);
        List<Long> transactionIds = transactionMapper.findStaleReservationIds(cutoff, BATCH_SIZE);
        for (Long transactionId : transactionIds) {
            try {
                pointsService.refund(transactionId);
            } catch (RuntimeException exception) {
                log.error("Failed to refund stale point reservation: transactionId={}",
                        transactionId, exception);
            }
        }
        if (!transactionIds.isEmpty()) {
            log.info("Processed stale point reservations: count={}", transactionIds.size());
        }
    }
}
