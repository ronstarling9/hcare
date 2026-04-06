# Shift & Recurrence Domain Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Shift, RecurrencePattern, EvvRecord, ShiftOffer, AdlTaskCompletion, IncidentReport, and CommunicationMessage entities plus the ShiftGenerationService that turns patterns into individual shifts on a rolling 8-week horizon. Plans 4 (EVV Compliance) and 5 (AI Scoring) build directly on this.

**Architecture:** All new entities follow the agency-scoped pattern: `agencyId` column + `@Filter(name = "agencyFilter", condition = "agency_id = :agencyId")`. RecurrencePattern carries `@Version` for optimistic locking (concurrent pattern saves + nightly job can race). ShiftGenerationService is a `@Transactional` Spring service — `generateForPattern` only extends the `generatedThrough` frontier; it never touches shifts with status IN_PROGRESS, COMPLETED, CANCELLED, or MISSED. EvvRecord stores raw captured data only (GPS, timestamps, verification method, state JSON) — compliance status computation is Plan 4. The nightly `@Scheduled` job is disabled in the test profile via a cron property placeholder to prevent interference.

**Tech Stack:** Java 25, Spring Boot 3.4.4, Spring Data JPA/Hibernate 6, Flyway, JUnit 5, Mockito, AssertJ, Testcontainers PostgreSQL 16.

---

## File Structure

```
backend/src/main/java/com/hcare/
├── config/
│   └── SchedulingConfig.java               — @Configuration @EnableScheduling (new)
├── domain/
│   ├── ShiftStatus.java                    — enum: OPEN | ASSIGNED | IN_PROGRESS | COMPLETED | CANCELLED | MISSED
│   ├── ShiftOfferResponse.java             — enum: ACCEPTED | DECLINED | NO_RESPONSE
│   ├── IncidentSeverity.java               — enum: LOW | MEDIUM | HIGH | CRITICAL
│   ├── RecurrencePattern.java              — @Filter(agencyFilter), @Version, generatedThrough
│   ├── RecurrencePatternRepository.java    — findActivePatternsBehindHorizon custom query
│   ├── Shift.java                          — @Filter(agencyFilter), status set on construction
│   ├── ShiftRepository.java                — findByClientId/CaregiverId window queries, deleteUnstartedFutureShifts
│   ├── EvvRecord.java                      — @Filter(agencyFilter), unique shiftId FK, no stored complianceStatus
│   ├── EvvRecordRepository.java            — findByShiftId
│   ├── ShiftOffer.java                     — @Filter(agencyFilter), uq(shiftId+caregiverId)
│   ├── ShiftOfferRepository.java           — findByShiftId
│   ├── AdlTaskCompletion.java              — @Filter(agencyFilter), uq(shiftId+adlTaskId)
│   ├── AdlTaskCompletionRepository.java    — findByShiftId
│   ├── IncidentReport.java                 — @Filter(agencyFilter), nullable shiftId (polymorphic reporter)
│   ├── IncidentReportRepository.java
│   ├── CommunicationMessage.java           — @Filter(agencyFilter)
│   └── CommunicationMessageRepository.java
├── evv/
│   └── VerificationMethod.java             — enum: GPS | TELEPHONY_LANDLINE | TELEPHONY_CELL | FIXED_DEVICE | FOB | BIOMETRIC | MANUAL
└── scheduling/
    ├── ShiftGenerationService.java         — interface
    ├── LocalShiftGenerationService.java    — @Service, static parseDaysOfWeek for testability
    └── ShiftGenerationScheduler.java       — @Scheduled cron, disabled in test profile

backend/src/main/resources/
├── application-test.yml                    — add hcare.scheduling.shift-generation-cron: "-"
└── db/migration/
    └── V6__shift_domain_schema.sql

backend/src/test/java/com/hcare/
├── domain/
│   ├── RecurrencePatternDomainIT.java      — save, version increment, agencyFilter, findBehindHorizon
│   ├── ShiftDomainIT.java                  — status on construction, pattern link, agencyFilter, window query
│   ├── EvvRecordDomainIT.java              — save as child of Shift, unique constraint, offline fields, agencyFilter
│   ├── ShiftSubEntitiesIT.java             — ShiftOffer respond/unique, AdlTaskCompletion unique
│   └── IncidentCommunicationIT.java        — IncidentReport with/without shift, CommunicationMessage, agencyFilters
└── scheduling/
    ├── ShiftGenerationServiceTest.java     — Mockito unit: day filtering, endDate, inactive, regenerate, parseDaysOfWeek
    └── ShiftGenerationServiceIT.java       — Testcontainers: real DB end-to-end, scheduler advanceGenerationFrontier
```

---

### Task 1: V6 Schema Migration + Enums + SchedulingConfig

**Files:**
- Create: `backend/src/main/resources/db/migration/V6__shift_domain_schema.sql`
- Create: `backend/src/main/java/com/hcare/domain/ShiftStatus.java`
- Create: `backend/src/main/java/com/hcare/domain/ShiftOfferResponse.java`
- Create: `backend/src/main/java/com/hcare/domain/IncidentSeverity.java`
- Create: `backend/src/main/java/com/hcare/evv/VerificationMethod.java`
- Create: `backend/src/main/java/com/hcare/config/SchedulingConfig.java`
- Modify: `backend/src/main/resources/application-test.yml`

- [ ] **Step 1: Write V6 migration SQL**

`backend/src/main/resources/db/migration/V6__shift_domain_schema.sql`:
```sql
-- V6: Shift domain — RecurrencePattern, Shift, EvvRecord, ShiftOffer,
-- AdlTaskCompletion, IncidentReport, CommunicationMessage.
--
-- JSON fields stored as TEXT (H2/PostgreSQL portability — no JSONB).
-- TIMESTAMP for all datetime fields (no timezone handling in P1).
-- No DEFAULT on id columns — Hibernate generates UUIDs via @GeneratedValue(UUID).

CREATE TABLE recurrence_patterns (
    id                         UUID         PRIMARY KEY,
    agency_id                  UUID         NOT NULL REFERENCES agencies(id),
    client_id                  UUID         NOT NULL REFERENCES clients(id),
    caregiver_id               UUID         REFERENCES caregivers(id),
    service_type_id            UUID         NOT NULL REFERENCES service_types(id),
    authorization_id           UUID         REFERENCES authorizations(id),
    scheduled_start_time       TIME         NOT NULL,
    scheduled_duration_minutes INT          NOT NULL,
    days_of_week               TEXT         NOT NULL,
    start_date                 DATE         NOT NULL,
    end_date                   DATE,
    generated_through          DATE         NOT NULL,
    is_active                  BOOLEAN      NOT NULL DEFAULT TRUE,
    version                    BIGINT       NOT NULL DEFAULT 0,
    created_at                 TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_recurrence_patterns_agency_id ON recurrence_patterns(agency_id);
CREATE INDEX idx_recurrence_patterns_client_id ON recurrence_patterns(client_id);
CREATE INDEX idx_recurrence_patterns_active_frontier ON recurrence_patterns(is_active, generated_through);

CREATE TABLE shifts (
    id                UUID        PRIMARY KEY,
    agency_id         UUID        NOT NULL REFERENCES agencies(id),
    source_pattern_id UUID        REFERENCES recurrence_patterns(id),
    client_id         UUID        NOT NULL REFERENCES clients(id),
    caregiver_id      UUID        REFERENCES caregivers(id),
    service_type_id   UUID        NOT NULL REFERENCES service_types(id),
    authorization_id  UUID        REFERENCES authorizations(id),
    scheduled_start   TIMESTAMP   NOT NULL,
    scheduled_end     TIMESTAMP   NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    notes             TEXT,
    created_at        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_shifts_agency_id       ON shifts(agency_id);
CREATE INDEX idx_shifts_client_id       ON shifts(client_id);
CREATE INDEX idx_shifts_caregiver_id    ON shifts(caregiver_id);
CREATE INDEX idx_shifts_scheduled_start ON shifts(scheduled_start);
CREATE INDEX idx_shifts_source_pattern  ON shifts(source_pattern_id);

CREATE TABLE evv_records (
    id                  UUID         PRIMARY KEY,
    shift_id            UUID         NOT NULL UNIQUE REFERENCES shifts(id),
    agency_id           UUID         NOT NULL REFERENCES agencies(id),
    -- Federal element 2: clientMedicaidId (elements 1, 3, 5 derivable from Shift)
    client_medicaid_id  VARCHAR(50),
    -- Federal element 4: GPS at clock-in
    location_lat        DECIMAL(10,7),
    location_lon        DECIMAL(10,7),
    -- Federal element 6: actual clock-in/out
    time_in             TIMESTAMP,
    time_out            TIMESTAMP,
    verification_method VARCHAR(30)  NOT NULL,
    co_resident         BOOLEAN      NOT NULL DEFAULT FALSE,
    state_fields        TEXT         NOT NULL DEFAULT '{}',
    captured_offline    BOOLEAN      NOT NULL DEFAULT FALSE,
    device_captured_at  TIMESTAMP,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_evv_records_agency_id ON evv_records(agency_id);

CREATE TABLE shift_offers (
    id            UUID        PRIMARY KEY,
    shift_id      UUID        NOT NULL REFERENCES shifts(id),
    caregiver_id  UUID        NOT NULL REFERENCES caregivers(id),
    agency_id     UUID        NOT NULL REFERENCES agencies(id),
    offered_at    TIMESTAMP   NOT NULL,
    responded_at  TIMESTAMP,
    response      VARCHAR(20) NOT NULL DEFAULT 'NO_RESPONSE',
    CONSTRAINT uq_shift_offers UNIQUE (shift_id, caregiver_id)
);
CREATE INDEX idx_shift_offers_agency_id ON shift_offers(agency_id);
CREATE INDEX idx_shift_offers_shift_id  ON shift_offers(shift_id);

CREATE TABLE adl_task_completions (
    id              UUID        PRIMARY KEY,
    shift_id        UUID        NOT NULL REFERENCES shifts(id),
    adl_task_id     UUID        NOT NULL REFERENCES adl_tasks(id),
    agency_id       UUID        NOT NULL REFERENCES agencies(id),
    completed_at    TIMESTAMP   NOT NULL,
    caregiver_notes TEXT,
    CONSTRAINT uq_adl_task_completion UNIQUE (shift_id, adl_task_id)
);
-- shift_id lookup is covered by the uq_adl_task_completion unique index (shift_id, adl_task_id)
CREATE INDEX idx_adl_task_completions_agency_id ON adl_task_completions(agency_id);

CREATE TABLE incident_reports (
    id                UUID        PRIMARY KEY,
    agency_id         UUID        NOT NULL REFERENCES agencies(id),
    shift_id          UUID        REFERENCES shifts(id),
    reported_by_type  VARCHAR(30) NOT NULL,
    reported_by_id    UUID        NOT NULL,
    description       TEXT        NOT NULL,
    severity          VARCHAR(20) NOT NULL,
    occurred_at       TIMESTAMP   NOT NULL,
    created_at        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_incident_reports_agency_id ON incident_reports(agency_id);

CREATE TABLE communication_messages (
    id              UUID        PRIMARY KEY,
    agency_id       UUID        NOT NULL REFERENCES agencies(id),
    sender_type     VARCHAR(30) NOT NULL,
    sender_id       UUID        NOT NULL,
    recipient_type  VARCHAR(30) NOT NULL,
    recipient_id    UUID        NOT NULL,
    subject         VARCHAR(255),
    body            TEXT        NOT NULL,
    sent_at         TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_communication_messages_agency_id ON communication_messages(agency_id);
```

- [ ] **Step 2: Write the four enum files**

`backend/src/main/java/com/hcare/domain/ShiftStatus.java`:
```java
package com.hcare.domain;

public enum ShiftStatus {
    OPEN,        // no caregiver assigned
    ASSIGNED,    // caregiver assigned, not yet started
    IN_PROGRESS, // caregiver clocked in
    COMPLETED,   // caregiver clocked out
    CANCELLED,
    MISSED       // scheduled time passed without clock-in
}
```

`backend/src/main/java/com/hcare/domain/ShiftOfferResponse.java`:
```java
package com.hcare.domain;

public enum ShiftOfferResponse {
    ACCEPTED,
    DECLINED,
    NO_RESPONSE
}
```

`backend/src/main/java/com/hcare/domain/IncidentSeverity.java`:
```java
package com.hcare.domain;

public enum IncidentSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
```

