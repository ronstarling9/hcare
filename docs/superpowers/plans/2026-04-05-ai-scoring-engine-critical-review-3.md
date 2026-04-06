# Critical Implementation Review — AI Scoring Engine
**Plan:** `2026-04-05-ai-scoring-engine.md`
**Review:** 3 of N (third pass — builds on reviews 1 and 2)
**Reviewer stance:** Production-grade, iterative review. Only raises issues not present in reviews 1 or 2.

---

## 1. Overall Assessment

The plan has converged well across three passes. All six critical issues and all nine minor issues from reviews 1 and 2 have been addressed: the `ScoringService` interface is clean, the IT test now autowires `LocalScoringService` directly (fixing CI-5), the file structure includes `ShiftRepository`, the commit command stages it, and `buildExplanation` uses the `PreferenceResult` value holder. One new compilation-blocking issue remains: the integration test calls a repository method (`findByScoringProfileId`) that the plan never declares, modifies, or verifies. There is also one minor correctness gap in the affinity update retry loop.

---

## 2. Critical Issues

### CI-6 — `affinityRepository.findByScoringProfileId()` is used in the IT test but never declared in the plan

**Description:** `LocalScoringServiceIT` calls `affinityRepository.findByScoringProfileId(profile.getId())` at two points (inside `onShiftCompleted_creates_profile_and_affinity` and `repeated_onShiftCompleted_accumulates_continuity_and_hours`):

```java
List<CaregiverClientAffinity> affinities = transactionTemplate.execute(status ->
    affinityRepository.findByScoringProfileId(profile.getId()));
```

The plan's file structure lists no modifications to `CaregiverClientAffinityRepository`. The only affinity repository method mentioned in any plan code is `findByScoringProfileIdAndClientId`, used inside `LocalScoringService`. If `findByScoringProfileId(UUID)` does not already exist in the codebase, the IT test will fail to compile — breaking Task 6, Step 2 and the full suite run in Step 3.

**Why it matters:** Six of the IT test's assertions depend on the affinity list returned by this call. A missing method is a silent compile failure that blocks the entire integration test step.

**Fix:** Add `CaregiverClientAffinityRepository` to the plan's file structure with the annotation `modify: add findByScoringProfileId query`, and include the method declaration in the Task 6 step or in a companion modification step before Task 6:

```java
// In CaregiverClientAffinityRepository
List<CaregiverClientAffinity> findByScoringProfileId(UUID scoringProfileId);
```

Alternatively, if this method already exists in the codebase, add a verification step (e.g., a `grep` for `findByScoringProfileId` in `CaregiverClientAffinityRepository.java`) as Step 0 of Task 6, so an implementer knows they can rely on it without adding it.

---

## 3. Previously Addressed Items (from reviews 1 and 2)

- **CI-1** — Java version intentionally set to 25; CLAUDE.md and pom.xml files updated.
- **CI-2** — `authorizationRepository.findById(...).orElse(null)` replaced with `orElseThrow`; matching unit test added.
- **CI-3** — `CaregiverClientAffinity` confirmed to have `@Version` (V4 migration) and unique constraint (V3 migration); V8 migration scope correct.
- **CI-4** — Event listener methods removed from `ScoringService` interface; remain on `LocalScoringService` directly.
- **CI-5** — `LocalScoringServiceIT` field changed from `ScoringService` to `LocalScoringService`; all `onShiftCompleted` call sites and the `resetWeeklyHours` cast now compile correctly.
- **MI-1** — 24-hour heuristic replaced with `findOverlapping` proper JPQL interval overlap.
- **MI-2** — `TODO(Plan 6)` comment added to `onShiftCancelled`.
- **MI-3** — `SET p.currentWeekHours = 0.00` literal.
- **MI-4** — `resetWeeklyHours_zeroes_current_week_hours_for_all_profiles` IT test added.
- **MI-5** — `Step 0` added to Task 5 to verify `@EnableScheduling`.
- **MI-6** — `buildExplanation` surfaces cancel rate, language mismatch, and pet conflict.
- **MI-7** — `ShiftRepository.java` added to the top-level file structure diagram.
- **MI-8** — Task 3 commit command now stages `ShiftRepository.java`.
- **MI-9** — `computePreferencesScore` returns `PreferenceResult`; `buildExplanation` accepts it — duplicate parsing eliminated.

