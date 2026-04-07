# Phase 1: Static UI with Mock Data

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Scaffold the entire React admin UI with mock data. Every screen — Schedule, Dashboard, Clients, Caregivers, Payers, EVV Status — must be navigable and visually complete before any backend call is wired.

**Architecture:** Page components import mock data and pass it as props to child components. Child components are pure presentational — they never fetch data. This makes Phase 4–8 wiring simple: only page components change.

**Tech Stack:** React 18, TypeScript, Vite 5, Tailwind CSS v3, React Router v6, Zustand 5, Vitest, Testing Library, Playwright

**Before starting:** Run from the repo root — not inside `frontend/`.

---

### Task 1: Scaffold Vite Project

**Files:**
- Create: `frontend/` (entire directory via Vite scaffold)

- [ ] **Step 1.1: Scaffold**

```bash
cd /path/to/hcare
npm create vite@latest frontend -- --template react-ts
```

Expected output ends with:
```
Done. Now run:
  cd frontend
  npm install
  npm run dev
```

- [ ] **Step 1.2: Install all dependencies**

```bash
cd frontend && npm install && npm install \
  react-router-dom \
  @tanstack/react-query \
  zustand \
  react-hook-form \
  axios \
  tailwindcss@^3 autoprefixer postcss \
  @types/node && \
npm install --save-dev \
  @testing-library/react \
  @testing-library/jest-dom \
  @testing-library/user-event \
  @vitest/coverage-v8 \
  jsdom \
  vitest \
  @playwright/test
```

Expected: no errors, `node_modules/` populated.

- [ ] **Step 1.3: Commit**

```bash
cd ..
git add frontend/package.json frontend/package-lock.json
git commit -m "chore: scaffold React frontend with Vite + install deps"
```

---

### Task 2: Configure Tailwind, Vite, and TypeScript

**Files:**
- Create: `frontend/tailwind.config.ts`
- Create: `frontend/postcss.config.js`
- Modify: `frontend/vite.config.ts`
- Modify: `frontend/src/index.css`
- Create: `frontend/src/test/setup.ts`
- Modify: `frontend/tsconfig.json`

- [ ] **Step 2.1: Create Tailwind config**

Create `frontend/tailwind.config.ts`:

```ts
import type { Config } from 'tailwindcss'

export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        dark: '#1a1a24',
        'dark-mid': '#2e2e38',
        blue: '#1a9afa',
        surface: '#f6f6fa',
        border: '#eaeaf2',
        'text-primary': '#1a1a24',
        'text-secondary': '#747480',
        'text-muted': '#94a3b8',
      },
      fontFamily: {
        sans: [
          '-apple-system',
          'BlinkMacSystemFont',
          "'Segoe UI'",
          'sans-serif',
        ],
      },
    },
  },
  plugins: [],
} satisfies Config
```

- [ ] **Step 2.2: Create PostCSS config**

Create `frontend/postcss.config.js`:

```js
export default {
  plugins: {
    tailwindcss: {},
    autoprefixer: {},
  },
}
```

- [ ] **Step 2.3: Replace src/index.css with design base**

Replace the entire content of `frontend/src/index.css`:

```css
@tailwind base;
@tailwind components;
@tailwind utilities;

@layer base {
  * {
    box-sizing: border-box;
  }
  html, body, #root {
    height: 100%;
    margin: 0;
    padding: 0;
  }
  body {
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
    font-size: 13px;
    color: #1a1a24;
    background: #f6f6fa;
  }
}
```

- [ ] **Step 2.4: Update vite.config.ts**

Replace `frontend/vite.config.ts`:

```ts
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html'],
      exclude: ['src/mock/**', 'src/test/**', '**/*.test.tsx'],
    },
  },
})
```

- [ ] **Step 2.5: Create test setup file**

Create `frontend/src/test/setup.ts`:

```ts
import '@testing-library/jest-dom'
```

- [ ] **Step 2.6: Update tsconfig.json**

Replace `frontend/tsconfig.json`:

```json
{
  "compilerOptions": {
    "target": "ES2020",
    "useDefineForClassFields": true,
    "lib": ["ES2020", "DOM", "DOM.Iterable"],
    "module": "ESNext",
    "skipLibCheck": true,
    "moduleResolution": "bundler",
    "allowImportingTsExtensions": true,
    "resolveJsonModule": true,
    "isolatedModules": true,
    "noEmit": true,
    "jsx": "react-jsx",
    "strict": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "noFallthroughCasesInSwitch": true,
    "types": ["vitest/globals", "@testing-library/jest-dom"]
  },
  "include": ["src"],
  "references": [{ "path": "./tsconfig.node.json" }]
}
```

- [ ] **Step 2.7: Verify Tailwind works**

```bash
cd frontend && npm run build 2>&1 | tail -5
```

Expected: `✓ built in` with no errors.

- [ ] **Step 2.8: Commit**

```bash
cd ..
git add frontend/tailwind.config.ts frontend/postcss.config.js \
  frontend/vite.config.ts frontend/src/index.css \
  frontend/src/test/setup.ts frontend/tsconfig.json
git commit -m "chore: configure Tailwind CSS v3 + Vitest + design tokens"
```

---

### Task 3: TypeScript API Types

**Files:**
- Create: `frontend/src/types/api.ts`

These types mirror the backend DTOs exactly. They are used in Phases 1–8 (mock data in Phase 1, real API in later phases).

- [ ] **Step 3.1: Create types file**

Create `frontend/src/types/api.ts`:

```ts
// ── Enums ────────────────────────────────────────────────────────────────────

export type ShiftStatus =
  | 'OPEN'
  | 'ASSIGNED'
  | 'IN_PROGRESS'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'MISSED'

export type EvvComplianceStatus =
  | 'GREY'
  | 'EXEMPT'
  | 'GREEN'
  | 'YELLOW'
  | 'PORTAL_SUBMIT'
  | 'RED'

export type VerificationMethod =
  | 'GPS'
  | 'TELEPHONY_LANDLINE'
  | 'TELEPHONY_CELL'
  | 'FIXED_DEVICE'
  | 'FOB'
  | 'BIOMETRIC'
  | 'MANUAL'

export type ClientStatus = 'ACTIVE' | 'INACTIVE' | 'DISCHARGED'
export type CaregiverStatus = 'ACTIVE' | 'INACTIVE' | 'TERMINATED'
export type UserRole = 'ADMIN' | 'SCHEDULER'
export type PayerType =
  | 'MEDICAID'
  | 'PRIVATE_PAY'
  | 'LTC_INSURANCE'
  | 'VA'
  | 'MEDICARE'

export type DashboardAlertType =
  | 'CREDENTIAL_EXPIRY'
  | 'AUTHORIZATION_LOW'
  | 'BACKGROUND_CHECK_DUE'

export type DashboardAlertResourceType = 'CAREGIVER' | 'CLIENT'

// ── Auth ─────────────────────────────────────────────────────────────────────

export interface LoginRequest {
  email: string
  password: string
}

export interface LoginResponse {
  token: string
  userId: string
  agencyId: string
  role: UserRole
}

// ── Spring Page wrapper ───────────────────────────────────────────────────────

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
  first: boolean
  last: boolean
}

// ── Shifts ────────────────────────────────────────────────────────────────────

export interface ShiftSummaryResponse {
  id: string
  agencyId: string
  clientId: string
  caregiverId: string | null
  serviceTypeId: string
  authorizationId: string | null
  sourcePatternId: string | null
  scheduledStart: string   // ISO-8601 LocalDateTime — no timezone
  scheduledEnd: string
  status: ShiftStatus
  notes: string | null
}

export interface EvvSummary {
  evvRecordId: string
  complianceStatus: EvvComplianceStatus
  timeIn: string | null
  timeOut: string | null
  verificationMethod: VerificationMethod | null
  capturedOffline: boolean
}

export interface ShiftDetailResponse extends ShiftSummaryResponse {
  evv: EvvSummary | null
}

export interface RankedCaregiverResponse {
  caregiverId: string
  score: number
  explanation: string
}

export interface CreateShiftRequest {
  clientId: string
  caregiverId?: string
  serviceTypeId: string
  authorizationId?: string
  scheduledStart: string
  scheduledEnd: string
  notes?: string
}

export interface AssignCaregiverRequest {
  caregiverId: string
}

// ── Clients ───────────────────────────────────────────────────────────────────

export interface ClientResponse {
  id: string
  firstName: string
  lastName: string
  dateOfBirth: string
  address: string | null
  phone: string | null
  medicaidId: string | null
  serviceState: string | null
  preferredCaregiverGender: string | null
  preferredLanguages: string | null
  noPetCaregiver: boolean
  status: ClientStatus
  createdAt: string
}

export interface AuthorizationResponse {
  id: string
  clientId: string
  payerId: string
  serviceTypeId: string
  authNumber: string
  authorizedUnits: number
  usedUnits: number
  unitType: 'HOUR' | 'VISIT' | 'DAY'
  startDate: string
  endDate: string
  version: number
  createdAt: string
}

// ── Caregivers ────────────────────────────────────────────────────────────────

export interface CaregiverResponse {
  id: string
  firstName: string
  lastName: string
  email: string
  phone: string | null
  address: string | null
  hireDate: string | null
  hasPet: boolean
  status: CaregiverStatus
  createdAt: string
}

export interface CredentialResponse {
  id: string
  caregiverId: string
  credentialType: string
  issueDate: string | null
  expiryDate: string | null
  verified: boolean
  verifiedBy: string | null
  createdAt: string
}

// ── Payers ────────────────────────────────────────────────────────────────────

export interface PayerResponse {
  id: string
  name: string
  payerType: PayerType
  state: string
  evvAggregator: string | null
  createdAt: string
}

// ── Dashboard ─────────────────────────────────────────────────────────────────

export interface DashboardVisitRow {
  shiftId: string
  clientFirstName: string
  clientLastName: string
  caregiverId: string | null
  caregiverFirstName: string | null
  caregiverLastName: string | null
  serviceTypeName: string
  scheduledStart: string
  scheduledEnd: string
  status: ShiftStatus
  evvStatus: EvvComplianceStatus
  evvStatusReason: string | null
}

export interface DashboardAlert {
  type: DashboardAlertType
  subject: string
  detail: string
  dueDate: string
  resourceId: string
  resourceType: DashboardAlertResourceType
}

export interface DashboardTodayResponse {
  redEvvCount: number
  yellowEvvCount: number
  uncoveredCount: number
  onTrackCount: number
  visits: DashboardVisitRow[]
  alerts: DashboardAlert[]
}

// ── EVV History ───────────────────────────────────────────────────────────────

export interface EvvHistoryRow {
  shiftId: string
  clientFirstName: string
  clientLastName: string
  caregiverFirstName: string | null
  caregiverLastName: string | null
  serviceTypeName: string
  scheduledStart: string
  scheduledEnd: string
  evvStatus: EvvComplianceStatus
  evvStatusReason: string | null
  timeIn: string | null
  timeOut: string | null
  verificationMethod: VerificationMethod | null
}
```

- [ ] **Step 3.2: Verify types compile**

```bash
cd frontend && npx tsc --noEmit 2>&1 | head -20
```

Expected: no output (no errors).

- [ ] **Step 3.3: Commit**

```bash
cd ..
git add frontend/src/types/api.ts
git commit -m "feat(frontend): add TypeScript API types matching backend DTOs"
```

---

### Task 4: Mock Data

**Files:**
- Create: `frontend/src/mock/data.ts`

Mock data uses fixed IDs and today's date (2026-04-06). All shifts are within 6am–10pm.

- [ ] **Step 4.1: Create mock data**

Create `frontend/src/mock/data.ts`:

