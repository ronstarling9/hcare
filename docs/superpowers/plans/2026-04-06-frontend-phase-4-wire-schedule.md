# Phase 4: Wire Schedule Screen

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace all mock data imports in the schedule screen with real API calls, so shifts, clients, caregivers, EVV status, and AI candidates all come from the backend.

**Before starting:**
- Phase 3 (auth wiring) must be complete — `api/client.ts` and `authStore.ts` exist.
- Backend is running on `http://localhost:8080`.
- The user can log in and land on `/schedule`.

**Data strategy note:** `GET /api/v1/shifts` returns `Page<ShiftSummaryResponse>` containing `clientId` and `caregiverId` UUIDs but no names. This phase fetches all clients and caregivers (page size 100) separately and builds in-memory lookup maps for name resolution in the calendar grid. EVV detail (clock-in/out times, compliance status) is only available on the single-shift endpoint `GET /api/v1/shifts/{id}`; the calendar grid will show GREY EVV status for all blocks — the detail panel shows full EVV data.

**Review notes applied (Critical Reviews 1 & 2):**
- C1: `weekStart` passed to `WeekCalendar` is always a `Date`; ISO strings derived separately for API params.
- C2: `WeekCalendar.shifts` typed as `ShiftSummaryResponse[]` (matches backend list contract); Task 0 fixes this before SchedulePage is wired.
- C3: `onEmptySlotClick` prop passed to `WeekCalendar` via `handleNewShift`; prefill support retained in `NewShiftPanel`.
- C4: `openPanel('shift', id, …)` used throughout — `'shiftDetail'` never introduced. Panel rendering stays in `Shell/PanelContent`; no inline rendering in `SchedulePage`.
- C5: `ShiftDetailPanel` and `NewShiftPanel` do not import `SlidePanel` — `Shell` owns the slide animation wrapper.
- C6: `ShiftDetailPanel` (Task 5) and `NewShiftPanel` (Task 6) are updated *before* `SchedulePage` (Task 7), so every intermediate commit is TypeScript-clean.
- M1: `CreateShiftRequest` and `AssignCaregiverRequest` are imported from `types/api`, not redefined.
- M2: ISO strings use local date parts (`getFullYear`/`getMonth`/`getDate`), not `toISOString()`.
- M3: Test steps added for every new file.
- M4: Tailwind tokens used throughout; no inline hex values. Back buttons use `text-blue`; candidate rank badge uses `bg-blue`/`bg-text-muted`.
- I1 (Review 2): `getCandidates` moved into `useGetCandidates` hook; component uses the hook only.
- I2 (Review 2): `"loading"` key added to `common.json` in Task 5 commit.
- m1 (Review 2): Residual `style={{ color: '#1a9afa' }}` on interactive elements replaced with `text-blue` class.

---

### Task 0: Update WeekCalendar — accept ShiftSummaryResponse[]

**Files:**
- Modify: `frontend/src/components/schedule/WeekCalendar.tsx`

The backend list endpoint returns `ShiftSummaryResponse` (no `evv` field). `WeekCalendar` must accept this type so `SchedulePage` can pass the API response directly without a type cast.

- [ ] **Step 0.1: Update WeekCalendar shifts prop type**

In `WeekCalendar.tsx` make three changes:

1. Change the import — add `ShiftSummaryResponse` and remove `ShiftDetailResponse`:
```ts
import type { ShiftSummaryResponse, EvvComplianceStatus } from '../../types/api'
```

2. Update `evvStatus` to accept `ShiftSummaryResponse`. Because `ShiftDetailResponse` extends `ShiftSummaryResponse`, the `evv` field is absent on the base type; treat it as always absent from the list:
```ts
function evvStatus(_shift: ShiftSummaryResponse): EvvComplianceStatus {
  return 'GREY'
}
```

3. Update the `WeekCalendarProps` interface:
```ts
interface WeekCalendarProps {
  weekStart: Date
  shifts: ShiftSummaryResponse[]   // was ShiftDetailResponse[]
  clientMap: Map<string, { firstName: string; lastName: string }>
  caregiverMap: Map<string, { firstName: string; lastName: string }>
  onShiftClick: (shiftId: string) => void
  onEmptySlotClick: (date: Date, hour: number) => void
}
```

- [ ] **Step 0.2: Verify TypeScript**

```bash
cd frontend && npx tsc --noEmit 2>&1 | head -20
```

Expected: no errors. Because `ShiftDetailResponse extends ShiftSummaryResponse`, the existing `mockShifts: ShiftDetailResponse[]` passed in current `SchedulePage` is still assignable — no cascading breaks.

- [ ] **Step 0.3: Commit**

```bash
cd frontend && git add src/components/schedule/WeekCalendar.tsx
git commit -m "refactor(WeekCalendar): accept ShiftSummaryResponse[] — EVV detail is in ShiftDetailPanel, not calendar grid"
```

---

### Task 1: Create Shifts API Module

**Files:**
- Create: `frontend/src/api/shifts.ts`
- Create: `frontend/src/api/shifts.test.ts`

- [ ] **Step 1.1: Write failing tests first**

Create `frontend/src/api/shifts.test.ts`:

