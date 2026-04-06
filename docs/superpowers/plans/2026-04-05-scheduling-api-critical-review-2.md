# Critical Implementation Review — Plan 6: Scheduling REST API
**Review:** 2 of N
**Plan file:** `docs/superpowers/plans/2026-04-05-scheduling-api.md`
**Reviewer:** Senior Staff Engineer
**Date:** 2026-04-05
**Previous reviews:** Review 1 (2026-04-05)

---

## 1. Overall Assessment

Review 1 correctly identified and resolved the most dangerous defects: the TOCTOU race condition (C3), the `NO_RESPONSE` validation gap (C4), the missing unit tests (C2), and the test count discrepancy (C1). The updated plan as written is substantially stronger. This second review, working from the full plan text including all the code that was added in response to Review 1, identifies two new correctness issues that were introduced during the C3/C4 fixes, one meaningful transaction-ordering defect in `respondToOffer`, one cross-cutting tenant isolation gap, and a set of lower-priority issues. None of the items below duplicate anything raised in Review 1.

---

## 2. Critical Issues

### C5 — `respondToOffer` Saves the Offer Before Acquiring the Pessimistic Lock — Incorrect Ordering

**File:** `ShiftSchedulingService.java` (Task 3, Step 3, `respondToOffer` method), lines 1154–1156 and 1161.

The current sequence in `respondToOffer` is:

```
1. Guard: NO_RESPONSE check
2. Load shift with plain findById (non-locking)
3. Load offer with findById
4. Guard: offer.shiftId matches path shiftId
5. Guard: offer.response != NO_RESPONSE
6. offer.respond(req.response())          // ← mutates offer
7. shiftOfferRepository.save(offer)       // ← WRITE committed if tx flushes here
8. if ACCEPTED:
     Shift shift = findByIdForUpdate(...)  // ← acquires lock HERE, after offer is already written
     if shift.status != OPEN → throw 409   // ← too late, offer already saved
```

The offer's `respond()` call and `shiftOfferRepository.save(offer)` happen unconditionally at step 7, before the pessimistic lock on the shift is acquired at step 8. Under Spring's default flush mode (`AUTO`), the offer save may flush to the DB before the 409 guard at step 8 fires. If the shift is no longer OPEN, the method throws 409 after the offer has already been marked ACCEPTED and saved. The caller gets a 409 error response but the `ShiftOffer` row in the database now has `response = ACCEPTED` and a populated `respondedAt` — corrupted state.

**Why it matters:** This is a data-integrity defect. An ACCEPTED offer with no corresponding shift assignment is an inconsistent state that will cause downstream confusion (the offer list shows ACCEPTED but the shift is still OPEN and unassigned).

**Fix:** Move the offer mutation and save to *after* the shift lock is acquired and the OPEN guard passes. The corrected ordering for the ACCEPTED path is:

```java
if (req.response() == ShiftOfferResponse.ACCEPTED) {
    Shift shift = shiftRepository.findByIdForUpdate(shiftId)
        .orElseThrow(...);
    if (shift.getStatus() != ShiftStatus.OPEN) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, ...);
    }
    // Only now mutate and save the offer
    offer.respond(ShiftOfferResponse.ACCEPTED);
    shiftOfferRepository.save(offer);

    shift.setCaregiverId(offer.getCaregiverId());
    shift.setStatus(ShiftStatus.ASSIGNED);
    shiftRepository.save(shift);

    // Decline others
    shiftOfferRepository.findByShiftId(shiftId).stream()
        .filter(o -> !o.getId().equals(offerId) && o.getResponse() == ShiftOfferResponse.NO_RESPONSE)
        .forEach(o -> { o.respond(ShiftOfferResponse.DECLINED); shiftOfferRepository.save(o); });

} else {
    // DECLINED path — no shift lock needed
    offer.respond(ShiftOfferResponse.DECLINED);
    shiftOfferRepository.save(offer);
}
```

