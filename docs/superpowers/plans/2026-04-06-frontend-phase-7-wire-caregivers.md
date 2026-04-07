# Phase 7: Wire Caregivers Screen

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace mock data in the caregivers screen with real API data — paginated caregiver list, detail panel with tabbed views for credentials (real expiry dates), background checks, and paginated shift history.

**Before starting:**
- Phase 3 (auth wiring) must be complete.
- `frontend/src/api/caregivers.ts` was created in Phase 4 with `listCaregivers` and `getCaregiver`. This phase expands it.
- `frontend/src/hooks/useCaregivers.ts` was created in Phase 4 with `useCaregivers` and `useCaregiverDetail`. This phase adds credential and shift-history hooks.

---

### Task 1: Expand caregivers.ts with Additional Endpoints

**Files:**
- Modify: `frontend/src/api/caregivers.ts`

- [ ] **Step 1.1: Replace caregivers.ts with expanded version**

Replace the full contents of `frontend/src/api/caregivers.ts`:

```ts
import { apiClient } from './client'
import type {
  CaregiverResponse,
  CredentialResponse,
  ShiftSummaryResponse,
  PageResponse,
} from '../types/api'

export async function listCaregivers(
  page = 0,
  size = 20,
): Promise<PageResponse<CaregiverResponse>> {
  const response = await apiClient.get<PageResponse<CaregiverResponse>>('/caregivers', {
    params: { page, size, sort: 'lastName' },
  })
  return response.data
}

export async function getCaregiver(id: string): Promise<CaregiverResponse> {
  const response = await apiClient.get<CaregiverResponse>(`/caregivers/${id}`)
  return response.data
}

export async function listCredentials(
  caregiverId: string,
  page = 0,
  size = 50,
): Promise<PageResponse<CredentialResponse>> {
  const response = await apiClient.get<PageResponse<CredentialResponse>>(
    `/caregivers/${caregiverId}/credentials`,
    { params: { page, size } },
  )
  return response.data
}

export async function listBackgroundChecks(
  caregiverId: string,
  page = 0,
  size = 20,
): Promise<PageResponse<unknown>> {
  const response = await apiClient.get<PageResponse<unknown>>(
    `/caregivers/${caregiverId}/background-checks`,
    { params: { page, size } },
  )
  return response.data
}

export async function listAvailability(caregiverId: string): Promise<unknown[]> {
  const response = await apiClient.get<unknown[]>(
    `/caregivers/${caregiverId}/availability`,
  )
  return response.data
}

export async function listShiftHistory(
  caregiverId: string,
  page = 0,
  size = 20,
): Promise<PageResponse<ShiftSummaryResponse>> {
  const response = await apiClient.get<PageResponse<ShiftSummaryResponse>>(
    `/caregivers/${caregiverId}/shifts`,
    { params: { page, size, sort: 'scheduledStart,desc' } },
  )
  return response.data
}
```

- [ ] **Step 1.2: Ensure CredentialResponse type exists in types/api.ts**

Open `frontend/src/types/api.ts` and confirm the following types are present. Add if missing:

```ts
// Add to frontend/src/types/api.ts if not already present

export interface CredentialResponse {
  id: string
  caregiverId: string
  agencyId: string
  credentialType: string
  issueDate: string | null   // ISO-8601 LocalDate
  expiryDate: string | null  // ISO-8601 LocalDate
  verified: boolean
  verifiedBy: string | null
  createdAt: string
}

export interface BackgroundCheckResponse {
  id: string
  caregiverId: string
  agencyId: string
  checkType: string
  result: 'PASS' | 'FAIL' | 'PENDING' | 'EXPIRED'
  checkedAt: string        // ISO-8601 LocalDate
  renewalDueDate: string | null
  createdAt: string
}
```

- [ ] **Step 1.3: Commit**

```bash
cd frontend && git add src/api/caregivers.ts src/types/api.ts
git commit -m "feat: expand caregivers API with credentials, background-checks, availability, shift-history"
```

---

### Task 2: Expand useCaregivers Hook

**Files:**
- Modify: `frontend/src/hooks/useCaregivers.ts`