```ts
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import MockAdapter from 'axios-mock-adapter'
import { apiClient } from './client'
import { listShifts, getShift, createShift, assignCaregiver, broadcastShift, getCandidates, clockIn } from './shifts'
import type { ShiftSummaryResponse, ShiftDetailResponse, RankedCaregiverResponse, PageResponse } from '../types/api'

vi.mock('../store/authStore', () => ({
  useAuthStore: { getState: vi.fn().mockReturnValue({ token: 'tok', logout: vi.fn() }) },
}))

describe('shifts API', () => {
  let mock: MockAdapter

  beforeEach(() => { mock = new MockAdapter(apiClient) })
  afterEach(() => { mock.restore() })

  const summary: ShiftSummaryResponse = {
    id: 's1', agencyId: 'a1', clientId: 'c1', caregiverId: null,
    serviceTypeId: 'st1', authorizationId: null, sourcePatternId: null,
    scheduledStart: '2026-04-07T09:00:00', scheduledEnd: '2026-04-07T13:00:00',
    status: 'OPEN', notes: null,
  }

  const detail: ShiftDetailResponse = { ...summary, evv: null }

  it('listShifts calls GET /shifts with start/end params', async () => {
    const page: PageResponse<ShiftSummaryResponse> = {
      content: [summary], totalElements: 1, totalPages: 1, number: 0, size: 200, first: true, last: true,
    }
    mock.onGet('/shifts', { params: { start: '2026-04-06T00:00:00', end: '2026-04-13T00:00:00', size: 200, sort: 'scheduledStart' } }).reply(200, page)
    const result = await listShifts('2026-04-06T00:00:00', '2026-04-13T00:00:00')
    expect(result.content).toHaveLength(1)
    expect(result.content[0].id).toBe('s1')
  })

  it('getShift calls GET /shifts/:id', async () => {
    mock.onGet('/shifts/s1').reply(200, detail)
    const result = await getShift('s1')
    expect(result.id).toBe('s1')
    expect(result.evv).toBeNull()
  })

  it('createShift posts to /shifts', async () => {
    mock.onPost('/shifts').reply(201, summary)
    const result = await createShift({ clientId: 'c1', serviceTypeId: 'st1', scheduledStart: '2026-04-07T09:00:00', scheduledEnd: '2026-04-07T13:00:00' })
    expect(result.clientId).toBe('c1')
  })

  it('assignCaregiver patches /shifts/:id/assign', async () => {
    mock.onPatch('/shifts/s1/assign').reply(200, { ...summary, caregiverId: 'g1' })
    const result = await assignCaregiver('s1', 'g1')
    expect(result.caregiverId).toBe('g1')
  })

  it('broadcastShift posts to /shifts/:id/broadcast', async () => {
    mock.onPost('/shifts/s1/broadcast').reply(200)
    await expect(broadcastShift('s1')).resolves.toBeUndefined()
  })

  it('getCandidates calls GET /shifts/:id/candidates', async () => {
    const candidates: RankedCaregiverResponse[] = [{ caregiverId: 'g1', score: 95, explanation: 'Great match' }]
    mock.onGet('/shifts/s1/candidates').reply(200, candidates)
    const result = await getCandidates('s1')
    expect(result[0].caregiverId).toBe('g1')
  })

  it('clockIn posts to /shifts/:id/clock-in', async () => {
    mock.onPost('/shifts/s1/clock-in').reply(200, detail)
    const result = await clockIn('s1', { locationLat: 30.2, locationLon: -97.7, verificationMethod: 'MANUAL', capturedOffline: false })
    expect(result.id).toBe('s1')
  })
})
```

- [ ] **Step 1.2: Run tests — expect failures**

```bash
cd frontend && npm test src/api/shifts.test.ts 2>&1 | tail -15
```

Expected: all tests fail with "Cannot find module './shifts'".

- [ ] **Step 1.3: Create shifts.ts**

Create `frontend/src/api/shifts.ts`:

```ts
import { apiClient } from './client'
import type {
  ShiftSummaryResponse,
  ShiftDetailResponse,
  RankedCaregiverResponse,
  CreateShiftRequest,
  PageResponse,
} from '../types/api'

export interface ClockInRequest {
  locationLat: number
  locationLon: number
  verificationMethod: string
  capturedOffline: boolean
  deviceCapturedAt?: string | null
}

export async function listShifts(
  start: string,
  end: string,
): Promise<PageResponse<ShiftSummaryResponse>> {
  const response = await apiClient.get<PageResponse<ShiftSummaryResponse>>('/shifts', {
    params: { start, end, size: 200, sort: 'scheduledStart' },
  })
  return response.data
}

export async function getShift(id: string): Promise<ShiftDetailResponse> {
  const response = await apiClient.get<ShiftDetailResponse>(`/shifts/${id}`)
  return response.data
}

export async function createShift(req: CreateShiftRequest): Promise<ShiftSummaryResponse> {
  const response = await apiClient.post<ShiftSummaryResponse>('/shifts', req)
  return response.data
}

export async function assignCaregiver(
  shiftId: string,
  caregiverId: string,
): Promise<ShiftSummaryResponse> {
  const response = await apiClient.patch<ShiftSummaryResponse>(`/shifts/${shiftId}/assign`, {
    caregiverId,
  })
  return response.data
}

export async function broadcastShift(shiftId: string): Promise<void> {
  await apiClient.post(`/shifts/${shiftId}/broadcast`)
}

export async function getCandidates(shiftId: string): Promise<RankedCaregiverResponse[]> {
  const response = await apiClient.get<RankedCaregiverResponse[]>(`/shifts/${shiftId}/candidates`)
  return response.data
}

export async function clockIn(
  shiftId: string,
  req: ClockInRequest,
): Promise<ShiftDetailResponse> {
  const response = await apiClient.post<ShiftDetailResponse>(`/shifts/${shiftId}/clock-in`, req)
  return response.data
}
```

- [ ] **Step 1.4: Run tests — expect all green**

```bash
cd frontend && npm test src/api/shifts.test.ts 2>&1 | tail -10
```

Expected: all 7 tests pass.

- [ ] **Step 1.5: Commit**

```bash
cd frontend && git add src/api/shifts.ts src/api/shifts.test.ts
git commit -m "feat: add shifts API module (listShifts, getShift, createShift, assignCaregiver, getCandidates, clockIn, broadcastShift)"
```

---

### Task 2: Create useShifts and Related Hooks

**Files:**
- Create: `frontend/src/hooks/useShifts.ts`
- Create: `frontend/src/hooks/useShifts.test.ts`

- [ ] **Step 2.1: Write failing tests first**

Create `frontend/src/hooks/useShifts.test.ts`:

```ts
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import MockAdapter from 'axios-mock-adapter'
import { apiClient } from '../api/client'
import { useShifts, useShiftDetail, useGetCandidates } from './useShifts'
import type { PageResponse, ShiftSummaryResponse, ShiftDetailResponse, RankedCaregiverResponse } from '../types/api'
import React from 'react'

vi.mock('../store/authStore', () => ({
  useAuthStore: { getState: vi.fn().mockReturnValue({ token: 'tok', logout: vi.fn() }) },
}))

function makeWrapper() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: qc }, children)
}

describe('useShifts', () => {
  let mock: MockAdapter
  beforeEach(() => { mock = new MockAdapter(apiClient) })
  afterEach(() => { mock.restore() })

  it('returns empty content while loading, then populated content', async () => {
    const page: PageResponse<ShiftSummaryResponse> = {
      content: [{ id: 's1', agencyId: 'a1', clientId: 'c1', caregiverId: null,
        serviceTypeId: 'st1', authorizationId: null, sourcePatternId: null,
        scheduledStart: '2026-04-07T09:00:00', scheduledEnd: '2026-04-07T13:00:00',
        status: 'OPEN', notes: null }],
      totalElements: 1, totalPages: 1, number: 0, size: 200, first: true, last: true,
    }
    mock.onGet('/shifts').reply(200, page)
    const { result } = renderHook(
      () => useShifts('2026-04-06T00:00:00', '2026-04-13T00:00:00'),
      { wrapper: makeWrapper() },
    )
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data?.content[0].id).toBe('s1')
  })

  it('does not fetch when weekStart is empty', () => {
    const { result } = renderHook(() => useShifts('', ''), { wrapper: makeWrapper() })
    expect(result.current.fetchStatus).toBe('idle')
  })
})

describe('useShiftDetail', () => {
  let mock: MockAdapter
  beforeEach(() => { mock = new MockAdapter(apiClient) })
  afterEach(() => { mock.restore() })

  it('fetches shift detail when id is provided', async () => {
    const detail: ShiftDetailResponse = {
      id: 's1', agencyId: 'a1', clientId: 'c1', caregiverId: null,
      serviceTypeId: 'st1', authorizationId: null, sourcePatternId: null,
      scheduledStart: '2026-04-07T09:00:00', scheduledEnd: '2026-04-07T13:00:00',
      status: 'OPEN', notes: null, evv: null,
    }
    mock.onGet('/shifts/s1').reply(200, detail)
    const { result } = renderHook(() => useShiftDetail('s1'), { wrapper: makeWrapper() })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data?.id).toBe('s1')
  })

  it('does not fetch when id is null', () => {
    const { result } = renderHook(() => useShiftDetail(null), { wrapper: makeWrapper() })
    expect(result.current.fetchStatus).toBe('idle')
  })
})

describe('useGetCandidates', () => {
  let mock: MockAdapter
  beforeEach(() => { mock = new MockAdapter(apiClient) })
  afterEach(() => { mock.restore() })

  it('returns candidates when shiftId is provided', async () => {
    const candidates: RankedCaregiverResponse[] = [{ caregiverId: 'g1', score: 95, explanation: 'Great match' }]
    mock.onGet('/shifts/s1/candidates').reply(200, candidates)
    const { result } = renderHook(() => useGetCandidates('s1'), { wrapper: makeWrapper() })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data?.[0].caregiverId).toBe('g1')
  })

  it('does not fetch when shiftId is null', () => {
    const { result } = renderHook(() => useGetCandidates(null), { wrapper: makeWrapper() })
    expect(result.current.fetchStatus).toBe('idle')
  })
})
```

- [ ] **Step 2.2: Run tests — expect failures**

```bash
cd frontend && npm test src/hooks/useShifts.test.ts 2>&1 | tail -10
```

Expected: all tests fail with "Cannot find module './useShifts'".

- [ ] **Step 2.3: Create useShifts.ts**

Create `frontend/src/hooks/useShifts.ts`:

```ts
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  listShifts,
  getShift,
  createShift,
  assignCaregiver,
  broadcastShift,
  getCandidates,
  clockIn,
  type ClockInRequest,
} from '../api/shifts'
import type { CreateShiftRequest } from '../types/api'

export function useShifts(weekStart: string, weekEnd: string) {
  return useQuery({
    queryKey: ['shifts', weekStart, weekEnd],
    queryFn: () => listShifts(weekStart, weekEnd),
    enabled: Boolean(weekStart && weekEnd),
  })
}

export function useShiftDetail(id: string | null) {
  return useQuery({
    queryKey: ['shift', id],
    queryFn: () => getShift(id!),
    enabled: Boolean(id),
  })
}

export function useCreateShift() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (req: CreateShiftRequest) => createShift(req),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['shifts'] })
    },
  })
}

export function useAssignCaregiver() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ shiftId, caregiverId }: { shiftId: string; caregiverId: string }) =>
      assignCaregiver(shiftId, caregiverId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['shifts'] })
      queryClient.invalidateQueries({ queryKey: ['shift'] })
    },
  })
}

export function useBroadcastShift() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (shiftId: string) => broadcastShift(shiftId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['shifts'] })
    },
  })
}

export function useClockIn() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ shiftId, req }: { shiftId: string; req: ClockInRequest }) =>
      clockIn(shiftId, req),
    onSuccess: (_data, { shiftId }) => {
      queryClient.invalidateQueries({ queryKey: ['shift', shiftId] })
      queryClient.invalidateQueries({ queryKey: ['shifts'] })
    },
  })
}

export function useGetCandidates(shiftId: string | null) {
  return useQuery({
    queryKey: ['candidates', shiftId],
    queryFn: () => getCandidates(shiftId!),
    enabled: Boolean(shiftId),
  })
}
```

- [ ] **Step 2.4: Run tests — expect all green**

```bash
cd frontend && npm test src/hooks/useShifts.test.ts 2>&1 | tail -10
```

Expected: all 4 tests pass.

- [ ] **Step 2.5: Commit**

```bash
cd frontend && git add src/hooks/useShifts.ts src/hooks/useShifts.test.ts
git commit -m "feat: add useShifts, useShiftDetail, useCreateShift, useAssignCaregiver, useClockIn hooks"
```

---

### Task 3: Create Clients API Module and Hook

**Files:**
- Create: `frontend/src/api/clients.ts`
- Create: `frontend/src/hooks/useClients.ts`
- Create: `frontend/src/hooks/useClients.test.ts`

- [ ] **Step 3.1: Create clients.ts**

Create `frontend/src/api/clients.ts`:

```ts
import { apiClient } from './client'
import type { ClientResponse, PageResponse } from '../types/api'

export async function listClients(page = 0, size = 100): Promise<PageResponse<ClientResponse>> {
  const response = await apiClient.get<PageResponse<ClientResponse>>('/clients', {
    params: { page, size, sort: 'lastName' },
  })
  return response.data
}

export async function getClient(id: string): Promise<ClientResponse> {
  const response = await apiClient.get<ClientResponse>(`/clients/${id}`)
  return response.data
}
```

- [ ] **Step 3.2: Write failing hook tests**

Create `frontend/src/hooks/useClients.test.ts`:

```ts
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import MockAdapter from 'axios-mock-adapter'
import { apiClient } from '../api/client'
import { useClients } from './useClients'
import type { PageResponse, ClientResponse } from '../types/api'
import React from 'react'

vi.mock('../store/authStore', () => ({
  useAuthStore: { getState: vi.fn().mockReturnValue({ token: 'tok', logout: vi.fn() }) },
}))

function makeWrapper() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: qc }, children)
}

const clientA: ClientResponse = {
  id: 'c1', firstName: 'Alice', lastName: 'Johnson', dateOfBirth: '1942-03-15',
  address: null, phone: null, medicaidId: null, serviceState: null,
  preferredCaregiverGender: null, preferredLanguages: null, noPetCaregiver: false,
  status: 'ACTIVE', createdAt: '2025-01-10T08:00:00',
}

describe('useClients', () => {
  let mock: MockAdapter
  beforeEach(() => { mock = new MockAdapter(apiClient) })
  afterEach(() => { mock.restore() })

  it('populates clients array and clientMap from API response', async () => {
    const page: PageResponse<ClientResponse> = {
      content: [clientA], totalElements: 1, totalPages: 1, number: 0, size: 100, first: true, last: true,
    }
    mock.onGet('/clients').reply(200, page)
    const { result } = renderHook(() => useClients(), { wrapper: makeWrapper() })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.clients).toHaveLength(1)
    expect(result.current.clientMap.get('c1')?.firstName).toBe('Alice')
  })

  it('returns empty clients and empty map before data loads', () => {
    mock.onGet('/clients').reply(200, { content: [], totalElements: 0, totalPages: 0, number: 0, size: 100, first: true, last: true })
    const { result } = renderHook(() => useClients(), { wrapper: makeWrapper() })
    expect(result.current.clients).toEqual([])
    expect(result.current.clientMap.size).toBe(0)
  })
})
```

- [ ] **Step 3.3: Run tests — expect failures**

```bash
cd frontend && npm test src/hooks/useClients.test.ts 2>&1 | tail -10
```

Expected: all tests fail with "Cannot find module './useClients'".

- [ ] **Step 3.4: Create useClients.ts**

Create `frontend/src/hooks/useClients.ts`:

```ts
import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { listClients, getClient } from '../api/clients'
import type { ClientResponse } from '../types/api'

export function useClients() {
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
```

- [ ] **Step 3.5: Run tests — expect all green**

```bash
cd frontend && npm test src/hooks/useClients.test.ts 2>&1 | tail -10
```

Expected: all 2 tests pass.

- [ ] **Step 3.6: Commit**

```bash
cd frontend && git add src/api/clients.ts src/hooks/useClients.ts src/hooks/useClients.test.ts
git commit -m "feat: add clients API + useClients, useClientDetail hooks"
```

---

### Task 4: Create Caregivers API Module and Hook

**Files:**
- Create: `frontend/src/api/caregivers.ts`
- Create: `frontend/src/hooks/useCaregivers.ts`
- Create: `frontend/src/hooks/useCaregivers.test.ts`

- [ ] **Step 4.1: Create caregivers.ts**

Create `frontend/src/api/caregivers.ts`:

```ts
import { apiClient } from './client'
import type { CaregiverResponse, PageResponse } from '../types/api'

export async function listCaregivers(
  page = 0,
  size = 100,
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
```

- [ ] **Step 4.2: Write failing hook tests**

Create `frontend/src/hooks/useCaregivers.test.ts`:

```ts
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import MockAdapter from 'axios-mock-adapter'
import { apiClient } from '../api/client'
import { useCaregivers } from './useCaregivers'
import type { PageResponse, CaregiverResponse } from '../types/api'
import React from 'react'

vi.mock('../store/authStore', () => ({
  useAuthStore: { getState: vi.fn().mockReturnValue({ token: 'tok', logout: vi.fn() }) },
}))

function makeWrapper() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: qc }, children)
}

const caregiverA: CaregiverResponse = {
  id: 'g1', firstName: 'Maria', lastName: 'Santos', email: 'maria@example.com',
  phone: null, address: null, hireDate: null, hasPet: false, status: 'ACTIVE', createdAt: '2025-01-10T08:00:00',
}

describe('useCaregivers', () => {
  let mock: MockAdapter
  beforeEach(() => { mock = new MockAdapter(apiClient) })
  afterEach(() => { mock.restore() })

  it('populates caregivers array and caregiverMap from API response', async () => {
    const page: PageResponse<CaregiverResponse> = {
      content: [caregiverA], totalElements: 1, totalPages: 1, number: 0, size: 100, first: true, last: true,
    }
    mock.onGet('/caregivers').reply(200, page)
    const { result } = renderHook(() => useCaregivers(), { wrapper: makeWrapper() })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.caregivers).toHaveLength(1)
    expect(result.current.caregiverMap.get('g1')?.firstName).toBe('Maria')
  })
})
```

- [ ] **Step 4.3: Run tests — expect failures**

```bash
cd frontend && npm test src/hooks/useCaregivers.test.ts 2>&1 | tail -10
```

Expected: test fails with "Cannot find module './useCaregivers'".

- [ ] **Step 4.4: Create useCaregivers.ts**

Create `frontend/src/hooks/useCaregivers.ts`:

```ts
import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { listCaregivers, getCaregiver } from '../api/caregivers'
import type { CaregiverResponse } from '../types/api'

export function useCaregivers() {
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
```

- [ ] **Step 4.5: Run tests — expect all green**

```bash
cd frontend && npm test src/hooks/useCaregivers.test.ts 2>&1 | tail -10
```

Expected: test passes.

- [ ] **Step 4.6: Commit**

```bash
cd frontend && git add src/api/caregivers.ts src/hooks/useCaregivers.ts src/hooks/useCaregivers.test.ts
git commit -m "feat: add caregivers API + useCaregivers, useCaregiverDetail hooks"
```

---

### Task 5: Update ShiftDetailPanel to Use Real Data

**Files:**
- Modify: `frontend/src/components/schedule/ShiftDetailPanel.tsx`

**Interface contract:** Props remain `{ shiftId: string; backLabel: string }`. `closePanel` comes from `usePanelStore`. `Shell/PanelContent` dispatches this component and does not change. No `SlidePanel` import here — `Shell` owns the slide wrapper.

- [ ] **Step 5.1: Update ShiftDetailPanel**

Replace the full contents of `frontend/src/components/schedule/ShiftDetailPanel.tsx`:

