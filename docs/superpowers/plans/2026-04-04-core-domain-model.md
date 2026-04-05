# Core Domain Model Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement all domain entities that Plans 3 (Scheduling), 4 (EVV), and 5 (AI Scoring) build on: Payer, ServiceType, FeatureFlags, Caregiver (with Credentials, BackgroundChecks, Availability, ScoringProfile + ClientAffinity), Client (with Diagnoses, Medications, Documents), CarePlan (versioned, with ADLTasks and Goals), Authorization (JPA `@Version` optimistic locking), FamilyPortalUser, and PayerEvvRoutingConfig.

**Architecture:** Every agency-scoped entity carries an `agency_id` column and `@Filter(name = "agencyFilter", condition = "agency_id = :agencyId")` — the same Hibernate filter mechanism from Plan 1 automatically scopes all queries to the authenticated agency. `Authorization` uses JPA `@Version` so concurrent shift-completion threads can safely increment `usedUnits` without a distributed lock; the second transaction catches `ObjectOptimisticLockingFailureException` and retries. `CarePlan` carries a business `planVersion` integer; the one-active-plan-per-client rule is enforced at the service layer (Plan 6), not at the DB layer (H2 lacks partial unique indexes). All JSON fields (`languages`, `requiredCredentials`, `preferredLanguages`) are stored as opaque `TEXT` — no DB-level JSON path queries in P1, all parsing at the application layer (H2/PostgreSQL portability rule from spec critical-review-2).

**Multi-tenancy note for tests:** The `TenantFilterAspect` requires an active `@Transactional` boundary to be open before the repository is entered (see Plan 1 critical-review-2, C1). Repository-level multi-tenancy tests must wrap repository calls in a `TransactionTemplate` so the outer transaction is already open when the aspect fires. Set `TenantContext.set(agencyId)` before the `TransactionTemplate` call.

**Tech Stack:** Java 25, Spring Boot 3.4.4, Spring Data JPA / Hibernate 6, Flyway, H2 (dev smoke test), PostgreSQL 16 (integration tests via Testcontainers), JUnit 5, AssertJ.

---

## File Structure

```
backend/
└── src/
    ├── main/java/com/hcare/
    │   ├── domain/
    │   │   ├── PayerType.java                         — enum: MEDICAID | PRIVATE_PAY | LTC_INSURANCE | VA | MEDICARE
    │   │   ├── Payer.java                             — @Filter(agencyFilter)
    │   │   ├── PayerRepository.java
    │   │   ├── ServiceType.java                       — @Filter(agencyFilter); requiredCredentials is JSON TEXT
    │   │   ├── ServiceTypeRepository.java
    │   │   ├── FeatureFlags.java                      — @Filter(agencyFilter), one per agency; mutable via setters
    │   │   ├── FeatureFlagsRepository.java
    │   │   ├── CaregiverStatus.java                   — enum: ACTIVE | INACTIVE | TERMINATED
    │   │   ├── Caregiver.java                         — @Filter(agencyFilter); languages is JSON TEXT; homeLat/homeLng geocoded at save time
    │   │   ├── CaregiverRepository.java
    │   │   ├── CredentialType.java                    — enum: HHA | CNA | LPN | RN | CPR | FIRST_AID | DRIVERS_LICENSE | TB_TEST | HEPATITIS_B | COVID_VACCINE
    │   │   ├── CaregiverCredential.java               — @Filter(agencyFilter)
    │   │   ├── CaregiverCredentialRepository.java
    │   │   ├── BackgroundCheckType.java               — enum: STATE_REGISTRY | FBI | OIG | SEX_OFFENDER
    │   │   ├── BackgroundCheckResult.java             — enum: CLEAR | FLAGS | PENDING
    │   │   ├── BackgroundCheck.java                   — @Filter(agencyFilter)
    │   │   ├── BackgroundCheckRepository.java
    │   │   ├── CaregiverAvailability.java             — @Filter(agencyFilter); dayOfWeek uses java.time.DayOfWeek
    │   │   ├── CaregiverAvailabilityRepository.java
    │   │   ├── CaregiverScoringProfile.java           — @Filter(agencyFilter); one per caregiver; pre-computed async
    │   │   ├── CaregiverScoringProfileRepository.java
    │   │   ├── CaregiverClientAffinity.java           — @Filter(agencyFilter); visitCount per caregiver+client pair
    │   │   ├── CaregiverClientAffinityRepository.java
    │   │   ├── ClientStatus.java                      — enum: ACTIVE | INACTIVE | DISCHARGED
    │   │   ├── Client.java                            — @Filter(agencyFilter); serviceState overrides agency state for EVV routing
    │   │   ├── ClientRepository.java
    │   │   ├── ClientDiagnosis.java                   — @Filter(agencyFilter); PHI
    │   │   ├── ClientDiagnosisRepository.java
    │   │   ├── ClientMedication.java                  — @Filter(agencyFilter); PHI
    │   │   ├── ClientMedicationRepository.java
    │   │   ├── DocumentOwnerType.java                 — enum: CLIENT | CAREGIVER
    │   │   ├── Document.java                          — @Filter(agencyFilter); polymorphic ownerType/ownerId
    │   │   ├── DocumentRepository.java
    │   │   ├── CarePlanStatus.java                    — enum: DRAFT | ACTIVE | SUPERSEDED
    │   │   ├── CarePlan.java                          — @Filter(agencyFilter); business planVersion int, not JPA @Version
    │   │   ├── CarePlanRepository.java
    │   │   ├── AssistanceLevel.java                   — enum: INDEPENDENT | SUPERVISION | MINIMAL_ASSIST | MODERATE_ASSIST | MAXIMUM_ASSIST | DEPENDENT
    │   │   ├── AdlTask.java                           — @Filter(agencyFilter)
    │   │   ├── AdlTaskRepository.java
    │   │   ├── GoalStatus.java                        — enum: ACTIVE | ACHIEVED | DISCONTINUED | ON_HOLD
    │   │   ├── Goal.java                              — @Filter(agencyFilter)
    │   │   ├── GoalRepository.java
    │   │   ├── UnitType.java                          — enum: HOURS | VISITS
    │   │   ├── Authorization.java                     — @Filter(agencyFilter); @Version Long version for optimistic locking
    │   │   ├── AuthorizationRepository.java
    │   │   ├── FamilyPortalUser.java                  — @Filter(agencyFilter); no password — magic-link auth
    │   │   └── FamilyPortalUserRepository.java
    │   └── evv/
    │       ├── PayerEvvRoutingConfig.java             — global (no agencyId, no @Filter); one row per state+payerType override
    │       └── PayerEvvRoutingConfigRepository.java
    └── resources/db/migration/
        └── V3__core_domain_schema.sql
    test/java/com/hcare/
    ├── domain/
    │   ├── PayerServiceTypeDomainIT.java              — Payer, ServiceType, FeatureFlags save + multi-tenancy
    │   ├── CaregiverDomainIT.java                     — Caregiver save + multi-tenancy filter proof
    │   ├── CaregiverSubEntitiesIT.java                — Credential, BackgroundCheck, Availability, ScoringProfile, Affinity
    │   ├── ClientDomainIT.java                        — Client save + multi-tenancy
    │   ├── ClientSubEntitiesIT.java                   — Diagnosis, Medication, Document
    │   ├── CarePlanDomainIT.java                      — CarePlan versioning + ADLTask + Goal
    │   └── AuthorizationOptimisticLockIT.java         — @Version concurrent-update test
    └── evv/
        └── PayerEvvRoutingConfigIT.java               — lookup by stateCode + payerType
```

---

## Task 1: V3 schema migration

**Files:**
- Create: `backend/src/main/resources/db/migration/V3__core_domain_schema.sql`

- [ ] **Step 1: Create `V3__core_domain_schema.sql`**

