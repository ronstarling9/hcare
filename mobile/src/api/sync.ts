import { apiClient } from './client';
import type { SyncRequest, SyncResponse } from '@/types/api';

export const syncApi = {
  batch: (body: SyncRequest) =>
    apiClient.post<SyncResponse>('/sync/visits', body).then(r => r.data),
};
