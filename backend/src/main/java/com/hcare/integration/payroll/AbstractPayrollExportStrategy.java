package com.hcare.integration.payroll;

import java.util.List;

/**
 * Template Method base for payroll export strategies.
 *
 * <p>{@link #export(PayrollBatch, AgencyPayrollCredentials)} is {@code final} and enforces the
 * validate → buildRecords → doExport pipeline. Subclasses implement the three abstract methods
 * and optionally override {@link #exportType()}.
 */
public abstract class AbstractPayrollExportStrategy implements PayrollExportStrategy {

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
        validate(batch);
        List<? extends PayrollRecord> records = buildRecords(batch);
        return doExport(records, creds);
    }
}
