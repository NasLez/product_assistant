package com.example.productassistant.analysis;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("user_analysis_access")
public class UserAnalysisAccessEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long analysisResultId;
    private Long pointTransactionId;
    private String requestId;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getAnalysisResultId() { return analysisResultId; }
    public void setAnalysisResultId(Long analysisResultId) { this.analysisResultId = analysisResultId; }
    public Long getPointTransactionId() { return pointTransactionId; }
    public void setPointTransactionId(Long pointTransactionId) { this.pointTransactionId = pointTransactionId; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
