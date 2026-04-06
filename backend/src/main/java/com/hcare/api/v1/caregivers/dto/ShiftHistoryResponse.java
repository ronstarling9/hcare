package com.hcare.api.v1.caregivers.dto;

import com.hcare.domain.Shift;
import com.hcare.domain.ShiftStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record ShiftHistoryResponse(
    UUID id,
    UUID clientId,
    UUID serviceTypeId,
    UUID authorizationId,
    LocalDateTime scheduledStart,
    LocalDateTime scheduledEnd,
    ShiftStatus status
) {
    public static ShiftHistoryResponse from(Shift s) {
        return new ShiftHistoryResponse(
            s.getId(), s.getClientId(), s.getServiceTypeId(), s.getAuthorizationId(),
            s.getScheduledStart(), s.getScheduledEnd(), s.getStatus());
    }
}
