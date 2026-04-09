package com.hcare.integration.payroll.csv;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
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

}
