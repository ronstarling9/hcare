# Family Portal — Implementation Plan Part 3: Frontend

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **This is Part 3 of 3.** Requires Parts 1 and 2 to be fully complete first.

**Goal:** Build the complete family portal frontend: auth store, API layer, portal routes, PortalVerifyPage, PortalDashboardPage, and the FamilyPortalTab admin component.

**Architecture:** New `/portal/*` routes as React Router siblings to admin routes. `portalAuthStore` (Zustand + `persist`) holds the FAMILY_PORTAL JWT in `localStorage`. All portal API calls use a dedicated `portalApiClient` (separate Axios instance — no admin JWT interceptor). Six verify-page states. Dashboard renders 6 todayVisit states with timezone-aware time display.

**Tech Stack:** React 18, TypeScript, Zustand 4 (`persist` middleware), React Query 5, Axios, React Router v6, react-i18next, Vitest, Testing Library.

---

## File Map

| Action | Path |
|--------|------|
| Create | `frontend/src/store/portalAuthStore.ts` |
| Create | `frontend/src/api/portal.ts` |
| Create | `frontend/src/hooks/usePortalDashboard.ts` |
| Create | `frontend/src/components/portal/PortalGuard.tsx` |
| Create | `frontend/src/components/portal/PortalLayout.tsx` |
| Create | `frontend/src/pages/PortalVerifyPage.tsx` |
| Create | `frontend/src/pages/PortalDashboardPage.tsx` |
| Create | `frontend/src/components/clients/FamilyPortalTab.tsx` |
| Create | `frontend/public/locales/en/portal.json` |
| Create | `frontend/src/components/clients/FamilyPortalTab.test.tsx` |
| Create | `frontend/src/pages/PortalVerifyPage.test.tsx` |
| Create | `frontend/src/pages/PortalDashboardPage.test.tsx` |
| Modify | `frontend/src/App.tsx` |
| Modify | `frontend/src/components/clients/ClientDetailPanel.tsx` |
| Modify | `frontend/src/i18n.ts` |
| Modify | `frontend/public/locales/en/clients.json` |
| Modify | `frontend/src/mock/data.ts` |
| Modify | `frontend/src/test/setup.ts` |

---

## Task 14: portalAuthStore, i18n, mock data

**Files:**
- Create: `frontend/src/store/portalAuthStore.ts`
- Create: `frontend/public/locales/en/portal.json`
- Modify: `frontend/src/i18n.ts`
- Modify: `frontend/public/locales/en/clients.json`
- Modify: `frontend/src/mock/data.ts`
- Modify: `frontend/src/test/setup.ts`

- [ ] **Step 1: Create `portalAuthStore.ts`**

```ts
import { create } from 'zustand'
import { persist } from 'zustand/middleware'

interface PortalAuthState {
  token: string | null
  clientId: string | null
  agencyId: string | null
  login: (token: string, clientId: string, agencyId: string) => void
  logout: () => void
}

export const usePortalAuthStore = create<PortalAuthState>()(
  persist(
    (set) => ({
      token: null,
      clientId: null,
      agencyId: null,
      login: (token, clientId, agencyId) => set({ token, clientId, agencyId }),
      logout: () => set({ token: null, clientId: null, agencyId: null }),
    }),
    { name: 'portal-auth' },
  ),
)
```

Note on `logout()`: the `persist` middleware writes `null` values back — the key `portal-auth` remains in `localStorage` but `token` is `null`, which is all `PortalGuard` checks. The key is not truly removed from `localStorage` after logout.

- [ ] **Step 2: Create `frontend/public/locales/en/portal.json`**

```json
{
  "verifyingTitle": "Signing you in…",
  "linkExpiredHeading": "Link expired",
  "linkExpiredBody": "This link has expired or is invalid. Ask your care coordinator for a new one.",
  "sessionExpiredHeading": "Session expired",
  "sessionExpiredBody": "Your session has expired. Ask your care coordinator for a new link.",
  "noSessionHeading": "No active session",
  "noSessionBody": "No active session found. Ask your care coordinator for an access link.",
  "signedOutHeading": "You've been signed out",
  "signedOutBody": "You've been signed out. Use your original link or ask your care coordinator to send a new one.",
  "accessRevokedHeading": "Access removed",
  "accessRevokedBody": "Your portal access has been removed. Contact your care coordinator for more information.",
  "signOut": "Sign out",
  "todayVisitHeading": "Today's Visit",
  "upcomingHeading": "Upcoming",
  "lastVisitHeading": "Last Visit",
  "statusInProgress": "{{name}} is here now",
  "statusScheduledOnTime": "{{name}} is scheduled for {{time}}",
  "statusLate": "Scheduled for {{time}} — not yet started. Contact your care coordinator.",
  "statusCompleted": "Visit completed at {{time}}",
  "statusCancelled": "Today's visit was cancelled. Contact your care coordinator.",
  "noVisitToday": "No visit scheduled for today",
  "noUpcomingVisits": "No upcoming visits scheduled. Contact your care coordinator to confirm the schedule.",
  "clockedIn": "Clocked in",
  "scheduledUntil": "Scheduled until",
  "lastVisitCompleted": "Completed at {{time}} · {{duration}}",
  "careServicesConcludedHeading": "Care services concluded",
  "careServicesConcludedBody": "Care services for {{name}} have concluded. Please contact the agency for more information.",
  "loadError": "Unable to load.",
  "tapToRetry": "Tap to retry",
  "inviteEmail": "EMAIL ADDRESS",
  "generateLink": "Generate Link",
  "copyLink": "Copy",
  "linkCopied": "Copied!",
  "inviteExpiry": "Link expires {{date}} at {{time}} — valid for 72 hours",
  "reInviteNote": "A new link will be sent to this existing user",
  "removeConfirmation": "Remove {{name}}? Their access will be revoked on next page load. Any active session ends when they next visit the portal.",
  "removeConfirm": "Remove",
  "neverLoggedIn": "Never logged in",
  "lastLogin": "Last login",
  "sendNewLink": "Send New Link",
  "addInvite": "+ Invite",
  "portalAccessHeading": "Family Portal Access",
  "noPortalUsers": "No family members have portal access yet.",
  "cancel": "Cancel",
  "done": "Done",
  "remove": "Remove",
  "loading": "Loading…"
}
```

- [ ] **Step 3: Register `portal` namespace in `i18n.ts`**

Add `'portal'` to the `ns` array:
```ts
ns: [
  'common',
  'nav',
  'schedule',
  'shiftDetail',
  'newShift',
  'dashboard',
  'clients',
  'caregivers',
  'payers',
  'evvStatus',
  'auth',
  'portal',
],
```

- [ ] **Step 4: Add `familyPortal*` keys to `clients.json`**

Add these keys to the end of `frontend/public/locales/en/clients.json` (before the closing `}`):
```json
  "familyPortalPhaseNote": "Family portal coming soon.",
  "familyPortalTabAccess": "Family Portal Access"
```

(The `familyPortalPhaseNote` key already exists — do not duplicate it. Just add `familyPortalTabAccess` if missing.)

- [ ] **Step 5: Add portal fixture IDs to `mock/data.ts`**

Add to the `IDS` object:
```ts
fpUser1: 'f1000000-0000-0000-0000-000000000001',
fpUser2: 'f2000000-0000-0000-0000-000000000002',
```

- [ ] **Step 6: Add portal i18n keys to `test/setup.ts`**

