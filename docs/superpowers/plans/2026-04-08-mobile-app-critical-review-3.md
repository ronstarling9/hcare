# Critical Implementation Review — Pass 3

**Plan reviewed:** `docs/superpowers/plans/2026-04-08-mobile-app.md`
**Previous reviews:** `2026-04-08-mobile-app-critical-review-1.md` (6C, 9M, 5m — all resolved), `2026-04-08-mobile-app-critical-review-2.md` (3C, 5m — all resolved in session prior to this review)
**Date:** 2026-04-08

---

## 1. Overall Assessment

Across three review passes the plan has converged from a foundation with 20 significant defects to a well-structured implementation document. All issues from reviews 1 and 2 have been correctly resolved: the rehydrate Option B fix is cleanly implemented with the `caregiverProfile` SecureStore key, the `ClockInSheet` race condition is addressed with the correct `useEffect`/null-guard pattern, and the three deterministic test failures are fixed. This third pass identifies one new critical defect and two new minor issues. The critical defect (void clock-in is a no-op) will result in a corrupted store and leaked SQLite events at runtime, silently breaking the recovery flow the UX spec depends on. The two minor issues (pull-to-refresh is a stub; clock-out before care plan loads) are lower-priority but worth addressing before execution.

---

## 2. Critical Issues

### C1 — "Void Clock-In" in `VisitScreen` navigates only — does not call the API or clear the store

**Files:** `src/screens/visit/VisitScreen.tsx` (~lines 3502–3507)

**Description:**

The "Wrong shift?" overflow menu fires an `Alert.alert` with two actions. The destructive action does only one thing:

```tsx
{ text: 'Void Clock-In', style: 'destructive', onPress: () => navigation.navigate('Today') },
```

Three required side-effects are missing:

1. **`visitsApi.voidClockIn(activeVisitId!)`** is never called — the BFF endpoint `DELETE /mobile/visits/{visitId}/clock-in` that the spec requires is never hit.
2. **`clearActiveVisit()`** is never called — after navigation, `useVisitStore.activeVisitId` is still set. The TabBar's `isVisitActive` flag remains `true`; the FAB stays red; tapping it opens the Visit screen for a visit that was supposed to be voided.
3. **`deleteByVisitId(activeVisitId!)` from `@/db/events`** is never called — the CLOCK_IN event written to SQLite by `useClockIn` at clock-in time is still in the pending queue. When connectivity restores, `useOfflineSync` will attempt to sync a clock-in for a visit that was voided, producing an error or a ghost EVV record on the server.

**Why it matters:** The void clock-in flow is the only recovery path the spec provides for a wrong-shift scenario. After this action the caregiver appears to have recovered but the app is in a subtly broken state: wrong red FAB, wrong active-visit flag, and a leaked SQLite event that will cause a sync error or a phantom EVV record.

**Fix:**

Add a `voidClockIn` mutation to `VisitScreen` (or expose it from `useVisit`) and wire it to the alert:

```tsx
// In VisitScreen.tsx — add imports:
import { useMutation } from '@tanstack/react-query';
import { visitsApi } from '@/api/visits';
import { deleteByVisitId } from '@/db/events';

// Inside VisitScreen function, alongside the existing useVisit call:
const { activeVisitId, activeShiftId, activeClientId, activeClientName, clockInTime, gpsStatus, activeVisitNotes, setVisitNotes, clearActiveVisit } = useVisitStore();

const voidClockIn = useMutation({
  mutationFn: () => visitsApi.voidClockIn(activeVisitId!),
  onSuccess: async () => {
    // Clean up SQLite queue so the CLOCK_IN event is not synced.
    await deleteByVisitId(activeVisitId!).catch(console.error);
    clearActiveVisit();
    navigation.navigate('Today');
  },
  onError: () => {
    // Void window expired (>5 min) or network error — caregiver must contact agency.
    Alert.alert(
      'Could Not Void',
      'The void window may have expired (5 minutes from clock-in). Please contact your agency.',
    );
  },
});

// Update the Alert action:
<TouchableOpacity onPress={() => Alert.alert(
  'Wrong shift?',
  'This will void your clock-in if it was created less than 5 minutes ago.',
  [
    { text: 'Cancel', style: 'cancel' },
    { text: voidClockIn.isPending ? 'Voiding…' : 'Void Clock-In', style: 'destructive', onPress: () => voidClockIn.mutate() },
  ]
)}>
```

Note: `Alert.alert` returns immediately after the button tap — it does not re-render when `voidClockIn.isPending` changes, so the label will not update dynamically. The `isPending` guard is still useful to prevent double-tap via React state. For MVP this is acceptable. The error path (void window expired) should surface to the caregiver rather than silently failing.

---

## 3. Previously Addressed Items

All items from reviews 1 and 2 are confirmed resolved:

