# Add Client Panel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the `alert()` placeholder on the Clients list with a real slide-panel form that creates a client via `POST /api/v1/clients` and guides the user to add an authorization.

**Architecture:** A new `NewClientPanel` component wires into the existing `panelStore`/`SlidePanel` infrastructure. A generic `toastStore` + `Toast` component handles the "Save & Close" confirmation path. The only structural changes to existing components are: adding `initialTab` to `panelStore` and `ClientDetailPanel`, registering the new panel in `Shell`, and adding a `useCreateClient` mutation to `useClients`.

**Tech Stack:** React 18, TypeScript, react-hook-form, @tanstack/react-query v5, Zustand, axios (axios-mock-adapter for tests), react-i18next, Vitest + Testing Library

---

## File Map

| Action | Path | Purpose |
|---|---|---|
| Modify | `src/types/api.ts` | Add `CreateClientRequest` interface |
| Modify | `src/api/clients.ts` | Add `createClient()` function |
| Modify | `src/hooks/useClients.ts` | Add `useCreateClient()` mutation |
| Create | `src/hooks/useClients.test.ts` | Test `useCreateClient` mutation |
| Create | `src/store/toastStore.ts` | Zustand toast slice |
| Create | `src/store/toastStore.test.ts` | Toast store unit tests |
| Create | `src/components/common/Toast.tsx` | Generic dismissible toast banner |
| Create | `src/components/common/Toast.test.tsx` | Toast component tests |
| Modify | `src/store/panelStore.ts` | Add `newClient` PanelType + `initialTab` field |
| Modify | `src/components/clients/ClientDetailPanel.tsx` | Accept `initialTab?` prop |
| Modify | `public/locales/en/clients.json` | Add all new i18n keys |
| Create | `src/components/clients/NewClientPanel.tsx` | Add client form |
| Create | `src/components/clients/NewClientPanel.test.tsx` | Form tests |
| Modify | `src/components/layout/Shell.tsx` | Register `newClient`, render `<Toast />`, pass `initialTab` |
| Modify | `src/components/clients/ClientsPage.tsx` | Replace `alert()` with `openPanel('newClient', ...)` |

---

## Task 1: `CreateClientRequest` type + `createClient()` API function

**Files:**
- Modify: `src/types/api.ts`
- Modify: `src/api/clients.ts`

- [ ] **Step 1: Add `CreateClientRequest` to `src/types/api.ts`**

Find the `// ── Clients ──` section (or add one after Shifts). Add after the existing client-related types:

```typescript
export interface CreateClientRequest {
  firstName: string
  lastName: string
  dateOfBirth: string               // YYYY-MM-DD — Jackson reads directly as LocalDate
  phone?: string
  address?: string
  serviceState?: string             // 2-letter abbreviation; omit (not "") when blank
  medicaidId?: string
  preferredCaregiverGender?: string // "FEMALE" | "MALE"; omit (not "") for no preference
  preferredLanguages?: string       // JSON array string e.g. '["English","Spanish"]'
  noPetCaregiver?: boolean
}
```

- [ ] **Step 2: Add `createClient()` to `src/api/clients.ts`**

Append after `listAuthorizations`:

```typescript
export async function createClient(req: CreateClientRequest): Promise<ClientResponse> {
  const response = await apiClient.post<ClientResponse>('/clients', req)
  return response.data
}
```

Also add `CreateClientRequest` to the import line at the top of the file:

```typescript
import type { ClientResponse, AuthorizationResponse, PageResponse, CreateClientRequest } from '../types/api'
```

- [ ] **Step 3: Verify TypeScript compiles**

```bash
cd frontend && npx tsc --noEmit
```

Expected: no errors.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/types/api.ts frontend/src/api/clients.ts
git commit -m "feat: add CreateClientRequest type and createClient API function"
```

---

## Task 2: `useCreateClient` mutation hook

**Files:**
- Modify: `src/hooks/useClients.ts`
- Create: `src/hooks/useClients.test.ts`

- [ ] **Step 1: Write the failing test**

Create `src/hooks/useClients.test.ts`:

```typescript
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import MockAdapter from 'axios-mock-adapter'
import { vi, describe, it, expect, afterEach } from 'vitest'
import React from 'react'
import { apiClient } from '../api/client'
import { useCreateClient } from './useClients'

const mockAxios = new MockAdapter(apiClient)

function makeWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  const wrapper = ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: queryClient }, children)
  return { queryClient, wrapper }
}