```ts
import type {
  ShiftDetailResponse,
  ClientResponse,
  CaregiverResponse,
  DashboardTodayResponse,
  PayerResponse,
  EvvHistoryRow,
  AuthorizationResponse,
  CredentialResponse,
} from '../types/api'

// ── Fixed IDs ─────────────────────────────────────────────────────────────────

export const IDS = {
  agency: 'aaaaaaaa-0000-0000-0000-000000000001',
  client1: 'c1000000-0000-0000-0000-000000000001',
  client2: 'c2000000-0000-0000-0000-000000000002',
  client3: 'c3000000-0000-0000-0000-000000000003',
  client4: 'c4000000-0000-0000-0000-000000000004',
  caregiver1: 'g1000000-0000-0000-0000-000000000001',
  caregiver2: 'g2000000-0000-0000-0000-000000000002',
  caregiver3: 'g3000000-0000-0000-0000-000000000003',
  shift1: 's1000000-0000-0000-0000-000000000001',
  shift2: 's2000000-0000-0000-0000-000000000002',
  shift3: 's3000000-0000-0000-0000-000000000003',
  shift4: 's4000000-0000-0000-0000-000000000004',
  shift5: 's5000000-0000-0000-0000-000000000005',
  shift6: 's6000000-0000-0000-0000-000000000006',
  payer1: 'p1000000-0000-0000-0000-000000000001',
  payer2: 'p2000000-0000-0000-0000-000000000002',
  serviceType1: 'st000000-0000-0000-0000-000000000001',
}

// ── Clients ───────────────────────────────────────────────────────────────────

export const mockClients: ClientResponse[] = [
  {
    id: IDS.client1,
    firstName: 'Alice',
    lastName: 'Johnson',
    dateOfBirth: '1942-03-15',
    address: '123 Oak St, Austin, TX 78701',
    phone: '512-555-0101',
    medicaidId: 'TX-MCD-001122',
    serviceState: 'TX',
    preferredCaregiverGender: null,
    preferredLanguages: '["en"]',
    noPetCaregiver: false,
    status: 'ACTIVE',
    createdAt: '2025-01-10T08:00:00',
  },
  {
    id: IDS.client2,
    firstName: 'Robert',
    lastName: 'Martinez',
    dateOfBirth: '1938-07-22',
    address: '456 Elm Ave, Austin, TX 78702',
    phone: '512-555-0202',
    medicaidId: 'TX-MCD-003344',
    serviceState: 'TX',
    preferredCaregiverGender: 'FEMALE',
    preferredLanguages: '["en","es"]',
    noPetCaregiver: true,
    status: 'ACTIVE',
    createdAt: '2025-02-14T09:30:00',
  },
  {
    id: IDS.client3,
    firstName: 'Dorothy',
    lastName: 'Chen',
    dateOfBirth: '1950-11-03',
    address: '789 Pine Rd, Austin, TX 78703',
    phone: '512-555-0303',
    medicaidId: null,
    serviceState: 'TX',
    preferredCaregiverGender: null,
    preferredLanguages: '["en","zh"]',
    noPetCaregiver: false,
    status: 'ACTIVE',
    createdAt: '2025-03-01T10:00:00',
  },
  {
    id: IDS.client4,
    firstName: 'James',
    lastName: 'Williams',
    dateOfBirth: '1945-05-18',
    address: '321 Maple Ln, Austin, TX 78704',
    phone: '512-555-0404',
    medicaidId: 'TX-MCD-005566',
    serviceState: 'TX',
    preferredCaregiverGender: null,
    preferredLanguages: '["en"]',
    noPetCaregiver: false,
    status: 'ACTIVE',
    createdAt: '2025-03-15T11:00:00',
  },
]

export const mockClientMap = new Map(mockClients.map((c) => [c.id, c]))

// ── Caregivers ────────────────────────────────────────────────────────────────

export const mockCaregivers: CaregiverResponse[] = [
  {
    id: IDS.caregiver1,
    firstName: 'Maria',
    lastName: 'Garcia',
    email: 'maria.garcia@hcare.dev',
    phone: '512-555-1001',
    address: '10 Riverside Dr, Austin, TX 78701',
    hireDate: '2023-04-01',
    hasPet: false,
    status: 'ACTIVE',
    createdAt: '2023-04-01T08:00:00',
  },
  {
    id: IDS.caregiver2,
    firstName: 'James',
    lastName: 'Wilson',
    email: 'james.wilson@hcare.dev',
    phone: '512-555-1002',
    address: '22 Cedar St, Austin, TX 78702',
    hireDate: '2024-01-15',
    hasPet: true,
    status: 'ACTIVE',
    createdAt: '2024-01-15T09:00:00',
  },
  {
    id: IDS.caregiver3,
    firstName: 'Sarah',
    lastName: 'Davis',
    email: 'sarah.davis@hcare.dev',
    phone: '512-555-1003',
    address: '7 Birch Ave, Austin, TX 78703',
    hireDate: '2022-09-10',
    hasPet: false,
    status: 'ACTIVE',
    createdAt: '2022-09-10T10:00:00',
  },
]

export const mockCaregiverMap = new Map(mockCaregivers.map((c) => [c.id, c]))

// ── Shifts (this week: Apr 6–12, 2026) ────────────────────────────────────────

export const mockShifts: ShiftDetailResponse[] = [
  {
    id: IDS.shift1,
    agencyId: IDS.agency,
    clientId: IDS.client1,
    caregiverId: IDS.caregiver1,
    serviceTypeId: IDS.serviceType1,
    authorizationId: null,
    sourcePatternId: null,
    scheduledStart: '2026-04-06T08:00:00',
    scheduledEnd: '2026-04-06T12:00:00',
    status: 'COMPLETED',
    notes: null,
    evv: {
      evvRecordId: 'evv00001-0000-0000-0000-000000000001',
      complianceStatus: 'RED',
      timeIn: '2026-04-06T08:05:00',
      timeOut: null,
      verificationMethod: 'GPS',
      capturedOffline: false,
    },
  },
  {
    id: IDS.shift2,
    agencyId: IDS.agency,
    clientId: IDS.client2,
    caregiverId: IDS.caregiver2,
    serviceTypeId: IDS.serviceType1,
    authorizationId: null,
    sourcePatternId: null,
    scheduledStart: '2026-04-06T09:00:00',
    scheduledEnd: '2026-04-06T13:00:00',
    status: 'IN_PROGRESS',
    notes: null,
    evv: {
      evvRecordId: 'evv00002-0000-0000-0000-000000000002',
      complianceStatus: 'YELLOW',
      timeIn: '2026-04-06T09:12:00',
      timeOut: null,
      verificationMethod: 'GPS',
      capturedOffline: false,
    },
  },
  {
    id: IDS.shift3,
    agencyId: IDS.agency,
    clientId: IDS.client3,
    caregiverId: null,
    serviceTypeId: IDS.serviceType1,
    authorizationId: null,
    sourcePatternId: null,
    scheduledStart: '2026-04-06T10:00:00',
    scheduledEnd: '2026-04-06T14:00:00',
    status: 'OPEN',
    notes: null,
    evv: null,
  },
  {
    id: IDS.shift4,
    agencyId: IDS.agency,
    clientId: IDS.client4,
    caregiverId: IDS.caregiver3,
    serviceTypeId: IDS.serviceType1,
    authorizationId: null,
    sourcePatternId: null,
    scheduledStart: '2026-04-06T14:00:00',
    scheduledEnd: '2026-04-06T18:00:00',
    status: 'ASSIGNED',
    notes: null,
    evv: null,
  },
  {
    id: IDS.shift5,
    agencyId: IDS.agency,
    clientId: IDS.client1,
    caregiverId: IDS.caregiver1,
    serviceTypeId: IDS.serviceType1,
    authorizationId: null,
    sourcePatternId: null,
    scheduledStart: '2026-04-07T09:00:00',
    scheduledEnd: '2026-04-07T13:00:00',
    status: 'ASSIGNED',
    notes: null,
    evv: null,
  },
  {
    id: IDS.shift6,
    agencyId: IDS.agency,
    clientId: IDS.client2,
    caregiverId: IDS.caregiver3,
    serviceTypeId: IDS.serviceType1,
    authorizationId: null,
    sourcePatternId: null,
    scheduledStart: '2026-04-08T11:00:00',
    scheduledEnd: '2026-04-08T15:00:00',
    status: 'ASSIGNED',
    notes: null,
    evv: null,
  },
]

// ── Payers ────────────────────────────────────────────────────────────────────

export const mockPayers: PayerResponse[] = [
  {
    id: IDS.payer1,
    name: 'Texas Medicaid',
    payerType: 'MEDICAID',
    state: 'TX',
    evvAggregator: 'HHAeXchange',
    createdAt: '2025-01-01T00:00:00',
  },
  {
    id: IDS.payer2,
    name: 'Private Pay',
    payerType: 'PRIVATE_PAY',
    state: 'TX',
    evvAggregator: null,
    createdAt: '2025-01-01T00:00:00',
  },
]

// ── Credentials (for caregiver detail panel) ─────────────────────────────────

export const mockCredentials: CredentialResponse[] = [
  {
    id: 'cred0001-0000-0000-0000-000000000001',
    caregiverId: IDS.caregiver1,
    credentialType: 'HHA',
    issueDate: '2023-04-01',
    expiryDate: '2026-04-10',
    verified: true,
    verifiedBy: null,
    createdAt: '2023-04-01T08:00:00',
  },
  {
    id: 'cred0002-0000-0000-0000-000000000002',
    caregiverId: IDS.caregiver2,
    credentialType: 'CPR',
    issueDate: '2024-01-15',
    expiryDate: '2026-06-15',
    verified: true,
    verifiedBy: null,
    createdAt: '2024-01-15T09:00:00',
  },
]

// ── Authorizations (for client detail panel) ─────────────────────────────────

export const mockAuthorizations: AuthorizationResponse[] = [
  {
    id: 'auth0001-0000-0000-0000-000000000001',
    clientId: IDS.client1,
    payerId: IDS.payer1,
    serviceTypeId: IDS.serviceType1,
    authNumber: 'TX-AUTH-20260101',
    authorizedUnits: 80,
    usedUnits: 62,
    unitType: 'HOUR',
    startDate: '2026-01-01',
    endDate: '2026-06-30',
    version: 1,
    createdAt: '2026-01-01T00:00:00',
  },
]

// ── Dashboard ─────────────────────────────────────────────────────────────────

export const mockDashboard: DashboardTodayResponse = {
  redEvvCount: 1,
  yellowEvvCount: 1,
  uncoveredCount: 1,
  onTrackCount: 1,
  visits: [
    {
      shiftId: IDS.shift1,
      clientFirstName: 'Alice',
      clientLastName: 'Johnson',
      caregiverId: IDS.caregiver1,
      caregiverFirstName: 'Maria',
      caregiverLastName: 'Garcia',
      serviceTypeName: 'PCS',
      scheduledStart: '2026-04-06T08:00:00',
      scheduledEnd: '2026-04-06T12:00:00',
      status: 'COMPLETED',
      evvStatus: 'RED',
      evvStatusReason: 'No clock-out recorded',
    },
    {
      shiftId: IDS.shift2,
      clientFirstName: 'Robert',
      clientLastName: 'Martinez',
      caregiverId: IDS.caregiver2,
      caregiverFirstName: 'James',
      caregiverLastName: 'Wilson',
      serviceTypeName: 'PCS',
      scheduledStart: '2026-04-06T09:00:00',
      scheduledEnd: '2026-04-06T13:00:00',
      status: 'IN_PROGRESS',
      evvStatus: 'YELLOW',
      evvStatusReason: 'Clock-in 12 min late',
    },
    {
      shiftId: IDS.shift3,
      clientFirstName: 'Dorothy',
      clientLastName: 'Chen',
      caregiverId: null,
      caregiverFirstName: null,
      caregiverLastName: null,
      serviceTypeName: 'PCS',
      scheduledStart: '2026-04-06T10:00:00',
      scheduledEnd: '2026-04-06T14:00:00',
      status: 'OPEN',
      evvStatus: 'GREY',
      evvStatusReason: null,
    },
    {
      shiftId: IDS.shift4,
      clientFirstName: 'James',
      clientLastName: 'Williams',
      caregiverId: IDS.caregiver3,
      caregiverFirstName: 'Sarah',
      caregiverLastName: 'Davis',
      serviceTypeName: 'PCS',
      scheduledStart: '2026-04-06T14:00:00',
      scheduledEnd: '2026-04-06T18:00:00',
      status: 'ASSIGNED',
      evvStatus: 'GREY',
      evvStatusReason: null,
    },
  ],
  alerts: [
    {
      type: 'CREDENTIAL_EXPIRY',
      subject: 'M. Garcia',
      detail: 'HHA expires in 4 days',
      dueDate: '2026-04-10',
      resourceId: IDS.caregiver1,
      resourceType: 'CAREGIVER',
    },
    {
      type: 'AUTHORIZATION_LOW',
      subject: 'A. Johnson',
      detail: '18 hrs remaining (Medicaid)',
      dueDate: '2026-06-30',
      resourceId: IDS.client1,
      resourceType: 'CLIENT',
    },
  ],
}

// ── EVV History ───────────────────────────────────────────────────────────────

export const mockEvvHistory: EvvHistoryRow[] = [
  {
    shiftId: IDS.shift1,
    clientFirstName: 'Alice',
    clientLastName: 'Johnson',
    caregiverFirstName: 'Maria',
    caregiverLastName: 'Garcia',
    serviceTypeName: 'PCS',
    scheduledStart: '2026-04-06T08:00:00',
    scheduledEnd: '2026-04-06T12:00:00',
    evvStatus: 'RED',
    evvStatusReason: 'No clock-out recorded',
    timeIn: '2026-04-06T08:05:00',
    timeOut: null,
    verificationMethod: 'GPS',
  },
  {
    shiftId: IDS.shift2,
    clientFirstName: 'Robert',
    clientLastName: 'Martinez',
    caregiverFirstName: 'James',
    caregiverLastName: 'Wilson',
    serviceTypeName: 'PCS',
    scheduledStart: '2026-04-06T09:00:00',
    scheduledEnd: '2026-04-06T13:00:00',
    evvStatus: 'YELLOW',
    evvStatusReason: 'Clock-in 12 min late',
    timeIn: '2026-04-06T09:12:00',
    timeOut: null,
    verificationMethod: 'GPS',
  },
]
```

- [ ] **Step 4.2: Verify no TypeScript errors**

```bash
cd frontend && npx tsc --noEmit 2>&1 | head -20
```

Expected: no output.

- [ ] **Step 4.3: Commit**

```bash
cd ..
git add frontend/src/mock/data.ts
git commit -m "feat(frontend): add mock data for all screens"
```

---

### Task 5: Zustand Panel Store

**Files:**
- Create: `frontend/src/store/panelStore.ts`

Controls which slide-in panel is open. The panel type determines which component renders inside `SlidePanel`.

- [ ] **Step 5.1: Create panel store**

Create `frontend/src/store/panelStore.ts`:

