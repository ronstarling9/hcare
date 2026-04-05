# Core Domain Model — Critical Implementation Review 1

**Plan:** `2026-04-04-core-domain-model.md`
**Reviewer:** critical-implementation-review v1.5.1
**Date:** 2026-04-05
**Status:** First review (no prior review history)

---

## 1. Overall Assessment

The plan is thorough and well-structured for a domain model build-out. It correctly handles the Hibernate multi-tenancy pattern, documents the optimistic locking strategy for `Authorization`, and provides complete code for every entity. However, seven issues range from a potential data correctness regression (unprotected concurrent `visitCount` increments) to a real-world multi-tenancy violation (`FamilyPortalUser` email uniqueness scoped globally instead of per-agency). Several entities omit `created_at` inconsistently, and the broken `ClientRepository_Stub` placeholder in Task 4 is a latent trap for future readers. Test coverage has gaps on the mutation methods (`updateAfterShiftCompletion`, `recordLogin`) that future plans depend on.

---

## 2. Critical Issues

### C1 — `FamilyPortalUser.email` is globally unique — violates multi-tenancy semantics

**Description:** Both the migration (`CONSTRAINT uq_family_portal_users_email UNIQUE (email)`) and the entity (`@Column(unique = true)`) enforce a **global** unique constraint on `email`. A family member's email address (e.g., `daughter@gmail.com`) can only be registered with **one agency ever** — the second agency will get a constraint violation.

**Why it matters:** Home care families frequently move their relatives between agencies. A globally unique email is a support nightmare and a silent data correctness bug: the agency admin will see an opaque 500 or constraint error and have no way to register the family member.

**Fix:** Change the constraint to be per-agency:

```sql
-- In V3 migration, replace:
CONSTRAINT uq_family_portal_users_email UNIQUE (email)
-- With:
CONSTRAINT uq_family_portal_users_email UNIQUE (agency_id, email)
```

In `FamilyPortalUser.java`:
```java
// Remove: @Column(nullable = false, unique = true)
// Add:    @Column(nullable = false)
private String email;
```

`FamilyPortalUserRepository.findByEmail()` must then become `findByAgencyIdAndEmail()` for secure lookups (prevents an attacker at Agency B from logging in as a family member of Agency A if they share an email).

---

### C2 — `CaregiverClientAffinity.incrementVisitCount()` is not atomic — silent visit count loss under concurrency

**Description:** `visitCount++` is an in-memory increment with no `@Version` guard. Concurrent shift completions for the same caregiver+client pair (which would both call `incrementVisitCount()` and save) will produce a last-write-wins race — one increment will be silently overwritten.

**Why it matters:** `visitCount` is the foundation of the 25% continuity scoring factor (per the architecture description). Corrupt visit counts will silently degrade match quality. Unlike `Authorization.usedUnits` (financial data with an obvious retry signal), a lost visit count is invisible.

**Fix:** Add `@Version` to `CaregiverClientAffinity`, mirroring the `Authorization` pattern:

```java
@Version
@Column(nullable = false)
private Long version = 0L;
```

Add a getter and document that the caller must catch `ObjectOptimisticLockingFailureException` and retry — exactly as documented for `Authorization`. Update the migration:

```sql
-- In caregiver_client_affinities table definition:
version BIGINT NOT NULL DEFAULT 0,
```

---

### C3 — `adl_tasks` and `goals` omit `created_at` — inconsistency breaks future audit/reporting queries

**Description:** `adl_tasks` and `goals` are the only two tables in the migration that have no `created_at` column. `AdlTask.java` and `Goal.java` also have no `createdAt` field. Every other entity in the domain model has it.

**Why it matters:** Plan 3 (Scheduling) and Plan 6 (Admin API) will query ADL tasks and goals in date-range reports. A missing `created_at` forces a JOIN to `care_plans.created_at` as a proxy — incorrect if tasks are added to a plan after it is activated. The inconsistency also makes the schema harder to reason about.

**Fix:** Add to the migration:

```sql
CREATE TABLE adl_tasks (
    ...
    sort_order   INTEGER      NOT NULL DEFAULT 0,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP  -- add this
);

CREATE TABLE goals (
    ...
    status       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP  -- add this
);
```

Add to both entities:

```java
@Column(nullable = false, updatable = false)
private LocalDateTime createdAt = LocalDateTime.now();
```

---

## 3. Previously Addressed Items

None — this is the first review.

---

## 4. Minor Issues & Improvements

### M1 — Broken `ClientRepository_Stub` placeholder in Task 4 (Step 1)

The plan includes this line as part of the "failing test" for the affinity test:

```java
Client client = new ClientRepository_Stub(); // see note below
```

`ClientRepository_Stub` is not a real class. The note below it explains the intent, but the code block itself is invalid Java. A future reader following the plan literally will get a confusing compile error before reaching the note. **Fix:** Replace the broken method body with a clear `// TODO after Task 5` comment and move the complete affinity test to a separate step labelled "Add after Task 5."

---

