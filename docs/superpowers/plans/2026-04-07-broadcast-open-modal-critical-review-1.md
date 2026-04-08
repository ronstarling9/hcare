# Critical Implementation Review #1
**Plan:** `2026-04-07-broadcast-open-modal.md`
**Reviewer:** Senior Staff Engineer — first review pass (no prior reviews exist)

---

## 1. Overall Assessment

The plan is well-structured and covers the full stack from the derived query to the modal's phase state machine. The TDD discipline is followed throughout, the file map is clear, and the sequential-loop approach for broadcasts is the correct choice. Four issues require fixes before execution: one will silently trap users in an unresponsive modal on API failure, one produces a test that can never actually observe the state it claims to test, one leaves a controller behavior completely untested at the integration level, and the task description leaves an important ambiguity for the implementor.

---

## 2. Critical Issues

### C1 — `useEffect` fetch has no `.catch()` — modal traps users on API failure

**Description:** In `BroadcastOpenModal`, the `useEffect` calls `listOpenShifts(...).then(...)` with no `.catch()`. If the request fails (network error, 401, 500), `phase` stays at `'loading'` forever. The footer renders **nothing** in `loading` phase, so the user has no button to close the modal — they are completely trapped.

**Why it matters:** Production networks fail. The 401 session-expiry redirect fires in the Axios interceptor, which would leave the modal open and frozen in the background. Any transient 500 does the same. This is a silent usability breakage.

**Fix:** Add error state handling. Add `'error'` to the `Phase` type and a `.catch()` branch, and render a close button in the error phase:

```typescript
// Phase type:
type Phase = 'loading' | 'confirm' | 'broadcasting' | 'done' | 'error'

// In useEffect:
listOpenShifts(weekStart, weekEnd)
  .then((shifts) => {
    setOpenShifts(shifts)
    setPhase('confirm')
  })
  .catch(() => setPhase('error'))

// In JSX body — add after loading block:
{phase === 'error' && (
  <p className="text-[13px] text-red-600 py-4 text-center">
    {t('broadcastModal.loadError')}
  </p>
)}

// In footer — add:
{phase === 'error' && (
  <button type="button" onClick={handleClose}
    className="px-4 py-2 text-[13px] font-semibold border border-border text-dark rounded hover:brightness-95">
    {t('broadcastModal.close')}
  </button>
)}
```

Add `"loadError": "Failed to load open shifts. Please try again."` to `schedule.json` under `broadcastModal`.

Add a test in `BroadcastOpenModal.test.tsx`:

```typescript
it('shows error state and close button when fetch fails', async () => {
  vi.mocked(shiftsApi.listOpenShifts).mockRejectedValue(new Error('network'))
  const onClose = vi.fn()
  render(
    <BroadcastOpenModal
      open={true} onClose={onClose}
      weekStart="2026-04-07T00:00:00" weekEnd="2026-04-14T00:00:00"
      clientMap={clientMap}
    />
  )
  await waitFor(() =>
    expect(screen.getByText('broadcastModal.loadError')).toBeInTheDocument()
  )
  fireEvent.click(screen.getByRole('button', { name: 'broadcastModal.close' }))
  expect(onClose).toHaveBeenCalled()
})
```

---

### C2 — `'shows loading state on open'` test cannot observe the loading phase

**Description:** The test mocks `listOpenShifts` with `mockResolvedValue([])`, which resolves in the same microtask tick. By the time `render(...)` returns and the assertion runs, React has already flushed the `.then()` callback and transitioned `phase` to `'confirm'`. The `'broadcastModal.loading'` text is gone. This test will either pass flakily or — more likely in JSDOM — fail consistently.

**Why it matters:** A test that cannot observe the state it claims to cover gives false confidence. It may pass under one timing and fail under another, or it may always pass for the wrong reason (e.g. if the text appears elsewhere in the DOM).

**Fix:** Use a never-resolving promise to freeze the component in the loading phase:

```typescript
it('shows loading state on open', () => {
  vi.mocked(shiftsApi.listOpenShifts).mockImplementation(
    () => new Promise(() => {}) // never resolves — keeps phase at 'loading'
  )
  render(
    <BroadcastOpenModal
      open={true} onClose={vi.fn()}
      weekStart="2026-04-07T00:00:00" weekEnd="2026-04-14T00:00:00"
      clientMap={clientMap}
    />
  )
  expect(screen.getByText('broadcastModal.loading')).toBeInTheDocument()
  // synchronous assertion — no waitFor needed
})
```

---

### C3 — No integration test for the new `?status=OPEN` controller param

**Description:** Task 3 adds `@RequestParam(required = false) ShiftStatus status` to the controller and wires it through to the service. The existing `ShiftSchedulingServiceTest` unit tests verify the service branching. But `ShiftSchedulingControllerIT.java` exists and covers other endpoints — the new param has zero integration test coverage. If the binding fails (e.g. Spring cannot convert the string `"OPEN"` to `ShiftStatus`, or the param name is misspelled), the unit tests would still pass while every real API call with `?status=OPEN` returns a 400 or 500.

**Why it matters:** `@RequestParam` binding for custom enums relies on Spring's `ConversionService`. This has historically been a source of silent misconfiguration. An IT catches what the unit test cannot.

