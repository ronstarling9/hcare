-- V9: Rename scoring counter columns to reflect their true semantics.
-- V8 named them *_last_90_days but they are lifetime running totals;
-- the rolling 90-day window is a P2 feature (see Plan 5 notes).
ALTER TABLE caregiver_scoring_profiles
    RENAME COLUMN completed_shifts_last_90_days TO total_completed_shifts;
ALTER TABLE caregiver_scoring_profiles
    RENAME COLUMN cancelled_shifts_last_90_days TO total_cancelled_shifts;
ALTER TABLE caregiver_scoring_profiles
    RENAME COLUMN cancel_rate_last_90_days TO cancel_rate;