The existing unit test `respondToOffer_accepted_on_non_open_shift_throws_409` must also be updated: it currently stubs `shiftRepository.save(any())` with `never()`, but does not assert that `shiftOfferRepository.save(any())` was also never called. Add:

```java
verify(shiftOfferRepository, never()).save(any());
```

---

### C6 — `respondToOffer` Loads Shift Twice — Unnecessary and Inconsistent

**File:** `ShiftSchedulingService.java` (Task 3, Step 3), `respondToOffer` method.

The method calls `requireShift(shiftId)` (a plain `findById`) at line 1143 and then, inside the ACCEPTED branch, calls `shiftRepository.findByIdForUpdate(shiftId)` at line 1161. This loads the same shift row twice within the same transaction. The first load is wasted work and could return a stale snapshot compared to the locked second load. While Hibernate's first-level cache will return the same in-memory object for the second load (since the IDs match), the intent of the lock is undermined: the `PESSIMISTIC_WRITE` lock hint must be present on the load that is used for the status check. The current code guards on the locked load's status (correct) but loads an unlocked copy first (unnecessary noise).

**Why it matters:** The first `requireShift` call is dead code for the ACCEPTED path (its return value is discarded). It is confusing and creates a latent bug: if a future refactor moves the OPEN guard above the lock acquisition, the unlocked load will be used for the guard, re-introducing the TOCTOU bug that C3 was intended to fix.

**Fix:** Remove `requireShift(shiftId)` from the top of `respondToOffer`. Instead, load the shift lazily:
- For the DECLINED path, load the shift only if you need it (currently it is not used at all in the DECLINED path — the current code is only using it as a guard-the-path-exists check).
- If the shift-existence check is genuinely needed before the offer lookup (e.g., to return 404 on unknown shiftId before returning 404 on unknown offerId), keep it but document it explicitly. In the current code the `offer.getShiftId().equals(shiftId)` guard on line 1146 already ensures the offer belongs to the shift, so the pre-check on the shift is redundant.

The simplest correct structure:

```java
@Transactional
public ShiftOfferSummary respondToOffer(UUID shiftId, UUID offerId, RespondToOfferRequest req) {
    if (req.response() == ShiftOfferResponse.NO_RESPONSE) { ... }

    ShiftOffer offer = shiftOfferRepository.findById(offerId)
        .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Offer not found"));
    if (!offer.getShiftId().equals(shiftId)) {
        throw new ResponseStatusException(NOT_FOUND, "Offer does not belong to this shift");
    }
    if (offer.getResponse() != ShiftOfferResponse.NO_RESPONSE) {
        throw new ResponseStatusException(CONFLICT, "Offer already has a response: " + offer.getResponse());
    }

    if (req.response() == ShiftOfferResponse.ACCEPTED) {
        Shift shift = shiftRepository.findByIdForUpdate(shiftId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Shift not found"));
        if (shift.getStatus() != ShiftStatus.OPEN) { ... }
        offer.respond(ACCEPTED);
        shiftOfferRepository.save(offer);
        shift.setCaregiverId(offer.getCaregiverId());
        shift.setStatus(ASSIGNED);
        shiftRepository.save(shift);
        // decline others ...
    } else {
        offer.respond(DECLINED);
        shiftOfferRepository.save(offer);
    }
    return toOfferSummary(offer);
}
```

This eliminates the redundant load and makes the locking intent unambiguous. Update the unit test `respondToOffer_on_already_responded_offer_throws_409` to remove the `when(shiftRepository.findById(shiftId)).thenReturn(...)` stub since it is no longer needed.

---

### C7 — `RecurrencePatternService.deactivatePattern` Has No Tenant Guard on the Offer Delete

**File:** `RecurrencePatternService.java` (Task 5, Step 3), `deactivatePattern` method.

`deactivatePattern` calls `shiftRepository.deleteUnstartedFutureShifts(patternId, pattern.getAgencyId(), ...)`. The JPQL `DELETE` query in `ShiftRepository` explicitly includes `agencyId` in the WHERE clause (the existing comment notes that `@Filter` does not apply to bulk JPQL deletes). This is correct.

