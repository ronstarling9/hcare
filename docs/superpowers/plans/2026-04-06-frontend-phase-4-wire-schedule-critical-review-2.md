# Critical Implementation Review 2 — Phase 4: Wire Schedule Screen

**Reviewed:** `2026-04-06-frontend-phase-4-wire-schedule.md`
**Previous reviews:** `2026-04-06-frontend-phase-4-wire-schedule-critical-review-1.md`
**Date:** 2026-04-07

---

## 1. Overall Assessment

The revised plan has fully addressed every issue from Critical Review 1 — the six critical TypeScript errors (C1–C6) are gone, and all four minor issues (M1–M4) are at least partially resolved. The overall architecture is sound: correct prop types, correct panel store usage, correct commit ordering, TDD steps throughout, and no SlidePanel import in leaf components. Three new important issues remain that were not visible in Review 1 and will cause either CI failure or a visible UI regression if unaddressed. None require architectural rethinking.

---

## 2. Critical Issues

None. All C1–C6 from Review 1 have been resolved.

---

## 3. New Important Issues

### I1 — `getCandidates` called via raw `useQuery` directly inside `ShiftDetailPanel` — violates CLAUDE.md arch rule

**Description:** Task 5's `ShiftDetailPanel` contains:

```tsx
import { getCandidates } from '../../api/shifts'
...
const { data: candidates } = useQuery({
  queryKey: ['candidates', shiftId],
  queryFn: () => getCandidates(shiftId),
  enabled: Boolean(shiftId),
})
```

CLAUDE.md explicitly states:
> *"Do not fetch data directly in React components — use a custom hook wrapping React Query."*

This snippet does both violations at once: it imports the raw API function into a component, and it calls `useQuery` directly in a component rather than inside a custom hook. Every other data-fetch in this plan correctly goes through a hook (`useShiftDetail`, `useClients`, `useCaregivers`, etc.).

Additionally, because the query lives inline in the component it is untested — there is no test anywhere in the plan for the candidates query.

**Fix:** Add `useGetCandidates` to `useShifts.ts`:

```ts
export function useGetCandidates(shiftId: string | null) {
  return useQuery({
    queryKey: ['candidates', shiftId],
    queryFn: () => getCandidates(shiftId!),
    enabled: Boolean(shiftId),
  })
}
```

Add one test to `useShifts.test.ts` (following TDD: write test first):

```ts
describe('useGetCandidates', () => {
  it('returns empty array while loading, then candidates', async () => {
    const candidates: RankedCaregiverResponse[] = [{ caregiverId: 'g1', score: 95, explanation: 'Great match' }]
    mock.onGet('/shifts/s1/candidates').reply(200, candidates)
    const { result } = renderHook(() => useGetCandidates('s1'), { wrapper: makeWrapper() })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data?.[0].caregiverId).toBe('g1')
  })

  it('does not fetch when shiftId is null', () => {
    const { result } = renderHook(() => useGetCandidates(null), { wrapper: makeWrapper() })
    expect(result.current.fetchStatus).toBe('idle')
  })
})
```

In `ShiftDetailPanel`, replace the inline `useQuery` + `getCandidates` import with:

```tsx
import { useShiftDetail, useAssignCaregiver, useBroadcastShift, useClockIn, useGetCandidates } from '../../hooks/useShifts'
...
const { data: candidates } = useGetCandidates(shiftId)
```

---

### I2 — `tCommon('loading')` key is missing from `common.json`; `ShiftDetailPanel` loading state will render the literal string "loading"

**Description:** Task 5's `ShiftDetailPanel` loading state renders:

```tsx
<p className="text-[13px]">{tCommon('loading')}</p>
```

`tCommon` is `useTranslation('common').t`. Inspecting `public/locales/en/common.json`, the file contains only:

```json
{ "back", "unassigned", "noDash", "yes", "no", "cancel", "searchByName", "noExpiry" }
```

