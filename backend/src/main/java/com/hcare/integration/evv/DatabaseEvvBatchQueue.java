package com.hcare.integration.evv;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hcare.evv.AggregatorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DatabaseEvvBatchQueue implements EvvBatchQueue {

    private static final Logger log = LoggerFactory.getLogger(DatabaseEvvBatchQueue.class);

    private final EvvSubmissionRecordRepository repo;
    private final ObjectMapper objectMapper;

    public DatabaseEvvBatchQueue(EvvSubmissionRecordRepository repo, ObjectMapper objectMapper) {
        this.repo = repo;
        this.objectMapper = objectMapper;
    }

    @Override
    public void enqueue(EvvSubmissionContext ctx, AggregatorType aggregatorType) {
        EvvSubmissionRecord record = new EvvSubmissionRecord();
        record.setEvvRecordId(ctx.evvRecordId());
        record.setAgencyId(ctx.agencyId());
        record.setAggregatorType(aggregatorType.name());
        record.setSubmissionMode("BATCH");
        record.setStatus(EvvSubmissionStatus.PENDING.name());

        try {
            record.setContextJson(objectMapper.writeValueAsString(ctx));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize EvvSubmissionContext for evvRecordId={}", ctx.evvRecordId(), e);
        }

        try {
            repo.save(record);
        } catch (DataIntegrityViolationException e) {
            log.warn("Duplicate batch enqueue ignored for evvRecordId={}", ctx.evvRecordId());
        }
    }

    @Override
    public List<BatchEntry> drainAll() {
        return repo.findBySubmissionModeAndStatus("BATCH", EvvSubmissionStatus.PENDING.name())
                .stream()
                .map(r -> new BatchEntry(
                        deserializeContext(r.getContextJson()),
                        AggregatorType.valueOf(r.getAggregatorType())))
                .toList();
    }

    private EvvSubmissionContext deserializeContext(String contextJson) {
        if (contextJson == null) {
            return null;
        }
        try {
            return objectMapper.readValue(contextJson, EvvSubmissionContext.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize EvvSubmissionContext from context_json", e);
            return null;
        }
    }
}
