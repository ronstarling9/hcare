# Critical Review: Family Portal Plan 3 — Frontend
**Review date:** 2026-04-08  
**Reviewer:** superpowers:critical-implementation-review v1.5.1  
**Plan file:** `2026-04-08-add-family-portal-plan-3-frontend.md`  
**Prior reviews:** None

---

## 1. Overall Assessment

The plan is thorough in scope and coverage: all six verify-page states, correct timezone-aware time display, an honest remove-confirmation copy, and TDD structure throughout. The separation of `portalClient` from the admin `apiClient` is architecturally sound. However, several issues make it **not yet ready for implementation as-written**. The most serious are a broken state-management pattern in `PortalVerifyPage` (mixing a ref with `useState` in a non-functional way, including an invalid React import placement), a hardcoded timezone in `StatusPill.COMPLETED` that bypasses the `agencyTimezone` prop, a `meta.onError` pattern that does not fire in React Query v5, and missing touch-target compliance on several buttons. These are concrete defects that will cause test failures or incorrect behavior in production.

---

## 2. Critical Issues

### C1. `PortalVerifyPage` state management is broken — `useReactState` import at bottom of file, and stale-closure anti-pattern

**Location:** Task 17, Step 3 — `PortalVerifyPage.tsx`

**Problem:** The plan writes:
```ts
import { useState as useReactState } from 'react'
```
**at the bottom of the file**, after the JSX. JavaScript `import` declarations are hoisted by the module bundler but ESLint/TypeScript will error on this because ESLint enforces `import` statements at the top of the file (Airbnb config rule `import/first`). This alone will cause `npm run lint` to fail and TypeScript compilation will likely succeed but the intent is clearly a mistake.

More critically, the entire `verifyState`/`displayState` dual-state pattern is convoluted and fragile. The plan declares:
```ts
const [verifyState, setVerifyState] = [
  stateRef.current ?? initialState,
  (s: VerifyState) => { stateRef.current = s },
]
```
This is not a React hook — `setVerifyState` only updates a ref, not state, so **the component will never re-render** after the `verifyPortalToken` promise rejects and calls `setVerifyState('token_invalid')`. The `displayState` useEffect that reads `stateRef.current` will only re-run if something else triggers a render.

**Why it matters:** The core "show link expired on 400" test (PortalVerifyPage test case 3) will fail because the component never re-renders after the API call rejects. This is a runtime defect, not just a lint issue.

**Fix:** Remove the ref pattern entirely. Use a single `useState<VerifyState>` initialized to `initialState`, move all imports to the top of the file, and call `setState` directly inside the `.catch()`:
```ts
import { useState, useEffect } from 'react'
// ...
const [displayState, setDisplayState] = useState<VerifyState>(initialState)

useEffect(() => {
  if (!token) return
  verifyPortalToken(token)
    .then((res) => {
      login(res.jwt, res.clientId, res.agencyId)
      navigate('/portal/dashboard', { replace: true })
    })
    .catch(() => {
      setDisplayState('token_invalid')
    })
}, [token]) // eslint-disable-line react-hooks/exhaustive-deps
```

---

### C2. Hardcoded `'America/New_York'` in `StatusPill` for COMPLETED state — ignores `agencyTimezone` prop

**Location:** Task 18, Step 3 — `StatusPill` component, `COMPLETED` branch:
```ts
const completedTime = visit.clockedOutAt
  ? formatTime(visit.clockedOutAt, 'America/New_York')  // <-- BUG
  : scheduledTime
```

**Why it matters:** `tz` is passed as a prop to `TodayVisitCard` and down to `StatusPill`, but the `COMPLETED` branch ignores it and hardcodes Eastern time. Agencies in other timezones (the backend defaults to `America/New_York` for existing rows but this is explicitly configurable per agency per Plan 1) will display the wrong clock-out time for family members. The Plan 1 migration comment even notes the timezone is a per-agency setting.

**Fix:** Pass `tz` to `StatusPill` and use it:
```ts
function StatusPill({ visit, scheduledTime, late, t, tz }: { ...; tz: string }) {
  // ...
  const completedTime = visit.clockedOutAt ? formatTime(visit.clockedOutAt, tz) : scheduledTime
```

---