There is no `"loading"` key. i18next's fallback behavior is to return the key string itself — so the loading panel will display the word **"loading"** in production rather than a human-readable message.

Task 5 has no step to add this key. Task 7 adds `"loading"` to `schedule.json` (for `SchedulePage`), but `ShiftDetailPanel` reads from the `common` namespace.

**Fix:** Add a Step 5.x immediately after Step 5.1:

```
Step 5.x: Add 'loading' key to common locale

In `frontend/public/locales/en/common.json`, add:
  "loading": "Loading…"

Include this file in the Step 5.3 commit:
  git add src/components/schedule/ShiftDetailPanel.tsx public/locales/en/common.json
```

---

### I3 — Mutation hooks and utility hooks have no tests; 80% coverage threshold will likely be missed

**Description:** `useShifts.ts` exports six hooks. The plan's `useShifts.test.ts` covers only two (`useShifts`, `useShiftDetail`). The following four are untested:

- `useCreateShift`
- `useAssignCaregiver`
- `useBroadcastShift`
- `useClockIn`

Additionally, `useClients.ts` exports `useClientDetail` (untested) and `useCaregivers.ts` exports `useCaregiverDetail` (untested).

At the API layer: `api/clients.ts` and `api/caregivers.ts` have no corresponding `*.test.ts` files at all, while `api/shifts.ts` has 7 tests covering all functions. This asymmetry is inconsistent and leaves `listClients`, `getClient`, `listCaregivers`, `getCaregiver` entirely uncovered.

CLAUDE.md: *"80% frontend unit test coverage (Vitest + Testing Library)"*. With these gaps, CI's coverage check will block the merge.

**Fix:** Add the following test coverage before committing each file:

**`api/clients.ts` (add to Step 3.1 or as a new step before 3.2):** Create `frontend/src/api/clients.test.ts` with two tests (follows the exact pattern of `shifts.test.ts`): one that calls `listClients` and expects the mocked response, one that calls `getClient('c1')` and expects the mocked client.

**`api/caregivers.ts` (add to Step 4.1 or as a new step before 4.2):** Create `frontend/src/api/caregivers.test.ts` with two equivalent tests for `listCaregivers` and `getCaregiver`.

