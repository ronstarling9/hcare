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
