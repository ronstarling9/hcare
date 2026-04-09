package com.hcare.integration.payroll;

/**
 * Credentials and configuration required to authenticate against a payroll system API.
 *
 * <p>{@code companyCode} identifies the agency within the payroll provider's tenant.
 * {@code payGroupCode} selects the payroll processing group (e.g., weekly vs biweekly caregivers).
 * {@code configJson} holds strategy-specific configuration as a JSON string (e.g., CSV column
 * mappings for {@code CsvExportStrategy}).
 */
public record AgencyPayrollCredentials(
        String apiKey, String companyCode, String payGroupCode, String configJson) {}
