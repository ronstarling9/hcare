package com.hcare.api.v1.visits.dto;

import com.hcare.domain.ShiftStatus;
import com.hcare.evv.EvvComplianceStatus;
import com.hcare.evv.VerificationMethod;
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
    ShiftStatus status,
    String notes,
    EvvSummary evv
) {
    public record EvvSummary(
        UUID evvRecordId,
        EvvComplianceStatus complianceStatus,
        LocalDateTime timeIn,
        LocalDateTime timeOut,
        VerificationMethod verificationMethod,
        boolean capturedOffline
    ) {}
}