describe('useCreateClient', () => {
  afterEach(() => mockAxios.reset())

  it('calls POST /clients and invalidates the clients query key prefix on success', async () => {
    const { queryClient, wrapper } = makeWrapper()
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')

    mockAxios.onPost('/clients').reply(201, {
      id: '00000000-0000-0000-0000-000000000099',
      firstName: 'Jane',
      lastName: 'Doe',
      dateOfBirth: '1980-01-15',
      status: 'ACTIVE',
    })

    const { result } = renderHook(() => useCreateClient(), { wrapper })

    result.current.mutate({ firstName: 'Jane', lastName: 'Doe', dateOfBirth: '1980-01-15' })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['clients'] })
  })
})
```

- [ ] **Step 2: Run test — verify it fails**

```bash
cd frontend && npm run test -- useClients.test
```

Expected: FAIL — `useCreateClient is not a function` (or not exported).

- [ ] **Step 3: Add `useCreateClient` to `src/hooks/useClients.ts`**

Update **two existing import lines** — do not add new ones (duplicates cause TS errors):

```typescript
// Update the existing react-query import from:
import { useQuery } from '@tanstack/react-query'
// To:
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'

// Update the existing clients API import from:
import { listClients, getClient, listAuthorizations } from '../api/clients'
// To:
import { listClients, getClient, listAuthorizations, createClient } from '../api/clients'

// Leave unchanged (already correct):
// import { useMemo } from 'react'
// import type { ClientResponse } from '../types/api'
```

Then append the new hook after `useClientAuthorizations`:

```typescript
export function useCreateClient() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: createClient,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['clients'] })
    },
  })
}
```

> **Note:** `{ queryKey: ['clients'] }` uses prefix matching — this invalidates both `['clients', page, size]` (paginated list) and `['clients', 'all']` (used by schedule screen).

- [ ] **Step 4: Run test — verify it passes**

```bash
cd frontend && npm run test -- useClients.test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/hooks/useClients.ts frontend/src/hooks/useClients.test.ts
git commit -m "feat: add useCreateClient mutation hook with query invalidation"
```

---

## Task 3: `toastStore`

**Files:**
- Create: `src/store/toastStore.ts`
- Create: `src/store/toastStore.test.ts`

- [ ] **Step 1: Write the failing tests**

Create `src/store/toastStore.test.ts`:

```typescript
import { describe, it, expect, beforeEach } from 'vitest'
import { useToastStore } from './toastStore'

const INITIAL_STATE = {
  visible: false,
  showCount: 0,
  message: '',
  linkLabel: '',
  targetId: null,
  panelType: '',
  panelTab: '',
  backLabel: '',
}

const SHOW_OPTS = {
  message: 'Client saved.',
  linkLabel: 'Add Authorization',
  targetId: 'client-123',
  panelType: 'client',
  panelTab: 'authorizations',
  backLabel: '← Clients',
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
    expect(s.message).toBe('Client saved.')
    expect(s.linkLabel).toBe('Add Authorization')
    expect(s.targetId).toBe('client-123')
    expect(s.panelType).toBe('client')
    expect(s.panelTab).toBe('authorizations')
    expect(s.backLabel).toBe('← Clients')
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
    expect(s.panelType).toBe('')
    expect(s.panelTab).toBe('')
    expect(s.backLabel).toBe('')
  })
})
```

- [ ] **Step 2: Run tests — verify they fail**

```bash
cd frontend && npm run test -- toastStore.test
```

Expected: FAIL — `useToastStore` not found.

- [ ] **Step 3: Implement `src/store/toastStore.ts`**

```typescript
import { create } from 'zustand'

interface ToastState {
  visible: boolean
  showCount: number        // increments on every show(); useEffect dep for timer re-arm
  message: string
  linkLabel: string
  targetId: string | null  // passed to openPanel as selectedId
  panelType: string        // e.g. 'client'
  panelTab: string         // passed as initialTab, e.g. 'authorizations'
  backLabel: string        // e.g. '← Clients'
  show: (opts: {
    message: string
    linkLabel: string
    targetId: string
    panelType: string
    panelTab: string
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
  panelType: '',
  panelTab: '',
  backLabel: '',

  show: (opts) =>
    set((prev) => ({
      visible: true,
      showCount: prev.showCount + 1,
      message: opts.message,
      linkLabel: opts.linkLabel,
      targetId: opts.targetId,
      panelType: opts.panelType,
      panelTab: opts.panelTab,
      backLabel: opts.backLabel,
    })),

  dismiss: () =>
    set({
      visible: false,
      showCount: 0,
      message: '',
      linkLabel: '',
      targetId: null,
      panelType: '',
      panelTab: '',
      backLabel: '',
    }),
}))
```

- [ ] **Step 4: Run tests — verify they pass**

```bash
cd frontend && npm run test -- toastStore.test
```

Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/store/toastStore.ts frontend/src/store/toastStore.test.ts
git commit -m "feat: add toastStore Zustand slice with showCount timer re-arm support"
```

---

## Task 4: `Toast.tsx` component

**Files:**
- Create: `src/components/common/Toast.tsx`
- Create: `src/components/common/Toast.test.tsx`

- [ ] **Step 1: Write the failing tests**

Create `src/components/common/Toast.test.tsx`:

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
  message: 'Client saved.',
  linkLabel: 'Add Authorization',
  targetId: 'client-123',
  panelType: 'client',
  panelTab: 'authorizations',
  backLabel: '← Clients',
}

