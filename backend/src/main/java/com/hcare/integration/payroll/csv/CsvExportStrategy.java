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

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Payroll export strategy that produces a CSV file.
 *
 * <p>FLSA overtime uses the §207(j) 8/80 rule: shifts are grouped by caregiverId and
 * {@link #computeFlsa8_80(List)} is applied per caregiver, producing one
 * {@link GenericPayrollRecord} per caregiver rather than per shift.
 *
 * <p>Column configuration is read from {@link AgencyPayrollCredentials#configJson()} as a JSON
 * string if it matches the expected format; otherwise, the default column set is used.
 */
@Component
public class CsvExportStrategy extends AbstractPayrollExportStrategy {

    private static final Logger log = LoggerFactory.getLogger(CsvExportStrategy.class);

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
        // Group shifts by caregiverId, then compute FLSA 8/80 per caregiver
        Map<UUID, List<PayrollableShift>> byCaregiverId = batch.shifts().stream()
                .collect(Collectors.groupingBy(PayrollableShift::caregiverId));

        return byCaregiverId.entrySet().stream()
                .map(entry -> toGenericPayrollRecord(
                        entry.getKey(), entry.getValue(), batch.payPeriodCode()))
                .toList();
    }

    @Override
    protected PayrollExportResult doExport(
            List<? extends PayrollRecord> records, AgencyPayrollCredentials creds) {
        CsvFieldMapping mapping = resolveFieldMapping(creds);

        log.debug("Exporting {} payroll records as CSV for pay period config: {}",
                records.size(), creds != null ? creds.configJson() : "default");

        byte[] csvBytes = csvSerializer.serialize(records, mapping);
        String exportId = UUID.randomUUID().toString();

        log.debug("CSV payroll export complete: exportId={}, bytes={}", exportId, csvBytes.length);
        return PayrollExportResult.success(exportId, records.size());
    }

    private GenericPayrollRecord toGenericPayrollRecord(
            UUID caregiverId, List<PayrollableShift> caregiverShifts, String payPeriodCode) {
        FlsaResult flsa = computeFlsa8_80(caregiverShifts);

        // Cost center from the first shift — assumed consistent within a caregiver/period group
        String costCenter = caregiverShifts.get(0).costCenter();

        return new GenericPayrollRecord(
                caregiverId,
                null,                // caregiverName: not available in shift projection
                flsa.regularHours(),
                flsa.overtimeHours(),
                flsa.regularPay(),
                flsa.overtimePay(),
                flsa.grossPay(),
                payPeriodCode,
                costCenter);
    }

    private CsvFieldMapping resolveFieldMapping(AgencyPayrollCredentials creds) {
        if (creds != null && creds.configJson() != null
                && creds.configJson().trim().startsWith("{")) {
            try {
                return CsvFieldMapping.fromJson(creds.configJson(), objectMapper);
            } catch (Exception e) {
                log.warn("Failed to parse CsvFieldMapping from configJson — using defaults: {}",
                        e.getMessage());
            }
        }
        return new CsvFieldMapping(DEFAULT_COLUMNS, DEFAULT_DATE_FORMAT);
    }
}
