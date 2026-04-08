# Code Review: Broadcast Open Modal Feature
**Commits:** f89148b, 98a415b  
**Reviewer:** Claude Haiku 4.5  
**Date:** 2026-04-07

---

## SUMMARY

**APPROVE WITH MINOR CORRECTIONS**

The implementation is fundamentally sound and achieves its stated goals. The feature provides a complete end-to-end flow for broadcasting open shifts with real-time per-row progress feedback, proper error handling, and comprehensive test coverage (9 tests, including the N1 mixed success/failure case). The backend API changes are minimal and well-structured. However, there are three issues that should be addressed before merging: (1) a potential state consistency bug in the modal's useEffect, (2) incomplete error context, and (3) overly broad cache invalidation.

---

## HIGH PRIORITY ISSUES

### H1 — useEffect missing cleanup during unmount could leave stale state

**Location:** `BroadcastOpenModal.tsx`, lines 38-47

**Description:** The `useEffect` that handles the initial fetch has no cleanup function. If the component unmounts while `listOpenShifts()` is in flight (e.g., user closes the modal before the fetch completes), the subsequent `.then()` will attempt to call `setState` on an unmounted component, triggering a React warning:

```
Warning: Can't perform a React state update on an unmounted component.
```

While React won't crash, this is a code smell that suggests incomplete thinking about the component lifecycle.

**Example scenario:**
1. Modal opens, fetch begins
2. User closes modal (component unmounts)
3. Fetch completes 2 seconds later
4. `.then()` tries to call `setOpenShifts()` on unmounted component

**Fix:** Add a cleanup function that aborts the fetch or uses a mounted flag:

```typescript
useEffect(() => {
  if (!open) return
  
  let mounted = true
  setPhase('loading')
  setResults({})
  
  listOpenShifts(weekStart, weekEnd)
    .then((shifts) => {
      if (mounted) {
        setOpenShifts(shifts)
        setPhase('confirm')
      }
    })
    .catch(() => {
      if (mounted) {
        setPhase('error')
      }
    })
  
  return () => {
    mounted = false
  }
}, [open, weekStart, weekEnd])
```

**Impact:** High — prevents React warnings and ensures clean state transitions.

---

### H2 — Query cache invalidation is overly broad; could cause unnecessary refetches

**Location:** `BroadcastOpenModal.tsx`, line 62

**Description:** The code invalidates the entire `['shifts']` cache family after broadcasting:

```typescript
queryClient.invalidateQueries({ queryKey: ['shifts'] })
```

This pattern exists elsewhere in the codebase (e.g., `useShifts.ts`), which provides some consistency. However, since the modal knows exactly which shifts were broadcast and the time window (`weekStart`, `weekEnd`), it would be more efficient to invalidate only the specific window:

```typescript
queryClient.invalidateQueries({ queryKey: ['shifts', weekStartStr, weekEndStr] })
```

This avoids invalidating shifts queries for other weeks/panels, which could trigger unnecessary network requests.

**Note:** This is *not* a correctness issue — the broader invalidation will work fine. It's a performance optimization that becomes more important at scale.

**Impact:** Medium — causes unnecessary cache invalidation, worse with many shift queries in flight.

---

### H3 — Generic error message masks underlying failure reason

**Location:** `BroadcastOpenModal.tsx`, line 45 + i18n key

**Description:** When the initial fetch fails, the modal shows a generic error:

```typescript
.catch(() => setPhase('error'))
```

With i18n message: `"Failed to load open shifts. Please try again."`

This hides the actual error (network timeout, 401 auth, 500 server error, etc.). Users and support staff cannot diagnose the problem.

**Fix:** Capture and log the error reason:

```typescript
.catch((error) => {
  console.error('listOpenShifts failed:', error)
  setPhase('error')
})
```

The UI message can remain generic for UX reasons, but the error should be logged for debugging.

**Impact:** Medium — hampers debugging and support investigations.

---

## LOW PRIORITY SUGGESTIONS

