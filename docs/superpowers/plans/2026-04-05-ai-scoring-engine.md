# AI Scoring Engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the `com.hcare.scoring` module — weighted caregiver match ranking with hard filters, pre-computed scoring profiles updated via Spring events, and feature-flag-gated AI scoring degradation for Starter-tier agencies.

**Architecture:** `LocalScoringService` is the single `ScoringService` implementation. Hard filters (availability, conflicts, credentials, authorization units) eliminate ineligible caregivers. Weighted scoring (distance 30%, continuity 25%, OT risk 20%, preferences 15%, reliability 10%) ranks the rest with a plain-language explanation. `ShiftCompletedEvent` is published by `VisitService.clockOut` and consumed via `@TransactionalEventListener` to update `CaregiverScoringProfile` and `CaregiverClientAffinity`. A `@Scheduled` job resets `currentWeekHours` every Monday. `featureFlags.aiSchedulingEnabled = false` (Starter tier) skips scoring and returns unranked eligible caregivers with `score=0, explanation=null`.

**P1 scope note:** The spec lists "Caregiver not on OIG exclusion list" as a hard filter. The `Caregiver` domain entity does not have an `oigExcluded` field in P1 (OIG API integration is explicitly P2 per the spec). The filter is implemented as a TODO comment in `passesHardFilters` — it defaults to pass-through (all caregivers pass). Add `oig_excluded BOOLEAN NOT NULL DEFAULT FALSE` to caregivers at P2 when the OIG API integration is built.

**Tech Stack:** Java 25, Spring Boot 3.4.4, Spring Data JPA/Hibernate 6, Spring Events (`ApplicationEventPublisher` + `@TransactionalEventListener`), Jackson, Flyway, JUnit 5, Mockito, AssertJ, Testcontainers PostgreSQL 16.

---

## File Structure

```
backend/src/main/java/com/hcare/
├── domain/
│   ├── ShiftCompletedEvent.java              — new record
│   ├── ShiftCancelledEvent.java              — new record
│   └── CaregiverScoringProfile.java          — modify: add count fields + 3 new methods
│
├── scoring/
│   ├── ShiftMatchRequest.java                — new record
│   ├── RankedCaregiver.java                  — new record
│   ├── ScoringService.java                   — new interface
│   └── LocalScoringService.java              — new @Service (all scoring logic)
│
└── api/v1/visits/
    └── VisitService.java                     — modify: publish ShiftCompletedEvent on clock-out

backend/src/main/java/com/hcare/domain/
├── CaregiverScoringProfileRepository.java    — modify: add resetAllWeeklyHours query
├── CaregiverClientAffinityRepository.java    — modify: add findByScoringProfileId query
└── ShiftRepository.java                      — modify: add findOverlapping query

backend/src/main/resources/
├── application-test.yml                      — modify: add scoring weekly-reset-cron: "-"
└── db/migration/
    └── V8__scoring_profile_cancel_counts.sql — new

backend/src/test/java/com/hcare/
├── domain/
│   └── CaregiverScoringProfileTest.java      — new unit tests for profile methods
└── scoring/
    ├── LocalScoringServiceTest.java           — new unit tests (Mockito, no DB)
    └── LocalScoringServiceIT.java             — new integration tests (Testcontainers)
```

---

### Task 1: Domain events + scoring value objects + ScoringService interface

**Files:**
- Create: `backend/src/main/java/com/hcare/domain/ShiftCompletedEvent.java`
- Create: `backend/src/main/java/com/hcare/domain/ShiftCancelledEvent.java`
- Create: `backend/src/main/java/com/hcare/scoring/ShiftMatchRequest.java`
- Create: `backend/src/main/java/com/hcare/scoring/RankedCaregiver.java`
- Create: `backend/src/main/java/com/hcare/scoring/ScoringService.java`

- [ ] **Step 1: Create domain events**

`backend/src/main/java/com/hcare/domain/ShiftCompletedEvent.java`:
```java
package com.hcare.domain;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Published by VisitService.clockOut via ApplicationEventPublisher after the outer
 * transaction commits. LocalScoringService listens via @TransactionalEventListener to update
 * CaregiverScoringProfile and CaregiverClientAffinity asynchronously.
 *
 * timeIn / timeOut are the authoritative EVV timestamps — deviceCapturedAt for offline
 * visits, server receipt time for online visits (same values stored on EvvRecord).
 */
public record ShiftCompletedEvent(
    UUID shiftId,
    UUID caregiverId,
    UUID clientId,
    UUID agencyId,
    LocalDateTime timeIn,
    LocalDateTime timeOut
) {}
```

`backend/src/main/java/com/hcare/domain/ShiftCancelledEvent.java`:
```java
package com.hcare.domain;

import java.util.UUID;

/**
 * Published when a shift is cancelled (wired in the future Scheduling API, Plan 6).
 * LocalScoringService listens to increment the cancellation count on CaregiverScoringProfile,
 * keeping cancelRateLast90Days accurate.
 */
public record ShiftCancelledEvent(
    UUID shiftId,
    UUID caregiverId,
    UUID agencyId
) {}
```

- [ ] **Step 2: Create scoring value objects and ScoringService interface**

`backend/src/main/java/com/hcare/scoring/ShiftMatchRequest.java`:
```java
package com.hcare.scoring;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Input to ScoringService.rankCandidates. authorizationId is nullable — ad-hoc shifts
 * assigned without a payer authorization skip the authorization unit hard filter.
 */
public record ShiftMatchRequest(
    UUID agencyId,
    UUID clientId,
    UUID serviceTypeId,
    UUID authorizationId,
    LocalDateTime scheduledStart,
    LocalDateTime scheduledEnd
) {}
```

`backend/src/main/java/com/hcare/scoring/RankedCaregiver.java`:
```java
package com.hcare.scoring;

import java.util.UUID;

/**
 * A caregiver who passed all hard filters, with an optional weighted score and explanation.
 *
 * When featureFlags.aiSchedulingEnabled = false (Starter tier), score is 0.0 and explanation
 * is null — candidates are eligible but not ranked. The caller presents them in undefined order.
 */
public record RankedCaregiver(
    UUID caregiverId,
    double score,
    String explanation
) {}
```

`backend/src/main/java/com/hcare/scoring/ScoringService.java`:
```java
package com.hcare.scoring;

import java.util.List;

/**
 * Public surface of the scoring module. The only interface callers outside
 * com.hcare.scoring may use — nothing outside this package queries scoring tables directly.
 *
 * P2 microservice extraction path: swap LocalScoringService for HttpScoringServiceClient,
 * route events to a message broker. No caller changes required.
 */
public interface ScoringService {

    /**
     * Returns eligible caregivers sorted by score descending (AI enabled), or unsorted with
     * score=0 (AI disabled). Returns an empty list if no caregiver passes all hard filters.
     */
    List<RankedCaregiver> rankCandidates(ShiftMatchRequest request);
}
```

- [ ] **Step 3: Verify compilation**

Run: `cd backend && ./mvnw compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/hcare/domain/ShiftCompletedEvent.java \
        backend/src/main/java/com/hcare/domain/ShiftCancelledEvent.java \
        backend/src/main/java/com/hcare/scoring/ShiftMatchRequest.java \
        backend/src/main/java/com/hcare/scoring/RankedCaregiver.java \
        backend/src/main/java/com/hcare/scoring/ScoringService.java
git commit -m "feat: add scoring domain events and ScoringService interface"
```

---

### Task 2: V8 migration + CaregiverScoringProfile extensions

**Files:**
- Create: `backend/src/main/resources/db/migration/V8__scoring_profile_cancel_counts.sql`
- Modify: `backend/src/main/java/com/hcare/domain/CaregiverScoringProfile.java`
- Modify: `backend/src/main/java/com/hcare/domain/CaregiverScoringProfileRepository.java`
- Create: `backend/src/test/java/com/hcare/domain/CaregiverScoringProfileTest.java`

- [ ] **Step 1: Write the failing unit tests**

