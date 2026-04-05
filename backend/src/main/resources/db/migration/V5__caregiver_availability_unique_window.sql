-- M10: Prevent duplicate availability windows for the same caregiver.
-- Without this constraint, Plan 3 scheduler would double-count availability
-- and potentially overbook a caregiver who has two identical Monday 8:00-16:00 rows.
ALTER TABLE caregiver_availability
    ADD CONSTRAINT uq_caregiver_availability
    UNIQUE (caregiver_id, day_of_week, start_time, end_time);
