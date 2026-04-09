package com.hcare.integration.payroll;

/**
 * Result of a payroll export operation.
 *
 * <p>Use the static factory methods {@link #success(String, int)} and
 * {@link #failure(String, String)} rather than calling the canonical constructor directly.
 */
public record PayrollExportResult(
    boolean success,
    String exportId,
    int recordCount,
    String errorCode,
    String errorMessage
) {

    /**
     * Creates a successful export result.
     *
     * @param exportId    the unique identifier assigned to this export by the downstream system
     * @param recordCount the number of payroll records included in the export
     * @return a success result
     */
    public static PayrollExportResult success(String exportId, int recordCount) {
        return new PayrollExportResult(true, exportId, recordCount, null, null);
    }

    /**
     * Creates a failure export result.
     *
     * @param errorCode    a machine-readable error code
     * @param errorMessage a human-readable description of the error
     * @return a failure result
     */
    public static PayrollExportResult failure(String errorCode, String errorMessage) {
        return new PayrollExportResult(false, null, 0, errorCode, errorMessage);
    }
}