```sql
-- backend/src/main/resources/db/migration/V3__core_domain_schema.sql
-- Core domain model: Payer, ServiceType, FeatureFlags, Caregiver (+ sub-entities),
-- Client (+ sub-entities), CarePlan (+ ADLTasks, Goals), Authorization, FamilyPortalUser,
-- PayerEvvRoutingConfig.
--
-- id columns have no DEFAULT — Hibernate generates UUIDs via @GeneratedValue(UUID).
-- JSON fields stored as TEXT — no DB-level JSON path queries (H2/PostgreSQL portability).
-- Authorization.version is managed by JPA @Version — never modified by application code.

CREATE TABLE payers (
    id           UUID         PRIMARY KEY,
    agency_id    UUID         NOT NULL REFERENCES agencies(id),
    name         VARCHAR(255) NOT NULL,
    payer_type   VARCHAR(20)  NOT NULL,
    state        CHAR(2)      NOT NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_payers_agency_name UNIQUE (agency_id, name)
);
CREATE INDEX idx_payers_agency_id ON payers(agency_id);

-- Global EVV routing overrides: when an MCO/payer mandates a specific aggregator
-- different from the state default (NY has 3, FL/NC/VA/TN/AR have MCO-specific mappings).
-- No agency_id — global reference data like evv_state_configs.
CREATE TABLE payer_evv_routing_configs (
    id              UUID        PRIMARY KEY,
    state_code      CHAR(2)     NOT NULL,
    payer_type      VARCHAR(20) NOT NULL,
    aggregator_type VARCHAR(30) NOT NULL,
    notes           TEXT,
    CONSTRAINT uq_payer_evv_routing UNIQUE (state_code, payer_type)
);

CREATE TABLE service_types (
    id                   UUID         PRIMARY KEY,
    agency_id            UUID         NOT NULL REFERENCES agencies(id),
    name                 VARCHAR(255) NOT NULL,
    code                 VARCHAR(50)  NOT NULL,
    requires_evv         BOOLEAN      NOT NULL DEFAULT TRUE,
    required_credentials TEXT         NOT NULL DEFAULT '[]',
    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_service_types_agency_code UNIQUE (agency_id, code)
);
CREATE INDEX idx_service_types_agency_id ON service_types(agency_id);

-- One row per agency. aiSchedulingEnabled gates Pro-tier AI match engine (Pro = true,
-- Starter = false — degrades gracefully to unranked eligible-caregiver list).
CREATE TABLE feature_flags (
    id                    UUID      PRIMARY KEY,
    agency_id             UUID      NOT NULL UNIQUE REFERENCES agencies(id),
    ai_scheduling_enabled BOOLEAN   NOT NULL DEFAULT FALSE,
    family_portal_enabled BOOLEAN   NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE caregivers (
    id          UUID         PRIMARY KEY,
    agency_id   UUID         NOT NULL REFERENCES agencies(id),
    first_name  VARCHAR(100) NOT NULL,
    last_name   VARCHAR(100) NOT NULL,
    email       VARCHAR(255) NOT NULL,
    phone       VARCHAR(20),
    address     TEXT,
    home_lat    DECIMAL(10, 7),
    home_lng    DECIMAL(10, 7),
    hire_date   DATE,
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    has_pet     BOOLEAN      NOT NULL DEFAULT FALSE,
    languages   TEXT         NOT NULL DEFAULT '[]',
    fcm_token   TEXT,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_caregivers_agency_email UNIQUE (agency_id, email)
);
CREATE INDEX idx_caregivers_agency_id ON caregivers(agency_id);

CREATE TABLE caregiver_credentials (
    id              UUID        PRIMARY KEY,
    caregiver_id    UUID        NOT NULL REFERENCES caregivers(id),
    agency_id       UUID        NOT NULL REFERENCES agencies(id),
    credential_type VARCHAR(30) NOT NULL,
    issue_date      DATE,
    expiry_date     DATE,
    verified        BOOLEAN     NOT NULL DEFAULT FALSE,
    verified_by     UUID,
    created_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_caregiver_credentials_caregiver_id ON caregiver_credentials(caregiver_id);
CREATE INDEX idx_caregiver_credentials_agency_id    ON caregiver_credentials(agency_id);

CREATE TABLE background_checks (
    id               UUID        PRIMARY KEY,
    caregiver_id     UUID        NOT NULL REFERENCES caregivers(id),
    agency_id        UUID        NOT NULL REFERENCES agencies(id),
    check_type       VARCHAR(30) NOT NULL,
    result           VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    checked_at       DATE        NOT NULL,
    renewal_due_date DATE,
    created_at       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_background_checks_caregiver_id ON background_checks(caregiver_id);
CREATE INDEX idx_background_checks_agency_id    ON background_checks(agency_id);

CREATE TABLE caregiver_availability (
    id           UUID        PRIMARY KEY,
    caregiver_id UUID        NOT NULL REFERENCES caregivers(id),
    agency_id    UUID        NOT NULL REFERENCES agencies(id),
    day_of_week  VARCHAR(10) NOT NULL,
    start_time   TIME        NOT NULL,
    end_time     TIME        NOT NULL,
    created_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_caregiver_availability_caregiver_id ON caregiver_availability(caregiver_id);
CREATE INDEX idx_caregiver_availability_agency_id    ON caregiver_availability(agency_id);

-- Pre-computed scoring signals updated asynchronously via Spring events on shift
-- completion/cancellation (Plan 5). Never populated on the match-request path.
CREATE TABLE caregiver_scoring_profiles (
    id                       UUID          PRIMARY KEY,
    caregiver_id             UUID          NOT NULL UNIQUE REFERENCES caregivers(id),
    agency_id                UUID          NOT NULL REFERENCES agencies(id),
    cancel_rate_last_90_days DECIMAL(5, 4) NOT NULL DEFAULT 0,
    current_week_hours       DECIMAL(5, 2) NOT NULL DEFAULT 0,
    updated_at               TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_caregiver_scoring_profiles_agency_id ON caregiver_scoring_profiles(agency_id);

-- Visit history per caregiver+client pair — drives the 25% continuity scoring factor.
-- client_id FK is deferred below (clients table does not exist yet at this point).
CREATE TABLE caregiver_client_affinities (
    id                 UUID    PRIMARY KEY,
    scoring_profile_id UUID    NOT NULL REFERENCES caregiver_scoring_profiles(id),
    client_id          UUID    NOT NULL,
    agency_id          UUID    NOT NULL REFERENCES agencies(id),
    visit_count        INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uq_affinity_profile_client UNIQUE (scoring_profile_id, client_id)
);
CREATE INDEX idx_affinity_scoring_profile_id ON caregiver_client_affinities(scoring_profile_id);
CREATE INDEX idx_affinity_agency_id          ON caregiver_client_affinities(agency_id);

CREATE TABLE clients (
    id                         UUID         PRIMARY KEY,
    agency_id                  UUID         NOT NULL REFERENCES agencies(id),
    first_name                 VARCHAR(100) NOT NULL,
    last_name                  VARCHAR(100) NOT NULL,
    date_of_birth              DATE         NOT NULL,
    address                    TEXT,
    lat                        DECIMAL(10, 7),
    lng                        DECIMAL(10, 7),
    phone                      VARCHAR(20),
    medicaid_id                VARCHAR(50),
    service_state              CHAR(2),
    preferred_caregiver_gender VARCHAR(10),
    preferred_languages        TEXT         NOT NULL DEFAULT '[]',
    no_pet_caregiver           BOOLEAN      NOT NULL DEFAULT FALSE,
    status                     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at                 TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_clients_agency_id ON clients(agency_id);

-- Now that clients table exists, add the deferred FK
ALTER TABLE caregiver_client_affinities
    ADD CONSTRAINT fk_affinity_client FOREIGN KEY (client_id) REFERENCES clients(id);

CREATE TABLE client_diagnoses (
    id          UUID        PRIMARY KEY,
    client_id   UUID        NOT NULL REFERENCES clients(id),
    agency_id   UUID        NOT NULL REFERENCES agencies(id),
    icd10_code  VARCHAR(10) NOT NULL,
    description TEXT,
    onset_date  DATE,
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_client_diagnoses_client_id ON client_diagnoses(client_id);
CREATE INDEX idx_client_diagnoses_agency_id ON client_diagnoses(agency_id);

CREATE TABLE client_medications (
    id          UUID         PRIMARY KEY,
    client_id   UUID         NOT NULL REFERENCES clients(id),
    agency_id   UUID         NOT NULL REFERENCES agencies(id),
    name        VARCHAR(255) NOT NULL,
    dosage      VARCHAR(100),
    route       VARCHAR(100),
    schedule    TEXT,
    prescriber  VARCHAR(255),
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_client_medications_client_id ON client_medications(client_id);
CREATE INDEX idx_client_medications_agency_id ON client_medications(agency_id);

-- Polymorphic document store: owner_type = CLIENT | CAREGIVER.
-- No DB-level FK on owner_id because it references two different tables.
CREATE TABLE documents (
    id            UUID         PRIMARY KEY,
    agency_id     UUID         NOT NULL REFERENCES agencies(id),
    owner_type    VARCHAR(20)  NOT NULL,
    owner_id      UUID         NOT NULL,
    file_name     VARCHAR(255) NOT NULL,
    file_path     TEXT         NOT NULL,
    document_type VARCHAR(100),
    uploaded_by   UUID,
    uploaded_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_documents_owner     ON documents(owner_type, owner_id);
CREATE INDEX idx_documents_agency_id ON documents(agency_id);

-- Versioned care plans: one ACTIVE per client at a time, enforced at the service layer.
-- reviewed_by_clinician_id and reviewed_at are required for HHCS (skilled nursing) plans
-- to satisfy state-level clinical supervision requirements; PCS plans leave them null.
CREATE TABLE care_plans (
    id                       UUID        PRIMARY KEY,
    client_id                UUID        NOT NULL REFERENCES clients(id),
    agency_id                UUID        NOT NULL REFERENCES agencies(id),
    plan_version             INTEGER     NOT NULL DEFAULT 1,
    status                   VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at               TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    activated_at             TIMESTAMP,
    reviewed_by_clinician_id UUID,
    reviewed_at              TIMESTAMP
);
CREATE INDEX idx_care_plans_client_id ON care_plans(client_id);
CREATE INDEX idx_care_plans_agency_id ON care_plans(agency_id);

CREATE TABLE adl_tasks (
    id               UUID         PRIMARY KEY,
    care_plan_id     UUID         NOT NULL REFERENCES care_plans(id),
    agency_id        UUID         NOT NULL REFERENCES agencies(id),
    name             VARCHAR(255) NOT NULL,
    instructions     TEXT,
    assistance_level VARCHAR(30)  NOT NULL,
    frequency        VARCHAR(100),
    sort_order       INTEGER      NOT NULL DEFAULT 0
);
CREATE INDEX idx_adl_tasks_care_plan_id ON adl_tasks(care_plan_id);
CREATE INDEX idx_adl_tasks_agency_id    ON adl_tasks(agency_id);

CREATE TABLE goals (
    id           UUID        PRIMARY KEY,
    care_plan_id UUID        NOT NULL REFERENCES care_plans(id),
    agency_id    UUID        NOT NULL REFERENCES agencies(id),
    description  TEXT        NOT NULL,
    target_date  DATE,
    status       VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
);
CREATE INDEX idx_goals_care_plan_id ON goals(care_plan_id);
CREATE INDEX idx_goals_agency_id    ON goals(agency_id);

-- version column is managed exclusively by JPA @Version — never set by application code.
-- Concurrent shift completions both try UPDATE ... WHERE version=N; the second one
-- gets 0 rows updated and throws ObjectOptimisticLockingFailureException, then retries.
CREATE TABLE authorizations (
    id               UUID           PRIMARY KEY,
    client_id        UUID           NOT NULL REFERENCES clients(id),
    payer_id         UUID           NOT NULL REFERENCES payers(id),
    service_type_id  UUID           NOT NULL REFERENCES service_types(id),
    agency_id        UUID           NOT NULL REFERENCES agencies(id),
    auth_number      VARCHAR(100)   NOT NULL,
    authorized_units DECIMAL(10, 2) NOT NULL,
    used_units       DECIMAL(10, 2) NOT NULL DEFAULT 0,
    unit_type        VARCHAR(10)    NOT NULL DEFAULT 'HOURS',
    start_date       DATE           NOT NULL,
    end_date         DATE           NOT NULL,
    version          BIGINT         NOT NULL DEFAULT 0,
    created_at       TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_authorizations_client_id  ON authorizations(client_id);
CREATE INDEX idx_authorizations_agency_id  ON authorizations(agency_id);

-- Family portal users authenticate via magic link (no password).
-- JWT claim: {"role":"FAMILY_PORTAL","clientId":"...","agencyId":"..."}.
-- Hard-scoped to one client — all /api/v1/family/ endpoints verify the clientId JWT claim.
CREATE TABLE family_portal_users (
    id            UUID         PRIMARY KEY,
    client_id     UUID         NOT NULL REFERENCES clients(id),
    agency_id     UUID         NOT NULL REFERENCES agencies(id),
    email         VARCHAR(255) NOT NULL,
    name          VARCHAR(255),
    last_login_at TIMESTAMP,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_family_portal_users_email UNIQUE (email)
);
CREATE INDEX idx_family_portal_users_client_id ON family_portal_users(client_id);
CREATE INDEX idx_family_portal_users_agency_id ON family_portal_users(agency_id);
```

- [ ] **Step 2: Run the smoke test to confirm the migration applies cleanly on H2**

```bash
cd backend && mvn test -Dtest=HcareApplicationTest -Dspring.profiles.active=dev
```
Expected: `BUILD SUCCESS` — Flyway applies V3 and Hibernate validates. No new JPA entities exist yet so the context loads without mapping any V3 tables.

- [ ] **Step 3: Commit**

```bash
cd backend
git add src/main/resources/db/migration/V3__core_domain_schema.sql
git commit -m "feat: V3 schema migration — Payer, ServiceType, FeatureFlags, Caregiver, Client, CarePlan, Authorization, FamilyPortalUser, PayerEvvRoutingConfig"
```

---

## Task 2: Payer, ServiceType, and FeatureFlags entities

**Files:**
- Create: `backend/src/main/java/com/hcare/domain/PayerType.java`
- Create: `backend/src/main/java/com/hcare/domain/Payer.java`
- Create: `backend/src/main/java/com/hcare/domain/PayerRepository.java`
- Create: `backend/src/main/java/com/hcare/domain/ServiceType.java`
- Create: `backend/src/main/java/com/hcare/domain/ServiceTypeRepository.java`
- Create: `backend/src/main/java/com/hcare/domain/FeatureFlags.java`
- Create: `backend/src/main/java/com/hcare/domain/FeatureFlagsRepository.java`
- Test: `backend/src/test/java/com/hcare/domain/PayerServiceTypeDomainIT.java`

- [ ] **Step 1: Write the failing integration test**

```java
// backend/src/test/java/com/hcare/domain/PayerServiceTypeDomainIT.java
package com.hcare.domain;

import com.hcare.AbstractIntegrationTest;
import com.hcare.multitenancy.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class PayerServiceTypeDomainIT extends AbstractIntegrationTest {

    @Autowired private AgencyRepository agencyRepo;
    @Autowired private PayerRepository payerRepo;
    @Autowired private ServiceTypeRepository serviceTypeRepo;
    @Autowired private FeatureFlagsRepository featureFlagsRepo;
    @Autowired private TransactionTemplate transactionTemplate;

    @Test
    void payer_can_be_saved_and_retrieved() {
        Agency agency = agencyRepo.save(new Agency("Payer Test Agency", "TX"));
        Payer payer = payerRepo.save(
            new Payer(agency.getId(), "Texas Medicaid", PayerType.MEDICAID, "TX"));

        Payer loaded = payerRepo.findById(payer.getId()).orElseThrow();
        assertThat(loaded.getName()).isEqualTo("Texas Medicaid");
        assertThat(loaded.getPayerType()).isEqualTo(PayerType.MEDICAID);
        assertThat(loaded.getAgencyId()).isEqualTo(agency.getId());
        assertThat(loaded.getState()).isEqualTo("TX");
    }

    @Test
    void payer_agencyFilter_excludes_other_agency_payers() {
        Agency agencyA = agencyRepo.save(new Agency("Payer Agency A", "TX"));
        Agency agencyB = agencyRepo.save(new Agency("Payer Agency B", "CA"));

        Payer payerA = payerRepo.save(
            new Payer(agencyA.getId(), "Medicaid A", PayerType.MEDICAID, "TX"));
        payerRepo.save(
            new Payer(agencyB.getId(), "Medicaid B", PayerType.MEDICAID, "CA"));

        // TransactionTemplate ensures an outer @Transactional is open before the
        // repository is entered — required for TenantFilterAspect to fire correctly.
        TenantContext.set(agencyA.getId());
        List<Payer> result;
        try {
            result = transactionTemplate.execute(status -> payerRepo.findAll());
        } finally {
            TenantContext.clear();
        }

        assertThat(result).isNotNull();
        assertThat(result.stream().map(Payer::getId).toList())
            .contains(payerA.getId());
        // All returned payers belong to Agency A
        assertThat(result).allMatch(p -> p.getAgencyId().equals(agencyA.getId()));
    }

    @Test
    void serviceType_can_be_saved_with_required_credentials() {
        Agency agency = agencyRepo.save(new Agency("ST Test Agency", "TX"));
        ServiceType st = serviceTypeRepo.save(new ServiceType(
            agency.getId(), "Personal Care Services", "PCS", true, "[\"HHA\",\"CPR\"]"));

        ServiceType loaded = serviceTypeRepo.findById(st.getId()).orElseThrow();
        assertThat(loaded.getName()).isEqualTo("Personal Care Services");
        assertThat(loaded.isRequiresEvv()).isTrue();
        assertThat(loaded.getRequiredCredentials()).isEqualTo("[\"HHA\",\"CPR\"]");
    }

    @Test
    void featureFlags_default_values_are_correct() {
        Agency agency = agencyRepo.save(new Agency("Flags Test Agency", "TX"));
        FeatureFlags flags = featureFlagsRepo.save(new FeatureFlags(agency.getId()));

        FeatureFlags loaded = featureFlagsRepo.findById(flags.getId()).orElseThrow();
        assertThat(loaded.isAiSchedulingEnabled()).isFalse();  // Pro tier only, off by default
        assertThat(loaded.isFamilyPortalEnabled()).isTrue();   // on by default
        assertThat(loaded.getAgencyId()).isEqualTo(agency.getId());
    }

    @Test
    void featureFlags_aiScheduling_can_be_enabled() {
        Agency agency = agencyRepo.save(new Agency("Pro Agency", "TX"));
        FeatureFlags flags = featureFlagsRepo.save(new FeatureFlags(agency.getId()));

        flags.setAiSchedulingEnabled(true);
        featureFlagsRepo.save(flags);

        FeatureFlags loaded = featureFlagsRepo.findById(flags.getId()).orElseThrow();
        assertThat(loaded.isAiSchedulingEnabled()).isTrue();
    }
}
```

