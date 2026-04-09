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
