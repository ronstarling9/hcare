-- V12: Family portal token infrastructure
--
-- 1. Add timezone to agencies (defaults to America/New_York — covers most US home care agencies).
--    Existing rows get the default. Admins can update via a future settings endpoint.
-- 2. Create family_portal_tokens for hashed one-time invite tokens (72-hour TTL).
--    tokenHash stores hex-encoded SHA-256 of the raw URL token — raw token is never persisted.
--    ON DELETE CASCADE ensures token rows are cleaned up when a FamilyPortalUser is removed.
-- 3. Fix FamilyPortalUser unique constraint from (agency_id, email) to (client_id, agency_id, email)
--    so the same family member can have portal access for two clients at the same agency
--    (e.g., adult child caring for two parents).

ALTER TABLE agencies
    ADD COLUMN IF NOT EXISTS timezone VARCHAR(50) NOT NULL DEFAULT 'America/New_York';

-- DATA PRECONDITION: The new (client_id, agency_id, email) constraint requires that no two
-- FamilyPortalUser rows share the same (client_id, agency_id, email) triple.
-- In production: verify with the query below before running this migration.
-- SELECT client_id, agency_id, email, COUNT(*) FROM family_portal_users
-- GROUP BY client_id, agency_id, email HAVING COUNT(*) > 1;
-- Expected result: 0 rows. If rows are returned, deduplicate before applying.
-- In dev/test (H2): the DevDataSeeder inserts no duplicate triples, so this is safe.
ALTER TABLE family_portal_users
    DROP CONSTRAINT IF EXISTS uq_family_portal_users_agency_email;
ALTER TABLE family_portal_users
    ADD CONSTRAINT uq_fpu_client_agency_email UNIQUE (client_id, agency_id, email);

CREATE TABLE family_portal_tokens (
    id          UUID         PRIMARY KEY,
    token_hash  VARCHAR(64)  NOT NULL,
    fpu_id      UUID         NOT NULL REFERENCES family_portal_users(id) ON DELETE CASCADE,
    client_id   UUID         NOT NULL,
    agency_id   UUID         NOT NULL,
    expires_at  TIMESTAMP    NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_family_portal_tokens_hash UNIQUE (token_hash)
);
CREATE INDEX idx_fpt_fpu_id ON family_portal_tokens(fpu_id);
CREATE INDEX idx_fpt_expires_at ON family_portal_tokens(expires_at);
