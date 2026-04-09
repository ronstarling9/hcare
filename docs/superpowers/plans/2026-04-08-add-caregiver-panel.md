# Add Caregiver Panel — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the `alert()` placeholder on the Caregivers list with a real slide-panel form that creates a caregiver via `POST /api/v1/caregivers` and guides the user to add credentials.

**Architecture:** A new `NewCaregiverPanel` component wires into the existing `panelStore`/`SlidePanel` infrastructure. The shared `toastStore` + `Toast` component handles the "Save & Close" confirmation path. This task also renames `toastStore`'s `panelTab` field to `initialTab` and tightens `panelType` from `string` to `Exclude<PanelType, null>` — a naming inconsistency and type safety gap introduced by the Add Client spec. Key structural changes: add `'newCaregiver'` to `PanelType`, update `CaregiverDetailPanel` to accept `initialTab` with a validated type guard, register the new panel in `Shell`, and add a `useCreateCaregiver` mutation to `useCaregivers`.

**Tech Stack:** React 18, TypeScript, react-hook-form, @tanstack/react-query v5, Zustand, axios (axios-mock-adapter for tests), react-i18next, Vitest + Testing Library

---

## File Map

| Action | Path | Purpose |
|---|---|---|
| Modify | `src/types/api.ts` | Add `CreateCaregiverRequest` interface (before `CaregiverResponse`, line ~175) |
| Modify | `src/api/caregivers.ts` | Add `createCaregiver()` function + update imports |
| Modify | `src/hooks/useCaregivers.ts` | Add `useCreateCaregiver()` mutation + update import |
| Modify | `src/hooks/useCaregivers.test.ts` | Add `useCreateCaregiver` test |
| Modify | `src/store/toastStore.ts` | Rename `panelTab` → `initialTab`; fix `panelType: string` → `Exclude<PanelType, null>` |
| Modify | `src/store/toastStore.test.ts` | Rename `panelTab` → `initialTab`; fix zero-value `panelType: ''` → `'client' as const` |
| Modify | `src/components/common/Toast.tsx` | Rename `panelTab` → `initialTab`; remove unsafe `panelType` cast |
| Modify | `src/components/common/Toast.test.tsx` | Rename `panelTab` → `initialTab`; fix zero-value `panelType: ''` → `'client' as const` |
| Modify | `src/store/panelStore.ts` | Add `'newCaregiver'` to `PanelType` union |
| Modify | `src/components/caregivers/CaregiverDetailPanel.tsx` | Export `CaregiverTab` type + `CAREGIVER_TABS`; accept `initialTab?` prop with type guard |
| Create | `src/components/caregivers/CaregiverDetailPanel.test.tsx` | Test `initialTab` type guard (valid, invalid, undefined) |
| Modify | `public/locales/en/caregivers.json` | Add all new i18n keys |
| Create | `src/components/caregivers/NewCaregiverPanel.tsx` | Add caregiver form |
| Create | `src/components/caregivers/NewCaregiverPanel.test.tsx` | Form tests |
| Modify | `src/components/layout/Shell.tsx` | Register `newCaregiver`; pass `initialTab` to `CaregiverDetailPanel` |
| Modify | `src/components/caregivers/CaregiversPage.tsx` | Replace `alert()` with `openPanel('newCaregiver', ...)` |

---

## Task 1: `CreateCaregiverRequest` type + `createCaregiver()` API function

**Files:**
- Modify: `src/types/api.ts`
- Modify: `src/api/caregivers.ts`

- [ ] **Step 1: Add `CreateCaregiverRequest` to `src/types/api.ts`**

Find the `// ── Caregivers ──` section (line ~175). Insert the new interface immediately before `export interface CaregiverResponse`:

```typescript
export interface CreateCaregiverRequest {
  firstName: string
  lastName: string
  email: string
  phone?: string
  address?: string
  hireDate?: string    // YYYY-MM-DD — Jackson reads directly as LocalDate
  hasPet?: boolean     // defaults false on backend; omit to accept default
}
```

Result in context:

```typescript
// ── Caregivers ────────────────────────────────────────────────────────────────

export interface CreateCaregiverRequest {
  firstName: string
  lastName: string
  email: string
  phone?: string
  address?: string
  hireDate?: string
  hasPet?: boolean
}

export interface CaregiverResponse {
  id: string
  ...
```

- [ ] **Step 2: Add `createCaregiver()` to `src/api/caregivers.ts`**

Update the import block at the top (add `CreateCaregiverRequest` — do not duplicate other names):

```typescript
import type {
  CaregiverResponse,
  CredentialResponse,
  BackgroundCheckResponse,
  ShiftSummaryResponse,
  PageResponse,
  CreateCaregiverRequest,
} from '../types/api'
```

Append after `verifyCredential`:

```typescript
export async function createCaregiver(req: CreateCaregiverRequest): Promise<CaregiverResponse> {
  const response = await apiClient.post<CaregiverResponse>('/caregivers', req)
  return response.data
}
```

- [ ] **Step 3: Verify TypeScript compiles**

```bash
cd frontend && npx tsc --noEmit
```

