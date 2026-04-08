# Broadcast Open Modal — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the stub `alert()` on the "Broadcast Open" button with a real modal that fetches the week's OPEN shifts, shows them in a confirmation list, then broadcasts each sequentially — showing per-row spinner → checkmark/error as each call settles.

**Architecture:** Add an optional `status` filter to the existing `GET /shifts` endpoint (backend), add a `listOpenShifts` helper in the API layer (frontend), then build a self-contained `BroadcastOpenModal` component driven by a local phase state machine (`loading → confirm → broadcasting → done`, with an `error` phase if the initial fetch fails). Sequential broadcasts fire one at a time via plain `async/await` inside a loop — no React Query mutation needed for the loop itself.

**Tech Stack:** Java 25 / Spring Boot 3.4.4 (backend), React 18 + TypeScript + React Query + Tailwind (frontend), Vitest + Testing Library (frontend tests), JUnit 5 + Mockito (backend tests).

---

## File Map

| File | Action | Purpose |
|------|--------|---------|
| `backend/.../domain/ShiftRepository.java` | Modify | Add `findByAgencyIdAndStatusAndScheduledStartBetween()` derived query |
| `backend/.../scheduling/ShiftSchedulingService.java` | Modify | Add `status` param to `listShifts()`; branch to filtered repo query when non-null |
| `backend/.../scheduling/ShiftSchedulingController.java` | Modify | Add optional `@RequestParam ShiftStatus status` to `listShifts` handler; remove redundant date validation |
| `backend/.../scheduling/ShiftSchedulingServiceTest.java` | Modify | Update existing `listShifts` calls (add `null` arg); add 2 new status-filter tests |
| `backend/.../scheduling/ShiftSchedulingControllerIT.java` | Modify | Add IT tests: `?status=OPEN` returns only OPEN shifts; `?status=INVALID` returns 400 |
| `frontend/src/api/shifts.ts` | Modify | Add `listOpenShifts(start, end)` — calls `/shifts?status=OPEN` |
| `frontend/src/api/shifts.test.ts` | Modify | Add test for `listOpenShifts` |
| `frontend/src/components/schedule/BroadcastOpenModal.tsx` | **Create** | Full modal: loading → confirm list → per-row broadcast progress → done summary |
| `frontend/src/components/schedule/BroadcastOpenModal.test.tsx` | **Create** | Tests for all 4 phases and empty-shifts case |
| `frontend/src/components/schedule/SchedulePage.tsx` | Modify | Replace `alert()` with modal state; render `<BroadcastOpenModal>` |
| `frontend/public/locales/en/schedule.json` | Modify | Add `broadcastModal.*` i18n keys |

---

## Task 1: Backend — Add status filter to ShiftRepository

**Files:**
- Modify: `backend/src/main/java/com/hcare/domain/ShiftRepository.java`

- [ ] **Step 1: Add the derived query method**

Open `ShiftRepository.java`. Add this method after `findByAgencyIdAndScheduledStartBetween`:

```java
Page<Shift> findByAgencyIdAndStatusAndScheduledStartBetween(UUID agencyId,
                                                              ShiftStatus status,
                                                              LocalDateTime start,
                                                              LocalDateTime end,
                                                              Pageable pageable);
```

Spring Data JPA derives the SQL automatically — no `@Query` annotation needed.

- [ ] **Step 2: Verify it compiles**

```bash
cd backend && mvn compile -q
```
Expected: `BUILD SUCCESS` with no output.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/hcare/domain/ShiftRepository.java
git commit -m "feat: add findByAgencyIdAndStatusAndScheduledStartBetween to ShiftRepository"
```

---

## Task 2: Backend — Add status param to ShiftSchedulingService

**Files:**
- Modify: `backend/src/main/java/com/hcare/api/v1/scheduling/ShiftSchedulingService.java`
- Modify: `backend/src/test/java/com/hcare/api/v1/scheduling/ShiftSchedulingServiceTest.java`

- [ ] **Step 1: Write the two new failing tests**

In `ShiftSchedulingServiceTest.java`, add these two tests inside the `// --- listShifts ---` section (after the existing `listShifts_rejects_inverted_date_range` test):