- [ ] **Step 2.1: Replace useCaregivers.ts with expanded version**

Replace the full contents of `frontend/src/hooks/useCaregivers.ts`:

```ts
import { useQuery } from '@tanstack/react-query'
import {
  listCaregivers,
  getCaregiver,
  listCredentials,
  listBackgroundChecks,
  listShiftHistory,
} from '../api/caregivers'
import type { CaregiverResponse } from '../types/api'
import { useMemo } from 'react'

/**
 * Fetches a paginated list of caregivers for the caregivers screen.
 */
export function useCaregivers(page = 0, size = 20) {
  const query = useQuery({
    queryKey: ['caregivers', page, size],
    queryFn: () => listCaregivers(page, size),
    staleTime: 60_000,
  })

  const caregiverMap = useMemo<Map<string, CaregiverResponse>>(() => {
    if (!query.data?.content) return new Map()
    return new Map(query.data.content.map((c) => [c.id, c]))
  }, [query.data])

  return {
    ...query,
    caregivers: query.data?.content ?? [],
    caregiverMap,
    totalPages: query.data?.totalPages ?? 0,
    totalElements: query.data?.totalElements ?? 0,
  }
}

/**
 * Fetches all caregivers with a large page size for in-memory lookup maps.
 * Prefer this over useCaregivers() when you need a Map<id, caregiver>.
 */
export function useAllCaregivers() {
  const query = useQuery({
    queryKey: ['caregivers', 'all'],
    queryFn: () => listCaregivers(0, 100),
    staleTime: 60_000,
  })

  const caregiverMap = useMemo<Map<string, CaregiverResponse>>(() => {
    if (!query.data?.content) return new Map()
    return new Map(query.data.content.map((c) => [c.id, c]))
  }, [query.data])

  return {
    ...query,
    caregivers: query.data?.content ?? [],
    caregiverMap,
  }
}

export function useCaregiverDetail(id: string | null) {
  return useQuery({
    queryKey: ['caregiver', id],
    queryFn: () => getCaregiver(id!),
    enabled: Boolean(id),
  })
}

export function useCaregiverCredentials(caregiverId: string | null) {
  return useQuery({
    queryKey: ['caregiver-credentials', caregiverId],
    queryFn: () => listCredentials(caregiverId!, 0, 50),
    enabled: Boolean(caregiverId),
  })
}

export function useCaregiverBackgroundChecks(caregiverId: string | null) {
  return useQuery({
    queryKey: ['caregiver-background-checks', caregiverId],
    queryFn: () => listBackgroundChecks(caregiverId!, 0, 20),
    enabled: Boolean(caregiverId),
  })
}

export function useCaregiverShiftHistory(caregiverId: string | null, page = 0) {
  return useQuery({
    queryKey: ['caregiver-shifts', caregiverId, page],
    queryFn: () => listShiftHistory(caregiverId!, page, 20),
    enabled: Boolean(caregiverId),
  })
}
```

- [ ] **Step 2.2: Update Schedule-screen imports to use useAllCaregivers**

In `frontend/src/components/schedule/SchedulePage.tsx` and `frontend/src/components/schedule/ShiftDetailPanel.tsx`, update the import of `useCaregivers` to `useAllCaregivers` where the lookup map is needed:

In `SchedulePage.tsx`:
```tsx
// Change:
import { useCaregivers } from '../../hooks/useCaregivers'
// To:
import { useAllCaregivers } from '../../hooks/useCaregivers'
// And update usage:
const { caregiverMap } = useAllCaregivers()
```

In `ShiftDetailPanel.tsx`:
```tsx
// Change:
import { useCaregivers } from '../../hooks/useCaregivers'
// To:
import { useAllCaregivers } from '../../hooks/useCaregivers'
// And update usage:
const { caregiverMap } = useAllCaregivers()
```

- [ ] **Step 2.3: Commit**

```bash
cd frontend && git add src/hooks/useCaregivers.ts \
  src/components/schedule/SchedulePage.tsx \
  src/components/schedule/ShiftDetailPanel.tsx
git commit -m "feat: expand useCaregivers hook with credentials, background-checks, and shift-history hooks"
```

