package com.hcare.integration.evv;

public record EvvSubmissionResult(
        boolean success,
        String aggregatorVisitId,
        String errorCode,
        String errorMessage
) {

    public static EvvSubmissionResult ok(String aggregatorVisitId) {
        return new EvvSubmissionResult(true, aggregatorVisitId, null, null);
    }

    public static EvvSubmissionResult failure(String errorCode, String errorMessage) {
        return new EvvSubmissionResult(false, null, errorCode, errorMessage);
    }
}
