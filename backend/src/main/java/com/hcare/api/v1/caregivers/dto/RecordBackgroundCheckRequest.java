package com.hcare.api.v1.caregivers.dto;

import com.hcare.domain.BackgroundCheckResult;
import com.hcare.domain.BackgroundCheckType;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record RecordBackgroundCheckRequest(
    @NotNull BackgroundCheckType checkType,
    @NotNull BackgroundCheckResult result,
    @NotNull LocalDate checkedAt,
    LocalDate renewalDueDate
) {}