---

### Task 3: Update CaregiversPage to Use Real Data

**Files:**
- Modify: `frontend/src/components/caregivers/CaregiversPage.tsx`

- [ ] **Step 3.1: Update CaregiversPage**

Replace the full contents of `frontend/src/components/caregivers/CaregiversPage.tsx`:

```tsx
import { useState } from 'react'
import { CaregiversTable } from './CaregiversTable'
import { CaregiverDetailPanel } from './CaregiverDetailPanel'
import { usePanelStore } from '../../store/panelStore'
import { useCaregivers } from '../../hooks/useCaregivers'

export function CaregiversPage() {
  const [page, setPage] = useState(0)
  const { caregivers, isLoading, isError, totalPages, totalElements } = useCaregivers(page, 20)
  const panel = usePanelStore()

  if (isLoading) {
    return (
      <div
        className="flex items-center justify-center h-full"
        style={{ backgroundColor: '#f6f6fa' }}
      >
        <span className="text-sm" style={{ color: '#94a3b8' }}>Loading caregivers…</span>
      </div>
    )
  }

  if (isError) {
    return (
      <div
        className="flex items-center justify-center h-full"
        style={{ backgroundColor: '#f6f6fa' }}
      >
        <p className="text-sm" style={{ color: '#dc2626' }}>Failed to load caregivers.</p>
      </div>
    )
  }

  return (
    <div className="flex flex-col h-full" style={{ backgroundColor: '#f6f6fa' }}>
      {/* Header */}
      <div
        className="flex items-center justify-between px-6 py-4 border-b"
        style={{ backgroundColor: '#ffffff', borderColor: '#eaeaf2' }}
      >
        <div>
          <h1 className="text-lg font-semibold" style={{ color: '#1a1a24' }}>Caregivers</h1>
          <p className="text-xs mt-0.5" style={{ color: '#94a3b8' }}>
            {totalElements} total
          </p>
        </div>
      </div>

      {/* Table */}
      <div className="flex-1 overflow-auto p-6">
        <CaregiversTable
          caregivers={caregivers}
          onCaregiverClick={(id) => panel.openPanel('caregiverDetail', id)}
        />

        {/* Pagination */}
        {totalPages > 1 && (
          <div className="flex items-center justify-end gap-2 mt-4">
            <button
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              disabled={page === 0}
              className="px-3 py-1.5 rounded-lg text-sm disabled:opacity-40"
              style={{ border: '1px solid #eaeaf2', color: '#747480' }}
            >
              Prev
            </button>
            <span className="text-sm" style={{ color: '#747480' }}>
              Page {page + 1} of {totalPages}
            </span>
            <button
              onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
              disabled={page === totalPages - 1}
              className="px-3 py-1.5 rounded-lg text-sm disabled:opacity-40"
              style={{ border: '1px solid #eaeaf2', color: '#747480' }}
            >
              Next
            </button>
          </div>
        )}
      </div>

      {/* Detail Panel */}
      {panel.type === 'caregiverDetail' && panel.id && (
        <CaregiverDetailPanel caregiverId={panel.id} onClose={panel.closePanel} />
      )}
    </div>
  )
}
```

- [ ] **Step 3.2: Commit**

```bash
cd frontend && git add src/components/caregivers/CaregiversPage.tsx
git commit -m "feat: wire CaregiversPage to real API with pagination"
```

---

### Task 4: Update CaregiverDetailPanel to Use Real Data

**Files:**
- Modify: `frontend/src/components/caregivers/CaregiverDetailPanel.tsx`

- [ ] **Step 4.1: Update CaregiverDetailPanel**

Replace the full contents of `frontend/src/components/caregivers/CaregiverDetailPanel.tsx`:

```tsx
import { useState } from 'react'
import { SlidePanel } from '../panel/SlidePanel'
import {
  useCaregiverDetail,
  useCaregiverCredentials,
  useCaregiverBackgroundChecks,
  useCaregiverShiftHistory,
} from '../../hooks/useCaregivers'
import type { CredentialResponse } from '../../types/api'

type Tab = 'overview' | 'credentials' | 'background' | 'history'

interface CaregiverDetailPanelProps {
  caregiverId: string
  onClose: () => void
}

function CredentialRow({ cred }: { cred: CredentialResponse }) {
  const today = new Date()
  const expiry = cred.expiryDate ? new Date(cred.expiryDate) : null
  const daysUntilExpiry = expiry
    ? Math.ceil((expiry.getTime() - today.getTime()) / (1000 * 60 * 60 * 24))
    : null

  const expiryColor =
    daysUntilExpiry === null
      ? '#94a3b8'
      : daysUntilExpiry <= 0
      ? '#dc2626'
      : daysUntilExpiry <= 30
      ? '#ca8a04'
      : '#16a34a'

  return (
    <div
      className="flex items-center justify-between rounded-lg px-3 py-2"
      style={{ border: '1px solid #eaeaf2', backgroundColor: '#ffffff' }}
    >
      <div>
        <p className="text-sm font-medium" style={{ color: '#1a1a24' }}>
          {cred.credentialType.replace(/_/g, ' ')}
        </p>
        <p className="text-xs" style={{ color: '#747480' }}>
          {cred.verified ? 'Verified' : 'Unverified'}
        </p>
      </div>
      <div className="text-right">
        {expiry ? (
          <p className="text-xs font-semibold" style={{ color: expiryColor }}>
            {daysUntilExpiry !== null && daysUntilExpiry <= 0
              ? 'EXPIRED'
              : expiry.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })}
          </p>
        ) : (
          <p className="text-xs" style={{ color: '#94a3b8' }}>No expiry</p>
        )}
      </div>
    </div>
  )
}

export function CaregiverDetailPanel({ caregiverId, onClose }: CaregiverDetailPanelProps) {
  const [activeTab, setActiveTab] = useState<Tab>('overview')
  const [historyPage, setHistoryPage] = useState(0)

  const { data: caregiver, isLoading, isError } = useCaregiverDetail(caregiverId)
  const { data: credsPage } = useCaregiverCredentials(caregiverId)
  const { data: bgChecksPage } = useCaregiverBackgroundChecks(caregiverId)
  const { data: shiftHistoryPage } = useCaregiverShiftHistory(caregiverId, historyPage)

  const credentials = credsPage?.content ?? []
  const bgChecks = (bgChecksPage?.content ?? []) as Array<Record<string, unknown>>
  const shiftHistory = shiftHistoryPage?.content ?? []
  const shiftHistoryTotalPages = shiftHistoryPage?.totalPages ?? 0

  if (isLoading) {
    return (
      <SlidePanel title="Caregiver Detail" onClose={onClose}>
        <div className="flex items-center justify-center h-32">
          <span className="text-sm" style={{ color: '#94a3b8' }}>Loading…</span>
        </div>
      </SlidePanel>
    )
  }

  if (isError || !caregiver) {
    return (
      <SlidePanel title="Caregiver Detail" onClose={onClose}>
        <div className="flex items-center justify-center h-32">
          <span className="text-sm" style={{ color: '#dc2626' }}>Failed to load caregiver.</span>
        </div>
      </SlidePanel>
    )
  }

  const tabs: { id: Tab; label: string }[] = [
    { id: 'overview', label: 'Overview' },
    { id: 'credentials', label: `Credentials (${credentials.length})` },
    { id: 'background', label: 'Background' },
    { id: 'history', label: 'History' },
  ]

  return (
    <SlidePanel title={`${caregiver.firstName} ${caregiver.lastName}`} onClose={onClose}>
      {/* Tab bar */}
      <div
        className="flex border-b"
        style={{ borderColor: '#eaeaf2' }}
      >
        {tabs.map((tab) => (
          <button
            key={tab.id}
            onClick={() => setActiveTab(tab.id)}
            className="px-4 py-3 text-xs font-medium transition-colors"
            style={{
              color: activeTab === tab.id ? '#1a9afa' : '#747480',
              borderBottom: activeTab === tab.id ? '2px solid #1a9afa' : '2px solid transparent',
            }}
          >
            {tab.label}
          </button>
        ))}
      </div>

      <div className="p-4">
        {/* Overview tab */}
        {activeTab === 'overview' && (
          <dl className="space-y-2">
            <div className="flex justify-between">
              <dt className="text-xs" style={{ color: '#747480' }}>Email</dt>
              <dd className="text-xs font-medium" style={{ color: '#1a1a24' }}>{caregiver.email}</dd>
            </div>
            {caregiver.phone && (
              <div className="flex justify-between">
                <dt className="text-xs" style={{ color: '#747480' }}>Phone</dt>
                <dd className="text-xs font-medium" style={{ color: '#1a1a24' }}>{caregiver.phone}</dd>
              </div>
            )}
            {caregiver.address && (
              <div className="flex justify-between">
                <dt className="text-xs" style={{ color: '#747480' }}>Address</dt>
                <dd className="text-xs font-medium text-right" style={{ color: '#1a1a24', maxWidth: '60%' }}>
                  {caregiver.address}
                </dd>
              </div>
            )}
            {caregiver.hireDate && (
              <div className="flex justify-between">
                <dt className="text-xs" style={{ color: '#747480' }}>Hire Date</dt>
                <dd className="text-xs font-medium" style={{ color: '#1a1a24' }}>
                  {new Date(caregiver.hireDate).toLocaleDateString('en-US', {
                    month: 'short',
                    day: 'numeric',
                    year: 'numeric',
                  })}
                </dd>
              </div>
            )}
            <div className="flex justify-between">
              <dt className="text-xs" style={{ color: '#747480' }}>Status</dt>
              <dd className="text-xs font-medium" style={{ color: '#1a1a24' }}>{caregiver.status}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-xs" style={{ color: '#747480' }}>Has Pet</dt>
              <dd className="text-xs font-medium" style={{ color: '#1a1a24' }}>
                {caregiver.hasPet ? 'Yes' : 'No'}
              </dd>
            </div>
          </dl>
        )}

        {/* Credentials tab */}
        {activeTab === 'credentials' && (
          <div className="space-y-2">
            {credentials.length === 0 ? (
              <p className="text-xs" style={{ color: '#94a3b8' }}>No credentials on file.</p>
            ) : (
              credentials.map((cred) => <CredentialRow key={cred.id} cred={cred} />)
            )}
          </div>
        )}

        {/* Background checks tab */}
        {activeTab === 'background' && (
          <div className="space-y-2">
            {bgChecks.length === 0 ? (
              <p className="text-xs" style={{ color: '#94a3b8' }}>No background checks on file.</p>
            ) : (
              bgChecks.map((bc, i) => (
                <div
                  key={String(bc.id ?? i)}
                  className="rounded-lg px-3 py-2"
                  style={{ border: '1px solid #eaeaf2', backgroundColor: '#ffffff' }}
                >
                  <div className="flex items-center justify-between">
                    <p className="text-sm font-medium" style={{ color: '#1a1a24' }}>
                      {String(bc.checkType ?? 'Unknown').replace(/_/g, ' ')}
                    </p>
                    <span
                      className="text-xs font-semibold px-2 py-0.5 rounded"
                      style={{
                        backgroundColor:
                          bc.result === 'PASS' ? '#16a34a20' : '#dc262620',
                        color: bc.result === 'PASS' ? '#16a34a' : '#dc2626',
                      }}
                    >
                      {String(bc.result ?? '—')}
                    </span>
                  </div>
                  <p className="text-xs mt-1" style={{ color: '#747480' }}>
                    Checked:{' '}
                    {bc.checkedAt
                      ? new Date(String(bc.checkedAt)).toLocaleDateString()
                      : '—'}
                    {bc.renewalDueDate
                      ? ` · Renewal due: ${new Date(String(bc.renewalDueDate)).toLocaleDateString()}`
                      : ''}
                  </p>
                </div>
              ))
            )}
          </div>
        )}

        {/* Shift history tab */}
        {activeTab === 'history' && (
          <div>
            {shiftHistory.length === 0 ? (
              <p className="text-xs" style={{ color: '#94a3b8' }}>No shift history.</p>
            ) : (
              <div className="space-y-2">
                {shiftHistory.map((shift) => (
                  <div
                    key={shift.id}
                    className="rounded-lg px-3 py-2"
                    style={{ border: '1px solid #eaeaf2', backgroundColor: '#ffffff' }}
                  >
                    <div className="flex items-center justify-between">
                      <p className="text-xs font-medium" style={{ color: '#1a1a24' }}>
                        {new Date(shift.scheduledStart).toLocaleDateString('en-US', {
                          month: 'short',
                          day: 'numeric',
                        })}
                        {' '}
                        {new Date(shift.scheduledStart).toLocaleTimeString('en-US', {
                          hour: 'numeric',
                          minute: '2-digit',
                        })}
                        {' — '}
                        {new Date(shift.scheduledEnd).toLocaleTimeString('en-US', {
                          hour: 'numeric',
                          minute: '2-digit',
                        })}
                      </p>
                      <span
                        className="text-xs px-2 py-0.5 rounded"
                        style={{
                          backgroundColor: '#f6f6fa',
                          color: '#747480',
                          border: '1px solid #eaeaf2',
                        }}
                      >
                        {shift.status}
                      </span>
                    </div>
                  </div>
                ))}
              </div>
            )}

            {/* Shift history pagination */}
            {shiftHistoryTotalPages > 1 && (
              <div className="flex items-center justify-end gap-2 mt-4">
                <button
                  onClick={() => setHistoryPage((p) => Math.max(0, p - 1))}
                  disabled={historyPage === 0}
                  className="px-3 py-1.5 rounded-lg text-xs disabled:opacity-40"
                  style={{ border: '1px solid #eaeaf2', color: '#747480' }}
                >
                  Prev
                </button>
                <span className="text-xs" style={{ color: '#747480' }}>
                  {historyPage + 1} / {shiftHistoryTotalPages}
                </span>
                <button
                  onClick={() =>
                    setHistoryPage((p) => Math.min(shiftHistoryTotalPages - 1, p + 1))
                  }
                  disabled={historyPage === shiftHistoryTotalPages - 1}
                  className="px-3 py-1.5 rounded-lg text-xs disabled:opacity-40"
                  style={{ border: '1px solid #eaeaf2', color: '#747480' }}
                >
                  Next
                </button>
              </div>
            )}
          </div>
        )}
      </div>
    </SlidePanel>
  )
}
```

