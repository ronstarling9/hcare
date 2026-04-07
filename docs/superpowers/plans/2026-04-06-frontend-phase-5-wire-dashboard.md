# Phase 5: Wire Dashboard

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace mock data in the dashboard screen with real data from `GET /api/v1/dashboard/today`. Wire the sidebar EVV badge to the live `redEvvCount`.

**Before starting:**
- Phase 2 (dashboard endpoint) must be complete — `GET /api/v1/dashboard/today` exists and returns data.
- Phase 3 (auth wiring) must be complete — `api/client.ts` exists.
- Phase 4 (schedule wiring) may be complete but is not required.

---

### Task 1: Create Dashboard API Function

**Files:**
- Create: `frontend/src/api/dashboard.ts`

- [ ] **Step 1.1: Create dashboard.ts**

Create `frontend/src/api/dashboard.ts`:

```ts
import { apiClient } from './client'
import type { DashboardTodayResponse } from '../types/api'

export async function getDashboardToday(): Promise<DashboardTodayResponse> {
  const response = await apiClient.get<DashboardTodayResponse>('/dashboard/today')
  return response.data
}
```

- [ ] **Step 1.2: Ensure DashboardTodayResponse type exists in types/api.ts**

Open `frontend/src/types/api.ts` and confirm the following types are present. If they were not added in Phase 1, add them now:

```ts
// Add to frontend/src/types/api.ts if not already present

export type EvvComplianceStatus =
  | 'GREEN'
  | 'YELLOW'
  | 'RED'
  | 'GREY'
  | 'EXEMPT'
  | 'PORTAL_SUBMIT'

export type ShiftStatus =
  | 'OPEN'
  | 'ASSIGNED'
  | 'IN_PROGRESS'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'MISSED'

export interface DashboardVisitRow {
  shiftId: string
  clientFirstName: string
  clientLastName: string
  caregiverFirstName: string | null
  caregiverLastName: string | null
  scheduledStart: string // ISO-8601 LocalDateTime
  scheduledEnd: string
  status: ShiftStatus
  evvStatus: EvvComplianceStatus
}

export interface DashboardAlert {
  alertType: 'CREDENTIAL_EXPIRING' | 'BACKGROUND_CHECK_DUE' | 'AUTHORIZATION_LOW'
  subjectId: string
  subjectName: string
  detail: string
  dueDate: string // ISO-8601 LocalDate
}

export interface DashboardTodayResponse {
  totalVisitsToday: number
  completedVisits: number
  inProgressVisits: number
  openVisits: number
  redEvvCount: number
  visits: DashboardVisitRow[]
  alerts: DashboardAlert[]
}
```

- [ ] **Step 1.3: Commit**

```bash
cd frontend && git add src/api/dashboard.ts src/types/api.ts
git commit -m "feat: add getDashboardToday API function and DashboardTodayResponse types"
```

---

### Task 2: Create useDashboard Hook

**Files:**
- Create: `frontend/src/hooks/useDashboard.ts`

- [ ] **Step 2.1: Create useDashboard.ts**

Create `frontend/src/hooks/useDashboard.ts`:

```ts
import { useQuery } from '@tanstack/react-query'
import { getDashboardToday } from '../api/dashboard'

/**
 * Fetches today's dashboard data.
 * staleTime: 60s — dashboard stats refresh automatically every minute.
 * refetchInterval: 60_000 — polls every 60s while the tab is in focus.
 */
export function useDashboard() {
  return useQuery({
    queryKey: ['dashboard', 'today'],
    queryFn: getDashboardToday,
    staleTime: 60_000,
    refetchInterval: 60_000,
  })
}
```

- [ ] **Step 2.2: Commit**

```bash
cd frontend && git add src/hooks/useDashboard.ts
git commit -m "feat: add useDashboard hook with 60s polling"
```

---

### Task 3: Update DashboardPage to Use Real Data

**Files:**
- Modify: `frontend/src/components/dashboard/DashboardPage.tsx`

- [ ] **Step 3.1: Update DashboardPage**

Replace the full contents of `frontend/src/components/dashboard/DashboardPage.tsx`:

```tsx
import { useDashboard } from '../../hooks/useDashboard'
import { StatTiles } from './StatTiles'
import { VisitList } from './VisitList'
import { AlertsColumn } from './AlertsColumn'

export function DashboardPage() {
  const { data, isLoading, isError } = useDashboard()

  if (isLoading) {
    return (
      <div
        className="flex items-center justify-center h-full"
        style={{ backgroundColor: '#f6f6fa' }}
      >
        <span className="text-sm" style={{ color: '#94a3b8' }}>
          Loading dashboard…
        </span>
      </div>
    )
  }

  if (isError || !data) {
    return (
      <div
        className="flex items-center justify-center h-full"
        style={{ backgroundColor: '#f6f6fa' }}
      >
        <div className="text-center">
          <p className="text-sm font-medium" style={{ color: '#dc2626' }}>
            Failed to load dashboard
          </p>
          <p className="text-xs mt-1" style={{ color: '#94a3b8' }}>
            Check your connection and try refreshing.
          </p>
        </div>
      </div>
    )
  }

  return (
    <div className="flex flex-col h-full" style={{ backgroundColor: '#f6f6fa' }}>
      {/* Header */}
      <div
        className="px-6 py-4 border-b"
        style={{ backgroundColor: '#ffffff', borderColor: '#eaeaf2' }}
      >
        <h1 className="text-lg font-semibold" style={{ color: '#1a1a24' }}>
          Today's Overview
        </h1>
      </div>

      {/* Body */}
      <div className="flex-1 overflow-auto p-6">
        {/* Stat tiles */}
        <StatTiles
          totalVisitsToday={data.totalVisitsToday}
          completedVisits={data.completedVisits}
          inProgressVisits={data.inProgressVisits}
          openVisits={data.openVisits}
          redEvvCount={data.redEvvCount}
        />

        {/* Two-column layout: visit list + alerts */}
        <div className="mt-6 flex gap-6">
          <div className="flex-1 min-w-0">
            <VisitList visits={data.visits} />
          </div>
          <div className="w-80 flex-shrink-0">
            <AlertsColumn alerts={data.alerts} />
          </div>
        </div>
      </div>
    </div>
  )
}
```

- [ ] **Step 3.2: Verify TypeScript**

```bash
cd frontend && npx tsc --noEmit 2>&1 | head -20
```

Expected: no errors. If `StatTiles`, `VisitList`, or `AlertsColumn` prop types don't match the new data shape, update them to accept the `DashboardTodayResponse` sub-types directly. They should accept strongly-typed props from `types/api.ts` rather than mock shapes.

- [ ] **Step 3.3: Commit**

```bash
cd frontend && git add src/components/dashboard/DashboardPage.tsx
git commit -m "feat: wire DashboardPage to real API via useDashboard"
```

---

### Task 4: Wire EVV Badge in Shell Sidebar

**Files:**
- Modify: `frontend/src/components/layout/Shell.tsx`

- [ ] **Step 4.1: Update Shell.tsx to pass live redEvvCount to Sidebar**

Open `frontend/src/components/layout/Shell.tsx`. Add the `useDashboard` hook and pass `redEvvCount` to the `Sidebar` component.

Replace the full contents of `frontend/src/components/layout/Shell.tsx`:

```tsx
import { Outlet } from 'react-router-dom'
import { Sidebar } from './Sidebar'
import { useDashboard } from '../../hooks/useDashboard'

export function Shell() {
  const { data } = useDashboard()
  const redEvvCount = data?.redEvvCount ?? 0

  return (
    <div className="flex h-screen" style={{ backgroundColor: '#f6f6fa' }}>
      <Sidebar redEvvCount={redEvvCount} />
      <main className="flex-1 flex flex-col overflow-hidden">
        <Outlet />
      </main>
    </div>
  )
}
```

- [ ] **Step 4.2: Verify Sidebar accepts redEvvCount prop**

Open `frontend/src/components/layout/Sidebar.tsx` and confirm the component accepts a `redEvvCount: number` prop and uses it to render the EVV badge. If the prop name differs from Phase 1, reconcile it now.

The Sidebar should show the badge on the EVV nav item when `redEvvCount > 0`. If the badge is not already implemented, add it:

```tsx
// In Sidebar.tsx — locate the EVV nav link and add a badge like this:
{redEvvCount > 0 && (
  <span
    className="ml-auto text-xs font-bold rounded-full px-1.5 py-0.5 text-white"
    style={{ backgroundColor: '#dc2626', minWidth: '1.25rem', textAlign: 'center' }}
  >
    {redEvvCount}
  </span>
)}
```

- [ ] **Step 4.3: Verify TypeScript and build**

```bash
cd frontend && npx tsc --noEmit 2>&1 | head -20
cd frontend && npm run build 2>&1 | tail -5
```

Expected: no errors.

- [ ] **Step 4.4: Commit**

```bash
cd frontend && git add src/components/layout/Shell.tsx src/components/layout/Sidebar.tsx
git commit -m "feat: wire Shell sidebar EVV badge to live redEvvCount from useDashboard"
```

---

## ✋ MANUAL TEST CHECKPOINT 5

**Start both servers:**

```bash
# Terminal 1 — backend
cd backend && mvn spring-boot:run

# Terminal 2 — frontend
cd frontend && npm run dev
```

**Test the dashboard screen:**

1. Log in and navigate to `/dashboard`.
2. Verify the stat tiles show real numbers from the backend (total, completed, in-progress, open visits for today, red EVV count). All values may be 0 if no shifts exist for today — that is correct.
3. Verify the visit list renders today's shifts (empty state if none).
4. Verify the alerts column shows any expiring credentials, due background checks, or low-utilization authorizations.
5. Check the sidebar: if any visit has `RED` EVV status, a red badge with the count should appear on the EVV nav item.
6. Open DevTools → Network. Confirm `GET /api/v1/dashboard/today` is called on page load and re-called after ~60 seconds (polling).
7. Seed a shift for today via the Schedule screen (Phase 4), then navigate back to `/dashboard`. The total count should increase by 1.

Proceed to Phase 6 only after this checkpoint passes.
