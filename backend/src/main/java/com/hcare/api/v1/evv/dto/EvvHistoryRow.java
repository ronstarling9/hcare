package com.hcare.api.v1.evv.dto;

import com.hcare.evv.EvvComplianceStatus;
import com.hcare.evv.VerificationMethod;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Denormalized EVV history row — one row per shift.
 * Client and caregiver names are resolved at query time from their respective entities.
 * EVV status is computed on read via EvvComplianceService — never stored.
 *
 * <p>{@code evvStatusReason} carries a human-readable explanation only when the
 * status computation could not proceed (e.g. missing state config, unknown client,
 * agency state not configured). It is {@code null} when {@code EvvComplianceService.compute()}
 * runs successfully — the status enum itself is the authoritative signal in that case.
 */
public record EvvHistoryRow(
    UUID shiftId,
    String clientFirstName,
    String clientLastName,
    String caregiverFirstName,
    String caregiverLastName,
    String serviceTypeName,
    LocalDateTime scheduledStart,
    LocalDateTime scheduledEnd,
    EvvComplianceStatus evvStatus,
    String evvStatusReason,
    LocalDateTime timeIn,
    LocalDateTime timeOut,
    VerificationMethod verificationMethod,
    String shiftStatus
) {}
