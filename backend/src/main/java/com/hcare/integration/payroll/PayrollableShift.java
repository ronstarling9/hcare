package com.hcare.integration.payroll;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Projection of all shift data needed to compute payroll for a single visit.
 *
 * <p>{@code hoursWorked} is pre-computed from {@code timeOut - timeIn} by the assembler so that
 * export strategies receive a consistent value without needing to re-derive it.
 */
public record PayrollableShift(
    UUID shiftId,
    UUID caregiverId,
    UUID agencyId,
    LocalDate workDate,
    LocalDateTime timeIn,
    LocalDateTime timeOut,
    BigDecimal regularRate,
    BigDecimal overtimeRate,
    String costCenter,
    String payPeriodCode,
    BigDecimal hoursWorked,
    boolean isOvertimeEligible
) {}
