# Phase 7: Wire Caregivers Screen

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace mock data in the caregivers screen with real API data — paginated caregiver list, detail panel with tabbed views for credentials (real expiry dates), background checks, and paginated shift history.

**Before starting:**
- Phase 3 (auth wiring) must be complete.
- `frontend/src/api/caregivers.ts` was created in Phase 4 with `listCaregivers` and `getCaregiver`. This phase expands it.
- `frontend/src/hooks/useCaregivers.ts` was created in Phase 4 with `useCaregivers` and `useCaregiverDetail`. This phase adds credential and shift-history hooks.
- `Shell.tsx` already routes `type === 'caregiver'` to `CaregiverDetailPanel`. Do **not** render `CaregiverDetailPanel` inside `CaregiversPage` and do **not** add a new panel type — `CaregiversTable` already calls `openPanel('caregiver', id)` which is correct.

---

### Task 0: Add Missing Locale Keys

**Files:**
- Modify: `frontend/public/locales/en/caregivers.json`

- [ ] **Step 0.1: Add missing keys to caregivers.json**

Open `frontend/public/locales/en/caregivers.json` and add the following key-value pairs as entries inside the existing JSON object. Each new entry must be preceded by a comma after the previous entry; preserve the closing `}`.

```json
"loadingCaregivers": "Loading caregivers…",
"errorCaregivers": "Failed to load caregivers.",
"totalCaregivers": "{{total}} total",
"noBackgroundChecks": "No background checks on file.",
"noShiftHistory": "No shift history on file.",
"prev": "Prev",
"next": "Next",
"pageOf": "Page {{page}} of {{total}}"
```

- [ ] **Step 0.2: Commit**

