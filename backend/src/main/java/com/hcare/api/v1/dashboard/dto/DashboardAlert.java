package com.hcare.api.v1.dashboard.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A single actionable alert for the dashboard right-column.
 */
public record DashboardAlert(
    AlertType alertType,
    UUID subjectId,
    String subjectName,
    String detail,
    LocalDate dueDate
) {}
