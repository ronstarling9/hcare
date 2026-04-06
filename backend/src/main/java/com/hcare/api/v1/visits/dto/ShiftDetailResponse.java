package com.hcare.api.v1.visits.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record ShiftDetailResponse(
    UUID id,
    UUID agencyId,
    UUID clientId,
    UUID caregiverId,
    UUID serviceTypeId,
    UUID authorizationId,
    UUID sourcePatternId,
    LocalDateTime scheduledStart,
    LocalDateTime scheduledEnd,
    String status,
    String notes,
    EvvSummary evv
) {
    public record EvvSummary(
        UUID evvRecordId,
        String complianceStatus,
        LocalDateTime timeIn,
        LocalDateTime timeOut,
        String verificationMethod,
        boolean capturedOffline
    ) {}
}
