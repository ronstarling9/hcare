# Critical Implementation Review — AI Scoring Engine
**Plan:** `2026-04-05-ai-scoring-engine.md`
**Review:** 1 of N (first pass — no prior reviews)
**Reviewer stance:** Production-grade, iterative review. Only raises new issues.

---

## 1. Overall Assessment

The plan is detailed, well-structured, and TDD-compliant. The domain model, scoring weights, hard-filter logic, and event-listener wiring are coherent. Task sequencing is solid (value objects → migration → entity → service → event wiring → integration tests). However, there are four issues worth blocking on before implementation: a tech-stack header typo, a silent correctness bug in the authorization check, a dead-code retry loop caused by a missing `@Version` annotation, and a leaky public interface that will require a breaking change at P2. Several smaller issues are also noted below.

---

## 2. Critical Issues

### CI-1 — Tech stack header lists "Java 25" (should be Java 21)

**Description:** The plan's "Tech Stack" section reads: `Java 25, Spring Boot 3.4.4`. Java 25 does not yet exist (scheduled GA September 2026); CLAUDE.md mandates Java 21.

**Why it matters:** If an automated agent reads this header to configure the build (toolchain plugin, `java.version` property, CI matrix), it will either fail to compile or silently use a different JDK than every other module in the project. Spring Boot 3.4.4 is not certified against Java 25.

**Fix:** Change `Java 25` → `Java 21` in the Tech Stack line.

---

### CI-2 — Non-null `authorizationId` not found in DB silently passes the authorization hard filter

**Description:** In `rankCandidates`:
```java
Authorization authorization = request.authorizationId() != null
    ? authorizationRepository.findById(request.authorizationId()).orElse(null)
    : null;
```
If `authorizationId` is non-null but the record does not exist, `authorization` is `null`. The hard filter treats `null` authorization as "skip auth check" and passes all candidates. A shift can be booked against a deleted, expired, or mistyped authorization UUID with no error.

**Why it matters:** This is a correctness and data integrity bug. It allows scheduling against non-existent authorizations, which breaks payer billing and EVV compliance tracking silently.

**Fix:** When `authorizationId` is non-null, throw `IllegalArgumentException` (same pattern as the client/serviceType lookups) rather than silently treating a missing authorization as "no authorization". Add a unit test that verifies this throws.

```java
Authorization authorization = request.authorizationId() != null
    ? authorizationRepository.findById(request.authorizationId())
          .orElseThrow(() -> new IllegalArgumentException(
              "Authorization not found: " + request.authorizationId()))
    : null;
```

---

### CI-3 — `updateAffinity` retry loop is dead code without `@Version` on `CaregiverClientAffinity`

**Description:** `updateAffinity` wraps the affinity increment in a three-attempt retry loop that catches `OptimisticLockingFailureException`. Spring Data JPA only throws that exception when the entity carries a `@Version` field. The plan does not mention adding `@Version` to `CaregiverClientAffinity`, and the entity is not shown. Without it:
- `OptimisticLockingFailureException` is never thrown → the retry loop is unreachable dead code.
- Two concurrent `ShiftCompletedEvent` firings for the same caregiver/client pair (e.g., rapid re-delivery) will both succeed the `findByScoringProfileIdAndClientId + save-if-absent` check in the same instant, causing a **duplicate insert** that hits a unique constraint (if one exists) or silently creates two affinity rows (if not). Either outcome is wrong.

**Why it matters:** Concurrency correctness for the affinity counter — the stated reason for the retry loop. The current code provides neither the protection it claims (optimistic locking) nor the protection it needs (duplicate-insert guard).

**Fix:** Two changes required:
1. Add `@Version private int version;` to `CaregiverClientAffinity` (with a migration column `version INT NOT NULL DEFAULT 0`).
2. Ensure a unique constraint exists on `(scoring_profile_id, client_id)` so that duplicate inserts fail fast on the constraint rather than silently duplicating data.

Both changes must be called out in this plan or in a companion migration.

---

### CI-4 — `ScoringService` interface exposes event listener methods, creating a leaky abstraction that requires a breaking change at P2

**Description:** `ScoringService` declares `onShiftCompleted(ShiftCompletedEvent)` and `onShiftCancelled(ShiftCancelledEvent)`. The plan's own P2 extraction note says: *"swap `LocalScoringService` for `HttpScoringServiceClient`, route events to a message broker. No caller changes required."* But `HttpScoringServiceClient` would implement `ScoringService` — meaning it would need to implement `onShiftCompleted` and `onShiftCancelled`, which have no meaningful remote semantics (they would be no-ops, since events route to a broker instead). The interface contract is already wrong, and the P2 claim ("no caller changes required") is false.

**Why it matters:** Event listener concerns are implementation details, not public API. Publishing this on the interface now means future consumers (test helpers, alternative implementations, the P2 HTTP client) must implement or stub two methods that have no business meaning to them.