```tsx
import { useTranslation } from 'react-i18next'
import type { EvvComplianceStatus } from '../../types/api'
import { useShiftDetail, useAssignCaregiver, useBroadcastShift, useClockIn, useGetCandidates } from '../../hooks/useShifts'
import { useClients } from '../../hooks/useClients'
import { useCaregivers } from '../../hooks/useCaregivers'
import { usePanelStore } from '../../store/panelStore'
import { formatLocalTime } from '../../utils/dateFormat'

const EVV_BG: Record<EvvComplianceStatus, string> = {
  RED: '#fef2f2',
  YELLOW: '#fefce8',
  GREEN: '#f0fdf4',
  GREY: '#f8fafc',
  EXEMPT: '#f8fafc',
  PORTAL_SUBMIT: '#f0fdf4',
}

const EVV_TEXT: Record<EvvComplianceStatus, string> = {
  RED: '#dc2626',
  YELLOW: '#ca8a04',
  GREEN: '#16a34a',
  GREY: '#94a3b8',
  EXEMPT: '#94a3b8',
  PORTAL_SUBMIT: '#16a34a',
}

function formatDate(iso: string, locale: string): string {
  return new Date(iso).toLocaleDateString(locale, {
    weekday: 'short',
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  })
}

interface ShiftDetailPanelProps {
  shiftId: string
  backLabel: string
}

export function ShiftDetailPanel({ shiftId, backLabel }: ShiftDetailPanelProps) {
  const { t, i18n } = useTranslation('shiftDetail')
  const tCommon = useTranslation('common').t
  const { closePanel } = usePanelStore()

  const { data: shift, isLoading, error } = useShiftDetail(shiftId)
  const { clientMap } = useClients()
  const { caregiverMap } = useCaregivers()
  const assignMutation = useAssignCaregiver()
  const broadcastMutation = useBroadcastShift()
  const clockInMutation = useClockIn()

  const { data: candidates } = useGetCandidates(shiftId)

  const EVV_LABEL: Record<EvvComplianceStatus, string> = {
    RED: t('evvNonCompliant'),
    YELLOW: t('evvAttention'),
    GREEN: t('evvCompliant'),
    GREY: t('evvNotStarted'),
    EXEMPT: t('evvExempt'),
    PORTAL_SUBMIT: t('evvPortalSubmit'),
  }

  if (isLoading) {
    return (
      <div className="p-8 text-text-secondary">
        <button type="button" className="text-[13px] mb-4 hover:underline text-blue" onClick={closePanel}>
          {backLabel}
        </button>
        <p className="text-[13px]">{tCommon('loading')}</p>
      </div>
    )
  }

  if (error || !shift) {
    return (
      <div className="p-8 text-text-secondary">
        <button type="button" className="text-[13px] mb-4 hover:underline text-blue" onClick={closePanel}>
          {backLabel}
        </button>
        <p className="text-[13px] text-red-600">{t('notFound')}</p>
      </div>
    )
  }

  const client = clientMap.get(shift.clientId)
  const caregiver = shift.caregiverId ? caregiverMap.get(shift.caregiverId) : null
  const evvStatus = shift.evv?.complianceStatus ?? 'GREY'

  return (
    <div className="flex flex-col h-full">
      {/* Header */}
      <div className="px-6 py-4 border-b border-border">
        <button
          type="button"
          className="text-[13px] mb-2 hover:underline text-blue"
          onClick={closePanel}
        >
          {backLabel}
        </button>
        <h2 className="text-[16px] font-bold text-dark">
          {client ? `${client.firstName} ${client.lastName}` : t('unknownClient')}
        </h2>
        <p className="text-[12px] text-text-secondary mt-0.5">
          {formatDate(shift.scheduledStart, i18n.language)} · {formatLocalTime(shift.scheduledStart, i18n.language)} – {formatLocalTime(shift.scheduledEnd, i18n.language)} · {t('staticService')}
        </p>
      </div>

      {/* EVV Status badge */}
      <div
        className="mx-6 mt-4 px-4 py-3 text-[13px] font-semibold"
        style={{ background: EVV_BG[evvStatus], color: EVV_TEXT[evvStatus] }}
      >
        {EVV_LABEL[evvStatus]}
      </div>

      {/* Body */}
      <div className="flex-1 overflow-auto px-6 py-4">
        <div className="grid grid-cols-2 gap-6">
          {/* Left: Visit Details */}
          <div>
            <h3 className="text-[9px] font-bold uppercase tracking-[0.1em] text-text-secondary mb-3">
              {t('sectionVisitDetails')}
            </h3>
            <div className="space-y-2">
              <div>
                <div className="text-[10px] text-text-secondary">{t('fieldClient')}</div>
                <div className="text-[13px] text-dark">
                  {client ? `${client.firstName} ${client.lastName}` : tCommon('noDash')}
                </div>
              </div>
              <div>
                <div className="text-[10px] text-text-secondary">{t('fieldCaregiver')}</div>
                <div className="text-[13px] text-dark">
                  {caregiver ? `${caregiver.firstName} ${caregiver.lastName}` : tCommon('unassigned')}
                </div>
              </div>
              <div>
                <div className="text-[10px] text-text-secondary">{t('fieldStatus')}</div>
                <div className="text-[13px] text-dark">{shift.status.replace(/_/g, ' ')}</div>
              </div>
            </div>
          </div>

          {/* Right: EVV Record */}
          <div>
            <h3 className="text-[9px] font-bold uppercase tracking-[0.1em] text-text-secondary mb-3">
              {t('sectionEvvRecord')}
            </h3>
            <div className="space-y-2">
              <div>
                <div className="text-[10px] text-text-secondary">{t('fieldClockIn')}</div>
                <div className="text-[13px] text-dark">{formatLocalTime(shift.evv?.timeIn ?? null, i18n.language)}</div>
              </div>
              <div>
                <div className="text-[10px] text-text-secondary">{t('fieldClockOut')}</div>
                <div className="text-[13px] text-dark">{formatLocalTime(shift.evv?.timeOut ?? null, i18n.language)}</div>
              </div>
              <div>
                <div className="text-[10px] text-text-secondary">{t('fieldMethod')}</div>
                <div className="text-[13px] text-dark">{shift.evv?.verificationMethod ?? tCommon('noDash')}</div>
              </div>
              <div>
                <div className="text-[10px] text-text-secondary">{t('fieldOffline')}</div>
                <div className="text-[13px] text-dark">
                  {shift.evv?.capturedOffline ? tCommon('yes') : tCommon('no')}
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* AI Candidates — shown for OPEN shifts without a caregiver */}
        {!shift.caregiverId && candidates && candidates.length > 0 && (
          <div className="mt-6">
            <h3 className="text-[9px] font-bold uppercase tracking-[0.1em] text-text-secondary mb-3">
              {t('sectionAiMatch')}
            </h3>
            {candidates.slice(0, 5).map((candidate, i) => {
              const cg = caregiverMap.get(candidate.caregiverId)
              return (
                <div key={candidate.caregiverId} className="flex items-center gap-3 py-2 border-b border-border">
                  <span
                    className={`w-6 h-6 rounded-full flex items-center justify-center text-[11px] font-bold text-white shrink-0 ${i === 0 ? 'bg-blue' : 'bg-text-muted'}`}
                  >
                    {i + 1}
                  </span>
                  <div className="flex-1">
                    <div className="text-[13px] font-medium text-dark">
                      {cg ? `${cg.firstName} ${cg.lastName}` : candidate.caregiverId}
                    </div>
                    <div className="text-[11px] text-text-secondary">{candidate.explanation}</div>
                  </div>
                  <button
                    type="button"
                    disabled={assignMutation.isPending}
                    className="text-[12px] font-semibold disabled:opacity-50 text-blue"
                    onClick={() => assignMutation.mutate({ shiftId, caregiverId: candidate.caregiverId })}
                  >
                    {t('assign')}
                  </button>
                </div>
              )
            })}
          </div>
        )}
      </div>

      {/* Footer actions */}
      <div className="px-6 py-4 border-t border-border flex items-center gap-3">
        {shift.status === 'OPEN' && !shift.caregiverId && (
          <button
            type="button"
            disabled={broadcastMutation.isPending}
            className="px-4 py-2 text-[12px] font-bold bg-dark text-white hover:brightness-110 disabled:opacity-50"
            onClick={() => broadcastMutation.mutate(shiftId)}
          >
            {broadcastMutation.isPending ? '…' : t('assignCaregiver')}
          </button>
        )}
        {shift.status === 'ASSIGNED' && !shift.evv?.timeIn && (
          <button
            type="button"
            disabled={clockInMutation.isPending}
            className="px-4 py-2 text-[12px] font-bold bg-dark text-white hover:brightness-110 disabled:opacity-50"
            onClick={() =>
              clockInMutation.mutate({
                shiftId,
                req: { locationLat: 0, locationLon: 0, verificationMethod: 'MANUAL', capturedOffline: false },
              })
            }
          >
            {clockInMutation.isPending ? '…' : t('addManualClockIn')}
          </button>
        )}
        {(shift.status === 'COMPLETED' || shift.status === 'IN_PROGRESS') && evvStatus === 'RED' && (
          <button
            type="button"
            className="px-4 py-2 text-[12px] font-bold bg-dark text-white hover:brightness-110"
          >
            {t('addManualClockIn')}
          </button>
        )}
        <button
          type="button"
          className="px-4 py-2 text-[12px] font-semibold border border-border text-dark"
        >
          {t('editShift')}
        </button>
      </div>
    </div>
  )
}
```

