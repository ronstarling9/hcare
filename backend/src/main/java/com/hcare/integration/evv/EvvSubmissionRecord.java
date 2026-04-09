package com.hcare.integration.evv;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Persistence record for a single EVV submission attempt.
 *
 * <p>No Hibernate agencyFilter — tenant scope is caller responsibility. All repository callers
 * must supply agencyId explicitly.
 */
@Entity
@Table(name = "evv_submission_records")
public class EvvSubmissionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "evv_record_id", nullable = false, unique = true)
    private UUID evvRecordId;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    @Column(name = "aggregator_type", nullable = false, length = 30)
    private String aggregatorType;

    @Column(name = "aggregator_visit_id", length = 100)
    private String aggregatorVisitId;

    @Column(name = "submission_mode", nullable = false, length = 10)
    private String submissionMode = "REAL_TIME";

    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "context_json", columnDefinition = "TEXT")
    private String contextJson;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected EvvSubmissionRecord() {}

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now(ZoneOffset.UTC);
        }
    }

    public UUID getId() { return id; }
    public UUID getEvvRecordId() { return evvRecordId; }
    public UUID getAgencyId() { return agencyId; }
    public String getAggregatorType() { return aggregatorType; }
    public String getAggregatorVisitId() { return aggregatorVisitId; }
    public String getSubmissionMode() { return submissionMode; }
    public String getStatus() { return status; }
    public String getContextJson() { return contextJson; }
    public int getRetryCount() { return retryCount; }
    public String getLastError() { return lastError; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setEvvRecordId(UUID evvRecordId) { this.evvRecordId = evvRecordId; }
    public void setAgencyId(UUID agencyId) { this.agencyId = agencyId; }
    public void setAggregatorType(String aggregatorType) { this.aggregatorType = aggregatorType; }
    public void setAggregatorVisitId(String aggregatorVisitId) { this.aggregatorVisitId = aggregatorVisitId; }
    public void setSubmissionMode(String submissionMode) { this.submissionMode = submissionMode; }
    public void setStatus(String status) { this.status = status; }
    public void setContextJson(String contextJson) { this.contextJson = contextJson; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
}
