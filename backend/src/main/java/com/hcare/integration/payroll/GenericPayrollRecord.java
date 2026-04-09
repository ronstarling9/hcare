package com.hcare.integration.payroll;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A portable payroll record suitable for CSV export or generic downstream consumers.
 */
public record GenericPayrollRecord(
    UUID caregiverId,
    String caregiverName,
    BigDecimal regularHours,
    BigDecimal overtimeHours,
    BigDecimal regularPay,
    BigDecimal overtimePay,
    BigDecimal grossPay,
    String payPeriodCode,
    String costCenter
) implements PayrollRecord {}
