# Critical Implementation Review — Plan 6: Scheduling REST API
**Review:** 1 of N  
**Plan file:** `docs/superpowers/plans/2026-04-05-scheduling-api.md`  
**Reviewer:** Senior Staff Engineer  
**Date:** 2026-04-05  
**Previous reviews:** None

---

## 1. Overall Assessment

The plan is well-structured, architecturally coherent, and production-quality in most respects. The TDD approach (write failing test → implement → confirm green) is executed consistently across all eight tasks. The plan correctly identifies the naming collision with the existing `ShiftOfferResponse` enum and avoids it. The multi-tenancy concern is appropriately deferred to the Hibernate `agencyFilter` framework per project convention. 

However, there are **two correctness defects** that will cause test failures as written, **one significant concurrency bug** in the most complex service method, and a **meaningful gap in unit test coverage** for the four most complex service methods. These must be resolved before execution.

Pre-verification via actual code inspection (done as part of this review) confirms: `ShiftRepository.deleteUnstartedFutureShifts`, `ShiftRepository.findByClientIdAndScheduledStartBetween`, `ShiftOfferRepository.findByShiftId`, `ShiftGenerationService.generateForPattern`, and `ShiftGenerationService.regenerateAfterEdit` **all already exist** in the codebase — the plan's architecture assumptions about existing infrastructure are correct.

---

## 2. Critical Issues

### C1 — Test Count Discrepancy in Task 3 Step 4 Will Mislead Agentic Executor
**File:** `docs/superpowers/plans/2026-04-05-scheduling-api.md` (Task 3, Step 4 expected output)

The `ShiftSchedulingServiceTest` class as written contains **10 test methods**:
- `listShifts_delegates_to_repository_and_maps_to_response`
- `createShift_saves_shift_and_returns_response`
- `assignCaregiver_transitions_open_shift_to_assigned`
- `assignCaregiver_on_assigned_shift_throws_409`
- `assignCaregiver_on_missing_shift_throws_404`
- `unassignCaregiver_transitions_assigned_shift_to_open`
- `unassignCaregiver_on_open_shift_throws_409`
- `cancelShift_transitions_open_shift_to_cancelled_without_publishing_event`
- `cancelShift_on_assigned_shift_publishes_ShiftCancelledEvent`
- `cancelShift_on_in_progress_shift_throws_409`

The plan's Step 4 expected output says `Tests run: 9, Failures: 0`. An agentic worker following this plan will see `Tests run: 10` and believe something is wrong, potentially attempting to debug a non-existent failure.

**Fix:** Change the expected output in Task 3 Step 4 from `Tests run: 9` to `Tests run: 10`.

---

### C2 — Zero Unit Test Coverage for Four Complex Service Methods
**File:** `ShiftSchedulingServiceTest` (Task 3, Step 1)

`ShiftSchedulingService` implements `getCandidates`, `broadcastShift`, `listOffers`, and `respondToOffer`. None of these four methods have unit tests. `respondToOffer` in particular contains the plan's most complex branching logic:
- Guard on offer already having a response
- ACCEPTED path: guard on shift no longer being OPEN, assign caregiver, auto-decline all other pending offers
- DECLINED path: no shift mutation

The auto-decline cascade and the ACCEPTED-on-non-OPEN-shift guard are tested only via integration test. If `ShiftOffer.respond()` has a bug, or if the "decline others" loop fires when it shouldn't, the unit test suite provides no signal — you must run the full Testcontainers stack to detect the failure.

**Why it matters:** Unit tests are the fast feedback loop. The IT suite takes significantly longer and the isolation is coarser. The `respondToOffer` method has enough branches to justify 5–6 dedicated unit tests.

**Fix:** Add unit tests to `ShiftSchedulingServiceTest` for:
- `respondToOffer_accepted_assigns_caregiver_and_declines_other_pending_offers`
- `respondToOffer_on_already_responded_offer_throws_409`
- `respondToOffer_accepted_on_non_open_shift_throws_409`
- `respondToOffer_declined_does_not_mutate_shift`
- `getCandidates_delegates_to_scoring_service`
- `broadcastShift_on_non_open_shift_throws_409`
- `listOffers_delegates_to_repository`

Update expected count in Step 4 accordingly.

---

### C3 — TOCTOU Race Condition in `respondToOffer` Accepted Path
**File:** `ShiftSchedulingService.java` (Task 3, Step 3, `respondToOffer` method)

