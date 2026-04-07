# Phase 5: Wire Dashboard

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace mock data in the dashboard screen with real data from `GET /api/v1/dashboard/today`. Wire the sidebar EVV badge to the live `redEvvCount`.

**Before starting:**
- Phase 2 (dashboard endpoint) must be complete — `GET /api/v1/dashboard/today` exists and returns data.
- Phase 3 (auth wiring) must be complete — `api/client.ts` exists.
- Phase 4 (schedule wiring) may be complete but is not required.

---

### Task 0: Fix Backend DTO Contract

**Context:** The backend `GET /api/v1/dashboard/today` response does not match `frontend/src/types/api.ts`. Discovered in review-3 via live curl. Three components are broken at runtime: `StatTiles` (3 of 4 tiles show 0), `VisitList` (service type renders as "undefined"), `AlertsColumn` (all alert fields blank, panel navigation broken).

**Files to modify:**
- `backend/src/main/java/com/hcare/api/v1/dashboard/dto/DashboardTodayResponse.java`
- `backend/src/main/java/com/hcare/api/v1/dashboard/dto/DashboardVisitRow.java`
- `backend/src/main/java/com/hcare/api/v1/dashboard/dto/DashboardAlert.java`
- `backend/src/main/java/com/hcare/api/v1/dashboard/dto/AlertType.java`
- `backend/src/main/java/com/hcare/api/v1/dashboard/DashboardService.java`
- `backend/src/test/java/com/hcare/api/v1/dashboard/DashboardControllerIT.java`

- [x] **Step 0.1: Fix `DashboardTodayResponse.java`**

Replace the record fields to match `frontend/src/types/api.ts` (`DashboardTodayResponse`):

```java
public record DashboardTodayResponse(
    int redEvvCount,
    int yellowEvvCount,
    int uncoveredCount,   // open shifts with no caregiver
    int onTrackCount,     // total - red - yellow - uncovered
    List<DashboardVisitRow> visits,
    List<DashboardAlert> alerts
) {}
```

- [x] **Step 0.2: Fix `DashboardVisitRow.java`**

Add three missing fields (`caregiverId`, `serviceTypeName`, `evvStatusReason`):

```java
public record DashboardVisitRow(
    UUID shiftId,
    String clientFirstName,
    String clientLastName,
    UUID caregiverId,           // nullable
    String caregiverFirstName,  // nullable
    String caregiverLastName,   // nullable
    String serviceTypeName,
    LocalDateTime scheduledStart,
    LocalDateTime scheduledEnd,
    ShiftStatus status,
    EvvComplianceStatus evvStatus,
    String evvStatusReason      // nullable — pass null until EvvComplianceService returns reasons
) {}
```

- [x] **Step 0.3: Fix `DashboardAlert.java`**

Rename fields and add `resourceType` to match `frontend/src/types/api.ts` (`DashboardAlert`):

```java
public record DashboardAlert(
    AlertType type,        // was: alertType
    UUID resourceId,       // was: subjectId
    String subject,        // was: subjectName
    String detail,
    LocalDate dueDate,
    String resourceType    // "CAREGIVER" or "CLIENT"
) {}
```

- [x] **Step 0.4: Rename `AlertType.CREDENTIAL_EXPIRING` → `CREDENTIAL_EXPIRY`**

`frontend/src/types/api.ts` declares `DashboardAlertType` as `'CREDENTIAL_EXPIRY'`. Rename the enum value to match:

```java
public enum AlertType {
    CREDENTIAL_EXPIRY,
    BACKGROUND_CHECK_DUE,
    AUTHORIZATION_LOW
}
```

- [x] **Step 0.5: Update `DashboardService.java`**

Five changes:

1. **Add `ServiceTypeRepository` dependency** (constructor injection) to resolve `serviceTypeName` per shift.

2. **Build a `serviceTypeMap`** after the existing lookup maps:
   ```java
   Map<UUID, ServiceType> serviceTypeMap = serviceTypeRepository.findByAgencyId(agencyId).stream()
       .collect(Collectors.toMap(ServiceType::getId, st -> st));
   ```

3. **Update counters**: replace `completed / inProgress / open` with `yellow / uncovered / onTrack`:
   ```java
   int yellowCount = 0, uncoveredCount = 0;
   // red is already tracked as redCount
   // onTrackCount computed at the end as: total - red - yellow - uncovered
   ```
   `uncoveredCount` increments when `shift.getStatus() == ShiftStatus.OPEN`.
   `yellowCount` increments when `evvStatus == EvvComplianceStatus.YELLOW`.

