CREATE TABLE agencies (
    id           UUID        PRIMARY KEY,
    name         VARCHAR(255) NOT NULL,
    state        CHAR(2)     NOT NULL,
    created_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE agency_users (
    id            UUID        PRIMARY KEY,
    agency_id     UUID        NOT NULL REFERENCES agencies(id),
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20) NOT NULL,
    created_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_agency_users_email UNIQUE (email)
);

CREATE INDEX idx_agency_users_agency_id ON agency_users(agency_id);

CREATE TABLE phi_audit_logs (
    id                    UUID        PRIMARY KEY,
    user_id               UUID,
    family_portal_user_id UUID,
    system_job_id         VARCHAR(100),
    agency_id             UUID        NOT NULL,
    resource_type         VARCHAR(50) NOT NULL,
    resource_id           UUID        NOT NULL,
    action                VARCHAR(20) NOT NULL,
    occurred_at           TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ip_address            VARCHAR(45),
    user_agent            TEXT
);

CREATE INDEX idx_phi_audit_agency_id    ON phi_audit_logs(agency_id);
CREATE INDEX idx_phi_audit_occurred_at  ON phi_audit_logs(occurred_at);
