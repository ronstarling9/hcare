-- V8: Add cancellation and completion counters to caregiver_scoring_profiles.
-- These power the cancelRateLast90Days calculation driven by ShiftCompletedEvent /
-- ShiftCancelledEvent listeners in LocalScoringService (Plan 5).
-- Running counts since first recorded shift — exact 90-day rolling window is P2.
ALTER TABLE caregiver_scoring_profiles ADD COLUMN completed_shifts_last_90_days INT NOT NULL DEFAULT 0;
ALTER TABLE caregiver_scoring_profiles ADD COLUMN cancelled_shifts_last_90_days INT NOT NULL DEFAULT 0;
-- Note: caregiver_client_affinities already has @Version (version BIGINT, V4 migration)
-- and UNIQUE (scoring_profile_id, client_id) (V3 migration). No changes needed.
