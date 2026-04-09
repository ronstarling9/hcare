// src/hooks/useToday.ts
import { useQuery } from '@tanstack/react-query';
import { visitsApi } from '@/api/visits';
import type { Shift } from '@/types/domain';

export function useToday() {
  const today = useQuery({
    queryKey: ['visits', 'today'],
    queryFn: visitsApi.today,
    select: (d) => d.shifts,
  });

  const week = useQuery({
    queryKey: ['visits', 'week'],
    queryFn: visitsApi.week,
    select: (d) => d.shifts,
  });

  const upcoming = today.data?.filter(s => s.status === 'UPCOMING') ?? [];
  const completed = today.data?.filter(s => s.status === 'COMPLETED') ?? [];
  const cancelled = today.data?.filter(s => s.status === 'CANCELLED') ?? [];
  const inProgress = today.data?.find(s => s.status === 'IN_PROGRESS') ?? null;

  // Soonest upcoming shift gets the NEXT badge
  const nextShiftId = upcoming[0]?.id ?? null;

  return {
    todayShifts: today.data ?? [],
    weekShifts:  week.data ?? [],
    upcoming,
    completed,
    cancelled,
    inProgress,
    nextShiftId,
    isLoading: today.isLoading,
    refetch:   today.refetch,
  };
}
