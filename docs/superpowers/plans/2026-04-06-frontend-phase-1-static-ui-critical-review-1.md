# Critical Review 1: Phase 1 Static UI Plan

**Plan reviewed:** `docs/superpowers/plans/2026-04-06-frontend-phase-1-static-ui.md`  
**Previous reviews:** None — this is the first review.  
**Reviewer date:** 2026-04-07

---

## 1. Overall Assessment

The plan is thorough, well-structured, and production-oriented. The architecture decision (page components own data, children are purely presentational) is sound and sets up a clean API-wiring story. Task ordering is logical, TDD is applied to the most testable components, and the manual checkpoint list is comprehensive. Three issues require correction before execution: a prop typo that will calcify across files, a UTC date offset bug in schedule date formatting, and a broken UX path for the "+ Add Client/Caregiver" buttons. Several minor issues are also noted below.

---

## 2. Critical Issues

### C1 — `caregiverVId` prop typo embeds a naming defect (Tasks 6, 13, 14)

**Description:**  
`CaregiverDetailPanel` declares its prop as `caregiverVId: string` (Task 13.3), and `Shell.tsx` passes `caregiverVId={selectedId}` (Task 6.2). Task 14.3 acknowledges this and explicitly states "No change needed."

**Why it matters:**  
This preserves a misspelling (the `V` is spurious — the parameter is just `caregiverId`) in two committed files. Every future component, test, or API-wiring step that references this prop must carry the typo forward, making it progressively harder to rename. The single correct moment to fix it is before it spreads.

**Fix:**  
In Task 13.3, name the prop `caregiverId: string` (not `caregiverVId`). In Task 6.2, pass `caregiverId={selectedId}`. Delete Task 14.3 entirely (no longer needed).

---

### C2 — UTC date offset bug in `SchedulePage.handleNewShift` (Task 9.7)