**Fix:** Remove `onShiftCompleted` and `onShiftCancelled` from `ScoringService`. Annotate them on `LocalScoringService` directly (which is already the only place `@TransactionalEventListener` is placed). Spring's event dispatcher calls the concrete method by annotation — it does not need the method on the interface.

```java
// ScoringService.java — only the business method
public interface ScoringService {
    List<RankedCaregiver> rankCandidates(ShiftMatchRequest request);
}

// LocalScoringService.java — event wiring stays on the implementation
@TransactionalEventListener
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void onShiftCompleted(ShiftCompletedEvent event) { ... }
```

---

## 3. Previously Addressed Items

None — this is the first review.

---

## 4. Minor Issues & Improvements

### MI-1 — Conflict query uses a 24-hour heuristic instead of a proper interval overlap query

`findByCaregiverIdAndScheduledStartBetween(caregiverId, start.minusHours(24), end)` is a heuristic. Any shift longer than 24 hours that starts before the 24h window but ends inside the new shift would be missed. A proper overlap JPQL avoids the magic constant entirely:

```java
@Query("""
    SELECT s FROM Shift s
    WHERE s.caregiverId = :caregiverId
      AND s.scheduledStart < :end
      AND s.scheduledEnd > :start
    """)
List<Shift> findOverlapping(@Param("caregiverId") UUID caregiverId,
                             @Param("start") LocalDateTime start,
                             @Param("end") LocalDateTime end);
```

This makes `hasConflictingShift` trivially simple and eliminates the 24h comment explaining the workaround.

### MI-2 — `ShiftCancelledEvent` has no publisher in this plan; `cancelRateLast90Days` will be 0 for every caregiver until Plan 6

The architecture note acknowledges this but it deserves a TODO comment in `LocalScoringService.onShiftCancelled` and in the cancellation counter tests, so reviewers know the counter is inert in production until Plan 6.

### MI-3 — `resetAllWeeklyHours` JPQL uses integer literal `0` for a `BigDecimal` field

```java
@Query("UPDATE CaregiverScoringProfile p SET p.currentWeekHours = 0")
```

This is technically valid JPQL but Hibernate 6 can warn on numeric type coercion. Use `0.00` or a named parameter (`SET p.currentWeekHours = :zero` with `@Param("zero") BigDecimal zero`) for explicitness and to silence potential Hibernate warnings.

### MI-4 — No test for `resetWeeklyHours()` scheduled method

The unit and integration tests cover `CaregiverScoringProfile.resetWeeklyHours()` and `resetAllWeeklyHours()` on the repository, but neither test suite calls `LocalScoringService.resetWeeklyHours()` directly. A single integration test calling the scheduler method and asserting all profiles' `currentWeekHours` are zero would close this gap.

### MI-5 — `@EnableScheduling` dependency is implicit

The `@Scheduled` annotation on `resetWeeklyHours()` requires `@EnableScheduling` on an application config class. CLAUDE.md mentions an existing nightly scheduler, so this is likely already configured. The plan should add a one-line verification step ("confirm `@EnableScheduling` is already present in the application config") to avoid a silent no-op if the scheduling configuration is in a feature branch.

### MI-6 — `buildExplanation` does not mention reliability or language/pet preferences

The scoring function uses five components but `buildExplanation` only describes distance, continuity, and overtime risk. Omitting the other two makes the explanation misleading for users who were downranked due to language mismatch or pet ownership. Consider at least appending a note for non-perfect preference or reliability scores.

---

## 5. Questions for Clarification

**Q1:** Does `CaregiverClientAffinity` already have a `@Version` field and a unique constraint on `(scoring_profile_id, client_id)`? If yes, CI-3 is partially resolved; if no, a migration is required.

**Q2:** The `LocalScoringServiceIT` calls `scoringService.onShiftCompleted(...)` directly (bypassing the event bus). This works, but it means the test does not exercise the `@TransactionalEventListener` + `REQUIRES_NEW` combination end-to-end. Is there an existing integration test pattern in this repo for verifying transactional event listeners, or is direct invocation the accepted approach?

**Q3:** `CaregiverAvailability(UUID, UUID, DayOfWeek, LocalTime, LocalTime)` is used extensively in tests but not shown in the plan. Does this constructor exist today, or is it being added as part of this plan (and simply omitted from the file structure)?

---

## 6. Final Recommendation

**Approve with changes.**

CI-1 (Java version typo) and CI-4 (interface design) are quick to fix. CI-2 (auth not found) and CI-3 (missing `@Version`) are correctness bugs that must be addressed before implementation. None of these require plan restructuring — they are targeted amendments to the code blocks already written. Address the four critical issues, verify the answer to Q1 (and add the migration if needed), then this plan is ready for execution.
