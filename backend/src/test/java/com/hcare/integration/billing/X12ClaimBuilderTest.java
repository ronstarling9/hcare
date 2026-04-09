package com.hcare.integration.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class X12ClaimBuilderTest {

    private static final UUID AGENCY_ID = UUID.randomUUID();
    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID PROVIDER_ID = UUID.randomUUID();
    private static final String VALID_NPI = "1234567893";
    private static final String TAX_ID = "123456789";
    private static final LocalDate SERVICE_DATE = LocalDate.of(2026, 3, 15);
    private static final String SERVICE_CODE = "T1019";
    private static final BigDecimal UNITS = BigDecimal.valueOf(4);
    private static final BigDecimal AMOUNT = BigDecimal.valueOf(200.00);
    private static final String PAYER_ID = "BCBS001";

    @Test
    void build_institutional_allRequiredFields_succeeds() {
        Claim claim = X12ClaimBuilder.institutional()
                .agencyId(AGENCY_ID)
                .payerId(PAYER_ID)
                .billingProvider(VALID_NPI, TAX_ID)
                .serviceDate(SERVICE_DATE)
                .serviceCode(SERVICE_CODE)
                .units(UNITS)
                .billedAmount(AMOUNT)
                .build();

        assertThat(claim.claimType()).isEqualTo(Claim.ClaimType.INSTITUTIONAL);
        assertThat(claim.agencyId()).isEqualTo(AGENCY_ID);
        assertThat(claim.billingNpi()).isEqualTo(VALID_NPI);
    }

    @Test
    void build_professional_allRequiredFields_succeeds() {
        Claim claim = X12ClaimBuilder.professional()
                .agencyId(AGENCY_ID)
                .payerId(PAYER_ID)
                .billingProvider(VALID_NPI, TAX_ID)
                .serviceDate(SERVICE_DATE)
                .serviceCode(SERVICE_CODE)
                .units(UNITS)
                .billedAmount(AMOUNT)
                .build();

        assertThat(claim.claimType()).isEqualTo(Claim.ClaimType.PROFESSIONAL);
    }

    @Test
    void build_missingAgencyId_throwsNPE() {
        assertThatThrownBy(() -> X12ClaimBuilder.institutional()
                .payerId(PAYER_ID)
                .billingProvider(VALID_NPI, TAX_ID)
                .serviceDate(SERVICE_DATE)
                .serviceCode(SERVICE_CODE)
                .units(UNITS)
                .billedAmount(AMOUNT)
                .build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void build_missingPayerId_throwsNPE() {
        assertThatThrownBy(() -> X12ClaimBuilder.institutional()
                .agencyId(AGENCY_ID)
                .billingProvider(VALID_NPI, TAX_ID)
                .serviceDate(SERVICE_DATE)
                .serviceCode(SERVICE_CODE)
                .units(UNITS)
                .billedAmount(AMOUNT)
                .build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void build_missingBillingNpi_throwsNPE() {
        assertThatThrownBy(() -> X12ClaimBuilder.institutional()
                .agencyId(AGENCY_ID)
                .payerId(PAYER_ID)
                .serviceDate(SERVICE_DATE)
                .serviceCode(SERVICE_CODE)
                .units(UNITS)
                .billedAmount(AMOUNT)
                .build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void build_missingServiceDate_throwsNPE() {
        assertThatThrownBy(() -> X12ClaimBuilder.institutional()
                .agencyId(AGENCY_ID)
                .payerId(PAYER_ID)
                .billingProvider(VALID_NPI, TAX_ID)
                .serviceCode(SERVICE_CODE)
                .units(UNITS)
                .billedAmount(AMOUNT)
                .build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void build_missingServiceCode_throwsNPE() {
        assertThatThrownBy(() -> X12ClaimBuilder.institutional()
                .agencyId(AGENCY_ID)
                .payerId(PAYER_ID)
                .billingProvider(VALID_NPI, TAX_ID)
                .serviceDate(SERVICE_DATE)
                .units(UNITS)
                .billedAmount(AMOUNT)
                .build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void build_missingUnits_throwsNPE() {
        assertThatThrownBy(() -> X12ClaimBuilder.institutional()
                .agencyId(AGENCY_ID)
                .payerId(PAYER_ID)
                .billingProvider(VALID_NPI, TAX_ID)
                .serviceDate(SERVICE_DATE)
                .serviceCode(SERVICE_CODE)
                .billedAmount(AMOUNT)
                .build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void build_missingBilledAmount_throwsNPE() {
        assertThatThrownBy(() -> X12ClaimBuilder.institutional()
                .agencyId(AGENCY_ID)
                .payerId(PAYER_ID)
                .billingProvider(VALID_NPI, TAX_ID)
                .serviceDate(SERVICE_DATE)
                .serviceCode(SERVICE_CODE)
                .units(UNITS)
                .build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void build_optionalFieldsArePropagated() {
        List<String> diagnoses = List.of("Z87.39", "I10");

        Claim claim = X12ClaimBuilder.institutional()
                .agencyId(AGENCY_ID)
                .clientId(CLIENT_ID)
                .providerId(PROVIDER_ID)
                .payerId(PAYER_ID)
                .billingProvider(VALID_NPI, TAX_ID)
                .serviceDate(SERVICE_DATE)
                .serviceCode(SERVICE_CODE)
                .units(UNITS)
                .billedAmount(AMOUNT)
                .authorizationNumber("AUTH-001")
                .diagnosisCodes(diagnoses)
                .build();

        assertThat(claim.clientId()).isEqualTo(CLIENT_ID);
        assertThat(claim.providerId()).isEqualTo(PROVIDER_ID);
        assertThat(claim.billingTaxId()).isEqualTo(TAX_ID);
        assertThat(claim.priorAuthNumber()).isEqualTo("AUTH-001");
        assertThat(claim.diagnosisCodes()).containsExactly("Z87.39", "I10");
    }

    @Test
    void build_diagnosisCodes_nullInput_returnsEmptyList() {
        Claim claim = X12ClaimBuilder.institutional()
                .agencyId(AGENCY_ID)
                .payerId(PAYER_ID)
                .billingProvider(VALID_NPI, TAX_ID)
                .serviceDate(SERVICE_DATE)
                .serviceCode(SERVICE_CODE)
                .units(UNITS)
                .billedAmount(AMOUNT)
                .build();

        assertThat(claim.diagnosisCodes()).isEmpty();
    }
}
