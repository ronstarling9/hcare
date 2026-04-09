package com.hcare.integration.payroll;

/**
 * Strategy interface for exporting a {@link PayrollBatch} to a downstream payroll system.
 *
 * <p>Each implementation handles a distinct export format or payroll vendor. Implementations
 * are collected at startup by {@link PayrollStrategyFactory} and indexed by
 * {@link #exportType()}.
 */
public interface PayrollExportStrategy {

    /**
     * Returns the unique identifier for this export type (e.g., {@code "VIVENTIUM"},
     * {@code "CSV_EXPORT"}).
     *
     * @return a non-null, non-empty export type key
     */
    String exportType();

    /**
     * Exports the given payroll batch using the supplied agency credentials.
     *
     * @param batch the payroll batch to export
     * @param creds the agency's payroll credentials
     * @return the result of the export attempt
     */
    PayrollExportResult export(PayrollBatch batch, AgencyPayrollCredentials creds);
}
