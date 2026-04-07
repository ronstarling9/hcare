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
  @types/node \
  i18next react-i18next i18next-http-backend i18next-browser-languagedetector && \
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
import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'

// Initialize i18next synchronously for tests — no HTTP backend needed.
// Tests assert against real English strings, not translation keys.
//
// MAINTENANCE: Keep these inline resources in sync with public/locales/en/*.json.
// If you add a key to a JSON file but omit it here, tests will display raw
// translation keys (e.g. "nav.settings") instead of real strings.
i18n.use(initReactI18next).init({
  lng: 'en',
  fallbackLng: 'en',
  resources: {
    en: {
      nav: {
        wordmarkPrefix: 'h',
        wordmarkDot: '.',
        wordmarkSuffix: 'care',
        agencySubtitle: 'Agency Management',
        schedule: 'Schedule',
        dashboard: 'Dashboard',
        clients: 'Clients',
        caregivers: 'Caregivers',
        payers: 'Payers',
        evvStatus: 'EVV Status',
        settings: 'Settings',
        settingsComingSoon: 'Settings — coming soon',
        sectionOperations: 'Operations',
        sectionPeople: 'People',
        sectionAdmin: 'Admin',
      },
      common: {
        back: '← Back',
        unassigned: 'Unassigned',
        noDash: '—',
        yes: 'Yes',
        no: 'No',
        cancel: 'Cancel',
        searchByName: 'Search by name…',
        noExpiry: 'No expiry',
      },
      schedule: {
        pageTitle: 'Schedule',
        backLabel: '← Schedule',
        newShift: '+ New Shift',
        broadcastOpen: 'Broadcast Open',
        broadcastOpenAlert: 'Broadcast Open: confirms then broadcasts all unassigned shifts',
        prevWeek: '←',
        nextWeek: '→',
        alertStripToday: 'Today:',
        alertRedEvv: 'RED EVV',
        alertYellowEvv: 'YELLOW EVV',
        alertUncovered: 'Uncovered',
        alertLateClockIn: 'Late clock-in',
        settingsComingSoon: 'Settings — coming soon',
        newShiftAt: 'New shift at {{hour}}:00',
      },
      shiftDetail: {
        notFound: 'Shift not found.',
        sectionVisitDetails: 'Visit Details',
        sectionEvvRecord: 'EVV Record',
        sectionAiMatch: 'AI Match — Top Candidates',
        fieldClient: 'Client',
        fieldCaregiver: 'Caregiver',
        fieldService: 'Service',
        fieldStatus: 'Status',
        fieldClockIn: 'Clock-in',
        fieldClockOut: 'Clock-out',
        fieldMethod: 'Method',
        fieldOffline: 'Offline',
        unknownClient: 'Unknown Client',
        staticService: 'PCS',
        visitNotStarted: ' — Visit not yet started',
        evvCompliant: 'Compliant',
        evvAttention: 'Attention Required',
        evvNonCompliant: 'Non-Compliant',
        evvNotStarted: 'Not Started',
        evvExempt: 'Exempt',
        evvPortalSubmit: 'Portal Submit',
        assignCaregiver: 'Assign Caregiver',
        addManualClockIn: 'Add Manual Clock-in',
        editShift: 'Edit Shift',
        markAsMissed: 'Mark as Missed',
        viewCareNotes: 'View Care Notes',
        candidate1Name: 'Maria Garcia',
        candidate1Reason: '0.8 mi · 12 prior visits · no OT risk',
        candidate2Name: 'Sarah Davis',
        candidate2Reason: '1.4 mi · 5 prior visits · no OT risk',
        assign: 'Assign →',
      },
      newShift: {
        panelTitle: 'New Shift',
        labelClient: 'Client',
        labelServiceType: 'Service Type',
        labelDate: 'Date',
        labelStartTime: 'Start Time',
        labelEndTime: 'End Time',
        labelCaregiver: 'Caregiver (optional)',
        selectClient: 'Select client…',
        selectServiceType: 'Select service type…',
        serviceTypePcs: 'PCS — Personal Care Services',
        caregiverUnassigned: 'Leave unassigned (broadcast after)',
        caregiverPhaseNote: 'Phase 4 will populate this list from the API.',
        validationClientRequired: 'Client is required',
        validationServiceTypeRequired: 'Service type is required',
        validationDateRequired: 'Date is required',
        saveShift: 'Save Shift',
        mockAlert: 'Mock: shift for client {{clientId}} on {{date}} created.\n\nPhase 4 will wire this to POST /shifts.',
      },
      dashboard: {
        pageTitle: 'Dashboard',
        backLabel: '← Dashboard',
        tileRedEvv: 'RED EVV',
        tileRedEvvSub: 'Missing elements',
        tileYellowEvv: 'YELLOW EVV',
        tileYellowEvvSub: 'Attention needed',
        tileUncovered: 'UNCOVERED',
        tileUncoveredSub: 'No caregiver',
        tileOnTrack: 'ON TRACK',
        tileOnTrackSub: 'Compliant',
        noVisits: 'No visits today.',
        noAlerts: 'No active alerts.',
        alertsHeader: 'Alerts',
        due: 'Due {{date}}',
      },
      clients: {
        pageTitle: 'Clients',
        backLabel: '← Clients',
        addClient: '+ Add Client',
        addClientAlert: 'Add Client — Phase 6 will wire this form.',
        notFound: 'Client not found.',
        noResults: 'No clients found.',
        colClient: 'Client',
        colMedicaidId: 'Medicaid ID',
        colState: 'State',
        colStatus: 'Status',
        tabOverview: 'Overview',
        tabCarePlan: 'Care Plan',
        tabAuthorizations: 'Authorizations',
        tabDocuments: 'Documents',
        tabFamilyPortal: 'Family Portal',
        fieldPhone: 'Phone',
        fieldAddress: 'Address',
        fieldServiceState: 'Service State',
        fieldStatus: 'Status',
        fieldPreferredLanguage: 'Preferred Language',
        fieldNoPetCaregiver: 'No Pet Caregiver',
        noAuthorizations: 'No authorizations.',
        carePlanPhaseNote: 'Care plan — Phase 6 wires to real API.',
        documentsPhaseNote: 'Documents — Phase 6 wires to real API.',
        familyPortalPhaseNote: 'Family portal — Phase 6 wires to real API.',
        authHeader: 'Auth #{{authNumber}}',
        authUnitsUsed: '{{used}}/{{authorized}} {{unitType}}s used',
      },
      caregivers: {
        pageTitle: 'Caregivers',
        backLabel: '← Caregivers',
        addCaregiver: '+ Add Caregiver',
        addCaregiverAlert: 'Add Caregiver — Phase 7 will wire this form.',
        notFound: 'Caregiver not found.',
        noResults: 'No caregivers found.',
        colCaregiver: 'Caregiver',
        colEmail: 'Email',
        colPhone: 'Phone',
        colStatus: 'Status',
        colHireDate: 'Hire Date',
        tabOverview: 'Overview',
        tabCredentials: 'Credentials',
        tabBackgroundChecks: 'Background Checks',
        tabAvailability: 'Availability',
        tabShiftHistory: 'Shift History',
        fieldPhone: 'Phone',
        fieldAddress: 'Address',
        fieldHireDate: 'Hire Date',
        fieldStatus: 'Status',
        fieldHasPet: 'Has Pet',
        noCredentials: 'No credentials on file.',
        credExpires: 'Expires:',
        credExpiringSoon: 'Expiring soon',
        credVerified: 'Verified',
        credUnverified: 'Unverified',
        backgroundPhaseNote: 'Background checks — Phase 7 wires to real API.',
        availabilityPhaseNote: 'Availability — Phase 7 wires to real API.',
        shiftHistoryPhaseNote: 'Shift history — Phase 7 wires to real API.',
      },
      payers: {
        pageTitle: 'Payers',
        addPayer: '+ Add Payer',
        addPayerAlert: 'Phase 8 will wire this to the API.',
        colPayerName: 'Payer Name',
        colType: 'Type',
        colState: 'State',
        colEvvAggregator: 'EVV Aggregator',
        typeMedicaid: 'Medicaid',
        typePrivatePay: 'Private Pay',
        typeLtcInsurance: 'LTC Insurance',
        typeVa: 'VA',
        typeMedicare: 'Medicare',
      },
      evvStatus: {
        pageTitle: 'EVV Status',
        backLabel: '← EVV Status',
        subtitle: 'Last 30 days — computed live from Core API',
        colClient: 'Client',
        colCaregiver: 'Caregiver',
        colService: 'Service',
        colDate: 'Date',
        colClockIn: 'Clock-in',
        colClockOut: 'Clock-out',
        colStatus: 'Status',
      },
    },
  },
  interpolation: { escapeValue: false },
  initImmediate: false, // synchronous init for tests
})
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
git commit -m "chore: configure Tailwind CSS v3 + Vitest + design tokens + i18next test setup"
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

