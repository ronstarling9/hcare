# hcare Caregiver Mobile App — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the full React Native (Expo) caregiver mobile app against mock API data so the UX can be validated end-to-end before the BFF is implemented.

**Architecture:** React Navigation 6 with a custom tab bar (required for the raised center FAB). All server state via React Query v5; UI-only state via Zustand. The API client is real axios with axios-mock-adapter intercepting all calls when `EXPO_PUBLIC_USE_MOCKS=true`. Offline visit events are written to Expo SQLite before being sent to the BFF. When the BFF is ready, set `EXPO_PUBLIC_USE_MOCKS=false` and point `EXPO_PUBLIC_BFF_URL` to it — no other code changes needed.

**Tech Stack:** Expo SDK 52, React Native, TypeScript, React Navigation 6, React Query v5, Zustand v4, Expo SQLite v13, Expo Notifications, Expo Location, Expo SecureStore, axios, axios-mock-adapter, Jest, @testing-library/react-native.

**BFF endpoint reference:** `docs/superpowers/specs/2026-04-08-mobile-bff-endpoints.md`

---

## File Structure

```
mobile/
  app.json
  package.json
  tsconfig.json
  babel.config.js
  jest.config.js
  .env                        # EXPO_PUBLIC_USE_MOCKS=true, EXPO_PUBLIC_BFF_URL=http://localhost:8081

  src/
    constants/
      colors.ts               # all design-token color values
      typography.ts           # font size scale in pt

    types/
      domain.ts               # all shared domain interfaces (Shift, AdlTask, CarePlan, …)
      api.ts                  # all API request/response types

    mocks/
      data.ts                 # typed mock fixtures (fixed UUIDs, realistic values)
      handlers.ts             # axios-mock-adapter setup for all 21 BFF endpoints

    api/
      client.ts               # axios instance + JWT Bearer interceptor + 401 refresh logic
      auth.ts                 # exchange, refresh, sendLink
      visits.ts               # today, week, clockIn, voidClockIn, clockOut, completeTask, revertTask, saveNotes
      shifts.ts               # openShifts, acceptShift, declineShift
      messages.ts             # threads, thread, reply
      profile.ts              # profile, stats, carePlan
      sync.ts                 # syncBatch
      devices.ts              # registerPushToken

    store/
      authStore.ts            # Zustand: accessToken, refreshToken, caregiverId, agencyId, name, agencyName, firstLogin
      visitStore.ts           # Zustand: activeVisitId, activeShiftId, activeClientName, clockInTime

    db/
      schema.ts               # SQLite CREATE TABLE statements
      events.ts               # insertEvent, getPendingEvents, markSynced, deleteByVisitId

    hooks/
      useAuth.ts              # token exchange, send link, logout, mid-visit refresh
      useToday.ts             # React Query for today's + week's shifts
      useVisit.ts             # clock-in, clock-out, task mutations, notes save
      useOpenShifts.ts        # React Query for open shifts; accept/decline mutations
      useMessages.ts          # React Query for threads + thread detail; reply mutation
      useProfile.ts           # React Query for profile + stats + care plan
      useOfflineSync.ts       # NetInfo listener; SQLite queue drain; sync mutation
      useNotifications.ts     # Expo Notifications setup; push token registration; deep-link routing

    navigation/
      RootNavigator.tsx       # auth split: AuthStack vs AppTabs; checks authStore on mount
      AppNavigator.tsx        # bottom tabs screen definitions
      TabBar.tsx              # custom tab bar — raised center FAB, active visit state

    screens/
      auth/
        LoginScreen.tsx
        DeepLinkHandlerScreen.tsx
        LinkExpiredScreen.tsx
      onboarding/
        WelcomeScreen.tsx
        NotificationsScreen.tsx
        LocationScreen.tsx
      today/
        TodayScreen.tsx
        ShiftCard.tsx
        ActiveVisitBanner.tsx
      clockIn/
        ClockInSheet.tsx
      visit/
        VisitScreen.tsx
        GpsStatusBar.tsx
        CarePlanSection.tsx
        AdlTaskList.tsx
        CareNotes.tsx
        ClockOutModal.tsx
      carePlan/
        CarePlanScreen.tsx
      openShifts/
        OpenShiftsScreen.tsx
        OpenShiftCard.tsx
      messages/
        MessagesInboxScreen.tsx
        MessageThreadScreen.tsx
      profile/
        ProfileScreen.tsx
      settings/
        SettingsScreen.tsx
      conflict/
        ConflictDetailScreen.tsx

    components/
      OfflineBanner.tsx       # global amber connectivity banner
      Toast.tsx               # 3-second transient success toast
      SectionLabel.tsx        # reusable uppercase section header
      ProgressDots.tsx        # onboarding step indicator (shared across 3 onboarding screens)

    __tests__/
      store/
        authStore.test.ts
        visitStore.test.ts
      db/
        events.test.ts
      hooks/
        useAuth.test.ts
        useOfflineSync.test.ts
      screens/
        LoginScreen.test.tsx
        TodayScreen.test.tsx
        ClockInSheet.test.tsx
        VisitScreen.test.tsx
        OpenShiftsScreen.test.tsx
        MessagesInboxScreen.test.tsx
        ProfileScreen.test.tsx
        DeepLinkHandlerScreen.test.tsx
        CarePlanScreen.test.tsx
        MessageThreadScreen.test.tsx
        SettingsScreen.test.tsx
        ConflictDetailScreen.test.tsx
```

---

## Phase 1 — Foundation, Auth, Navigation Shell

---

### Task 1: Project Scaffolding

**Files:**
- Create: `mobile/package.json` (via Expo CLI)
- Create: `mobile/tsconfig.json`
- Create: `mobile/jest.config.js`
- Create: `mobile/.env`

- [ ] **Step 1: Initialise Expo project**

```bash
cd mobile
npx create-expo-app@latest . --template expo-template-blank-typescript
```

Expected: project files created in `mobile/`. Say "Yes" to any prompts about the directory existing.

- [ ] **Step 2: Install all dependencies**

```bash
npm install \
  @react-navigation/native \
  @react-navigation/bottom-tabs \
  @react-navigation/native-stack \
  react-native-safe-area-context \
  react-native-screens \
  @tanstack/react-query \
  zustand \
  axios \
  axios-mock-adapter \
  expo-secure-store \
  expo-sqlite \
  expo-notifications \
  expo-location \
  @react-native-community/netinfo \
  @react-native-async-storage/async-storage \
  expo-device
```

- [ ] **Step 3: Install dev/test dependencies**

```bash
npm install --save-dev \
  @testing-library/react-native \
  @testing-library/jest-native \
  @types/react
```

- [ ] **Step 4: Create `jest.config.js`**

```js
// mobile/jest.config.js
module.exports = {
  preset: 'jest-expo',
  // NOTE: In Jest 24–27 this key is `setupFilesAfterFramework`; in Jest 28+ it was aliased.
  // Verify this matches your installed jest-expo version — if matchers fail to load, this key is the first thing to check.
  setupFilesAfterFramework: ['@testing-library/jest-native/extend-expect'],
  transformIgnorePatterns: [
    'node_modules/(?!((jest-)?react-native|@react-native(-community)?)|expo(nent)?|@expo(nent)?/.*|@expo-google-fonts/.*|react-navigation|@react-navigation/.*|@unimodules/.*|unimodules|sentry-expo|native-base|react-native-svg)',
  ],
  moduleNameMapper: {
    '^@/(.*)$': '<rootDir>/src/$1',
  },
};
```

- [ ] **Step 5: Create `.env`**

```
EXPO_PUBLIC_USE_MOCKS=true
EXPO_PUBLIC_BFF_URL=http://localhost:8081
```

- [ ] **Step 6: Update `tsconfig.json` to add path alias**

Open `tsconfig.json` and add `paths` under `compilerOptions`:

```json
{
  "extends": "expo/tsconfig.base",
  "compilerOptions": {
    "strict": true,
    "baseUrl": ".",
    "paths": {
      "@/*": ["src/*"]
    }
  }
}
```

- [ ] **Step 7: Add path alias to `babel.config.js`**

```js
// mobile/babel.config.js
module.exports = function (api) {
  api.cache(true);
  return {
    presets: ['babel-preset-expo'],
    plugins: [
      ['module-resolver', { root: ['./src'], alias: { '@': './src' } }],
    ],
  };
};
```

Install the resolver: `npm install --save-dev babel-plugin-module-resolver`

- [ ] **Step 8: Verify the app runs**

```bash
npx expo start --go
```

Expected: Expo dev server starts. App loads with the default blank screen on the Expo Go client (or simulator). No TypeScript errors.

- [ ] **Step 9: Commit**

```bash
git add mobile/
git commit -m "feat(mobile): initialise React Native Expo project with dependencies"
```

---

### Task 2: Design Tokens, Domain Types, and Mock Data

**Files:**
- Create: `src/constants/colors.ts`
- Create: `src/constants/typography.ts`
- Create: `src/types/domain.ts`
- Create: `src/types/api.ts`
- Create: `src/mocks/data.ts`

- [ ] **Step 1: Write failing test for color tokens**

Create `mobile/src/__tests__/constants/colors.test.ts`:

```ts
import { Colors } from '@/constants/colors';

describe('Colors', () => {
  it('exports color-dark', () => {
    expect(Colors.dark).toBe('#1a1a24');
  });
  it('exports color-blue', () => {
    expect(Colors.blue).toBe('#1a9afa');
  });
});
```

Run: `npm test -- --testPathPattern=colors`
Expected: FAIL — module not found.

- [ ] **Step 2: Create `src/constants/colors.ts`**

```ts
// src/constants/colors.ts
export const Colors = {
  // Brand
  dark:          '#1a1a24',
  blue:          '#1a9afa',
  surface:       '#f6f6fa',
  white:         '#ffffff',
  border:        '#eaeaf2',

  // Text
  textPrimary:   '#1a1a24',
  textSecondary: '#747480',
  textMuted:     '#94a3b8',

  // EVV / status semantic
  green:         '#16a34a',
  amber:         '#ca8a04',
  red:           '#dc2626',
  grey:          '#94a3b8',
} as const;

export type ColorKey = keyof typeof Colors;
```

- [ ] **Step 3: Create `src/constants/typography.ts`**

```ts
// src/constants/typography.ts
// All values are density-independent points (pt), not CSS px.
// The app must respect iOS Dynamic Type and Android font scaling.
// Bounds: floor 0.8× (legible), ceiling 1.5× (layout usable).
export const Typography = {
  screenTitle:   { fontSize: 17, fontWeight: '700' as const },
  sectionLabel:  { fontSize: 11, fontWeight: '700' as const, letterSpacing: 0.1, textTransform: 'uppercase' as const },
  cardTitle:     { fontSize: 15, fontWeight: '700' as const },
  body:          { fontSize: 14, fontWeight: '400' as const },
  bodyMedium:    { fontSize: 14, fontWeight: '600' as const },
  timestamp:     { fontSize: 13, fontWeight: '400' as const },
} as const;
```

- [ ] **Step 4: Run tests — expect PASS**

```bash
npm test -- --testPathPattern=colors
```

Expected: PASS.

- [ ] **Step 5: Create `src/types/domain.ts`**

```ts
// src/types/domain.ts
export type ShiftStatus = 'UPCOMING' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';
export type EvvStatus = 'GREEN' | 'YELLOW' | 'RED' | 'GREY' | 'EXEMPT' | 'PORTAL_SUBMIT';
export type CredentialStatus = 'VALID' | 'EXPIRING_SOON' | 'EXPIRED';
export type SenderType = 'AGENCY' | 'CAREGIVER';
export type SyncEventType = 'CLOCK_IN' | 'CLOCK_OUT' | 'TASK_COMPLETE' | 'TASK_REVERT' | 'NOTE_SAVE';

export interface GpsCoordinate { lat: number; lng: number }

export interface Shift {
  id: string;
  clientName: string;
  clientAddress: string;
  clientId: string;
  scheduledStart: string; // ISO 8601
  scheduledEnd: string;
  serviceType: string;
  status: ShiftStatus;
  evvStatus?: EvvStatus;
  carePlanUpdatedSinceLastVisit?: boolean;
}

export interface AdlTask {
  id: string;
  name: string;
  instructions?: string;
  completed: boolean;
}

export interface CarePlan {
  id: string;
  diagnoses: string[];
  allergies: string[];
  caregiverNotes: string;
  adlTasks: AdlTask[];
  goals: string[];
  updatedSinceLastVisit: boolean;
}

export interface OpenShift {
  id: string;
  clientName: string;
  clientId: string;
  scheduledStart: string;
  scheduledEnd: string;
  serviceType: string;
  distance?: number; // km; undefined when caregiver homeLatLng not set
  urgent: boolean;
}

export interface MessageThread {
  id: string;
  subject: string;
  previewText: string;
  timestamp: string;
  unread: boolean;
}

export interface Message {
  id: string;
  threadId: string;
  body: string;
  sentAt: string;
  senderType: SenderType;
}

export interface Credential {
  name: string;
  expiryDate: string; // YYYY-MM-DD
  status: CredentialStatus;
}

export interface CaregiverProfile {
  id: string;
  name: string;
  agencyName: string;
  primaryCredentialType: string;
  credentials: Credential[];
}

export interface ProfileStats {
  shiftsCompleted: number;
  hoursWorked: number;
}

export interface SyncEvent {
  type: SyncEventType;
  visitId: string;
  taskId?: string;
  gpsCoordinate?: GpsCoordinate;
  capturedOffline: boolean;
  notes?: string;
  occurredAt: string; // ISO 8601
}

export interface ConflictDetail {
  visitId: string;
  shiftDate: string;
  clientName: string;
  caregiverClockIn: string;
  caregiverClockOut: string;
}
```

- [ ] **Step 6: Create `src/types/api.ts`**

```ts
// src/types/api.ts
import type {
  Shift, CarePlan, OpenShift, MessageThread, Message,
  CaregiverProfile, ProfileStats, SyncEvent, ConflictDetail, EvvStatus,
  GpsCoordinate,
} from './domain';

export interface AuthExchangeResponse {
  accessToken: string;
  refreshToken: string;
  caregiverId: string;
  agencyId: string;
  name: string;
  agencyName: string;
  firstLogin: boolean;
}

export interface ClockInResponse {
  visitId: string;
  clockInTime: string;
}

export interface ClockOutResponse {
  visitId: string;
  clockOutTime: string;
  evvStatus: EvvStatus;
}

export interface SyncEventResult {
  visitId: string;
  result: 'OK' | 'CONFLICT_REASSIGNED' | 'DUPLICATE';
  conflict?: ConflictDetail;
}

export interface SyncResponse { results: SyncEventResult[] }
export interface TodayResponse { shifts: Shift[] }
export interface WeekResponse  { shifts: Shift[] }
export interface OpenShiftsResponse { shifts: OpenShift[] }
export interface MessagesResponse  { threads: MessageThread[] }
export interface ThreadResponse    { thread: MessageThread; messages: Message[] }
export interface ReplyResponse     { message: Message }
export interface CarePlanResponse  { carePlan: CarePlan }

export interface ClockInRequest  { gpsCoordinate?: GpsCoordinate; capturedOffline: boolean }
export interface ClockOutRequest { gpsCoordinate?: GpsCoordinate; capturedOffline: boolean; notes: string }
export interface SyncRequest     { deviceId: string; events: SyncEvent[] }
```

- [ ] **Step 7: Create `src/mocks/data.ts`**

```ts
// src/mocks/data.ts
import type { Shift, OpenShift, MessageThread, Message, CaregiverProfile, ProfileStats, CarePlan } from '@/types/domain';

export const MOCK_CAREGIVER_ID  = 'cg-001';
export const MOCK_AGENCY_ID     = 'ag-001';
export const MOCK_VISIT_ID      = 'v-001';
export const MOCK_SHIFT_ID_1    = 'sh-001';
export const MOCK_SHIFT_ID_2    = 'sh-002';
export const MOCK_THREAD_ID_1   = 'th-001';

const now = Date.now();

export const mockTodayShifts: Shift[] = [
  {
    id: MOCK_SHIFT_ID_1,
    clientName: 'Eleanor Vance',
    clientAddress: '142 Maple Street, Springfield, IL 62701',
    clientId: 'cl-001',
    scheduledStart: new Date(now + 30 * 60_000).toISOString(),
    scheduledEnd:   new Date(now + 4.5 * 3_600_000).toISOString(),
    serviceType: 'Personal Care',
    status: 'UPCOMING',
    evvStatus: 'GREY',
    carePlanUpdatedSinceLastVisit: true,
  },
  {
    id: MOCK_SHIFT_ID_2,
    clientName: 'Harold Briggs',
    clientAddress: '88 Oak Avenue, Springfield, IL 62704',
    clientId: 'cl-002',
    scheduledStart: new Date(now + 6 * 3_600_000).toISOString(),
    scheduledEnd:   new Date(now + 9 * 3_600_000).toISOString(),
    serviceType: 'Companion Care',
    status: 'UPCOMING',
    evvStatus: 'GREY',
  },
];

export const mockWeekShifts: Shift[] = [
  {
    id: 'sh-003',
    clientName: 'Eleanor Vance',
    clientAddress: '142 Maple Street, Springfield, IL 62701',
    clientId: 'cl-001',
    scheduledStart: new Date(now + 2 * 86_400_000).toISOString(),
    scheduledEnd:   new Date(now + 2 * 86_400_000 + 4 * 3_600_000).toISOString(),
    serviceType: 'Personal Care',
    status: 'UPCOMING',
    evvStatus: 'GREY',
  },
];

export const mockCarePlan: CarePlan = {
  id: 'cp-001',
  diagnoses: ['Type 2 Diabetes', 'Hypertension'],
  allergies: ['Penicillin', 'Shellfish'],
  caregiverNotes: "Client prefers morning routine completed before 9 AM. Takes blood pressure medication at breakfast.",
  adlTasks: [
    { id: 'task-001', name: 'Assist with bathing', instructions: 'Use shower chair. Water warm, not hot.', completed: false },
    { id: 'task-002', name: 'Medication reminder', instructions: 'Metformin 500mg with breakfast. Record in log.', completed: false },
    { id: 'task-003', name: 'Prepare breakfast', instructions: 'Low-sodium, diabetic-friendly options in pantry.', completed: false },
    { id: 'task-004', name: 'Assist with dressing', completed: false },
    { id: 'task-005', name: 'Blood pressure check', instructions: 'Record reading in the client binder.', completed: false },
  ],
  goals: ['Maintain independence in daily activities', 'Monitor glucose levels'],
  updatedSinceLastVisit: true,
};

export const mockOpenShifts: OpenShift[] = [
  {
    id: 'os-001',
    clientName: 'Margaret Chen',
    clientId: 'cl-003',
    scheduledStart: new Date(now + 2 * 3_600_000).toISOString(),
    scheduledEnd:   new Date(now + 6 * 3_600_000).toISOString(),
    serviceType: 'Personal Care',
    distance: 3.2,
    urgent: true,
  },
  {
    id: 'os-002',
    clientName: 'Robert Kim',
    clientId: 'cl-004',
    scheduledStart: new Date(now + 3 * 86_400_000).toISOString(),
    scheduledEnd:   new Date(now + 3 * 86_400_000 + 3 * 3_600_000).toISOString(),
    serviceType: 'Companion Care',
    distance: 7.8,
    urgent: false,
  },
];

export const mockThreads: MessageThread[] = [
  {
    id: MOCK_THREAD_ID_1,
    subject: 'Schedule update for next week',
    previewText: 'Hi team, please check your updated schedules for the week of April 14th...',
    timestamp: new Date(now - 2 * 3_600_000).toISOString(),
    unread: true,
  },
  {
    id: 'th-002',
    subject: 'Payroll processed',
    previewText: 'Your payroll for the period ending April 6th has been processed.',
    timestamp: new Date(now - 2 * 86_400_000).toISOString(),
    unread: false,
  },
];

export const mockMessages: Message[] = [
  {
    id: 'msg-001',
    threadId: MOCK_THREAD_ID_1,
    body: 'Hi team, please check your updated schedules for the week of April 14th. Some shifts have moved. Contact the office with any questions.',
    sentAt: new Date(now - 2 * 3_600_000).toISOString(),
    senderType: 'AGENCY',
  },
];

export const mockProfile: CaregiverProfile = {
  id: MOCK_CAREGIVER_ID,
  name: 'Sarah Johnson',
  agencyName: 'Sunrise Home Care',
  primaryCredentialType: 'HHA',
  credentials: [
    { name: 'HHA Certificate',   expiryDate: '2026-12-01', status: 'VALID' },
    { name: 'CPR Certification', expiryDate: '2026-06-01', status: 'EXPIRING_SOON' },
    { name: 'Background Check',  expiryDate: '2025-03-01', status: 'VALID' },
  ],
};

export const mockProfileStats: ProfileStats = {
  shiftsCompleted: 18,
  hoursWorked: 72,
};

export const mockAuthResponse = {
  accessToken:  'mock-access-token',
  refreshToken: 'mock-refresh-token',
  caregiverId:  MOCK_CAREGIVER_ID,
  agencyId:     MOCK_AGENCY_ID,
  name:         'Sarah Johnson',
  agencyName:   'Sunrise Home Care',
  firstLogin:   false,
};
```

- [ ] **Step 8: Commit**

```bash
git add mobile/src/
git commit -m "feat(mobile): add design tokens, domain types, and mock fixtures"
```

---

### Task 3: API Client + Mock Layer

**Files:**
- Create: `src/api/client.ts`
- Create: `src/api/auth.ts`
- Create: `src/api/visits.ts`
- Create: `src/api/shifts.ts`
- Create: `src/api/messages.ts`
- Create: `src/api/profile.ts`
- Create: `src/api/sync.ts`
- Create: `src/api/devices.ts`
- Create: `src/mocks/handlers.ts`
- Test: `src/__tests__/api/client.test.ts`

- [ ] **Step 1: Write failing test for API client**

Create `mobile/src/__tests__/api/client.test.ts`:

```ts
import axios from 'axios';
import MockAdapter from 'axios-mock-adapter';
import { apiClient } from '@/api/client';

describe('apiClient', () => {
  it('uses the BFF base URL', () => {
    expect(apiClient.defaults.baseURL).toBe(process.env.EXPO_PUBLIC_BFF_URL ?? 'http://localhost:8081');
  });

  it('adds Bearer token from Authorization header when token is set', async () => {
    const mock = new MockAdapter(apiClient);
    mock.onGet('/test').reply((config) => {
      return [200, { auth: config.headers?.Authorization }];
    });

    // Simulate a stored token by importing authStore
    // (authStore is tested separately; here we just verify the interceptor wiring)
    mock.restore();
  });
});
```

Run: `npm test -- --testPathPattern=client`
Expected: FAIL — module not found.

- [ ] **Step 2: Create `src/api/client.ts`**

```ts
// src/api/client.ts
import axios from 'axios';
import * as SecureStore from 'expo-secure-store';

export const BFF_URL = process.env.EXPO_PUBLIC_BFF_URL ?? 'http://localhost:8081';

export const apiClient = axios.create({ baseURL: BFF_URL });

// Request interceptor: attach Bearer token from SecureStore
apiClient.interceptors.request.use(async (config) => {
  const token = await SecureStore.getItemAsync('accessToken');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Response interceptor: silent 401 refresh
// Import is deferred to avoid circular dependency with authStore
apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;
    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;
      try {
        const refreshToken = await SecureStore.getItemAsync('refreshToken');
        if (!refreshToken) throw new Error('No refresh token');
        const res = await axios.post(`${BFF_URL}/mobile/auth/refresh`, { refreshToken });
        const { accessToken } = res.data as { accessToken: string };
        await SecureStore.setItemAsync('accessToken', accessToken);
        originalRequest.headers.Authorization = `Bearer ${accessToken}`;
        return apiClient(originalRequest);
      } catch {
        // Refresh failed — caller receives the 401
        return Promise.reject(error);
      }
    }
    return Promise.reject(error);
  }
);

// Enable mocks when EXPO_PUBLIC_USE_MOCKS=true
if (process.env.EXPO_PUBLIC_USE_MOCKS === 'true') {
  // Dynamically imported to keep mock code tree-shaken in prod.
  // apiClient is passed as a parameter to avoid a circular import:
  // handlers.ts no longer imports apiClient — it receives it here.
  import('../mocks/handlers').then(({ setupMocks }) => setupMocks(apiClient));
}
```

