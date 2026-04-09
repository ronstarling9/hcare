# Critical Implementation Review — Pass 2

**Plan reviewed:** `docs/superpowers/plans/2026-04-08-mobile-app.md`
**Previous reviews:** `2026-04-08-mobile-app-critical-review-1.md` (6 critical, 9 medium, 5 minor issues)
**Date:** 2026-04-08

---

## 1. Overall Assessment

The plan has improved substantially since review-1. All 20 previously raised issues (C1–C6, M1–M9, m1–m5) have been addressed — the circular-import resolution, GPS status tracking, clientId keying, notes persistence, and store selector discipline are all correctly implemented. This pass surfaces four new problems, two of which are deterministic test failures that will block the CI run on the first `npm test` execution. A race condition in `ClockInSheet` will cause a subtle UX regression that's easy to miss in a mock environment. A missing rehydration fix will produce a degraded-state app after restart. Fixing these four issues will bring the plan to a state ready for confident execution.

---

## 2. Critical Issues

### C1 — SettingsScreen test assertions don't match the screen implementation

**Files:** `src/__tests__/screens/SettingsScreen.test.tsx`, `src/screens/settings/SettingsScreen.tsx`

**Description:**

The `SettingsScreen` test added in Task 15 (Step 5) contains two assertions that will fail deterministically:

```tsx
it('renders Sign Out button with confirmation dialog label', () => {
  render(<SettingsScreen navigation={nav} />, { wrapper });
  expect(screen.getByText(/sign out/i)).toBeTruthy();  // ← FAIL
});

it('shows a "Change in Settings" link for permission states', () => {
  render(<SettingsScreen navigation={nav} />, { wrapper });
  expect(screen.getByText(/change in settings/i)).toBeTruthy();  // ← FAIL
});
```

1. **Sign Out is not in SettingsScreen.** The `SettingsScreen` implementation (lines 4409–4510) shows Permissions, Agency name, App version, Terms, and Privacy. Sign Out lives in `ProfileScreen`. No `/sign out/i` match exists in `SettingsScreen`.

2. **"Change in Settings" does not appear in SettingsScreen.** The permission rows render `"Change →"` (line ~4457, 4466), not `"Change in Settings"`. The regex `/change in settings/i` matches nothing.

**Why it matters:** These tests will fail on `npm test`, blocking the CI run. The step says "Expected: PASS" — that's incorrect.

**Fix:**

Replace the two assertions with ones that match the actual screen:

```tsx
describe('SettingsScreen', () => {
  const nav = { goBack: jest.fn(), navigate: jest.fn() };

  it('renders the Permissions section header', () => {
    render(<SettingsScreen navigation={nav} />, { wrapper });
    expect(screen.getByText(/permissions/i)).toBeTruthy();
  });

  it('shows a "Change →" link for notification and location permissions', () => {
    render(<SettingsScreen navigation={nav} />, { wrapper });
    const changeLinks = screen.getAllByText('Change →');
    expect(changeLinks.length).toBeGreaterThanOrEqual(2); // one for Notifications, one for Location
  });
});
```

---

### C2 — MessageThreadScreen test assertion uses wrong subject text

**Files:** `src/__tests__/screens/MessageThreadScreen.test.tsx`

**Description:**

The test passes `subject: 'Scheduling Update'` in route params and then asserts that the screen renders that text:

```tsx
const route = { params: { threadId: MOCK_THREAD_ID_1, subject: 'Scheduling Update' } };

it('renders the thread subject', () => {
  render(<MessageThreadScreen navigation={nav} route={route} />, { wrapper });
  expect(screen.getByText('Scheduling Update')).toBeTruthy();  // ← FAIL
});
```

Two problems:
1. `MessageThreadScreen` does not use `route.params.subject` at all. The implementation reads the subject from the API response: `{data?.thread.subject ?? ''}`. Before the query resolves, this renders an empty string.
2. The mock handler returns `mockThreads.find(t => t.id === id)?.subject`, which is `'Schedule update for next week'` (from `mockThreads[0]`) — not `'Scheduling Update'`. The assertion will never find a match.

**Why it matters:** This test will fail on the first run.

**Fix:**

Use `waitFor` and assert the actual mock data subject, or assert the component's loading behavior:

```tsx
describe('MessageThreadScreen', () => {
  const nav = { goBack: jest.fn() };
  const route = { params: { threadId: MOCK_THREAD_ID_1 } };  // remove unused subject param

  it('loads and displays the thread subject from the API', async () => {
    render(<MessageThreadScreen navigation={nav} route={route} />, { wrapper });
    // subject comes from the mock API response, not route params
    await waitFor(() =>
      expect(screen.getByText(mockThreads[0].subject)).toBeTruthy()
    );
  });

  it('loads and displays thread messages', async () => {
    render(<MessageThreadScreen navigation={nav} route={route} />, { wrapper });
    await waitFor(() =>
      expect(screen.getByText(mockMessages[0].body)).toBeTruthy()
    );
  });
});
```

---

### C3 — ClockInSheet `selectedId` initializes to `null` due to async data

**Files:** `src/screens/clockIn/ClockInSheet.tsx`

**Description:**

```tsx
export function ClockInSheet({ navigation }: any) {
  const { upcoming } = useToday();
  const [selectedId, setSelectedId] = useState<string | null>(upcoming[0]?.id ?? null);
  ...
```

`useToday()` fetches data asynchronously via React Query. On first render, `upcoming` is `[]` (the initial empty array), so `selectedId` initializes to `null`. `useState` only uses its initial value on the first render — when `upcoming` resolves later, the state is **not** updated. The caregiver will see the shift list with no shift selected, and the Clock In button won't appear until they manually tap a shift.

This regression from the intended behavior ("soonest upcoming shift is pre-selected") will occur even in the happy path (online, mocks active, data resolves in 350ms).

**Why it matters:** The shift list renders with no selection. The `selected` variable will be `null` and the `{selected && <View style={styles.footer}>...}` block won't render, so the Clock In button is hidden until the user manually taps. The test at `'enables Clock In button after selecting a shift'` confirms that the button appears after a tap, which will pass — but the pre-selection feature is silently broken.

**Fix:**

Add a `useEffect` that sets the initial selection once data arrives, but only if nothing has been selected yet:

```tsx
// In ClockInSheet, replace the useState line and add a useEffect:
const [selectedId, setSelectedId] = useState<string | null>(null);

useEffect(() => {
  if (upcoming.length > 0 && selectedId === null) {
    setSelectedId(upcoming[0].id);
  }
}, [upcoming]);
```

This sets the initial selection when the data loads without overriding a user's explicit selection change.

---

## 3. Previously Addressed Items

All items from review-1 are resolved:

- **C1 (gpsStatus hardcoded 'OK'):** `VisitScreen` now reads `gpsStatus ?? 'UNAVAILABLE'` from `visitStore`.
- **C2 (CarePlanSection clientId wrong source):** `clientId={activeClientId ?? ''}` from store, not `carePlan.id`.
- **C3 (notes not surviving re-navigation):** `CareNotes` accepts `initialValue` prop; `VisitScreen` reads from `activeVisitNotes`; `doClockOut` uses `activeVisitNotes ?? ''`.
- **C4 (useAuthStore spread):** `useAuth` uses individual `useAuthStore(s => s.field)` selectors.
- **C5 (circular import):** `handlers.ts` accepts optional `apiClient` param with default; `client.ts` passes it explicitly; module-load ordering safety documented.
- **C6 (stale syncMutation closure):** `syncMutationRef` pattern correctly prevents stale closure in the NetInfo listener.
- **M1–M9:** GpsStatus type, clientId in store, notes persistence, store selector discipline, Platform maps URL, `require()` replaced with `Platform` import, 5 missing screen test files added, `SectionLabel` implemented.
- **m1–m5:** Dead code removed, visitStore in-memory caveat added, jest config comment added, ProgressDots extracted and shared.

---

## 4. Minor Issues & Improvements

### m1 — `visitStore.test.ts` listed in file structure but no task creates it

**File:** `src/__tests__/store/visitStore.test.ts`

The file structure (line 116) lists `visitStore.test.ts` alongside `authStore.test.ts`, but no task ever creates it. Only `authStore.test.ts` gets a proper test task (Task 4). The `visitStore` has grown significantly (added `activeClientId`, `gpsStatus`, `activeVisitNotes`, `setVisitNotes`) and deserves coverage.

**Fix:** Add a step to Task 5 (visitStore is created there) that creates the test file:

