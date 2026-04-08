# Critical Implementation Review — Phase 5: Wire Dashboard
**Review #1** | Reviewed against: `2026-04-06-frontend-phase-5-wire-dashboard.md`
No previous reviews found — this is the first pass.

---

## 1. Overall Assessment

The plan is well-structured and has the right goals, but it was written without inspecting the actual state of the codebase that prior phases produced. As a result, it contains several type-contract mismatches that, if followed literally, would silently break existing components and permanently regress the SlidePanel/panel system wired in Phase 4. These are not minor nits — they are implementation-blocking contradictions between the plan's proposed types and the already-shipped API contracts in `types/api.ts`, `StatTiles.tsx`, and `AlertsColumn.tsx`. Task 4 (Shell.tsx replacement) is particularly dangerous. The plan needs targeted corrections before it is safe to execute.

---

## 2. Critical Issues

### CI-1 — `DashboardTodayResponse` in the plan contradicts the already-established type in `types/api.ts`

**Description:** Step 1.2 instructs the implementer to add a `DashboardTodayResponse` with fields `totalVisitsToday`, `completedVisits`, `inProgressVisits`, `openVisits`, `redEvvCount`, `visits`, `alerts`. However, `frontend/src/types/api.ts` (lines 216–223) already defines `DashboardTodayResponse` with a completely different shape:

```ts
// Already in types/api.ts
export interface DashboardTodayResponse {
  redEvvCount: number
  yellowEvvCount: number
  uncoveredCount: number
  onTrackCount: number
  visits: DashboardVisitRow[]
  alerts: DashboardAlert[]
}
```

**Why it matters:** Step 1.2's caveat "if they were not added in Phase 1, add them now" will mislead an implementer who does not notice the existing type. If they proceed with the plan's version, `useDashboard` returns the plan's type, but `StatTiles` (which already accepts `yellowEvvCount`, `uncoveredCount`, `onTrackCount`) receives none of those props — silent TypeScript failures or broken UI. The plan's proposed `DashboardPage` passes `totalVisitsToday / completedVisits / inProgressVisits / openVisits` to `StatTiles`, which doesn't accept any of these props.

**Fix:** Remove Step 1.2 entirely. The type already exists and is already correct for the components in the codebase. The plan's proposed stat fields (`totalVisitsToday`, etc.) do not match what `StatTiles` renders — the plan must use the existing `DashboardTodayResponse` shape, or the backend contract must be reconciled first (out of scope for this phase). If the backend response uses different field names, document the discrepancy explicitly and reconcile `types/api.ts` rather than introducing a parallel type.

---

### CI-2 — `DashboardAlert` proposed type conflicts with the already-shipped `AlertsColumn` component

**Description:** The plan (Step 1.2) proposes a `DashboardAlert` with fields `alertType`, `subjectId`, `subjectName`, `detail`, `dueDate`. The already-shipped `AlertsColumn.tsx` and `types/api.ts` use: `type`, `subject`, `detail`, `dueDate`, `resourceId`, `resourceType`. These are structurally incompatible.

**Why it matters:** `AlertsColumn` reads `alert.subject`, `alert.type`, `alert.resourceId`, `alert.resourceType` to render content and open detail panels. Replacing the type with the plan's version would cause TypeScript errors at a minimum, and if TypeScript is somehow satisfied, the component would silently render blank/undefined values at runtime.

**Fix:** Do not add a conflicting `DashboardAlert` type. The existing type in `types/api.ts` (lines 207–214) must be the canonical definition. Verify that the backend `GET /api/v1/dashboard/today` response matches this existing shape; if not, the backend contract must be corrected first.

---

### CI-3 — `DashboardVisitRow` in the plan drops fields already present in `types/api.ts`

**Description:** The plan's proposed `DashboardVisitRow` omits `caregiverId`, `serviceTypeName`, and `evvStatusReason` — all three are present in the existing `types/api.ts` (lines 192–205) and may already be consumed by `VisitList.tsx`.

**Why it matters:** Replacing or shadowing the type would narrow the interface, causing TypeScript errors in `VisitList` and potentially hiding runtime data that the UI needs.

**Fix:** Do not redefine `DashboardVisitRow`. Read the existing type and verify alignment with the backend before this step.

---

### CI-4 — Step 4.1 replaces `Shell.tsx` in full, destroying the Phase 4 SlidePanel system

**Description:** The plan instructs replacing `Shell.tsx` wholesale with a minimal version containing only `<Sidebar>` and `<Outlet>`. The existing `Shell.tsx` (lines 1–50) contains a complete `PanelContent` dispatcher and `SlidePanel` integration that was wired in Phase 4 for shift detail, new shift, client, caregiver, and payer panels.

**Why it matters:** This is an outright regression. Overwriting Shell.tsx as instructed would silently delete all panel routing, making `ShiftDetailPanel`, `NewShiftPanel`, `ClientDetailPanel`, and `CaregiverDetailPanel` permanently inaccessible via the sidebar layout. There is no "verify the panels still work" step in the plan.

**Fix:** Replace Step 4.1 with a targeted edit: add the `useDashboard` import and `redEvvCount` derivation, then change the single `<Sidebar />` call to `<Sidebar redEvvCount={redEvvCount} />`. Do not replace the entire file.

