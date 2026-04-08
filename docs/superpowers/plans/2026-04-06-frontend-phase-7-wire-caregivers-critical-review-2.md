# Critical Implementation Review 2 — Phase 7: Wire Caregivers Screen

**Plan reviewed:** `docs/superpowers/plans/2026-04-06-frontend-phase-7-wire-caregivers.md`  
**Previous reviews:** review-1 (2026-04-07)  
**Reviewer date:** 2026-04-07

---

## 1. Overall Assessment

The revision is a substantial improvement. All four build-breaking critical issues from review-1 have been fully resolved: the `SlidePanel` API misuse is gone, the panel type problem is gone (correctly reuses `'caregiver'`), the `panel.id` bug is gone, and `NewShiftPanel` is now included in the migration. All six minor issues from review-1 are addressed. The plan is nearly approvable — four new items emerged on this pass, the most impactful being raw ISO date strings in `BackgroundCheckRow` and a react-i18next pluralization hazard in the `totalCaregivers` key. Neither is a build failure, but both will produce visibly wrong output.

---

## 2. Critical Issues

None new.

---

## 3. Previously Addressed Items

All issues raised in review-1 are resolved:

- **CI-1 (SlidePanel API mismatch):** `CaregiverDetailPanel` now follows the `ClientDetailPanel` pattern — `flex flex-col h-full` root, no `SlidePanel` usage, `usePanelStore().closePanel()` for close actions. ✓
- **CI-2 (PanelType missing 'caregiverDetail'):** Resolved by correctly reusing the existing `'caregiver'` panel type. No `panelStore` changes needed. ✓
- **CI-3 (`panel.id` vs `panel.selectedId`):** Resolved by removing direct panel state management from `CaregiversPage` entirely. ✓
- **CI-4 (`NewShiftPanel.tsx` not in Task 2.2):** All three schedule consumers now updated to `useAllCaregivers`. ✓
- **MI-1 (Ambiguous "Add if missing" for `CredentialResponse`):** Step 1.2 now explicitly instructs adding `agencyId` to the existing interface. ✓
- **MI-2 (Timezone off-by-one in `CredentialRow`):** Uses `new Date(\`${cred.expiryDate}T12:00:00\`)` with explanatory comment; display uses `formatLocalDate`. ✓
- **MI-3 (`BackgroundCheckResponse` not wired):** `listBackgroundChecks` now typed `PageResponse<BackgroundCheckResponse>`; `BackgroundCheckRow` is a fully typed sub-component. ✓
- **MI-4 (Design token regression):** Tailwind tokens used throughout `CaregiversPage` and `CaregiverDetailPanel`. ✓
- **MI-5 (i18n regression):** Task 0 adds missing locale keys; `useTranslation('caregivers')` and `tCommon` used consistently. ✓
- **MI-6 (Search input regression):** Search input preserved in `CaregiversPage`. ✓

---

## 4. Minor Issues & Improvements

### MI-A — `BackgroundCheckRow` renders ISO date strings raw (no `formatLocalDate`)

**Description:** The plan renders:
```tsx
<p className="text-[11px] text-text-secondary mt-1">
  Checked: {bc.checkedAt}
  {bc.renewalDueDate ? ` · Renewal due: ${bc.renewalDueDate}` : ''}
</p>
```
`bc.checkedAt` and `bc.renewalDueDate` are `ISO-8601 LocalDate` strings (e.g. `"2026-12-31"`). These will display as-is rather than locale-formatted. The same date-formatting fix applied to `CredentialRow` (using `formatLocalDate`) was not applied here.

**Fix:** Format both dates with `formatLocalDate` — requires passing `locale` to `BackgroundCheckRow`:
```tsx
function BackgroundCheckRow({ bc, locale }: { bc: BackgroundCheckResponse; locale: string }) {
  // ...
  <p className="text-[11px] text-text-secondary mt-1">
    Checked: {formatLocalDate(bc.checkedAt, locale)}
    {bc.renewalDueDate ? ` · Renewal due: ${formatLocalDate(bc.renewalDueDate, locale)}` : ''}
  </p>
}
// And at call site:
bgChecks.map((bc) => <BackgroundCheckRow key={bc.id} bc={bc} locale={i18n.language} />)
```

---

### MI-B — `{{count}}` triggers react-i18next pluralization, not plain interpolation

**Description:** The locale key added in Task 0:
```json
"totalCaregivers": "{{count}} total"
```
…is called as:
```tsx
t('totalCaregivers', { count: totalElements })
```
In react-i18next, `count` is a reserved interpolation key that triggers plural form selection. i18next will look for `totalCaregivers_one` / `totalCaregivers_other` (depending on the locale's plural rules). Since only the base key is defined, it will fall back to `"{{count}} total"` — so the output is correct today, but this is fragile and semantically wrong.

**Fix:** Use a non-reserved variable name in both the locale file and the call site:
```json
"totalCaregivers": "{{total}} total"
```
```tsx
t('totalCaregivers', { total: totalElements })
```

---

### MI-C — Sub-queries missing `staleTime` cause a refetch on every panel open

**Description:** `useCaregiverCredentials`, `useCaregiverBackgroundChecks`, and `useCaregiverShiftHistory` all use the default `staleTime: 0`. This means credentials, background checks, and shift history are re-fetched every time the panel opens (or every time the component re-renders while mounted). The parent `useCaregiverDetail` also lacks `staleTime`. Compare to `useCaregivers` and `useAllCaregivers`, both of which set `staleTime: 60_000`.

**Fix:** Add `staleTime: 60_000` to all four queries in the expanded `useCaregivers.ts`:
```ts
export function useCaregiverDetail(id: string | null) {
  return useQuery({
    queryKey: ['caregiver', id],
    queryFn: () => getCaregiver(id!),
    enabled: Boolean(id),
    staleTime: 60_000,
  })
}
// Same for useCaregiverCredentials, useCaregiverBackgroundChecks, useCaregiverShiftHistory
```

---

### MI-D — Step 0.1 locale instruction is unclear about JSON placement

**Description:** Step 0.1 says "add the following keys inside the existing JSON object" and shows a raw JSON snippet. An implementer following this literally may paste the block verbatim — resulting in invalid JSON if they append it without removing the surrounding `{}` or adjusting for the trailing comma on the last existing key.

**Fix:** Either show the full updated `caregivers.json` file contents, or be explicit: "Add these key-value pairs as entries in the existing JSON object — ensure each new entry is preceded by a comma after the previous entry, and the closing `}` is preserved."

---

## 5. Questions for Clarification

None remaining from review-1. One new:

- **Should the credential count in the tab label (`${t('tabCredentials')} (${credentials.length})`) reflect the page count (up to 50) or the API's `totalElements`?** With `size=50`, they're equivalent for most caregivers, but for agencies with >50 credentials per caregiver, the tab would show "Credentials (50)" even if there are more. Using `credsPage?.totalElements ?? 0` is more accurate and costs nothing.

---

## 6. Final Recommendation

**Approve with changes.**

No new critical issues. MI-A (raw date strings in `BackgroundCheckRow`) and MI-B (i18next pluralization) will produce visibly wrong output in production and should be fixed before implementation. MI-C (missing `staleTime`) is a UX quality issue worth fixing in the same pass. MI-D is a clarity fix for the locale step. All four are small, targeted edits to the existing plan.