`backend/src/test/java/com/hcare/domain/CaregiverScoringProfileTest.java`:
```java
package com.hcare.domain;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class CaregiverScoringProfileTest {

    private final CaregiverScoringProfile profile =
        new CaregiverScoringProfile(UUID.randomUUID(), UUID.randomUUID());

    @Test
    void updateAfterShiftCompletion_adds_hours_and_increments_completed_count() {
        profile.updateAfterShiftCompletion(new BigDecimal("4.00"));
        assertThat(profile.getCurrentWeekHours()).isEqualByComparingTo("4.00");
        assertThat(profile.getCompletedShiftsLast90Days()).isEqualTo(1);
        assertThat(profile.getCancelRateLast90Days()).isEqualByComparingTo("0.0000");
    }

    @Test
    void updateAfterShiftCancellation_increments_cancelled_count_and_recalculates_rate() {
        profile.updateAfterShiftCompletion(new BigDecimal("4.00")); // 1 completed
        profile.updateAfterShiftCancellation();                      // 1 cancelled
        assertThat(profile.getCancelledShiftsLast90Days()).isEqualTo(1);
        // rate = 1 / (1 + 1) = 0.5000
        assertThat(profile.getCancelRateLast90Days()).isEqualByComparingTo("0.5000");
    }

    @Test
    void cancel_rate_is_zero_when_no_shifts_recorded() {
        assertThat(profile.getCancelRateLast90Days()).isEqualByComparingTo("0.0000");
    }

    @Test
    void cancel_rate_is_one_when_all_shifts_cancelled() {
        profile.updateAfterShiftCancellation();
        assertThat(profile.getCancelRateLast90Days()).isEqualByComparingTo("1.0000");
    }

    @Test
    void resetWeeklyHours_sets_current_week_hours_to_zero() {
        profile.updateAfterShiftCompletion(new BigDecimal("38.00"));
        profile.resetWeeklyHours();
        assertThat(profile.getCurrentWeekHours()).isEqualByComparingTo("0.00");
    }

    @Test
    void multiple_completions_accumulate_hours_and_count() {
        profile.updateAfterShiftCompletion(new BigDecimal("4.00"));
        profile.updateAfterShiftCompletion(new BigDecimal("6.00"));
        assertThat(profile.getCurrentWeekHours()).isEqualByComparingTo("10.00");
        assertThat(profile.getCompletedShiftsLast90Days()).isEqualTo(2);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -pl . -Dtest=CaregiverScoringProfileTest -q`
Expected: FAIL — `getCompletedShiftsLast90Days()`, `getCancelledShiftsLast90Days()`, `updateAfterShiftCancellation()`, `resetWeeklyHours()` do not exist yet.

- [ ] **Step 3: Create V8 migration**

`backend/src/main/resources/db/migration/V8__scoring_profile_cancel_counts.sql`:
```sql
-- V8: Add cancellation and completion counters to caregiver_scoring_profiles.
-- These power the cancelRateLast90Days calculation driven by ShiftCompletedEvent /
-- ShiftCancelledEvent listeners in LocalScoringService (Plan 5).
-- Running counts since first recorded shift — exact 90-day rolling window is P2.
ALTER TABLE caregiver_scoring_profiles
    ADD COLUMN completed_shifts_last_90_days INT NOT NULL DEFAULT 0,
    ADD COLUMN cancelled_shifts_last_90_days INT NOT NULL DEFAULT 0;
-- Note: caregiver_client_affinities already has @Version (version BIGINT, V4 migration)
-- and UNIQUE (scoring_profile_id, client_id) (V3 migration). No changes needed.
```

- [ ] **Step 4: Update CaregiverScoringProfile entity**

Replace the full content of `backend/src/main/java/com/hcare/domain/CaregiverScoringProfile.java`:
```java
package com.hcare.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "caregiver_scoring_profiles")
@Filter(name = "agencyFilter", condition = "agency_id = :agencyId")
public class CaregiverScoringProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "caregiver_id", nullable = false, unique = true)
    private UUID caregiverId;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    @Column(name = "cancel_rate_last_90_days", nullable = false, precision = 5, scale = 4)
    private BigDecimal cancelRateLast90Days = BigDecimal.ZERO;

    @Column(name = "current_week_hours", nullable = false, precision = 5, scale = 2)
    private BigDecimal currentWeekHours = BigDecimal.ZERO;

    @Column(name = "completed_shifts_last_90_days", nullable = false)
    private int completedShiftsLast90Days = 0;

    @Column(name = "cancelled_shifts_last_90_days", nullable = false)
    private int cancelledShiftsLast90Days = 0;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now(ZoneOffset.UTC);

    protected CaregiverScoringProfile() {}

    public CaregiverScoringProfile(UUID caregiverId, UUID agencyId) {
        this.caregiverId = caregiverId;
        this.agencyId = agencyId;
    }

    /** Called by onShiftCompleted listener — adds shift hours and increments completion count. */
    public void updateAfterShiftCompletion(BigDecimal hoursWorked) {
        this.currentWeekHours = this.currentWeekHours.add(hoursWorked);
        this.completedShiftsLast90Days++;
        recalculateCancelRate();
        this.updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    /** Called by onShiftCancelled listener — increments cancel count and recalculates rate. */
    public void updateAfterShiftCancellation() {
        this.cancelledShiftsLast90Days++;
        recalculateCancelRate();
        this.updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    /** Called by the weekly @Scheduled job each Monday to reset OT-risk scoring data. */
    public void resetWeeklyHours() {
        this.currentWeekHours = BigDecimal.ZERO;
        this.updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    private void recalculateCancelRate() {
        int total = completedShiftsLast90Days + cancelledShiftsLast90Days;
        this.cancelRateLast90Days = total == 0 ? BigDecimal.ZERO
            : BigDecimal.valueOf(cancelledShiftsLast90Days)
                .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
    }

    public UUID getId() { return id; }
    public UUID getCaregiverId() { return caregiverId; }
    public UUID getAgencyId() { return agencyId; }
    public BigDecimal getCancelRateLast90Days() { return cancelRateLast90Days; }
    public BigDecimal getCurrentWeekHours() { return currentWeekHours; }
    public int getCompletedShiftsLast90Days() { return completedShiftsLast90Days; }
    public int getCancelledShiftsLast90Days() { return cancelledShiftsLast90Days; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
```

- [ ] **Step 5: Update CaregiverScoringProfileRepository**

Replace the full content of `backend/src/main/java/com/hcare/domain/CaregiverScoringProfileRepository.java`:
```java
package com.hcare.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

public interface CaregiverScoringProfileRepository extends JpaRepository<CaregiverScoringProfile, UUID> {

    Optional<CaregiverScoringProfile> findByCaregiverId(UUID caregiverId);

    /**
     * Bulk-resets currentWeekHours to 0 for ALL profiles across ALL agencies.
     * Intentionally bypasses the Hibernate agencyFilter — this is a global weekly
     * maintenance operation that must touch every agency's data regardless of tenant.
     */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE CaregiverScoringProfile p SET p.currentWeekHours = 0.00")
    void resetAllWeeklyHours();
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `cd backend && ./mvnw test -pl . -Dtest=CaregiverScoringProfileTest -q`
Expected: 6 tests PASS

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/resources/db/migration/V8__scoring_profile_cancel_counts.sql \
        backend/src/main/java/com/hcare/domain/CaregiverScoringProfile.java \
        backend/src/main/java/com/hcare/domain/CaregiverScoringProfileRepository.java \
        backend/src/test/java/com/hcare/domain/CaregiverScoringProfileTest.java
git commit -m "feat: add cancel/completion counters to CaregiverScoringProfile (V8 migration)"
```

---

### Task 3: LocalScoringService — full implementation

**Files:**
- Modify: `backend/src/main/java/com/hcare/domain/ShiftRepository.java`
- Create: `backend/src/test/java/com/hcare/scoring/LocalScoringServiceTest.java` (hard filter tests)
- Create: `backend/src/main/java/com/hcare/scoring/LocalScoringService.java`

- [ ] **Step 0: Verify pre-existing dependencies**

`LocalScoringService` depends on classes that are not created by this plan. Verify they exist before writing any code — a missing symbol surfaces as a confusing error on the new file rather than on the missing dependency.

```bash
# FeatureFlags API
cd backend && grep -r "findByAgencyId" src/main/java/com/hcare/domain/FeatureFlagsRepository.java
cd backend && grep -r "isAiSchedulingEnabled\|setAiSchedulingEnabled" src/main/java/com/hcare/domain/FeatureFlags.java

# CaregiverAvailability 5-arg constructor (UUID, UUID, DayOfWeek, LocalTime, LocalTime)
cd backend && grep -A5 "public CaregiverAvailability" src/main/java/com/hcare/domain/CaregiverAvailability.java
```

Expected: all three greps return matches. If `FeatureFlagsRepository.findByAgencyId`, `FeatureFlags.isAiSchedulingEnabled`/`setAiSchedulingEnabled`, or the `CaregiverAvailability(UUID, UUID, DayOfWeek, LocalTime, LocalTime)` constructor are absent, add them before proceeding. Do not continue to Step 1 until `mvn compile -q` succeeds against the existing codebase.

- [ ] **Step 1: Write the failing hard-filter unit tests**

