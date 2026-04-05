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

-- One row per agency. aiSchedulingEnabled gates Pro-tier AI match engine.
CREATE TABLE feature_flags (
    id                    UUID      PRIMARY KEY,
    agency_id             UUID      NOT NULL UNIQUE REFERENCES agencies(id),
    ai_scheduling_enabled BOOLEAN   NOT NULL DEFAULT FALSE,
    family_portal_enabled BOOLEAN   NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_feature_flags_agency_id ON feature_flags(agency_id);

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

-- Pre-computed scoring signals updated asynchronously via Spring events.
-- Never populated on the match-request path.
CREATE TABLE caregiver_scoring_profiles (
    id                       UUID          PRIMARY KEY,
    caregiver_id             UUID          NOT NULL UNIQUE REFERENCES caregivers(id),
    agency_id                UUID          NOT NULL REFERENCES agencies(id),
    cancel_rate_last_90_days DECIMAL(5, 4) NOT NULL DEFAULT 0,
    current_week_hours       DECIMAL(5, 2) NOT NULL DEFAULT 0,
    updated_at               TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_caregiver_scoring_profiles_agency_id ON caregiver_scoring_profiles(agency_id);

-- Visit history per caregiver+client pair.
-- client_id FK is added below via ALTER TABLE after the clients table is created.
CREATE TABLE caregiver_client_affinities (
    id                 UUID    PRIMARY KEY,
    scoring_profile_id UUID    NOT NULL REFERENCES caregiver_scoring_profiles(id),
    client_id          UUID    NOT NULL,
    agency_id          UUID    NOT NULL REFERENCES agencies(id),
    visit_count        INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uq_affinity_profile_client UNIQUE (scoring_profile_id, client_id)
);
CREATE INDEX idx_caregiver_client_affinities_scoring_profile_id ON caregiver_client_affinities(scoring_profile_id);
CREATE INDEX idx_caregiver_client_affinities_agency_id          ON caregiver_client_affinities(agency_id);

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
CREATE INDEX idx_caregiver_client_affinities_client_id ON caregiver_client_affinities(client_id);

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
    sort_order       INTEGER      NOT NULL DEFAULT 0,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_adl_tasks_care_plan_id ON adl_tasks(care_plan_id);
CREATE INDEX idx_adl_tasks_agency_id    ON adl_tasks(agency_id);

CREATE TABLE goals (
    id           UUID        PRIMARY KEY,
    care_plan_id UUID        NOT NULL REFERENCES care_plans(id),
    agency_id    UUID        NOT NULL REFERENCES agencies(id),
    description  TEXT        NOT NULL,
    target_date  DATE,
    status       VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_goals_care_plan_id ON goals(care_plan_id);
CREATE INDEX idx_goals_agency_id    ON goals(agency_id);

-- version column is managed exclusively by JPA @Version — never set by application code.
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
CREATE INDEX idx_authorizations_payer_id        ON authorizations(payer_id);
CREATE INDEX idx_authorizations_service_type_id ON authorizations(service_type_id);

-- Family portal users authenticate via magic link (no password).
CREATE TABLE family_portal_users (
    id            UUID         PRIMARY KEY,
    client_id     UUID         NOT NULL REFERENCES clients(id),
    agency_id     UUID         NOT NULL REFERENCES agencies(id),
    email         VARCHAR(255) NOT NULL,
    name          VARCHAR(255),
    last_login_at TIMESTAMP,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- Email is globally unique: a family member with accounts at two agencies uses the same
    -- login email — magic-link auth looks up the user by email across all agencies.
    CONSTRAINT uq_family_portal_users_email UNIQUE (email)
);
CREATE INDEX idx_family_portal_users_client_id ON family_portal_users(client_id);
CREATE INDEX idx_family_portal_users_agency_id ON family_portal_users(agency_id);