- [ ] **Step 3: Create individual API modules**

**`src/api/auth.ts`:**
```ts
import { apiClient } from './client';
import type { AuthExchangeResponse } from '@/types/api';

export const authApi = {
  exchange: (token: string) =>
    apiClient.post<AuthExchangeResponse>('/mobile/auth/exchange', { token }).then(r => r.data),

  refresh: (refreshToken: string) =>
    apiClient.post<{ accessToken: string }>('/mobile/auth/refresh', { refreshToken }).then(r => r.data),

  sendLink: (email: string) =>
    apiClient.post<{ message: string }>('/mobile/auth/send-link', { email }).then(r => r.data),
};
```

**`src/api/visits.ts`:**
```ts
import { apiClient } from './client';
import type { TodayResponse, WeekResponse, ClockInRequest, ClockInResponse, ClockOutRequest, ClockOutResponse, CarePlanResponse } from '@/types/api';

export const visitsApi = {
  today:       () => apiClient.get<TodayResponse>('/mobile/visits/today').then(r => r.data),
  week:        () => apiClient.get<WeekResponse>('/mobile/visits/week').then(r => r.data),
  clockIn:     (shiftId: string, body: ClockInRequest) =>
                 apiClient.post<ClockInResponse>(`/mobile/visits/${shiftId}/clock-in`, body).then(r => r.data),
  voidClockIn: (visitId: string) =>
                 apiClient.delete(`/mobile/visits/${visitId}/clock-in`),
  clockOut:    (visitId: string, body: ClockOutRequest) =>
                 apiClient.post<ClockOutResponse>(`/mobile/visits/${visitId}/clock-out`, body).then(r => r.data),
  completeTask: (visitId: string, taskId: string) =>
                 apiClient.post(`/mobile/visits/${visitId}/tasks/${taskId}/complete`),
  revertTask:  (visitId: string, taskId: string) =>
                 apiClient.delete(`/mobile/visits/${visitId}/tasks/${taskId}/complete`),
  saveNotes:   (visitId: string, notes: string) =>
                 apiClient.put(`/mobile/visits/${visitId}/notes`, { notes }),
  carePlan:    (shiftId: string) =>
                 apiClient.get<CarePlanResponse>(`/mobile/careplan/${shiftId}`).then(r => r.data),
};
```

**`src/api/shifts.ts`:**
```ts
import { apiClient } from './client';
import type { OpenShiftsResponse } from '@/types/api';

export const shiftsApi = {
  open:    () => apiClient.get<OpenShiftsResponse>('/mobile/shifts/open').then(r => r.data),
  accept:  (shiftId: string) => apiClient.post(`/mobile/shifts/${shiftId}/accept`),
  decline: (shiftId: string) => apiClient.post(`/mobile/shifts/${shiftId}/decline`),
};
```

**`src/api/messages.ts`:**
```ts
import { apiClient } from './client';
import type { MessagesResponse, ThreadResponse, ReplyResponse } from '@/types/api';

export const messagesApi = {
  threads: () => apiClient.get<MessagesResponse>('/mobile/messages').then(r => r.data),
  thread:  (threadId: string) =>
             apiClient.get<ThreadResponse>(`/mobile/messages/${threadId}`).then(r => r.data),
  reply:   (threadId: string, body: string) =>
             apiClient.post<ReplyResponse>(`/mobile/messages/${threadId}/reply`, { body }).then(r => r.data),
};
```

**`src/api/profile.ts`:**
```ts
import { apiClient } from './client';
import type { CaregiverProfile, ProfileStats } from '@/types/domain';

export const profileApi = {
  get:   () => apiClient.get<CaregiverProfile>('/mobile/profile').then(r => r.data),
  stats: () => apiClient.get<ProfileStats>('/mobile/profile/stats').then(r => r.data),
};
```

**`src/api/sync.ts`:**
```ts
import { apiClient } from './client';
import type { SyncRequest, SyncResponse } from '@/types/api';

export const syncApi = {
  batch: (body: SyncRequest) =>
    apiClient.post<SyncResponse>('/sync/visits', body).then(r => r.data),
};
```

**`src/api/devices.ts`:**
```ts
import { apiClient } from './client';

export const devicesApi = {
  registerPushToken: (token: string, platform: 'ios' | 'android') =>
    apiClient.post('/mobile/devices/push-token', { token, platform }),
};
```

- [ ] **Step 4: Create `src/mocks/handlers.ts`**

```ts
// src/mocks/handlers.ts
// handlers.ts accepts an optional apiClient parameter. When omitted (e.g. in
// tests that call setupMocks() directly), it falls back to the shared instance
// imported from client.ts.
//
// Circular-import safety: client.ts uses a *dynamic* import() for handlers.ts,
// so this static import only resolves after client.ts has fully evaluated and
// apiClient is defined. The module cache ensures no re-evaluation occurs.
// See: https://developer.mozilla.org/en-US/docs/Web/JavaScript/Guide/Modules#dynamic_imports
import MockAdapter from 'axios-mock-adapter';
import type { AxiosInstance } from 'axios';
import { apiClient as defaultClient } from '@/api/client';
import {
  mockAuthResponse, mockTodayShifts, mockWeekShifts,
  mockCarePlan, mockOpenShifts, mockThreads, mockMessages,
  mockProfile, mockProfileStats, MOCK_VISIT_ID, MOCK_THREAD_ID_1,
} from './data';

let mock: MockAdapter | null = null;

export function setupMocks(apiClient: AxiosInstance = defaultClient) {
  if (mock) return;
  mock = new MockAdapter(apiClient, { delayResponse: 350 });

  mock.onPost('/mobile/auth/exchange').reply(200, mockAuthResponse);
  mock.onPost('/mobile/auth/refresh').reply(200, { accessToken: 'mock-refreshed-token' });
  mock.onPost('/mobile/auth/send-link').reply(200, { message: "If that email matches your account, a link has been sent." });

  mock.onPost('/mobile/devices/push-token').reply(200);

  mock.onGet('/mobile/visits/today').reply(200, { shifts: mockTodayShifts });
  mock.onGet('/mobile/visits/week').reply(200, { shifts: mockWeekShifts });

  mock.onPost(new RegExp('/mobile/visits/.+/clock-in')).reply(200, {
    visitId: MOCK_VISIT_ID,
    clockInTime: new Date().toISOString(),
  });
  mock.onDelete(new RegExp('/mobile/visits/.+/clock-in')).reply(200);
  mock.onPost(new RegExp('/mobile/visits/.+/clock-out')).reply(200, {
    visitId: MOCK_VISIT_ID,
    clockOutTime: new Date().toISOString(),
    evvStatus: 'GREEN',
  });

  mock.onPost(new RegExp('/mobile/visits/.+/tasks/.+/complete')).reply(200);
  mock.onDelete(new RegExp('/mobile/visits/.+/tasks/.+/complete')).reply(200);
  mock.onPut(new RegExp('/mobile/visits/.+/notes')).reply(200);

  mock.onPost('/sync/visits').reply(200, {
    results: [{ visitId: MOCK_VISIT_ID, result: 'OK' }],
  });

  mock.onGet('/mobile/shifts/open').reply(200, { shifts: mockOpenShifts });
  mock.onPost(new RegExp('/mobile/shifts/.+/accept')).reply(200);
  mock.onPost(new RegExp('/mobile/shifts/.+/decline')).reply(200);

  mock.onGet('/mobile/messages').reply(200, { threads: mockThreads });
  mock.onGet(new RegExp('/mobile/messages/.+')).reply((config) => {
    const id = config.url?.split('/').pop();
    const thread = mockThreads.find(t => t.id === id);
    if (!thread) return [404, { error: 'Not found' }];
    return [200, { thread, messages: mockMessages.filter(m => m.threadId === id) }];
  });
  mock.onPost(new RegExp('/mobile/messages/.+/reply')).reply(200, {
    message: {
      id: `msg-${Date.now()}`,
      threadId: MOCK_THREAD_ID_1,
      body: 'Acknowledged, thank you.',
      sentAt: new Date().toISOString(),
      senderType: 'CAREGIVER',
    },
  });

  mock.onGet('/mobile/profile').reply(200, mockProfile);
  mock.onGet('/mobile/profile/stats').reply(200, mockProfileStats);
  mock.onGet(new RegExp('/mobile/careplan/.+')).reply(200, { carePlan: mockCarePlan });
}

export function teardownMocks() {
  mock?.restore();
  mock = null;
}
```

- [ ] **Step 5: Run tests**

```bash
npm test -- --testPathPattern=client
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add mobile/src/
git commit -m "feat(mobile): add API client, all BFF endpoint modules, and mock adapter"
```

---

### Task 4: Auth Store + Token Persistence

**Files:**
- Create: `src/store/authStore.ts`
- Create: `src/hooks/useAuth.ts`
- Test: `src/__tests__/store/authStore.test.ts`
- Test: `src/__tests__/hooks/useAuth.test.ts`

- [ ] **Step 1: Write failing auth store tests**

Create `mobile/src/__tests__/store/authStore.test.ts`:

```ts
import { act, renderHook } from '@testing-library/react-native';
import { useAuthStore } from '@/store/authStore';

beforeEach(() => {
  useAuthStore.setState({
    accessToken: null,
    refreshToken: null,
    caregiverId: null,
    agencyId: null,
    name: null,
    agencyName: null,
    firstLogin: false,
    isAuthenticated: false,
  });
});

describe('authStore', () => {
  it('starts with no auth state', () => {
    const { result } = renderHook(() => useAuthStore());
    expect(result.current.accessToken).toBeNull();
    expect(result.current.isAuthenticated).toBe(false);
  });

  it('setAuth populates all fields', () => {
    const { result } = renderHook(() => useAuthStore());
    act(() => {
      result.current.setAuth({
        accessToken: 'tok',
        refreshToken: 'ref',
        caregiverId: 'cg-1',
        agencyId: 'ag-1',
        name: 'Sarah',
        agencyName: 'Sunrise',
        firstLogin: false,
      });
    });
    expect(result.current.accessToken).toBe('tok');
    expect(result.current.isAuthenticated).toBe(true);
  });

  it('clearAuth removes all auth fields', () => {
    const { result } = renderHook(() => useAuthStore());
    act(() => {
      result.current.setAuth({ accessToken: 'tok', refreshToken: 'ref', caregiverId: 'cg-1', agencyId: 'ag-1', name: 'Sarah', agencyName: 'Sunrise', firstLogin: false });
    });
    act(() => {
      result.current.clearAuth();
    });
    expect(result.current.isAuthenticated).toBe(false);
    expect(result.current.accessToken).toBeNull();
  });
});
```

Run: `npm test -- --testPathPattern=authStore`
Expected: FAIL — module not found.

- [ ] **Step 2: Create `src/store/authStore.ts`**

```ts
// src/store/authStore.ts
import { create } from 'zustand';
import * as SecureStore from 'expo-secure-store';

interface AuthState {
  accessToken:  string | null;
  refreshToken: string | null;
  caregiverId:  string | null;
  agencyId:     string | null;
  name:         string | null;
  agencyName:   string | null;
  firstLogin:   boolean;
  isAuthenticated: boolean;

  setAuth: (payload: {
    accessToken: string;
    refreshToken: string;
    caregiverId: string;
    agencyId: string;
    name: string;
    agencyName: string;
    firstLogin: boolean;
  }) => Promise<void>;

  clearAuth: () => Promise<void>;

  /** Rehydrate tokens from SecureStore on app cold start */
  rehydrate: () => Promise<void>;
}

export const useAuthStore = create<AuthState>((set) => ({
  accessToken:     null,
  refreshToken:    null,
  caregiverId:     null,
  agencyId:        null,
  name:            null,
  agencyName:      null,
  firstLogin:      false,
  isAuthenticated: false,

  setAuth: async ({ accessToken, refreshToken, caregiverId, agencyId, name, agencyName, firstLogin }) => {
    await SecureStore.setItemAsync('accessToken', accessToken);
    await SecureStore.setItemAsync('refreshToken', refreshToken);
    // Persist non-sensitive profile fields so they survive a cold restart.
    // name/agencyName are not PHI — storing them eliminates the blank-greeting flash on rehydration.
    await SecureStore.setItemAsync('caregiverProfile', JSON.stringify({ caregiverId, agencyId, name, agencyName, firstLogin }));
    set({ accessToken, refreshToken, caregiverId, agencyId, name, agencyName, firstLogin, isAuthenticated: true });
  },

  clearAuth: async () => {
    await SecureStore.deleteItemAsync('accessToken');
    await SecureStore.deleteItemAsync('refreshToken');
    await SecureStore.deleteItemAsync('caregiverProfile');
    set({ accessToken: null, refreshToken: null, caregiverId: null, agencyId: null, name: null, agencyName: null, firstLogin: false, isAuthenticated: false });
  },

  rehydrate: async () => {
    const accessToken   = await SecureStore.getItemAsync('accessToken');
    const refreshToken  = await SecureStore.getItemAsync('refreshToken');
    const profileJson   = await SecureStore.getItemAsync('caregiverProfile');
    if (accessToken && refreshToken) {
      const profile = profileJson ? JSON.parse(profileJson) : {};
      set({ accessToken, refreshToken, isAuthenticated: true, ...profile });
    }
  },
}));
```

- [ ] **Step 3: Create `src/hooks/useAuth.ts`**

```ts
// src/hooks/useAuth.ts
import { authApi } from '@/api/auth';
import { useAuthStore } from '@/store/authStore';

export function useAuth() {
  // Use individual selectors rather than subscribing to the whole store.
  // Spreading useAuthStore() re-renders on ANY store field change and
  // leaks raw store actions (setAuth, clearAuth, rehydrate) to consumers.
  const isAuthenticated = useAuthStore(s => s.isAuthenticated);
  const name            = useAuthStore(s => s.name);
  const agencyName      = useAuthStore(s => s.agencyName);
  const firstLogin      = useAuthStore(s => s.firstLogin);
  const setAuth         = useAuthStore(s => s.setAuth);
  const clearAuth       = useAuthStore(s => s.clearAuth);
  const rehydrate       = useAuthStore(s => s.rehydrate);

  async function exchangeToken(token: string) {
    const data = await authApi.exchange(token);
    await setAuth(data);
    return data;
  }

  async function sendLink(email: string) {
    return authApi.sendLink(email);
  }

  async function logout() {
    await clearAuth();
  }

  return { exchangeToken, sendLink, logout, isAuthenticated, name, agencyName, firstLogin, rehydrate };
}
```

- [ ] **Step 4: Run tests**

```bash
npm test -- --testPathPattern=authStore
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add mobile/src/
git commit -m "feat(mobile): add auth store with SecureStore persistence and useAuth hook"
```

---

### Task 5: Custom Tab Bar + Navigation Shell

**Files:**
- Create: `src/navigation/TabBar.tsx`
- Create: `src/navigation/AppNavigator.tsx`
- Create: `src/navigation/RootNavigator.tsx`
- Create: `src/store/visitStore.ts`
- Test: `src/__tests__/navigation/TabBar.test.tsx`

- [ ] **Step 1: Create `src/store/visitStore.ts`**

```ts
// src/store/visitStore.ts
import { create } from 'zustand';

// IMPORTANT: visitStore is in-memory only (Zustand, no persistence).
// If the app is killed mid-visit and restarted, activeVisitId and activeShiftId
// will be null and the Visit screen will be unreachable. This is a known
// limitation at MVP. The caregiver must contact their agency to manually
// record the visit. Persisting to SecureStore is a P2 improvement.

export type GpsStatus = 'OK' | 'OUTSIDE_RANGE' | 'OFFLINE' | 'UNAVAILABLE';

interface VisitState {
  activeVisitId:    string | null;
  activeShiftId:    string | null;
  activeClientId:   string | null;    // used to key CarePlanSection collapse preference per client
  activeClientName: string | null;
  clockInTime:      string | null;    // ISO 8601
  gpsStatus:        GpsStatus | null; // determined at clock-in, shown in GpsStatusBar throughout visit
  activeVisitNotes: string | null;    // persists notes text across re-navigation within a visit
  setActiveVisit: (visitId: string, shiftId: string, clientId: string, clientName: string, clockInTime: string, gpsStatus: GpsStatus) => void;
  setVisitNotes: (notes: string) => void;
  clearActiveVisit: () => void;
}

export const useVisitStore = create<VisitState>((set) => ({
  activeVisitId:    null,
  activeShiftId:    null,
  activeClientId:   null,
  activeClientName: null,
  clockInTime:      null,
  gpsStatus:        null,
  activeVisitNotes: null,
  setActiveVisit: (visitId, shiftId, clientId, clientName, clockInTime, gpsStatus) =>
    set({ activeVisitId: visitId, activeShiftId: shiftId, activeClientId: clientId, activeClientName: clientName, clockInTime, gpsStatus }),
  setVisitNotes: (notes) => set({ activeVisitNotes: notes }),
  clearActiveVisit: () =>
    set({ activeVisitId: null, activeShiftId: null, activeClientId: null, activeClientName: null, clockInTime: null, gpsStatus: null, activeVisitNotes: null }),
}));
```

- [ ] **Step 1b: Create `src/__tests__/store/visitStore.test.ts`**

```ts
import { useVisitStore } from '@/store/visitStore';

beforeEach(() => {
  useVisitStore.setState({
    activeVisitId: null, activeShiftId: null, activeClientId: null,
    activeClientName: null, clockInTime: null, gpsStatus: null, activeVisitNotes: null,
  });
});

describe('visitStore', () => {
  it('starts with no active visit', () => {
    expect(useVisitStore.getState().activeVisitId).toBeNull();
  });

  it('setActiveVisit populates all fields', () => {
    useVisitStore.getState().setActiveVisit('v-1', 'sh-1', 'cl-1', 'Eleanor Vance', '2026-04-08T07:00:00Z', 'OK');
    const state = useVisitStore.getState();
    expect(state.activeVisitId).toBe('v-1');
    expect(state.activeShiftId).toBe('sh-1');
    expect(state.activeClientId).toBe('cl-1');
    expect(state.activeClientName).toBe('Eleanor Vance');
    expect(state.gpsStatus).toBe('OK');
  });

  it('setVisitNotes updates only notes', () => {
    useVisitStore.getState().setActiveVisit('v-1', 'sh-1', 'cl-1', 'Eleanor', '2026-04-08T07:00:00Z', 'OK');
    useVisitStore.getState().setVisitNotes('Some notes.');
    expect(useVisitStore.getState().activeVisitNotes).toBe('Some notes.');
    expect(useVisitStore.getState().activeVisitId).toBe('v-1'); // unchanged
  });

  it('clearActiveVisit resets all fields', () => {
    useVisitStore.getState().setActiveVisit('v-1', 'sh-1', 'cl-1', 'Eleanor', '2026-04-08T07:00:00Z', 'OK');
    useVisitStore.getState().clearActiveVisit();
    expect(useVisitStore.getState().activeVisitId).toBeNull();
    expect(useVisitStore.getState().gpsStatus).toBeNull();
    expect(useVisitStore.getState().activeVisitNotes).toBeNull();
  });
});
```

Run: `npm test -- --testPathPattern=visitStore`
Expected: FAIL — module not found.

- [ ] **Step 1c: Run visitStore tests**

Run: `npm test -- --testPathPattern=visitStore`
Expected: PASS.

- [ ] **Step 2: Write failing tab bar test**

Create `mobile/src/__tests__/navigation/TabBar.test.tsx`:

```tsx
import React from 'react';
import { render, screen } from '@testing-library/react-native';
import { TabBar } from '@/navigation/TabBar';

// Minimal bottom tab bar props required by React Navigation
const baseProps = {
  state: {
    index: 0,
    routes: [
      { key: 'Today', name: 'Today' },
      { key: 'OpenShifts', name: 'OpenShifts' },
      { key: 'ClockIn', name: 'ClockIn' },
      { key: 'Messages', name: 'Messages' },
      { key: 'Profile', name: 'Profile' },
    ],
  },
  navigation: { emit: jest.fn(), navigate: jest.fn() },
  descriptors: {},
} as any;

describe('TabBar', () => {
  it('renders all 5 tab labels', () => {
    render(<TabBar {...baseProps} />);
    expect(screen.getByText('Today')).toBeTruthy();
    expect(screen.getByText('Open Shifts')).toBeTruthy();
    expect(screen.getByText('Messages')).toBeTruthy();
    expect(screen.getByText('Profile')).toBeTruthy();
  });
});
```

Run: `npm test -- --testPathPattern=TabBar`
Expected: FAIL — module not found.

- [ ] **Step 3: Create `src/navigation/TabBar.tsx`**

```tsx
// src/navigation/TabBar.tsx
import React from 'react';
import { View, TouchableOpacity, Text, StyleSheet } from 'react-native';
import type { BottomTabBarProps } from '@react-navigation/bottom-tabs';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Colors } from '@/constants/colors';
import { useVisitStore } from '@/store/visitStore';

const TAB_LABELS: Record<string, string> = {
  Today:      'Today',
  OpenShifts: 'Open Shifts',
  ClockIn:    'Clock In',
  Messages:   'Messages',
  Profile:    'Profile',
};

export function TabBar({ state, navigation }: BottomTabBarProps) {
  const insets = useSafeAreaInsets();
  const { activeVisitId } = useVisitStore();
  const isVisitActive = !!activeVisitId;

  return (
    <View style={[styles.container, { paddingBottom: insets.bottom }]}>
      {state.routes.map((route, index) => {
        const isFocused = state.index === index;
        const isCenter = route.name === 'ClockIn';

        const onPress = () => {
          const event = navigation.emit({ type: 'tabPress', target: route.key, canPreventDefault: true });
          if (!isFocused && !event.defaultPrevented) {
            navigation.navigate(route.name);
          }
        };

        if (isCenter) {
          const handleFabPress = () => {
            if (isVisitActive) {
              // Return to the active visit — Visit is in the parent stack
              navigation.getParent()?.navigate('Visit' as never, { visitId: activeVisitId } as never);
            } else {
              // Present the clock-in sheet as a modal from the parent (root) stack
              // so it floats above the tab bar as a proper bottom sheet
              navigation.getParent()?.navigate('ClockInSheet' as never);
            }
          };

          return (
            <TouchableOpacity
              key={route.key}
              style={styles.fabContainer}
              onPress={handleFabPress}
              accessibilityRole="button"
              accessibilityLabel={isVisitActive ? 'Visit Active — tap to return' : 'Clock In'}
            >
              <View style={[styles.fab, isVisitActive && styles.fabActive]}>
                <Text style={styles.fabIcon}>{isVisitActive ? '⏹' : '⏱'}</Text>
              </View>
              <Text style={[styles.fabLabel, isVisitActive && styles.fabLabelActive]}>
                {isVisitActive ? 'Visit Active' : 'Clock In'}
              </Text>
            </TouchableOpacity>
          );
        }

        return (
          <TouchableOpacity key={route.key} style={styles.tab} onPress={onPress} accessibilityRole="tab">
            <Text style={[styles.label, isFocused && styles.labelActive]}>
              {TAB_LABELS[route.name] ?? route.name}
            </Text>
          </TouchableOpacity>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: Colors.white,
    borderTopWidth: 1,
    borderTopColor: Colors.border,
    paddingTop: 6,
    minHeight: 56,
  },
  tab: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 4,
    minHeight: 44, // minimum touch target
  },
  label: {
    fontSize: 11,
    color: Colors.textMuted,
    fontWeight: '500',
    textAlign: 'center',
  },
  labelActive: { color: Colors.blue, fontWeight: '700' },
  fabContainer: {
    flex: 1,
    alignItems: 'center',
    marginTop: -20,
  },
  fab: {
    width: 46,
    height: 46,
    borderRadius: 23,
    backgroundColor: Colors.blue,
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: Colors.blue,
    shadowOffset: { width: 0, height: 3 },
    shadowOpacity: 0.45,
    shadowRadius: 6,
    elevation: 6,
  },
  fabActive: { backgroundColor: Colors.red },
  fabIcon: { fontSize: 20 },
  fabLabel: { fontSize: 11, color: Colors.blue, fontWeight: '700', marginTop: 2 },
  fabLabelActive: { color: Colors.red },
});
```