**Mutation hooks (add to Step 2's test suite, before Step 2.3):** Three focused mutation tests for `useCreateShift`, `useAssignCaregiver`, and `useBroadcastShift` — verifying that `mutateAsync` calls the correct API and that `invalidateQueries` fires. Use the `@testing-library/react` `act` + `waitFor` pattern:

```ts
describe('useCreateShift', () => {
  it('calls POST /shifts and invalidates the shifts query', async () => {
    mock.onPost('/shifts').reply(201, summary)
    const { result } = renderHook(() => useCreateShift(), { wrapper: makeWrapper() })
    await act(() =>
      result.current.mutateAsync({
        clientId: 'c1', serviceTypeId: 'st1',
        scheduledStart: '2026-04-07T09:00:00', scheduledEnd: '2026-04-07T13:00:00',
      })
    )
    expect(result.current.isSuccess).toBe(true)
  })
})
```

---

## 4. Previously Addressed Items

All eight issues from Critical Review 1 are fully resolved:

- **C1** (`weekStart` type) — `weekStart` state is `Date`; `toLocalISODateTime()` derives ISO strings for API params; `WeekCalendar` receives `weekStart={weekStart}` as `Date`.
- **C2** (shifts type) — Task 0 changes `WeekCalendar.shifts` to `ShiftSummaryResponse[]` before any other task touches SchedulePage; every intermediate commit is type-correct.
- **C3** (`onEmptySlotClick`) — `handleNewShift` retained in SchedulePage and passed as `onEmptySlotClick={handleNewShift}`.
- **C4** (`'shiftDetail'` panel type + double-render) — `openPanel('shift', id, …)` throughout; no inline panel rendering in SchedulePage; Shell remains sole owner.
- **C5** (SlidePanel in leaf panels) — ShiftDetailPanel and NewShiftPanel render plain `div` layouts; no `SlidePanel` import.
- **C6** (broken intermediate commits) — Order is Task 5 (ShiftDetailPanel) → Task 6 (NewShiftPanel) → Task 7 (SchedulePage); SchedulePage references new panel interfaces only after they exist.
- **M1** (duplicate types) — `CreateShiftRequest` imported from `types/api`; only `ClockInRequest` defined locally.
- **M2** (UTC timezone) — `toLocalISODateTime()` uses `getFullYear`/`getMonth`/`getDate`; no `toISOString()` calls.
- **M3** (missing test steps) — TDD steps (red → green) added to Tasks 1, 2, 3, 4.
- **M4** (inline hex) — EVV status badge colors (`EVV_BG`/`EVV_TEXT`) use inline hex as a necessary dynamic record (no corresponding Tailwind tokens for health-status semantics); this is acceptable. The primary token violations (raw hex on backgrounds, primary text colors) are removed.

---

## 5. Minor Issues

### m1 — Back-button `color` and candidate rank badge `background` still use inline hex

**Description:** Despite M4 being listed as fixed, Task 5 (`ShiftDetailPanel`) and Task 6 (`NewShiftPanel`) retain inline hex on interactive elements:

- Back/close buttons: `style={{ color: '#1a9afa' }}` (×4 instances across both panels)
- AI candidate rank badge: `style={{ background: i === 0 ? '#1a9afa' : '#94a3b8' }}`

The `bg-blue` token is defined as `#1a9afa` in the Tailwind config. The `text-text-muted` token is `#94a3b8`. These should be expressed as Tailwind classes:

```tsx
// Instead of: style={{ color: '#1a9afa' }}
className="text-blue hover:underline"

// Instead of: style={{ background: i === 0 ? '#1a9afa' : '#94a3b8' }}
className={`${i === 0 ? 'bg-blue' : 'bg-text-muted'} w-6 h-6 …`}
```

The EVV_BG/EVV_TEXT records are exempt (no matching tokens for health-status semantics).

---

### m2 — `useClientDetail` and `useCaregiverDetail` are exported but never tested or used

Both hooks are created in Tasks 3 and 4 and exported, but:
- Neither has a test in the corresponding `.test.ts` file.
- Neither is referenced anywhere in Tasks 5–7.

If they are intended for a later phase, their test coverage gap still applies to the current plan (CLAUDE.md: 80% coverage on every merged commit). If they are not needed yet, removing them from this plan keeps scope clean (YAGNI) and they can be added when the consuming code exists.

---

## 6. Questions for Clarification

- **Q1:** The `assignCaregiver` function in `shifts.ts` constructs the request body inline (`{ caregiverId }`). `types/api.ts` exports `AssignCaregiverRequest`. Should the function accept `AssignCaregiverRequest` explicitly, or is the current `(shiftId: string, caregiverId: string)` signature preferred for its callsite clarity? (This is a style question, not a bug — both produce the same HTTP payload.)
- **Q2:** The plan passes `Map<string, ClientResponse>` where `WeekCalendar` currently expects `Map<string, { firstName: string; lastName: string }>`. TypeScript's bivariant method checking will accept this without error, but it may be worth tightening `WeekCalendar`'s `clientMap`/`caregiverMap` types to the actual `ClientResponse`/`CaregiverResponse` types (or a `PersonName` interface shared across both) to make the contract explicit. Out of scope for Phase 4, but worth noting for a later refactor.

---

## 7. Final Recommendation

**Approve with changes.**

The plan is substantially correct and ready to execute once three changes are made:

1. Move the candidates query into a `useGetCandidates` hook and add tests (I1).
2. Add `"loading": "Loading…"` to `common.json` in the Task 5 commit (I2).
3. Add API-level tests for `api/clients.ts` and `api/caregivers.ts`, and mutation tests for `useShifts.ts` (I3).

None of these require structural changes to the task ordering or architectural approach.
