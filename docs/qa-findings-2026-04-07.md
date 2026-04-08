# QA Findings — Exploratory Testing
**Date:** 2026-04-07
**Method:** API-level testing across all three seeded agencies using real JWTs
**Agencies tested:** Sunrise Home Care (TX), Golden Years Care (FL), Harmony Home Health (CA)

---

## Critical

### QA-001 — Cross-agency data leak on all detail endpoints
**Severity:** Critical  
**Affected endpoints:** `GET /clients/{id}`, `GET /caregivers/{id}`, `GET /payers/{id}`, and any other single-resource lookup backed by `findById()`

An authenticated JWT from any agency can read any client, caregiver, or payer by UUID regardless of which agency owns the record. For example, a Sunrise admin token successfully returns a Golden Years client record — including `dateOfBirth`, `medicaidId`, and all associated PHI.

**Root cause:** Service `require*()` methods call bare JPA `repository.findById(id)`. Hibernate's `@Filter` (the `agencyFilter` declared in `domain/package-info.java`) is never applied to primary-key lookups via `EntityManager.find()` — it only fires on JPQL/HQL collection queries. The `TenantFilterAspect` does enable the filter before repository calls, but `findById()` bypasses it entirely.

**Fix:** Add `Optional<T> findByIdAndAgencyId(UUID id, UUID agencyId)` to `ClientRepository`, `CaregiverRepository`, and `PayerRepository`. Replace bare `findById()` calls in `ClientService.requireClient()`, `CaregiverService.requireCaregiver()`, and `PayerService.getPayer()` with the agency-scoped variant. Throw 404 (not 403) when the record does not exist under the requesting agency — returning 403 would confirm the record exists.

**Verified:** Sunrise JWT → `GET /clients/{golden-client-id}` → HTTP 200 with Golden's data. All agency-cross combinations confirmed.

---

## High

### QA-002 — All seeded credentials are unverified — candidates and broadcast always return empty
**Severity:** High (complete workflow blocker)  
**Affected:** `GET /shifts/{id}/candidates`, `POST /shifts/{id}/broadcast` — all three agencies

`DevDataSeeder` creates credentials but never sets `verified = true`. The scoring hard filter in `LocalScoringService` requires `isVerified() == true` for any credential type required by the shift's service type. Since Personal Care Services requires HHA and all HHA credentials are unverified, zero caregivers pass the filter. `getCandidates()` returns `[]` and `broadcastShift()` creates zero offers for every agency.

This also makes the AI scheduling differentiator between Harmony (AI ON) and the other agencies completely unobservable — both code paths are exercised but both return empty results.

**Fix:** In `DevDataSeeder.seedCredentials()`, set `verified = true` on each credential before saving.

---

### QA-003 — No API endpoint to verify caregiver credentials
**Severity:** High  
**Affected:** Credential management workflow (admin function per MVP spec)

The MVP spec states that credential verification is a manual admin action: the admin sets `verified = true` with an optional notes field. No such endpoint exists. There is no `POST /caregivers/{id}/credentials/{credentialId}/verify` (or equivalent). Combined with QA-002, there is no way for an admin to unblock the shift-fill workflow after the seed data fix — real agencies would also have no path to verifying their caregivers' credentials.

**Fix:** Implement `POST /api/v1/caregivers/{caregiverId}/credentials/{credentialId}/verify` (ADMIN role only). Should set `verified = true`, record `verifiedBy` (current user ID) and `verifiedAt` timestamp on the credential. If `CaregiverCredential` lacks these audit fields, add them via a new Flyway migration.

---

### QA-004 — Date-only params cause HTTP 500 on shifts and EVV history
**Severity:** High  
**Affected:** `GET /shifts?start=&end=`, `GET /evv/history?startDate=&endDate=`

Both endpoints use `@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)`, requiring full ISO-8601 datetime strings (e.g., `2026-04-01T00:00:00`). Passing date-only strings (e.g., `2026-04-01`) causes `MethodArgumentTypeMismatchException`, which the global exception handler does not catch — it falls through to a generic 500 response with no useful error message. The frontend's date range pickers produce date-only strings by default.

**Fix (two parts):**
1. Add a handler in `GlobalExceptionHandler` for `MethodArgumentTypeMismatchException` returning HTTP 400 with a message indicating the expected format.
2. Ensure frontend hooks (`useShifts`, `useEvvHistory`) append `T00:00:00` / `T23:59:59` when constructing query params from date picker values.