Add a `portal` namespace to the test i18n resources object (after the last existing namespace):
```ts
portal: {
  verifyingTitle: 'Signing you in…',
  linkExpiredHeading: 'Link expired',
  linkExpiredBody: 'This link has expired or is invalid. Ask your care coordinator for a new one.',
  sessionExpiredHeading: 'Session expired',
  sessionExpiredBody: 'Your session has expired. Ask your care coordinator for a new link.',
  noSessionHeading: 'No active session',
  noSessionBody: 'No active session found. Ask your care coordinator for an access link.',
  signedOutHeading: "You've been signed out",
  signedOutBody: "You've been signed out. Use your original link or ask your care coordinator to send a new one.",
  accessRevokedHeading: 'Access removed',
  accessRevokedBody: 'Your portal access has been removed. Contact your care coordinator for more information.',
  signOut: 'Sign out',
  todayVisitHeading: "Today's Visit",
  upcomingHeading: 'Upcoming',
  lastVisitHeading: 'Last Visit',
  statusInProgress: '{{name}} is here now',
  statusScheduledOnTime: '{{name}} is scheduled for {{time}}',
  statusLate: 'Scheduled for {{time}} — not yet started. Contact your care coordinator.',
  statusCompleted: 'Visit completed at {{time}}',
  statusCancelled: "Today's visit was cancelled. Contact your care coordinator.",
  noVisitToday: 'No visit scheduled for today',
  noUpcomingVisits: 'No upcoming visits scheduled. Contact your care coordinator to confirm the schedule.',
  clockedIn: 'Clocked in',
  scheduledUntil: 'Scheduled until',
  lastVisitCompleted: 'Completed at {{time}} · {{duration}}',
  careServicesConcludedHeading: 'Care services concluded',
  careServicesConcludedBody: 'Care services for {{name}} have concluded. Please contact the agency for more information.',
  loadError: 'Unable to load.',
  tapToRetry: 'Tap to retry',
  inviteEmail: 'EMAIL ADDRESS',
  generateLink: 'Generate Link',
  copyLink: 'Copy',
  linkCopied: 'Copied!',
  inviteExpiry: 'Link expires {{date}} at {{time}} — valid for 72 hours',
  reInviteNote: 'A new link will be sent to this existing user',
  removeConfirmation: 'Remove {{name}}? Their access will be revoked on next page load. Any active session ends when they next visit the portal.',
  removeConfirm: 'Remove',
  neverLoggedIn: 'Never logged in',
  lastLogin: 'Last login',
  sendNewLink: 'Send New Link',
  addInvite: '+ Invite',
  portalAccessHeading: 'Family Portal Access',
  noPortalUsers: 'No family members have portal access yet.',
  cancel: 'Cancel',
  done: 'Done',
  remove: 'Remove',
  loading: 'Loading…',
},
```

- [ ] **Step 7: Run frontend tests to confirm baseline**

```bash
cd frontend && npm run test -- --run 2>&1 | tail -10
```
Expected: All existing tests pass.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/store/portalAuthStore.ts \
        frontend/public/locales/en/portal.json \
        frontend/src/i18n.ts \
        frontend/public/locales/en/clients.json \
        frontend/src/mock/data.ts \
        frontend/src/test/setup.ts
git commit -m "feat: portalAuthStore (Zustand persist), portal.json i18n, test setup sync"
```

---

## Task 15: portal.ts API and usePortalDashboard hook

**Files:**
- Create: `frontend/src/api/portal.ts`
- Create: `frontend/src/hooks/usePortalDashboard.ts`

- [ ] **Step 1: Create `portal.ts`**

Portal API calls use a dedicated Axios instance that attaches the portal JWT (not the admin JWT):

```ts
import axios from 'axios'
import { usePortalAuthStore } from '../store/portalAuthStore'

const BASE = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1'

// Separate Axios instance for portal — does NOT use the admin authStore interceptor.
const portalClient = axios.create({
  baseURL: BASE,
  headers: { 'Content-Type': 'application/json' },
})