### L1 — Modal state not reset when component remounts

If the user opens the modal, encounters an error, closes it, and immediately reopens it, the `phase` will still be `'error'` momentarily before the new fetch completes. This is a minor UX glitch because the `useEffect` dependency on `open` will trigger a fresh fetch, but the state should be reset eagerly:

```typescript
useEffect(() => {
  if (!open) {
    setPhase('loading')  // Reset to clean state
    setOpenShifts([])
    setResults({})
    return
  }
  // ... existing fetch logic
}, [open, weekStart, weekEnd])
```

---

### L2 — Missing loading state during broadcasts

During the `broadcasting` phase, there's no visual indication of how many shifts have been processed vs. remaining. For agencies with 10+ open shifts, users have no sense of progress. Consider adding a progress indicator:

```typescript
{phase === 'broadcasting' && (
  <p className="text-[12px] text-text-secondary mb-3">
    {successCount + failCount} of {openShifts.length} broadcasts complete
  </p>
)}
```

---

### L3 — Hardcoded `size: 200` lacks justification

In `listOpenShifts()`, the API call uses `size: 200`, which is fine for small agencies (1–25 caregivers) but is undocumented. Consider adding a comment or moving to a constant:

```typescript
const OPEN_SHIFTS_BATCH_SIZE = 200 // Suitable for small agencies (≤25 caregivers)
export async function listOpenShifts(start: string, end: string): Promise<ShiftSummaryResponse[]> {
  const response = await apiClient.get<PageResponse<ShiftSummaryResponse>>('/shifts', {
    params: { start, end, status: 'OPEN', size: OPEN_SHIFTS_BATCH_SIZE, sort: 'scheduledStart' },
  })
  return response.data.content
}
```

---

### L4 — Test mock for `broadcastShift` doesn't verify return value is discarded

The test mocks `broadcastShift` as `mockResolvedValue(undefined)`, but the actual API returns `List<ShiftOfferSummary>`. While the plan explicitly documents this discard as intentional (M4 from critical-review-1), the test should reflect this more clearly:

```typescript
vi.mocked(shiftsApi.broadcastShift).mockResolvedValue(undefined)
// Consider adding a comment or verifying with the actual response structure
```

---

## WHAT'S GOOD

✅ **Phase state machine is clean and well-designed** — The 5-phase flow (loading/confirm/broadcasting/done/error) is clear and handles edge cases (empty shifts, errors, mixed success/failure).

✅ **Excellent test coverage** — 9 tests cover all phases including the critical N1 case (mixed success/failure). The never-resolving promise for the loading test is a clever way to avoid flakiness.

✅ **Proper accessibility** — `aria-modal="true"`, `aria-labelledby`, and `id` attributes are correct. Dialog role is appropriate.

✅ **Error recovery is robust** — The `error` phase provides a close button, preventing users from getting stuck in a broken modal.

✅ **Per-row progress is user-friendly** — Showing pending/success/error status for each shift individually (rather than a single progress bar) gives users visibility into what succeeded and what failed.

✅ **Backend changes are minimal and correct** — The derived query, optional status param, and removal of redundant validation are exactly right. The two new IT tests validate the integration.

✅ **i18n is complete** — All strings are externalized with proper pluralization handling (`_one`, `_other`). The `loadError` key addresses the critical review finding.

✅ **Sequential broadcast loop is correct** — Using `await` inside a `for` loop is the right pattern here (not over-engineering with React Query mutations). Each broadcast waits for the previous one, preventing race conditions.

✅ **Integration with calendar is seamless** — Modal closes cleanly, cache invalidation triggers calendar refresh, and the button wiring in `SchedulePage` is straightforward.

---

## FINAL VERDICT

**Approve with fixes for H1 and H2.** The code is production-ready after addressing the useEffect cleanup and implementing the suggested cache invalidation. H3 (error logging) is a softer recommendation but should be done before beta release. All low-priority suggestions can be addressed in a follow-up sprint.