4. **Update `DashboardVisitRow` constructor call** to pass all new fields:
   ```java
   String serviceTypeName = serviceTypeMap.containsKey(shift.getServiceTypeId())
       ? serviceTypeMap.get(shift.getServiceTypeId()).getName()
       : "Unknown";
   visitRows.add(new DashboardVisitRow(
       shift.getId(),
       client != null ? client.getFirstName() : "Unknown",
       client != null ? client.getLastName() : "Client",
       caregiver != null ? caregiver.getId() : null,
       caregiver != null ? caregiver.getFirstName() : null,
       caregiver != null ? caregiver.getLastName() : null,
       serviceTypeName,
       shift.getScheduledStart(),
       shift.getScheduledEnd(),
       shift.getStatus(),
       evvStatus,
       null  // evvStatusReason — not yet computed by EvvComplianceService
   ));
   ```

5. **Update `DashboardAlert` constructor calls** to use new field names and add `resourceType`:
   - Credential alerts: `resourceType = "CAREGIVER"`, `type = AlertType.CREDENTIAL_EXPIRY`
   - Background check alerts: `resourceType = "CAREGIVER"`, `type = AlertType.BACKGROUND_CHECK_DUE`
   - Authorization alerts: `resourceType = "CLIENT"`, `type = AlertType.AUTHORIZATION_LOW`

6. **Update `DashboardTodayResponse` constructor call**:
   ```java
   int onTrackCount = todayShifts.size() - redCount - yellowCount - uncoveredCount;
   return new DashboardTodayResponse(
       redCount,
       yellowCount,
       uncoveredCount,
       Math.max(0, onTrackCount),
       visitRows,
       alerts
   );
   ```

- [x] **Step 0.6: Update `DashboardControllerIT.java`**

Update all assertions to use the new field names. Key changes:
- Replace `body.totalVisitsToday()` with `body.onTrackCount() + body.redEvvCount() + body.yellowEvvCount() + body.uncoveredCount()` where a total count is needed, or simply assert `body.visits().size()`
- Replace `alert.alertType()` with `alert.type()`
- Replace `alert.subjectName()` with `alert.subject()`
- Replace `AlertType.CREDENTIAL_EXPIRING` with `AlertType.CREDENTIAL_EXPIRY`
- Add assertion for `serviceTypeName` on visit rows where a `ServiceType` is seeded

- [x] **Step 0.7: Verify and commit**

```bash
cd backend && mvn test -Dtest=DashboardControllerIT 2>&1 | tail -20
```

Expected: all tests pass. Then:

```bash
cd backend && git add src/main/java/com/hcare/api/v1/dashboard/ src/test/java/com/hcare/api/v1/dashboard/
git commit -m "fix: align DashboardTodayResponse DTO fields with frontend types/api.ts contract"
```

---

### Task 1: Create Dashboard API Function

**Files:**
- Create: `frontend/src/api/dashboard.ts`

- [x] **Step 1.1: Create dashboard.ts**

Create `frontend/src/api/dashboard.ts`:

```ts
import { apiClient } from './client'
import type { DashboardTodayResponse } from '../types/api'

export async function getDashboardToday(): Promise<DashboardTodayResponse> {
  const response = await apiClient.get<DashboardTodayResponse>('/dashboard/today')
  return response.data
}
```

- [x] **Step 1.2: Ensure DashboardTodayResponse type exists in types/api.ts**

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

- [x] **Step 1.3: Commit**

```bash
cd frontend && git add src/api/dashboard.ts src/types/api.ts
git commit -m "feat: add getDashboardToday API function and DashboardTodayResponse types"
```

---

### Task 2: Create useDashboard Hook

**Files:**
- Create: `frontend/src/hooks/useDashboard.ts`

- [x] **Step 2.1: Create useDashboard.ts**

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

- [x] **Step 2.2: Commit**

```bash
cd frontend && git add src/hooks/useDashboard.ts
git commit -m "feat: add useDashboard hook with 60s polling"
```

---

### Task 3: Update DashboardPage to Use Real Data

**Files:**
- Modify: `frontend/src/components/dashboard/DashboardPage.tsx`

- [x] **Step 3.1: Update DashboardPage**

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

- [x] **Step 3.2: Verify TypeScript**

```bash
cd frontend && npx tsc --noEmit 2>&1 | head -20
```

Expected: no errors. If `StatTiles`, `VisitList`, or `AlertsColumn` prop types don't match the new data shape, update them to accept the `DashboardTodayResponse` sub-types directly. They should accept strongly-typed props from `types/api.ts` rather than mock shapes.

- [x] **Step 3.3: Commit**

```bash
cd frontend && git add src/components/dashboard/DashboardPage.tsx
git commit -m "feat: wire DashboardPage to real API via useDashboard"
```

---

### Task 4: Wire EVV Badge in Shell Sidebar

**Files:**
- Modify: `frontend/src/components/layout/Shell.tsx`

- [x] **Step 4.1: Update Shell.tsx to pass live redEvvCount to Sidebar**

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

- [x] **Step 4.2: Verify Sidebar accepts redEvvCount prop**

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

- [x] **Step 4.3: Verify TypeScript and build**

```bash
cd frontend && npx tsc --noEmit 2>&1 | head -20
cd frontend && npm run build 2>&1 | tail -5
```

Expected: no errors.

- [x] **Step 4.4: Commit**

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
