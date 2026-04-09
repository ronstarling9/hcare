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

/**
 * Payroll export strategy that sends records to the Viventium payroll API.
 *
 * <p>FLSA overtime is computed using the simpler 8-hour daily rule:
 * <ul>
 *   <li>Regular hours = min(hoursWorked, 8.0)</li>
 *   <li>Overtime hours = max(0, hoursWorked - 8.0)</li>
 * </ul>
 * The {@code doubleTimeHours} field is set to zero in this implementation; it is reserved for
 * states with double-time rules (e.g., California) and can be extended later.
 *
 * <p>When {@code viventiumRestClient} is not configured (no base-url property set), all export
 * attempts return {@link PayrollExportResult#failure(String, String)} rather than throwing.
 */
@Component
public class ViventiumExportStrategy extends AbstractPayrollExportStrategy {

    private static final Logger log = LoggerFactory.getLogger(ViventiumExportStrategy.class);

    private static final BigDecimal EIGHT_HOURS = new BigDecimal("8.00");
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
        return batch.shifts().stream()
                .map(this::toViventiumPayRecord)
                .toList();
    }

    @Override
    protected PayrollExportResult doExport(
            List<? extends PayrollRecord> records, AgencyPayrollCredentials creds) {
        if (viventiumRestClient == null) {
            log.warn("Viventium REST client not configured — cannot export payroll");
            return PayrollExportResult.failure("CONNECTOR_UNAVAILABLE", "Viventium not configured");
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

    private ViventiumPayRecord toViventiumPayRecord(PayrollableShift shift) {
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

        return new ViventiumPayRecord(
                shift.caregiverId(),
                shift.caregiverId().toString(),   // employeeId: caregiver UUID until HR mapping is available
                regularHours,
                overtimeHours,
                ZERO.setScale(SCALE, RoundingMode.HALF_UP), // doubleTimeHours reserved
                regularPay,
                overtimePay,
                grossPay,
                shift.costCenter(),
                null  // payGroupCode sourced from credentials at export time
        );
    }
}
