# Critical Implementation Review — Phase 5: Wire Dashboard
**Review #3** | Reviewed against: `2026-04-06-frontend-phase-5-wire-dashboard.md`
Previous reviews: `critical-review-1.md` (6 critical issues), `critical-review-2.md` (2 critical issues).

---

## 1. Overall Assessment

Both critical issues from review-2 (CI-7 missing i18n keys, CI-8 simultaneous error+data render) have been correctly resolved. All eight critical issues across the two prior reviews are now addressed. The implementation is structurally correct, type-safe (zero `tsc` errors confirmed), and uses React Query v5.96.2 correctly. No new critical issues were found in this pass. The four minor items raised in review-2 remain open but none are blockers. This implementation is ready for Manual Test Checkpoint 5.

---

## 2. Critical Issues

None. No new or still-unresolved critical issues found.

---

## 3. Previously Addressed Items

**From review-2 (resolved since then):**
- **CI-7** — `t('loading')` and `t('error')` missing from `dashboard.json`: both keys now present with correct copy.
- **CI-8** — Simultaneous error+data render on background refetch failure: both guards now use `&& !data`, making loading and error states exclusive to the initial-load path.

**From review-1 (carried forward, still resolved):**
- **CI-1** — `DashboardTodayResponse` type conflict: existing type used, not overwritten.
- **CI-2** — `DashboardAlert` type conflict: existing `type/subject/resourceId/resourceType` shape preserved.
- **CI-3** — `DashboardVisitRow` field narrowing: `caregiverId`, `serviceTypeName`, `evvStatusReason` intact.
- **CI-4** — Shell.tsx full replacement: targeted three-line edit only; SlidePanel/PanelContent system intact.
- **CI-5** — i18n and design tokens: `useTranslation` and localized date retained; Tailwind tokens used.
- **CI-6** — StatTiles wrong props: all four correct props passed.

---

## 4. Minor Issues & Improvements

These were all raised in review-2 and remain open. Restated here for completeness only — no new minor issues found.

- **Global polling on non-dashboard routes (review-2):** `Shell.tsx` subscribes to `useDashboard()` unconditionally with `refetchInterval: 60_000`. Confirmed with React Query v5.96.2: `refetchIntervalInBackground` defaults to `false`, so polling pauses when the tab is hidden. However, on any non-dashboard route where the tab is in focus, the 60s poll still fires. Low impact for a small-agency SaaS but worth addressing if backend load becomes a concern.

- **No unit tests (review-2):** `api/dashboard.ts`, `hooks/useDashboard.ts`, and the updated `DashboardPage.tsx` have no test files. CLAUDE.md targets 80% frontend coverage. The three loading/error/data branches in `DashboardPage` are the highest-value test targets.

- **`style={{ width: 220 }}` inline style on alerts column (review-2):** Should be `w-[220px]` Tailwind class. Cosmetic inconsistency, not a functional issue.

- **Top bar always renders during loading/error (review-2):** Header and date visible while content area shows spinner. Accepted as-is for now.

---

## 5. Questions for Clarification

1. **Backend field names (carried from review-2):** Has `GET /api/v1/dashboard/today` been confirmed to return `yellowEvvCount`, `uncoveredCount`, and `onTrackCount`? If the backend diverges, all three stat tiles silently render `0`. This is the only remaining verification that cannot be done at compile time and must be confirmed during Checkpoint 5.

---

## 6. Final Recommendation

**Approve as-is.** All critical issues are resolved. The implementation is correct and safe to test. Proceed to Manual Test Checkpoint 5. The four remaining minor items are good candidates for a follow-up cleanup PR after the checkpoint passes.
