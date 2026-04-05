-- C1: FamilyPortalUser email uniqueness — change from global to per-agency.
-- A family member can now register with multiple agencies using the same email address.
-- Magic-link auth looks up users by (agency_id, email), not email alone, preventing
-- cross-agency user disclosure.
ALTER TABLE family_portal_users DROP CONSTRAINT uq_family_portal_users_email;
ALTER TABLE family_portal_users ADD CONSTRAINT uq_family_portal_users_agency_email UNIQUE (agency_id, email);

-- C2: CaregiverClientAffinity optimistic locking — add @Version column.
-- Concurrent shift completions both try to increment visit_count; without a version guard
-- the last write wins and count increments are silently lost, corrupting the 25% continuity
-- scoring factor. The @Version column causes Hibernate to emit:
--   UPDATE caregiver_client_affinities SET visit_count=?, version=? WHERE id=? AND version=?
-- If 0 rows are updated, ObjectOptimisticLockingFailureException is thrown; the caller retries.
ALTER TABLE caregiver_client_affinities ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