Expected: no errors.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/types/api.ts frontend/src/api/caregivers.ts
git commit -m "feat: add CreateCaregiverRequest type and createCaregiver API function"
```

---

## Task 2: `useCreateCaregiver` mutation hook

**Files:**
- Modify: `src/hooks/useCaregivers.ts`
- Modify: `src/hooks/useCaregivers.test.ts`

- [ ] **Step 1: Write the failing test**

Open `src/hooks/useCaregivers.test.ts`. Add `useCreateCaregiver` and `createCaregiver` to the imports:

```typescript
// Update import line from:
import { useCaregivers, useCaregiverDetail } from './useCaregivers'
// To:
import { useCaregivers, useCaregiverDetail, useCreateCaregiver } from './useCaregivers'
```

Append this describe block after all existing tests:

```typescript
describe('useCreateCaregiver', () => {
  let mock: MockAdapter
  beforeEach(() => { mock = new MockAdapter(apiClient) })
  afterEach(() => { mock.restore() })

  it('calls POST /caregivers and invalidates the caregivers query key prefix on success', async () => {
    const qc = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    })
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries')
    const wrapper = ({ children }: { children: React.ReactNode }) =>
      React.createElement(QueryClientProvider, { client: qc }, children)

    mock.onPost('/caregivers').reply(201, {
      id: '00000000-0000-0000-0000-000000000099',
      firstName: 'Maria',
      lastName: 'Santos',
      email: 'maria@sunrise.dev',
      phone: null,
      address: null,
      hireDate: null,
      hasPet: false,
      status: 'ACTIVE',
      createdAt: '2026-04-08T10:00:00',
    })

    const { result } = renderHook(() => useCreateCaregiver(), { wrapper })

    result.current.mutate({
      firstName: 'Maria',
      lastName: 'Santos',
      email: 'maria@sunrise.dev',
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['caregivers'] })
  })
})
```

- [ ] **Step 2: Run test — verify it fails**

```bash
cd frontend && npm run test -- useCaregivers.test
```

Expected: FAIL — `useCreateCaregiver is not a function` (not exported).

- [ ] **Step 3: Add `useCreateCaregiver` to `src/hooks/useCaregivers.ts`**

Update the import from `../api/caregivers` (add `createCaregiver`):

```typescript
import {
  listCaregivers,
  getCaregiver,
  listCredentials,
  listBackgroundChecks,
  listAvailability,
  listShiftHistory,
  verifyCredential,
  createCaregiver,
} from '../api/caregivers'
```

Append after `useVerifyCredential`:

```typescript
export function useCreateCaregiver() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: createCaregiver,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['caregivers'] })
    },
  })
}
```

> **Note:** `{ queryKey: ['caregivers'] }` uses prefix matching — this invalidates both `['caregivers', page, size]` and `['caregivers', 'all']`.

- [ ] **Step 4: Run test — verify it passes**

```bash
cd frontend && npm run test -- useCaregivers.test
```

Expected: PASS (all existing tests + new `useCreateCaregiver` test).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/hooks/useCaregivers.ts frontend/src/hooks/useCaregivers.test.ts
git commit -m "feat: add useCreateCaregiver mutation hook with query invalidation"
```

---

## Task 3: `toastStore` + `Toast` — rename `panelTab` → `initialTab` and fix `panelType` typing

**Context:** The Add Client spec introduced `toastStore` with two issues that surface here: (1) the field `panelTab` should be named `initialTab` to match `PanelState.initialTab` and `openPanel`'s `options.initialTab`; (2) `panelType: string` is too loose — it allows any string at call sites and requires an unsafe cast in `Toast.tsx`. Both are fixed in this task by replacing the relevant sections in all four files.

**Files:**
- Modify: `src/store/toastStore.ts`
- Modify: `src/store/toastStore.test.ts`
- Modify: `src/components/common/Toast.tsx`
- Modify: `src/components/common/Toast.test.tsx`

- [ ] **Step 1: Write the failing tests (toastStore.test.ts)**

Replace the entire file `src/store/toastStore.test.ts`:

```typescript
import { describe, it, expect, beforeEach } from 'vitest'
import { useToastStore } from './toastStore'

const INITIAL_STATE = {
  visible: false,
  showCount: 0,
  message: '',
  linkLabel: '',
  targetId: null,
  panelType: 'client' as const,   // zero value — matches TOAST_ZERO_PANEL_TYPE
  initialTab: '',
  backLabel: '',
}

const SHOW_OPTS = {
  message: 'Caregiver saved. Add credentials to enable scheduling.',
  linkLabel: 'Add Credentials',
  targetId: 'caregiver-123',
  panelType: 'caregiver' as const,
  initialTab: 'credentials',
  backLabel: '← Caregivers',
}

describe('toastStore', () => {
  beforeEach(() => {
    useToastStore.setState(INITIAL_STATE)
  })

  it('show() sets visible to true and populates all fields including showCount', () => {
    useToastStore.getState().show(SHOW_OPTS)
    const s = useToastStore.getState()
    expect(s.visible).toBe(true)
    expect(s.showCount).toBe(1)
    expect(s.message).toBe('Caregiver saved. Add credentials to enable scheduling.')
    expect(s.linkLabel).toBe('Add Credentials')
    expect(s.targetId).toBe('caregiver-123')
    expect(s.panelType).toBe('caregiver')
    expect(s.initialTab).toBe('credentials')
    expect(s.backLabel).toBe('← Caregivers')
  })

  it('calling show() twice increments showCount each time', () => {
    useToastStore.getState().show(SHOW_OPTS)
    useToastStore.getState().show(SHOW_OPTS)
    expect(useToastStore.getState().showCount).toBe(2)
  })

  it('dismiss() resets all state to initial values', () => {
    useToastStore.getState().show(SHOW_OPTS)
    useToastStore.getState().dismiss()
    const s = useToastStore.getState()
    expect(s.visible).toBe(false)
    expect(s.showCount).toBe(0)
    expect(s.message).toBe('')
    expect(s.targetId).toBeNull()
    expect(s.panelType).toBe('client')   // reset to TOAST_ZERO_PANEL_TYPE
    expect(s.initialTab).toBe('')
    expect(s.backLabel).toBe('')
  })
})
```

- [ ] **Step 2: Run tests — verify they fail**

```bash
cd frontend && npm run test -- toastStore.test
```

Expected: FAIL — the store still has `panelTab` (not `initialTab`) and `panelType: string`.

- [ ] **Step 3: Replace `src/store/toastStore.ts`**

```typescript
import { create } from 'zustand'
import type { PanelType } from './panelStore'

// 'client' is a valid PanelType used as the zero value for panelType.
// The `visible: false` guard in Toast.tsx ensures it is never read or acted on.
const TOAST_ZERO_PANEL_TYPE: Exclude<PanelType, null> = 'client'

interface ToastState {
  visible: boolean
  showCount: number        // increments on every show(); useEffect dep for timer re-arm
  message: string
  linkLabel: string
  targetId: string | null  // passed to openPanel as the id argument
  panelType: Exclude<PanelType, null>  // typed union — catches invalid panel types at call sites
  initialTab: string       // passed as initialTab to openPanel options, e.g. 'credentials'
  backLabel: string        // e.g. '← Caregivers'
  show: (opts: {
    message: string
    linkLabel: string
    targetId: string
    panelType: Exclude<PanelType, null>
    initialTab: string
    backLabel: string
  }) => void
  dismiss: () => void
}

export const useToastStore = create<ToastState>((set) => ({
  visible: false,
  showCount: 0,
  message: '',
  linkLabel: '',
  targetId: null,
  panelType: TOAST_ZERO_PANEL_TYPE,
  initialTab: '',
  backLabel: '',

  show: (opts) =>
    set((prev) => ({
      visible: true,
      showCount: prev.showCount + 1,
      message: opts.message,
      linkLabel: opts.linkLabel,
      targetId: opts.targetId,
      panelType: opts.panelType,
      initialTab: opts.initialTab,
      backLabel: opts.backLabel,
    })),

  dismiss: () =>
    set({
      visible: false,
      showCount: 0,
      message: '',
      linkLabel: '',
      targetId: null,
      panelType: TOAST_ZERO_PANEL_TYPE,
      initialTab: '',
      backLabel: '',
    }),
}))
```

