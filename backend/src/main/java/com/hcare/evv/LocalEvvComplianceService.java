package com.hcare.evv;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hcare.domain.EvvRecord;
import com.hcare.domain.PayerType;
import com.hcare.domain.Shift;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class LocalEvvComplianceService implements EvvComplianceService {

    private static final Logger log = LoggerFactory.getLogger(LocalEvvComplianceService.class);

    private final ObjectMapper objectMapper;

    public LocalEvvComplianceService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    private static final double EARTH_RADIUS_MILES = 3958.8;

    /** Clock-in more than this many minutes from scheduled start is a YELLOW time anomaly. */
    static final long TIME_ANOMALY_THRESHOLD_MINUTES = 30;

    @Override
    public EvvComplianceStatus compute(EvvRecord record, EvvStateConfig stateConfig,
                                        Shift shift, PayerType payerType,
                                        BigDecimal clientLat, BigDecimal clientLng) {
        // GREY: visit not started
        if (record == null) {
            return EvvComplianceStatus.GREY;
        }
        // EXEMPT: live-in caregiver or private-pay payer
        if (record.isCoResident() || payerType == PayerType.PRIVATE_PAY) {
            return EvvComplianceStatus.EXEMPT;
        }
        // RED: any of the 6 federal elements missing or no clock-out
        // Element 1 (serviceType) and 3 (dateOfService) are always derivable from Shift — not null-checked.
        // Element 5 (caregiverId): null when shift is OPEN; clockIn guard prevents this in production,
        // but compliance engine is authoritative for all 6 elements.
        if (record.getClientMedicaidId() == null    // element 2
                || record.getLocationLat() == null   // element 4 (GPS)
                || record.getLocationLon() == null
                || record.getTimeIn() == null        // element 6 (timeIn)
                || record.getTimeOut() == null       // element 6 (timeOut)
                || shift.getCaregiverId() == null) { // element 5
            return EvvComplianceStatus.RED;
        }

        // All required elements present — evaluate quality conditions.
        // CLOSED state is handled first because it supersedes other checks.
        // HYBRID states are treated identically to OPEN for compliance computation — aggregator
        // selection at submission time handles payer-specific routing (PayerEvvRoutingConfig).
        // HYBRID does not change the quality-check rules applied here.
        if (stateConfig.getSystemModel() == EvvSystemModel.CLOSED) {
            return stateConfig.isClosedSystemAcknowledgedByAgency()
                ? EvvComplianceStatus.PORTAL_SUBMIT
                : EvvComplianceStatus.YELLOW;
        }

        // MANUAL verification method — agency override, always YELLOW
        if (record.getVerificationMethod() == VerificationMethod.MANUAL) {
            return EvvComplianceStatus.YELLOW;
        }

        // Verification method not in state's allowed set → YELLOW
        Set<VerificationMethod> allowedMethods = parseAllowedMethods(stateConfig.getAllowedVerificationMethods());
        if (!allowedMethods.contains(record.getVerificationMethod())) {
            return EvvComplianceStatus.YELLOW;
        }

        // GPS tolerance check — only when stateConfig publishes a tolerance AND client is geocoded
        if (stateConfig.getGpsToleranceMiles() != null && clientLat != null && clientLng != null) {
            double distanceMiles = haversineDistanceMiles(
                record.getLocationLat(), record.getLocationLon(), clientLat, clientLng);
            if (distanceMiles > stateConfig.getGpsToleranceMiles().doubleValue()) {
                return EvvComplianceStatus.YELLOW;
            }
        }

        // Time anomaly: clock-in more than 30 minutes from scheduled start
        long minutesFromScheduled = Math.abs(
            Duration.between(shift.getScheduledStart(), record.getTimeIn()).toMinutes());
        if (minutesFromScheduled > TIME_ANOMALY_THRESHOLD_MINUTES) {
            return EvvComplianceStatus.YELLOW;
        }

        return EvvComplianceStatus.GREEN;
    }

    /**
     * Parses a JSON TEXT array of VerificationMethod names e.g. ["GPS","TELEPHONY_LANDLINE"].
     * Unknown names (e.g., a new enum value seeded in DB before the code deploys) are skipped
     * with a warning rather than throwing — fail-open keeps compliant visits GREEN.
     * Package-private for direct test access.
     */
    Set<VerificationMethod> parseAllowedMethods(String json) {
        if (json == null || json.isBlank()) return EnumSet.noneOf(VerificationMethod.class);
        try {
            List<String> names = objectMapper.readValue(json, new TypeReference<List<String>>() {});
            return names.stream()
                .flatMap(s -> {
                    try {
                        return java.util.stream.Stream.of(VerificationMethod.valueOf(s));
                    } catch (IllegalArgumentException e) {
                        log.warn("Unknown VerificationMethod '{}' in EvvStateConfig — skipping", s);
                        return java.util.stream.Stream.empty();
                    }
                })
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(VerificationMethod.class)));
        } catch (Exception e) {
            log.error("Failed to parse allowedVerificationMethods JSON '{}' — treating as empty", json, e);
            return EnumSet.noneOf(VerificationMethod.class);
        }
    }

    /**
     * Haversine great-circle distance between two lat/lng points in miles.
     * Sufficient for the GPS proximity check — no per-request Maps API call needed.
     */
    static double haversineDistanceMiles(BigDecimal lat1, BigDecimal lon1,
                                          BigDecimal lat2, BigDecimal lon2) {
        double dLat = Math.toRadians(lat2.doubleValue() - lat1.doubleValue());
        double dLon = Math.toRadians(lon2.doubleValue() - lon1.doubleValue());
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1.doubleValue()))
                 * Math.cos(Math.toRadians(lat2.doubleValue()))
                 * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return EARTH_RADIUS_MILES * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