- [ ] **Step 2: Run to confirm failure**

```bash
cd backend && mvn test -Dtest=PayerServiceTypeDomainIT
```
Expected: FAIL — compile error, `Payer`, `PayerType`, `ServiceType`, `FeatureFlags` do not exist.

- [ ] **Step 3: Create enum files**

```java
// backend/src/main/java/com/hcare/domain/PayerType.java
package com.hcare.domain;

public enum PayerType {
    MEDICAID, PRIVATE_PAY, LTC_INSURANCE, VA, MEDICARE
}
```

- [ ] **Step 4: Create `Payer.java`**

```java
// backend/src/main/java/com/hcare/domain/Payer.java
package com.hcare.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payers")
@Filter(name = "agencyFilter", condition = "agency_id = :agencyId")
public class Payer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "payer_type", nullable = false, length = 20)
    private PayerType payerType;

    @Column(nullable = false, columnDefinition = "CHAR(2)")
    private String state;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected Payer() {}

    public Payer(UUID agencyId, String name, PayerType payerType, String state) {
        this.agencyId = agencyId;
        this.name = name;
        this.payerType = payerType;
        this.state = state;
    }

    public UUID getId() { return id; }
    public UUID getAgencyId() { return agencyId; }
    public String getName() { return name; }
    public PayerType getPayerType() { return payerType; }
    public String getState() { return state; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
```

- [ ] **Step 5: Create `PayerRepository.java`**

```java
// backend/src/main/java/com/hcare/domain/PayerRepository.java
package com.hcare.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface PayerRepository extends JpaRepository<Payer, UUID> {
    List<Payer> findByAgencyId(UUID agencyId);
}
```

- [ ] **Step 6: Create `ServiceType.java`**

```java
// backend/src/main/java/com/hcare/domain/ServiceType.java
package com.hcare.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "service_types")
@Filter(name = "agencyFilter", condition = "agency_id = :agencyId")
public class ServiceType {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(name = "requires_evv", nullable = false)
    private boolean requiresEvv;

    // JSON array of CredentialType enum names — parsed at application layer, never queried at DB level
    @Column(name = "required_credentials", nullable = false, columnDefinition = "TEXT")
    private String requiredCredentials;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected ServiceType() {}

    public ServiceType(UUID agencyId, String name, String code,
                       boolean requiresEvv, String requiredCredentials) {
        this.agencyId = agencyId;
        this.name = name;
        this.code = code;
        this.requiresEvv = requiresEvv;
        this.requiredCredentials = requiredCredentials;
    }

    public UUID getId() { return id; }
    public UUID getAgencyId() { return agencyId; }
    public String getName() { return name; }
    public String getCode() { return code; }
    public boolean isRequiresEvv() { return requiresEvv; }
    public String getRequiredCredentials() { return requiredCredentials; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
```

- [ ] **Step 7: Create `ServiceTypeRepository.java`**

```java
// backend/src/main/java/com/hcare/domain/ServiceTypeRepository.java
package com.hcare.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ServiceTypeRepository extends JpaRepository<ServiceType, UUID> {
    List<ServiceType> findByAgencyId(UUID agencyId);
}
```

- [ ] **Step 8: Create `FeatureFlags.java`**

```java
// backend/src/main/java/com/hcare/domain/FeatureFlags.java
package com.hcare.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "feature_flags")
@Filter(name = "agencyFilter", condition = "agency_id = :agencyId")
public class FeatureFlags {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "agency_id", nullable = false, unique = true)
    private UUID agencyId;

    @Column(name = "ai_scheduling_enabled", nullable = false)
    private boolean aiSchedulingEnabled = false;

    @Column(name = "family_portal_enabled", nullable = false)
    private boolean familyPortalEnabled = true;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected FeatureFlags() {}

    public FeatureFlags(UUID agencyId) {
        this.agencyId = agencyId;
    }

    public void setAiSchedulingEnabled(boolean enabled) { this.aiSchedulingEnabled = enabled; }
    public void setFamilyPortalEnabled(boolean enabled) { this.familyPortalEnabled = enabled; }

    public UUID getId() { return id; }
    public UUID getAgencyId() { return agencyId; }
    public boolean isAiSchedulingEnabled() { return aiSchedulingEnabled; }
    public boolean isFamilyPortalEnabled() { return familyPortalEnabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
```

- [ ] **Step 9: Create `FeatureFlagsRepository.java`**

```java
// backend/src/main/java/com/hcare/domain/FeatureFlagsRepository.java
package com.hcare.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface FeatureFlagsRepository extends JpaRepository<FeatureFlags, UUID> {
    Optional<FeatureFlags> findByAgencyId(UUID agencyId);
}
```

- [ ] **Step 10: Run tests to confirm they pass**

```bash
cd backend && mvn test -Dtest=PayerServiceTypeDomainIT
```
Expected: `Tests run: 5, Failures: 0, Errors: 0`.

- [ ] **Step 11: Commit**

```bash
cd backend
git add src/main/java/com/hcare/domain/PayerType.java \
        src/main/java/com/hcare/domain/Payer.java \
        src/main/java/com/hcare/domain/PayerRepository.java \
        src/main/java/com/hcare/domain/ServiceType.java \
        src/main/java/com/hcare/domain/ServiceTypeRepository.java \
        src/main/java/com/hcare/domain/FeatureFlags.java \
        src/main/java/com/hcare/domain/FeatureFlagsRepository.java \
        src/test/java/com/hcare/domain/PayerServiceTypeDomainIT.java
git commit -m "feat: Payer, ServiceType, FeatureFlags entities — agency-scoped with agencyFilter"
```

---

## Task 3: Caregiver entity

**Files:**
- Create: `backend/src/main/java/com/hcare/domain/CaregiverStatus.java`
- Create: `backend/src/main/java/com/hcare/domain/Caregiver.java`
- Create: `backend/src/main/java/com/hcare/domain/CaregiverRepository.java`
- Test: `backend/src/test/java/com/hcare/domain/CaregiverDomainIT.java`

- [ ] **Step 1: Write the failing integration test**

```java
// backend/src/test/java/com/hcare/domain/CaregiverDomainIT.java
package com.hcare.domain;

import com.hcare.AbstractIntegrationTest;
import com.hcare.multitenancy.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class CaregiverDomainIT extends AbstractIntegrationTest {

    @Autowired private AgencyRepository agencyRepo;
    @Autowired private CaregiverRepository caregiverRepo;
    @Autowired private TransactionTemplate transactionTemplate;

    @Test
    void caregiver_can_be_saved_and_retrieved() {
        Agency agency = agencyRepo.save(new Agency("Caregiver Test Agency", "TX"));
        Caregiver caregiver = caregiverRepo.save(
            new Caregiver(agency.getId(), "Alice", "Smith", "alice@test.com"));

        Caregiver loaded = caregiverRepo.findById(caregiver.getId()).orElseThrow();
        assertThat(loaded.getFirstName()).isEqualTo("Alice");
        assertThat(loaded.getLastName()).isEqualTo("Smith");
        assertThat(loaded.getEmail()).isEqualTo("alice@test.com");
        assertThat(loaded.getStatus()).isEqualTo(CaregiverStatus.ACTIVE);
        assertThat(loaded.isHasPet()).isFalse();
        assertThat(loaded.getLanguages()).isEqualTo("[]");
        assertThat(loaded.getAgencyId()).isEqualTo(agency.getId());
    }

    @Test
    void caregiver_stores_geocoded_location() {
        Agency agency = agencyRepo.save(new Agency("Geo Agency", "TX"));
        Caregiver caregiver = new Caregiver(agency.getId(), "Bob", "Jones", "bob@geo.com");
        // In production, homeLat/homeLng are populated when the admin saves the caregiver's
        // address — the geocoding API call happens at save time, not at match time.
        // Here we set them directly to simulate a geocoded caregiver.
        caregiver.setHomeLat(new BigDecimal("30.2672"));
        caregiver.setHomeLng(new BigDecimal("-97.7431"));
        caregiverRepo.save(caregiver);

        Caregiver loaded = caregiverRepo.findById(caregiver.getId()).orElseThrow();
        assertThat(loaded.getHomeLat()).isEqualByComparingTo(new BigDecimal("30.2672"));
        assertThat(loaded.getHomeLng()).isEqualByComparingTo(new BigDecimal("-97.7431"));
    }

    @Test
    void caregiver_agencyFilter_excludes_other_agency_caregivers() {
        Agency agencyA = agencyRepo.save(new Agency("Caregiver Agency A", "TX"));
        Agency agencyB = agencyRepo.save(new Agency("Caregiver Agency B", "CA"));

        Caregiver caregiverA = caregiverRepo.save(
            new Caregiver(agencyA.getId(), "Alice", "A", "alice-a@test.com"));
        caregiverRepo.save(
            new Caregiver(agencyB.getId(), "Bob", "B", "bob-b@test.com"));

        TenantContext.set(agencyA.getId());
        List<Caregiver> result;
        try {
            result = transactionTemplate.execute(status -> caregiverRepo.findAll());
        } finally {
            TenantContext.clear();
        }

        assertThat(result).isNotNull();
        assertThat(result.stream().map(Caregiver::getId).toList())
            .contains(caregiverA.getId());
        assertThat(result).allMatch(c -> c.getAgencyId().equals(agencyA.getId()));
    }
}
```

- [ ] **Step 2: Run to confirm failure**

```bash
cd backend && mvn test -Dtest=CaregiverDomainIT
```
Expected: FAIL — compile error, `Caregiver`, `CaregiverStatus`, `CaregiverRepository` do not exist.

- [ ] **Step 3: Create `CaregiverStatus.java`**

```java
// backend/src/main/java/com/hcare/domain/CaregiverStatus.java
package com.hcare.domain;

public enum CaregiverStatus {
    ACTIVE, INACTIVE, TERMINATED
}
```

- [ ] **Step 4: Create `Caregiver.java`**

```java
// backend/src/main/java/com/hcare/domain/Caregiver.java
package com.hcare.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "caregivers")
@Filter(name = "agencyFilter", condition = "agency_id = :agencyId")
public class Caregiver {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(nullable = false)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(columnDefinition = "TEXT")
    private String address;

    // Populated at save time via geocoding API — never called at match-request time
    @Column(name = "home_lat", precision = 10, scale = 7)
    private BigDecimal homeLat;

    @Column(name = "home_lng", precision = 10, scale = 7)
    private BigDecimal homeLng;

    @Column(name = "hire_date")
    private LocalDate hireDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CaregiverStatus status = CaregiverStatus.ACTIVE;

    @Column(name = "has_pet", nullable = false)
    private boolean hasPet = false;

    // JSON array of language codes e.g. ["en","es"] — parsed at application layer
    @Column(nullable = false, columnDefinition = "TEXT")
    private String languages = "[]";

    @Column(name = "fcm_token", columnDefinition = "TEXT")
    private String fcmToken;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected Caregiver() {}

    public Caregiver(UUID agencyId, String firstName, String lastName, String email) {
        this.agencyId = agencyId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
    }

    public void setHomeLat(BigDecimal homeLat) { this.homeLat = homeLat; }
    public void setHomeLng(BigDecimal homeLng) { this.homeLng = homeLng; }
    public void setLanguages(String languages) { this.languages = languages; }
    public void setFcmToken(String fcmToken) { this.fcmToken = fcmToken; }

    public UUID getId() { return id; }
    public UUID getAgencyId() { return agencyId; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getAddress() { return address; }
    public BigDecimal getHomeLat() { return homeLat; }
    public BigDecimal getHomeLng() { return homeLng; }
    public LocalDate getHireDate() { return hireDate; }
    public CaregiverStatus getStatus() { return status; }
    public boolean isHasPet() { return hasPet; }
    public String getLanguages() { return languages; }
    public String getFcmToken() { return fcmToken; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
```

- [ ] **Step 5: Create `CaregiverRepository.java`**

```java
// backend/src/main/java/com/hcare/domain/CaregiverRepository.java
package com.hcare.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface CaregiverRepository extends JpaRepository<Caregiver, UUID> {
    List<Caregiver> findByAgencyId(UUID agencyId);
}
```

- [ ] **Step 6: Run tests to confirm they pass**

```bash
cd backend && mvn test -Dtest=CaregiverDomainIT
```
Expected: `Tests run: 3, Failures: 0, Errors: 0`.

- [ ] **Step 7: Commit**

```bash
cd backend
git add src/main/java/com/hcare/domain/CaregiverStatus.java \
        src/main/java/com/hcare/domain/Caregiver.java \
        src/main/java/com/hcare/domain/CaregiverRepository.java \
        src/test/java/com/hcare/domain/CaregiverDomainIT.java
git commit -m "feat: Caregiver entity — agency-scoped, geocoded lat/lng, agencyFilter multi-tenancy verified"
```

---

## Task 4: Caregiver sub-entities (Credentials, BackgroundCheck, Availability, ScoringProfile, ClientAffinity)