When `req.response() == ACCEPTED`, the method:
1. Loads the shift (reads OPEN status)
2. Checks `shift.getStatus() != OPEN` → throws if not open
3. Sets `caregiverId` + `ASSIGNED`, saves

Under `READ_COMMITTED` isolation (PostgreSQL default), two concurrent admin sessions accepting **different** offers on the same OPEN shift will both pass step 2 — they each see OPEN before either commits. Both will assign their respective caregivers; the second write wins silently. The first admin's acceptance confirmation response will say the shift is ASSIGNED to caregiver A; the actual state is ASSIGNED to caregiver B.

**Why it matters:** This is a correctness violation for a scheduling system. Double-assignment of a shift causes a real operational problem (two caregivers showing up, authorization unit over-consumption).

**Fix:** Use pessimistic locking when loading the shift for the ACCEPTED path. Since `Shift` has no `@Version` field, pessimistic write lock is the appropriate choice:

```java
// In the ACCEPTED branch of respondToOffer, replace:
Shift shift = requireShift(shiftId);

// With a dedicated locked load:
Shift shift = shiftRepository.findById(shiftId)
    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shift not found"));
// Then lock before mutation — add to ShiftRepository:
// @Lock(LockModeType.PESSIMISTIC_WRITE)
// Optional<Shift> findByIdForUpdate(UUID id);
```

Add `findByIdForUpdate` to `ShiftRepository` in Task 1 (or a dedicated sub-step before Task 3):

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT s FROM Shift s WHERE s.id = :id")
Optional<Shift> findByIdForUpdate(@Param("id") UUID id);
```

Use this method only in `respondToOffer`'s ACCEPTED branch, not in the general `requireShift` helper (reads-only calls don't need locking).

Add a unit test that verifies the service calls the locked variant in the ACCEPTED path (mock both `findById` and `findByIdForUpdate`; assert only `findByIdForUpdate` is called when processing ACCEPTED).

---

### C4 — `RespondToOfferRequest` Accepts `NO_RESPONSE` as a Valid Value
**File:** `RespondToOfferRequest.java` (Task 2, Step 7)

```java
public record RespondToOfferRequest(
    @NotNull ShiftOfferResponse response
) {}
```

`ShiftOfferResponse` has three values: `ACCEPTED`, `DECLINED`, `NO_RESPONSE`. A caller can POST `{"response": "NO_RESPONSE"}`, which passes `@NotNull` validation. The service will then call `offer.respond(NO_RESPONSE)`, which depending on `ShiftOffer.respond()`'s implementation may be a silent no-op or write garbage state.

**Fix:** Add a class-level validator or use a dedicated `RespondAction` enum with only `ACCEPTED`/`DECLINED`. The simplest fix without a new enum is a service-layer guard:

```java
if (req.response() == ShiftOfferResponse.NO_RESPONSE) {
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
        "Response must be ACCEPTED or DECLINED");
}
```

Add this check at the top of `respondToOffer` before the offer lookup. Add a unit test: `respondToOffer_with_NO_RESPONSE_throws_400`.

---

## 3. Previously Addressed Items
None — this is the first review.

---

## 4. Minor Issues & Improvements

### M1 — No Temporal Ordering Validation on `CreateShiftRequest`
`scheduledEnd` can be `<= scheduledStart` with no validation error. Add a class-level `@AssertTrue` or service-layer guard:
```java
// In ShiftSchedulingService.createShift, before save:
if (!req.scheduledEnd().isAfter(req.scheduledStart())) {
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
        "scheduledEnd must be after scheduledStart");
}
```
Mirror this in the unit test and IT test.

### M2 — `daysOfWeek` Is an Unvalidated Raw String
Both `CreateRecurrencePatternRequest` and `UpdateRecurrencePatternRequest` accept any string for `daysOfWeek`. A malformed value (e.g., `"monday"`, `"INVALID"`, `"[]"`) will be stored successfully and then fail silently or throw when `ShiftGenerationService` parses it. Add a `@Pattern` constraint:
```java
@Pattern(regexp = "^\\[\"(MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY)\"" +
    "(,\"(MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY)\")*\\]$")
