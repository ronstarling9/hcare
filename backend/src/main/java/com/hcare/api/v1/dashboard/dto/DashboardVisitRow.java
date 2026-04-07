package com.hcare.api.v1.dashboard.dto;

import com.hcare.domain.ShiftStatus;
import com.hcare.evv.EvvComplianceStatus;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One row in the dashboard visit list for today.
 * Names are denormalized from Client and Caregiver at query time.
 */
public record DashboardVisitRow(
    UUID shiftId,
    String clientFirstName,
    String clientLastName,
    String caregiverFirstName,
    String caregiverLastName,
    LocalDateTime scheduledStart,
    LocalDateTime scheduledEnd,
    ShiftStatus status,
    EvvComplianceStatus evvStatus
) {}