portalClient.interceptors.request.use((config) => {
  const token = usePortalAuthStore.getState().token
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// Redirect to session_expired when the FAMILY_PORTAL JWT is rejected.
// usePortalAuthStore.getState() is safe to call outside React (Zustand pattern).
portalClient.interceptors.response.use(
  (r) => r,
  (error) => {
    if (error.response?.status === 401) {
      usePortalAuthStore.getState().logout()
      window.location.href = '/portal/verify?reason=session_expired'
    }
    return Promise.reject(error)
  },
)

// ── Types ──────────────────────────────────────────────────────────────────

export interface InviteResponse {
  inviteUrl: string
  expiresAt: string
}

export interface PortalVerifyResponse {
  jwt: string
  clientId: string
  agencyId: string
}

export interface CaregiverDto {
  name: string
  serviceType: string
}

export interface TodayVisitDto {
  shiftId: string
  scheduledStart: string
  scheduledEnd: string
  status: 'GREY' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'
  clockedInAt: string | null
  clockedOutAt: string | null
  caregiver: CaregiverDto | null
}

export interface UpcomingVisitDto {
  scheduledStart: string
  scheduledEnd: string
  caregiverName: string | null
}

export interface LastVisitDto {
  date: string
  clockedOutAt: string | null
  durationMinutes: number
  noteText: string | null
}

export interface PortalDashboardResponse {
  clientFirstName: string
  agencyTimezone: string
  todayVisit: TodayVisitDto | null
  upcomingVisits: UpcomingVisitDto[]
  lastVisit: LastVisitDto | null
}

// ── Admin-side calls (use admin apiClient from client.ts) ──────────────────

import { apiClient } from './client'

export async function inviteFamilyPortalUser(
  clientId: string,
  email: string,
): Promise<InviteResponse> {
  const res = await apiClient.post<InviteResponse>(
    `/clients/${clientId}/family-portal-users/invite`,
    { email },
  )
  return res.data
}

export async function removeFamilyPortalUser(
  clientId: string,
  fpuId: string,
): Promise<void> {
  await apiClient.delete(`/clients/${clientId}/family-portal-users/${fpuId}`)
}

// ── Portal-side calls (use portalClient with FAMILY_PORTAL JWT) ────────────

export async function verifyPortalToken(token: string): Promise<PortalVerifyResponse> {
  const res = await portalClient.post<PortalVerifyResponse>('/family/auth/verify', { token })
  return res.data
}

export async function getPortalDashboard(): Promise<PortalDashboardResponse> {
  const res = await portalClient.get<PortalDashboardResponse>('/family/portal/dashboard')
  return res.data
}
```

- [ ] **Step 2: Create `usePortalDashboard.ts`**

```ts
import { useQuery } from '@tanstack/react-query'
import { getPortalDashboard } from '../api/portal'
import { usePortalAuthStore } from '../store/portalAuthStore'

export function usePortalDashboard() {
  const clientId = usePortalAuthStore((s) => s.clientId)

  return useQuery({
    queryKey: ['portal-dashboard', clientId],
    queryFn: getPortalDashboard,
    retry: 2,
    // Do not show stale data silently — if cache is stale and network fails, show error state.
    staleTime: 60_000,        // 1 minute — fresh window
    gcTime: 60_000,           // purge cache after 1 minute to avoid stale display
    throwOnError: false,
    // Note: meta.onError was removed in React Query v5. 403 (access_revoked) and 410
    // (client_discharged) are handled in the component via useEffect watching isError/error.
  })
}
```

- [ ] **Step 3: Compile TypeScript**

```bash
cd frontend && npx tsc --noEmit 2>&1 | head -20
```
Expected: No errors.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/api/portal.ts \
        frontend/src/hooks/usePortalDashboard.ts
git commit -m "feat: portal.ts API module and usePortalDashboard hook"
```

---

## Task 16: PortalGuard and PortalLayout

**Files:**
- Create: `frontend/src/components/portal/PortalGuard.tsx`
- Create: `frontend/src/components/portal/PortalLayout.tsx`

- [ ] **Step 1: Create `PortalGuard.tsx`**

Parses the JWT `exp` claim client-side to detect expiry before making any API call:

```tsx
import { useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { usePortalAuthStore } from '../../store/portalAuthStore'

interface Props {
  children: React.ReactNode
}

function isJwtExpired(token: string): boolean {
  try {
    const base64Url = token.split('.')[1]
    // JWT uses base64url (RFC 4648 §5): replace URL-safe chars and restore padding
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/')
    const padded = base64 + '='.repeat((4 - base64.length % 4) % 4)
    const payload = JSON.parse(atob(padded))
    return payload.exp * 1000 < Date.now()
  } catch {
    return true
  }
}

export default function PortalGuard({ children }: Props) {
  const token = usePortalAuthStore((s) => s.token)
  const navigate = useNavigate()

  useEffect(() => {
    if (!token) {
      navigate('/portal/verify?reason=no_session', { replace: true })
    } else if (isJwtExpired(token)) {
      navigate('/portal/verify?reason=session_expired', { replace: true })
    }
  }, [token, navigate])

  if (!token || isJwtExpired(token)) return null
  return <>{children}</>
}
```

- [ ] **Step 2: Create `PortalLayout.tsx`**

```tsx
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { usePortalAuthStore } from '../../store/portalAuthStore'

interface Props {
  children: React.ReactNode
}

export default function PortalLayout({ children }: Props) {
  const { t } = useTranslation('portal')
  const navigate = useNavigate()
  const logout = usePortalAuthStore((s) => s.logout)

  function handleSignOut() {
    logout()
    navigate('/portal/verify?reason=signed_out', { replace: true })
  }

  return (
    <div className="min-h-screen bg-surface">
      {/* Portal header */}
      <div className="bg-white border-b border-border px-5 py-3 flex justify-between items-center">
        <div>
          <div className="text-[11px] text-text-secondary uppercase tracking-[.08em]">hcare</div>
        </div>
        {/* Sign out — minimum 44px touch target via py-3 padding */}
        <button
          onClick={handleSignOut}
          className="text-[12px] text-text-secondary py-3 px-2 min-h-[44px] flex items-center"
        >
          {t('signOut')}
        </button>
      </div>
      <main>{children}</main>
    </div>
  )
}
```

- [ ] **Step 3: Add portal routes to `App.tsx`**

Add these two routes as siblings to the admin `<Route element={<RequireAuth>...}>` block, before the closing `</Routes>`:

```tsx
import PortalGuard from '@/components/portal/PortalGuard'
import PortalLayout from '@/components/portal/PortalLayout'
import PortalVerifyPage from '@/pages/PortalVerifyPage'
import PortalDashboardPage from '@/pages/PortalDashboardPage'

// Inside <Routes>:
<Route path="/portal/verify" element={<PortalVerifyPage />} />
<Route
  path="/portal/dashboard"
  element={
    <PortalGuard>
      <PortalLayout>
        <PortalDashboardPage />
      </PortalLayout>
    </PortalGuard>
  }
/>
```

The pages `PortalVerifyPage` and `PortalDashboardPage` are created in the next tasks — add the imports now but create placeholder files first if needed to keep the compile happy.

- [ ] **Step 4: Create placeholder page files (so App.tsx compiles)**

`frontend/src/pages/PortalVerifyPage.tsx`:
```tsx
export default function PortalVerifyPage() { return <div>verify</div> }
```

`frontend/src/pages/PortalDashboardPage.tsx`:
```tsx
export default function PortalDashboardPage() { return <div>dashboard</div> }
```

- [ ] **Step 5: Write failing tests for `PortalGuard`**

```tsx
// frontend/src/components/portal/PortalGuard.test.tsx
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, it, expect, beforeEach } from 'vitest'
import PortalGuard from './PortalGuard'
import { usePortalAuthStore } from '../../store/portalAuthStore'

function makeJwt(payload: object): string {
  // Build a real base64url-encoded JWT (header.payload.sig).
  // btoa produces standard base64; convert to base64url by swapping chars and stripping padding.
  const encode = (obj: object) =>
    btoa(JSON.stringify(obj)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
  return `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode(payload)}.sig`
}

function renderGuard() {
  return render(
    <MemoryRouter initialEntries={['/portal/dashboard']}>
      <Routes>
        <Route
          path="/portal/dashboard"
          element={
            <PortalGuard>
              <div>protected content</div>
            </PortalGuard>
          }
        />
        <Route path="/portal/verify" element={<div>verify page</div>} />
      </Routes>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  usePortalAuthStore.getState().logout()
})

describe('PortalGuard', () => {
  it('redirects to no_session when no token is present', () => {
    renderGuard()
    expect(screen.getByText('verify page')).toBeInTheDocument()
  })

  it('renders children when token is valid and not expired', () => {
    // Use a real base64url-encoded JWT with a UUID clientId (contains '-' chars) to verify
    // the base64url normalization in isJwtExpired works correctly. A naive atob() call
    // would throw a DOMException on the '-' characters, causing isJwtExpired to return true
    // (expired) and redirect — this test proves the fix is effective.
    const futureExp = Math.floor(Date.now() / 1000) + 3600
    const token = makeJwt({
      exp: futureExp,
      clientId: 'a1b2c3d4-e5f6-7890-abcd-ef1234567890', // UUID with '-' chars
      agencyId: 'f0e1d2c3-b4a5-6789-fedc-ba9876543210',
    })
    usePortalAuthStore.getState().login(token, 'a1b2c3d4-e5f6-7890-abcd-ef1234567890', 'f0e1d2c3-b4a5-6789-fedc-ba9876543210')
    renderGuard()
    expect(screen.getByText('protected content')).toBeInTheDocument()
  })

  it('redirects to session_expired when token exp is in the past', () => {
    const pastExp = Math.floor(Date.now() / 1000) - 60
    const token = makeJwt({
      exp: pastExp,
      clientId: 'a1b2c3d4-e5f6-7890-abcd-ef1234567890',
      agencyId: 'f0e1d2c3-b4a5-6789-fedc-ba9876543210',
    })
    usePortalAuthStore.getState().login(token, 'a1b2c3d4-e5f6-7890-abcd-ef1234567890', 'f0e1d2c3-b4a5-6789-fedc-ba9876543210')
    renderGuard()
    expect(screen.getByText('verify page')).toBeInTheDocument()
  })
})
```

- [ ] **Step 6: Compile TypeScript**

```bash
cd frontend && npx tsc --noEmit 2>&1 | head -20
```

- [ ] **Step 7: Run tests**

```bash
cd frontend && npm run test -- PortalGuard
```

Expect 3 passing tests. The "renders children when token is valid" test confirms that `isJwtExpired` correctly handles base64url-encoded tokens with `-` characters (UUIDs in claims) — if the `atob` call were not normalized it would throw and return `true`, causing this test to fail.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/components/portal/ \
        frontend/src/pages/PortalVerifyPage.tsx \
        frontend/src/pages/PortalDashboardPage.tsx \
        frontend/src/App.tsx
git commit -m "feat: PortalGuard, PortalLayout, portal routes in App.tsx"
```

---

## Task 17: PortalVerifyPage

**Files:**
- Modify: `frontend/src/pages/PortalVerifyPage.tsx` (replace placeholder)
- Create: `frontend/src/pages/PortalVerifyPage.test.tsx`

- [ ] **Step 1: Write the failing tests**

```tsx
// frontend/src/pages/PortalVerifyPage.test.tsx
import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { vi, describe, it, expect, beforeEach } from 'vitest'
import PortalVerifyPage from './PortalVerifyPage'
import * as portalApi from '../api/portal'
import { usePortalAuthStore } from '../store/portalAuthStore'

vi.mock('../api/portal')

const mockVerify = vi.mocked(portalApi.verifyPortalToken)

function renderVerify(search = '') {
  return render(
    <MemoryRouter initialEntries={[`/portal/verify${search}`]}>
      <Routes>
        <Route path="/portal/verify" element={<PortalVerifyPage />} />
        <Route path="/portal/dashboard" element={<div>dashboard</div>} />
      </Routes>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  usePortalAuthStore.getState().logout()
})

describe('PortalVerifyPage', () => {
  it('auto-submits token on mount and redirects to dashboard on success', async () => {
    mockVerify.mockResolvedValue({ jwt: 'test.jwt.token', clientId: 'c1', agencyId: 'a1' })
    renderVerify('?token=abc123')
    expect(screen.getByText('Signing you in…')).toBeInTheDocument()
    await waitFor(() => expect(screen.getByText('dashboard')).toBeInTheDocument())
    expect(usePortalAuthStore.getState().token).toBe('test.jwt.token')
  })

  it('shows aria-live region while verifying', async () => {
    mockVerify.mockResolvedValue({ jwt: 'test.jwt', clientId: 'c1', agencyId: 'a1' })
    renderVerify('?token=abc123')
    const liveRegion = document.querySelector('[role="status"]')
    expect(liveRegion).toBeInTheDocument()
  })

  it('shows link expired state on 400 error', async () => {
    mockVerify.mockRejectedValue({ response: { status: 400 } })
    renderVerify('?token=badtoken')
    await waitFor(() => expect(screen.getByText('Link expired')).toBeInTheDocument())
    expect(screen.getByText(/expired or is invalid/)).toBeInTheDocument()
  })

  it('shows session expired state for ?reason=session_expired', () => {
    renderVerify('?reason=session_expired')
    expect(screen.getByText('Session expired')).toBeInTheDocument()
    expect(screen.getByText(/Your session has expired/)).toBeInTheDocument()
  })

  it('shows no active session state for ?reason=no_session', () => {
    renderVerify('?reason=no_session')
    expect(screen.getByText('No active session')).toBeInTheDocument()
  })

  it('shows signed out state for ?reason=signed_out', () => {
    renderVerify('?reason=signed_out')
    expect(screen.getByText("You've been signed out")).toBeInTheDocument()
    expect(screen.getByText(/Use your original link/)).toBeInTheDocument()
  })

  it('shows access revoked state for ?reason=access_revoked', () => {
    renderVerify('?reason=access_revoked')
    expect(screen.getByText('Access removed')).toBeInTheDocument()
    expect(screen.getByText(/portal access has been removed/)).toBeInTheDocument()
  })

  it('strips ?reason= from URL after reading it', () => {
    const replaceState = vi.spyOn(window.history, 'replaceState')
    renderVerify('?reason=signed_out')
    expect(replaceState).toHaveBeenCalledWith(null, '', '/portal/verify')
  })
})
```

- [ ] **Step 2: Run tests — expect failures**

```bash
cd frontend && npm run test -- --run PortalVerifyPage.test 2>&1 | tail -10
```
Expected: Multiple failures — page is a placeholder.

- [ ] **Step 3: Implement `PortalVerifyPage.tsx`**

```tsx
import { useState, useEffect } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { verifyPortalToken } from '../api/portal'
import { usePortalAuthStore } from '../store/portalAuthStore'

type VerifyState =
  | 'verifying'
  | 'token_invalid'
  | 'session_expired'
  | 'no_session'
  | 'signed_out'
  | 'access_revoked'

const REASON_MAP: Record<string, VerifyState> = {
  session_expired: 'session_expired',
  no_session: 'no_session',
  signed_out: 'signed_out',
  access_revoked: 'access_revoked',
}

export default function PortalVerifyPage() {
  const { t } = useTranslation('portal')
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const login = usePortalAuthStore((s) => s.login)

  const token = searchParams.get('token')
  const reason = searchParams.get('reason')

  // Determine initial state
  let initialState: VerifyState = 'verifying'
  if (reason && REASON_MAP[reason]) {
    initialState = REASON_MAP[reason]
  } else if (!token && !reason) {
    initialState = 'no_session'
  }

  const [displayState, setDisplayState] = useState<VerifyState>(initialState)

  // Strip ?reason= or any query param from URL bar after reading
  useEffect(() => {
    if (reason || (!token && !reason)) {
      window.history.replaceState(null, '', '/portal/verify')
    }
  }, [reason, token])

  // Auto-submit if we have a token
  useEffect(() => {
    if (!token) return
    verifyPortalToken(token)
      .then((res) => {
        login(res.jwt, res.clientId, res.agencyId)
        navigate('/portal/dashboard', { replace: true })
      })
      .catch(() => {
        setDisplayState('token_invalid')
      })
  }, [token]) // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <div className="min-h-screen bg-surface flex items-center justify-center p-4">
      <div className="bg-white border border-border p-8 w-full max-w-sm">
        {displayState === 'verifying' ? (
          <div role="status" aria-live="polite" className="text-center">
            <div className="text-[14px] text-text-primary">{t('verifyingTitle')}</div>
          </div>
        ) : (
          <VerifyErrorCard state={displayState} t={t} />
        )}
      </div>
    </div>
  )
}