```java
@Test
void listShifts_withStatusFilter_delegates_to_status_filtered_repo() {
    LocalDateTime start = LocalDateTime.now();
    LocalDateTime end = start.plusDays(7);
    Shift shift = new Shift(agencyId, null, clientId, null, serviceTypeId, null,
        start.plusHours(1), start.plusHours(5));
    when(shiftRepository.findByAgencyIdAndStatusAndScheduledStartBetween(
        agencyId, ShiftStatus.OPEN, start, end, any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(shift)));

    Page<ShiftSummaryResponse> result = service.listShifts(agencyId, start, end, ShiftStatus.OPEN, Pageable.unpaged());

    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).status()).isEqualTo(ShiftStatus.OPEN);
    verify(shiftRepository).findByAgencyIdAndStatusAndScheduledStartBetween(
        eq(agencyId), eq(ShiftStatus.OPEN), eq(start), eq(end), any(Pageable.class));
    verify(shiftRepository, never()).findByAgencyIdAndScheduledStartBetween(any(), any(), any(), any());
}

@Test
void listShifts_withoutStatusFilter_delegates_to_unfiltered_repo() {
    LocalDateTime start = LocalDateTime.now();
    LocalDateTime end = start.plusDays(7);
    when(shiftRepository.findByAgencyIdAndScheduledStartBetween(agencyId, start, end, any(Pageable.class)))
        .thenReturn(Page.empty());

    service.listShifts(agencyId, start, end, null, Pageable.unpaged());

    verify(shiftRepository).findByAgencyIdAndScheduledStartBetween(
        eq(agencyId), eq(start), eq(end), any(Pageable.class));
    verify(shiftRepository, never()).findByAgencyIdAndStatusAndScheduledStartBetween(
        any(), any(), any(), any(), any());
}
```

- [ ] **Step 2: Update the two existing `listShifts` test calls to pass `null` as status**

The existing tests call the old 4-arg signature. Find these two calls and add `null` as the 4th argument:

```java
// listShifts_delegates_to_repository_and_maps_to_response — change:
Page<ShiftSummaryResponse> result = service.listShifts(agencyId, start, end, Pageable.unpaged());
// to:
Page<ShiftSummaryResponse> result = service.listShifts(agencyId, start, end, null, Pageable.unpaged());

// listShifts_rejects_inverted_date_range — change:
assertThatThrownBy(() -> service.listShifts(agencyId, start, end, Pageable.unpaged()))
// to:
assertThatThrownBy(() -> service.listShifts(agencyId, start, end, null, Pageable.unpaged()))
```

- [ ] **Step 3: Run tests — confirm new tests fail, existing pass**

```bash
cd backend && mvn test -Dtest=ShiftSchedulingServiceTest 2>&1 | grep -E "FAIL|PASS|ERROR|Tests run"
```

