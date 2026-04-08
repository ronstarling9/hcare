package com.hcare.api.v1.dashboard.dto;

import com.hcare.domain.ShiftStatus;
import com.hcare.evv.EvvComplianceStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record DashboardVisitRow(
    UUID shiftId,
    String clientFirstName,
    String clientLastName,
    UUID caregiverId,
    String caregiverFirstName,
    String caregiverLastName,
    String serviceTypeName,
    LocalDateTime scheduledStart,
    LocalDateTime scheduledEnd,
    ShiftStatus status,
    EvvComplianceStatus evvStatus,
    String evvStatusReason
) {}
