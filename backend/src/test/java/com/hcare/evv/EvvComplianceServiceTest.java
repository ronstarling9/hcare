package com.hcare.evv;

import com.hcare.domain.EvvRecord;
import com.hcare.domain.PayerType;
import com.hcare.domain.Shift;
import com.hcare.domain.ShiftStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

// LENIENT strictness: tests that exit early (null record → GREY, coResident → EXEMPT, etc.)
// never exercise all @BeforeEach stateConfig stubs. Mockito 4+ (Spring Boot 3.x default:
// STRICT_STUBS) would throw UnnecessaryStubbingException for those unused stubs.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EvvComplianceServiceTest {

    @Mock EvvStateConfig stateConfig;
    EvvComplianceService service;

    // Shift scheduled 2026-04-20 09:00–13:00
    static final LocalDateTime SCHEDULED_START = LocalDateTime.of(2026, 4, 20, 9, 0);
    static final LocalDateTime SCHEDULED_END   = LocalDateTime.of(2026, 4, 20, 13, 0);

    // Client geocoded at Austin, TX
    static final BigDecimal CLIENT_LAT = new BigDecimal("30.2672");
    static final BigDecimal CLIENT_LNG = new BigDecimal("-97.7431");

    @BeforeEach
    void setup() {
        service = new LocalEvvComplianceService();
        // Default: OPEN state, GPS allowed, no tolerance published
        when(stateConfig.getSystemModel()).thenReturn(EvvSystemModel.OPEN);
        when(stateConfig.getAllowedVerificationMethods()).thenReturn("[\"GPS\",\"TELEPHONY_LANDLINE\",\"TELEPHONY_CELL\",\"FIXED_DEVICE\",\"FOB\",\"BIOMETRIC\"]");
        when(stateConfig.getGpsToleranceMiles()).thenReturn(null);
        when(stateConfig.isClosedSystemAcknowledgedByAgency()).thenReturn(false);
    }

    private Shift buildShift() {
        return new Shift(
            UUID.randomUUID(), null, UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), null, SCHEDULED_START, SCHEDULED_END
        );
    }

    private EvvRecord buildCompleteRecord() {
        EvvRecord r = new EvvRecord(UUID.randomUUID(), UUID.randomUUID(), VerificationMethod.GPS);
        r.setClientMedicaidId("TX12345");
        r.setLocationLat(CLIENT_LAT);
        r.setLocationLon(CLIENT_LNG);
        r.setTimeIn(LocalDateTime.of(2026, 4, 20, 9, 5));
        r.setTimeOut(LocalDateTime.of(2026, 4, 20, 13, 10));
        return r;
    }

    @Test
    void null_record_returns_grey() {
        assertThat(service.compute(null, stateConfig, buildShift(), null, CLIENT_LAT, CLIENT_LNG))
            .isEqualTo(EvvComplianceStatus.GREY);
    }

    @Test
    void co_resident_returns_exempt() {
        EvvRecord r = buildCompleteRecord();
        r.setCoResident(true);
        assertThat(service.compute(r, stateConfig, buildShift(), null, CLIENT_LAT, CLIENT_LNG))
            .isEqualTo(EvvComplianceStatus.EXEMPT);
    }

    @Test
    void private_pay_payer_returns_exempt() {
        assertThat(service.compute(buildCompleteRecord(), stateConfig, buildShift(),
            PayerType.PRIVATE_PAY, CLIENT_LAT, CLIENT_LNG))
            .isEqualTo(EvvComplianceStatus.EXEMPT);
    }

    @Test
    void missing_medicaid_id_returns_red() {
        EvvRecord r = buildCompleteRecord();
        r.setClientMedicaidId(null);
        assertThat(service.compute(r, stateConfig, buildShift(), null, CLIENT_LAT, CLIENT_LNG))
            .isEqualTo(EvvComplianceStatus.RED);
    }

    @Test
    void missing_location_returns_red() {
        EvvRecord r = buildCompleteRecord();
        r.setLocationLat(null);
        assertThat(service.compute(r, stateConfig, buildShift(), null, CLIENT_LAT, CLIENT_LNG))
            .isEqualTo(EvvComplianceStatus.RED);
    }

    @Test
    void missing_time_in_returns_red() {
        EvvRecord r = buildCompleteRecord();
        r.setTimeIn(null);
        assertThat(service.compute(r, stateConfig, buildShift(), null, CLIENT_LAT, CLIENT_LNG))
            .isEqualTo(EvvComplianceStatus.RED);
    }

    @Test
    void missing_time_out_returns_red() {
        EvvRecord r = buildCompleteRecord();
        r.setTimeOut(null);
        assertThat(service.compute(r, stateConfig, buildShift(), null, CLIENT_LAT, CLIENT_LNG))
            .isEqualTo(EvvComplianceStatus.RED);
    }

    @Test
    void closed_state_not_acknowledged_returns_yellow() {
        when(stateConfig.getSystemModel()).thenReturn(EvvSystemModel.CLOSED);
        when(stateConfig.isClosedSystemAcknowledgedByAgency()).thenReturn(false);
        assertThat(service.compute(buildCompleteRecord(), stateConfig, buildShift(), null, CLIENT_LAT, CLIENT_LNG))
            .isEqualTo(EvvComplianceStatus.YELLOW);
    }

    @Test
    void closed_state_acknowledged_returns_portal_submit() {
        when(stateConfig.getSystemModel()).thenReturn(EvvSystemModel.CLOSED);
        when(stateConfig.isClosedSystemAcknowledgedByAgency()).thenReturn(true);
        assertThat(service.compute(buildCompleteRecord(), stateConfig, buildShift(), null, CLIENT_LAT, CLIENT_LNG))
            .isEqualTo(EvvComplianceStatus.PORTAL_SUBMIT);
    }

    @Test
    void manual_verification_method_returns_yellow() {
        // verificationMethod is set in constructor so re-create with MANUAL directly
        EvvRecord manual = new EvvRecord(UUID.randomUUID(), UUID.randomUUID(), VerificationMethod.MANUAL);
        manual.setClientMedicaidId("TX12345");
        manual.setLocationLat(CLIENT_LAT);
        manual.setLocationLon(CLIENT_LNG);
        manual.setTimeIn(LocalDateTime.of(2026, 4, 20, 9, 5));
        manual.setTimeOut(LocalDateTime.of(2026, 4, 20, 13, 10));
        assertThat(service.compute(manual, stateConfig, buildShift(), null, CLIENT_LAT, CLIENT_LNG))
            .isEqualTo(EvvComplianceStatus.YELLOW);
    }

    @Test
    void time_anomaly_over_30_minutes_returns_yellow() {
        EvvRecord r = buildCompleteRecord();
        // Clock-in 45 minutes after scheduled start
        r.setTimeIn(LocalDateTime.of(2026, 4, 20, 9, 45));
        assertThat(service.compute(r, stateConfig, buildShift(), null, CLIENT_LAT, CLIENT_LNG))
            .isEqualTo(EvvComplianceStatus.YELLOW);
    }

    @Test
    void time_anomaly_exactly_30_minutes_returns_green() {
        EvvRecord r = buildCompleteRecord();
        // Exactly 30 minutes — threshold is >, so this is still GREEN
        r.setTimeIn(LocalDateTime.of(2026, 4, 20, 9, 30));
        assertThat(service.compute(r, stateConfig, buildShift(), null, CLIENT_LAT, CLIENT_LNG))
            .isEqualTo(EvvComplianceStatus.GREEN);
    }

    @Test
    void gps_outside_tolerance_returns_yellow() {
        when(stateConfig.getGpsToleranceMiles()).thenReturn(new BigDecimal("0.5"));
        EvvRecord r = buildCompleteRecord();
        // ~6.9 miles north of the client — way outside 0.5-mile tolerance
        r.setLocationLat(new BigDecimal("30.3672"));
        r.setLocationLon(new BigDecimal("-97.7431"));
        assertThat(service.compute(r, stateConfig, buildShift(), null, CLIENT_LAT, CLIENT_LNG))
            .isEqualTo(EvvComplianceStatus.YELLOW);
    }

    @Test
    void gps_within_tolerance_does_not_trigger_yellow() {
        when(stateConfig.getGpsToleranceMiles()).thenReturn(new BigDecimal("0.5"));
        EvvRecord r = buildCompleteRecord();
        // Same coordinates as client — 0 miles
        r.setLocationLat(CLIENT_LAT);
        r.setLocationLon(CLIENT_LNG);
        assertThat(service.compute(r, stateConfig, buildShift(), null, CLIENT_LAT, CLIENT_LNG))
            .isEqualTo(EvvComplianceStatus.GREEN);
    }

    @Test
    void null_client_gps_skips_tolerance_check() {
        when(stateConfig.getGpsToleranceMiles()).thenReturn(new BigDecimal("0.5"));
        // Client has no geocoded coordinates — tolerance check must be skipped, not throw
        assertThat(service.compute(buildCompleteRecord(), stateConfig, buildShift(), null, null, null))
            .isEqualTo(EvvComplianceStatus.GREEN);
    }

    @Test
    void all_conditions_met_returns_green() {
        assertThat(service.compute(buildCompleteRecord(), stateConfig, buildShift(), null, CLIENT_LAT, CLIENT_LNG))
            .isEqualTo(EvvComplianceStatus.GREEN);
    }

    @Test
    void non_medicaid_payer_is_not_exempt() {
        // MEDICAID payer with all elements → GREEN (not EXEMPT)
        assertThat(service.compute(buildCompleteRecord(), stateConfig, buildShift(),
            PayerType.MEDICAID, CLIENT_LAT, CLIENT_LNG))
            .isEqualTo(EvvComplianceStatus.GREEN);
    }

    @Test
    void hybrid_state_uses_same_quality_checks_as_open() {
        // HYBRID states fall through to quality checks — same rules as OPEN
        when(stateConfig.getSystemModel()).thenReturn(EvvSystemModel.HYBRID);
        assertThat(service.compute(buildCompleteRecord(), stateConfig, buildShift(), null, CLIENT_LAT, CLIENT_LNG))
            .isEqualTo(EvvComplianceStatus.GREEN);
    }

    @Test
    void missing_caregiver_id_returns_red() {
        // Shift with null caregiverId (OPEN shift) — missing federal element 5
        Shift openShift = new Shift(
            UUID.randomUUID(), null, UUID.randomUUID(), null, // null caregiverId
            UUID.randomUUID(), null, SCHEDULED_START, SCHEDULED_END
        );
        assertThat(service.compute(buildCompleteRecord(), stateConfig, openShift, null, CLIENT_LAT, CLIENT_LNG))
            .isEqualTo(EvvComplianceStatus.RED);
    }
}