`backend/src/test/java/com/hcare/scoring/LocalScoringServiceTest.java`:
```java
package com.hcare.scoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hcare.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.*;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// LENIENT: many tests stub a full "success path" in @BeforeEach but only exercise one path.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LocalScoringServiceTest {

    @Mock CaregiverRepository caregiverRepository;
    @Mock CaregiverAvailabilityRepository availabilityRepository;
    @Mock CaregiverCredentialRepository credentialRepository;
    @Mock ShiftRepository shiftRepository;
    @Mock CaregiverScoringProfileRepository scoringProfileRepository;
    @Mock CaregiverClientAffinityRepository affinityRepository;
    @Mock AuthorizationRepository authorizationRepository;
    @Mock FeatureFlagsRepository featureFlagsRepository;
    @Mock ClientRepository clientRepository;
    @Mock ServiceTypeRepository serviceTypeRepository;

    LocalScoringService service;

    static final UUID AGENCY_ID       = UUID.randomUUID();
    static final UUID CLIENT_ID       = UUID.randomUUID();
    static final UUID CAREGIVER_ID    = UUID.randomUUID();
    static final UUID SERVICE_TYPE_ID = UUID.randomUUID();

    // Monday 2026-04-20, 09:00–13:00 (4 hours)
    static final LocalDateTime SHIFT_START = LocalDateTime.of(2026, 4, 20, 9, 0);
    static final LocalDateTime SHIFT_END   = LocalDateTime.of(2026, 4, 20, 13, 0);

    // Austin, TX — client location
    static final BigDecimal CLIENT_LAT = new BigDecimal("30.2672");
    static final BigDecimal CLIENT_LNG = new BigDecimal("-97.7431");
    // ~0.3 miles north of client
    static final BigDecimal NEAR_LAT   = new BigDecimal("30.2700");
    static final BigDecimal NEAR_LNG   = new BigDecimal("-97.7400");

    @BeforeEach
    void setup() {
        service = new LocalScoringService(
            caregiverRepository, availabilityRepository, credentialRepository,
            shiftRepository, scoringProfileRepository, affinityRepository,
            authorizationRepository, featureFlagsRepository, clientRepository,
            serviceTypeRepository, new ObjectMapper()
        );
    }

    // ── fixture builders ──────────────────────────────────────────────────────

    private Caregiver buildActiveCaregiver() {
        Caregiver c = mock(Caregiver.class);
        when(c.getId()).thenReturn(CAREGIVER_ID);
        when(c.getStatus()).thenReturn(CaregiverStatus.ACTIVE);
        when(c.getHomeLat()).thenReturn(NEAR_LAT);
        when(c.getHomeLng()).thenReturn(NEAR_LNG);
        when(c.getLanguages()).thenReturn("[]");
        when(c.hasPet()).thenReturn(false);
        return c;
    }

    private Client buildClient() {
        Client cl = mock(Client.class);
        when(cl.getLat()).thenReturn(CLIENT_LAT);
        when(cl.getLng()).thenReturn(CLIENT_LNG);
        when(cl.getPreferredLanguages()).thenReturn("[]");
        when(cl.isNoPetCaregiver()).thenReturn(false);
        return cl;
    }

    private ServiceType buildServiceTypeNoCredentials() {
        ServiceType st = mock(ServiceType.class);
        when(st.getRequiredCredentials()).thenReturn("[]");
        return st;
    }

    private ShiftMatchRequest buildRequest() {
        return new ShiftMatchRequest(AGENCY_ID, CLIENT_ID, SERVICE_TYPE_ID, null,
            SHIFT_START, SHIFT_END);
    }

    /** Wires up all mocks so a single caregiver passes all hard filters (AI disabled). */
    private void setupSuccessPathMocks(Caregiver caregiver, Client client, ServiceType st) {
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(client));
        when(serviceTypeRepository.findById(SERVICE_TYPE_ID)).thenReturn(Optional.of(st));
        when(featureFlagsRepository.findByAgencyId(AGENCY_ID)).thenReturn(Optional.empty()); // AI disabled
        when(caregiverRepository.findByAgencyId(AGENCY_ID)).thenReturn(List.of(caregiver));

        CaregiverAvailability avail = new CaregiverAvailability(
            CAREGIVER_ID, AGENCY_ID, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(17, 0));
        when(availabilityRepository.findByCaregiverId(CAREGIVER_ID)).thenReturn(List.of(avail));
        when(shiftRepository.findOverlapping(
            eq(CAREGIVER_ID), any(), any())).thenReturn(Collections.emptyList());
        when(credentialRepository.findByCaregiverId(CAREGIVER_ID)).thenReturn(Collections.emptyList());
        when(scoringProfileRepository.findByCaregiverId(CAREGIVER_ID)).thenReturn(Optional.empty());
    }

    // ── hard filter tests ─────────────────────────────────────────────────────

    @Test
    void inactive_caregiver_is_excluded() {
        Caregiver inactive = mock(Caregiver.class);
        when(inactive.getStatus()).thenReturn(CaregiverStatus.INACTIVE);
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(buildClient()));
        when(serviceTypeRepository.findById(SERVICE_TYPE_ID)).thenReturn(Optional.of(buildServiceTypeNoCredentials()));
        when(featureFlagsRepository.findByAgencyId(AGENCY_ID)).thenReturn(Optional.empty());
        when(caregiverRepository.findByAgencyId(AGENCY_ID)).thenReturn(List.of(inactive));

        assertThat(service.rankCandidates(buildRequest())).isEmpty();
    }

    @Test
    void caregiver_with_no_availability_is_excluded() {
        Caregiver caregiver = buildActiveCaregiver();
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(buildClient()));
        when(serviceTypeRepository.findById(SERVICE_TYPE_ID)).thenReturn(Optional.of(buildServiceTypeNoCredentials()));
        when(featureFlagsRepository.findByAgencyId(AGENCY_ID)).thenReturn(Optional.empty());
        when(caregiverRepository.findByAgencyId(AGENCY_ID)).thenReturn(List.of(caregiver));
        when(availabilityRepository.findByCaregiverId(CAREGIVER_ID)).thenReturn(Collections.emptyList());

        assertThat(service.rankCandidates(buildRequest())).isEmpty();
    }

    @Test
    void caregiver_with_availability_on_wrong_day_is_excluded() {
        Caregiver caregiver = buildActiveCaregiver();
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(buildClient()));
        when(serviceTypeRepository.findById(SERVICE_TYPE_ID)).thenReturn(Optional.of(buildServiceTypeNoCredentials()));
        when(featureFlagsRepository.findByAgencyId(AGENCY_ID)).thenReturn(Optional.empty());
        when(caregiverRepository.findByAgencyId(AGENCY_ID)).thenReturn(List.of(caregiver));
        // Tuesday availability only — shift is Monday
        CaregiverAvailability tuesdayAvail = new CaregiverAvailability(
            CAREGIVER_ID, AGENCY_ID, DayOfWeek.TUESDAY, LocalTime.of(8, 0), LocalTime.of(17, 0));
        when(availabilityRepository.findByCaregiverId(CAREGIVER_ID)).thenReturn(List.of(tuesdayAvail));

        assertThat(service.rankCandidates(buildRequest())).isEmpty();
    }

    @Test
    void caregiver_with_overlapping_shift_is_excluded() {
        Caregiver caregiver = buildActiveCaregiver();
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(buildClient()));
        when(serviceTypeRepository.findById(SERVICE_TYPE_ID)).thenReturn(Optional.of(buildServiceTypeNoCredentials()));
        when(featureFlagsRepository.findByAgencyId(AGENCY_ID)).thenReturn(Optional.empty());
        when(caregiverRepository.findByAgencyId(AGENCY_ID)).thenReturn(List.of(caregiver));
        when(availabilityRepository.findByCaregiverId(CAREGIVER_ID)).thenReturn(List.of(
            new CaregiverAvailability(CAREGIVER_ID, AGENCY_ID, DayOfWeek.MONDAY,
                LocalTime.of(8, 0), LocalTime.of(17, 0))));

        // Overlapping ASSIGNED shift 10:00–14:00 (overlaps our 09:00–13:00)
        Shift conflict = mock(Shift.class);
        when(conflict.getStatus()).thenReturn(ShiftStatus.ASSIGNED);
        when(conflict.getScheduledStart()).thenReturn(LocalDateTime.of(2026, 4, 20, 10, 0));
        when(conflict.getScheduledEnd()).thenReturn(LocalDateTime.of(2026, 4, 20, 14, 0));
        when(shiftRepository.findOverlapping(
            eq(CAREGIVER_ID), any(), any())).thenReturn(List.of(conflict));

        assertThat(service.rankCandidates(buildRequest())).isEmpty();
    }

    @Test
    void cancelled_shift_does_not_block_candidate() {
        Caregiver caregiver = buildActiveCaregiver();
        setupSuccessPathMocks(caregiver, buildClient(), buildServiceTypeNoCredentials());

        Shift cancelled = mock(Shift.class);
        when(cancelled.getStatus()).thenReturn(ShiftStatus.CANCELLED);
        when(cancelled.getScheduledStart()).thenReturn(LocalDateTime.of(2026, 4, 20, 10, 0));
        when(cancelled.getScheduledEnd()).thenReturn(LocalDateTime.of(2026, 4, 20, 14, 0));
        when(shiftRepository.findOverlapping(
            eq(CAREGIVER_ID), any(), any())).thenReturn(List.of(cancelled));

        assertThat(service.rankCandidates(buildRequest())).hasSize(1);
    }

    @Test
    void expired_required_credential_is_excluded() {
        Caregiver caregiver = buildActiveCaregiver();
        ServiceType requiresHha = mock(ServiceType.class);
        when(requiresHha.getRequiredCredentials()).thenReturn("[\"HHA\"]");
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(buildClient()));
        when(serviceTypeRepository.findById(SERVICE_TYPE_ID)).thenReturn(Optional.of(requiresHha));
        when(featureFlagsRepository.findByAgencyId(AGENCY_ID)).thenReturn(Optional.empty());
        when(caregiverRepository.findByAgencyId(AGENCY_ID)).thenReturn(List.of(caregiver));
        when(availabilityRepository.findByCaregiverId(CAREGIVER_ID)).thenReturn(List.of(
            new CaregiverAvailability(CAREGIVER_ID, AGENCY_ID, DayOfWeek.MONDAY,
                LocalTime.of(8, 0), LocalTime.of(17, 0))));
        when(shiftRepository.findOverlapping(
            eq(CAREGIVER_ID), any(), any())).thenReturn(Collections.emptyList());

        CaregiverCredential expired = new CaregiverCredential(
            CAREGIVER_ID, AGENCY_ID, CredentialType.HHA,
            LocalDate.of(2024, 1, 1), LocalDate.now().minusDays(1)); // expired yesterday
        expired.verify(UUID.randomUUID());
        when(credentialRepository.findByCaregiverId(CAREGIVER_ID)).thenReturn(List.of(expired));

        assertThat(service.rankCandidates(buildRequest())).isEmpty();
    }

    @Test
    void unverified_required_credential_is_excluded() {
        Caregiver caregiver = buildActiveCaregiver();
        ServiceType requiresCna = mock(ServiceType.class);
        when(requiresCna.getRequiredCredentials()).thenReturn("[\"CNA\"]");
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(buildClient()));
        when(serviceTypeRepository.findById(SERVICE_TYPE_ID)).thenReturn(Optional.of(requiresCna));
        when(featureFlagsRepository.findByAgencyId(AGENCY_ID)).thenReturn(Optional.empty());
        when(caregiverRepository.findByAgencyId(AGENCY_ID)).thenReturn(List.of(caregiver));
        when(availabilityRepository.findByCaregiverId(CAREGIVER_ID)).thenReturn(List.of(
            new CaregiverAvailability(CAREGIVER_ID, AGENCY_ID, DayOfWeek.MONDAY,
                LocalTime.of(8, 0), LocalTime.of(17, 0))));
        when(shiftRepository.findOverlapping(
            eq(CAREGIVER_ID), any(), any())).thenReturn(Collections.emptyList());

        // Valid expiry but NOT verified (verify() never called)
        CaregiverCredential unverified = new CaregiverCredential(
            CAREGIVER_ID, AGENCY_ID, CredentialType.CNA,
            LocalDate.of(2025, 1, 1), LocalDate.now().plusYears(1));
        when(credentialRepository.findByCaregiverId(CAREGIVER_ID)).thenReturn(List.of(unverified));

        assertThat(service.rankCandidates(buildRequest())).isEmpty();
    }

    @Test
    void exhausted_authorization_excludes_all_candidates() {
        Caregiver caregiver = buildActiveCaregiver();
        UUID AUTH_ID = UUID.randomUUID();
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(buildClient()));
        when(serviceTypeRepository.findById(SERVICE_TYPE_ID)).thenReturn(Optional.of(buildServiceTypeNoCredentials()));
        when(featureFlagsRepository.findByAgencyId(AGENCY_ID)).thenReturn(Optional.empty());
        when(caregiverRepository.findByAgencyId(AGENCY_ID)).thenReturn(List.of(caregiver));
        when(availabilityRepository.findByCaregiverId(CAREGIVER_ID)).thenReturn(List.of(
            new CaregiverAvailability(CAREGIVER_ID, AGENCY_ID, DayOfWeek.MONDAY,
                LocalTime.of(8, 0), LocalTime.of(17, 0))));
        when(shiftRepository.findOverlapping(
            eq(CAREGIVER_ID), any(), any())).thenReturn(Collections.emptyList());
        when(credentialRepository.findByCaregiverId(CAREGIVER_ID)).thenReturn(Collections.emptyList());

        Authorization exhausted = mock(Authorization.class);
        when(exhausted.getUsedUnits()).thenReturn(new BigDecimal("40.00"));
        when(exhausted.getAuthorizedUnits()).thenReturn(new BigDecimal("40.00")); // used == authorized
        when(authorizationRepository.findById(AUTH_ID)).thenReturn(Optional.of(exhausted));

        ShiftMatchRequest req = new ShiftMatchRequest(
            AGENCY_ID, CLIENT_ID, SERVICE_TYPE_ID, AUTH_ID, SHIFT_START, SHIFT_END);
        assertThat(service.rankCandidates(req)).isEmpty();
    }

    @Test
    void unknown_authorization_id_throws_illegal_argument() {
        UUID unknownAuthId = UUID.randomUUID();
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(buildClient()));
        when(serviceTypeRepository.findById(SERVICE_TYPE_ID)).thenReturn(Optional.of(buildServiceTypeNoCredentials()));
        when(featureFlagsRepository.findByAgencyId(AGENCY_ID)).thenReturn(Optional.empty());
        when(caregiverRepository.findByAgencyId(AGENCY_ID)).thenReturn(Collections.emptyList());
        when(authorizationRepository.findById(unknownAuthId)).thenReturn(Optional.empty());

        ShiftMatchRequest req = new ShiftMatchRequest(
            AGENCY_ID, CLIENT_ID, SERVICE_TYPE_ID, unknownAuthId, SHIFT_START, SHIFT_END);
        assertThatThrownBy(() -> service.rankCandidates(req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(unknownAuthId.toString());
    }

    @Test
    void all_hard_filters_pass_returns_candidate_with_no_score_when_ai_disabled() {
        Caregiver caregiver = buildActiveCaregiver();
        setupSuccessPathMocks(caregiver, buildClient(), buildServiceTypeNoCredentials());

        List<RankedCaregiver> results = service.rankCandidates(buildRequest());

        assertThat(results).hasSize(1);
        assertThat(results.get(0).caregiverId()).isEqualTo(CAREGIVER_ID);
        assertThat(results.get(0).score()).isEqualTo(0.0);
        assertThat(results.get(0).explanation()).isNull();
    }
}
```