However, the method acquires `pattern` via `requirePattern(patternId)`, which calls `patternRepository.findById(patternId)`. `RecurrencePattern` has `@Filter(name = "agencyFilter")` applied, so when the Hibernate filter is active (i.e., when called through the normal request path with `TenantContext` set), `findById` will correctly return `Optional.empty()` if `patternId` belongs to a different agency, and the method throws 404. This is correct.

The gap is that `deactivatePattern` is also the entry point used by `RecurrencePatternController.deletePattern`, which is called with the authenticated user's `agencyId` in context. The concern is that `deleteUnstartedFutureShifts` is called with `pattern.getAgencyId()` — the agencyId stored on the pattern object. If the filter is active and findById returns the correct pattern, the agencyIds are consistent. This is fine.

The actual concern is in `updatePattern`: when `needsRegeneration` is true, `shiftGenerationService.regenerateAfterEdit(pattern)` is called *after* `patternRepository.save(pattern)` but *within the same `@Transactional` method*. The `regenerateAfterEdit` implementation (in `LocalShiftGenerationService`) calls `shiftRepository.deleteUnstartedFutureShifts(...)` with bulk JPQL. If `regenerateAfterEdit` is itself `@Transactional`, Spring will reuse the existing transaction (same thread, same TX context). The Hibernate `agencyFilter` is set by `TenantFilterAspect` as a `@Before` advice on repository calls — but `ShiftGenerationService.regenerateAfterEdit` may not be a Spring Data repository call; it is a service method. Verify that `TenantFilterAspect` pointcut includes calls to `ShiftGenerationService` methods, or that `regenerateAfterEdit` explicitly inherits the session filter from the outer transaction.

**Why it matters:** If the filter is not inherited, the `findActivePatternsBehindHorizon` query called inside `regenerateAfterEdit` (or similar internal queries) may return patterns from other agencies.

**Fix:** Verify that `TenantFilterAspect`'s pointcut covers `com.hcare.scheduling.*` service calls, or confirm that all session-filter-dependent queries inside `regenerateAfterEdit` are either Spring Data repository calls (covered by the pointcut) or explicitly do not require the filter (like the `deleteUnstartedFutureShifts` bulk delete which includes `agencyId` in its WHERE clause directly).

Add a clarifying comment in `RecurrencePatternService.updatePattern`:

```java
// regenerateAfterEdit runs within this transaction — the Hibernate agencyFilter
// is inherited from the current session. The deleteUnstartedFutureShifts bulk DELETE
// uses an explicit agencyId parameter and does not rely on the filter.
shiftGenerationService.regenerateAfterEdit(pattern);
```

---

## 3. Previously Addressed Items

The following issues from Review 1 are confirmed resolved in the updated plan:

- **C1** — Test count discrepancy: Task 3 Step 4 now expects `Tests run: 19`.
- **C2** — Missing unit tests for `getCandidates`, `broadcastShift`, `listOffers`, `respondToOffer`: all seven tests are present in the updated `ShiftSchedulingServiceTest`.
- **C3** — TOCTOU race condition: `findByIdForUpdate` added to Task 1 Step 10 and used in `respondToOffer`'s ACCEPTED path.
- **C4** — `NO_RESPONSE` accepted as valid: guard added at top of `respondToOffer`; unit test `respondToOffer_with_NO_RESPONSE_throws_400` included.
- **M2** — `daysOfWeek` unvalidated: `@Pattern` constraint added to both request DTOs.
- **M3** — `UpdateRecurrencePatternRequest.scheduledDurationMinutes` missing `@Min(1)`: now present.
- **M4** — `broadcastShift` swallows all `DataIntegrityViolationException`s silently: WARN log added.
- **M5** — No role enforcement on mutation endpoints: `@PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")` added throughout; `DELETE /recurrence-patterns/{id}` correctly uses `hasRole('ADMIN')` only.
- **M7** — `RecurrencePatternController.updatePattern` missing `@Valid`: `@Valid` is present on the `updatePattern` handler.

---

## 4. Minor Issues and Improvements

