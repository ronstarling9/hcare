# Critical Implementation Review #2
**Plan:** `2026-04-07-broadcast-open-modal.md`
**Reviewer:** Senior Staff Engineer — second pass (Review #1 completed and all fixes applied)

---

## 1. Overall Assessment

All critical issues from Review #1 have been successfully addressed in this revision: error state handling is complete with `.catch()` and a dedicated error phase, the loading-state test uses a never-resolving promise for reliable observation, two controller IT tests have been added, and the controller date validation removal is now definitive with clear rationale. One new test coverage gap has emerged in this revised plan: the component's success/failure count logic for mixed broadcast outcomes is unexercised — there is a test for initial fetch failure but none for per-shift failures during the sequential broadcast loop itself.

---

## 2. Critical Issues

### N1 — No test for partial success/failure during broadcasting phase

**Description:** The `BroadcastOpenModal` component computes `successCount` and `failCount` from the `results` state (line 622) and uses these values to select between two done-phase messages (lines 714–716):
- All success: `t('broadcastModal.doneAllSuccess', { count: successCount })`  
- Mixed: `t('broadcastModal.doneSummary', { success: successCount, failed: failCount })`

The i18n keys exist and the logic exists (line 351 shows `"doneSummary": "{{success}} broadcast, {{failed}} failed."`), but the test suite contains no test that exercises this mixed success/failure path. The `done summary` test (lines 470–486) only mocks all shifts succeeding (`mockResolvedValue(undefined)`).

**Why it matters:** The `doneSummary` code path (line 716) is dead code at test time. If the condition logic is wrong — e.g., if `failCount === 0` is accidentally flipped — the test suite will not catch it. Agencies with large broadcast lists may hit single-shift failures (e.g., network timeout mid-loop, caregiver no longer available), and the done summary must accurately report mixed results.

**Fix:** Add this test to the bottom of the `BroadcastOpenModal` test suite (after the "renders nothing when open is false" test, before the closing `})`):

```typescript
it('shows mixed success/failure summary when some broadcasts fail', async () => {
  const shift1: ShiftSummaryResponse = { ...openShift, id: 's1' }
  const shift2: ShiftSummaryResponse = { ...openShift, id: 's2' }
  vi.mocked(shiftsApi.listOpenShifts).mockResolvedValue([shift1, shift2])
  
  let callCount = 0
  vi.mocked(shiftsApi.broadcastShift).mockImplementation(async (id) => {
    callCount++
    if (id === 's2') throw new Error('network timeout')
  })
  
  render(
    <BroadcastOpenModal
      open={true} onClose={vi.fn()}
      weekStart="2026-04-07T00:00:00" weekEnd="2026-04-14T00:00:00"
      clientMap={clientMap}
    />
  )
  await waitFor(() => screen.getByRole('button', { name: /broadcastModal.confirmBtn/ }))
  fireEvent.click(screen.getByRole('button', { name: /broadcastModal.confirmBtn/ }))
  
  await waitFor(() =>
    expect(screen.getByText(/broadcastModal.doneSummary/)).toBeInTheDocument()
  )
  expect(screen.getByText('broadcastModal.doneSummary:{"success":1,"failed":1}')).toBeInTheDocument()
})
```

Update the test count expectation (line 786) from "8 tests pass" to "9 tests pass".

Also update the Step 4 assertion comment (line 780) from "Expected: 8 tests pass." to "Expected: 9 tests pass."

---

## 3. Previously Addressed Items

✅ **C1 — Error handling on initial fetch** (Review #1) — Fully resolved. `.catch()` branch sets `phase='error'`, error body renders `loadError` text, error footer renders close button. Test verifies error state and close callback.

✅ **C2 — Loading-state test timing** (Review #1) — Fully resolved. Test now uses `mockImplementation(() => new Promise(() => {}))` to freeze component in loading phase for reliable synchronous assertion.

✅ **C3 — No controller IT for status param** (Review #1) — Fully resolved. Task 3 Step 2 adds two integration tests to `ShiftSchedulingControllerIT.java`: one for `?status=OPEN` returns only OPEN shifts, one for `?status=INVALID` returns 400.

✅ **C4 — Ambiguous controller validation instruction** (Review #1) — Fully resolved. Task 3 Step 1 explicitly states the `end.isAfter(start)` check is **removed** because service already validates. Rationale provided: controller check predates service validation and is now redundant.

✅ **M1 — Cancel label semantically wrong** (Review #1) — Resolved. Empty-shifts case footer uses `t('broadcastModal.close')` (line 738), not cancel.

✅ **M2 — Missing `aria-labelledby`** (Review #1) — Resolved. Dialog has `aria-labelledby="broadcast-modal-title"` (line 630); `<h2>` has `id="broadcast-modal-title"` (line 634).

---

## 4. Minor Issues & Improvements

**M4 — Hardcoded `size: 200` in listOpenShifts lacks justification**  
The API function (line 290 in Task 4) hardcodes `size: 200` shifts per request. For a 1–25 caregiver agency, this is more than sufficient, but it's unexplained. Add a comment:
```typescript
// Fetch up to 200 shifts; suitable for small agencies (≤25 caregivers).
export async function listOpenShifts(start: string, end: string): Promise<ShiftSummaryResponse[]> {
```

**M5 — No test for component unmount during async broadcast**  
If the modal closes while a broadcast is in flight, the resulting `setResults` state update is orphaned. React 18 + Strict Mode will warn in development but it's not tested. Low priority — async state cleanup during unmount is handled by cleanup in useEffect naturally in this case since there's no persisted state after `onClose()` unmounts the component. Not a bug, just a smell.

---

## 5. Questions for Clarification

**Q1 — Does the existing calendar view use `['shifts']` as the React Query key?**  
Line 609 invalidates `queryKey: ['shifts']` after all broadcasts complete. Verify that the calendar's `useQuery(...)` for shifts uses exactly this key; if it differs (e.g., `['shifts', weekStart, weekEnd]`), the calendar won't update on broadcast completion. Check `frontend/src/hooks/useShifts.ts` or equivalent.

**Q2 — Should the error message during broadcast failures surface the specific error reason?**  
Currently, if a single shift fails during broadcasting, the error state (`result === 'error'`, line 700) is shown as a red ✗. The error reason is lost. Is this acceptable for MVP, or should the plan capture the error message for a future tooltip/hover state?

---

## 6. Final Recommendation

**Approve with one additional test.**

N1 is a straightforward test addition that closes a genuine coverage gap for the mixed success/failure path. All Review #1 critical issues remain fixed. The plan is complete and implementation-ready after adding the 9th test.

