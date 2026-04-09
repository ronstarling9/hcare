package com.hcare.integration.evv;

import com.hcare.evv.AggregatorType;

import java.util.List;

public interface EvvBatchQueue {

    void enqueue(EvvSubmissionContext ctx, AggregatorType aggregatorType);

    List<BatchEntry> drainAll();
}