---

### QA-005 — Malformed UUID path params return 500 instead of 400
**Severity:** High  
**Affected:** All detail endpoints with `{id}` path variables

`GET /clients/not-a-uuid` returns HTTP 500 with "An unexpected error occurred." The `GlobalExceptionHandler` does not catch `MethodArgumentTypeMismatchException` or `InvalidFormatException` for UUID path variable conversion failures.

**Fix:** Add a handler in `GlobalExceptionHandler` for `MethodArgumentTypeMismatchException` (covers both date and UUID format failures) returning HTTP 400.

---

## Medium

### QA-006 — Unauthenticated requests return HTTP 403, not 401
**Severity:** Medium  
**Affected:** All protected endpoints

Calling any protected endpoint without a JWT returns HTTP 403 (Forbidden) instead of HTTP 401 (Unauthorized). Per RFC 7235, unauthenticated access must return 401 with a `WWW-Authenticate` header. The frontend's `apiClient` interceptor listens for 401 responses to trigger a session-expiry redirect to `/login` — it will never fire for truly unauthenticated requests because Spring Security returns 403 by default when no `AuthenticationEntryPoint` is configured.

**Fix:** Configure `AuthenticationEntryPoint` in `SecurityConfig` to return 401 with `WWW-Authenticate: Bearer` for unauthenticated requests.

---

### QA-007 — Dashboard "today" shows no in-progress or completed visits due to UTC/local date mismatch
**Severity:** Medium  
**Affected:** `GET /dashboard/today`, dashboard stat tiles

The server runs UTC. `DevDataSeeder` seeds "today's" shifts against `LocalDate.now()` in the system's local timezone (Pacific, UTC-7). At testing time (21:15 PDT = 04:15 UTC next day), the server's "today" window is 2026-04-08 UTC while the seeded IN_PROGRESS and COMPLETED shifts are on 2026-04-07. The dashboard correctly reflects today from the server's perspective, but the pre-wired interesting shifts are already in "yesterday" — tile counts show `redEvvCount=0, yellowEvvCount=0, uncoveredCount=0, onTrack=0` despite rich EVV data existing in the system.

**Fix:** Seed shift timestamps using `LocalDate.now(ZoneOffset.UTC)` in `DevDataSeeder`, or document that the dashboard will look empty when testing in timezones west of UTC after ~5 PM local time.

---

### QA-008 — `preferredLanguages` returned as a raw JSON string instead of an array
**Severity:** Medium  
**Affected:** `GET /clients/{id}` response

The `preferredLanguages` field is stored as a JSON string in the database and exposed in `ClientResponse` as a `String`. The response contains `"preferredLanguages": "[\"en\"]"` instead of `"preferredLanguages": ["en"]`. Any frontend code iterating over this field as an array will fail.

**Fix:** Map `preferredLanguages` to `List<String>` in `ClientResponse` and deserialize it in `ClientService` using Jackson's `ObjectMapper`, or annotate the entity field with a `@Convert(converter = StringListConverter.class)` JPA converter.

---

### QA-009 — `MISSED` shift status not exposed in EVV history rows
**Severity:** Medium  
**Affected:** `GET /evv/history`, EVV Status page

Shifts with `status = MISSED` appear in EVV history with `evvStatus = GREY` (no EVVRecord), making them indistinguishable from future OPEN shifts that also have no EVVRecord. The `EvvHistoryRow` DTO does not include the underlying `Shift.status` field.

**Fix:** Add `String shiftStatus` (or a `ShiftStatus` enum value) to `EvvHistoryRow` and populate it from `Shift.getStatus()` in `EvvHistoryService`. The EVV Status page can then display MISSED shifts with distinct styling or a status label.

---

### QA-010 — `GET /payers/{id}/authorizations` returns 500 (route does not exist)
**Severity:** Medium  
**Affected:** Payer detail panel — authorization list

The payer detail panel in the frontend spec shows "a list of active authorizations under this payer across all clients." There is no such endpoint — `PayerController` only exposes `GET /payers` and `GET /payers/{id}`. Authorizations are nested under clients (`/clients/{id}/authorizations`). Any frontend call to a payer-scoped authorization route hits an unmapped path, which the global exception handler returns as 500 rather than 404.

