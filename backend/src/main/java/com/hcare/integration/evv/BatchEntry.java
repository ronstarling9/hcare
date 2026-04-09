package com.hcare.integration.evv;

import com.hcare.evv.AggregatorType;

public record BatchEntry(EvvSubmissionContext ctx, AggregatorType aggregatorType) {}