@NotBlank String daysOfWeek
```
Or validate in the service before saving.

### M3 — `UpdateRecurrencePatternRequest.scheduledDurationMinutes` Missing `@Min(1)`
The create request has `@Min(1) int scheduledDurationMinutes`. The update request uses `Integer scheduledDurationMinutes` (nullable for patch semantics) but has no minimum bound. A PATCH with `{"scheduledDurationMinutes": 0}` will be stored and cause downstream failures in shift generation. Add `@Min(value = 1, message = "scheduledDurationMinutes must be at least 1")`.

### M4 — `broadcastShift` Catches All `DataIntegrityViolationException`
The `ignored` catch in the broadcast loop swallows all `DataIntegrityViolationException`s, not just duplicate-key violations. A genuine constraint violation (e.g., a bad FK because `caregiverId` no longer exists) would be silently discarded. Narrow the guard with a message check, or log at WARN before ignoring:
```java
} catch (DataIntegrityViolationException e) {
    log.warn("Skipping duplicate offer for shift={} caregiver={}: {}", 
        shiftId, rc.caregiverId(), e.getMessage());
}
```

### M5 — No Role Enforcement on Mutation Endpoints
All endpoints require authentication (JWT) but no `@PreAuthorize` restricts mutation endpoints to `ADMIN` or `SCHEDULER`. Any authenticated user can create, assign, cancel, or broadcast shifts. The handoff notes this is "ADMIN/SCHEDULER only in P1." The plan should include either `@PreAuthorize("hasRole('ADMIN') or hasRole('SCHEDULER')")` on the mutation endpoints, or a comment explicitly deferring this to a later task with a `// TODO(security): role-restrict this endpoint` marker so it isn't forgotten.

### M6 — No Pagination on `GET /shifts`
A calendar range of 12 months for a 25-caregiver agency with daily shifts = ~9,000 shifts returned in one call. For P1 this is acceptable, but the endpoint design should at minimum document the intended max range in a comment, or the plan should note this as a known gap with a suggested page size for future implementation.

### M7 — `RecurrencePatternController.updatePattern` Missing `@Valid`
The `PATCH /{id}` handler lacks `@Valid` on the request body. The current `UpdateRecurrencePatternRequest` has no Bean Validation annotations so this is harmless today, but when M3's `@Min(1)` is added, the missing `@Valid` would silently skip validation. Add `@Valid` proactively.

### M8 — `git add -A` in Task 8 Step 3
Task 8 Step 3 uses `git add -A` which stages everything untracked including any stray files. Prefer explicit paths matching the pattern used in Tasks 1–7, or at minimum confirm `.gitignore` excludes `target/` before executing.

---

## 5. Questions for Clarification

1. **`ShiftOffer` Hibernate filter**: Does `ShiftOffer` have the `agencyFilter` `@Filter` annotation applied? The `respondToOffer` method loads offers by ID (`findById`) — if `ShiftOffer` is not tenant-filtered, a user from agency A could process an offer belonging to agency B's shift. (Point: CLAUDE.md says the framework handles this, but it only works if `@Filter` is applied to the entity.)

2. **`cancelShift` COMPLETED guard**: The plan tests that `IN_PROGRESS` shifts throw 409 on cancel, but the guard is `status != OPEN && status != ASSIGNED`. This means `COMPLETED` and `MISSED` shifts also correctly throw 409. However, there is no IT test confirming this for `COMPLETED` specifically — Task 4 has a `cancelShift_on_completed_shift_returns_409` IT test. Is there a corresponding unit test for `CANCELLED` (re-cancel) in `ShiftSchedulingServiceTest`? It appears not. Low priority but gap.

3. **`respondToOffer` double-accept scenario**: If offer1 is ACCEPTED and shift transitions to ASSIGNED, can a second ACCEPT on offer2 proceed? Task 7's Step 8 tests this (expects 409 because offer2 is already DECLINED). But this relies on offer2 being auto-declined in the same transaction as offer1's acceptance. If offer2 has not yet been declined (edge case: the loop in `respondToOffer` hasn't committed yet), a concurrent accept on offer2 would pass the `offer.getResponse() != NO_RESPONSE` check. The fix in C3 (pessimistic locking on the shift) addresses the shift assignment half of this, but the offer-level check still has a window.

---

## 6. Final Recommendation

**Approve with changes.**

The plan is executable and will produce a working implementation. The four critical issues (C1–C4) must be addressed before the plan is handed to an agentic executor:
- C1 is a one-line fix (test count in expected output)
- C2 requires adding ~7 unit tests to Task 3
- C3 requires adding one repository method (Task 1) and updating `respondToOffer` (Task 3)
- C4 requires a 3-line guard in `respondToOffer` and one new unit test

The minor issues (M1–M8) are recommended improvements but will not cause test failures. M5 (role enforcement) is the highest-priority minor issue from a security standpoint and should be addressed in this plan rather than deferred.
