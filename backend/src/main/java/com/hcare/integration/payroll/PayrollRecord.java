package com.hcare.integration.payroll;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Sealed interface representing a computed payroll record for a single caregiver.
 *
 * <p>Permitted implementations are {@link GenericPayrollRecord} and {@link ViventiumPayRecord}.
 * Both must reside in this package because Java's unnamed module does not allow cross-package
 * sealed subclasses.
 */
public sealed interface PayrollRecord
        permits GenericPayrollRecord, ViventiumPayRecord {

    UUID caregiverId();

    BigDecimal grossPay();
}