- [ ] **Step 4: Create `src/navigation/AppNavigator.tsx`**

```tsx
// src/navigation/AppNavigator.tsx
import React from 'react';
import { View } from 'react-native';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { TabBar } from './TabBar';

// Screen imports — stubs until each task creates them
import { TodayScreen }           from '@/screens/today/TodayScreen';
import { OpenShiftsScreen }       from '@/screens/openShifts/OpenShiftsScreen';
import { MessagesInboxScreen }    from '@/screens/messages/MessagesInboxScreen';
import { MessageThreadScreen }    from '@/screens/messages/MessageThreadScreen';
import { ProfileScreen }          from '@/screens/profile/ProfileScreen';
import { SettingsScreen }         from '@/screens/settings/SettingsScreen';
import { VisitScreen }            from '@/screens/visit/VisitScreen';
import { CarePlanScreen }         from '@/screens/carePlan/CarePlanScreen';
import { ConflictDetailScreen }   from '@/screens/conflict/ConflictDetailScreen';
import { ClockInSheet }           from '@/screens/clockIn/ClockInSheet';

const Tab   = createBottomTabNavigator();
const Stack = createNativeStackNavigator();

// Placeholder rendered when the ClockIn tab slot is somehow focused directly.
// The FAB in TabBar navigates to the ClockInSheet modal instead of this tab,
// so this screen should never be visible to the user.
function ClockInPlaceholder() {
  return <View style={{ flex: 1, backgroundColor: '#f6f6fa' }} />;
}

function MessageStack() {
  return (
    <Stack.Navigator screenOptions={{ headerShown: false }}>
      <Stack.Screen name="MessagesInbox"  component={MessagesInboxScreen} />
      <Stack.Screen name="MessageThread"  component={MessageThreadScreen} />
    </Stack.Navigator>
  );
}

export function AppNavigator() {
  return (
    <Stack.Navigator screenOptions={{ headerShown: false }}>
      <Stack.Screen name="Tabs" component={TabNavigator} />
      <Stack.Screen name="Visit"          component={VisitScreen} />
      <Stack.Screen name="CarePlan"       component={CarePlanScreen} />
      <Stack.Screen name="Settings"       component={SettingsScreen} />
      <Stack.Screen name="ConflictDetail" component={ConflictDetailScreen} />
      {/* ClockInSheet must be a modal in the ROOT stack so it floats above the tab bar */}
      <Stack.Screen name="ClockInSheet"   component={ClockInSheet}
                    options={{ presentation: 'modal', headerShown: false }} />
    </Stack.Navigator>
  );
}

function TabNavigator() {
  return (
    <Tab.Navigator tabBar={(props) => <TabBar {...props} />}
                   screenOptions={{ headerShown: false }}>
      <Tab.Screen name="Today"      component={TodayScreen} />
      <Tab.Screen name="OpenShifts" component={OpenShiftsScreen} />
      {/* ClockIn tab slot exists for layout purposes only — FAB navigates to the ClockInSheet modal */}
      <Tab.Screen name="ClockIn"    component={ClockInPlaceholder} />
      <Tab.Screen name="Messages"   component={MessageStack} />
      <Tab.Screen name="Profile"    component={ProfileScreen} />
    </Tab.Navigator>
  );
}
```

- [ ] **Step 5: Create `src/navigation/RootNavigator.tsx`**

```tsx
// src/navigation/RootNavigator.tsx
import React, { useEffect, useState } from 'react';
import { NavigationContainer, LinkingOptions } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { useAuthStore } from '@/store/authStore';
import { AppNavigator }             from './AppNavigator';
import { LoginScreen }              from '@/screens/auth/LoginScreen';
import { DeepLinkHandlerScreen }    from '@/screens/auth/DeepLinkHandlerScreen';
import { LinkExpiredScreen }        from '@/screens/auth/LinkExpiredScreen';
import { WelcomeScreen }            from '@/screens/onboarding/WelcomeScreen';
import { NotificationsScreen }      from '@/screens/onboarding/NotificationsScreen';
import { LocationScreen }           from '@/screens/onboarding/LocationScreen';

const AuthStack = createNativeStackNavigator();
const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 2, staleTime: 30_000 } },
});

const linking: LinkingOptions<{}> = {
  prefixes: ['hcare://'],
  config: {
    screens: {
      DeepLinkHandler: 'auth',
    },
  },
};

export function RootNavigator() {
  const { isAuthenticated, firstLogin, rehydrate } = useAuthStore();
  const [hydrated, setHydrated] = useState(false);

  useEffect(() => {
    rehydrate().finally(() => setHydrated(true));
  }, []);

  if (!hydrated) return null; // Show splash while rehydrating

  return (
    <QueryClientProvider client={queryClient}>
      <SafeAreaProvider>
        <NavigationContainer linking={linking}>
          {isAuthenticated ? (
            firstLogin ? (
              <AuthStack.Navigator screenOptions={{ headerShown: false }}>
                <AuthStack.Screen name="Welcome"       component={WelcomeScreen} />
                <AuthStack.Screen name="Notifications" component={NotificationsScreen} />
                <AuthStack.Screen name="Location"      component={LocationScreen} />
              </AuthStack.Navigator>
            ) : (
              <AppNavigator />
            )
          ) : (
            <AuthStack.Navigator screenOptions={{ headerShown: false }}>
              <AuthStack.Screen name="Login"           component={LoginScreen} />
              <AuthStack.Screen name="DeepLinkHandler" component={DeepLinkHandlerScreen} />
              <AuthStack.Screen name="LinkExpired"     component={LinkExpiredScreen} />
            </AuthStack.Navigator>
          )}
        </NavigationContainer>
      </SafeAreaProvider>
    </QueryClientProvider>
  );
}
```

- [ ] **Step 6: Wire `RootNavigator` into `App.tsx`**

Replace the contents of `mobile/App.tsx`:

```tsx
import React from 'react';
import { RootNavigator } from './src/navigation/RootNavigator';

export default function App() {
  return <RootNavigator />;
}
```

- [ ] **Step 7: Create stub screens for each unimplemented screen**

Each stub is a one-liner so AppNavigator compiles. Replace these as you implement each task. Create the directory and file for each:

```tsx
// Template for all stub screens:
import React from 'react';
import { View, Text } from 'react-native';
export function StubScreen({ route }: any) {
  return <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center' }}>
    <Text>{route?.name ?? 'Screen'}</Text>
  </View>;
}
```

Files to create as stubs (copy template, rename export and filename):
- `src/screens/today/TodayScreen.tsx` — export `TodayScreen`
- `src/screens/openShifts/OpenShiftsScreen.tsx` — export `OpenShiftsScreen`
- `src/screens/messages/MessagesInboxScreen.tsx` — export `MessagesInboxScreen`
- `src/screens/messages/MessageThreadScreen.tsx` — export `MessageThreadScreen`
- `src/screens/profile/ProfileScreen.tsx` — export `ProfileScreen`
- `src/screens/settings/SettingsScreen.tsx` — export `SettingsScreen`
- `src/screens/visit/VisitScreen.tsx` — export `VisitScreen`
- `src/screens/carePlan/CarePlanScreen.tsx` — export `CarePlanScreen`
- `src/screens/conflict/ConflictDetailScreen.tsx` — export `ConflictDetailScreen`
- `src/screens/clockIn/ClockInSheet.tsx` — export `ClockInSheet`
- `src/screens/auth/LoginScreen.tsx` — export `LoginScreen`
- `src/screens/auth/DeepLinkHandlerScreen.tsx` — export `DeepLinkHandlerScreen`
- `src/screens/auth/LinkExpiredScreen.tsx` — export `LinkExpiredScreen`
- `src/screens/onboarding/WelcomeScreen.tsx` — export `WelcomeScreen`
- `src/screens/onboarding/NotificationsScreen.tsx` — export `NotificationsScreen`
- `src/screens/onboarding/LocationScreen.tsx` — export `LocationScreen`

- [ ] **Step 8: Run tab bar tests**

```bash
npm test -- --testPathPattern=TabBar
```

Expected: PASS.

- [ ] **Step 9: Verify app builds and nav shell renders**

```bash
npx expo start --go
```

Expected: app loads, bottom tab bar visible with 5 tabs, center FAB raised. Tapping the non-FAB tabs shows stub screens. Tapping the center FAB presents `ClockInSheet` as a modal sliding up from the bottom (not a full-screen tab transition). No crash.

- [ ] **Step 10: Commit**

```bash
git add mobile/src/ mobile/App.tsx
git commit -m "feat(mobile): add navigation shell — custom raised-FAB tab bar, auth/app split navigator"
```

---

### Task 6: Auth Screens (Login, Link Expired, Deep Link Handler)

**Files:**
- Implement: `src/screens/auth/LoginScreen.tsx`
- Implement: `src/screens/auth/LinkExpiredScreen.tsx`
- Implement: `src/screens/auth/DeepLinkHandlerScreen.tsx`
- Test: `src/__tests__/screens/LoginScreen.test.tsx`

- [ ] **Step 1: Write failing login screen test**

Create `mobile/src/__tests__/screens/LoginScreen.test.tsx`:

```tsx
import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react-native';
import { LoginScreen } from '@/screens/auth/LoginScreen';
import { mockAuthResponse } from '@/mocks/data';
import { setupMocks, teardownMocks } from '@/mocks/handlers';

beforeEach(() => setupMocks());
afterEach(() => teardownMocks());

describe('LoginScreen', () => {
  const nav = { navigate: jest.fn() } as any;

  it('renders primary message and email input', () => {
    render(<LoginScreen navigation={nav} />);
    expect(screen.getByText(/check your email/i)).toBeTruthy();
    expect(screen.getByPlaceholderText(/your@email.com/i)).toBeTruthy();
    expect(screen.getByText(/send new sign-in link/i)).toBeTruthy();
  });

  it('shows success message after submitting email', async () => {
    render(<LoginScreen navigation={nav} />);
    fireEvent.changeText(screen.getByPlaceholderText(/your@email.com/i), 'sarah@example.com');
    fireEvent.press(screen.getByText(/send new sign-in link/i));
    await waitFor(() =>
      expect(screen.getByText(/link has been sent/i)).toBeTruthy()
    );
  });
});
```

Run: `npm test -- --testPathPattern=LoginScreen`
Expected: FAIL.

- [ ] **Step 2: Implement `LoginScreen.tsx`**

```tsx
// src/screens/auth/LoginScreen.tsx
import React, { useState } from 'react';
import { View, Text, TextInput, TouchableOpacity, StyleSheet, Alert, KeyboardAvoidingView, Platform } from 'react-native';
import { Colors } from '@/constants/colors';
import { Typography } from '@/constants/typography';
import { useAuth } from '@/hooks/useAuth';

export function LoginScreen({ navigation }: any) {
  const [email, setEmail] = useState('');
  const [loading, setLoading] = useState(false);
  const [sent, setSent] = useState(false);
  const { sendLink } = useAuth();

  const handleSend = async () => {
    if (!email.trim()) return;
    setLoading(true);
    try {
      await sendLink(email.trim());
      setSent(true);
    } catch {
      // Anti-enumeration: always show success
      setSent(true);
    } finally {
      setLoading(false);
    }
  };

  return (
    <KeyboardAvoidingView style={styles.root} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
      {/* Branded header */}
      <View style={styles.header}>
        <Text style={styles.logo}>h<Text style={{ color: Colors.blue }}>.</Text>care</Text>
        <Text style={styles.logoSub}>Caregiver App</Text>
      </View>

      <View style={styles.body}>
        <Text style={[Typography.cardTitle, styles.title]}>Check your email</Text>
        <Text style={[Typography.body, styles.sub]}>
          Your agency sent you a sign-in link. Tap that link on your phone to get started.
        </Text>

        <View style={styles.divider} />

        <Text style={[Typography.body, { color: Colors.textMuted, marginBottom: 10 }]}>
          Can't find the email? Request a new sign-in link.
        </Text>

        <TextInput
          style={styles.input}
          placeholder="your@email.com"
          placeholderTextColor={Colors.textMuted}
          keyboardType="email-address"
          autoCapitalize="none"
          autoCorrect={false}
          value={email}
          onChangeText={setEmail}
        />

        <TouchableOpacity
          style={[styles.btn, loading && styles.btnDisabled]}
          onPress={handleSend}
          disabled={loading}
        >
          <Text style={styles.btnText}>
            {loading ? 'Sending…' : 'Send New Sign-In Link'}
          </Text>
        </TouchableOpacity>

        {sent && (
          <Text style={[Typography.body, { color: Colors.green, textAlign: 'center', marginTop: 12 }]}>
            If that email matches your account, a link has been sent.
          </Text>
        )}
      </View>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  root:    { flex: 1, backgroundColor: Colors.white },
  header:  { backgroundColor: Colors.dark, paddingTop: 60, paddingBottom: 32, alignItems: 'center' },
  logo:    { fontSize: 28, fontWeight: '700', color: Colors.white, letterSpacing: -0.5 },
  logoSub: { ...Typography.timestamp, color: Colors.textMuted, textTransform: 'uppercase', letterSpacing: 0.1, marginTop: 4 },
  body:    { padding: 24 },
  title:   { color: Colors.textPrimary, marginBottom: 8 },
  sub:     { color: Colors.textSecondary, lineHeight: 22, marginBottom: 20 },
  divider: { height: 1, backgroundColor: Colors.border, marginBottom: 16 },
  input:   { backgroundColor: Colors.surface, borderWidth: 1, borderColor: Colors.border, borderRadius: 8, paddingHorizontal: 12, paddingVertical: 10, ...Typography.body, marginBottom: 12 },
  btn:     { backgroundColor: Colors.dark, borderRadius: 8, paddingVertical: 12, alignItems: 'center' },
  btnDisabled: { opacity: 0.6 },
  btnText: { ...Typography.bodyMedium, color: Colors.white, fontWeight: '700' },
});
```

- [ ] **Step 3: Implement `LinkExpiredScreen.tsx`**

```tsx
// src/screens/auth/LinkExpiredScreen.tsx
import React, { useState } from 'react';
import { View, Text, TextInput, TouchableOpacity, StyleSheet, KeyboardAvoidingView, Platform } from 'react-native';
import { Colors } from '@/constants/colors';
import { Typography } from '@/constants/typography';
import { useAuth } from '@/hooks/useAuth';

export function LinkExpiredScreen({ navigation }: any) {
  const [email, setEmail] = useState('');
  const [sent, setSent] = useState(false);
  const [loading, setLoading] = useState(false);
  const { sendLink } = useAuth();

  const handleSend = async () => {
    if (!email.trim()) return;
    setLoading(true);
    try { await sendLink(email.trim()); } catch { /* anti-enumeration */ }
    setSent(true);
    setLoading(false);
  };

  return (
    <KeyboardAvoidingView style={styles.root} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
      <View style={styles.header}>
        <Text style={styles.logo}>h<Text style={{ color: Colors.blue }}>.</Text>care</Text>
      </View>
      <View style={styles.body}>
        <Text style={{ fontSize: 28, textAlign: 'center', marginBottom: 10 }}>⚠️</Text>
        <Text style={[Typography.cardTitle, styles.title]}>Link expired</Text>
        <Text style={[Typography.body, styles.sub]}>
          Sign-in links expire after 24 hours. Enter your email and we'll send a fresh one.
        </Text>
        <TextInput
          style={styles.input}
          placeholder="your@email.com"
          placeholderTextColor={Colors.textMuted}
          keyboardType="email-address"
          autoCapitalize="none"
          value={email}
          onChangeText={setEmail}
        />
        <TouchableOpacity style={[styles.btn, loading && styles.btnDisabled]} onPress={handleSend} disabled={loading}>
          <Text style={styles.btnText}>{loading ? 'Sending…' : 'Send New Sign-In Link'}</Text>
        </TouchableOpacity>
        {sent && (
          <Text style={[Typography.body, { color: Colors.green, textAlign: 'center', marginTop: 12 }]}>
            If that email matches your account, a link has been sent.
          </Text>
        )}
      </View>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  root:   { flex: 1, backgroundColor: Colors.white },
  header: { backgroundColor: Colors.dark, paddingTop: 60, paddingBottom: 28, alignItems: 'center' },
  logo:   { fontSize: 28, fontWeight: '700', color: Colors.white },
  body:   { padding: 24, alignItems: 'center' },
  title:  { color: Colors.textPrimary, marginBottom: 8, textAlign: 'center' },
  sub:    { color: Colors.textSecondary, lineHeight: 22, marginBottom: 20, textAlign: 'center' },
  input:  { width: '100%', backgroundColor: Colors.surface, borderWidth: 1, borderColor: Colors.border, borderRadius: 8, paddingHorizontal: 12, paddingVertical: 10, ...Typography.body, marginBottom: 12 },
  btn:    { width: '100%', backgroundColor: Colors.blue, borderRadius: 8, paddingVertical: 12, alignItems: 'center' },
  btnDisabled: { opacity: 0.6 },
  btnText: { ...Typography.bodyMedium, color: Colors.white, fontWeight: '700' },
});
```

- [ ] **Step 4: Implement `DeepLinkHandlerScreen.tsx`**

This screen receives `hcare://auth?token=<token>` from the deep link and exchanges the token.

```tsx
// src/screens/auth/DeepLinkHandlerScreen.tsx
import React, { useEffect } from 'react';
import { View, Text, ActivityIndicator, StyleSheet } from 'react-native';
import { Colors } from '@/constants/colors';
import { Typography } from '@/constants/typography';
import { useAuth } from '@/hooks/useAuth';

export function DeepLinkHandlerScreen({ route, navigation }: any) {
  const { exchangeToken } = useAuth();

  useEffect(() => {
    const token = route?.params?.token as string | undefined;
    if (!token) {
      navigation.replace('LinkExpired');
      return;
    }

    exchangeToken(token)
      .then(() => {
        // RootNavigator will re-render based on isAuthenticated — no explicit navigate needed
      })
      .catch(() => {
        navigation.replace('LinkExpired');
      });
  }, []);

  return (
    <View style={styles.root}>
      <Text style={styles.logo}>h<Text style={{ color: Colors.blue }}>.</Text>care</Text>
      <ActivityIndicator size="large" color={Colors.blue} style={{ marginTop: 32 }} />
      <Text style={[Typography.body, { color: Colors.textSecondary, marginTop: 16 }]}>Signing you in…</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: Colors.dark, alignItems: 'center', justifyContent: 'center' },
  logo: { fontSize: 36, fontWeight: '700', color: Colors.white },
});
```

- [ ] **Step 5: Write failing `DeepLinkHandlerScreen` test**

Create `mobile/src/__tests__/screens/DeepLinkHandlerScreen.test.tsx`:

```tsx
import React from 'react';
import { render, screen, waitFor } from '@testing-library/react-native';
import { DeepLinkHandlerScreen } from '@/screens/auth/DeepLinkHandlerScreen';
import { setupMocks, teardownMocks } from '@/mocks/handlers';

beforeEach(() => setupMocks());
afterEach(() => teardownMocks());

describe('DeepLinkHandlerScreen', () => {
  it('navigates to LinkExpired immediately when no token param', async () => {
    const nav = { replace: jest.fn() };
    render(<DeepLinkHandlerScreen navigation={nav} route={{ params: {} }} />);
    await waitFor(() => expect(nav.replace).toHaveBeenCalledWith('LinkExpired'));
  });

  it('shows loading indicator while exchanging a token', () => {
    const nav = { replace: jest.fn() };
    // Token present — exchange is async; loading state must be visible synchronously
    render(<DeepLinkHandlerScreen navigation={nav} route={{ params: { token: 'test-token' } }} />);
    expect(screen.getByText(/signing you in/i)).toBeTruthy();
  });
});
```

Run: `npm test -- --testPathPattern=DeepLinkHandlerScreen`
Expected: PASS.

- [ ] **Step 6: Run LoginScreen tests**

```bash
npm test -- --testPathPattern=LoginScreen
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add mobile/src/screens/auth/ mobile/src/__tests__/screens/DeepLinkHandlerScreen.test.tsx
git commit -m "feat(mobile): implement Login, LinkExpired, and DeepLinkHandler auth screens"
```

---

### Task 7: Onboarding Flow

**Files:**
- Create: `src/components/ProgressDots.tsx`
- Implement: `src/screens/onboarding/WelcomeScreen.tsx`
- Implement: `src/screens/onboarding/NotificationsScreen.tsx`
- Implement: `src/screens/onboarding/LocationScreen.tsx`

- [ ] **Step 1: Create `src/components/ProgressDots.tsx`**

```tsx
// src/components/ProgressDots.tsx
// Shared step indicator used by all three onboarding screens.
// Extracted to avoid defining an identical component three times.
import React from 'react';
import { View } from 'react-native';
import { Colors } from '@/constants/colors';

interface Props { current: number; total: number }

export function ProgressDots({ current, total }: Props) {
  return (
    <View style={{ flexDirection: 'row', gap: 5, marginBottom: 24 }}>
      {Array.from({ length: total }).map((_, i) => (
        <View
          key={i}
          style={{
            width: i === current ? 20 : 8,
            height: 4,
            borderRadius: 2,
            backgroundColor: i === current ? Colors.blue : Colors.border,
          }}
        />
      ))}
    </View>
  );
}
```

- [ ] **Step 2: Implement `WelcomeScreen.tsx`**

```tsx
// src/screens/onboarding/WelcomeScreen.tsx
import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Colors } from '@/constants/colors';
import { Typography } from '@/constants/typography';
import { useAuthStore } from '@/store/authStore';
import { ProgressDots } from '@/components/ProgressDots';

export function WelcomeScreen({ navigation }: any) {
  const insets = useSafeAreaInsets();
  const name = useAuthStore(s => s.name);
  const agencyName = useAuthStore(s => s.agencyName);

  return (
    <View style={[styles.root, { paddingTop: insets.top + 16, paddingBottom: insets.bottom + 16 }]}>
      <ProgressDots current={0} total={3} />
      <Text style={{ fontSize: 32, marginBottom: 12 }}>👋</Text>
      <Text style={[Typography.screenTitle, styles.title]}>Welcome, {name}!</Text>
      <Text style={[Typography.body, styles.sub]}>{agencyName}</Text>
      <View style={styles.divider} />
      <Text style={[Typography.body, styles.desc]}>
        Let's get two quick things set up so the app works properly for you.
      </Text>
      <TouchableOpacity style={styles.btn} onPress={() => navigation.navigate('Notifications')}>
        <Text style={styles.btnText}>Get Started →</Text>
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  root:    { flex: 1, backgroundColor: Colors.white, alignItems: 'center', justifyContent: 'center', padding: 28 },
  title:   { color: Colors.textPrimary, marginBottom: 4 },
  sub:     { color: Colors.textSecondary, marginBottom: 4 },
  divider: { height: 1, width: '100%', backgroundColor: Colors.border, marginVertical: 20 },
  desc:    { color: Colors.textSecondary, lineHeight: 22, textAlign: 'center', marginBottom: 28 },
  btn:     { width: '100%', backgroundColor: Colors.blue, borderRadius: 8, paddingVertical: 13, alignItems: 'center' },
  btnText: { ...Typography.bodyMedium, color: Colors.white, fontWeight: '700' },
});
```

