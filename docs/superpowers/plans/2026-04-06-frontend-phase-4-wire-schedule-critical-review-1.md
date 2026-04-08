# Critical Implementation Review 1 — Phase 4: Wire Schedule Screen

**Reviewed:** `2026-04-06-frontend-phase-4-wire-schedule.md`
**Date:** 2026-04-07

---

## 1. Overall Assessment

The plan has a sound high-level structure — create API modules, create hooks, update components — but it was written against an imagined component interface rather than the actual one. Six critical issues will produce TypeScript compilation failures before the plan finishes Task 5. None of them require architectural changes; all are correctible by adjusting the plan's code snippets to match what is actually in the repository. The plan must not be executed as written.

---

## 2. Critical Issues

### C1 — `WeekCalendar.weekStart` is `Date`; plan passes `string`

**Description:** The plan's `getWeekBounds()` helper in Task 5 returns `{ weekStart: string; weekEnd: string }` (ISO strings). That `weekStart` string is then passed to `WeekCalendar`:

```tsx
<WeekCalendar ... weekStart={weekStart} ... />
```

`WeekCalendar`'s actual prop interface (`WeekCalendar.tsx:48`):

```ts
interface WeekCalendarProps {
  weekStart: Date   // ← Date, not string
  ...
}
```

TypeScript error: `Type 'string' is not assignable to type 'Date'`.

**Fix:** Keep `currentWeekDate: Date` as the state value and pass it directly as `weekStart`. Derive the ISO strings (`weekStart: string`, `weekEnd: string`) separately for the `useShifts` query params. The `getWeekBounds` helper can return both:

```ts
function getWeekBounds(referenceDate: Date): { weekStart: Date; weekStartStr: string; weekEndStr: string }
```

Or simply compute the ISO strings inline and keep the `Date` object for `WeekCalendar`.

---

### C2 — `WeekCalendar.shifts` is `ShiftDetailResponse[]`; plan provides `ShiftSummaryResponse[]`

**Description:** `listShifts()` (plan Task 1) returns `PageResponse<ShiftSummaryResponse>`. The plan does:

```ts
const shifts = shiftsPage?.content ?? []   // ShiftSummaryResponse[]
```

`WeekCalendar`'s actual prop (`WeekCalendar.tsx:50`):

```ts
shifts: ShiftDetailResponse[]
```

`ShiftDetailResponse` extends `ShiftSummaryResponse` with `evv: EvvSummary | null`. `WeekCalendar` calls `evvStatus(shift: ShiftDetailResponse)` which accesses `shift.evv?.complianceStatus`. TypeScript error: `Type 'ShiftSummaryResponse[]' is not assignable to type 'ShiftDetailResponse[]'`.

**Fix:** Change the return type of `listShifts` in `shifts.ts` to `PageResponse<ShiftDetailResponse>` so the list endpoint returns EVV data. Verify the backend `GET /api/v1/shifts` list endpoint actually returns `evv` in its response body. If the backend returns `ShiftSummaryResponse` without `evv`, the endpoint contract needs revisiting before Phase 4 can proceed.

---

### C3 — `onEmptySlotClick` is a required prop on `WeekCalendar`; plan's SchedulePage omits it

**Description:** `WeekCalendar`'s actual interface (`WeekCalendar.tsx:53`):

```ts
onEmptySlotClick: (date: Date, hour: number) => void  // no `?`
```

The plan's SchedulePage (`Task 5.1`) does not pass this prop to `WeekCalendar`. TypeScript error: `Property 'onEmptySlotClick' is missing in type`.

Beyond the type error, omitting this prop silently regresses UX: users can no longer click an empty calendar slot to pre-fill the new-shift form with a date and time.

**Fix:** Add a handler in the plan's SchedulePage:

