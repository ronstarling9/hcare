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