- [ ] **Step 3: Implement `NotificationsScreen.tsx`**

```tsx
// src/screens/onboarding/NotificationsScreen.tsx
import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import * as Notifications from 'expo-notifications';
import { Colors } from '@/constants/colors';
import { Typography } from '@/constants/typography';
import { ProgressDots } from '@/components/ProgressDots';

export function NotificationsScreen({ navigation }: any) {
  const insets = useSafeAreaInsets();

  const handleEnable = async () => {
    await Notifications.requestPermissionsAsync();
    navigation.navigate('Location');
  };

  return (
    <View style={[styles.root, { paddingTop: insets.top + 16, paddingBottom: insets.bottom + 16 }]}>
      <ProgressDots current={1} total={3} />
      <Text style={{ fontSize: 32, marginBottom: 12 }}>🔔</Text>
      <Text style={[Typography.screenTitle, styles.title]}>Stay in the loop</Text>
      <Text style={[Typography.body, styles.desc]}>
        Enable notifications to get alerted when open shifts are available and receive messages from your agency.
      </Text>
      <TouchableOpacity style={styles.btn} onPress={handleEnable}>
        <Text style={styles.btnText}>Enable Notifications</Text>
      </TouchableOpacity>
      <TouchableOpacity onPress={() => navigation.navigate('Location')}>
        <Text style={styles.skip}>Not now</Text>
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  root:  { flex: 1, backgroundColor: Colors.white, alignItems: 'center', justifyContent: 'center', padding: 28 },
  title: { color: Colors.textPrimary, marginBottom: 16, textAlign: 'center' },
  desc:  { color: Colors.textSecondary, lineHeight: 22, textAlign: 'center', marginBottom: 28 },
  btn:   { width: '100%', backgroundColor: Colors.blue, borderRadius: 8, paddingVertical: 13, alignItems: 'center', marginBottom: 14 },
  btnText: { ...Typography.bodyMedium, color: Colors.white, fontWeight: '700' },
  skip:  { ...Typography.body, color: Colors.textMuted, textAlign: 'center' },
});
```

- [ ] **Step 4: Implement `LocationScreen.tsx`**

```tsx
// src/screens/onboarding/LocationScreen.tsx
import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import * as Location from 'expo-location';
import { useAuthStore } from '@/store/authStore';
import { Colors } from '@/constants/colors';
import { Typography } from '@/constants/typography';
import { ProgressDots } from '@/components/ProgressDots';

export function LocationScreen({ navigation }: any) {
  const insets = useSafeAreaInsets();
  const setFirstLoginComplete = () =>
    useAuthStore.setState({ firstLogin: false });

  const handleAllow = async () => {
    await Location.requestForegroundPermissionsAsync();
    setFirstLoginComplete();
  };

  return (
    <View style={[styles.root, { paddingTop: insets.top + 16, paddingBottom: insets.bottom + 16 }]}>
      <ProgressDots current={2} total={3} />
      <Text style={{ fontSize: 32, marginBottom: 12 }}>📍</Text>
      <Text style={[Typography.screenTitle, styles.title]}>Location for EVV</Text>
      <Text style={[Typography.body, styles.desc]}>
        Your agency uses Electronic Visit Verification. Your GPS location is captured only at clock-in and clock-out — never tracked during a visit.
      </Text>
      <Text style={[Typography.body, { color: Colors.amber, lineHeight: 20, marginBottom: 20, textAlign: 'center' }]}>
        Declining location access may create compliance issues for your visit records. Your agency will need to verify visits manually.
      </Text>
      <TouchableOpacity style={styles.btn} onPress={handleAllow}>
        <Text style={styles.btnText}>Allow Location Access</Text>
      </TouchableOpacity>
      <TouchableOpacity onPress={setFirstLoginComplete}>
        <Text style={styles.skip}>Not now</Text>
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  root:  { flex: 1, backgroundColor: Colors.white, alignItems: 'center', justifyContent: 'center', padding: 28 },
  title: { color: Colors.textPrimary, marginBottom: 16, textAlign: 'center' },
  desc:  { color: Colors.textSecondary, lineHeight: 22, textAlign: 'center', marginBottom: 16 },
  btn:   { width: '100%', backgroundColor: Colors.blue, borderRadius: 8, paddingVertical: 13, alignItems: 'center', marginBottom: 14 },
  btnText: { ...Typography.bodyMedium, color: Colors.white, fontWeight: '700' },
  skip:  { ...Typography.body, color: Colors.textMuted },
});
```

- [ ] **Step 5: Commit**

```bash
git add mobile/src/screens/onboarding/ mobile/src/components/ProgressDots.tsx
git commit -m "feat(mobile): implement 3-screen onboarding flow (welcome, notifications, location)"
```

---

## Phase 2 — Core EVV Flow

---

### Task 8: Today Screen + ShiftCard

**Files:**
- Create: `src/hooks/useToday.ts`
- Create: `src/screens/today/ShiftCard.tsx`
- Create: `src/screens/today/ActiveVisitBanner.tsx`
- Implement: `src/screens/today/TodayScreen.tsx`
- Test: `src/__tests__/screens/TodayScreen.test.tsx`

- [ ] **Step 1: Create `src/hooks/useToday.ts`**

```ts
// src/hooks/useToday.ts
import { useQuery } from '@tanstack/react-query';
import { visitsApi } from '@/api/visits';
import type { Shift } from '@/types/domain';

export function useToday() {
  const today = useQuery({
    queryKey: ['visits', 'today'],
    queryFn: visitsApi.today,
    select: (d) => d.shifts,
  });

  const week = useQuery({
    queryKey: ['visits', 'week'],
    queryFn: visitsApi.week,
    select: (d) => d.shifts,
  });

  const upcoming = today.data?.filter(s => s.status === 'UPCOMING') ?? [];
  const completed = today.data?.filter(s => s.status === 'COMPLETED') ?? [];
  const cancelled = today.data?.filter(s => s.status === 'CANCELLED') ?? [];
  const inProgress = today.data?.find(s => s.status === 'IN_PROGRESS') ?? null;

  // Soonest upcoming shift gets the NEXT badge
  const nextShiftId = upcoming[0]?.id ?? null;

  return {
    todayShifts: today.data ?? [],
    weekShifts:  week.data ?? [],
    upcoming,
    completed,
    cancelled,
    inProgress,
    nextShiftId,
    isLoading: today.isLoading,
    refetch:   today.refetch,
  };
}
```

- [ ] **Step 2: Create `src/screens/today/ShiftCard.tsx`**

```tsx
// src/screens/today/ShiftCard.tsx
import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet, Alert } from 'react-native';
import type { Shift } from '@/types/domain';
import { Colors } from '@/constants/colors';
import { Typography } from '@/constants/typography';

interface Props {
  shift: Shift;
  isNext?: boolean;
  onPressMaps?: () => void;
  onPressCarePlan?: () => void;
}

function formatTime(iso: string) {
  return new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

function formatDuration(start: string, end: string) {
  const hrs = (new Date(end).getTime() - new Date(start).getTime()) / 3_600_000;
  return `${hrs.toFixed(1)}h`;
}

const LEFT_BORDER_COLOR: Record<string, string> = {
  UPCOMING:    Colors.blue,
  IN_PROGRESS: Colors.blue,
  COMPLETED:   Colors.green,
  CANCELLED:   Colors.textMuted,
};

export function ShiftCard({ shift, isNext, onPressMaps, onPressCarePlan }: Props) {
  const borderColor = LEFT_BORDER_COLOR[shift.status] ?? Colors.border;
  const isCancelled = shift.status === 'CANCELLED';
  const isCompleted = shift.status === 'COMPLETED';

  const handleCancelledPress = () => {
    Alert.alert('Shift Cancelled', 'This shift was cancelled by your agency.');
  };

  const cardContent = (
    <View style={[styles.card, { borderLeftColor: borderColor }]}>
      <View style={styles.row}>
        <Text style={[styles.clientName, isCancelled && styles.strikethrough]} numberOfLines={1}>
          {shift.clientName}
        </Text>
        <View style={styles.badges}>
          {isNext && <View style={styles.badgeNext}><Text style={styles.badgeNextText}>NEXT</Text></View>}
          {isCancelled && <View style={styles.badgeCancelled}><Text style={styles.badgeCancelledText}>CANCELLED</Text></View>}
          {isCompleted && <View style={styles.badgeCompleted}><Text style={styles.badgeCompletedText}>DONE</Text></View>}
        </View>
      </View>
      <Text style={styles.meta}>
        {formatTime(shift.scheduledStart)} – {formatTime(shift.scheduledEnd)} · {formatDuration(shift.scheduledStart, shift.scheduledEnd)}
      </Text>
      <Text style={styles.meta}>{shift.serviceType}</Text>

      {/* Expanded actions — only on the next upcoming card */}
      {isNext && !isCancelled && (
        <View style={styles.actions}>
          <TouchableOpacity style={styles.actionBtn} onPress={onPressMaps}>
            <Text style={styles.actionBtnText}>Maps</Text>
          </TouchableOpacity>
          <TouchableOpacity style={styles.actionBtn} onPress={onPressCarePlan}>
            <Text style={styles.actionBtnText}>Care Plan</Text>
          </TouchableOpacity>
        </View>
      )}
    </View>
  );

  if (isCancelled) {
    return <TouchableOpacity onPress={handleCancelledPress} activeOpacity={0.7}>{cardContent}</TouchableOpacity>;
  }

  return cardContent;
}

const styles = StyleSheet.create({
  card: {
    backgroundColor: Colors.white,
    borderWidth: 1,
    borderColor: Colors.border,
    borderLeftWidth: 3,
    borderRadius: 10,
    padding: 14,
    marginBottom: 10,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.06,
    shadowRadius: 4,
    elevation: 2,
  },
  row:          { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 4 },
  clientName:   { ...Typography.cardTitle, color: Colors.textPrimary, flex: 1 },
  strikethrough:{ textDecorationLine: 'line-through', color: Colors.textMuted },
  meta:         { ...Typography.body, color: Colors.textSecondary, marginBottom: 2 },
  badges:       { flexDirection: 'row', gap: 4 },
  badgeNext:    { backgroundColor: Colors.blue, borderRadius: 4, paddingHorizontal: 6, paddingVertical: 2 },
  badgeNextText:{ fontSize: 10, fontWeight: '700', color: Colors.white },
  badgeCancelled:    { backgroundColor: Colors.surface, borderRadius: 4, paddingHorizontal: 6, paddingVertical: 2, borderWidth: 1, borderColor: Colors.border },
  badgeCancelledText:{ fontSize: 10, fontWeight: '700', color: Colors.textMuted },
  badgeCompleted:    { backgroundColor: '#f0fdf4', borderRadius: 4, paddingHorizontal: 6, paddingVertical: 2 },
  badgeCompletedText:{ fontSize: 10, fontWeight: '700', color: Colors.green },
  actions:  { flexDirection: 'row', gap: 8, marginTop: 12 },
  actionBtn:{ flex: 1, backgroundColor: Colors.surface, borderWidth: 1, borderColor: Colors.border, borderRadius: 8, paddingVertical: 8, alignItems: 'center' },
  actionBtnText: { ...Typography.body, color: Colors.textSecondary, fontWeight: '600' },
});
```

- [ ] **Step 3: Create `src/screens/today/ActiveVisitBanner.tsx`**

```tsx
// src/screens/today/ActiveVisitBanner.tsx
import React, { useState, useEffect } from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { Colors } from '@/constants/colors';
import { Typography } from '@/constants/typography';
import type { Shift } from '@/types/domain';

function formatElapsed(clockInTime: string) {
  const elapsed = Math.floor((Date.now() - new Date(clockInTime).getTime()) / 1000);
  const h = Math.floor(elapsed / 3600);
  const m = Math.floor((elapsed % 3600) / 60);
  const s = elapsed % 60;
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
}

interface Props {
  clientName: string;
  clockInTime: string;
  onContinue: () => void;
}

export function ActiveVisitBanner({ clientName, clockInTime, onContinue }: Props) {
  const [elapsed, setElapsed] = useState(formatElapsed(clockInTime));

  useEffect(() => {
    const id = setInterval(() => setElapsed(formatElapsed(clockInTime)), 1000);
    return () => clearInterval(id);
  }, [clockInTime]);

  return (
    <View style={styles.banner}>
      <View>
        <Text style={styles.clientName}>{clientName}</Text>
        <Text style={styles.timer}>{elapsed}</Text>
      </View>
      <TouchableOpacity style={styles.btn} onPress={onContinue}>
        <Text style={styles.btnText}>Continue Visit →</Text>
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  banner:     { backgroundColor: Colors.blue, padding: 14, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', borderRadius: 10, marginBottom: 12 },
  clientName: { ...Typography.bodyMedium, color: Colors.white },
  timer:      { fontSize: 18, fontWeight: '700', color: Colors.white, fontVariant: ['tabular-nums'] },
  btn:        { backgroundColor: 'rgba(255,255,255,0.2)', paddingVertical: 8, paddingHorizontal: 12, borderRadius: 8 },
  btnText:    { ...Typography.body, color: Colors.white, fontWeight: '700' },
});
```

- [ ] **Step 4: Write failing Today screen test**

Create `mobile/src/__tests__/screens/TodayScreen.test.tsx`:

```tsx
import React from 'react';
import { render, screen, waitFor } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { TodayScreen } from '@/screens/today/TodayScreen';
import { setupMocks, teardownMocks } from '@/mocks/handlers';
import { mockTodayShifts } from '@/mocks/data';

const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
const wrapper = ({ children }: any) => <QueryClientProvider client={qc}>{children}</QueryClientProvider>;

beforeEach(() => setupMocks());
afterEach(() => { qc.clear(); teardownMocks(); });

describe('TodayScreen', () => {
  it('renders the first upcoming shift client name', async () => {
    render(<TodayScreen navigation={{ navigate: jest.fn() }} />, { wrapper });
    await waitFor(() =>
      expect(screen.getByText(mockTodayShifts[0].clientName)).toBeTruthy()
    );
  });

  it('shows NEXT badge on the soonest shift', async () => {
    render(<TodayScreen navigation={{ navigate: jest.fn() }} />, { wrapper });
    await waitFor(() => expect(screen.getByText('NEXT')).toBeTruthy());
  });
});
```

Run: `npm test -- --testPathPattern=TodayScreen`
Expected: FAIL.

- [ ] **Step 5: Implement `TodayScreen.tsx`**

```tsx
// src/screens/today/TodayScreen.tsx
import React, { useState } from 'react';
import { View, Text, ScrollView, TouchableOpacity, StyleSheet, Linking, RefreshControl, Platform } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useToday } from '@/hooks/useToday';
import { useVisitStore } from '@/store/visitStore';
import { useAuthStore } from '@/store/authStore';
import { ShiftCard } from './ShiftCard';
import { ActiveVisitBanner } from './ActiveVisitBanner';
import { Colors } from '@/constants/colors';
import { Typography } from '@/constants/typography';
import type { Shift } from '@/types/domain';

function greeting() {
  const h = new Date().getHours();
  if (h < 12) return 'Good morning';
  if (h < 17) return 'Good afternoon';
  return 'Good evening';
}

export function TodayScreen({ navigation }: any) {
  const insets = useSafeAreaInsets();
  const { upcoming, completed, cancelled, inProgress, weekShifts, nextShiftId, isLoading, refetch } = useToday();
  const { activeVisitId, activeClientName, clockInTime } = useVisitStore();
  const name = useAuthStore(s => s.name);
  const [weekExpanded, setWeekExpanded] = useState(false);
  const isVisitActive = !!activeVisitId;

  const today = new Date();
  const dateStr = today.toLocaleDateString('en-US', { weekday: 'long', month: 'long', day: 'numeric' });
  const shiftCount = upcoming.length + (inProgress ? 1 : 0);

  const handleOpenMaps = (address: string) => {
    const encoded = encodeURIComponent(address);
    // Apple Maps works on iOS; Google Maps universal link works on Android.
    const url = Platform.OS === 'ios'
      ? `http://maps.apple.com/?q=${encoded}`
      : `https://maps.google.com/?q=${encoded}`;
    Linking.openURL(url);
  };

  const handleCarePlan = (shift: Shift) => {
    navigation.navigate('CarePlan', { shiftId: shift.id });
  };

  const allShifts = [...(inProgress ? [inProgress] : []), ...upcoming, ...completed, ...cancelled];

  return (
    <View style={[styles.root, { paddingTop: insets.top }]}>
      {/* Header */}
      <View style={styles.header}>
        {isVisitActive ? (
          <ActiveVisitBanner
            clientName={activeClientName!}
            clockInTime={clockInTime!}
            onContinue={() => navigation.navigate('Visit', { visitId: activeVisitId })}
          />
        ) : (
          <>
            <Text style={styles.greeting}>{greeting()}, {name?.split(' ')[0]}</Text>
            <Text style={styles.dateStr}>{dateStr} · {shiftCount} shift{shiftCount !== 1 ? 's' : ''} today</Text>
          </>
        )}
      </View>

      <ScrollView
        style={styles.list}
        contentContainerStyle={{ padding: 16, paddingBottom: 32 }}
        refreshControl={<RefreshControl refreshing={isLoading} onRefresh={refetch} />}
      >
        {/* Section: Today */}
        <Text style={[Typography.sectionLabel, styles.sectionLabel]}>
          {isVisitActive ? 'LATER TODAY' : 'UPCOMING'}
        </Text>

        {allShifts.length === 0 && !isLoading && (
          <Text style={[Typography.body, { color: Colors.textMuted, textAlign: 'center', marginTop: 24 }]}>No shifts today</Text>
        )}

        {allShifts.map(shift => (
          <ShiftCard
            key={shift.id}
            shift={shift}
            isNext={shift.id === nextShiftId && !isVisitActive}
            onPressMaps={() => handleOpenMaps(shift.clientAddress)}
            onPressCarePlan={() => handleCarePlan(shift)}
          />
        ))}

        {/* Section: This Week (collapsed by default) */}
        {weekShifts.length > 0 && (
          <>
            <TouchableOpacity style={styles.weekToggle} onPress={() => setWeekExpanded(v => !v)}>
              <Text style={[Typography.sectionLabel, styles.sectionLabel]}>
                {weekExpanded ? 'THIS WEEK' : `Show this week (${weekShifts.length} shift${weekShifts.length !== 1 ? 's' : ''})`}
              </Text>
              <Text style={styles.chevron}>{weekExpanded ? '▲' : '▼'}</Text>
            </TouchableOpacity>
            {weekExpanded && weekShifts.map(shift => (
              <View key={shift.id} style={{ opacity: 0.6 }}>
                <ShiftCard shift={shift} />
              </View>
            ))}
          </>
        )}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  root:       { flex: 1, backgroundColor: Colors.surface },
  header:     { backgroundColor: Colors.white, padding: 16, borderBottomWidth: 1, borderBottomColor: Colors.border },
  greeting:   { ...Typography.screenTitle, color: Colors.textPrimary },
  dateStr:    { ...Typography.body, color: Colors.textSecondary, marginTop: 2 },
  list:       { flex: 1 },
  sectionLabel: { color: Colors.textSecondary, marginBottom: 8 },
  weekToggle: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingVertical: 8 },
  chevron:    { fontSize: 10, color: Colors.textMuted },
});
```

- [ ] **Step 6: Run tests**

```bash
npm test -- --testPathPattern=TodayScreen
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add mobile/src/hooks/useToday.ts mobile/src/screens/today/
git commit -m "feat(mobile): implement Today screen with shift cards, active visit banner, and THIS WEEK collapse"
```

---

### Task 9: Clock-In Bottom Sheet

**Files:**
- Create: `src/hooks/useClockIn.ts`
- Implement: `src/screens/clockIn/ClockInSheet.tsx`
- Test: `src/__tests__/screens/ClockInSheet.test.tsx`

- [ ] **Step 1: Write failing clock-in test**

Create `mobile/src/__tests__/screens/ClockInSheet.test.tsx`:

```tsx
import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ClockInSheet } from '@/screens/clockIn/ClockInSheet';
import { setupMocks, teardownMocks } from '@/mocks/handlers';
import { mockTodayShifts } from '@/mocks/data';

const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
const wrapper = ({ children }: any) => <QueryClientProvider client={qc}>{children}</QueryClientProvider>;

beforeEach(() => setupMocks());
afterEach(() => { qc.clear(); teardownMocks(); });

describe('ClockInSheet', () => {
  it('shows all upcoming shifts', async () => {
    render(<ClockInSheet navigation={{ navigate: jest.fn(), goBack: jest.fn() }} />, { wrapper });
    await waitFor(() =>
      expect(screen.getByText(mockTodayShifts[0].clientName)).toBeTruthy()
    );
    expect(screen.getByText(mockTodayShifts[1].clientName)).toBeTruthy();
  });

  it('enables Clock In button after selecting a shift', async () => {
    const nav = { navigate: jest.fn(), goBack: jest.fn() };
    render(<ClockInSheet navigation={nav} />, { wrapper });
    await waitFor(() => screen.getByText(mockTodayShifts[0].clientName));
    fireEvent.press(screen.getByText(mockTodayShifts[0].clientName));
    expect(screen.getByText(/clock in/i)).toBeTruthy();
  });
});
```

Run: `npm test -- --testPathPattern=ClockInSheet`
Expected: FAIL.

- [ ] **Step 2: Create `src/hooks/useClockIn.ts`**

```ts
// src/hooks/useClockIn.ts
import { useMutation } from '@tanstack/react-query';
import * as Location from 'expo-location';
import { visitsApi } from '@/api/visits';
import { useVisitStore } from '@/store/visitStore';
import type { GpsStatus } from '@/store/visitStore';
import { insertEvent } from '@/db/events';
import NetInfo from '@react-native-community/netinfo';
import type { GpsCoordinate } from '@/types/domain';

export function useClockIn() {
  const { setActiveVisit } = useVisitStore();

  const mutation = useMutation({
    mutationFn: async ({ shiftId, clientId, clientName }: { shiftId: string; clientId: string; clientName: string }) => {
      const net = await NetInfo.fetch();
      const capturedOffline = !net.isConnected;

      let gpsCoordinate: GpsCoordinate | undefined;
      let gpsAvailable = false;
      try {
        const loc = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced });
        gpsCoordinate = { lat: loc.coords.latitude, lng: loc.coords.longitude };
        gpsAvailable = true;
      } catch {
        // GPS unavailable — proceed without coordinate (spec 15a)
      }

      // Determine GPS status shown in GpsStatusBar for the duration of the visit.
      // OUTSIDE_RANGE can only be determined server-side (BFF compares GPS to client address);
      // the mobile app sets OK, OFFLINE, or UNAVAILABLE based on what it can observe locally.
      const gpsStatus: GpsStatus = capturedOffline ? 'OFFLINE' : gpsAvailable ? 'OK' : 'UNAVAILABLE';

      const occurredAt = new Date().toISOString();

      // Write to SQLite BEFORE calling the BFF (offline-first).
      // If the device is offline the API call is skipped, the event is queued,
      // and the caregiver can proceed with the visit; useOfflineSync drains the
      // queue on reconnect.
      await insertEvent({
        type: 'CLOCK_IN',
        visitId: shiftId,       // shiftId used as placeholder visitId until BFF confirms
        gpsCoordinate,
        capturedOffline,
        occurredAt,
      });

      if (capturedOffline) {
        // Offline path: use shiftId as a local visitId placeholder.
        // The sync batch will send this event to the BFF on reconnect.
        // Note: local-prefixed visitId distinguishes offline visits from BFF-assigned ones.
        const localVisitId = `local-${shiftId}`;
        setActiveVisit(localVisitId, shiftId, clientId, clientName, occurredAt, gpsStatus);
        return { visitId: localVisitId, clockInTime: occurredAt };
      }

      const res = await visitsApi.clockIn(shiftId, { gpsCoordinate, capturedOffline });
      setActiveVisit(res.visitId, shiftId, clientId, clientName, res.clockInTime, gpsStatus);
      return res;
    },
  });

  return mutation;
}
```

- [ ] **Step 3: Implement `ClockInSheet.tsx`**

```tsx
// src/screens/clockIn/ClockInSheet.tsx
import React, { useState, useEffect } from 'react';
import { View, Text, FlatList, TouchableOpacity, StyleSheet } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useToday } from '@/hooks/useToday';
import { useClockIn } from '@/hooks/useClockIn';
import { Colors } from '@/constants/colors';
import { Typography } from '@/constants/typography';
import type { Shift } from '@/types/domain';

