# Critical Review: Family Portal Plan 3 — Frontend (Review 3)
**Review date:** 2026-04-08
**Reviewer:** superpowers:critical-implementation-review v1.5.1
**Plan file:** `2026-04-08-add-family-portal-plan-3-frontend.md`
**Prior reviews:** Review 1 (`…-critical-review-1.md`), Review 2 (`…-critical-review-2.md`)

---

## 1. Overall Assessment

After two rounds of revision the plan has converged to a near-implementation-ready state. The one remaining critical issue from review 2 — the `atob` base64url bug in `PortalGuard.isJwtExpired` — is now correctly fixed in the plan text (lines 436–440 of the implementation). No new critical issues were found in this pass. However, seven minor issues from review 2 remain unaddressed in the current plan text. The most impactful of these are: two buttons still missing `min-h-[44px]` touch targets (the Copy button at line 1756 and the confirm-Remove button at line 1822), the unguarded `navigator.clipboard.writeText` call, and the missing `enabled: !!clientId` guard in `usePortalDashboard`. These should be fixed before implementation begins. The remaining items (eslint-disable scope, narrow `t` prop type, `formatExpiry` timezone, confusing variable name) are lower-risk polish items.

---

## 2. Critical Issues

None. The base64url `atob` fix (C1 from review 2) is correctly present in the current plan text.

---

## 3. Previously Addressed Items

The following issues from the entire review history are resolved in the current plan:

**From review 1:**
- C1: `PortalVerifyPage` broken ref/state pattern — single `useState<VerifyState>`, imports at top.
- C2: Hardcoded `'America/New_York'` in `StatusPill` COMPLETED — `tz` prop threaded and used correctly.
- C3: `meta.onError` removed in React Query v5 — replaced with `useEffect` watching `isError/error`; 403 test case added.
- C4: Double-`Z` bug in `isLate` — `includes('Z') ? '' : 'Z'` guard applied consistently.
- C5: Missing 401 interceptor on `portalClient` — interceptor added; calls `logout()` and redirects to `?reason=session_expired`.
- C6: Touch targets below 44px on FamilyPortalTab Send/Remove/Cancel/Done — `min-h-[44px] flex items-center` applied to those specific buttons.
- M1: Query key not scoped to `clientId` — `['portal-dashboard', clientId]` in use.
- M2: `upcomingVisits` not capped — `.slice(0, 3)` added; test fixture now has 5 items asserting 3 rendered.
- M3: Hardcoded English "Cancel"/"Done"/"Remove" — all use `t()` from the `portal` namespace.
- M4: Unused `navigate`/`logout` in `PortalDashboardPage` — used in the 403 `useEffect`.
- M8: Loading state missing `role="status"` — added with `aria-live="polite"`.
- M10: Incorrect `logout()` localStorage comment — comment now accurately states the key remains with null values.

**From review 2:**
- C1: `isJwtExpired` used bare `atob` on base64url JWT segment — base64url normalization (`replace(/-/g, '+').replace(/_/g, '/')` + padding) is now present at lines 436–440. The PortalGuard test explicitly verifies this with a UUID-containing token.

---

## 4. Minor Issues & Improvements (Unresolved from Review 2)

The following issues were raised in review 2 and are **still present** in the current plan text:

### M1 (review 2, still open). Copy button missing `min-h-[44px]`

**Location:** Task 19, Step 4 — FamilyPortalTab.tsx, the Copy button at line 1756:

```tsx
<button
  onClick={handleCopy}
  className="border border-blue text-blue text-[11px] px-3 py-1.5"
>
```

`py-1.5` (6px × 2) + ~16px line-height ≈ 28px total. Below the 44px minimum. The adjacent "Done" button has `min-h-[44px] flex items-center justify-center`. Fix: add the same classes to the Copy button.

### M2 (review 2, still open). Confirm "Remove" button missing `min-h-[44px]`

**Location:** Task 19, Step 4 — FamilyPortalTab.tsx, the `data-confirm` remove button at line 1822:

```tsx
<button
  data-confirm
  onClick={() => removeMutation.mutate(fpu.id)}
  className="bg-dark text-white text-[11px] font-bold px-3 py-1.5"
>
```

Same calculation — approximately 28px. The adjacent Cancel button in the same row has `min-h-[44px] flex items-center justify-center`. Fix: add `min-h-[44px] flex items-center justify-center` to the data-confirm button.

### M3 (review 2, still open). `navigator.clipboard.writeText` unguarded against rejection

**Location:** Task 19, Step 4 — FamilyPortalTab.tsx, `handleCopy` at line 1682:

```ts
navigator.clipboard.writeText(inviteUrl).then(() => {
  setCopied(true)
  setTimeout(() => setCopied(false), 2000)
})
```

No `.catch` handler. In non-HTTPS environments or when the `clipboard-write` permission is denied, the Promise rejects silently, leaving the admin with no feedback. Fix: add a `.catch` that at minimum is a no-op (the URL is visible in the text box for manual copy):