- [ ] **Step 4.2: Verify TypeScript and build**

```bash
cd frontend && npx tsc --noEmit 2>&1 | head -20
cd frontend && npm run build 2>&1 | tail -5
```

Expected: no errors.

- [ ] **Step 4.3: Commit**

```bash
cd frontend && git add src/components/caregivers/CaregiverDetailPanel.tsx
git commit -m "feat: wire CaregiverDetailPanel to real credentials, background-checks, and paginated shift history"
```

---

## ✋ MANUAL TEST CHECKPOINT 7

**Start both servers:**

```bash
# Terminal 1 — backend
cd backend && mvn spring-boot:run

# Terminal 2 — frontend
cd frontend && npm run dev
```

**Test the caregivers screen:**

1. Log in and navigate to `/caregivers`.
2. Verify the caregiver table renders real data (or empty state).
3. If there are multiple pages (>20 caregivers), verify the "Prev" / "Next" pagination works.
4. Click on a caregiver. The detail panel should open on the "Overview" tab showing real email, phone, hire date, and status.
5. Click the "Credentials" tab. Verify credentials are listed with real expiry dates. Credentials expiring within 30 days should have their date displayed in yellow; expired credentials in red; valid credentials in green.
6. Click the "Background" tab. Verify background checks are listed with PASS/FAIL badges.
7. Click the "History" tab. Verify the caregiver's shift history is listed (most recent first). If there are more than 20 shifts, verify the pagination controls work.
8. Open DevTools → Network. Confirm `GET /api/v1/caregivers/{id}/credentials`, `GET /api/v1/caregivers/{id}/background-checks`, and `GET /api/v1/caregivers/{id}/shifts` are called as tabs are opened.

Proceed to Phase 8 only after this checkpoint passes.