---

### CI-5 — Step 3.1's `DashboardPage` replacement loses i18n, the date display, and violates design token conventions

**Description:** The existing `DashboardPage.tsx` uses `useTranslation('dashboard')` for the page title and renders a localized date string. The plan's replacement hardcodes `"Today's Overview"` as an English string and uses raw hex values (`#f6f6fa`, `#eaeaf2`, `#1a1a24`, `#94a3b8`, `#dc2626`) rather than Tailwind design tokens mandated by `CLAUDE.md`.

**Why it matters:** Hardcoding English text bypasses the i18n system already in use throughout the app. Using raw hex values instead of tokens (`bg-surface`, `border-border`, `text-dark`, `text-text-muted`) makes the component inconsistent with every other component and will silently diverge if theme values ever change.

**Fix:** Retain `useTranslation('dashboard')` and the localized date. Replace raw hex values with design tokens per `CLAUDE.md` conventions (e.g., `className="bg-surface"` instead of `style={{ backgroundColor: '#f6f6fa' }}`). The loading/error states can use the same token-based styling.

---

### CI-6 — `StatTiles` prop mismatch: the plan's `DashboardPage` passes the wrong props

**Description:** Step 3.1 passes `totalVisitsToday`, `completedVisits`, `inProgressVisits`, `openVisits`, `redEvvCount` to `StatTiles`. The actual `StatTiles` component accepts `redEvvCount`, `yellowEvvCount`, `uncoveredCount`, `onTrackCount` (lines 3–8 of `StatTiles.tsx`). The plan's props and the component's interface share only `redEvvCount` in common.

**Why it matters:** TypeScript will error, and even if suppressed, three of the four stat tiles will render `0` or `undefined`.

**Fix:** Correct the `DashboardPage` to pass `yellowEvvCount`, `uncoveredCount`, and `onTrackCount` from `data` — not the plan's fabricated visit-count fields. This is only possible after CI-1 is resolved.

---

## 3. Previously Addressed Items

None — this is the first review.

---

## 4. Minor Issues & Improvements

- **Step 4.2 is partially redundant:** `Sidebar.tsx` already accepts `redEvvCount?: number` (optional, defaults to `0`) and already shows the badge on the `/dashboard` nav item (lines 87 and 106–110). The plan treats this as "if not already implemented, add it" — but it's already there. Step 4.2 only needs to verify prop name alignment, not add badge code.

- **`refetchInterval: 60_000` in Shell:** Once `useDashboard` is also called from `Shell.tsx`, there will be two active `useQuery` subscriptions to `['dashboard', 'today']` — one in `DashboardPage` and one in `Shell`. React Query deduplicates network calls and shares the cache, so this is safe, but worth noting explicitly in the plan comments to avoid future confusion.

- **No error boundary on `DashboardPage`:** The plan adds an inline error state for the dashboard query, but `Shell.tsx` silently swallows a failed `useDashboard()` call with `data?.redEvvCount ?? 0`. This is acceptable behavior (badge simply doesn't show), but the plan should document this intentional silent-fail decision.

- **Loading shimmer omitted:** The loading state is a plain text string. Existing components in the codebase use skeleton patterns. This is a UX inconsistency, not a blocker.

- **Step 1.3 / 2.2 / 3.3 / 4.4 commit commands are shell-only:** The commit steps use relative `cd frontend &&` paths and no `git status` verification. Consider adding `npx tsc --noEmit` before each commit to catch type errors earlier.

---

## 5. Questions for Clarification

1. **Backend source of truth for stat fields:** Is the backend `GET /api/v1/dashboard/today` already returning `redEvvCount / yellowEvvCount / uncoveredCount / onTrackCount` (matching `types/api.ts`), or is it returning `totalVisitsToday / completedVisits / inProgressVisits / openVisits` (the plan's proposed shape)? This must be confirmed before any frontend work begins.

2. **`EvvComplianceStatus` ordering:** The plan proposes a different variant ordering (`GREEN | YELLOW | RED | GREY | EXEMPT | PORTAL_SUBMIT`) compared to what's already in `types/api.ts`. This doesn't affect runtime behavior but is a signal that the type was written independently of the existing code. Was this intentional?

---

## 6. Final Recommendation

**Major revisions needed** before this plan is safe to execute.

The critical blockers (CI-1 through CI-6) would cause TypeScript build failures and/or silent UI regressions if the plan is followed literally. The most dangerous is CI-4 (Shell.tsx full replacement) which would delete Phase 4's panel system. The type mismatches (CI-1, CI-2, CI-3) indicate the plan was drafted without reading the current state of `types/api.ts`.

**Minimum required changes before execution:**
1. Remove Step 1.2 (types already exist — confirm alignment with backend instead).
2. Change Step 4.1 from "replace full file" to "targeted edit: add useDashboard + pass redEvvCount to Sidebar."
3. Correct the `DashboardPage` StatTiles props to match `StatTiles`'s actual interface.
4. Replace raw hex values with design tokens in the proposed `DashboardPage` JSX.
5. Retain `useTranslation` in `DashboardPage`.