### Task 3.5: i18n Setup (react-i18next)

**Files:**
- Create: `frontend/src/i18n.ts`
- Create: `frontend/public/locales/en/nav.json`
- Create: `frontend/public/locales/en/common.json`
- Create: `frontend/public/locales/en/schedule.json`
- Create: `frontend/public/locales/en/shiftDetail.json`
- Create: `frontend/public/locales/en/newShift.json`
- Create: `frontend/public/locales/en/dashboard.json`
- Create: `frontend/public/locales/en/clients.json`
- Create: `frontend/public/locales/en/caregivers.json`
- Create: `frontend/public/locales/en/payers.json`
- Create: `frontend/public/locales/en/evvStatus.json`

All user-visible text lives in JSON translation files under `public/locales/`. Components use the `useTranslation` hook from react-i18next. Adding a new locale is: create `public/locales/<lng>/` with the same filenames and add the language code to `supportedLngs` in `i18n.ts`.

- [ ] **Step 3.5.1: Create i18n config**

Create `frontend/src/i18n.ts`:

```ts
import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import HttpBackend from 'i18next-http-backend'
import LanguageDetector from 'i18next-browser-languagedetector'

i18n
  .use(HttpBackend)
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    fallbackLng: 'en',
    supportedLngs: ['en'],
    defaultNS: 'common',
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
    ],
    backend: {
      loadPath: '/locales/{{lng}}/{{ns}}.json',
    },
    interpolation: {
      escapeValue: false, // React handles XSS escaping
    },
  })

export default i18n
```

- [ ] **Step 3.5.2: Create translation JSON files**

Create `frontend/public/locales/en/nav.json`:

```json
{
  "wordmarkPrefix": "h",
  "wordmarkDot": ".",
  "wordmarkSuffix": "care",
  "agencySubtitle": "Agency Management",
  "schedule": "Schedule",
  "dashboard": "Dashboard",
  "clients": "Clients",
  "caregivers": "Caregivers",
  "payers": "Payers",
  "evvStatus": "EVV Status",
  "settings": "Settings",
  "settingsComingSoon": "Settings — coming soon",
  "sectionOperations": "Operations",
  "sectionPeople": "People",
  "sectionAdmin": "Admin"
}
```

Create `frontend/public/locales/en/common.json`:

```json
{
  "back": "← Back",
  "unassigned": "Unassigned",
  "noDash": "—",
  "yes": "Yes",
  "no": "No",
  "cancel": "Cancel",
  "searchByName": "Search by name…",
  "noExpiry": "No expiry"
}
```