- [ ] **Step 4: Run toastStore tests — verify they pass**

```bash
cd frontend && npm run test -- toastStore.test
```

Expected: PASS (3 tests).

- [ ] **Step 5: Write the failing Toast component tests (Toast.test.tsx)**

Replace the entire file `src/components/common/Toast.test.tsx`:

```typescript
import { render, screen, fireEvent, act } from '@testing-library/react'
import { vi, describe, it, expect, beforeEach, afterEach } from 'vitest'
import { Toast } from './Toast'
import { useToastStore } from '../../store/toastStore'
import { usePanelStore } from '../../store/panelStore'

vi.mock('../../store/panelStore')

const mockOpenPanel = vi.fn()

const TOAST_STATE = {
  visible: true,
  showCount: 1,
  message: 'Caregiver saved. Add credentials to enable scheduling.',
  linkLabel: 'Add Credentials',
  targetId: 'caregiver-123',
  panelType: 'caregiver' as const,
  initialTab: 'credentials',
  backLabel: '← Caregivers',
}

const INITIAL_STORE = {
  visible: false,
  showCount: 0,
  message: '',
  linkLabel: '',
  targetId: null,
  panelType: 'client' as const,   // zero value — matches TOAST_ZERO_PANEL_TYPE
  initialTab: '',
  backLabel: '',
}

describe('Toast', () => {
  beforeEach(() => {
    vi.mocked(usePanelStore).mockReturnValue({
      openPanel: mockOpenPanel,
    } as ReturnType<typeof usePanelStore>)
    useToastStore.setState(INITIAL_STORE)
    mockOpenPanel.mockClear()
  })

  afterEach(() => {
    vi.clearAllTimers()
  })

  it('renders message and link when visible is true', () => {
    useToastStore.setState(TOAST_STATE)
    render(<Toast />)
    expect(screen.getByText('Caregiver saved. Add credentials to enable scheduling.')).toBeInTheDocument()
    expect(screen.getByText('Add Credentials')).toBeInTheDocument()
  })

  it('renders nothing when visible is false', () => {
    render(<Toast />)
    expect(screen.queryByText('Caregiver saved.')).not.toBeInTheDocument()
  })

  it('dismiss button click sets visible to false', () => {
    useToastStore.setState(TOAST_STATE)
    render(<Toast />)
    fireEvent.click(screen.getByRole('button', { name: 'Dismiss' }))
    expect(useToastStore.getState().visible).toBe(false)
  })

  it('link click calls openPanel with panelType, targetId, initialTab, and backLabel', () => {
    useToastStore.setState(TOAST_STATE)
    render(<Toast />)
    fireEvent.click(screen.getByText('Add Credentials'))
    expect(mockOpenPanel).toHaveBeenCalledWith('caregiver', 'caregiver-123', {
      initialTab: 'credentials',
      backLabel: '← Caregivers',
    })
  })

  it('auto-dismisses after 6 seconds', () => {
    vi.useFakeTimers()
    useToastStore.setState(TOAST_STATE)
    render(<Toast />)
    act(() => vi.advanceTimersByTime(6001))
    expect(useToastStore.getState().visible).toBe(false)
    vi.useRealTimers()
  })

  it('manual dismiss before 6 s clears timer — no stale dismiss fires after timeout', () => {
    vi.useFakeTimers()
    useToastStore.setState(TOAST_STATE)
    render(<Toast />)
    act(() => vi.advanceTimersByTime(3000))
    act(() => useToastStore.getState().dismiss())
    expect(useToastStore.getState().visible).toBe(false)
    // Advance past original 6 s — should not re-dismiss or error
    act(() => vi.advanceTimersByTime(4000))
    expect(useToastStore.getState().visible).toBe(false)
    vi.useRealTimers()
  })

  it('show() called while already visible resets the 6-second timer', () => {
    vi.useFakeTimers()
    useToastStore.setState(TOAST_STATE)
    const { rerender } = render(<Toast />)

    act(() => vi.advanceTimersByTime(4000))
    expect(useToastStore.getState().visible).toBe(true)

    act(() => useToastStore.setState({ ...TOAST_STATE, showCount: 2 }))
    rerender(<Toast />)

    act(() => vi.advanceTimersByTime(4000))
    expect(useToastStore.getState().visible).toBe(true)

    act(() => vi.advanceTimersByTime(2000))
    expect(useToastStore.getState().visible).toBe(false)
    vi.useRealTimers()
  })
})
```

- [ ] **Step 6: Run Toast tests — verify they fail**

```bash
cd frontend && npm run test -- Toast.test
```

Expected: FAIL — `Toast` still reads `panelTab` (not `initialTab`) and uses an unsafe `panelType` cast.

- [ ] **Step 7: Replace `src/components/common/Toast.tsx`**

```typescript
import { useEffect } from 'react'
import { useToastStore } from '../../store/toastStore'
import { usePanelStore } from '../../store/panelStore'

export function Toast() {
  const { visible, showCount, message, linkLabel, targetId, panelType, initialTab, backLabel, dismiss } =
    useToastStore()
  const { openPanel } = usePanelStore()

  useEffect(() => {
    if (!visible) return
    const id = setTimeout(() => dismiss(), 6000)
    return () => clearTimeout(id)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [visible, showCount])

  if (!visible) return null

  function handleLinkClick() {
    dismiss()
    // panelType is Exclude<PanelType, null> — no cast needed
    openPanel(panelType, targetId ?? undefined, {
      initialTab,
      backLabel,
    })
  }

  return (
    <div
      role="status"
      aria-live="polite"
      className="fixed bottom-4 right-4 z-50 flex items-center gap-3 px-4 py-3 bg-dark text-white text-[13px] shadow-lg max-w-sm"
    >
      <span>{message}</span>
      {linkLabel && targetId && (
        <button
          type="button"
          onClick={handleLinkClick}
          className="underline text-blue whitespace-nowrap hover:no-underline"
        >
          {linkLabel}
        </button>
      )}
      <button
        type="button"
        aria-label="Dismiss"
        onClick={dismiss}
        className="ml-1 text-text-muted hover:text-white text-[16px] leading-none"
      >
        ×
      </button>
    </div>
  )
}
```

- [ ] **Step 8: Run all Toast + toastStore tests — verify they pass**