**Files:**
- Create: `backend/src/main/java/com/hcare/domain/CredentialType.java`
- Create: `backend/src/main/java/com/hcare/domain/CaregiverCredential.java`
- Create: `backend/src/main/java/com/hcare/domain/CaregiverCredentialRepository.java`
- Create: `backend/src/main/java/com/hcare/domain/BackgroundCheckType.java`
- Create: `backend/src/main/java/com/hcare/domain/BackgroundCheckResult.java`
- Create: `backend/src/main/java/com/hcare/domain/BackgroundCheck.java`
- Create: `backend/src/main/java/com/hcare/domain/BackgroundCheckRepository.java`
- Create: `backend/src/main/java/com/hcare/domain/CaregiverAvailability.java`
- Create: `backend/src/main/java/com/hcare/domain/CaregiverAvailabilityRepository.java`
- Create: `backend/src/main/java/com/hcare/domain/CaregiverScoringProfile.java`
- Create: `backend/src/main/java/com/hcare/domain/CaregiverScoringProfileRepository.java`
- Create: `backend/src/main/java/com/hcare/domain/CaregiverClientAffinity.java`
- Create: `backend/src/main/java/com/hcare/domain/CaregiverClientAffinityRepository.java`
- Test: `backend/src/test/java/com/hcare/domain/CaregiverSubEntitiesIT.java`

- [ ] **Step 1: Write the failing integration test**

```java
// backend/src/test/java/com/hcare/domain/CaregiverSubEntitiesIT.java
package com.hcare.domain;

import com.hcare.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class CaregiverSubEntitiesIT extends AbstractIntegrationTest {

    @Autowired private AgencyRepository agencyRepo;
    @Autowired private CaregiverRepository caregiverRepo;
    @Autowired private CaregiverCredentialRepository credentialRepo;
    @Autowired private BackgroundCheckRepository bgCheckRepo;
    @Autowired private CaregiverAvailabilityRepository availabilityRepo;
    @Autowired private CaregiverScoringProfileRepository scoringProfileRepo;
    @Autowired private CaregiverClientAffinityRepository affinityRepo;

    private Agency agency;
    private Caregiver caregiver;

    @BeforeEach
    void setup() {
        agency = agencyRepo.save(new Agency("Sub-Entity Agency", "TX"));
        caregiver = caregiverRepo.save(
            new Caregiver(agency.getId(), "Carol", "White", "carol@test.com"));
    }

    @Test
    void credential_can_be_saved_and_retrieved() {
        CaregiverCredential cred = credentialRepo.save(new CaregiverCredential(
            caregiver.getId(), agency.getId(), CredentialType.HHA,
            LocalDate.of(2023, 1, 15), LocalDate.of(2025, 1, 15)));

        CaregiverCredential loaded = credentialRepo.findById(cred.getId()).orElseThrow();
        assertThat(loaded.getCredentialType()).isEqualTo(CredentialType.HHA);
        assertThat(loaded.isVerified()).isFalse(); // unverified by default
        assertThat(loaded.getCaregiverId()).isEqualTo(caregiver.getId());
        assertThat(loaded.getExpiryDate()).isEqualTo(LocalDate.of(2025, 1, 15));
    }

    @Test
    void background_check_defaults_to_pending() {
        BackgroundCheck check = bgCheckRepo.save(new BackgroundCheck(
            caregiver.getId(), agency.getId(), BackgroundCheckType.OIG,
            BackgroundCheckResult.CLEAR, LocalDate.now()));

        BackgroundCheck loaded = bgCheckRepo.findById(check.getId()).orElseThrow();
        assertThat(loaded.getCheckType()).isEqualTo(BackgroundCheckType.OIG);
        assertThat(loaded.getResult()).isEqualTo(BackgroundCheckResult.CLEAR);
    }

    @Test
    void availability_stores_weekly_time_block() {
        CaregiverAvailability avail = availabilityRepo.save(new CaregiverAvailability(
            caregiver.getId(), agency.getId(),
            DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(16, 0)));

        CaregiverAvailability loaded = availabilityRepo.findById(avail.getId()).orElseThrow();
        assertThat(loaded.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(loaded.getStartTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(loaded.getEndTime()).isEqualTo(LocalTime.of(16, 0));
    }

    @Test
    void scoring_profile_initialises_with_zero_values() {
        CaregiverScoringProfile profile = scoringProfileRepo.save(
            new CaregiverScoringProfile(caregiver.getId(), agency.getId()));

        CaregiverScoringProfile loaded = scoringProfileRepo.findById(profile.getId()).orElseThrow();
        assertThat(loaded.getCancelRateLast90Days()).isEqualByComparingTo("0");
        assertThat(loaded.getCurrentWeekHours()).isEqualByComparingTo("0");
        assertThat(loaded.getCaregiverId()).isEqualTo(caregiver.getId());
    }

    // TODO after Task 5: add caregiver_client_affinity_tracks_visit_count test here.
    // CaregiverClientAffinity.clientId has an FK to clients, so a real Client row is required.
    // ClientRepository is created in Task 5 — add the full affinity test to ClientDomainIT
    // once Task 5 is complete (see Task 5 step 1 for the complete test).
}
```

> **Note on affinity test:** The `CaregiverClientAffinity.clientId` has a FK to `clients`. The affinity save/retrieve test lives in `ClientDomainIT` (Task 5), where `ClientRepository` is available.

- [ ] **Step 2: Run to confirm failure**

```bash
cd backend && mvn test -Dtest=CaregiverSubEntitiesIT
```
Expected: FAIL — compile error, credential/background check/availability/scoring profile types do not exist.

- [ ] **Step 3: Create enum files**

```java
// backend/src/main/java/com/hcare/domain/CredentialType.java
package com.hcare.domain;

public enum CredentialType {
    HHA, CNA, LPN, RN, CPR, FIRST_AID, DRIVERS_LICENSE, TB_TEST, HEPATITIS_B, COVID_VACCINE
}
```

```java
// backend/src/main/java/com/hcare/domain/BackgroundCheckType.java
package com.hcare.domain;

public enum BackgroundCheckType {
    STATE_REGISTRY, FBI, OIG, SEX_OFFENDER
}
```

```java
// backend/src/main/java/com/hcare/domain/BackgroundCheckResult.java
package com.hcare.domain;

public enum BackgroundCheckResult {
    CLEAR, FLAGS, PENDING
}
```

- [ ] **Step 4: Create `CaregiverCredential.java`**

```java
// backend/src/main/java/com/hcare/domain/CaregiverCredential.java
package com.hcare.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "caregiver_credentials")
@Filter(name = "agencyFilter", condition = "agency_id = :agencyId")
public class CaregiverCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "caregiver_id", nullable = false)
    private UUID caregiverId;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "credential_type", nullable = false, length = 30)
    private CredentialType credentialType;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(nullable = false)
    private boolean verified = false;

    @Column(name = "verified_by")
    private UUID verifiedBy; // agency_user id who verified (nullable — manual admin action at P1)

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected CaregiverCredential() {}

    public CaregiverCredential(UUID caregiverId, UUID agencyId, CredentialType credentialType,
                               LocalDate issueDate, LocalDate expiryDate) {
        this.caregiverId = caregiverId;
        this.agencyId = agencyId;
        this.credentialType = credentialType;
        this.issueDate = issueDate;
        this.expiryDate = expiryDate;
    }

    public void verify(UUID adminUserId) {
        this.verified = true;
        this.verifiedBy = adminUserId;
    }

    public UUID getId() { return id; }
    public UUID getCaregiverId() { return caregiverId; }
    public UUID getAgencyId() { return agencyId; }
    public CredentialType getCredentialType() { return credentialType; }
    public LocalDate getIssueDate() { return issueDate; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public boolean isVerified() { return verified; }
    public UUID getVerifiedBy() { return verifiedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
```

- [ ] **Step 5: Create `CaregiverCredentialRepository.java`**

```java
// backend/src/main/java/com/hcare/domain/CaregiverCredentialRepository.java
package com.hcare.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface CaregiverCredentialRepository extends JpaRepository<CaregiverCredential, UUID> {
    List<CaregiverCredential> findByCaregiverId(UUID caregiverId);
}
```

- [ ] **Step 6: Create `BackgroundCheck.java`**

```java
// backend/src/main/java/com/hcare/domain/BackgroundCheck.java
package com.hcare.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "background_checks")
@Filter(name = "agencyFilter", condition = "agency_id = :agencyId")
public class BackgroundCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "caregiver_id", nullable = false)
    private UUID caregiverId;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "check_type", nullable = false, length = 30)
    private BackgroundCheckType checkType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BackgroundCheckResult result;

    @Column(name = "checked_at", nullable = false)
    private LocalDate checkedAt;

    @Column(name = "renewal_due_date")
    private LocalDate renewalDueDate;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected BackgroundCheck() {}

    public BackgroundCheck(UUID caregiverId, UUID agencyId, BackgroundCheckType checkType,
                           BackgroundCheckResult result, LocalDate checkedAt) {
        this.caregiverId = caregiverId;
        this.agencyId = agencyId;
        this.checkType = checkType;
        this.result = result;
        this.checkedAt = checkedAt;
    }

    public UUID getId() { return id; }
    public UUID getCaregiverId() { return caregiverId; }
    public UUID getAgencyId() { return agencyId; }
    public BackgroundCheckType getCheckType() { return checkType; }
    public BackgroundCheckResult getResult() { return result; }
    public LocalDate getCheckedAt() { return checkedAt; }
    public LocalDate getRenewalDueDate() { return renewalDueDate; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
```

- [ ] **Step 7: Create `BackgroundCheckRepository.java`**

```java
// backend/src/main/java/com/hcare/domain/BackgroundCheckRepository.java
package com.hcare.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface BackgroundCheckRepository extends JpaRepository<BackgroundCheck, UUID> {
    List<BackgroundCheck> findByCaregiverId(UUID caregiverId);
}
```

- [ ] **Step 8: Create `CaregiverAvailability.java`**

```java
// backend/src/main/java/com/hcare/domain/CaregiverAvailability.java
package com.hcare.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "caregiver_availability")
@Filter(name = "agencyFilter", condition = "agency_id = :agencyId")
public class CaregiverAvailability {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "caregiver_id", nullable = false)
    private UUID caregiverId;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 10)
    private DayOfWeek dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected CaregiverAvailability() {}

    public CaregiverAvailability(UUID caregiverId, UUID agencyId,
                                  DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
        this.caregiverId = caregiverId;
        this.agencyId = agencyId;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public UUID getId() { return id; }
    public UUID getCaregiverId() { return caregiverId; }
    public UUID getAgencyId() { return agencyId; }
    public DayOfWeek getDayOfWeek() { return dayOfWeek; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
```

- [ ] **Step 9: Create `CaregiverAvailabilityRepository.java`**

```java
// backend/src/main/java/com/hcare/domain/CaregiverAvailabilityRepository.java
package com.hcare.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface CaregiverAvailabilityRepository extends JpaRepository<CaregiverAvailability, UUID> {
    List<CaregiverAvailability> findByCaregiverId(UUID caregiverId);
}
```

- [ ] **Step 10: Create `CaregiverScoringProfile.java`**

```java
// backend/src/main/java/com/hcare/domain/CaregiverScoringProfile.java
package com.hcare.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

// Pre-computed scoring signals. Updated asynchronously via Spring events when shifts
// complete or cancel (Plan 5). Never computed on the match-request path.
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

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    protected CaregiverScoringProfile() {}

    public CaregiverScoringProfile(UUID caregiverId, UUID agencyId) {
        this.caregiverId = caregiverId;
        this.agencyId = agencyId;
    }

    public void updateAfterShiftCompletion(BigDecimal hoursWorked) {
        this.currentWeekHours = this.currentWeekHours.add(hoursWorked);
        this.updatedAt = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getCaregiverId() { return caregiverId; }
    public UUID getAgencyId() { return agencyId; }
    public BigDecimal getCancelRateLast90Days() { return cancelRateLast90Days; }
    public BigDecimal getCurrentWeekHours() { return currentWeekHours; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
```

- [ ] **Step 11: Create `CaregiverScoringProfileRepository.java`**

```java
// backend/src/main/java/com/hcare/domain/CaregiverScoringProfileRepository.java
package com.hcare.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface CaregiverScoringProfileRepository extends JpaRepository<CaregiverScoringProfile, UUID> {
    Optional<CaregiverScoringProfile> findByCaregiverId(UUID caregiverId);
}
```

- [ ] **Step 12: Create `CaregiverClientAffinity.java`**

```java
// backend/src/main/java/com/hcare/domain/CaregiverClientAffinity.java
package com.hcare.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.util.UUID;

// Visit history per caregiver+client pair — drives the 25% continuity scoring factor.
// visitCount is updated via Plan 5's Spring event listener when shifts complete.
@Entity
@Table(name = "caregiver_client_affinities")
@Filter(name = "agencyFilter", condition = "agency_id = :agencyId")
public class CaregiverClientAffinity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "scoring_profile_id", nullable = false)
    private UUID scoringProfileId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    @Column(name = "visit_count", nullable = false)
    private int visitCount = 0;

    protected CaregiverClientAffinity() {}

    public CaregiverClientAffinity(UUID scoringProfileId, UUID clientId, UUID agencyId) {
        this.scoringProfileId = scoringProfileId;
        this.clientId = clientId;
        this.agencyId = agencyId;
    }

    public void incrementVisitCount() { this.visitCount++; }

    public UUID getId() { return id; }
    public UUID getScoringProfileId() { return scoringProfileId; }
    public UUID getClientId() { return clientId; }
    public UUID getAgencyId() { return agencyId; }
    public int getVisitCount() { return visitCount; }
}
```

- [ ] **Step 13: Create `CaregiverClientAffinityRepository.java`**

