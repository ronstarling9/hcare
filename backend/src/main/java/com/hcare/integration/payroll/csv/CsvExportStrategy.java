package com.hcare.integration.payroll.csv;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hcare.integration.payroll.AbstractPayrollExportStrategy;
import com.hcare.integration.payroll.AgencyPayrollCredentials;
import com.hcare.integration.payroll.GenericPayrollRecord;
import com.hcare.integration.payroll.PayrollBatch;
import com.hcare.integration.payroll.PayrollExportResult;
import com.hcare.integration.payroll.PayrollRecord;
import com.hcare.integration.payroll.PayrollableShift;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/**
 * Payroll export strategy that produces a CSV file.
 *
 * <p>FLSA overtime uses the 8-hour daily rule: regular hours = min(hoursWorked, 8.0),
 * overtime hours = max(0, hoursWorked - 8.0). When a shift is not overtime-eligible
 * (FLSA exempt), all hours are classified as regular.
 *
 * <p>Column configuration is read from {@link AgencyPayrollCredentials#companyCode()} as a JSON
 * string if it matches the expected format; otherwise, the default column set is used.
 */
@Component
public class CsvExportStrategy extends AbstractPayrollExportStrategy {

    private static final Logger log = LoggerFactory.getLogger(CsvExportStrategy.class);

    private static final BigDecimal EIGHT_HOURS = new BigDecimal("8.00");
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final int SCALE = 4;

    private static final List<String> DEFAULT_COLUMNS = List.of(
            "caregiverId",
            "caregiverName",
            "regularHours",
            "overtimeHours",
            "regularPay",
            "overtimePay",
            "grossPay",
            "payPeriodCode",
            "costCenter");

    private static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd";

    private final ObjectMapper objectMapper;
    private final CsvSerializer csvSerializer;

    public CsvExportStrategy(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.csvSerializer = new CsvSerializer();
    }

    @Override
    public String exportType() {
        return "CSV_EXPORT";
    }

    @Override
    protected void validate(PayrollBatch batch) {
        if (batch.agencyId() == null) {
            throw new IllegalArgumentException("PayrollBatch.agencyId must not be null");
        }
        if (batch.shifts() == null || batch.shifts().isEmpty()) {
            throw new IllegalArgumentException(
                    "PayrollBatch must contain at least one shift for agencyId: " + batch.agencyId());
        }
    }

    @Override
    protected List<GenericPayrollRecord> buildRecords(PayrollBatch batch) {
        return batch.shifts().stream()
                .map(shift -> toGenericPayrollRecord(shift, batch.payPeriodCode()))
                .toList();
    }

    @Override
    protected PayrollExportResult doExport(
            List<? extends PayrollRecord> records, AgencyPayrollCredentials creds) {
        CsvFieldMapping mapping = resolveFieldMapping(creds);

        log.debug("Exporting {} payroll records as CSV for pay period config: {}",
                records.size(), creds != null ? creds.companyCode() : "default");

        byte[] csvBytes = csvSerializer.serialize(records, mapping);
        String exportId = UUID.randomUUID().toString();

        log.debug("CSV payroll export complete: exportId={}, bytes={}", exportId, csvBytes.length);
        return PayrollExportResult.success(exportId, records.size());
    }

    private GenericPayrollRecord toGenericPayrollRecord(
            PayrollableShift shift, String payPeriodCode) {
        BigDecimal hoursWorked = shift.hoursWorked() != null ? shift.hoursWorked() : ZERO;

        BigDecimal regularHours;
        BigDecimal overtimeHours;

        if (shift.isOvertimeEligible()) {
            regularHours = hoursWorked.min(EIGHT_HOURS).setScale(SCALE, RoundingMode.HALF_UP);
            overtimeHours = hoursWorked.subtract(EIGHT_HOURS).max(ZERO)
                    .setScale(SCALE, RoundingMode.HALF_UP);
        } else {
            regularHours = hoursWorked.setScale(SCALE, RoundingMode.HALF_UP);
            overtimeHours = ZERO.setScale(SCALE, RoundingMode.HALF_UP);
        }

        BigDecimal regularRate = shift.regularRate() != null ? shift.regularRate() : ZERO;
        BigDecimal overtimeRate = shift.overtimeRate() != null ? shift.overtimeRate() : ZERO;

        BigDecimal regularPay = regularHours.multiply(regularRate)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal overtimePay = overtimeHours.multiply(overtimeRate)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal grossPay = regularPay.add(overtimePay);

        return new GenericPayrollRecord(
                shift.caregiverId(),
                null,                                  // caregiverName: not available in shift projection
                regularHours,
                overtimeHours,
                regularPay,
                overtimePay,
                grossPay,
                payPeriodCode,
                shift.costCenter());
    }

    private CsvFieldMapping resolveFieldMapping(AgencyPayrollCredentials creds) {
        if (creds != null && creds.companyCode() != null
                && creds.companyCode().trim().startsWith("{")) {
            try {
                return CsvFieldMapping.fromJson(creds.companyCode(), objectMapper);
            } catch (Exception e) {
                log.warn("Failed to parse CsvFieldMapping from companyCode — using defaults: {}",
                        e.getMessage());
            }
        }
        return new CsvFieldMapping(DEFAULT_COLUMNS, DEFAULT_DATE_FORMAT);
    }
}