```ts
// src/__tests__/store/visitStore.test.ts
import { useVisitStore } from '@/store/visitStore';

beforeEach(() => {
  useVisitStore.setState({
    activeVisitId: null, activeShiftId: null, activeClientId: null,
    activeClientName: null, clockInTime: null, gpsStatus: null, activeVisitNotes: null,
  });
});

describe('visitStore', () => {
  it('starts with no active visit', () => {
    expect(useVisitStore.getState().activeVisitId).toBeNull();
  });

  it('setActiveVisit populates all fields', () => {
    useVisitStore.getState().setActiveVisit('v-1', 'sh-1', 'cl-1', 'Eleanor Vance', '2026-04-08T07:00:00Z', 'OK');
    const state = useVisitStore.getState();
    expect(state.activeVisitId).toBe('v-1');
    expect(state.gpsStatus).toBe('OK');
    expect(state.activeClientId).toBe('cl-1');
  });

  it('setVisitNotes updates only notes', () => {
    useVisitStore.getState().setActiveVisit('v-1', 'sh-1', 'cl-1', 'Eleanor', '2026-04-08T07:00:00Z', 'OK');
    useVisitStore.getState().setVisitNotes('Some notes.');
    expect(useVisitStore.getState().activeVisitNotes).toBe('Some notes.');
    expect(useVisitStore.getState().activeVisitId).toBe('v-1'); // unchanged
  });

  it('clearActiveVisit resets all fields', () => {
    useVisitStore.getState().setActiveVisit('v-1', 'sh-1', 'cl-1', 'Eleanor', '2026-04-08T07:00:00Z', 'OK');
    useVisitStore.getState().clearActiveVisit();
    expect(useVisitStore.getState().activeVisitId).toBeNull();
    expect(useVisitStore.getState().gpsStatus).toBeNull();
  });
});
```

---

### m2 — `authStore.test.ts` `beforeEach` does not reset `isAuthenticated`

**File:** `src/__tests__/store/authStore.test.ts`

```ts
beforeEach(() => {
  useAuthStore.setState({
    accessToken: null, refreshToken: null, caregiverId: null,
    agencyId: null, name: null, agencyName: null, firstLogin: false,
    // ← isAuthenticated missing
  });
});
```

`isAuthenticated` is stored state (not derived). If the `setAuth` test runs before the `clearAuth` test, `isAuthenticated` remains `true` at the start of the next test. The `'starts with no auth state'` test would then spuriously pass only because it runs first.

**Fix:** Add `isAuthenticated: false` to the `beforeEach` setState call.

---

### m3 — `authStore.rehydrate` does not restore `name`, `agencyName`, `caregiverId`

**File:** `src/store/authStore.ts`

```ts
rehydrate: async () => {
  const accessToken  = await SecureStore.getItemAsync('accessToken');
  const refreshToken = await SecureStore.getItemAsync('refreshToken');
  if (accessToken && refreshToken) {
    set({ accessToken, refreshToken, isAuthenticated: true });
    //   ↑ name, agencyName, caregiverId, agencyId NOT restored
  }
},
```

After a cold restart, `RootNavigator` calls `rehydrate()`, sets `isAuthenticated: true`, and renders `AppNavigator`. But `name` is `null`. The TodayScreen greeting reads `name?.split(' ')[0]` and shows "Good morning, " (blank). `ProfileScreen` shows `??` initials.