const INITIAL_STORE = {
  visible: false,
  showCount: 0,
  message: '',
  linkLabel: '',
  targetId: null,
  panelType: '',
  panelTab: '',
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
    expect(screen.getByText('Client saved.')).toBeInTheDocument()
    expect(screen.getByText('Add Authorization')).toBeInTheDocument()
  })

  it('renders nothing when visible is false', () => {
    render(<Toast />)
    expect(screen.queryByText('Client saved.')).not.toBeInTheDocument()
  })

  it('link click calls openPanel with panelType, targetId, initialTab, and backLabel', () => {
    useToastStore.setState(TOAST_STATE)
    render(<Toast />)
    fireEvent.click(screen.getByText('Add Authorization'))
    expect(mockOpenPanel).toHaveBeenCalledWith('client', 'client-123', {
      initialTab: 'authorizations',
      backLabel: '← Clients',
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
    expect(useToastStore.getState().visible).toBe(false) // still false, no double-dismiss
    vi.useRealTimers()
  })

  it('show() called while already visible resets the 6-second timer', () => {
    vi.useFakeTimers()
    useToastStore.setState(TOAST_STATE)
    const { rerender } = render(<Toast />)

    // 4 s pass — not yet dismissed
    act(() => vi.advanceTimersByTime(4000))
    expect(useToastStore.getState().visible).toBe(true)

    // show() called again: showCount increments, visible stays true
    act(() => useToastStore.setState({ ...TOAST_STATE, showCount: 2 }))
    rerender(<Toast />)

    // 4 more seconds (8 s total, 4 s from second show) — timer reset, still not dismissed
    act(() => vi.advanceTimersByTime(4000))
    expect(useToastStore.getState().visible).toBe(true)

    // 2 more seconds (6 s from second show) — now dismisses
    act(() => vi.advanceTimersByTime(2000))
    expect(useToastStore.getState().visible).toBe(false)
    vi.useRealTimers()
  })
})
```

- [ ] **Step 2: Run tests — verify they fail**

```bash
cd frontend && npm run test -- Toast.test
```

Expected: FAIL — `Toast` not found.

- [ ] **Step 3: Implement `src/components/common/Toast.tsx`**

```typescript
import { useEffect } from 'react'
import { useToastStore } from '../../store/toastStore'
import { usePanelStore } from '../../store/panelStore'

