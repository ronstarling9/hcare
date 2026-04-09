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
