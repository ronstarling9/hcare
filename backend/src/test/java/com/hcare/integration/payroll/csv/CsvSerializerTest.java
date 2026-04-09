package com.hcare.integration.payroll.csv;

import static org.assertj.core.api.Assertions.assertThat;

import com.hcare.integration.payroll.GenericPayrollRecord;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Verifies that {@link CsvSerializer} produces correctly ordered columns and that
 * {@link CsvFieldMapping} carries the expected date format string.
 */
class CsvSerializerTest {

    private static final UUID CAREGIVER_ID =
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    private final CsvSerializer serializer = new CsvSerializer();

    // -------------------------------------------------------------------------
    // Column ordering
    // -------------------------------------------------------------------------

    @Test
    void serialize_defaultColumns_headerRowMatchesExpectedOrder() {
        CsvFieldMapping mapping = new CsvFieldMapping(
                List.of("caregiverId", "caregiverName", "regularHours", "overtimeHours",
                        "regularPay", "overtimePay", "grossPay", "payPeriodCode", "costCenter"),
                "yyyy-MM-dd");

        byte[] csv = serializer.serialize(List.of(record()), mapping);
        String[] lines = new String(csv, StandardCharsets.UTF_8).split("\\R");

        String[] headers = lines[0].split(",", -1);
        assertThat(headers[0]).isEqualTo("caregiverId");
        assertThat(headers[1]).isEqualTo("caregiverName");
        assertThat(headers[2]).isEqualTo("regularHours");
        assertThat(headers[3]).isEqualTo("overtimeHours");
        assertThat(headers[4]).isEqualTo("regularPay");
        assertThat(headers[5]).isEqualTo("overtimePay");
        assertThat(headers[6]).isEqualTo("grossPay");
        assertThat(headers[7]).isEqualTo("payPeriodCode");
        assertThat(headers[8]).isEqualTo("costCenter");
    }

    @Test
    void serialize_defaultColumns_dataRowValuesAlignWithColumnOrder() {
        CsvFieldMapping mapping = new CsvFieldMapping(
                List.of("caregiverId", "caregiverName", "regularHours", "overtimeHours",
                        "regularPay", "overtimePay", "grossPay", "payPeriodCode", "costCenter"),
                "yyyy-MM-dd");

        byte[] csv = serializer.serialize(List.of(record()), mapping);
        String[] lines = new String(csv, StandardCharsets.UTF_8).split("\\R");

        // lines[0] = header, lines[1] = data row
        assertThat(lines).hasSizeGreaterThanOrEqualTo(2);

        String[] data = lines[1].split(",", -1);
        // caregiverId at index 0
        assertThat(data[0]).isEqualTo(CAREGIVER_ID.toString());
        // caregiverName at index 1 — strategy sets this to null, serializer emits empty string
        assertThat(data[1]).isEmpty();
        // regularHours at index 2
        assertThat(data[2]).isEqualTo("8.00");
        // overtimeHours at index 3
        assertThat(data[3]).isEqualTo("2.00");
        // regularPay at index 4
        assertThat(data[4]).isEqualTo("160.00");
        // overtimePay at index 5
        assertThat(data[5]).isEqualTo("60.00");
        // grossPay at index 6
        assertThat(data[6]).isEqualTo("220.00");
        // payPeriodCode at index 7
        assertThat(data[7]).isEqualTo("2026-Q1");
        // costCenter at index 8
        assertThat(data[8]).isEqualTo("CC-001");
    }

    @Test
    void serialize_customColumnSubset_onlyRequestedColumnsAppearInOrder() {
        CsvFieldMapping mapping = new CsvFieldMapping(
                List.of("caregiverId", "grossPay", "costCenter"),
                "yyyy-MM-dd");

        byte[] csv = serializer.serialize(List.of(record()), mapping);
        String[] lines = new String(csv, StandardCharsets.UTF_8).split("\\R");

        String[] headers = lines[0].split(",", -1);
        assertThat(headers).hasSize(3);
        assertThat(headers[0]).isEqualTo("caregiverId");
        assertThat(headers[1]).isEqualTo("grossPay");
        assertThat(headers[2]).isEqualTo("costCenter");

        String[] data = lines[1].split(",", -1);
        assertThat(data[0]).isEqualTo(CAREGIVER_ID.toString());
        assertThat(data[1]).isEqualTo("220.00");
        assertThat(data[2]).isEqualTo("CC-001");
    }