The tokens-only restore is intentional for security (names aren't secrets), but the UX degradation is unacceptable for the next API call round-trip window (350ms mock delay → blank greeting flashes on load).

**Fix — Option A (minimal):** Accept the degraded state and document it as a known limitation. Add a comment to `rehydrate`:

```ts
// Only tokens are restored — profile fields (name, agencyName, etc.) are
// null until the first API response populates them. Screens reading these
// fields must handle null gracefully (e.g., greeting shows blank briefly).
```

**Fix — Option B (correct):** Persist non-sensitive profile fields to SecureStore in `setAuth` and restore them in `rehydrate`:

```ts
setAuth: async ({ ..., name, agencyName, caregiverId, agencyId, firstLogin }) => {
  await SecureStore.setItemAsync('accessToken', accessToken);
  await SecureStore.setItemAsync('refreshToken', refreshToken);
  await SecureStore.setItemAsync('caregiverProfile', JSON.stringify({ name, agencyName, caregiverId, agencyId, firstLogin }));
  set({ accessToken, refreshToken, name, agencyName, caregiverId, agencyId, firstLogin, isAuthenticated: true });
},

rehydrate: async () => {
  const accessToken  = await SecureStore.getItemAsync('accessToken');
  const refreshToken = await SecureStore.getItemAsync('refreshToken');
  const profileJson  = await SecureStore.getItemAsync('caregiverProfile');
  if (accessToken && refreshToken) {
    const profile = profileJson ? JSON.parse(profileJson) : {};
    set({ accessToken, refreshToken, isAuthenticated: true, ...profile });
  }
},

clearAuth: async () => {
  await SecureStore.deleteItemAsync('accessToken');
  await SecureStore.deleteItemAsync('refreshToken');
  await SecureStore.deleteItemAsync('caregiverProfile');
  set({ ... });
},
```

Option B is recommended — it eliminates the blank-greeting flash without introducing any security risk (name/agency are not PHI).

---

### m4 — `VisitScreen` test `beforeEach` doesn't seed `activeClientId` and `gpsStatus`

**File:** `src/__tests__/screens/VisitScreen.test.tsx`

```ts
beforeEach(() => {
  setupMocks();
  useVisitStore.setState({
    activeVisitId: MOCK_VISIT_ID,
    activeShiftId: MOCK_SHIFT_ID_1,
    activeClientName: 'Eleanor Vance',
    clockInTime: new Date().toISOString(),
    // ← activeClientId missing (needed for CarePlanSection collapse key)
    // ← gpsStatus missing (GpsStatusBar renders 'UNAVAILABLE' fallback)
  });
});
```

The `VisitScreen` implementation reads `activeClientId` from the store and passes it to `CarePlanSection`. Without a seeded `activeClientId`, the AsyncStorage key is `careplan_expanded_` (empty string) — which still works functionally but is misleading. The `GpsStatusBar` will always show `UNAVAILABLE` in tests because `gpsStatus` is never set, preventing any test from verifying the OK or OFFLINE paths.

**Fix:** Seed both fields in `beforeEach`:

```ts
useVisitStore.setState({
  activeVisitId:    MOCK_VISIT_ID,
  activeShiftId:    MOCK_SHIFT_ID_1,
  activeClientId:   'cl-001',          // matches mockTodayShifts[0].clientId
  activeClientName: 'Eleanor Vance',
  clockInTime:      new Date().toISOString(),
  gpsStatus:        'OK',
  activeVisitNotes: null,
});
```

---

### m5 — Task 7 has duplicate "Step 2" label

**File:** `docs/superpowers/plans/2026-04-08-mobile-app.md` (Task 7)

Task 7 labels two steps as "Step 2": one for `WelcomeScreen.tsx` and one for `NotificationsScreen.tsx`. The `LocationScreen.tsx` step is labeled "Step 3". The correct sequence should be Step 1 (ProgressDots), Step 2 (WelcomeScreen), Step 3 (NotificationsScreen), Step 4 (LocationScreen), Step 5 (Commit).

**Fix:** Renumber `NotificationsScreen` step from "Step 2" to "Step 3", `LocationScreen` from "Step 3" to "Step 4", and the Commit from "Step 4" to "Step 5".

---

## 5. Questions for Clarification

1. **`authStore.rehydrate` — Option A or B?** Option B (persist profile fields) is the recommended fix for m3. Confirm preference before Task 4 implementation begins, since it adds 3 SecureStore keys and changes the `clearAuth` cleanup logic.

2. **`ClockInSheet` auto-selection UX:** The C3 fix pre-selects `upcoming[0]` when data loads. If the user taps a different shift before data arrives (unlikely at 350ms delay), the `useEffect` won't override it (because `selectedId !== null`). Confirm this is the intended behavior, or whether the first shift should always be forced to selected on data load.

3. **`useOfflineSync` — conflicts array persistence:** Once a `CONFLICT_REASSIGNED` is surfaced in the `conflicts` state, it lives only in React component memory. If the user navigates away from TodayScreen (or the app is backgrounded), the conflict banner disappears. Should conflicts be persisted to AsyncStorage or SQLite so they survive navigation? The current design means a caregiver who taps away from Today loses the conflict warning.

---

## 6. Final Recommendation

**Approve with changes.**

Two deterministic test failures (C1, C2) will block CI on the first run and must be fixed before execution begins. The ClockInSheet race condition (C3) produces a silent UX regression that won't be caught by the existing test (which only asserts post-tap behavior). These three fixes are small and localized. The minor issues (m1–m5) are lower priority but m3 (rehydrate) affects first-impression UX on every cold restart.

Recommended order of fixes:
1. Fix C1 (SettingsScreen tests) — 5 minutes
2. Fix C2 (MessageThreadScreen test subject) — 5 minutes
3. Fix C3 (ClockInSheet useEffect) — 10 minutes
4. Fix m3 (authStore rehydrate) — 15 minutes, clarify Option A vs B first
5. Fix m1, m2, m4, m5 — opportunistically during the relevant tasks

---

*Review written: 2026-04-08*