function VerifyErrorCard({
  state,
  t,
}: {
  state: Exclude<VerifyState, 'verifying'>
  t: (key: string) => string
}) {
  const headingKey = {
    token_invalid: 'linkExpiredHeading',
    session_expired: 'sessionExpiredHeading',
    no_session: 'noSessionHeading',
    signed_out: 'signedOutHeading',
    access_revoked: 'accessRevokedHeading',
  }[state]

  const bodyKey = {
    token_invalid: 'linkExpiredBody',
    session_expired: 'sessionExpiredBody',
    no_session: 'noSessionBody',
    signed_out: 'signedOutBody',
    access_revoked: 'accessRevokedBody',
  }[state]

  return (
    <div>
      <h1 className="text-[16px] font-bold text-text-primary mb-2">{t(headingKey)}</h1>
      <p className="text-[14px] text-text-primary leading-relaxed">{t(bodyKey)}</p>
    </div>
  )
}
```

- [ ] **Step 4: Run the tests — expect pass**

```bash
cd frontend && npm run test -- --run PortalVerifyPage.test 2>&1 | tail -10
```
Expected: All 7 tests pass.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/pages/PortalVerifyPage.tsx \
        frontend/src/pages/PortalVerifyPage.test.tsx
git commit -m "feat: PortalVerifyPage — 6 states, aria-live, URL cleanup"
```

---

## Task 18: PortalDashboardPage

**Files:**
- Modify: `frontend/src/pages/PortalDashboardPage.tsx` (replace placeholder)
- Create: `frontend/src/pages/PortalDashboardPage.test.tsx`

- [ ] **Step 1: Write the failing tests**