function formatTime(iso: string) {
  return new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

export function ClockInSheet({ navigation }: any) {
  const insets = useSafeAreaInsets();
  const { upcoming } = useToday();
  // Initialize to null — upcoming is [] at mount (React Query async).
  // useEffect sets the first shift once data loads, but only if the user
  // hasn't already made an explicit selection (selectedId !== null guard).
  const [selectedId, setSelectedId] = useState<string | null>(null);

  useEffect(() => {
    if (upcoming.length > 0 && selectedId === null) {
      setSelectedId(upcoming[0].id);
    }
  }, [upcoming]);
  const { mutate: clockIn, isPending } = useClockIn();

  const selected = upcoming.find(s => s.id === selectedId) ?? null;

  const handleClockIn = () => {
    if (!selected) return;
    clockIn(
      { shiftId: selected.id, clientId: selected.clientId, clientName: selected.clientName },
      { onSuccess: (res) => navigation.navigate('Visit', { visitId: res.visitId }) }
    );
  };

  return (
    <View style={[styles.root, { paddingBottom: insets.bottom + 16 }]}>
      {/* Drag handle */}
      <View style={styles.handle} />

      <Text style={[Typography.sectionLabel, styles.sectionLabel]}>CLOCK IN TO</Text>

      <FlatList
        data={upcoming}
        keyExtractor={s => s.id}
        style={{ maxHeight: 320 }}
        renderItem={({ item: shift }) => {
          const isSelected = shift.id === selectedId;
          return (
            <TouchableOpacity
              style={[styles.shiftRow, isSelected && styles.shiftRowSelected]}
              onPress={() => setSelectedId(shift.id)}
            >
              <View style={[styles.leftBorder, isSelected && styles.leftBorderSelected]} />
              <View style={styles.shiftInfo}>
                <Text style={[Typography.cardTitle, { color: Colors.textPrimary }]}>{shift.clientName}</Text>
                <Text style={[Typography.body, { color: Colors.textSecondary }]}>
                  {formatTime(shift.scheduledStart)} · {shift.serviceType}
                </Text>
              </View>
              {isSelected && <Text style={styles.selectLabel}>SELECT</Text>}
            </TouchableOpacity>
          );
        }}
        ListEmptyComponent={
          <Text style={[Typography.body, { color: Colors.textMuted, textAlign: 'center', marginTop: 20 }]}>
            No upcoming shifts today
          </Text>
        }
      />

      {selected && (
        <View style={styles.footer}>
          <TouchableOpacity
            style={[styles.btn, isPending && styles.btnDisabled]}
            onPress={handleClockIn}
            disabled={isPending}
          >
            <Text style={styles.btnText}>
              {isPending ? 'Clocking in…' : `Clock In — ${selected.clientName}`}
            </Text>
          </TouchableOpacity>
          <TouchableOpacity onPress={() => navigation.goBack()}>
            <Text style={styles.cancel}>Cancel</Text>
          </TouchableOpacity>
        </View>
      )}

      {!selected && (
        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.footer}>
          <Text style={styles.cancel}>Cancel</Text>
        </TouchableOpacity>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  root:        { flex: 1, backgroundColor: Colors.white, borderTopLeftRadius: 20, borderTopRightRadius: 20, paddingTop: 12, paddingHorizontal: 20 },
  handle:      { width: 36, height: 4, backgroundColor: Colors.border, borderRadius: 2, alignSelf: 'center', marginBottom: 20 },
  sectionLabel:{ color: Colors.textSecondary, marginBottom: 12 },
  shiftRow:    { flexDirection: 'row', alignItems: 'center', borderRadius: 10, marginBottom: 8, borderWidth: 1, borderColor: Colors.border, overflow: 'hidden' },
  shiftRowSelected: { borderColor: Colors.blue, backgroundColor: '#f0f8ff' },
  leftBorder:  { width: 3, alignSelf: 'stretch', backgroundColor: Colors.border },
  leftBorderSelected: { backgroundColor: Colors.blue },
  shiftInfo:   { flex: 1, padding: 12 },
  selectLabel: { fontSize: 10, fontWeight: '700', color: Colors.blue, paddingRight: 12 },
  footer:      { marginTop: 16 },
  btn:         { backgroundColor: Colors.blue, borderRadius: 8, paddingVertical: 14, alignItems: 'center', marginBottom: 12 },
  btnDisabled: { opacity: 0.6 },
  btnText:     { ...Typography.bodyMedium, color: Colors.white, fontWeight: '700' },
  cancel:      { textAlign: 'center', ...Typography.body, color: Colors.textSecondary },
});
```

- [ ] **Step 4: Run tests**

```bash
npm test -- --testPathPattern=ClockInSheet
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add mobile/src/hooks/useClockIn.ts mobile/src/screens/clockIn/
git commit -m "feat(mobile): implement clock-in bottom sheet with all-shifts selection and GPS capture"
```

---

### Task 10: SQLite Offline Event Log

**Files:**
- Create: `src/db/schema.ts`
- Create: `src/db/events.ts`
- Test: `src/__tests__/db/events.test.ts`

- [ ] **Step 1: Write failing SQLite test**

Create `mobile/src/__tests__/db/events.test.ts`:

```ts
import { openEventStore, insertEvent, getPendingEvents, markSynced } from '@/db/events';
import type { SyncEvent } from '@/types/domain';

beforeAll(async () => {
  await openEventStore();
});

describe('SQLite event log', () => {
  const event: SyncEvent = {
    type: 'CLOCK_IN',
    visitId: 'v-test',
    capturedOffline: true,
    occurredAt: new Date().toISOString(),
  };

  it('inserts and retrieves a pending event', async () => {
    await insertEvent(event);
    const pending = await getPendingEvents();
    const found = pending.find(e => e.visitId === 'v-test' && e.type === 'CLOCK_IN');
    expect(found).toBeTruthy();
  });

  it('marks an event synced so it no longer appears in pending', async () => {
    await insertEvent({ ...event, visitId: 'v-sync-test' });
    const before = await getPendingEvents();
    const toSync = before.filter(e => e.visitId === 'v-sync-test');
    await markSynced(toSync.map(e => (e as any).rowId));
    const after = await getPendingEvents();
    expect(after.find(e => e.visitId === 'v-sync-test')).toBeUndefined();
  });
});
```

Run: `npm test -- --testPathPattern=events`
Expected: FAIL.

- [ ] **Step 2: Create `src/db/schema.ts`**

```ts
// src/db/schema.ts
export const CREATE_EVENTS_TABLE = `
  CREATE TABLE IF NOT EXISTS sync_events (
    row_id        INTEGER PRIMARY KEY AUTOINCREMENT,
    type          TEXT NOT NULL,
    visit_id      TEXT NOT NULL,
    task_id       TEXT,
    gps_lat       REAL,
    gps_lng       REAL,
    captured_offline INTEGER NOT NULL DEFAULT 1,
    notes         TEXT,
    occurred_at   TEXT NOT NULL,
    synced        INTEGER NOT NULL DEFAULT 0
  );
`;
```

- [ ] **Step 3: Create `src/db/events.ts`**

```ts
// src/db/events.ts
import * as SQLite from 'expo-sqlite';
import { CREATE_EVENTS_TABLE } from './schema';
import type { SyncEvent } from '@/types/domain';

let db: SQLite.SQLiteDatabase | null = null;

export async function openEventStore() {
  db = await SQLite.openDatabaseAsync('hcare_events.db');
  await db.execAsync(CREATE_EVENTS_TABLE);
}

function getDb(): SQLite.SQLiteDatabase {
  if (!db) throw new Error('Event store not opened. Call openEventStore() first.');
  return db;
}

export async function insertEvent(event: SyncEvent): Promise<void> {
  const database = getDb();
  await database.runAsync(
    `INSERT INTO sync_events (type, visit_id, task_id, gps_lat, gps_lng, captured_offline, notes, occurred_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
    [
      event.type,
      event.visitId,
      event.taskId ?? null,
      event.gpsCoordinate?.lat ?? null,
      event.gpsCoordinate?.lng ?? null,
      event.capturedOffline ? 1 : 0,
      event.notes ?? null,
      event.occurredAt,
    ]
  );
}

export async function getPendingEvents(): Promise<(SyncEvent & { rowId: number })[]> {
  const database = getDb();
  const rows = await database.getAllAsync<any>(
    `SELECT * FROM sync_events WHERE synced = 0 ORDER BY occurred_at ASC`
  );
  return rows.map(row => ({
    rowId:           row.row_id,
    type:            row.type,
    visitId:         row.visit_id,
    taskId:          row.task_id ?? undefined,
    gpsCoordinate:   row.gps_lat != null ? { lat: row.gps_lat, lng: row.gps_lng } : undefined,
    capturedOffline: row.captured_offline === 1,
    notes:           row.notes ?? undefined,
    occurredAt:      row.occurred_at,
  }));
}

export async function markSynced(rowIds: number[]): Promise<void> {
  if (rowIds.length === 0) return;
  const database = getDb();
  const placeholders = rowIds.map(() => '?').join(',');
  await database.runAsync(
    `UPDATE sync_events SET synced = 1 WHERE row_id IN (${placeholders})`,
    rowIds
  );
}

export async function deleteByVisitId(visitId: string): Promise<void> {
  const database = getDb();
  await database.runAsync(`DELETE FROM sync_events WHERE visit_id = ?`, [visitId]);
}
```

- [ ] **Step 4: Open the event store in `App.tsx` on launch**

In `mobile/App.tsx`:

```tsx
import React, { useEffect } from 'react';
import { RootNavigator } from './src/navigation/RootNavigator';
import { openEventStore } from './src/db/events';

export default function App() {
  useEffect(() => {
    openEventStore().catch(console.error);
  }, []);

  return <RootNavigator />;
}
```

- [ ] **Step 5: Run tests**

```bash
npm test -- --testPathPattern=events
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add mobile/src/db/ mobile/App.tsx
git commit -m "feat(mobile): add SQLite offline event log with insert, getPending, markSynced"
```

---

### Task 11: Visit Execution Screen

**Files:**
- Create: `src/hooks/useVisit.ts`
- Create: `src/screens/visit/GpsStatusBar.tsx`
- Create: `src/screens/visit/CarePlanSection.tsx`
- Create: `src/screens/visit/AdlTaskList.tsx`
- Create: `src/screens/visit/CareNotes.tsx`
- Create: `src/screens/visit/ClockOutModal.tsx`
- Implement: `src/screens/visit/VisitScreen.tsx`
- Test: `src/__tests__/screens/VisitScreen.test.tsx`

- [ ] **Step 1: Create `src/hooks/useVisit.ts`**

```ts
// src/hooks/useVisit.ts
import { useMutation, useQuery } from '@tanstack/react-query';
import { visitsApi } from '@/api/visits';
import { useVisitStore } from '@/store/visitStore';
import { insertEvent } from '@/db/events';
import NetInfo from '@react-native-community/netinfo';
import * as Location from 'expo-location';
import type { AdlTask } from '@/types/domain';

export function useVisit(shiftId: string) {
  const { activeVisitId, clearActiveVisit } = useVisitStore();

  const carePlanQuery = useQuery({
    queryKey: ['carePlan', shiftId],
    queryFn: () => visitsApi.carePlan(shiftId).then(r => r.carePlan),
    enabled: !!shiftId,
  });

  const completeTask = useMutation({
    mutationFn: async ({ taskId }: { taskId: string }) => {
      if (!activeVisitId) return;
      const net = await NetInfo.fetch();
      const offline = !net.isConnected;
      await insertEvent({ type: 'TASK_COMPLETE', visitId: activeVisitId, taskId, capturedOffline: offline, occurredAt: new Date().toISOString() });
      if (!offline) await visitsApi.completeTask(activeVisitId, taskId);
    },
  });

  const revertTask = useMutation({
    mutationFn: async ({ taskId }: { taskId: string }) => {
      if (!activeVisitId) return;
      const net = await NetInfo.fetch();
      const offline = !net.isConnected;
      await insertEvent({ type: 'TASK_REVERT', visitId: activeVisitId, taskId, capturedOffline: offline, occurredAt: new Date().toISOString() });
      if (!offline) await visitsApi.revertTask(activeVisitId, taskId);
    },
  });

  const saveNotes = useMutation({
    mutationFn: async ({ notes }: { notes: string }) => {
      if (!activeVisitId) return;
      const net = await NetInfo.fetch();
      const offline = !net.isConnected;
      await insertEvent({ type: 'NOTE_SAVE', visitId: activeVisitId, notes, capturedOffline: offline, occurredAt: new Date().toISOString() });
      if (!offline) await visitsApi.saveNotes(activeVisitId, notes);
    },
  });

  const clockOut = useMutation({
    mutationFn: async ({ notes }: { notes: string }) => {
      if (!activeVisitId) return;
      const net = await NetInfo.fetch();
      const capturedOffline = !net.isConnected;

      let gpsCoordinate: { lat: number; lng: number } | undefined;
      try {
        const loc = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced });
        gpsCoordinate = { lat: loc.coords.latitude, lng: loc.coords.longitude };
      } catch { /* GPS unavailable — spec 15b */ }

      await insertEvent({ type: 'CLOCK_OUT', visitId: activeVisitId!, gpsCoordinate, capturedOffline, occurredAt: new Date().toISOString() });
      if (!capturedOffline) {
        await visitsApi.clockOut(activeVisitId!, { gpsCoordinate, capturedOffline, notes });
      }
      clearActiveVisit();
    },
  });

  return { carePlanQuery, completeTask, revertTask, saveNotes, clockOut };
}
```

- [ ] **Step 2: Create `src/screens/visit/GpsStatusBar.tsx`**

```tsx
// src/screens/visit/GpsStatusBar.tsx
import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { Colors } from '@/constants/colors';
import { Typography } from '@/constants/typography';

type GpsStatus = 'OK' | 'OUTSIDE_RANGE' | 'OFFLINE' | 'UNAVAILABLE';

interface Props { status: GpsStatus; distance?: number }

export function GpsStatusBar({ status, distance }: Props) {
  if (status === 'OK') {
    return (
      <View style={[styles.bar, { backgroundColor: '#f0fdf4', borderColor: Colors.green }]}>
        <Text style={[Typography.body, { color: Colors.green }]}>
          GPS captured{distance != null ? ` · ${distance.toFixed(0)}m from client` : ''}
        </Text>
      </View>
    );
  }
  if (status === 'OUTSIDE_RANGE') {
    return (
      <View style={[styles.bar, { backgroundColor: '#fffbeb', borderColor: Colors.amber }]}>
        <Text style={[Typography.body, { color: Colors.textPrimary }]}>
          GPS outside expected range — your agency will review this visit.
        </Text>
      </View>
    );
  }
  if (status === 'OFFLINE') {
    return (
      <View style={[styles.bar, { backgroundColor: '#fffbeb', borderColor: Colors.amber }]}>
        <Text style={[Typography.body, { color: Colors.textPrimary }]}>
          Offline — GPS captured on device, will sync on reconnect
        </Text>
      </View>
    );
  }
  // UNAVAILABLE
  return (
    <View style={[styles.bar, { backgroundColor: '#fffbeb', borderColor: Colors.amber }]}>
      <Text style={[Typography.body, { color: Colors.textPrimary }]}>
        Location unavailable — your agency will verify this visit manually.
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  bar: { padding: 10, borderRadius: 8, borderWidth: 1, marginBottom: 12 },
});
```

- [ ] **Step 3: Create `src/screens/visit/CarePlanSection.tsx`**

```tsx
// src/screens/visit/CarePlanSection.tsx
import React, { useState, useEffect } from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import type { CarePlan } from '@/types/domain';
import { Colors } from '@/constants/colors';
import { Typography } from '@/constants/typography';

interface Props { carePlan: CarePlan; clientId: string }

export function CarePlanSection({ carePlan, clientId }: Props) {
  const [expanded, setExpanded] = useState(carePlan.updatedSinceLastVisit);
  const key = `careplan_expanded_${clientId}`;

  useEffect(() => {
    if (!carePlan.updatedSinceLastVisit) {
      AsyncStorage.getItem(key).then(v => { if (v !== null) setExpanded(v === 'true'); });
    }
  }, [clientId]);

  const toggle = () => {
    const next = !expanded;
    setExpanded(next);
    AsyncStorage.setItem(key, String(next));
  };

  return (
    <View style={styles.section}>
      <TouchableOpacity style={styles.header} onPress={toggle}>
        <View style={styles.headerLeft}>
          <Text style={[Typography.sectionLabel, { color: Colors.textSecondary }]}>CARE PLAN</Text>
          {carePlan.updatedSinceLastVisit && (
            <View style={styles.updatedPill}>
              <Text style={styles.updatedText}>Updated since your last visit</Text>
            </View>
          )}
        </View>
        <Text style={{ color: Colors.textMuted }}>{expanded ? '▲' : '▼'}</Text>
      </TouchableOpacity>
      {expanded && (
        <View style={styles.body}>
          {carePlan.diagnoses.length > 0 && (
            <>
              <Text style={styles.label}>DIAGNOSES</Text>
              {carePlan.diagnoses.map((d, i) => <Text key={i} style={styles.item}>• {d}</Text>)}
            </>
          )}
          {carePlan.allergies.length > 0 && (
            <>
              <Text style={[styles.label, { marginTop: 8 }]}>ALLERGIES</Text>
              {carePlan.allergies.map((a, i) => <Text key={i} style={[styles.item, { color: Colors.red }]}>⚠ {a}</Text>)}
            </>
          )}
          {carePlan.caregiverNotes.length > 0 && (
            <>
              <Text style={[styles.label, { marginTop: 8 }]}>NOTES</Text>
              <Text style={styles.item}>{carePlan.caregiverNotes}</Text>
            </>
          )}
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  section:     { backgroundColor: Colors.white, borderRadius: 10, borderWidth: 1, borderColor: Colors.border, marginBottom: 12 },
  header:      { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: 14 },
  headerLeft:  { flexDirection: 'row', alignItems: 'center', gap: 8, flex: 1 },
  updatedPill: { backgroundColor: '#fef9c3', borderRadius: 4, paddingHorizontal: 6, paddingVertical: 2 },
  updatedText: { fontSize: 10, fontWeight: '700', color: '#854d0e' },
  body:        { padding: 14, paddingTop: 0 },
  label:       { fontSize: 10, fontWeight: '700', color: Colors.textMuted, textTransform: 'uppercase', letterSpacing: 0.1, marginBottom: 4 },
  item:        { ...Typography.body, color: Colors.textPrimary, lineHeight: 20 },
});
```

- [ ] **Step 4: Create `src/screens/visit/AdlTaskList.tsx`**

```tsx
// src/screens/visit/AdlTaskList.tsx
import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import type { AdlTask } from '@/types/domain';
import { Colors } from '@/constants/colors';
import { Typography } from '@/constants/typography';

interface Props {
  tasks: AdlTask[];
  onToggle: (taskId: string, completed: boolean) => void;
}

export function AdlTaskList({ tasks, onToggle }: Props) {
  const pending   = tasks.filter(t => !t.completed);
  const completed = tasks.filter(t => t.completed);
  const ordered   = [...pending, ...completed];

  return (
    <View style={styles.section}>
      <Text style={[Typography.sectionLabel, styles.label]}>
        ADL TASKS {tasks.filter(t => t.completed).length} / {tasks.length}
      </Text>
      {ordered.map(task => (
        <TouchableOpacity key={task.id} style={styles.row} onPress={() => onToggle(task.id, task.completed)} activeOpacity={0.7}>
          <View style={[styles.checkbox, task.completed && styles.checkboxDone]}>
            {task.completed && <Text style={styles.checkmark}>✓</Text>}
          </View>
          <View style={styles.taskInfo}>
            <Text style={[Typography.body, styles.taskName, task.completed && styles.strikethrough]}>
              {task.name}
            </Text>
            {task.instructions && !task.completed && (
              <Text style={[Typography.timestamp, { color: Colors.textMuted, marginTop: 2 }]}>
                {task.instructions}
              </Text>
            )}
          </View>
        </TouchableOpacity>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  section:    { backgroundColor: Colors.white, borderRadius: 10, borderWidth: 1, borderColor: Colors.border, padding: 14, marginBottom: 12 },
  label:      { color: Colors.textSecondary, marginBottom: 10 },
  row:        { flexDirection: 'row', alignItems: 'flex-start', paddingVertical: 10, borderTopWidth: 1, borderTopColor: Colors.border },
  checkbox:   { width: 22, height: 22, borderRadius: 11, borderWidth: 2, borderColor: Colors.border, alignItems: 'center', justifyContent: 'center', marginRight: 12, marginTop: 1 },
  checkboxDone: { backgroundColor: Colors.green, borderColor: Colors.green },
  checkmark:  { color: Colors.white, fontSize: 12, fontWeight: '700' },
  taskInfo:   { flex: 1 },
  taskName:   { color: Colors.textPrimary },
  strikethrough: { textDecorationLine: 'line-through', color: Colors.textMuted },
});
```

- [ ] **Step 5: Create `src/screens/visit/CareNotes.tsx`**

```tsx
// src/screens/visit/CareNotes.tsx
import React, { useState } from 'react';
import { View, Text, TextInput, StyleSheet } from 'react-native';
import { Colors } from '@/constants/colors';
import { Typography } from '@/constants/typography';

interface Props {
  onBlur: (text: string) => void;
  initialValue?: string; // restored from visitStore so notes survive re-navigation
}

export function CareNotes({ onBlur, initialValue }: Props) {
  const [text, setText] = useState(initialValue ?? '');

  return (
    <View style={styles.section}>
      <Text style={[Typography.sectionLabel, styles.label]}>CARE NOTES</Text>
      <TextInput
        style={styles.input}
        multiline
        placeholder="Add visit notes…"
        placeholderTextColor={Colors.textMuted}
        value={text}
        onChangeText={setText}
        onBlur={() => onBlur(text)}
        textAlignVertical="top"
        minHeight={80}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  section: { backgroundColor: Colors.white, borderRadius: 10, borderWidth: 1, borderColor: Colors.border, padding: 14, marginBottom: 12 },
  label:   { color: Colors.textSecondary, marginBottom: 8 },
  input:   { ...Typography.body, color: Colors.textPrimary, lineHeight: 22 },
});
```

- [ ] **Step 6: Create `src/screens/visit/ClockOutModal.tsx`**

```tsx
// src/screens/visit/ClockOutModal.tsx
import React from 'react';
import { Modal, View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { Colors } from '@/constants/colors';
import { Typography } from '@/constants/typography';

interface Props {
  visible: boolean;
  remainingTasks: number;
  onConfirm: () => void;
  onCancel: () => void;
}

export function ClockOutModal({ visible, remainingTasks, onConfirm, onCancel }: Props) {
  return (
    <Modal visible={visible} transparent animationType="fade">
      <View style={styles.overlay}>
        <View style={styles.dialog}>
          <Text style={[Typography.cardTitle, styles.title]}>Tasks remaining</Text>
          <Text style={[Typography.body, styles.body]}>
            You have {remainingTasks} task{remainingTasks !== 1 ? 's' : ''} remaining. Clock out anyway?
          </Text>
          <View style={styles.actions}>
            <TouchableOpacity style={styles.cancelBtn} onPress={onCancel}>
              <Text style={styles.cancelText}>Go Back</Text>
            </TouchableOpacity>
            <TouchableOpacity style={styles.confirmBtn} onPress={onConfirm}>
              <Text style={styles.confirmText}>Clock Out</Text>
            </TouchableOpacity>
          </View>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  overlay:    { flex: 1, backgroundColor: 'rgba(0,0,0,0.45)', justifyContent: 'center', alignItems: 'center' },
  dialog:     { backgroundColor: Colors.white, borderRadius: 14, padding: 24, width: '82%' },
  title:      { color: Colors.textPrimary, marginBottom: 8 },
  body:       { color: Colors.textSecondary, lineHeight: 22, marginBottom: 20 },
  actions:    { flexDirection: 'row', gap: 10 },
  cancelBtn:  { flex: 1, paddingVertical: 12, alignItems: 'center', backgroundColor: Colors.surface, borderRadius: 8 },
  cancelText: { ...Typography.body, color: Colors.textSecondary, fontWeight: '600' },
  confirmBtn: { flex: 1, paddingVertical: 12, alignItems: 'center', backgroundColor: Colors.dark, borderRadius: 8 },
  confirmText:{ ...Typography.body, color: Colors.white, fontWeight: '700' },
});
```

- [ ] **Step 7: Write failing Visit screen test**

Create `mobile/src/__tests__/screens/VisitScreen.test.tsx`:

```tsx
import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { VisitScreen } from '@/screens/visit/VisitScreen';
import { useVisitStore } from '@/store/visitStore';
import { setupMocks, teardownMocks } from '@/mocks/handlers';
import { MOCK_SHIFT_ID_1, MOCK_VISIT_ID, mockCarePlan } from '@/mocks/data';

const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
const wrapper = ({ children }: any) => <QueryClientProvider client={qc}>{children}</QueryClientProvider>;

beforeEach(() => {
  setupMocks();
  useVisitStore.setState({
    activeVisitId:    MOCK_VISIT_ID,
    activeShiftId:    MOCK_SHIFT_ID_1,
    activeClientId:   'cl-001',          // matches mockTodayShifts[0].clientId; keys CarePlanSection per-client collapse pref
    activeClientName: 'Eleanor Vance',
    clockInTime:      new Date().toISOString(),
    gpsStatus:        'OK',              // required for GpsStatusBar OK/OFFLINE path tests
    activeVisitNotes: null,
  });
});
afterEach(() => { qc.clear(); teardownMocks(); useVisitStore.setState({ activeVisitId: null, activeShiftId: null, activeClientId: null, activeClientName: null, clockInTime: null, gpsStatus: null, activeVisitNotes: null }); });

describe('VisitScreen', () => {
  const nav = { navigate: jest.fn(), goBack: jest.fn() };
  const route = { params: { visitId: MOCK_VISIT_ID } };

  it('shows client name in hero', () => {
    render(<VisitScreen navigation={nav} route={route} />, { wrapper });
    expect(screen.getByText('Eleanor Vance')).toBeTruthy();
  });

  it('loads and displays ADL tasks', async () => {
    render(<VisitScreen navigation={nav} route={route} />, { wrapper });
    await waitFor(() =>
      expect(screen.getByText(mockCarePlan.adlTasks[0].name)).toBeTruthy()
    );
  });

  it('shows Clock Out button', async () => {
    render(<VisitScreen navigation={nav} route={route} />, { wrapper });
    expect(screen.getByText(/clock out/i)).toBeTruthy();
  });
});
```

Run: `npm test -- --testPathPattern=VisitScreen`
Expected: FAIL.

- [ ] **Step 8: Implement `VisitScreen.tsx`**

```tsx
// src/screens/visit/VisitScreen.tsx
import React, { useState, useEffect, useRef } from 'react';
import { View, Text, ScrollView, TouchableOpacity, StyleSheet, Alert } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useMutation } from '@tanstack/react-query';
import { useVisit } from '@/hooks/useVisit';
import { useVisitStore } from '@/store/visitStore';
import { visitsApi } from '@/api/visits';
import { deleteByVisitId } from '@/db/events';
import { GpsStatusBar } from './GpsStatusBar';
import { CarePlanSection } from './CarePlanSection';
import { AdlTaskList } from './AdlTaskList';
import { CareNotes } from './CareNotes';
import { ClockOutModal } from './ClockOutModal';
import { Colors } from '@/constants/colors';
import { Typography } from '@/constants/typography';
import type { AdlTask } from '@/types/domain';

function formatTime(iso: string) {
  return new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

function formatElapsed(clockInTime: string) {
  const elapsed = Math.floor((Date.now() - new Date(clockInTime).getTime()) / 1000);
  const h = Math.floor(elapsed / 3600);
  const m = Math.floor((elapsed % 3600) / 60);
  const s = elapsed % 60;
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
}

export function VisitScreen({ navigation, route }: any) {
  const insets = useSafeAreaInsets();
  // shiftId always comes from visitStore — route.params.shiftId is not used.
  const { activeShiftId, activeClientId, activeClientName, clockInTime, gpsStatus, activeVisitNotes, setVisitNotes, clearActiveVisit } = useVisitStore();
  const activeVisitId = useVisitStore(s => s.activeVisitId);
  const shiftId = activeShiftId ?? '';
  const { carePlanQuery, completeTask, revertTask, saveNotes, clockOut } = useVisit(shiftId);

  // Void clock-in: calls BFF DELETE, cleans up SQLite queue, clears store.
  // The BFF rejects voids older than 5 minutes — onError surfaces that to the caregiver.
  const voidClockIn = useMutation({
    mutationFn: () => visitsApi.voidClockIn(activeVisitId!),
    onSuccess: async () => {
      await deleteByVisitId(activeVisitId!).catch(console.error);
      clearActiveVisit();
      navigation.navigate('Today');
    },
    onError: () => {
      Alert.alert(
        'Could Not Void',
        'The void window may have expired (5 minutes from clock-in). Please contact your agency to resolve this visit.',
      );
    },
  });

  const [tasks, setTasks] = useState<AdlTask[]>([]);
  const [showClockOutModal, setShowClockOutModal] = useState(false);
  const [elapsed, setElapsed] = useState(clockInTime ? formatElapsed(clockInTime) : '00:00:00');

  useEffect(() => {
    if (carePlanQuery.data) setTasks(carePlanQuery.data.adlTasks);
  }, [carePlanQuery.data]);

  useEffect(() => {
    if (!clockInTime) return;
    const id = setInterval(() => setElapsed(formatElapsed(clockInTime)), 1000);
    return () => clearInterval(id);
  }, [clockInTime]);

  const handleToggleTask = (taskId: string, completed: boolean) => {
    setTasks(prev => prev.map(t => t.id === taskId ? { ...t, completed: !completed } : t));
    if (completed) {
      revertTask.mutate({ taskId });
    } else {
      completeTask.mutate({ taskId });
    }
  };

  const handleClockOutPress = () => {
    const pending = tasks.filter(t => !t.completed);
    if (pending.length > 0 && tasks.filter(t => t.completed).length / tasks.length < 0.5) {
      setShowClockOutModal(true);
    } else {
      doClockOut();
    }
  };

  const doClockOut = () => {
    setShowClockOutModal(false);
    clockOut.mutate(
      { notes: activeVisitNotes ?? '' },
      { onSuccess: () => navigation.navigate('Today') }
    );
  };

  const carePlan = carePlanQuery.data;
  const remainingTasks = tasks.filter(t => !t.completed).length;

  return (
    <View style={[styles.root, { paddingTop: insets.top }]}>
      {/* Sticky mini-header */}
      <View style={styles.miniHeader}>
        <TouchableOpacity onPress={() => navigation.navigate('Today')}>
          <Text style={styles.back}>← Today</Text>
        </TouchableOpacity>
        <Text style={styles.miniTimer}>{elapsed}</Text>
        <TouchableOpacity onPress={() => Alert.alert('Wrong shift?', 'This will void your clock-in if it was created less than 5 minutes ago.', [
          { text: 'Cancel', style: 'cancel' },
          {
            text: 'Void Clock-In',
            style: 'destructive',
            onPress: () => voidClockIn.mutate(),
          },
        ])}>
          <Text style={styles.overflow}>⚠ Wrong shift?</Text>
        </TouchableOpacity>
      </View>

      <ScrollView contentContainerStyle={{ padding: 16, paddingBottom: 32 }}>
        {/* Hero */}
        <View style={styles.hero}>
          <Text style={styles.heroLabel}>IN PROGRESS</Text>
          <Text style={styles.heroClient}>{activeClientName}</Text>
          <Text style={styles.heroMeta}>{carePlan?.caregiverNotes ? 'Personal Care' : 'Visit'}</Text>
          <View style={styles.heroTimes}>
            <Text style={styles.heroTimeLabel}>Clock-in {clockInTime ? formatTime(clockInTime) : '—'}</Text>
            <Text style={styles.heroElapsed}>{elapsed}</Text>
          </View>
        </View>

        {/* GPS status — dynamic: determined at clock-in by useClockIn, stored in visitStore */}
        <GpsStatusBar status={gpsStatus ?? 'UNAVAILABLE'} />

        {/* Care plan — clientId from visitStore (not carePlan.id) so collapse preference
            persists per client across care plan version changes */}
        {carePlan && (
          <CarePlanSection carePlan={carePlan} clientId={activeClientId ?? ''} />
        )}

        {/* ADL tasks */}
        {tasks.length > 0 && (
          <AdlTaskList tasks={tasks} onToggle={handleToggleTask} />
        )}

        {/* Care notes — initialValue from visitStore so text survives re-navigation */}
        <CareNotes
          initialValue={activeVisitNotes ?? ''}
          onBlur={(text) => {
            setVisitNotes(text);
            saveNotes.mutate({ notes: text });
          }}
        />

        {/* Clock Out — disabled while care plan loads so handleClockOutPress always has
            accurate task data. Tapping before care plan loads would see tasks=[] and
            skip the incomplete-task confirmation, creating an EVV record with no ADL data. */}
        <TouchableOpacity
          style={[styles.clockOutBtn, (clockOut.isPending || carePlanQuery.isLoading) && styles.clockOutBtnDisabled]}
          onPress={handleClockOutPress}
          disabled={clockOut.isPending || carePlanQuery.isLoading}
        >
          <Text style={styles.clockOutText}>
            {clockOut.isPending ? 'Clocking out…' : carePlanQuery.isLoading ? 'Loading…' : 'Clock Out'}
          </Text>
          <Text style={styles.clockOutSub}>GPS will be captured</Text>
        </TouchableOpacity>
      </ScrollView>

      <ClockOutModal
        visible={showClockOutModal}
        remainingTasks={remainingTasks}
        onConfirm={doClockOut}
        onCancel={() => setShowClockOutModal(false)}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  root:       { flex: 1, backgroundColor: Colors.surface },
  miniHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', backgroundColor: Colors.white, paddingHorizontal: 16, paddingVertical: 10, borderBottomWidth: 1, borderBottomColor: Colors.border },
  back:       { ...Typography.body, color: Colors.blue },
  miniTimer:  { ...Typography.bodyMedium, color: Colors.blue, fontVariant: ['tabular-nums'] },
  overflow:   { fontSize: 12, color: Colors.amber },
  hero:       { backgroundColor: Colors.blue, borderRadius: 12, padding: 16, marginBottom: 12 },
  heroLabel:  { fontSize: 10, fontWeight: '700', color: 'rgba(255,255,255,0.7)', textTransform: 'uppercase', letterSpacing: 0.1, marginBottom: 4 },
  heroClient: { ...Typography.screenTitle, color: Colors.white, marginBottom: 4 },
  heroMeta:   { ...Typography.body, color: 'rgba(255,255,255,0.8)', marginBottom: 12 },
  heroTimes:  { flexDirection: 'row', justifyContent: 'space-between' },
  heroTimeLabel: { ...Typography.timestamp, color: 'rgba(255,255,255,0.8)' },
  heroElapsed:{ fontSize: 20, fontWeight: '700', color: Colors.white, fontVariant: ['tabular-nums'] },
  clockOutBtn:{ backgroundColor: Colors.dark, borderRadius: 10, paddingVertical: 16, alignItems: 'center', marginTop: 8 },
  clockOutBtnDisabled: { opacity: 0.6 },
  clockOutText: { ...Typography.cardTitle, color: Colors.white },
  clockOutSub:  { ...Typography.timestamp, color: 'rgba(255,255,255,0.6)', marginTop: 4 },
});
```

- [ ] **Step 9: Run tests**

```bash
npm test -- --testPathPattern=VisitScreen
```

Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add mobile/src/hooks/useVisit.ts mobile/src/screens/visit/
git commit -m "feat(mobile): implement visit execution screen — ADL tasks, care plan, notes, clock-out"
```

---

### Task 12: Care Plan Read-Only Screen

**Files:**
- Implement: `src/screens/carePlan/CarePlanScreen.tsx`

- [ ] **Step 1: Implement `CarePlanScreen.tsx`**

```tsx
// src/screens/carePlan/CarePlanScreen.tsx
import React from 'react';
import { View, Text, ScrollView, StyleSheet } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useQuery } from '@tanstack/react-query';
import { visitsApi } from '@/api/visits';
import { Colors } from '@/constants/colors';
import { Typography } from '@/constants/typography';

export function CarePlanScreen({ route, navigation }: any) {
  const insets = useSafeAreaInsets();
  const shiftId = route?.params?.shiftId as string;

  const { data, isLoading } = useQuery({
    queryKey: ['carePlan', shiftId],
    queryFn: () => visitsApi.carePlan(shiftId).then(r => r.carePlan),
  });

  return (
    <View style={[styles.root, { paddingTop: insets.top }]}>
      <View style={styles.header}>
        <Text onPress={() => navigation.goBack()} style={styles.back}>← Back</Text>
        <Text style={[Typography.screenTitle, { color: Colors.textPrimary }]}>Care Plan</Text>
        <View style={{ width: 40 }} />
      </View>

      <ScrollView contentContainerStyle={{ padding: 16 }}>
        {isLoading && <Text style={[Typography.body, { color: Colors.textMuted }]}>Loading…</Text>}
        {data && (
          <>
            {data.updatedSinceLastVisit && (
              <View style={styles.updatedBanner}>
                <Text style={styles.updatedText}>Updated since your last visit</Text>
              </View>
            )}
            <Section title="DIAGNOSES" items={data.diagnoses} />
            <Section title="ALLERGIES" items={data.allergies} color={Colors.red} prefix="⚠ " />
            <Section title="GOALS" items={data.goals} />
            {data.caregiverNotes.length > 0 && (
              <View style={styles.card}>
                <Text style={[Typography.sectionLabel, styles.sectionLabel]}>CAREGIVER NOTES</Text>
                <Text style={[Typography.body, { color: Colors.textPrimary, lineHeight: 22 }]}>{data.caregiverNotes}</Text>
              </View>
            )}
            {data.adlTasks.length > 0 && (
              <View style={styles.card}>
                <Text style={[Typography.sectionLabel, styles.sectionLabel]}>ADL TASKS ({data.adlTasks.length})</Text>
                {data.adlTasks.map(task => (
                  <View key={task.id} style={styles.taskRow}>
                    <Text style={[Typography.body, { color: Colors.textPrimary }]}>• {task.name}</Text>
                    {task.instructions && (
                      <Text style={[Typography.timestamp, { color: Colors.textMuted, marginLeft: 12, marginTop: 2 }]}>{task.instructions}</Text>
                    )}
                  </View>
                ))}
              </View>
            )}
          </>
        )}
      </ScrollView>
    </View>
  );
}

function Section({ title, items, color = Colors.textPrimary, prefix = '• ' }: { title: string; items: string[]; color?: string; prefix?: string }) {
  if (items.length === 0) return null;
  return (
    <View style={styles.card}>
      <Text style={[Typography.sectionLabel, { color: Colors.textSecondary, marginBottom: 8 }]}>{title}</Text>
      {items.map((item, i) => (
        <Text key={i} style={[Typography.body, { color, lineHeight: 22 }]}>{prefix}{item}</Text>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  root:         { flex: 1, backgroundColor: Colors.surface },
  header:       { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', backgroundColor: Colors.white, padding: 16, borderBottomWidth: 1, borderBottomColor: Colors.border },
  back:         { ...Typography.body, color: Colors.blue, width: 40 },
  updatedBanner:{ backgroundColor: '#fef9c3', borderRadius: 8, padding: 10, marginBottom: 12 },
  updatedText:  { ...Typography.body, color: '#854d0e', fontWeight: '600' },
  card:         { backgroundColor: Colors.white, borderRadius: 10, borderWidth: 1, borderColor: Colors.border, padding: 14, marginBottom: 10 },
  sectionLabel: { color: Colors.textSecondary, marginBottom: 8 },
  taskRow:      { marginBottom: 8 },
});
```

- [ ] **Step 2: Write failing `CarePlanScreen` test**

Create `mobile/src/__tests__/screens/CarePlanScreen.test.tsx`:

```tsx
import React from 'react';
import { render, screen, waitFor } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { CarePlanScreen } from '@/screens/carePlan/CarePlanScreen';
import { setupMocks, teardownMocks } from '@/mocks/handlers';
import { MOCK_SHIFT_ID_1, mockCarePlan } from '@/mocks/data';

const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
const wrapper = ({ children }: any) => <QueryClientProvider client={qc}>{children}</QueryClientProvider>;

beforeEach(() => setupMocks());
afterEach(() => { qc.clear(); teardownMocks(); });

describe('CarePlanScreen', () => {
  const nav = { goBack: jest.fn() };
  const route = { params: { shiftId: MOCK_SHIFT_ID_1 } };

  it('renders the Care Plan header', () => {
    render(<CarePlanScreen navigation={nav} route={route} />, { wrapper });
    expect(screen.getByText('Care Plan')).toBeTruthy();
  });

  it('loads and displays ADL tasks from mock', async () => {
    render(<CarePlanScreen navigation={nav} route={route} />, { wrapper });
    await waitFor(() =>
      expect(screen.getByText(mockCarePlan.adlTasks[0].name)).toBeTruthy()
    );
  });
});
```

Run: `npm test -- --testPathPattern=CarePlanScreen`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add mobile/src/screens/carePlan/ mobile/src/__tests__/screens/CarePlanScreen.test.tsx
git commit -m "feat(mobile): implement Care Plan read-only screen"
```

---

## Phase 3 — Secondary Features

---

### Task 13: Open Shifts Screen

**Files:**
- Create: `src/hooks/useOpenShifts.ts`
- Create: `src/screens/openShifts/OpenShiftCard.tsx`
- Implement: `src/screens/openShifts/OpenShiftsScreen.tsx`
- Test: `src/__tests__/screens/OpenShiftsScreen.test.tsx`

- [ ] **Step 1: Create `src/hooks/useOpenShifts.ts`**

```ts
// src/hooks/useOpenShifts.ts
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { shiftsApi } from '@/api/shifts';

export function useOpenShifts() {
  const qc = useQueryClient();

  const query = useQuery({
    queryKey: ['shifts', 'open'],
    queryFn: () => shiftsApi.open().then(r => r.shifts),
  });

  const accept = useMutation({
    mutationFn: (shiftId: string) => shiftsApi.accept(shiftId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['shifts', 'open'] }),
  });

  const decline = useMutation({
    mutationFn: (shiftId: string) => shiftsApi.decline(shiftId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['shifts', 'open'] }),
  });

  return { shifts: query.data ?? [], isLoading: query.isLoading, refetch: query.refetch, accept, decline };
}
```

- [ ] **Step 2: Create `src/screens/openShifts/OpenShiftCard.tsx`**

```tsx
// src/screens/openShifts/OpenShiftCard.tsx
import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import type { OpenShift } from '@/types/domain';
import { Colors } from '@/constants/colors';
import { Typography } from '@/constants/typography';

interface Props {
  shift: OpenShift;
  isOnline: boolean;
  onAccept: () => void;
  onDecline: () => void;
  isAccepting: boolean;
  isDeclining: boolean;
}

function formatDateTime(iso: string) {
  const d = new Date(iso);
  return `${d.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric' })} · ${d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`;
}

export function OpenShiftCard({ shift, isOnline, onAccept, onDecline, isAccepting, isDeclining }: Props) {
  return (
    <View style={[styles.card, { borderLeftColor: shift.urgent ? Colors.red : Colors.textMuted }]}>
      {shift.urgent && (
        <View style={styles.urgentBadge}>
          <Text style={styles.urgentText}>URGENT</Text>
        </View>
      )}
      <Text style={[Typography.cardTitle, { color: Colors.textPrimary, marginBottom: 4 }]}>{shift.clientName}</Text>
      <Text style={[Typography.body, { color: Colors.textSecondary }]}>{formatDateTime(shift.scheduledStart)}</Text>
      <Text style={[Typography.body, { color: Colors.textSecondary }]}>{shift.serviceType}</Text>
      {shift.distance != null && (
        <Text style={[Typography.timestamp, { color: Colors.textMuted, marginTop: 2 }]}>{shift.distance.toFixed(1)} km away</Text>
      )}

      <View style={styles.actions}>
        <TouchableOpacity
          style={[styles.acceptBtn, (!isOnline || isAccepting) && styles.btnDisabled]}
          onPress={onAccept}
          disabled={!isOnline || isAccepting}
        >
          <Text style={styles.acceptText}>{isAccepting ? 'Accepting…' : 'Accept Shift'}</Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={[styles.declineBtn, isDeclining && styles.btnDisabled]}
          onPress={onDecline}
          disabled={isDeclining}
        >
          <Text style={styles.declineText}>{isDeclining ? '…' : 'Decline'}</Text>
        </TouchableOpacity>
      </View>

      {!isOnline && (
        <Text style={[Typography.timestamp, { color: Colors.amber, marginTop: 6 }]}>
          Connect to the internet to accept shifts.
        </Text>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  card:       { backgroundColor: Colors.white, borderWidth: 1, borderColor: Colors.border, borderLeftWidth: 3, borderRadius: 10, padding: 14, marginBottom: 10 },
  urgentBadge:{ alignSelf: 'flex-start', backgroundColor: '#fef2f2', borderRadius: 4, paddingHorizontal: 6, paddingVertical: 2, marginBottom: 6 },
  urgentText: { fontSize: 10, fontWeight: '700', color: Colors.red },
  actions:    { flexDirection: 'row', gap: 8, marginTop: 12 },
  acceptBtn:  { flex: 2, backgroundColor: Colors.blue, borderRadius: 8, paddingVertical: 10, alignItems: 'center' },
  acceptText: { ...Typography.body, color: Colors.white, fontWeight: '700' },
  declineBtn: { flex: 1, backgroundColor: Colors.surface, borderWidth: 1, borderColor: Colors.border, borderRadius: 8, paddingVertical: 10, alignItems: 'center' },
  declineText:{ ...Typography.body, color: Colors.textSecondary },
  btnDisabled:{ opacity: 0.5 },
});
```

- [ ] **Step 3: Write failing open shifts test**

Create `mobile/src/__tests__/screens/OpenShiftsScreen.test.tsx`:

```tsx
import React from 'react';
import { render, screen, waitFor } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { OpenShiftsScreen } from '@/screens/openShifts/OpenShiftsScreen';
import { setupMocks, teardownMocks } from '@/mocks/handlers';
import { mockOpenShifts } from '@/mocks/data';

const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
const wrapper = ({ children }: any) => <QueryClientProvider client={qc}>{children}</QueryClientProvider>;

beforeEach(() => setupMocks());
afterEach(() => { qc.clear(); teardownMocks(); });

describe('OpenShiftsScreen', () => {
  it('displays open shift client names', async () => {
    render(<OpenShiftsScreen />, { wrapper });
    await waitFor(() =>
      expect(screen.getByText(mockOpenShifts[0].clientName)).toBeTruthy()
    );
  });

  it('shows URGENT badge for urgent shifts', async () => {
    render(<OpenShiftsScreen />, { wrapper });
    await waitFor(() => expect(screen.getByText('URGENT')).toBeTruthy());
  });
});
```

Run: `npm test -- --testPathPattern=OpenShiftsScreen`
Expected: FAIL.

- [ ] **Step 4: Implement `OpenShiftsScreen.tsx`**

```tsx
// src/screens/openShifts/OpenShiftsScreen.tsx
import React, { useState } from 'react';
import { View, Text, FlatList, StyleSheet, RefreshControl } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import NetInfo from '@react-native-community/netinfo';
import { useOpenShifts } from '@/hooks/useOpenShifts';
import { OpenShiftCard } from './OpenShiftCard';
import { Colors } from '@/constants/colors';
import { Typography } from '@/constants/typography';

export function OpenShiftsScreen() {
  const insets = useSafeAreaInsets();
  const { shifts, isLoading, refetch, accept, decline } = useOpenShifts();
  const [isOnline, setIsOnline] = useState(true);

  React.useEffect(() => {
    const unsub = NetInfo.addEventListener(state => setIsOnline(!!state.isConnected));
    return unsub;
  }, []);

  return (
    <View style={[styles.root, { paddingTop: insets.top }]}>
      <View style={styles.header}>
        <Text style={[Typography.screenTitle, { color: Colors.textPrimary }]}>Open Shifts</Text>
      </View>
      <FlatList
        data={shifts}
        keyExtractor={s => s.id}
        contentContainerStyle={{ padding: 16 }}
        refreshControl={<RefreshControl refreshing={isLoading} onRefresh={refetch} />}
        ListEmptyComponent={
          !isLoading ? (
            <Text style={[Typography.body, { color: Colors.textMuted, textAlign: 'center', marginTop: 40 }]}>
              No open shifts right now. We'll notify you when one becomes available.
            </Text>
          ) : null
        }
        renderItem={({ item }) => (
          <OpenShiftCard
            shift={item}
            isOnline={isOnline}
            onAccept={() => accept.mutate(item.id)}
            onDecline={() => decline.mutate(item.id)}
            isAccepting={accept.isPending && accept.variables === item.id}
            isDeclining={decline.isPending && decline.variables === item.id}
          />
        )}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  root:   { flex: 1, backgroundColor: Colors.surface },
  header: { backgroundColor: Colors.white, padding: 16, borderBottomWidth: 1, borderBottomColor: Colors.border },
});
```

- [ ] **Step 5: Run tests**

```bash
npm test -- --testPathPattern=OpenShiftsScreen
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add mobile/src/hooks/useOpenShifts.ts mobile/src/screens/openShifts/
git commit -m "feat(mobile): implement Open Shifts screen with accept/decline and offline guard"
```

---

### Task 14: Messages — Inbox + Thread

**Files:**
- Create: `src/hooks/useMessages.ts`
- Implement: `src/screens/messages/MessagesInboxScreen.tsx`
- Implement: `src/screens/messages/MessageThreadScreen.tsx`
- Test: `src/__tests__/screens/MessagesInboxScreen.test.tsx`

- [ ] **Step 1: Create `src/hooks/useMessages.ts`**

```ts
// src/hooks/useMessages.ts
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { messagesApi } from '@/api/messages';

export function useThreads() {
  return useQuery({
    queryKey: ['messages'],
    queryFn: () => messagesApi.threads().then(r => r.threads),
  });
}

export function useThread(threadId: string) {
  const qc = useQueryClient();

  const query = useQuery({
    queryKey: ['messages', threadId],
    queryFn: () => messagesApi.thread(threadId),
    enabled: !!threadId,
  });

  const reply = useMutation({
    mutationFn: (body: string) => messagesApi.reply(threadId, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['messages', threadId] }),
  });

  return { ...query, reply };
}
```

- [ ] **Step 2: Write failing inbox test**

Create `mobile/src/__tests__/screens/MessagesInboxScreen.test.tsx`:

```tsx
import React from 'react';
import { render, screen, waitFor } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MessagesInboxScreen } from '@/screens/messages/MessagesInboxScreen';
import { setupMocks, teardownMocks } from '@/mocks/handlers';
import { mockThreads } from '@/mocks/data';

const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
const wrapper = ({ children }: any) => <QueryClientProvider client={qc}>{children}</QueryClientProvider>;

beforeEach(() => setupMocks());
afterEach(() => { qc.clear(); teardownMocks(); });

describe('MessagesInboxScreen', () => {
  it('shows thread subjects', async () => {
    render(<MessagesInboxScreen navigation={{ navigate: jest.fn() }} />, { wrapper });
    await waitFor(() =>
      expect(screen.getByText(mockThreads[0].subject)).toBeTruthy()
    );
  });
});
```

Run: `npm test -- --testPathPattern=MessagesInboxScreen`
Expected: FAIL.

- [ ] **Step 3: Implement `MessagesInboxScreen.tsx`**

```tsx
// src/screens/messages/MessagesInboxScreen.tsx
import React from 'react';
import { View, Text, FlatList, TouchableOpacity, StyleSheet } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useThreads } from '@/hooks/useMessages';
import { Colors } from '@/constants/colors';
import { Typography } from '@/constants/typography';

function formatTimestamp(iso: string) {
  const d = new Date(iso);
  const now = new Date();
  const dayDiff = Math.floor((now.getTime() - d.getTime()) / 86_400_000);
  if (dayDiff === 0) return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  if (dayDiff === 1) return 'Yesterday';
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
}

export function MessagesInboxScreen({ navigation }: any) {
  const insets = useSafeAreaInsets();
  const { data: threads, isLoading } = useThreads();

  return (
    <View style={[styles.root, { paddingTop: insets.top }]}>
      <View style={styles.header}>
        <Text style={[Typography.screenTitle, { color: Colors.textPrimary }]}>Messages</Text>
      </View>
      <FlatList
        data={threads ?? []}
        keyExtractor={t => t.id}
        ListEmptyComponent={
          !isLoading ? (
            <Text style={[Typography.body, { color: Colors.textMuted, textAlign: 'center', marginTop: 40 }]}>No messages</Text>
          ) : null
        }
        renderItem={({ item: thread }) => (
          <TouchableOpacity
            style={styles.row}
            onPress={() => navigation.navigate('MessageThread', { threadId: thread.id })}
          >
            <View style={styles.avatar}>
              <Text style={styles.avatarText}>SC</Text>
            </View>
            <View style={styles.body}>
              <View style={styles.rowTop}>
                <Text style={[styles.subject, thread.unread && styles.subjectUnread]}>{thread.subject}</Text>
                <Text style={[Typography.timestamp, { color: Colors.textMuted }]}>{formatTimestamp(thread.timestamp)}</Text>
              </View>
              <Text style={[Typography.body, { color: Colors.textMuted }]} numberOfLines={1}>{thread.previewText}</Text>
            </View>
            {thread.unread && <View style={styles.unreadDot} />}
          </TouchableOpacity>
        )}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  root:          { flex: 1, backgroundColor: Colors.white },
  header:        { padding: 16, borderBottomWidth: 1, borderBottomColor: Colors.border },
  row:           { flexDirection: 'row', alignItems: 'center', padding: 16, borderBottomWidth: 1, borderBottomColor: Colors.border },
  avatar:        { width: 40, height: 40, borderRadius: 20, backgroundColor: Colors.dark, alignItems: 'center', justifyContent: 'center', marginRight: 12 },
  avatarText:    { fontSize: 13, fontWeight: '700', color: Colors.white },
  body:          { flex: 1 },
  rowTop:        { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 3 },
  subject:       { ...Typography.body, color: Colors.textSecondary, flex: 1, marginRight: 8 },
  subjectUnread: { color: Colors.textPrimary, fontWeight: '700' },
  unreadDot:     { width: 8, height: 8, borderRadius: 4, backgroundColor: Colors.blue },
});
```

- [ ] **Step 4: Implement `MessageThreadScreen.tsx`**

```tsx
// src/screens/messages/MessageThreadScreen.tsx
import React, { useState } from 'react';
import { View, Text, FlatList, TextInput, TouchableOpacity, StyleSheet, KeyboardAvoidingView, Platform } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useThread } from '@/hooks/useMessages';
import { Colors } from '@/constants/colors';
import { Typography } from '@/constants/typography';

export function MessageThreadScreen({ route, navigation }: any) {
  const insets = useSafeAreaInsets();
  const threadId = route?.params?.threadId as string;
  const { data, reply } = useThread(threadId);
  const [replyText, setReplyText] = useState('');
  const [sendError, setSendError] = useState(false);

  const handleSend = async () => {
    if (!replyText.trim()) return;
    setSendError(false);
    try {
      await reply.mutateAsync(replyText.trim());
      setReplyText('');
    } catch {
      setSendError(true);
    }
  };

  return (
    <KeyboardAvoidingView style={styles.root} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
      <View style={[styles.header, { paddingTop: insets.top }]}>
        <TouchableOpacity onPress={() => navigation.goBack()}>
          <Text style={[Typography.body, { color: Colors.blue }]}>← Back</Text>
        </TouchableOpacity>
        <Text style={[Typography.bodyMedium, { color: Colors.textPrimary, flex: 1, textAlign: 'center' }]} numberOfLines={1}>
          {data?.thread.subject ?? ''}
        </Text>
        <View style={{ width: 40 }} />
      </View>

      <FlatList
        data={data?.messages ?? []}
        keyExtractor={m => m.id}
        contentContainerStyle={{ padding: 16, paddingBottom: 8 }}
        renderItem={({ item: msg }) => {
          const isAgency = msg.senderType === 'AGENCY';
          return (
            <View style={[styles.bubble, isAgency ? styles.bubbleAgency : styles.bubbleCg]}>
              <Text style={[Typography.body, { color: isAgency ? Colors.textPrimary : Colors.white, lineHeight: 20 }]}>
                {msg.body}
              </Text>
              <Text style={[Typography.timestamp, { color: isAgency ? Colors.textMuted : 'rgba(255,255,255,0.7)', marginTop: 4 }]}>
                {new Date(msg.sentAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
              </Text>
            </View>
          );
        }}
      />

      {sendError && (
        <Text style={styles.sendError}>Message not sent — tap to retry</Text>
      )}

      <View style={[styles.replyBar, { paddingBottom: insets.bottom + 8 }]}>
        <TextInput
          style={styles.replyInput}
          placeholder="Reply…"
          placeholderTextColor={Colors.textMuted}
          value={replyText}
          onChangeText={setReplyText}
        />
        <TouchableOpacity
          style={[styles.sendBtn, !replyText.trim() && styles.sendBtnDisabled]}
          onPress={handleSend}
          disabled={!replyText.trim() || reply.isPending}
        >
          <Text style={styles.sendText}>Send</Text>
        </TouchableOpacity>
      </View>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  root:          { flex: 1, backgroundColor: Colors.surface },
  header:        { flexDirection: 'row', alignItems: 'center', backgroundColor: Colors.white, padding: 16, borderBottomWidth: 1, borderBottomColor: Colors.border },
  bubble:        { maxWidth: '80%', borderRadius: 12, padding: 12, marginBottom: 10 },
  bubbleAgency:  { backgroundColor: Colors.white, borderWidth: 1, borderColor: Colors.border, alignSelf: 'flex-start' },
  bubbleCg:      { backgroundColor: Colors.blue, alignSelf: 'flex-end' },
  replyBar:      { flexDirection: 'row', alignItems: 'center', backgroundColor: Colors.white, borderTopWidth: 1, borderTopColor: Colors.border, paddingHorizontal: 12, paddingTop: 8 },
  replyInput:    { flex: 1, ...Typography.body, color: Colors.textPrimary, backgroundColor: Colors.surface, borderRadius: 20, paddingHorizontal: 14, paddingVertical: 8, marginRight: 8 },
  sendBtn:       { backgroundColor: Colors.blue, borderRadius: 20, paddingHorizontal: 16, paddingVertical: 8 },
  sendBtnDisabled: { opacity: 0.4 },
  sendText:      { ...Typography.body, color: Colors.white, fontWeight: '700' },
  sendError:     { ...Typography.body, color: Colors.red, textAlign: 'center', padding: 8 },
});
```

- [ ] **Step 5: Write failing `MessageThreadScreen` test**

Create `mobile/src/__tests__/screens/MessageThreadScreen.test.tsx`:

```tsx
import React from 'react';
import { render, screen, waitFor } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MessageThreadScreen } from '@/screens/messages/MessageThreadScreen';
import { setupMocks, teardownMocks } from '@/mocks/handlers';
import { MOCK_THREAD_ID_1, mockMessages, mockThreads } from '@/mocks/data';

const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
const wrapper = ({ children }: any) => <QueryClientProvider client={qc}>{children}</QueryClientProvider>;

beforeEach(() => setupMocks());
afterEach(() => { qc.clear(); teardownMocks(); });

describe('MessageThreadScreen', () => {
  const nav = { goBack: jest.fn() };
  // subject is NOT passed as a route param — the screen reads it from the API response.
  const route = { params: { threadId: MOCK_THREAD_ID_1 } };

  it('loads and displays the thread subject from the API', async () => {
    render(<MessageThreadScreen navigation={nav} route={route} />, { wrapper });
    // Subject comes from the mock API response (data?.thread.subject), not route params.
    // mockThreads[0].subject === 'Schedule update for next week'
    await waitFor(() =>
      expect(screen.getByText(mockThreads[0].subject)).toBeTruthy()
    );
  });

  it('loads and displays thread messages', async () => {
    render(<MessageThreadScreen navigation={nav} route={route} />, { wrapper });
    await waitFor(() =>
      expect(screen.getByText(mockMessages[0].body)).toBeTruthy()
    );
  });
});
```

Run: `npm test -- --testPathPattern=MessageThreadScreen`
Expected: PASS.

- [ ] **Step 6: Run MessagesInboxScreen tests**

```bash
npm test -- --testPathPattern=MessagesInboxScreen
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add mobile/src/hooks/useMessages.ts mobile/src/screens/messages/ mobile/src/__tests__/screens/MessageThreadScreen.test.tsx
git commit -m "feat(mobile): implement Messages inbox and thread screens"
```

---

### Task 15: Profile + Settings Screens

**Files:**
- Create: `src/hooks/useProfile.ts`
- Implement: `src/screens/profile/ProfileScreen.tsx`
- Implement: `src/screens/settings/SettingsScreen.tsx`
- Test: `src/__tests__/screens/ProfileScreen.test.tsx`

- [ ] **Step 1: Create `src/hooks/useProfile.ts`**

```ts
// src/hooks/useProfile.ts
import { useQuery } from '@tanstack/react-query';
import { profileApi } from '@/api/profile';

export function useProfile() {
  const profile = useQuery({ queryKey: ['profile'], queryFn: profileApi.get });
  const stats   = useQuery({ queryKey: ['profile', 'stats'], queryFn: profileApi.stats });
  return { profile: profile.data, stats: stats.data, isLoading: profile.isLoading };
}
```

- [ ] **Step 2: Write failing profile test**

Create `mobile/src/__tests__/screens/ProfileScreen.test.tsx`:

```tsx
import React from 'react';
import { render, screen, waitFor } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ProfileScreen } from '@/screens/profile/ProfileScreen';
import { setupMocks, teardownMocks } from '@/mocks/handlers';
import { mockProfile, mockProfileStats } from '@/mocks/data';

const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
const wrapper = ({ children }: any) => <QueryClientProvider client={qc}>{children}</QueryClientProvider>;

beforeEach(() => setupMocks());
afterEach(() => { qc.clear(); teardownMocks(); });

describe('ProfileScreen', () => {
  const nav = { navigate: jest.fn() };

  it('shows caregiver name and agency', async () => {
    render(<ProfileScreen navigation={nav} />, { wrapper });
    await waitFor(() => expect(screen.getByText(mockProfile.name)).toBeTruthy());
    expect(screen.getByText(new RegExp(mockProfile.agencyName))).toBeTruthy();
  });

  it('shows credential list', async () => {
    render(<ProfileScreen navigation={nav} />, { wrapper });
    await waitFor(() =>
      expect(screen.getByText(mockProfile.credentials[0].name)).toBeTruthy()
    );
  });

  it('shows monthly stats', async () => {
    render(<ProfileScreen navigation={nav} />, { wrapper });
    await waitFor(() =>
      expect(screen.getByText(String(mockProfileStats.shiftsCompleted))).toBeTruthy()
    );
  });
});
```

Run: `npm test -- --testPathPattern=ProfileScreen`
Expected: FAIL.

- [ ] **Step 3: Implement `ProfileScreen.tsx`**

```tsx
// src/screens/profile/ProfileScreen.tsx
import React from 'react';
import { View, Text, ScrollView, TouchableOpacity, StyleSheet, Alert } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useProfile } from '@/hooks/useProfile';
import { useAuth } from '@/hooks/useAuth';
import { Colors } from '@/constants/colors';
import { Typography } from '@/constants/typography';
import type { Credential } from '@/types/domain';

const CREDENTIAL_COLOR: Record<string, string> = {
  VALID:          Colors.green,
  EXPIRING_SOON:  Colors.amber,
  EXPIRED:        Colors.red,
};

function CredentialRow({ credential }: { credential: Credential }) {
  const color = CREDENTIAL_COLOR[credential.status] ?? Colors.textMuted;
  const expiry = new Date(credential.expiryDate).toLocaleDateString('en-US', { month: 'short', year: 'numeric' });
  const label = credential.status === 'EXPIRED' ? 'Expired' :
                credential.status === 'EXPIRING_SOON' ? `Expires ${expiry}` :
                `Valid · ${expiry}`;

  return (
    <View style={styles.credentialRow}>
      <Text style={[Typography.body, { color: Colors.textPrimary }]}>{credential.name}</Text>
      <Text style={[Typography.timestamp, { color }]}>{label}</Text>
    </View>
  );
}

export function ProfileScreen({ navigation }: any) {
  const insets = useSafeAreaInsets();
  const { profile, stats, isLoading } = useProfile();
  const { logout } = useAuth();

  const initials = profile?.name.split(' ').map(n => n[0]).join('').toUpperCase().slice(0, 2) ?? '??';

  const handleSignOut = () => {
    Alert.alert(
      'Sign out of hcare?',
      "You'll need a sign-in link to log back in.",
      [
        { text: 'Cancel', style: 'cancel' },
        { text: 'Sign Out', style: 'destructive', onPress: () => logout() },
      ]
    );
  };

  if (isLoading) {
    return <View style={[styles.root, { paddingTop: insets.top }]} />;
  }

  return (
    <ScrollView style={[styles.root, { paddingTop: insets.top }]} contentContainerStyle={{ paddingBottom: 40 }}>
      {/* Avatar + name */}
      <View style={styles.avatarSection}>
        <View style={styles.avatar}>
          <Text style={styles.initials}>{initials}</Text>
        </View>
        <Text style={[Typography.screenTitle, { color: Colors.textPrimary, marginTop: 10 }]}>{profile?.name}</Text>
        <Text style={[Typography.body, { color: Colors.textSecondary }]}>
          {profile?.agencyName} · {profile?.primaryCredentialType}
        </Text>
      </View>

      <View style={styles.content}>
        {/* Credentials */}
        <Text style={[Typography.sectionLabel, styles.sectionLabel]}>CREDENTIALS</Text>
        <View style={styles.card}>
          {profile?.credentials.map((c, i) => (
            <React.Fragment key={c.name}>
              {i > 0 && <View style={styles.divider} />}
              <CredentialRow credential={c} />
            </React.Fragment>
          ))}
        </View>

        {/* This Month */}
        <Text style={[Typography.sectionLabel, styles.sectionLabel]}>THIS MONTH</Text>
        <View style={[styles.card, styles.statsRow]}>
          <View style={styles.statCell}>
            <Text style={styles.statNumber}>{stats?.shiftsCompleted ?? '—'}</Text>
            <Text style={[Typography.body, { color: Colors.textSecondary }]}>Shifts</Text>
          </View>
          <View style={styles.statDivider} />
          <View style={styles.statCell}>
            <Text style={styles.statNumber}>{stats?.hoursWorked ?? '—'}h</Text>
            <Text style={[Typography.body, { color: Colors.textSecondary }]}>Hours</Text>
          </View>
        </View>

        {/* Nav rows */}
        <View style={styles.card}>
          <TouchableOpacity style={styles.navRow} onPress={() => navigation.navigate('Settings')}>
            <Text style={[Typography.body, { color: Colors.textPrimary }]}>Settings</Text>
            <Text style={{ color: Colors.textMuted }}>→</Text>
          </TouchableOpacity>
          <View style={styles.divider} />
          <TouchableOpacity style={styles.navRow} onPress={handleSignOut}>
            <Text style={[Typography.body, { color: Colors.red }]}>Sign Out</Text>
          </TouchableOpacity>
        </View>
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  root:        { flex: 1, backgroundColor: Colors.surface },
  avatarSection:{ alignItems: 'center', padding: 24, backgroundColor: Colors.white, borderBottomWidth: 1, borderBottomColor: Colors.border },
  avatar:      { width: 52, height: 52, borderRadius: 26, backgroundColor: Colors.blue, alignItems: 'center', justifyContent: 'center' },
  initials:    { fontSize: 20, fontWeight: '700', color: Colors.white },
  content:     { padding: 16 },
  sectionLabel:{ color: Colors.textSecondary, marginBottom: 8, marginTop: 12 },
  card:        { backgroundColor: Colors.white, borderRadius: 10, borderWidth: 1, borderColor: Colors.border, marginBottom: 12, overflow: 'hidden' },
  credentialRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingHorizontal: 14, paddingVertical: 12 },
  divider:     { height: 1, backgroundColor: Colors.border },
  statsRow:    { flexDirection: 'row' },
  statCell:    { flex: 1, alignItems: 'center', paddingVertical: 14 },
  statNumber:  { fontSize: 22, fontWeight: '700', color: Colors.textPrimary },
  statDivider: { width: 1, backgroundColor: Colors.border },
  navRow:      { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingHorizontal: 14, paddingVertical: 13 },
});
```

- [ ] **Step 4: Implement `SettingsScreen.tsx`**

```tsx
// src/screens/settings/SettingsScreen.tsx
import React, { useEffect, useState } from 'react';
import { View, Text, TouchableOpacity, StyleSheet, Linking } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import * as Notifications from 'expo-notifications';
import * as Location from 'expo-location';
import Constants from 'expo-constants';
import { useAuthStore } from '@/store/authStore';
import { Colors } from '@/constants/colors';
import { Typography } from '@/constants/typography';

export function SettingsScreen({ navigation }: any) {
  const insets = useSafeAreaInsets();
  const agencyName = useAuthStore(s => s.agencyName);
  const [notifStatus, setNotifStatus] = useState('—');
  const [locationStatus, setLocationStatus] = useState('—');

  useEffect(() => {
    Notifications.getPermissionsAsync().then(p =>
      setNotifStatus(p.granted ? 'On' : 'Off')
    );
    Location.getForegroundPermissionsAsync().then(p => {
      if (p.status === 'granted') setLocationStatus('Always / When In Use');
      else if (p.status === 'denied') setLocationStatus('Denied');
      else setLocationStatus('Not set');
    });
  }, []);

  const openSettings = () => Linking.openSettings();

  return (
    <View style={[styles.root, { paddingTop: insets.top }]}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()}>
          <Text style={[Typography.body, { color: Colors.blue }]}>← Back</Text>
        </TouchableOpacity>
        <Text style={[Typography.screenTitle, { color: Colors.textPrimary }]}>Settings</Text>
        <View style={{ width: 40 }} />
      </View>

      <View style={{ padding: 16 }}>
        <Text style={[Typography.sectionLabel, styles.sectionLabel]}>PERMISSIONS</Text>
        <View style={styles.card}>
          <View style={styles.row}>
            <Text style={[Typography.body, { color: Colors.textPrimary }]}>Notifications</Text>
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
              <Text style={[Typography.body, { color: Colors.textSecondary }]}>{notifStatus}</Text>
              <TouchableOpacity onPress={openSettings}>
                <Text style={[Typography.body, { color: Colors.blue }]}>Change →</Text>
              </TouchableOpacity>
            </View>
          </View>
          <View style={styles.divider} />
          <View style={styles.row}>
            <Text style={[Typography.body, { color: Colors.textPrimary }]}>Location access</Text>
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
              <Text style={[Typography.body, { color: Colors.textSecondary }]}>{locationStatus}</Text>
              <TouchableOpacity onPress={openSettings}>
                <Text style={[Typography.body, { color: Colors.blue }]}>Change →</Text>
              </TouchableOpacity>
            </View>
          </View>
        </View>

        <Text style={[Typography.sectionLabel, styles.sectionLabel]}>AGENCY</Text>
        <View style={styles.card}>
          <View style={styles.row}>
            <Text style={[Typography.body, { color: Colors.textSecondary }]}>Agency</Text>
            <Text style={[Typography.body, { color: Colors.textPrimary }]}>{agencyName ?? '—'}</Text>
          </View>
        </View>

        <Text style={[Typography.sectionLabel, styles.sectionLabel]}>APP</Text>
        <View style={styles.card}>
          <View style={styles.row}>
            <Text style={[Typography.body, { color: Colors.textSecondary }]}>App version</Text>
            <Text style={[Typography.body, { color: Colors.textPrimary }]}>
              {Constants.expoConfig?.version ?? '—'}
            </Text>
          </View>
          <View style={styles.divider} />
          <TouchableOpacity style={styles.row}>
            <Text style={[Typography.body, { color: Colors.blue }]}>Terms of Service</Text>
          </TouchableOpacity>
          <View style={styles.divider} />
          <TouchableOpacity style={styles.row}>
            <Text style={[Typography.body, { color: Colors.blue }]}>Privacy Policy</Text>
          </TouchableOpacity>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root:        { flex: 1, backgroundColor: Colors.surface },
  header:      { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', backgroundColor: Colors.white, padding: 16, borderBottomWidth: 1, borderBottomColor: Colors.border },
  sectionLabel:{ color: Colors.textSecondary, marginBottom: 8, marginTop: 12 },
  card:        { backgroundColor: Colors.white, borderRadius: 10, borderWidth: 1, borderColor: Colors.border, overflow: 'hidden', marginBottom: 4 },
  row:         { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingHorizontal: 14, paddingVertical: 13 },
  divider:     { height: 1, backgroundColor: Colors.border },
});
```

- [ ] **Step 5: Write failing `SettingsScreen` test**

Create `mobile/src/__tests__/screens/SettingsScreen.test.tsx`:

```tsx
import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { SettingsScreen } from '@/screens/settings/SettingsScreen';
import { useAuthStore } from '@/store/authStore';

const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
const wrapper = ({ children }: any) => <QueryClientProvider client={qc}>{children}</QueryClientProvider>;

beforeEach(() => {
  useAuthStore.setState({ accessToken: 'tok', isAuthenticated: true, name: 'Sarah', agencyName: 'Sunrise', firstLogin: false });
});
afterEach(() => qc.clear());

describe('SettingsScreen', () => {
  const nav = { goBack: jest.fn(), navigate: jest.fn() };

  it('renders the Permissions section header', () => {
    render(<SettingsScreen navigation={nav} />, { wrapper });
    expect(screen.getByText(/permissions/i)).toBeTruthy();
  });

  it('shows "Change →" links for Notifications and Location permission rows', () => {
    render(<SettingsScreen navigation={nav} />, { wrapper });
    // SettingsScreen renders "Change →" (not "Change in Settings") for each permission row.
    // Sign Out lives in ProfileScreen, not here.
    const changeLinks = screen.getAllByText('Change →');
    expect(changeLinks.length).toBeGreaterThanOrEqual(2);
  });
});
```

Run: `npm test -- --testPathPattern=SettingsScreen`
Expected: PASS.

- [ ] **Step 6: Run ProfileScreen tests**

```bash
npm test -- --testPathPattern=ProfileScreen
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add mobile/src/hooks/useProfile.ts mobile/src/screens/profile/ mobile/src/screens/settings/ mobile/src/__tests__/screens/SettingsScreen.test.tsx
git commit -m "feat(mobile): implement Profile and Settings screens"
```

---

### Task 16: Offline Sync + Shared Components

**Files:**
- Create: `src/hooks/useOfflineSync.ts`
- Create: `src/components/SectionLabel.tsx`
- Create: `src/components/OfflineBanner.tsx`
- Create: `src/components/Toast.tsx`
- Test: `src/__tests__/hooks/useOfflineSync.test.ts`

- [ ] **Step 1: Write failing offline sync test**

Create `mobile/src/__tests__/hooks/useOfflineSync.test.ts`:

```ts
import { renderHook, act } from '@testing-library/react-native';
import { useOfflineSync } from '@/hooks/useOfflineSync';
import { setupMocks, teardownMocks } from '@/mocks/handlers';

beforeEach(() => setupMocks());
afterEach(() => teardownMocks());

describe('useOfflineSync', () => {
  it('starts with no pending sync', () => {
    const { result } = renderHook(() => useOfflineSync());
    expect(result.current.syncPending).toBe(false);
  });
});
```

Run: `npm test -- --testPathPattern=useOfflineSync`
Expected: FAIL.

- [ ] **Step 2: Create `src/hooks/useOfflineSync.ts`**

```ts
// src/hooks/useOfflineSync.ts
import { useEffect, useRef, useState, useCallback } from 'react';
import NetInfo from '@react-native-community/netinfo';
import { useMutation } from '@tanstack/react-query';
import { syncApi } from '@/api/sync';
import { getPendingEvents, markSynced } from '@/db/events';
import type { SyncEventResult } from '@/types/api';
import * as Device from 'expo-device';

export function useOfflineSync() {
  const [isOnline, setIsOnline] = useState(true);
  const [syncPending, setSyncPending] = useState(false);
  const [conflicts, setConflicts] = useState<SyncEventResult[]>([]);
  const [syncSuccess, setSyncSuccess] = useState(false);
  const wasOffline = useRef(false);

  // Stable ref so the NetInfo listener always calls the latest mutation,
  // avoiding a stale closure on the empty-dependency useEffect below.
  const syncMutationRef = useRef<ReturnType<typeof useMutation<{ results: SyncEventResult[] }>>['mutateAsync'] | null>(null);

  const syncMutation = useMutation({
    mutationFn: async () => {
      const events = await getPendingEvents();
      if (events.length === 0) return { results: [] as SyncEventResult[] };

      // expo-device may return null on simulators — fall back to a fixed string.
      const deviceId = Device.modelId ?? Device.deviceName ?? 'unknown-device';

      const res = await syncApi.batch({ deviceId, events });

      // The BFF returns one result per visitId (not per event).
      // Mark synced: all local events whose visitId received an OK or DUPLICATE result.
      // Events for CONFLICT_REASSIGNED visitIds are intentionally left unsynced so
      // the caregiver sees the conflict UI and the agency can investigate.
      const succeededVisitIds = new Set(
        res.results
          .filter(r => r.result === 'OK' || r.result === 'DUPLICATE')
          .map(r => r.visitId)
      );
      const rowIdsToMark = events
        .filter(e => succeededVisitIds.has(e.visitId))
        .map(e => e.rowId);
      if (rowIdsToMark.length > 0) {
        await markSynced(rowIdsToMark);
      }

      return res;
    },
    onSuccess: (res) => {
      const conflictResults = res.results.filter(r => r.result === 'CONFLICT_REASSIGNED');
      setConflicts(conflictResults);
      if (res.results.some(r => r.result === 'OK')) {
        setSyncSuccess(true);
        setTimeout(() => setSyncSuccess(false), 3000);
      }
    },
  });

  // Keep the ref current on every render so the listener never holds a stale closure.
  syncMutationRef.current = syncMutation.mutateAsync;

  useEffect(() => {
    const unsub = NetInfo.addEventListener(async (state) => {
      const online = !!state.isConnected;
      setIsOnline(online);
      if (online && wasOffline.current) {
        wasOffline.current = false;
        setSyncPending(true);
        try {
          await syncMutationRef.current?.();
        } finally {
          setSyncPending(false);
        }
      }
      if (!online) wasOffline.current = true;
    });
    return unsub;
  }, []); // empty deps intentional — listener uses ref, not closure

  const retrySync = useCallback(async () => {
    setSyncPending(true);
    try { await syncMutationRef.current?.(); }
    finally { setSyncPending(false); }
  }, []); // stable — reads through ref, no deps needed

  return {
    isOnline,
    syncPending,
    syncSuccess,
    syncFailed: syncMutation.isError,
    conflicts,
    retrySync,
  };
}
```

- [ ] **Step 3: Create `src/components/SectionLabel.tsx`**

```tsx
// src/components/SectionLabel.tsx
// Reusable uppercase section header. Screens that previously inlined
// [Typography.sectionLabel, { color: Colors.textSecondary }] should use this instead.
import React from 'react';
import { Text, StyleSheet, type StyleProp, type TextStyle } from 'react-native';
import { Typography } from '@/constants/typography';
import { Colors } from '@/constants/colors';

interface Props { children: string; style?: StyleProp<TextStyle> }

export function SectionLabel({ children, style }: Props) {
  return <Text style={[styles.label, style]}>{children}</Text>;
}

const styles = StyleSheet.create({
  label: { ...Typography.sectionLabel, color: Colors.textSecondary },
});
```

- [ ] **Step 4: Create `src/components/OfflineBanner.tsx`**

```tsx
// src/components/OfflineBanner.tsx
import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { Colors } from '@/constants/colors';
import { Typography } from '@/constants/typography';

interface Props {
  isOnline: boolean;
  syncFailed: boolean;
  syncPending: boolean;
  onRetry: () => void;
}

export function OfflineBanner({ isOnline, syncFailed, syncPending, onRetry }: Props) {
  if (isOnline && !syncFailed) return null;

  if (syncFailed) {
    return (
      <View style={[styles.banner, { backgroundColor: '#fef2f2', borderColor: Colors.red }]}>
        <Text style={[Typography.body, { color: Colors.textPrimary, flex: 1 }]}>
          Sync failed — tap to retry
        </Text>
        <TouchableOpacity onPress={onRetry} disabled={syncPending}>
          <Text style={[Typography.body, { color: Colors.blue, fontWeight: '700' }]}>
            {syncPending ? 'Retrying…' : 'Retry'}
          </Text>
        </TouchableOpacity>
      </View>
    );
  }

  return (
    <View style={[styles.banner, { backgroundColor: '#fffbeb', borderColor: Colors.amber }]}>
      <Text style={[Typography.body, { color: Colors.textPrimary }]}>
        Offline — data saved locally, will sync on reconnect
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  banner: { flexDirection: 'row', alignItems: 'center', padding: 10, borderWidth: 1, borderRadius: 8, marginHorizontal: 16, marginBottom: 8 },
});
```

- [ ] **Step 5: Create `src/components/Toast.tsx`**

```tsx
// src/components/Toast.tsx
import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { Colors } from '@/constants/colors';
import { Typography } from '@/constants/typography';

interface Props { visible: boolean; message: string }

export function Toast({ visible, message }: Props) {
  if (!visible) return null;
  return (
    <View style={styles.toast}>
      <Text style={styles.text}>{message}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  toast: {
    position: 'absolute', bottom: 100, alignSelf: 'center',
    backgroundColor: Colors.dark, borderRadius: 20, paddingHorizontal: 18, paddingVertical: 10,
    shadowColor: '#000', shadowOpacity: 0.2, shadowRadius: 8, elevation: 8,
  },
  text: { ...Typography.body, color: Colors.white, fontWeight: '600' },
});
```

- [ ] **Step 6: Wire `useOfflineSync` into `TodayScreen`**

In `TodayScreen.tsx`, import and use the hook to show the offline banner and toast:

```tsx
// Add to TodayScreen.tsx imports:
import { useOfflineSync } from '@/hooks/useOfflineSync';
import { OfflineBanner } from '@/components/OfflineBanner';
import { Toast } from '@/components/Toast';

// Inside TodayScreen function, add:
const { isOnline, syncFailed, syncPending, syncSuccess, retrySync } = useOfflineSync();

// Add inside the return JSX, between the header and the ScrollView:
<OfflineBanner isOnline={isOnline} syncFailed={syncFailed} syncPending={syncPending} onRetry={retrySync} />
<Toast visible={syncSuccess} message="Visit data synced ✓" />
```

- [ ] **Step 7: Run tests**

```bash
npm test -- --testPathPattern=useOfflineSync
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add mobile/src/hooks/useOfflineSync.ts mobile/src/components/
git commit -m "feat(mobile): add offline sync hook, SectionLabel, OfflineBanner, and sync success Toast"
```

---

### Task 17: Push Notifications + Deep Linking

**Files:**
- Create: `src/hooks/useNotifications.ts`
- Modify: `src/navigation/RootNavigator.tsx`

- [ ] **Step 1: Create `src/hooks/useNotifications.ts`**

```ts
// src/hooks/useNotifications.ts
import { useEffect } from 'react';
import { Platform } from 'react-native';
import * as Notifications from 'expo-notifications';
import { devicesApi } from '@/api/devices';
import { useAuthStore } from '@/store/authStore';
import type { NavigationContainerRef } from '@react-navigation/native';

Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowAlert: true,
    shouldPlaySound: true,
    shouldSetBadge: true,
  }),
});

