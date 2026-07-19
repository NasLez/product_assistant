package com.example.productassistant.analysis;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("analysis_result")
public class AnalysisResultEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long productSnapshotId;
    private String model;
    private String promptVersion;
    private String targetUsers;
    private String useCases;
    private String painPoints;
    private String coreSellingPoints;
    private String videoScript;
    private String aiRawJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProductSnapshotId() { return productSnapshotId; }
    public void setProductSnapshotId(Long productSnapshotId) { this.productSnapshotId = productSnapshotId; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getPromptVersion() { return promptVersion; }
    public void setPromptVersion(String promptVersion) { this.promptVersion = promptVersion; }
    public String getTargetUsers() { return targetUsers; }
    public void setTargetUsers(String targetUsers) { this.targetUsers = targetUsers; }
    public String getUseCases() { return useCases; }
    public void setUseCases(String useCases) { this.useCases = useCases; }
    public String getPainPoints() { return painPoints; }
    public void setPainPoints(String painPoints) { this.painPoints = painPoints; }
    public String getCoreSellingPoints() { return coreSellingPoints; }
    public void setCoreSellingPoints(String coreSellingPoints) { this.coreSellingPoints = coreSellingPoints; }
    public String getVideoScript() { return videoScript; }
    public void setVideoScript(String videoScript) { this.videoScript = videoScript; }
    public String getAiRawJson() { return aiRawJson; }
    public void setAiRawJson(String aiRawJson) { this.aiRawJson = aiRawJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