```tsx
const handleEmptySlotClick = (date: Date, hour: number) => {
  const dateStr = [
    date.getFullYear(),
    String(date.getMonth() + 1).padStart(2, '0'),
    String(date.getDate()).padStart(2, '0'),
  ].join('-')
  panel.openPanel('newShift', undefined, {
    prefill: { date: dateStr, time: `${String(hour).padStart(2, '0')}:00` },
  })
}
```

Pass it as `onEmptySlotClick={handleEmptySlotClick}` and restore the `prefill` prop on `NewShiftPanel`.

---

### C4 — Plan uses `openPanel('shiftDetail', …)` — `'shiftDetail'` is not in `PanelType`

**Description:** The plan's SchedulePage calls:

```tsx
onShiftClick={(shiftId) => panel.openPanel('shiftDetail', shiftId)}
```

`panelStore.ts:3–9` defines:

```ts
export type PanelType = 'shift' | 'newShift' | 'client' | 'caregiver' | 'payer' | null
```

`'shiftDetail'` does not exist. TypeScript error:
`Argument of type '"shiftDetail"' is not assignable to parameter of type '"shift" | "newShift" | "client" | "caregiver" | "payer"'`.

Additionally, the plan moves panel rendering out of `Shell.tsx/PanelContent` into `SchedulePage` itself. Shell's `PanelContent` will continue to match on `type === 'shift'` and attempt to render the *old* `ShiftDetailPanel` interface (`backLabel` prop). This creates a double-render bug the moment a shift click fires.

**Fix:** Either:
- (a) Use the existing type name: `panel.openPanel('shift', shiftId)`. Keep panel rendering in `Shell.tsx/PanelContent` and update `PanelContent` to pass `onClose={closePanel}` instead of `backLabel={backLabel}` once `ShiftDetailPanel` is updated. Remove the inline panel rendering from the plan's SchedulePage.
- (b) Add `'shiftDetail'` to `PanelType` in `panelStore.ts` as a migration step, update `Shell/PanelContent` accordingly, and remove the stale `'shift'` branch. This requires touching panelStore, Shell, and SchedulePage atomically.

Option (a) is simpler and matches the existing architecture.

---

### C5 — `SlidePanel` props mismatch in plan's ShiftDetailPanel and NewShiftPanel

**Description:** The plan's `ShiftDetailPanel` (Task 6) and `NewShiftPanel` (Task 7) both import and use `SlidePanel`:

```tsx
<SlidePanel title="Shift Detail" onClose={onClose}>
```

`SlidePanel`'s actual interface (`SlidePanel.tsx:3–7`):

```ts
interface SlidePanelProps {
  isOpen: boolean      // required — missing in plan
  onClose: () => void
  children: React.ReactNode
  // no `title` prop
}
```

Two TypeScript errors per panel:
- `Property 'isOpen' is missing in type`
- `Property 'title' does not exist on type 'SlidePanelProps'`

**Fix:** The plan's replacement components should not import `SlidePanel` directly — that's Shell's job. The panel components should just render their own content (a `div` with their layout), as the existing `ShiftDetailPanel` and `NewShiftPanel` do. Shell wraps them in `SlidePanel`. Remove the `SlidePanel` imports and wrappers from the plan's Task 6 and Task 7 snippets.

---

### C6 — Tasks 5 → 6 → 7 commit broken TypeScript between steps

**Description:** Task 5 commits a `SchedulePage` that references the new `ShiftDetailPanel` interface (`onClose: () => void`), but that interface doesn't exist until Task 6. Similarly Task 5 references `NewShiftPanel` without `prefill`/`backLabel` (new interface), but the old interface is still in place. Step 5.2 explicitly says "if prop-type mismatches, fix them in the next steps" — then Step 5.3 says commit. Committing with known TypeScript errors violates CLAUDE.md's "never commit with failing tests" rule (the TS check runs in CI).

**Fix:** Either:
- Update Tasks 5, 6, and 7 to land in a single commit, or
- Reorder so panel components (Tasks 6, 7) are updated *before* SchedulePage (Task 5) references their new interfaces.

