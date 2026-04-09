import { apiClient } from './client';
import type { OpenShiftsResponse } from '@/types/api';

export const shiftsApi = {
  open:    () => apiClient.get<OpenShiftsResponse>('/mobile/shifts/open').then(r => r.data),
  accept:  (shiftId: string) => apiClient.post(`/mobile/shifts/${shiftId}/accept`),
  decline: (shiftId: string) => apiClient.post(`/mobile/shifts/${shiftId}/decline`),
};
