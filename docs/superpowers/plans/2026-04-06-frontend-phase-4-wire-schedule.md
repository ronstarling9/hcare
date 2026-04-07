# Phase 4: Wire Schedule Screen

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace all mock data imports in the schedule screen with real API calls, so shifts, clients, caregivers, EVV status, and AI candidates all come from the backend.

**Before starting:**
- Phase 3 (auth wiring) must be complete — `api/client.ts` and `authStore.ts` exist.
- Backend is running on `http://localhost:8080`.
- The user can log in and land on `/schedule`.

**Data strategy note:** `GET /api/v1/shifts` returns `Page<ShiftSummaryResponse>` containing `clientId` and `caregiverId` UUIDs but no names. This phase fetches all clients and caregivers (page size 100) separately and builds in-memory lookup maps for name resolution in the calendar grid.

---

### Task 1: Create Shifts API Module

**Files:**
- Create: `frontend/src/api/shifts.ts`

- [ ] **Step 1.1: Create shifts.ts**

Create `frontend/src/api/shifts.ts`:

```ts
import { apiClient } from './client'
import type {
  ShiftSummaryResponse,
  ShiftDetailResponse,
  RankedCaregiverResponse,
  PageResponse,
} from '../types/api'

export interface CreateShiftRequest {
  clientId: string
  caregiverId?: string | null
  serviceTypeId: string
  authorizationId?: string | null
  scheduledStart: string // ISO-8601 LocalDateTime, no timezone
  scheduledEnd: string
  notes?: string | null
}

export interface AssignCaregiverRequest {
  caregiverId: string
}

export interface ClockInRequest {
  locationLat: number
  locationLon: number
  verificationMethod: string
  capturedOffline: boolean
  deviceCapturedAt?: string | null // ISO-8601 LocalDateTime
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

- [ ] **Step 1.2: Verify TypeScript**

```bash
cd frontend && npx tsc --noEmit 2>&1 | head -20
```

Expected: no errors.

- [ ] **Step 1.3: Commit**

```bash
cd frontend && git add src/api/shifts.ts
git commit -m "feat: add shifts API module (listShifts, getShift, createShift, assignCaregiver, getCandidates, clockIn, broadcastShift)"
```

---

### Task 2: Create useShifts and useShiftDetail Hooks

**Files:**
- Create: `frontend/src/hooks/useShifts.ts`

- [ ] **Step 2.1: Create useShifts.ts**

Create `frontend/src/hooks/useShifts.ts`:

```ts
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  listShifts,
  getShift,
  createShift,
  assignCaregiver,
  broadcastShift,
  clockIn,
  type CreateShiftRequest,
  type ClockInRequest,
} from '../api/shifts'

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
```

- [ ] **Step 2.2: Commit**

```bash
cd frontend && git add src/hooks/useShifts.ts
git commit -m "feat: add useShifts, useShiftDetail, useCreateShift, useAssignCaregiver, useClockIn hooks"
```

---

### Task 3: Create useClients Hook

**Files:**
- Create: `frontend/src/hooks/useClients.ts`
- Create: `frontend/src/api/clients.ts` (initial version — full expansion in Phase 6)

- [ ] **Step 3.1: Create clients.ts (Phase 4 slice)**

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

- [ ] **Step 3.2: Create useClients.ts**

Create `frontend/src/hooks/useClients.ts`:

```ts
import { useQuery } from '@tanstack/react-query'
import { listClients, getClient } from '../api/clients'
import type { ClientResponse } from '../types/api'
import { useMemo } from 'react'

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

- [ ] **Step 3.3: Commit**

```bash
cd frontend && git add src/api/clients.ts src/hooks/useClients.ts
git commit -m "feat: add clients API + useClients, useClientDetail hooks"
```

---

### Task 4: Create useCaregivers Hook

**Files:**
- Create: `frontend/src/hooks/useCaregivers.ts`
- Create: `frontend/src/api/caregivers.ts` (initial version — full expansion in Phase 7)

- [ ] **Step 4.1: Create caregivers.ts (Phase 4 slice)**

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

- [ ] **Step 4.2: Create useCaregivers.ts**

Create `frontend/src/hooks/useCaregivers.ts`:

```ts
import { useQuery } from '@tanstack/react-query'
import { listCaregivers, getCaregiver } from '../api/caregivers'
import type { CaregiverResponse } from '../types/api'
import { useMemo } from 'react'

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

- [ ] **Step 4.3: Commit**

```bash
cd frontend && git add src/api/caregivers.ts src/hooks/useCaregivers.ts
git commit -m "feat: add caregivers API + useCaregivers, useCaregiverDetail hooks"
```

---

### Task 5: Update SchedulePage to Use Real Data

**Files:**
- Modify: `frontend/src/components/schedule/SchedulePage.tsx`

- [ ] **Step 5.1: Update SchedulePage**

Replace the full contents of `frontend/src/components/schedule/SchedulePage.tsx`:

```tsx
import { useState, useMemo } from 'react'
import { WeekCalendar } from './WeekCalendar'
import { ShiftDetailPanel } from './ShiftDetailPanel'
import { NewShiftPanel } from './NewShiftPanel'
import { usePanelStore } from '../../store/panelStore'
import { useShifts } from '../../hooks/useShifts'
import { useClients } from '../../hooks/useClients'
import { useCaregivers } from '../../hooks/useCaregivers'

function getWeekBounds(referenceDate: Date): { weekStart: string; weekEnd: string } {
  const d = new Date(referenceDate)
  const day = d.getDay() // 0=Sun
  const diffToMon = day === 0 ? -6 : 1 - day
  const monday = new Date(d)
  monday.setDate(d.getDate() + diffToMon)
  monday.setHours(0, 0, 0, 0)

  const sunday = new Date(monday)
  sunday.setDate(monday.getDate() + 7)
  sunday.setHours(0, 0, 0, 0)

  // Format as ISO-8601 LocalDateTime (no timezone suffix — backend expects this)
  const fmt = (dt: Date) => dt.toISOString().replace('Z', '').replace(/\.\d+$/, '')
  return { weekStart: fmt(monday), weekEnd: fmt(sunday) }
}

export function SchedulePage() {
  const [currentWeekDate, setCurrentWeekDate] = useState(() => new Date())
  const { weekStart, weekEnd } = useMemo(
    () => getWeekBounds(currentWeekDate),
    [currentWeekDate],
  )

  const { data: shiftsPage, isLoading: shiftsLoading } = useShifts(weekStart, weekEnd)
  const { clientMap } = useClients()
  const { caregiverMap } = useCaregivers()

  const panel = usePanelStore()

  const shifts = shiftsPage?.content ?? []

  const handlePrevWeek = () => {
    setCurrentWeekDate((d) => {
      const nd = new Date(d)
      nd.setDate(nd.getDate() - 7)
      return nd
    })
  }

  const handleNextWeek = () => {
    setCurrentWeekDate((d) => {
      const nd = new Date(d)
      nd.setDate(nd.getDate() + 7)
      return nd
    })
  }

  return (
    <div className="flex flex-col h-full" style={{ backgroundColor: '#f6f6fa' }}>
      {/* Header */}
      <div
        className="flex items-center justify-between px-6 py-4 border-b"
        style={{ backgroundColor: '#ffffff', borderColor: '#eaeaf2' }}
      >
        <h1 className="text-lg font-semibold" style={{ color: '#1a1a24' }}>
          Schedule
        </h1>
        <div className="flex items-center gap-3">
          <button
            onClick={handlePrevWeek}
            className="px-3 py-1.5 rounded-lg text-sm"
            style={{ border: '1px solid #eaeaf2', color: '#747480' }}
          >
            ← Prev
          </button>
          <span className="text-sm font-medium" style={{ color: '#1a1a24' }}>
            Week of {new Date(weekStart).toLocaleDateString('en-US', { month: 'short', day: 'numeric' })}
          </span>
          <button
            onClick={handleNextWeek}
            className="px-3 py-1.5 rounded-lg text-sm"
            style={{ border: '1px solid #eaeaf2', color: '#747480' }}
          >
            Next →
          </button>
          <button
            onClick={() => panel.openPanel('newShift')}
            className="px-4 py-1.5 rounded-lg text-sm font-medium text-white"
            style={{ backgroundColor: '#1a9afa' }}
          >
            + New Shift
          </button>
        </div>
      </div>

      {/* Calendar */}
      <div className="flex-1 overflow-auto p-6">
        {shiftsLoading ? (
          <div className="flex items-center justify-center h-64">
            <span className="text-sm" style={{ color: '#94a3b8' }}>
              Loading shifts…
            </span>
          </div>
        ) : (
          <WeekCalendar
            shifts={shifts}
            clientMap={clientMap}
            caregiverMap={caregiverMap}
            weekStart={weekStart}
            onShiftClick={(shiftId) => panel.openPanel('shiftDetail', shiftId)}
          />
        )}
      </div>

      {/* Panels */}
      {panel.type === 'shiftDetail' && panel.id && (
        <ShiftDetailPanel shiftId={panel.id} onClose={panel.closePanel} />
      )}
      {panel.type === 'newShift' && (
        <NewShiftPanel onClose={panel.closePanel} />
      )}
    </div>
  )
}
```

- [ ] **Step 5.2: Verify TypeScript**

```bash
cd frontend && npx tsc --noEmit 2>&1 | head -30
```

Expected: no errors. If `WeekCalendar`, `ShiftDetailPanel`, or `NewShiftPanel` have prop-type mismatches, fix them in the next steps.

- [ ] **Step 5.3: Commit**

```bash
cd frontend && git add src/components/schedule/SchedulePage.tsx
git commit -m "feat: wire SchedulePage to real API via useShifts, useClients, useCaregivers"
```

---

### Task 6: Update ShiftDetailPanel to Use Real Data

**Files:**
- Modify: `frontend/src/components/schedule/ShiftDetailPanel.tsx`

- [ ] **Step 6.1: Update ShiftDetailPanel**

Replace the full contents of `frontend/src/components/schedule/ShiftDetailPanel.tsx`:

```tsx
import { SlidePanel } from '../panel/SlidePanel'
import { useShiftDetail, useAssignCaregiver, useBroadcastShift, useClockIn } from '../../hooks/useShifts'
import { useClients } from '../../hooks/useClients'
import { useCaregivers } from '../../hooks/useCaregivers'
import { useQuery } from '@tanstack/react-query'
import { getCandidates } from '../../api/shifts'
import type { EvvComplianceStatus } from '../../types/api'