- [ ] **Step 1b: Add `findOverlapping` query to `ShiftRepository`**

In `backend/src/main/java/com/hcare/domain/ShiftRepository.java`, add this method:
```java
    /**
     * Returns all shifts for a caregiver whose scheduled window overlaps [start, end).
     * Uses a proper interval overlap predicate instead of a time-window heuristic.
     */
    @Query("""
        SELECT s FROM Shift s
        WHERE s.caregiverId = :caregiverId
          AND s.scheduledStart < :end
          AND s.scheduledEnd > :start
        """)
    List<Shift> findOverlapping(@Param("caregiverId") UUID caregiverId,
                                @Param("start") LocalDateTime start,
                                @Param("end") LocalDateTime end);
```

Required imports (add if not already present):
```java
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && ./mvnw test -pl . -Dtest=LocalScoringServiceTest -q`
Expected: FAIL — `LocalScoringService` does not exist yet.

- [ ] **Step 3: Create LocalScoringService**

Create `backend/src/main/java/com/hcare/scoring/LocalScoringService.java`:
```java
package com.hcare.scoring;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hcare.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
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

        List<RankedCaregiver> results = new ArrayList<>();
        for (Caregiver caregiver : activeCaregivers) {
            if (!passesHardFilters(caregiver, request, authorization, requiredCredentials, today)) {
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
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onShiftCompleted(ShiftCompletedEvent event) {
        if (event.caregiverId() == null) return;

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
    }

    // TODO(Plan 6): ShiftCancelledEvent is not yet published anywhere. This listener is
    // wired and tested but inert in production until the Scheduling API (Plan 6) adds
    // eventPublisher.publishEvent(new ShiftCancelledEvent(...)) on shift cancellation.
    // Until then, cancelRateLast90Days remains 0 for all caregivers.
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onShiftCancelled(ShiftCancelledEvent event) {
        if (event.caregiverId() == null) return;

        CaregiverScoringProfile profile = scoringProfileRepository
            .findByCaregiverId(event.caregiverId())
            .orElseGet(() -> scoringProfileRepository.save(
                new CaregiverScoringProfile(event.caregiverId(), event.agencyId())));
        profile.updateAfterShiftCancellation();
        scoringProfileRepository.save(profile);
    }

    /**
     * Resets currentWeekHours to 0 for all caregiver scoring profiles.
     * Runs every Monday at midnight. Controlled by hcare.scoring.weekly-reset-cron
     * (set to "-" in application-test.yml to disable during integration tests).
     */
    @Scheduled(cron = "${hcare.scoring.weekly-reset-cron:0 0 0 * * MON}")
    @Transactional
    public void resetWeeklyHours() {
        log.info("Resetting currentWeekHours for all caregiver scoring profiles");
        scoringProfileRepository.resetAllWeeklyHours();
    }

    // ── hard filters ──────────────────────────────────────────────────────────

    private boolean passesHardFilters(Caregiver caregiver, ShiftMatchRequest request,
                                       Authorization authorization,
                                       List<CredentialType> requiredCredentials,
                                       LocalDate today) {
        // TODO(P2): Add caregiver.isOigExcluded() check once OIG API integration lands
        //           and the oig_excluded column is added to the caregivers table.
        return hasAvailability(caregiver.getId(), request.scheduledStart(), request.scheduledEnd())
            && !hasConflictingShift(caregiver.getId(), request.scheduledStart(), request.scheduledEnd())
            && hasRequiredCredentials(caregiver.getId(), requiredCredentials, today)
            && (authorization == null
                || authorization.getUsedUnits().compareTo(authorization.getAuthorizedUnits()) < 0);
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

    /** 1.0 if projected week hours remain under threshold; 0.0 if OT risk. */
    private double computeOvertimeScore(CaregiverScoringProfile profile, ShiftMatchRequest request) {
        double current = profile != null ? profile.getCurrentWeekHours().doubleValue() : 0.0;
        double shift   = Duration.between(request.scheduledStart(), request.scheduledEnd()).toMinutes() / 60.0;
        return (current + shift) < OVERTIME_THRESHOLD_HOURS ? 1.0 : 0.0;
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
        return Math.max(0.0, 1.0 - profile.getCancelRateLast90Days().doubleValue());
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
        double current = profile != null ? profile.getCurrentWeekHours().doubleValue() : 0.0;
        double shift   = Duration.between(request.scheduledStart(), request.scheduledEnd()).toMinutes() / 60.0;
        parts.add((current + shift) < OVERTIME_THRESHOLD_HOURS ? "no overtime risk" : "overtime risk");
        if (profile != null && profile.getCancelRateLast90Days().doubleValue() > 0.0) {
            parts.add(String.format("%.0f%% cancel rate", profile.getCancelRateLast90Days().doubleValue() * 100));
        }
        if (prefs.languageMismatch()) parts.add("language mismatch");
        if (prefs.petConflict()) parts.add("pet conflict");
        return String.join(" · ", parts);
    }

    // ── event listener helper ─────────────────────────────────────────────────

    private void updateAffinity(UUID scoringProfileId, UUID clientId, UUID agencyId) {
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                CaregiverClientAffinity affinity = affinityRepository
                    .findByScoringProfileIdAndClientId(scoringProfileId, clientId)
                    .orElseGet(() -> affinityRepository.save(
                        new CaregiverClientAffinity(scoringProfileId, clientId, agencyId)));
                affinity.incrementVisitCount();
                affinityRepository.save(affinity);
                return;
            } catch (OptimisticLockingFailureException | DataIntegrityViolationException e) {
                // OptimisticLockingFailureException: concurrent incrementVisitCount + save race.
                // DataIntegrityViolationException: two concurrent listeners both tried to insert
                // the same (scoring_profile_id, client_id) row; unique constraint fired on the
                // loser. Re-querying on the next attempt will find the now-existing row.
                if (attempt == 2) {
                    log.error("Exhausted retries updating CaregiverClientAffinity for " +
                        "scoringProfile={} client={}", scoringProfileId, clientId, e);
                }
            }
        }
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
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
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
```

