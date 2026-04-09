package com.hcare.integration.billing;

/**
 * Immutable record returned after a claim submission attempt.
 */
public record ClaimSubmissionReceipt(
        boolean success,
        String batchId,
        String controlNumber,
        String errorCode,
        String errorMessage) {

    public static ClaimSubmissionReceipt success(String batchId, String controlNumber) {
        return new ClaimSubmissionReceipt(true, batchId, controlNumber, null, null);
    }

    public static ClaimSubmissionReceipt failure(String errorCode, String errorMessage) {
        return new ClaimSubmissionReceipt(false, null, null, errorCode, errorMessage);
    }
}