### C3. `meta.onError` does not fire in React Query v5 — 403 redirect for `access_revoked` is silently broken

**Location:** Task 15, Step 2 — `usePortalDashboard.ts`:
```ts
meta: {
  onError: (error: unknown) => { ... }
}
```

**Why it matters:** In React Query v5 (TanStack Query v5), the `meta.onError` callback was removed. The `onError` option at the `useQuery` call level was also deprecated and removed in v5. To run side effects on query error in v5, you must use a global `QueryCache` error handler, or inspect `isError`/`error` in the component. As-written, the 403 → `access_revoked` redirect will **never execute**, meaning a family member whose access was revoked will see a generic error state rather than the informative revocation screen. There is no test for this exact flow in the plan's test suite either (the test that checks 403 handling is absent from `PortalDashboardPage.test.tsx`).

**Fix:** Move the 403 check into the component, alongside the existing 410 check:
```ts
const status403 = (error as { response?: { status?: number } })?.response?.status === 403
if (status403) {
  logout()
  navigate('/portal/verify?reason=access_revoked', { replace: true })
}
```
Alternatively, configure the error handler in a `QueryCache` callback passed to the `QueryClient` constructor. Also add a test case to `PortalDashboardPage.test.tsx` for the 403 scenario.

---

### C4. `isLate` appends `'Z'` to `scheduledStart` unconditionally — double-`Z` bug when backend sends UTC ISO-8601 strings

**Location:** Task 18, Step 3 — `isLate` function:
```ts
const scheduled = new Date(visit.scheduledStart + 'Z')
```
And in `formatTime`/`formatDate`:
```ts
const d = new Date(utcIso + (utcIso.includes('Z') ? '' : 'Z'))
```

**Why it matters:** `formatTime` and `formatDate` already guard against double-`Z` with the ternary. But `isLate` does not — it blindly appends `'Z'`. Plan 2 documents that all `PortalDashboardResponse` timestamps are "UTC ISO-8601" strings. If the backend serializes them with a trailing `Z` (standard Jackson behavior for `Instant` / `OffsetDateTime`), `visit.scheduledStart + 'Z'` becomes `"2026-04-08T09:00:00ZZ"`, which `new Date()` parses as `Invalid Date`. `Date.now() > NaN` is always `false`, so no visit would ever be detected as late.

**Fix:** Apply the same guard used in `formatTime`:
```ts
const scheduled = new Date(visit.scheduledStart + (visit.scheduledStart.includes('Z') ? '' : 'Z'))
```

---

### C5. `portalClient` 401 response has no interceptor — expired FAMILY_PORTAL JWTs silently fail instead of redirecting

**Location:** Task 15, Step 1 — `portal.ts`:

The admin `apiClient` in `client.ts` has a response interceptor that catches 401 and redirects to `/login`. The new `portalClient` has only a request interceptor. If the FAMILY_PORTAL JWT expires between the `PortalGuard` check and the API call (race condition), or if the token's `exp` is correct but the backend rejects it for another reason, `getPortalDashboard()` will throw an unhandled Axios error with status 401. `usePortalDashboard` sets `retry: 2`, so it will silently retry twice and eventually show the generic `isError` "Unable to load" state — rather than the `session_expired` state.

**Why it matters:** Family members will see a confusing "Unable to load" error rather than "Session expired — ask your coordinator for a new link", degrading the UX on a path that will happen regularly (72-hour token + sessions lasting multiple days).

**Fix:** Add a response interceptor to `portalClient` that catches 401 and redirects:
```ts
portalClient.interceptors.response.use(
  (r) => r,
  (error) => {
    if (error.response?.status === 401) {
      usePortalAuthStore.getState().logout()
      window.location.href = '/portal/verify?reason=session_expired'
    }
    return Promise.reject(error)
  },
)
```
Note: `usePortalAuthStore.getState()` is safe to call outside React here, matching the pattern in `client.ts`.

---

### C6. Touch targets below 44px on multiple `FamilyPortalTab` buttons

**Location:** Task 19, Step 4 — `FamilyPortalTab.tsx`:

The "Send New Link" and "Remove" buttons in the user list use:
```tsx
className="text-[11px] text-text-secondary border border-border px-2 py-1"
```
`py-1` = 4px padding × 2 = 8px padding + ~16px line-height for `text-[11px]` ≈ 24px total height. This is well below the 44px minimum touch target required by WCAG 2.5.5 and the project's own accessibility convention (confirmed by the Plan itself noting `min-h-[44px]` on the sign-out button).

The `+ Invite` / `Generate Link` buttons use `py-1.5` (6px × 2 = 12px padding + font = ~28px), also below 44px. The Cancel and Done buttons in the invite form also use `py-1.5`.

**Fix:** Apply `min-h-[44px] flex items-center` to all interactive buttons in the tab, as done on the sign-out button in `PortalLayout`.

---

## 3. Previously Addressed Items

No prior reviews exist for this plan.

---

## 4. Minor Issues & Improvements

### M1. `usePortalDashboard` query key is not scoped to `clientId` — stale data across clients in the same session

The query key is `['portal-dashboard']`. The portal is single-client per session (the JWT encodes a specific `clientId`), so this is not a functional bug. However, if a family member navigates back and then re-verifies with a different token (a different sibling's access link), React Query will serve the previous client's cached data until the 60-second `gcTime` expires. Scoping to the token or clientId (`['portal-dashboard', clientId]`) eliminates this edge case at no cost.

### M2. `upcomingVisits` is not capped at 3 in the component — test expects cap but implementation does not enforce it

The plan's test ("shows upcoming visits capped at 3") provides exactly 3 items and asserts `screen.getAllByText(/Maria/).length === 3`. This test passes vacuously because the fixture only has 3 items. The `PortalDashboardPage` implementation renders `upcomingVisits.map(...)` with no `.slice(0, 3)`. If the backend returns more than 3 (Plan 2 does not document a limit), all would be rendered. Either add `.slice(0, 3)` in the component, or add a test with 5 items asserting only 3 are shown.

### M3. `FamilyPortalTab` — "Cancel" and "Done" buttons are hardcoded English strings, not translated

In `FamilyPortalTab.tsx`, several strings bypass `t()`:
- `Cancel` (invite form close button, line ~1638)
- `Done` (post-generation close button, line ~1656)
- `Remove` (user list remove button, line ~1700 — uses hardcoded string rather than `t('removeConfirm')`)

The `common.json` locale file contains a `cancel` key used across the codebase. These should use `t('cancel', { ns: 'common' })` or add keys to `portal.json`.

### M4. `PortalDashboardPage` re-declares `navigate` and `logout` but they are already used in `PortalLayout` and `PortalGuard`

The `PortalDashboardPage` calls `useNavigate()` and reads `usePortalAuthStore` for `logout`, but the sign-out button is in `PortalLayout` (which already handles sign-out). The `navigate`/`logout` variables in `PortalDashboardPage` are declared but never used in the implementation as written. This will produce TypeScript/lint warnings (`no-unused-vars`). Remove them from `PortalDashboardPage`.

### M5. `formatExpiry` in `FamilyPortalTab` uses browser-local timezone, not agency timezone

`formatExpiry` formats the `expiresAt` using `new Date(iso).toLocaleTimeString()` without specifying a `timeZone`. This renders the expiry time in the admin's local browser timezone, not the agency's timezone. An admin in PST viewing a link that expires at 17:34 UTC will see "9:34 AM" while the family member (also PST) would see the same, which is correct but coincidental. An agency admin traveling across timezones will see confusing values. Since `agencyTimezone` is not available in `FamilyPortalTab` (it is not part of the client detail props), this is a known limitation, but it should at least be noted in a comment. Alternatively, format as UTC explicitly.

### M6. `PortalVerifyPage` test uses `vi.mock('../api/portal')` but the mock is a module-level auto-mock

The test file uses `vi.mock('../api/portal')` and then `vi.mocked(portalApi.verifyPortalToken)`. Since `verifyPortalToken` is defined in the same file as `portalClient` (a module-level Axios instance), the auto-mock will mock the entire module including `portalClient`. This is correct behavior, but the `portalClient` interceptor that reads `usePortalAuthStore.getState()` during module initialization could cause issues if `portalAuthStore` is not reset between tests — the `beforeEach(() => usePortalAuthStore.getState().logout())` call handles state reset but the interceptor registration happens once at module load time, which is fine. No change needed, but worth a comment.

### M7. `isLate` test comment has an error — `twentyMinAgo` is actually 90 minutes in the future

In `PortalDashboardPage.test.tsx`, the variable is named `twentyMinAgo` but computed as:
```ts
const twentyMinAgo = new Date(now.getTime() + 90 * 60 * 1000).toISOString()...
```
This is actually `scheduledEnd` set 90 minutes in the future (which is correct — the visit hasn't ended yet). The variable name is confusing. Rename to `ninetyMinFromNow` or `futureEnd` for clarity.

### M8. `PortalDashboardPage` does not have a `<h1>` on the loading state — accessibility gap

The loading state renders:
```tsx
<div className="p-6 text-center text-[13px] text-text-secondary">Loading…</div>
```
The loading text is not inside a landmark and has no `aria-live` or `role="status"`. Screen reader users navigating to the dashboard will hear nothing until data loads. Add `role="status"` and `aria-live="polite"` to the loading div, matching the pattern already used in `PortalVerifyPage`.

### M9. `listFamilyPortalUsers` is added to `clients.ts` but `PageResponse<T>` type is used without import documentation

The plan shows `PageResponse<FamilyPortalUserResponse>` in `clients.ts` but does not note where `PageResponse<T>` is defined. If it is already exported from `clients.ts` or a shared types file this is fine, but the implementer should verify rather than assume.

### M10. `portalAuthStore` `logout` does not clear `localStorage` key on explicit call — plan comment is incorrect

The plan states: "`usePortalAuthStore.getState().logout()` clears state AND removes `portal-auth` from `localStorage` automatically (Zustand `persist` handles this)." This is partially incorrect. Zustand `persist` does not remove the localStorage key when you call `set({ token: null, ... })` — it writes the null values back to localStorage. The key `portal-auth` remains in localStorage with `{"state":{"token":null,"clientId":null,"agencyId":null}}`. This is functionally equivalent to "logged out" because the guard checks `token === null`, but the key is not truly removed. If the implementer expects `localStorage.getItem('portal-auth')` to return `null` after logout (e.g., in tests), it will not. The plan's comment should be corrected to avoid confusion: "the persist middleware writes `null` values back — the key remains but token is null."

---

## 5. Questions for Clarification

**Q1.** Plan 2 documents that `TodayVisitDto.scheduledStart`/`scheduledEnd`/`clockedInAt`/`clockedOutAt` are "UTC ISO-8601" strings. Will the backend Jackson serializer emit trailing `Z` (e.g., `"2026-04-08T09:00:00Z"`) or not (e.g., `"2026-04-08T09:00:00"`)? The `formatTime`/`formatDate` guards exist but `isLate` does not guard (see C4). Clarifying the exact format would allow removing all the `includes('Z')` guards in favor of a single well-known format.

**Q2.** The plan caps `upcomingVisits` display at 3 (per the test name) but the `PortalDashboardResponse` has no documented server-side limit and the component does not slice. Should the backend limit to 3, or should the frontend slice? Currently neither does.

**Q3.** The plan mentions `listFamilyPortalUsers` calls `GET /clients/{clientId}/family-portal-users`. Plan 2's File Map does not add a `GET` list endpoint to `ClientController` — only `POST .../invite` and `DELETE .../family-portal-users/{id}` are documented. Is a list endpoint expected to be added in Plan 2, or was it omitted?

**Q4.** `PortalDashboardPage` declares `navigate` and `logout` but the sign-out action is handled by `PortalLayout`. Is `PortalDashboardPage` meant to have its own sign-out affordance (separate from the layout header), or are these unused variables that should be removed?

---

## 6. Final Recommendation

**Major revisions needed.**

Five of the six critical issues (C1–C5) involve defects that will cause either test failures or incorrect runtime behavior: the broken re-render pattern in `PortalVerifyPage` (C1), the hardcoded timezone in `StatusPill` (C2), the non-functional `meta.onError` in React Query v5 (C3), the double-`Z` bug in `isLate` (C4), and the missing 401 interceptor on `portalClient` (C5). C6 (touch targets) is an accessibility compliance violation. The plan cannot be implemented as-written and produce a passing test suite and production-quality component. Address the critical issues and the minor issues in M3 (hardcoded strings) and M4 (unused variables) before implementation begins.