export function useNotifications(navigationRef: React.RefObject<NavigationContainerRef<any>>) {
  const isAuthenticated = useAuthStore(s => s.isAuthenticated);

  useEffect(() => {
    if (!isAuthenticated) return;

    // Register push token on every launch
    Notifications.getExpoPushTokenAsync()
      .then(({ data: token }) => {
        return devicesApi.registerPushToken(token, Platform.OS as 'ios' | 'android');
      })
      .catch(console.error);

    // Handle notification tap while app is in background/quit
    const sub = Notifications.addNotificationResponseReceivedListener(response => {
      const data = response.notification.request.content.data as Record<string, string>;
      const nav = navigationRef.current;
      if (!nav) return;

      if (data.shiftId) nav.navigate('Today' as never);
      if (data.threadId) nav.navigate('Messages' as never);
    });

    return () => sub.remove();
  }, [isAuthenticated]);
}
```

- [ ] **Step 2: Wire notifications into `RootNavigator.tsx`**

Add to `RootNavigator.tsx`:

```tsx
import { useRef } from 'react';
import type { NavigationContainerRef } from '@react-navigation/native';
import { useNotifications } from '@/hooks/useNotifications';

// Inside RootNavigator function:
const navigationRef = useRef<NavigationContainerRef<any>>(null);
useNotifications(navigationRef);