`backend/src/main/java/com/hcare/evv/VerificationMethod.java`:
```java
package com.hcare.evv;

public enum VerificationMethod {
    GPS,
    TELEPHONY_LANDLINE,
    TELEPHONY_CELL,
    FIXED_DEVICE,
    FOB,
    BIOMETRIC,
    MANUAL
}
```

- [ ] **Step 3: Add SchedulingConfig and disable scheduler in test profile**

`backend/src/main/java/com/hcare/config/SchedulingConfig.java`:
```java
package com.hcare.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class SchedulingConfig {}
```

`backend/src/main/resources/application-test.yml`:
```yaml
spring:
  jpa:
    show-sql: false

hcare:
  scheduling:
    shift-generation-cron: "-"
```

- [ ] **Step 4: Run context loads test to verify migration compiles and runs**

```bash
cd backend && ./mvnw test -Dtest=HcareApplicationTest 2>&1 | tail -5
```
Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/resources/db/migration/V6__shift_domain_schema.sql \
        backend/src/main/java/com/hcare/domain/ShiftStatus.java \
        backend/src/main/java/com/hcare/domain/ShiftOfferResponse.java \
        backend/src/main/java/com/hcare/domain/IncidentSeverity.java \
        backend/src/main/java/com/hcare/evv/VerificationMethod.java \
        backend/src/main/java/com/hcare/config/SchedulingConfig.java \
        backend/src/main/resources/application-test.yml
git commit -m "feat: V6 shift domain schema + enums (ShiftStatus, ShiftOfferResponse, IncidentSeverity, VerificationMethod) + @EnableScheduling"
```

---

### Task 2: RecurrencePattern Entity + Repository + IT

**Files:**
- Create: `backend/src/test/java/com/hcare/domain/RecurrencePatternDomainIT.java`
- Create: `backend/src/main/java/com/hcare/domain/RecurrencePattern.java`
- Create: `backend/src/main/java/com/hcare/domain/RecurrencePatternRepository.java`

- [ ] **Step 1: Write the failing test**

`backend/src/test/java/com/hcare/domain/RecurrencePatternDomainIT.java`:
```java
package com.hcare.domain;