Create `frontend/public/locales/en/schedule.json`:

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
  "newShiftAt": "New shift at {{hour}}:00"
}
```

Create `frontend/public/locales/en/shiftDetail.json`:

```json
{
  "notFound": "Shift not found.",
  "sectionVisitDetails": "Visit Details",
  "sectionEvvRecord": "EVV Record",
  "sectionAiMatch": "AI Match — Top Candidates",
  "fieldClient": "Client",
  "fieldCaregiver": "Caregiver",
  "fieldService": "Service",
  "fieldStatus": "Status",
  "fieldClockIn": "Clock-in",
  "fieldClockOut": "Clock-out",
  "fieldMethod": "Method",
  "fieldOffline": "Offline",
  "unknownClient": "Unknown Client",
  "staticService": "PCS",
  "visitNotStarted": " — Visit not yet started",
  "evvCompliant": "Compliant",
  "evvAttention": "Attention Required",
  "evvNonCompliant": "Non-Compliant",
  "evvNotStarted": "Not Started",
  "evvExempt": "Exempt",
  "evvPortalSubmit": "Portal Submit",
  "assignCaregiver": "Assign Caregiver",
  "addManualClockIn": "Add Manual Clock-in",
  "editShift": "Edit Shift",
  "markAsMissed": "Mark as Missed",
  "viewCareNotes": "View Care Notes",
  "candidate1Name": "Maria Garcia",
  "candidate1Reason": "0.8 mi · 12 prior visits · no OT risk",
  "candidate2Name": "Sarah Davis",
  "candidate2Reason": "1.4 mi · 5 prior visits · no OT risk",
  "assign": "Assign →"
}
```

Create `frontend/public/locales/en/newShift.json`:

```json
{
  "panelTitle": "New Shift",
  "labelClient": "Client",
  "labelServiceType": "Service Type",
  "labelDate": "Date",
  "labelStartTime": "Start Time",
  "labelEndTime": "End Time",
  "labelCaregiver": "Caregiver (optional)",
  "selectClient": "Select client…",
  "selectServiceType": "Select service type…",
  "serviceTypePcs": "PCS — Personal Care Services",
  "caregiverUnassigned": "Leave unassigned (broadcast after)",
  "caregiverPhaseNote": "Phase 4 will populate this list from the API.",
  "validationClientRequired": "Client is required",
  "validationServiceTypeRequired": "Service type is required",
  "validationDateRequired": "Date is required",
  "saveShift": "Save Shift",
  "mockAlert": "Mock: shift for client {{clientId}} on {{date}} created.\n\nPhase 4 will wire this to POST /shifts."
}
```

Create `frontend/public/locales/en/dashboard.json`:

```json
{
  "pageTitle": "Dashboard",
  "backLabel": "← Dashboard",
  "tileRedEvv": "RED EVV",
  "tileRedEvvSub": "Missing elements",
  "tileYellowEvv": "YELLOW EVV",
  "tileYellowEvvSub": "Attention needed",
  "tileUncovered": "UNCOVERED",
  "tileUncoveredSub": "No caregiver",
  "tileOnTrack": "ON TRACK",
  "tileOnTrackSub": "Compliant",
  "noVisits": "No visits today.",
  "noAlerts": "No active alerts.",
  "alertsHeader": "Alerts",
  "due": "Due {{date}}"
}
```

Create `frontend/public/locales/en/clients.json`:

```json
{
  "pageTitle": "Clients",
  "backLabel": "← Clients",
  "addClient": "+ Add Client",
  "addClientAlert": "Add Client — Phase 6 will wire this form.",
  "notFound": "Client not found.",
  "noResults": "No clients found.",
  "colClient": "Client",
  "colMedicaidId": "Medicaid ID",
  "colState": "State",
  "colStatus": "Status",
  "tabOverview": "Overview",
  "tabCarePlan": "Care Plan",
  "tabAuthorizations": "Authorizations",
  "tabDocuments": "Documents",
  "tabFamilyPortal": "Family Portal",
  "fieldPhone": "Phone",
  "fieldAddress": "Address",
  "fieldServiceState": "Service State",
  "fieldStatus": "Status",
  "fieldPreferredLanguage": "Preferred Language",
  "fieldNoPetCaregiver": "No Pet Caregiver",
  "noAuthorizations": "No authorizations.",
  "carePlanPhaseNote": "Care plan — Phase 6 wires to real API.",
  "documentsPhaseNote": "Documents — Phase 6 wires to real API.",
  "familyPortalPhaseNote": "Family portal — Phase 6 wires to real API.",
  "authHeader": "Auth #{{authNumber}}",
  "authUnitsUsed": "{{used}}/{{authorized}} {{unitType}}s used"
}
```

Create `frontend/public/locales/en/caregivers.json`:

```json
{
  "pageTitle": "Caregivers",
  "backLabel": "← Caregivers",
  "addCaregiver": "+ Add Caregiver",
  "addCaregiverAlert": "Add Caregiver — Phase 7 will wire this form.",
  "notFound": "Caregiver not found.",
  "noResults": "No caregivers found.",
  "colCaregiver": "Caregiver",
  "colEmail": "Email",
  "colPhone": "Phone",
  "colStatus": "Status",
  "colHireDate": "Hire Date",
  "tabOverview": "Overview",
  "tabCredentials": "Credentials",
  "tabBackgroundChecks": "Background Checks",
  "tabAvailability": "Availability",
  "tabShiftHistory": "Shift History",
  "fieldPhone": "Phone",
  "fieldAddress": "Address",
  "fieldHireDate": "Hire Date",
  "fieldStatus": "Status",
  "fieldHasPet": "Has Pet",
  "noCredentials": "No credentials on file.",
  "credExpires": "Expires:",
  "credExpiringSoon": "Expiring soon",
  "credVerified": "Verified",
  "credUnverified": "Unverified",
  "backgroundPhaseNote": "Background checks — Phase 7 wires to real API.",
  "availabilityPhaseNote": "Availability — Phase 7 wires to real API.",
  "shiftHistoryPhaseNote": "Shift history — Phase 7 wires to real API."
}
```

Create `frontend/public/locales/en/payers.json`:

```json
{
  "pageTitle": "Payers",
  "addPayer": "+ Add Payer",
  "addPayerAlert": "Phase 8 will wire this to the API.",
  "colPayerName": "Payer Name",
  "colType": "Type",
  "colState": "State",
  "colEvvAggregator": "EVV Aggregator",
  "typeMedicaid": "Medicaid",
  "typePrivatePay": "Private Pay",
  "typeLtcInsurance": "LTC Insurance",
  "typeVa": "VA",
  "typeMedicare": "Medicare"
}
```

Create `frontend/public/locales/en/evvStatus.json`:

```json
{
  "pageTitle": "EVV Status",
  "backLabel": "← EVV Status",
  "subtitle": "Last 30 days — computed live from Core API",
  "colClient": "Client",
  "colCaregiver": "Caregiver",
  "colService": "Service",
  "colDate": "Date",
  "colClockIn": "Clock-in",
  "colClockOut": "Clock-out",
  "colStatus": "Status"
}
```

- [ ] **Step 3.5.3: Verify no TypeScript errors**

```bash
cd frontend && npx tsc --noEmit 2>&1 | head -20
```

Expected: no output.

- [ ] **Step 3.5.4: Commit**

```bash
cd ..
git add frontend/src/i18n.ts frontend/public/locales/
git commit -m "feat(frontend): add react-i18next setup with translation JSON files"
```

---

### Task 4: Mock Data

**Files:**
- Create: `frontend/src/mock/data.ts`

Mock data uses fixed IDs and today's date (2026-04-07). All shifts are within 6am–10pm.

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

// ── Shifts (this week: Apr 6–12, 2026; today = Apr 7 Tuesday) ─────────────────

export const mockShifts: ShiftDetailResponse[] = [
  {
    id: IDS.shift1,
    agencyId: IDS.agency,
    clientId: IDS.client1,
    caregiverId: IDS.caregiver1,
    serviceTypeId: IDS.serviceType1,
    authorizationId: null,
    sourcePatternId: null,
    scheduledStart: '2026-04-07T08:00:00',
    scheduledEnd: '2026-04-07T12:00:00',
    status: 'COMPLETED',
    notes: null,
    evv: {
      evvRecordId: 'evv00001-0000-0000-0000-000000000001',
      complianceStatus: 'RED',
      timeIn: '2026-04-07T08:05:00',
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
    scheduledStart: '2026-04-07T09:00:00',
    scheduledEnd: '2026-04-07T13:00:00',
    status: 'IN_PROGRESS',
    notes: null,
    evv: {
      evvRecordId: 'evv00002-0000-0000-0000-000000000002',
      complianceStatus: 'YELLOW',
      timeIn: '2026-04-07T09:12:00',
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
    scheduledStart: '2026-04-07T10:00:00',
    scheduledEnd: '2026-04-07T14:00:00',
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
    scheduledStart: '2026-04-07T14:00:00',
    scheduledEnd: '2026-04-07T18:00:00',
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
    scheduledStart: '2026-04-08T09:00:00',
    scheduledEnd: '2026-04-08T13:00:00',
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
    scheduledStart: '2026-04-09T11:00:00',
    scheduledEnd: '2026-04-09T15:00:00',
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
      scheduledStart: '2026-04-07T08:00:00',
      scheduledEnd: '2026-04-07T12:00:00',
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
      scheduledStart: '2026-04-07T09:00:00',
      scheduledEnd: '2026-04-07T13:00:00',
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
      scheduledStart: '2026-04-07T10:00:00',
      scheduledEnd: '2026-04-07T14:00:00',
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
      scheduledStart: '2026-04-07T14:00:00',
      scheduledEnd: '2026-04-07T18:00:00',
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
    scheduledStart: '2026-04-07T08:00:00',
    scheduledEnd: '2026-04-07T12:00:00',
    evvStatus: 'RED',
    evvStatusReason: 'No clock-out recorded',
    timeIn: '2026-04-07T08:05:00',
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
    scheduledStart: '2026-04-07T09:00:00',
    scheduledEnd: '2026-04-07T13:00:00',
    evvStatus: 'YELLOW',
    evvStatusReason: 'Clock-in 12 min late',
    timeIn: '2026-04-07T09:12:00',
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
import { useTranslation } from 'react-i18next'

interface NavItem {
  label: string
  to: string
  icon: string       // emoji placeholder — replace with real icons in P2
}

interface NavSection {
  section: string
  items: NavItem[]
}

interface SidebarProps {
  /** RED EVV count — drives the Dashboard badge. Passed by Shell from dashboard data. */
  redEvvCount?: number
  /** Logged-in user display name */
  userName?: string
  userRole?: string
}

export function Sidebar({ redEvvCount = 0, userName = 'Admin User', userRole = 'ADMIN' }: SidebarProps) {
  const { t } = useTranslation('nav')

  const NAV_SECTIONS: NavSection[] = [
    {
      section: t('sectionOperations'),
      items: [
        { label: t('schedule'), to: '/schedule', icon: '📅' },
        { label: t('dashboard'), to: '/dashboard', icon: '📊' },
      ],
    },
    {
      section: t('sectionPeople'),
      items: [
        { label: t('clients'), to: '/clients', icon: '👤' },
        { label: t('caregivers'), to: '/caregivers', icon: '🏥' },
      ],
    },
    {
      section: t('sectionAdmin'),
      items: [
        { label: t('payers'), to: '/payers', icon: '💳' },
        { label: t('evvStatus'), to: '/evv', icon: '✅' },
        { label: t('settings'), to: '/settings', icon: '⚙️' },
      ],
    },
  ]

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
          <span className="text-white">{t('wordmarkPrefix')}</span>
          <span style={{ color: '#1a9afa' }}>{t('wordmarkDot')}</span>
          <span className="text-white">{t('wordmarkSuffix')}</span>
        </div>
        <div className="text-[11px] mt-0.5" style={{ color: '#94a3b8' }}>
          {t('agencySubtitle')}
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
              // Use stable route path (not translated label) to detect dashboard item
              const showBadge = item.to === '/dashboard' && redEvvCount > 0
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
    return <CaregiverDetailPanel caregiverId={selectedId} backLabel={backLabel} />
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
git commit -m "feat(frontend): add Shell layout and Sidebar (react-i18next)"
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
import './i18n'
import { StrictMode, Suspense } from 'react'
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
    <Suspense fallback={<div style={{ display: 'flex', height: '100vh', alignItems: 'center', justifyContent: 'center' }}>Loading…</div>}>
      <BrowserRouter>
        <QueryClientProvider client={queryClient}>
          <App />
        </QueryClientProvider>
      </BrowserRouter>
    </Suspense>
  </StrictMode>
)
```

