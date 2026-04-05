-- V7: Add offline-capture fields to adl_task_completions.
-- Mirrors the capturedOffline + deviceCapturedAt pattern on evv_records.
-- completedAt remains the server receipt time; deviceCapturedAt is authoritative for offline visits.

ALTER TABLE adl_task_completions
    ADD COLUMN captured_offline  BOOLEAN   NOT NULL DEFAULT FALSE,
    ADD COLUMN device_captured_at TIMESTAMP;
