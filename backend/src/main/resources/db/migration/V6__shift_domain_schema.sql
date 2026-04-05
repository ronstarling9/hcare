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