- [ ] **Step 8.2: Create App.tsx**

Create `frontend/src/App.tsx`:

```tsx
import { Navigate, Route, Routes } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Shell } from './components/layout/Shell'
import { SchedulePage } from './components/schedule/SchedulePage'
import { DashboardPage } from './components/dashboard/DashboardPage'
import { ClientsPage } from './components/clients/ClientsPage'
import { CaregiversPage } from './components/caregivers/CaregiversPage'
import { PayersPage } from './components/payers/PayersPage'
import { EvvStatusPage } from './components/evv/EvvStatusPage'

function SettingsPlaceholder() {
  const { t } = useTranslation('nav')
  return <div className="p-8 text-text-secondary">{t('settingsComingSoon')}</div>
}

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
        <Route path="/settings" element={<SettingsPlaceholder />} />
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
git commit -m "feat(frontend): add app routing with i18n import and Suspense wrapper"
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
    // 'Unassigned' comes from the common namespace loaded in test setup
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
import { useTranslation } from 'react-i18next'
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
  const { t } = useTranslation('common')
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
        {caregiverName ?? t('unassigned')}
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
import { useTranslation } from 'react-i18next'

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
  const { t } = useTranslation('schedule')

  const chips: Chip[] = [
    { label: t('alertRedEvv'), count: redCount, borderColor: '#dc2626', textColor: '#dc2626' },
    { label: t('alertYellowEvv'), count: yellowCount, borderColor: '#ca8a04', textColor: '#ca8a04' },
    { label: t('alertUncovered'), count: uncoveredCount, borderColor: '#94a3b8', textColor: '#747480' },
    { label: t('alertLateClockIn'), count: lateClockInCount, borderColor: '#ca8a04', textColor: '#ca8a04' },
  ].filter((c) => c.count > 0)

  if (chips.length === 0) return null

  return (
    <div className="flex items-center gap-3 px-6 py-2 bg-surface border-b border-border">
      <span className="text-[11px] text-text-secondary font-medium">{t('alertStripToday')}</span>
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
import { useTranslation } from 'react-i18next'
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
  const { t, i18n } = useTranslation('schedule')
  const days = useMemo(() => getWeekDays(weekStart), [weekStart])

  return (
    <div className="flex overflow-auto" style={{ height: `${TOTAL_HOURS * PX_PER_HOUR + 40}px` }}>
      {/* Time column */}
      <div className="shrink-0 w-16 bg-surface border-r border-border">
        {/* Header spacer */}
        <div className="h-10 border-b border-border" />
        {/* Time labels — formatted with Intl using the active i18n locale */}
        {HOURS.map((h) => (
          <div
            key={h}
            className="relative"
            style={{ height: PX_PER_HOUR }}
          >
            <span
              className="absolute -top-2 right-2 text-[10px] text-text-secondary"
            >
              {new Date(2000, 0, 1, h).toLocaleTimeString(i18n.language, { hour: 'numeric' })}
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
            {/* Day header — weekday via Intl, date number via getDate() */}
            <div className="h-10 flex flex-col items-center justify-center border-b border-border">
              <span className="text-[9px] font-bold uppercase text-text-secondary">
                {day.toLocaleDateString(i18n.language, { weekday: 'short' })}
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
                  aria-label={t('newShiftAt', { hour: h })}
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
import { useTranslation } from 'react-i18next'
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
    // Use local date parts — toISOString() returns UTC and would give the wrong date
    // for users in UTC−N timezones clicking late-evening slots.
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
          onShiftClick={(id) => openPanel('shift', id, { backLabel: t('backLabel') })}
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
git commit -m "feat(frontend): add Schedule page — WeekCalendar, ShiftBlock, AlertStrip (react-i18next)"
```

---

### Task 10: Shift Detail Panel and New Shift Panel

**Files:**
- Create: `frontend/src/components/schedule/ShiftDetailPanel.tsx`
- Create: `frontend/src/components/schedule/NewShiftPanel.tsx`

- [ ] **Step 10.1: Create ShiftDetailPanel**

Create `frontend/src/components/schedule/ShiftDetailPanel.tsx`:

```tsx
import { useTranslation } from 'react-i18next'
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

function formatTime(iso: string | null, locale: string): string {
  if (!iso) return '—'
  return new Date(iso).toLocaleTimeString(locale, { hour: 'numeric', minute: '2-digit' })
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

  const EVV_LABEL: Record<EvvComplianceStatus, string> = {
    RED: t('evvNonCompliant'),
    YELLOW: t('evvAttention'),
    GREEN: t('evvCompliant'),
    GREY: t('evvNotStarted'),
    EXEMPT: t('evvExempt'),
    PORTAL_SUBMIT: t('evvPortalSubmit'),
  }

  const shift: ShiftDetailResponse | undefined = mockShifts.find((s) => s.id === shiftId)

  if (!shift) {
    return (
      <div className="p-8 text-text-secondary">
        <button type="button" className="text-blue text-[13px] mb-4" onClick={closePanel}>
          {backLabel}
        </button>
        <p>{t('notFound')}</p>
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
          {client ? `${client.firstName} ${client.lastName}` : t('unknownClient')}
        </h2>
        <p className="text-[12px] text-text-secondary mt-0.5">
          {formatDate(shift.scheduledStart, i18n.language)} · {formatTime(shift.scheduledStart, i18n.language)} – {formatTime(shift.scheduledEnd, i18n.language)} · {t('staticService')}
        </p>
      </div>

      {/* EVV Status badge */}
      <div
        className="mx-6 mt-4 px-4 py-3 text-[13px] font-semibold"
        style={{ background: EVV_BG[status], color: EVV_TEXT[status] }}
      >
        {EVV_LABEL[status]}
        {shift.evv === null && t('visitNotStarted')}
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
                <div className="text-[10px] text-text-secondary">{t('fieldService')}</div>
                <div className="text-[13px] text-dark">{t('staticService')}</div>
              </div>
              <div>
                <div className="text-[10px] text-text-secondary">{t('fieldStatus')}</div>
                <div className="text-[13px] text-dark">{shift.status.replace('_', ' ')}</div>
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
                <div className="text-[13px] text-dark">{formatTime(shift.evv?.timeIn ?? null, i18n.language)}</div>
              </div>
              <div>
                <div className="text-[10px] text-text-secondary">{t('fieldClockOut')}</div>
                <div className="text-[13px] text-dark">{formatTime(shift.evv?.timeOut ?? null, i18n.language)}</div>
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

        {/* AI Candidates (shown when OPEN) */}
        {shift.status === 'OPEN' && (
          <div className="mt-6">
            <h3 className="text-[9px] font-bold uppercase tracking-[0.1em] text-text-secondary mb-3">
              {t('sectionAiMatch')}
            </h3>
            {[
              { rank: 1, name: t('candidate1Name'), reason: t('candidate1Reason') },
              { rank: 2, name: t('candidate2Name'), reason: t('candidate2Reason') },
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
                  {t('assign')}
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
            {t('assignCaregiver')}
          </button>
        )}
        {(shift.status === 'COMPLETED' || shift.status === 'IN_PROGRESS') &&
          status === 'RED' && (
            <>
              <button
                type="button"
                className="px-4 py-2 text-[12px] font-bold bg-dark text-white hover:brightness-110"
              >
                {t('addManualClockIn')}
              </button>
              <button
                type="button"
                className="px-4 py-2 text-[12px] font-semibold border border-border text-dark"
              >
                {t('editShift')}
              </button>
              <button
                type="button"
                className="ml-auto px-4 py-2 text-[12px] font-semibold text-red-600 border border-red-200"
              >
                {t('markAsMissed')}
              </button>
            </>
          )}
        {shift.status === 'ASSIGNED' && (
          <button
            type="button"
            className="px-4 py-2 text-[12px] font-semibold border border-border text-dark"
          >
            {t('editShift')}
          </button>
        )}
        {shift.status === 'COMPLETED' && status === 'GREEN' && (
          <>
            <button
              type="button"
              className="px-4 py-2 text-[12px] font-semibold border border-border text-dark"
            >
              {t('editShift')}
            </button>
            <button
              type="button"
              className="px-4 py-2 text-[12px] font-semibold border border-border text-dark"
            >
              {t('viewCareNotes')}
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
import { useTranslation } from 'react-i18next'
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
  const { t } = useTranslation('newShift')
  const tCommon = useTranslation('common').t
  const { closePanel } = usePanelStore()
  const {
    register,
    handleSubmit,
    formState: { errors },
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

  function onSubmit(values: FormValues) {
    // Phase 4: replace with API call
    console.log('Creating shift:', values)
    alert(t('mockAlert', { clientId: values.clientId, date: values.date }))
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
        <h2 className="text-[16px] font-bold text-dark">{t('panelTitle')}</h2>
      </div>

      {/* Form */}
      <form
        onSubmit={handleSubmit(onSubmit)}
        className="flex-1 overflow-auto px-6 py-4 space-y-4"
      >
        {/* Client */}
        <div>
          <label className="block text-[10px] font-bold uppercase tracking-[0.1em] text-text-secondary mb-1">
            {t('labelClient')}
          </label>
          <select
            {...register('clientId', { required: t('validationClientRequired') })}
            className="w-full border border-border px-3 py-2 text-[13px] text-dark bg-white"
          >
            <option value="">{t('selectClient')}</option>
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
            {t('labelServiceType')}
          </label>
          <select
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
          <label className="block text-[10px] font-bold uppercase tracking-[0.1em] text-text-secondary mb-1">
            {t('labelDate')}
          </label>
          <input
            type="date"
            {...register('date', { required: t('validationDateRequired') })}
            className="w-full border border-border px-3 py-2 text-[13px] text-dark"
          />
        </div>

        {/* Start / End time */}
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block text-[10px] font-bold uppercase tracking-[0.1em] text-text-secondary mb-1">
              {t('labelStartTime')}
            </label>
            <input
              type="time"
              {...register('startTime', { required: true })}
              className="w-full border border-border px-3 py-2 text-[13px] text-dark"
            />
          </div>
          <div>
            <label className="block text-[10px] font-bold uppercase tracking-[0.1em] text-text-secondary mb-1">
              {t('labelEndTime')}
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
            {t('labelCaregiver')}
          </label>
          <select
            {...register('caregiverId')}
            className="w-full border border-border px-3 py-2 text-[13px] text-dark bg-white"
          >
            <option value="">{t('caregiverUnassigned')}</option>
          </select>
          <p className="text-[10px] text-text-secondary mt-1">
            {t('caregiverPhaseNote')}
          </p>
        </div>

        {/* Footer */}
        <div className="pt-4 border-t border-border flex gap-3">
          <button
            type="submit"
            className="px-4 py-2 text-[12px] font-bold bg-dark text-white hover:brightness-110"
          >
            {t('saveShift')}
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

- [ ] **Step 10.3: Commit**

```bash
cd ..
git add frontend/src/components/schedule/ShiftDetailPanel.tsx \
  frontend/src/components/schedule/NewShiftPanel.tsx