- [ ] **Step 4: Run hard-filter unit tests to verify they pass**

Run: `cd backend && ./mvnw test -pl . -Dtest=LocalScoringServiceTest -q`
Expected: 10 tests PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/hcare/domain/ShiftRepository.java \
        backend/src/main/java/com/hcare/scoring/LocalScoringService.java \
        backend/src/test/java/com/hcare/scoring/LocalScoringServiceTest.java
git commit -m "feat: LocalScoringService — hard filters, scoring, event listeners; add findOverlapping to ShiftRepository"
```

---

### Task 4: Scoring + explanation + event listener unit tests

The scoring and listener logic was already implemented in Task 3. This task adds the tests that verify it.

**Files:**
- Modify: `backend/src/test/java/com/hcare/scoring/LocalScoringServiceTest.java`

- [ ] **Step 1: Append scoring and event listener tests to LocalScoringServiceTest**

Add the following methods before the closing `}` of `LocalScoringServiceTest`:

```java
    // ── scoring tests ─────────────────────────────────────────────────────────

    @Test
    void ai_enabled_returns_nonzero_score_and_explanation() {
        Caregiver caregiver = buildActiveCaregiver();
        FeatureFlags flags = mock(FeatureFlags.class);
        when(flags.isAiSchedulingEnabled()).thenReturn(true);

        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(buildClient()));
        when(serviceTypeRepository.findById(SERVICE_TYPE_ID)).thenReturn(Optional.of(buildServiceTypeNoCredentials()));
        when(featureFlagsRepository.findByAgencyId(AGENCY_ID)).thenReturn(Optional.of(flags));
        when(caregiverRepository.findByAgencyId(AGENCY_ID)).thenReturn(List.of(caregiver));
        when(availabilityRepository.findByCaregiverId(CAREGIVER_ID)).thenReturn(List.of(
            new CaregiverAvailability(CAREGIVER_ID, AGENCY_ID, DayOfWeek.MONDAY,
                LocalTime.of(8, 0), LocalTime.of(17, 0))));
        when(shiftRepository.findOverlapping(
            eq(CAREGIVER_ID), any(), any())).thenReturn(Collections.emptyList());
        when(credentialRepository.findByCaregiverId(CAREGIVER_ID)).thenReturn(Collections.emptyList());
        when(scoringProfileRepository.findByCaregiverId(CAREGIVER_ID)).thenReturn(Optional.empty());

        RankedCaregiver result = service.rankCandidates(buildRequest()).get(0);

        assertThat(result.score()).isGreaterThan(0.0);
        assertThat(result.explanation()).contains("miles away").contains("no overtime risk");
    }

    @Test
    void overtime_risk_lowers_score_vs_no_overtime() {
        FeatureFlags flags = mock(FeatureFlags.class);
        when(flags.isAiSchedulingEnabled()).thenReturn(true);
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(buildClient()));
        when(serviceTypeRepository.findById(SERVICE_TYPE_ID)).thenReturn(Optional.of(buildServiceTypeNoCredentials()));
        when(featureFlagsRepository.findByAgencyId(AGENCY_ID)).thenReturn(Optional.of(flags));
        when(credentialRepository.findByCaregiverId(any())).thenReturn(Collections.emptyList());
        when(shiftRepository.findOverlapping(any(), any(), any()))
            .thenReturn(Collections.emptyList());

        // Caregiver A: 38h this week + 4h shift = 42h (OT risk)
        UUID CG_OT = UUID.randomUUID();
        Caregiver cgOt = mock(Caregiver.class);
        when(cgOt.getId()).thenReturn(CG_OT);
        when(cgOt.getStatus()).thenReturn(CaregiverStatus.ACTIVE);
        when(cgOt.getHomeLat()).thenReturn(NEAR_LAT);
        when(cgOt.getHomeLng()).thenReturn(NEAR_LNG);
        when(cgOt.getLanguages()).thenReturn("[]");
        when(cgOt.hasPet()).thenReturn(false);
        when(availabilityRepository.findByCaregiverId(CG_OT)).thenReturn(List.of(
            new CaregiverAvailability(CG_OT, AGENCY_ID, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(17, 0))));
        CaregiverScoringProfile otProfile = new CaregiverScoringProfile(CG_OT, AGENCY_ID);
        otProfile.updateAfterShiftCompletion(new BigDecimal("38.00"));
        when(scoringProfileRepository.findByCaregiverId(CG_OT)).thenReturn(Optional.of(otProfile));
        when(affinityRepository.findByScoringProfileIdAndClientId(any(), any())).thenReturn(Optional.empty());

        // Caregiver B: 0h this week (no OT risk)
        UUID CG_NO_OT = UUID.randomUUID();
        Caregiver cgNoOt = mock(Caregiver.class);
        when(cgNoOt.getId()).thenReturn(CG_NO_OT);
        when(cgNoOt.getStatus()).thenReturn(CaregiverStatus.ACTIVE);
        when(cgNoOt.getHomeLat()).thenReturn(NEAR_LAT);
        when(cgNoOt.getHomeLng()).thenReturn(NEAR_LNG);
        when(cgNoOt.getLanguages()).thenReturn("[]");
        when(cgNoOt.hasPet()).thenReturn(false);
        when(availabilityRepository.findByCaregiverId(CG_NO_OT)).thenReturn(List.of(
            new CaregiverAvailability(CG_NO_OT, AGENCY_ID, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(17, 0))));
        when(scoringProfileRepository.findByCaregiverId(CG_NO_OT)).thenReturn(Optional.empty());

        when(caregiverRepository.findByAgencyId(AGENCY_ID)).thenReturn(List.of(cgOt, cgNoOt));

        List<RankedCaregiver> results = service.rankCandidates(buildRequest());
        assertThat(results).hasSize(2);
        // No-OT caregiver ranks first (higher score)
        assertThat(results.get(0).caregiverId()).isEqualTo(CG_NO_OT);
        assertThat(results.get(0).explanation()).contains("no overtime risk");
        assertThat(results.get(1).explanation()).contains("overtime risk");
    }

    @Test
    void language_mismatch_reduces_score_vs_no_preference() {
        FeatureFlags flags = mock(FeatureFlags.class);
        when(flags.isAiSchedulingEnabled()).thenReturn(true);
        when(serviceTypeRepository.findById(SERVICE_TYPE_ID)).thenReturn(Optional.of(buildServiceTypeNoCredentials()));
        when(featureFlagsRepository.findByAgencyId(AGENCY_ID)).thenReturn(Optional.of(flags));

        Caregiver englishOnly = mock(Caregiver.class);
        when(englishOnly.getId()).thenReturn(CAREGIVER_ID);
        when(englishOnly.getStatus()).thenReturn(CaregiverStatus.ACTIVE);
        when(englishOnly.getHomeLat()).thenReturn(NEAR_LAT);
        when(englishOnly.getHomeLng()).thenReturn(NEAR_LNG);
        when(englishOnly.getLanguages()).thenReturn("[\"en\"]");
        when(englishOnly.hasPet()).thenReturn(false);
        when(caregiverRepository.findByAgencyId(AGENCY_ID)).thenReturn(List.of(englishOnly));
        when(availabilityRepository.findByCaregiverId(CAREGIVER_ID)).thenReturn(List.of(
            new CaregiverAvailability(CAREGIVER_ID, AGENCY_ID, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(17, 0))));
        when(shiftRepository.findOverlapping(eq(CAREGIVER_ID), any(), any()))
            .thenReturn(Collections.emptyList());
        when(credentialRepository.findByCaregiverId(CAREGIVER_ID)).thenReturn(Collections.emptyList());
        when(scoringProfileRepository.findByCaregiverId(CAREGIVER_ID)).thenReturn(Optional.empty());

        // Client wants Spanish
        Client spanishClient = mock(Client.class);
        when(spanishClient.getLat()).thenReturn(CLIENT_LAT);
        when(spanishClient.getLng()).thenReturn(CLIENT_LNG);
        when(spanishClient.getPreferredLanguages()).thenReturn("[\"es\"]");
        when(spanishClient.isNoPetCaregiver()).thenReturn(false);
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(spanishClient));
        double mismatchScore = service.rankCandidates(buildRequest()).get(0).score();

        // Client has no language preference
        Client noPreferenceClient = buildClient();
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(noPreferenceClient));
        double noPreferenceScore = service.rankCandidates(buildRequest()).get(0).score();

        assertThat(mismatchScore).isLessThan(noPreferenceScore);
    }

    @Test
    void pet_mismatch_reduces_score() {
        FeatureFlags flags = mock(FeatureFlags.class);
        when(flags.isAiSchedulingEnabled()).thenReturn(true);
        when(serviceTypeRepository.findById(SERVICE_TYPE_ID)).thenReturn(Optional.of(buildServiceTypeNoCredentials()));
        when(featureFlagsRepository.findByAgencyId(AGENCY_ID)).thenReturn(Optional.of(flags));

        Caregiver cgWithPet = mock(Caregiver.class);
        when(cgWithPet.getId()).thenReturn(CAREGIVER_ID);
        when(cgWithPet.getStatus()).thenReturn(CaregiverStatus.ACTIVE);
        when(cgWithPet.getHomeLat()).thenReturn(NEAR_LAT);
        when(cgWithPet.getHomeLng()).thenReturn(NEAR_LNG);
        when(cgWithPet.getLanguages()).thenReturn("[]");
        when(cgWithPet.hasPet()).thenReturn(true);
        when(caregiverRepository.findByAgencyId(AGENCY_ID)).thenReturn(List.of(cgWithPet));
        when(availabilityRepository.findByCaregiverId(CAREGIVER_ID)).thenReturn(List.of(
            new CaregiverAvailability(CAREGIVER_ID, AGENCY_ID, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(17, 0))));
        when(shiftRepository.findOverlapping(eq(CAREGIVER_ID), any(), any()))
            .thenReturn(Collections.emptyList());
        when(credentialRepository.findByCaregiverId(CAREGIVER_ID)).thenReturn(Collections.emptyList());
        when(scoringProfileRepository.findByCaregiverId(CAREGIVER_ID)).thenReturn(Optional.empty());

        Client noPetClient = mock(Client.class);
        when(noPetClient.getLat()).thenReturn(CLIENT_LAT);
        when(noPetClient.getLng()).thenReturn(CLIENT_LNG);
        when(noPetClient.getPreferredLanguages()).thenReturn("[]");
        when(noPetClient.isNoPetCaregiver()).thenReturn(true); // allergic
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(noPetClient));
        double petMismatchScore = service.rankCandidates(buildRequest()).get(0).score();

        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(buildClient()));
        double noPetPreferenceScore = service.rankCandidates(buildRequest()).get(0).score();

        assertThat(petMismatchScore).isLessThan(noPetPreferenceScore);
    }

    @Test
    void higher_continuity_increases_score() {
        FeatureFlags flags = mock(FeatureFlags.class);
        when(flags.isAiSchedulingEnabled()).thenReturn(true);
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(buildClient()));
        when(serviceTypeRepository.findById(SERVICE_TYPE_ID)).thenReturn(Optional.of(buildServiceTypeNoCredentials()));
        when(featureFlagsRepository.findByAgencyId(AGENCY_ID)).thenReturn(Optional.of(flags));
        when(caregiverRepository.findByAgencyId(AGENCY_ID)).thenReturn(List.of(buildActiveCaregiver()));
        when(availabilityRepository.findByCaregiverId(CAREGIVER_ID)).thenReturn(List.of(
            new CaregiverAvailability(CAREGIVER_ID, AGENCY_ID, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(17, 0))));
        when(shiftRepository.findOverlapping(eq(CAREGIVER_ID), any(), any()))
            .thenReturn(Collections.emptyList());
        when(credentialRepository.findByCaregiverId(CAREGIVER_ID)).thenReturn(Collections.emptyList());

        CaregiverScoringProfile profile = new CaregiverScoringProfile(CAREGIVER_ID, AGENCY_ID);
        when(scoringProfileRepository.findByCaregiverId(CAREGIVER_ID)).thenReturn(Optional.of(profile));

        when(affinityRepository.findByScoringProfileIdAndClientId(any(), eq(CLIENT_ID)))
            .thenReturn(Optional.empty());
        double noVisitScore = service.rankCandidates(buildRequest()).get(0).score();

        CaregiverClientAffinity affinity = mock(CaregiverClientAffinity.class);
        when(affinity.getVisitCount()).thenReturn(5);
        when(affinityRepository.findByScoringProfileIdAndClientId(any(), eq(CLIENT_ID)))
            .thenReturn(Optional.of(affinity));
        double fiveVisitScore = service.rankCandidates(buildRequest()).get(0).score();

        assertThat(fiveVisitScore).isGreaterThan(noVisitScore);
    }

    // ── event listener tests ──────────────────────────────────────────────────

    @Test
    void onShiftCompleted_creates_profile_and_updates_hours() {
        UUID CG = UUID.randomUUID();
        UUID CL = UUID.randomUUID();
        UUID AG = UUID.randomUUID();
        LocalDateTime timeIn  = LocalDateTime.of(2026, 4, 20, 9, 0);
        LocalDateTime timeOut = LocalDateTime.of(2026, 4, 20, 13, 0); // 4h

        when(scoringProfileRepository.findByCaregiverId(CG)).thenReturn(Optional.empty());
        CaregiverScoringProfile newProfile = new CaregiverScoringProfile(CG, AG);
        when(scoringProfileRepository.save(any(CaregiverScoringProfile.class))).thenReturn(newProfile);
        when(affinityRepository.findByScoringProfileIdAndClientId(any(), eq(CL))).thenReturn(Optional.empty());
        CaregiverClientAffinity newAffinity = new CaregiverClientAffinity(newProfile.getId(), CL, AG);
        when(affinityRepository.save(any(CaregiverClientAffinity.class))).thenReturn(newAffinity);

        service.onShiftCompleted(new ShiftCompletedEvent(UUID.randomUUID(), CG, CL, AG, timeIn, timeOut));

        verify(scoringProfileRepository, atLeastOnce()).save(argThat(p ->
            p.getCurrentWeekHours().compareTo(new BigDecimal("4.00")) == 0
            && p.getCompletedShiftsLast90Days() == 1));
    }

    @Test
    void onShiftCompleted_increments_affinity_visit_count() {
        UUID CG = UUID.randomUUID();
        UUID CL = UUID.randomUUID();
        UUID AG = UUID.randomUUID();
        UUID PROFILE_ID = UUID.randomUUID();

        CaregiverScoringProfile existingProfile = mock(CaregiverScoringProfile.class);
        when(existingProfile.getId()).thenReturn(PROFILE_ID);
        when(scoringProfileRepository.findByCaregiverId(CG)).thenReturn(Optional.of(existingProfile));
        when(scoringProfileRepository.save(any())).thenReturn(existingProfile);

        CaregiverClientAffinity affinity = new CaregiverClientAffinity(PROFILE_ID, CL, AG);
        when(affinityRepository.findByScoringProfileIdAndClientId(PROFILE_ID, CL))
            .thenReturn(Optional.of(affinity));
        when(affinityRepository.save(any())).thenReturn(affinity);

        service.onShiftCompleted(new ShiftCompletedEvent(UUID.randomUUID(), CG, CL, AG,
            LocalDateTime.of(2026, 4, 20, 9, 0), LocalDateTime.of(2026, 4, 20, 13, 0)));

        assertThat(affinity.getVisitCount()).isEqualTo(1);
    }

    @Test
    void onShiftCancelled_increments_cancel_count_and_recalculates_rate() {
        UUID CG = UUID.randomUUID();
        UUID AG = UUID.randomUUID();
        CaregiverScoringProfile profile = new CaregiverScoringProfile(CG, AG);
        when(scoringProfileRepository.findByCaregiverId(CG)).thenReturn(Optional.of(profile));
        when(scoringProfileRepository.save(any())).thenReturn(profile);

        service.onShiftCancelled(new ShiftCancelledEvent(UUID.randomUUID(), CG, AG));

        assertThat(profile.getCancelledShiftsLast90Days()).isEqualTo(1);
        assertThat(profile.getCancelRateLast90Days()).isEqualByComparingTo("1.0000");
    }

    @Test
    void onShiftCompleted_null_caregiverId_is_no_op() {
        service.onShiftCompleted(new ShiftCompletedEvent(
            UUID.randomUUID(), null, UUID.randomUUID(), UUID.randomUUID(),
            LocalDateTime.now(), LocalDateTime.now().plusHours(4)));
        verify(scoringProfileRepository, never()).findByCaregiverId(any());
    }
