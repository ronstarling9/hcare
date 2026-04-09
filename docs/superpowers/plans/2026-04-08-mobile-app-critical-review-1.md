# hcare Mobile App — Critical Implementation Review 1

**Plan reviewed:** `docs/superpowers/plans/2026-04-08-mobile-app.md`
**Date:** 2026-04-08
**Previous reviews:** None (first review)

---

## 1. Overall Assessment

The plan is architecturally sound and impressively complete — all 19 tasks have real code, no placeholders, and the task breakdown follows the spec faithfully. The mock-toggle pattern via `EXPO_PUBLIC_USE_MOCKS` is well-designed. However, there are six issues that will block the app from functioning correctly: an offline clock-in will silently fail with no event persisted (the most critical flaw), a dependency is missing that causes an import crash, the bottom sheet navigation is wired incorrectly, a navigation call happens during the render phase, two more packages are used but never installed, and the SQLite sync deduplication logic is incorrectly keyed. Several medium-severity issues — hardcoded GPS status, wrong `clientId` key for collapse preferences, notes not preserved on re-navigation — will surface immediately during smoke testing.

---

## 2. Critical Issues

### C1. `useClockIn.ts` doesn't write to SQLite before the API call — offline clock-in silently fails

**Where:** Task 9, `src/hooks/useClockIn.ts`

**Problem:** The offline-first contract requires writing to SQLite _before_ calling the BFF so that events survive if the network call fails. `useClockIn.ts` calls `visitsApi.clockIn()` directly with no SQLite write at all. When `capturedOffline=true`, the API call will throw a network error (no connectivity), the event is never persisted, and `setActiveVisit` is never called. The caregiver clocks in, sees a spinner, and nothing happens. `useVisit.ts` correctly uses SQLite-first for every other mutation — this is the only place that omits it.

**Fix:** In `useClockIn.ts`, insert the CLOCK_IN event to SQLite first, then call the API only if online:

```ts
mutationFn: async ({ shiftId, clientName }: { shiftId: string; clientName: string }) => {
  const net = await NetInfo.fetch();
  const capturedOffline = !net.isConnected;

  let gpsCoordinate: GpsCoordinate | undefined;
  try {
    const loc = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced });
    gpsCoordinate = { lat: loc.coords.latitude, lng: loc.coords.longitude };
  } catch { /* GPS unavailable */ }

  // Write to SQLite BEFORE calling BFF (offline-first)
  const occurredAt = new Date().toISOString();
  await insertEvent({ type: 'CLOCK_IN', visitId: shiftId, gpsCoordinate, capturedOffline, occurredAt });

  // Need a visitId to set active visit — for offline case use shiftId as a local placeholder
  if (capturedOffline) {
    setActiveVisit(`local-${shiftId}`, shiftId, clientName, occurredAt);
    return { visitId: `local-${shiftId}`, clockInTime: occurredAt };
  }

  const res = await visitsApi.clockIn(shiftId, { gpsCoordinate, capturedOffline });
  // Update the SQLite record with the real visitId from BFF
  setActiveVisit(res.visitId, shiftId, clientName, res.clockInTime);
  return res;
},
```

Note: This exposes a design gap — offline clock-in has no real `visitId` from the BFF. The plan needs to decide whether to use a local UUID or the shiftId as a placeholder, and how to reconcile it during sync. This needs a task-level note so the implementer thinks through the offline visitId mapping.

---

### C2. `@react-native-async-storage/async-storage` never installed — app crashes at `CarePlanSection`

**Where:** Task 1 (install list), Task 11 (`src/screens/visit/CarePlanSection.tsx`)

**Problem:** `CarePlanSection.tsx` imports:
```ts
import AsyncStorage from '@react-native-async-storage/async-storage';
```
This package is not in the Task 1 install step. React Native's built-in `AsyncStorage` was removed in RN 0.60+. The app will crash with a module-not-found error the first time a visit screen opens.

**Fix:** Add to the Task 1 install command:
```bash
npm install @react-native-async-storage/async-storage
```

---

### C3. `ClockInSheet` is wired as both a tab _and_ a modal — the bottom sheet UX is broken

**Where:** Task 5, `src/navigation/AppNavigator.tsx`