```ts
import { create } from 'zustand'

export type PanelType =
  | 'shift'
  | 'newShift'
  | 'client'
  | 'caregiver'
  | 'payer'
  | null

interface PanelPrefill {
  date?: string      // ISO date string — pre-fills date field in NewShiftPanel
  time?: string      // HH:mm — pre-fills start time when clicking an empty slot
}

interface PanelState {
  open: boolean
  type: PanelType
  selectedId: string | null
  prefill: PanelPrefill | null
  backLabel: string   // e.g. "← Schedule", "← Clients"
  openPanel: (
    type: Exclude<PanelType, null>,
    id?: string,
    options?: { prefill?: PanelPrefill; backLabel?: string }
  ) => void
  closePanel: () => void
}

export const usePanelStore = create<PanelState>((set) => ({
  open: false,
  type: null,
  selectedId: null,
  prefill: null,
  backLabel: '← Back',

  openPanel: (type, id, options) =>
    set({
      open: true,
      type,
      selectedId: id ?? null,
      prefill: options?.prefill ?? null,
      backLabel: options?.backLabel ?? '← Back',
    }),

  closePanel: () =>
    set({ open: false, type: null, selectedId: null, prefill: null }),
}))
```

- [ ] **Step 5.2: Verify types compile**

```bash
cd frontend && npx tsc --noEmit 2>&1 | head -10
```

Expected: no output.

- [ ] **Step 5.3: Commit**

```bash
cd ..
git add frontend/src/store/panelStore.ts
git commit -m "feat(frontend): add Zustand panel store"
```

---

### Task 6: Shell Layout and Sidebar

**Files:**
- Create: `frontend/src/components/layout/Sidebar.tsx`
- Create: `frontend/src/components/layout/Shell.tsx`

The sidebar is 200px fixed. The main area is `flex-1`. The slide panel is positioned `absolute inset-0` over the main area so it covers the content but not the sidebar.

- [ ] **Step 6.1: Create Sidebar**

Create `frontend/src/components/layout/Sidebar.tsx`:

```tsx
import { NavLink } from 'react-router-dom'

interface NavItem {
  label: string
  to: string
  icon: string       // emoji placeholder — replace with real icons in P2
  badge?: number     // red dot count (Dashboard only)
}

const NAV_SECTIONS: { section: string; items: NavItem[] }[] = [
  {
    section: 'Operations',
    items: [
      { label: 'Schedule', to: '/schedule', icon: '📅' },
      { label: 'Dashboard', to: '/dashboard', icon: '📊' },
    ],
  },
  {
    section: 'People',
    items: [
      { label: 'Clients', to: '/clients', icon: '👤' },
      { label: 'Caregivers', to: '/caregivers', icon: '🏥' },
    ],
  },
  {
    section: 'Admin',
    items: [
      { label: 'Payers', to: '/payers', icon: '💳' },
      { label: 'EVV Status', to: '/evv', icon: '✅' },
      { label: 'Settings', to: '/settings', icon: '⚙️' },
    ],
  },
]

interface SidebarProps {
  /** RED EVV count — drives the Dashboard badge. Passed by Shell from dashboard data. */
  redEvvCount?: number
  /** Logged-in user display name */
  userName?: string
  userRole?: string
}

export function Sidebar({ redEvvCount = 0, userName = 'Admin User', userRole = 'ADMIN' }: SidebarProps) {
  const initials = userName
    .split(' ')
    .map((n) => n[0])
    .join('')
    .toUpperCase()
    .slice(0, 2)

  return (
    <aside
      className="flex flex-col w-[200px] shrink-0 h-screen overflow-y-auto"
      style={{ background: '#1a1a24' }}
    >
      {/* Logo */}
      <div className="px-5 py-6">
        <div className="text-base font-bold tracking-tight">
          <span className="text-white">h</span>
          <span style={{ color: '#1a9afa' }}>.</span>
          <span className="text-white">care</span>
        </div>
        <div className="text-[11px] mt-0.5" style={{ color: '#94a3b8' }}>
          Agency Management
        </div>
      </div>

      {/* Nav */}
      <nav className="flex-1 px-3 pb-4">
        {NAV_SECTIONS.map(({ section, items }) => (
          <div key={section} className="mb-4">
            <div
              className="px-2 mb-1 text-[9px] font-bold uppercase tracking-[0.1em]"
              style={{ color: '#747480' }}
            >
              {section}
            </div>
            {items.map((item) => {
              const showBadge = item.label === 'Dashboard' && redEvvCount > 0
              return (
                <NavLink
                  key={item.to}
                  to={item.to}
                  className={({ isActive }) =>
                    [
                      'flex items-center gap-2.5 px-3 py-2 rounded text-[13px] font-medium mb-0.5',
                      isActive
                        ? 'text-white font-semibold'
                        : 'text-[#94a3b8] hover:text-white',
                    ].join(' ')
                  }
                  style={({ isActive }) =>
                    isActive ? { background: '#1a9afa' } : undefined
                  }
                >
                  <span className="text-base leading-none">{item.icon}</span>
                  <span className="flex-1">{item.label}</span>
                  {showBadge && (
                    <span className="w-4 h-4 rounded-full bg-red-600 text-white text-[10px] flex items-center justify-center font-bold">
                      {redEvvCount > 9 ? '9+' : redEvvCount}
                    </span>
                  )}
                </NavLink>
              )
            })}
          </div>
        ))}
      </nav>

      {/* User footer */}
      <div
        className="px-4 py-4 border-t flex items-center gap-3"
        style={{ borderColor: '#2e2e38' }}
      >
        <div
          className="w-8 h-8 rounded-full flex items-center justify-center text-[11px] font-bold text-white shrink-0"
          style={{ background: '#1a9afa' }}
        >
          {initials}
        </div>
        <div className="overflow-hidden">
          <div className="text-white text-[12px] font-medium truncate">{userName}</div>
          <div className="text-[11px] capitalize truncate" style={{ color: '#94a3b8' }}>
            {userRole.toLowerCase()}
          </div>
        </div>
      </div>
    </aside>
  )
}
```

- [ ] **Step 6.2: Create Shell**

Create `frontend/src/components/layout/Shell.tsx`:

```tsx
import { Outlet } from 'react-router-dom'
import { Sidebar } from './Sidebar'
import { SlidePanel } from '../panel/SlidePanel'
import { usePanelStore } from '../../store/panelStore'
import { ShiftDetailPanel } from '../schedule/ShiftDetailPanel'
import { NewShiftPanel } from '../schedule/NewShiftPanel'
import { ClientDetailPanel } from '../clients/ClientDetailPanel'
import { CaregiverDetailPanel } from '../caregivers/CaregiverDetailPanel'

function PanelContent() {
  const { type, selectedId, prefill, backLabel } = usePanelStore()

  if (type === 'shift' && selectedId) {
    return <ShiftDetailPanel shiftId={selectedId} backLabel={backLabel} />
  }
  if (type === 'newShift') {
    return <NewShiftPanel prefill={prefill} backLabel={backLabel} />
  }
  if (type === 'client' && selectedId) {
    return <ClientDetailPanel clientId={selectedId} backLabel={backLabel} />
  }
  if (type === 'caregiver' && selectedId) {
    return <CaregiverDetailPanel caregiverVId={selectedId} backLabel={backLabel} />
  }
  return null
}

export function Shell() {
  const { open, closePanel } = usePanelStore()

  return (
    <div className="flex h-screen overflow-hidden">
      <Sidebar />
      {/* Main area: relative so SlidePanel's absolute positioning is scoped here */}
      <div className="relative flex-1 overflow-auto bg-surface">
        <Outlet />
        <SlidePanel isOpen={open} onClose={closePanel}>
          <PanelContent />
        </SlidePanel>
      </div>
    </div>
  )
}
```

- [ ] **Step 6.3: Commit**

```bash
cd ..
git add frontend/src/components/layout/
git commit -m "feat(frontend): add Shell layout and Sidebar"
```

---

### Task 7: SlidePanel Component + Test

**Files:**
- Create: `frontend/src/components/panel/SlidePanel.tsx`
- Create: `frontend/src/components/panel/SlidePanel.test.tsx`

- [ ] **Step 7.1: Write the failing test**

Create `frontend/src/components/panel/SlidePanel.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { SlidePanel } from './SlidePanel'

describe('SlidePanel', () => {
  it('renders children when open', () => {
    render(
      <SlidePanel isOpen onClose={() => {}}>
        <div>Panel content</div>
      </SlidePanel>
    )
    expect(screen.getByText('Panel content')).toBeInTheDocument()
  })

  it('is translated off-screen when closed', () => {
    const { container } = render(
      <SlidePanel isOpen={false} onClose={() => {}}>
        <div>Panel content</div>
      </SlidePanel>
    )
    const panel = container.firstChild as HTMLElement
    expect(panel.style.transform).toBe('translateX(100%)')
  })

  it('is at translateX(0) when open', () => {
    const { container } = render(
      <SlidePanel isOpen onClose={() => {}}>
        <div>Panel content</div>
      </SlidePanel>
    )
    const panel = container.firstChild as HTMLElement
    expect(panel.style.transform).toBe('translateX(0)')
  })

  it('calls onClose when Escape is pressed', async () => {
    const onClose = vi.fn()
    render(
      <SlidePanel isOpen onClose={onClose}>
        <div>Panel content</div>
      </SlidePanel>
    )
    await userEvent.keyboard('{Escape}')
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('does not call onClose on Escape when closed', async () => {
    const onClose = vi.fn()
    render(
      <SlidePanel isOpen={false} onClose={onClose}>
        <div>Panel</div>
      </SlidePanel>
    )
    await userEvent.keyboard('{Escape}')
    expect(onClose).not.toHaveBeenCalled()
  })
})
```

- [ ] **Step 7.2: Run test to verify it fails**

```bash
cd frontend && npx vitest run src/components/panel/SlidePanel.test.tsx 2>&1 | tail -10
```

Expected: FAIL — `SlidePanel` not found.

- [ ] **Step 7.3: Implement SlidePanel**

Create `frontend/src/components/panel/SlidePanel.tsx`:

```tsx
import { useEffect } from 'react'

interface SlidePanelProps {
  isOpen: boolean
  onClose: () => void
  children: React.ReactNode
}

export function SlidePanel({ isOpen, onClose, children }: SlidePanelProps) {
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && isOpen) onClose()
    }
    document.addEventListener('keydown', handler)
    return () => document.removeEventListener('keydown', handler)
  }, [isOpen, onClose])

  return (
    <div
      className="absolute inset-0 z-50 bg-white overflow-y-auto"
      style={{
        transform: isOpen ? 'translateX(0)' : 'translateX(100%)',
        transition: 'transform 280ms cubic-bezier(0.4, 0, 0.2, 1)',
      }}
    >
      {children}
    </div>
  )
}
```

- [ ] **Step 7.4: Run test to verify it passes**

```bash
cd frontend && npx vitest run src/components/panel/SlidePanel.test.tsx 2>&1 | tail -5
```

Expected: `Tests 5 passed (5)`.

- [ ] **Step 7.5: Commit**

```bash
cd ..
git add frontend/src/components/panel/
git commit -m "feat(frontend): add SlidePanel component with animation + tests"
```

---

### Task 8: App Routing

**Files:**
- Modify: `frontend/src/main.tsx`
- Create: `frontend/src/App.tsx`

- [ ] **Step 8.1: Replace main.tsx**

Replace `frontend/src/main.tsx`:

```tsx
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter } from 'react-router-dom'
import './index.css'
import App from './App'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      retry: 1,
    },
  },
})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <QueryClientProvider client={queryClient}>
        <App />
      </QueryClientProvider>
    </BrowserRouter>
  </StrictMode>
)
```

- [ ] **Step 8.2: Create App.tsx**

Create `frontend/src/App.tsx`:

```tsx
import { Navigate, Route, Routes } from 'react-router-dom'
import { Shell } from './components/layout/Shell'
import { SchedulePage } from './components/schedule/SchedulePage'
import { DashboardPage } from './components/dashboard/DashboardPage'
import { ClientsPage } from './components/clients/ClientsPage'
import { CaregiversPage } from './components/caregivers/CaregiversPage'
import { PayersPage } from './components/payers/PayersPage'
import { EvvStatusPage } from './components/evv/EvvStatusPage'

export default function App() {
  return (
    <Routes>
      <Route element={<Shell />}>
        <Route index element={<Navigate to="/schedule" replace />} />
        <Route path="/schedule" element={<SchedulePage />} />
        <Route path="/dashboard" element={<DashboardPage />} />
        <Route path="/clients" element={<ClientsPage />} />
        <Route path="/caregivers" element={<CaregiversPage />} />
        <Route path="/payers" element={<PayersPage />} />
        <Route path="/evv" element={<EvvStatusPage />} />
        <Route path="/settings" element={<div className="p-8 text-text-secondary">Settings — coming soon</div>} />
      </Route>
    </Routes>
  )
}
```

- [ ] **Step 8.3: Verify build**

```bash
cd frontend && npx tsc --noEmit 2>&1 | head -20
```

Expected: errors referencing missing page components — that's fine, they don't exist yet.

- [ ] **Step 8.4: Commit what we have**

```bash
cd ..
git add frontend/src/main.tsx frontend/src/App.tsx
git commit -m "feat(frontend): add app routing (Shell + all route placeholders)"
```

---

### Task 9: Schedule Page — WeekCalendar, ShiftBlock, AlertStrip

**Files:**
- Create: `frontend/src/components/schedule/SchedulePage.tsx`
- Create: `frontend/src/components/schedule/WeekCalendar.tsx`
- Create: `frontend/src/components/schedule/ShiftBlock.tsx`
- Create: `frontend/src/components/schedule/AlertStrip.tsx`
- Create: `frontend/src/components/schedule/ShiftBlock.test.tsx`