```

- [ ] **Step 2: Run all unit tests to verify they pass**

Run: `cd backend && ./mvnw test -pl . -Dtest=LocalScoringServiceTest -q`
Expected: All tests PASS

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/hcare/scoring/LocalScoringServiceTest.java
git commit -m "test: add scoring, explanation, and event listener unit tests (Task 4)"
```

---

### Task 5: Publish ShiftCompletedEvent from VisitService + disable cron in test profile

**Files:**
- Modify: `backend/src/main/java/com/hcare/api/v1/visits/VisitService.java`
- Modify: `backend/src/main/resources/application-test.yml`

- [ ] **Step 0: Verify `@EnableScheduling` is present**

Confirm that `@EnableScheduling` appears somewhere in the application configuration (e.g., on the main `@SpringBootApplication` class or a `@Configuration` class). The existing nightly shift-generation scheduler requires it, so it should already be present. If missing, add it to the main application class.

Run: `cd backend && grep -r "@EnableScheduling" src/main/java`
Expected: at least one match.

- [ ] **Step 1: Add ShiftCompletedEvent publish to VisitService.clockOut**

In `backend/src/main/java/com/hcare/api/v1/visits/VisitService.java`:

Add import at the top of the import block:
```java
import com.hcare.domain.ShiftCompletedEvent;
```

