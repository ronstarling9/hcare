# Critical Review: Family Portal Plan 3 — Frontend (Review 2)
**Review date:** 2026-04-08
**Reviewer:** superpowers:critical-implementation-review v1.5.1
**Plan file:** `2026-04-08-add-family-portal-plan-3-frontend.md`
**Prior reviews:** Review 1 (`2026-04-08-add-family-portal-plan-3-frontend-critical-review-1.md`)

---

## 1. Overall Assessment

The plan has been substantially improved since review 1. All six critical issues from that review have been addressed in the current text: `PortalVerifyPage` uses a single `useState` correctly; `StatusPill` passes `tz` through; the 403 case is handled via `useEffect` in the component; `isLate` guards against double-`Z`; `portalClient` has a 401 response interceptor; and buttons throughout the UI have `min-h-[44px]`. The minor issues around i18n keys (`t('cancel')`, `t('done')`, `t('remove')`), the loading state `role="status"`, the query key scoped to `clientId`, `upcomingVisits.slice(0,3)`, and the `logout` comment are all resolved. One new critical issue remains: `isJwtExpired` in `PortalGuard` uses the browser's `atob` directly against a JWT segment, which is base64url-encoded (RFC 4648 §5) rather than standard base64. This will throw a `DOMException` for any JWT that contains `-` or `_` characters in its payload segment — a near-certain occurrence for UUID-containing JWTs. Two minor issues also remain: the Copy (invite URL) button and the confirm-Remove button both lack `min-h-[44px]`, and `navigator.clipboard.writeText` is unguarded against rejection.

---

## 2. Critical Issues

### C1. `isJwtExpired` uses `atob` on a base64url segment — throws for virtually all real JWTs

**Location:** Task 16, Step 1 — `PortalGuard.tsx`, `isJwtExpired` function:

```ts
const payload = JSON.parse(atob(token.split('.')[1]))
```

**Problem:** JWT header and payload segments are base64url-encoded (RFC 4648 §5), which replaces `+` with `-` and `/` with `_`, and omits the `=` padding. The browser's `atob()` only accepts standard base64 — it will throw a `DOMException: Failed to execute 'atob'` on any input containing `-` or `_`. Because the JWT payload here contains a UUID (`clientId`) serialized as a claim, the encoded segment will routinely contain `-` characters. The `try/catch` wrapper catches this and returns `true` (treated as expired), meaning `PortalGuard` will redirect every authenticated family member to `?reason=session_expired` on every page load, making the dashboard completely inaccessible.

**Why it matters:** This is a total availability failure for the portal dashboard. The symptom — "session expired" loop even immediately after a successful verify — would be extremely difficult for a family member to diagnose.

**Fix:** Normalize base64url to standard base64 before calling `atob`:

```ts
function isJwtExpired(token: string): boolean {
  try {
    const seg = token.split('.')[1]
    // base64url → base64: replace URL-safe chars and restore padding
    const b64 = seg.replace(/-/g, '+').replace(/_/g, '/').padEnd(
      seg.length + ((4 - (seg.length % 4)) % 4),
      '=',
    )
    const payload = JSON.parse(atob(b64))
    return payload.exp * 1000 < Date.now()
  } catch {
    return true
  }
}
```

Alternatively, use `atob(seg.replace(/-/g, '+').replace(/_/g, '/'))` without padding restoration — most browsers tolerate missing padding in `atob`, so the simpler form usually works, but the padded form is more strictly correct.

---

## 3. Previously Addressed Items

The following issues from review 1 are resolved in the current plan text:

- **C1 (review 1):** `PortalVerifyPage` broken ref/state pattern — replaced with single `useState<VerifyState>`, imports at top.
- **C2 (review 1):** Hardcoded `'America/New_York'` in `StatusPill` COMPLETED branch — `tz` prop is now threaded through and used.
- **C3 (review 1):** `meta.onError` in React Query v5 — replaced with `useEffect` watching `isError`/`error` in `PortalDashboardPage`, with a 403 test case added.
- **C4 (review 1):** Double-`Z` bug in `isLate` — the same `includes('Z') ? '' : 'Z'` guard used in `formatTime` is now applied.
- **C5 (review 1):** Missing 401 interceptor on `portalClient` — interceptor is present, calls `logout()` and redirects to `?reason=session_expired`.
- **C6 (review 1):** Touch targets below 44px on FamilyPortalTab Send/Remove/Cancel/Done — `min-h-[44px] flex items-center` applied to those buttons.
- **M1 (review 1):** Query key not scoped to `clientId` — `['portal-dashboard', clientId]` now used.
- **M2 (review 1):** `upcomingVisits` not capped — `upcomingVisits.slice(0, 3)` added; test fixture updated to 5 items asserting 3 rendered.
- **M3 (review 1):** Hardcoded English "Cancel"/"Done"/"Remove" — all now use `t('cancel')`, `t('done')`, `t('removeConfirm')` from the `portal` namespace.
- **M4 (review 1):** Unused `navigate`/`logout` in `PortalDashboardPage` — they are used in the 403 `useEffect`.
- **M8 (review 1):** Loading state missing `role="status"` — added with `aria-live="polite"`.
- **M10 (review 1):** Incorrect comment claiming `logout()` removes the localStorage key — comment now accurately states the key remains with null values.

---

## 4. Minor Issues & Improvements

### M1. Copy button in invite result area is missing `min-h-[44px]`

**Location:** Task 19, Step 4 — `FamilyPortalTab.tsx`, the Copy button rendered after a successful generate:

```tsx
<button
  onClick={handleCopy}
  className="border border-blue text-blue text-[11px] px-3 py-1.5"
>
```

`py-1.5` (6px × 2) plus an 11px font's line-height (~16px) yields roughly 28px — below the 44px minimum. The "Done" button beside it has `min-h-[44px] flex items-center justify-center`; the Copy button does not. Add the same classes.

### M2. Confirm "Remove" button (`data-confirm`) is missing `min-h-[44px]`

**Location:** Task 19, Step 4 — `FamilyPortalTab.tsx`, the inline remove-confirmation confirm button:

```tsx
<button
  data-confirm
  onClick={() => removeMutation.mutate(fpu.id)}
  className="bg-dark text-white text-[11px] font-bold px-3 py-1.5"
>
```

Same calculation as M1 — approximately 28px, not 44px. The adjacent Cancel button has `min-h-[44px]`; this one does not. The discrepancy is also observable in the test selector: `screen.getByText('Remove', { selector: 'button[data-confirm]' })` — the test passes even without the height class, but the accessibility violation remains.

### M3. `navigator.clipboard.writeText` is unguarded against rejection

**Location:** Task 19, Step 4 — `FamilyPortalTab.tsx`, `handleCopy`:

```ts
navigator.clipboard.writeText(inviteUrl).then(() => {
  setCopied(true)
  setTimeout(() => setCopied(false), 2000)
})
```

`navigator.clipboard` is only available in secure contexts (HTTPS or `localhost`). In an HTTP development environment or if the user has denied the `clipboard-write` permission, this call throws synchronously (`Cannot read properties of undefined`) or the `Promise` rejects silently, leaving `setCopied` never called. The admin sees no feedback that the copy failed. Add a `.catch` handler that falls back to `document.execCommand('copy')` or at minimum shows an error state:

```ts
navigator.clipboard.writeText(inviteUrl).then(() => {
  setCopied(true)
  setTimeout(() => setCopied(false), 2000)
}).catch(() => {
  // Clipboard API unavailable — no-op; URL is visible in the box for manual copy
})
```

### M4. `usePortalDashboard` is not gated with `enabled: !!clientId`

**Location:** Task 15, Step 2 — `usePortalDashboard.ts`:

```ts
return useQuery({
  queryKey: ['portal-dashboard', clientId],
  queryFn: getPortalDashboard,
  ...
})
```

`clientId` is read from `portalAuthStore`. If `PortalGuard` has not yet redirected an unauthenticated user (the `useEffect` fires after the first render), `clientId` will be `null` for one render cycle. The query will fire with a `null`-keyed key and no Authorization header, triggering the 401 interceptor which calls `window.location.href` — a full navigation — unnecessarily. Adding `enabled: !!clientId` prevents the network call until a valid clientId is present:

```ts
return useQuery({
  queryKey: ['portal-dashboard', clientId],
  queryFn: getPortalDashboard,
  enabled: !!clientId,
  ...
})
```

### M5. `PortalVerifyPage` token `useEffect` eslint-disable suppresses a real warning — `login` and `navigate` omitted from deps

