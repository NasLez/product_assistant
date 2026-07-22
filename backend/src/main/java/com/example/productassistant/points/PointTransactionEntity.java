package com.example.productassistant.points;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("point_transaction")
public class PointTransactionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String requestId;
    private String transactionType;
    private String status;
    private Integer delta;
    private Integer balanceAfter;
    private Long analysisResultId;
    private LocalDateTime createdAt;
    private LocalDateTime settledAt;
    private LocalDateTime refundedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String transactionType) { this.transactionType = transactionType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getDelta() { return delta; }
    public void setDelta(Integer delta) { this.delta = delta; }
    public Integer getBalanceAfter() { return balanceAfter; }
    public void setBalanceAfter(Integer balanceAfter) { this.balanceAfter = balanceAfter; }
    public Long getAnalysisResultId() { return analysisResultId; }
    public void setAnalysisResultId(Long analysisResultId) { this.analysisResultId = analysisResultId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getSettledAt() { return settledAt; }
    public void setSettledAt(LocalDateTime settledAt) { this.settledAt = settledAt; }
    public LocalDateTime getRefundedAt() { return refundedAt; }
    public void setRefundedAt(LocalDateTime refundedAt) { this.refundedAt = refundedAt; }
}
