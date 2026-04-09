package com.hcare.integration.payroll.csv;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * Describes which fields to include in a CSV payroll export and how to format dates.
 *
 * <p>Instances are typically deserialized from a JSON configuration string stored in
 * {@code AgencyIntegrationConfig.configJson}.
 *
 * <p>Example JSON:
 * <pre>{@code
 * {"columns":["caregiverId","regularHours","grossPay"],"dateFormat":"yyyy-MM-dd"}
 * }</pre>
 */
public record CsvFieldMapping(List<String> columns, String dateFormat) {

    /**
     * Deserializes a {@link CsvFieldMapping} from a JSON string.
     *
     * @param configJson a JSON object with {@code columns} (string array) and {@code dateFormat}
     * @param mapper     the Jackson {@link ObjectMapper} to use for parsing
     * @return the parsed mapping
     * @throws RuntimeException if the JSON cannot be parsed
     */
    public static CsvFieldMapping fromJson(String configJson, ObjectMapper mapper) {
        try {
            return mapper.readValue(configJson, CsvFieldMapping.class);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Failed to parse CsvFieldMapping from JSON: " + configJson, e);
        }
    }
}
