package com.hcare.scoring;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hcare.domain.*;
import com.hcare.multitenancy.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class LocalScoringService implements ScoringService {

    private static final Logger log = LoggerFactory.getLogger(LocalScoringService.class);

    // Scoring weights — must sum to 1.0
    static final double W_DISTANCE    = 0.30;
    static final double W_CONTINUITY  = 0.25;
    static final double W_OVERTIME    = 0.20;
    static final double W_PREFERENCES = 0.15;
    static final double W_RELIABILITY = 0.10;

    /** Distance (miles) at which the distance score component reaches 0. */
    static final double MAX_DISTANCE_MILES = 25.0;

    /** Visit count with a specific client above which continuity score saturates at 1.0. */
    static final int CONTINUITY_SATURATION = 10;

    /** Projected weekly hours at or above which the OT-risk score is 0. */
    static final double OVERTIME_THRESHOLD_HOURS = 40.0;

    private static final double EARTH_RADIUS_MILES = 3958.8;

    private final CaregiverRepository caregiverRepository;
    private final CaregiverAvailabilityRepository availabilityRepository;
    private final CaregiverCredentialRepository credentialRepository;
    private final ShiftRepository shiftRepository;
    private final CaregiverScoringProfileRepository scoringProfileRepository;
    private final CaregiverClientAffinityRepository affinityRepository;
    private final AuthorizationRepository authorizationRepository;
    private final FeatureFlagsRepository featureFlagsRepository;
    private final ClientRepository clientRepository;
    private final ServiceTypeRepository serviceTypeRepository;
    private final ObjectMapper objectMapper;

    public LocalScoringService(CaregiverRepository caregiverRepository,
                                CaregiverAvailabilityRepository availabilityRepository,
                                CaregiverCredentialRepository credentialRepository,
                                ShiftRepository shiftRepository,
                                CaregiverScoringProfileRepository scoringProfileRepository,
                                CaregiverClientAffinityRepository affinityRepository,
                                AuthorizationRepository authorizationRepository,
                                FeatureFlagsRepository featureFlagsRepository,
                                ClientRepository clientRepository,
                                ServiceTypeRepository serviceTypeRepository,
                                ObjectMapper objectMapper) {
        this.caregiverRepository = caregiverRepository;
        this.availabilityRepository = availabilityRepository;
        this.credentialRepository = credentialRepository;
        this.shiftRepository = shiftRepository;
        this.scoringProfileRepository = scoringProfileRepository;
        this.affinityRepository = affinityRepository;
        this.authorizationRepository = authorizationRepository;
        this.featureFlagsRepository = featureFlagsRepository;
        this.clientRepository = clientRepository;
        this.serviceTypeRepository = serviceTypeRepository;
        this.objectMapper = objectMapper;
    }

    // ── public interface ──────────────────────────────────────────────────────

    /**
     * Returns candidates sorted by score descending (AI enabled) or unsorted with score=0
     * (AI disabled). N+1 repository calls per candidate are acceptable for P1 agency size
     * (1–25 caregivers). Batch pre-loading is a P2 optimization.
     */
    @Override
    @Transactional(readOnly = true)
    public List<RankedCaregiver> rankCandidates(ShiftMatchRequest request) {
        Client client = clientRepository.findById(request.clientId())
            .orElseThrow(() -> new IllegalArgumentException("Client not found: " + request.clientId()));
        ServiceType serviceType = serviceTypeRepository.findById(request.serviceTypeId())
            .orElseThrow(() -> new IllegalArgumentException("ServiceType not found: " + request.serviceTypeId()));

        Authorization authorization = request.authorizationId() != null
            ? authorizationRepository.findById(request.authorizationId())
                  .orElseThrow(() -> new IllegalArgumentException(
                      "Authorization not found: " + request.authorizationId()))
            : null;

        boolean aiEnabled = featureFlagsRepository.findByAgencyId(request.agencyId())
            .map(FeatureFlags::isAiSchedulingEnabled)
            .orElse(false);

        List<Caregiver> activeCaregivers = caregiverRepository.findByAgencyId(request.agencyId()).stream()
            .filter(c -> c.getStatus() == CaregiverStatus.ACTIVE)
            .collect(Collectors.toList());

        List<CredentialType> requiredCredentials = parseCredentialTypes(serviceType.getRequiredCredentials());
        LocalDate today = LocalDate.now();

        BigDecimal shiftHours = BigDecimal.valueOf(
            Duration.between(request.scheduledStart(), request.scheduledEnd()).toMinutes())
            .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);

        // Short-circuit: if the shift hours exceed remaining authorized units, no caregiver can be assigned.
        if (authorization != null
                && authorization.getUsedUnits().add(shiftHours)
                    .compareTo(authorization.getAuthorizedUnits()) > 0) {
            return Collections.emptyList();
        }

        List<RankedCaregiver> results = new ArrayList<>();
        for (Caregiver caregiver : activeCaregivers) {
            if (!passesHardFilters(caregiver, request, authorization, requiredCredentials, today, shiftHours)) {
                continue;
            }
            if (!aiEnabled) {
                results.add(new RankedCaregiver(caregiver.getId(), 0.0, null));
                continue;
            }

            CaregiverScoringProfile profile =
                scoringProfileRepository.findByCaregiverId(caregiver.getId()).orElse(null);
            int visitCount = 0;
            if (profile != null) {
                visitCount = affinityRepository
                    .findByScoringProfileIdAndClientId(profile.getId(), request.clientId())
                    .map(CaregiverClientAffinity::getVisitCount)
                    .orElse(0);
            }

            double rawDistance = computeRawDistance(caregiver, client);
            PreferenceResult prefs = computePreferencesScore(caregiver, client);
            double score = W_DISTANCE    * computeDistanceScore(rawDistance)
                         + W_CONTINUITY  * Math.min(1.0, visitCount / (double) CONTINUITY_SATURATION)
                         + W_OVERTIME    * computeOvertimeScore(profile, request)
                         + W_PREFERENCES * prefs.score()
                         + W_RELIABILITY * computeReliabilityScore(profile);

            results.add(new RankedCaregiver(
                caregiver.getId(), score,
                buildExplanation(rawDistance, visitCount, profile, request, prefs)));
        }

        results.sort(Comparator.comparingDouble(RankedCaregiver::score).reversed());
        return results;
    }

    // Fires synchronously (AFTER_COMMIT) in the VisitService.clockOut thread — acceptable
    // latency for P1 (1–25 caregivers: ~3–6 DB calls). Add @Async at P2 if profiling shows
    // this exceeds acceptable response budgets; note that @Async + REQUIRES_NEW requires an
    // explicit PlatformTransactionManager binding.
    //
    // C1: @TransactionalEventListener fires after the outer transaction commits. TenantContext
    // (ThreadLocal) has already been cleared by TenantFilterInterceptor.afterCompletion().
    // Re-bind before any repository call; clear in finally to prevent cross-tenant leakage.
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onShiftCompleted(ShiftCompletedEvent event) {
        TenantContext.set(event.agencyId()); // C1: re-bind before any repo call
        try {
            if (event.caregiverId() == null) return;

            if (!event.timeOut().isAfter(event.timeIn())) {
                log.error("ShiftCompletedEvent has non-positive duration — skipping profile update: " +
                    "shift={} caregiverId={} timeIn={} timeOut={}",
                    event.shiftId(), event.caregiverId(), event.timeIn(), event.timeOut());
                return;
            }

            CaregiverScoringProfile profile = scoringProfileRepository
                .findByCaregiverId(event.caregiverId())
                .orElseGet(() -> scoringProfileRepository.save(
                    new CaregiverScoringProfile(event.caregiverId(), event.agencyId())));

            long minutes = Duration.between(event.timeIn(), event.timeOut()).toMinutes();
            BigDecimal hours = BigDecimal.valueOf(minutes)
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
            profile.updateAfterShiftCompletion(hours);
            scoringProfileRepository.save(profile);

            updateAffinity(profile.getId(), event.clientId(), event.agencyId());
        } finally {
            TenantContext.clear(); // C1: always clean up
        }
    }

    // C1: same TenantContext re-bind fix applied here.
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onShiftCancelled(ShiftCancelledEvent event) {
        TenantContext.set(event.agencyId()); // C1: re-bind before any repo call
        try {
            if (event.caregiverId() == null) return;

            CaregiverScoringProfile profile = scoringProfileRepository
                .findByCaregiverId(event.caregiverId())
                .orElseGet(() -> scoringProfileRepository.save(
                    new CaregiverScoringProfile(event.caregiverId(), event.agencyId())));
            profile.updateAfterShiftCancellation();
            scoringProfileRepository.save(profile);
        } finally {
            TenantContext.clear(); // C1: always clean up
        }
    }

    /**
     * Resets currentWeekHours to 0 for all caregiver scoring profiles.
     * Runs every Monday at 00:00 UTC — Sunday evening for US agencies in most time zones.
     * Controlled by hcare.scoring.weekly-reset-cron (set to "-" in application-test.yml
     * to disable during integration tests).
     */
    @Scheduled(cron = "${hcare.scoring.weekly-reset-cron:0 0 0 * * MON}")
    @Transactional
    public void resetWeeklyHours() {
        log.info("Resetting currentWeekHours for all caregiver scoring profiles");
        scoringProfileRepository.resetAllWeeklyHours(LocalDateTime.now(ZoneOffset.UTC));
    }

    // ── hard filters ──────────────────────────────────────────────────────────

    private boolean passesHardFilters(Caregiver caregiver, ShiftMatchRequest request,
                                       Authorization authorization,
                                       List<CredentialType> requiredCredentials,
                                       LocalDate today,
                                       BigDecimal shiftHours) {
        // TODO(P2): Add caregiver.isOigExcluded() check once OIG API integration lands
        //           and the oig_excluded column is added to the caregivers table.
        return hasAvailability(caregiver.getId(), request.scheduledStart(), request.scheduledEnd())
            && !hasConflictingShift(caregiver.getId(), request.scheduledStart(), request.scheduledEnd())
            && hasRequiredCredentials(caregiver.getId(), requiredCredentials, today)
            && (authorization == null
                || authorization.getUsedUnits().add(shiftHours)
                    .compareTo(authorization.getAuthorizedUnits()) <= 0);
    }

    private boolean hasAvailability(UUID caregiverId, LocalDateTime start, LocalDateTime end) {
        // Overnight shifts (crosses midnight) are not modelled in P1 availability — pass through
        if (!start.toLocalDate().equals(end.toLocalDate())) return true;

        DayOfWeek day = start.getDayOfWeek();
        LocalTime shiftStart = start.toLocalTime();
        LocalTime shiftEnd   = end.toLocalTime();
        return availabilityRepository.findByCaregiverId(caregiverId).stream()
            .filter(w -> w.getDayOfWeek() == day)
            .anyMatch(w -> !w.getStartTime().isAfter(shiftStart) && !w.getEndTime().isBefore(shiftEnd));
    }

    private boolean hasConflictingShift(UUID caregiverId, LocalDateTime start, LocalDateTime end) {
        return shiftRepository.findOverlapping(caregiverId, start, end).stream()
            .anyMatch(s -> s.getStatus() != ShiftStatus.CANCELLED);
    }

    private boolean hasRequiredCredentials(UUID caregiverId, List<CredentialType> required, LocalDate today) {
        if (required.isEmpty()) return true;
        List<CaregiverCredential> credentials = credentialRepository.findByCaregiverId(caregiverId);
        for (CredentialType type : required) {
            boolean valid = credentials.stream()
                .filter(c -> c.getCredentialType() == type)
                .anyMatch(c -> c.isVerified()
                    && (c.getExpiryDate() == null || !c.getExpiryDate().isBefore(today)));
            if (!valid) return false;
        }
        return true;
    }

    // ── scoring ───────────────────────────────────────────────────────────────

    /** Returns raw haversine distance in miles, or -1.0 if either location is missing. */
    private double computeRawDistance(Caregiver caregiver, Client client) {
        if (caregiver.getHomeLat() == null || caregiver.getHomeLng() == null
                || client.getLat() == null || client.getLng() == null) {
            return -1.0;
        }
        return haversineDistanceMiles(
            caregiver.getHomeLat(), caregiver.getHomeLng(),
            client.getLat(), client.getLng());
    }

    /** 0.5 neutral when coordinates missing; otherwise max(0, 1 − distance/MAX_DISTANCE_MILES). */
    private double computeDistanceScore(double rawDistanceMiles) {
        if (rawDistanceMiles < 0) return 0.5;
        return Math.max(0.0, 1.0 - (rawDistanceMiles / MAX_DISTANCE_MILES));
    }

    private double projectedWeekHours(CaregiverScoringProfile profile, ShiftMatchRequest request) {
        double current = profile != null ? profile.getCurrentWeekHours().doubleValue() : 0.0;
        double shift   = Duration.between(request.scheduledStart(), request.scheduledEnd()).toMinutes() / 60.0;
        return current + shift;
    }

    /** 1.0 if projected week hours remain under threshold; 0.0 if OT risk. */
    private double computeOvertimeScore(CaregiverScoringProfile profile, ShiftMatchRequest request) {
        return projectedWeekHours(profile, request) < OVERTIME_THRESHOLD_HOURS ? 1.0 : 0.0;
    }

    /** Carries the preference score and the flags that drove it, for use in buildExplanation. */
    private record PreferenceResult(double score, boolean languageMismatch, boolean petConflict) {}

    /**
     * Base 1.0: deduct 0.5 for language mismatch (when client has preferences),
     * deduct 0.2 for pet mismatch. Clamped to [0, 1].
     * Note: gender preference deferred to P2 — Caregiver.gender field not in P1 schema.
     */
    private PreferenceResult computePreferencesScore(Caregiver caregiver, Client client) {
        double score = 1.0;
        boolean languageMismatch = false;
        boolean petConflict = false;
        List<String> clientLangs = parseLanguageList(client.getPreferredLanguages());
        if (!clientLangs.isEmpty()) {
            List<String> cgLangs = parseLanguageList(caregiver.getLanguages());
            if (cgLangs.stream().noneMatch(clientLangs::contains)) {
                score -= 0.5;
                languageMismatch = true;
            }
        }
        if (client.isNoPetCaregiver() && caregiver.hasPet()) {
            score -= 0.2;
            petConflict = true;
        }
        return new PreferenceResult(Math.max(0.0, score), languageMismatch, petConflict);
    }

    private double computeReliabilityScore(CaregiverScoringProfile profile) {
        if (profile == null) return 1.0; // no history — assume reliable
        return Math.max(0.0, 1.0 - profile.getCancelRate().doubleValue());
    }

    private String buildExplanation(double rawDistanceMiles, int visitCount,
                                     CaregiverScoringProfile profile,
                                     ShiftMatchRequest request,
                                     PreferenceResult prefs) {
        List<String> parts = new ArrayList<>();
        if (rawDistanceMiles >= 0) parts.add(String.format("%.1f miles away", rawDistanceMiles));
        if (visitCount > 0) {
            parts.add("worked with this client " + visitCount + (visitCount == 1 ? " time" : " times"));
        }
        double projected = projectedWeekHours(profile, request);
        parts.add(projected < OVERTIME_THRESHOLD_HOURS ? "no overtime risk" : "overtime risk");
        if (profile != null && profile.getCancelRate().doubleValue() > 0.0) {
            parts.add(String.format("%.0f%% cancel rate", profile.getCancelRate().doubleValue() * 100));
        }
        if (prefs.languageMismatch()) parts.add("language mismatch");
        if (prefs.petConflict()) parts.add("pet conflict");
        return String.join(" · ", parts);
    }

    // ── event listener helper ─────────────────────────────────────────────────

    private void updateAffinity(UUID scoringProfileId, UUID clientId, UUID agencyId) {
        // Ensure the row exists first (ON CONFLICT DO NOTHING is safe to call concurrently
        // without tainting the enclosing transaction).
        affinityRepository.insertIfNotExists(scoringProfileId, clientId, agencyId);
        // Atomic SQL increment — no read-modify-write race, no retry needed.
        affinityRepository.incrementVisitCount(scoringProfileId, clientId);
    }

    // ── parsing helpers ───────────────────────────────────────────────────────

    private List<CredentialType> parseCredentialTypes(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            List<String> names = objectMapper.readValue(json, new TypeReference<List<String>>() {});
            return names.stream()
                .flatMap(s -> {
                    try {
                        return java.util.stream.Stream.of(CredentialType.valueOf(s));
                    } catch (IllegalArgumentException e) {
                        log.warn("Unknown CredentialType '{}' in ServiceType.requiredCredentials — skipping", s);
                        return java.util.stream.Stream.empty();
                    }
                })
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to parse requiredCredentials JSON '{}' — treating as empty", json, e);
            return Collections.emptyList();
        }
    }

    private List<String> parseLanguageList(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            List<String> raw = objectMapper.readValue(json, new TypeReference<List<String>>() {});
            return raw.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to parse language list JSON '{}' — treating as empty", json);
            return Collections.emptyList();
        }
    }

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
