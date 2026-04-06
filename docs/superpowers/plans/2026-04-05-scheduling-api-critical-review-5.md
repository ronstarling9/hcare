# Critical Implementation Review — Plan 6: Scheduling REST API
**Review:** 5 of N
**Plan file:** `docs/superpowers/plans/2026-04-05-scheduling-api.md`
**Reviewer:** Senior Staff Engineer
**Date:** 2026-04-05
**Previous reviews:** Reviews 1–4 (all 2026-04-05)

---

## 1. Overall Assessment

Across five review cycles, C1–C12 have all been resolved in the current plan text, and M19/M20 (flagged unincorporated in Review 4) are now also confirmed addressed. The plan is close to ready for agentic execution. This fifth pass surfaces **one new critical issue** (C13) — a concurrency gap in `broadcastShift` that is structurally identical to the anti-pattern C9 was supposed to fix, but for concurrent callers rather than sequential re-broadcasts — and **three minor issues** (M21–M23). After C13 is resolved, the plan is ready.

---

## 2. Critical Issues

### C13 — `broadcastShift` Pre-Flight Check Does Not Protect Against Concurrent Callers — Same `DataIntegrityViolationException`-inside-`@Transactional` Anti-Pattern as C9

**File:** `ShiftSchedulingService.java` (Task 3, Step 3, `broadcastShift` method, lines ~1180–1191).

The C9 fix replaced a `try/catch DataIntegrityViolationException` inside `@Transactional` with a pre-flight `findByCaregiverIdAndShiftId(...).isEmpty()` check. The C9 comment in the plan reads:

```java
// C9: pre-flight duplicate check — avoids catching DataIntegrityViolationException
// inside a @Transactional method, which would poison the transaction (Spring marks
// it rollback-only before the catch block is reached).
```

This comment **implies that a unique constraint on `(shift_id, caregiver_id)` exists in the `shift_offers` table** — otherwise there would be no `DataIntegrityViolationException` to worry about in the first place. The pre-flight check solves the sequential re-broadcast case (one user broadcasting twice): the second call sees a non-empty result and skips `save`. However, two concurrent requests to `POST /api/v1/shifts/{id}/broadcast` for the same shift can both read `Optional.empty()` from the pre-flight check before either has committed its `save`. One `save` succeeds; the other hits the unique constraint and throws `DataIntegrityViolationException` **inside the active `@Transactional` boundary**. Spring's `AbstractPlatformTransactionManager` catches it before the user-space `forEach` resumes, marks the transaction rollback-only, and the exception propagates to the caller as a 500. This is the same anti-pattern C9 was supposed to eliminate — just triggered by concurrency instead of sequential re-entry.

**Why it matters:** Two schedulers simultaneously broadcasting the same understaffed shift is a realistic scenario. One broadcast call would silently roll back and return HTTP 500, leaving the shift without some of the expected offers.

**Fix (recommended — Option A, defensive + cheap):** Inside the `for` loop, replace the pre-flight check with a per-offer `REQUIRES_NEW` save that catches the exception at the boundary of a fresh transaction, keeping the outer transaction clean:

```java
for (RankedCaregiver rc : eligible) {
    offerCreationService.createOfferIfAbsent(shiftId, rc.caregiverId(), shift.getAgencyId());
}
```

where `offerCreationService.createOfferIfAbsent` is a new `@Service` method annotated `@Transactional(propagation = REQUIRES_NEW)`:

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void createOfferIfAbsent(UUID shiftId, UUID caregiverId, UUID agencyId) {
    if (shiftOfferRepository.findByCaregiverIdAndShiftId(caregiverId, shiftId).isEmpty()) {
        shiftOfferRepository.save(new ShiftOffer(shiftId, caregiverId, agencyId));
    }
}
```

Each offer creation is its own sub-transaction. If two concurrent broadcasts race, one sub-transaction commits and the other fails — but the failure is isolated to that sub-transaction; the outer transaction and the rest of the offer loop are unaffected.

**Fix (Option B — pragmatic if concurrency is genuinely not a concern):** Drop the unique constraint on `(shift_id, caregiver_id)` in `shift_offers` and rely solely on the pre-flight check. The deduplication guarantee is then best-effort under race conditions. Add a code comment documenting this trade-off explicitly so future maintainers don't re-add the constraint without adjusting the service.

**Fix (Option C — minimal scope):** Keep the pre-flight check but add a `REQUIRES_NEW` wrapper only around the `save`, catching and logging `DataIntegrityViolationException` without rethrowing. This is mechanically safe but couples the service to exception handling, which the original C9 fix deliberately avoided.

Option A is cleanest. The plan should add `ShiftOfferCreationService` (a one-method helper) to the File Map and Task 3.

---

## 3. Previously Addressed Items

All issues from Reviews 1–4 are confirmed resolved in the current plan text:

- **C1–C4** (Review 1): test count, missing unit tests, TOCTOU race, NO_RESPONSE guard — all present and correct.
- **C5–C7** (Review 2): offer-before-lock ordering, double-load removal, tenant filter comment — all confirmed.
- **C8–C10** (Review 3): `createShift` ASSIGNED status on caregiverId, broadcast `DataIntegrityViolationException`, no-op guard position — all confirmed.
- **C11** (Review 4): `CreateShiftRequest` constructor argument count — confirmed fixed (7 arguments, caregiverId in position 2 at line 691).
- **C12** (Review 4): null-safe `equals` in "decline others" filter — confirmed at line 1240 (`!offerId.equals(o.getId())`).
- **M19** (Review 4, previously M15 from Review 3): `broadcastShift` unit test now stubs and verifies both `findByCaregiverIdAndShiftId` calls (lines 880–892, with `// C9 idempotency` comment). Resolved.
- **M20** (Review 4): Task 7 write-then-replace instruction removed; test written directly with `ShiftDetailResponse` and a pre-check note. Resolved.

