# Phase 6: Wire Clients Screen

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace mock data in the clients screen with real API data — paginated client list, client detail panel showing real demographics, and authorization utilization bars sourced from the backend.

**Before starting:**
- Phase 3 (auth wiring) must be complete.
- `frontend/src/api/clients.ts` was created in Phase 4 with `listClients` and `getClient`. This phase expands it with additional endpoints.
- `frontend/src/hooks/useClients.ts` was created in Phase 4 with `useClients` and `useClientDetail`. This phase adds authorization hooks.

---

### Task 1: Expand clients.ts with Additional Endpoints

**Files:**
- Modify: `frontend/src/api/clients.ts`

- [ ] **Step 1.1: Replace clients.ts with expanded version**

Replace the full contents of `frontend/src/api/clients.ts`:

```ts
import { apiClient } from './client'
import type { ClientResponse, AuthorizationResponse, PageResponse } from '../types/api'

export async function listClients(page = 0, size = 20): Promise<PageResponse<ClientResponse>> {
  const response = await apiClient.get<PageResponse<ClientResponse>>('/clients', {
    params: { page, size, sort: 'lastName' },
  })
  return response.data
}

export async function getClient(id: string): Promise<ClientResponse> {
  const response = await apiClient.get<ClientResponse>(`/clients/${id}`)
  return response.data
}

export async function listAuthorizations(
  clientId: string,
  page = 0,
  size = 20,
): Promise<PageResponse<AuthorizationResponse>> {
  const response = await apiClient.get<PageResponse<AuthorizationResponse>>(
    `/clients/${clientId}/authorizations`,
    { params: { page, size } },
  )
  return response.data
}

export async function listCarePlans(clientId: string, page = 0, size = 20): Promise<PageResponse<unknown>> {
  const response = await apiClient.get<PageResponse<unknown>>(
    `/clients/${clientId}/care-plans`,
    { params: { page, size } },
  )
  return response.data
}

export async function listDiagnoses(clientId: string, page = 0, size = 50): Promise<PageResponse<unknown>> {
  const response = await apiClient.get<PageResponse<unknown>>(
    `/clients/${clientId}/diagnoses`,
    { params: { page, size } },
  )
  return response.data
}

export async function listMedications(clientId: string, page = 0, size = 50): Promise<PageResponse<unknown>> {
  const response = await apiClient.get<PageResponse<unknown>>(
    `/clients/${clientId}/medications`,
    { params: { page, size } },
  )
  return response.data
}

export async function listFamilyPortalUsers(clientId: string): Promise<PageResponse<unknown>> {
  const response = await apiClient.get<PageResponse<unknown>>(
    `/clients/${clientId}/family-portal-users`,
  )
  return response.data
}
```

- [ ] **Step 1.2: Ensure AuthorizationResponse type exists in types/api.ts**

Open `frontend/src/types/api.ts` and confirm the following type is present. Add if missing:

```ts
// Add to frontend/src/types/api.ts if not already present

export interface AuthorizationResponse {
  id: string
  clientId: string
  payerId: string
  serviceTypeId: string
  agencyId: string
  authNumber: string
  authorizedUnits: number
  usedUnits: number
  unitType: 'HOURS' | 'VISITS'
  startDate: string // ISO-8601 LocalDate
  endDate: string
  createdAt: string
}
```

- [ ] **Step 1.3: Commit**

```bash
cd frontend && git add src/api/clients.ts src/types/api.ts
git commit -m "feat: expand clients API with authorizations, care-plans, diagnoses, medications, family-portal-users"
```

---

### Task 2: Expand useClients Hook with Authorization Hooks

**Files:**
- Modify: `frontend/src/hooks/useClients.ts`

- [ ] **Step 2.1: Replace useClients.ts with expanded version**

Replace the full contents of `frontend/src/hooks/useClients.ts`:

```ts
import { useQuery } from '@tanstack/react-query'
import { listClients, getClient, listAuthorizations } from '../api/clients'
import type { ClientResponse } from '../types/api'
import { useMemo } from 'react'

/**
 * Fetches all clients for lookup maps (page size 100).
 * Used by Schedule and other screens that need to resolve clientId → name.
 */
export function useClients(page = 0, size = 20) {
  const query = useQuery({
    queryKey: ['clients', page, size],
    queryFn: () => listClients(page, size),
    staleTime: 60_000,
  })

  const clientMap = useMemo<Map<string, ClientResponse>>(() => {
    if (!query.data?.content) return new Map()
    return new Map(query.data.content.map((c) => [c.id, c]))
  }, [query.data])

  return {
    ...query,
    clients: query.data?.content ?? [],
    clientMap,
    totalPages: query.data?.totalPages ?? 0,
    totalElements: query.data?.totalElements ?? 0,
  }
}

/**
 * Fetches all clients with a large page size for in-memory lookup maps.
 * Prefer this over useClients() when you need a Map<id, client>.
 */
export function useAllClients() {
  const query = useQuery({
    queryKey: ['clients', 'all'],
    queryFn: () => listClients(0, 100),
    staleTime: 60_000,
  })

  const clientMap = useMemo<Map<string, ClientResponse>>(() => {
    if (!query.data?.content) return new Map()
    return new Map(query.data.content.map((c) => [c.id, c]))
  }, [query.data])

  return {
    ...query,
    clients: query.data?.content ?? [],
    clientMap,
  }
}

export function useClientDetail(id: string | null) {
  return useQuery({
    queryKey: ['client', id],
    queryFn: () => getClient(id!),
    enabled: Boolean(id),
  })
}

export function useClientAuthorizations(clientId: string | null) {
  return useQuery({
    queryKey: ['client-authorizations', clientId],
    queryFn: () => listAuthorizations(clientId!, 0, 50),
    enabled: Boolean(clientId),
  })
}
```

- [ ] **Step 2.2: Update Schedule-screen imports to use useAllClients**

In `frontend/src/components/schedule/SchedulePage.tsx` and `frontend/src/components/schedule/ShiftDetailPanel.tsx`, update the import of `useClients` to `useAllClients` where the lookup map is needed:

In `SchedulePage.tsx`:
```tsx
// Change:
import { useClients } from '../../hooks/useClients'
// To:
import { useAllClients } from '../../hooks/useClients'
// And update usage:
const { clientMap } = useAllClients()
```

In `ShiftDetailPanel.tsx`:
```tsx
// Change:
import { useClients } from '../../hooks/useClients'
// To:
import { useAllClients } from '../../hooks/useClients'
// And update usage:
const { clientMap } = useAllClients()
```

- [ ] **Step 2.3: Commit**

```bash
cd frontend && git add src/hooks/useClients.ts \
  src/components/schedule/SchedulePage.tsx \
  src/components/schedule/ShiftDetailPanel.tsx
git commit -m "feat: expand useClients hook with useAllClients and useClientAuthorizations"
```

---

### Task 3: Update ClientsPage to Use Real Data

**Files:**
- Modify: `frontend/src/components/clients/ClientsPage.tsx`

- [ ] **Step 3.1: Update ClientsPage**

Replace the full contents of `frontend/src/components/clients/ClientsPage.tsx`:

```tsx
import { useState } from 'react'
import { ClientsTable } from './ClientsTable'
import { ClientDetailPanel } from './ClientDetailPanel'
import { usePanelStore } from '../../store/panelStore'
import { useClients } from '../../hooks/useClients'

export function ClientsPage() {
  const [page, setPage] = useState(0)
  const { clients, isLoading, isError, totalPages, totalElements } = useClients(page, 20)
  const panel = usePanelStore()

  if (isLoading) {
    return (
      <div
        className="flex items-center justify-center h-full"
        style={{ backgroundColor: '#f6f6fa' }}
      >
        <span className="text-sm" style={{ color: '#94a3b8' }}>Loading clients…</span>
      </div>
    )
  }

  if (isError) {
    return (
      <div
        className="flex items-center justify-center h-full"
        style={{ backgroundColor: '#f6f6fa' }}
      >
        <p className="text-sm" style={{ color: '#dc2626' }}>Failed to load clients.</p>
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
          <h1 className="text-lg font-semibold" style={{ color: '#1a1a24' }}>Clients</h1>
          <p className="text-xs mt-0.5" style={{ color: '#94a3b8' }}>
            {totalElements} total
          </p>
        </div>
      </div>

      {/* Table */}
      <div className="flex-1 overflow-auto p-6">
        <ClientsTable
          clients={clients}
          onClientClick={(id) => panel.openPanel('clientDetail', id)}
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
      {panel.type === 'clientDetail' && panel.id && (
        <ClientDetailPanel clientId={panel.id} onClose={panel.closePanel} />
      )}
    </div>
  )
}
```