---

## 4. Minor Issues & Improvements

### MI-10 — `updateAffinity` insert race throws `DataIntegrityViolationException`, which the retry loop does not catch

The retry loop in `updateAffinity` guards the `incrementVisitCount + save` path with `catch (OptimisticLockingFailureException e)`. However, the `orElseGet(() -> affinityRepository.save(...))` new-record insert can itself throw a `DataIntegrityViolationException` (constraint violation) if two concurrent `onShiftCompleted` listeners race to insert the same `(scoring_profile_id, client_id)` pair. This exception is not `OptimisticLockingFailureException` and propagates uncaught out of `updateAffinity` and through `onShiftCompleted`, causing the event listener to fail for one of the two callers. The profile hours update (already saved above) would commit, but the affinity visit count would not be incremented for the losing thread — a silent data inconsistency.

CI-3 (review 1) confirmed the unique constraint exists, which prevents silent duplicates, but the exception escaping the catch block was not explicitly addressed.

**Fix (minimal):** Catch `DataIntegrityViolationException` in addition to `OptimisticLockingFailureException` and re-query the affinity before retrying the increment:

```java
} catch (OptimisticLockingFailureException | DataIntegrityViolationException e) {
    // Re-query on next attempt — handles both update race and insert race
    if (attempt == 2) {
        log.error("Exhausted retries updating CaregiverClientAffinity ...", e);
    }
}
```

Since `DataIntegrityViolationException` is only possible on the insert path, the `orElseGet` on retry will find the now-existing row and proceed to increment — no other change required.

---

### MI-11 — Task 3 is named "hard filters + skeleton" but creates the complete `LocalScoringService`

Task 3's heading reads: *"LocalScoringService — hard filters + skeleton"*, which implies the scoring and event-listener logic are stubs to be filled in later. In practice, Step 3 creates the fully implemented `LocalScoringService` including `computeDistanceScore`, `computePreferencesScore`, `buildExplanation`, `onShiftCompleted`, `onShiftCancelled`, `updateAffinity`, and `resetWeeklyHours`. Task 4 only appends tests. The "skeleton" label creates false expectations for an implementer who might expect to find TODOs to fill in.

**Fix:** Rename Task 3 to "LocalScoringService — hard filters + scoring + event listeners" (or simply "LocalScoringService — full implementation"). No code changes required.

---

## 5. Questions for Clarification

**Q2 (carried from reviews 1 and 2, still open):** The IT test calls `scoringService.onShiftCompleted(...)` directly, bypassing the Spring event bus and the `@TransactionalEventListener(phase = AFTER_COMMIT)` binding. This means no test in this plan exercises the full event dispatch path (publish → commit → listener fires in new transaction). Is direct-invocation the accepted pattern in this repo, or should at least one test use `ApplicationEventPublisher` + `TransactionTemplate` to verify the listener is correctly wired?

**Q3 (carried from reviews 1 and 2, still open):** `CaregiverAvailability(UUID, UUID, DayOfWeek, LocalTime, LocalTime)` is used in both unit tests and the IT. The plan does not create or modify this constructor. If it doesn't exist in the current entity, every test that calls it will fail to compile.

**Q4 (new):** Does `CaregiverClientAffinityRepository.findByScoringProfileId(UUID)` already exist in the codebase? This is the crux of CI-6 above — if it exists, add a verification step and the issue is minor; if it doesn't, a plan amendment is required.

---

## 6. Final Recommendation

**Approve with changes.**

CI-6 is a single-line fix: either declare `findByScoringProfileId` in the plan (if it doesn't exist) or add a verification grep step (if it does). Answer Q4 first to determine which applies. MI-10 is a two-line catch-block addition. MI-11 is a label rename with no code impact. Address CI-6 before execution; the rest can be done alongside.