---

## 4. Minor Issues & Improvements

### M21 — `ShiftRepository.findByClientIdAndScheduledStartBetween` Used in Task 6 IT Tests But Not Added in Task 1 and Not Confirmed to Exist

**Files:** `RecurrencePatternControllerIT.java` (Task 6, Step 1), three test methods: `createPattern_returns_201_and_generates_shifts`, `updatePattern_scheduling_fields_trigger_regeneration`, `updatePattern_non_scheduling_fields_do_not_trigger_regeneration`, `deletePattern_removes_future_unstarted_shifts`.

All four tests call `shiftRepo.findByClientIdAndScheduledStartBetween(...)`. Task 1's File Map and Step sequences add only two new methods to `ShiftRepository`: `findByAgencyIdAndScheduledStartBetween` and `findByIdForUpdate`. The plan header states "no new persistence layer code is needed except two new repository query methods." If `findByClientIdAndScheduledStartBetween` does not already exist in the current codebase, Task 6 will fail to compile.

**Fix:** Add a pre-check note at the start of Task 6 Step 1:

> Before writing this test class, confirm `ShiftRepository` already declares `findByClientIdAndScheduledStartBetween(UUID clientId, LocalDateTime start, LocalDateTime end)`. If it does not, add it to Task 1 Step 3 (alongside `findByAgencyIdAndScheduledStartBetween`) with a corresponding domain IT test step.

---

### M22 — `assignCaregiver` Does Not Validate That the Supplied `caregiverId` Belongs to the Same Agency

**File:** `ShiftSchedulingService.java` (Task 3, Step 3, `assignCaregiver` method, lines ~1119–1128).

```java
shift.setCaregiverId(req.caregiverId());
shift.setStatus(ShiftStatus.ASSIGNED);
return toSummary(shiftRepository.save(shift));
```

The Hibernate `agencyFilter` prevents SELECT queries from leaking cross-agency data but does not prevent writing a foreign key that references a row from another agency. A `SCHEDULER` for Agency A who knows a caregiver UUID from Agency B (e.g., via a past data breach or enumeration) can set that caregiver on Agency A's shifts. A `CaregiverRepository.existsByIdAndAgencyId(caregiverId, agencyId)` check before the assignment would close this gap.

The same observation applies to `createShift` for the `caregiverId` and `clientId` fields, though `clientId` cross-agency writes would produce FK violations if cascading constraints are in place. `caregiverId` is the higher-risk field since misassignment is operationally valid at the DB level but violates business rules.

**Fix:** Add a pre-assignment guard in `assignCaregiver` (and optionally in `createShift`):

```java
if (!caregiverRepository.existsByIdAndAgencyId(req.caregiverId(), shift.getAgencyId())) {
    throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
        "Caregiver does not belong to this agency");
}
```

If adding `caregiverRepository` as a dependency to `ShiftSchedulingService` is undesirable, this check can be delegated to a domain method on `Shift` or done at the DB level via a check constraint.

---

### M23 — Task 1 Step 10 Does Not List `@Param` or `@Query` Imports Required by `findByIdForUpdate`

**File:** Plan Task 1, Step 10.

The plan instructs the agent to add these imports to `ShiftRepository.java`:

```java
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import java.util.Optional;
```

The new method body uses `@Query` and `@Param`, which require:

```java
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
```

If `findOverlapping` (the existing method) already uses `@Query` and `@Param`, these imports are present and no action is needed. But the plan does not confirm this. An agentic executor that adds only the listed imports will get a compilation error on `@Query` or `@Param` if they are not already present.

**Fix:** Extend the import list in Task 1 Step 10 to include all four imports, with a note: "Skip any that are already present — `findOverlapping` likely imports `@Query` and `@Param` already."

---

## 5. Questions for Clarification

1. **Does `shift_offers` have a unique constraint on `(shift_id, caregiver_id)`?** The C9 comment implies it does. If it does, C13 is a definite critical gap; if it does not, the pre-flight check is the only idempotency guarantee and C13 is reduced to a documentation gap (the comment should be updated to remove the implication of a constraint).

2. **Does `ShiftRepository` already declare `findByClientIdAndScheduledStartBetween`?** Needed to determine whether Task 6 can compile without changes to Task 1.

3. **M16 (Review 3) / Q3 (Review 4) — `RecurrencePatternControllerIT` cross-agency isolation test — still absent.** The shift controller IT has a corresponding `listShifts_does_not_return_shifts_from_another_agency` test. Is the recurrence pattern cross-agency isolation test being intentionally deferred?

---

## 6. Final Recommendation

**Approve with changes.**

C13 is a genuine concurrency gap that re-introduces the `DataIntegrityViolationException`-inside-`@Transactional` anti-pattern that C9 explicitly fixed for the sequential case — if the unique constraint on `(shift_id, caregiver_id)` exists. The fix is well-defined (Option A: extract a `REQUIRES_NEW` save helper). M21 and M23 are low-cost pre-checks that prevent compile-time surprises for an agentic executor; M22 is a security hardening item that is lower priority but cleanly fixable. After C13 is addressed and the two pre-checks (M21, M23) are added, the plan is ready for agentic execution.
