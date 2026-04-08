package com.hcare.api.v1.dashboard.dto;

import java.time.LocalDate;
import java.util.UUID;

public record DashboardAlert(
    AlertType type,
    UUID resourceId,
    String subject,
    String detail,
    LocalDate dueDate,
    String resourceType
) {}