```bash
cd frontend && npm run test -- Toast.test toastStore.test
```

Expected: PASS (all 9 tests across both files).

- [ ] **Step 9: Verify TypeScript compiles**

```bash
cd frontend && npx tsc --noEmit
```

Expected: no errors. If you see a TypeScript error about an unknown `panelTab` property (e.g. `NewClientPanel.tsx` still calls `show({ ..., panelTab: 'authorizations' })`), replace `panelTab` with `initialTab`. Steps 10 and 11 fix the known call sites.

- [ ] **Step 10: Fix `NewClientPanel.tsx` call to `useToastStore.getState().show()`**

Open `src/components/clients/NewClientPanel.tsx`. Find the `onSaveAndClose` function. Update the `show()` call to replace `panelTab` with `initialTab`:

```typescript
// Before:
useToastStore.getState().show({
  message: t('saveCloseToast'),
  linkLabel: t('saveCloseToastLink'),
  targetId: client.id,
  panelType: 'client',
  panelTab: 'authorizations',
  backLabel: t('backLabel'),
})

// After:
useToastStore.getState().show({
  message: t('saveCloseToast'),
  linkLabel: t('saveCloseToastLink'),
  targetId: client.id,
  panelType: 'client',
  initialTab: 'authorizations',
  backLabel: t('backLabel'),
})
```

- [ ] **Step 11: Fix `NewClientPanel.test.tsx` INITIAL_TOAST**

Open `src/components/clients/NewClientPanel.test.tsx`. Find the `INITIAL_TOAST` constant and update it:

```typescript
// Before:
const INITIAL_TOAST = {
  visible: false, showCount: 0, message: '', linkLabel: '',
  targetId: null, panelType: '', panelTab: '', backLabel: '',
}

// After:
const INITIAL_TOAST = {
  visible: false, showCount: 0, message: '', linkLabel: '',
  targetId: null, panelType: 'client' as const, initialTab: '', backLabel: '',
}
```

Also update the `toast.panelTab` assertion in the Save & Close test:

```typescript
// Before:
expect(toast.panelTab).toBe('authorizations')

// After:
expect(toast.initialTab).toBe('authorizations')
```

- [ ] **Step 12: Run all tests — verify nothing is broken**

```bash
cd frontend && npm run test
```

Expected: all tests pass.

- [ ] **Step 13: Commit**

```bash
git add frontend/src/store/toastStore.ts frontend/src/store/toastStore.test.ts \
        frontend/src/components/common/Toast.tsx frontend/src/components/common/Toast.test.tsx \
        frontend/src/components/clients/NewClientPanel.tsx frontend/src/components/clients/NewClientPanel.test.tsx
git commit -m "refactor: rename toastStore.panelTab to initialTab; type panelType as Exclude<PanelType, null>"
```

---

## Task 4: `panelStore` — add `'newCaregiver'` to `PanelType`

**Files:**
- Modify: `src/store/panelStore.ts`

- [ ] **Step 1: Update `PanelType` in `src/store/panelStore.ts`**

Find the `PanelType` union (currently `'shift' | 'newShift' | 'newClient' | 'client' | 'caregiver' | 'payer' | null`) and add `'newCaregiver'`:

```typescript
export type PanelType =
  | 'shift'
  | 'newShift'
  | 'newClient'
  | 'client'
  | 'caregiver'
  | 'newCaregiver'
  | 'payer'
  | null
```

No other changes to `panelStore.ts` — `initialTab` and `backLabel` are already top-level fields added by the Add Client spec.

- [ ] **Step 2: Verify TypeScript compiles**

```bash
cd frontend && npx tsc --noEmit
```

Expected: no errors. (`Exclude<PanelType, null>` in `toastStore.ts` now includes `'newCaregiver'` automatically.)

- [ ] **Step 3: Commit**

```bash
git add frontend/src/store/panelStore.ts
git commit -m "feat: add newCaregiver to PanelType union"
```

---

## Task 5: `CaregiverDetailPanel` — export types and accept `initialTab` prop

**Files:**
- Modify: `src/components/caregivers/CaregiverDetailPanel.tsx`
- Create: `src/components/caregivers/CaregiverDetailPanel.test.tsx`

- [ ] **Step 1: Write the failing tests**

Create `src/components/caregivers/CaregiverDetailPanel.test.tsx`:

```typescript
import { render, screen } from '@testing-library/react'
import { vi, describe, it, expect, beforeEach } from 'vitest'
import { CaregiverDetailPanel } from './CaregiverDetailPanel'
import {
  useCaregiverDetail,
  useCaregiverCredentials,
  useCaregiverBackgroundChecks,
  useCaregiverShiftHistory,
  useVerifyCredential,
} from '../../hooks/useCaregivers'
import { usePanelStore } from '../../store/panelStore'
import { useAuthStore } from '../../store/authStore'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
}))

vi.mock('../../hooks/useCaregivers')
vi.mock('../../store/panelStore')
vi.mock('../../store/authStore')

const MOCK_CAREGIVER = {
  id: 'caregiver-1',
  firstName: 'Maria',
  lastName: 'Santos',
  email: 'maria@sunrise.dev',
  phone: null,
  address: null,
  hireDate: null,
  hasPet: false,
  status: 'ACTIVE',
  createdAt: '2026-01-10T08:00:00',
}

describe('CaregiverDetailPanel — initialTab type guard', () => {
  beforeEach(() => {
    vi.mocked(usePanelStore).mockReturnValue({
      closePanel: vi.fn(),
    } as ReturnType<typeof usePanelStore>)
    // useAuthStore uses a selector: useAuthStore((s) => s.role)
    vi.mocked(useAuthStore).mockImplementation((selector?: (s: any) => any) =>
      selector ? selector({ role: 'ADMIN' }) : { role: 'ADMIN' }
    )
    vi.mocked(useCaregiverDetail).mockReturnValue({
      data: MOCK_CAREGIVER,
      isLoading: false,
      isError: false,
    } as ReturnType<typeof useCaregiverDetail>)
    vi.mocked(useCaregiverCredentials).mockReturnValue({
      data: { content: [], totalElements: 0, totalPages: 1, number: 0, size: 50, first: true, last: true },
    } as ReturnType<typeof useCaregiverCredentials>)
    vi.mocked(useCaregiverBackgroundChecks).mockReturnValue({
      data: { content: [], totalElements: 0, totalPages: 1, number: 0, size: 50, first: true, last: true },
    } as ReturnType<typeof useCaregiverBackgroundChecks>)
    vi.mocked(useCaregiverShiftHistory).mockReturnValue({
      data: { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20, first: true, last: true },
    } as ReturnType<typeof useCaregiverShiftHistory>)
    vi.mocked(useVerifyCredential).mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
      isError: false,
      variables: undefined,
    } as unknown as ReturnType<typeof useVerifyCredential>)
  })

  it('opens on the credentials tab when initialTab is "credentials"', () => {
    render(
      <CaregiverDetailPanel
        caregiverId="caregiver-1"
        backLabel="← Caregivers"
        initialTab="credentials"
      />
    )
    // credentials tab is active — empty state renders t('noCredentials')
    expect(screen.getByText('noCredentials')).toBeInTheDocument()
    // overview tab content is not rendered
    expect(screen.queryByText('fieldPhone')).not.toBeInTheDocument()
  })

  it('falls back to overview tab when initialTab is an unrecognised string', () => {
    render(
      <CaregiverDetailPanel
        caregiverId="caregiver-1"
        backLabel="← Caregivers"
        initialTab="bogus"
      />
    )
    // overview tab is active — renders t('fieldPhone') in the overview grid
    expect(screen.getByText('fieldPhone')).toBeInTheDocument()
    expect(screen.queryByText('noCredentials')).not.toBeInTheDocument()
  })

  it('falls back to overview tab when initialTab is undefined', () => {
    render(
      <CaregiverDetailPanel
        caregiverId="caregiver-1"
        backLabel="← Caregivers"
      />
    )
    expect(screen.getByText('fieldPhone')).toBeInTheDocument()
    expect(screen.queryByText('noCredentials')).not.toBeInTheDocument()
  })
})
```