- [ ] **Step 5.2: Add 'loading' key to common locale**

Check `frontend/public/locales/en/common.json`. If `loading` is absent, add it:

```json
"loading": "Loading…"
```

- [ ] **Step 5.3: Verify TypeScript**

```bash
cd frontend && npx tsc --noEmit 2>&1 | head -20
```

Expected: no errors.

- [ ] **Step 5.4: Commit**

```bash
cd frontend && git add src/components/schedule/ShiftDetailPanel.tsx public/locales/en/common.json
git commit -m "feat: wire ShiftDetailPanel to real shift detail, assign, broadcast, clock-in, and AI candidates APIs"
```

---

### Task 6: Update NewShiftPanel to Use Real Data

**Files:**
- Modify: `frontend/src/components/schedule/NewShiftPanel.tsx`

**Interface contract:** Props remain `{ prefill: { date?: string; time?: string } | null; backLabel: string }`. `closePanel` comes from `usePanelStore`. Form keeps separate `date` + `startTime` + `endTime` fields; `scheduledStart`/`scheduledEnd` are assembled in `onSubmit`.

- [ ] **Step 6.1: Update NewShiftPanel**

Replace the full contents of `frontend/src/components/schedule/NewShiftPanel.tsx`:

```tsx
import { useTranslation } from 'react-i18next'
import { useForm } from 'react-hook-form'
import { useClients } from '../../hooks/useClients'
import { useCaregivers } from '../../hooks/useCaregivers'
import { useCreateShift } from '../../hooks/useShifts'
import { usePanelStore } from '../../store/panelStore'

interface FormValues {
  clientId: string
  serviceTypeId: string
  date: string
  startTime: string
  endTime: string
  caregiverId: string
}

interface NewShiftPanelProps {
  prefill: { date?: string; time?: string } | null
  backLabel: string
}

export function NewShiftPanel({ prefill, backLabel }: NewShiftPanelProps) {
  const { t } = useTranslation('newShift')
  const tCommon = useTranslation('common').t
  const { closePanel } = usePanelStore()
  const { clients } = useClients()
  const { caregivers } = useCaregivers()
  const createMutation = useCreateShift()

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    defaultValues: {
      date: prefill?.date ?? [
        new Date().getFullYear(),
        String(new Date().getMonth() + 1).padStart(2, '0'),
        String(new Date().getDate()).padStart(2, '0'),
      ].join('-'),
      startTime: prefill?.time ?? '09:00',
      endTime: '13:00',
    },
  })

  async function onSubmit(values: FormValues) {
    await createMutation.mutateAsync({
      clientId: values.clientId,
      caregiverId: values.caregiverId || undefined,
      serviceTypeId: values.serviceTypeId,
      scheduledStart: `${values.date}T${values.startTime}:00`,
      scheduledEnd: `${values.date}T${values.endTime}:00`,
    })
    closePanel()
  }

  return (
    <div className="flex flex-col h-full">
      {/* Header */}
      <div className="px-6 py-4 border-b border-border">
        <button
          type="button"
          className="text-[13px] mb-2 hover:underline text-blue"
          onClick={closePanel}
        >
          {backLabel}
        </button>
        <h2 className="text-[16px] font-bold text-dark">{t('panelTitle')}</h2>
      </div>

      {/* Form */}
      <form
        onSubmit={handleSubmit(onSubmit)}
        className="flex-1 overflow-auto px-6 py-4 space-y-4"
      >
        {/* Client */}
        <div>
          <label htmlFor="ns-client" className="block text-[10px] font-bold uppercase tracking-[0.1em] text-text-secondary mb-1">
            {t('labelClient')}
          </label>
          <select
            id="ns-client"
            {...register('clientId', { required: t('validationClientRequired') })}
            className="w-full border border-border px-3 py-2 text-[13px] text-dark bg-white"
          >
            <option value="">{t('selectClient')}</option>
            {clients.map((c) => (
              <option key={c.id} value={c.id}>
                {c.firstName} {c.lastName}
              </option>
            ))}
          </select>
          {errors.clientId && (
            <p className="text-[11px] text-red-600 mt-1">{errors.clientId.message}</p>
          )}
        </div>

        {/* Service Type — hardcoded until Phase 6 adds service-types API */}
        <div>
          <label htmlFor="ns-service-type" className="block text-[10px] font-bold uppercase tracking-[0.1em] text-text-secondary mb-1">
            {t('labelServiceType')}
          </label>
          <select
            id="ns-service-type"
            {...register('serviceTypeId', { required: t('validationServiceTypeRequired') })}
            className="w-full border border-border px-3 py-2 text-[13px] text-dark bg-white"
          >
            <option value="">{t('selectServiceType')}</option>
            <option value="st000000-0000-0000-0000-000000000001">{t('serviceTypePcs')}</option>
          </select>
          {errors.serviceTypeId && (
            <p className="text-[11px] text-red-600 mt-1">{errors.serviceTypeId.message}</p>
          )}
        </div>

        {/* Date */}
        <div>
          <label htmlFor="ns-date" className="block text-[10px] font-bold uppercase tracking-[0.1em] text-text-secondary mb-1">
            {t('labelDate')}
          </label>
          <input
            id="ns-date"
            type="date"
            {...register('date', { required: t('validationDateRequired') })}
            className="w-full border border-border px-3 py-2 text-[13px] text-dark"
          />
        </div>

        {/* Start / End time */}
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label htmlFor="ns-start-time" className="block text-[10px] font-bold uppercase tracking-[0.1em] text-text-secondary mb-1">
              {t('labelStartTime')}
            </label>
            <input
              id="ns-start-time"
              type="time"
              {...register('startTime', { required: t('validationRequired') })}
              className="w-full border border-border px-3 py-2 text-[13px] text-dark"
            />
            {errors.startTime && (
              <p className="text-[11px] text-red-600 mt-1">{errors.startTime.message}</p>
            )}
          </div>
          <div>
            <label htmlFor="ns-end-time" className="block text-[10px] font-bold uppercase tracking-[0.1em] text-text-secondary mb-1">
              {t('labelEndTime')}
            </label>
            <input
              id="ns-end-time"
              type="time"
              {...register('endTime', { required: t('validationRequired') })}
              className="w-full border border-border px-3 py-2 text-[13px] text-dark"
            />
            {errors.endTime && (
              <p className="text-[11px] text-red-600 mt-1">{errors.endTime.message}</p>
            )}
          </div>
        </div>

        {/* Caregiver (optional) */}
        <div>
          <label htmlFor="ns-caregiver" className="block text-[10px] font-bold uppercase tracking-[0.1em] text-text-secondary mb-1">
            {t('labelCaregiver')}
          </label>
          <select
            id="ns-caregiver"
            {...register('caregiverId')}
            className="w-full border border-border px-3 py-2 text-[13px] text-dark bg-white"
          >
            <option value="">{t('caregiverUnassigned')}</option>
            {caregivers.map((c) => (
              <option key={c.id} value={c.id}>
                {c.firstName} {c.lastName}
              </option>
            ))}
          </select>
        </div>

        {/* API error */}
        {createMutation.isError && (
          <p className="text-[11px] text-red-600">{tCommon('errorTryAgain')}</p>
        )}

        {/* Footer */}
        <div className="pt-4 border-t border-border flex gap-3">
          <button
            type="submit"
            disabled={isSubmitting || createMutation.isPending}
            className="px-4 py-2 text-[12px] font-bold bg-dark text-white hover:brightness-110 disabled:opacity-50"
          >
            {isSubmitting || createMutation.isPending ? '…' : t('saveShift')}
          </button>
          <button
            type="button"
            onClick={closePanel}
            className="px-4 py-2 text-[12px] font-semibold border border-border text-dark"
          >
            {tCommon('cancel')}
          </button>
        </div>
      </form>
    </div>
  )
}
```