```tsx
// frontend/src/pages/PortalDashboardPage.test.tsx
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { vi, describe, it, expect, beforeEach } from 'vitest'
import PortalDashboardPage from './PortalDashboardPage'
import * as portalApi from '../api/portal'
import { usePortalAuthStore } from '../store/portalAuthStore'
import type { PortalDashboardResponse } from '../api/portal'

vi.mock('../api/portal')
const mockGetDashboard = vi.mocked(portalApi.getPortalDashboard)

const BASE: PortalDashboardResponse = {
  clientFirstName: 'Margaret',
  agencyTimezone: 'America/New_York',
  todayVisit: null,
  upcomingVisits: [],
  lastVisit: null,
}

function renderDashboard() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={['/portal/dashboard']}>
        <Routes>
          <Route path="/portal/dashboard" element={<PortalDashboardPage />} />
          <Route path="/portal/verify" element={<div>verify page</div>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  usePortalAuthStore.setState({ token: 'fake.jwt', clientId: 'c1', agencyId: 'a1' })
})

describe('PortalDashboardPage', () => {
  it('renders no visit today when todayVisit is null', async () => {
    mockGetDashboard.mockResolvedValue(BASE)
    renderDashboard()
    await waitFor(() => expect(screen.getByText("Margaret's Care")).toBeInTheDocument())
    expect(screen.getByText('No visit scheduled for today')).toBeInTheDocument()
  })

  it('renders IN_PROGRESS state', async () => {
    mockGetDashboard.mockResolvedValue({
      ...BASE,
      todayVisit: {
        shiftId: 's1', scheduledStart: '2026-04-08T09:00:00',
        scheduledEnd: '2026-04-08T11:00:00', status: 'IN_PROGRESS',
        clockedInAt: '2026-04-08T09:04:00', clockedOutAt: null,
        caregiver: { name: 'Maria Gonzalez', serviceType: 'Personal Care Aide' },
      },
    })
    renderDashboard()
    await waitFor(() => expect(screen.getByText(/Maria Gonzalez is here now/i)).toBeInTheDocument())
    expect(screen.getByText('Maria Gonzalez')).toBeInTheDocument()
  })

  it('renders GREY on-time state', async () => {
    mockGetDashboard.mockResolvedValue({
      ...BASE,
      todayVisit: {
        shiftId: 's1', scheduledStart: '2026-04-08T13:00:00',
        scheduledEnd: '2026-04-08T15:00:00', status: 'GREY',
        clockedInAt: null, clockedOutAt: null,
        caregiver: { name: 'Maria Gonzalez', serviceType: 'Personal Care Aide' },
      },
    })
    renderDashboard()
    await waitFor(() => expect(screen.getByText(/is scheduled for/i)).toBeInTheDocument())
  })

  it('renders COMPLETED with checkmark and text-text-primary', async () => {
    mockGetDashboard.mockResolvedValue({
      ...BASE,
      todayVisit: {
        shiftId: 's1', scheduledStart: '2026-04-08T09:00:00',
        scheduledEnd: '2026-04-08T11:00:00', status: 'COMPLETED',
        clockedInAt: '2026-04-08T09:04:00', clockedOutAt: '2026-04-08T11:03:00',
        caregiver: { name: 'Maria Gonzalez', serviceType: 'Personal Care Aide' },
      },
    })
    renderDashboard()
    await waitFor(() => expect(screen.getByText(/Visit completed at/)).toBeInTheDocument())
    // Checkmark icon should be present
    expect(document.querySelector('[data-testid="checkmark-icon"]')).toBeInTheDocument()
  })

  it('renders CANCELLED without caregiver card', async () => {
    mockGetDashboard.mockResolvedValue({
      ...BASE,
      todayVisit: {
        shiftId: 's1', scheduledStart: '2026-04-08T09:00:00',
        scheduledEnd: '2026-04-08T11:00:00', status: 'CANCELLED',
        clockedInAt: null, clockedOutAt: null,
        caregiver: null,
      },
    })
    renderDashboard()
    await waitFor(() => expect(screen.getByText(/Today's visit was cancelled/)).toBeInTheDocument())
    expect(screen.queryByText('Maria Gonzalez')).not.toBeInTheDocument()
  })

  it('renders late GREY state with clock icon', async () => {
    // Simulate a visit scheduled 30 min ago (past scheduledStart + 15 min threshold)
    const now = new Date()
    const thirtyMinAgo = new Date(now.getTime() - 30 * 60 * 1000).toISOString().replace('Z', '')
    const twentyMinAgo = new Date(now.getTime() + 90 * 60 * 1000).toISOString().replace('Z', '')
    mockGetDashboard.mockResolvedValue({
      ...BASE,
      todayVisit: {
        shiftId: 's1', scheduledStart: thirtyMinAgo,
        scheduledEnd: twentyMinAgo, status: 'GREY',
        clockedInAt: null, clockedOutAt: null,
        caregiver: { name: 'Maria Gonzalez', serviceType: 'Personal Care Aide' },
      },
    })
    renderDashboard()
    await waitFor(() => expect(screen.getByText(/not yet started/)).toBeInTheDocument())
    // Clock icon for late state (not color alone)
    expect(document.querySelector('[data-testid="clock-icon"]')).toBeInTheDocument()
  })

  it('shows upcoming visits capped at 3', async () => {
    mockGetDashboard.mockResolvedValue({
      ...BASE,
      upcomingVisits: [
        { scheduledStart: '2026-04-09T09:00:00', scheduledEnd: '2026-04-09T11:00:00', caregiverName: 'Maria' },
        { scheduledStart: '2026-04-10T09:00:00', scheduledEnd: '2026-04-10T11:00:00', caregiverName: 'Maria' },
        { scheduledStart: '2026-04-11T09:00:00', scheduledEnd: '2026-04-11T11:00:00', caregiverName: 'Maria' },
        { scheduledStart: '2026-04-12T09:00:00', scheduledEnd: '2026-04-12T11:00:00', caregiverName: 'Maria' },
        { scheduledStart: '2026-04-13T09:00:00', scheduledEnd: '2026-04-13T11:00:00', caregiverName: 'Maria' },
      ],
    })
    renderDashboard()
    // Backend returned 5 items but component caps display at 3
    await waitFor(() => expect(screen.getAllByText(/Maria/).length).toBe(3))
  })

  it('shows empty upcoming state when upcomingVisits is empty', async () => {
    mockGetDashboard.mockResolvedValue(BASE)
    renderDashboard()
    await waitFor(() =>
      expect(screen.getByText(/No upcoming visits scheduled/)).toBeInTheDocument())
  })

  it('renders lastVisit with note', async () => {
    mockGetDashboard.mockResolvedValue({
      ...BASE,
      lastVisit: {
        date: '2026-04-07',
        clockedOutAt: '2026-04-07T11:03:00',
        durationMinutes: 119,
        noteText: 'Margaret was in good spirits.',
      },
    })
    renderDashboard()
    await waitFor(() => expect(screen.getByText(/Margaret was in good spirits/)).toBeInTheDocument())
    expect(screen.getByText(/Completed at/)).toBeInTheDocument()
  })

  it('renders lastVisit without note — completion fact always shown', async () => {
    mockGetDashboard.mockResolvedValue({
      ...BASE,
      lastVisit: { date: '2026-04-07', clockedOutAt: '2026-04-07T11:03:00', durationMinutes: 119, noteText: null },
    })
    renderDashboard()
    await waitFor(() => expect(screen.getByText(/Completed at/)).toBeInTheDocument())
    // Note section absent
    expect(screen.queryByText('Margaret was in good spirits.')).not.toBeInTheDocument()
  })

  it('omits lastVisit section when lastVisit is null', async () => {
    mockGetDashboard.mockResolvedValue(BASE)
    renderDashboard()
    await waitFor(() => expect(screen.getByText("Margaret's Care")).toBeInTheDocument())
    expect(screen.queryByText(/Last Visit/)).not.toBeInTheDocument()
  })

  it('shows timezone label on times', async () => {
    mockGetDashboard.mockResolvedValue({
      ...BASE,
      todayVisit: {
        shiftId: 's1', scheduledStart: '2026-04-08T09:00:00',
        scheduledEnd: '2026-04-08T11:00:00', status: 'IN_PROGRESS',
        clockedInAt: '2026-04-08T09:04:00', clockedOutAt: null,
        caregiver: { name: 'Maria Gonzalez', serviceType: 'Personal Care Aide' },
      },
    })
    renderDashboard()
    // Times should include timezone abbreviation (ET/EDT/EST)
    await waitFor(() => expect(screen.getByText(/AM ET|AM EDT|AM EST/)).toBeInTheDocument())
  })

  it('sign out clears store and navigates to ?reason=signed_out', async () => {
    mockGetDashboard.mockResolvedValue(BASE)
    renderDashboard()
    await waitFor(() => expect(screen.getByText("Margaret's Care")).toBeInTheDocument())
    await userEvent.click(screen.getByText('Sign out'))
    expect(usePortalAuthStore.getState().token).toBeNull()
    expect(screen.getByText('verify page')).toBeInTheDocument()
  })

  it('shows care concluded screen on 410 response', async () => {
    mockGetDashboard.mockRejectedValue({ response: { status: 410 } })
    renderDashboard()
    await waitFor(() =>
      expect(screen.getByText('Care services concluded')).toBeInTheDocument())
    // Session not cleared on 410
    expect(usePortalAuthStore.getState().token).not.toBeNull()
  })

  it('clears session and redirects to access_revoked on 403 response', async () => {
    mockGetDashboard.mockRejectedValue({ response: { status: 403 } })
    renderDashboard()
    await waitFor(() => expect(screen.getByText('verify page')).toBeInTheDocument())
    expect(usePortalAuthStore.getState().token).toBeNull()
  })
})
```

- [ ] **Step 2: Run tests — expect failures**

```bash
cd frontend && npm run test -- --run PortalDashboardPage.test 2>&1 | tail -10
```

- [ ] **Step 3: Implement `PortalDashboardPage.tsx`**

