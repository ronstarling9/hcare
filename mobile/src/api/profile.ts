import { apiClient } from './client';
import type { CaregiverProfile, ProfileStats } from '@/types/domain';

export const profileApi = {
  get:   () => apiClient.get<CaregiverProfile>('/mobile/profile').then(r => r.data),
  stats: () => apiClient.get<ProfileStats>('/mobile/profile/stats').then(r => r.data),
};