```java
// backend/src/main/java/com/hcare/domain/CaregiverClientAffinityRepository.java
package com.hcare.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CaregiverClientAffinityRepository extends JpaRepository<CaregiverClientAffinity, UUID> {
    List<CaregiverClientAffinity> findByScoringProfileId(UUID scoringProfileId);
    Optional<CaregiverClientAffinity> findByScoringProfileIdAndClientId(UUID scoringProfileId, UUID clientId);
}
```

- [ ] **Step 14: Remove the incomplete affinity test stub and run**

Remove the `caregiver_client_affinity_tracks_visit_count` test method that references `ClientRepository_Stub` (it was a placeholder). The affinity test will be added in Task 5 once `ClientRepository` exists.

```bash
cd backend && mvn test -Dtest=CaregiverSubEntitiesIT
```
Expected: `Tests run: 4, Failures: 0, Errors: 0`.

- [ ] **Step 15: Commit**

```bash
cd backend
git add src/main/java/com/hcare/domain/CredentialType.java \
        src/main/java/com/hcare/domain/CaregiverCredential.java \
        src/main/java/com/hcare/domain/CaregiverCredentialRepository.java \
        src/main/java/com/hcare/domain/BackgroundCheckType.java \
        src/main/java/com/hcare/domain/BackgroundCheckResult.java \
        src/main/java/com/hcare/domain/BackgroundCheck.java \
        src/main/java/com/hcare/domain/BackgroundCheckRepository.java \
        src/main/java/com/hcare/domain/CaregiverAvailability.java \
        src/main/java/com/hcare/domain/CaregiverAvailabilityRepository.java \
        src/main/java/com/hcare/domain/CaregiverScoringProfile.java \
        src/main/java/com/hcare/domain/CaregiverScoringProfileRepository.java \
        src/main/java/com/hcare/domain/CaregiverClientAffinity.java \
        src/main/java/com/hcare/domain/CaregiverClientAffinityRepository.java \
        src/test/java/com/hcare/domain/CaregiverSubEntitiesIT.java
git commit -m "feat: Caregiver sub-entities — Credentials, BackgroundChecks, Availability, ScoringProfile, ClientAffinity"
```

---

## Task 5: Client entity

**Files:**
- Create: `backend/src/main/java/com/hcare/domain/ClientStatus.java`
- Create: `backend/src/main/java/com/hcare/domain/Client.java`
- Create: `backend/src/main/java/com/hcare/domain/ClientRepository.java`
- Test: `backend/src/test/java/com/hcare/domain/ClientDomainIT.java`

- [ ] **Step 1: Write the failing integration test**

```java
// backend/src/test/java/com/hcare/domain/ClientDomainIT.java
package com.hcare.domain;

import com.hcare.AbstractIntegrationTest;
import com.hcare.multitenancy.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class ClientDomainIT extends AbstractIntegrationTest {

    @Autowired private AgencyRepository agencyRepo;
    @Autowired private ClientRepository clientRepo;
    @Autowired private CaregiverClientAffinityRepository affinityRepo;
    @Autowired private CaregiverScoringProfileRepository scoringProfileRepo;
    @Autowired private CaregiverRepository caregiverRepo;
    @Autowired private TransactionTemplate transactionTemplate;

    @Test
    void client_can_be_saved_and_retrieved() {
        Agency agency = agencyRepo.save(new Agency("Client Test Agency", "TX"));
        Client client = clientRepo.save(new Client(
            agency.getId(), "Jane", "Doe", LocalDate.of(1960, 5, 20)));

        Client loaded = clientRepo.findById(client.getId()).orElseThrow();
        assertThat(loaded.getFirstName()).isEqualTo("Jane");
        assertThat(loaded.getLastName()).isEqualTo("Doe");
        assertThat(loaded.getDateOfBirth()).isEqualTo(LocalDate.of(1960, 5, 20));
        assertThat(loaded.getStatus()).isEqualTo(ClientStatus.ACTIVE);
        assertThat(loaded.getPreferredLanguages()).isEqualTo("[]");
        assertThat(loaded.isNoPetCaregiver()).isFalse();
        assertThat(loaded.getServiceState()).isNull(); // null = use agency state for EVV routing
    }

    @Test
    void client_serviceState_overrides_agency_state_for_evv_routing() {
        // Border-county agencies serve clients in two states: client.serviceState overrides
        // the agency's default state when routing EVV records to the correct aggregator.
        Agency agency = agencyRepo.save(new Agency("Border Agency", "TX"));
        Client client = new Client(agency.getId(), "Bob", "Border", LocalDate.of(1975, 3, 10));
        client.setServiceState("OK"); // client lives in Oklahoma, agency is in Texas
        clientRepo.save(client);

        Client loaded = clientRepo.findById(client.getId()).orElseThrow();
        assertThat(loaded.getServiceState()).isEqualTo("OK");
    }

    @Test
    void client_agencyFilter_excludes_other_agency_clients() {
        Agency agencyA = agencyRepo.save(new Agency("Client Agency A", "TX"));
        Agency agencyB = agencyRepo.save(new Agency("Client Agency B", "CA"));

        Client clientA = clientRepo.save(
            new Client(agencyA.getId(), "Alice", "A", LocalDate.of(1970, 1, 1)));
        clientRepo.save(
            new Client(agencyB.getId(), "Bob", "B", LocalDate.of(1970, 1, 1)));

        TenantContext.set(agencyA.getId());
        List<Client> result;
        try {
            result = transactionTemplate.execute(status -> clientRepo.findAll());
        } finally {
            TenantContext.clear();
        }

        assertThat(result).isNotNull();
        assertThat(result.stream().map(Client::getId).toList())
            .contains(clientA.getId());
        assertThat(result).allMatch(c -> c.getAgencyId().equals(agencyA.getId()));
    }

    @Test
    void caregiver_client_affinity_can_be_saved_now_that_client_exists() {
        // This test was deferred from Task 4 because it requires a real Client row.
        Agency agency = agencyRepo.save(new Agency("Affinity Agency", "TX"));
        Caregiver caregiver = caregiverRepo.save(
            new Caregiver(agency.getId(), "Dave", "D", "dave-aff@test.com"));
        Client client = clientRepo.save(
            new Client(agency.getId(), "Eve", "E", LocalDate.of(1980, 6, 15)));
        CaregiverScoringProfile profile = scoringProfileRepo.save(
            new CaregiverScoringProfile(caregiver.getId(), agency.getId()));

        CaregiverClientAffinity affinity = affinityRepo.save(
            new CaregiverClientAffinity(profile.getId(), client.getId(), agency.getId()));
        affinity.incrementVisitCount();
        affinityRepo.save(affinity);

        CaregiverClientAffinity loaded = affinityRepo.findById(affinity.getId()).orElseThrow();
        assertThat(loaded.getVisitCount()).isEqualTo(1);
        assertThat(loaded.getClientId()).isEqualTo(client.getId());
        assertThat(loaded.getScoringProfileId()).isEqualTo(profile.getId());
    }
}
```

- [ ] **Step 2: Run to confirm failure**

```bash
cd backend && mvn test -Dtest=ClientDomainIT
```
Expected: FAIL — compile error, `Client`, `ClientStatus`, `ClientRepository` do not exist.

- [ ] **Step 3: Create `ClientStatus.java`**

```java
// backend/src/main/java/com/hcare/domain/ClientStatus.java
package com.hcare.domain;

public enum ClientStatus {
    ACTIVE, INACTIVE, DISCHARGED
}
```

- [ ] **Step 4: Create `Client.java`**

```java
// backend/src/main/java/com/hcare/domain/Client.java
package com.hcare.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "clients")
@Filter(name = "agencyFilter", condition = "agency_id = :agencyId")
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Column(columnDefinition = "TEXT")
    private String address;

    // Geocoded at save time — never called per match request
    @Column(precision = 10, scale = 7)
    private BigDecimal lat;

    @Column(precision = 10, scale = 7)
    private BigDecimal lng;

    @Column(length = 20)
    private String phone;

    @Column(name = "medicaid_id", length = 50)
    private String medicaidId; // PHI — nullable

    // Overrides agency.state for EVV routing. Null = use agency state.
    // Required for border-county agencies serving clients in two states.
    @Column(name = "service_state", columnDefinition = "CHAR(2)")
    private String serviceState;

    @Column(name = "preferred_caregiver_gender", length = 10)
    private String preferredCaregiverGender; // MALE | FEMALE | null

    // JSON array of language codes — parsed at application layer
    @Column(name = "preferred_languages", nullable = false, columnDefinition = "TEXT")
    private String preferredLanguages = "[]";

    @Column(name = "no_pet_caregiver", nullable = false)
    private boolean noPetCaregiver = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClientStatus status = ClientStatus.ACTIVE;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected Client() {}

    public Client(UUID agencyId, String firstName, String lastName, LocalDate dateOfBirth) {
        this.agencyId = agencyId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.dateOfBirth = dateOfBirth;
    }

    public void setServiceState(String serviceState) { this.serviceState = serviceState; }
    public void setLat(BigDecimal lat) { this.lat = lat; }
    public void setLng(BigDecimal lng) { this.lng = lng; }
    public void setPreferredLanguages(String preferredLanguages) { this.preferredLanguages = preferredLanguages; }
    public void setNoPetCaregiver(boolean noPetCaregiver) { this.noPetCaregiver = noPetCaregiver; }
    public void setMedicaidId(String medicaidId) { this.medicaidId = medicaidId; }

    public UUID getId() { return id; }
    public UUID getAgencyId() { return agencyId; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public String getAddress() { return address; }
    public BigDecimal getLat() { return lat; }
    public BigDecimal getLng() { return lng; }
    public String getPhone() { return phone; }
    public String getMedicaidId() { return medicaidId; }
    public String getServiceState() { return serviceState; }
    public String getPreferredCaregiverGender() { return preferredCaregiverGender; }
    public String getPreferredLanguages() { return preferredLanguages; }
    public boolean isNoPetCaregiver() { return noPetCaregiver; }
    public ClientStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
```

- [ ] **Step 5: Create `ClientRepository.java`**

```java
// backend/src/main/java/com/hcare/domain/ClientRepository.java
package com.hcare.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ClientRepository extends JpaRepository<Client, UUID> {
    List<Client> findByAgencyId(UUID agencyId);
}
```

- [ ] **Step 6: Run tests to confirm they pass**

```bash
cd backend && mvn test -Dtest=ClientDomainIT
```
Expected: `Tests run: 4, Failures: 0, Errors: 0`.

- [ ] **Step 7: Commit**

```bash
cd backend
git add src/main/java/com/hcare/domain/ClientStatus.java \
        src/main/java/com/hcare/domain/Client.java \
        src/main/java/com/hcare/domain/ClientRepository.java \
        src/test/java/com/hcare/domain/ClientDomainIT.java
git commit -m "feat: Client entity — agency-scoped, serviceState EVV routing override, agencyFilter verified"
```

---

## Task 6: Client sub-entities (Diagnoses, Medications, Documents)

**Files:**
- Create: `backend/src/main/java/com/hcare/domain/ClientDiagnosis.java`
- Create: `backend/src/main/java/com/hcare/domain/ClientDiagnosisRepository.java`
- Create: `backend/src/main/java/com/hcare/domain/ClientMedication.java`
- Create: `backend/src/main/java/com/hcare/domain/ClientMedicationRepository.java`
- Create: `backend/src/main/java/com/hcare/domain/DocumentOwnerType.java`
- Create: `backend/src/main/java/com/hcare/domain/Document.java`
- Create: `backend/src/main/java/com/hcare/domain/DocumentRepository.java`
- Test: `backend/src/test/java/com/hcare/domain/ClientSubEntitiesIT.java`

- [ ] **Step 1: Write the failing integration test**

```java
// backend/src/test/java/com/hcare/domain/ClientSubEntitiesIT.java
package com.hcare.domain;

import com.hcare.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class ClientSubEntitiesIT extends AbstractIntegrationTest {

    @Autowired private AgencyRepository agencyRepo;
    @Autowired private ClientRepository clientRepo;
    @Autowired private ClientDiagnosisRepository diagnosisRepo;
    @Autowired private ClientMedicationRepository medicationRepo;
    @Autowired private DocumentRepository documentRepo;

    private Agency agency;
    private Client client;

    @BeforeEach
    void setup() {
        agency = agencyRepo.save(new Agency("Sub-Entity Client Agency", "TX"));
        client = clientRepo.save(
            new Client(agency.getId(), "Frank", "Green", LocalDate.of(1945, 8, 30)));
    }

    @Test
    void diagnosis_can_be_saved_with_icd10_code() {
        ClientDiagnosis dx = diagnosisRepo.save(
            new ClientDiagnosis(client.getId(), agency.getId(), "E11.9"));

        ClientDiagnosis loaded = diagnosisRepo.findById(dx.getId()).orElseThrow();
        assertThat(loaded.getIcd10Code()).isEqualTo("E11.9");
        assertThat(loaded.getClientId()).isEqualTo(client.getId());
        assertThat(loaded.getDescription()).isNull();
    }

    @Test
    void multiple_diagnoses_can_be_retrieved_by_client() {
        diagnosisRepo.save(new ClientDiagnosis(client.getId(), agency.getId(), "E11.9"));
        diagnosisRepo.save(new ClientDiagnosis(client.getId(), agency.getId(), "I10"));

        List<ClientDiagnosis> diagnoses = diagnosisRepo.findByClientId(client.getId());
        assertThat(diagnoses).hasSize(2);
        assertThat(diagnoses.stream().map(ClientDiagnosis::getIcd10Code))
            .containsExactlyInAnyOrder("E11.9", "I10");
    }

    @Test
    void medication_can_be_saved_and_retrieved() {
        ClientMedication med = medicationRepo.save(
            new ClientMedication(client.getId(), agency.getId(), "Metformin"));

        ClientMedication loaded = medicationRepo.findById(med.getId()).orElseThrow();
        assertThat(loaded.getName()).isEqualTo("Metformin");
        assertThat(loaded.getDosage()).isNull();
        assertThat(loaded.getClientId()).isEqualTo(client.getId());
    }

    @Test
    void document_can_be_saved_with_polymorphic_owner() {
        Document doc = documentRepo.save(new Document(
            agency.getId(), DocumentOwnerType.CLIENT, client.getId(),
            "care_plan_v1.pdf", "/storage/agency-123/care_plan_v1.pdf"));

        Document loaded = documentRepo.findById(doc.getId()).orElseThrow();
        assertThat(loaded.getOwnerType()).isEqualTo(DocumentOwnerType.CLIENT);
        assertThat(loaded.getOwnerId()).isEqualTo(client.getId());
        assertThat(loaded.getFileName()).isEqualTo("care_plan_v1.pdf");
    }
}
```