```tsx
import { useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { usePortalAuthStore } from '../store/portalAuthStore'
import { usePortalDashboard } from '../hooks/usePortalDashboard'
import type { TodayVisitDto, UpcomingVisitDto, LastVisitDto } from '../api/portal'

/** Formats a UTC ISO timestamp into a human-readable time in the given IANA timezone,
 *  with a short timezone abbreviation (e.g., "9:04 AM EDT"). */
function formatTime(utcIso: string, tz: string): string {
  const d = new Date(utcIso + (utcIso.includes('Z') ? '' : 'Z'))
  return d.toLocaleTimeString('en-US', {
    hour: 'numeric',
    minute: '2-digit',
    timeZone: tz,
    timeZoneName: 'short',
  })
}

function formatDate(utcIso: string, tz: string): string {
  const d = new Date(utcIso + (utcIso.includes('Z') ? '' : 'Z'))
  return d.toLocaleDateString('en-US', {
    weekday: 'short', month: 'short', day: 'numeric', timeZone: tz,
  })
}

function formatDuration(minutes: number): string {
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  if (h === 0) return `${m} min`
  if (m === 0) return `${h} hr`
  return `${h} hr ${m} min`
}

function isLate(visit: TodayVisitDto): boolean {
  if (visit.status !== 'GREY' || visit.clockedInAt) return false
  const scheduled = new Date(visit.scheduledStart + (visit.scheduledStart.includes('Z') ? '' : 'Z'))
  return Date.now() > scheduled.getTime() + 15 * 60 * 1000
}

export default function PortalDashboardPage() {
  const { t } = useTranslation('portal')
  const navigate = useNavigate()
  const logout = usePortalAuthStore((s) => s.logout)
  const { data, isLoading, isError, error, refetch } = usePortalDashboard()

  // Handle 403 PORTAL_ACCESS_REVOKED — React Query v5 removed meta.onError, so we handle
  // side-effects from errors here in the component via useEffect.
  useEffect(() => {
    if (!isError || !error) return
    const status = (error as { response?: { status?: number } })?.response?.status
    if (status === 403) {
      logout()
      navigate('/portal/verify?reason=access_revoked', { replace: true })
    }
  }, [isError, error, logout, navigate])

  const status410 = (error as { response?: { status?: number } })?.response?.status === 410

  if (isLoading) {
    return (
      <div role="status" aria-live="polite" className="p-6 text-center text-[13px] text-text-secondary">
        {t('loading')}
      </div>
    )
  }

  if (status410) {
    return (
      <div className="p-6 text-center">
        <h1 className="text-[16px] font-bold text-text-primary mb-2">
          {t('careServicesConcludedHeading')}
        </h1>
        <p className="text-[14px] text-text-primary">
          {t('careServicesConcludedBody', { name: '' })}
        </p>
      </div>
    )
  }

  if (isError) {
    return (
      <div className="p-6 text-center">
        <p className="text-[13px] text-text-primary mb-3">{t('loadError')}</p>
        <button
          onClick={() => refetch()}
          className="text-[13px] text-text-primary border border-border px-4 py-2 min-h-[44px]"
        >
          {t('tapToRetry')}
        </button>
      </div>
    )
  }

  if (!data) return null

  const { clientFirstName, agencyTimezone: tz, todayVisit, upcomingVisits, lastVisit } = data
  const today = new Date().toLocaleDateString('en-US', {
    weekday: 'short', month: 'short', day: 'numeric', timeZone: tz,
  })

  return (
    <div className="bg-surface min-h-screen">
      {/* Page header */}
      <div className="bg-white border-b border-border px-5 py-4 flex justify-between items-center">
        <div>
          <div className="text-[11px] text-text-secondary uppercase tracking-[.08em]">hcare</div>
          <div className="text-[18px] font-bold text-text-primary mt-0.5">
            {clientFirstName}'s Care
          </div>
        </div>
        <div className="text-[12px] text-text-secondary">{today}</div>
      </div>

      <div className="p-4 space-y-4 max-w-md mx-auto">
        {/* Today's Visit */}
        <div className="bg-white border border-border p-4">
          <div className="text-[9px] font-bold tracking-[.1em] uppercase text-text-secondary mb-3">
            {t('todayVisitHeading')}
          </div>
          <TodayVisitCard visit={todayVisit} tz={tz} t={t} />
        </div>

        {/* Upcoming visits */}
        <div>
          <div className="text-[9px] font-bold tracking-[.1em] uppercase text-text-secondary mb-2">
            {t('upcomingHeading')}
          </div>
          {upcomingVisits.length === 0 ? (
            <p className="text-[13px] text-text-secondary">{t('noUpcomingVisits')}</p>
          ) : (
            <div className="flex flex-col gap-px">
              {upcomingVisits.slice(0, 3).map((v, i) => (
                <UpcomingRow key={i} visit={v} tz={tz} />
              ))}
            </div>
          )}
        </div>

        {/* Last visit */}
        {lastVisit && <LastVisitCard visit={lastVisit} tz={tz} t={t} />}
      </div>
    </div>
  )
}

function TodayVisitCard({
  visit,
  tz,
  t,
}: {
  visit: TodayVisitDto | null
  tz: string
  t: (k: string, opts?: Record<string, string>) => string
}) {
  if (!visit) {
    return (
      <p className="text-[13px] text-text-secondary text-center py-2">{t('noVisitToday')}</p>
    )
  }

  const late = isLate(visit)
  const scheduledTime = formatTime(visit.scheduledStart, tz)

  return (
    <div>
      <StatusPill visit={visit} scheduledTime={scheduledTime} late={late} t={t} tz={tz} />
      {/* Caregiver card — hidden for CANCELLED */}
      {visit.caregiver && visit.status !== 'CANCELLED' && (
        <div className="flex items-center gap-3 mt-3">
          <div className="w-11 h-11 rounded-full bg-blue flex items-center justify-center text-[16px] font-bold text-white flex-shrink-0">
            {visit.caregiver.name[0]}
          </div>
          <div>
            <div className="text-[15px] font-bold text-text-primary">{visit.caregiver.name}</div>
            <div className="text-[13px] text-text-secondary">{visit.caregiver.serviceType}</div>
          </div>
        </div>
      )}
      {/* Times for IN_PROGRESS */}
      {visit.status === 'IN_PROGRESS' && visit.clockedInAt && (
        <div className="flex gap-5 mt-3">
          <div>
            <div className="text-[9px] font-bold uppercase tracking-[.08em] text-text-secondary">{t('clockedIn')}</div>
            <div className="text-[14px] font-semibold text-text-primary mt-0.5">
              {formatTime(visit.clockedInAt, tz)}
            </div>
          </div>
          <div>
            <div className="text-[9px] font-bold uppercase tracking-[.08em] text-text-secondary">{t('scheduledUntil')}</div>
            <div className="text-[14px] font-semibold text-text-primary mt-0.5">
              {formatTime(visit.scheduledEnd, tz)}
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

function StatusPill({
  visit,
  scheduledTime,
  late,
  t,
  tz,
}: {
  visit: TodayVisitDto
  scheduledTime: string
  late: boolean
  t: (k: string, opts?: Record<string, string>) => string
  tz: string
}) {
  const caregiverName = visit.caregiver?.name ?? ''

  if (visit.status === 'IN_PROGRESS') {
    return (
      <div className="inline-flex items-center gap-1.5 bg-green-50 border border-green-200 px-2.5 py-1.5 mb-3">
        <div className="w-1.5 h-1.5 rounded-full bg-green-500" />
        <span className="text-[12px] font-bold text-green-700">
          {t('statusInProgress', { name: caregiverName })}
        </span>
      </div>
    )
  }

  if (visit.status === 'GREY' && late) {
    return (
      <div className="inline-flex items-center gap-1.5 bg-amber-50 border border-amber-200 px-2.5 py-1.5 mb-3">
        <span data-testid="clock-icon" className="text-amber-600 text-[12px]">⏰</span>
        <span className="text-[12px] font-bold text-amber-700">
          {t('statusLate', { time: scheduledTime })}
        </span>
      </div>
    )
  }

  if (visit.status === 'GREY') {
    return (
      <div className="inline-flex items-center gap-1.5 bg-surface border border-border px-2.5 py-1.5 mb-3">
        <div className="w-1.5 h-1.5 rounded-full bg-slate-400" />
        <span className="text-[12px] font-bold text-text-secondary">
          {t('statusScheduledOnTime', { name: caregiverName, time: scheduledTime })}
        </span>
      </div>
    )
  }

  if (visit.status === 'COMPLETED') {
    const completedTime = visit.clockedOutAt ? formatTime(visit.clockedOutAt, tz) : scheduledTime
    return (
      <div className="inline-flex items-center gap-1.5 bg-surface border border-border px-2.5 py-1.5 mb-3">
        <span data-testid="checkmark-icon" className="text-[12px]">✓</span>
        <span className="text-[12px] font-bold text-text-primary">
          {t('statusCompleted', { time: completedTime })}
        </span>
      </div>
    )
  }

  if (visit.status === 'CANCELLED') {
    return (
      <div className="inline-flex items-center gap-1.5 bg-red-50 border border-red-200 px-2.5 py-1.5 mb-3">
        <span className="text-[12px] font-bold text-red-700">{t('statusCancelled')}</span>
      </div>
    )
  }

  return null
}

function UpcomingRow({ visit, tz }: { visit: UpcomingVisitDto; tz: string }) {
  return (
    <div className="bg-white border border-border px-3 py-2.5 flex justify-between items-center">
      <div className="text-[13px] font-semibold text-text-primary">
        {formatDate(visit.scheduledStart, tz)}
      </div>
      <div className="text-[12px] text-text-secondary">
        {formatTime(visit.scheduledStart, tz)} – {formatTime(visit.scheduledEnd, tz)}
        {visit.caregiverName ? ` · ${visit.caregiverName}` : ''}
      </div>
    </div>
  )
}

function LastVisitCard({
  visit,
  tz,
  t,
}: {
  visit: LastVisitDto
  tz: string
  t: (k: string, opts?: Record<string, string>) => string
}) {
  const dateLabel = new Date(visit.date + 'T00:00:00Z').toLocaleDateString('en-US', {
    month: 'short', day: 'numeric', timeZone: tz,
  })
  const completedTime = visit.clockedOutAt ? formatTime(visit.clockedOutAt, tz) : '—'
  const duration = formatDuration(visit.durationMinutes)

  return (
    <div>
      <div className="text-[9px] font-bold tracking-[.1em] uppercase text-text-secondary mb-2">
        {t('lastVisitHeading')} ({dateLabel})
      </div>
      <div className="bg-white border border-border p-3">
        <div className="text-[13px] text-text-primary">
          {t('lastVisitCompleted', { time: completedTime, duration })}
        </div>
        {visit.noteText && (
          <div className="text-[13px] text-text-primary leading-relaxed mt-2">
            "{visit.noteText}"
          </div>
        )}
      </div>
    </div>
  )
}
```

