package com.hcare.integration.payroll.viventium;

import com.hcare.integration.payroll.AbstractPayrollExportStrategy;
import com.hcare.integration.payroll.AgencyPayrollCredentials;
import com.hcare.integration.payroll.PayrollBatch;
import com.hcare.integration.payroll.PayrollExportResult;
import com.hcare.integration.payroll.PayrollRecord;
import com.hcare.integration.payroll.PayrollableShift;
import com.hcare.integration.payroll.ViventiumPayRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Payroll export strategy that sends records to the Viventium payroll API.
 *
 * <p>FLSA overtime is computed using the §207(j) 8/80 rule (homecare-specific):
 * <ul>
 *   <li>Daily OT = sum of max(0, dailyHours - 8) across all worked days in the period.</li>
 *   <li>Period OT = max(0, totalPeriodHours - 80).</li>
 *   <li>Final OT = max(dailyOT, periodOT) — whichever is greater.</li>
 * </ul>
 * Shifts are grouped by caregiverId so that one {@link ViventiumPayRecord} is produced per
 * caregiver, not per shift.
 *
 * <p>The {@code doubleTimeHours} field is set to zero in this implementation; it is reserved for
 * states with double-time rules (e.g., California) and can be extended later.
 *
 * <p>When {@code viventiumRestClient} is not configured (no base-url property set), all export
 * attempts return {@link PayrollExportResult#failure(String, String)} rather than throwing.
 */
@Component
public class ViventiumExportStrategy extends AbstractPayrollExportStrategy {

    private static final Logger log = LoggerFactory.getLogger(ViventiumExportStrategy.class);

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final int SCALE = 4;

    private final RestClient viventiumRestClient;

    public ViventiumExportStrategy(
            @Qualifier("viventiumRestClient") @Nullable RestClient viventiumRestClient) {
        this.viventiumRestClient = viventiumRestClient;
    }

    @Override
    public String exportType() {
        return "VIVENTIUM";
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
    protected List<ViventiumPayRecord> buildRecords(PayrollBatch batch) {
        // Group shifts by caregiverId, then compute FLSA 8/80 per caregiver
        Map<UUID, List<PayrollableShift>> byCaregiverId = batch.shifts().stream()
                .collect(Collectors.groupingBy(PayrollableShift::caregiverId));

        return byCaregiverId.entrySet().stream()
                .map(entry -> toViventiumPayRecord(entry.getKey(), entry.getValue()))
                .toList();
    }

    @Override
    protected PayrollExportResult doExport(
            List<? extends PayrollRecord> records, AgencyPayrollCredentials creds) {
        // Fix 2: guard against null credentials
        if (creds == null) {
            return PayrollExportResult.failure(
                    "MISSING_CREDENTIALS", "Viventium credentials are required");
        }

        if (viventiumRestClient == null) {
            log.warn("Viventium REST client not configured — cannot export payroll");
            return PayrollExportResult.failure("CONNECTOR_UNAVAILABLE", "Viventium not configured");
        }

        // Fix 3: block submission when employeeId values are still internal UUIDs
        boolean hasUnmappedIds = records.stream()
                .filter(r -> r instanceof ViventiumPayRecord)
                .map(r -> (ViventiumPayRecord) r)
                .anyMatch(r -> r.employeeId().matches("[0-9a-f]{8}-[0-9a-f]{4}-.*"));
        if (hasUnmappedIds) {
            log.error(
                    "Viventium export blocked — employeeId values are internal UUIDs, not HR"
                            + " employee numbers. Configure HR ID mapping before using Viventium"
                            + " export.");
            return PayrollExportResult.failure(
                    "EMPLOYEE_ID_UNMAPPED",
                    "Viventium export requires HR employee IDs. Internal UUIDs cannot be"
                            + " submitted to Viventium.");
        }

        log.debug("Exporting {} payroll records to Viventium for company {}",
                records.size(), creds.companyCode());

        try {
            Map<String, Object> payload = Map.of(
                    "companyCode", creds.companyCode(),
                    "payGroupCode", creds.payGroupCode(),
                    "records", records);

            Map<String, Object> response = viventiumRestClient.post()
                    .uri("/v1/payroll/import")
                    .header("Authorization", "Bearer " + creds.apiKey())
                    .body(payload)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response != null && response.containsKey("exportId")) {
                String exportId = String.valueOf(response.get("exportId"));
                return PayrollExportResult.success(exportId, records.size());
            }
            return PayrollExportResult.failure("UNEXPECTED_RESPONSE",
                    "Viventium returned no exportId in response");
        } catch (Exception e) {
            log.error("Viventium payroll export failed: {}", e.getMessage(), e);
            return PayrollExportResult.failure("EXPORT_ERROR", e.getMessage());
        }
    }

    private ViventiumPayRecord toViventiumPayRecord(
            UUID caregiverId, List<PayrollableShift> caregiverShifts) {
        FlsaResult flsa = computeFlsa8_80(caregiverShifts);

        // TODO: employeeId must be Viventium HR employee number, not internal UUID.
        // Until HR mapping is implemented, the UUID is used as a placeholder. The doExport()
        // guard above will block submission until real HR IDs are configured.
        String employeeId = caregiverId.toString();

        // Cost center from the first shift — assumed consistent within a caregiver/period group
        String costCenter = caregiverShifts.get(0).costCenter();

        return new ViventiumPayRecord(
                caregiverId,
                employeeId,
                flsa.regularHours().setScale(SCALE, RoundingMode.HALF_UP),
                flsa.overtimeHours().setScale(SCALE, RoundingMode.HALF_UP),
                ZERO.setScale(SCALE, RoundingMode.HALF_UP), // doubleTimeHours reserved
                flsa.regularPay(),
                flsa.overtimePay(),
                flsa.grossPay(),
                costCenter,
                null  // payGroupCode sourced from credentials at export time
        );
    }
}