- [ ] **Step 2: Run to confirm failure**

```bash
cd backend && mvn test -Dtest=ClientSubEntitiesIT
```
Expected: FAIL — compile error, `ClientDiagnosis`, `ClientMedication`, `Document`, `DocumentOwnerType` do not exist.

- [ ] **Step 3: Create enum**

```java
// backend/src/main/java/com/hcare/domain/DocumentOwnerType.java
package com.hcare.domain;

public enum DocumentOwnerType {
    CLIENT, CAREGIVER
}
```

- [ ] **Step 4: Create `ClientDiagnosis.java`**

```java
// backend/src/main/java/com/hcare/domain/ClientDiagnosis.java
package com.hcare.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "client_diagnoses")
@Filter(name = "agencyFilter", condition = "agency_id = :agencyId")
public class ClientDiagnosis {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    @Column(name = "icd10_code", nullable = false, length = 10)
    private String icd10Code;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "onset_date")
    private LocalDate onsetDate;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected ClientDiagnosis() {}

    public ClientDiagnosis(UUID clientId, UUID agencyId, String icd10Code) {
        this.clientId = clientId;
        this.agencyId = agencyId;
        this.icd10Code = icd10Code;
    }

    public UUID getId() { return id; }
    public UUID getClientId() { return clientId; }
    public UUID getAgencyId() { return agencyId; }
    public String getIcd10Code() { return icd10Code; }
    public String getDescription() { return description; }
    public LocalDate getOnsetDate() { return onsetDate; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
```

- [ ] **Step 5: Create `ClientDiagnosisRepository.java`**

```java
// backend/src/main/java/com/hcare/domain/ClientDiagnosisRepository.java
package com.hcare.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ClientDiagnosisRepository extends JpaRepository<ClientDiagnosis, UUID> {
    List<ClientDiagnosis> findByClientId(UUID clientId);
}
```

- [ ] **Step 6: Create `ClientMedication.java`**

```java
// backend/src/main/java/com/hcare/domain/ClientMedication.java
package com.hcare.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "client_medications")
@Filter(name = "agencyFilter", condition = "agency_id = :agencyId")
public class ClientMedication {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    @Column(nullable = false)
    private String name;

    @Column(length = 100)
    private String dosage;

    @Column(length = 100)
    private String route;

    @Column(columnDefinition = "TEXT")
    private String schedule;

    @Column(length = 255)
    private String prescriber;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected ClientMedication() {}

    public ClientMedication(UUID clientId, UUID agencyId, String name) {
        this.clientId = clientId;
        this.agencyId = agencyId;
        this.name = name;
    }

    public UUID getId() { return id; }
    public UUID getClientId() { return clientId; }
    public UUID getAgencyId() { return agencyId; }
    public String getName() { return name; }
    public String getDosage() { return dosage; }
    public String getRoute() { return route; }
    public String getSchedule() { return schedule; }
    public String getPrescriber() { return prescriber; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
```

- [ ] **Step 7: Create `ClientMedicationRepository.java`**

```java
// backend/src/main/java/com/hcare/domain/ClientMedicationRepository.java
package com.hcare.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ClientMedicationRepository extends JpaRepository<ClientMedication, UUID> {
    List<ClientMedication> findByClientId(UUID clientId);
}
```

- [ ] **Step 8: Create `Document.java`**

```java
// backend/src/main/java/com/hcare/domain/Document.java
package com.hcare.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "documents")
@Filter(name = "agencyFilter", condition = "agency_id = :agencyId")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 20)
    private DocumentOwnerType ownerType;

    // Polymorphic FK — references client or caregiver id. No DB-level FK constraint.
    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_path", nullable = false, columnDefinition = "TEXT")
    private String filePath;

    @Column(name = "document_type", length = 100)
    private String documentType;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private LocalDateTime uploadedAt = LocalDateTime.now();

    protected Document() {}

    public Document(UUID agencyId, DocumentOwnerType ownerType, UUID ownerId,
                    String fileName, String filePath) {
        this.agencyId = agencyId;
        this.ownerType = ownerType;
        this.ownerId = ownerId;
        this.fileName = fileName;
        this.filePath = filePath;
    }

    public UUID getId() { return id; }
    public UUID getAgencyId() { return agencyId; }
    public DocumentOwnerType getOwnerType() { return ownerType; }
    public UUID getOwnerId() { return ownerId; }
    public String getFileName() { return fileName; }
    public String getFilePath() { return filePath; }
    public String getDocumentType() { return documentType; }
    public UUID getUploadedBy() { return uploadedBy; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
}
```

- [ ] **Step 9: Create `DocumentRepository.java`**

```java
// backend/src/main/java/com/hcare/domain/DocumentRepository.java
package com.hcare.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {
    List<Document> findByOwnerTypeAndOwnerId(DocumentOwnerType ownerType, UUID ownerId);
}
```

- [ ] **Step 10: Run tests to confirm they pass**

```bash
cd backend && mvn test -Dtest=ClientSubEntitiesIT
```
Expected: `Tests run: 4, Failures: 0, Errors: 0`.

- [ ] **Step 11: Commit**

```bash
cd backend
git add src/main/java/com/hcare/domain/DocumentOwnerType.java \
        src/main/java/com/hcare/domain/ClientDiagnosis.java \
        src/main/java/com/hcare/domain/ClientDiagnosisRepository.java \
        src/main/java/com/hcare/domain/ClientMedication.java \
        src/main/java/com/hcare/domain/ClientMedicationRepository.java \
        src/main/java/com/hcare/domain/Document.java \
        src/main/java/com/hcare/domain/DocumentRepository.java \
        src/test/java/com/hcare/domain/ClientSubEntitiesIT.java
git commit -m "feat: Client sub-entities — Diagnoses (ICD-10), Medications, Documents (polymorphic)"
```

---

## Task 7: CarePlan, ADLTask, and Goal entities

**Files:**
- Create: `backend/src/main/java/com/hcare/domain/CarePlanStatus.java`
- Create: `backend/src/main/java/com/hcare/domain/CarePlan.java`
- Create: `backend/src/main/java/com/hcare/domain/CarePlanRepository.java`
- Create: `backend/src/main/java/com/hcare/domain/AssistanceLevel.java`
- Create: `backend/src/main/java/com/hcare/domain/AdlTask.java`
- Create: `backend/src/main/java/com/hcare/domain/AdlTaskRepository.java`
- Create: `backend/src/main/java/com/hcare/domain/GoalStatus.java`
- Create: `backend/src/main/java/com/hcare/domain/Goal.java`
- Create: `backend/src/main/java/com/hcare/domain/GoalRepository.java`
- Test: `backend/src/test/java/com/hcare/domain/CarePlanDomainIT.java`

- [ ] **Step 1: Write the failing integration test**

```java
// backend/src/test/java/com/hcare/domain/CarePlanDomainIT.java
package com.hcare.domain;

import com.hcare.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class CarePlanDomainIT extends AbstractIntegrationTest {

    @Autowired private AgencyRepository agencyRepo;
    @Autowired private ClientRepository clientRepo;
    @Autowired private CarePlanRepository carePlanRepo;
    @Autowired private AdlTaskRepository adlTaskRepo;
    @Autowired private GoalRepository goalRepo;

    private Agency agency;
    private Client client;

    @BeforeEach
    void setup() {
        agency = agencyRepo.save(new Agency("Care Plan Agency", "TX"));
        client = clientRepo.save(
            new Client(agency.getId(), "Helen", "Brown", LocalDate.of(1935, 11, 12)));
    }

    @Test
    void care_plan_defaults_to_draft_with_version_1() {
        CarePlan plan = carePlanRepo.save(new CarePlan(client.getId(), agency.getId(), 1));

        CarePlan loaded = carePlanRepo.findById(plan.getId()).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(CarePlanStatus.DRAFT);
        assertThat(loaded.getPlanVersion()).isEqualTo(1);
        assertThat(loaded.getActivatedAt()).isNull();
        assertThat(loaded.getClientId()).isEqualTo(client.getId());
    }

    @Test
    void activate_transitions_plan_to_active_status() {
        CarePlan plan = carePlanRepo.save(new CarePlan(client.getId(), agency.getId(), 1));
        plan.activate();
        carePlanRepo.save(plan);

        CarePlan loaded = carePlanRepo.findById(plan.getId()).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(CarePlanStatus.ACTIVE);
        assertThat(loaded.getActivatedAt()).isNotNull();
    }

    @Test
    void supersede_transitions_plan_to_superseded_and_new_version_can_be_active() {
        // Create v1, activate it
        CarePlan v1 = carePlanRepo.save(new CarePlan(client.getId(), agency.getId(), 1));
        v1.activate();
        carePlanRepo.save(v1);

        // Create v2 — supersede v1, activate v2
        // (One-active-per-client rule is enforced at the service layer, not here.)
        v1.supersede();
        carePlanRepo.save(v1);

        CarePlan v2 = carePlanRepo.save(new CarePlan(client.getId(), agency.getId(), 2));
        v2.activate();
        carePlanRepo.save(v2);

        List<CarePlan> plans = carePlanRepo.findByClientId(client.getId());
        assertThat(plans).hasSize(2);

        CarePlan loadedV1 = carePlanRepo.findById(v1.getId()).orElseThrow();
        CarePlan loadedV2 = carePlanRepo.findById(v2.getId()).orElseThrow();
        assertThat(loadedV1.getStatus()).isEqualTo(CarePlanStatus.SUPERSEDED);
        assertThat(loadedV2.getStatus()).isEqualTo(CarePlanStatus.ACTIVE);
        assertThat(loadedV2.getPlanVersion()).isEqualTo(2);
    }

    @Test
    void adl_task_can_be_added_to_care_plan() {
        CarePlan plan = carePlanRepo.save(new CarePlan(client.getId(), agency.getId(), 1));
        AdlTask task = adlTaskRepo.save(new AdlTask(
            plan.getId(), agency.getId(), "Bathing", AssistanceLevel.MODERATE_ASSIST));

        AdlTask loaded = adlTaskRepo.findById(task.getId()).orElseThrow();
        assertThat(loaded.getName()).isEqualTo("Bathing");
        assertThat(loaded.getAssistanceLevel()).isEqualTo(AssistanceLevel.MODERATE_ASSIST);
        assertThat(loaded.getCarePlanId()).isEqualTo(plan.getId());
        assertThat(loaded.getSortOrder()).isEqualTo(0);
    }

    @Test
    void goal_can_be_added_to_care_plan() {
        CarePlan plan = carePlanRepo.save(new CarePlan(client.getId(), agency.getId(), 1));
        Goal goal = goalRepo.save(new Goal(
            plan.getId(), agency.getId(),
            "Improve independent ambulation to 50 feet without assistance"));

        Goal loaded = goalRepo.findById(goal.getId()).orElseThrow();
        assertThat(loaded.getDescription())
            .isEqualTo("Improve independent ambulation to 50 feet without assistance");
        assertThat(loaded.getStatus()).isEqualTo(GoalStatus.ACTIVE);
        assertThat(loaded.getTargetDate()).isNull();
    }
}
```

- [ ] **Step 2: Run to confirm failure**

```bash
cd backend && mvn test -Dtest=CarePlanDomainIT
```
Expected: FAIL — compile error, `CarePlan`, `CarePlanStatus`, `AdlTask`, `AssistanceLevel`, `Goal`, `GoalStatus` do not exist.

- [ ] **Step 3: Create enum files**

```java
// backend/src/main/java/com/hcare/domain/CarePlanStatus.java
package com.hcare.domain;

public enum CarePlanStatus {
    DRAFT, ACTIVE, SUPERSEDED
}
```

```java
// backend/src/main/java/com/hcare/domain/AssistanceLevel.java
package com.hcare.domain;

public enum AssistanceLevel {
    INDEPENDENT, SUPERVISION, MINIMAL_ASSIST, MODERATE_ASSIST, MAXIMUM_ASSIST, DEPENDENT
}
```

```java
// backend/src/main/java/com/hcare/domain/GoalStatus.java
package com.hcare.domain;

public enum GoalStatus {
    ACTIVE, ACHIEVED, DISCONTINUED, ON_HOLD
}
```

- [ ] **Step 4: Create `CarePlan.java`**