const EVV_COLORS: Record<EvvComplianceStatus, string> = {
  GREEN: '#16a34a',
  YELLOW: '#ca8a04',
  RED: '#dc2626',
  GREY: '#94a3b8',
  EXEMPT: '#94a3b8',
  PORTAL_SUBMIT: '#94a3b8',
}

interface ShiftDetailPanelProps {
  shiftId: string
  onClose: () => void
}

export function ShiftDetailPanel({ shiftId, onClose }: ShiftDetailPanelProps) {
  const { data: shift, isLoading, error } = useShiftDetail(shiftId)
  const { clientMap } = useClients()
  const { caregiverMap } = useCaregivers()
  const assignMutation = useAssignCaregiver()
  const broadcastMutation = useBroadcastShift()
  const clockInMutation = useClockIn()

  const { data: candidates } = useQuery({
    queryKey: ['candidates', shiftId],
    queryFn: () => getCandidates(shiftId),
    enabled: Boolean(shiftId),
  })

  if (isLoading) {
    return (
      <SlidePanel title="Shift Detail" onClose={onClose}>
        <div className="flex items-center justify-center h-32">
          <span className="text-sm" style={{ color: '#94a3b8' }}>Loading…</span>
        </div>
      </SlidePanel>
    )
  }

  if (error || !shift) {
    return (
      <SlidePanel title="Shift Detail" onClose={onClose}>
        <div className="flex items-center justify-center h-32">
          <span className="text-sm" style={{ color: '#dc2626' }}>Failed to load shift.</span>
        </div>
      </SlidePanel>
    )
  }

  const client = shift.clientId ? clientMap.get(shift.clientId) : undefined
  const caregiver = shift.caregiverId ? caregiverMap.get(shift.caregiverId) : undefined
  const evvStatus = shift.evv?.complianceStatus ?? 'GREY'
  const evvColor = EVV_COLORS[evvStatus as EvvComplianceStatus]

  const handleAssign = (caregiverId: string) => {
    assignMutation.mutate({ shiftId, caregiverId })
  }

  const handleBroadcast = () => {
    broadcastMutation.mutate(shiftId)
  }

  const handleClockIn = () => {
    // Manual admin clock-in uses MANUAL verification method at client's location
    clockInMutation.mutate({
      shiftId,
      req: {
        locationLat: 0,
        locationLon: 0,
        verificationMethod: 'MANUAL',
        capturedOffline: false,
        deviceCapturedAt: null,
      },
    })
  }

  return (
    <SlidePanel title="Shift Detail" onClose={onClose}>
      <div className="space-y-4 p-4">
        {/* Client */}
        <div>
          <p className="text-xs font-medium uppercase mb-1" style={{ color: '#94a3b8' }}>Client</p>
          <p className="text-sm font-medium" style={{ color: '#1a1a24' }}>
            {client ? `${client.firstName} ${client.lastName}` : shift.clientId}
          </p>
        </div>

        {/* Caregiver */}
        <div>
          <p className="text-xs font-medium uppercase mb-1" style={{ color: '#94a3b8' }}>Caregiver</p>
          <p className="text-sm" style={{ color: '#1a1a24' }}>
            {caregiver
              ? `${caregiver.firstName} ${caregiver.lastName}`
              : shift.caregiverId
              ? shift.caregiverId
              : 'Unassigned'}
          </p>
        </div>

        {/* Time */}
        <div>
          <p className="text-xs font-medium uppercase mb-1" style={{ color: '#94a3b8' }}>Scheduled</p>
          <p className="text-sm" style={{ color: '#1a1a24' }}>
            {new Date(shift.scheduledStart).toLocaleTimeString('en-US', {
              hour: 'numeric',
              minute: '2-digit',
            })}{' '}
            —{' '}
            {new Date(shift.scheduledEnd).toLocaleTimeString('en-US', {
              hour: 'numeric',
              minute: '2-digit',
            })}
          </p>
        </div>

        {/* Status */}
        <div>
          <p className="text-xs font-medium uppercase mb-1" style={{ color: '#94a3b8' }}>Status</p>
          <p className="text-sm font-medium" style={{ color: '#1a1a24' }}>
            {shift.status}
          </p>
        </div>

        {/* EVV */}
        <div>
          <p className="text-xs font-medium uppercase mb-1" style={{ color: '#94a3b8' }}>EVV Status</p>
          <span
            className="inline-block px-2 py-0.5 rounded text-xs font-semibold text-white"
            style={{ backgroundColor: evvColor }}
          >
            {evvStatus}
          </span>
          {shift.evv?.timeIn && (
            <p className="text-xs mt-1" style={{ color: '#747480' }}>
              In: {new Date(shift.evv.timeIn).toLocaleTimeString()}
              {shift.evv.timeOut
                ? ` — Out: ${new Date(shift.evv.timeOut).toLocaleTimeString()}`
                : ''}
            </p>
          )}
        </div>

        {/* Actions */}
        <div className="flex flex-col gap-2 pt-2">
          {!shift.caregiverId && (
            <button
              onClick={handleBroadcast}
              disabled={broadcastMutation.isPending}
              className="w-full rounded-lg py-2 text-sm font-medium text-white disabled:opacity-50"
              style={{ backgroundColor: '#1a9afa' }}
            >
              {broadcastMutation.isPending ? 'Broadcasting…' : 'Broadcast to Caregivers'}
            </button>
          )}

          {!shift.evv?.timeIn && shift.status === 'ASSIGNED' && (
            <button
              onClick={handleClockIn}
              disabled={clockInMutation.isPending}
              className="w-full rounded-lg py-2 text-sm font-medium disabled:opacity-50"
              style={{ border: '1px solid #eaeaf2', color: '#1a1a24' }}
            >
              {clockInMutation.isPending ? 'Clocking in…' : 'Add Manual Clock-in'}
            </button>
          )}
        </div>

        {/* AI Candidates */}
        {candidates && candidates.length > 0 && (
          <div>
            <p className="text-xs font-medium uppercase mb-2" style={{ color: '#94a3b8' }}>
              AI Candidates
            </p>
            <div className="space-y-2">
              {candidates.slice(0, 5).map((candidate) => {
                const cg = caregiverMap.get(candidate.caregiverId)
                return (
                  <div
                    key={candidate.caregiverId}
                    className="flex items-center justify-between rounded-lg px-3 py-2"
                    style={{ backgroundColor: '#f6f6fa', border: '1px solid #eaeaf2' }}
                  >
                    <span className="text-sm" style={{ color: '#1a1a24' }}>
                      {cg ? `${cg.firstName} ${cg.lastName}` : candidate.caregiverId}
                    </span>
                    <div className="flex items-center gap-2">
                      <span className="text-xs" style={{ color: '#747480' }}>
                        Score: {candidate.score}
                      </span>
                      <button
                        onClick={() => handleAssign(candidate.caregiverId)}
                        disabled={assignMutation.isPending}
                        className="text-xs font-medium px-2 py-0.5 rounded disabled:opacity-50"
                        style={{ color: '#1a9afa' }}
                      >
                        Assign
                      </button>
                    </div>
                  </div>
                )
              })}
            </div>
          </div>
        )}
      </div>
    </SlidePanel>
  )
}
```

- [ ] **Step 6.2: Commit**

```bash
cd frontend && git add src/components/schedule/ShiftDetailPanel.tsx
git commit -m "feat: wire ShiftDetailPanel to real shift detail, assign, clock-in, and AI candidates APIs"
```

---

### Task 7: Update NewShiftPanel to Use Real Data

**Files:**
- Modify: `frontend/src/components/schedule/NewShiftPanel.tsx`

- [ ] **Step 7.1: Update NewShiftPanel**

Replace the full contents of `frontend/src/components/schedule/NewShiftPanel.tsx`:

```tsx
import { useForm } from 'react-hook-form'
import { SlidePanel } from '../panel/SlidePanel'
import { useClients } from '../../hooks/useClients'
import { useCaregivers } from '../../hooks/useCaregivers'
import { useCreateShift } from '../../hooks/useShifts'