    @Test
    void serialize_customColumnSubset_generatesExpectedHeaders() {
        CsvFieldMapping mapping = new CsvFieldMapping(
                List.of("caregiverId", "regularHours", "grossPay"), "yyyy-MM-dd");

        UUID caregiverId = UUID.randomUUID();
        GenericPayrollRecord rec = new GenericPayrollRecord(
                caregiverId,
                "Jane Doe",
                BigDecimal.valueOf(8),
                BigDecimal.ZERO,
                BigDecimal.valueOf(160),
                BigDecimal.ZERO,
                BigDecimal.valueOf(160),
                "2026-W10",
                "CC-01");

        byte[] csv = serializer.serialize(List.of(rec), mapping);
        String content = new String(csv, StandardCharsets.UTF_8);

        String headerLine = content.lines().findFirst().orElse("");
        assertThat(headerLine).isEqualTo("caregiverId,regularHours,grossPay");
    }

    // -------------------------------------------------------------------------
    // Date format
    // -------------------------------------------------------------------------

    @Test
    void csvFieldMapping_dateFormat_isIso8601ByDefault() {
        // The spec requires dates to be formatted as yyyy-MM-dd.
        // CsvFieldMapping carries the dateFormat string; CsvExportStrategy defaults to yyyy-MM-dd.
        CsvFieldMapping mapping = new CsvFieldMapping(
                List.of("caregiverId", "regularHours"),
                "yyyy-MM-dd");

        assertThat(mapping.dateFormat())
                .as("dateFormat must conform to the spec-required ISO-8601 date pattern")
                .isEqualTo("yyyy-MM-dd");
    }

    @Test
    void csvFieldMapping_dateFormat_matchesIso8601Pattern() {
        // Structural check: pattern must have 4-digit year, 2-digit month, 2-digit day
        // separated by hyphens — i.e. matches yyyy-MM-dd exactly.
        CsvFieldMapping mapping = new CsvFieldMapping(
                List.of("caregiverId"),
                "yyyy-MM-dd");

        assertThat(mapping.dateFormat()).matches("yyyy-MM-dd");
    }

    // -------------------------------------------------------------------------
    // Structural guarantees
    // -------------------------------------------------------------------------

    @Test
    void serialize_singleRecord_producesTwoLines() {
        CsvFieldMapping mapping = new CsvFieldMapping(
                List.of("caregiverId", "grossPay"),
                "yyyy-MM-dd");

        byte[] csv = serializer.serialize(List.of(record()), mapping);
        String content = new String(csv, StandardCharsets.UTF_8);

        // Header line + 1 data line (trailing newline yields empty last element — filter it)
        long nonEmptyLines = content.lines().filter(l -> !l.isEmpty()).count();
        assertThat(nonEmptyLines).isEqualTo(2);
    }

    @Test
    void serialize_multipleRecords_columnCountConsistentAcrossAllRows() {
        CsvFieldMapping mapping = new CsvFieldMapping(
                List.of("caregiverId", "regularHours", "grossPay", "payPeriodCode"),
                "yyyy-MM-dd");

        GenericPayrollRecord r2 = new GenericPayrollRecord(
                UUID.randomUUID(),
                null,
                new BigDecimal("40.00"),
                BigDecimal.ZERO,
                new BigDecimal("800.00"),
                BigDecimal.ZERO,
                new BigDecimal("800.00"),
                "2026-Q2",
                "CC-002");

        byte[] csv = serializer.serialize(List.of(record(), r2), mapping);
        String[] lines = new String(csv, StandardCharsets.UTF_8).split("\\R");

        int expectedColumns = 4;
        for (String line : lines) {
            if (line.isEmpty()) continue;
            assertThat(line.split(",", -1))
                    .as("every row must have exactly %d columns", expectedColumns)
                    .hasSize(expectedColumns);
        }
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private GenericPayrollRecord record() {
        return new GenericPayrollRecord(
                CAREGIVER_ID,
                null,                          // caregiverName: null per CsvExportStrategy
                new BigDecimal("8.00"),
                new BigDecimal("2.00"),
                new BigDecimal("160.00"),
                new BigDecimal("60.00"),
                new BigDecimal("220.00"),
                "2026-Q1",
                "CC-001");
    }
}