**Problem:** `AppNavigator.tsx` registers `ClockInSheet` in two places:
```tsx
// As a tab (always visible behind bottom tab bar):
<Tab.Screen name="ClockIn" component={ClockInSheet} />

// As a modal overlay:
<Stack.Screen name="ClockInSheet" component={ClockInSheet}
              options={{ presentation: 'modal', headerShown: false }} />
```
The custom `TabBar` fires `navigation.navigate(route.name)` for the center FAB, which switches to the `ClockIn` _tab_ — rendering the sheet as a full-screen page instead of as a floating modal over Today. The user experience will be a full-screen transition, not the bottom-sheet slide-up described in the spec.

**Fix:** The center FAB should navigate to the modal stack screen, not the tab. In `TabBar.tsx`, when `route.name === 'ClockIn'`, call the parent stack navigator:

```tsx
if (isCenter) {
  return (
    <TouchableOpacity
      key={route.key}
      style={styles.fabContainer}
      onPress={() => {
        if (isVisitActive) {
          // Navigate back to the active visit
          navigation.navigate('Visit' as never, { visitId: activeVisitId } as never);
        } else {
          // Present the clock-in sheet as a modal from the root stack
          (navigation as any).navigate('ClockInSheet');
        }
      }}
      ...
    >
```

The `ClockIn` tab entry in `TabNavigator` should be a non-renderable placeholder (or removed, with the FAB's function handled entirely through the modal).

---

### C4. `DeepLinkHandlerScreen` calls `navigation.replace()` during render — React error

**Where:** Task 6, `src/screens/auth/DeepLinkHandlerScreen.tsx`

**Problem:**
```tsx
if (error) {
  navigation.replace('LinkExpired');  // ← synchronous call during render
  return null;
}
```
Calling a navigation side effect in the component body (outside `useEffect`) during the render phase will produce a React error ("Cannot update a component (`NavigationContainer`) while rendering a different component"). This will also double-render issues depending on timing.

**Fix:** Move into the existing `useEffect`:
```tsx
useEffect(() => {
  const token = route?.params?.token as string | undefined;
  if (!token) {
    navigation.replace('LinkExpired');
    return;
  }

  exchangeToken(token)
    .catch(() => navigation.replace('LinkExpired'));
}, []);
```
Remove the early-return `if (error)` block entirely. Use the loading state as the default render.

---

### C5. `expo-device` used in `useOfflineSync.ts` but never installed

**Where:** Task 1 (install list), Task 16 (`src/hooks/useOfflineSync.ts`)

**Problem:**
```ts
import * as Device from 'expo-device';
...
const deviceId = Device.modelId ?? 'unknown';
```
`expo-device` is not in the Task 1 package install. The module will not be found at runtime.

**Fix:** Add to Task 1:
```bash
npx expo install expo-device
```
Or use a stable UUID stored in SecureStore as the device ID (more reliable across resets than `Device.modelId`, which can be null on simulators).

---

### C6. `useOfflineSync` deduplication marks all events for a `visitId` synced even when only some succeeded

**Where:** Task 16, `src/hooks/useOfflineSync.ts`

**Problem:**
```ts
const synced = res.results.filter(r => r.result === 'OK' || r.result === 'DUPLICATE');
await markSynced(
  events
    .filter(e => synced.some(s => s.visitId === e.visitId))  // ← wrong: checks visitId only
    .map(e => e.rowId)
);
```
A visit has multiple events (CLOCK_IN, TASK_COMPLETE × N, NOTE_SAVE, CLOCK_OUT). The BFF returns one result per visit (not per event). If a visit's sync result is OK, all events for that visit should be marked synced. If it's CONFLICT_REASSIGNED, none should be. The current code is actually _correct_ for this case — it marks all events for a visitId synced when the result is OK.

However, the issue is that the BFF spec (endpoint table, note 1) says the BFF deduplicates by `(deviceId, visitId, eventType, occurredAt)`, implying events are individual records. But the `SyncEventResult` only has `visitId` — no `eventType`. The mismatch between "per-event deduplication in BFF" and "per-visit result returned to client" needs clarification. Currently, if the BFF does per-event dedup, the client has no way to know which specific event was a DUPLICATE vs OK. Mark this as a design gap requiring BFF team coordination.

Additionally: `markSynced` is called with `rowId`s from the local events, but the `rowId` property is returned as `(SyncEvent & { rowId: number })[]` yet the test uses `(e as any).rowId`. The type is declared correctly but the `as any` cast in the test suggests the type narrowing wasn't working during plan authoring — verify that the exported type is being inferred correctly.

---

## 3. Previously Addressed Items

No previous reviews exist. This is the first review.

---

## 4. Medium Issues

### M1. `GpsStatusBar` in `VisitScreen` is hardcoded to `status="OK"` — real GPS state never shown

**Where:** Task 11, `src/screens/visit/VisitScreen.tsx`

```tsx
<GpsStatusBar status="OK" distance={120} />
```

The component has full support for `OUTSIDE_RANGE`, `OFFLINE`, and `UNAVAILABLE` states, but VisitScreen hardcodes `OK`. The amber warning states the spec requires will never appear. The GPS status should be derived from the clock-in response (whether GPS was captured) and current connectivity. Add a `gpsStatus` state variable derived from `useClockIn`'s result and the `isOnline` state from `useOfflineSync`.

---

### M2. `CarePlanSection` uses `carePlan.id` as the collapse preference key — should be `clientId`

**Where:** Task 11, `src/screens/visit/VisitScreen.tsx`

```tsx
<CarePlanSection carePlan={carePlan} clientId={carePlan.id} />
```

The `clientId` prop keys the AsyncStorage preference (`careplan_expanded_${clientId}`). Using `carePlan.id` means a new care plan version (new care plan ID) resets the stored collapse preference for that client — which is precisely the wrong behavior. The preference should persist per _client_, not per care plan. Pass the shift's `clientId` instead:

```tsx
// Need clientId from shift data — thread it through useVisit or VisitScreen's route params
<CarePlanSection carePlan={carePlan} clientId={activeShiftClientId} />
```

The `clientId` should be available from the active shift data. Consider adding it to `visitStore` alongside `activeShiftId`.

---

### M3. `CareNotes` doesn't accept an initial value — notes are blank after re-navigation

**Where:** Task 11, `src/screens/visit/CareNotes.tsx`

```tsx
const [text, setText] = useState('');
```

If the caregiver navigates to Care Plan and back, the notes field resets to empty. The component should accept an `initialValue?: string` prop and initialize state from it. Pass the previously-saved notes text from the visit store or care plan context.

---

### M4. `useAuth.ts` spreads all store state — re-renders on any store change; exposes raw store actions

**Where:** Task 4, `src/hooks/useAuth.ts`

```ts
const store = useAuthStore();  // subscribes to entire store — re-renders on any field change
return { exchangeToken, sendLink, logout, ...store };
```

Spreading `...store` exposes raw `setAuth`, `clearAuth`, and `rehydrate` functions directly to consumers, bypassing the hook's wrapper logic. It also subscribes to all state changes. Use selectors instead:

```ts
const setAuth = useAuthStore(s => s.setAuth);
const clearAuth = useAuthStore(s => s.clearAuth);
const isAuthenticated = useAuthStore(s => s.isAuthenticated);
// etc.
return { exchangeToken, sendLink, logout, isAuthenticated, ... };
```

---

### M5. Circular dependency: `client.ts` → `handlers.ts` → `client.ts`

**Where:** Task 3

`client.ts` dynamically imports `handlers.ts`. `handlers.ts` statically imports `apiClient` from `client.ts`. While dynamic imports typically avoid circular dependency issues (the static export is resolved first), if `setupMocks()` is called before `apiClient` finishes its module evaluation, `apiClient` could be `undefined`. The safer pattern:

```ts
// handlers.ts — accept apiClient as a parameter instead of importing it
export function setupMocks(apiClient: AxiosInstance) {
  const mock = new MockAdapter(apiClient, { delayResponse: 350 });
  ...
}
```
```ts
// client.ts
if (process.env.EXPO_PUBLIC_USE_MOCKS === 'true') {
  import('../mocks/handlers').then(({ setupMocks }) => setupMocks(apiClient));
}
```

---

### M6. Maps URL hardcoded to Apple Maps — silently fails on Android

**Where:** Task 8, `src/screens/today/TodayScreen.tsx`

```ts
Linking.openURL(`https://maps.apple.com/?q=${encoded}`);
```

Apple Maps URLs work on iOS but are ignored or fail on Android. Fix:

```ts
import { Platform } from 'react-native';
const mapsUrl = Platform.OS === 'ios'
  ? `http://maps.apple.com/?q=${encoded}`
  : `https://maps.google.com/?q=${encoded}`;
Linking.openURL(mapsUrl);
```

---

### M7. `require('react-native')` inside `useNotifications.ts` — use import

**Where:** Task 17, `src/hooks/useNotifications.ts`

```ts
const platform = require('react-native').Platform.OS as 'ios' | 'android';
```

CommonJS `require()` inside an ESM TypeScript file is a code smell and can confuse bundlers. Replace with:
```ts
import { Platform } from 'react-native';
// ...
const platform = Platform.OS as 'ios' | 'android';
```

---

### M8. Missing tests for five screens listed in the file structure

**Where:** File structure (`__tests__/screens/`)

The following screens are listed in the structure but have no test task:
- `DeepLinkHandlerScreen` (Task 6)
- `ConflictDetailScreen` (Task 18)
- `SettingsScreen` (Task 15)
- `MessageThreadScreen` (Task 14)
- `CarePlanScreen` (Task 12)

Task 12 (CarePlanScreen) explicitly has no test step at all. A plan claiming 80% coverage omits tests for 5 of ~12 screens.

---

### M9. `SectionLabel.tsx` defined in file structure but never implemented

**Where:** File structure, `src/components/SectionLabel.tsx`

The file structure declares this as a "reusable uppercase section header" but no task creates it — all screens use `[Typography.sectionLabel, ...]` spreads inline. Either implement it in Task 16 alongside the other shared components, or remove it from the file structure.

---

## 5. Minor Issues

**m1. `VisitScreen.tsx` — `route.params.shiftId` is dead code**
The visit is navigated to with `{ visitId: res.visitId }`, not `shiftId`. The fallback `route?.params?.shiftId ?? ''` will always be `undefined` (the param key doesn't match). The shiftId comes from `activeShiftId` in the store, which is correct. Remove the dead fallback or add the correct param.

**m2. `VisitScreen.tsx` — visit store not persisted; app crash mid-visit loses context**
`visitStore` is Zustand in-memory only. If the app crashes mid-visit and restarts, `activeVisitId` and `activeShiftId` are null. The Visit screen would be inaccessible and the caregiver can't resume. Consider persisting `visitStore` to SecureStore or at minimum document this limitation.

**m3. `jest.config.js` — verify `setupFilesAfterFramework` is the correct Jest option**
The plan uses `setupFilesAfterFramework`. Confirm with the running Jest version that this is the correct key (renamed from `setupTestFrameworkScriptFile` in Jest 24). If incorrect, `@testing-library/jest-native/extend-expect` matchers won't load and all tests using `.toBeDisabled()` etc. will fail with cryptic errors.

**m4. `useOfflineSync.ts` — stale `syncMutation` closure in NetInfo listener**
The `useEffect` captures `syncMutation` at mount time with an empty dependency array. If React Query's `useMutation` updates the mutation reference, the listener holds a stale one. Use a `useRef` to hold the latest mutation reference and call it via the ref in the listener.

**m5. `ProgressDots` component defined 3 times (once per onboarding screen)**
`WelcomeScreen`, `NotificationsScreen`, and `LocationScreen` each define an identical `ProgressDots` function locally. Extract it to `src/components/ProgressDots.tsx` to follow DRY. (Low priority given the mock-first goal, but creates maintenance debt.)

---

## 6. Questions for Clarification

**Q1.** For offline clock-in (C1): what `visitId` should be used to key the SQLite event and the visit store before the BFF confirms the clock-in? The BFF assigns `visitId` — if offline, the client has no real `visitId`. Using the `shiftId` as a temporary key is one option, but the BFF sync batch uses `(deviceId, visitId, eventType, occurredAt)` for dedup. Needs clarification before the offline clock-in path can be correctly implemented.

**Q2.** For the BFF sync deduplication (C6): the BFF spec says it deduplicates by `(deviceId, visitId, eventType, occurredAt)` but `SyncEventResult` only has `visitId`. Does the BFF return one result per visit or per event? The client-side dedup logic depends on this.

**Q3.** Is `visitStore` intended to survive app restarts? If so, it needs SecureStore persistence similar to `authStore`. If not, the handling for a mid-visit app crash/restart is undefined.

---

## 7. Final Recommendation

**Approve with changes.**

The plan is well-structured and the code is genuinely complete and readable. Fix the six critical issues before handing to an implementer — especially C1 (offline clock-in data loss), C2 (crash on care plan open), and C3 (broken bottom sheet). The medium issues (M1–M9) can be addressed by the implementer as they work through tasks if the critical issues are resolved first. C4 and C5 are one-line fixes that should be incorporated into the plan immediately.

---

*Review written: 2026-04-08*