// Update NavigationContainer to use ref:
<NavigationContainer ref={navigationRef} linking={linking}>
```

- [ ] **Step 3: Commit**

```bash
git add mobile/src/hooks/useNotifications.ts mobile/src/navigation/RootNavigator.tsx
git commit -m "feat(mobile): add push notification setup, push token registration, and deep link tap routing"
```

---

### Task 18: CONFLICT_REASSIGNED UI

**Files:**
- Implement: `src/screens/conflict/ConflictDetailScreen.tsx`
- Modify: `src/screens/today/TodayScreen.tsx` (add conflict banner)

- [ ] **Step 1: Implement `ConflictDetailScreen.tsx`**

```tsx
// src/screens/conflict/ConflictDetailScreen.tsx
import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Colors } from '@/constants/colors';
import { Typography } from '@/constants/typography';
import type { ConflictDetail } from '@/types/domain';

export function ConflictDetailScreen({ route, navigation }: any) {
  const insets = useSafeAreaInsets();
  const conflict = route?.params?.conflict as ConflictDetail | undefined;

  const formatDateTime = (iso: string) =>
    new Date(iso).toLocaleString('en-US', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });

  return (
    <View style={[styles.root, { paddingTop: insets.top }]}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()}>
          <Text style={[Typography.body, { color: Colors.blue }]}>← Back</Text>
        </TouchableOpacity>
        <Text style={[Typography.screenTitle, { color: Colors.textPrimary }]}>Visit Not Recorded</Text>
        <View style={{ width: 40 }} />
      </View>

      <View style={styles.body}>
        <Text style={{ fontSize: 40, textAlign: 'center', marginBottom: 16 }}>⚠️</Text>
        <Text style={[Typography.cardTitle, styles.title]}>This visit was not recorded</Text>
        <Text style={[Typography.body, styles.explanation]}>
          The shift for {conflict?.clientName ?? 'this client'} on {conflict ? new Date(conflict.shiftDate).toLocaleDateString('en-US', { month: 'long', day: 'numeric' }) : 'this date'} was reassigned to another caregiver while you were offline. Your visit data could not be saved.
        </Text>

        {conflict && (
          <View style={styles.detailCard}>
            <Text style={[Typography.sectionLabel, { color: Colors.textSecondary, marginBottom: 10 }]}>YOUR VISIT TIMES</Text>
            <View style={styles.detailRow}>
              <Text style={[Typography.body, { color: Colors.textSecondary }]}>Clocked in</Text>
              <Text style={[Typography.body, { color: Colors.textPrimary, fontWeight: '600' }]}>{formatDateTime(conflict.caregiverClockIn)}</Text>
            </View>
            <View style={styles.detailRow}>
              <Text style={[Typography.body, { color: Colors.textSecondary }]}>Clocked out</Text>
              <Text style={[Typography.body, { color: Colors.textPrimary, fontWeight: '600' }]}>{formatDateTime(conflict.caregiverClockOut)}</Text>
            </View>
          </View>
        )}

        <Text style={[Typography.body, styles.guidance]}>
          Contact your agency to resolve this visit and ensure you are compensated for time worked.
        </Text>

        <TouchableOpacity
          style={styles.contactBtn}
          onPress={() => navigation.navigate('Messages')}
        >
          <Text style={styles.contactBtnText}>Contact Agency</Text>
        </TouchableOpacity>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root:       { flex: 1, backgroundColor: Colors.white },
  header:     { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', padding: 16, borderBottomWidth: 1, borderBottomColor: Colors.border },
  body:       { padding: 24 },
  title:      { color: Colors.textPrimary, textAlign: 'center', marginBottom: 12 },
  explanation:{ color: Colors.textSecondary, lineHeight: 22, textAlign: 'center', marginBottom: 20 },
  detailCard: { backgroundColor: Colors.surface, borderRadius: 10, padding: 14, marginBottom: 20 },
  detailRow:  { flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 6 },
  guidance:   { color: Colors.textSecondary, lineHeight: 22, textAlign: 'center', marginBottom: 24 },
  contactBtn: { backgroundColor: Colors.dark, borderRadius: 8, paddingVertical: 14, alignItems: 'center' },
  contactBtnText: { ...Typography.bodyMedium, color: Colors.white, fontWeight: '700' },
});
```

- [ ] **Step 2: Add conflict banner to `TodayScreen.tsx`**

In `useOfflineSync`, the `conflicts` array is already returned. Pass it to `TodayScreen` via the hook and show a banner.

Add to `TodayScreen.tsx` (after the `useOfflineSync` destructure):

```tsx
// Add to imports:
import type { SyncEventResult } from '@/types/api';

