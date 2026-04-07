# Critical Review 3: Phase 1 Static UI Plan

**Plan reviewed:** `docs/superpowers/plans/2026-04-06-frontend-phase-1-static-ui.md`  
**Previous reviews:** review-1, review-2  
**Reviewer date:** 2026-04-07

---

## 1. Overall Assessment

Reviews 1 and 2 have driven the plan to a high level of quality. All six previously raised critical issues (two per review) are correctly resolved in the current plan. This pass found one new critical issue: the UTC-midnight date parsing fix applied in review-2 C2 missed a fourth affected location — `AlertsColumn.tsx` displays `alert.dueDate` using the same raw `new Date(dateStr)` pattern on a date-only string. Two minor usability issues are also noted for the first time.

---

## 2. Critical Issues

### C1 — `AlertsColumn` `dueDate` display uses raw `new Date()` on a date-only string (Task 11.3)

**Description:**

Review-2 C2 explicitly fixed four call sites across three files (ClientDetailPanel, CaregiversTable, CaregiverDetailPanel). However, `AlertsColumn.tsx` (Task 11.3) also parses and displays a date-only string without the `T12:00:00` guard:

```tsx
// AlertsColumn.tsx — Task 11.3
Due {new Date(alert.dueDate).toLocaleDateString('en-US', { month: 'short', day: 'numeric' })}
```

`alert.dueDate` values in mock data are `'2026-04-10'` (M. Garcia credential expiry) and `'2026-06-30'` (Alice Johnson authorization). Both are date-only ISO strings parsed as UTC midnight.

The `isUrgent()` function in the same file uses the same `new Date(dueDate)` call for a threshold comparison — this is a negligible ≤1-hour error on a 7-day window (same reasoning as review-2 applied to `isExpiringSoon`). Only the display call site needs fixing.

**Why it matters:**

A scheduler in Austin, TX (UTC−5) will see the credential expiry alert display "Due Apr 9" instead of "Due Apr 10." This is the same user-visible off-by-one as review-2 C2, occurring on the Dashboard — the page most likely to be viewed first during the manual checkpoint. It also creates an inconsistency: `CaregiverDetailPanel` now correctly shows "Apr 10" (fixed in review-2) while the Dashboard alert for the same event shows "Apr 9."

**Fix:**

Add `formatLocalDate` to `AlertsColumn.tsx` (identical helper to the three files fixed in review-2), then replace the display call:

```tsx
// Add near the top of AlertsColumn.tsx, before isUrgent():
function formatLocalDate(dateStr: string, options?: Intl.DateTimeFormatOptions): string {
  // Date-only ISO strings are UTC-midnight per spec; append T12:00:00 to avoid day-shift in UTC-N.
  return new Date(dateStr + 'T12:00:00').toLocaleDateString('en-US', options)
}

// Replace the affected display line:
Due {formatLocalDate(alert.dueDate, { month: 'short', day: 'numeric' })}
```

`isUrgent()` does not need changing — a ≤1-hour UTC error on a 7-day threshold is negligible.

---

## 3. Previously Addressed Items

All issues from reviews 1 and 2 are correctly resolved:

- **C1 (review-1) — `caregiverVId` prop typo**: `caregiverId` used consistently in `CaregiverDetailPanel` and `Shell.tsx`. ✅
- **C2 (review-1) — UTC `toISOString()` in `handleNewShift`**: Local date parts used in `SchedulePage.handleNewShift`. ✅
- **C3 (review-1) — Broken Add Client/Caregiver buttons**: Both use `alert()` stubs. ✅
- **M1 (review-1) — Fragile Playwright `getByText('care')`**: Replaced with `page.locator('aside span').filter({ hasText: /^care$/ }).first()`. ✅
- **M2 (review-1) — No overlap note in manual checkpoint**: Overlap note added. ✅
- **M4 (review-1) — Redundant second-terminal instruction**: Removed; `webServer` block documented. ✅
- **Q2 (review-1) — Stale mock dates**: All data updated to 2026-04-07. ✅
- **C1 (review-2) — `NewShiftPanel` `defaultValues.date` using `toISOString()`**: Fixed with local date parts. ✅
- **C2 (review-2) — Date-only strings parsed as UTC-midnight in ClientDetailPanel, CaregiversTable, CaregiverDetailPanel**: `formatLocalDate` helper added to all three files; four call sites replaced. ✅

---

## 4. Minor Issues & Improvements

### M-new-1 — `serviceTypeId` validation fires silently in `NewShiftPanel` (Task 10.2)

`clientId` shows a validation error message:
```tsx
{errors.clientId && (
  <p className="text-[11px] text-red-600 mt-1">{errors.clientId.message}</p>
)}
```

`serviceTypeId` is also required (`register('serviceTypeId', { required: 'Service type is required' })`), but has no corresponding error display. If a user submits without selecting a service type, React Hook Form blocks submission but no error message appears — the form appears to do nothing. Since Phase 1 has only one service type option (`PCS`), this is hard to trigger accidentally, but it's a latent usability gap.

**Fix (one line):** Add the error display beneath the service type `<select>`:
```tsx
{errors.serviceTypeId && (
  <p className="text-[11px] text-red-600 mt-1">{errors.serviceTypeId.message}</p>
)}
```

### M-new-2 — Authorization date range displays as raw ISO strings (Task 12.3)

In `ClientDetailPanel`'s Authorizations tab:
```tsx
<div className="text-[10px] text-text-secondary mt-1">
  {auth.startDate} – {auth.endDate}
</div>
```

`auth.startDate` and `auth.endDate` are date-only strings (`'2026-01-01'`, `'2026-06-30'`), displayed raw without formatting. They will appear as `2026-01-01 – 2026-06-30` — technically correct but inconsistent with the formatted dates shown elsewhere in the panel. Not a bug, but noticeable at the manual checkpoint.

**Optional fix:** Use `formatLocalDate` (already in scope in that file) with `{ month: 'short', day: 'numeric', year: 'numeric' }` options for both dates.

---

## 5. Questions for Clarification

None. The critical fix is unambiguous.

---

## 6. Final Recommendation

**Approve with changes.**

Fix C1 (add `formatLocalDate` to `AlertsColumn.tsx` and replace the one display call site). The two minor issues are optional — M-new-1 is a one-line add and worth fixing; M-new-2 is cosmetic. The plan is otherwise complete and production-ready.