git commit -m "feat(frontend): add ShiftDetailPanel and NewShiftPanel (mock, react-i18next)"
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
import { useTranslation } from 'react-i18next'

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
  const { t } = useTranslation('dashboard')

  const tiles: Tile[] = [
    {
      label: t('tileRedEvv'),
      sublabel: t('tileRedEvvSub'),
      count: redEvvCount,
      numColor: '#dc2626',
      bgColor: redEvvCount > 0 ? '#fef2f2' : undefined,
    },
    {
      label: t('tileYellowEvv'),
      sublabel: t('tileYellowEvvSub'),
      count: yellowEvvCount,
      numColor: '#ca8a04',
    },
    {
      label: t('tileUncovered'),
      sublabel: t('tileUncoveredSub'),
      count: uncoveredCount,
      numColor: '#94a3b8',
    },
    {
      label: t('tileOnTrack'),
      sublabel: t('tileOnTrackSub'),
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
import { useTranslation } from 'react-i18next'
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

function formatTime(iso: string, locale: string): string {
  return new Date(iso).toLocaleTimeString(locale, { hour: 'numeric', minute: '2-digit' })
}

interface VisitListProps {
  visits: DashboardVisitRow[]
}

export function VisitList({ visits }: VisitListProps) {
  const { t, i18n } = useTranslation('dashboard')
  const tCommon = useTranslation('common').t
  const { openPanel } = usePanelStore()

  if (visits.length === 0) {
    return <p className="px-6 py-8 text-text-secondary text-[13px]">{t('noVisits')}</p>
  }

  return (
    <div>
      {visits.map((row) => (
        <button
          key={row.shiftId}
          type="button"
          onClick={() => openPanel('shift', row.shiftId, { backLabel: t('backLabel') })}
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
                : `${tCommon('unassigned')} · ${row.serviceTypeName}`}
            </div>
          </div>
          {/* Time */}
          <div className="text-[11px] text-text-secondary shrink-0">
            {formatTime(row.scheduledStart, i18n.language)} – {formatTime(row.scheduledEnd, i18n.language)}
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
import { useTranslation } from 'react-i18next'
import type { DashboardAlert } from '../../types/api'
import { useNavigate } from 'react-router-dom'

// Date-only ISO strings (e.g. '2026-04-10') are parsed as UTC-midnight by the spec,
// causing off-by-one display in UTC-N timezones. Appending T12:00:00 keeps the date
// in the correct calendar day across all UTC-14 to UTC+14 zones.
function formatLocalDate(dateStr: string, options?: Intl.DateTimeFormatOptions): string {
  return new Date(dateStr + 'T12:00:00').toLocaleDateString('en-US', options)
}

function isUrgent(dueDate: string): boolean {
  const days = (new Date(dueDate).getTime() - Date.now()) / (1000 * 60 * 60 * 24)
  return days <= 7
}

interface AlertsColumnProps {
  alerts: DashboardAlert[]
}

export function AlertsColumn({ alerts }: AlertsColumnProps) {
  const { t } = useTranslation('dashboard')
  const navigate = useNavigate()

  if (alerts.length === 0) {
    return (
      <div className="p-4 text-[12px] text-text-secondary">{t('noAlerts')}</div>
    )
  }

  return (
    <div>
      <div className="px-4 py-3 border-b border-border">
        <span className="text-[9px] font-bold uppercase tracking-[0.1em] text-text-secondary">
          {t('alertsHeader')}
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
                {t('due', { date: formatLocalDate(alert.dueDate, { month: 'short', day: 'numeric' }) })}
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
import { useTranslation } from 'react-i18next'
import { mockDashboard } from '../../mock/data'
import { StatTiles } from './StatTiles'
import { VisitList } from './VisitList'
import { AlertsColumn } from './AlertsColumn'

export function DashboardPage() {
  const { t, i18n } = useTranslation('dashboard')
  const data = mockDashboard
  const today = new Date().toLocaleDateString(i18n.language, {
    weekday: 'long',
    month: 'long',
    day: 'numeric',
    year: 'numeric',
  })

  return (
    <div className="flex flex-col h-full">
      {/* Top bar */}
      <div className="flex items-center px-6 py-4 bg-white border-b border-border">
        <h1 className="text-[16px] font-bold tracking-[-0.02em] text-dark">{t('pageTitle')}</h1>
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
git commit -m "feat(frontend): add Dashboard page — StatTiles, VisitList, AlertsColumn (react-i18next)"
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
import { useTranslation } from 'react-i18next'
import type { ClientResponse } from '../../types/api'
import { usePanelStore } from '../../store/panelStore'

interface ClientsTableProps {
  clients: ClientResponse[]
  search: string
}

export function ClientsTable({ clients, search }: ClientsTableProps) {
  const { t } = useTranslation('clients')
  const tCommon = useTranslation('common').t
  const { openPanel } = usePanelStore()

  const filtered = clients.filter((c) => {
    const name = `${c.firstName} ${c.lastName}`.toLowerCase()
    return name.includes(search.toLowerCase())
  })

  if (filtered.length === 0) {
    return <p className="px-6 py-8 text-text-secondary text-[13px]">{t('noResults')}</p>
  }

  return (
    <table className="w-full text-[13px]">
      <thead>
        <tr className="border-b border-border bg-surface">
          <th className="text-left px-6 py-2 text-[9px] font-bold uppercase tracking-[0.1em] text-text-secondary">
            {t('colClient')}
          </th>
          <th className="text-left px-4 py-2 text-[9px] font-bold uppercase tracking-[0.1em] text-text-secondary">
            {t('colMedicaidId')}
          </th>
          <th className="text-left px-4 py-2 text-[9px] font-bold uppercase tracking-[0.1em] text-text-secondary">
            {t('colState')}
          </th>
          <th className="text-left px-4 py-2 text-[9px] font-bold uppercase tracking-[0.1em] text-text-secondary">
            {t('colStatus')}
          </th>
        </tr>
      </thead>
      <tbody>
        {filtered.map((client) => (
          <tr
            key={client.id}
            className="border-b border-border hover:bg-surface cursor-pointer"
            onClick={() => openPanel('client', client.id, { backLabel: t('backLabel') })}
          >
            <td className="px-6 py-3 font-medium text-dark">
              {client.firstName} {client.lastName}
            </td>
            <td className="px-4 py-3 text-text-secondary">{client.medicaidId ?? tCommon('noDash')}</td>
            <td className="px-4 py-3 text-text-secondary">{client.serviceState ?? tCommon('noDash')}</td>
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
import { useTranslation } from 'react-i18next'
import { mockClients } from '../../mock/data'
import { ClientsTable } from './ClientsTable'

export function ClientsPage() {
  const { t } = useTranslation('clients')
  const tCommon = useTranslation('common').t
  const [search, setSearch] = useState('')

  return (
    <div className="flex flex-col h-full">
      <div className="flex items-center gap-4 px-6 py-4 bg-white border-b border-border">
        <h1 className="text-[16px] font-bold tracking-[-0.02em] text-dark">{t('pageTitle')}</h1>
        <input
          type="search"
          placeholder={tCommon('searchByName')}
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="ml-4 border border-border px-3 py-1.5 text-[13px] w-64 focus:outline-none focus:border-dark"
        />
        <button
          type="button"
          className="ml-auto px-4 py-1.5 text-[12px] font-bold bg-dark text-white hover:brightness-110"
          onClick={() => alert(t('addClientAlert'))}
        >
          {t('addClient')}
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
import { useTranslation } from 'react-i18next'
import { mockClients, mockAuthorizations } from '../../mock/data'
import { usePanelStore } from '../../store/panelStore'

// Date-only ISO strings (e.g. '1942-03-15') are parsed as UTC-midnight by the spec,
// causing off-by-one display in UTC-N timezones. Appending T12:00:00 keeps the date
// in the correct calendar day across all UTC-14 to UTC+14 zones.
function formatLocalDate(dateStr: string, options?: Intl.DateTimeFormatOptions): string {
  return new Date(dateStr + 'T12:00:00').toLocaleDateString('en-US', options)
}

type Tab = 'overview' | 'carePlan' | 'authorizations' | 'documents' | 'familyPortal'

interface ClientDetailPanelProps {
  clientId: string | undefined
  backLabel: string
}

export function ClientDetailPanel({ clientId, backLabel }: ClientDetailPanelProps) {
  const { t } = useTranslation('clients')
  const tCommon = useTranslation('common').t
  const { closePanel } = usePanelStore()
  const [activeTab, setActiveTab] = useState<Tab>('overview')
  const client = mockClients.find((c) => c.id === clientId)

  const TABS: { id: Tab; label: string }[] = [
    { id: 'overview', label: t('tabOverview') },
    { id: 'carePlan', label: t('tabCarePlan') },
    { id: 'authorizations', label: t('tabAuthorizations') },
    { id: 'documents', label: t('tabDocuments') },
    { id: 'familyPortal', label: t('tabFamilyPortal') },
  ]

  if (!client) {
    return (
      <div className="p-8">
        <button type="button" className="text-blue text-[13px] mb-4" onClick={closePanel}>
          {backLabel}
        </button>
        <p className="text-text-secondary">{t('notFound')}</p>
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
          DOB: {formatLocalDate(client.dateOfBirth)} ·{' '}
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
              [t('fieldPhone'), client.phone ?? tCommon('noDash')],
              [t('fieldAddress'), client.address ?? tCommon('noDash')],
              [t('fieldServiceState'), client.serviceState ?? tCommon('noDash')],
              [t('fieldStatus'), client.status],
              [t('fieldPreferredLanguage'), client.preferredLanguages ?? tCommon('noDash')],
              [t('fieldNoPetCaregiver'), client.noPetCaregiver ? tCommon('yes') : tCommon('no')],
            ].map(([label, value]) => (
              <div key={label}>
                <div className="text-[10px] text-text-secondary">{label}</div>
                <div className="text-[13px] text-dark">{value}</div>
              </div>
            ))}
          </div>
        )}
        {activeTab === 'carePlan' && (
          <p className="text-text-secondary text-[13px]">{t('carePlanPhaseNote')}</p>
        )}
        {activeTab === 'authorizations' && (
          <div>
            {authorizations.length === 0 ? (
              <p className="text-text-secondary text-[13px]">{t('noAuthorizations')}</p>
            ) : (
              authorizations.map((auth) => {
                const pct = (auth.usedUnits / auth.authorizedUnits) * 100
                return (
                  <div key={auth.id} className="border border-border p-4 mb-3">
                    <div className="flex justify-between mb-2">
                      <span className="text-[13px] font-medium text-dark">{t('authHeader', { authNumber: auth.authNumber })}</span>
                      <span className="text-[12px] text-text-secondary">
                        {t('authUnitsUsed', { used: auth.usedUnits, authorized: auth.authorizedUnits, unitType: auth.unitType.toLowerCase() })}
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
                      {formatLocalDate(auth.startDate, { month: 'short', day: 'numeric', year: 'numeric' })} – {formatLocalDate(auth.endDate, { month: 'short', day: 'numeric', year: 'numeric' })}
                    </div>
                  </div>
                )
              })
            )}
          </div>
        )}
        {activeTab === 'documents' && (
          <p className="text-text-secondary text-[13px]">{t('documentsPhaseNote')}</p>
        )}
        {activeTab === 'familyPortal' && (
          <p className="text-text-secondary text-[13px]">{t('familyPortalPhaseNote')}</p>
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
git commit -m "feat(frontend): add Clients page and ClientDetailPanel (mock, react-i18next)"
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
import { useTranslation } from 'react-i18next'
import type { CaregiverResponse } from '../../types/api'
import { usePanelStore } from '../../store/panelStore'

// Date-only ISO strings (e.g. '2023-04-01') are parsed as UTC-midnight by the spec,
// causing off-by-one display in UTC-N timezones. Appending T12:00:00 keeps the date
// in the correct calendar day across all UTC-14 to UTC+14 zones.
function formatLocalDate(dateStr: string, options?: Intl.DateTimeFormatOptions): string {
  return new Date(dateStr + 'T12:00:00').toLocaleDateString('en-US', options)
}

interface CaregiversTableProps {
  caregivers: CaregiverResponse[]
  search: string
}

export function CaregiversTable({ caregivers, search }: CaregiversTableProps) {
  const { t } = useTranslation('caregivers')
  const tCommon = useTranslation('common').t
  const { openPanel } = usePanelStore()

  const filtered = caregivers.filter((c) => {
    const name = `${c.firstName} ${c.lastName}`.toLowerCase()
    return name.includes(search.toLowerCase())
  })

  if (filtered.length === 0) {
    return <p className="px-6 py-8 text-text-secondary text-[13px]">{t('noResults')}</p>
  }

  return (
    <table className="w-full text-[13px]">
      <thead>
        <tr className="border-b border-border bg-surface">
          {[t('colCaregiver'), t('colEmail'), t('colPhone'), t('colStatus'), t('colHireDate')].map((h) => (
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
            onClick={() => openPanel('caregiver', cg.id, { backLabel: t('backLabel') })}
          >
            <td className="px-6 py-3 font-medium text-dark">
              {cg.firstName} {cg.lastName}
            </td>
            <td className="px-6 py-3 text-text-secondary">{cg.email}</td>
            <td className="px-6 py-3 text-text-secondary">{cg.phone ?? tCommon('noDash')}</td>
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
                ? formatLocalDate(cg.hireDate, { month: 'short', day: 'numeric', year: 'numeric' })
                : tCommon('noDash')}
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
import { useTranslation } from 'react-i18next'
import { mockCaregivers } from '../../mock/data'
import { CaregiversTable } from './CaregiversTable'

export function CaregiversPage() {
  const { t } = useTranslation('caregivers')
  const tCommon = useTranslation('common').t
  const [search, setSearch] = useState('')

  return (
    <div className="flex flex-col h-full">
      <div className="flex items-center gap-4 px-6 py-4 bg-white border-b border-border">
        <h1 className="text-[16px] font-bold tracking-[-0.02em] text-dark">{t('pageTitle')}</h1>
        <input
          type="search"
          placeholder={tCommon('searchByName')}
          value={search}
          onChange={(e) => setSearch(e.target.value)}
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
import { useTranslation } from 'react-i18next'
import { mockCaregivers, mockCredentials } from '../../mock/data'
import { usePanelStore } from '../../store/panelStore'

// Date-only ISO strings (e.g. '2023-04-01') are parsed as UTC-midnight by the spec,
// causing off-by-one display in UTC-N timezones. Appending T12:00:00 keeps the date
// in the correct calendar day across all UTC-14 to UTC+14 zones.
function formatLocalDate(dateStr: string, options?: Intl.DateTimeFormatOptions): string {
  return new Date(dateStr + 'T12:00:00').toLocaleDateString('en-US', options)
}

type Tab = 'overview' | 'credentials' | 'backgroundChecks' | 'availability' | 'shiftHistory'

interface CaregiverDetailPanelProps {
  caregiverId: string
  backLabel: string
}

export function CaregiverDetailPanel({ caregiverId, backLabel }: CaregiverDetailPanelProps) {
  const { t } = useTranslation('caregivers')
  const tCommon = useTranslation('common').t
  const { closePanel } = usePanelStore()
  const [activeTab, setActiveTab] = useState<Tab>('overview')
  const caregiver = mockCaregivers.find((c) => c.id === caregiverId)

  const TABS: { id: Tab; label: string }[] = [
    { id: 'overview', label: t('tabOverview') },
    { id: 'credentials', label: t('tabCredentials') },
    { id: 'backgroundChecks', label: t('tabBackgroundChecks') },
    { id: 'availability', label: t('tabAvailability') },
    { id: 'shiftHistory', label: t('tabShiftHistory') },
  ]

  if (!caregiver) {
    return (
      <div className="p-8">
        <button type="button" className="text-[13px] mb-4" style={{ color: '#1a9afa' }} onClick={closePanel}>
          {backLabel}
        </button>
        <p className="text-text-secondary">{t('notFound')}</p>
      </div>
    )
  }

  const credentials = mockCredentials.filter((c) => c.caregiverId === caregiverId)

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
              [t('fieldPhone'), caregiver.phone ?? tCommon('noDash')],
              [t('fieldAddress'), caregiver.address ?? tCommon('noDash')],
              [t('fieldHireDate'), caregiver.hireDate ? formatLocalDate(caregiver.hireDate) : tCommon('noDash')],
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
        {activeTab === 'credentials' && (
          <div>
            {credentials.length === 0 ? (
              <p className="text-text-secondary text-[13px]">{t('noCredentials')}</p>
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
                        {t('credExpires')}{' '}
                        {cred.expiryDate
                          ? formatLocalDate(cred.expiryDate)
                          : tCommon('noExpiry')}
                        {expiring && (
                          <span className="ml-2 text-red-600 font-semibold">{t('credExpiringSoon')}</span>
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
                      {cred.verified ? t('credVerified') : t('credUnverified')}
                    </span>
                  </div>
                )
              })
            )}
          </div>
        )}
        {activeTab === 'backgroundChecks' && (
          <p className="text-text-secondary text-[13px]">{t('backgroundPhaseNote')}</p>
        )}
        {activeTab === 'availability' && (
          <p className="text-text-secondary text-[13px]">{t('availabilityPhaseNote')}</p>
        )}
        {activeTab === 'shiftHistory' && (
          <p className="text-text-secondary text-[13px]">{t('shiftHistoryPhaseNote')}</p>
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
git commit -m "feat(frontend): add Caregivers page and CaregiverDetailPanel (mock, react-i18next)"
```

---

### Task 14: Payers Page and EVV Status Page

**Files:**
- Create: `frontend/src/components/payers/PayersPage.tsx`
- Create: `frontend/src/components/evv/EvvStatusPage.tsx`

- [ ] **Step 14.1: Create PayersPage**

Create `frontend/src/components/payers/PayersPage.tsx`:

```tsx
import { useTranslation } from 'react-i18next'
import { mockPayers } from '../../mock/data'

export function PayersPage() {
  const { t } = useTranslation('payers')
  const tCommon = useTranslation('common').t

  const PAYER_TYPE_LABEL: Record<string, string> = {
    MEDICAID: t('typeMedicaid'),
    PRIVATE_PAY: t('typePrivatePay'),
    LTC_INSURANCE: t('typeLtcInsurance'),
    VA: t('typeVa'),
    MEDICARE: t('typeMedicare'),
  }

  return (
    <div className="flex flex-col h-full">
      <div className="flex items-center px-6 py-4 bg-white border-b border-border">
        <h1 className="text-[16px] font-bold tracking-[-0.02em] text-dark">{t('pageTitle')}</h1>
        <button
          type="button"
          className="ml-auto px-4 py-1.5 text-[12px] font-bold bg-dark text-white hover:brightness-110"
          onClick={() => alert(t('addPayerAlert'))}
        >
          {t('addPayer')}
        </button>
      </div>
      <div className="flex-1 overflow-auto">
        <table className="w-full text-[13px]">
          <thead>
            <tr className="border-b border-border bg-surface">
              {[t('colPayerName'), t('colType'), t('colState'), t('colEvvAggregator')].map((h) => (
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
                <td className="px-6 py-3 text-text-secondary">{payer.evvAggregator ?? tCommon('noDash')}</td>
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
import { useTranslation } from 'react-i18next'
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

function formatTime(iso: string | null, locale: string): string {
  if (!iso) return '—'
  return new Date(iso).toLocaleTimeString(locale, { hour: 'numeric', minute: '2-digit' })
}

export function EvvStatusPage() {
  const { t, i18n } = useTranslation('evvStatus')
  const tCommon = useTranslation('common').t
  const { openPanel } = usePanelStore()

  return (
    <div className="flex flex-col h-full">
      <div className="flex items-center px-6 py-4 bg-white border-b border-border">
        <h1 className="text-[16px] font-bold tracking-[-0.02em] text-dark">{t('pageTitle')}</h1>
        <span className="ml-3 text-[12px] text-text-secondary">{t('subtitle')}</span>
      </div>
      <div className="flex-1 overflow-auto">
        <table className="w-full text-[13px]">
          <thead>
            <tr className="border-b border-border bg-surface">
              {[t('colClient'), t('colCaregiver'), t('colService'), t('colDate'), t('colClockIn'), t('colClockOut'), t('colStatus')].map((h) => (
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
                onClick={() => openPanel('shift', row.shiftId, { backLabel: t('backLabel') })}
              >
                <td className="px-6 py-3 font-medium text-dark">
                  {row.clientFirstName} {row.clientLastName}
                </td>
                <td className="px-6 py-3 text-text-secondary">
                  {row.caregiverFirstName
                    ? `${row.caregiverFirstName} ${row.caregiverLastName}`
                    : tCommon('noDash')}
                </td>
                <td className="px-6 py-3 text-text-secondary">{row.serviceTypeName}</td>
                <td className="px-6 py-3 text-text-secondary">
                  {new Date(row.scheduledStart).toLocaleDateString(i18n.language, {
                    month: 'short',
                    day: 'numeric',
                  })}
                </td>
                <td className="px-6 py-3 text-text-secondary">{formatTime(row.timeIn, i18n.language)}</td>
                <td className="px-6 py-3 text-text-secondary">{formatTime(row.timeOut, i18n.language)}</td>
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

- [ ] **Step 14.3: Verify full build**

```bash
cd frontend && npx tsc --noEmit 2>&1
```

Expected: no errors. Fix any type errors before proceeding.

- [ ] **Step 14.4: Commit**

```bash
cd ..
git add frontend/src/components/payers/ frontend/src/components/evv/
git commit -m "feat(frontend): add Payers and EVV Status pages (mock, react-i18next)"
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

// i18next is initialized synchronously in src/test/setup.ts with real English strings.
// No mock needed — useTranslation returns actual translations in tests.

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
  // Use exact match on the <span>care</span> within the logo to avoid
  // matching "Caregivers" nav link which also contains the substring "care".
  await expect(page.locator('aside span').filter({ hasText: /^care$/ }).first()).toBeVisible()
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

- [ ] **Step 16.4: Run e2e tests**

```bash
cd frontend && npx playwright test 2>&1 | tail -15
```

> The `webServer` block in `playwright.config.ts` starts `npm run dev` automatically (`reuseExistingServer: true` reuses port 5173 if already running). No separate terminal needed.

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
| Shift blocks visible for today Apr 7 (Alice Johnson — RED, Robert Martinez — YELLOW, Dorothy Chen — GREY uncovered, James Williams — GREY assigned) | ✅ |
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

> **Note:** If two shifts overlap in the same day column (e.g., after manually creating a conflicting shift via New Shift), their blocks will visually stack on top of each other. This is expected — overlap detection is not implemented in Phase 1.

**When all checks pass:** Proceed to `2026-04-06-frontend-phase-2-dashboard-endpoint.md`.