---

## 3. Minor Issues

### M1 — `CreateShiftRequest` and `AssignCaregiverRequest` are redefined; both already exist in `types/api.ts`

`types/api.ts` already exports `CreateShiftRequest` (line 106) and `AssignCaregiverRequest` (line 119). The plan's `shifts.ts` (Task 1) re-declares both locally. The comment in the plan says "import type … from '../types/api'" but the code snippets define them inline, which will create a duplicate-identifier error if both exist in scope, or a silent drift if only the local copy is used.

**Fix:** Remove the local declarations from `shifts.ts`. Import from `types/api`:

```ts
import type { ..., CreateShiftRequest, AssignCaregiverRequest } from '../types/api'
```

Note: the plan's local `CreateShiftRequest` uses `caregiverId?: string | null` while `types/api.ts` uses `caregiverId?: string`. Prefer the `types/api.ts` version; if `null` must be sent to clear an assignment, update the canonical type there rather than duplicating it.

---

### M2 — `getWeekBounds` uses `toISOString()` — UTC offset will give wrong week boundary for non-UTC users

`toISOString()` always emits UTC. For a user in UTC−5 at 22:00 local time, `new Date()` is already the next UTC day. The computed Monday start will be off by one day. The existing `handleNewShift` function in the current `SchedulePage` avoids this by using `getFullYear()`/`getMonth()`/`getDate()` directly. The plan's helper should do the same:

```ts
const pad = (n: number) => String(n).padStart(2, '0')
const fmt = (dt: Date) =>
  `${dt.getFullYear()}-${pad(dt.getMonth() + 1)}-${pad(dt.getDate())}T00:00:00`
```

---

### M3 — No test steps for any new API modules or hooks

CLAUDE.md: 80% frontend unit test coverage (Vitest + Testing Library). Tasks 1–4 add six new files (`api/shifts.ts`, `api/clients.ts`, `api/caregivers.ts`, `hooks/useShifts.ts`, `hooks/useClients.ts`, `hooks/useCaregivers.ts`) with no corresponding test steps. The hooks are straightforward to test with `axios-mock-adapter` (already installed as a dev dependency since Phase 3) and `renderHook` from Testing Library. Without tests, the coverage threshold will be missed and CI will block the merge.

---

### M4 — Inline hex values instead of Tailwind tokens

CLAUDE.md requires using project Tailwind tokens instead of raw hex values. The plan's SchedulePage, ShiftDetailPanel, and NewShiftPanel snippets use `style={{ color: '#1a1a24' }}`, `style={{ backgroundColor: '#1a9afa' }}`, etc. throughout. These should use `text-text-primary`, `bg-blue`, `text-text-secondary`, `bg-surface`, `border-border`, etc.

---

## 4. Questions for Clarification

- **Q1:** Does `GET /api/v1/shifts` return the `evv` object in list responses, or only in the detail endpoint `GET /api/v1/shifts/{id}`? If the list endpoint returns `ShiftSummaryResponse` (no `evv`), EVV status badges cannot be shown on the `WeekCalendar` shift blocks, and the approach in C2 needs a different resolution.
- **Q2:** Should `ShiftDetailPanel` and `NewShiftPanel` continue to use `usePanelStore` for closing, or should they accept an `onClose` callback prop (decoupling them from the store)? The plan moves to callback props, but that requires updating Shell's `PanelContent` dispatch as well — is that in scope for Phase 4?

---

## 5. Final Recommendation

**Do not execute. Revise first.**

C1–C6 will all produce TypeScript errors; C4 introduces a double-render runtime bug. None require architectural rethinking — they are mismatches between the plan's code snippets and the actual file interfaces. Fix the snippets, reorder or batch the commits so no intermediate state breaks TypeScript, and add test steps for the new API/hook files before executing.
