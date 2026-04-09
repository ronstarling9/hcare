package com.hcare.integration.evv;

public interface EvvDeadLetterQueue {

    void enqueue(EvvSubmissionContext ctx, EvvSubmissionResult failure);
}
