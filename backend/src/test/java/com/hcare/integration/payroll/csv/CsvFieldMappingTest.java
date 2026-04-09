package com.hcare.integration.payroll.csv;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hcare.integration.payroll.GenericPayrollRecord;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CsvFieldMappingTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void csvFieldMapping_defaultColumns_preservesOrderAndDateFormat() {
        String configJson = "{\"columns\":[\"caregiverId\",\"regularHours\",\"overtimeHours\","
                + "\"grossPay\",\"payPeriodCode\"],\"dateFormat\":\"yyyy-MM-dd\"}";

        CsvFieldMapping mapping = CsvFieldMapping.fromJson(configJson, objectMapper);

        assertThat(mapping.columns()).containsExactly(
                "caregiverId", "regularHours", "overtimeHours", "grossPay", "payPeriodCode");
        assertThat(mapping.dateFormat()).isEqualTo("yyyy-MM-dd");
    }

    @Test
    void csvSerializer_generatesExpectedHeaders() {
        CsvFieldMapping mapping = new CsvFieldMapping(
                List.of("caregiverId", "regularHours", "grossPay"), "yyyy-MM-dd");
        CsvSerializer serializer = new CsvSerializer();

        UUID caregiverId = UUID.randomUUID();
        GenericPayrollRecord record = new GenericPayrollRecord(
                caregiverId,
                "Jane Doe",
                BigDecimal.valueOf(8),
                BigDecimal.ZERO,
                BigDecimal.valueOf(160),
                BigDecimal.ZERO,
                BigDecimal.valueOf(160),
                "2026-W10",
                "CC-01");

        byte[] csv = serializer.serialize(List.of(record), mapping);
        String content = new String(csv, java.nio.charset.StandardCharsets.UTF_8);

        String headerLine = content.lines().findFirst().orElse("");
        assertThat(headerLine).isEqualTo("caregiverId,regularHours,grossPay");
    }
}