### M2 — Missing index on `authorizations.payer_id` and `service_type_id`

The migration creates indexes on `client_id` and `agency_id` but not on the two FK columns `payer_id` and `service_type_id`. Plan 4 (EVV) and Plan 6 (Admin API) will query "all authorizations for this payer" and "all authorizations for this service type" — both will do full table scans.

```sql
CREATE INDEX idx_authorizations_payer_id        ON authorizations(payer_id);
CREATE INDEX idx_authorizations_service_type_id ON authorizations(service_type_id);
```

---

### M3 — Missing index on `caregiver_client_affinities.client_id`

The deferred FK constraint is correctly deferred, but no index is created on `client_id`. The scoring query "find all caregivers who have visited client X" is the primary use of this table and will full-scan without an index.

```sql
CREATE INDEX idx_caregiver_client_affinities_client_id ON caregiver_client_affinities(client_id);
```

---

### M4 — No test for `CaregiverScoringProfile.updateAfterShiftCompletion()`

`CaregiverSubEntitiesIT` only tests the initial zero-value state of `CaregiverScoringProfile`. The `updateAfterShiftCompletion(BigDecimal hoursWorked)` method — the primary mutation that Plan 5 will call — is not tested. A test should verify:

```java
profile.updateAfterShiftCompletion(new BigDecimal("4.0"));
scoringProfileRepo.save(profile);
CaregiverScoringProfile loaded = scoringProfileRepo.findById(profile.getId()).orElseThrow();
assertThat(loaded.getCurrentWeekHours()).isEqualByComparingTo("4.0");
assertThat(loaded.getUpdatedAt()).isAfterOrEqualTo(before);
```

---

### M5 — No test for `CarePlanRepository.findByClientIdAndStatus()`

`CarePlanRepository` exposes `findByClientIdAndStatus()` which is the query Plan 6 relies on to enforce "one active plan per client." It is never exercised in `CarePlanDomainIT`. Add a test that saves ACTIVE + SUPERSEDED plans for the same client and asserts `findByClientIdAndStatus(clientId, ACTIVE)` returns exactly one result.

---

### M6 — `background_check_defaults_to_pending` test name contradicts what it tests

The test method is named `background_check_defaults_to_pending` but passes `BackgroundCheckResult.CLEAR` to the constructor and asserts `CLEAR` back. The DB schema default of `'PENDING'` is never tested because the constructor always sets the result explicitly. **Fix:** Either rename the test to `background_check_stores_given_result` or add a separate test that exercises the DB default by inserting a row without specifying a result.

---

### M7 — `FamilyPortalUser.recordLogin()` method has no test

The entity exposes `recordLogin()` which sets `lastLoginAt = LocalDateTime.now()`. Plan 3 (or the auth layer) will call this on every family portal login. No test verifies that `recordLogin()` + save persists the timestamp. **Fix:** Add a test to `ClientDomainIT`:

```java
fpu.recordLogin();
familyPortalUserRepo.save(fpu);
FamilyPortalUser reloaded = familyPortalUserRepo.findById(fpu.getId()).orElseThrow();
assertThat(reloaded.getLastLoginAt()).isNotNull();
```

---

### M8 — `CaregiverAvailability` allows `startTime >= endTime` with no guard

The entity stores `startTime` and `endTime` with no validation that `startTime < endTime`. An overnight availability slot (e.g., 10 PM → 6 AM) would be stored as a record where `startTime > endTime`, which the Plan 3 scheduling engine may not handle correctly. If overnight slots are not in scope for P1, add a note or an assertion in the constructor.

---

## 5. Questions for Clarification

**Q1 — `FamilyPortalUser` global email uniqueness (C1):** Is the current globally-unique email constraint intentional? If a family member legitimately works with two different agencies (e.g., a daughter whose mother moved from one agency to another), they would be blocked from registering with the second agency. If per-agency uniqueness is the right model, C1 above is a blocker. If truly global (one email = one family portal identity across all agencies), the magic-link JWT must carry `agencyId` and the system must support multi-agency family portal access — which the current plan does not address.

**Q2 — `visitCount` concurrency (C2):** Is `visitCount` loss under concurrency acceptable at P1? If the scoring service runs single-threaded via a queue, the race may never occur. If concurrent shift completions are expected (e.g., batch EVV import), `@Version` is necessary.

**Q3 — Overnight availability slots:** Should `CaregiverAvailability` support overnight slots (startTime > endTime as a cross-midnight indicator)? If so, the Plan 3 scheduler needs to know about this convention.

---

## 6. Final Recommendation

**Approve with changes.**

C1 (global email uniqueness) and C2 (unprotected `visitCount` increments) are production correctness bugs that should be fixed before Plan 3 (Scheduling) and Plan 5 (AI Scoring) build on these entities. C3 (`created_at` on adl_tasks/goals) is a consistency debt that will cause friction in Plan 6 reporting queries. The minor issues (M2–M8) are low-risk but the missing tests for mutation methods (M4, M5, M7) should be added before Plan 5 integrates with the scoring profile.
