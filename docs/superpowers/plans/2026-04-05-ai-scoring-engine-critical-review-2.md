# Critical Implementation Review — AI Scoring Engine
**Plan:** `2026-04-05-ai-scoring-engine.md`
**Review:** 2 of N (second pass — builds on review-1)
**Reviewer stance:** Production-grade, iterative review. Only raises issues not present in review-1.

---

## 1. Overall Assessment

The plan has improved substantially since review-1: all six critical and minor issues from that pass were addressed. The authorization check is now correct, the interface is clean, the overlap query is proper JPQL, and the explanation is more informative. However, the CI-4 fix (removing `onShiftCompleted`/`onShiftCancelled` from `ScoringService`) introduced a new **compilation-breaking regression** in `LocalScoringServiceIT`: the IT test still calls `scoringService.onShiftCompleted(...)` on a `ScoringService`-typed variable, which no longer has that method. One line needs to change to fix it. There are also two minor housekeeping gaps and one DRY concern in `buildExplanation`.

---

## 2. Critical Issues

### CI-5 — `LocalScoringServiceIT` will not compile: calls `onShiftCompleted` on a `ScoringService`-typed reference

**Description:** The IT test declares its injection as `@Autowired ScoringService scoringService;`. After the CI-4 fix, `ScoringService` only declares `rankCandidates`. The test then calls:

```java
scoringService.onShiftCompleted(new ShiftCompletedEvent(...));  // ← compile error
```

This appears in `onShiftCompleted_creates_profile_and_affinity`, `repeated_onShiftCompleted_accumulates_continuity_and_hours`, `continuity_improves_ranking_after_visits_recorded`, and `resetWeeklyHours_zeroes_current_week_hours_for_all_profiles` — every test that exercises the event listener path. The plan already casts for `resetWeeklyHours`:

```java
((LocalScoringService) scoringService).resetWeeklyHours();  // already cast
```

but omits the equivalent cast for `onShiftCompleted`.

**Why it matters:** The entire integration test file fails to compile. Six of six tests are broken.

**Fix:** Change the field declaration in `LocalScoringServiceIT` from:

```java
@Autowired ScoringService scoringService;
```

to:

```java
@Autowired LocalScoringService scoringService;
```

This resolves all `onShiftCompleted` call sites at once and also eliminates the now-unnecessary cast on `resetWeeklyHours`. No test logic changes are needed.

---

## 3. Previously Addressed Items (from review-1)

- **CI-1** — Java version intentionally set to 25 (user decision); CLAUDE.md and both pom.xml files updated.
- **CI-2** — `authorizationRepository.findById(...).orElse(null)` replaced with `orElseThrow`; matching unit test `unknown_authorization_id_throws_illegal_argument` added.
- **CI-3** — Confirmed `CaregiverClientAffinity` already has `@Version` (V4 migration), unique constraint already exists (V3 migration); V8 migration scope correctly limited to `caregiver_scoring_profiles`.
- **CI-4** — `onShiftCompleted`/`onShiftCancelled` removed from `ScoringService` interface; `@Override` annotations removed from `LocalScoringService` implementations. *(Note: this fix introduced CI-5 above.)*
- **MI-1** — 24-hour heuristic replaced with `ShiftRepository.findOverlapping` using proper JPQL interval overlap; all 14 test mocks updated.
- **MI-2** — `TODO(Plan 6)` comment added to `onShiftCancelled`.
- **MI-3** — `SET p.currentWeekHours = 0.00` literal.
- **MI-4** — `resetWeeklyHours_zeroes_current_week_hours_for_all_profiles` IT test added; expected count 5 → 6.
- **MI-5** — `Step 0` added to Task 5 to verify `@EnableScheduling`.
- **MI-6** — `buildExplanation` now surfaces cancel rate, language mismatch, and pet conflict.

---

## 4. Minor Issues & Improvements

### MI-7 — Top-level file structure diagram omits `ShiftRepository.java`

`ShiftRepository.java` was added to the Task 3 file list (correctly) but is absent from the `## File Structure` overview at the top of the plan. Implementers scanning that diagram to understand the change footprint will miss it.

**Fix:** Add `ShiftRepository.java — modify: add findOverlapping query` under the `domain/` section of the file structure diagram.

---

### MI-8 — Task 3 commit command omits `ShiftRepository.java`

Task 3, Step 5 commit:
```bash
git add backend/src/main/java/com/hcare/scoring/LocalScoringService.java \
        backend/src/test/java/com/hcare/scoring/LocalScoringServiceTest.java
```

Step 1b modifies `ShiftRepository.java` but it is not staged here. The file would be left unstaged, breaking the task's commit boundary.

**Fix:** Add `backend/src/main/java/com/hcare/domain/ShiftRepository.java \` to the `git add` command.

---

### MI-9 — `buildExplanation` re-parses language and pet data already parsed in `computePreferencesScore`

`rankCandidates` calls `computePreferencesScore(caregiver, client)` and then immediately `buildExplanation(...)`. Both methods independently call `parseLanguageList(client.getPreferredLanguages())`, `parseLanguageList(caregiver.getLanguages())`, and check `client.isNoPetCaregiver() && caregiver.hasPet()`. The preference logic is evaluated twice per caregiver per scoring run — a DRY violation and minor correctness risk (if the two copies ever diverge, a caregiver could be scored down for language mismatch but not have it shown in the explanation, or vice versa).

**Fix:** Extract a `PreferenceResult` value holder (or inline record) returned by `computePreferencesScore`, then pass it into `buildExplanation`:

```java
record PreferenceResult(double score, boolean languageMismatch, boolean petConflict) {}

private PreferenceResult computePreferencesScore(Caregiver caregiver, Client client) {
    ...
    return new PreferenceResult(score, languageMismatch, petConflict);
}
```

This is a minor refactor but eliminates the duplicate parsing and makes it impossible for the score and explanation to disagree.

---

## 5. Questions for Clarification

**Q2 (carried from review-1, still open):** `LocalScoringServiceIT` calls `onShiftCompleted` directly rather than via the Spring event bus. This bypasses the `@TransactionalEventListener(phase = AFTER_COMMIT)` binding. Is direct invocation the accepted pattern in this repo for event listener integration tests, or should there be at least one test that publishes via `ApplicationEventPublisher` within a committed transaction to verify the full event dispatch path?

**Q3 (carried from review-1, still open):** `CaregiverAvailability(UUID, UUID, DayOfWeek, LocalTime, LocalTime)` is used in both unit and integration tests but neither created nor modified by this plan. Does this constructor exist in the current entity, or must the implementer add it before tests will compile?

---

## 6. Final Recommendation

**Approve with changes.**

CI-5 is a single-line fix (`ScoringService` → `LocalScoringService` in the IT test field declaration) that unblocks compilation of the entire integration test file. MI-7 and MI-8 are one-line housekeeping edits. Address those three before execution. MI-9 is a good refactor but not blocking.
