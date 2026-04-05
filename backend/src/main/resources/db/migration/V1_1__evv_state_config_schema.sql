CREATE TABLE evv_state_configs (
    id                                  UUID         PRIMARY KEY,
    state_code                          CHAR(2)      NOT NULL,
    default_aggregator                  VARCHAR(30)  NOT NULL,
    system_model                        VARCHAR(10)  NOT NULL,
    allowed_verification_methods        TEXT         NOT NULL,
    gps_tolerance_miles                 DECIMAL(5,2),
    requires_real_time_submission       BOOLEAN      NOT NULL DEFAULT FALSE,
    manual_entry_cap_percent            INTEGER,
    co_resident_exemption_supported     BOOLEAN      NOT NULL DEFAULT TRUE,
    extra_required_fields               TEXT,
    compliance_threshold_percent        INTEGER,
    closed_system_acknowledged_by_agency BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_evv_state_configs_state_code UNIQUE (state_code)
);