**Fix (two parts):**
1. Add `GET /payers/{id}/authorizations` to `PayerController` that queries authorizations by payer ID across all clients in the agency.
2. Fix `GlobalExceptionHandler` to return 404 (not 500) for unmapped routes (`NoHandlerFoundException` / `NoResourceFoundException`).

---

## Low

### QA-011 — `address` and `phone` null for all seeded clients and caregivers
**Severity:** Low  
**Affected:** Client detail panel, caregiver detail panel

`DevDataSeeder` sets lat/lng coordinates on clients and caregivers (used by the scoring distance factor) but never populates the displayable `address` or `phone` fields. All 15 clients and 15 caregivers across all three agencies have `address: null` and `phone: null`. Any detail panel UI that renders contact information will show blank fields.

**Fix:** Populate realistic `address` and `phone` values in `DevDataSeeder` for all seeded clients and caregivers.

---

### QA-012 — `PORTAL_SUBMIT` EVV status is never produced by seed data
**Severity:** Low  
**Affected:** EVV Status page filter chips, `PORTAL_SUBMIT` code path

`PORTAL_SUBMIT` is a documented EVV compliance status (`systemModel = CLOSED` AND `closedSystemAcknowledgedByAgency = true`). None of the three seeded agencies produce this status — there are no closed-system payers in the seed data and no agency has acknowledged the closed-system limitation. The status chip on the EVV Status page filter bar has no data to filter on, making this code path untestable without manual data setup.

**Fix:** Either add a closed-system payer + acknowledgment flag to one seeded agency's data, or document this as a known seed data gap with instructions for manual testing.

---

### QA-013 — Client detail `GET /clients/{id}/care-plan` (singular) returns 500
**Severity:** Low  
**Affected:** Any frontend code calling the singular route

The correct route is `GET /clients/{id}/care-plans` (plural). The singular form hits an unmapped path and returns 500 instead of 404 — same global exception handler gap as QA-010.

**Fix:** Same fix as QA-010 part 2 — handle `NoHandlerFoundException` in `GlobalExceptionHandler` to return 404.

---

### QA-014 — Client diagnoses and medications not populated in Golden Years seed data
**Severity:** Low  
**Affected:** `GET /clients/{id}/diagnoses`, `GET /clients/{id}/medications` for Golden Years agency

Golden Years clients return empty arrays for diagnoses and medications. Sunrise and Harmony clients have these populated. This appears to be an inconsistency in `DevDataSeeder` rather than a functional bug — the endpoints work, the seed data is incomplete for this agency.

**Fix:** Add diagnoses and medication seed data for Golden Years clients in `DevDataSeeder`.

---

## Summary

| ID | Severity | Area | Description |
|----|----------|------|-------------|
| QA-001 | Critical | Security / Multi-tenancy | Cross-agency PHI leak via `findById()` bypassing `@Filter` |
| QA-002 | High | Scoring / Seed data | All seeded credentials unverified — candidates/broadcast return empty |
| QA-003 | High | Caregivers API | No endpoint to verify credentials |
| QA-004 | High | Shifts / EVV API | Date-only params cause 500; need full ISO-8601 datetime |
| QA-005 | High | Global error handling | Malformed UUID path params return 500 instead of 400 |
| QA-006 | Medium | Security / Auth | Unauthenticated requests return 403 instead of 401 |
| QA-007 | Medium | Dashboard / Seed data | UTC/local timezone mismatch makes "today" dashboard appear empty |
| QA-008 | Medium | Clients API | `preferredLanguages` returned as JSON string, not array |
| QA-009 | Medium | EVV history | `shiftStatus` missing from `EvvHistoryRow` — MISSED vs OPEN indistinguishable |
| QA-010 | Medium | Payers API | `GET /payers/{id}/authorizations` route missing; unmapped routes return 500 not 404 |
| QA-011 | Low | Seed data | `address` and `phone` null for all clients and caregivers |
| QA-012 | Low | EVV seed data | `PORTAL_SUBMIT` status untestable — no closed-system payer in seed data |
| QA-013 | Low | Global error handling | `GET /clients/{id}/care-plan` (singular) returns 500 not 404 |
| QA-014 | Low | Seed data | Golden Years clients missing diagnoses and medications |
