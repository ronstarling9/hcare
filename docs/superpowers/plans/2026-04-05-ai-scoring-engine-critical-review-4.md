# Critical Implementation Review — AI Scoring Engine
**Plan:** `2026-04-05-ai-scoring-engine.md`
**Review:** 4 of N (fourth pass — builds on reviews 1, 2, and 3)
**Reviewer stance:** Production-grade, iterative review. Only raises issues not present in reviews 1–3.

---

## 1. Overall Assessment

The plan has converged to a near-production-ready state across four passes. All nine critical issues and eleven minor issues from reviews 1–3 have been addressed: CI-6 (`findByScoringProfileId`) is now explicitly declared in Task 6 Step 0, MI-10 (`DataIntegrityViolationException` catch) is in the retry block, and MI-11 (task naming) is corrected. No new critical blockers remain. Three new minor issues are surfaced below: a missing pre-Task-3 verification step for `FeatureFlags` dependencies, a missing `clearAutomatically = true` on the `@Modifying` bulk-update query, and undocumented synchronous blocking behaviour of the `@TransactionalEventListener` on the clock-out path.

---

## 2. Critical Issues

None. All prior blocking issues are resolved.

---

## 3. Previously Addressed Items (from review 3)

- **CI-6** — `CaregiverClientAffinityRepository.findByScoringProfileId(UUID)` is now declared in Task 6 Step 0 with both the method body and compile verification. The file structure diagram lists `CaregiverClientAffinityRepository.java — modify: add findByScoringProfileId query`.
- **MI-10** — `updateAffinity` retry loop now catches `OptimisticLockingFailureException | DataIntegrityViolationException`; the comment explains the insert-race scenario.
- **MI-11** — Task 3 heading renamed to "LocalScoringService — full implementation"; the "skeleton" label is gone.

---

## 4. Minor Issues & Improvements

### MI-12 — No pre-Task-3 verification step for `FeatureFlags` / `FeatureFlagsRepository`

**Description:** `LocalScoringService` (created in Task 3) directly uses `FeatureFlagsRepository.findByAgencyId(UUID)`, `FeatureFlags.isAiSchedulingEnabled()`, and the `FeatureFlags(UUID agencyId)` constructor. The IT test also calls `new FeatureFlags(agency.getId())` and `flags.setAiSchedulingEnabled(true)`. None of these types, constructors, or methods are created or verified anywhere in the plan.

Compare to CI-6 (review 3): the plan appropriately added a Step 0 to Task 6 declaring `findByScoringProfileId` before the IT test used it. The same pattern is needed here: if `FeatureFlags` or its repository do not expose the expected API, Task 3 Step 3 (`mvn compile -q`) fails with a "cannot find symbol" error on the newly created `LocalScoringService.java`, which could mislead an implementer into thinking the issue is with their new code rather than a missing dependency.

**Fix:** Add a Step 0 to Task 3:

```bash
# Verify FeatureFlags API exists before creating LocalScoringService
cd backend && grep -r "findByAgencyId" src/main/java/com/hcare/domain/FeatureFlagsRepository.java
cd backend && grep -r "isAiSchedulingEnabled\|setAiSchedulingEnabled" src/main/java/com/hcare/domain/FeatureFlags.java
```

Expected: both return matches. If either is absent, add the missing method / constructor before Task 3 proceeds, identical in form to the Step 0 already used in Tasks 5 and 6.

---

### MI-13 — `resetAllWeeklyHours()` `@Modifying` query lacks `clearAutomatically = true`

**Description:** The repository method:

```java
@Modifying
@Query("UPDATE CaregiverScoringProfile p SET p.currentWeekHours = 0.00")
void resetAllWeeklyHours();
```

runs a JPQL bulk update that bypasses the JPA first-level cache. If this method is ever called within a transaction that subsequently queries any `CaregiverScoringProfile` (e.g., if `resetWeeklyHours()` in `LocalScoringService` is extended to log post-reset counts, or if a caller wraps the scheduled invocation in a larger transaction), the stale cached entities would be returned instead of the DB-updated values. Spring Data JPA requires `clearAutomatically = true` to flush and clear the persistence context automatically after a bulk update.

**Why it matters:** The current tests exercise `resetWeeklyHours()` via `TransactionTemplate` calls (each in its own transaction), so the bug is invisible in the test suite. In production, a future addition to the scheduler logic that reads after reset could silently return stale hours, making it appear the reset did not execute.

**Fix:**

```java
@Modifying(clearAutomatically = true)
@Query("UPDATE CaregiverScoringProfile p SET p.currentWeekHours = 0.00")
void resetAllWeeklyHours();
```

This is a one-token addition with no other code changes required.

---

### MI-14 — Undocumented synchronous execution of `@TransactionalEventListener` on the `clockOut` request path

**Description:** `@TransactionalEventListener(phase = AFTER_COMMIT)` fires synchronously in the committing thread by default — there is no `@Async` annotation on `onShiftCompleted`. This means `VisitService.clockOut` waits for `onShiftCompleted` to finish (profile upsert + affinity upsert + up to 3 retry iterations) before returning an HTTP response to the mobile caller. The plan has no comment acknowledging this latency trade-off.

**Why it matters:** For P1 (1–25 caregivers) the latency is likely acceptable (≈3–6 DB round-trips, probably 30–80 ms). But the *absence* of documentation means a future developer seeing the `@TransactionalEventListener` might add more work to the listener assuming it is async, or might attempt to add `@Async` without understanding the `REQUIRES_NEW` transaction interaction (async + `REQUIRES_NEW` needs an explicit `PlatformTransactionManager` reference).

**Fix (documentation only):** Add a comment on `onShiftCompleted` in `LocalScoringService`:

```java
// Fires synchronously (AFTER_COMMIT) in the VisitService.clockOut thread — acceptable
// latency for P1 (1–25 caregivers: ~3–6 DB calls). Add @Async at P2 if profiling shows
// this exceeds acceptable response budgets; note that @Async + REQUIRES_NEW requires an
// explicit PlatformTransactionManager binding.
@TransactionalEventListener
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void onShiftCompleted(ShiftCompletedEvent event) {
```

---

## 5. Questions for Clarification

**Q2 (carried from reviews 1–3, still open):** `LocalScoringServiceIT` calls `scoringService.onShiftCompleted(...)` directly. This does not exercise the `@TransactionalEventListener(phase = AFTER_COMMIT)` wiring — if the annotation were accidentally removed or mis-configured, the IT suite would still pass. Is direct invocation the accepted pattern in this repo, or should at least one test publish via `ApplicationEventPublisher` within a committed `TransactionTemplate` block to verify the full dispatch path?

**Q3 (carried from reviews 1–3, still open):** `CaregiverAvailability(UUID, UUID, DayOfWeek, LocalTime, LocalTime)` is used in both unit and IT tests but the plan neither creates nor verifies this constructor. If it does not exist in the current entity, every test using it fails to compile. This is the same class of issue as CI-6; it has been raised in all three prior reviews without resolution. A verification grep (similar to Task 3 Step 0 proposed in MI-12) or an explicit plan amendment is needed.

---

## 6. Final Recommendation

**Approve with minor changes.**

No blocking issues remain. MI-12 (add a FeatureFlags verification step), MI-13 (add `clearAutomatically = true`), and MI-14 (add a comment documenting synchronous execution) are all one- or two-line fixes with no logic impact. Answer Q3 to determine whether `CaregiverAvailability`'s constructor needs a verification step or an amendment — that question has now been raised in four consecutive reviews without a definitive response, and it remains the single ambiguity that could block a compile run before it is discovered.