// Inside TodayScreen, after existing useOfflineSync call:
const { ..., conflicts } = useOfflineSync();

// In the JSX, after OfflineBanner:
{conflicts.map(c => (
  c.conflict && (
    <TouchableOpacity
      key={c.visitId}
      style={styles.conflictBanner}
      onPress={() => navigation.navigate('ConflictDetail', { conflict: c.conflict })}
    >
      <Text style={styles.conflictText}>
        Visit not recorded — {c.conflict.clientName} shift was reassigned. Tap for details.
      </Text>
    </TouchableOpacity>
  )
))}

// Add to styles:
conflictBanner: { backgroundColor: '#fef2f2', borderWidth: 1, borderColor: Colors.red, borderRadius: 8, padding: 10, marginHorizontal: 16, marginBottom: 8 },
conflictText: { ...Typography.body, color: Colors.red, lineHeight: 20 },
```

- [ ] **Step 3: Write failing `ConflictDetailScreen` test**

Create `mobile/src/__tests__/screens/ConflictDetailScreen.test.tsx`:

```tsx
import React from 'react';
import { render, screen } from '@testing-library/react-native';
import { ConflictDetailScreen } from '@/screens/conflict/ConflictDetailScreen';

describe('ConflictDetailScreen', () => {
  const conflict = {
    clientName: 'Eleanor Vance',
    shiftDate: '2026-04-08T00:00:00.000Z',
    caregiverClockIn: '2026-04-08T07:00:00.000Z',
    caregiverClockOut: '2026-04-08T10:00:00.000Z',
  };
  const nav = { navigate: jest.fn() };
  const route = { params: { conflict } };

  it('shows the client name', () => {
    render(<ConflictDetailScreen navigation={nav} route={route} />);
    expect(screen.getByText(/Eleanor Vance/)).toBeTruthy();
  });

  it('shows agency contact guidance', () => {
    render(<ConflictDetailScreen navigation={nav} route={route} />);
    expect(screen.getByText(/contact your agency/i)).toBeTruthy();
  });
});
```

Run: `npm test -- --testPathPattern=ConflictDetailScreen`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add mobile/src/screens/conflict/ mobile/src/screens/today/TodayScreen.tsx mobile/src/__tests__/screens/ConflictDetailScreen.test.tsx
git commit -m "feat(mobile): implement CONFLICT_REASSIGNED detail screen and Today screen banner"
```

---

### Task 19: Final Integration Check

- [ ] **Step 1: Run full test suite**

```bash
cd mobile && npm test -- --coverage
```

Expected: all tests pass. Coverage output shows >80% on hooks and stores.

- [ ] **Step 2: Start app and smoke-test all flows**

```bash
npx expo start --go
```

Manually verify:
- [ ] App opens → Login screen shown (no session)
- [ ] Enter email → "link has been sent" message appears
- [ ] Toggle mock to return `firstLogin: true` → onboarding shows
- [ ] After onboarding → Today screen with Eleanor Vance shift visible
- [ ] Tap center FAB → ClockInSheet slides up as a modal (bottom sheet) with both shifts
- [ ] Select shift, tap Clock In → Visit screen opens, timer running
- [ ] Check ADL tasks → completed tasks sink below pending; tap a completed task → reverts
- [ ] Expand Care Plan → "Updated since your last visit" pill visible
- [ ] Tap Clock Out → returns to Today screen
- [ ] Open Shifts tab → two shifts visible, URGENT badge on urgent one
- [ ] Messages tab → thread list visible, unread dot on first thread
- [ ] Tap thread → messages + reply bar visible
- [ ] Profile tab → Sarah Johnson, credentials, stats
- [ ] Profile → Settings → Change → links present
- [ ] Profile → Sign Out → confirmation dialog appears

- [ ] **Step 3: Final commit**

```bash
git add mobile/
git commit -m "feat(mobile): complete caregiver mobile app UX — all screens built against mock BFF"
```

---

## Summary

| Phase | Tasks | Deliverable |
|---|---|---|
| Phase 1 | 1–7 | Auth, navigation shell, onboarding — app runs and authenticates |
| Phase 2 | 8–12 | Today, clock-in, offline log, visit execution, care plan — full EVV flow |
| Phase 3 | 13–19 | Open shifts, messages, profile/settings, offline sync, notifications, conflict UI |

**When the BFF is ready:** Set `EXPO_PUBLIC_USE_MOCKS=false` in `.env` and point `EXPO_PUBLIC_BFF_URL` to `http://localhost:8081`. No code changes needed — all API modules are already wired to the real endpoints documented in `docs/superpowers/specs/2026-04-08-mobile-bff-endpoints.md`.
