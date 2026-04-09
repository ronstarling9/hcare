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
