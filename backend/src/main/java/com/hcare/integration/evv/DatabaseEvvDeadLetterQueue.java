package com.hcare.integration.evv;

import org.springframework.stereotype.Component;

@Component
public class DatabaseEvvDeadLetterQueue implements EvvDeadLetterQueue {

    private final EvvSubmissionRecordRepository repo;

    public DatabaseEvvDeadLetterQueue(EvvSubmissionRecordRepository repo) {
        this.repo = repo;
    }

    @Override
    public void enqueue(EvvSubmissionContext ctx, EvvSubmissionResult failure) {
        EvvSubmissionRecord record = new EvvSubmissionRecord();
        record.setEvvRecordId(ctx.evvRecordId());
        record.setAgencyId(ctx.agencyId());
        record.setAggregatorType(ctx.aggregatorType().name());
        record.setStatus(EvvSubmissionStatus.REJECTED.name());
        record.setLastError(failure.errorMessage());
        record.setSubmissionMode("REAL_TIME");
        repo.save(record);
    }
}