interface NewShiftFormValues {
  clientId: string
  caregiverId: string
  serviceTypeId: string
  scheduledStart: string
  scheduledEnd: string
  notes: string
}

interface NewShiftPanelProps {
  onClose: () => void
}

// Format a datetime-local input value to ISO-8601 LocalDateTime (no timezone suffix)
function toLocalDateTime(datetimeLocalValue: string): string {
  // datetime-local gives "2026-04-07T09:00" — backend wants "2026-04-07T09:00:00"
  return datetimeLocalValue.length === 16 ? `${datetimeLocalValue}:00` : datetimeLocalValue
}

export function NewShiftPanel({ onClose }: NewShiftPanelProps) {
  const { clients } = useClients()
  const { caregivers } = useCaregivers()
  const createMutation = useCreateShift()

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<NewShiftFormValues>()

  const onSubmit = async (values: NewShiftFormValues) => {
    await createMutation.mutateAsync({
      clientId: values.clientId,
      caregiverId: values.caregiverId || null,
      serviceTypeId: values.serviceTypeId,
      scheduledStart: toLocalDateTime(values.scheduledStart),
      scheduledEnd: toLocalDateTime(values.scheduledEnd),
      notes: values.notes || null,
    })
    onClose()
  }

  return (
    <SlidePanel title="New Shift" onClose={onClose}>
      <form onSubmit={handleSubmit(onSubmit)} noValidate className="p-4 space-y-4">
        {/* Client */}
        <div>
          <label className="block text-xs font-medium mb-1" style={{ color: '#747480' }}>
            Client *
          </label>
          <select
            className="w-full rounded-lg px-3 py-2 text-sm"
            style={{ border: '1px solid #eaeaf2', backgroundColor: '#ffffff', color: '#1a1a24' }}
            {...register('clientId', { required: 'Client is required' })}
          >
            <option value="">Select a client…</option>
            {clients.map((c) => (
              <option key={c.id} value={c.id}>
                {c.firstName} {c.lastName}
              </option>
            ))}
          </select>
          {errors.clientId && (
            <p className="mt-1 text-xs" style={{ color: '#dc2626' }}>
              {errors.clientId.message}
            </p>
          )}
        </div>

        {/* Caregiver (optional) */}
        <div>
          <label className="block text-xs font-medium mb-1" style={{ color: '#747480' }}>
            Caregiver (optional)
          </label>
          <select
            className="w-full rounded-lg px-3 py-2 text-sm"
            style={{ border: '1px solid #eaeaf2', backgroundColor: '#ffffff', color: '#1a1a24' }}
            {...register('caregiverId')}
          >
            <option value="">Leave unassigned</option>
            {caregivers.map((c) => (
              <option key={c.id} value={c.id}>
                {c.firstName} {c.lastName}
              </option>
            ))}
          </select>
        </div>

        {/* Service Type ID — free text for now; Phase 6+ can replace with a dropdown */}
        <div>
          <label className="block text-xs font-medium mb-1" style={{ color: '#747480' }}>
            Service Type ID *
          </label>
          <input
            type="text"
            placeholder="e.g. uuid of service type"
            className="w-full rounded-lg px-3 py-2 text-sm"
            style={{ border: '1px solid #eaeaf2', backgroundColor: '#ffffff', color: '#1a1a24' }}
            {...register('serviceTypeId', { required: 'Service type is required' })}
          />
          {errors.serviceTypeId && (
            <p className="mt-1 text-xs" style={{ color: '#dc2626' }}>
              {errors.serviceTypeId.message}
            </p>
          )}
        </div>

        {/* Start */}
        <div>
          <label className="block text-xs font-medium mb-1" style={{ color: '#747480' }}>
            Start *
          </label>
          <input
            type="datetime-local"
            className="w-full rounded-lg px-3 py-2 text-sm"
            style={{ border: '1px solid #eaeaf2', backgroundColor: '#ffffff', color: '#1a1a24' }}
            {...register('scheduledStart', { required: 'Start time is required' })}
          />
          {errors.scheduledStart && (
            <p className="mt-1 text-xs" style={{ color: '#dc2626' }}>
              {errors.scheduledStart.message}
            </p>
          )}
        </div>

        {/* End */}
        <div>
          <label className="block text-xs font-medium mb-1" style={{ color: '#747480' }}>
            End *
          </label>
          <input
            type="datetime-local"
            className="w-full rounded-lg px-3 py-2 text-sm"
            style={{ border: '1px solid #eaeaf2', backgroundColor: '#ffffff', color: '#1a1a24' }}
            {...register('scheduledEnd', { required: 'End time is required' })}
          />
          {errors.scheduledEnd && (
            <p className="mt-1 text-xs" style={{ color: '#dc2626' }}>
              {errors.scheduledEnd.message}
            </p>
          )}
        </div>

        {/* Notes */}
        <div>
          <label className="block text-xs font-medium mb-1" style={{ color: '#747480' }}>
            Notes
          </label>
          <textarea
            rows={3}
            className="w-full rounded-lg px-3 py-2 text-sm resize-none"
            style={{ border: '1px solid #eaeaf2', backgroundColor: '#ffffff', color: '#1a1a24' }}
            {...register('notes')}
          />
        </div>

        {/* Error */}
        {createMutation.isError && (
          <p className="text-xs" style={{ color: '#dc2626' }}>
            Failed to create shift. Please try again.
          </p>
        )}

        {/* Submit */}
        <button
          type="submit"
          disabled={isSubmitting || createMutation.isPending}
          className="w-full rounded-lg py-2 text-sm font-medium text-white disabled:opacity-50"
          style={{ backgroundColor: '#1a9afa' }}
        >
          {isSubmitting || createMutation.isPending ? 'Creating…' : 'Create Shift'}
        </button>
      </form>
    </SlidePanel>
  )
}
```

- [ ] **Step 7.2: Verify TypeScript and build**

```bash
cd frontend && npx tsc --noEmit 2>&1 | head -20
```

Expected: no errors.

```bash
cd frontend && npm run build 2>&1 | tail -5
```

Expected: clean build.

- [ ] **Step 7.3: Commit**

```bash
cd frontend && git add src/components/schedule/NewShiftPanel.tsx
git commit -m "feat: wire NewShiftPanel to real API using useClients, useCaregivers, useCreateShift"
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

1. Log in and navigate to `/schedule`.
2. Verify the week calendar renders. If no shifts exist for the current week, confirm the loading spinner disappears and an empty calendar grid is shown (no JavaScript errors in console).
3. Click "Next →" / "← Prev" to change weeks — network requests should fire for the new date range.
4. Click "+ New Shift", fill in all required fields, and submit. Verify the new shift appears on the calendar.
5. Click on an existing shift (if any). The detail panel should show the real client and caregiver names, EVV status badge in the correct color, and AI candidate list (empty if scoring is disabled for the agency).
6. In the detail panel, click "Assign" next to a candidate. The shift should update to ASSIGNED status.
7. Open DevTools → Network. Confirm `GET /api/v1/shifts?start=...&end=...` returns a paged JSON response. Confirm `GET /api/v1/clients?size=100` and `GET /api/v1/caregivers?size=100` are called once (cached for 60s).

Proceed to Phase 5 only after this checkpoint passes.