- [ ] **Step 3.2: Commit**

```bash
cd frontend && git add src/components/clients/ClientsPage.tsx
git commit -m "feat: wire ClientsPage to real API with pagination"
```

---

### Task 4: Update ClientDetailPanel to Use Real Data

**Files:**
- Modify: `frontend/src/components/clients/ClientDetailPanel.tsx`

- [ ] **Step 4.1: Update ClientDetailPanel**

Replace the full contents of `frontend/src/components/clients/ClientDetailPanel.tsx`:

```tsx
import { SlidePanel } from '../panel/SlidePanel'
import { useClientDetail, useClientAuthorizations } from '../../hooks/useClients'
import type { AuthorizationResponse } from '../../types/api'

interface ClientDetailPanelProps {
  clientId: string
  onClose: () => void
}

function UtilizationBar({ auth }: { auth: AuthorizationResponse }) {
  const pct =
    auth.authorizedUnits > 0
      ? Math.min(100, Math.round((auth.usedUnits / auth.authorizedUnits) * 100))
      : 0
  const color = pct >= 80 ? '#dc2626' : pct >= 60 ? '#ca8a04' : '#16a34a'

  return (
    <div className="mb-3">
      <div className="flex items-center justify-between mb-1">
        <span className="text-xs" style={{ color: '#747480' }}>
          Auth #{auth.authNumber}
        </span>
        <span className="text-xs font-medium" style={{ color }}>
          {auth.usedUnits.toFixed(1)} / {auth.authorizedUnits.toFixed(1)}{' '}
          {auth.unitType === 'HOURS' ? 'hrs' : 'visits'} ({pct}%)
        </span>
      </div>
      <div
        className="w-full rounded-full h-1.5"
        style={{ backgroundColor: '#eaeaf2' }}
      >
        <div
          className="h-1.5 rounded-full transition-all"
          style={{ width: `${pct}%`, backgroundColor: color }}
        />
      </div>
      <p className="text-xs mt-0.5" style={{ color: '#94a3b8' }}>
        Expires {new Date(auth.endDate).toLocaleDateString('en-US', {
          month: 'short',
          day: 'numeric',
          year: 'numeric',
        })}
      </p>
    </div>
  )
}

export function ClientDetailPanel({ clientId, onClose }: ClientDetailPanelProps) {
  const { data: client, isLoading, isError } = useClientDetail(clientId)
  const { data: authsPage } = useClientAuthorizations(clientId)

  const authorizations = authsPage?.content ?? []

  if (isLoading) {
    return (
      <SlidePanel title="Client Detail" onClose={onClose}>
        <div className="flex items-center justify-center h-32">
          <span className="text-sm" style={{ color: '#94a3b8' }}>Loading…</span>
        </div>
      </SlidePanel>
    )
  }

  if (isError || !client) {
    return (
      <SlidePanel title="Client Detail" onClose={onClose}>
        <div className="flex items-center justify-center h-32">
          <span className="text-sm" style={{ color: '#dc2626' }}>Failed to load client.</span>
        </div>
      </SlidePanel>
    )
  }

  return (
    <SlidePanel title={`${client.firstName} ${client.lastName}`} onClose={onClose}>
      <div className="p-4 space-y-5">
        {/* Demographics */}
        <section>
          <p className="text-xs font-semibold uppercase mb-2" style={{ color: '#94a3b8' }}>
            Demographics
          </p>
          <dl className="space-y-1">
            <div className="flex justify-between">
              <dt className="text-xs" style={{ color: '#747480' }}>Date of Birth</dt>
              <dd className="text-xs font-medium" style={{ color: '#1a1a24' }}>
                {new Date(client.dateOfBirth).toLocaleDateString('en-US', {
                  month: 'short',
                  day: 'numeric',
                  year: 'numeric',
                })}
              </dd>
            </div>
            {client.phone && (
              <div className="flex justify-between">
                <dt className="text-xs" style={{ color: '#747480' }}>Phone</dt>
                <dd className="text-xs font-medium" style={{ color: '#1a1a24' }}>{client.phone}</dd>
              </div>
            )}
            {client.address && (
              <div className="flex justify-between">
                <dt className="text-xs" style={{ color: '#747480' }}>Address</dt>
                <dd className="text-xs font-medium text-right" style={{ color: '#1a1a24', maxWidth: '60%' }}>
                  {client.address}
                </dd>
              </div>
            )}
            {client.medicaidId && (
              <div className="flex justify-between">
                <dt className="text-xs" style={{ color: '#747480' }}>Medicaid ID</dt>
                <dd className="text-xs font-medium" style={{ color: '#1a1a24' }}>{client.medicaidId}</dd>
              </div>
            )}
            <div className="flex justify-between">
              <dt className="text-xs" style={{ color: '#747480' }}>Service State</dt>
              <dd className="text-xs font-medium" style={{ color: '#1a1a24' }}>
                {client.serviceState ?? '—'}
              </dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-xs" style={{ color: '#747480' }}>Status</dt>
              <dd className="text-xs font-medium" style={{ color: '#1a1a24' }}>{client.status}</dd>
            </div>
          </dl>
        </section>

        {/* Preferences */}
        <section>
          <p className="text-xs font-semibold uppercase mb-2" style={{ color: '#94a3b8' }}>
            Preferences
          </p>
          <dl className="space-y-1">
            <div className="flex justify-between">
              <dt className="text-xs" style={{ color: '#747480' }}>Gender preference</dt>
              <dd className="text-xs font-medium" style={{ color: '#1a1a24' }}>
                {client.preferredCaregiverGender ?? 'No preference'}
              </dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-xs" style={{ color: '#747480' }}>No pet caregiver</dt>
              <dd className="text-xs font-medium" style={{ color: '#1a1a24' }}>
                {client.noPetCaregiver ? 'Yes' : 'No'}
              </dd>
            </div>
          </dl>
        </section>

        {/* Authorizations */}
        <section>
          <p className="text-xs font-semibold uppercase mb-2" style={{ color: '#94a3b8' }}>
            Authorizations ({authorizations.length})
          </p>
          {authorizations.length === 0 ? (
            <p className="text-xs" style={{ color: '#94a3b8' }}>No authorizations on file.</p>
          ) : (
            authorizations.map((auth) => (
              <UtilizationBar key={auth.id} auth={auth} />
            ))
          )}
        </section>
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
cd frontend && git add src/components/clients/ClientDetailPanel.tsx
git commit -m "feat: wire ClientDetailPanel to real client detail and authorization data"
```

---

## ✋ MANUAL TEST CHECKPOINT 6

**Start both servers:**

```bash
# Terminal 1 — backend
cd backend && mvn spring-boot:run

# Terminal 2 — frontend
cd frontend && npm run dev
```

**Test the clients screen:**

1. Log in and navigate to `/clients`.
2. Verify the client table renders real data (or an empty state if no clients are seeded).
3. If there are multiple pages (>20 clients), verify the pagination controls work — click "Next" and confirm a new page of clients loads.
4. Click on a client row. The detail panel should open showing real demographics (DOB, phone, address, Medicaid ID, service state).
5. Verify the authorization section shows utilization bars with correct percentages. Red bars for authorizations ≥80% used, yellow for ≥60%, green for <60%.
6. Close the panel and click a different client. Confirm the panel updates without stale data.
7. Open DevTools → Network. Confirm `GET /api/v1/clients?page=0&size=20&sort=lastName` is the first request, and `GET /api/v1/clients/{id}/authorizations` fires when a client is opened.

Proceed to Phase 7 only after this checkpoint passes.