export function Toast() {
  const { visible, showCount, message, linkLabel, targetId, panelType, panelTab, backLabel, dismiss } =
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
    openPanel(panelType as Parameters<typeof openPanel>[0], targetId ?? undefined, {
      initialTab: panelTab,
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

- [ ] **Step 4: Run tests — verify they pass**

```bash
cd frontend && npm run test -- Toast.test
```

Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/common/Toast.tsx frontend/src/components/common/Toast.test.tsx
git commit -m "feat: add generic Toast component with auto-dismiss and link navigation"
```

---

## Task 5: `panelStore` + `ClientDetailPanel` `initialTab` prop

**Files:**
- Modify: `src/store/panelStore.ts`
- Modify: `src/components/clients/ClientDetailPanel.tsx`

- [ ] **Step 1: Update `src/store/panelStore.ts`**

Replace the entire file with the updated version (changes: add `'newClient'` to `PanelType`, add `initialTab` to `PanelState`, update `openPanel` options type, reset `initialTab` in `closePanel`):

```typescript
import { create } from 'zustand'

export type PanelType =
  | 'shift'
  | 'newShift'
  | 'newClient'
  | 'client'
  | 'caregiver'
  | 'payer'
  | null

interface PanelPrefill {
  date?: string
  time?: string
  editShiftId?: string
  clientId?: string
  caregiverId?: string
  serviceTypeId?: string
  endTime?: string
}

interface PanelState {
  open: boolean
  type: PanelType
  selectedId: string | null
  prefill: PanelPrefill | null
  backLabel: string
  initialTab: string | undefined   // top-level nav state — NOT part of PanelPrefill
  openPanel: (
    type: Exclude<PanelType, null>,
    id?: string,
    options?: { prefill?: PanelPrefill; backLabel?: string; initialTab?: string }
  ) => void
  closePanel: () => void
}

export const usePanelStore = create<PanelState>((set) => ({
  open: false,
  type: null,
  selectedId: null,
  prefill: null,
  backLabel: '← Back',
  initialTab: undefined,

  openPanel: (type, id, options) =>
    set({
      open: true,
      type,
      selectedId: id ?? null,
      prefill: options?.prefill ?? null,
      backLabel: options?.backLabel ?? '← Back',
      initialTab: options?.initialTab,
    }),

  closePanel: () =>
    set({
      open: false,
      type: null,
      selectedId: null,
      prefill: null,
      initialTab: undefined,
    }),
}))
```

- [ ] **Step 2: Update `ClientDetailPanel` to accept `initialTab` prop**

In `src/components/clients/ClientDetailPanel.tsx`, update the props interface and `useState` initial value:

```typescript
// Change the props interface from:
interface ClientDetailPanelProps {
  clientId: string
  backLabel: string
}

// To:
interface ClientDetailPanelProps {
  clientId: string
  backLabel: string
  initialTab?: string
}
```

Change the function signature:

```typescript
// From:
export function ClientDetailPanel({ clientId, backLabel }: ClientDetailPanelProps) {

// To:
export function ClientDetailPanel({ clientId, backLabel, initialTab }: ClientDetailPanelProps) {
```

Change the `useState` call for `activeTab`:

```typescript
// From:
const [activeTab, setActiveTab] = useState<Tab>('overview')

// To:
const [activeTab, setActiveTab] = useState<Tab>((initialTab as Tab | undefined) ?? 'overview')
// useState reads initialTab only once on mount — an invalid value silently falls back to 'overview'
```

- [ ] **Step 3: Verify TypeScript compiles**

```bash
cd frontend && npx tsc --noEmit
```

Expected: no errors.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/store/panelStore.ts frontend/src/components/clients/ClientDetailPanel.tsx
git commit -m "feat: add initialTab to panelStore and ClientDetailPanel for deep-link tab navigation"
```

---

## Task 6: i18n keys

**Files:**
- Modify: `public/locales/en/clients.json`

- [ ] **Step 1: Add new keys to `public/locales/en/clients.json`**

Add the following keys to the existing JSON object (do not duplicate `"fieldPhone"`, `"fieldAddress"`, `"fieldServiceState"`, or `"backLabel"` — they already exist):

```json
"addClientPanelTitle": "Add Client",
"addClientRequiredNote": "* Required field",
"sectionIdentity": "Client Identity",
"sectionContact": "Contact & Location",
"sectionBilling": "Billing",
"sectionPreferences": "Care Preferences",
"fieldFirstName": "First Name",
"fieldLastName": "Last Name",
"fieldDateOfBirth": "Date of Birth",
"fieldSelectState": "Select state…",
"fieldMedicaidId": "Medicaid ID",
"fieldPreferredGender": "Preferred Caregiver Gender",
"fieldGenderFemale": "Female",
"fieldGenderMale": "Male",
"fieldGenderNoPreference": "No preference",
"fieldPreferredLanguages": "Preferred Languages",
"fieldPreferredLanguagesHint": "Comma-separated, e.g. English, Spanish",
"fieldNoPetCaregiver": "No pet caregiver",
"validationFirstNameRequired": "First name is required",
"validationLastNameRequired": "Last name is required",
"validationDobRequired": "Date of birth is required",
"saveAndAddAuth": "Save & Add Authorization",
"saveAndClose": "Save & Close",
"saveCloseToast": "Client saved. Add an authorization to enable scheduling.",
"saveCloseToastLink": "Add Authorization"
```

- [ ] **Step 2: Validate JSON is well-formed**

```bash
node -e "JSON.parse(require('fs').readFileSync('frontend/public/locales/en/clients.json','utf8'))" && echo "JSON valid"
```

Expected: `JSON valid`. A SyntaxError here means a missing/trailing comma — fix before committing.

- [ ] **Step 3: Commit**

```bash
git add frontend/public/locales/en/clients.json
git commit -m "feat: add i18n keys for Add Client panel"
```

---

## Task 7: `NewClientPanel` component

**Files:**
- Create: `src/components/clients/NewClientPanel.tsx`
- Create: `src/components/clients/NewClientPanel.test.tsx`

- [ ] **Step 1: Write the failing tests**

Create `src/components/clients/NewClientPanel.test.tsx`:

```typescript
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { vi, describe, it, expect, beforeEach } from 'vitest'
import { NewClientPanel } from './NewClientPanel'
import { useCreateClient } from '../../hooks/useClients'
import { usePanelStore } from '../../store/panelStore'
import { useToastStore } from '../../store/toastStore'

// Mock i18n — return the key as the translation value so assertions are predictable
vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}))

vi.mock('../../hooks/useClients')
vi.mock('../../store/panelStore')

const mockMutateAsync = vi.fn()
const mockClosePanel = vi.fn()
const mockOpenPanel = vi.fn()

const INITIAL_TOAST = {
  visible: false, showCount: 0, message: '', linkLabel: '',
  targetId: null, panelType: '', panelTab: '', backLabel: '',
}

describe('NewClientPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(useCreateClient).mockReturnValue({
      mutateAsync: mockMutateAsync,
      isPending: false,
    } as ReturnType<typeof useCreateClient>)
    vi.mocked(usePanelStore).mockReturnValue({
      closePanel: mockClosePanel,
      openPanel: mockOpenPanel,
    } as ReturnType<typeof usePanelStore>)
    useToastStore.setState(INITIAL_TOAST)
  })

  // ── Rendering ──────────────────────────────────────────────────────────────

  it('renders all four section headings', () => {
    render(<NewClientPanel backLabel="← Clients" />)
    expect(screen.getByText('sectionIdentity')).toBeInTheDocument()
    expect(screen.getByText('sectionContact')).toBeInTheDocument()
    expect(screen.getByText('sectionBilling')).toBeInTheDocument()
    expect(screen.getByText('sectionPreferences')).toBeInTheDocument()
  })

  // ── Validation ─────────────────────────────────────────────────────────────

  it('shows all three required-field errors and does not call the API when required fields are empty', async () => {
    const user = userEvent.setup()
    render(<NewClientPanel backLabel="← Clients" />)
    await user.click(screen.getByRole('button', { name: 'saveAndAddAuth' }))
    expect(await screen.findByText('validationFirstNameRequired')).toBeInTheDocument()
    expect(screen.getByText('validationLastNameRequired')).toBeInTheDocument()
    expect(screen.getByText('validationDobRequired')).toBeInTheDocument()
    expect(mockMutateAsync).not.toHaveBeenCalled()
  })

  // ── Payload ────────────────────────────────────────────────────────────────

  it('calls createClient with correct payload for required fields only', async () => {
    const user = userEvent.setup()
    mockMutateAsync.mockResolvedValue({ id: 'client-99', firstName: 'Jane', lastName: 'Doe' })
    render(<NewClientPanel backLabel="← Clients" />)
    await user.type(screen.getByLabelText('fieldFirstName *'), 'Jane')
    await user.type(screen.getByLabelText('fieldLastName *'), 'Doe')
    await user.type(screen.getByLabelText('fieldDateOfBirth *'), '1980-01-15')
    await user.click(screen.getByRole('button', { name: 'saveAndAddAuth' }))
    await waitFor(() => expect(mockMutateAsync).toHaveBeenCalledOnce())
    const payload = mockMutateAsync.mock.calls[0][0]
    expect(payload.firstName).toBe('Jane')
    expect(payload.lastName).toBe('Doe')
    expect(payload.dateOfBirth).toBe('1980-01-15')
    expect(payload.serviceState).toBeUndefined()    // empty select → omitted
    expect(payload.preferredCaregiverGender).toBeUndefined()  // "no preference" → omitted
  })

  it('serializes preferredLanguages as a JSON array string', async () => {
    const user = userEvent.setup()
    mockMutateAsync.mockResolvedValue({ id: 'client-99' })
    render(<NewClientPanel backLabel="← Clients" />)
    await user.type(screen.getByLabelText('fieldFirstName *'), 'Jane')
    await user.type(screen.getByLabelText('fieldLastName *'), 'Doe')
    await user.type(screen.getByLabelText('fieldDateOfBirth *'), '1980-01-15')
    await user.type(screen.getByLabelText('fieldPreferredLanguages'), 'English, Spanish')
    await user.click(screen.getByRole('button', { name: 'saveAndAddAuth' }))
    await waitFor(() => expect(mockMutateAsync).toHaveBeenCalledOnce())
    expect(mockMutateAsync.mock.calls[0][0].preferredLanguages).toBe('["English","Spanish"]')
  })

  it('omits serviceState from POST payload when no state is selected', async () => {
    const user = userEvent.setup()
    mockMutateAsync.mockResolvedValue({ id: 'client-99' })
    render(<NewClientPanel backLabel="← Clients" />)
    await user.type(screen.getByLabelText('fieldFirstName *'), 'Jane')
    await user.type(screen.getByLabelText('fieldLastName *'), 'Doe')
    await user.type(screen.getByLabelText('fieldDateOfBirth *'), '1980-01-15')
    // Leave serviceState at default empty value
    await user.click(screen.getByRole('button', { name: 'saveAndAddAuth' }))
    await waitFor(() => expect(mockMutateAsync).toHaveBeenCalledOnce())
    expect(mockMutateAsync.mock.calls[0][0].serviceState).toBeUndefined()
  })

  // ── API error ──────────────────────────────────────────────────────────────

  it('shows inline error banner and keeps panel open on API failure', async () => {
    const user = userEvent.setup()
    mockMutateAsync.mockRejectedValue(new Error('Network error'))
    render(<NewClientPanel backLabel="← Clients" />)
    await user.type(screen.getByLabelText('fieldFirstName *'), 'Jane')
    await user.type(screen.getByLabelText('fieldLastName *'), 'Doe')
    await user.type(screen.getByLabelText('fieldDateOfBirth *'), '1980-01-15')
    await user.click(screen.getByRole('button', { name: 'saveAndAddAuth' }))
    expect(await screen.findByRole('alert')).toBeInTheDocument()
    expect(mockClosePanel).not.toHaveBeenCalled()
  })

  // ── Save & Add Authorization ───────────────────────────────────────────────

  it('Save & Add Authorization: closes panel and opens client detail on Authorizations tab', async () => {
    const user = userEvent.setup()
    mockMutateAsync.mockResolvedValue({ id: 'new-client-id' })
    render(<NewClientPanel backLabel="← Clients" />)
    await user.type(screen.getByLabelText('fieldFirstName *'), 'Jane')
    await user.type(screen.getByLabelText('fieldLastName *'), 'Doe')
    await user.type(screen.getByLabelText('fieldDateOfBirth *'), '1980-01-15')
    await user.click(screen.getByRole('button', { name: 'saveAndAddAuth' }))
    await waitFor(() => expect(mockClosePanel).toHaveBeenCalledOnce())
    expect(mockOpenPanel).toHaveBeenCalledWith('client', 'new-client-id', {
      backLabel: 'backLabel',
      initialTab: 'authorizations',
    })
  })

  // ── Save & Close ───────────────────────────────────────────────────────────

  it('Save & Close: shows toast and closes panel', async () => {
    const user = userEvent.setup()
    mockMutateAsync.mockResolvedValue({ id: 'new-client-id' })
    render(<NewClientPanel backLabel="← Clients" />)
    await user.type(screen.getByLabelText('fieldFirstName *'), 'Jane')
    await user.type(screen.getByLabelText('fieldLastName *'), 'Doe')
    await user.type(screen.getByLabelText('fieldDateOfBirth *'), '1980-01-15')
    await user.click(screen.getByRole('button', { name: 'saveAndClose' }))
    await waitFor(() => expect(mockClosePanel).toHaveBeenCalledOnce())
    const toast = useToastStore.getState()
    expect(toast.visible).toBe(true)
    expect(toast.targetId).toBe('new-client-id')
    expect(toast.panelType).toBe('client')
    expect(toast.panelTab).toBe('authorizations')
  })
})
```

- [ ] **Step 2: Run tests — verify they fail**

```bash
cd frontend && npm run test -- NewClientPanel.test
```

Expected: FAIL — `NewClientPanel` not found.

- [ ] **Step 3: Implement `src/components/clients/NewClientPanel.tsx`**

```typescript
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { useCreateClient } from '../../hooks/useClients'
import { usePanelStore } from '../../store/panelStore'
import { useToastStore } from '../../store/toastStore'

const US_STATES = [
  'AL','AK','AZ','AR','CA','CO','CT','DE','FL','GA',
  'HI','ID','IL','IN','IA','KS','KY','LA','ME','MD',
  'MA','MI','MN','MS','MO','MT','NE','NV','NH','NJ',
  'NM','NY','NC','ND','OH','OK','OR','PA','RI','SC',
  'SD','TN','TX','UT','VT','VA','WA','WV','WI','WY',
]

interface FormValues {
  firstName: string
  lastName: string
  dateOfBirth: string
  phone: string
  address: string
  serviceState: string
  medicaidId: string
  preferredCaregiverGender: string
  preferredLanguages: string
  noPetCaregiver: boolean
}

interface Props {
  backLabel: string
}

export function NewClientPanel({ backLabel }: Props) {
  const { t } = useTranslation('clients')
  const tCommon = useTranslation('common').t
  const { closePanel, openPanel } = usePanelStore()
  const createMutation = useCreateClient()
  const [apiError, setApiError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({
    defaultValues: {
      firstName: '',
      lastName: '',
      dateOfBirth: '',
      phone: '',
      address: '',
      serviceState: '',
      medicaidId: '',
      preferredCaregiverGender: '',
      preferredLanguages: '',
      noPetCaregiver: false,
    },
  })

  function buildPayload(values: FormValues) {
    return {
      firstName: values.firstName,
      lastName: values.lastName,
      dateOfBirth: values.dateOfBirth,
      phone: values.phone || undefined,
      address: values.address || undefined,
      serviceState: values.serviceState || undefined,      // empty select → omit, not ""
      medicaidId: values.medicaidId || undefined,
      preferredCaregiverGender: values.preferredCaregiverGender || undefined, // "no preference" → omit
      preferredLanguages: values.preferredLanguages
        ? JSON.stringify(
            values.preferredLanguages.split(',').map((s) => s.trim()).filter(Boolean)
          )
        : undefined,
      noPetCaregiver: values.noPetCaregiver,
    }
  }

  async function onSaveAndAddAuth(values: FormValues) {
    setApiError(null)
    try {
      const client = await createMutation.mutateAsync(buildPayload(values))
      closePanel()
      openPanel('client', client.id, {
        backLabel: t('backLabel'),
        initialTab: 'authorizations',
      })
    } catch {
      setApiError(tCommon('errorTryAgain'))
    }
  }

  async function onSaveAndClose(values: FormValues) {
    setApiError(null)
    try {
      const client = await createMutation.mutateAsync(buildPayload(values))
      useToastStore.getState().show({
        message: t('saveCloseToast'),
        linkLabel: t('saveCloseToastLink'),
        targetId: client.id,
        panelType: 'client',
        panelTab: 'authorizations',
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
        <h2 className="text-[16px] font-bold text-dark">{t('addClientPanelTitle')}</h2>
        <p className="text-[12px] text-text-secondary mt-0.5">{t('addClientRequiredNote')}</p>
      </div>

      {/* Scrollable form body */}
      <div className="flex-1 overflow-auto px-6 py-4 space-y-6">

        {/* Client Identity */}
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
            <label htmlFor="dateOfBirth" className="block text-[12px] text-text-secondary mb-1">
              {t('fieldDateOfBirth')} *
            </label>
            <input
              id="dateOfBirth"
              type="date"
              {...register('dateOfBirth', { required: t('validationDobRequired') })}
              className="border border-border px-3 py-1.5 text-[13px] focus:outline-none focus:border-dark"
            />
            {errors.dateOfBirth && (
              <p className="text-[11px] text-red-500 mt-0.5">{errors.dateOfBirth.message}</p>
            )}
          </div>
        </section>

        {/* Contact & Location */}
        <section>
          <h3 className="text-[10px] font-bold uppercase tracking-[0.1em] text-text-secondary mb-3">
            {t('sectionContact')}
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
              <label htmlFor="serviceState" className="block text-[12px] text-text-secondary mb-1">
                {t('fieldServiceState')}
              </label>
              <select
                id="serviceState"
                {...register('serviceState')}
                className="w-full border border-border px-3 py-1.5 text-[13px] bg-white focus:outline-none focus:border-dark"
              >
                <option value="">{t('fieldSelectState')}</option>
                {US_STATES.map((s) => (
                  <option key={s} value={s}>{s}</option>
                ))}
              </select>
            </div>
          </div>
        </section>

        {/* Billing */}
        <section>
          <h3 className="text-[10px] font-bold uppercase tracking-[0.1em] text-text-secondary mb-3">
            {t('sectionBilling')}
          </h3>
          <div>
            <label htmlFor="medicaidId" className="block text-[12px] text-text-secondary mb-1">
              {t('fieldMedicaidId')}
            </label>
            <input
              id="medicaidId"
              type="text"
              autoComplete="off"
              {...register('medicaidId')}
              className="w-full border border-border px-3 py-1.5 text-[13px] focus:outline-none focus:border-dark"
            />
          </div>
        </section>

        {/* Care Preferences */}
        <section>
          <h3 className="text-[10px] font-bold uppercase tracking-[0.1em] text-text-secondary mb-3">
            {t('sectionPreferences')}
          </h3>
          <div className="space-y-3">
            <div>
              <label htmlFor="preferredCaregiverGender" className="block text-[12px] text-text-secondary mb-1">
                {t('fieldPreferredGender')}
              </label>
              <select
                id="preferredCaregiverGender"
                {...register('preferredCaregiverGender')}
                className="w-full border border-border px-3 py-1.5 text-[13px] bg-white focus:outline-none focus:border-dark"
              >
                <option value="">{t('fieldGenderNoPreference')}</option>
                <option value="FEMALE">{t('fieldGenderFemale')}</option>
                <option value="MALE">{t('fieldGenderMale')}</option>
              </select>
            </div>
            <div>
              <label htmlFor="preferredLanguages" className="block text-[12px] text-text-secondary mb-1">
                {t('fieldPreferredLanguages')}
              </label>
              <input
                id="preferredLanguages"
                type="text"
                placeholder={t('fieldPreferredLanguagesHint')}
                {...register('preferredLanguages')}
                className="w-full border border-border px-3 py-1.5 text-[13px] focus:outline-none focus:border-dark"
              />
            </div>
            <label className="flex items-center gap-2 text-[13px] cursor-pointer">
              <input type="checkbox" {...register('noPetCaregiver')} className="w-4 h-4" />
              {t('fieldNoPetCaregiver')}
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
          onClick={handleSubmit(onSaveAndAddAuth)}
          className="flex-1 py-2 text-[13px] font-bold bg-dark text-white disabled:opacity-50 hover:brightness-110"
        >
          {t('saveAndAddAuth')}
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
cd frontend && npm run test -- NewClientPanel.test
```

Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/clients/NewClientPanel.tsx frontend/src/components/clients/NewClientPanel.test.tsx
git commit -m "feat: implement NewClientPanel form with validation and dual save paths"
```

---

## Task 8: Wire up `Shell.tsx` and `ClientsPage.tsx`

**Files:**
- Modify: `src/components/layout/Shell.tsx`
- Modify: `src/components/clients/ClientsPage.tsx`

- [ ] **Step 1: Update `src/components/layout/Shell.tsx`**

Add `NewClientPanel` and `Toast` imports at the top of the file:

```typescript
import { NewClientPanel } from '../clients/NewClientPanel'
import { Toast } from '../common/Toast'
```

In `PanelContent`, add the `initialTab` destructure and the `newClient` branch. Also pass `initialTab` to `ClientDetailPanel`:

```typescript
function PanelContent() {
  const { type, selectedId, prefill, backLabel, initialTab } = usePanelStore()

  if (type === 'shift' && selectedId) {
    return <ShiftDetailPanel shiftId={selectedId} backLabel={backLabel} />
  }
  if (type === 'newShift') {
    return <NewShiftPanel prefill={prefill} backLabel={backLabel} />
  }
  if (type === 'newClient') {
    return <NewClientPanel backLabel={backLabel} />
  }
  if (type === 'client' && selectedId) {
    return <ClientDetailPanel clientId={selectedId} backLabel={backLabel} initialTab={initialTab} />
  }
  if (type === 'caregiver' && selectedId) {
    return <CaregiverDetailPanel caregiverId={selectedId} backLabel={backLabel} />
  }
  if (type === 'payer') {
    return (
      <div className="p-6 text-text-secondary">
        Payer detail — coming in Phase 8
      </div>
    )
  }
  return null
}
```

In the `Shell` return, add `<Toast />` alongside `<SlidePanel>`:

```typescript
export function Shell() {
  const { open, closePanel } = usePanelStore()
  const { data } = useDashboard()
  const redEvvCount = data?.redEvvCount ?? 0

  return (
    <div className="flex h-screen overflow-hidden">
      <Sidebar redEvvCount={redEvvCount} />
      <div className="relative flex-1 overflow-auto bg-surface">
        <Outlet />
        <SlidePanel isOpen={open} onClose={closePanel}>
          <PanelContent />
        </SlidePanel>
        <Toast />
      </div>
    </div>
  )
}
```

- [ ] **Step 2: Update `src/components/clients/ClientsPage.tsx`**

Add the `usePanelStore` import:

```typescript
import { usePanelStore } from '../../store/panelStore'
```

Inside `ClientsPage`, destructure `openPanel`:

```typescript
const { openPanel } = usePanelStore()
```

Replace the `alert()` call in the button's `onClick`:

```typescript
// From:
onClick={() => alert(t('addClientAlert'))}

// To:
onClick={() => openPanel('newClient', undefined, { backLabel: t('backLabel') })}
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

Expected: all tests pass. If lint is configured as a pre-commit hook, also run:

```bash
npm run lint --fix
```

- [ ] **Step 5: Smoke test in the browser**

```bash
# From repo root:
./dev-start.sh
```

1. Log in as `admin@sunrise.dev` / `Admin1234!`
2. Navigate to **Clients**
3. Click **+ Add Client** — the slide panel should open
4. Submit with empty fields — validation errors should appear
5. Fill in First Name, Last Name, Date of Birth → click **Save & Close** — toast appears bottom-right for 6 s
6. Click **Add Authorization** in the toast — client detail panel opens on Authorizations tab
7. Repeat with **Save & Add Authorization** — panel transitions directly to client detail Authorizations tab

- [ ] **Step 6: Commit**

```bash
git add frontend/src/components/layout/Shell.tsx frontend/src/components/clients/ClientsPage.tsx
git commit -m "feat: wire NewClientPanel and Toast into Shell; replace alert() in ClientsPage"
```

---

## Self-Review Checklist

**Spec coverage:**
- [x] `+ Add Client` button replaced with `openPanel('newClient', ...)` — Task 8
- [x] 10 fields captured with correct input types — Task 7
- [x] Required fields: firstName, lastName, dateOfBirth — Task 7
- [x] `preferredCaregiverGender` omits field (not `""`) for no preference — Task 7 `buildPayload`
- [x] `serviceState` omits field (not `""`) when blank — Task 7 `buildPayload`
- [x] `preferredLanguages` JSON-serialized — Task 7 `buildPayload`
- [x] Save & Add Authorization → opens `ClientDetailPanel` on authorizations tab — Task 7 + 5
- [x] Save & Close → toast with link to authorizations tab — Task 7 + 3
- [x] Toast auto-dismisses after 6 s — Task 4
- [x] Toast timer re-arms on rapid successive saves (showCount) — Task 3 + 4
- [x] `initialTab` as top-level `PanelState` field, not in `PanelPrefill` — Task 5
- [x] `closePanel` resets `initialTab` to `undefined` — Task 5
- [x] Toast placed `fixed bottom-4 right-4 z-50` — Task 4
- [x] Toast imports no i18n — Task 4
- [x] All i18n keys added — Task 6
- [x] `medicaidId` has `autocomplete="off"` — Task 7
- [x] Both footer buttons disabled while mutation pending — Task 7
- [x] API error banner stays panel open — Task 7
- [x] React Query cache invalidated on success — Task 2

**Test coverage:**
- [x] `useCreateClient` mutation + invalidation — Task 2
- [x] `toastStore` show/dismiss/showCount — Task 3
- [x] `Toast` render, link click, auto-dismiss, manual dismiss, re-arm — Task 4
- [x] `NewClientPanel` sections, validation, payload, languages, serviceState empty, API error, both save paths — Task 7