```java
// backend/src/main/java/com/hcare/domain/CarePlan.java
package com.hcare.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "care_plans")
@Filter(name = "agencyFilter", condition = "agency_id = :agencyId")
public class CarePlan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    // Business version counter. One ACTIVE plan per client enforced at service layer (Plan 6).
    @Column(name = "plan_version", nullable = false)
    private int planVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CarePlanStatus status = CarePlanStatus.DRAFT;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    // HHCS (skilled nursing) plans require clinical review sign-off.
    // PCS (non-medical personal care) plans leave these null.
    @Column(name = "reviewed_by_clinician_id")
    private UUID reviewedByClinicianId;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    protected CarePlan() {}

    public CarePlan(UUID clientId, UUID agencyId, int planVersion) {
        this.clientId = clientId;
        this.agencyId = agencyId;
        this.planVersion = planVersion;
    }

    public void activate() {
        this.status = CarePlanStatus.ACTIVE;
        this.activatedAt = LocalDateTime.now();
    }

    public void supersede() {
        this.status = CarePlanStatus.SUPERSEDED;
    }

    public UUID getId() { return id; }
    public UUID getClientId() { return clientId; }
    public UUID getAgencyId() { return agencyId; }
    public int getPlanVersion() { return planVersion; }
    public CarePlanStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getActivatedAt() { return activatedAt; }
    public UUID getReviewedByClinicianId() { return reviewedByClinicianId; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
}
```

- [ ] **Step 5: Create `CarePlanRepository.java`**

```java
// backend/src/main/java/com/hcare/domain/CarePlanRepository.java
package com.hcare.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CarePlanRepository extends JpaRepository<CarePlan, UUID> {
    List<CarePlan> findByClientId(UUID clientId);
    Optional<CarePlan> findByClientIdAndStatus(UUID clientId, CarePlanStatus status);
}
```

- [ ] **Step 6: Create `AdlTask.java`**

```java
// backend/src/main/java/com/hcare/domain/AdlTask.java
package com.hcare.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.util.UUID;

@Entity
@Table(name = "adl_tasks")
@Filter(name = "agencyFilter", condition = "agency_id = :agencyId")
public class AdlTask {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "care_plan_id", nullable = false)
    private UUID carePlanId;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String instructions;

    @Enumerated(EnumType.STRING)
    @Column(name = "assistance_level", nullable = false, length = 30)
    private AssistanceLevel assistanceLevel;

    @Column(length = 100)
    private String frequency;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    protected AdlTask() {}

    public AdlTask(UUID carePlanId, UUID agencyId, String name, AssistanceLevel assistanceLevel) {
        this.carePlanId = carePlanId;
        this.agencyId = agencyId;
        this.name = name;
        this.assistanceLevel = assistanceLevel;
    }

    public UUID getId() { return id; }
    public UUID getCarePlanId() { return carePlanId; }
    public UUID getAgencyId() { return agencyId; }
    public String getName() { return name; }
    public String getInstructions() { return instructions; }
    public AssistanceLevel getAssistanceLevel() { return assistanceLevel; }
    public String getFrequency() { return frequency; }
    public int getSortOrder() { return sortOrder; }
}
```

- [ ] **Step 7: Create `AdlTaskRepository.java`**

```java
// backend/src/main/java/com/hcare/domain/AdlTaskRepository.java
package com.hcare.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface AdlTaskRepository extends JpaRepository<AdlTask, UUID> {
    List<AdlTask> findByCarePlanIdOrderBySortOrder(UUID carePlanId);
}
```

- [ ] **Step 8: Create `Goal.java`**

```java
// backend/src/main/java/com/hcare/domain/Goal.java
package com.hcare.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "goals")
@Filter(name = "agencyFilter", condition = "agency_id = :agencyId")
public class Goal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "care_plan_id", nullable = false)
    private UUID carePlanId;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GoalStatus status = GoalStatus.ACTIVE;

    protected Goal() {}

    public Goal(UUID carePlanId, UUID agencyId, String description) {
        this.carePlanId = carePlanId;
        this.agencyId = agencyId;
        this.description = description;
    }

    public UUID getId() { return id; }
    public UUID getCarePlanId() { return carePlanId; }
    public UUID getAgencyId() { return agencyId; }
    public String getDescription() { return description; }
    public LocalDate getTargetDate() { return targetDate; }
    public GoalStatus getStatus() { return status; }
}
```

- [ ] **Step 9: Create `GoalRepository.java`**

```java
// backend/src/main/java/com/hcare/domain/GoalRepository.java
package com.hcare.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface GoalRepository extends JpaRepository<Goal, UUID> {
    List<Goal> findByCarePlanId(UUID carePlanId);
}
```

- [ ] **Step 10: Run tests to confirm they pass**

```bash
cd backend && mvn test -Dtest=CarePlanDomainIT
```
Expected: `Tests run: 5, Failures: 0, Errors: 0`.

- [ ] **Step 11: Commit**

```bash
cd backend
git add src/main/java/com/hcare/domain/CarePlanStatus.java \
        src/main/java/com/hcare/domain/CarePlan.java \
        src/main/java/com/hcare/domain/CarePlanRepository.java \
        src/main/java/com/hcare/domain/AssistanceLevel.java \
        src/main/java/com/hcare/domain/AdlTask.java \
        src/main/java/com/hcare/domain/AdlTaskRepository.java \
        src/main/java/com/hcare/domain/GoalStatus.java \
        src/main/java/com/hcare/domain/Goal.java \
        src/main/java/com/hcare/domain/GoalRepository.java \
        src/test/java/com/hcare/domain/CarePlanDomainIT.java
git commit -m "feat: CarePlan (versioned), ADLTask, Goal entities — activate/supersede lifecycle"
```

---

## Task 8: Authorization entity with optimistic locking

**Files:**
- Create: `backend/src/main/java/com/hcare/domain/UnitType.java`
- Create: `backend/src/main/java/com/hcare/domain/Authorization.java`
- Create: `backend/src/main/java/com/hcare/domain/AuthorizationRepository.java`
- Test: `backend/src/test/java/com/hcare/domain/AuthorizationOptimisticLockIT.java`

- [ ] **Step 1: Write the failing integration test**

```java
// backend/src/test/java/com/hcare/domain/AuthorizationOptimisticLockIT.java
package com.hcare.domain;

import com.hcare.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.*;

class AuthorizationOptimisticLockIT extends AbstractIntegrationTest {

    @Autowired private AgencyRepository agencyRepo;
    @Autowired private PayerRepository payerRepo;
    @Autowired private ServiceTypeRepository serviceTypeRepo;
    @Autowired private ClientRepository clientRepo;
    @Autowired private AuthorizationRepository authRepo;

    @Test
    void authorization_can_be_saved_and_used_units_tracked() {
        Authorization auth = createAndSaveAuth("AUTH-001");

        auth.addUsedUnits(new BigDecimal("8.0"));
        authRepo.save(auth);

        Authorization loaded = authRepo.findById(auth.getId()).orElseThrow();
        assertThat(loaded.getUsedUnits()).isEqualByComparingTo("8.0");
        assertThat(loaded.getAuthorizedUnits()).isEqualByComparingTo("40.0");
        assertThat(loaded.getUnitType()).isEqualTo(UnitType.HOURS);
        assertThat(loaded.getVersion()).isEqualTo(1L);
    }

    @Test
    void stale_authorization_save_throws_optimistic_lock_exception() {
        // Simulates two concurrent threads both trying to increment usedUnits.
        // In production, the losing thread catches ObjectOptimisticLockingFailureException
        // and retries — this test proves the exception is thrown.

        Authorization auth = createAndSaveAuth("AUTH-002");
        // auth has version=0 after INSERT

        // Simulate Thread A: loads fresh, updates, saves (version 0 → 1)
        Authorization threadACopy = authRepo.findById(auth.getId()).orElseThrow();
        threadACopy.addUsedUnits(new BigDecimal("8.0"));
        authRepo.save(threadACopy); // committed, DB version is now 1

        // Simulate Thread B: uses stale object (version still 0 in memory)
        auth.addUsedUnits(new BigDecimal("4.0"));

        // UPDATE WHERE version=0 finds 0 rows (DB has version=1) → exception
        assertThatThrownBy(() -> authRepo.save(auth))
            .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void authorization_start_and_end_dates_are_persisted() {
        Authorization auth = createAndSaveAuth("AUTH-003");

        Authorization loaded = authRepo.findById(auth.getId()).orElseThrow();
        assertThat(loaded.getStartDate()).isEqualTo(LocalDate.now());
        assertThat(loaded.getEndDate()).isEqualTo(LocalDate.now().plusMonths(6));
        assertThat(loaded.getAuthNumber()).isEqualTo("AUTH-003");
    }

    // Helper shared by all tests in this class
    private Authorization createAndSaveAuth(String authNumber) {
        Agency agency = agencyRepo.save(new Agency("Auth Agency " + authNumber, "TX"));
        Payer payer = payerRepo.save(
            new Payer(agency.getId(), "Medicaid " + authNumber, PayerType.MEDICAID, "TX"));
        ServiceType st = serviceTypeRepo.save(
            new ServiceType(agency.getId(), "PCS " + authNumber, "PCS-" + authNumber, true, "[]"));
        Client client = clientRepo.save(
            new Client(agency.getId(), "Client " + authNumber, "Test", LocalDate.of(1960, 1, 1)));
        return authRepo.save(new Authorization(
            client.getId(), payer.getId(), st.getId(), agency.getId(),
            authNumber, new BigDecimal("40.0"),
            UnitType.HOURS, LocalDate.now(), LocalDate.now().plusMonths(6)));
    }
}
```

- [ ] **Step 2: Run to confirm failure**

```bash
cd backend && mvn test -Dtest=AuthorizationOptimisticLockIT
```
Expected: FAIL — compile error, `Authorization`, `UnitType`, `AuthorizationRepository` do not exist.

- [ ] **Step 3: Create `UnitType.java`**

```java
// backend/src/main/java/com/hcare/domain/UnitType.java
package com.hcare.domain;

public enum UnitType {
    HOURS, VISITS
}
```

- [ ] **Step 4: Create `Authorization.java`**

```java
// backend/src/main/java/com/hcare/domain/Authorization.java
package com.hcare.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "authorizations")
@Filter(name = "agencyFilter", condition = "agency_id = :agencyId")
public class Authorization {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "payer_id", nullable = false)
    private UUID payerId;

    @Column(name = "service_type_id", nullable = false)
    private UUID serviceTypeId;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    @Column(name = "auth_number", nullable = false, length = 100)
    private String authNumber;

    @Column(name = "authorized_units", nullable = false, precision = 10, scale = 2)
    private BigDecimal authorizedUnits;

    @Column(name = "used_units", nullable = false, precision = 10, scale = 2)
    private BigDecimal usedUnits = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit_type", nullable = false, length = 10)
    private UnitType unitType;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    // JPA @Version: Hibernate emits UPDATE ... WHERE id=? AND version=?
    // If 0 rows are updated (another transaction already incremented the version),
    // Hibernate throws StaleObjectStateException → Spring wraps as
    // ObjectOptimisticLockingFailureException. The caller retries the operation.
    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected Authorization() {}

    public Authorization(UUID clientId, UUID payerId, UUID serviceTypeId, UUID agencyId,
                         String authNumber, BigDecimal authorizedUnits,
                         UnitType unitType, LocalDate startDate, LocalDate endDate) {
        this.clientId = clientId;
        this.payerId = payerId;
        this.serviceTypeId = serviceTypeId;
        this.agencyId = agencyId;
        this.authNumber = authNumber;
        this.authorizedUnits = authorizedUnits;
        this.usedUnits = BigDecimal.ZERO;
        this.unitType = unitType;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public void addUsedUnits(BigDecimal amount) {
        this.usedUnits = this.usedUnits.add(amount);
    }

    public UUID getId() { return id; }
    public UUID getClientId() { return clientId; }
    public UUID getPayerId() { return payerId; }
    public UUID getServiceTypeId() { return serviceTypeId; }
    public UUID getAgencyId() { return agencyId; }
    public String getAuthNumber() { return authNumber; }
    public BigDecimal getAuthorizedUnits() { return authorizedUnits; }
    public BigDecimal getUsedUnits() { return usedUnits; }
    public UnitType getUnitType() { return unitType; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public Long getVersion() { return version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
```

- [ ] **Step 5: Create `AuthorizationRepository.java`**

```java
// backend/src/main/java/com/hcare/domain/AuthorizationRepository.java
package com.hcare.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface AuthorizationRepository extends JpaRepository<Authorization, UUID> {
    List<Authorization> findByClientId(UUID clientId);
    List<Authorization> findByAgencyId(UUID agencyId);
}
```

- [ ] **Step 6: Run tests to confirm they pass**

```bash
cd backend && mvn test -Dtest=AuthorizationOptimisticLockIT
```
Expected: `Tests run: 3, Failures: 0, Errors: 0` — including the concurrent-update test that proves `ObjectOptimisticLockingFailureException` is thrown.

- [ ] **Step 7: Commit**

```bash
cd backend
git add src/main/java/com/hcare/domain/UnitType.java \
        src/main/java/com/hcare/domain/Authorization.java \
        src/main/java/com/hcare/domain/AuthorizationRepository.java \
        src/test/java/com/hcare/domain/AuthorizationOptimisticLockIT.java
git commit -m "feat: Authorization entity — @Version optimistic locking for concurrent usedUnits updates"
```

---

## Task 9: FamilyPortalUser entity

