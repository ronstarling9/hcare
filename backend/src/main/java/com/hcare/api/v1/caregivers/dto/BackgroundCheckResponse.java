package com.hcare.api.v1.caregivers.dto;

import com.hcare.domain.BackgroundCheck;
import com.hcare.domain.BackgroundCheckResult;
import com.hcare.domain.BackgroundCheckType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record BackgroundCheckResponse(
    UUID id,
    UUID caregiverId,
    UUID agencyId,
    BackgroundCheckType checkType,
    BackgroundCheckResult result,
    LocalDate checkedAt,
    LocalDate renewalDueDate,
    LocalDateTime createdAt
) {
    public static BackgroundCheckResponse from(BackgroundCheck b) {
        return new BackgroundCheckResponse(
            b.getId(), b.getCaregiverId(), b.getAgencyId(),
            b.getCheckType(), b.getResult(), b.getCheckedAt(),
            b.getRenewalDueDate(), b.getCreatedAt());
    }
}
