package com.hcare.integration.payroll;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hcare.integration.payroll.csv.CsvExportStrategy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CsvExportStrategyTest {

    private CsvExportStrategy strategy;
    private AgencyPayrollCredentials defaultCreds;

    @BeforeEach
    void setUp() {
        strategy = new CsvExportStrategy(new ObjectMapper());
        defaultCreds = new AgencyPayrollCredentials("api-key", "COMP01", "WEEKLY", null);
    }

    @Test
    void export_nullAgencyId_returnsValidationError() {
        PayrollBatch batch = new PayrollBatch(
                null,
                "2026-W10",
                LocalDate.of(2026, 3, 2),
                LocalDate.of(2026, 3, 8),
                List.of(shift(UUID.randomUUID(), 8)));

        PayrollExportResult result = strategy.export(batch, defaultCreds);

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void export_emptyShifts_returnsValidationError() {
        PayrollBatch batch = new PayrollBatch(
                UUID.randomUUID(),
                "2026-W10",
                LocalDate.of(2026, 3, 2),
                LocalDate.of(2026, 3, 8),
                List.of());

        PayrollExportResult result = strategy.export(batch, defaultCreds);

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void export_singleShift_regularHoursOnly() {
        UUID caregiverId = UUID.randomUUID();
        PayrollBatch batch = batch(caregiverId, List.of(shift(caregiverId, 8)));

        PayrollExportResult result = strategy.export(batch, defaultCreds);

        assertThat(result.success()).isTrue();
        assertThat(result.recordCount()).isEqualTo(1);
    }

    @Test
    void export_singleShift_dailyOvertime() {
        UUID caregiverId = UUID.randomUUID();
        PayrollBatch batch = batch(caregiverId, List.of(shift(caregiverId, 10)));

        PayrollExportResult result = strategy.export(batch, defaultCreds);

        assertThat(result.success()).isTrue();
        assertThat(result.recordCount()).isEqualTo(1);
    }

    @Test
    void export_multipleCaregiversGroupedSeparately() {
        UUID cg1 = UUID.randomUUID();
        UUID cg2 = UUID.randomUUID();
        UUID agencyId = UUID.randomUUID();
        PayrollBatch batch = new PayrollBatch(
                agencyId,
                "2026-W10",
                LocalDate.of(2026, 3, 2),
                LocalDate.of(2026, 3, 8),
                List.of(shift(cg1, 8), shift(cg2, 8)));

        PayrollExportResult result = strategy.export(batch, defaultCreds);

        assertThat(result.success()).isTrue();
        assertThat(result.recordCount()).isEqualTo(2);
    }

    @Test
    void export_periodOvertime_80hRule() {
        UUID caregiverId = UUID.randomUUID();
        // 82h total across 11 days (8h each, last day 10h to trigger period OT)
        List<PayrollableShift> shifts = new java.util.ArrayList<>();
        LocalDate base = LocalDate.of(2026, 3, 2);
        for (int i = 0; i < 10; i++) {
            shifts.add(shiftOnDate(caregiverId, base.plusDays(i), 8));
        }
        // 11th day: 2h more → total 82h, period OT = 2h
        shifts.add(shiftOnDate(caregiverId, base.plusDays(10), 2));

        PayrollBatch batch = new PayrollBatch(
                UUID.randomUUID(),
                "2026-W10",
                base,
                base.plusDays(10),
                shifts);

        PayrollExportResult result = strategy.export(batch, defaultCreds);

        assertThat(result.success()).isTrue();
        assertThat(result.recordCount()).isEqualTo(1);
    }

    @Test
    void export_flsa8_80_dailyOtDominatesOverPeriodOt() {
        // One caregiver: one shift with 12h (4h daily OT), total 12h (well under 80h period OT)
        // Daily OT (4h) > Period OT (0h) → dailyOT dominates
        UUID caregiverId = UUID.randomUUID();
        PayrollBatch batch = batch(caregiverId, List.of(shift(caregiverId, 12)));

        PayrollExportResult result = strategy.export(batch, defaultCreds);

        assertThat(result.success()).isTrue();
        assertThat(result.recordCount()).isEqualTo(1);
    }

    private PayrollBatch batch(UUID caregiverId, List<PayrollableShift> shifts) {
        return new PayrollBatch(
                UUID.randomUUID(),
                "2026-W10",
                LocalDate.of(2026, 3, 2),
                LocalDate.of(2026, 3, 8),
                shifts);
    }

    private PayrollableShift shift(UUID caregiverId, int hours) {
        return shiftOnDate(caregiverId, LocalDate.of(2026, 3, 3), hours);
    }

    private PayrollableShift shiftOnDate(UUID caregiverId, LocalDate date, int hours) {
        LocalDateTime timeIn = date.atTime(8, 0);
        LocalDateTime timeOut = timeIn.plusHours(hours);
        return new PayrollableShift(
                UUID.randomUUID(),
                caregiverId,
                UUID.randomUUID(),
                date,
                timeIn,
                timeOut,
                BigDecimal.valueOf(20.00),
                BigDecimal.valueOf(30.00),
                "CC-01",
                "2026-W10",
                BigDecimal.valueOf(hours),
                true);
    }
}
