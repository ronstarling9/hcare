// src/hooks/useProfile.ts
import { useQuery } from '@tanstack/react-query';
import { profileApi } from '@/api/profile';

export function useProfile() {
  const profile = useQuery({ queryKey: ['profile'], queryFn: profileApi.get });
  const stats   = useQuery({ queryKey: ['profile', 'stats'], queryFn: profileApi.stats });
  return { profile: profile.data, stats: stats.data, isLoading: profile.isLoading };
}
