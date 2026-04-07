# Critical Review 2: Phase 1 Static UI Plan

**Plan reviewed:** `docs/superpowers/plans/2026-04-06-frontend-phase-1-static-ui.md`  
**Previous reviews:** review-1 (`2026-04-06-frontend-phase-1-static-ui-critical-review-1.md`)  
**Reviewer date:** 2026-04-07

---

## 1. Overall Assessment

Review-1's three critical issues and three of its five minor issues have been correctly resolved. The plan is materially improved: the prop typo is gone, the schedule date-pre-fill now uses local date parts, the broken Add Client/Caregiver paths now use `alert()` stubs, the Playwright locator is precise, an overlap note has been added to the manual checkpoint, the redundant second-terminal instruction is removed, and mock data has been advanced to April 7. Two new critical issues were found during this pass: the UTC date offset bug (same root cause as review-1 C2) survived in a second code path (`NewShiftPanel`'s fallback `defaultValues`), and date-only ISO strings (`dateOfBirth`, `hireDate`, `expiryDate`) are parsed as UTC-midnight throughout the display components, causing all such dates to render one day early for UTC−N users. Both are one- or two-line fixes each.

---

## 2. Critical Issues

### C1 — `NewShiftPanel` `defaultValues.date` still uses `.toISOString()` (Task 10.2)

**Description:**

```tsx
// NewShiftPanel.tsx — Task 10.2, defaultValues
date: prefill?.date ?? new Date().toISOString().split('T')[0],
```

Review-1 C2 fixed `handleNewShift` in `SchedulePage` (the slot-click path). That fix was correctly applied. However, `NewShiftPanel` computes *its own* fallback date using the same `toISOString()` pattern. This fallback is reached when the user opens the panel via the top-bar `+ New Shift` button, which passes no `prefill`:

```tsx
// SchedulePage.tsx — top-bar button
onClick={() => openPanel('newShift', undefined, { backLabel: '← Schedule' })}
```

`prefill` is `null`, so `prefill?.date` is `undefined`, so the fallback fires.

**Why it matters:**

A scheduler in Austin, TX (UTC−5) clicking `+ New Shift` at 8 pm will see tomorrow's date pre-filled in the form. This is the same user-visible bug as review-1 C2; it just survived in a second code path that the review-1 fix didn't cover.

**Fix:**

Replace with local date parts (same pattern as the review-1 C2 fix):

```tsx
defaultValues: {
  date: prefill?.date ?? [
    new Date().getFullYear(),
    String(new Date().getMonth() + 1).padStart(2, '0'),
    String(new Date().getDate()).padStart(2, '0'),
  ].join('-'),
  startTime: prefill?.time ?? '09:00',
  endTime: '13:00',
},
```

---

### C2 — Date-only ISO strings parsed as UTC-midnight cause off-by-one date display (Tasks 12, 13)

**Description:**

Per the ECMAScript spec, date-only ISO strings (e.g. `'1942-03-15'`) are parsed by `new Date()` as **UTC midnight**, not local midnight. When displayed with `.toLocaleDateString()` in a UTC−N timezone, the date shifts to the previous calendar day.

The following locations in the plan create `Date` objects from date-only strings and immediately call `.toLocaleDateString()`:

| Location | Field | Value (mock) |
|---|---|---|
| `ClientDetailPanel` (Task 12.3) | `client.dateOfBirth` | `'1942-03-15'` |
| `CaregiverDetailPanel` (Task 13.3, overview tab) | `caregiver.hireDate` | `'2023-04-01'` |
| `CaregiversTable` (Task 13.1) | `cg.hireDate` | `'2023-04-01'` |
| `CaregiverDetailPanel` (Task 13.3, credentials tab) | `cred.expiryDate` | `'2026-04-10'` |

For a user in UTC−5, all four show one day earlier (e.g. March 14 instead of March 15 for Alice Johnson's DOB, April 9 instead of April 10 for Maria Garcia's credential expiry).

Note: `formatDate()` in `ShiftDetailPanel` (Task 10.1) is not affected — it operates on datetime strings (`'2026-04-07T08:00:00'`) which are parsed as **local time**.

**Why it matters:**

During the Phase 1 manual checkpoint, a tester in Central Time (UTC−5) will see every DOB, hire date, and credential expiry rendered one day early — including the "Expiring soon" warning for Maria Garcia's HHA credential, which would show April 9 instead of April 10. This creates a confusing result at the checkpoint and the pattern persists identically into Phases 6 and 7 when real API data flows through.

**Fix:**

Add a local helper function in each of the three affected files that appends `T12:00:00` before parsing — midday local time is safe across all UTC−14 to UTC+14 timezones:

```tsx
// Place near the top of ClientDetailPanel.tsx, CaregiverDetailPanel.tsx, CaregiversTable.tsx
function formatLocalDate(dateStr: string, options?: Intl.DateTimeFormatOptions): string {
  // Date-only strings are UTC-midnight per spec; append T12:00:00 to avoid day-shift in UTC-N.
  return new Date(dateStr + 'T12:00:00').toLocaleDateString('en-US', options)
}
```

Then replace each affected call:

```tsx
// ClientDetailPanel — DOB
DOB: {formatLocalDate(client.dateOfBirth)}

// CaregiverDetailPanel — hire date
['Hire Date', caregiver.hireDate ? formatLocalDate(caregiver.hireDate) : '—'],

// CaregiversTable — hire date
formatLocalDate(cg.hireDate, { month: 'short', day: 'numeric', year: 'numeric' })

// CaregiverDetailPanel — credential expiry
{formatLocalDate(cred.expiryDate)}
```

`isExpiringSoon()` compares timestamps over a 30-day window; an hour of UTC offset error is negligible there and does not need fixing.

---

## 3. Previously Addressed Items

All of the following review-1 issues were correctly resolved:

- **C1 (review-1) — `caregiverVId` prop typo**: Renamed to `caregiverId` in `CaregiverDetailPanel` props, in `Shell.tsx` pass-through, and the now-deleted Task 14.3. ✅
- **C2 (review-1) — UTC date offset in `handleNewShift`**: `SchedulePage.handleNewShift` now uses `getFullYear()/getMonth()+1/getDate()` local date parts. ✅
- **C3 (review-1) — Broken "+ Add Client / + Add Caregiver" buttons**: Both `ClientsPage` and `CaregiversPage` now use `alert()` stubs consistent with the Payers page. `usePanelStore` import removed from both. ✅
- **M1 (review-1) — Fragile Playwright `getByText('care')`**: Replaced with `page.locator('aside span').filter({ hasText: /^care$/ }).first()`. ✅
- **M2 (review-1) — No overlap warning in manual checkpoint**: Overlap note added to MANUAL TEST CHECKPOINT 1. ✅
- **M4 (review-1) — Redundant second terminal instruction**: Step 16.4 now documents the `webServer` block behavior; manual terminal instruction removed. ✅
- **Q2 (review-1) — Stale mock dates**: All four shifts, dashboard visits, and EVV history rows updated to 2026-04-07. Week comment and Task 4 header updated. ✅

---

## 4. Minor Issues & Improvements

No new minor issues beyond what remains from review-1 (M3, M5 — not re-raised per review rules). The C1/C2 fixes above address all material new findings.

---

## 5. Questions for Clarification

None. Both new critical issues have a clear, unambiguous fix.

---

## 6. Final Recommendation

**Approve with changes.**

Fix C1 (one function in `NewShiftPanel.tsx`) and C2 (add `formatLocalDate` helper to three files and replace four call sites). Both are small, targeted edits. The plan is otherwise clean and ready to execute once these two remaining UTC handling issues are resolved.
