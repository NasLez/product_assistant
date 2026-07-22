package com.example.productassistant.points;

import java.util.Objects;
import java.util.UUID;

import com.example.productassistant.user.AppUserMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PointsService {

    private static final String ANALYSIS_DEBIT = "ANALYSIS_DEBIT";
    private static final String RESERVED = "RESERVED";
    private static final String SETTLED = "SETTLED";
    private static final String REFUNDED = "REFUNDED";

    private final AppUserMapper userMapper;
    private final PointTransactionMapper transactionMapper;
    private final TransactionTemplate transactionTemplate;

    public PointsService(
            AppUserMapper userMapper,
            PointTransactionMapper transactionMapper,
            TransactionTemplate transactionTemplate) {
        this.userMapper = userMapper;
        this.transactionMapper = transactionMapper;
        this.transactionTemplate = transactionTemplate;
    }

    public PointReservation reserve(long userId, UUID requestId) {
        Objects.requireNonNull(requestId, "requestId");
        String requestKey = requestId.toString();
        try {
            PointReservation reservation = transactionTemplate.execute(
                    status -> reserveInTransaction(userId, requestKey));
            return Objects.requireNonNull(reservation, "Reservation transaction returned no result");
        } catch (DuplicateKeyException exception) {
            PointTransactionEntity existing = findExisting(userId, requestKey);
            if (existing == null) {
                throw exception;
            }
            return classifyExisting(existing);
        } catch (InsufficientPointsException exception) {
            PointTransactionEntity existing = findExisting(userId, requestKey);
            if (existing == null) {
                throw exception;
            }
            return classifyExisting(existing);
        }
    }

    private PointReservation reserveInTransaction(long userId, String requestId) {
        PointTransactionEntity existing = transactionMapper.findByUserAndRequest(userId, requestId);
        if (existing != null) {
            return classifyExisting(existing);
        }

        if (userMapper.debitOnePoint(userId) != 1) {
            throw new InsufficientPointsException();
        }
        int remainingPoints = currentBalanceRequired(userId);

        PointTransactionEntity transaction = new PointTransactionEntity();
        transaction.setUserId(userId);
        transaction.setRequestId(requestId);
        transaction.setTransactionType(ANALYSIS_DEBIT);
        transaction.setStatus(RESERVED);
        transaction.setDelta(-1);
        transaction.setBalanceAfter(remainingPoints);
        transactionMapper.insert(transaction);

        return new PointReservation(
                PointReservation.State.NEW,
                transaction.getId(),
                null,
                remainingPoints);
    }

    private PointReservation classifyExisting(PointTransactionEntity transaction) {
        int currentPoints = currentBalanceRequired(transaction.getUserId());
        return switch (transaction.getStatus()) {
            case SETTLED -> new PointReservation(
                    PointReservation.State.SETTLED,
                    transaction.getId(),
                    requireAnalysisResultId(transaction),
                    currentPoints);
            case RESERVED -> throw new PointRequestConflictException(
                    PointRequestConflictException.Kind.IN_PROGRESS);
            case REFUNDED -> throw new PointRequestConflictException(
                    PointRequestConflictException.Kind.ALREADY_FAILED);
            default -> throw new IllegalStateException(
                    "Unknown point transaction status for id " + transaction.getId());
        };
    }

    @Transactional
    public void settle(long transactionId, long analysisResultId) {
        if (transactionMapper.settleReserved(transactionId, analysisResultId) == 1) {
            return;
        }
        PointTransactionEntity existing = transactionMapper.findByTransactionId(transactionId);
        if (existing != null
                && SETTLED.equals(existing.getStatus())
                && Objects.equals(existing.getAnalysisResultId(), analysisResultId)) {
            return;
        }
        throw new PointTransactionStateException(transactionId);
    }

    public void refund(long transactionId) {
        transactionTemplate.executeWithoutResult(status -> {
            PointTransactionEntity transaction = transactionMapper.findByTransactionId(transactionId);
            if (transaction == null || !RESERVED.equals(transaction.getStatus())) {
                return;
            }
            if (transactionMapper.markRefunded(transactionId) == 1
                    && userMapper.refundOnePoint(transaction.getUserId()) != 1) {
                throw new IllegalStateException(
                        "Unable to restore point for transaction " + transactionId);
            }
        });
    }

    public int currentBalance(long userId) {
        return currentBalanceRequired(userId);
    }

    private int currentBalanceRequired(long userId) {
        Integer balance = userMapper.findCurrentBalance(userId);
        if (balance == null) {
            throw new IllegalStateException("Active user not found: " + userId);
        }
        return balance;
    }

    private PointTransactionEntity findExisting(long userId, String requestKey) {
        return transactionMapper.findByUserAndRequest(userId, requestKey);
    }

    private long requireAnalysisResultId(PointTransactionEntity transaction) {
        if (transaction.getAnalysisResultId() == null) {
            throw new IllegalStateException(
                    "Settled point transaction has no analysis result: " + transaction.getId());
        }
        return transaction.getAnalysisResultId();
    }
}