### M9 — Task 7 E2E Test Step 6 Uses a Confusing Inline Import Syntax

**File:** Plan Task 7 Step 1, the "Replace step 6" instruction.

The plan instructs the agentic executor to add:

```java
import com.hcare.api.v1.visits.dto.ShiftDetailResponse;
```

as an *inline import comment* inside the test method body. Java imports must appear at the file level, not inside a method body. While the plan also says "Add the import at the top of `ShiftSchedulingControllerIT.java`", the inline example is confusing and may cause the agentic executor to paste the import statement inside the method, causing a compilation error.

**Fix:** Remove the inline `import` statement from the code block shown in step 6 and ensure only the "Add the import at the top" instruction remains.

### M10 — `GET /shifts` Has No Validation That `start` Is Before `end`

**File:** `ShiftSchedulingController.java` (Task 4, Step 3) and `ShiftSchedulingService.listShifts`.

A caller can pass `?start=2026-05-08T00:00:00&end=2026-05-01T00:00:00` (end before start). `findByAgencyIdAndScheduledStartBetween` will execute successfully with an inverted range and return an empty list — no error, no indication to the caller that their request was malformed. For a calendar endpoint used to populate a UI view, an inverted window is almost certainly a client programming error.

**Fix:** Add a guard in `listShifts`:

```java
if (!end.isAfter(start)) {
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
        "end must be after start");
}
```

### M11 — `RecurrencePatternService.updatePattern` Does Not Guard Against a No-Op Patch

The service accepts a `PATCH` request where all fields are null (an empty patch body `{}`). The current behavior saves the pattern unchanged and returns 200. While not strictly wrong, it is unnecessary work — the pattern is loaded, saved (triggering a Hibernate dirty check and UPDATE query), and the version counter is incremented, even though nothing changed. This can cause spurious optimistic locking conflicts if the nightly scheduler is also updating `generatedThrough` concurrently.

**Fix:** Add a guard:

```java
if (!needsRegeneration && req.caregiverId() == null
        && req.authorizationId() == null && req.endDate() == null) {
    return toResponse(pattern); // nothing to update
}
```

### M12 — `RecurrencePatternServiceTest` Unit Test Count Mismatch

**File:** Plan Task 5, Step 4.

The `RecurrencePatternServiceTest` as written contains exactly 9 test methods:
- `createPattern_saves_pattern_and_calls_generateForPattern`
- `getPattern_returns_response_when_found`
- `getPattern_throws_404_when_not_found`
- `updatePattern_with_new_scheduledStartTime_calls_regenerateAfterEdit`
- `updatePattern_with_new_scheduledDurationMinutes_calls_regenerateAfterEdit`
- `updatePattern_with_new_daysOfWeek_calls_regenerateAfterEdit`
- `updatePattern_caregiverId_only_saves_in_place_without_regeneration`
- `updatePattern_endDate_only_saves_in_place_without_regeneration`
- `deactivatePattern_sets_isActive_false_and_deletes_future_shifts`

The plan's Step 4 expected output says `Tests run: 9, Failures: 0` — this count is correct. Flagged only for completeness; no fix needed.

### M13 — `listShifts` IT Test Does Not Verify Tenant Isolation

**File:** `ShiftSchedulingControllerIT.java` (Task 4, Step 1).

The IT test suite creates one agency and verifies that `GET /shifts` returns shifts belonging to that agency. It does not create a second agency with shifts in the same time window and assert those shifts are excluded. This mirrors a pattern already noted in the domain tests (which do test cross-agency isolation), but the controller-level IT test is where end-to-end tenant isolation would be most visible and impactful.

**Recommendation:** Add one IT test:

```java
@Test
void listShifts_does_not_return_shifts_from_another_agency() {
    Agency other = agencyRepo.save(new Agency("Other Agency", "TX"));
    Client otherClient = clientRepo.save(new Client(other.getId(), "Other", "Client", LocalDate.of(1970,1,1)));
    ServiceType otherSt = serviceTypeRepo.save(new ServiceType(other.getId(), "PCS", "PCS-OTH", true, "[]"));
    shiftRepo.save(new Shift(other.getId(), null, otherClient.getId(), null, otherSt.getId(), null,
        LocalDateTime.of(2026, 5, 3, 9, 0), LocalDateTime.of(2026, 5, 3, 13, 0)));

    ResponseEntity<List<ShiftSummaryResponse>> resp = restTemplate.exchange(
        "/api/v1/shifts?start=2026-05-01T00:00:00&end=2026-05-08T00:00:00",
        HttpMethod.GET, new HttpEntity<>(auth()),
        new ParameterizedTypeReference<>() {});

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(resp.getBody()).isEmpty();
}
```

### M14 — `broadcastShift` IT Has No Happy-Path Test

**File:** `ShiftSchedulingControllerIT.java` (Task 4, Step 1).

The only IT test for `POST /shifts/{id}/broadcast` is the negative case (non-OPEN shift returns 409). There is no IT test verifying that a broadcast on an OPEN shift returns 200 with the expected offer list. While the unit test covers this path, the IT test gap means the integration between `ScoringService`, `ShiftOfferRepository`, and the controller is not exercised in the full Spring context.

**Recommendation:** Add:

```java
@Test
void broadcastShift_on_open_shift_returns_200_with_offer_list() {
    Shift shift = shiftRepo.save(new Shift(agency.getId(), null, client.getId(), null,
        serviceType.getId(), null,
        LocalDateTime.of(2026, 5, 18, 9, 0), LocalDateTime.of(2026, 5, 18, 13, 0)));

    ResponseEntity<List<ShiftOfferSummary>> resp = restTemplate.exchange(
        "/api/v1/shifts/" + shift.getId() + "/broadcast",
        HttpMethod.POST, new HttpEntity<>(auth()),
        new ParameterizedTypeReference<>() {});

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    // Result may be empty if no caregiver scoring profiles are seeded — that is acceptable
    assertThat(resp.getBody()).isNotNull();
}
```

---

## 5. Questions for Clarification

1. **`ShiftSchedulingService` missing `listPatterns` (GET all patterns for agency):** The plan includes `RecurrencePatternController` with `GET /{id}` (single pattern) but no `GET /recurrence-patterns` (list all patterns for the authenticated agency). The `findByAgencyId` repository method added in Task 1 is never called by the service. Was the list endpoint intentionally deferred, or is this an omission? If deferred, a comment in `RecurrencePatternController` noting `// TODO: GET /recurrence-patterns list endpoint` would prevent ambiguity.

2. **`TenantFilterAspect` pointcut scope for `regenerateAfterEdit`:** The CLAUDE.md states `TenantFilterAspect` enables the Hibernate session filter `@Before` repository calls inside `@Transactional`. Does this pointcut cover `ShiftGenerationService.regenerateAfterEdit` (a service method, not a repository call)? If the pointcut is limited to `com.hcare.domain.*Repository`, then internal repository calls made by `regenerateAfterEdit` would need to be reachable through new `@Transactional` proxied boundaries that the aspect intercepts — which they would not be if called within the same transaction. Clarifying this is important before the plan executes.

3. **`authorizationId` cross-validation in `createShift`:** `CreateShiftRequest` accepts an `authorizationId` (nullable). There is no validation that the authorization belongs to the shift's client or agency. A scheduler could attach a valid authorizationId from a different client's authorization to a new shift — this would pass validation and be stored. Is cross-entity FK validation intended to be deferred to a later plan?

---

## 6. Final Recommendation

**Approve with changes.**

C5 is a genuine data-integrity defect introduced by the C3 fix in Review 1 — the locking sequence is correct but the offer mutation must be moved to after the lock is acquired. C6 removes dead code that creates a latent maintainability hazard. C7 is a question requiring confirmation before execution. C5 and C6 together require restructuring approximately 15 lines in `respondToOffer` and updating two unit tests. The minor issues (M9–M14) are non-blocking but M9 in particular should be fixed to prevent a compilation error in an agentic execution context.