- [ ] **Step 2: Run tests — verify they fail**

```bash
cd frontend && npm run test -- CaregiverDetailPanel.test
```

Expected: FAIL — `CaregiverDetailPanel` does not yet accept `initialTab` prop; always renders overview.

- [ ] **Step 3: Update `src/components/caregivers/CaregiverDetailPanel.tsx`**

Make four changes:

**3a. Change `type Tab` to exported `CaregiverTab` + add `CAREGIVER_TABS` constant**

```typescript
// Before (line 15):
type Tab = 'overview' | 'credentials' | 'backgroundChecks' | 'shiftHistory'

// After:
export type CaregiverTab = 'overview' | 'credentials' | 'backgroundChecks' | 'shiftHistory'
export const CAREGIVER_TABS: readonly CaregiverTab[] = [
  'overview', 'credentials', 'backgroundChecks', 'shiftHistory',
]
```

**3b. Update the props interface (line ~17-20)**

```typescript
// Before:
interface CaregiverDetailPanelProps {
  caregiverId: string
  backLabel: string
}

// After:
interface CaregiverDetailPanelProps {
  caregiverId: string
  backLabel: string
  initialTab?: string
}
```

**3c. Update the function signature (line ~116)**

```typescript
// Before:
export function CaregiverDetailPanel({ caregiverId, backLabel }: CaregiverDetailPanelProps) {

// After:
export function CaregiverDetailPanel({ caregiverId, backLabel, initialTab }: CaregiverDetailPanelProps) {
```

**3d. Replace the `useState` for `activeTab` (line ~120)**

```typescript
// Before:
const [activeTab, setActiveTab] = useState<Tab>('overview')

// After:
const resolvedTab: CaregiverTab = (CAREGIVER_TABS as readonly string[]).includes(initialTab ?? '')
  ? (initialTab as CaregiverTab)
  : 'overview'
// The array (not the value) is widened to string[] for the membership test.
// The cast in the truthy branch is safe because includes() confirmed membership.
const [activeTab, setActiveTab] = useState<CaregiverTab>(resolvedTab)
```

Also replace every remaining `Tab` reference inside the function body with `CaregiverTab`:
- `TABS: { id: Tab; label: string }[]` → `TABS: { id: CaregiverTab; label: string }[]`

- [ ] **Step 4: Run tests — verify they pass**

```bash
cd frontend && npm run test -- CaregiverDetailPanel.test
```

Expected: PASS (3 tests).

- [ ] **Step 5: Verify TypeScript compiles**

```bash
cd frontend && npx tsc --noEmit
```

Expected: no errors.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/components/caregivers/CaregiverDetailPanel.tsx \
        frontend/src/components/caregivers/CaregiverDetailPanel.test.tsx
git commit -m "feat: export CaregiverTab types and add initialTab prop with type guard to CaregiverDetailPanel"
```

---

## Task 6: i18n keys

**Files:**
- Modify: `public/locales/en/caregivers.json`

> **Do not add** `fieldPhone`, `fieldAddress`, `fieldHireDate`, `fieldHasPet`, or `backLabel` — they already exist in `caregivers.json`.

- [ ] **Step 1: Add new keys to `public/locales/en/caregivers.json`**

Append the following key-value pairs inside the existing JSON object (before the closing `}`):

```json
"addCaregiverPanelTitle": "Add Caregiver",
"addCaregiverRequiredNote": "* Required field",
"sectionIdentity": "Caregiver Identity",
"sectionEmployment": "Employment & Contact",
"fieldFirstName": "First Name",
"fieldLastName": "Last Name",
"fieldEmail": "Email",
"validationFirstNameRequired": "First name is required",
"validationLastNameRequired": "Last name is required",
"validationEmailRequired": "Email is required",
"validationEmailInvalid": "Enter a valid email address",
"saveAndAddCredentials": "Save & Add Credentials",
"saveAndClose": "Save & Close",
"saveCloseToast": "Caregiver saved. Add credentials to enable scheduling.",
"saveCloseToastLink": "Add Credentials"
```

- [ ] **Step 2: Validate JSON is well-formed**

```bash
node -e "JSON.parse(require('fs').readFileSync('frontend/public/locales/en/caregivers.json','utf8'))" && echo "JSON valid"
```

Expected: `JSON valid`. A `SyntaxError` here means a missing/extra comma — fix before committing.

- [ ] **Step 3: Commit**

```bash
git add frontend/public/locales/en/caregivers.json
git commit -m "feat: add i18n keys for Add Caregiver panel"
```

---

## Task 7: `NewCaregiverPanel` component

**Files:**
- Create: `src/components/caregivers/NewCaregiverPanel.tsx`
- Create: `src/components/caregivers/NewCaregiverPanel.test.tsx`

- [ ] **Step 1: Write the failing tests**

Create `src/components/caregivers/NewCaregiverPanel.test.tsx`:

```typescript
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { vi, describe, it, expect, beforeEach } from 'vitest'
import { NewCaregiverPanel } from './NewCaregiverPanel'
import { useCreateCaregiver } from '../../hooks/useCaregivers'
import { usePanelStore } from '../../store/panelStore'
import { useToastStore } from '../../store/toastStore'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}))