- [ ] **Step 4: Run tests — expect pass**

```bash
cd frontend && npm run test -- --run PortalDashboardPage.test 2>&1 | tail -10
```
Expected: All 14 tests pass.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/pages/PortalDashboardPage.tsx \
        frontend/src/pages/PortalDashboardPage.test.tsx
git commit -m "feat: PortalDashboardPage — 6 visit states, timezone display, lastVisit, error handling"
```

---

## Task 19: FamilyPortalTab

**Files:**
- Create: `frontend/src/components/clients/FamilyPortalTab.tsx`
- Create: `frontend/src/components/clients/FamilyPortalTab.test.tsx`
- Modify: `frontend/src/components/clients/ClientDetailPanel.tsx`

- [ ] **Step 1: Write the failing tests**

```tsx
// frontend/src/components/clients/FamilyPortalTab.test.tsx
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { vi, describe, it, expect, beforeEach } from 'vitest'
import FamilyPortalTab from './FamilyPortalTab'
import * as portalApi from '../../api/portal'
import * as clientsApi from '../../api/clients'
import { IDS } from '../../mock/data'

vi.mock('../../api/portal')
vi.mock('../../api/clients')

const mockListFpu = vi.mocked(clientsApi.listFamilyPortalUsers)
const mockInvite = vi.mocked(portalApi.inviteFamilyPortalUser)
const mockRemove = vi.mocked(portalApi.removeFamilyPortalUser)

const FPU_LIST = [
  {
    id: IDS.fpUser1,
    email: 'alice@example.com',
    name: 'Alice',
    lastLoginAt: '2026-04-01T10:00:00',
    clientId: IDS.client1,
    agencyId: IDS.agency,
  },
  {
    id: IDS.fpUser2,
    email: 'bob@example.com',
    name: 'Bob',
    lastLoginAt: null,
    clientId: IDS.client1,
    agencyId: IDS.agency,
  },
]

function renderTab() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <FamilyPortalTab clientId={IDS.client1} />
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  mockListFpu.mockResolvedValue({ content: FPU_LIST, totalElements: 2, totalPages: 1, number: 0, size: 25 })
})

describe('FamilyPortalTab', () => {
  it('renders user list with "Never logged in" for null lastLoginAt', async () => {
    renderTab()
    await waitFor(() => expect(screen.getByText('alice@example.com')).toBeInTheDocument())
    expect(screen.getByText('bob@example.com')).toBeInTheDocument()
    expect(screen.getByText('Never logged in')).toBeInTheDocument()
  })

  it('opens invite form on "+ Invite" click', async () => {
    renderTab()
    await waitFor(() => screen.getByText('+ Invite'))
    await userEvent.click(screen.getByText('+ Invite'))
    expect(screen.getByPlaceholderText(/email/i)).toBeInTheDocument()
    expect(screen.getByText('Generate Link')).toBeInTheDocument()
  })

  it('pre-fills email when opened from "Send New Link"', async () => {
    renderTab()
    await waitFor(() => screen.getAllByText('Send New Link'))
    await userEvent.click(screen.getAllByText('Send New Link')[0])
    const input = screen.getByDisplayValue('alice@example.com')
    expect(input).toBeInTheDocument()
  })

  it('shows re-invite note immediately when form opens from Send New Link', async () => {
    renderTab()
    await waitFor(() => screen.getAllByText('Send New Link'))
    await userEvent.click(screen.getAllByText('Send New Link')[0])
    expect(screen.getByText('A new link will be sent to this existing user')).toBeInTheDocument()
  })

  it('shows invite URL and copy button after successful generate', async () => {
    mockInvite.mockResolvedValue({
      inviteUrl: 'http://localhost:5173/portal/verify?token=abc123',
      expiresAt: '2026-04-11T14:34:00Z',
    })
    renderTab()
    await waitFor(() => screen.getByText('+ Invite'))
    await userEvent.click(screen.getByText('+ Invite'))
    await userEvent.type(screen.getByPlaceholderText(/email/i), 'new@example.com')
    await userEvent.click(screen.getByText('Generate Link'))
    await waitFor(() =>
      expect(screen.getByText(/portal\/verify\?token=abc123/)).toBeInTheDocument())
    expect(screen.getByText('Copy')).toBeInTheDocument()
  })

  it('shows expiry note after generation', async () => {
    mockInvite.mockResolvedValue({
      inviteUrl: 'http://localhost:5173/portal/verify?token=abc',
      expiresAt: '2026-04-11T14:34:00Z',
    })
    renderTab()
    await waitFor(() => screen.getByText('+ Invite'))
    await userEvent.click(screen.getByText('+ Invite'))
    await userEvent.type(screen.getByPlaceholderText(/email/i), 'exp@example.com')
    await userEvent.click(screen.getByText('Generate Link'))
    await waitFor(() => expect(screen.getByText(/Link expires/)).toBeInTheDocument())
  })

  it('shows remove confirmation with correct copy on Remove click', async () => {
    renderTab()
    await waitFor(() => screen.getAllByText('Remove'))
    await userEvent.click(screen.getAllByText('Remove')[0])
    expect(screen.getByText(/Their access will be revoked on next page load/)).toBeInTheDocument()
    expect(screen.getByText('Remove', { selector: 'button' })).toBeInTheDocument()
  })

  it('calls DELETE on remove confirm', async () => {
    mockRemove.mockResolvedValue(undefined)
    renderTab()
    await waitFor(() => screen.getAllByText('Remove'))
    await userEvent.click(screen.getAllByText('Remove')[0])
    await userEvent.click(screen.getByText('Remove', { selector: 'button[data-confirm]' }))
    expect(mockRemove).toHaveBeenCalledWith(IDS.client1, IDS.fpUser1)
  })
})
```

- [ ] **Step 2: Add `listFamilyPortalUsers` to `clients.ts` API**

Add to `frontend/src/api/clients.ts`:
```ts
export interface FamilyPortalUserResponse {
  id: string
  email: string
  name: string | null
  lastLoginAt: string | null
  clientId: string
  agencyId: string
}

export async function listFamilyPortalUsers(
  clientId: string,
): Promise<PageResponse<FamilyPortalUserResponse>> {
  const res = await apiClient.get<PageResponse<FamilyPortalUserResponse>>(
    `/clients/${clientId}/family-portal-users`,
  )
  return res.data
}
```

- [ ] **Step 3: Run tests — expect failures**

```bash
cd frontend && npm run test -- --run FamilyPortalTab.test 2>&1 | tail -10
```

- [ ] **Step 4: Implement `FamilyPortalTab.tsx`**

```tsx
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { listFamilyPortalUsers, type FamilyPortalUserResponse } from '../../api/clients'
import { inviteFamilyPortalUser, removeFamilyPortalUser } from '../../api/portal'

interface Props {
  clientId: string
}