- **Review-1 C1–C6:** GPS status, clientId keying, notes persistence, store selectors, circular import, stale closure.
- **Review-1 M1–M9:** GpsStatus type, store fields, in-memory caveat, individual selectors, circular dep fix, maps URL, Platform import, 5 missing test files, SectionLabel.
- **Review-1 m1–m5:** Dead code, visitStore caveat comment, jest config, ProgressDots DRY, formatting.
- **Review-2 C1:** SettingsScreen tests now assert `screen.getByText(/permissions/i)` and `getAllByText('Change →')` — correct.
- **Review-2 C2:** MessageThreadScreen test uses `await waitFor` + `mockThreads[0].subject`, no unused `subject` route param — correct.
- **Review-2 C3:** ClockInSheet `useState(null)` + `useEffect` to set first shift when `upcoming` loads — correct.
- **Review-2 m1:** `visitStore.test.ts` created in Steps 1b/1c of Task 5 with full coverage of `setActiveVisit`, `setVisitNotes`, `clearActiveVisit`.
- **Review-2 m2:** `isAuthenticated: false` added to `authStore.test.ts` `beforeEach` setState.
- **Review-2 m3:** `authStore.rehydrate` now uses Option B — `caregiverProfile` SecureStore key persisted in `setAuth`, restored in `rehydrate`, deleted in `clearAuth`.
- **Review-2 m4:** VisitScreen test `beforeEach` seeds `activeClientId: 'cl-001'` and `gpsStatus: 'OK'`.
- **Review-2 m5:** Task 7 step numbering corrected (Steps 3, 4, 5).

---

## 4. Minor Issues & Improvements

### m1 — `OpenShiftsScreen` pull-to-refresh is a stub

**File:** `src/screens/openShifts/OpenShiftsScreen.tsx` (~line 3934)

```tsx
refreshControl={<RefreshControl refreshing={isLoading} onRefresh={() => {}} />}
```

`onRefresh` is a no-op. The `useOpenShifts` hook does not expose `refetch`, so there is no way for the user to manually trigger a refresh. The pull gesture shows the loading indicator momentarily (driven by `isLoading`) but fires no fetch.

**Fix:**

```ts
// In useOpenShifts.ts — add refetch to the return value:
return { shifts: query.data ?? [], isLoading: query.isLoading, refetch: query.refetch, accept, decline };

// In OpenShiftsScreen.tsx:
const { shifts, isLoading, refetch, accept, decline } = useOpenShifts();
// ...
refreshControl={<RefreshControl refreshing={isLoading} onRefresh={refetch} />}
```

---

### m2 — Clock Out button is active before the care plan loads — silent clock-out with no task data

**File:** `src/screens/visit/VisitScreen.tsx` (~lines 3474–3481, 3546–3553)

```tsx
const handleClockOutPress = () => {
  const pending = tasks.filter(t => !t.completed);
  if (pending.length > 0 && tasks.filter(t => t.completed).length / tasks.length < 0.5) {
    setShowClockOutModal(true);
  } else {
    doClockOut();  // ← called immediately when tasks is []
  }
};
```

`tasks` starts as `[]` (seeded by `useState<AdlTask[]>([])`). The care plan loads asynchronously via React Query. If the caregiver taps "Clock Out" before the care plan resolves — possible on a slow connection — `tasks` is empty, `pending.length > 0` is `false`, and `doClockOut()` is called immediately with no ADL task context and no incomplete-task warning. The EVV record is created with zero task completions.

**Fix:** Disable the Clock Out button until the care plan has loaded:

```tsx
<TouchableOpacity
  style={[styles.clockOutBtn, (clockOut.isPending || carePlanQuery.isLoading) && styles.clockOutBtnDisabled]}
  onPress={handleClockOutPress}
  disabled={clockOut.isPending || carePlanQuery.isLoading}
>
  <Text style={styles.clockOutText}>
    {clockOut.isPending ? 'Clocking out…' : carePlanQuery.isLoading ? 'Loading…' : 'Clock Out'}
  </Text>
  <Text style={styles.clockOutSub}>GPS will be captured</Text>
</TouchableOpacity>
```

---

## 5. Questions for Clarification

1. **`voidClockIn` — online-only or offline-queueable?** The BFF endpoint spec says `DELETE /mobile/visits/{visitId}/clock-in` requires connectivity (the void window is 5 minutes, making offline-queue semantics problematic). Should the "Wrong shift?" action be disabled entirely when offline? The current `Alert.alert` has no connectivity check.

2. **`events.test.ts` — SQLite in Jest:** The SQLite test (`src/__tests__/db/events.test.ts`) calls `openEventStore()`, `insertEvent()`, `getPendingEvents()`, and `markSynced()` directly. Whether this works depends on whether the `jest-expo` preset's expo-sqlite mock supports async read/write. If the mock is a stub (returns `undefined` from all async functions), these tests will silently pass without actually testing anything. Recommend confirming before Task 10 that `jest-expo`'s expo-sqlite mock supports the async `openDatabaseAsync`, `execAsync`, `runAsync`, `getAllAsync` API surface used in `events.ts`.

---

## 6. Final Recommendation

**Approve with changes.**

Fix C1 (void clock-in) before starting Task 11 execution — it directly affects the VisitScreen implementation step and the "Wrong shift?" recovery path tested in the smoke-test checklist (Task 19 Step 2). The two minor issues (m1, m2) are straightforward one-line fixes best applied during their respective tasks (Task 13 for m1, Task 11 for m2).

Recommended order:
1. Fix C1 — void clock-in mutation + `clearActiveVisit()` + `deleteByVisitId()` — before Task 11 starts (~15 min)
2. Fix m2 — disable Clock Out during `carePlanQuery.isLoading` — inside Task 11 when implementing VisitScreen (~2 min)
3. Fix m1 — expose `refetch` from `useOpenShifts` and wire `onRefresh` — inside Task 13 (~2 min)
4. Clarify Q2 (SQLite in Jest) before Task 10 begins

---

*Review written: 2026-04-08*