**Calendar layout:** 6am–10pm (16 hours). Each hour = 60px. Shift blocks positioned absolutely within day columns using `top = minutesSince6am` px, `height = durationMinutes` px.

- [ ] **Step 9.1: Write ShiftBlock test first**

Create `frontend/src/components/schedule/ShiftBlock.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ShiftBlock } from './ShiftBlock'

const baseBlock = {
  shiftId: 's1',
  clientName: 'Alice Johnson',
  caregiverName: 'Maria Garcia',
  evvStatus: 'GREEN' as const,
  top: 120,
  height: 240,
  onClick: () => {},
}

describe('ShiftBlock', () => {
  it('renders client name', () => {
    render(<ShiftBlock {...baseBlock} />)
    expect(screen.getByText('Alice Johnson')).toBeInTheDocument()
  })

  it('renders caregiver name', () => {
    render(<ShiftBlock {...baseBlock} />)
    expect(screen.getByText('Maria Garcia')).toBeInTheDocument()
  })

  it('shows "Unassigned" when no caregiver', () => {
    render(<ShiftBlock {...baseBlock} caregiverName={null} />)
    expect(screen.getByText('Unassigned')).toBeInTheDocument()
  })

  it('applies red left border for RED evv status', () => {
    const { container } = render(<ShiftBlock {...baseBlock} evvStatus="RED" />)
    const block = container.firstChild as HTMLElement
    expect(block.style.borderLeftColor).toBe('rgb(220, 38, 38)')
  })

  it('applies green left border for GREEN evv status', () => {
    const { container } = render(<ShiftBlock {...baseBlock} evvStatus="GREEN" />)
    const block = container.firstChild as HTMLElement
    expect(block.style.borderLeftColor).toBe('rgb(22, 163, 74)')
  })

  it('calls onClick when clicked', async () => {
    const onClick = vi.fn()
    render(<ShiftBlock {...baseBlock} onClick={onClick} />)
    await userEvent.click(screen.getByText('Alice Johnson'))
    expect(onClick).toHaveBeenCalledTimes(1)
  })
})
```

- [ ] **Step 9.2: Run test to verify it fails**

```bash
cd frontend && npx vitest run src/components/schedule/ShiftBlock.test.tsx 2>&1 | tail -5
```

Expected: FAIL — `ShiftBlock` not found.

- [ ] **Step 9.3: Create ShiftBlock**

Create `frontend/src/components/schedule/ShiftBlock.tsx`:

```tsx
import type { EvvComplianceStatus } from '../../types/api'

const EVV_BORDER_COLOR: Record<EvvComplianceStatus, string> = {
  RED: '#dc2626',
  YELLOW: '#ca8a04',
  GREEN: '#16a34a',
  GREY: '#94a3b8',
  EXEMPT: '#94a3b8',
  PORTAL_SUBMIT: '#94a3b8',
}

interface ShiftBlockProps {
  shiftId: string
  clientName: string
  caregiverName: string | null
  evvStatus: EvvComplianceStatus
  /** px from calendar top (6am = 0) */
  top: number
  /** px height (1 min = 1px) */
  height: number
  onClick: () => void
}

export function ShiftBlock({
  clientName,
  caregiverName,
  evvStatus,
  top,
  height,
  onClick,
}: ShiftBlockProps) {
  const borderColor = EVV_BORDER_COLOR[evvStatus]

  return (
    <button
      type="button"
      onClick={onClick}
      className="absolute left-0.5 right-0.5 overflow-hidden rounded-sm bg-white text-left px-2 py-1 hover:brightness-95 transition-[filter]"
      style={{
        top,
        height: Math.max(height, 24),
        borderLeft: `3px solid ${borderColor}`,
        borderLeftColor: borderColor,
        borderTop: '1px solid #eaeaf2',
        borderRight: '1px solid #eaeaf2',
        borderBottom: '1px solid #eaeaf2',
      }}
    >
      <div className="text-[11px] font-semibold text-text-primary truncate leading-tight">
        {clientName}
      </div>
      <div className="text-[10px] text-text-secondary truncate leading-tight">
        {caregiverName ?? 'Unassigned'}
      </div>
    </button>
  )
}
```

- [ ] **Step 9.4: Run test to verify it passes**

```bash
cd frontend && npx vitest run src/components/schedule/ShiftBlock.test.tsx 2>&1 | tail -5
```

Expected: `Tests 6 passed (6)`.

- [ ] **Step 9.5: Create AlertStrip**

Create `frontend/src/components/schedule/AlertStrip.tsx`:

```tsx
interface AlertStripProps {
  redCount: number
  yellowCount: number
  uncoveredCount: number
  lateClockInCount: number
}

interface Chip {
  label: string
  count: number
  borderColor: string
  textColor: string
}

export function AlertStrip({ redCount, yellowCount, uncoveredCount, lateClockInCount }: AlertStripProps) {
  const chips: Chip[] = [
    { label: 'RED EVV', count: redCount, borderColor: '#dc2626', textColor: '#dc2626' },
    { label: 'YELLOW EVV', count: yellowCount, borderColor: '#ca8a04', textColor: '#ca8a04' },
    { label: 'Uncovered', count: uncoveredCount, borderColor: '#94a3b8', textColor: '#747480' },
    { label: 'Late clock-in', count: lateClockInCount, borderColor: '#ca8a04', textColor: '#ca8a04' },
  ].filter((c) => c.count > 0)

  if (chips.length === 0) return null

  return (
    <div className="flex items-center gap-3 px-6 py-2 bg-surface border-b border-border">
      <span className="text-[11px] text-text-secondary font-medium">Today:</span>
      {chips.map((chip) => (
        <span
          key={chip.label}
          className="flex items-center gap-1.5 text-[11px] font-semibold px-2 py-0.5 bg-white"
          style={{
            borderLeft: `3px solid ${chip.borderColor}`,
            color: chip.textColor,
          }}
        >
          <span>{chip.count}</span>
          <span>{chip.label}</span>
        </span>
      ))}
    </div>
  )
}
```

- [ ] **Step 9.6: Create WeekCalendar**

Create `frontend/src/components/schedule/WeekCalendar.tsx`:

```tsx
import { useMemo } from 'react'
import type { ShiftDetailResponse, EvvComplianceStatus } from '../../types/api'
import { ShiftBlock } from './ShiftBlock'

const CALENDAR_START_HOUR = 6   // 6am
const CALENDAR_END_HOUR = 22    // 10pm
const PX_PER_HOUR = 60
const TOTAL_HOURS = CALENDAR_END_HOUR - CALENDAR_START_HOUR

const HOURS = Array.from({ length: TOTAL_HOURS }, (_, i) => CALENDAR_START_HOUR + i)

function getWeekDays(weekStart: Date): Date[] {
  return Array.from({ length: 7 }, (_, i) => {
    const d = new Date(weekStart)
    d.setDate(d.getDate() + i)
    return d
  })
}

function toMinutesSince6am(iso: string): number {
  const d = new Date(iso.includes('T') ? iso : iso + 'T00:00:00')
  return d.getHours() * 60 + d.getMinutes() - CALENDAR_START_HOUR * 60
}

function sameDay(isoA: string, dateB: Date): boolean {
  const a = new Date(isoA.includes('T') ? isoA : isoA + 'T00:00:00')
  return (
    a.getFullYear() === dateB.getFullYear() &&
    a.getMonth() === dateB.getMonth() &&
    a.getDate() === dateB.getDate()
  )
}

function evvStatus(shift: ShiftDetailResponse): EvvComplianceStatus {
  return shift.evv?.complianceStatus ?? 'GREY'
}

const DAY_NAMES = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']

function isToday(d: Date): boolean {
  const now = new Date()
  return (
    d.getFullYear() === now.getFullYear() &&
    d.getMonth() === now.getMonth() &&
    d.getDate() === now.getDate()
  )
}

interface WeekCalendarProps {
  weekStart: Date
  shifts: ShiftDetailResponse[]
  clientMap: Map<string, { firstName: string; lastName: string }>
  caregiverMap: Map<string, { firstName: string; lastName: string }>
  onShiftClick: (shiftId: string) => void
  onEmptySlotClick: (date: Date, hour: number) => void
}

export function WeekCalendar({
  weekStart,
  shifts,
  clientMap,
  caregiverMap,
  onShiftClick,
  onEmptySlotClick,
}: WeekCalendarProps) {
  const days = useMemo(() => getWeekDays(weekStart), [weekStart])

  return (
    <div className="flex overflow-auto" style={{ height: `${TOTAL_HOURS * PX_PER_HOUR + 40}px` }}>
      {/* Time column */}
      <div className="shrink-0 w-16 bg-surface border-r border-border">
        {/* Header spacer */}
        <div className="h-10 border-b border-border" />
        {/* Time labels */}
        {HOURS.map((h) => (
          <div
            key={h}
            className="relative"
            style={{ height: PX_PER_HOUR }}
          >
            <span
              className="absolute -top-2 right-2 text-[10px] text-text-secondary"
            >
              {h === 12 ? '12pm' : h < 12 ? `${h}am` : `${h - 12}pm`}
            </span>
          </div>
        ))}
      </div>

      {/* Day columns */}
      {days.map((day, dayIdx) => {
        const today = isToday(day)
        const isWeekend = dayIdx >= 5
        const dayShifts = shifts.filter((s) => sameDay(s.scheduledStart, day))

        return (
          <div
            key={day.toISOString()}
            className="flex-1 border-r border-border min-w-0"
            style={{ background: today ? '#f0f8ff' : isWeekend ? '#f9f9fc' : '#ffffff' }}
          >
            {/* Day header */}
            <div className="h-10 flex flex-col items-center justify-center border-b border-border">
              <span className="text-[9px] font-bold uppercase text-text-secondary">
                {DAY_NAMES[dayIdx]}
              </span>
              <span
                className={[
                  'text-[13px] font-semibold w-6 h-6 flex items-center justify-center rounded-full',
                  today ? 'text-white' : 'text-text-primary',
                ].join(' ')}
                style={today ? { background: '#1a9afa' } : undefined}
              >
                {day.getDate()}
              </span>
            </div>

            {/* Grid + shift blocks */}
            <div
              className="relative"
              style={{ height: TOTAL_HOURS * PX_PER_HOUR }}
            >
              {/* Hour grid lines */}
              {HOURS.map((h) => (
                <div
                  key={h}
                  className="absolute left-0 right-0 border-t border-border"
                  style={{ top: (h - CALENDAR_START_HOUR) * PX_PER_HOUR }}
                />
              ))}

              {/* Empty slot click targets (one per hour) */}
              {HOURS.map((h) => (
                <button
                  key={h}
                  type="button"
                  className="absolute left-0 right-0 opacity-0 hover:opacity-100 hover:bg-blue-50 transition-opacity"
                  style={{
                    top: (h - CALENDAR_START_HOUR) * PX_PER_HOUR,
                    height: PX_PER_HOUR,
                  }}
                  onClick={() => onEmptySlotClick(day, h)}
                  aria-label={`New shift at ${h}:00`}
                />
              ))}

              {/* Shift blocks */}
              {dayShifts.map((shift) => {
                const client = clientMap.get(shift.clientId)
                const caregiver = shift.caregiverId ? caregiverMap.get(shift.caregiverId) : null
                const startMin = toMinutesSince6am(shift.scheduledStart)
                const endMin = toMinutesSince6am(shift.scheduledEnd)
                const top = Math.max(0, (startMin / 60) * PX_PER_HOUR)
                const height = Math.max(24, ((endMin - startMin) / 60) * PX_PER_HOUR)

                return (
                  <ShiftBlock
                    key={shift.id}
                    shiftId={shift.id}
                    clientName={client ? `${client.firstName} ${client.lastName}` : shift.clientId}
                    caregiverName={caregiver ? `${caregiver.firstName} ${caregiver.lastName}` : null}
                    evvStatus={evvStatus(shift)}
                    top={top}
                    height={height}
                    onClick={() => onShiftClick(shift.id)}
                  />
                )
              })}
            </div>
          </div>
        )
      })}
    </div>
  )
}
```

- [ ] **Step 9.7: Create SchedulePage**

Create `frontend/src/components/schedule/SchedulePage.tsx`:

```tsx
import { useState } from 'react'
import { WeekCalendar } from './WeekCalendar'
import { AlertStrip } from './AlertStrip'
import { mockShifts, mockClientMap, mockCaregiverMap, mockDashboard } from '../../mock/data'
import { usePanelStore } from '../../store/panelStore'

function getMonday(d: Date): Date {
  const date = new Date(d)
  const day = date.getDay()
  const diff = day === 0 ? -6 : 1 - day
  date.setDate(date.getDate() + diff)
  date.setHours(0, 0, 0, 0)
  return date
}

function formatWeekRange(monday: Date): string {
  const sunday = new Date(monday)
  sunday.setDate(sunday.getDate() + 6)
  const fmt = (d: Date) =>
    d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
  return `${fmt(monday)} – ${fmt(sunday)}`
}

export function SchedulePage() {
  const [weekStart, setWeekStart] = useState(() => getMonday(new Date()))
  const { openPanel } = usePanelStore()

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

  function handleNewShift(date: Date, hour: number) {
    const dateStr = date.toISOString().split('T')[0]
    const timeStr = `${String(hour).padStart(2, '0')}:00`
    openPanel('newShift', undefined, {
      prefill: { date: dateStr, time: timeStr },
      backLabel: '← Schedule',
    })
  }

  return (
    <div className="flex flex-col h-full">
      {/* Top bar */}
      <div className="flex items-center gap-4 px-6 py-4 bg-white border-b border-border">
        <h1 className="text-[16px] font-bold tracking-[-0.02em] text-dark mr-2">Schedule</h1>
        <span className="text-[13px] text-text-secondary">{formatWeekRange(weekStart)}</span>
        <div className="ml-auto flex items-center gap-2">
          <button
            type="button"
            onClick={prevWeek}
            className="px-3 py-1.5 text-[12px] font-semibold border border-border text-dark hover:brightness-95 transition-[filter]"
          >
            ←
          </button>
          <button
            type="button"
            onClick={nextWeek}
            className="px-3 py-1.5 text-[12px] font-semibold border border-border text-dark hover:brightness-95 transition-[filter]"
          >
            →
          </button>
          <button
            type="button"
            className="px-3 py-1.5 text-[12px] font-semibold border border-border text-dark hover:brightness-95 ml-2"
            onClick={() => alert('Broadcast Open: confirms then broadcasts all unassigned shifts')}
          >
            Broadcast Open
          </button>
          <button
            type="button"
            className="px-4 py-1.5 text-[12px] font-bold bg-dark text-white hover:brightness-110"
            onClick={() => openPanel('newShift', undefined, { backLabel: '← Schedule' })}
          >
            + New Shift
          </button>
        </div>
      </div>

      {/* Alert strip */}
      <AlertStrip
        redCount={mockDashboard.redEvvCount}
        yellowCount={mockDashboard.yellowEvvCount}
        uncoveredCount={mockDashboard.uncoveredCount}
        lateClockInCount={0}
      />

      {/* Calendar */}
      <div className="flex-1 overflow-auto">
        <WeekCalendar
          weekStart={weekStart}
          shifts={mockShifts}
          clientMap={mockClientMap}
          caregiverMap={mockCaregiverMap}
          onShiftClick={(id) => openPanel('shift', id, { backLabel: '← Schedule' })}
          onEmptySlotClick={handleNewShift}
        />
      </div>
    </div>
  )
}
```

- [ ] **Step 9.8: Commit**

```bash
cd ..
git add frontend/src/components/schedule/
git commit -m "feat(frontend): add Schedule page — WeekCalendar, ShiftBlock, AlertStrip"
```

---

### Task 10: Shift Detail Panel and New Shift Panel

**Files:**
- Create: `frontend/src/components/schedule/ShiftDetailPanel.tsx`
- Create: `frontend/src/components/schedule/NewShiftPanel.tsx`

- [ ] **Step 10.1: Create ShiftDetailPanel**

Create `frontend/src/components/schedule/ShiftDetailPanel.tsx`:

```tsx
import type { EvvComplianceStatus, ShiftDetailResponse } from '../../types/api'
import { mockShifts, mockClientMap, mockCaregiverMap } from '../../mock/data'
import { usePanelStore } from '../../store/panelStore'

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

const EVV_LABEL: Record<EvvComplianceStatus, string> = {
  RED: 'Non-Compliant',
  YELLOW: 'Attention Required',
  GREEN: 'Compliant',
  GREY: 'Not Started',
  EXEMPT: 'Exempt',
  PORTAL_SUBMIT: 'Portal Submit',
}

function formatTime(iso: string | null): string {
  if (!iso) return '—'
  return new Date(iso).toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' })
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('en-US', {
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
  const { closePanel, openPanel } = usePanelStore()
  const shift: ShiftDetailResponse | undefined = mockShifts.find((s) => s.id === shiftId)

  if (!shift) {
    return (
      <div className="p-8 text-text-secondary">
        <button type="button" className="text-blue text-[13px] mb-4" onClick={closePanel}>
          {backLabel}
        </button>
        <p>Shift not found.</p>
      </div>
    )
  }

  const client = mockClientMap.get(shift.clientId)
  const caregiver = shift.caregiverId ? mockCaregiverMap.get(shift.caregiverId) : null
  const status = shift.evv?.complianceStatus ?? 'GREY'

  return (
    <div className="flex flex-col h-full">
      {/* Header */}
      <div className="px-6 py-4 border-b border-border">
        <button
          type="button"
          className="text-[13px] mb-2 hover:underline"
          style={{ color: '#1a9afa' }}
          onClick={closePanel}
        >
          {backLabel}
        </button>
        <h2 className="text-[16px] font-bold text-dark">
          {client ? `${client.firstName} ${client.lastName}` : 'Unknown Client'}
        </h2>
        <p className="text-[12px] text-text-secondary mt-0.5">
          {formatDate(shift.scheduledStart)} · {formatTime(shift.scheduledStart)} – {formatTime(shift.scheduledEnd)} · PCS
        </p>
      </div>

      {/* EVV Status badge */}
      <div
        className="mx-6 mt-4 px-4 py-3 text-[13px] font-semibold"
        style={{ background: EVV_BG[status], color: EVV_TEXT[status] }}
      >
        {EVV_LABEL[status]}
        {shift.evv === null && ' — Visit not yet started'}
      </div>

      {/* Body */}
      <div className="flex-1 overflow-auto px-6 py-4">
        <div className="grid grid-cols-2 gap-6">
          {/* Left: Visit Details */}
          <div>
            <h3 className="text-[9px] font-bold uppercase tracking-[0.1em] text-text-secondary mb-3">
              Visit Details
            </h3>
            <div className="space-y-2">
              <div>
                <div className="text-[10px] text-text-secondary">Client</div>
                <div className="text-[13px] text-dark">
                  {client ? `${client.firstName} ${client.lastName}` : '—'}
                </div>
              </div>
              <div>
                <div className="text-[10px] text-text-secondary">Caregiver</div>
                <div className="text-[13px] text-dark">
                  {caregiver ? `${caregiver.firstName} ${caregiver.lastName}` : 'Unassigned'}
                </div>
              </div>
              <div>
                <div className="text-[10px] text-text-secondary">Service</div>
                <div className="text-[13px] text-dark">PCS</div>
              </div>
              <div>
                <div className="text-[10px] text-text-secondary">Status</div>
                <div className="text-[13px] text-dark">{shift.status.replace('_', ' ')}</div>
              </div>
            </div>
          </div>

          {/* Right: EVV Record */}
          <div>
            <h3 className="text-[9px] font-bold uppercase tracking-[0.1em] text-text-secondary mb-3">
              EVV Record
            </h3>
            <div className="space-y-2">
              <div>
                <div className="text-[10px] text-text-secondary">Clock-in</div>
                <div className="text-[13px] text-dark">{formatTime(shift.evv?.timeIn ?? null)}</div>
              </div>
              <div>
                <div className="text-[10px] text-text-secondary">Clock-out</div>
                <div className="text-[13px] text-dark">{formatTime(shift.evv?.timeOut ?? null)}</div>
              </div>
              <div>
                <div className="text-[10px] text-text-secondary">Method</div>
                <div className="text-[13px] text-dark">{shift.evv?.verificationMethod ?? '—'}</div>
              </div>
              <div>
                <div className="text-[10px] text-text-secondary">Offline</div>
                <div className="text-[13px] text-dark">
                  {shift.evv?.capturedOffline ? 'Yes' : 'No'}
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* AI Candidates (shown when OPEN) */}
        {shift.status === 'OPEN' && (
          <div className="mt-6">
            <h3 className="text-[9px] font-bold uppercase tracking-[0.1em] text-text-secondary mb-3">
              AI Match — Top Candidates
            </h3>
            {[
              { rank: 1, name: 'Maria Garcia', reason: '0.8 mi · 12 prior visits · no OT risk' },
              { rank: 2, name: 'Sarah Davis', reason: '1.4 mi · 5 prior visits · no OT risk' },
            ].map((c) => (
              <div key={c.rank} className="flex items-center gap-3 py-2 border-b border-border">
                <span
                  className="w-6 h-6 rounded-full flex items-center justify-center text-[11px] font-bold text-white shrink-0"
                  style={{ background: c.rank === 1 ? '#1a9afa' : '#94a3b8' }}
                >
                  {c.rank}
                </span>
                <div className="flex-1">
                  <div className="text-[13px] font-medium text-dark">{c.name}</div>
                  <div className="text-[11px] text-text-secondary">{c.reason}</div>
                </div>
                <button type="button" className="text-[12px] font-semibold" style={{ color: '#1a9afa' }}>
                  Assign →
                </button>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Footer actions */}
      <div className="px-6 py-4 border-t border-border flex items-center gap-3">
        {shift.status === 'OPEN' && (
          <button
            type="button"
            className="px-4 py-2 text-[12px] font-bold bg-dark text-white hover:brightness-110"
          >
            Assign Caregiver
          </button>
        )}
        {(shift.status === 'COMPLETED' || shift.status === 'IN_PROGRESS') &&
          status === 'RED' && (
            <>
              <button
                type="button"
                className="px-4 py-2 text-[12px] font-bold bg-dark text-white hover:brightness-110"
              >
                Add Manual Clock-in
              </button>
              <button
                type="button"
                className="px-4 py-2 text-[12px] font-semibold border border-border text-dark"
              >
                Edit Shift
              </button>
              <button
                type="button"
                className="ml-auto px-4 py-2 text-[12px] font-semibold text-red-600 border border-red-200"
              >
                Mark as Missed
              </button>
            </>
          )}
        {shift.status === 'ASSIGNED' && (
          <button
            type="button"
            className="px-4 py-2 text-[12px] font-semibold border border-border text-dark"
          >
            Edit Shift
          </button>
        )}
        {shift.status === 'COMPLETED' && status === 'GREEN' && (
          <>
            <button
              type="button"
              className="px-4 py-2 text-[12px] font-semibold border border-border text-dark"
            >
              Edit Shift
            </button>
            <button
              type="button"
              className="px-4 py-2 text-[12px] font-semibold border border-border text-dark"
            >
              View Care Notes
            </button>
          </>
        )}
      </div>
    </div>
  )
}
```

- [ ] **Step 10.2: Create NewShiftPanel**

Create `frontend/src/components/schedule/NewShiftPanel.tsx`:

```tsx
import { useForm } from 'react-hook-form'
import { mockClients } from '../../mock/data'
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
  const { closePanel } = usePanelStore()
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({
    defaultValues: {
      date: prefill?.date ?? new Date().toISOString().split('T')[0],
      startTime: prefill?.time ?? '09:00',
      endTime: '13:00',
    },
  })

  function onSubmit(values: FormValues) {
    // Phase 4: replace with API call
    console.log('Creating shift:', values)
    alert(`Mock: shift for client ${values.clientId} on ${values.date} created.\n\nPhase 4 will wire this to POST /shifts.`)
    closePanel()
  }

  return (
    <div className="flex flex-col h-full">
      {/* Header */}
      <div className="px-6 py-4 border-b border-border">
        <button
          type="button"
          className="text-[13px] mb-2 hover:underline"
          style={{ color: '#1a9afa' }}
          onClick={closePanel}
        >
          {backLabel}
        </button>
        <h2 className="text-[16px] font-bold text-dark">New Shift</h2>
      </div>

      {/* Form */}
      <form
        onSubmit={handleSubmit(onSubmit)}
        className="flex-1 overflow-auto px-6 py-4 space-y-4"
      >
        {/* Client */}
        <div>
          <label className="block text-[10px] font-bold uppercase tracking-[0.1em] text-text-secondary mb-1">
            Client
          </label>
          <select
            {...register('clientId', { required: 'Client is required' })}
            className="w-full border border-border px-3 py-2 text-[13px] text-dark bg-white"
          >
            <option value="">Select client…</option>
            {mockClients.map((c) => (
              <option key={c.id} value={c.id}>
                {c.firstName} {c.lastName}
              </option>
            ))}
          </select>
          {errors.clientId && (
            <p className="text-[11px] text-red-600 mt-1">{errors.clientId.message}</p>
          )}
        </div>

        {/* Service Type (static for Phase 1) */}
        <div>
          <label className="block text-[10px] font-bold uppercase tracking-[0.1em] text-text-secondary mb-1">
            Service Type
          </label>
          <select
            {...register('serviceTypeId', { required: 'Service type is required' })}
            className="w-full border border-border px-3 py-2 text-[13px] text-dark bg-white"
          >
            <option value="">Select service type…</option>
            <option value="st000000-0000-0000-0000-000000000001">PCS — Personal Care Services</option>
          </select>
        </div>

        {/* Date */}
        <div>
          <label className="block text-[10px] font-bold uppercase tracking-[0.1em] text-text-secondary mb-1">
            Date
          </label>
          <input
            type="date"
            {...register('date', { required: 'Date is required' })}
            className="w-full border border-border px-3 py-2 text-[13px] text-dark"
          />
        </div>

        {/* Start / End time */}
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block text-[10px] font-bold uppercase tracking-[0.1em] text-text-secondary mb-1">
              Start Time
            </label>
            <input
              type="time"
              {...register('startTime', { required: true })}
              className="w-full border border-border px-3 py-2 text-[13px] text-dark"
            />
          </div>
          <div>
            <label className="block text-[10px] font-bold uppercase tracking-[0.1em] text-text-secondary mb-1">
              End Time
            </label>
            <input
              type="time"
              {...register('endTime', { required: true })}
              className="w-full border border-border px-3 py-2 text-[13px] text-dark"
            />
          </div>
        </div>

        {/* Caregiver (optional) */}
        <div>
          <label className="block text-[10px] font-bold uppercase tracking-[0.1em] text-text-secondary mb-1">
            Caregiver (optional)
          </label>
          <select
            {...register('caregiverId')}
            className="w-full border border-border px-3 py-2 text-[13px] text-dark bg-white"
          >
            <option value="">Leave unassigned (broadcast after)</option>
          </select>
          <p className="text-[10px] text-text-secondary mt-1">
            Phase 4 will populate this list from the API.
          </p>
        </div>

        {/* Footer */}
        <div className="pt-4 border-t border-border flex gap-3">
          <button
            type="submit"
            className="px-4 py-2 text-[12px] font-bold bg-dark text-white hover:brightness-110"
          >
            Save Shift
          </button>
          <button
            type="button"
            onClick={closePanel}
            className="px-4 py-2 text-[12px] font-semibold border border-border text-dark"
          >
            Cancel
          </button>
        </div>
      </form>
    </div>
  )
}
```