vi.mock('../../hooks/useCaregivers')
vi.mock('../../store/panelStore')

const mockMutateAsync = vi.fn()
const mockClosePanel = vi.fn()
const mockOpenPanel = vi.fn()

const INITIAL_TOAST = {
  visible: false, showCount: 0, message: '', linkLabel: '',
  targetId: null, panelType: 'client' as const, initialTab: '', backLabel: '',
}

describe('NewCaregiverPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(useCreateCaregiver).mockReturnValue({
      mutateAsync: mockMutateAsync,
      isPending: false,
    } as ReturnType<typeof useCreateCaregiver>)
    vi.mocked(usePanelStore).mockReturnValue({
      closePanel: mockClosePanel,
      openPanel: mockOpenPanel,
    } as ReturnType<typeof usePanelStore>)
    useToastStore.setState(INITIAL_TOAST)
  })

  // ── Rendering ──────────────────────────────────────────────────────────────

  it('renders both section headings', () => {
    render(<NewCaregiverPanel backLabel="← Caregivers" />)
    expect(screen.getByText('sectionIdentity')).toBeInTheDocument()
    expect(screen.getByText('sectionEmployment')).toBeInTheDocument()
  })

  // ── Validation ─────────────────────────────────────────────────────────────

  it('shows required-field errors and does not call the API when required fields are empty', async () => {
    const user = userEvent.setup()
    render(<NewCaregiverPanel backLabel="← Caregivers" />)
    await user.click(screen.getByRole('button', { name: 'saveAndAddCredentials' }))
    expect(await screen.findByText('validationFirstNameRequired')).toBeInTheDocument()
    expect(screen.getByText('validationLastNameRequired')).toBeInTheDocument()
    expect(screen.getByText('validationEmailRequired')).toBeInTheDocument()
    expect(mockMutateAsync).not.toHaveBeenCalled()
  })

  it('shows email format error when email is invalid', async () => {
    const user = userEvent.setup()
    render(<NewCaregiverPanel backLabel="← Caregivers" />)
    await user.type(screen.getByLabelText('fieldFirstName *'), 'Maria')
    await user.type(screen.getByLabelText('fieldLastName *'), 'Santos')
    await user.type(screen.getByLabelText('fieldEmail *'), 'not-an-email')
    await user.click(screen.getByRole('button', { name: 'saveAndAddCredentials' }))
    expect(await screen.findByText('validationEmailInvalid')).toBeInTheDocument()
    expect(mockMutateAsync).not.toHaveBeenCalled()
  })

  // ── Payload ────────────────────────────────────────────────────────────────

  it('calls createCaregiver with correct payload for required fields only', async () => {
    const user = userEvent.setup()
    mockMutateAsync.mockResolvedValue({
      id: 'caregiver-99', firstName: 'Maria', lastName: 'Santos', email: 'maria@sunrise.dev',
    })
    render(<NewCaregiverPanel backLabel="← Caregivers" />)
    await user.type(screen.getByLabelText('fieldFirstName *'), 'Maria')
    await user.type(screen.getByLabelText('fieldLastName *'), 'Santos')
    await user.type(screen.getByLabelText('fieldEmail *'), 'maria@sunrise.dev')
    await user.click(screen.getByRole('button', { name: 'saveAndAddCredentials' }))
    await waitFor(() => expect(mockMutateAsync).toHaveBeenCalledOnce())
    const payload = mockMutateAsync.mock.calls[0][0]
    expect(payload.firstName).toBe('Maria')
    expect(payload.lastName).toBe('Santos')
    expect(payload.email).toBe('maria@sunrise.dev')
    expect(payload.phone).toBeUndefined()
    expect(payload.address).toBeUndefined()
    expect(payload.hireDate).toBeUndefined()
  })

  // ── API error ──────────────────────────────────────────────────────────────

  it('shows inline error banner and keeps panel open on API failure', async () => {
    const user = userEvent.setup()
    mockMutateAsync.mockRejectedValue(new Error('Network error'))
    render(<NewCaregiverPanel backLabel="← Caregivers" />)
    await user.type(screen.getByLabelText('fieldFirstName *'), 'Maria')
    await user.type(screen.getByLabelText('fieldLastName *'), 'Santos')
    await user.type(screen.getByLabelText('fieldEmail *'), 'maria@sunrise.dev')
    await user.click(screen.getByRole('button', { name: 'saveAndAddCredentials' }))
    expect(await screen.findByRole('alert')).toBeInTheDocument()
    expect(mockClosePanel).not.toHaveBeenCalled()
  })

  // ── Save & Add Credentials ─────────────────────────────────────────────────

  it('Save & Add Credentials: closes panel and opens caregiver detail on Credentials tab', async () => {
    const user = userEvent.setup()
    mockMutateAsync.mockResolvedValue({ id: 'caregiver-99' })
    render(<NewCaregiverPanel backLabel="← Caregivers" />)
    await user.type(screen.getByLabelText('fieldFirstName *'), 'Maria')
    await user.type(screen.getByLabelText('fieldLastName *'), 'Santos')
    await user.type(screen.getByLabelText('fieldEmail *'), 'maria@sunrise.dev')
    await user.click(screen.getByRole('button', { name: 'saveAndAddCredentials' }))
    await waitFor(() => expect(mockClosePanel).toHaveBeenCalledOnce())
    expect(mockOpenPanel).toHaveBeenCalledWith('caregiver', 'caregiver-99', {
      backLabel: 'backLabel',
      initialTab: 'credentials',
    })
  })

  // ── Save & Close ───────────────────────────────────────────────────────────

  it('Save & Close: shows toast with correct fields and closes panel', async () => {
    const user = userEvent.setup()
    mockMutateAsync.mockResolvedValue({ id: 'caregiver-99' })
    render(<NewCaregiverPanel backLabel="← Caregivers" />)
    await user.type(screen.getByLabelText('fieldFirstName *'), 'Maria')
    await user.type(screen.getByLabelText('fieldLastName *'), 'Santos')
    await user.type(screen.getByLabelText('fieldEmail *'), 'maria@sunrise.dev')
    await user.click(screen.getByRole('button', { name: 'saveAndClose' }))
    await waitFor(() => expect(mockClosePanel).toHaveBeenCalledOnce())
    const toast = useToastStore.getState()
    expect(toast.visible).toBe(true)
    expect(toast.targetId).toBe('caregiver-99')
    expect(toast.panelType).toBe('caregiver')
    expect(toast.initialTab).toBe('credentials')
    expect(toast.message).toBe('saveCloseToast')
    expect(toast.linkLabel).toBe('saveCloseToastLink')
    expect(toast.backLabel).toBe('backLabel')
  })
})
```

- [ ] **Step 2: Run tests — verify they fail**

```bash
cd frontend && npm run test -- NewCaregiverPanel.test
```

Expected: FAIL — `NewCaregiverPanel` does not exist.

- [ ] **Step 3: Create `src/components/caregivers/NewCaregiverPanel.tsx`**

```typescript
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { useCreateCaregiver } from '../../hooks/useCaregivers'
import { usePanelStore } from '../../store/panelStore'
import { useToastStore } from '../../store/toastStore'