Expected: the 2 new tests FAIL (method doesn't exist yet); existing tests pass.

- [ ] **Step 4: Update the service method signature and implementation**

In `ShiftSchedulingService.java`, change the `listShifts` method:

```java
@Transactional(readOnly = true)
public Page<ShiftSummaryResponse> listShifts(UUID agencyId, LocalDateTime start, LocalDateTime end,
                                              ShiftStatus status, Pageable pageable) {
    if (!end.isAfter(start)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "end must be after start");
    }
    if (status != null) {
        return shiftRepository.findByAgencyIdAndStatusAndScheduledStartBetween(agencyId, status, start, end, pageable)
            .map(this::toSummary);
    }
    return shiftRepository.findByAgencyIdAndScheduledStartBetween(agencyId, start, end, pageable)
        .map(this::toSummary);
}
```

The import `com.hcare.domain.ShiftStatus` is already in the file.

- [ ] **Step 5: Run tests — all pass**

```bash
cd backend && mvn test -Dtest=ShiftSchedulingServiceTest 2>&1 | grep "Tests run:"
```

Expected: `Tests run: N, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/hcare/api/v1/scheduling/ShiftSchedulingService.java \
        backend/src/test/java/com/hcare/api/v1/scheduling/ShiftSchedulingServiceTest.java
git commit -m "feat: add optional status filter to ShiftSchedulingService.listShifts"
```

---

## Task 3: Backend — Expose status param in controller

**Files:**
- Modify: `backend/src/main/java/com/hcare/api/v1/scheduling/ShiftSchedulingController.java`
- Modify: `backend/src/test/java/com/hcare/api/v1/scheduling/ShiftSchedulingControllerIT.java`

- [ ] **Step 1: Add the optional status param; remove the redundant date validation**

In `ShiftSchedulingController.java`, replace the `listShifts` method entirely with:

```java
@GetMapping
@PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
public ResponseEntity<Page<ShiftSummaryResponse>> listShifts(
        @AuthenticationPrincipal UserPrincipal principal,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
        @RequestParam(required = false) ShiftStatus status,
        @PageableDefault(size = 20, sort = "scheduledStart") Pageable pageable) {
    return ResponseEntity.ok(shiftSchedulingService.listShifts(principal.getAgencyId(), start, end, status, pageable));
}
```

The `end.isAfter(start)` check that was in the controller is **removed** — it was redundant. The service already throws `ResponseStatusException(BAD_REQUEST)` for inverted ranges, which propagates correctly. Also remove the `ResponseStatusException` import if it is now unused in the controller.

- [ ] **Step 2: Add integration tests to ShiftSchedulingControllerIT**

In `ShiftSchedulingControllerIT.java`, add two tests (follow the existing `mockMvc` / `adminToken` / auth pattern already used in that file):

```java
@Test
void listShifts_withStatusFilter_returnsOnlyOpenShifts() throws Exception {
    mockMvc.perform(get("/api/v1/shifts")
            .header("Authorization", "Bearer " + adminToken)
            .param("start", "2026-04-07T00:00:00")
            .param("end", "2026-04-14T00:00:00")
            .param("status", "OPEN"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[*].status", everyItem(is("OPEN"))));
}

@Test
void listShifts_withInvalidStatus_returns400() throws Exception {
    mockMvc.perform(get("/api/v1/shifts")
            .header("Authorization", "Bearer " + adminToken)
            .param("start", "2026-04-07T00:00:00")
            .param("end", "2026-04-14T00:00:00")
            .param("status", "NOT_A_STATUS"))
        .andExpect(status().isBadRequest());
}
```

Add required static imports if not already present:
```java
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
```

- [ ] **Step 3: Run the full backend test suite**

```bash
cd backend && mvn test 2>&1 | grep "Tests run:" | tail -3
```

Expected: all tests pass.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/hcare/api/v1/scheduling/ShiftSchedulingController.java \
        backend/src/test/java/com/hcare/api/v1/scheduling/ShiftSchedulingControllerIT.java
git commit -m "feat: expose optional status query param on GET /shifts; add controller IT"
```

---

## Task 4: Frontend — Add `listOpenShifts` API function

**Files:**
- Modify: `frontend/src/api/shifts.ts`
- Modify: `frontend/src/api/shifts.test.ts`

- [ ] **Step 1: Write the failing test**

In `shifts.test.ts`, add this test (also add `listOpenShifts` to the import on line 4):

```typescript
it('listOpenShifts calls GET /shifts with status=OPEN', async () => {
  const page: PageResponse<ShiftSummaryResponse> = {
    content: [summary], totalElements: 1, totalPages: 1, number: 0, size: 200, first: true, last: true,
  }
  mock.onGet('/shifts', {
    params: { start: '2026-04-06T00:00:00', end: '2026-04-13T00:00:00', status: 'OPEN', size: 200, sort: 'scheduledStart' },
  }).reply(200, page)

  const result = await listOpenShifts('2026-04-06T00:00:00', '2026-04-13T00:00:00')
  expect(result).toHaveLength(1)
  expect(result[0].status).toBe('OPEN')
})
```

- [ ] **Step 2: Run to confirm it fails**

```bash
cd frontend && npm test -- --reporter=verbose shifts.test 2>&1 | grep -E "FAIL|PASS|✓|✗"
```

Expected: new test fails with import error.

- [ ] **Step 3: Add the function to shifts.ts**

```typescript
export async function listOpenShifts(start: string, end: string): Promise<ShiftSummaryResponse[]> {
  const response = await apiClient.get<PageResponse<ShiftSummaryResponse>>('/shifts', {
    params: { start, end, status: 'OPEN', size: 200, sort: 'scheduledStart' },
  })
  return response.data.content
}
```

- [ ] **Step 4: Run tests — all pass**

```bash
cd frontend && npm test -- --reporter=verbose shifts.test 2>&1 | grep -E "✓|✗|Tests"
```

Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/api/shifts.ts frontend/src/api/shifts.test.ts
git commit -m "feat: add listOpenShifts API function with status=OPEN filter"
```

---

## Task 5: Frontend — Add i18n keys for the modal

**Files:**
- Modify: `frontend/public/locales/en/schedule.json`

- [ ] **Step 1: Add the broadcastModal keys**

Open `frontend/public/locales/en/schedule.json` and add a `broadcastModal` object:

```json
{
  "pageTitle": "Schedule",
  "backLabel": "← Schedule",
  "newShift": "+ New Shift",
  "broadcastOpen": "Broadcast Open",
  "broadcastOpenAlert": "Broadcast Open: confirms then broadcasts all unassigned shifts",
  "prevWeek": "←",
  "nextWeek": "→",
  "alertStripToday": "Today:",
  "alertRedEvv": "RED EVV",
  "alertYellowEvv": "YELLOW EVV",
  "alertUncovered": "Uncovered",
  "alertLateClockIn": "Late clock-in",
  "settingsComingSoon": "Settings — coming soon",
  "newShiftAt": "New shift at {{hour}}:00",
  "loading": "Loading shifts…",
  "broadcastModal": {
    "title": "Broadcast Open Shifts",
    "loading": "Loading open shifts…",
    "noOpenShifts": "No open shifts this week to broadcast.",
    "confirmSubtitle_one": "{{count}} open shift will be broadcast to eligible caregivers.",
    "confirmSubtitle_other": "{{count}} open shifts will be broadcast to eligible caregivers.",
    "confirmBtn_one": "Broadcast {{count}} Shift",
    "confirmBtn_other": "Broadcast {{count}} Shifts",
    "cancel": "Cancel",
    "broadcasting": "Broadcasting…",
    "doneAllSuccess_one": "{{count}} shift broadcast successfully.",
    "doneAllSuccess_other": "{{count}} shifts broadcast successfully.",
    "doneSummary": "{{success}} broadcast, {{failed}} failed.",
    "close": "Close",
    "shiftRow": "{{date}} · {{time}} · {{client}}",
    "loadError": "Failed to load open shifts. Please try again."
  }
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/public/locales/en/schedule.json
git commit -m "feat: add broadcastModal i18n keys to schedule namespace"
```

---

## Task 6: Frontend — Create BroadcastOpenModal component

**Files:**
- Create: `frontend/src/components/schedule/BroadcastOpenModal.tsx`
- Create: `frontend/src/components/schedule/BroadcastOpenModal.test.tsx`

- [ ] **Step 1: Write the failing tests first**

Create `frontend/src/components/schedule/BroadcastOpenModal.test.tsx`:

```typescript
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { BroadcastOpenModal } from './BroadcastOpenModal'
import * as shiftsApi from '../../api/shifts'
import type { ShiftSummaryResponse } from '../../types/api'

vi.mock('../../api/shifts')
vi.mock('@tanstack/react-query', () => ({
  useQueryClient: () => ({ invalidateQueries: vi.fn() }),
}))
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) => {
      if (opts?.count !== undefined) return `${key}:${opts.count}`
      if (opts) return `${key}:${JSON.stringify(opts)}`
      return key
    },
  }),
}))

const clientMap = new Map([['c1', { firstName: 'Alice', lastName: 'Johnson' }]])

const openShift: ShiftSummaryResponse = {
  id: 's1', agencyId: 'a1', clientId: 'c1', caregiverId: null,
  serviceTypeId: 'st1', authorizationId: null, sourcePatternId: null,
  scheduledStart: '2026-04-07T09:00:00', scheduledEnd: '2026-04-07T13:00:00',
  status: 'OPEN', notes: null,
}

describe('BroadcastOpenModal', () => {
  beforeEach(() => vi.clearAllMocks())

  it('shows loading state on open', () => {
    // Never-resolving promise keeps phase at 'loading' for the duration of the assertion
    vi.mocked(shiftsApi.listOpenShifts).mockImplementation(() => new Promise(() => {}))
    render(
      <BroadcastOpenModal
        open={true} onClose={vi.fn()}
        weekStart="2026-04-07T00:00:00" weekEnd="2026-04-14T00:00:00"
        clientMap={clientMap}
      />
    )
    expect(screen.getByText('broadcastModal.loading')).toBeInTheDocument()
  })

  it('shows empty state when no open shifts', async () => {
    vi.mocked(shiftsApi.listOpenShifts).mockResolvedValue([])
    render(
      <BroadcastOpenModal
        open={true} onClose={vi.fn()}
        weekStart="2026-04-07T00:00:00" weekEnd="2026-04-14T00:00:00"
        clientMap={clientMap}
      />
    )
    await waitFor(() =>
      expect(screen.getByText('broadcastModal.noOpenShifts')).toBeInTheDocument()
    )
  })

  it('shows confirm list with shift rows and broadcast button', async () => {
    vi.mocked(shiftsApi.listOpenShifts).mockResolvedValue([openShift])
    render(
      <BroadcastOpenModal
        open={true} onClose={vi.fn()}
        weekStart="2026-04-07T00:00:00" weekEnd="2026-04-14T00:00:00"
        clientMap={clientMap}
      />
    )
    await waitFor(() =>
      expect(screen.getByText('Alice Johnson')).toBeInTheDocument()
    )
    expect(screen.getByRole('button', { name: /broadcastModal.confirmBtn/ })).toBeInTheDocument()
  })

  it('transitions to broadcasting phase and calls broadcastShift for each shift', async () => {
    vi.mocked(shiftsApi.listOpenShifts).mockResolvedValue([openShift])
    vi.mocked(shiftsApi.broadcastShift).mockResolvedValue(undefined)
    render(
      <BroadcastOpenModal
        open={true} onClose={vi.fn()}
        weekStart="2026-04-07T00:00:00" weekEnd="2026-04-14T00:00:00"
        clientMap={clientMap}
      />
    )
    await waitFor(() => screen.getByRole('button', { name: /broadcastModal.confirmBtn/ }))
    fireEvent.click(screen.getByRole('button', { name: /broadcastModal.confirmBtn/ }))
    await waitFor(() =>
      expect(shiftsApi.broadcastShift).toHaveBeenCalledWith('s1')
    )
  })

  it('shows done summary after all broadcasts complete', async () => {
    vi.mocked(shiftsApi.listOpenShifts).mockResolvedValue([openShift])
    vi.mocked(shiftsApi.broadcastShift).mockResolvedValue(undefined)
    render(
      <BroadcastOpenModal
        open={true} onClose={vi.fn()}
        weekStart="2026-04-07T00:00:00" weekEnd="2026-04-14T00:00:00"
        clientMap={clientMap}
      />
    )
    await waitFor(() => screen.getByRole('button', { name: /broadcastModal.confirmBtn/ }))
    fireEvent.click(screen.getByRole('button', { name: /broadcastModal.confirmBtn/ }))
    await waitFor(() =>
      expect(screen.getByText(/broadcastModal.doneAllSuccess/)).toBeInTheDocument()
    )
    expect(screen.getByRole('button', { name: 'broadcastModal.close' })).toBeInTheDocument()
  })

  it('calls onClose when cancel is clicked in confirm phase', async () => {
    vi.mocked(shiftsApi.listOpenShifts).mockResolvedValue([openShift])
    const onClose = vi.fn()
    render(
      <BroadcastOpenModal
        open={true} onClose={onClose}
        weekStart="2026-04-07T00:00:00" weekEnd="2026-04-14T00:00:00"
        clientMap={clientMap}
      />
    )
    await waitFor(() => screen.getByRole('button', { name: 'broadcastModal.cancel' }))
    fireEvent.click(screen.getByRole('button', { name: 'broadcastModal.cancel' }))
    expect(onClose).toHaveBeenCalled()
  })

  it('shows error state and close button when fetch fails', async () => {
    vi.mocked(shiftsApi.listOpenShifts).mockRejectedValue(new Error('network'))
    const onClose = vi.fn()
    render(
      <BroadcastOpenModal
        open={true} onClose={onClose}
        weekStart="2026-04-07T00:00:00" weekEnd="2026-04-14T00:00:00"
        clientMap={clientMap}
      />
    )
    await waitFor(() =>
      expect(screen.getByText('broadcastModal.loadError')).toBeInTheDocument()
    )
    fireEvent.click(screen.getByRole('button', { name: 'broadcastModal.close' }))
    expect(onClose).toHaveBeenCalled()
  })

  it('renders nothing when open is false', () => {
    render(
      <BroadcastOpenModal
        open={false} onClose={vi.fn()}
        weekStart="2026-04-07T00:00:00" weekEnd="2026-04-14T00:00:00"
        clientMap={clientMap}
      />
    )
    expect(screen.queryByText('broadcastModal.title')).not.toBeInTheDocument()
  })
})
```

- [ ] **Step 2: Run tests — confirm they all fail**

```bash
cd frontend && npm test -- --reporter=verbose BroadcastOpenModal.test 2>&1 | grep -E "✓|✗|FAIL|cannot find"
```

Expected: all 8 tests fail (module not found).

- [ ] **Step 3: Create the component**

Create `frontend/src/components/schedule/BroadcastOpenModal.tsx`:

```tsx
import { useState, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { useQueryClient } from '@tanstack/react-query'
import { listOpenShifts, broadcastShift } from '../../api/shifts'
import type { ShiftSummaryResponse } from '../../types/api'

type Phase = 'loading' | 'confirm' | 'broadcasting' | 'done' | 'error'
type RowResult = 'idle' | 'pending' | 'success' | 'error'

interface Props {
  open: boolean
  onClose: () => void
  weekStart: string
  weekEnd: string
  clientMap: Map<string, { firstName: string; lastName: string }>
}

function formatShiftTime(iso: string): string {
  const d = new Date(iso)
  return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
}

function formatShiftDate(iso: string): string {
  const d = new Date(iso)
  return d.toLocaleDateString([], { month: 'short', day: 'numeric' })
}

export function BroadcastOpenModal({ open, onClose, weekStart, weekEnd, clientMap }: Props) {
  const { t } = useTranslation('schedule')
  const queryClient = useQueryClient()
  const [phase, setPhase] = useState<Phase>('loading')
  const [openShifts, setOpenShifts] = useState<ShiftSummaryResponse[]>([])
  const [results, setResults] = useState<Record<string, RowResult>>({})

  useEffect(() => {
    if (!open) return
    setPhase('loading')
    setResults({})
    listOpenShifts(weekStart, weekEnd)
      .then((shifts) => {
        setOpenShifts(shifts)
        setPhase('confirm')
      })
      .catch(() => setPhase('error'))
  }, [open, weekStart, weekEnd])

  async function handleConfirm() {
    const initial: Record<string, RowResult> = {}
    for (const s of openShifts) initial[s.id] = 'idle'
    setResults(initial)
    setPhase('broadcasting')

    for (const shift of openShifts) {
      setResults((prev) => ({ ...prev, [shift.id]: 'pending' }))
      try {
        await broadcastShift(shift.id)
        setResults((prev) => ({ ...prev, [shift.id]: 'success' }))
      } catch {
        setResults((prev) => ({ ...prev, [shift.id]: 'error' }))
      }
    }

    setPhase('done')
    queryClient.invalidateQueries({ queryKey: ['shifts'] })
  }

  function handleClose() {
    setPhase('loading')
    setOpenShifts([])
    setResults({})
    onClose()
  }

  if (!open) return null

  const successCount = Object.values(results).filter((r) => r === 'success').length
  const failCount = Object.values(results).filter((r) => r === 'error').length

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div
        className="bg-white rounded-xl shadow-xl w-full max-w-md mx-4 overflow-hidden"
        role="dialog"
        aria-modal="true"
        aria-labelledby="broadcast-modal-title"
      >
        {/* Header */}
        <div className="px-6 pt-5 pb-3 border-b border-border">
          <h2 id="broadcast-modal-title" className="text-[15px] font-bold text-dark">{t('broadcastModal.title')}</h2>
        </div>

        {/* Body */}
        <div className="px-6 py-4 max-h-[60vh] overflow-y-auto">
          {phase === 'loading' && (
            <p className="text-[13px] text-text-muted py-4 text-center">
              {t('broadcastModal.loading')}
            </p>
          )}

          {phase === 'error' && (
            <p className="text-[13px] text-red-600 py-4 text-center">
              {t('broadcastModal.loadError')}
            </p>
          )}

          {(phase === 'confirm') && openShifts.length === 0 && (
            <p className="text-[13px] text-text-secondary py-4 text-center">
              {t('broadcastModal.noOpenShifts')}
            </p>
          )}

          {(phase === 'confirm') && openShifts.length > 0 && (
            <>
              <p className="text-[13px] text-text-secondary mb-3">
                {t('broadcastModal.confirmSubtitle', { count: openShifts.length })}
              </p>
              <ul className="space-y-2">
                {openShifts.map((shift) => {
                  const client = clientMap.get(shift.clientId)
                  const clientName = client
                    ? `${client.firstName} ${client.lastName}`
                    : shift.clientId
                  return (
                    <li key={shift.id} className="flex items-center gap-3 py-2 border-b border-border last:border-0">
                      <span className="text-[12px] text-text-secondary w-20 shrink-0">
                        {formatShiftDate(shift.scheduledStart)}
                      </span>
                      <span className="text-[12px] text-text-secondary w-24 shrink-0">
                        {formatShiftTime(shift.scheduledStart)}–{formatShiftTime(shift.scheduledEnd)}
                      </span>
                      <span className="text-[13px] text-dark font-medium truncate">{clientName}</span>
                    </li>
                  )
                })}
              </ul>
            </>
          )}

          {(phase === 'broadcasting' || phase === 'done') && (
            <ul className="space-y-2">
              {openShifts.map((shift) => {
                const result = results[shift.id] ?? 'idle'
                const client = clientMap.get(shift.clientId)
                const clientName = client
                  ? `${client.firstName} ${client.lastName}`
                  : shift.clientId
                return (
                  <li key={shift.id} className="flex items-center gap-3 py-2 border-b border-border last:border-0">
                    <span className="w-5 shrink-0 text-center">
                      {result === 'idle' && <span className="text-text-muted">·</span>}
                      {result === 'pending' && (
                        <span className="inline-block w-3 h-3 border-2 border-blue-400 border-t-transparent rounded-full animate-spin" />
                      )}
                      {result === 'success' && <span className="text-green-500 text-[14px]">✓</span>}
                      {result === 'error' && <span className="text-red-500 text-[14px]">✗</span>}
                    </span>
                    <span className="text-[12px] text-text-secondary w-20 shrink-0">
                      {formatShiftDate(shift.scheduledStart)}
                    </span>
                    <span className="text-[13px] text-dark font-medium truncate">{clientName}</span>
                  </li>
                )
              })}
            </ul>
          )}

          {phase === 'done' && (
            <p className="text-[13px] text-text-secondary mt-4 text-center">
              {failCount === 0
                ? t('broadcastModal.doneAllSuccess', { count: successCount })
                : t('broadcastModal.doneSummary', { success: successCount, failed: failCount })}
            </p>
          )}
        </div>

        {/* Footer */}
        <div className="px-6 py-4 border-t border-border flex justify-end gap-3">
          {phase === 'error' && (
            <button
              type="button"
              onClick={handleClose}
              className="px-4 py-2 text-[13px] font-semibold border border-border text-dark rounded hover:brightness-95"
            >
              {t('broadcastModal.close')}
            </button>
          )}
          {phase === 'confirm' && openShifts.length === 0 && (
            <button
              type="button"
              onClick={handleClose}
              className="px-4 py-2 text-[13px] font-semibold border border-border text-dark rounded hover:brightness-95"
            >
              {t('broadcastModal.close')}
            </button>
          )}
          {phase === 'confirm' && openShifts.length > 0 && (
            <>
              <button
                type="button"
                onClick={handleClose}
                className="px-4 py-2 text-[13px] font-semibold border border-border text-dark rounded hover:brightness-95"
              >
                {t('broadcastModal.cancel')}
              </button>
              <button
                type="button"
                onClick={handleConfirm}
                className="px-4 py-2 text-[13px] font-bold bg-dark text-white rounded hover:brightness-110"
              >
                {t('broadcastModal.confirmBtn', { count: openShifts.length })}
              </button>
            </>
          )}
          {phase === 'broadcasting' && (
            <span className="text-[13px] text-text-muted self-center">
              {t('broadcastModal.broadcasting')}
            </span>
          )}
          {phase === 'done' && (
            <button
              type="button"
              onClick={handleClose}
              className="px-4 py-2 text-[13px] font-bold bg-dark text-white rounded hover:brightness-110"
            >
              {t('broadcastModal.close')}
            </button>
          )}
        </div>
      </div>
    </div>
  )
}
```

- [ ] **Step 4: Run tests — all pass**

```bash
cd frontend && npm test -- --reporter=verbose BroadcastOpenModal.test 2>&1 | grep -E "✓|✗|Tests"
```

Expected: 8 tests pass.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/schedule/BroadcastOpenModal.tsx \
        frontend/src/components/schedule/BroadcastOpenModal.test.tsx
git commit -m "feat: add BroadcastOpenModal with per-row broadcast progress"
```

---

## Task 7: Frontend — Wire modal into SchedulePage

**Files:**
- Modify: `frontend/src/components/schedule/SchedulePage.tsx`

- [ ] **Step 1: Add modal state and replace the alert()**

In `SchedulePage.tsx`:

1. Add the import at the top:
```typescript
import { BroadcastOpenModal } from './BroadcastOpenModal'
```

2. Add state inside the component (after existing `useState` calls):
```typescript
const [broadcastModalOpen, setBroadcastModalOpen] = useState(false)
```

3. Replace the button's `onClick`:
```tsx
// Old:
onClick={() => alert(t('broadcastOpenAlert'))}
// New:
onClick={() => setBroadcastModalOpen(true)}
```

4. Add the modal at the bottom of the returned JSX, before the closing `</div>`:
```tsx
<BroadcastOpenModal
  open={broadcastModalOpen}
  onClose={() => setBroadcastModalOpen(false)}
  weekStart={weekStartStr}
  weekEnd={weekEndStr}
  clientMap={clientMap}
/>
```

- [ ] **Step 2: Run the full frontend test suite**

```bash
cd frontend && npm test 2>&1 | grep -E "Tests|failed|passed"
```

Expected: all tests pass.

- [ ] **Step 3: Build check**

```bash
cd frontend && npm run build 2>&1 | tail -5
```

Expected: `✓ built in ...ms`

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/schedule/SchedulePage.tsx
git commit -m "feat: wire BroadcastOpenModal into SchedulePage — replace alert stub"
```

---

## Self-Review

**Spec coverage check:**

| Requirement | Task |
|-------------|------|
| Fetch OPEN shifts for the week before confirming | Task 4 (`listOpenShifts`), Task 6 (`useEffect` on open) |
| Show confirmation list of shifts | Task 6 (`confirm` phase) |
| Sequential per-shift broadcast calls | Task 6 (`handleConfirm` loop) |
| Per-row spinner → checkmark/error progress | Task 6 (`results` state, row icons) |
| Invalidate shifts query when done (calendar updates) | Task 6 (`queryClient.invalidateQueries`) |
| Fetch failure shows error state + close button | Task 6 (`error` phase, `.catch()`, error footer button) |
| Backend status filter param | Tasks 1–3 |
| Controller IT tests for status filter + invalid value | Task 3 |
| i18n for all strings including `loadError` | Task 5 |

**Placeholder scan:** No TBDs, no "similar to Task N" references, no missing type definitions.

**Type consistency:**
- `listOpenShifts` returns `ShiftSummaryResponse[]` — used as `openShifts` in modal ✓
- `clientMap: Map<string, { firstName: string; lastName: string }>` — matches `WeekCalendar` prop type ✓
- `broadcastShift(shift.id)` — `id: string`, matches existing `broadcastShift(shiftId: string)` signature ✓
- `BroadcastOpenModal` props interface matches usage in `SchedulePage` ✓
