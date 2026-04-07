# Critical Implementation Review — Phase 5: Wire Dashboard
**Review #2** | Reviewed against: `2026-04-06-frontend-phase-5-wire-dashboard.md`
Previous review: `critical-review-1.md` (6 critical issues raised).

---

## 1. Overall Assessment

All six critical issues from review-1 were resolved in the implementation — the agents correctly read the existing codebase state rather than following the plan's flawed instructions verbatim. The core wiring (API function, hook, DashboardPage, Shell targeted edit) is structurally sound. However, two new correctness bugs were introduced in the implementation that were not covered by review-1: missing i18n translation keys that will render as literal strings, and a simultaneous error+data render condition that fires on every background refetch failure. Both are silent at TypeScript compile time and require targeted fixes before the checkpoint test.

---

## 2. Critical Issues

### CI-7 — `t('loading')` and `t('error')` are missing from `dashboard.json`

**Description:** `DashboardPage.tsx` (lines 29, 34) calls `t('loading')` and `t('error')`. Neither key exists in `frontend/public/locales/en/dashboard.json` (verified — the file has 15 keys, none of which are `loading` or `error`). In react-i18next, a missing key falls back to rendering the key name itself. Users will see the literal text **"loading"** and **"error"** in place of real messages.

**Why it matters:** This is a silent regression. TypeScript cannot catch missing translation keys. The loading and error states will always display broken text in production.

**Fix:** Add the missing keys to `frontend/public/locales/en/dashboard.json`:

```json
"loading": "Loading dashboard…",
"error": "Failed to load dashboard. Check your connection and try refreshing."
```

---

### CI-8 — `isError` and `data` can render simultaneously on background refetch failure

**Description:** `DashboardPage.tsx` uses independent `&&` guards for each state:

```tsx
{isLoading && <LoadingUI />}
{isError && <ErrorUI />}
{data && <Content />}
```

React Query's behavior with `refetchInterval`: after the initial successful load, `data` holds the last successful response and is never cleared on a subsequent failed refetch. When the 60-second polling refetch fails (network blip, 500, timeout), React Query sets `isError = true` while keeping `data` populated. Both the error banner and the full dashboard content will render simultaneously in the same flex column.

**Why it matters:** The error banner and the stat tiles / visit list / alerts column stack on top of each other. Users see duplicated or visually broken layout on any transient refetch failure — which will happen in normal operation.

**Fix:** Restructure as exclusive branches. Show the error state only on initial load failure (when `!data`); on background refetch failure, rely on the stale data and optionally show a non-intrusive toast:

```tsx
if (isLoading && !data) {
  return <LoadingUI />
}

if (isError && !data) {
  return <ErrorUI />
}

// data is guaranteed here (either fresh or stale)
return (
  <div className="flex flex-col h-full">
    {/* Top bar */}
    ...
    <StatTiles ... />
    ...
  </div>
)
```

Alternatively keep the `&&` pattern but gate the error branch on `!data`:
```tsx
{isError && !data && <ErrorUI />}
{data && <Content />}
```

---

## 3. Previously Addressed Items (from review-1)

All six critical issues raised in review-1 were resolved in the implementation:

- **CI-1** — `DashboardTodayResponse` conflict: existing type used as-is; plan's Step 1.2 was skipped entirely.
- **CI-2** — `DashboardAlert` conflict: existing type (`type`, `subject`, `resourceId`, `resourceType`) preserved; plan's conflicting fields not introduced.
- **CI-3** — `DashboardVisitRow` field narrowing: existing type with `caregiverId`, `serviceTypeName`, `evvStatusReason` left intact.
- **CI-4** — Shell.tsx full replacement: targeted three-line edit applied; SlidePanel / PanelContent system fully preserved.
- **CI-5** — i18n and design tokens: `useTranslation('dashboard')` retained, localized date preserved, design tokens used throughout (`bg-surface`, `text-text-muted`, `border-border`, etc.).
- **CI-6** — StatTiles wrong props: all four correct props passed (`redEvvCount`, `yellowEvvCount`, `uncoveredCount`, `onTrackCount`).

---

## 4. Minor Issues & Improvements

- **Global polling on non-dashboard routes:** `Shell.tsx` calls `useDashboard()` unconditionally, so `GET /dashboard/today` is polled every 60 seconds regardless of which route the user is on (schedule, clients, caregivers, etc.). React Query deduplicates the network call when both `Shell` and `DashboardPage` are mounted simultaneously, but when the user is on any other route, only `Shell` subscribes — the poll still fires. This adds unnecessary backend load on non-dashboard pages. Consider passing `refetchInterval` only to the `DashboardPage` consumer (via hook options) and having `Shell` use a static snapshot with a longer or disabled refetch interval.

- **No unit tests added:** Three new/modified files (`api/dashboard.ts`, `hooks/useDashboard.ts`, `DashboardPage.tsx`) have no accompanying tests. CLAUDE.md mandates 80% frontend coverage. The hook is straightforward to test with `renderHook` + `axios-mock-adapter`; the page loading/error/data states each need a test case.

- **`width: 220` inline style on alerts column:** The alerts column in `DashboardPage.tsx` uses `style={{ width: 220 }}` (carried over from the mock version). This should be a Tailwind class (`w-[220px]` or `w-56`) to stay consistent with the rest of the codebase.

- **Top bar always renders during loading/error:** The page title and localized date render even while `isLoading` is true and there's no content beneath them. This is not a bug but creates a jarring layout — a full-height loading spinner beneath a header is visually odd. Consider including the header inside the data-available branch, or accepting this as-is for now (not a blocker).

---

## 5. Questions for Clarification

1. **Backend contract verification:** CI-7 and CI-8 are fixed in the frontend. Have you confirmed that `GET /api/v1/dashboard/today` actually returns `yellowEvvCount`, `uncoveredCount`, and `onTrackCount` in its JSON response? If the backend is still returning the fields the plan described (`totalVisitsToday`, `completedVisits`, etc.), the stat tiles will render `undefined` cast as `0`. This should be verified during Manual Test Checkpoint 5 before proceeding.

2. **`t('error')` copy:** The error message will now be a full sentence. Should it include a "Retry" button (triggering `refetch()` from the hook), or is the plain text string sufficient for now?

---

## 6. Final Recommendation

**Approve with changes.** The structural implementation is correct and all review-1 blockers are resolved. Fix CI-7 (add missing translation keys — a 2-line change) and CI-8 (guard the error branch with `!data`) before running Manual Test Checkpoint 5. The minor items can follow in a cleanup pass.
