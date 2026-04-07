package com.hcare.api.v1.dashboard.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A single actionable alert for the dashboard right-column.
 *
 * <p>alertType values:
 * <ul>
 *   <li>CREDENTIAL_EXPIRING — caregiver credential expiring within 30 days</li>
 *   <li>BACKGROUND_CHECK_DUE — background check renewal due within 30 days</li>
 *   <li>AUTHORIZATION_LOW — authorization utilization >= 80%</li>
 * </ul>
 */
public record DashboardAlert(
    String alertType,
    UUID subjectId,
    String subjectName,
    String detail,
    LocalDate dueDate
) {}
