# Code Review: Broadcast Open Modal (Opus Review)

**Commits:** f89148b, 98a415b
**Date:** 2026-04-07
**Reviewer:** Claude Opus 4.6

---

## SUMMARY
**Approve with minor suggestions.** The implementation is clean, well-structured, and closely follows the design document. Backend changes are minimal and correct, frontend modal has a clear state machine, tests cover the important paths. A few issues worth addressing but nothing blocking.

---

## HIGH PRIORITY ISSUES

### 1. Controller removed `end > start` validation without replacement guard
**`ShiftSchedulingController.java` lines 43-45 (diff lines 27-31)**

The controller's `end.isAfter(start)` check was removed. While the service still validates this, the service now throws `ResponseStatusException` — which is a Spring Web exception being thrown from a `@Service` class. This works but violates the project's convention:

> *"Exceptions bubble up to a global `@ControllerAdvice` handler — don't catch-and-swallow."*

`ResponseStatusException` bypasses `@ControllerAdvice` (Spring handles it directly). This was pre-existing, but the PR makes it the *only* line of defense now. Consider either:
- Moving to a custom exception that the `@ControllerAdvice` maps to 400, or
- Keeping basic validation in the controller (preferred — it was there before)

**Severity:** Medium. The behavior is correct, but the pattern is a latent issue.

### 2. `listOpenShifts` hardcodes `size: 200` — silent data loss for agencies with >200 open shifts
**`frontend/src/api/shifts.ts` line ~270**

```typescript
params: { start, end, status: 'OPEN', size: 200, sort: 'scheduledStart' },
```

The function returns `response.data.content` and discards pagination metadata. If an agency has >200 open shifts in a week, the remainder will be silently dropped. The modal will broadcast only the first 200 with no indication to the user that shifts were omitted.

Given the target is small agencies (1-25 caregivers), 200 is likely safe in practice, but a defensive check (e.g., warn if `totalElements > content.length`) or an unpaged fetch would be more robust.

### 3. No `AbortController` / cleanup in the `useEffect` fetch
**`BroadcastOpenModal.tsx` lines 502-512**

If the modal is closed while `listOpenShifts` is in-flight, the `.then()` still runs and calls `setOpenShifts` / `setPhase` on an unmounted (or re-opened with different props) component. This can cause stale state if the modal is rapidly closed and reopened with different week dates.

```typescript
useEffect(() => {
  if (!open) return
  // No cleanup / abort
  listOpenShifts(weekStart, weekEnd)
    .then(...)
    .catch(...)
}, [open, weekStart, weekEnd])
```

Should return a cleanup function that sets a cancelled flag or uses `AbortController`.

---

## LOW PRIORITY SUGGESTIONS

### 1. Duplicated shift row rendering
**`BroadcastOpenModal.tsx` — confirm phase (lines 582-599) vs broadcasting/done phase (lines 604-628)**

The shift list is rendered twice with slightly different layouts. Extract a `ShiftRow` component to reduce duplication and keep the two views consistent.

### 2. Duplicated "Close" button markup
**`BroadcastOpenModal.tsx` — footer section**

The same close button is rendered in 3 places (error, empty-confirm, done phases) with slightly different styling. A shared button or at least a helper would reduce this.

### 3. `handleClose` is a trivial wrapper
**`BroadcastOpenModal.tsx` line 534**

```typescript
function handleClose() { onClose() }
```

This adds indirection for no benefit. Pass `onClose` directly to the `onClick` handlers, or keep the wrapper if you plan to add cleanup logic later (but YAGNI for now).

### 4. Integration test uses `restTemplate` instead of `mockMvc`
**`ShiftSchedulingControllerIT.java`**

The new IT tests use `restTemplate.exchange()` while the plan's spec showed `mockMvc`-style tests. This is fine if the existing file consistently uses `restTemplate`, but worth noting for consistency.

### 5. Unicode escapes in `schedule.json`
**`schedule.json` lines 192-209**

The diff converts readable characters (`←`, `→`, `—`, `…`) to Unicode escapes (`\u2190`, `\u2192`, `\u2014`, `\u2026`). This is functionally identical but reduces readability of the i18n file. Likely a tooling artifact — not a bug, but worth being aware of.

### 6. Modal doesn't handle ESC key or backdrop click to close
Not in the plan, but standard modal UX. Could be a follow-up.

---

## WHAT'S GOOD

- **Clean state machine design.** The 5-phase model (`loading → confirm → broadcasting → done | error`) is easy to reason about and maps directly to the UI states. Each phase has clear entry/exit conditions.

- **Sequential broadcasting with per-row feedback** is the right UX choice — it gives users visibility into progress and naturally handles partial failures without complex retry logic.

- **Backend changes are minimal and backwards-compatible.** The optional `status` param preserves all existing API consumers. The Spring Data derived query avoids manual SQL.

- **Good test coverage.** 9 frontend tests cover all phases including the mixed success/failure edge case. Backend has both unit and integration coverage for the new parameter. The "never-resolving promise" pattern for testing loading state is clean.

- **i18n done correctly.** Pluralization with `_one`/`_other` suffixes, all user-facing strings externalized, interpolation used for dynamic values.

- **Follows project conventions.** Named exports, React Query cache invalidation after mutations, Tailwind design tokens, proper TypeScript typing.