interface FormValues {
  firstName: string
  lastName: string
  email: string
  phone: string
  address: string
  hireDate: string
  hasPet: boolean
}

interface Props {
  backLabel: string
}

export function NewCaregiverPanel({ backLabel }: Props) {
  const { t } = useTranslation('caregivers')
  const { t: tCommon } = useTranslation('common')
  const { closePanel, openPanel } = usePanelStore()
  const createMutation = useCreateCaregiver()
  const [apiError, setApiError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({
    defaultValues: {
      firstName: '',
      lastName: '',
      email: '',
      phone: '',
      address: '',
      hireDate: '',
      hasPet: false,
    },
  })

  function buildPayload(values: FormValues) {
    return {
      firstName: values.firstName.trim(),
      lastName: values.lastName.trim(),
      email: values.email.trim(),
      phone: values.phone || undefined,
      address: values.address || undefined,
      hireDate: values.hireDate || undefined,
      hasPet: values.hasPet,
    }
  }

  async function onSaveAndAddCredentials(values: FormValues) {
    setApiError(null)
    try {
      const caregiver = await createMutation.mutateAsync(buildPayload(values))
      closePanel()
      openPanel('caregiver', caregiver.id, {
        backLabel: t('backLabel'),
        initialTab: 'credentials',
      })
    } catch {
      setApiError(tCommon('errorTryAgain'))
    }
  }

  async function onSaveAndClose(values: FormValues) {
    setApiError(null)
    try {
      const caregiver = await createMutation.mutateAsync(buildPayload(values))
      useToastStore.getState().show({
        message: t('saveCloseToast'),
        linkLabel: t('saveCloseToastLink'),
        targetId: caregiver.id,
        panelType: 'caregiver',
        initialTab: 'credentials',
        backLabel: t('backLabel'),
      })
      closePanel()
    } catch {
      setApiError(tCommon('errorTryAgain'))
    }
  }

  const isPending = createMutation.isPending

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
        <h2 className="text-[16px] font-bold text-dark">{t('addCaregiverPanelTitle')}</h2>
        <p className="text-[12px] text-text-secondary mt-0.5">{t('addCaregiverRequiredNote')}</p>
      </div>

      {/* Scrollable form body */}
      <div className="flex-1 overflow-auto px-6 py-4 space-y-6">

        {/* Caregiver Identity */}
        <section>
          <h3 className="text-[10px] font-bold uppercase tracking-[0.1em] text-text-secondary mb-3">
            {t('sectionIdentity')}
          </h3>
          <div className="grid grid-cols-2 gap-3 mb-3">
            <div>
              <label htmlFor="firstName" className="block text-[12px] text-text-secondary mb-1">
                {t('fieldFirstName')} *
              </label>
              <input
                id="firstName"
                type="text"
                {...register('firstName', {
                  required: t('validationFirstNameRequired'),
                  validate: (v) => v.trim() !== '' || t('validationFirstNameRequired'),
                })}
                className="w-full border border-border px-3 py-1.5 text-[13px] focus:outline-none focus:border-dark"
              />
              {errors.firstName && (
                <p className="text-[11px] text-red-500 mt-0.5">{errors.firstName.message}</p>
              )}
            </div>
            <div>
              <label htmlFor="lastName" className="block text-[12px] text-text-secondary mb-1">
                {t('fieldLastName')} *
              </label>
              <input
                id="lastName"
                type="text"
                {...register('lastName', {
                  required: t('validationLastNameRequired'),
                  validate: (v) => v.trim() !== '' || t('validationLastNameRequired'),
                })}
                className="w-full border border-border px-3 py-1.5 text-[13px] focus:outline-none focus:border-dark"
              />
              {errors.lastName && (
                <p className="text-[11px] text-red-500 mt-0.5">{errors.lastName.message}</p>
              )}
            </div>
          </div>
          <div>
            <label htmlFor="email" className="block text-[12px] text-text-secondary mb-1">
              {t('fieldEmail')} *
            </label>
            <input
              id="email"
              type="email"
              {...register('email', {
                required: t('validationEmailRequired'),
                pattern: {
                  value: /^[^\s@]+@[^\s@]+\.[^\s@]+$/,
                  message: t('validationEmailInvalid'),
                },
              })}
              className="w-full border border-border px-3 py-1.5 text-[13px] focus:outline-none focus:border-dark"
            />
            {errors.email && (
              <p className="text-[11px] text-red-500 mt-0.5">{errors.email.message}</p>
            )}
          </div>
        </section>

        {/* Employment & Contact */}
        <section>
          <h3 className="text-[10px] font-bold uppercase tracking-[0.1em] text-text-secondary mb-3">
            {t('sectionEmployment')}
          </h3>
          <div className="space-y-3">
            <div>
              <label htmlFor="phone" className="block text-[12px] text-text-secondary mb-1">
                {t('fieldPhone')}
              </label>
              <input
                id="phone"
                type="tel"
                {...register('phone')}
                className="w-full border border-border px-3 py-1.5 text-[13px] focus:outline-none focus:border-dark"
              />
            </div>
            <div>
              <label htmlFor="address" className="block text-[12px] text-text-secondary mb-1">
                {t('fieldAddress')}
              </label>
              <input
                id="address"
                type="text"
                {...register('address')}
                className="w-full border border-border px-3 py-1.5 text-[13px] focus:outline-none focus:border-dark"
              />
            </div>
            <div>
              <label htmlFor="hireDate" className="block text-[12px] text-text-secondary mb-1">
                {t('fieldHireDate')}
              </label>
              <input
                id="hireDate"
                type="date"
                {...register('hireDate')}
                className="border border-border px-3 py-1.5 text-[13px] focus:outline-none focus:border-dark"
              />
            </div>
            <label className="flex items-center gap-2 text-[13px] cursor-pointer">
              <input type="checkbox" {...register('hasPet')} className="w-4 h-4" />
              {t('fieldHasPet')}
            </label>
          </div>
        </section>
      </div>

      {/* API error banner */}
      {apiError && (
        <div
          role="alert"
          className="mx-6 mb-2 px-4 py-2 bg-red-50 border border-red-200 text-[12px] text-red-700"
        >
          {apiError}
        </div>
      )}

      {/* Footer */}
      <div className="px-6 py-4 border-t border-border flex gap-3">
        <button
          type="button"
          disabled={isPending}
          onClick={handleSubmit(onSaveAndAddCredentials)}
          className="flex-1 py-2 text-[13px] font-bold bg-dark text-white disabled:opacity-50 hover:brightness-110"
        >
          {t('saveAndAddCredentials')}
        </button>
        <button
          type="button"
          disabled={isPending}
          onClick={handleSubmit(onSaveAndClose)}
          className="px-4 py-2 text-[13px] font-bold border border-border text-text-primary disabled:opacity-50 hover:bg-surface"
        >
          {t('saveAndClose')}
        </button>
      </div>
    </div>
  )
}
```

- [ ] **Step 4: Run tests — verify they pass**

```bash
cd frontend && npm run test -- NewCaregiverPanel.test
```

Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/caregivers/NewCaregiverPanel.tsx \
        frontend/src/components/caregivers/NewCaregiverPanel.test.tsx
git commit -m "feat: add NewCaregiverPanel form component with Save & Add Credentials and Save & Close flows"
```

