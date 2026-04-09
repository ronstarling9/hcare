package com.hcare.integration.billing;

import java.math.BigDecimal;

/**
 * Immutable record representing the outcome of a remittance fetch from a billing connector.
 */
public record RemittanceResult(
        boolean ready,
        String batchId,
        BigDecimal paidAmount,
        String adjustmentReason,
        String rawX12) {

    public static RemittanceResult notReady() {
        return new RemittanceResult(false, null, null, null, null);
    }
}
