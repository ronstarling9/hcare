import { apiClient } from './client';

export const devicesApi = {
  registerPushToken: (token: string, platform: 'ios' | 'android') =>
    apiClient.post('/mobile/devices/push-token', { token, platform }),
};