- [ ] **Step 10.3: Commit**

```bash
cd ..
git add frontend/src/components/schedule/ShiftDetailPanel.tsx \
  frontend/src/components/schedule/NewShiftPanel.tsx
git commit -m "feat(frontend): add ShiftDetailPanel and NewShiftPanel (mock)"
```

---

### Task 11: Dashboard Page

**Files:**
- Create: `frontend/src/components/dashboard/DashboardPage.tsx`
- Create: `frontend/src/components/dashboard/StatTiles.tsx`
- Create: `frontend/src/components/dashboard/VisitList.tsx`
- Create: `frontend/src/components/dashboard/AlertsColumn.tsx`

- [ ] **Step 11.1: Create StatTiles**

Create `frontend/src/components/dashboard/StatTiles.tsx`:

```tsx
interface StatTilesProps {
  redEvvCount: number
  yellowEvvCount: number
  uncoveredCount: number
  onTrackCount: number
}

interface Tile {
  label: string
  sublabel: string
  count: number
  numColor: string
  bgColor?: string
}

export function StatTiles({ redEvvCount, yellowEvvCount, uncoveredCount, onTrackCount }: StatTilesProps) {
  const tiles: Tile[] = [
    {
      label: 'RED EVV',
      sublabel: 'Missing elements',
      count: redEvvCount,
      numColor: '#dc2626',
      bgColor: redEvvCount > 0 ? '#fef2f2' : undefined,
    },
    {
      label: 'YELLOW EVV',
      sublabel: 'Attention needed',
      count: yellowEvvCount,
      numColor: '#ca8a04',
    },
    {
      label: 'UNCOVERED',
      sublabel: 'No caregiver',
      count: uncoveredCount,
      numColor: '#94a3b8',
    },
    {
      label: 'ON TRACK',
      sublabel: 'Compliant',
      count: onTrackCount,
      numColor: '#16a34a',
    },
  ]

  return (
    <div className="grid grid-cols-4 border-b border-border">
      {tiles.map((tile, i) => (
        <div
          key={tile.label}
          className={['px-6 py-5', i < 3 ? 'border-r border-border' : ''].join(' ')}
          style={{ background: tile.bgColor ?? '#f6f6fa' }}
        >
          <div
            className="text-[28px] font-bold leading-none"
            style={{ color: tile.numColor }}
          >
            {tile.count}
          </div>
          <div className="text-[10px] font-bold uppercase tracking-[0.08em] text-dark mt-1">
            {tile.label}
          </div>
          <div className="text-[10px] text-text-secondary">{tile.sublabel}</div>
        </div>
      ))}
    </div>
  )
}
```

- [ ] **Step 11.2: Create VisitList**

Create `frontend/src/components/dashboard/VisitList.tsx`:

```tsx
import type { DashboardVisitRow, EvvComplianceStatus } from '../../types/api'
import { usePanelStore } from '../../store/panelStore'

const STATUS_BORDER: Record<EvvComplianceStatus, string> = {
  RED: '#dc2626',
  YELLOW: '#ca8a04',
  GREEN: '#16a34a',
  GREY: '#94a3b8',
  EXEMPT: '#94a3b8',
  PORTAL_SUBMIT: '#16a34a',
}

const STATUS_PILL_BG: Record<EvvComplianceStatus, string> = {
  RED: '#fef2f2',
  YELLOW: '#fefce8',
  GREEN: '#f0fdf4',
  GREY: '#f8fafc',
  EXEMPT: '#f8fafc',
  PORTAL_SUBMIT: '#f0fdf4',
}

const STATUS_PILL_COLOR: Record<EvvComplianceStatus, string> = {
  RED: '#dc2626',
  YELLOW: '#ca8a04',
  GREEN: '#16a34a',
  GREY: '#94a3b8',
  EXEMPT: '#94a3b8',
  PORTAL_SUBMIT: '#16a34a',
}

function formatTime(iso: string): string {
  return new Date(iso).toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' })
}

interface VisitListProps {
  visits: DashboardVisitRow[]
}

export function VisitList({ visits }: VisitListProps) {
  const { openPanel } = usePanelStore()

  if (visits.length === 0) {
    return <p className="px-6 py-8 text-text-secondary text-[13px]">No visits today.</p>
  }

  return (
    <div>
      {visits.map((row) => (
        <button
          key={row.shiftId}
          type="button"
          onClick={() => openPanel('shift', row.shiftId, { backLabel: '← Dashboard' })}
          className="w-full flex items-center gap-3 px-6 py-3 border-b border-border hover:bg-surface text-left transition-colors"
          style={{ borderLeft: `3px solid ${STATUS_BORDER[row.evvStatus]}` }}
        >
          {/* EVV dot */}
          <span
            className="w-2 h-2 rounded-full shrink-0"
            style={{ background: STATUS_BORDER[row.evvStatus] }}
          />
          {/* Client + caregiver */}
          <div className="flex-1 min-w-0">
            <div className="text-[13px] font-semibold text-dark truncate">
              {row.clientFirstName} {row.clientLastName}
            </div>
            <div className="text-[11px] text-text-secondary truncate">
              {row.caregiverFirstName
                ? `${row.caregiverFirstName} ${row.caregiverLastName} · ${row.serviceTypeName}`
                : `Unassigned · ${row.serviceTypeName}`}
            </div>
          </div>
          {/* Time */}
          <div className="text-[11px] text-text-secondary shrink-0">
            {formatTime(row.scheduledStart)} – {formatTime(row.scheduledEnd)}
          </div>
          {/* EVV pill */}
          <span
            className="text-[10px] font-bold px-2 py-0.5 shrink-0"
            style={{
              background: STATUS_PILL_BG[row.evvStatus],
              color: STATUS_PILL_COLOR[row.evvStatus],
            }}
          >
            {row.evvStatus}
          </span>
        </button>
      ))}
    </div>
  )
}
```

- [ ] **Step 11.3: Create AlertsColumn**

Create `frontend/src/components/dashboard/AlertsColumn.tsx`:

```tsx
import type { DashboardAlert } from '../../types/api'
import { useNavigate } from 'react-router-dom'

function isUrgent(dueDate: string): boolean {
  const days = (new Date(dueDate).getTime() - Date.now()) / (1000 * 60 * 60 * 24)
  return days <= 7
}

interface AlertsColumnProps {
  alerts: DashboardAlert[]
}

export function AlertsColumn({ alerts }: AlertsColumnProps) {
  const navigate = useNavigate()

  if (alerts.length === 0) {
    return (
      <div className="p-4 text-[12px] text-text-secondary">No active alerts.</div>
    )
  }

  return (
    <div>
      <div className="px-4 py-3 border-b border-border">
        <span className="text-[9px] font-bold uppercase tracking-[0.1em] text-text-secondary">
          Alerts
        </span>
      </div>
      <div>
        {alerts.map((alert) => {
          const urgent = isUrgent(alert.dueDate)
          return (
            <button
              key={`${alert.resourceId}-${alert.type}`}
              type="button"
              onClick={() =>
                navigate(
                  alert.resourceType === 'CAREGIVER'
                    ? `/caregivers`
                    : `/clients`
                )
              }
              className="w-full text-left px-4 py-3 border-b border-border hover:bg-surface transition-colors"
            >
              <div
                className="text-[12px] font-semibold truncate"
                style={{ color: urgent ? '#dc2626' : '#1a1a24' }}
              >
                {alert.subject}
              </div>
              <div className="text-[11px] text-text-secondary mt-0.5">{alert.detail}</div>
              <div className="text-[10px] mt-0.5" style={{ color: urgent ? '#dc2626' : '#94a3b8' }}>
                Due {new Date(alert.dueDate).toLocaleDateString('en-US', { month: 'short', day: 'numeric' })}
              </div>
            </button>
          )
        })}
      </div>
    </div>
  )
}
```

- [ ] **Step 11.4: Create DashboardPage**

Create `frontend/src/components/dashboard/DashboardPage.tsx`:

```tsx
import { mockDashboard } from '../../mock/data'
import { StatTiles } from './StatTiles'
import { VisitList } from './VisitList'
import { AlertsColumn } from './AlertsColumn'

export function DashboardPage() {
  const data = mockDashboard
  const today = new Date().toLocaleDateString('en-US', {
    weekday: 'long',
    month: 'long',
    day: 'numeric',
    year: 'numeric',
  })

  return (
    <div className="flex flex-col h-full">
      {/* Top bar */}
      <div className="flex items-center px-6 py-4 bg-white border-b border-border">
        <h1 className="text-[16px] font-bold tracking-[-0.02em] text-dark">Dashboard</h1>
        <span className="ml-3 text-[13px] text-text-secondary">{today}</span>
      </div>

      {/* Stat tiles */}
      <StatTiles
        redEvvCount={data.redEvvCount}
        yellowEvvCount={data.yellowEvvCount}
        uncoveredCount={data.uncoveredCount}
        onTrackCount={data.onTrackCount}
      />

      {/* Main area + alerts column */}
      <div className="flex flex-1 overflow-hidden">
        {/* Visit list */}
        <div className="flex-1 overflow-auto">
          <VisitList visits={data.visits} />
        </div>

        {/* Alerts column — fixed 220px */}
        <div
          className="shrink-0 overflow-auto border-l border-border"
          style={{ width: 220 }}
        >
          <AlertsColumn alerts={data.alerts} />
        </div>
      </div>
    </div>
  )
}
```

- [ ] **Step 11.5: Commit**

```bash
cd ..
git add frontend/src/components/dashboard/
git commit -m "feat(frontend): add Dashboard page — StatTiles, VisitList, AlertsColumn"
```

---

### Task 12: Clients Page + ClientDetailPanel

**Files:**
- Create: `frontend/src/components/clients/ClientsPage.tsx`
- Create: `frontend/src/components/clients/ClientsTable.tsx`
- Create: `frontend/src/components/clients/ClientDetailPanel.tsx`

- [ ] **Step 12.1: Create ClientsTable**

Create `frontend/src/components/clients/ClientsTable.tsx`:

```tsx
import type { ClientResponse } from '../../types/api'
import { usePanelStore } from '../../store/panelStore'

interface ClientsTableProps {
  clients: ClientResponse[]
  search: string
}

export function ClientsTable({ clients, search }: ClientsTableProps) {
  const { openPanel } = usePanelStore()

  const filtered = clients.filter((c) => {
    const name = `${c.firstName} ${c.lastName}`.toLowerCase()
    return name.includes(search.toLowerCase())
  })

  if (filtered.length === 0) {
    return <p className="px-6 py-8 text-text-secondary text-[13px]">No clients found.</p>
  }

  return (
    <table className="w-full text-[13px]">
      <thead>
        <tr className="border-b border-border bg-surface">
          <th className="text-left px-6 py-2 text-[9px] font-bold uppercase tracking-[0.1em] text-text-secondary">
            Client
          </th>
          <th className="text-left px-4 py-2 text-[9px] font-bold uppercase tracking-[0.1em] text-text-secondary">
            Medicaid ID
          </th>
          <th className="text-left px-4 py-2 text-[9px] font-bold uppercase tracking-[0.1em] text-text-secondary">
            State
          </th>
          <th className="text-left px-4 py-2 text-[9px] font-bold uppercase tracking-[0.1em] text-text-secondary">
            Status
          </th>
        </tr>
      </thead>
      <tbody>
        {filtered.map((client) => (
          <tr
            key={client.id}
            className="border-b border-border hover:bg-surface cursor-pointer"
            onClick={() => openPanel('client', client.id, { backLabel: '← Clients' })}
          >
            <td className="px-6 py-3 font-medium text-dark">
              {client.firstName} {client.lastName}
            </td>
            <td className="px-4 py-3 text-text-secondary">{client.medicaidId ?? '—'}</td>
            <td className="px-4 py-3 text-text-secondary">{client.serviceState ?? '—'}</td>
            <td className="px-4 py-3">
              <span
                className="text-[11px] font-semibold px-2 py-0.5"
                style={{
                  background: client.status === 'ACTIVE' ? '#f0fdf4' : '#f8fafc',
                  color: client.status === 'ACTIVE' ? '#16a34a' : '#94a3b8',
                }}
              >
                {client.status}
              </span>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}
```

- [ ] **Step 12.2: Create ClientsPage**

Create `frontend/src/components/clients/ClientsPage.tsx`:

```tsx
import { useState } from 'react'
import { mockClients } from '../../mock/data'
import { usePanelStore } from '../../store/panelStore'
import { ClientsTable } from './ClientsTable'

export function ClientsPage() {
  const [search, setSearch] = useState('')
  const { openPanel } = usePanelStore()

  return (
    <div className="flex flex-col h-full">
      <div className="flex items-center gap-4 px-6 py-4 bg-white border-b border-border">
        <h1 className="text-[16px] font-bold tracking-[-0.02em] text-dark">Clients</h1>
        <input
          type="search"
          placeholder="Search by name…"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="ml-4 border border-border px-3 py-1.5 text-[13px] w-64 focus:outline-none focus:border-dark"
        />
        <button
          type="button"
          className="ml-auto px-4 py-1.5 text-[12px] font-bold bg-dark text-white hover:brightness-110"
          onClick={() => openPanel('client', undefined, { backLabel: '← Clients' })}
        >
          + Add Client
        </button>
      </div>
      <div className="flex-1 overflow-auto">
        <ClientsTable clients={mockClients} search={search} />
      </div>
    </div>
  )
}
```

- [ ] **Step 12.3: Create ClientDetailPanel**

Create `frontend/src/components/clients/ClientDetailPanel.tsx`:

```tsx
import { useState } from 'react'
import { mockClients, mockAuthorizations } from '../../mock/data'
import { usePanelStore } from '../../store/panelStore'

type Tab = 'overview' | 'carePlan' | 'authorizations' | 'documents' | 'familyPortal'

const TABS: { id: Tab; label: string }[] = [
  { id: 'overview', label: 'Overview' },
  { id: 'carePlan', label: 'Care Plan' },
  { id: 'authorizations', label: 'Authorizations' },
  { id: 'documents', label: 'Documents' },
  { id: 'familyPortal', label: 'Family Portal' },
]

interface ClientDetailPanelProps {
  clientId: string | undefined
  backLabel: string
}

export function ClientDetailPanel({ clientId, backLabel }: ClientDetailPanelProps) {
  const { closePanel } = usePanelStore()
  const [activeTab, setActiveTab] = useState<Tab>('overview')
  const client = mockClients.find((c) => c.id === clientId)

  if (!client) {
    return (
      <div className="p-8">
        <button type="button" className="text-blue text-[13px] mb-4" onClick={closePanel}>
          {backLabel}
        </button>
        <p className="text-text-secondary">Client not found.</p>
      </div>
    )
  }

  const authorizations = mockAuthorizations.filter((a) => a.clientId === clientId)

  return (
    <div className="flex flex-col h-full">
      {/* Header */}
      <div className="px-6 py-4 border-b border-border">
        <button
          type="button"
          className="text-[13px] mb-2 hover:underline"
          style={{ color: '#1a9afa' }}
          onClick={closePanel}
        >
          {backLabel}
        </button>
        <h2 className="text-[16px] font-bold text-dark">
          {client.firstName} {client.lastName}
        </h2>
        <p className="text-[12px] text-text-secondary mt-0.5">
          DOB: {new Date(client.dateOfBirth).toLocaleDateString('en-US')} ·{' '}
          {client.medicaidId ?? 'No Medicaid ID'}
        </p>
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
        {activeTab === 'overview' && (
          <div className="grid grid-cols-2 gap-4">
            {[
              ['Phone', client.phone ?? '—'],
              ['Address', client.address ?? '—'],
              ['Service State', client.serviceState ?? '—'],
              ['Status', client.status],
              ['Preferred Language', client.preferredLanguages ?? '—'],
              ['No Pet Caregiver', client.noPetCaregiver ? 'Yes' : 'No'],
            ].map(([label, value]) => (
              <div key={label}>
                <div className="text-[10px] text-text-secondary">{label}</div>
                <div className="text-[13px] text-dark">{value}</div>
              </div>
            ))}
          </div>
        )}
        {activeTab === 'carePlan' && (
          <p className="text-text-secondary text-[13px]">Care plan — Phase 6 wires to real API.</p>
        )}
        {activeTab === 'authorizations' && (
          <div>
            {authorizations.length === 0 ? (
              <p className="text-text-secondary text-[13px]">No authorizations.</p>
            ) : (
              authorizations.map((auth) => {
                const pct = (auth.usedUnits / auth.authorizedUnits) * 100
                return (
                  <div key={auth.id} className="border border-border p-4 mb-3">
                    <div className="flex justify-between mb-2">
                      <span className="text-[13px] font-medium text-dark">Auth #{auth.authNumber}</span>
                      <span className="text-[12px] text-text-secondary">
                        {auth.usedUnits}/{auth.authorizedUnits} {auth.unitType.toLowerCase()}s used
                      </span>
                    </div>
                    <div className="w-full bg-border h-2">
                      <div
                        className="h-2"
                        style={{
                          width: `${Math.min(100, pct)}%`,
                          background: pct > 80 ? '#dc2626' : '#1a9afa',
                        }}
                      />
                    </div>
                    <div className="text-[10px] text-text-secondary mt-1">
                      {auth.startDate} – {auth.endDate}
                    </div>
                  </div>
                )
              })
            )}
          </div>
        )}
        {activeTab === 'documents' && (
          <p className="text-text-secondary text-[13px]">Documents — Phase 6 wires to real API.</p>
        )}
        {activeTab === 'familyPortal' && (
          <p className="text-text-secondary text-[13px]">Family portal — Phase 6 wires to real API.</p>
        )}
      </div>
    </div>
  )
}
```

- [ ] **Step 12.4: Commit**

```bash
cd ..
git add frontend/src/components/clients/
git commit -m "feat(frontend): add Clients page and ClientDetailPanel (mock)"
```

---

### Task 13: Caregivers Page + CaregiverDetailPanel

**Files:**
- Create: `frontend/src/components/caregivers/CaregiversPage.tsx`
- Create: `frontend/src/components/caregivers/CaregiversTable.tsx`
- Create: `frontend/src/components/caregivers/CaregiverDetailPanel.tsx`

- [ ] **Step 13.1: Create CaregiversTable**

Create `frontend/src/components/caregivers/CaregiversTable.tsx`:

```tsx
import type { CaregiverResponse } from '../../types/api'
import { usePanelStore } from '../../store/panelStore'

interface CaregiversTableProps {
  caregivers: CaregiverResponse[]
  search: string
}

export function CaregiversTable({ caregivers, search }: CaregiversTableProps) {
  const { openPanel } = usePanelStore()

  const filtered = caregivers.filter((c) => {
    const name = `${c.firstName} ${c.lastName}`.toLowerCase()
    return name.includes(search.toLowerCase())
  })

  if (filtered.length === 0) {
    return <p className="px-6 py-8 text-text-secondary text-[13px]">No caregivers found.</p>
  }

  return (
    <table className="w-full text-[13px]">
      <thead>
        <tr className="border-b border-border bg-surface">
          {['Caregiver', 'Email', 'Phone', 'Status', 'Hire Date'].map((h) => (
            <th
              key={h}
              className="text-left px-6 py-2 text-[9px] font-bold uppercase tracking-[0.1em] text-text-secondary first:pl-6"
            >
              {h}
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        {filtered.map((cg) => (
          <tr
            key={cg.id}
            className="border-b border-border hover:bg-surface cursor-pointer"
            onClick={() => openPanel('caregiver', cg.id, { backLabel: '← Caregivers' })}
          >
            <td className="px-6 py-3 font-medium text-dark">
              {cg.firstName} {cg.lastName}
            </td>
            <td className="px-6 py-3 text-text-secondary">{cg.email}</td>
            <td className="px-6 py-3 text-text-secondary">{cg.phone ?? '—'}</td>
            <td className="px-6 py-3">
              <span
                className="text-[11px] font-semibold px-2 py-0.5"
                style={{
                  background: cg.status === 'ACTIVE' ? '#f0fdf4' : '#f8fafc',
                  color: cg.status === 'ACTIVE' ? '#16a34a' : '#94a3b8',
                }}
              >
                {cg.status}
              </span>
            </td>
            <td className="px-6 py-3 text-text-secondary">
              {cg.hireDate
                ? new Date(cg.hireDate).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
                : '—'}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}
```

- [ ] **Step 13.2: Create CaregiversPage**

Create `frontend/src/components/caregivers/CaregiversPage.tsx`:

```tsx
import { useState } from 'react'
import { mockCaregivers } from '../../mock/data'
import { usePanelStore } from '../../store/panelStore'
import { CaregiversTable } from './CaregiversTable'

export function CaregiversPage() {
  const [search, setSearch] = useState('')
  const { openPanel } = usePanelStore()

  return (
    <div className="flex flex-col h-full">
      <div className="flex items-center gap-4 px-6 py-4 bg-white border-b border-border">
        <h1 className="text-[16px] font-bold tracking-[-0.02em] text-dark">Caregivers</h1>
        <input
          type="search"
          placeholder="Search by name…"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="ml-4 border border-border px-3 py-1.5 text-[13px] w-64 focus:outline-none focus:border-dark"
        />
        <button
          type="button"
          className="ml-auto px-4 py-1.5 text-[12px] font-bold bg-dark text-white hover:brightness-110"
          onClick={() => openPanel('caregiver', undefined, { backLabel: '← Caregivers' })}
        >
          + Add Caregiver
        </button>
      </div>
      <div className="flex-1 overflow-auto">
        <CaregiversTable caregivers={mockCaregivers} search={search} />
      </div>
    </div>
  )
}
```

- [ ] **Step 13.3: Create CaregiverDetailPanel**

Create `frontend/src/components/caregivers/CaregiverDetailPanel.tsx`:

```tsx
import { useState } from 'react'
import { mockCaregivers, mockCredentials } from '../../mock/data'
import { usePanelStore } from '../../store/panelStore'

type Tab = 'overview' | 'credentials' | 'backgroundChecks' | 'availability' | 'shiftHistory'

const TABS: { id: Tab; label: string }[] = [
  { id: 'overview', label: 'Overview' },
  { id: 'credentials', label: 'Credentials' },
  { id: 'backgroundChecks', label: 'Background Checks' },
  { id: 'availability', label: 'Availability' },
  { id: 'shiftHistory', label: 'Shift History' },
]

interface CaregiverDetailPanelProps {
  caregiverVId: string
  backLabel: string
}

export function CaregiverDetailPanel({ caregiverVId, backLabel }: CaregiverDetailPanelProps) {
  const { closePanel } = usePanelStore()
  const [activeTab, setActiveTab] = useState<Tab>('overview')
  const caregiver = mockCaregivers.find((c) => c.id === caregiverVId)

  if (!caregiver) {
    return (
      <div className="p-8">
        <button type="button" className="text-[13px] mb-4" style={{ color: '#1a9afa' }} onClick={closePanel}>
          {backLabel}
        </button>
        <p className="text-text-secondary">Caregiver not found.</p>
      </div>
    )
  }

  const credentials = mockCredentials.filter((c) => c.caregiverId === caregiverVId)

  function isExpiringSoon(expiryDate: string | null): boolean {
    if (!expiryDate) return false
    const days = (new Date(expiryDate).getTime() - Date.now()) / (1000 * 60 * 60 * 24)
    return days <= 30
  }

  return (
    <div className="flex flex-col h-full">
      {/* Header */}
      <div className="px-6 py-4 border-b border-border">
        <button
          type="button"
          className="text-[13px] mb-2 hover:underline"
          style={{ color: '#1a9afa' }}
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
        {activeTab === 'overview' && (
          <div className="grid grid-cols-2 gap-4">
            {[
              ['Phone', caregiver.phone ?? '—'],
              ['Address', caregiver.address ?? '—'],
              ['Hire Date', caregiver.hireDate ? new Date(caregiver.hireDate).toLocaleDateString('en-US') : '—'],
              ['Status', caregiver.status],
              ['Has Pet', caregiver.hasPet ? 'Yes' : 'No'],
            ].map(([label, value]) => (
              <div key={label}>
                <div className="text-[10px] text-text-secondary">{label}</div>
                <div className="text-[13px] text-dark">{value}</div>
              </div>
            ))}
          </div>
        )}
        {activeTab === 'credentials' && (
          <div>
            {credentials.length === 0 ? (
              <p className="text-text-secondary text-[13px]">No credentials on file.</p>
            ) : (
              credentials.map((cred) => {
                const expiring = isExpiringSoon(cred.expiryDate)
                return (
                  <div key={cred.id} className="flex items-center gap-3 py-3 border-b border-border">
                    <div className="flex-1">
                      <div
                        className="text-[13px] font-medium"
                        style={{ color: expiring ? '#dc2626' : '#1a1a24' }}
                      >
                        {cred.credentialType}
                      </div>
                      <div className="text-[11px] text-text-secondary">
                        Expires:{' '}
                        {cred.expiryDate
                          ? new Date(cred.expiryDate).toLocaleDateString('en-US')
                          : 'No expiry'}
                        {expiring && (
                          <span className="ml-2 text-red-600 font-semibold">Expiring soon</span>
                        )}
                      </div>
                    </div>
                    <span
                      className="text-[11px] font-semibold px-2 py-0.5"
                      style={{
                        background: cred.verified ? '#f0fdf4' : '#fef2f2',
                        color: cred.verified ? '#16a34a' : '#dc2626',
                      }}
                    >
                      {cred.verified ? 'Verified' : 'Unverified'}
                    </span>
                  </div>
                )
              })
            )}
          </div>
        )}
        {activeTab === 'backgroundChecks' && (
          <p className="text-text-secondary text-[13px]">Background checks — Phase 7 wires to real API.</p>
        )}
        {activeTab === 'availability' && (
          <p className="text-text-secondary text-[13px]">Availability — Phase 7 wires to real API.</p>
        )}
        {activeTab === 'shiftHistory' && (
          <p className="text-text-secondary text-[13px]">Shift history — Phase 7 wires to real API.</p>
        )}
      </div>
    </div>
  )
}
```