export default function FamilyPortalTab({ clientId }: Props) {
  const { t } = useTranslation('portal')
  const qc = useQueryClient()
  const [formOpen, setFormOpen] = useState(false)
  const [prefilledEmail, setPrefilledEmail] = useState<string | null>(null)
  const [confirmRemove, setConfirmRemove] = useState<FamilyPortalUserResponse | null>(null)
  const [inviteUrl, setInviteUrl] = useState<string | null>(null)
  const [expiresAt, setExpiresAt] = useState<string | null>(null)
  const [email, setEmail] = useState('')
  const [copied, setCopied] = useState(false)
  const isReInvite = prefilledEmail !== null

  const { data } = useQuery({
    queryKey: ['family-portal-users', clientId],
    queryFn: () => listFamilyPortalUsers(clientId),
  })

  const inviteMutation = useMutation({
    mutationFn: ({ email }: { email: string }) =>
      inviteFamilyPortalUser(clientId, email),
    onSuccess: (res) => {
      setInviteUrl(res.inviteUrl)
      setExpiresAt(res.expiresAt)
      qc.invalidateQueries({ queryKey: ['family-portal-users', clientId] })
    },
  })

  const removeMutation = useMutation({
    mutationFn: (fpuId: string) => removeFamilyPortalUser(clientId, fpuId),
    onSuccess: () => {
      setConfirmRemove(null)
      qc.invalidateQueries({ queryKey: ['family-portal-users', clientId] })
    },
  })

  function openInviteForm(prefill?: string) {
    setEmail(prefill ?? '')
    setPrefilledEmail(prefill ?? null)
    setInviteUrl(null)
    setExpiresAt(null)
    setCopied(false)
    setFormOpen(true)
  }

  function handleGenerate() {
    inviteMutation.mutate({ email })
  }

  function handleCopy() {
    if (inviteUrl) {
      navigator.clipboard.writeText(inviteUrl).then(() => {
        setCopied(true)
        setTimeout(() => setCopied(false), 2000)
      })
    }
  }

  function formatExpiry(iso: string): { date: string; time: string } {
    const d = new Date(iso)
    return {
      date: d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' }),
      time: d.toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' }),
    }
  }

  const users = data?.content ?? []

  return (
    <div className="bg-surface p-3">
      {/* Section header */}
      <div className="flex justify-between items-center mb-3">
        <span className="text-[9px] font-bold tracking-[.1em] uppercase text-text-secondary">
          {t('portalAccessHeading')}
        </span>
        <button
          onClick={() => openInviteForm()}
          className="bg-dark text-white text-[11px] font-bold px-3 py-1.5 min-h-[44px] flex items-center justify-center"
        >
          {t('addInvite')}
        </button>
      </div>

      {/* Invite form */}
      {formOpen && (
        <div className="border border-blue bg-white p-3 mb-3">
          {isReInvite && (
            <p className="text-[12px] text-text-secondary mb-2">{t('reInviteNote')}</p>
          )}
          {!inviteUrl ? (
            <div className="flex gap-2 items-end">
              <div className="flex-1">
                <label className="text-[9px] font-bold tracking-[.08em] uppercase text-text-secondary block mb-1">
                  {t('inviteEmail')}
                </label>
                <input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="email@example.com"
                  className="border border-border px-2.5 py-1.5 text-[12px] text-text-primary w-full"
                />
              </div>
              <button
                onClick={handleGenerate}
                disabled={!email || inviteMutation.isPending}
                className="bg-dark text-white text-[11px] font-bold px-3 py-1.5 min-h-[44px] flex items-center justify-center"
              >
                {t('generateLink')}
              </button>
              <button
                onClick={() => { setFormOpen(false); setPrefilledEmail(null) }}
                className="border border-border text-text-secondary text-[11px] px-3 py-1.5 bg-transparent min-h-[44px] flex items-center justify-center"
              >
                {t('cancel')}
              </button>
            </div>
          ) : (
            <div>
              <div className="bg-surface border border-border p-2 font-mono text-[11px] text-text-primary break-all mb-2">
                {inviteUrl}
              </div>
              <div className="flex items-center gap-2 mb-2">
                <button
                  onClick={handleCopy}
                  className="border border-blue text-blue text-[11px] px-3 py-1.5"
                >
                  {copied ? t('linkCopied') : t('copyLink')}
                </button>
                <button
                  onClick={() => { setFormOpen(false); setPrefilledEmail(null) }}
                  className="border border-border text-text-secondary text-[11px] px-3 py-1.5 min-h-[44px] flex items-center justify-center"
                >
                  {t('done')}
                </button>
              </div>
              {expiresAt && (() => {
                const { date, time } = formatExpiry(expiresAt)
                return (
                  <p className="text-[12px] text-text-secondary">
                    {t('inviteExpiry', { date, time })}
                  </p>
                )
              })()}
            </div>
          )}
        </div>
      )}

      {/* User list */}
      {users.length === 0 && !formOpen && (
        <p className="text-[13px] text-text-secondary">{t('noPortalUsers')}</p>
      )}
      <div className="flex flex-col gap-px">
        {users.map((fpu) => (
          <div key={fpu.id}>
            <div className="bg-white border border-border p-3 flex justify-between items-start">
              <div>
                <div className="text-[13px] font-semibold text-text-primary">{fpu.name ?? fpu.email}</div>
                <div className="text-[12px] text-text-secondary">{fpu.email}</div>
                <div className="text-[11px] text-text-muted mt-0.5">
                  {fpu.lastLoginAt
                    ? `${t('lastLogin')} ${new Date(fpu.lastLoginAt).toLocaleDateString()}`
                    : t('neverLoggedIn')}
                </div>
              </div>
              <div className="flex gap-2 flex-shrink-0">
                <button
                  onClick={() => openInviteForm(fpu.email)}
                  className="text-[11px] text-text-secondary border border-border px-2 py-1 min-h-[44px] flex items-center justify-center"
                >
                  {t('sendNewLink')}
                </button>
                <button
                  onClick={() => setConfirmRemove(fpu)}
                  className="text-[11px] text-text-secondary border border-border px-2 py-1 min-h-[44px] flex items-center justify-center"
                >
                  {t('remove')}
                </button>
              </div>
            </div>
            {/* Inline remove confirmation */}
            {confirmRemove?.id === fpu.id && (
              <div className="bg-white border border-border border-t-0 px-3 pb-3">
                <p className="text-[12px] text-text-primary mb-2">
                  {t('removeConfirmation', { name: fpu.name ?? fpu.email })}
                </p>
                <div className="flex gap-2">
                  <button
                    data-confirm
                    onClick={() => removeMutation.mutate(fpu.id)}
                    className="bg-dark text-white text-[11px] font-bold px-3 py-1.5"
                  >
                    {t('removeConfirm')}
                  </button>
                  <button
                    onClick={() => setConfirmRemove(null)}
                    className="border border-border text-text-secondary text-[11px] px-3 py-1.5 min-h-[44px] flex items-center justify-center"
                  >
                    {t('cancel')}
                  </button>
                </div>
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  )
}
```

- [ ] **Step 5: Run tests — expect pass**

```bash
cd frontend && npm run test -- --run FamilyPortalTab.test 2>&1 | tail -10
```
Expected: All 8 tests pass.

- [ ] **Step 6: Wire `FamilyPortalTab` into `ClientDetailPanel`**

In `frontend/src/components/clients/ClientDetailPanel.tsx`, find:
```tsx
{activeTab === 'familyPortal' && (
  <p className="text-text-secondary text-[13px]">{t('familyPortalPhaseNote')}</p>
)}
```

Replace with:
```tsx
{activeTab === 'familyPortal' && (
  <FamilyPortalTab clientId={clientId} />
)}
```

And add the import at the top of the file:
```tsx
import FamilyPortalTab from './FamilyPortalTab'
```

- [ ] **Step 7: TypeScript compile**

```bash
cd frontend && npx tsc --noEmit 2>&1 | head -20
```

- [ ] **Step 8: Commit**

```bash
git add frontend/src/components/clients/FamilyPortalTab.tsx \
        frontend/src/components/clients/FamilyPortalTab.test.tsx \
        frontend/src/components/clients/ClientDetailPanel.tsx \
        frontend/src/api/clients.ts
git commit -m "feat: FamilyPortalTab — invite, re-invite, copy link, remove with confirmation"
```

---

## Task 20: Final verification

- [ ] **Step 1: Run all frontend tests**

```bash
cd frontend && npm run test -- --run 2>&1 | tail -10
```
Expected: All tests pass, BUILD SUCCESS.

- [ ] **Step 2: TypeScript full compile**

```bash
cd frontend && npx tsc --noEmit 2>&1 | head -20
```
Expected: No errors.

- [ ] **Step 3: Lint**

```bash
cd frontend && npm run lint 2>&1 | tail -10
```
Fix any lint errors before committing.

- [ ] **Step 4: Run backend test suite**

```bash
cd backend && mvn test -q 2>&1 | tail -10
```
Expected: BUILD SUCCESS.

- [ ] **Step 5: Smoke test end-to-end**

Start both services:
```bash
./dev-start.sh
```

1. Log in as `admin@sunrise.dev` / `Admin1234!`
2. Open any client detail panel → "Family Portal" tab
3. Click "+ Invite", enter an email, click "Generate Link"
4. Copy the invite URL, paste in a new browser tab
5. Confirm you are redirected to `/portal/dashboard`
6. Verify today's visit, upcoming, and last visit sections render
7. Click "Sign out" — confirm you land on verify page with "You've been signed out" heading

- [ ] **Step 6: Final commit**

```bash
git add -A
git commit -m "feat: family portal — complete invite flow, verify page, dashboard, admin tab"
```
