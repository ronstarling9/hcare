package com.hcare.integration.payroll;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Template Method base for payroll export strategies.
 *
 * <p>{@link #export(PayrollBatch, AgencyPayrollCredentials)} is {@code final} and enforces the
 * validate → buildRecords → doExport pipeline. Subclasses implement the three abstract methods
 * and optionally override {@link #exportType()}.
 *
 * <p>The shared {@link #computeFlsa8_80(List)} utility implements the FLSA §207(j) 8/80 rule
 * used by homecare employers: overtime is the greater of daily excess over 8 hours or period
 * excess over 80 hours.
 */
public abstract class AbstractPayrollExportStrategy implements PayrollExportStrategy {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    /**
     * Validates the batch before record construction. Implementations should throw
     * {@link IllegalArgumentException} or return a failure result via
     * {@link PayrollExportResult#failure(String, String)} — but since validation here is
     * void-returning, validation errors should throw unchecked exceptions that propagate to the
     * caller or be reflected as a failure return from {@link #export}.
     *
     * @param batch the batch to validate
     */
    protected abstract void validate(PayrollBatch batch);

    /**
     * Converts the validated batch into a list of typed payroll records.
     *
     * @param batch the validated batch
     * @return a non-null list of payroll records; may be empty
     */
    protected abstract List<? extends PayrollRecord> buildRecords(PayrollBatch batch);

    /**
     * Performs the actual export of the computed records.
     *
     * @param records the records produced by {@link #buildRecords(PayrollBatch)}
     * @param creds   the agency credentials for this export
     * @return the result of the export attempt
     */
    protected abstract PayrollExportResult doExport(
            List<? extends PayrollRecord> records, AgencyPayrollCredentials creds);

    /**
     * Template method that enforces the validate → buildRecords → doExport pipeline.
     * Subclasses must not override this method.
     *
     * @param batch the payroll batch to export
     * @param creds the agency's payroll credentials
     * @return the result of the export
     */
    @Override
    public final PayrollExportResult export(PayrollBatch batch, AgencyPayrollCredentials creds) {
        try {
            validate(batch);
        } catch (IllegalArgumentException e) {
            return PayrollExportResult.failure("VALIDATION_ERROR", e.getMessage());
        }
        List<? extends PayrollRecord> records = buildRecords(batch);
        return doExport(records, creds);
    }

    /**
     * Computes FLSA §207(j) 8/80 overtime for a single caregiver's shifts within a pay period.
     *
     * <p>The rule for homecare employers:
     * <ol>
     *   <li>Sum hours per calendar day; daily OT = max(0, dailyHours - 8) summed across all days.
     *   <li>Period OT = max(0, totalPeriodHours - 80).
     *   <li>Final OT = max(dailyOT, periodOT) — whichever produces more overtime for the employee.
     *   <li>Regular hours = totalPeriodHours - finalOT.
     * </ol>
     *
     * <p>Pay rates are taken from the first shift in the list. This assumes a single pay rate per
     * caregiver per period — a simplification that must be revisited if tiered rates are introduced.
     *
     * @param shifts all shifts for one caregiver within the pay period; must not be null
     * @return a {@link FlsaResult} with regular/overtime hours and pay broken out
     */
    protected static FlsaResult computeFlsa8_80(List<PayrollableShift> shifts) {
        if (shifts.isEmpty()) {
            return new FlsaResult(ZERO, ZERO, ZERO, ZERO, ZERO);
        }

        // Step 1: sum hours per calendar day and accumulate period total
        Map<LocalDate, BigDecimal> dailyHours = new LinkedHashMap<>();
        BigDecimal totalPeriodHours = ZERO;
        for (PayrollableShift shift : shifts) {
            LocalDate day = shift.workDate();
            dailyHours.merge(day, shift.hoursWorked(), BigDecimal::add);
            totalPeriodHours = totalPeriodHours.add(shift.hoursWorked());
        }

        // Step 2: daily overtime — excess over 8 hours per day
        BigDecimal eight = new BigDecimal("8");
        BigDecimal dailyOt = ZERO;
        for (BigDecimal hours : dailyHours.values()) {
            dailyOt = dailyOt.add(hours.subtract(eight).max(ZERO));
        }

        // Step 3: period overtime — excess over 80 hours in the period
        BigDecimal eighty = new BigDecimal("80");
        BigDecimal periodOt = totalPeriodHours.subtract(eighty).max(ZERO);

        // Step 4: use whichever produces more OT (FLSA requires the greater of the two)
        BigDecimal overtimeHours = dailyOt.max(periodOt);
        BigDecimal regularHours = totalPeriodHours.subtract(overtimeHours).max(ZERO);

        // Rates from the first shift — assumes uniform rate per caregiver per period
        BigDecimal regularRate =
                shifts.get(0).regularRate() != null ? shifts.get(0).regularRate() : ZERO;
        BigDecimal overtimeRate =
                shifts.get(0).overtimeRate() != null ? shifts.get(0).overtimeRate() : ZERO;

        BigDecimal regularPay =
                regularHours.multiply(regularRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal overtimePay =
                overtimeHours.multiply(overtimeRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal grossPay = regularPay.add(overtimePay);

        return new FlsaResult(regularHours, overtimeHours, regularPay, overtimePay, grossPay);
    }

    /**
     * Value type returned by {@link #computeFlsa8_80(List)}.
     *
     * @param regularHours  hours classified as regular time
     * @param overtimeHours hours classified as overtime under the FLSA 8/80 rule
     * @param regularPay    regularHours × regularRate
     * @param overtimePay   overtimeHours × overtimeRate
     * @param grossPay      regularPay + overtimePay
     */
    protected static record FlsaResult(
            BigDecimal regularHours,
            BigDecimal overtimeHours,
            BigDecimal regularPay,
            BigDecimal overtimePay,
            BigDecimal grossPay) {}
}