---

## Task 8: Wire up `Shell.tsx` and `CaregiversPage.tsx`

**Files:**
- Modify: `src/components/layout/Shell.tsx`
- Modify: `src/components/caregivers/CaregiversPage.tsx`

- [ ] **Step 1: Update `src/components/layout/Shell.tsx`**

Add the `NewCaregiverPanel` import alongside the other caregiver import:

```typescript
// Add after the existing CaregiverDetailPanel import:
import { NewCaregiverPanel } from '../caregivers/NewCaregiverPanel'
```

Inside `PanelContent`, add the `newCaregiver` case and pass `initialTab` to `CaregiverDetailPanel`. Find the existing caregiver block:

```typescript
// Before:
if (type === 'caregiver' && selectedId) {
  return <CaregiverDetailPanel caregiverId={selectedId} backLabel={backLabel} />
}

// After:
if (type === 'newCaregiver') {
  return <NewCaregiverPanel backLabel={backLabel} />
}
if (type === 'caregiver' && selectedId) {
  return <CaregiverDetailPanel caregiverId={selectedId} backLabel={backLabel} initialTab={initialTab} />
}
```

No other changes to `Shell.tsx` — `initialTab` is already destructured from `usePanelStore()` at line 14.

- [ ] **Step 2: Update `src/components/caregivers/CaregiversPage.tsx`**

Add the `usePanelStore` import (it does not currently import it):

```typescript
import { usePanelStore } from '../../store/panelStore'
```

Add `openPanel` to the destructure inside the component (after the existing hooks):

```typescript
const { openPanel } = usePanelStore()
```

Replace the `alert()` call on the `+ Add Caregiver` button (currently line ~49):

```typescript
// Before:
onClick={() => alert(t('addCaregiverAlert'))}

// After:
onClick={() => openPanel('newCaregiver', undefined, { backLabel: t('backLabel') })}
```

- [ ] **Step 3: Verify TypeScript compiles**

```bash
cd frontend && npx tsc --noEmit
```

Expected: no errors.

- [ ] **Step 4: Run the full test suite**

```bash
cd frontend && npm run test
```

Expected: all tests pass, no regressions.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/layout/Shell.tsx \
        frontend/src/components/caregivers/CaregiversPage.tsx
git commit -m "feat: wire NewCaregiverPanel into Shell and CaregiversPage — Add Caregiver panel complete"
```

---

## Self-Review Checklist

### 1. Spec coverage

| Spec requirement | Implemented in |
|---|---|
| `CreateCaregiverRequest` (7 fields) | Task 1 |
| `createCaregiver()` API function | Task 1 |
| `useCreateCaregiver()` with query invalidation | Task 2 |
| `panelTab` → `initialTab` rename in toastStore/Toast | Task 3 |
| `panelType: Exclude<PanelType, null>` safety | Task 3 |
| `NewClientPanel` `show()` call updated | Task 3 Step 10 |
| `'newCaregiver'` in PanelType union | Task 4 |
| `CaregiverTab` + `CAREGIVER_TABS` exported | Task 5 |
| `initialTab` prop with type guard on `CaregiverDetailPanel` | Task 5 |
| `initialTab` type guard tests (valid, invalid, undefined) | Task 5 |
| All 15 i18n keys added | Task 6 |
| Form renders Identity + Employment sections | Task 7 |
| Required field validation (firstName, lastName, email) | Task 7 |
| Email format validation | Task 7 |
| `buildPayload` omits optional fields when blank | Task 7 |
| Save & Add Credentials flow | Task 7 |
| Save & Close flow with toast | Task 7 |
| API error banner, panel stays open | Task 7 |
| `newCaregiver` registered in Shell `PanelContent` | Task 8 |
| `initialTab` forwarded to `CaregiverDetailPanel` in Shell | Task 8 |
| `alert()` replaced with `openPanel` in CaregiversPage | Task 8 |
| Email uniqueness gap documented (spec Known Gaps) | Addressed in spec; no code change needed |
| Credentials tab empty state verified at runtime | Verify manually during smoke test |

### 2. Placeholder scan

No placeholders found — all steps contain complete code blocks.

### 3. Type consistency

- `panelType: Exclude<PanelType, null>` — consistent in `toastStore.ts` interface, `show()` opts, `NewCaregiverPanel.tsx` call site, and `Toast.tsx` destructure (no cast).
- `initialTab` — consistent field name in `toastStore.ts`, `Toast.tsx`, `NewCaregiverPanel.tsx` `show()` call, and `openPanel` options arg.
- `CaregiverTab` — exported from `CaregiverDetailPanel.tsx`; `CAREGIVER_TABS` is `readonly CaregiverTab[]`; type guard returns `CaregiverTab`; `useState<CaregiverTab>` matches.
- `TOAST_ZERO_PANEL_TYPE = 'client'` — used as zero value in `panelType` initial state and `dismiss()` reset in `toastStore.ts`. Also used in `INITIAL_TOAST` / `INITIAL_STORE` test constants.
