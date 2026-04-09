package com.hcare.integration.evv;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DatabaseEvvDeadLetterQueue implements EvvDeadLetterQueue {

    private static final Logger log = LoggerFactory.getLogger(DatabaseEvvDeadLetterQueue.class);

    private final EvvSubmissionRecordRepository repo;
    private final ObjectMapper objectMapper;

    public DatabaseEvvDeadLetterQueue(EvvSubmissionRecordRepository repo, ObjectMapper objectMapper) {
        this.repo = repo;
        this.objectMapper = objectMapper;
    }

    @Override
    public void enqueue(EvvSubmissionContext ctx, EvvSubmissionResult failure) {
        EvvSubmissionRecord record = new EvvSubmissionRecord();
        record.setEvvRecordId(ctx.evvRecordId());
        record.setAgencyId(ctx.agencyId());
        record.setAggregatorType(ctx.aggregatorType().name());
        record.setStatus(EvvSubmissionStatus.REJECTED.name());
        // Store errorCode + message together since EvvSubmissionRecord has lastError but no separate errorCode column
        record.setLastError(failure.errorCode() + ": " + failure.errorMessage());
        // DLQ entries are always from real-time path failures — submissionMode=REAL_TIME is correct here.
        record.setSubmissionMode("REAL_TIME");

        try {
            record.setContextJson(objectMapper.writeValueAsString(ctx));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize EvvSubmissionContext for DLQ entry evvRecordId={}", ctx.evvRecordId(), e);
        }

        repo.save(record);
    }
}