```bash
cd frontend && git add public/locales/en/caregivers.json
git commit -m "feat: add missing caregivers locale keys for phase 7 wiring"
```

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
  BackgroundCheckResponse,
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
): Promise<PageResponse<BackgroundCheckResponse>> {
  const response = await apiClient.get<PageResponse<BackgroundCheckResponse>>(
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

- [ ] **Step 1.2: Update CredentialResponse and add BackgroundCheckResponse in types/api.ts**

Open `frontend/src/types/api.ts`. The `CredentialResponse` interface already exists but is missing `agencyId`. Add that field, then add `BackgroundCheckResponse` below it:

```ts
// In the existing CredentialResponse interface, add agencyId after caregiverId:
export interface CredentialResponse {
  id: string
  caregiverId: string
  agencyId: string          // ← add this field
  credentialType: string
  issueDate: string | null   // ISO-8601 LocalDate
  expiryDate: string | null  // ISO-8601 LocalDate
  verified: boolean
  verifiedBy: string | null
  createdAt: string
}

// Add this new interface after CredentialResponse:
export interface BackgroundCheckResponse {
  id: string
  caregiverId: string
  agencyId: string
  checkType: string
  result: 'PASS' | 'FAIL' | 'PENDING' | 'EXPIRED'
  checkedAt: string          // ISO-8601 LocalDate
  renewalDueDate: string | null
  createdAt: string
}
```

- [ ] **Step 1.2b: Update mockCredentials in mock/data.ts to include agencyId**

Open `frontend/src/mock/data.ts`. The `mockCredentials` array has two `CredentialResponse` objects. Add `agencyId` to each — use the same agency ID constant used elsewhere in that file (e.g. `IDS.agency` or the literal UUID). After Step 1.2 makes `agencyId` a required field, these objects will cause a TypeScript compile error unless updated.

```ts
// In each object inside mockCredentials, add agencyId after caregiverId:
agencyId: IDS.agency,   // use whatever agency ID constant the file already uses
```

- [ ] **Step 1.3: Commit**

```bash
cd frontend && git add src/api/caregivers.ts src/types/api.ts src/mock/data.ts
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
    staleTime: 60_000,
  })
}

export function useCaregiverCredentials(caregiverId: string | null) {
  return useQuery({
    queryKey: ['caregiver-credentials', caregiverId],
    queryFn: () => listCredentials(caregiverId!, 0, 50),
    enabled: Boolean(caregiverId),
    staleTime: 60_000,
  })
}

export function useCaregiverBackgroundChecks(caregiverId: string | null) {
  return useQuery({
    queryKey: ['caregiver-background-checks', caregiverId],
    queryFn: () => listBackgroundChecks(caregiverId!, 0, 20),
    enabled: Boolean(caregiverId),
    staleTime: 60_000,
  })
}

export function useCaregiverShiftHistory(caregiverId: string | null, page = 0) {
  return useQuery({
    queryKey: ['caregiver-shifts', caregiverId, page],
    queryFn: () => listShiftHistory(caregiverId!, page, 20),
    enabled: Boolean(caregiverId),
    staleTime: 60_000,
  })
}
```

- [ ] **Step 2.2: Update Schedule-screen and NewShiftPanel imports to use useAllCaregivers**

Three files use `useCaregivers` for lookup maps or full caregiver lists. Update all three:

In `frontend/src/components/schedule/SchedulePage.tsx`:
```tsx
// Change:
import { useCaregivers } from '../../hooks/useCaregivers'
// To:
import { useAllCaregivers } from '../../hooks/useCaregivers'
// And update usage:
const { caregiverMap } = useAllCaregivers()
```

In `frontend/src/components/schedule/ShiftDetailPanel.tsx`:
```tsx
// Change:
import { useCaregivers } from '../../hooks/useCaregivers'
// To:
import { useAllCaregivers } from '../../hooks/useCaregivers'
// And update usage:
const { caregiverMap } = useAllCaregivers()
```

In `frontend/src/components/schedule/NewShiftPanel.tsx`:
```tsx
// Change:
import { useCaregivers } from '../../hooks/useCaregivers'
// To:
import { useAllCaregivers } from '../../hooks/useCaregivers'
// And update usage:
const { caregivers } = useAllCaregivers()
```

- [ ] **Step 2.3: Commit**

```bash
cd frontend && git add src/hooks/useCaregivers.ts \
  src/components/schedule/SchedulePage.tsx \
  src/components/schedule/ShiftDetailPanel.tsx \
  src/components/schedule/NewShiftPanel.tsx
git commit -m "feat: expand useCaregivers hook with credentials, background-checks, and shift-history hooks"
```

---

### Task 3: Update CaregiversPage to Use Real Data

**Files:**
- Modify: `frontend/src/components/caregivers/CaregiversPage.tsx`

**Note:** `CaregiversTable` already calls `openPanel('caregiver', id, { backLabel: t('backLabel') })`, which `Shell.tsx` routes to `CaregiverDetailPanel`. Do **not** render `CaregiverDetailPanel` inside `CaregiversPage`, and do **not** add a new `PanelType`. The only change here is replacing mock data with real API data and adding server-side pagination.

- [ ] **Step 3.1: Update CaregiversPage**

Replace the full contents of `frontend/src/components/caregivers/CaregiversPage.tsx`:

```tsx
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { CaregiversTable } from './CaregiversTable'
import { useCaregivers } from '../../hooks/useCaregivers'

export function CaregiversPage() {
  const { t } = useTranslation('caregivers')
  const tCommon = useTranslation('common').t
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)
  const { caregivers, isLoading, isError, totalPages, totalElements } = useCaregivers(page, 20)

  if (isLoading && caregivers.length === 0) {
    return (
      <div className="flex items-center justify-center h-full bg-surface">
        <span className="text-[14px] text-text-muted">{t('loadingCaregivers')}</span>
      </div>
    )
  }

  if (isError && caregivers.length === 0) {
    return (
      <div className="flex items-center justify-center h-full bg-surface">
        <span className="text-[14px] text-text-muted">{t('errorCaregivers')}</span>
      </div>
    )
  }

  return (
    <div className="flex flex-col h-full">
      {/* Header */}
      <div className="flex items-center gap-4 px-6 py-4 bg-white border-b border-border">
        <h1 className="text-[16px] font-bold tracking-[-0.02em] text-dark">{t('pageTitle')}</h1>
        {totalElements > 0 && (
          <span className="text-[13px] text-text-secondary">
            {t('totalCaregivers', { total: totalElements })}
          </span>
        )}
        <input
          type="search"
          placeholder={tCommon('searchByName')}
          value={search}
          onChange={(e) => { setSearch(e.target.value); setPage(0) }}
          className="ml-4 border border-border px-3 py-1.5 text-[13px] w-64 focus:outline-none focus:border-dark"
        />
        <button
          type="button"
          className="ml-auto px-4 py-1.5 text-[12px] font-bold bg-dark text-white hover:brightness-110"
          onClick={() => alert(t('addCaregiverAlert'))}
        >
          {t('addCaregiver')}
        </button>
      </div>

      {/* Table */}
      <div className="flex-1 overflow-auto">
        <CaregiversTable caregivers={caregivers} search={search} />
      </div>

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex items-center justify-end gap-2 px-6 py-3 border-t border-border bg-white">
          <button
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            disabled={page === 0}
            className="px-3 py-1.5 rounded text-[13px] border border-border text-text-secondary disabled:opacity-40"
          >
            {t('prev')}
          </button>
          <span className="text-[13px] text-text-secondary">
            {t('pageOf', { page: page + 1, total: totalPages })}
          </span>
          <button
            onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
            disabled={page === totalPages - 1}
            className="px-3 py-1.5 rounded text-[13px] border border-border text-text-secondary disabled:opacity-40"
          >
            {t('next')}
          </button>
        </div>
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

**Pattern:** Follow `ClientDetailPanel` exactly — render `flex flex-col h-full` as root, call `usePanelStore().closePanel()` for all close actions, use `useTranslation('caregivers')`, use `formatLocalDate` for all ISO date strings (avoids UTC-midnight off-by-one errors), use Tailwind design tokens (not raw hex). Do **not** use `SlidePanel` — `Shell.tsx` already wraps the content in `SlidePanel`.

- [ ] **Step 4.1: Update CaregiverDetailPanel**

Replace the full contents of `frontend/src/components/caregivers/CaregiverDetailPanel.tsx`:

```tsx
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { usePanelStore } from '../../store/panelStore'
import { formatLocalDate, formatLocalTime } from '../../utils/dateFormat'
import {
  useCaregiverDetail,
  useCaregiverCredentials,
  useCaregiverBackgroundChecks,
  useCaregiverShiftHistory,
} from '../../hooks/useCaregivers'
import type { CredentialResponse, BackgroundCheckResponse } from '../../types/api'

type Tab = 'overview' | 'credentials' | 'backgroundChecks' | 'shiftHistory'

interface CaregiverDetailPanelProps {
  caregiverId: string
  backLabel: string
}

function CredentialRow({ cred, locale }: { cred: CredentialResponse; locale: string }) {
  const { t } = useTranslation('caregivers')
  const today = new Date()
  // Use T12:00:00 anchor to avoid UTC-midnight off-by-one in negative-offset timezones
  const expiry = cred.expiryDate ? new Date(`${cred.expiryDate}T12:00:00`) : null
  const daysUntilExpiry = expiry
    ? Math.ceil((expiry.getTime() - today.getTime()) / (1000 * 60 * 60 * 24))
    : null

  const expiryColorClass =
    daysUntilExpiry === null
      ? 'text-text-muted'
      : daysUntilExpiry <= 0
      ? 'text-red-600'
      : daysUntilExpiry <= 30
      ? 'text-yellow-600'
      : 'text-green-600'

  return (
    <div className="flex items-center justify-between border border-border rounded px-3 py-2 bg-white">
      <div>
        <p className="text-[13px] font-medium text-dark">
          {cred.credentialType.replace(/_/g, ' ')}
        </p>
        <p className="text-[11px] text-text-secondary">
          {cred.verified ? t('credVerified') : t('credUnverified')}
        </p>
      </div>
      <div className="text-right">
        {expiry ? (
          <p className={`text-[11px] font-semibold ${expiryColorClass}`}>
            {daysUntilExpiry !== null && daysUntilExpiry <= 0
              ? 'EXPIRED'
              : formatLocalDate(cred.expiryDate!, locale)}
          </p>
        ) : (
          <p className="text-[11px] text-text-muted">No expiry</p>
        )}
      </div>
    </div>
  )
}

function BackgroundCheckRow({ bc, locale }: { bc: BackgroundCheckResponse; locale: string }) {
  const badgeClass: Record<BackgroundCheckResponse['result'], string> = {
    PASS:    'bg-green-50 text-green-600',
    FAIL:    'bg-red-50 text-red-600',
    EXPIRED: 'bg-red-50 text-red-600',
    PENDING: 'bg-yellow-50 text-yellow-700',
  }
  return (
    <div className="border border-border rounded px-3 py-2 bg-white">
      <div className="flex items-center justify-between">
        <p className="text-[13px] font-medium text-dark">
          {bc.checkType.replace(/_/g, ' ')}
        </p>
        <span className={`text-[11px] font-semibold px-2 py-0.5 rounded ${badgeClass[bc.result]}`}>
          {bc.result}
        </span>
      </div>
      {bc.checkedAt && (
        <p className="text-[11px] text-text-secondary mt-1">
          Checked: {formatLocalDate(bc.checkedAt, locale)}
          {bc.renewalDueDate ? ` · Renewal due: ${formatLocalDate(bc.renewalDueDate, locale)}` : ''}
        </p>
      )}
    </div>
  )
}

export function CaregiverDetailPanel({ caregiverId, backLabel }: CaregiverDetailPanelProps) {
  const { t, i18n } = useTranslation('caregivers')
  const tCommon = useTranslation('common').t
  const { closePanel } = usePanelStore()
  const [activeTab, setActiveTab] = useState<Tab>('overview')
  const [historyPage, setHistoryPage] = useState(0)

  const { data: caregiver, isLoading, isError } = useCaregiverDetail(caregiverId)
  const { data: credsPage } = useCaregiverCredentials(caregiverId)
  const { data: bgChecksPage } = useCaregiverBackgroundChecks(caregiverId)
  const { data: shiftHistoryPage } = useCaregiverShiftHistory(caregiverId, historyPage)

  const credentials = credsPage?.content ?? []
  const bgChecks = bgChecksPage?.content ?? []
  const shiftHistory = shiftHistoryPage?.content ?? []
  const shiftHistoryTotalPages = shiftHistoryPage?.totalPages ?? 0

  const TABS: { id: Tab; label: string }[] = [
    { id: 'overview', label: t('tabOverview') },
    { id: 'credentials', label: `${t('tabCredentials')} (${credsPage?.totalElements ?? 0})` },
    { id: 'backgroundChecks', label: t('tabBackgroundChecks') },
    { id: 'shiftHistory', label: t('tabShiftHistory') },
  ]

  if (isLoading && !caregiver) {
    return (
      <div className="flex flex-col h-full">
        <div className="px-6 py-4 border-b border-border">
          <button
            type="button"
            className="text-[13px] mb-2 text-blue hover:underline"
            onClick={closePanel}
          >
            {backLabel}
          </button>
        </div>
        <div className="flex items-center justify-center flex-1">
          <span className="text-[13px] text-text-muted">{tCommon('loading')}</span>
        </div>
      </div>
    )
  }

  if (isError || !caregiver) {
    return (
      <div className="p-8">
        <button type="button" className="text-[13px] mb-4 text-blue hover:underline" onClick={closePanel}>
          {backLabel}
        </button>
        <p className="text-text-secondary">{t('notFound')}</p>
      </div>
    )
  }

  return (
    <div className="flex flex-col h-full">
      {/* Header */}
      <div className="px-6 py-4 border-b border-border">
        <button
          type="button"
          className="text-[13px] mb-2 text-blue hover:underline"
          onClick={closePanel}
        >
          {backLabel}
        </button>
        <h2 className="text-[16px] font-bold text-dark">
          {caregiver.firstName} {caregiver.lastName}
        </h2>
        <p className="text-[12px] text-text-secondary mt-0.5">{caregiver.email}</p>
      </div>

      {/* Tabs */}
      <div className="flex border-b border-border px-6 bg-white">
        {TABS.map((tab) => (
          <button
            key={tab.id}
            type="button"
            onClick={() => setActiveTab(tab.id)}
            className={[
              'px-4 py-3 text-[12px] font-medium border-b-2 -mb-px transition-colors',
              activeTab === tab.id
                ? 'border-dark text-dark'
                : 'border-transparent text-text-secondary hover:text-dark',
            ].join(' ')}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* Tab content */}
      <div className="flex-1 overflow-auto px-6 py-4">
        {/* Overview tab */}
        {activeTab === 'overview' && (
          <div className="grid grid-cols-2 gap-4">
            {[
              [t('fieldPhone'), caregiver.phone ?? tCommon('noDash')],
              [t('fieldAddress'), caregiver.address ?? tCommon('noDash')],
              [t('fieldHireDate'), caregiver.hireDate
                ? formatLocalDate(caregiver.hireDate, i18n.language)
                : tCommon('noDash')],
              [t('fieldStatus'), caregiver.status],
              [t('fieldHasPet'), caregiver.hasPet ? tCommon('yes') : tCommon('no')],
            ].map(([label, value]) => (
              <div key={label}>
                <div className="text-[10px] text-text-secondary">{label}</div>
                <div className="text-[13px] text-dark">{value}</div>
              </div>
            ))}
          </div>
        )}

        {/* Credentials tab */}
        {activeTab === 'credentials' && (
          <div className="space-y-2">
            {credentials.length === 0 ? (
              <p className="text-[13px] text-text-secondary">{t('noCredentials')}</p>
            ) : (
              credentials.map((cred) => (
                <CredentialRow key={cred.id} cred={cred} locale={i18n.language} />
              ))
            )}
          </div>
        )}

        {/* Background checks tab */}
        {activeTab === 'backgroundChecks' && (
          <div className="space-y-2">
            {bgChecks.length === 0 ? (
              <p className="text-[13px] text-text-secondary">{t('noBackgroundChecks')}</p>
            ) : (
              bgChecks.map((bc) => <BackgroundCheckRow key={bc.id} bc={bc} locale={i18n.language} />)
            )}
          </div>
        )}

        {/* Shift history tab */}
        {activeTab === 'shiftHistory' && (
          <div>
            {shiftHistory.length === 0 ? (
              <p className="text-[13px] text-text-secondary">{t('noShiftHistory')}</p>
            ) : (
              <div className="space-y-2">
                {shiftHistory.map((shift) => (
                  <div
                    key={shift.id}
                    className="border border-border rounded px-3 py-2 bg-white"
                  >
                    <div className="flex items-center justify-between">
                      <p className="text-[12px] font-medium text-dark">
                        {new Date(shift.scheduledStart).toLocaleDateString(i18n.language, {
                          month: 'short',
                          day: 'numeric',
                        })}
                        {' '}
                        {formatLocalTime(shift.scheduledStart, i18n.language)}
                        {' — '}
                        {formatLocalTime(shift.scheduledEnd, i18n.language)}
                      </p>
                      <span className="text-[11px] px-2 py-0.5 rounded border border-border text-text-secondary bg-surface">
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
                  className="px-3 py-1.5 rounded text-[12px] border border-border text-text-secondary disabled:opacity-40"
                >
                  {t('prev')}
                </button>
                <span className="text-[12px] text-text-secondary">
                  {historyPage + 1} / {shiftHistoryTotalPages}
                </span>
                <button
                  onClick={() => setHistoryPage((p) => Math.min(shiftHistoryTotalPages - 1, p + 1))}
                  disabled={historyPage === shiftHistoryTotalPages - 1}
                  className="px-3 py-1.5 rounded text-[12px] border border-border text-text-secondary disabled:opacity-40"
                >
                  {t('next')}
                </button>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
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
3. If there are multiple pages (>20 caregivers), verify the "Prev" / "Next" pagination at the bottom works.
4. Verify the search input filters by name within the current page.
5. Click on a caregiver. The detail panel should slide in on the "Overview" tab showing real email, phone, hire date, and status.
6. Click the "Credentials" tab. Verify credentials are listed with real expiry dates. Credentials expiring within 30 days display in yellow; expired credentials in red; valid credentials in green.
7. Click the "Background Checks" tab. Verify background checks are listed with PASS/FAIL/PENDING/EXPIRED badges.
8. Click the "Shift History" tab. Verify the caregiver's shift history is listed (most recent first). If there are more than 20 shifts, verify the pagination controls work.
9. Open DevTools → Network. Confirm `GET /api/v1/caregivers/{id}/credentials`, `GET /api/v1/caregivers/{id}/background-checks`, and `GET /api/v1/caregivers/{id}/shifts` are called as tabs are opened.

Proceed to Phase 8 only after this checkpoint passes.
