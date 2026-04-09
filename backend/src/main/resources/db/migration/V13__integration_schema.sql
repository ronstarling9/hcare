-- Domain field additions
ALTER TABLE evv_records     ADD COLUMN aggregator_visit_id VARCHAR(100);
ALTER TABLE caregivers      ADD COLUMN npi VARCHAR(10);
ALTER TABLE agencies        ADD COLUMN npi VARCHAR(10);
ALTER TABLE agencies        ADD COLUMN tax_id VARCHAR(9);
ALTER TABLE shifts          ADD COLUMN hcpcs_code VARCHAR(10);
ALTER TABLE shifts          ADD COLUMN billed_amount NUMERIC(10,2);
ALTER TABLE shifts          ADD COLUMN units INT;

-- Integration tables
CREATE TABLE agency_evv_credentials (
    id                   UUID         PRIMARY KEY,
    agency_id            UUID         NOT NULL REFERENCES agencies(id),
    aggregator_type      VARCHAR(30)  NOT NULL,
    credentials_encrypted TEXT        NOT NULL,
    endpoint_override    VARCHAR(500),
    active               BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_agency_aggregator UNIQUE (agency_id, aggregator_type)
);

CREATE TABLE evv_submission_records (
    id                   UUID         PRIMARY KEY,
    evv_record_id        UUID         NOT NULL UNIQUE,
    agency_id            UUID         NOT NULL REFERENCES agencies(id),
    aggregator_type      VARCHAR(30)  NOT NULL,
    aggregator_visit_id  VARCHAR(100),
    submission_mode      VARCHAR(10)  NOT NULL DEFAULT 'REAL_TIME',
    status               VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    in_flight_since      TIMESTAMP,
    context_json         TEXT,
    retry_count          INT          NOT NULL DEFAULT 0,
    last_error           TEXT,
    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    submitted_at         TIMESTAMP
);

CREATE TABLE agency_integration_configs (
    id                   UUID         PRIMARY KEY,
    agency_id            UUID         NOT NULL REFERENCES agencies(id),
    integration_type     VARCHAR(30)  NOT NULL,
    connector_class      VARCHAR(100) NOT NULL,
    state_code           CHAR(2),
    payer_type           VARCHAR(20),
    endpoint_url         VARCHAR(500),
    credentials_encrypted TEXT        NOT NULL,
    config_json          TEXT,
    active               BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE integration_audit_log (
    id           UUID        PRIMARY KEY,
    agency_id    UUID        NOT NULL REFERENCES agencies(id),
    entity_id    UUID        NOT NULL,
    connector    VARCHAR(100) NOT NULL,
    operation    VARCHAR(20) NOT NULL,
    success      BOOLEAN     NOT NULL,
    duration_ms  BIGINT      NOT NULL,
    error_code   VARCHAR(50),
    recorded_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes
CREATE INDEX idx_evv_submission_agency  ON evv_submission_records(agency_id, submission_mode, status);
CREATE INDEX idx_integration_audit      ON integration_audit_log(agency_id, recorded_at DESC);
CREATE INDEX idx_agency_integration_configs_agency ON agency_integration_configs(agency_id);