- [ ] **Step 6.2: Add `errorTryAgain` key to common locale if missing**

Check `frontend/public/locales/en/common.json`. If `errorTryAgain` is absent, add it:

```json
"errorTryAgain": "Something went wrong. Please try again."
```

- [ ] **Step 6.3: Verify TypeScript**

```bash
cd frontend && npx tsc --noEmit 2>&1 | head -20
```

Expected: no errors.

- [ ] **Step 6.4: Commit**

```bash
cd frontend && git add src/components/schedule/NewShiftPanel.tsx public/locales/en/common.json
git commit -m "feat: wire NewShiftPanel to real API using useClients, useCaregivers, useCreateShift"
```

---

### Task 7: Update SchedulePage to Use Real Data

**Files:**
- Modify: `frontend/src/components/schedule/SchedulePage.tsx`

**Key fixes applied:**
- `weekStart` state is a `Date`; passed directly to `WeekCalendar` as `weekStart={weekStart}` (C1).
- ISO strings for API params use local date parts, not `toISOString()` (M2).
- `handleNewShift` is retained and passed as `onEmptySlotClick` (C3).
- `openPanel('shift', id, …)` — panel type unchanged from current code (C4). Shell/PanelContent remains the sole owner of panel rendering (C4).
- Tailwind tokens throughout; no inline hex (M4).

- [ ] **Step 7.1: Update SchedulePage**

Replace the full contents of `frontend/src/components/schedule/SchedulePage.tsx`:

```tsx
import { useState, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { WeekCalendar } from './WeekCalendar'
import { AlertStrip } from './AlertStrip'
import { usePanelStore } from '../../store/panelStore'
import { useShifts } from '../../hooks/useShifts'
import { useClients } from '../../hooks/useClients'
import { useCaregivers } from '../../hooks/useCaregivers'

function getMonday(d: Date): Date {
  const date = new Date(d)
  const day = date.getDay()
  const diff = day === 0 ? -6 : 1 - day
  date.setDate(date.getDate() + diff)
  date.setHours(0, 0, 0, 0)
  return date
}

// Formats a local Date as an ISO-8601 LocalDateTime string without timezone offset.
// Uses local date/time parts (not toISOString) so the boundary is correct in all timezones.
function toLocalISODateTime(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T00:00:00`
}