In the `clockOut` method, after `shiftRepository.save(shift);` and before `if (shift.getAuthorizationId() != null)`, add:
```java
        // Notify scoring module to update CaregiverScoringProfile + CaregiverClientAffinity.
        // @TransactionalEventListener on LocalScoringService.onShiftCompleted fires AFTER_COMMIT.
        if (shift.getCaregiverId() != null) {
            eventPublisher.publishEvent(new ShiftCompletedEvent(
                shiftId, shift.getCaregiverId(), shift.getClientId(), shift.getAgencyId(),
                record.getTimeIn(), record.getTimeOut()));
        }
```

The modified block in `clockOut` looks like:
```java
        // (existing) record.setTimeOut / setCapturedOffline / evvRecordRepository.save
        evvRecordRepository.save(record);
        shift.setStatus(ShiftStatus.COMPLETED);
        shiftRepository.save(shift);

        // NEW: publish ShiftCompletedEvent
        if (shift.getCaregiverId() != null) {
            eventPublisher.publishEvent(new ShiftCompletedEvent(
                shiftId, shift.getCaregiverId(), shift.getClientId(), shift.getAgencyId(),
                record.getTimeIn(), record.getTimeOut()));
        }

        // (existing) if (shift.getAuthorizationId() != null) { ... afterCommit ... }
```

- [ ] **Step 2: Disable weekly reset cron in test profile**

Modify `backend/src/main/resources/application-test.yml` — add the `scoring` block:
```yaml
spring:
  jpa:
    show-sql: false

hcare:
  scheduling:
    shift-generation-cron: "-"
  scoring:
    weekly-reset-cron: "-"
```

- [ ] **Step 3: Run all tests to verify nothing regressed**

Run: `cd backend && ./mvnw test -q`
Expected: All tests PASS

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/hcare/api/v1/visits/VisitService.java \
        backend/src/main/resources/application-test.yml
git commit -m "feat: publish ShiftCompletedEvent from VisitService.clockOut; disable weekly-reset cron in test profile"
```

---

### Task 6: Integration test

**Files:**
- Modify: `backend/src/main/java/com/hcare/domain/CaregiverClientAffinityRepository.java`
- Create: `backend/src/test/java/com/hcare/scoring/LocalScoringServiceIT.java`

- [ ] **Step 0: Add `findByScoringProfileId` to `CaregiverClientAffinityRepository`**

In `backend/src/main/java/com/hcare/domain/CaregiverClientAffinityRepository.java`, add this method:
```java
    /**
     * Returns all affinity records for a given scoring profile.
     * Used by integration tests to verify affinity accumulation after shift completion events.
     */
    List<CaregiverClientAffinity> findByScoringProfileId(UUID scoringProfileId);
```

Required import (add if not already present):
```java
import java.util.List;
import java.util.UUID;
```

Run: `cd backend && ./mvnw compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 1: Write the integration test**

