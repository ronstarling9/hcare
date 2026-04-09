package com.hcare.integration.evv;

import com.hcare.evv.AggregatorType;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Carries all data needed to submit an EVV record to an aggregator.
 *
 * <p>C16: NEVER includes decrypted credential values — credentials resolved at submit time by the
 * caller. Safe to serialize to context_json without leaking plaintext secrets.
 *
 * @param stateCode Two-letter state code for aggregator routing and timezone resolution
 * @param serviceState Two-letter state code where the service was delivered (for EVV payload body
 *     — may differ from routing state for interstate clients)
 * @param aggregatorVisitId Aggregator's visit ID from prior submission — used in update/void
 *     operations. Null for initial submissions.
 */
public record EvvSubmissionContext(
        UUID evvRecordId,
        UUID shiftId,
        UUID agencyId,
        UUID clientId,
        UUID caregiverId,
        UUID payerId,
        AggregatorType aggregatorType,
        String stateCode,
        String caregiverNpi,
        String clientMedicaidId,
        String serviceCode,
        LocalDateTime timeIn,
        LocalDateTime timeOut,
        String serviceState,
        String aggregatorVisitId
) {}