import com.hcare.AbstractIntegrationTest;
import com.hcare.multitenancy.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class RecurrencePatternDomainIT extends AbstractIntegrationTest {

    @Autowired AgencyRepository agencyRepo;
    @Autowired ClientRepository clientRepo;
    @Autowired ServiceTypeRepository serviceTypeRepo;
    @Autowired RecurrencePatternRepository patternRepo;
    @Autowired TransactionTemplate transactionTemplate;

    @Test
    void recurrencePattern_can_be_saved_and_retrieved() {
        Agency agency = agencyRepo.save(new Agency("RP Save Agency", "TX"));
        Client client = clientRepo.save(new Client(agency.getId(), "Jane", "Doe", LocalDate.of(1960, 1, 15)));
        ServiceType st = serviceTypeRepo.save(new ServiceType(agency.getId(), "Personal Care", "PCS-RP1", true, "[]"));

        RecurrencePattern pattern = new RecurrencePattern(
            agency.getId(), client.getId(), st.getId(),
            LocalTime.of(9, 0), 240, "[\"MONDAY\",\"WEDNESDAY\",\"FRIDAY\"]",
            LocalDate.of(2026, 4, 7)
        );
        patternRepo.save(pattern);

        RecurrencePattern loaded = patternRepo.findById(pattern.getId()).orElseThrow();
        assertThat(loaded.getAgencyId()).isEqualTo(agency.getId());
        assertThat(loaded.getClientId()).isEqualTo(client.getId());
        assertThat(loaded.getServiceTypeId()).isEqualTo(st.getId());
        assertThat(loaded.getScheduledStartTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(loaded.getScheduledDurationMinutes()).isEqualTo(240);
        assertThat(loaded.getDaysOfWeek()).isEqualTo("[\"MONDAY\",\"WEDNESDAY\",\"FRIDAY\"]");
        assertThat(loaded.isActive()).isTrue();
        assertThat(loaded.getVersion()).isZero();
        // generatedThrough is initialized to startDate - 1 day
        assertThat(loaded.getGeneratedThrough()).isEqualTo(LocalDate.of(2026, 4, 6));
        assertThat(loaded.getCaregiverId()).isNull();
        assertThat(loaded.getEndDate()).isNull();
    }

    @Test
    void recurrencePattern_version_increments_on_update() {
        Agency agency = agencyRepo.save(new Agency("RP Version Agency", "TX"));
        Client client = clientRepo.save(new Client(agency.getId(), "Ver", "Test", LocalDate.of(1970, 6, 1)));
        ServiceType st = serviceTypeRepo.save(new ServiceType(agency.getId(), "SN", "SN-RP2", true, "[]"));

        RecurrencePattern pattern = new RecurrencePattern(
            agency.getId(), client.getId(), st.getId(),
            LocalTime.of(8, 0), 480, "[\"TUESDAY\"]", LocalDate.of(2026, 4, 8)
        );
        patternRepo.save(pattern);
        assertThat(pattern.getVersion()).isZero();

        pattern.setActive(false);
        patternRepo.save(pattern);

        assertThat(patternRepo.findById(pattern.getId()).orElseThrow().getVersion()).isEqualTo(1L);
    }

    @Test
    void recurrencePattern_agencyFilter_excludes_other_agency_patterns() {
        Agency agencyA = agencyRepo.save(new Agency("RP Agency A", "TX"));
        Agency agencyB = agencyRepo.save(new Agency("RP Agency B", "CA"));
        Client clientA = clientRepo.save(new Client(agencyA.getId(), "A", "Client", LocalDate.of(1960, 1, 1)));
        Client clientB = clientRepo.save(new Client(agencyB.getId(), "B", "Client", LocalDate.of(1960, 1, 1)));
        ServiceType stA = serviceTypeRepo.save(new ServiceType(agencyA.getId(), "PCS", "PCS-RPA", true, "[]"));
        ServiceType stB = serviceTypeRepo.save(new ServiceType(agencyB.getId(), "PCS", "PCS-RPB", true, "[]"));

        RecurrencePattern patternA = patternRepo.save(new RecurrencePattern(
            agencyA.getId(), clientA.getId(), stA.getId(),
            LocalTime.of(9, 0), 120, "[\"MONDAY\"]", LocalDate.of(2026, 4, 7)
        ));
        patternRepo.save(new RecurrencePattern(
            agencyB.getId(), clientB.getId(), stB.getId(),
            LocalTime.of(9, 0), 120, "[\"MONDAY\"]", LocalDate.of(2026, 4, 7)
        ));

        TenantContext.set(agencyA.getId());
        List<RecurrencePattern> result;
        try {
            result = transactionTemplate.execute(status -> patternRepo.findAll());
        } finally {
            TenantContext.clear();
        }

        assertThat(result).isNotNull();
        assertThat(result.stream().map(RecurrencePattern::getId).toList())
            .contains(patternA.getId());
        assertThat(result).allMatch(p -> p.getAgencyId().equals(agencyA.getId()));
    }

    @Test
    void findActivePatternsBehindHorizon_returns_only_patterns_behind_horizon() {
        Agency agency = agencyRepo.save(new Agency("RP Horizon Agency", "TX"));
        Client client = clientRepo.save(new Client(agency.getId(), "Horizon", "Client", LocalDate.of(1965, 3, 10)));
        ServiceType st = serviceTypeRepo.save(new ServiceType(agency.getId(), "PCS", "PCS-RPH", true, "[]"));

        // Pattern behind horizon: generatedThrough = yesterday
        RecurrencePattern behindHorizon = new RecurrencePattern(
            agency.getId(), client.getId(), st.getId(),
            LocalTime.of(10, 0), 60, "[\"WEDNESDAY\"]", LocalDate.now().minusDays(2)
        );
        patternRepo.save(behindHorizon);

        // Pattern at horizon: generatedThrough manually set to now + 8 weeks
        RecurrencePattern atHorizon = new RecurrencePattern(
            agency.getId(), client.getId(), st.getId(),
            LocalTime.of(11, 0), 60, "[\"THURSDAY\"]", LocalDate.now().minusDays(2)
        );
        atHorizon.setGeneratedThrough(LocalDate.now().plusWeeks(8));
        patternRepo.save(atHorizon);

        LocalDate horizon = LocalDate.now().plusWeeks(8);
        List<RecurrencePattern> result = patternRepo.findActivePatternsBehindHorizon(horizon, LocalDate.now());

        assertThat(result.stream().map(RecurrencePattern::getId).toList())
            .contains(behindHorizon.getId())
            .doesNotContain(atHorizon.getId());
    }
}
```

- [ ] **Step 2: Run to verify it fails**

```bash
cd backend && ./mvnw test -Dtest=RecurrencePatternDomainIT 2>&1 | tail -10
```
Expected: `COMPILATION ERROR` — `RecurrencePattern` not found.

- [ ] **Step 3: Write RecurrencePattern entity and RecurrencePatternRepository**

`backend/src/main/java/com/hcare/domain/RecurrencePattern.java`:
```java
package com.hcare.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "recurrence_patterns")
@Filter(name = "agencyFilter", condition = "agency_id = :agencyId")
public class RecurrencePattern {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "caregiver_id")
    private UUID caregiverId;

    @Column(name = "service_type_id", nullable = false)
    private UUID serviceTypeId;

    @Column(name = "authorization_id")
    private UUID authorizationId;

    @Column(name = "scheduled_start_time", nullable = false)
    private LocalTime scheduledStartTime;

    @Column(name = "scheduled_duration_minutes", nullable = false)
    private int scheduledDurationMinutes;

    // JSON array of DayOfWeek names e.g. ["MONDAY","WEDNESDAY","FRIDAY"]
    @Column(name = "days_of_week", nullable = false, columnDefinition = "TEXT")
    private String daysOfWeek;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    // Last date for which Shift rows have been generated.
    // Initialized to startDate - 1 day so the first generateForPattern call starts from startDate.
    @Column(name = "generated_through", nullable = false)
    private LocalDate generatedThrough;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    // Incremented by Hibernate on every UPDATE. Concurrent saves (e.g. pattern edit + nightly
    // scheduler) throw ObjectOptimisticLockingFailureException — caller must retry.
    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected RecurrencePattern() {}

    public RecurrencePattern(UUID agencyId, UUID clientId, UUID serviceTypeId,
                              LocalTime scheduledStartTime, int scheduledDurationMinutes,
                              String daysOfWeek, LocalDate startDate) {
        this.agencyId = agencyId;
        this.clientId = clientId;
        this.serviceTypeId = serviceTypeId;
        this.scheduledStartTime = scheduledStartTime;
        this.scheduledDurationMinutes = scheduledDurationMinutes;
        this.daysOfWeek = daysOfWeek;
        this.startDate = startDate;
        this.generatedThrough = startDate.minusDays(1);
    }

    public void setCaregiverId(UUID caregiverId) { this.caregiverId = caregiverId; }
    public void setAuthorizationId(UUID authorizationId) { this.authorizationId = authorizationId; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public void setActive(boolean active) { this.active = active; }
    public void setGeneratedThrough(LocalDate generatedThrough) { this.generatedThrough = generatedThrough; }

    public UUID getId() { return id; }
    public UUID getAgencyId() { return agencyId; }
    public UUID getClientId() { return clientId; }
    public UUID getCaregiverId() { return caregiverId; }
    public UUID getServiceTypeId() { return serviceTypeId; }
    public UUID getAuthorizationId() { return authorizationId; }
    public LocalTime getScheduledStartTime() { return scheduledStartTime; }
    public int getScheduledDurationMinutes() { return scheduledDurationMinutes; }
    public String getDaysOfWeek() { return daysOfWeek; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public LocalDate getGeneratedThrough() { return generatedThrough; }
    public boolean isActive() { return active; }
    public Long getVersion() { return version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
```

`backend/src/main/java/com/hcare/domain/RecurrencePatternRepository.java`:
```java
package com.hcare.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface RecurrencePatternRepository extends JpaRepository<RecurrencePattern, UUID> {

    /**
     * Returns active patterns whose generatedThrough frontier is behind the given horizon
     * and whose endDate has not yet passed.
     * Called by the nightly scheduler — no TenantContext set, so the Hibernate @Filter is
     * not active. This is intentional: the nightly job processes all agencies.
     */
    @Query("SELECT rp FROM RecurrencePattern rp WHERE rp.active = true " +
           "AND rp.generatedThrough < :horizon " +
           "AND (rp.endDate IS NULL OR rp.endDate >= :today)")
    List<RecurrencePattern> findActivePatternsBehindHorizon(@Param("horizon") LocalDate horizon,
                                                             @Param("today") LocalDate today);
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd backend && ./mvnw test -Dtest=RecurrencePatternDomainIT 2>&1 | tail -5
```
Expected: `BUILD SUCCESS` — 4 tests passing.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/hcare/domain/RecurrencePattern.java \
        backend/src/main/java/com/hcare/domain/RecurrencePatternRepository.java \
        backend/src/test/java/com/hcare/domain/RecurrencePatternDomainIT.java
git commit -m "feat: RecurrencePattern entity — @Version optimistic locking, generatedThrough frontier, agencyFilter"
```

---

### Task 3: Shift Entity + Repository + IT

**Files:**
- Create: `backend/src/test/java/com/hcare/domain/ShiftDomainIT.java`
- Create: `backend/src/main/java/com/hcare/domain/Shift.java`
- Create: `backend/src/main/java/com/hcare/domain/ShiftRepository.java`

- [ ] **Step 1: Write the failing test**

`backend/src/test/java/com/hcare/domain/ShiftDomainIT.java`:
```java
package com.hcare.domain;

import com.hcare.AbstractIntegrationTest;
import com.hcare.multitenancy.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class ShiftDomainIT extends AbstractIntegrationTest {

    @Autowired AgencyRepository agencyRepo;
    @Autowired ClientRepository clientRepo;
    @Autowired CaregiverRepository caregiverRepo;
    @Autowired ServiceTypeRepository serviceTypeRepo;
    @Autowired RecurrencePatternRepository patternRepo;
    @Autowired ShiftRepository shiftRepo;
    @Autowired TransactionTemplate transactionTemplate;

    @Test
    void shift_status_is_open_when_no_caregiver() {
        Agency agency = agencyRepo.save(new Agency("Shift Open Agency", "TX"));
        Client client = clientRepo.save(new Client(agency.getId(), "Open", "Client", LocalDate.of(1955, 3, 1)));
        ServiceType st = serviceTypeRepo.save(new ServiceType(agency.getId(), "PCS", "PCS-SO", true, "[]"));

        LocalDateTime start = LocalDate.of(2026, 4, 14).atTime(9, 0);
        Shift shift = shiftRepo.save(new Shift(
            agency.getId(), null, client.getId(), null,
            st.getId(), null, start, start.plusMinutes(240)
        ));

        Shift loaded = shiftRepo.findById(shift.getId()).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(ShiftStatus.OPEN);
        assertThat(loaded.getCaregiverId()).isNull();
        assertThat(loaded.getSourcePatternId()).isNull();
        assertThat(loaded.getScheduledEnd()).isEqualTo(start.plusMinutes(240));
    }

    @Test
    void shift_status_is_assigned_when_caregiver_provided() {
        Agency agency = agencyRepo.save(new Agency("Shift Assigned Agency", "TX"));
        Client client = clientRepo.save(new Client(agency.getId(), "Assigned", "Client", LocalDate.of(1960, 7, 4)));
        ServiceType st = serviceTypeRepo.save(new ServiceType(agency.getId(), "PCS", "PCS-SA", true, "[]"));
        Caregiver cg = caregiverRepo.save(new Caregiver(agency.getId(), "Alice", "Smith", "alice.shift@test.com"));

        LocalDateTime start = LocalDate.of(2026, 4, 15).atTime(14, 0);
        Shift shift = shiftRepo.save(new Shift(
            agency.getId(), null, client.getId(), cg.getId(),
            st.getId(), null, start, start.plusHours(4)
        ));

        assertThat(shiftRepo.findById(shift.getId()).orElseThrow().getStatus())
            .isEqualTo(ShiftStatus.ASSIGNED);
    }

    @Test
    void shift_links_to_recurrence_pattern() {
        Agency agency = agencyRepo.save(new Agency("Shift Pattern Agency", "TX"));
        Client client = clientRepo.save(new Client(agency.getId(), "Pattern", "Link", LocalDate.of(1970, 11, 11)));
        ServiceType st = serviceTypeRepo.save(new ServiceType(agency.getId(), "PCS", "PCS-SP", true, "[]"));

        RecurrencePattern pattern = patternRepo.save(new RecurrencePattern(
            agency.getId(), client.getId(), st.getId(),
            LocalTime.of(10, 0), 180, "[\"THURSDAY\"]", LocalDate.of(2026, 4, 8)
        ));

        LocalDateTime start = LocalDate.of(2026, 4, 9).atTime(10, 0);
        Shift shift = shiftRepo.save(new Shift(
            agency.getId(), pattern.getId(), client.getId(), null,
            st.getId(), null, start, start.plusMinutes(180)
        ));

        assertThat(shiftRepo.findById(shift.getId()).orElseThrow().getSourcePatternId())
            .isEqualTo(pattern.getId());
    }

    @Test
    void shift_agencyFilter_excludes_other_agency_shifts() {
        Agency agencyA = agencyRepo.save(new Agency("Shift Agency A", "TX"));
        Agency agencyB = agencyRepo.save(new Agency("Shift Agency B", "CA"));
        Client clientA = clientRepo.save(new Client(agencyA.getId(), "A", "Shift", LocalDate.of(1960, 1, 1)));
        Client clientB = clientRepo.save(new Client(agencyB.getId(), "B", "Shift", LocalDate.of(1960, 1, 1)));
        ServiceType stA = serviceTypeRepo.save(new ServiceType(agencyA.getId(), "PCS", "PCS-SA2", true, "[]"));
        ServiceType stB = serviceTypeRepo.save(new ServiceType(agencyB.getId(), "PCS", "PCS-SB2", true, "[]"));

        LocalDateTime start = LocalDate.of(2026, 4, 14).atTime(9, 0);
        Shift shiftA = shiftRepo.save(new Shift(
            agencyA.getId(), null, clientA.getId(), null, stA.getId(), null, start, start.plusHours(4)
        ));
        shiftRepo.save(new Shift(
            agencyB.getId(), null, clientB.getId(), null, stB.getId(), null, start, start.plusHours(4)
        ));

        TenantContext.set(agencyA.getId());
        List<Shift> result;
        try {
            result = transactionTemplate.execute(status -> shiftRepo.findAll());
        } finally {
            TenantContext.clear();
        }

        assertThat(result).isNotNull();
        assertThat(result.stream().map(Shift::getId).toList()).contains(shiftA.getId());
        assertThat(result).allMatch(s -> s.getAgencyId().equals(agencyA.getId()));
    }

    @Test
    void findByClientIdAndScheduledStartBetween_returns_matching_shifts() {
        Agency agency = agencyRepo.save(new Agency("Shift Window Agency", "TX"));
        Client client = clientRepo.save(new Client(agency.getId(), "Window", "Client", LocalDate.of(1965, 4, 20)));
        ServiceType st = serviceTypeRepo.save(new ServiceType(agency.getId(), "PCS", "PCS-SW", true, "[]"));

        LocalDateTime inWindow = LocalDate.of(2026, 4, 20).atTime(9, 0);
        LocalDateTime outOfWindow = LocalDate.of(2026, 5, 20).atTime(9, 0);

        shiftRepo.save(new Shift(agency.getId(), null, client.getId(), null, st.getId(), null,
            inWindow, inWindow.plusHours(4)));
        shiftRepo.save(new Shift(agency.getId(), null, client.getId(), null, st.getId(), null,
            outOfWindow, outOfWindow.plusHours(4)));

        List<Shift> result = shiftRepo.findByClientIdAndScheduledStartBetween(
            client.getId(),
            LocalDate.of(2026, 4, 1).atStartOfDay(),
            LocalDate.of(2026, 4, 30).atTime(23, 59)
        );

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getScheduledStart()).isEqualTo(inWindow);
    }

    @Test
    void deleteUnstartedFutureShifts_leaves_completed_and_other_agency_shifts_intact() {
        Agency agencyA = agencyRepo.save(new Agency("Delete Agency A", "TX"));
        Agency agencyB = agencyRepo.save(new Agency("Delete Agency B", "CA"));
        Client clientA = clientRepo.save(new Client(agencyA.getId(), "Del", "A", LocalDate.of(1960, 1, 1)));
        Client clientB = clientRepo.save(new Client(agencyB.getId(), "Del", "B", LocalDate.of(1960, 1, 1)));
        ServiceType stA = serviceTypeRepo.save(new ServiceType(agencyA.getId(), "PCS", "PCS-DEL-A", true, "[]"));
        ServiceType stB = serviceTypeRepo.save(new ServiceType(agencyB.getId(), "PCS", "PCS-DEL-B", true, "[]"));

        UUID patternId = UUID.randomUUID();
        LocalDateTime future = LocalDateTime.now().plusDays(7);

        // Should be deleted: agencyA OPEN shift in future
        Shift toDelete = shiftRepo.save(new Shift(agencyA.getId(), patternId, clientA.getId(), null,
            stA.getId(), null, future, future.plusHours(4)));

        // Should survive: COMPLETED shift for same pattern
        Shift completed = shiftRepo.save(new Shift(agencyA.getId(), patternId, clientA.getId(), null,
            stA.getId(), null, future.plusDays(1), future.plusDays(1).plusHours(4)));
        completed.setStatus(ShiftStatus.COMPLETED);
        shiftRepo.save(completed);

        // Should survive: agencyB's OPEN shift for the same patternId (different agency)
        Shift otherAgency = shiftRepo.save(new Shift(agencyB.getId(), patternId, clientB.getId(), null,
            stB.getId(), null, future, future.plusHours(4)));

        shiftRepo.deleteUnstartedFutureShifts(
            patternId, agencyA.getId(), LocalDateTime.now(),
            List.of(ShiftStatus.OPEN, ShiftStatus.ASSIGNED)
        );

        assertThat(shiftRepo.findById(toDelete.getId())).isEmpty();
        assertThat(shiftRepo.findById(completed.getId())).isPresent();
        assertThat(shiftRepo.findById(otherAgency.getId())).isPresent();
    }
}
```

- [ ] **Step 2: Run to verify it fails**

```bash
cd backend && ./mvnw test -Dtest=ShiftDomainIT 2>&1 | tail -10
```
Expected: `COMPILATION ERROR` — `Shift` not found.

- [ ] **Step 3: Write Shift entity and ShiftRepository**

`backend/src/main/java/com/hcare/domain/Shift.java`:
```java
package com.hcare.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "shifts")
@Filter(name = "agencyFilter", condition = "agency_id = :agencyId")
public class Shift {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    @Column(name = "source_pattern_id")
    private UUID sourcePatternId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "caregiver_id")
    private UUID caregiverId;

    @Column(name = "service_type_id", nullable = false)
    private UUID serviceTypeId;

    @Column(name = "authorization_id")
    private UUID authorizationId;

    @Column(name = "scheduled_start", nullable = false)
    private LocalDateTime scheduledStart;

    @Column(name = "scheduled_end", nullable = false)
    private LocalDateTime scheduledEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ShiftStatus status;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected Shift() {}

    /**
     * Status is set automatically: OPEN when caregiverId is null, ASSIGNED otherwise.
     * sourcePatternId is null for ad-hoc shifts not generated from a RecurrencePattern.
     */
    public Shift(UUID agencyId, UUID sourcePatternId, UUID clientId, UUID caregiverId,
                 UUID serviceTypeId, UUID authorizationId,
                 LocalDateTime scheduledStart, LocalDateTime scheduledEnd) {
        this.agencyId = agencyId;
        this.sourcePatternId = sourcePatternId;
        this.clientId = clientId;
        this.caregiverId = caregiverId;
        this.serviceTypeId = serviceTypeId;
        this.authorizationId = authorizationId;
        this.scheduledStart = scheduledStart;
        this.scheduledEnd = scheduledEnd;
        this.status = caregiverId != null ? ShiftStatus.ASSIGNED : ShiftStatus.OPEN;
    }

    public void setStatus(ShiftStatus status) { this.status = status; }
    public void setCaregiverId(UUID caregiverId) { this.caregiverId = caregiverId; }
    public void setNotes(String notes) { this.notes = notes; }

    public UUID getId() { return id; }
    public UUID getAgencyId() { return agencyId; }
    public UUID getSourcePatternId() { return sourcePatternId; }
    public UUID getClientId() { return clientId; }
    public UUID getCaregiverId() { return caregiverId; }
    public UUID getServiceTypeId() { return serviceTypeId; }
    public UUID getAuthorizationId() { return authorizationId; }
    public LocalDateTime getScheduledStart() { return scheduledStart; }
    public LocalDateTime getScheduledEnd() { return scheduledEnd; }
    public ShiftStatus getStatus() { return status; }
    public String getNotes() { return notes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
```

`backend/src/main/java/com/hcare/domain/ShiftRepository.java`:
```java
package com.hcare.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ShiftRepository extends JpaRepository<Shift, UUID> {

    List<Shift> findByClientIdAndScheduledStartBetween(UUID clientId,
                                                        LocalDateTime start,
                                                        LocalDateTime end);

    List<Shift> findByCaregiverIdAndScheduledStartBetween(UUID caregiverId,
                                                           LocalDateTime start,
                                                           LocalDateTime end);

    /**
     * Bulk-deletes future unstarted shifts for a pattern (OPEN or ASSIGNED, scheduledStart > cutoff).
     * Explicitly includes agencyId in the WHERE clause — Hibernate @Filter does not apply to
     * bulk JPQL DELETE statements.
     */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM Shift s WHERE s.sourcePatternId = :patternId " +
           "AND s.agencyId = :agencyId " +
           "AND s.scheduledStart > :cutoff " +
           "AND s.status IN :statuses")
    void deleteUnstartedFutureShifts(@Param("patternId") UUID patternId,
                                      @Param("agencyId") UUID agencyId,
                                      @Param("cutoff") LocalDateTime cutoff,
                                      @Param("statuses") Collection<ShiftStatus> statuses);
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd backend && ./mvnw test -Dtest=ShiftDomainIT 2>&1 | tail -5
```
Expected: `BUILD SUCCESS` — 6 tests passing.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/hcare/domain/Shift.java \
        backend/src/main/java/com/hcare/domain/ShiftRepository.java \
        backend/src/test/java/com/hcare/domain/ShiftDomainIT.java
git commit -m "feat: Shift entity — status OPEN/ASSIGNED on construction, agencyFilter, bulk delete JPQL query"
```

---

### Task 4: EvvRecord Entity + Repository + IT

**Files:**
- Create: `backend/src/test/java/com/hcare/domain/EvvRecordDomainIT.java`
- Create: `backend/src/main/java/com/hcare/domain/EvvRecord.java`
- Create: `backend/src/main/java/com/hcare/domain/EvvRecordRepository.java`

- [ ] **Step 1: Write the failing test**

`backend/src/test/java/com/hcare/domain/EvvRecordDomainIT.java`:
```java
package com.hcare.domain;

import com.hcare.AbstractIntegrationTest;
import com.hcare.evv.VerificationMethod;
import com.hcare.multitenancy.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class EvvRecordDomainIT extends AbstractIntegrationTest {

    @Autowired AgencyRepository agencyRepo;
    @Autowired ClientRepository clientRepo;
    @Autowired ServiceTypeRepository serviceTypeRepo;
    @Autowired ShiftRepository shiftRepo;
    @Autowired EvvRecordRepository evvRecordRepo;
    @Autowired TransactionTemplate transactionTemplate;

    private Shift createShift(Agency agency, Client client, ServiceType st) {
        LocalDateTime start = LocalDate.of(2026, 4, 20).atTime(9, 0);
        return shiftRepo.save(new Shift(
            agency.getId(), null, client.getId(), null,
            st.getId(), null, start, start.plusHours(4)
        ));
    }

    @Test
    void evvRecord_can_be_saved_as_child_of_shift() {
        Agency agency = agencyRepo.save(new Agency("EVV Save Agency", "TX"));
        Client client = clientRepo.save(new Client(agency.getId(), "EVV", "Client", LocalDate.of(1958, 2, 14)));
        ServiceType st = serviceTypeRepo.save(new ServiceType(agency.getId(), "PCS", "PCS-E1", true, "[]"));
        Shift shift = createShift(agency, client, st);

        EvvRecord record = new EvvRecord(shift.getId(), agency.getId(), VerificationMethod.GPS);
        record.setLocationLat(new BigDecimal("30.2672"));
        record.setLocationLon(new BigDecimal("-97.7431"));
        record.setTimeIn(LocalDateTime.of(2026, 4, 20, 9, 5));
        evvRecordRepo.save(record);

        EvvRecord loaded = evvRecordRepo.findByShiftId(shift.getId()).orElseThrow();
        assertThat(loaded.getShiftId()).isEqualTo(shift.getId());
        assertThat(loaded.getAgencyId()).isEqualTo(agency.getId());
        assertThat(loaded.getVerificationMethod()).isEqualTo(VerificationMethod.GPS);
        assertThat(loaded.getLocationLat()).isEqualByComparingTo(new BigDecimal("30.2672"));
        assertThat(loaded.getTimeIn()).isEqualTo(LocalDateTime.of(2026, 4, 20, 9, 5));
        assertThat(loaded.getTimeOut()).isNull();
        assertThat(loaded.isCoResident()).isFalse();
        assertThat(loaded.isCapturedOffline()).isFalse();
        assertThat(loaded.getStateFields()).isEqualTo("{}");
    }

    @Test
    void evvRecord_unique_constraint_prevents_second_record_for_same_shift() {
        Agency agency = agencyRepo.save(new Agency("EVV Unique Agency", "TX"));
        Client client = clientRepo.save(new Client(agency.getId(), "Unique", "EVV", LocalDate.of(1965, 5, 5)));
        ServiceType st = serviceTypeRepo.save(new ServiceType(agency.getId(), "PCS", "PCS-EU", true, "[]"));
        Shift shift = createShift(agency, client, st);

        evvRecordRepo.save(new EvvRecord(shift.getId(), agency.getId(), VerificationMethod.GPS));

        assertThatThrownBy(() ->
            evvRecordRepo.saveAndFlush(new EvvRecord(shift.getId(), agency.getId(), VerificationMethod.MANUAL))
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void evvRecord_offline_fields_are_stored_correctly() {
        Agency agency = agencyRepo.save(new Agency("EVV Offline Agency", "TX"));
        Client client = clientRepo.save(new Client(agency.getId(), "Offline", "Test", LocalDate.of(1972, 8, 22)));
        ServiceType st = serviceTypeRepo.save(new ServiceType(agency.getId(), "PCS", "PCS-EO", true, "[]"));
        Shift shift = createShift(agency, client, st);

        EvvRecord record = new EvvRecord(shift.getId(), agency.getId(), VerificationMethod.GPS);
        record.setCapturedOffline(true);
        record.setDeviceCapturedAt(LocalDateTime.of(2026, 4, 20, 9, 2));
        evvRecordRepo.save(record);

        EvvRecord loaded = evvRecordRepo.findById(record.getId()).orElseThrow();
        assertThat(loaded.isCapturedOffline()).isTrue();
        assertThat(loaded.getDeviceCapturedAt()).isEqualTo(LocalDateTime.of(2026, 4, 20, 9, 2));
    }

    @Test
    void evvRecord_agencyFilter_excludes_other_agency_records() {
        Agency agencyA = agencyRepo.save(new Agency("EVV Agency A", "TX"));
        Agency agencyB = agencyRepo.save(new Agency("EVV Agency B", "CA"));
        Client clientA = clientRepo.save(new Client(agencyA.getId(), "A", "EVV", LocalDate.of(1960, 1, 1)));
        Client clientB = clientRepo.save(new Client(agencyB.getId(), "B", "EVV", LocalDate.of(1960, 1, 1)));
        ServiceType stA = serviceTypeRepo.save(new ServiceType(agencyA.getId(), "PCS", "PCS-EA", true, "[]"));
        ServiceType stB = serviceTypeRepo.save(new ServiceType(agencyB.getId(), "PCS", "PCS-EB", true, "[]"));

        LocalDateTime start = LocalDate.of(2026, 4, 21).atTime(9, 0);
        Shift shiftA = shiftRepo.save(new Shift(agencyA.getId(), null, clientA.getId(), null, stA.getId(), null, start, start.plusHours(4)));
        Shift shiftB = shiftRepo.save(new Shift(agencyB.getId(), null, clientB.getId(), null, stB.getId(), null, start, start.plusHours(4)));

        EvvRecord recordA = evvRecordRepo.save(new EvvRecord(shiftA.getId(), agencyA.getId(), VerificationMethod.GPS));
        evvRecordRepo.save(new EvvRecord(shiftB.getId(), agencyB.getId(), VerificationMethod.GPS));

        TenantContext.set(agencyA.getId());
        List<EvvRecord> result;
        try {
            result = transactionTemplate.execute(status -> evvRecordRepo.findAll());
        } finally {
            TenantContext.clear();
        }

        assertThat(result).isNotNull();
        assertThat(result.stream().map(EvvRecord::getId).toList()).contains(recordA.getId());
        assertThat(result).allMatch(r -> r.getAgencyId().equals(agencyA.getId()));
    }
}
```

- [ ] **Step 2: Run to verify it fails**

```bash
cd backend && ./mvnw test -Dtest=EvvRecordDomainIT 2>&1 | tail -10
```
Expected: `COMPILATION ERROR` — `EvvRecord` not found.

- [ ] **Step 3: Write EvvRecord entity and EvvRecordRepository**

`backend/src/main/java/com/hcare/domain/EvvRecord.java`:
```java
package com.hcare.domain;

import com.hcare.evv.VerificationMethod;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Raw captured EVV data for a completed or in-progress visit.
 * Compliance status (GREEN/YELLOW/RED/EXEMPT/PORTAL_SUBMIT/GREY) is computed on read by
 * the Core API EVV compliance module (Plan 4) — it is never stored here.
 *
 * Federal elements:
 *   1 — serviceType: derivable from Shift.serviceTypeId
 *   2 — clientMedicaidId: stored here (not guaranteed set on every client)
 *   3 — dateOfService: derivable from Shift.scheduledStart
 *   4 — GPS location: locationLat / locationLon
 *   5 — caregiverId: derivable from Shift.caregiverId
 *   6 — timeIn / timeOut
 */
@Entity
@Table(name = "evv_records")
@Filter(name = "agencyFilter", condition = "agency_id = :agencyId")
public class EvvRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "shift_id", nullable = false, unique = true)
    private UUID shiftId;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    @Column(name = "client_medicaid_id", length = 50)
    private String clientMedicaidId;

    @Column(name = "location_lat", precision = 10, scale = 7)
    private BigDecimal locationLat;

    @Column(name = "location_lon", precision = 10, scale = 7)
    private BigDecimal locationLon;

    @Column(name = "time_in")
    private LocalDateTime timeIn;

    @Column(name = "time_out")
    private LocalDateTime timeOut;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_method", nullable = false, length = 30)
    private VerificationMethod verificationMethod;

    // True when caregiver is a live-in co-resident — suppresses EVV requirement in most states
    @Column(name = "co_resident", nullable = false)
    private boolean coResident = false;

    // State-specific extra fields as JSON e.g. {"taskDocumentation": [...]} for Missouri
    @Column(name = "state_fields", nullable = false, columnDefinition = "TEXT")
    private String stateFields = "{}";

    // True when visit was captured offline. deviceCapturedAt is authoritative for compliance
    // timestamp; server receipt time is never used as the EVV timestamp for offline visits.
    @Column(name = "captured_offline", nullable = false)
    private boolean capturedOffline = false;

    @Column(name = "device_captured_at")
    private LocalDateTime deviceCapturedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected EvvRecord() {}

    public EvvRecord(UUID shiftId, UUID agencyId, VerificationMethod verificationMethod) {
        this.shiftId = shiftId;
        this.agencyId = agencyId;
        this.verificationMethod = verificationMethod;
    }

    public void setClientMedicaidId(String clientMedicaidId) { this.clientMedicaidId = clientMedicaidId; }
    public void setLocationLat(BigDecimal locationLat) { this.locationLat = locationLat; }
    public void setLocationLon(BigDecimal locationLon) { this.locationLon = locationLon; }
    public void setTimeIn(LocalDateTime timeIn) { this.timeIn = timeIn; }
    public void setTimeOut(LocalDateTime timeOut) { this.timeOut = timeOut; }
    public void setCoResident(boolean coResident) { this.coResident = coResident; }
    public void setStateFields(String stateFields) { this.stateFields = stateFields; }
    public void setCapturedOffline(boolean capturedOffline) { this.capturedOffline = capturedOffline; }
    public void setDeviceCapturedAt(LocalDateTime deviceCapturedAt) { this.deviceCapturedAt = deviceCapturedAt; }

    public UUID getId() { return id; }
    public UUID getShiftId() { return shiftId; }
    public UUID getAgencyId() { return agencyId; }
    public String getClientMedicaidId() { return clientMedicaidId; }
    public BigDecimal getLocationLat() { return locationLat; }
    public BigDecimal getLocationLon() { return locationLon; }
    public LocalDateTime getTimeIn() { return timeIn; }
    public LocalDateTime getTimeOut() { return timeOut; }
    public VerificationMethod getVerificationMethod() { return verificationMethod; }
    public boolean isCoResident() { return coResident; }
    public String getStateFields() { return stateFields; }
    public boolean isCapturedOffline() { return capturedOffline; }
    public LocalDateTime getDeviceCapturedAt() { return deviceCapturedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
```

`backend/src/main/java/com/hcare/domain/EvvRecordRepository.java`:
```java
package com.hcare.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface EvvRecordRepository extends JpaRepository<EvvRecord, UUID> {
    Optional<EvvRecord> findByShiftId(UUID shiftId);
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd backend && ./mvnw test -Dtest=EvvRecordDomainIT 2>&1 | tail -5
```
Expected: `BUILD SUCCESS` — 4 tests passing.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/hcare/domain/EvvRecord.java \
        backend/src/main/java/com/hcare/domain/EvvRecordRepository.java \
        backend/src/test/java/com/hcare/domain/EvvRecordDomainIT.java
git commit -m "feat: EvvRecord entity — raw captured data, UNIQUE per shift, no stored complianceStatus, agencyFilter"
```

---

### Task 5: ShiftOffer + AdlTaskCompletion Entities + IT

**Files:**
- Create: `backend/src/test/java/com/hcare/domain/ShiftSubEntitiesIT.java`
- Create: `backend/src/main/java/com/hcare/domain/ShiftOffer.java`
- Create: `backend/src/main/java/com/hcare/domain/ShiftOfferRepository.java`
- Create: `backend/src/main/java/com/hcare/domain/AdlTaskCompletion.java`
- Create: `backend/src/main/java/com/hcare/domain/AdlTaskCompletionRepository.java`

- [ ] **Step 1: Write the failing test**

`backend/src/test/java/com/hcare/domain/ShiftSubEntitiesIT.java`:
```java
package com.hcare.domain;

import com.hcare.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class ShiftSubEntitiesIT extends AbstractIntegrationTest {

    @Autowired AgencyRepository agencyRepo;
    @Autowired ClientRepository clientRepo;
    @Autowired CaregiverRepository caregiverRepo;
    @Autowired ServiceTypeRepository serviceTypeRepo;
    @Autowired CarePlanRepository carePlanRepo;
    @Autowired AdlTaskRepository adlTaskRepo;
    @Autowired ShiftRepository shiftRepo;
    @Autowired ShiftOfferRepository shiftOfferRepo;
    @Autowired AdlTaskCompletionRepository adlTaskCompletionRepo;

    // Mutable fields set per test via setupFixture — JUnit 5 creates a new instance per test
    private Agency agency;
    private Client client;
    private ServiceType st;
    private Shift shift;
    private Caregiver caregiver;

    private void setupFixture(String suffix) {
        agency = agencyRepo.save(new Agency("Sub Entity Agency " + suffix, "TX"));
        client = clientRepo.save(new Client(agency.getId(), "Sub", "Client", LocalDate.of(1960, 1, 1)));
        st = serviceTypeRepo.save(new ServiceType(agency.getId(), "PCS", "PCS-SUB-" + suffix, true, "[]"));
        caregiver = caregiverRepo.save(new Caregiver(agency.getId(), "Sub", "Cg", "subcg." + suffix + "@test.com"));
        LocalDateTime start = LocalDate.of(2026, 4, 22).atTime(9, 0);
        shift = shiftRepo.save(new Shift(agency.getId(), null, client.getId(), null,
            st.getId(), null, start, start.plusHours(4)));
    }

    @Test
    void shiftOffer_defaults_to_no_response() {
        setupFixture("OF1");

        ShiftOffer offer = shiftOfferRepo.save(new ShiftOffer(shift.getId(), caregiver.getId(), agency.getId()));

        ShiftOffer loaded = shiftOfferRepo.findById(offer.getId()).orElseThrow();
        assertThat(loaded.getShiftId()).isEqualTo(shift.getId());
        assertThat(loaded.getCaregiverId()).isEqualTo(caregiver.getId());
        assertThat(loaded.getResponse()).isEqualTo(ShiftOfferResponse.NO_RESPONSE);
        assertThat(loaded.getOfferedAt()).isNotNull();
        assertThat(loaded.getRespondedAt()).isNull();
    }

    @Test
    void shiftOffer_respond_sets_response_and_respondedAt() {
        setupFixture("OF2");

        ShiftOffer offer = new ShiftOffer(shift.getId(), caregiver.getId(), agency.getId());
        offer.respond(ShiftOfferResponse.ACCEPTED);
        shiftOfferRepo.save(offer);

        ShiftOffer loaded = shiftOfferRepo.findById(offer.getId()).orElseThrow();
        assertThat(loaded.getResponse()).isEqualTo(ShiftOfferResponse.ACCEPTED);
        assertThat(loaded.getRespondedAt()).isNotNull();
    }

    @Test
    void shiftOffer_unique_constraint_prevents_duplicate_offer() {
        setupFixture("OF3");

        shiftOfferRepo.save(new ShiftOffer(shift.getId(), caregiver.getId(), agency.getId()));

        assertThatThrownBy(() ->
            shiftOfferRepo.saveAndFlush(new ShiftOffer(shift.getId(), caregiver.getId(), agency.getId()))
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByShiftId_returns_all_offers_for_shift() {
        setupFixture("OF4");
        Caregiver cg2 = caregiverRepo.save(new Caregiver(agency.getId(), "Sub2", "Cg2", "subcg2.OF4@test.com"));

        shiftOfferRepo.save(new ShiftOffer(shift.getId(), caregiver.getId(), agency.getId()));
        shiftOfferRepo.save(new ShiftOffer(shift.getId(), cg2.getId(), agency.getId()));

        List<ShiftOffer> offers = shiftOfferRepo.findByShiftId(shift.getId());
        assertThat(offers).hasSize(2);
        assertThat(offers).allMatch(o -> o.getShiftId().equals(shift.getId()));
    }

    @Test
    void adlTaskCompletion_can_be_saved() {
        setupFixture("AT1");

        CarePlan plan = carePlanRepo.save(new CarePlan(client.getId(), agency.getId(), 1));
        AdlTask task = adlTaskRepo.save(new AdlTask(plan.getId(), agency.getId(), "Bathing", AssistanceLevel.MODERATE_ASSIST));

        AdlTaskCompletion completion = new AdlTaskCompletion(shift.getId(), task.getId(), agency.getId());
        completion.setCaregiverNotes("Completed with moderate assistance");
        adlTaskCompletionRepo.save(completion);

        AdlTaskCompletion loaded = adlTaskCompletionRepo.findById(completion.getId()).orElseThrow();
        assertThat(loaded.getShiftId()).isEqualTo(shift.getId());
        assertThat(loaded.getAdlTaskId()).isEqualTo(task.getId());
        assertThat(loaded.getCaregiverNotes()).isEqualTo("Completed with moderate assistance");
        assertThat(loaded.getCompletedAt()).isNotNull();
    }

    @Test
    void adlTaskCompletion_unique_constraint_prevents_duplicate() {
        setupFixture("AT2");

        CarePlan plan = carePlanRepo.save(new CarePlan(client.getId(), agency.getId(), 1));
        AdlTask task = adlTaskRepo.save(new AdlTask(plan.getId(), agency.getId(), "Dressing", AssistanceLevel.MINIMAL_ASSIST));

        adlTaskCompletionRepo.save(new AdlTaskCompletion(shift.getId(), task.getId(), agency.getId()));

        assertThatThrownBy(() ->
            adlTaskCompletionRepo.saveAndFlush(new AdlTaskCompletion(shift.getId(), task.getId(), agency.getId()))
        ).isInstanceOf(DataIntegrityViolationException.class);
    }
}
```

- [ ] **Step 2: Run to verify it fails**

```bash
cd backend && ./mvnw test -Dtest=ShiftSubEntitiesIT 2>&1 | tail -10
```
Expected: `COMPILATION ERROR` — `ShiftOffer` and `AdlTaskCompletion` not found.

- [ ] **Step 3: Write ShiftOffer + AdlTaskCompletion entities and repositories**

`backend/src/main/java/com/hcare/domain/ShiftOffer.java`:
```java
package com.hcare.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "shift_offers",
    uniqueConstraints = @UniqueConstraint(name = "uq_shift_offers", columnNames = {"shift_id", "caregiver_id"}))
@Filter(name = "agencyFilter", condition = "agency_id = :agencyId")
public class ShiftOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "shift_id", nullable = false)
    private UUID shiftId;

    @Column(name = "caregiver_id", nullable = false)
    private UUID caregiverId;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    @Column(name = "offered_at", nullable = false)
    private LocalDateTime offeredAt;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ShiftOfferResponse response = ShiftOfferResponse.NO_RESPONSE;

    protected ShiftOffer() {}

    public ShiftOffer(UUID shiftId, UUID caregiverId, UUID agencyId) {
        this.shiftId = shiftId;
        this.caregiverId = caregiverId;
        this.agencyId = agencyId;
        this.offeredAt = LocalDateTime.now();
    }

    public void respond(ShiftOfferResponse response) {
        this.response = response;
        this.respondedAt = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getShiftId() { return shiftId; }
    public UUID getCaregiverId() { return caregiverId; }
    public UUID getAgencyId() { return agencyId; }
    public LocalDateTime getOfferedAt() { return offeredAt; }
    public LocalDateTime getRespondedAt() { return respondedAt; }
    public ShiftOfferResponse getResponse() { return response; }
}
```

`backend/src/main/java/com/hcare/domain/ShiftOfferRepository.java`:
```java
package com.hcare.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ShiftOfferRepository extends JpaRepository<ShiftOffer, UUID> {
    List<ShiftOffer> findByShiftId(UUID shiftId);
}
```

`backend/src/main/java/com/hcare/domain/AdlTaskCompletion.java`:
```java
package com.hcare.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "adl_task_completions",
    uniqueConstraints = @UniqueConstraint(name = "uq_adl_task_completion", columnNames = {"shift_id", "adl_task_id"}))
@Filter(name = "agencyFilter", condition = "agency_id = :agencyId")
public class AdlTaskCompletion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "shift_id", nullable = false)
    private UUID shiftId;

    @Column(name = "adl_task_id", nullable = false)
    private UUID adlTaskId;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    @Column(name = "completed_at", nullable = false)
    private LocalDateTime completedAt;

    @Column(name = "caregiver_notes", columnDefinition = "TEXT")
    private String caregiverNotes;

    protected AdlTaskCompletion() {}

    public AdlTaskCompletion(UUID shiftId, UUID adlTaskId, UUID agencyId) {
        this.shiftId = shiftId;
        this.adlTaskId = adlTaskId;
        this.agencyId = agencyId;
        this.completedAt = LocalDateTime.now();
    }

    public void setCaregiverNotes(String caregiverNotes) { this.caregiverNotes = caregiverNotes; }

    public UUID getId() { return id; }
    public UUID getShiftId() { return shiftId; }
    public UUID getAdlTaskId() { return adlTaskId; }
    public UUID getAgencyId() { return agencyId; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public String getCaregiverNotes() { return caregiverNotes; }
}
```

`backend/src/main/java/com/hcare/domain/AdlTaskCompletionRepository.java`:
```java
package com.hcare.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface AdlTaskCompletionRepository extends JpaRepository<AdlTaskCompletion, UUID> {
    List<AdlTaskCompletion> findByShiftId(UUID shiftId);
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd backend && ./mvnw test -Dtest=ShiftSubEntitiesIT 2>&1 | tail -5
```
Expected: `BUILD SUCCESS` — 6 tests passing.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/hcare/domain/ShiftOffer.java \
        backend/src/main/java/com/hcare/domain/ShiftOfferRepository.java \
        backend/src/main/java/com/hcare/domain/AdlTaskCompletion.java \
        backend/src/main/java/com/hcare/domain/AdlTaskCompletionRepository.java \
        backend/src/test/java/com/hcare/domain/ShiftSubEntitiesIT.java
git commit -m "feat: ShiftOffer + AdlTaskCompletion entities — unique constraints, respond() helper"
```

---

### Task 6: IncidentReport + CommunicationMessage Entities + IT

**Files:**
- Create: `backend/src/test/java/com/hcare/domain/IncidentCommunicationIT.java`
- Create: `backend/src/main/java/com/hcare/domain/IncidentReport.java`
- Create: `backend/src/main/java/com/hcare/domain/IncidentReportRepository.java`
- Create: `backend/src/main/java/com/hcare/domain/CommunicationMessage.java`
- Create: `backend/src/main/java/com/hcare/domain/CommunicationMessageRepository.java`

- [ ] **Step 1: Write the failing test**

`backend/src/test/java/com/hcare/domain/IncidentCommunicationIT.java`:
```java
package com.hcare.domain;

import com.hcare.AbstractIntegrationTest;
import com.hcare.multitenancy.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class IncidentCommunicationIT extends AbstractIntegrationTest {

    @Autowired AgencyRepository agencyRepo;
    @Autowired ClientRepository clientRepo;
    @Autowired ServiceTypeRepository serviceTypeRepo;
    @Autowired ShiftRepository shiftRepo;
    @Autowired IncidentReportRepository incidentRepo;
    @Autowired CommunicationMessageRepository messageRepo;
    @Autowired TransactionTemplate transactionTemplate;

    // reported_by_id has no FK constraint (polymorphic: AGENCY_USER | CAREGIVER)
    private static final UUID REPORTER_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");

    @Test
    void incidentReport_can_be_saved_with_shift_reference() {
        Agency agency = agencyRepo.save(new Agency("Incident Shift Agency", "TX"));
        Client client = clientRepo.save(new Client(agency.getId(), "Inc", "Client", LocalDate.of(1960, 1, 1)));
        ServiceType st = serviceTypeRepo.save(new ServiceType(agency.getId(), "PCS", "PCS-I1", true, "[]"));
        LocalDateTime start = LocalDate.of(2026, 4, 23).atTime(10, 0);
        Shift shift = shiftRepo.save(new Shift(agency.getId(), null, client.getId(), null, st.getId(), null, start, start.plusHours(4)));

        IncidentReport report = new IncidentReport(
            agency.getId(), "AGENCY_USER", REPORTER_ID,
            "Client reported fall in bathroom", IncidentSeverity.HIGH,
            LocalDateTime.of(2026, 4, 23, 11, 30)
        );
        report.setShiftId(shift.getId());
        incidentRepo.save(report);

        IncidentReport loaded = incidentRepo.findById(report.getId()).orElseThrow();
        assertThat(loaded.getShiftId()).isEqualTo(shift.getId());
        assertThat(loaded.getAgencyId()).isEqualTo(agency.getId());
        assertThat(loaded.getSeverity()).isEqualTo(IncidentSeverity.HIGH);
        assertThat(loaded.getDescription()).isEqualTo("Client reported fall in bathroom");
        assertThat(loaded.getReportedByType()).isEqualTo("AGENCY_USER");
    }

    @Test
    void incidentReport_can_be_saved_without_shift_reference() {
        Agency agency = agencyRepo.save(new Agency("Incident No-Shift Agency", "TX"));

        IncidentReport report = new IncidentReport(
            agency.getId(), "AGENCY_USER", REPORTER_ID,
            "Staffing complaint from family", IncidentSeverity.LOW,
            LocalDateTime.of(2026, 4, 23, 14, 0)
        );
        incidentRepo.save(report);

        assertThat(incidentRepo.findById(report.getId()).orElseThrow().getShiftId()).isNull();
    }

    @Test
    void incidentReport_agencyFilter_excludes_other_agency_incidents() {
        Agency agencyA = agencyRepo.save(new Agency("Inc Agency A", "TX"));
        Agency agencyB = agencyRepo.save(new Agency("Inc Agency B", "CA"));

        IncidentReport reportA = incidentRepo.save(new IncidentReport(
            agencyA.getId(), "AGENCY_USER", REPORTER_ID,
            "Agency A incident", IncidentSeverity.MEDIUM, LocalDateTime.now()
        ));
        incidentRepo.save(new IncidentReport(
            agencyB.getId(), "AGENCY_USER", REPORTER_ID,
            "Agency B incident", IncidentSeverity.MEDIUM, LocalDateTime.now()
        ));

        TenantContext.set(agencyA.getId());
        List<IncidentReport> result;
        try {
            result = transactionTemplate.execute(status -> incidentRepo.findAll());
        } finally {
            TenantContext.clear();
        }

        assertThat(result).isNotNull();
        assertThat(result.stream().map(IncidentReport::getId).toList()).contains(reportA.getId());
        assertThat(result).allMatch(r -> r.getAgencyId().equals(agencyA.getId()));
    }

    @Test
    void communicationMessage_can_be_saved_and_retrieved() {
        Agency agency = agencyRepo.save(new Agency("Comm Agency", "TX"));
        UUID senderId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();

        CommunicationMessage msg = new CommunicationMessage(
            agency.getId(), "AGENCY_USER", senderId,
            "CAREGIVER", recipientId,
            "Please confirm availability for Thursday"
        );
        msg.setSubject("Schedule Confirmation");
        messageRepo.save(msg);

        CommunicationMessage loaded = messageRepo.findById(msg.getId()).orElseThrow();
        assertThat(loaded.getAgencyId()).isEqualTo(agency.getId());
        assertThat(loaded.getSenderType()).isEqualTo("AGENCY_USER");
        assertThat(loaded.getRecipientType()).isEqualTo("CAREGIVER");
        assertThat(loaded.getBody()).isEqualTo("Please confirm availability for Thursday");
        assertThat(loaded.getSubject()).isEqualTo("Schedule Confirmation");
        assertThat(loaded.getSentAt()).isNotNull();
    }

    @Test
    void communicationMessage_agencyFilter_excludes_other_agency_messages() {
        Agency agencyA = agencyRepo.save(new Agency("Comm Agency A", "TX"));
        Agency agencyB = agencyRepo.save(new Agency("Comm Agency B", "CA"));
        UUID uid = UUID.randomUUID();

        CommunicationMessage msgA = messageRepo.save(new CommunicationMessage(
            agencyA.getId(), "AGENCY_USER", uid, "CAREGIVER", uid, "Agency A message"
        ));
        messageRepo.save(new CommunicationMessage(
            agencyB.getId(), "AGENCY_USER", uid, "CAREGIVER", uid, "Agency B message"
        ));

        TenantContext.set(agencyA.getId());
        List<CommunicationMessage> result;
        try {
            result = transactionTemplate.execute(status -> messageRepo.findAll());
        } finally {
            TenantContext.clear();
        }

        assertThat(result).isNotNull();
        assertThat(result.stream().map(CommunicationMessage::getId).toList()).contains(msgA.getId());
        assertThat(result).allMatch(m -> m.getAgencyId().equals(agencyA.getId()));
    }
}
```

- [ ] **Step 2: Run to verify it fails**

```bash
cd backend && ./mvnw test -Dtest=IncidentCommunicationIT 2>&1 | tail -10
```
Expected: `COMPILATION ERROR` — `IncidentReport` and `CommunicationMessage` not found.

- [ ] **Step 3: Write IncidentReport + CommunicationMessage entities and repositories**

`backend/src/main/java/com/hcare/domain/IncidentReport.java`:
```java
package com.hcare.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "incident_reports")
@Filter(name = "agencyFilter", condition = "agency_id = :agencyId")
public class IncidentReport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    // Nullable: not all incidents are shift-related (e.g. general complaints)
    @Column(name = "shift_id")
    private UUID shiftId;

    // Polymorphic: AGENCY_USER or CAREGIVER — no FK constraint
    @Column(name = "reported_by_type", nullable = false, length = 30)
    private String reportedByType;

    @Column(name = "reported_by_id", nullable = false)
    private UUID reportedById;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IncidentSeverity severity;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected IncidentReport() {}

    public IncidentReport(UUID agencyId, String reportedByType, UUID reportedById,
                          String description, IncidentSeverity severity, LocalDateTime occurredAt) {
        this.agencyId = agencyId;
        this.reportedByType = reportedByType;
        this.reportedById = reportedById;
        this.description = description;
        this.severity = severity;
        this.occurredAt = occurredAt;
    }

    public void setShiftId(UUID shiftId) { this.shiftId = shiftId; }

    public UUID getId() { return id; }
    public UUID getAgencyId() { return agencyId; }
    public UUID getShiftId() { return shiftId; }
    public String getReportedByType() { return reportedByType; }
    public UUID getReportedById() { return reportedById; }
    public String getDescription() { return description; }
    public IncidentSeverity getSeverity() { return severity; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
```

`backend/src/main/java/com/hcare/domain/IncidentReportRepository.java`:
```java
package com.hcare.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface IncidentReportRepository extends JpaRepository<IncidentReport, UUID> {}
```

`backend/src/main/java/com/hcare/domain/CommunicationMessage.java`:
```java
package com.hcare.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "communication_messages")
@Filter(name = "agencyFilter", condition = "agency_id = :agencyId")
public class CommunicationMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    // Polymorphic participant types: AGENCY_USER | CAREGIVER | FAMILY_PORTAL_USER — no FK constraint
    @Column(name = "sender_type", nullable = false, length = 30)
    private String senderType;

    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    @Column(name = "recipient_type", nullable = false, length = 30)
    private String recipientType;

    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;

    @Column(length = 255)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    // sentAt is both the user-visible send time and the row creation time —
    // messages are immutable so there is no useful distinction.
    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt = LocalDateTime.now();

    protected CommunicationMessage() {}

    public CommunicationMessage(UUID agencyId, String senderType, UUID senderId,
                                 String recipientType, UUID recipientId, String body) {
        this.agencyId = agencyId;
        this.senderType = senderType;
        this.senderId = senderId;
        this.recipientType = recipientType;
        this.recipientId = recipientId;
        this.body = body;
    }

    public void setSubject(String subject) { this.subject = subject; }

    public UUID getId() { return id; }
    public UUID getAgencyId() { return agencyId; }
    public String getSenderType() { return senderType; }
    public UUID getSenderId() { return senderId; }
    public String getRecipientType() { return recipientType; }
    public UUID getRecipientId() { return recipientId; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public LocalDateTime getSentAt() { return sentAt; }
}
```

`backend/src/main/java/com/hcare/domain/CommunicationMessageRepository.java`:
```java
package com.hcare.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface CommunicationMessageRepository extends JpaRepository<CommunicationMessage, UUID> {}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd backend && ./mvnw test -Dtest=IncidentCommunicationIT 2>&1 | tail -5
```
Expected: `BUILD SUCCESS` — 5 tests passing.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/hcare/domain/IncidentReport.java \
        backend/src/main/java/com/hcare/domain/IncidentReportRepository.java \
        backend/src/main/java/com/hcare/domain/CommunicationMessage.java \
        backend/src/main/java/com/hcare/domain/CommunicationMessageRepository.java \
        backend/src/test/java/com/hcare/domain/IncidentCommunicationIT.java
git commit -m "feat: IncidentReport + CommunicationMessage entities — agency-scoped, polymorphic reporter/sender"
```

---

### Task 7: ShiftGenerationService Unit Tests + Implementation

**Files:**
- Create: `backend/src/test/java/com/hcare/scheduling/ShiftGenerationServiceTest.java`
- Create: `backend/src/main/java/com/hcare/scheduling/ShiftGenerationService.java`
- Create: `backend/src/main/java/com/hcare/scheduling/LocalShiftGenerationService.java`

- [ ] **Step 1: Write the failing unit tests**

`backend/src/test/java/com/hcare/scheduling/ShiftGenerationServiceTest.java`:
```java
package com.hcare.scheduling;

import com.hcare.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShiftGenerationServiceTest {

    @Mock RecurrencePatternRepository patternRepo;
    @Mock ShiftRepository shiftRepo;
    @InjectMocks LocalShiftGenerationService service;

    private RecurrencePattern buildPattern(String daysOfWeek, LocalDate generatedThrough) {
        RecurrencePattern pattern = new RecurrencePattern(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            LocalTime.of(9, 0), 240, daysOfWeek,
            LocalDate.now().minusDays(1)
        );
        pattern.setGeneratedThrough(generatedThrough);
        return pattern;
    }

    @SuppressWarnings("unchecked")
    @Test
    void generateForPattern_creates_shifts_only_on_matching_days() {
        RecurrencePattern pattern = buildPattern("[\"MONDAY\",\"WEDNESDAY\",\"FRIDAY\"]",
            LocalDate.now().minusDays(1));

        service.generateForPattern(pattern);

        ArgumentCaptor<List<Shift>> captor = ArgumentCaptor.forClass(List.class);
        verify(shiftRepo).saveAll(captor.capture());
        List<Shift> saved = captor.getValue();

        assertThat(saved).isNotEmpty();
        assertThat(saved).allMatch(s ->
            s.getScheduledStart().getDayOfWeek() == DayOfWeek.MONDAY ||
            s.getScheduledStart().getDayOfWeek() == DayOfWeek.WEDNESDAY ||
            s.getScheduledStart().getDayOfWeek() == DayOfWeek.FRIDAY
        );
        assertThat(saved).noneMatch(s ->
            s.getScheduledStart().getDayOfWeek() == DayOfWeek.TUESDAY ||
            s.getScheduledStart().getDayOfWeek() == DayOfWeek.THURSDAY ||
            s.getScheduledStart().getDayOfWeek() == DayOfWeek.SATURDAY ||
            s.getScheduledStart().getDayOfWeek() == DayOfWeek.SUNDAY
        );
    }

    @SuppressWarnings("unchecked")
    @Test
    void generateForPattern_shift_times_match_pattern_time_and_duration() {
        RecurrencePattern pattern = buildPattern("[\"TUESDAY\"]", LocalDate.now().minusDays(1));

        service.generateForPattern(pattern);

        ArgumentCaptor<List<Shift>> captor = ArgumentCaptor.forClass(List.class);
        verify(shiftRepo).saveAll(captor.capture());
        captor.getValue().forEach(s -> {
            assertThat(s.getScheduledStart().toLocalTime()).isEqualTo(LocalTime.of(9, 0));
            assertThat(s.getScheduledEnd()).isEqualTo(s.getScheduledStart().plusMinutes(240));
        });
    }

    @SuppressWarnings("unchecked")
    @Test
    void generateForPattern_respects_pattern_endDate() {
        LocalDate endDate = LocalDate.now().plusWeeks(2);
        RecurrencePattern pattern = buildPattern("[\"MONDAY\"]", LocalDate.now().minusDays(1));
        pattern.setEndDate(endDate);

        service.generateForPattern(pattern);

        ArgumentCaptor<List<Shift>> captor = ArgumentCaptor.forClass(List.class);
        verify(shiftRepo).saveAll(captor.capture());
        captor.getValue().forEach(s ->
            assertThat(s.getScheduledStart().toLocalDate()).isBeforeOrEqualTo(endDate)
        );
        assertThat(pattern.getGeneratedThrough()).isEqualTo(endDate);
    }

    @Test
    void generateForPattern_does_nothing_for_inactive_pattern() {
        RecurrencePattern pattern = buildPattern("[\"MONDAY\"]", LocalDate.now().minusDays(1));
        pattern.setActive(false);

        service.generateForPattern(pattern);

        verifyNoInteractions(shiftRepo, patternRepo);
    }

    @Test
    void generateForPattern_does_nothing_when_already_at_horizon() {
        // generatedThrough = now + 8 weeks → start would be after end → early return
        RecurrencePattern pattern = buildPattern("[\"MONDAY\"]", LocalDate.now().plusWeeks(8));

        service.generateForPattern(pattern);

        verify(shiftRepo, never()).saveAll(any());
        verify(patternRepo, never()).save(any());
    }

    @Test
    void generateForPattern_updates_generatedThrough_and_saves_pattern() {
        RecurrencePattern pattern = buildPattern("[\"TUESDAY\"]", LocalDate.now().minusDays(1));

        service.generateForPattern(pattern);

        assertThat(pattern.getGeneratedThrough()).isEqualTo(LocalDate.now().plusWeeks(8));
        verify(patternRepo).save(pattern);
    }

    @Test
    void regenerateAfterEdit_deletes_future_unstarted_shifts_then_regenerates() {
        RecurrencePattern pattern = buildPattern("[\"WEDNESDAY\"]", LocalDate.now().minusDays(1));

        service.regenerateAfterEdit(pattern);

        verify(shiftRepo).deleteUnstartedFutureShifts(
            eq(pattern.getId()),
            eq(pattern.getAgencyId()),
            any(LocalDateTime.class),
            eq(List.of(ShiftStatus.OPEN, ShiftStatus.ASSIGNED))
        );
        // generateForPattern is called internally: pattern saved + shifts generated
        verify(patternRepo).save(pattern);
    }

    @Test
    void regenerateAfterEdit_resets_generatedThrough_to_today_so_generation_starts_tomorrow() {
        // Ensures no stale past-time shift is created for today's matching day of week,
        // and no duplicate is created alongside an in-progress today's shift.
        RecurrencePattern pattern = buildPattern("[\"WEDNESDAY\"]", LocalDate.now().minusDays(1));

        service.regenerateAfterEdit(pattern);

        ArgumentCaptor<RecurrencePattern> captor = ArgumentCaptor.forClass(RecurrencePattern.class);
        verify(patternRepo).save(captor.capture());
        // generatedThrough must be >= today (generation starts from tomorrow at earliest)
        assertThat(captor.getValue().getGeneratedThrough())
            .isGreaterThanOrEqualTo(LocalDate.now());
    }

    @Test
    void parseDaysOfWeek_parses_multiple_days() {
        List<DayOfWeek> result = LocalShiftGenerationService.parseDaysOfWeek(
            "[\"MONDAY\",\"WEDNESDAY\",\"FRIDAY\"]"
        );
        assertThat(result).containsExactly(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);
    }

    @Test
    void parseDaysOfWeek_parses_single_day() {
        List<DayOfWeek> result = LocalShiftGenerationService.parseDaysOfWeek("[\"SATURDAY\"]");
        assertThat(result).containsExactly(DayOfWeek.SATURDAY);
    }
}
```

- [ ] **Step 2: Run to verify it fails**

```bash
cd backend && ./mvnw test -Dtest=ShiftGenerationServiceTest 2>&1 | tail -10
```
Expected: `COMPILATION ERROR` — `ShiftGenerationService` and `LocalShiftGenerationService` not found.

- [ ] **Step 3: Write ShiftGenerationService interface and LocalShiftGenerationService**

`backend/src/main/java/com/hcare/scheduling/ShiftGenerationService.java`:
```java
package com.hcare.scheduling;

import com.hcare.domain.RecurrencePattern;

public interface ShiftGenerationService {

    /**
     * Generates shifts for the pattern from its current generatedThrough frontier through
     * LocalDate.now() plus 8 weeks. Safe to call multiple times — only advances the frontier.
     * No-ops silently for inactive patterns (isActive = false).
     */
    void generateForPattern(RecurrencePattern pattern);

    /**
     * Deletes future unstarted shifts (OPEN or ASSIGNED, scheduledStart after now) for the
     * given pattern, resets generatedThrough to today, then calls generateForPattern.
     * Generation resumes from tomorrow — preserving any in-progress visit on today's date.
     * Called when a pattern's scheduling fields are edited.
     * Note: no-ops silently on inactive patterns (delegates to generateForPattern).
     */
    void regenerateAfterEdit(RecurrencePattern pattern);
}
```

`backend/src/main/java/com/hcare/scheduling/LocalShiftGenerationService.java`:
```java
package com.hcare.scheduling;

import com.hcare.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class LocalShiftGenerationService implements ShiftGenerationService {

    private static final Logger log = LoggerFactory.getLogger(LocalShiftGenerationService.class);

    static final int HORIZON_WEEKS = 8;

    private final RecurrencePatternRepository patternRepository;
    private final ShiftRepository shiftRepository;

    public LocalShiftGenerationService(RecurrencePatternRepository patternRepository,
                                        ShiftRepository shiftRepository) {
        this.patternRepository = patternRepository;
        this.shiftRepository = shiftRepository;
    }

    @Override
    @Transactional
    public void generateForPattern(RecurrencePattern pattern) {
        if (!pattern.isActive()) return;

        LocalDate start = pattern.getGeneratedThrough().plusDays(1);
        LocalDate horizonEnd = LocalDate.now().plusWeeks(HORIZON_WEEKS);
        LocalDate end = (pattern.getEndDate() != null && pattern.getEndDate().isBefore(horizonEnd))
            ? pattern.getEndDate()
            : horizonEnd;

        if (start.isAfter(end)) return;

        List<DayOfWeek> daysOfWeek = parseDaysOfWeek(pattern.getDaysOfWeek());
        List<Shift> shifts = new ArrayList<>();

        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            if (daysOfWeek.contains(date.getDayOfWeek())) {
                LocalDateTime scheduledStart = date.atTime(pattern.getScheduledStartTime());
                LocalDateTime scheduledEnd = scheduledStart.plusMinutes(pattern.getScheduledDurationMinutes());
                shifts.add(new Shift(
                    pattern.getAgencyId(),
                    pattern.getId(),
                    pattern.getClientId(),
                    pattern.getCaregiverId(),
                    pattern.getServiceTypeId(),
                    pattern.getAuthorizationId(),
                    scheduledStart,
                    scheduledEnd
                ));
            }
        }

        if (!shifts.isEmpty()) {
            shiftRepository.saveAll(shifts);
        }
        pattern.setGeneratedThrough(end);
        patternRepository.save(pattern);
        log.debug("Generated {} shifts for pattern {} (agency {}), generatedThrough advanced to {}",
            shifts.size(), pattern.getId(), pattern.getAgencyId(), end);
    }

    @Override
    @Transactional
    public void regenerateAfterEdit(RecurrencePattern pattern) {
        shiftRepository.deleteUnstartedFutureShifts(
            pattern.getId(),
            pattern.getAgencyId(),
            LocalDateTime.now(),
            List.of(ShiftStatus.OPEN, ShiftStatus.ASSIGNED)
        );
        // Reset to today so generateForPattern starts from tomorrow — avoids creating
        // stale past-time shifts or a duplicate alongside an in-progress today's visit.
        pattern.setGeneratedThrough(LocalDate.now());
        generateForPattern(pattern);
    }

    /**
     * Parses a JSON TEXT array of DayOfWeek names e.g. ["MONDAY","WEDNESDAY","FRIDAY"].
     * Package-private for unit testing without reflection.
     */
    static List<DayOfWeek> parseDaysOfWeek(String json) {
        try {
            return Arrays.stream(json.replaceAll("[\\[\\]\"\\s]", "").split(","))
                .filter(s -> !s.isEmpty())
                .map(DayOfWeek::valueOf)
                .collect(Collectors.toList());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Invalid daysOfWeek JSON — expected array of DayOfWeek names, got: " + json, e);
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd backend && ./mvnw test -Dtest=ShiftGenerationServiceTest 2>&1 | tail -5
```
Expected: `BUILD SUCCESS` — 10 tests passing.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/hcare/scheduling/ShiftGenerationService.java \
        backend/src/main/java/com/hcare/scheduling/LocalShiftGenerationService.java \
        backend/src/test/java/com/hcare/scheduling/ShiftGenerationServiceTest.java
git commit -m "feat: ShiftGenerationService — rolling 8-week horizon, regenerateAfterEdit, parseDaysOfWeek, TDD unit tested"
```

---

### Task 8: ShiftGenerationScheduler + Integration Test

**Files:**
- Create: `backend/src/test/java/com/hcare/scheduling/ShiftGenerationServiceIT.java`
- Create: `backend/src/main/java/com/hcare/scheduling/ShiftGenerationScheduler.java`

- [ ] **Step 1: Write the failing integration test**

`backend/src/test/java/com/hcare/scheduling/ShiftGenerationServiceIT.java`:
```java
package com.hcare.scheduling;

import com.hcare.AbstractIntegrationTest;
import com.hcare.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class ShiftGenerationServiceIT extends AbstractIntegrationTest {

    @Autowired AgencyRepository agencyRepo;
    @Autowired ClientRepository clientRepo;
    @Autowired ServiceTypeRepository serviceTypeRepo;
    @Autowired RecurrencePatternRepository patternRepo;
    @Autowired ShiftRepository shiftRepo;
    @Autowired ShiftGenerationService shiftGenerationService;
    @Autowired ShiftGenerationScheduler shiftGenerationScheduler;
    @Autowired TransactionTemplate transactionTemplate;

    @Test
    void generateForPattern_creates_correct_shifts_in_database() {
        Agency agency = agencyRepo.save(new Agency("Gen IT Agency", "TX"));
        Client client = clientRepo.save(new Client(agency.getId(), "GenIT", "Client", LocalDate.of(1960, 1, 1)));
        ServiceType st = serviceTypeRepo.save(new ServiceType(agency.getId(), "PCS", "PCS-GIT", true, "[]"));

        LocalDate nextMonday = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        RecurrencePattern pattern = patternRepo.save(new RecurrencePattern(
            agency.getId(), client.getId(), st.getId(),
            LocalTime.of(9, 0), 240, "[\"MONDAY\",\"FRIDAY\"]", nextMonday
        ));

        shiftGenerationService.generateForPattern(pattern);

        LocalDate horizon = LocalDate.now().plusWeeks(8);
        List<Shift> shifts = shiftRepo.findByClientIdAndScheduledStartBetween(
            client.getId(), nextMonday.atStartOfDay(), horizon.plusDays(1).atStartOfDay()
        );

        assertThat(shifts).isNotEmpty();
        assertThat(shifts).allMatch(s ->
            s.getScheduledStart().getDayOfWeek() == DayOfWeek.MONDAY ||
            s.getScheduledStart().getDayOfWeek() == DayOfWeek.FRIDAY
        );
        assertThat(shifts).allMatch(s -> s.getSourcePatternId().equals(pattern.getId()));
        assertThat(shifts).allMatch(s -> s.getStatus() == ShiftStatus.OPEN);
        assertThat(shifts).allMatch(s -> s.getScheduledEnd().equals(s.getScheduledStart().plusMinutes(240)));
        assertThat(shifts).allMatch(s -> s.getScheduledStart().toLocalTime().equals(LocalTime.of(9, 0)));

        RecurrencePattern updated = patternRepo.findById(pattern.getId()).orElseThrow();
        assertThat(updated.getGeneratedThrough()).isEqualTo(horizon);
    }

    @Test
    void regenerateAfterEdit_deletes_future_open_shifts_and_regenerates() {
        Agency agency = agencyRepo.save(new Agency("Regen IT Agency", "TX"));
        Client client = clientRepo.save(new Client(agency.getId(), "RegenIT", "Client", LocalDate.of(1965, 4, 10)));
        ServiceType st = serviceTypeRepo.save(new ServiceType(agency.getId(), "PCS", "PCS-RIT", true, "[]"));

        LocalDate startDate = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.TUESDAY));
        RecurrencePattern pattern = patternRepo.save(new RecurrencePattern(
            agency.getId(), client.getId(), st.getId(),
            LocalTime.of(10, 0), 120, "[\"TUESDAY\"]", startDate
        ));

        // Initial generation
        shiftGenerationService.generateForPattern(pattern);
        int initialCount = shiftRepo.findByClientIdAndScheduledStartBetween(
            client.getId(), LocalDateTime.now(), LocalDate.now().plusWeeks(9).atStartOfDay()
        ).size();
        assertThat(initialCount).isGreaterThan(0);

        // Regenerate (simulates a pattern edit — deletes old shifts and re-creates)
        shiftGenerationService.regenerateAfterEdit(pattern);

        List<Shift> afterRegen = shiftRepo.findByClientIdAndScheduledStartBetween(
            client.getId(), LocalDateTime.now(), LocalDate.now().plusWeeks(9).atStartOfDay()
        );
        assertThat(afterRegen).isNotEmpty();
        assertThat(afterRegen).allMatch(s -> s.getSourcePatternId().equals(pattern.getId()));
        assertThat(afterRegen).allMatch(s -> s.getScheduledStart().getDayOfWeek() == DayOfWeek.TUESDAY);
    }

    @Test
    void scheduler_advanceGenerationFrontier_generates_shifts_for_patterns_behind_horizon() {
        Agency agency = agencyRepo.save(new Agency("Scheduler IT Agency", "TX"));
        Client client = clientRepo.save(new Client(agency.getId(), "SchedIT", "Client", LocalDate.of(1970, 7, 1)));
        ServiceType st = serviceTypeRepo.save(new ServiceType(agency.getId(), "PCS", "PCS-SCHED", true, "[]"));

        RecurrencePattern pattern = new RecurrencePattern(
            agency.getId(), client.getId(), st.getId(),
            LocalTime.of(8, 0), 180, "[\"WEDNESDAY\"]",
            LocalDate.now().minusDays(2)
        );
        // Set generatedThrough behind the horizon so the scheduler picks it up
        pattern.setGeneratedThrough(LocalDate.now().minusDays(1));
        patternRepo.save(pattern);

        shiftGenerationScheduler.advanceGenerationFrontier();

        List<Shift> shifts = shiftRepo.findByClientIdAndScheduledStartBetween(
            client.getId(),
            LocalDateTime.now(),
            LocalDate.now().plusWeeks(9).atStartOfDay()
        );
        assertThat(shifts).isNotEmpty();
        assertThat(shifts).allMatch(s -> s.getScheduledStart().getDayOfWeek() == DayOfWeek.WEDNESDAY);

        RecurrencePattern updated = patternRepo.findById(pattern.getId()).orElseThrow();
        assertThat(updated.getGeneratedThrough())
            .isGreaterThanOrEqualTo(LocalDate.now().plusWeeks(7));
    }
}
```

- [ ] **Step 2: Run to verify it fails**

```bash
cd backend && ./mvnw test -Dtest=ShiftGenerationServiceIT 2>&1 | tail -10
```
Expected: `COMPILATION ERROR` — `ShiftGenerationScheduler` not found.

- [ ] **Step 3: Write ShiftGenerationScheduler**

`backend/src/main/java/com/hcare/scheduling/ShiftGenerationScheduler.java`:
```java
package com.hcare.scheduling;

import com.hcare.domain.RecurrencePattern;
import com.hcare.domain.RecurrencePatternRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Nightly job that advances the shift-generation frontier for all active RecurrencePatterns
 * that have not yet been generated through the 8-week horizon.
 *
 * The @Scheduled cron is controlled by hcare.scheduling.shift-generation-cron, which defaults
 * to "0 0 2 * * *" (2 AM daily) and is set to "-" in application-test.yml to prevent the
 * cron from firing during integration tests. Tests invoke advanceGenerationFrontier() directly.
 */
@Component
public class ShiftGenerationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ShiftGenerationScheduler.class);

    private final RecurrencePatternRepository patternRepository;
    private final ShiftGenerationService shiftGenerationService;

    public ShiftGenerationScheduler(RecurrencePatternRepository patternRepository,
                                     ShiftGenerationService shiftGenerationService) {
        this.patternRepository = patternRepository;
        this.shiftGenerationService = shiftGenerationService;
    }

    @Scheduled(cron = "${hcare.scheduling.shift-generation-cron:0 0 2 * * *}")
    public void advanceGenerationFrontier() {
        LocalDate today = LocalDate.now();
        LocalDate horizon = today.plusWeeks(LocalShiftGenerationService.HORIZON_WEEKS);
        List<RecurrencePattern> patterns = patternRepository.findActivePatternsBehindHorizon(horizon, today);
        for (RecurrencePattern pattern : patterns) {
            try {
                shiftGenerationService.generateForPattern(pattern);
            } catch (Exception e) {
                // Log and continue — one failed pattern must not block all others.
                // ObjectOptimisticLockingFailureException is the expected concurrent-edit case;
                // the pattern will be retried on the next nightly run.
                log.error("Failed to generate shifts for pattern {} (agency {}): {}",
                    pattern.getId(), pattern.getAgencyId(), e.getMessage());
            }
        }
    }
}
```

- [ ] **Step 4: Run integration tests to verify they pass**

```bash
cd backend && ./mvnw test -Dtest=ShiftGenerationServiceIT 2>&1 | tail -5
```
Expected: `BUILD SUCCESS` — 3 tests passing.

- [ ] **Step 5: Run the full test suite to confirm nothing is broken**

```bash
cd backend && ./mvnw test 2>&1 | tail -10
```
Expected: `BUILD SUCCESS` — all tests pass.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/hcare/scheduling/ShiftGenerationScheduler.java \
        backend/src/test/java/com/hcare/scheduling/ShiftGenerationServiceIT.java
git commit -m "feat: ShiftGenerationScheduler — nightly 8-week frontier advance, cron disabled in test profile"
```

---

## Self-Review

### 1. Spec Coverage Check

| Spec requirement | Task |
|---|---|
| Shift entity with OPEN/ASSIGNED/IN_PROGRESS/COMPLETED/CANCELLED/MISSED | Task 3 |
| `Shift.sourcePatternId` links generated shifts to pattern; null = ad-hoc | Task 3 |
| RecurrencePattern `@Version` optimistic locking (concurrent save + nightly job race) | Task 2 |
| RecurrencePattern `generatedThrough` frontier, initialized to `startDate - 1 day` | Task 2 |
| Nightly job generates shifts on rolling 8-week horizon | Task 8 |
| Pattern edits: only future unstarted shifts (OPEN/ASSIGNED) affected; IN_PROGRESS/COMPLETED untouched | Task 7 (`regenerateAfterEdit`) |
| EVVRecord stores 6 federal elements (2 stored directly; 1/3/5 derivable from Shift) | Task 4 |
| EVVRecord.capturedOffline + deviceCapturedAt for offline sync | Task 4 |
| EVVRecord.stateFields (JSON TEXT) for state-specific extra elements | Task 4 |
| EVVRecord.coResident suppresses EVV for live-in caregiver | Task 4 |
| Compliance status NOT stored — computed on read in Plan 4 | ✓ No `complianceStatus` field on EvvRecord |
| ShiftOffer broadcast record (caregiver, offeredAt, respondedAt, response) | Task 5 |
| ADLTaskCompletion per-visit task check-off | Task 5 |
| IncidentReports entity (agency-scoped, nullable shiftId) | Task 6 |
| CommunicationMessages entity (agency-scoped, polymorphic sender/recipient) | Task 6 |
| VerificationMethod enum (GPS/TELEPHONY_LANDLINE/TELEPHONY_CELL/FIXED_DEVICE/FOB/BIOMETRIC/MANUAL) | Task 1 |
| `@Filter(agencyFilter)` on all new entities | Tasks 2–6 |
| `@EnableScheduling` + test profile disable | Task 1 |

All requirements covered. No gaps.

### 2. Placeholder Scan

No TBD, TODO, "implement later", or vague "add error handling" instructions found. All code blocks are complete.

### 3. Type Consistency Check

- `ShiftStatus` (Task 1) → used in `Shift.status` (Task 3) and `ShiftRepository.deleteUnstartedFutureShifts` parameter (Task 3) and service call `List.of(ShiftStatus.OPEN, ShiftStatus.ASSIGNED)` (Task 7) — consistent.
- `VerificationMethod` in `com.hcare.evv` (Task 1) → imported in `EvvRecord` (Task 4) — consistent.
- `ShiftOfferResponse` (Task 1) → `ShiftOffer.response` field (Task 5) and `offer.respond(ShiftOfferResponse.ACCEPTED)` in test — consistent.
- `IncidentSeverity` (Task 1) → `IncidentReport.severity` (Task 6) — consistent.
- `RecurrencePattern` (Task 2) → `Shift.sourcePatternId` UUID FK (Task 3), `generateForPattern(RecurrencePattern)` interface (Task 7) — consistent.
- `ShiftRepository.deleteUnstartedFutureShifts(UUID, UUID, LocalDateTime, Collection<ShiftStatus>)` (Task 3) → called in `LocalShiftGenerationService.regenerateAfterEdit` as `shiftRepository.deleteUnstartedFutureShifts(pattern.getId(), pattern.getAgencyId(), LocalDateTime.now(), List.of(ShiftStatus.OPEN, ShiftStatus.ASSIGNED))` (Task 7) — consistent.
- `LocalShiftGenerationService.HORIZON_WEEKS` is `static final int` → referenced in `ShiftGenerationScheduler` as `LocalShiftGenerationService.HORIZON_WEEKS` (Task 8) — consistent.
- `RecurrencePatternRepository.findActivePatternsBehindHorizon(LocalDate horizon, LocalDate today)` (Task 2) → called in `ShiftGenerationScheduler.advanceGenerationFrontier()` as `findActivePatternsBehindHorizon(horizon, today)` (Task 8) — consistent.
- `RecurrencePattern.active` field (Task 2) → JPQL `rp.active = true` in `findActivePatternsBehindHorizon` (Task 2) → `pattern.isActive()` / `pattern.setActive()` in service and tests — consistent.
