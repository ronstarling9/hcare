package com.hcare.integration.payroll;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A collection of {@link PayrollableShift}s for a single agency within one pay period.
 */
public record PayrollBatch(
    UUID agencyId,
    String payPeriodCode,
    LocalDate payPeriodStart,
    LocalDate payPeriodEnd,
    List<PayrollableShift> shifts
) {}