**Description:**  
```tsx
const dateStr = date.toISOString().split('T')[0]
```
`toISOString()` returns a UTC string. For a user in UTC−5 (e.g., Austin, TX — the agency's location), clicking an 11pm slot on April 7 produces `2026-04-08` as the pre-filled date, because 11pm local = 04:00 UTC next day.

**Why it matters:**  
Schedulers creating late-evening shifts will silently get the wrong date pre-filled. This is a real user-facing bug that survives into Phase 4 (where it wires to the actual API).

**Fix:**  
Replace with a local-date formatter:
```tsx
const dateStr = [
  date.getFullYear(),
  String(date.getMonth() + 1).padStart(2, '0'),
  String(date.getDate()).padStart(2, '0'),
].join('-')
```

---

### C3 — "+ Add Client" / "+ Add Caregiver" buttons open a "Client not found" panel (Tasks 12.2, 13.2)

**Description:**  
```tsx
onClick={() => openPanel('client', undefined, { backLabel: '← Clients' })}
```
`ClientDetailPanel` (Task 12.3) does:
```tsx
const client = mockClients.find((c) => c.id === clientId)
if (!client) { return <p>Client not found.</p> }
```
Since `clientId` is `undefined`, the panel opens and immediately shows "Client not found." — a confusing dead-end during the manual test checkpoint.

**Why it matters:**  
The manual checkpoint at the end of Phase 1 includes verifying the Clients and Caregivers pages. A tester clicking "+ Add Client" will see an error state and won't know if it's a bug or intentional. This undermines the checkpoint's purpose.

**Fix (two options, pick one):**  
- **Option A (simpler):** Change the button to `onClick={() => alert('Add Client — Phase 6 will wire this form.')}` matching the Payers page pattern.  
- **Option B (better UX):** Add a `'createClient'` panel type and a stub `CreateClientPanel` that just renders a placeholder message. Same for caregivers.

Option A is consistent with the rest of the static-UI intent and requires no new components.

---

## 3. Previously Addressed Items

None — this is the first review.

---

## 4. Minor Issues & Improvements

### M1 — Playwright `getByText('care')` is fragile (Task 16.2)

```ts
await expect(page.getByText('care')).toBeVisible()
```
Playwright's `getByText` does substring matching by default. This will match "Caregivers" in the nav as well as the wordmark `<span>care</span>`. Under normal circumstances the `<span>care</span>` will be matched first, but the test will fail if sidebar nav items render before the wordmark, or match a different element. Use a precise locator:
```ts
await expect(page.locator('nav').getByText('care', { exact: true })).toBeVisible()
```
Or target the logo container specifically with a `data-testid="logo"` on the wordmark div.

---

### M2 — Shift block overlap: no collision detection (Task 9.6)

All `ShiftBlock` elements in a day column are absolutely positioned with no overlap handling. In mock data this isn't visible (no same-day same-caregiver overlaps), but during manual testing if a tester clicks "+ New Shift" and creates a shift that overlaps an existing one, the blocks will overlap and obscure each other. Phase 1 doesn't need full collision resolution, but the plan should add a note to the manual checkpoint: "Overlapping shifts will visually stack — this is expected and will be addressed post-Phase-1."

---

### M3 — `PanelType` includes `'payer'` but `Shell` has no handler (Tasks 5, 6)

`panelStore.ts` exports `'payer'` as a valid `PanelType`, and `PanelContent()` in Shell has no case for it. If `openPanel('payer', ...)` is ever mistakenly called (e.g., copy-paste in Phase 8), it silently renders nothing. Either:  
- Remove `'payer'` from `PanelType` until Phase 8 adds the handler, OR  
- Add `// Phase 8: payer panel` comment in `PanelContent()` to make the omission explicit.

---

### M4 — Step 16.4 manual dev server instruction is redundant (Task 16)

The plan says: "Open a second terminal: `npm run dev`". But `playwright.config.ts` already has a `webServer` block that starts `npm run dev` automatically (with `reuseExistingServer: true` in non-CI). If a manual dev server is already running on 5173, Playwright reuses it — no second terminal needed. Remove the "Open a second terminal" instruction to avoid confusion. Keep the comment that `reuseExistingServer: !process.env.CI` handles this.

---

### M5 — `shiftId` declared in `ShiftBlockProps` but never used (Task 9.3)

```tsx
interface ShiftBlockProps {
  shiftId: string   // passed in but destructured out entirely
  ...
}
export function ShiftBlock({ clientName, caregiverName, ... }: ShiftBlockProps) {
```
`shiftId` is in the interface but not destructured. `noUnusedLocals` won't flag interface properties, but it's a misleading contract — callers pass `shiftId` for no reason. Either remove it from the interface, or destructure it and use it as the `key` prop source (though `key` is set by the parent, not the component itself). Simplest fix: remove `shiftId` from the interface since nothing inside the component uses it.

---

## 5. Questions for Clarification

**Q1:** For "+ Add Client" (C3 above) — do you want a stub `alert()` (consistent with Payers page, minimal effort) or a placeholder `CreateClientPanel` component (better UX, more work)? The plan author left this as an open panel-to-nowhere; a decision here avoids a confusing manual test experience.

**Q2:** The mock data `mockDashboard` counts (`redEvvCount: 1`, `yellowEvvCount: 1`, `uncoveredCount: 1`) refer to April 6 shifts. Since today is April 7, the Alert Strip on the Schedule page will show these counts even when viewing the current week where those shifts are "yesterday." Is this acceptable for Phase 1's static mock, or should the mock be updated to have some April 7 shifts with EVV issues?

---

## 6. Final Recommendation

**Approve with changes.**

Fix C1 (prop typo), C2 (UTC date bug), and C3 (broken Add buttons) before executing. C1 and C3 are two-line fixes each; C2 is a one-function fix. The minor issues (M1–M5) are low-risk and can be addressed inline during execution. The overall plan is well-designed and ready to execute once these three corrections are applied.