- [ ] **Step 13.4: Commit**

```bash
cd ..
git add frontend/src/components/caregivers/
git commit -m "feat(frontend): add Caregivers page and CaregiverDetailPanel (mock)"
```

---

### Task 14: Payers Page and EVV Status Page

**Files:**
- Create: `frontend/src/components/payers/PayersPage.tsx`
- Create: `frontend/src/components/evv/EvvStatusPage.tsx`

- [ ] **Step 14.1: Create PayersPage**

Create `frontend/src/components/payers/PayersPage.tsx`:

```tsx
import { mockPayers } from '../../mock/data'

const PAYER_TYPE_LABEL: Record<string, string> = {
  MEDICAID: 'Medicaid',
  PRIVATE_PAY: 'Private Pay',
  LTC_INSURANCE: 'LTC Insurance',
  VA: 'VA',
  MEDICARE: 'Medicare',
}

export function PayersPage() {
  return (
    <div className="flex flex-col h-full">
      <div className="flex items-center px-6 py-4 bg-white border-b border-border">
        <h1 className="text-[16px] font-bold tracking-[-0.02em] text-dark">Payers</h1>
        <button
          type="button"
          className="ml-auto px-4 py-1.5 text-[12px] font-bold bg-dark text-white hover:brightness-110"
          onClick={() => alert('Phase 8 will wire this to the API.')}
        >
          + Add Payer
        </button>
      </div>
      <div className="flex-1 overflow-auto">
        <table className="w-full text-[13px]">
          <thead>
            <tr className="border-b border-border bg-surface">
              {['Payer Name', 'Type', 'State', 'EVV Aggregator'].map((h) => (
                <th
                  key={h}
                  className="text-left px-6 py-2 text-[9px] font-bold uppercase tracking-[0.1em] text-text-secondary"
                >
                  {h}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {mockPayers.map((payer) => (
              <tr key={payer.id} className="border-b border-border hover:bg-surface cursor-pointer">
                <td className="px-6 py-3 font-medium text-dark">{payer.name}</td>
                <td className="px-6 py-3 text-text-secondary">
                  {PAYER_TYPE_LABEL[payer.payerType] ?? payer.payerType}
                </td>
                <td className="px-6 py-3 text-text-secondary">{payer.state}</td>
                <td className="px-6 py-3 text-text-secondary">{payer.evvAggregator ?? '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
```

- [ ] **Step 14.2: Create EvvStatusPage**

Create `frontend/src/components/evv/EvvStatusPage.tsx`:

```tsx
import type { EvvComplianceStatus } from '../../types/api'
import { mockEvvHistory } from '../../mock/data'
import { usePanelStore } from '../../store/panelStore'

const STATUS_COLOR: Record<EvvComplianceStatus, string> = {
  RED: '#dc2626',
  YELLOW: '#ca8a04',
  GREEN: '#16a34a',
  GREY: '#94a3b8',
  EXEMPT: '#94a3b8',
  PORTAL_SUBMIT: '#16a34a',
}

function formatTime(iso: string | null): string {
  if (!iso) return '—'
  return new Date(iso).toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' })
}

export function EvvStatusPage() {
  const { openPanel } = usePanelStore()

  return (
    <div className="flex flex-col h-full">
      <div className="flex items-center px-6 py-4 bg-white border-b border-border">
        <h1 className="text-[16px] font-bold tracking-[-0.02em] text-dark">EVV Status</h1>
        <span className="ml-3 text-[12px] text-text-secondary">Last 30 days — computed live from Core API</span>
      </div>
      <div className="flex-1 overflow-auto">
        <table className="w-full text-[13px]">
          <thead>
            <tr className="border-b border-border bg-surface">
              {['Client', 'Caregiver', 'Service', 'Date', 'Clock-in', 'Clock-out', 'Status'].map((h) => (
                <th
                  key={h}
                  className="text-left px-6 py-2 text-[9px] font-bold uppercase tracking-[0.1em] text-text-secondary"
                >
                  {h}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {mockEvvHistory.map((row) => (
              <tr
                key={row.shiftId}
                className="border-b border-border hover:bg-surface cursor-pointer"
                style={{ borderLeft: `3px solid ${STATUS_COLOR[row.evvStatus]}` }}
                onClick={() => openPanel('shift', row.shiftId, { backLabel: '← EVV Status' })}
              >
                <td className="px-6 py-3 font-medium text-dark">
                  {row.clientFirstName} {row.clientLastName}
                </td>
                <td className="px-6 py-3 text-text-secondary">
                  {row.caregiverFirstName
                    ? `${row.caregiverFirstName} ${row.caregiverLastName}`
                    : '—'}
                </td>
                <td className="px-6 py-3 text-text-secondary">{row.serviceTypeName}</td>
                <td className="px-6 py-3 text-text-secondary">
                  {new Date(row.scheduledStart).toLocaleDateString('en-US', {
                    month: 'short',
                    day: 'numeric',
                  })}
                </td>
                <td className="px-6 py-3 text-text-secondary">{formatTime(row.timeIn)}</td>
                <td className="px-6 py-3 text-text-secondary">{formatTime(row.timeOut)}</td>
                <td className="px-6 py-3">
                  <span
                    className="text-[11px] font-bold px-2 py-0.5"
                    style={{
                      color: STATUS_COLOR[row.evvStatus],
                      background: `${STATUS_COLOR[row.evvStatus]}18`,
                    }}
                  >
                    {row.evvStatus}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
```

- [ ] **Step 14.3: Fix Shell.tsx caregiver prop typo**

In `frontend/src/components/layout/Shell.tsx`, the `CaregiverDetailPanel` prop is named `caregiverVId` (see Task 13). This matches the component definition. No change needed.

- [ ] **Step 14.4: Verify full build**

```bash
cd frontend && npx tsc --noEmit 2>&1
```

Expected: no errors. Fix any type errors before proceeding.

- [ ] **Step 14.5: Commit**

```bash
cd ..
git add frontend/src/components/payers/ frontend/src/components/evv/
git commit -m "feat(frontend): add Payers and EVV Status pages (mock)"
```

---

### Task 15: Component Tests

**Files:**
- Create: `frontend/src/components/layout/Sidebar.test.tsx`

- [ ] **Step 15.1: Write Sidebar test**

Create `frontend/src/components/layout/Sidebar.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { Sidebar } from './Sidebar'

function renderSidebar(initialPath = '/schedule') {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <Sidebar userName="Ada Lovelace" userRole="ADMIN" />
    </MemoryRouter>
  )
}

describe('Sidebar', () => {
  it('renders h.care wordmark', () => {
    renderSidebar()
    expect(screen.getByText('h')).toBeInTheDocument()
    expect(screen.getByText('care')).toBeInTheDocument()
  })

  it('renders all nav items', () => {
    renderSidebar()
    expect(screen.getByText('Schedule')).toBeInTheDocument()
    expect(screen.getByText('Dashboard')).toBeInTheDocument()
    expect(screen.getByText('Clients')).toBeInTheDocument()
    expect(screen.getByText('Caregivers')).toBeInTheDocument()
    expect(screen.getByText('Payers')).toBeInTheDocument()
    expect(screen.getByText('EVV Status')).toBeInTheDocument()
  })

  it('renders user initials in footer', () => {
    renderSidebar()
    expect(screen.getByText('AL')).toBeInTheDocument()
  })

  it('renders user name and role in footer', () => {
    renderSidebar()
    expect(screen.getByText('Ada Lovelace')).toBeInTheDocument()
    expect(screen.getByText('admin')).toBeInTheDocument()
  })

  it('does not show dashboard badge when redEvvCount is 0', () => {
    renderSidebar()
    expect(screen.queryByText(/^[0-9]$/)).not.toBeInTheDocument()
  })

  it('shows dashboard badge when redEvvCount > 0', () => {
    render(
      <MemoryRouter initialEntries={['/schedule']}>
        <Sidebar userName="Admin" userRole="ADMIN" redEvvCount={3} />
      </MemoryRouter>
    )
    expect(screen.getByText('3')).toBeInTheDocument()
  })
})
```

- [ ] **Step 15.2: Run Sidebar test**

```bash
cd frontend && npx vitest run src/components/layout/Sidebar.test.tsx 2>&1 | tail -5
```

Expected: `Tests 6 passed (6)`.

- [ ] **Step 15.3: Run all tests**

```bash
cd frontend && npx vitest run 2>&1 | tail -10
```

Expected: all tests pass. Fix any failures before committing.

- [ ] **Step 15.4: Commit**

```bash
cd ..
git add frontend/src/components/layout/Sidebar.test.tsx
git commit -m "test(frontend): add Sidebar component tests"
```

---

### Task 16: Playwright E2E Setup + Smoke Test

**Files:**
- Create: `frontend/playwright.config.ts`
- Create: `frontend/e2e/smoke.spec.ts`

- [ ] **Step 16.1: Create Playwright config**

Create `frontend/playwright.config.ts`:

```ts
import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'on-first-retry',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:5173',
    reuseExistingServer: !process.env.CI,
    cwd: '.',
  },
})
```

- [ ] **Step 16.2: Create smoke test**

Create `frontend/e2e/smoke.spec.ts`:

```ts
import { test, expect } from '@playwright/test'

test('redirects / to /schedule', async ({ page }) => {
  await page.goto('/')
  await expect(page).toHaveURL('/schedule')
})

test('renders sidebar with h.care wordmark', async ({ page }) => {
  await page.goto('/schedule')
  await expect(page.getByText('care')).toBeVisible()
})

test('schedule page shows week calendar', async ({ page }) => {
  await page.goto('/schedule')
  await expect(page.getByRole('heading', { name: 'Schedule' })).toBeVisible()
  // Week contains today's date
  const today = new Date().getDate().toString()
  await expect(page.getByText(today, { exact: true }).first()).toBeVisible()
})

test('clicking shift block opens shift detail panel', async ({ page }) => {
  await page.goto('/schedule')
  // Click the first shift block
  await page.locator('button').filter({ hasText: 'Alice Johnson' }).first().click()
  // Panel slides in — back link visible
  await expect(page.getByText('← Schedule')).toBeVisible()
})

test('New Shift button opens new shift panel', async ({ page }) => {
  await page.goto('/schedule')
  await page.getByRole('button', { name: '+ New Shift' }).click()
  await expect(page.getByRole('heading', { name: 'New Shift' })).toBeVisible()
})

test('Escape closes the panel', async ({ page }) => {
  await page.goto('/schedule')
  await page.getByRole('button', { name: '+ New Shift' }).click()
  await expect(page.getByRole('heading', { name: 'New Shift' })).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(page.getByRole('heading', { name: 'New Shift' })).not.toBeVisible()
})

test('navigating to /dashboard shows stat tiles', async ({ page }) => {
  await page.goto('/dashboard')
  await expect(page.getByText('RED EVV')).toBeVisible()
  await expect(page.getByText('ON TRACK')).toBeVisible()
})

test('navigating to /clients shows client table', async ({ page }) => {
  await page.goto('/clients')
  await expect(page.getByText('Alice Johnson')).toBeVisible()
})

test('navigating to /caregivers shows caregiver table', async ({ page }) => {
  await page.goto('/caregivers')
  await expect(page.getByText('Maria Garcia')).toBeVisible()
})
```

- [ ] **Step 16.3: Install Playwright browsers**

```bash
cd frontend && npx playwright install chromium
```

- [ ] **Step 16.4: Run e2e tests (dev server must be running)**

Open a second terminal:
```bash
cd frontend && npm run dev
```

In the original terminal:
```bash
cd frontend && npx playwright test 2>&1 | tail -15
```

Expected: `9 passed`.

- [ ] **Step 16.5: Commit**

```bash
cd ..
git add frontend/playwright.config.ts frontend/e2e/
git commit -m "test(frontend): add Playwright smoke tests for Phase 1"
```

---

## ✋ MANUAL TEST CHECKPOINT 1

**Before proceeding to Phase 2, manually verify the following:**

```bash
cd frontend && npm run dev
```

Open `http://localhost:5173` in a browser.

| Test | Expected |
|------|----------|
| Root `/` redirects to `/schedule` | ✅ |
| Sidebar shows h.care wordmark | ✅ |
| Schedule shows week calendar with today highlighted in blue | ✅ |
| Shift blocks visible for today (Alice Johnson — RED, Robert Martinez — YELLOW, Dorothy Chen — GREY uncovered) | ✅ |
| Clicking a shift block slides in ShiftDetailPanel — sidebar stays visible | ✅ |
| Escape key closes the panel | ✅ |
| "← Schedule" back link closes the panel | ✅ |
| "+ New Shift" opens NewShiftPanel with form | ✅ |
| Alert strip shows RED, YELLOW, Uncovered chip counts | ✅ |
| Prev/Next week navigation works | ✅ |
| `/dashboard` shows 4 stat tiles + visit list + 220px alerts column | ✅ |
| Clicking a visit row in dashboard opens shift detail panel | ✅ |
| `/clients` shows searchable client table | ✅ |
| Clicking a client row opens ClientDetailPanel with tabs | ✅ |
| `/caregivers` shows caregiver table | ✅ |
| Clicking caregiver opens CaregiverDetailPanel — credentials tab shows expiry warning for M. Garcia | ✅ |
| `/payers` shows payer table | ✅ |
| `/evv` shows EVV history table with status colors | ✅ |

**When all checks pass:** Proceed to `2026-04-06-frontend-phase-2-dashboard-endpoint.md`.
