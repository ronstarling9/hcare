# Critical Design Review #3
## Add Client Panel — Design Spec
**Reviewed:** `2026-04-08-add-client-panel-design.md`
**Reviewer pass:** 3 (previous: review-1, review-2)

---

## 1. Overall Assessment

Across three review passes the spec has improved substantially: the original four critical issues (C1–C4 in review-1), and the three critical issues from review-2 (openPanel type gap, Toast coupling, spurious case normalization), have all been resolved. The document is near implementation-ready. This pass surfaces one new correctness bug in the toast timer lifecycle — a narrow but real edge case the current spec creates — and two lower-priority items. No structural redesign is required.

---

## 2. Critical Issues

### C1 — Toast timer never re-arms when `show()` is called while already visible

**Description:** The spec correctly places the `setTimeout` inside a `useEffect` with `visible` as its dependency. This works correctly for the primary flow (toast hidden → `show()` → visible becomes `true` → effect runs → timer arms). However, if `show()` is called while `visible` is already `true` — for example, the user saves, sees the toast, then immediately saves a second time before the first toast auto-dismisses — `visible` does not change value (`true → true`). React does not re-run the `useEffect`, so no new timer is armed for the second toast.

Two bad outcomes follow:
- If the first timer is still running, it fires against the second toast, dismissing it after the *remaining* time from the first show, not a full 6 seconds.
- If the first timer already fired and cleared (user was slow), the second toast never auto-dismisses at all.

The `clearTimeout` cleanup in the effect doesn't help here: cleanup only runs when `visible` changes or the component unmounts, neither of which happens in this scenario.

**Why it matters:** The spec explicitly supports the Save & Close path, which a fast user can trigger multiple times in a session. The bug is silent — it produces inconsistent auto-dismiss timing with no error logged, difficult to reproduce in unit tests, and likely to survive code review.

**Actionable fix:** Add a monotonically-increasing `showCount: number` (initial value `0`) to `toastStore`. `show()` increments it. Change the `useEffect` dependency array to `[visible, showCount]`. When `show()` is called while visible, `showCount` changes, the effect re-runs, the old timer is cleared by the cleanup, and a fresh 6-second timer starts. This requires one new field in the store and a one-line dependency change in `Toast.tsx`. Add a test case: "calling `show()` while already visible resets the 6-second timer."

---

## 3. Previously Addressed Items (from Reviews #1 and #2)

- **R1-C1** ✅ — `initialTab` is a top-level `PanelState` field and a direct prop on `ClientDetailPanel`.
- **R1-C2** ✅ — Toast timer lifecycle fully specified: `setTimeout` in `useEffect`, `clearTimeout` in cleanup.
- **R1-C3** ✅ — `preferredCaregiverGender` "no preference" sends `undefined`; canonical values documented.
- **R1-C4** ✅ — Duplicate `"fieldPhone"` i18n key removed.
- **R1 minor: React 18 batching** ✅ — Documented and marked intentional with opt-out path noted.
- **R1 minor: Toast tests** ✅ — `toastStore.test.ts` and `Toast.test.tsx` test plans added.
- **R1 minor: `preferredLanguages` case** ✅ — Normalization correctly removed; `LocalScoringService` lowercasing documented.
- **R1 minor: `dateOfBirth` serialization** ✅ — `YYYY-MM-DD` → `LocalDate` note added to field table.
- **R2-C1** ✅ — `openPanel` options type updated to include `initialTab?: string`; `closePanel` reset documented.
- **R2-C2** ✅ — `toastStore` now carries `panelType`, `panelTab`, `backLabel`; `Toast.tsx` reads all navigation params from store with no i18n import.
- **R2-C3** ✅ — Title-case normalization removed; replaced with accurate note about `parseLanguageList` lowercasing.
- **R2 minor: `initialTab` cast** ✅ — `(initialTab as Tab | undefined) ?? 'overview'` documented.
- **R2 minor: `toastStore` initial state** ✅ — Full initial values documented.

---

## 4. Alternative Architectural Challenge

**Alternative: Derive toast auto-dismiss from a timestamp rather than a timer**

Instead of managing a `setTimeout` in `Toast.tsx`, store a `shownAt: number` (epoch ms from `Date.now()`) in `toastStore` whenever `show()` is called. `Toast.tsx` uses a single `requestAnimationFrame` loop (or `setInterval(100ms)`) to compare `Date.now() - shownAt >= 6000` and calls `dismiss()` when true.

**Pros:**
- Re-calling `show()` always updates `shownAt` to now, so the 6-second window automatically resets regardless of whether `visible` changed — the C1 bug above simply does not exist.
- No `useEffect` dependency array to reason about; no `clearTimeout` to wire up.
- `shownAt: 0` as initial state is sufficient; no `showCount` counter needed.

**Cons:**
- `requestAnimationFrame` / `setInterval` polling feels heavy for a simple timer — `setTimeout` in a `useEffect` is idiomatic React and universally understood.
- The rAF approach is marginally harder to unit-test (requires mocking `Date.now()` instead of `jest.useFakeTimers()`).
- The simpler fix (adding `showCount`) preserves the existing architecture with minimal change.

**Verdict:** The `showCount` patch (described in C1) is the right call. The timestamp approach is a legitimate alternative worth knowing if the toast infrastructure grows to support a queue with individual timers per message.

---

## 5. Minor Issues & Improvements

- **`backLabel` key not listed in i18n additions:** The spec calls `t('backLabel')` in `NewClientPanel` (both in the initial `openPanel` call from `ClientsPage` and in the post-save `openPanel` call). The i18n additions section does not include a `"backLabel"` key. If this key already exists in `clients.json` (used by other panels), this is correct — but the spec should add a note confirming the key is pre-existing and reused, parallel to the existing note for `"fieldPhone"`. If it doesn't exist, this is a missing key.

- **Two questions from Review #2 remain unanswered in the spec:**
  1. *Toast placement:* Top-of-content-area, bottom-right, or bottom-center? This is the first toast in the app and sets a visual precedent for the whole system.
  2. *"Save & Add Authorization" landing state:* Does the Authorizations tab show a standard empty state with a CTA, or should an "Add Authorization" inline form be pre-opened on arrival? The spec currently says only "opens the client detail panel directly on the Authorizations tab" — whether that implies any additional affordance is unspecified.

---

## 6. Questions for Clarification

1. **Is `backLabel` a pre-existing key in `clients.json`?** If yes, add a reuse note alongside the `"fieldPhone"` note. If no, add `"backLabel": "← Clients"` (or equivalent) to the i18n additions.

2. **What is the expected `show()` behavior when the toast is already visible?** The spec describes rapid save sequences as a concern (timer lifecycle), but doesn't state the intended UX: should the 6-second window reset from the new show, or should the existing toast remain until its original timer expires and then be replaced? The C1 fix assumes "reset the timer" — confirm this is the intended behavior before implementing.

---

## 7. Final Recommendation

**Approve with changes.**

One correctness bug (C1) must be fixed before implementation:

1. **C1** — Add `showCount: number` to `toastStore`; increment in `show()`; add it to `useEffect`'s dependency array in `Toast.tsx`; add a test case for rapid successive `show()` calls.

Two minor items should be addressed in the spec text (not blockers, but will cause implementer uncertainty):

- Confirm or add the `backLabel` i18n key.
- Answer the two open questions on toast placement and authorization tab landing state.

After C1 is addressed, the spec is implementation-ready.
