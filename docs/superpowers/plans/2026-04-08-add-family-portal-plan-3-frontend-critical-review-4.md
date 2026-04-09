# Critical Review: Family Portal Plan 3 — Frontend (Review 4)
**Review date:** 2026-04-08
**Reviewer:** superpowers:critical-implementation-review v1.5.1
**Plan file:** `2026-04-08-add-family-portal-plan-3-frontend.md`
**Prior reviews:** Review 1 (`…-critical-review-1.md`), Review 2 (`…-critical-review-2.md`), Review 3 (`…-critical-review-3.md`)

---

## 1. Overall Assessment

The plan has resolved the four most important items from review 3: the Copy and confirm-Remove buttons now have `min-h-[44px]`; the clipboard call is guarded with a `.catch`; `enabled: !!clientId` is present in `usePortalDashboard`; `inviteMutation` and `removeMutation` both have error feedback; and the `FamilyPortalTab` query now surfaces loading and error states. However, one new critical issue was introduced while adding the `inviteError` display: the error paragraph is placed as a sibling to the `<div>` flex container inside a JSX ternary branch — invalid JSX that will fail to compile. Four minor issues from reviews 2 and 3 remain unaddressed.

---

## 2. Critical Issues

### C1. Adjacent JSX siblings inside ternary branch — compile error in `FamilyPortalTab`

**Location:** Task 19, Step 4 — `FamilyPortalTab.tsx`, lines 1745–1775 of the plan:

```tsx
{!inviteUrl ? (
  <div className="flex gap-2 items-end">
    {/* ... input, Generate, Cancel buttons ... */}
  </div>
  {inviteError && (
    <p className="text-[12px] text-red-600 mt-1">{inviteError}</p>
  )}
) : (
  <div>...</div>
)}
```

**Problem:** A JSX ternary branch (`condition ? (...) : (...)`) must return a single root element. The first branch here contains two adjacent top-level elements — the flex `<div>` and the `{inviteError && <p>}` conditional — without a wrapper. TypeScript/JSX will refuse to compile this, reporting: `"Adjacent JSX elements must be wrapped in an enclosing tag. Did you want a JSX fragment?"`. The `npx tsc --noEmit` step (Task 19, Step 7) will catch this, but the `npm run test` step at Task 19, Step 3 (run before the implementation exists) will pass vacuously, and the actual compile will fail when the implementer reaches Step 7.

**Why it matters:** The component will not compile as written. This is a guaranteed build failure.

**Fix:** Wrap both elements in a `<>...</>` Fragment:

```tsx
{!inviteUrl ? (
  <>
    <div className="flex gap-2 items-end">
      {/* ... input, Generate, Cancel buttons ... */}
    </div>
    {inviteError && (
      <p className="text-[12px] text-red-600 mt-1">{inviteError}</p>
    )}
  </>
) : (
  <div>...</div>
)}
```

---

## 3. Previously Addressed Items

The following issues from prior reviews are resolved in the current plan text:

**From review 1:** C1–C6 (broken ref/state in VerifyPage, hardcoded timezone in StatusPill, `meta.onError`, double-Z in `isLate`, missing 401 interceptor, touch targets on core buttons), M1–M4, M8, M10.

**From review 2:** C1 (`atob` base64url bug in `isJwtExpired`). M1–M4 from review 2 (Copy button `min-h-[44px]`, confirm-Remove `min-h-[44px]`, clipboard `.catch`, `enabled: !!clientId`).

**From review 3:** N1 (`careServicesConcludedBody` no longer interpolates `{{name}}`). N2 (`inviteMutation` has `onError` and displays `inviteError`). N3 (`FamilyPortalTab` query now destructures `isLoading`/`isError` and renders loading/error states). N4 (`removeMutation` has `onError`, `disabled` on pending, and `removeError` display).

---

## 4. Minor Issues (Still Unresolved from Prior Reviews)

The following issues were raised in reviews 2 and 3 and remain present in the current plan text:

### M1 (review 2 M5, review 3 M5, still open). Blanket `eslint-disable-line` on `useEffect` deps

**Location:** Task 17, Step 3 — `PortalVerifyPage.tsx`, line 815:

```ts
}, [token]) // eslint-disable-line react-hooks/exhaustive-deps
```

`login` and `navigate` are used in the effect body but omitted from the dependency array. Both are stable references at runtime. The blanket suppression disables the entire `exhaustive-deps` rule for the line. The idiomatic fix with no runtime cost:

```ts
}, [token, login, navigate])
```

### M2 (review 2 M6, review 3 M6, still open). `VerifyErrorCard` accepts simplified `t` prop type

**Location:** Task 17, Step 3 — `PortalVerifyPage.tsx`, `VerifyErrorCard` prop signature:

```ts
t: (key: string) => string
```

The actual `TFunction` from react-i18next is a wider type. Under strict TypeScript settings this may generate an assignability warning. The lowest-friction fix is to call `useTranslation('portal')` directly inside `VerifyErrorCard` rather than accepting `t` as a prop — the component only exists inside `PortalVerifyPage` and the namespace is the same.

### M3 (review 2 M7, review 3 M7, still open). `formatExpiry` uses browser-local timezone

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

No `timeZone` specified — renders in the admin's browser locale. An admin traveling or in a different timezone than their agency will see incorrect expiry times. Since `agencyTimezone` is not available in `FamilyPortalTab`, rendering UTC with a `' UTC'` suffix is the safest unambiguous option:

```ts
date: d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', timeZone: 'UTC' }),
time: d.toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit', timeZone: 'UTC' }) + ' UTC',
```

### M4 (review 2 M8, review 3 M8, still open). Confusing `twentyMinAgo` variable name in late-GREY test

**Location:** Task 18, Step 1 — `PortalDashboardPage.test.tsx`, line 1003:

```ts
const twentyMinAgo = new Date(now.getTime() + 90 * 60 * 1000).toISOString().replace('Z', '')
```

This variable is the `scheduledEnd` set 90 minutes in the future (so the visit window is still open), not a timestamp 20 minutes in the past. The name is actively misleading. Rename to `scheduledEnd` or `ninetyMinFromNow`.

---

## 5. New Minor Issue Found in This Pass

### N1. `inviteError` state is not cleared when the invite form is closed

**Location:** Task 19, Step 4 — `FamilyPortalTab.tsx`, `openInviteForm`:

```ts
function openInviteForm(prefill?: string) {
  setEmail(prefill ?? '')
  setPrefilledEmail(prefill ?? null)
  setInviteUrl(null)
  setExpiresAt(null)
  setCopied(false)
  setFormOpen(true)
}
```

`setInviteError(null)` is missing from `openInviteForm`. If the admin clicks "Generate Link", gets an error, closes the form with "Cancel", and then re-opens it with "+ Invite" or "Send New Link", the previous error message will be visible immediately before the user has even submitted the new form. The error should be cleared when the form is opened:

```ts
function openInviteForm(prefill?: string) {
  setEmail(prefill ?? '')
  setPrefilledEmail(prefill ?? null)
  setInviteUrl(null)
  setExpiresAt(null)
  setCopied(false)
  setInviteError(null)  // add this
  setFormOpen(true)
}
```

Similarly, the Cancel button's `onClick` closes the form but does not reset `inviteError`. If the form is closed via Cancel rather than via `openInviteForm`, the error persists in state. Add `setInviteError(null)` to the Cancel button's handler:

```tsx
<button
  onClick={() => { setFormOpen(false); setPrefilledEmail(null); setInviteError(null) }}
  ...
>
```

---

## 6. Questions for Clarification

**Q1 (carried from reviews 2 and 3).** Does the backend `InviteResponse.expiresAt` carry a UTC ISO-8601 timestamp (with trailing `Z`)? If yes, the `formatExpiry` fix in M3 (adding `timeZone: 'UTC'`) is definitive.

**Q2 (carried from reviews 1, 2, and 3).** Does Plan 2 include a `GET /clients/{id}/family-portal-users` endpoint? The File Map in Plan 2 documents only `POST .../invite` and `DELETE .../family-portal-users/{id}`. The `listFamilyPortalUsers` function in `clients.ts` (Task 19, Step 2) depends on this endpoint. If it was accidentally omitted from Plan 2, that plan needs a corrective task before this frontend plan can be fully implemented.

---

## 7. Final Recommendation

**Approve with changes.**

The one new critical issue (C1: adjacent JSX siblings in the ternary branch) is a guaranteed compile failure and must be fixed before implementation. The fix is a one-line Fragment wrapper. The four remaining minor issues (M1–M4) are carry-overs from prior reviews that have not impacted correctness but represent lint compliance, type accuracy, timezone correctness, and readability. Once C1 is corrected, the plan is ready for implementation.