`backend/src/test/java/com/hcare/scoring/LocalScoringServiceIT.java`:
```java
package com.hcare.scoring;

import com.hcare.AbstractIntegrationTest;
import com.hcare.domain.*;
import com.hcare.multitenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LocalScoringServiceIT extends AbstractIntegrationTest {

    @Autowired LocalScoringService scoringService;
    @Autowired AgencyRepository agencyRepository;
    @Autowired ClientRepository clientRepository;
    @Autowired CaregiverRepository caregiverRepository;
    @Autowired ServiceTypeRepository serviceTypeRepository;
    @Autowired FeatureFlagsRepository featureFlagsRepository;
    @Autowired CaregiverAvailabilityRepository availabilityRepository;
    @Autowired CaregiverScoringProfileRepository scoringProfileRepository;
    @Autowired CaregiverClientAffinityRepository affinityRepository;
    @Autowired TransactionTemplate transactionTemplate;

    Agency agency;
    Client client;
    ServiceType serviceType;
    LocalDateTime shiftStart;
    LocalDateTime shiftEnd;

    @BeforeEach
    void setupData() {
        agency = agencyRepository.save(new Agency("Scoring IT Agency", "TX"));
        TenantContext.set(agency.getId());

        client = clientRepository.save(new Client(
            agency.getId(), "Score", "Client", java.time.LocalDate.of(1950, 1, 1)));
        client.setLat(new BigDecimal("30.2672"));
        client.setLng(new BigDecimal("-97.7431"));
        clientRepository.save(client);

        serviceType = serviceTypeRepository.save(
            new ServiceType(agency.getId(), "PCS", "PCS-SCORE-IT", true, "[]"));

        FeatureFlags flags = new FeatureFlags(agency.getId());
        flags.setAiSchedulingEnabled(true);
        featureFlagsRepository.save(flags);

        // Next Monday at 09:00–13:00
        LocalDate nextMonday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        shiftStart = nextMonday.atTime(9, 0);
        shiftEnd   = nextMonday.atTime(13, 0);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private Caregiver createCaregiverWithAvailability(String name, BigDecimal homeLat, BigDecimal homeLng) {
        Caregiver c = new Caregiver(agency.getId(), name, "CG", name.toLowerCase() + "@test.com");
        c.setHomeLat(homeLat);
        c.setHomeLng(homeLng);
        Caregiver saved = caregiverRepository.save(c);
        availabilityRepository.save(new CaregiverAvailability(
            saved.getId(), agency.getId(), DayOfWeek.MONDAY,
            LocalTime.of(8, 0), LocalTime.of(17, 0)));
        return saved;
    }

    @Test
    void near_caregiver_ranks_above_far_caregiver() {
        // Near: ~0.3 miles from client
        Caregiver near = createCaregiverWithAvailability(
            "Near", new BigDecimal("30.2700"), new BigDecimal("-97.7400"));
        // Far: ~16 miles from client
        Caregiver far = createCaregiverWithAvailability(
            "Far", new BigDecimal("30.5000"), new BigDecimal("-97.7431"));

        ShiftMatchRequest request = new ShiftMatchRequest(
            agency.getId(), client.getId(), serviceType.getId(), null, shiftStart, shiftEnd);

        List<RankedCaregiver> results = transactionTemplate.execute(status ->
            scoringService.rankCandidates(request));

        assertThat(results).hasSize(2);
        assertThat(results.get(0).caregiverId()).isEqualTo(near.getId());
        assertThat(results.get(0).score()).isGreaterThan(results.get(1).score());
        assertThat(results.get(0).explanation())
            .contains("miles away")
            .contains("no overtime risk");
    }

    @Test
    void caregiver_without_availability_is_excluded_from_results() {
        Caregiver eligible = createCaregiverWithAvailability(
            "Eligible", new BigDecimal("30.2700"), new BigDecimal("-97.7400"));
        // Second caregiver has no availability windows
        caregiverRepository.save(new Caregiver(
            agency.getId(), "NoAvail", "CG", "noavail@test.com"));

        ShiftMatchRequest request = new ShiftMatchRequest(
            agency.getId(), client.getId(), serviceType.getId(), null, shiftStart, shiftEnd);

        List<RankedCaregiver> results = transactionTemplate.execute(status ->
            scoringService.rankCandidates(request));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).caregiverId()).isEqualTo(eligible.getId());
    }

    @Test
    void onShiftCompleted_creates_profile_and_affinity() {
        Caregiver caregiver = createCaregiverWithAvailability(
            "EventCG", new BigDecimal("30.2700"), new BigDecimal("-97.7400"));

        // Call listener directly — @Transactional(REQUIRES_NEW) creates its own transaction
        scoringService.onShiftCompleted(new ShiftCompletedEvent(
            UUID.randomUUID(),
            caregiver.getId(), client.getId(), agency.getId(),
            LocalDateTime.of(2026, 4, 20, 9, 0),
            LocalDateTime.of(2026, 4, 20, 13, 0) // 4 hours
        ));

        CaregiverScoringProfile profile = transactionTemplate.execute(status ->
            scoringProfileRepository.findByCaregiverId(caregiver.getId()).orElse(null));
        assertThat(profile).isNotNull();
        assertThat(profile.getCurrentWeekHours()).isEqualByComparingTo("4.00");
        assertThat(profile.getCompletedShiftsLast90Days()).isEqualTo(1);

        List<CaregiverClientAffinity> affinities = transactionTemplate.execute(status ->
            affinityRepository.findByScoringProfileId(profile.getId()));
        assertThat(affinities).hasSize(1);
        assertThat(affinities.get(0).getClientId()).isEqualTo(client.getId());
        assertThat(affinities.get(0).getVisitCount()).isEqualTo(1);
    }

    @Test
    void repeated_onShiftCompleted_accumulates_continuity_and_hours() {
        Caregiver caregiver = createCaregiverWithAvailability(
            "ContinuousCG", new BigDecimal("30.2700"), new BigDecimal("-97.7400"));

        for (int i = 0; i < 3; i++) {
            final int day = 20 + i;
            scoringService.onShiftCompleted(new ShiftCompletedEvent(
                UUID.randomUUID(), caregiver.getId(), client.getId(), agency.getId(),
                LocalDateTime.of(2026, 4, day, 9, 0),
                LocalDateTime.of(2026, 4, day, 13, 0)));
        }

        CaregiverScoringProfile profile = transactionTemplate.execute(status ->
            scoringProfileRepository.findByCaregiverId(caregiver.getId()).orElseThrow());
        assertThat(profile.getCompletedShiftsLast90Days()).isEqualTo(3);
        assertThat(profile.getCurrentWeekHours()).isEqualByComparingTo("12.00");

        List<CaregiverClientAffinity> affinities = transactionTemplate.execute(status ->
            affinityRepository.findByScoringProfileId(profile.getId()));
        assertThat(affinities.get(0).getVisitCount()).isEqualTo(3);
    }

    @Test
    void continuity_improves_ranking_after_visits_recorded() {
        Caregiver cgWithHistory = createCaregiverWithAvailability(
            "WithHistory", new BigDecimal("30.2700"), new BigDecimal("-97.7400"));
        Caregiver cgNoHistory = createCaregiverWithAvailability(
            "NoHistory", new BigDecimal("30.2700"), new BigDecimal("-97.7400"));
        // Both near the client — only continuity differentiates them

        // Record 5 visits for cgWithHistory
        for (int i = 0; i < 5; i++) {
            final int day = 20 + i;
            scoringService.onShiftCompleted(new ShiftCompletedEvent(
                UUID.randomUUID(), cgWithHistory.getId(), client.getId(), agency.getId(),
                LocalDateTime.of(2026, 4, day, 9, 0),
                LocalDateTime.of(2026, 4, day, 13, 0)));
        }

        ShiftMatchRequest request = new ShiftMatchRequest(
            agency.getId(), client.getId(), serviceType.getId(), null, shiftStart, shiftEnd);

        List<RankedCaregiver> results = transactionTemplate.execute(status ->
            scoringService.rankCandidates(request));

        assertThat(results).hasSize(2);
        assertThat(results.get(0).caregiverId()).isEqualTo(cgWithHistory.getId());
        assertThat(results.get(0).explanation()).contains("worked with this client 5 times");
    }

    @Test
    void resetWeeklyHours_zeroes_current_week_hours_for_all_profiles() {
        Caregiver cg1 = createCaregiverWithAvailability(
            "Reset1", new BigDecimal("30.2700"), new BigDecimal("-97.7400"));
        Caregiver cg2 = createCaregiverWithAvailability(
            "Reset2", new BigDecimal("30.2700"), new BigDecimal("-97.7400"));

        // Record hours for both caregivers
        scoringService.onShiftCompleted(new ShiftCompletedEvent(
            UUID.randomUUID(), cg1.getId(), client.getId(), agency.getId(),
            LocalDateTime.of(2026, 4, 20, 9, 0), LocalDateTime.of(2026, 4, 20, 13, 0)));
        scoringService.onShiftCompleted(new ShiftCompletedEvent(
            UUID.randomUUID(), cg2.getId(), client.getId(), agency.getId(),
            LocalDateTime.of(2026, 4, 20, 9, 0), LocalDateTime.of(2026, 4, 20, 17, 0)));

        // Verify hours accumulated before reset
        transactionTemplate.execute(status -> {
            assertThat(scoringProfileRepository.findByCaregiverId(cg1.getId())
                .orElseThrow().getCurrentWeekHours()).isEqualByComparingTo("4.00");
            assertThat(scoringProfileRepository.findByCaregiverId(cg2.getId())
                .orElseThrow().getCurrentWeekHours()).isEqualByComparingTo("8.00");
            return null;
        });

        // Invoke the scheduled method directly (cron is disabled in test profile)
        scoringService.resetWeeklyHours();

        transactionTemplate.execute(status -> {
            assertThat(scoringProfileRepository.findByCaregiverId(cg1.getId())
                .orElseThrow().getCurrentWeekHours()).isEqualByComparingTo("0.00");
            assertThat(scoringProfileRepository.findByCaregiverId(cg2.getId())
                .orElseThrow().getCurrentWeekHours()).isEqualByComparingTo("0.00");
            return null;
        });
    }
}
```

- [ ] **Step 2: Run the integration test (requires Docker)**

Run: `cd backend && ./mvnw test -pl . -Dtest=LocalScoringServiceIT -q`
Expected: 6 tests PASS

- [ ] **Step 3: Run the full test suite**

Run: `cd backend && ./mvnw test -q`
Expected: All tests PASS

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/hcare/domain/CaregiverClientAffinityRepository.java \
        backend/src/test/java/com/hcare/scoring/LocalScoringServiceIT.java
git commit -m "test: LocalScoringServiceIT — end-to-end ranking, event listeners, continuity accumulation"
```
