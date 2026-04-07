# Critical Review 4: Phase 1 Static UI Plan

**Plan reviewed:** `docs/superpowers/plans/2026-04-06-frontend-phase-1-static-ui.md`  
**Previous reviews:** review-1, review-2, review-3  
**Reviewer date:** 2026-04-07

---

## 1. Overall Assessment

Reviews 1–3 have driven the plan to a very high level of quality. All nine previously raised critical issues are correctly resolved in the current plan, including the three review-3 additions: `AlertsColumn.tsx` now uses `formatLocalDate` for `dueDate` display, `NewShiftPanel` now shows `serviceTypeId` error messages, and the authorization date range in `ClientDetailPanel` uses `formatLocalDate` instead of raw ISO strings. The react-i18next integration added since review-3 is correctly implemented: namespaces are well-organized, `useTranslation` hooks replace all module-level string constants, locale-stable conditions (route path instead of translated label) are used for badge detection, and the test setup uses synchronous init with inline resources. No new critical issues were found. Four minor localization gaps are noted for awareness before a second language is added.

---

## 2. Critical Issues

None. No new or still-unresolved high-priority problems were found in this pass.

---

## 3. Previously Addressed Items

All issues from reviews 1–3 are correctly resolved:

- **C1 (review-1) — `caregiverVId` prop typo**: `caregiverId` used consistently throughout. ✅
- **C2 (review-1) — UTC `toISOString()` in `handleNewShift`**: Local date parts used in `SchedulePage.handleNewShift`. ✅
- **C3 (review-1) — Broken Add Client/Caregiver buttons**: Both use `alert()` stubs. ✅
- **M1 (review-1) — Fragile Playwright `getByText('care')`**: Replaced with precise `aside span` locator. ✅
- **M2 (review-1) — No overlap note in manual checkpoint**: Overlap note added. ✅
- **M4 (review-1) — Redundant second-terminal instruction**: Removed; `webServer` block documented. ✅
- **Q2 (review-1) — Stale mock dates**: All data updated to 2026-04-07. ✅
- **C1 (review-2) — `NewShiftPanel` `defaultValues.date` using `toISOString()`**: Fixed with local date parts. ✅
- **C2 (review-2) — Date-only strings parsed as UTC-midnight in ClientDetailPanel, CaregiversTable, CaregiverDetailPanel**: `formatLocalDate` helper added; four call sites replaced. ✅
- **C1 (review-3) — `AlertsColumn` `dueDate` display using raw `new Date()` on date-only string**: `formatLocalDate` added to `AlertsColumn.tsx`; display call site replaced with `t('due', { date: formatLocalDate(...) })`. ✅
- **M-new-1 (review-3) — `serviceTypeId` validation fires silently in `NewShiftPanel`**: `{errors.serviceTypeId && <p>...</p>}` added after service type select. ✅
- **M-new-2 (review-3) — Authorization date range displays as raw ISO strings**: Both `auth.startDate` and `auth.endDate` now use `formatLocalDate` with `{ month: 'short', day: 'numeric', year: 'numeric' }`. ✅

---

## 4. Minor Issues & Improvements

### M1 — `WeekCalendar` has hardcoded English strings not in i18next (Task 9.6)

Three user-visible (or screen-reader-visible) strings in `WeekCalendar.tsx` are hardcoded English, outside the i18next system:

1. **Day names in calendar header** (module-level constant):
   ```ts
   const DAY_NAMES = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']
   ```

2. **AM/PM time labels** in the time gutter:
   ```tsx
   {h === 12 ? '12pm' : h < 12 ? `${h}am` : `${h - 12}pm`}
   ```

3. **Accessibility `aria-label`** on empty slot buttons:
   ```tsx
   aria-label={`New shift at ${h}:00`}
   ```

The correct approach for (1) and (2) is `Intl.DateTimeFormat` using `i18n.language`, not translation keys — day/month names and time formats are locale conventions, not text strings. For Phase 1 (English-only), this has no user impact. Before adding a second locale, replace these with:

```tsx
// Day header — uses browser Intl with current i18n language
day.toLocaleDateString(i18n.language, { weekday: 'short' })

// Time gutter
new Date(2000, 0, 1, h).toLocaleTimeString(i18n.language, { hour: 'numeric' })
```

For (3), add `aria-label` to the translation files (`schedule` namespace) as `newShiftAt: "New shift at {{hour}}:00"`.

---

### M2 — `toLocaleDateString('en-US', ...)` hardcoded throughout (Tasks 9.7, 10.1, 11.4, 14.2)

The following functions hardcode `'en-US'` as the locale instead of using `i18n.language`:

| File | Function |
|---|---|
| `SchedulePage.tsx` | `formatWeekRange()` |
| `ShiftDetailPanel.tsx` | `formatDate()` |
| `DashboardPage.tsx` | `today` constant |
| `EvvStatusPage.tsx` | inline `toLocaleDateString` on `scheduledStart` |

For Phase 1, these are functionally identical to using `i18n.language` since `supportedLngs: ['en']` only supports English. Replace with `i18n.language` before adding a second locale:

```tsx
// Example fix — import { useTranslation } from 'react-i18next' and use i18n.language
d.toLocaleDateString(i18n.language, { month: 'short', day: 'numeric', year: 'numeric' })
```

---

### M3 — `SettingsPlaceholder` in `App.tsx` contains hardcoded English text (Task 8.2)

```tsx
function SettingsPlaceholder() {
  const { t } = useTranslation('nav')
  return <div className="p-8 text-text-secondary">{t('settings')} — coming soon</div>
}
```

`" — coming soon"` is hardcoded English adjacent to a translated string. Trivial one-key fix: add `settingsComingSoon: "Settings — coming soon"` to the `nav` namespace and replace the inline concatenation with `t('settingsComingSoon')`. (Note: `schedule.settingsComingSoon` exists in the `schedule` namespace for a different purpose — the `Broadcast Open` alert — so add this key to `nav`, not `schedule`.)

---

### M4 — `setup.ts` inline resources are a maintenance duplicate of the JSON files (Task 2.5)

`src/test/setup.ts` replicates all 10 translation namespaces as inline objects. If a new key is added to a `public/locales/en/*.json` file but omitted from `setup.ts`, unit tests will display translation keys (`"nav.settings"`) rather than real strings — a subtle regression that TypeScript won't catch.

**Mitigation for later phases:** When Phase 4+ adds new translation keys, the dev running tests will immediately see key-style output rather than real text and will know to update `setup.ts`. No structural change is needed for Phase 1; note this in the development workflow.

---

## 5. Questions for Clarification

None. The plan is clear and executable.

---

## 6. Final Recommendation

**Approve as-is.**

No critical issues remain. All review-3 fixes are correctly applied. The four minor issues above are all localization gaps that only matter when adding a second language — Phase 1 supports English only (`supportedLngs: ['en']`) and will pass its manual checkpoint without addressing them. The plan is ready to execute.
