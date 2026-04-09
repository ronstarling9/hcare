package com.hcare.integration.payroll.csv;

import com.hcare.integration.payroll.GenericPayrollRecord;
import com.hcare.integration.payroll.PayrollRecord;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Converts a list of {@link PayrollRecord} instances to UTF-8 encoded CSV bytes.
 *
 * <p>Column ordering and inclusion are controlled by a {@link CsvFieldMapping}. Only
 * {@link GenericPayrollRecord} instances are currently supported — unrecognized record types
 * produce empty columns. Unrecognized column names also produce empty values.
 */
public class CsvSerializer {

    private static final Map<String, Function<GenericPayrollRecord, String>> FIELD_EXTRACTORS =
            Map.ofEntries(
                    Map.entry("caregiverId",    r -> toString(r.caregiverId())),
                    Map.entry("caregiverName",  r -> toString(r.caregiverName())),
                    Map.entry("regularHours",   r -> toString(r.regularHours())),
                    Map.entry("overtimeHours",  r -> toString(r.overtimeHours())),
                    Map.entry("regularPay",     r -> toString(r.regularPay())),
                    Map.entry("overtimePay",    r -> toString(r.overtimePay())),
                    Map.entry("grossPay",       r -> toString(r.grossPay())),
                    Map.entry("payPeriodCode",  r -> toString(r.payPeriodCode())),
                    Map.entry("costCenter",     r -> toString(r.costCenter()))
            );

    /**
     * Serializes the given records to CSV bytes.
     *
     * <p>Produces a header row followed by one data row per record. Values containing
     * commas or double-quotes are quoted and internal double-quotes are escaped.
     *
     * @param records the payroll records to serialize
     * @param mapping the field mapping controlling column order
     * @return UTF-8 encoded CSV bytes
     */
    public byte[] serialize(List<? extends PayrollRecord> records, CsvFieldMapping mapping) {
        List<String> columns = mapping.columns();
        StringBuilder sb = new StringBuilder();

        // Header row
        sb.append(String.join(",", columns));
        sb.append("\n");

        // Data rows
        for (PayrollRecord record : records) {
            if (record instanceof GenericPayrollRecord generic) {
                sb.append(buildRow(generic, columns));
            } else {
                // Unsupported type — emit empty columns
                sb.append(",".repeat(Math.max(0, columns.size() - 1)));
            }
            sb.append("\n");
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String buildRow(GenericPayrollRecord record, List<String> columns) {
        StringBuilder row = new StringBuilder();
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                row.append(",");
            }
            Function<GenericPayrollRecord, String> extractor = FIELD_EXTRACTORS.get(columns.get(i));
            String value = extractor != null ? extractor.apply(record) : "";
            row.append(csvEscape(value));
        }
        return row.toString();
    }

    private static String toString(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String csvEscape(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        boolean needsQuoting = value.contains(",") || value.contains("\"") || value.contains("\n");
        if (!needsQuoting) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