**Fix:** Add a test to `ShiftSchedulingControllerIT.java` in Task 3:

```java
@Test
void listShifts_withStatusFilter_returnsOnlyOpenShifts() throws Exception {
    mockMvc.perform(get("/api/v1/shifts")
            .header("Authorization", "Bearer " + adminToken)
            .param("start", "2026-04-07T00:00:00")
            .param("end", "2026-04-14T00:00:00")
            .param("status", "OPEN"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[*].status", everyItem(is("OPEN"))));
}
```

Also add a negative test to confirm `?status=INVALID` returns 400 (validates the `GlobalExceptionHandler` integration from the QA sprint):

```java
@Test
void listShifts_withInvalidStatus_returns400() throws Exception {
    mockMvc.perform(get("/api/v1/shifts")
            .header("Authorization", "Bearer " + adminToken)
            .param("start", "2026-04-07T00:00:00")
            .param("end", "2026-04-14T00:00:00")
            .param("status", "NOT_A_STATUS"))
        .andExpect(status().isBadRequest());
}
```

---

### C4 — Plan ambiguously tells implementor to optionally remove controller-level date validation

**Description:** Task 3 Step 1 says: _"Remove the duplicate `end.isAfter(start)` check from the controller — the service already validates this. (Keep it if the existing code has it to avoid a 500 before the service call; either is fine…)"_. Leaving this to implementor judgment means the code may diverge inconsistently across environments. The controller currently has the check — the plan must make a definitive call.

**Why it matters:** A plan with "either is fine" invites drift and confusion in code review. The right answer here has a clear rationale.

**Fix:** Remove the check from the controller. The service validates and throws a `ResponseStatusException(BAD_REQUEST)`, which propagates correctly. The controller-level check predates the service-layer validation and is now redundant. State this explicitly as a required removal.

---

## 3. Previously Addressed Items

N/A — this is the first review.

---

## 4. Minor Issues & Improvements

**M1 — Empty-shifts state uses "Cancel" label — semantically wrong**
When `openShifts.length === 0` in the confirm phase, the footer shows a "Cancel" button. There is nothing to cancel — no action has been initiated. The label should be "Close". The fix is in the JSX footer block for the empty case; change `{t('broadcastModal.cancel')}` to `{t('broadcastModal.close')}`.

**M2 — Missing `aria-labelledby` on the dialog element**
The modal has `role="dialog" aria-modal="true"` but no `aria-labelledby` pointing at the `<h2>` title. Screen readers will not announce the dialog name on open. Fix: add `id="broadcast-modal-title"` to the `<h2>` and `aria-labelledby="broadcast-modal-title"` to the outer `<div role="dialog">`.

**M3 — `handleClose` redundantly resets state that is never observed after close**
`handleClose` sets `phase='loading'`, clears `openShifts` and `results`, then calls `onClose()`. Since `open` becomes `false` and the component immediately returns `null`, this cleanup is never rendered and has no effect. It is not harmful but adds misleading noise. The `useEffect` already resets everything on the next `open=true` transition. Consider removing the redundant state resets from `handleClose` to keep it to: `onClose()`.

**M4 — `broadcastShift` response body is silently discarded**
The backend's `POST /shifts/{id}/broadcast` returns `List<ShiftOfferSummary>` (the caregivers who were offered the shift). The frontend function `broadcastShift` returns `Promise<void>` and discards this data. The current plan doesn't surface caregiver counts in the done summary. This is intentional and acceptable for now, but should be called out explicitly in the plan so a future engineer doesn't wonder if the discard was accidental. Add a one-line comment in `shifts.ts`:

```typescript
// Response body (List<ShiftOfferSummary>) intentionally discarded — surface offer counts in a future iteration
export async function broadcastShift(shiftId: string): Promise<void> {
  await apiClient.post(`/shifts/${shiftId}/broadcast`)
}
```

**M5 — No focus management on modal open**
The modal doesn't move focus to the first interactive element on open. Combined with the missing `aria-labelledby` (M2), keyboard-only users have no indication the modal appeared. A `useEffect` that focuses the first button or the close button on phase transition would address this. This is a lower-priority accessibility improvement.

---

## 5. Questions for Clarification

**Q1 — Should the done summary show how many caregivers were notified per shift?**
The backend returns `List<ShiftOfferSummary>` from each broadcast call. If the frontend upgraded `broadcastShift` to return `ShiftOfferSummary[]`, the done summary could show "7 shifts broadcast, 23 caregivers notified." Is that level of detail desired now, or deferred?

**Q2 — `ShiftSchedulingControllerIT` test setup — is an `adminToken` fixture available?**
The IT test in C3 assumes a `adminToken` field. Confirm the existing `ShiftSchedulingControllerIT` already has a mechanism to obtain a valid JWT (many projects seed a test user in `@BeforeEach` using the seeder or a test-specific auth endpoint). Adjust if the pattern differs.

---

## 6. Final Recommendation

**Approve with changes.**

Fix C1 (error state + close button + test), C2 (loading-state test timing), C3 (add controller IT), and C4 (remove ambiguity about controller validation). Address M1 (Close vs Cancel label) since it's a one-line fix. M2–M5 can be tracked as follow-on issues.