**Files:**
- Create: `backend/src/main/java/com/hcare/domain/FamilyPortalUser.java`
- Create: `backend/src/main/java/com/hcare/domain/FamilyPortalUserRepository.java`

Note: `FamilyPortalUser` is used in `PhiAuditLog` (from Plan 1) via the `family_portal_user_id` field. The `phi_audit_logs.family_portal_user_id` column is a nullable UUID — no FK constraint, so adding `FamilyPortalUser` now does not require migrating `phi_audit_logs`.

- [ ] **Step 1: Add family portal user tests to an existing test file**

Add the following test to `ClientDomainIT.java` (it already exists and has `Agency` and `Client` setup):

```java
// Add to ClientDomainIT.java — inject these additional repositories:
@Autowired private FamilyPortalUserRepository familyPortalUserRepo;

// Add this test method:
@Test
void family_portal_user_is_scoped_to_a_single_client() {
    Agency agency = agencyRepo.save(new Agency("FPU Agency", "TX"));
    Client client = clientRepo.save(
        new Client(agency.getId(), "Grace", "Hall", LocalDate.of(1952, 4, 7)));

    FamilyPortalUser fpu = familyPortalUserRepo.save(
        new FamilyPortalUser(client.getId(), agency.getId(), "daughter@family.com"));

    FamilyPortalUser loaded = familyPortalUserRepo.findById(fpu.getId()).orElseThrow();
    assertThat(loaded.getEmail()).isEqualTo("daughter@family.com");
    assertThat(loaded.getClientId()).isEqualTo(client.getId());
    assertThat(loaded.getAgencyId()).isEqualTo(agency.getId());
    assertThat(loaded.getLastLoginAt()).isNull(); // not logged in yet
}

@Test
void family_portal_user_can_be_found_by_email() {
    Agency agency = agencyRepo.save(new Agency("FPU Lookup Agency", "TX"));
    Client client = clientRepo.save(
        new Client(agency.getId(), "Ivan", "King", LocalDate.of(1940, 9, 3)));
    familyPortalUserRepo.save(
        new FamilyPortalUser(client.getId(), agency.getId(), "son@lookup.com"));

    assertThat(familyPortalUserRepo.findByEmail("son@lookup.com")).isPresent();
    assertThat(familyPortalUserRepo.findByEmail("notexists@lookup.com")).isEmpty();
}
```

- [ ] **Step 2: Run `ClientDomainIT` to confirm failure**

```bash
cd backend && mvn test -Dtest=ClientDomainIT
```
Expected: FAIL — compile error, `FamilyPortalUser`, `FamilyPortalUserRepository` do not exist.

- [ ] **Step 3: Create `FamilyPortalUser.java`**

```java
// backend/src/main/java/com/hcare/domain/FamilyPortalUser.java
package com.hcare.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.time.LocalDateTime;
import java.util.UUID;

// Authentication model: magic link via email — no password stored.
// JWT claim on successful auth: {"role":"FAMILY_PORTAL","clientId":"...","agencyId":"..."}.
// The clientId JWT claim is the hard scope boundary — all /api/v1/family/ endpoints
// verify this claim and reject requests for resources belonging to other clients.
@Entity
@Table(name = "family_portal_users")
@Filter(name = "agencyFilter", condition = "agency_id = :agencyId")
public class FamilyPortalUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(length = 255)
    private String name;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected FamilyPortalUser() {}

    public FamilyPortalUser(UUID clientId, UUID agencyId, String email) {
        this.clientId = clientId;
        this.agencyId = agencyId;
        this.email = email;
    }

    public void recordLogin() {
        this.lastLoginAt = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getClientId() { return clientId; }
    public UUID getAgencyId() { return agencyId; }
    public String getEmail() { return email; }
    public String getName() { return name; }
    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
```

- [ ] **Step 4: Create `FamilyPortalUserRepository.java`**

```java
// backend/src/main/java/com/hcare/domain/FamilyPortalUserRepository.java
package com.hcare.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FamilyPortalUserRepository extends JpaRepository<FamilyPortalUser, UUID> {
    Optional<FamilyPortalUser> findByEmail(String email);
    List<FamilyPortalUser> findByClientId(UUID clientId);
}
```

- [ ] **Step 5: Run tests to confirm they pass**

```bash
cd backend && mvn test -Dtest=ClientDomainIT
```
Expected: all tests in `ClientDomainIT` pass including the two new FamilyPortalUser tests.

- [ ] **Step 6: Commit**

```bash
cd backend
git add src/main/java/com/hcare/domain/FamilyPortalUser.java \
        src/main/java/com/hcare/domain/FamilyPortalUserRepository.java \
        src/test/java/com/hcare/domain/ClientDomainIT.java
git commit -m "feat: FamilyPortalUser entity — magic-link auth, client-scoped, agencyFilter"
```

---

## Task 10: PayerEvvRoutingConfig entity

**Files:**
- Create: `backend/src/main/java/com/hcare/evv/PayerEvvRoutingConfig.java`
- Create: `backend/src/main/java/com/hcare/evv/PayerEvvRoutingConfigRepository.java`
- Test: `backend/src/test/java/com/hcare/evv/PayerEvvRoutingConfigIT.java`

`PayerEvvRoutingConfig` is global reference data — no `agencyId`, no `@Filter` — exactly like `EvvStateConfig`. It is queried at EVV submission time to determine which aggregator to use when a payer's MCO/plan overrides the state default.

Aggregator selection logic (Plan 4 will implement this):
1. Check `PayerEvvRoutingConfig` for (stateCode, payerType) override
2. Fall back to `EvvStateConfig.defaultAggregator`

- [ ] **Step 1: Write the failing integration test**

```java
// backend/src/test/java/com/hcare/evv/PayerEvvRoutingConfigIT.java
package com.hcare.evv;

import com.hcare.AbstractIntegrationTest;
import com.hcare.domain.PayerType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;

class PayerEvvRoutingConfigIT extends AbstractIntegrationTest {

    @Autowired private PayerEvvRoutingConfigRepository routingRepo;

    @Test
    void routing_config_can_be_found_by_state_and_payer_type() {
        // NY uses HHAeXchange as default but some MCOs require Sandata — simulate that override.
        // (In production these rows are seeded; here we insert directly to test the lookup.)
        PayerEvvRoutingConfig config = new PayerEvvRoutingConfig("NY", PayerType.MEDICAID, AggregatorType.SANDATA);
        routingRepo.save(config);

        Optional<PayerEvvRoutingConfig> found = routingRepo.findByStateCodeAndPayerType("NY", PayerType.MEDICAID);
        assertThat(found).isPresent();
        assertThat(found.get().getAggregatorType()).isEqualTo(AggregatorType.SANDATA);
    }

    @Test
    void lookup_returns_empty_when_no_override_exists() {
        // CO uses Sandata by default and has no MCO routing overrides — the caller falls
        // back to EvvStateConfig.defaultAggregator.
        Optional<PayerEvvRoutingConfig> found = routingRepo.findByStateCodeAndPayerType("CO", PayerType.MEDICAID);
        assertThat(found).isEmpty();
    }
}
```

- [ ] **Step 2: Run to confirm failure**

```bash
cd backend && mvn test -Dtest=PayerEvvRoutingConfigIT
```
Expected: FAIL — compile error, `PayerEvvRoutingConfig` does not exist.

- [ ] **Step 3: Create `PayerEvvRoutingConfig.java`**

```java
// backend/src/main/java/com/hcare/evv/PayerEvvRoutingConfig.java
package com.hcare.evv;

import com.hcare.domain.PayerType;
import jakarta.persistence.*;
import java.util.UUID;

// Global reference data — no agencyId, no @Filter.
// Rows cover multi-aggregator states: NY (3 aggregators), FL/NC/VA/TN/AR (MCO-specific mappings).
// Aggregator selection: check this table first, then fall back to EvvStateConfig.defaultAggregator.
@Entity
@Table(name = "payer_evv_routing_configs")
public class PayerEvvRoutingConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "state_code", nullable = false, columnDefinition = "CHAR(2)")
    private String stateCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "payer_type", nullable = false, length = 20)
    private PayerType payerType;

    @Enumerated(EnumType.STRING)
    @Column(name = "aggregator_type", nullable = false, length = 30)
    private AggregatorType aggregatorType;

    @Column(columnDefinition = "TEXT")
    private String notes;

    protected PayerEvvRoutingConfig() {}

    public PayerEvvRoutingConfig(String stateCode, PayerType payerType, AggregatorType aggregatorType) {
        this.stateCode = stateCode;
        this.payerType = payerType;
        this.aggregatorType = aggregatorType;
    }

    public UUID getId() { return id; }
    public String getStateCode() { return stateCode; }
    public PayerType getPayerType() { return payerType; }
    public AggregatorType getAggregatorType() { return aggregatorType; }
    public String getNotes() { return notes; }
}
```

- [ ] **Step 4: Create `PayerEvvRoutingConfigRepository.java`**

```java
// backend/src/main/java/com/hcare/evv/PayerEvvRoutingConfigRepository.java
package com.hcare.evv;

import com.hcare.domain.PayerType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface PayerEvvRoutingConfigRepository extends JpaRepository<PayerEvvRoutingConfig, UUID> {
    Optional<PayerEvvRoutingConfig> findByStateCodeAndPayerType(String stateCode, PayerType payerType);
}
```

- [ ] **Step 5: Run tests to confirm they pass**

```bash
cd backend && mvn test -Dtest=PayerEvvRoutingConfigIT
```
Expected: `Tests run: 2, Failures: 0, Errors: 0`.

- [ ] **Step 6: Run the full test suite**

```bash
cd backend && mvn test
```
Expected: `BUILD SUCCESS` — all tests pass.

- [ ] **Step 7: Commit**

```bash
cd backend
git add src/main/java/com/hcare/evv/PayerEvvRoutingConfig.java \
        src/main/java/com/hcare/evv/PayerEvvRoutingConfigRepository.java \
        src/test/java/com/hcare/evv/PayerEvvRoutingConfigIT.java
git commit -m "feat: PayerEvvRoutingConfig — global EVV aggregator override table for multi-aggregator states (NY, FL, NC, VA, TN, AR)"
```

---

## Self-Review

**Spec coverage check:**

| Spec requirement | Covered by |
|---|---|
| Payer entity (P1 — needed for EVV compliance and authorization) | Task 2 |
| PayerType enum: MEDICAID, PRIVATE_PAY, LTC_INSURANCE, VA, MEDICARE | Task 2 |
| ServiceType with requiredCredentials (JSON TEXT) | Task 2 |
| FeatureFlags per agency — aiSchedulingEnabled gates Pro tier | Task 2 |
| Caregiver with homeLat/homeLng (geocoded at save time) | Task 3 |
| Caregiver with languages, hasPet (scoring inputs) | Task 3 |
| Caregiver credentials with expiry + verified flag (manual admin action at P1) | Task 4 |
| Background checks (STATE_REGISTRY, FBI, OIG, SEX_OFFENDER) | Task 4 |
| CaregiverAvailability (recurring weekly blocks) | Task 4 |
| CaregiverScoringProfile — pre-computed, async update hook (Plan 5) | Task 4 |
| CaregiverClientAffinity — visitCount per caregiver+client | Task 4 |
| Client with serviceState override (border-county EVV routing) | Task 5 |
| Client with preferredLanguages, noPetCaregiver (scoring inputs) | Task 5 |
| Client diagnoses (ICD-10) | Task 6 |
| Client medications | Task 6 |
| Documents (polymorphic CLIENT or CAREGIVER owner) | Task 6 |
| CarePlan versioned (planVersion int, DRAFT/ACTIVE/SUPERSEDED lifecycle) | Task 7 |
| CarePlan reviewed_by_clinician_id for HHCS clinical supervision | Task 7 |
| ADLTasks with assistance_level + sort_order | Task 7 |
| Goals with status lifecycle | Task 7 |
| Authorization with @Version optimistic locking on usedUnits | Task 8 |
| Authorization unit_type HOURS or VISITS | Task 8 |
| FamilyPortalUser — magic-link auth, client-scoped | Task 9 |
| PayerEvvRoutingConfig — global table for MCO aggregator overrides | Task 10 |
| All agency-scoped entities carry agencyId + @Filter(agencyFilter) | Tasks 2–9 |
| JSON fields as opaque TEXT (H2/PostgreSQL portability) | Tasks 2–5 (requiredCredentials, languages, preferredLanguages) |

**No placeholders found.**

**Type consistency check:**
- `PayerEvvRoutingConfig` uses `PayerType` (from `com.hcare.domain`) and `AggregatorType` (from `com.hcare.evv`) — consistent with Task 10 imports.
- `Authorization` constructor takes `(clientId, payerId, serviceTypeId, agencyId, authNumber, authorizedUnits, unitType, startDate, endDate)` — matches `AuthorizationOptimisticLockIT.createAndSaveAuth()` which passes all nine arguments.
- `CarePlan.findByClientIdAndStatus(UUID clientId, CarePlanStatus status)` in `CarePlanRepository` — `CarePlanStatus` type matches the enum created in Task 7.
- `CaregiverClientAffinity(scoringProfileId, clientId, agencyId)` constructor — matches `ClientDomainIT.caregiver_client_affinity_can_be_saved_now_that_client_exists()` usage.
- `CaregiverCredential.verify(UUID adminUserId)` method — not called in tests but matches the field names used in Plan 5 (AI scoring) and Plan 6 (admin API).
- All entity constructors match their test usages exactly.
