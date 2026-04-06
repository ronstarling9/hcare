package com.hcare.api.v1.clients.dto;

import com.hcare.domain.CarePlan;
import com.hcare.domain.CarePlanStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record CarePlanResponse(
    UUID id,
    UUID clientId,
    UUID agencyId,
    int planVersion,
    CarePlanStatus status,
    UUID reviewedByClinicianId,
    LocalDateTime reviewedAt,
    LocalDateTime activatedAt,
    LocalDateTime createdAt
) {
    public static CarePlanResponse from(CarePlan p) {
        return new CarePlanResponse(
            p.getId(), p.getClientId(), p.getAgencyId(), p.getPlanVersion(),
            p.getStatus(), p.getReviewedByClinicianId(), p.getReviewedAt(),
            p.getActivatedAt(), p.getCreatedAt());
    }
}
