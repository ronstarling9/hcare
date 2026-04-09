package com.hcare.integration.payroll;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A Viventium-specific payroll record including FLSA 8/80 overtime breakdowns and
 * Viventium pay-group routing.
 *
 * <p>Kept in the {@code com.hcare.integration.payroll} package so that it can be listed in the
 * {@code permits} clause of the {@link PayrollRecord} sealed interface. Java's unnamed module
 * requires all permitted subclasses to reside in the same package as the sealed type.
 */
public record ViventiumPayRecord(
    UUID caregiverId,
    String employeeId,
    BigDecimal regularHours,
    BigDecimal overtimeHours,
    BigDecimal doubleTimeHours,
    BigDecimal regularPay,
    BigDecimal overtimePay,
    BigDecimal grossPay,
    String costCenter,
    String payGroupCode
) implements PayrollRecord {}