function formatWeekRange(monday: Date, locale: string): string {
  const sunday = new Date(monday)
  sunday.setDate(sunday.getDate() + 6)
  const fmt = (d: Date) =>
    d.toLocaleDateString(locale, { month: 'short', day: 'numeric', year: 'numeric' })
  return `${fmt(monday)} – ${fmt(sunday)}`
}

export function SchedulePage() {
  const { t, i18n } = useTranslation('schedule')
  const [weekStart, setWeekStart] = useState(() => getMonday(new Date()))
  const { openPanel } = usePanelStore()

  // ISO strings derived from weekStart for API params — weekStart (Date) is passed to WeekCalendar
  const weekStartStr = useMemo(() => toLocalISODateTime(weekStart), [weekStart])
  const weekEndStr = useMemo(() => {
    const end = new Date(weekStart)
    end.setDate(end.getDate() + 7)
    return toLocalISODateTime(end)
  }, [weekStart])

  const { data: shiftsPage, isLoading: shiftsLoading } = useShifts(weekStartStr, weekEndStr)
  const { clientMap } = useClients()
  const { caregiverMap } = useCaregivers()

  const shifts = shiftsPage?.content ?? []

  function prevWeek() {
    setWeekStart((d) => {
      const prev = new Date(d)
      prev.setDate(prev.getDate() - 7)
      return prev
    })
  }

  function nextWeek() {
    setWeekStart((d) => {
      const next = new Date(d)
      next.setDate(next.getDate() + 7)
      return next
    })
  }

  // Clicking an empty slot pre-fills the new shift form with the clicked date + hour
  function handleNewShift(date: Date, hour: number) {
    const dateStr = [
      date.getFullYear(),
      String(date.getMonth() + 1).padStart(2, '0'),
      String(date.getDate()).padStart(2, '0'),
    ].join('-')
    const timeStr = `${String(hour).padStart(2, '0')}:00`
    openPanel('newShift', undefined, {
      prefill: { date: dateStr, time: timeStr },
      backLabel: t('backLabel'),
    })
  }

  return (
    <div className="flex flex-col h-full">
      {/* Top bar */}
      <div className="flex items-center gap-4 px-6 py-4 bg-white border-b border-border">
        <h1 className="text-[16px] font-bold tracking-[-0.02em] text-dark mr-2">{t('pageTitle')}</h1>
        <span className="text-[13px] text-text-secondary">{formatWeekRange(weekStart, i18n.language)}</span>
        <div className="ml-auto flex items-center gap-2">
          <button
            type="button"
            onClick={prevWeek}
            className="px-3 py-1.5 text-[12px] font-semibold border border-border text-dark hover:brightness-95 transition-[filter]"
          >
            {t('prevWeek')}
          </button>
          <button
            type="button"
            onClick={nextWeek}
            className="px-3 py-1.5 text-[12px] font-semibold border border-border text-dark hover:brightness-95 transition-[filter]"
          >
            {t('nextWeek')}
          </button>
          <button
            type="button"
            className="px-3 py-1.5 text-[12px] font-semibold border border-border text-dark hover:brightness-95 ml-2"
            onClick={() => alert(t('broadcastOpenAlert'))}
          >
            {t('broadcastOpen')}
          </button>
          <button
            type="button"
            className="px-4 py-1.5 text-[12px] font-bold bg-dark text-white hover:brightness-110"
            onClick={() => openPanel('newShift', undefined, { backLabel: t('backLabel') })}
          >
            {t('newShift')}
          </button>
        </div>
      </div>

      {/* Alert strip — EVV counts wired in a later phase; zero placeholders suppress the bar */}
      <AlertStrip
        redCount={0}
        yellowCount={0}
        uncoveredCount={0}
        lateClockInCount={0}
      />

      {/* Calendar */}
      <div className="flex-1 overflow-auto">
        {shiftsLoading ? (
          <div className="flex items-center justify-center h-64">
            <span className="text-[13px] text-text-muted">{t('loading')}</span>
          </div>
        ) : (
          <WeekCalendar
            weekStart={weekStart}
            shifts={shifts}
            clientMap={clientMap}
            caregiverMap={caregiverMap}
            onShiftClick={(id) => openPanel('shift', id, { backLabel: t('backLabel') })}
            onEmptySlotClick={handleNewShift}
          />
        )}
      </div>
    </div>
  )
}
```

- [ ] **Step 7.2: Add `loading` key to schedule locale if missing**

Check `frontend/public/locales/en/schedule.json`. If `loading` is absent, add it:

```json
"loading": "Loading shifts…"
```

- [ ] **Step 7.3: Verify TypeScript and build**

```bash
cd frontend && npx tsc --noEmit 2>&1 | head -20
```

Expected: no errors.

```bash
cd frontend && npm run build 2>&1 | tail -5
```

Expected: clean build.

- [ ] **Step 7.4: Run all frontend tests**

```bash
cd frontend && npm test 2>&1 | tail -20
```

Expected: all tests pass, no failures.

- [ ] **Step 7.5: Commit**

```bash
cd frontend && git add src/components/schedule/SchedulePage.tsx public/locales/en/schedule.json
git commit -m "feat: wire SchedulePage to real API via useShifts, useClients, useCaregivers"
```

---

## ✋ MANUAL TEST CHECKPOINT 4

**Start both servers:**

```bash
# Terminal 1 — backend
cd backend && mvn spring-boot:run

# Terminal 2 — frontend
cd frontend && npm run dev
```

**Test the schedule screen:**

1. Log in (`admin@sunrise.dev` / `Admin1234!`) and navigate to `/schedule`.
2. Verify the week calendar renders. If no shifts exist for the current week, confirm the loading spinner disappears and an empty calendar grid is shown (no JavaScript errors in console).
3. Click "Next →" / "← Prev" to change weeks — network requests should fire for the new date range.
4. Click "+ New Shift", select a client from the dropdown (populated from real data), fill in required fields, and submit. Verify the shift appears on the calendar.
5. Click "New Shift" via an empty slot click — the form should pre-fill the date and start time for the clicked slot.
6. Click on an existing shift. The detail panel should show real client and caregiver names, EVV status badge, and AI candidate list (empty if scoring is disabled).
7. In the detail panel, click "Assign" next to a candidate — shift should update.
8. Open DevTools → Network. Confirm `GET /api/v1/shifts?start=…&end=…` is called. Confirm `GET /api/v1/clients?size=100` and `GET /api/v1/caregivers?size=100` are each called once and then served from cache for 60 s.

Proceed to Phase 5 only after this checkpoint passes.