```ts
navigator.clipboard.writeText(inviteUrl).then(() => {
  setCopied(true)
  setTimeout(() => setCopied(false), 2000)
}).catch(() => {
  // Clipboard API unavailable — URL is visible in the box above for manual copy
})
```

### M4 (review 2, still open). `usePortalDashboard` lacks `enabled: !!clientId`

**Location:** Task 15, Step 2 — `usePortalDashboard.ts` at line 383:

```ts
return useQuery({
  queryKey: ['portal-dashboard', clientId],
  queryFn: getPortalDashboard,
  retry: 2,
  ...
})
```

If `PortalGuard`'s `useEffect` redirect has not yet fired (first render cycle), `clientId` is `null`. The query fires immediately with a null token, the request is sent without an `Authorization` header, the backend returns 401, and the `portalClient` 401 interceptor fires `window.location.href = '/portal/verify?reason=session_expired'` — a full page navigation for a race condition that `PortalGuard` would have handled cleanly. Adding `enabled: !!clientId` suppresses the network call until a valid session is present:

```ts
return useQuery({
  queryKey: ['portal-dashboard', clientId],
  queryFn: getPortalDashboard,
  enabled: !!clientId,
  retry: 2,
  ...
})
```

### M5 (review 2, still open). Blanket `eslint-disable-line` suppresses entire `react-hooks/exhaustive-deps` rule

**Location:** Task 17, Step 3 — PortalVerifyPage.tsx at line 804:

```ts
}, [token]) // eslint-disable-line react-hooks/exhaustive-deps
```

`login` and `navigate` are used inside the effect but omitted from the dependency array. Both are stable references at runtime (Zustand actions, React Router `navigate`), so there is no functional bug. However, the blanket suppress disables the exhaustive-deps lint rule for the entire line rather than targeting specific variables. The idiomatic fix — which has no runtime cost — is to include them:

```ts
}, [token, login, navigate])
```

### M6 (review 2, still open). `VerifyErrorCard` `t` prop type uses a simplified signature

**Location:** Task 17, Step 3 — PortalVerifyPage.tsx at line 826:

```ts
t: (key: string) => string
```

The actual `TFunction` from react-i18next has a broader signature. TypeScript strict mode may emit an assignability warning depending on project settings. The low-friction fix is to call `useTranslation('portal')` directly inside `VerifyErrorCard` instead of accepting `t` as a prop — `VerifyErrorCard` only renders inside `PortalVerifyPage` which already has the same translation loaded, so there is no overhead.

### M7 (review 2, still open). `formatExpiry` uses browser-local timezone

**Location:** Task 19, Step 4 — FamilyPortalTab.tsx, `formatExpiry` at line 1689:

```ts
function formatExpiry(iso: string): { date: string; time: string } {
  const d = new Date(iso)
  return {
    date: d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' }),
    time: d.toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' }),
  }
}
```

No `timeZone` specified — renders in the admin's browser locale. An admin traveling across timezones sees a different expiry time than their home-office colleague. Since `FamilyPortalTab` does not receive `agencyTimezone`, the safest explicit fix is to render UTC and label it:

```ts
date: d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', timeZone: 'UTC' }),
time: d.toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit', timeZone: 'UTC' }) + ' UTC',
```

### M8 (review 2, still open). Confusing variable name `twentyMinAgo` in late-GREY test

**Location:** Task 18, Step 1 — PortalDashboardPage.test.tsx at line 992:

```ts
const twentyMinAgo = new Date(now.getTime() + 90 * 60 * 1000).toISOString().replace('Z', '')
```

This is the `scheduledEnd` set 90 minutes in the future (so the visit window has not yet ended), not a timestamp 20 minutes in the past. The name is actively misleading. Rename to `scheduledEnd` or `ninetyMinFromNow`.

---

## 5. New Minor Issues Found in This Pass

### N1. `PortalDashboardPage` `careServicesConcludedBody` interpolation passes empty `name`

**Location:** Task 18, Step 3 — PortalDashboardPage.tsx at line 1189:

```tsx
{t('careServicesConcludedBody', { name: '' })}
```

The i18n string is `"Care services for {{name}} have concluded."`. An empty string for `name` produces `"Care services for  have concluded."` — a double space and missing name. At the point this screen renders, `data` is unavailable (the query errored with 410). `clientFirstName` is therefore unknown. The fix is either to use a generic message that does not include the name, or to cache the last-known `clientFirstName` in a `useRef` that is set in a `useEffect` whenever `data` arrives. The simplest production-safe option is to add a fallback in the i18n string: use `"Care services have concluded."` (without the name interpolation) for the error branch, since the name is not reliably available on a 410 response.

### N2. `FamilyPortalTab` `inviteMutation` has no error state shown to the user

**Location:** Task 19, Step 4 — FamilyPortalTab.tsx, `inviteMutation` at line 1649:

```ts
const inviteMutation = useMutation({
  mutationFn: ({ email }: { email: string }) =>
    inviteFamilyPortalUser(clientId, email),
  onSuccess: (res) => { ... },
})
```