**Location:** Task 17, Step 3 — `PortalVerifyPage.tsx`:

```ts
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

`login` and `navigate` are used inside the effect but excluded from the dependency array. Both are stable references in practice (`navigate` from React Router is stable; Zustand action `login` is stable), so this does not cause a functional bug at runtime. However, the blanket `eslint-disable-line` suppresses the entire `react-hooks/exhaustive-deps` rule rather than targeting the specific variables. The safer and self-documenting approach is to include `login` and `navigate` in the dependency array — they are stable and adding them has no performance cost while satisfying the linter without suppression:

```ts
}, [token, login, navigate])
```

### M6. `VerifyErrorCard` internal `t` prop type is too narrow

**Location:** Task 17, Step 3 — `VerifyErrorCard` subcomponent:

```ts
function VerifyErrorCard({
  state,
  t,
}: {
  state: Exclude<VerifyState, 'verifying'>
  t: (key: string) => string
}) {
```

The actual `t` function from `useTranslation` is `TFunction`, not `(key: string) => string`. TypeScript will likely accept this narrower type at the call site (since the real `t` is assignment-compatible), but if TypeScript strict mode compares function types, it may warn about the simplified signature. The idiomatic pattern is to accept `TFunction` from `react-i18next` or to call `useTranslation('portal')` inside `VerifyErrorCard` directly (as it does not need to be a prop at all). This is a low-risk issue but worth fixing for type accuracy.

### M7. `formatExpiry` in `FamilyPortalTab` has no IANA timezone — renders in browser locale time

**Location:** Task 19, Step 4 — `FamilyPortalTab.tsx`, `formatExpiry`:

```ts
function formatExpiry(iso: string): { date: string; time: string } {
  const d = new Date(iso)
  return {
    date: d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' }),
    time: d.toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' }),
  }
}
```

This was raised as M5 in review 1. The plan still uses the browser's local timezone. Since `FamilyPortalTab` has no access to `agencyTimezone`, the practical fix is to render the expiry as UTC explicitly (add `timeZone: 'UTC'` and append `' UTC'` to the time string), which is unambiguous regardless of the admin's browser location. The current behavior gives wrong times to admins who are travelling or in a different timezone than their agency.

### M8. `upcomingVisits` test has a confusing variable name carried over from review 1

**Location:** Task 18, Step 1 — `PortalDashboardPage.test.tsx`, late GREY test:

```ts
const twentyMinAgo = new Date(now.getTime() + 90 * 60 * 1000).toISOString().replace('Z', '')
```

This variable represents `scheduledEnd` set 90 minutes in the future (so the visit has not ended), but is named `twentyMinAgo`. This was flagged as M7 in review 1. The name should be `scheduledEnd` or `ninetyMinFromNow` for the reader to understand the test fixture without arithmetic.

---

## 5. Questions for Clarification

**Q1.** The backend JWT (`PortalVerifyResponse.jwt`) — is it generated using a standard JWT library (e.g., JJWT/Nimbus)? If so, the payload is definitively base64url-encoded and the `atob` fix in C1 is required. Confirming this would remove any ambiguity about whether the base64url normalization is needed.

**Q2.** For M7: does Plan 2 specify whether `InviteResponse.expiresAt` is in the agency's timezone or UTC? If it is UTC (the Plan 2 DTO comment says "ISO-8601 UTC timestamp string"), the fix is straightforward: render it as UTC and label it `" UTC"`.

**Q3.** `listFamilyPortalUsers` (added to `clients.ts` in Task 19, Step 2) — Plan 2 does not include a `GET /clients/{id}/family-portal-users` endpoint in its File Map or task list. Was this endpoint intentionally omitted from Plan 2 and needs to be added there, or is it expected to be added in Plan 3 as a backend change that slipped through?

---

## 6. Final Recommendation

**Approve with changes.**

The plan is nearly implementation-ready. The one remaining critical issue (C1: `atob` base64url bug in `PortalGuard`) will cause a total dashboard availability failure for family members and must be fixed before implementation begins. The two touch-target gaps on the Copy and confirm-Remove buttons (M1, M2) are accessibility compliance violations that should be fixed at the same time. All other items are low-risk improvements. Once C1, M1, and M2 are corrected, the plan can be implemented as written.