There is no `onError` handler and no error state displayed in the JSX. If the POST fails (network error, 409 duplicate email, 422 invalid email format from backend), the Generate Link button silently un-disables (because `isPending` returns to false) and the admin has no indication that the request failed. Add an error display:

```tsx
{inviteMutation.isError && (
  <p className="text-[12px] text-red-600 mt-1">
    {t('loadError')}
  </p>
)}
```

Or use an `onError` callback in the mutation to set a local error string.

### N3. `FamilyPortalTab` query has no loading or error states rendered

**Location:** Task 19, Step 4 — FamilyPortalTab.tsx at line 1644:

```ts
const { data } = useQuery({
  queryKey: ['family-portal-users', clientId],
  queryFn: () => listFamilyPortalUsers(clientId),
})
```

`isLoading` and `isError` are destructured nowhere in the component. On a slow network, the user list area shows neither a loading skeleton nor the "No family members" empty state (because `users` defaults to `[]`), which is identical to the genuinely empty state — a false empty for potentially 2-3 seconds. On a network error, users see nothing rather than an error/retry prompt. Extract `isLoading` and `isError` from the `useQuery` call and render appropriate states.

### N4. `removeMutation` has no error feedback and `confirmRemove` stays open on failure

**Location:** Task 19, Step 4 — FamilyPortalTab.tsx, `removeMutation` at line 1659:

```ts
const removeMutation = useMutation({
  mutationFn: (fpuId: string) => removeFamilyPortalUser(clientId, fpuId),
  onSuccess: () => {
    setConfirmRemove(null)
    qc.invalidateQueries({ queryKey: ['family-portal-users', clientId] })
  },
})
```

On DELETE failure, `confirmRemove` stays open (the user can retry), but there is no visible error. The Remove confirm button stays in its default state (not a loading/disabled state while the mutation is pending either), allowing double-submits. Fix: disable the confirm button while `removeMutation.isPending` and show an inline error when `removeMutation.isError`.

### N5. `usePortalDashboard` `queryFn` does not receive `clientId` — query key and function are misaligned

**Location:** Task 15, Step 2 — `usePortalDashboard.ts` at line 385:

```ts
queryKey: ['portal-dashboard', clientId],
queryFn: getPortalDashboard,
```

The `queryKey` includes `clientId` for correct per-client cache scoping, but `getPortalDashboard` ignores the `QueryFunctionContext` argument entirely — it reads the `clientId` implicitly from the `portalAuthStore`. This is functionally correct (the store `clientId` and the key `clientId` are always in sync while authenticated), but it is a subtle coupling: a future developer may look at the query key and expect the function to receive `clientId` as an argument. Consider either making the function accept `clientId` explicitly via the `QueryFunctionContext`, or adding a comment explaining the intentional implicit coupling:

```ts
// Note: getPortalDashboard reads clientId from portalAuthStore internally;
// clientId is in the query key solely for per-client cache scoping.
queryFn: getPortalDashboard,
```

---

## 6. Questions for Clarification

**Q1 (carried from review 2).** Does the backend `InviteResponse.expiresAt` carry a UTC ISO-8601 timestamp (with trailing `Z`)? If yes, the `formatExpiry` fix in M7 (adding `timeZone: 'UTC'`) is definitive. Plan 2 DTO comment says "ISO-8601 UTC timestamp string" — if confirmed, the fix is straightforward and the `' UTC'` suffix removes all ambiguity.

**Q2.** For N1 (`careServicesConcludedBody`): is the family member's first name (`clientFirstName`) guaranteed to be available from a previous successful render when a 410 occurs (i.e., is the 410 always a transient fetch error on an already-loaded dashboard), or can it occur on the very first fetch? If it can occur on the first fetch, passing an empty `name` to the i18n key will always produce a malformed sentence and the message text must be changed.

**Q3 (carried from reviews 1 & 2).** Does Plan 2 include a `GET /clients/{id}/family-portal-users` endpoint? The File Map in Plan 2 documents only `POST .../invite` and `DELETE .../family-portal-users/{id}`. The `listFamilyPortalUsers` function in `clients.ts` (Task 19, Step 2) depends on this endpoint existing in the backend. If it was accidentally omitted from Plan 2, that plan needs a corrective task before this frontend plan can be fully implemented.

---

## 7. Final Recommendation

**Approve with changes.**

No new critical issues were found. The `atob` base64url fix (the blocker from review 2) is correctly implemented. The remaining blockers before implementation are: the two accessibility touch-target violations (M1 and M2 — the Copy and confirm-Remove buttons still lack `min-h-[44px]`), the unguarded clipboard call (M3), and the missing `enabled: !!clientId` guard (M4). The newly found issues N1–N5 are not blockers but should be addressed in the same pass: the missing mutation error states (N2, N4) and missing list loading/error states (N3) represent observable UX gaps that will require follow-up fixes post-implementation if not done now. Once M1–M4 and N1–N5 are incorporated, the plan is ready for implementation.
