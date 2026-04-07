import { useQuery } from '@tanstack/react-query'
import { getDashboardToday } from '../api/dashboard'

/**
 * Fetches today's dashboard data.
 * staleTime: 60s — dashboard stats refresh automatically every minute.
 * refetchInterval: 60_000 — polls every 60s while the tab is in focus.
 */
export function useDashboard() {
  return useQuery({
    queryKey: ['dashboard', 'today'],
    queryFn: getDashboardToday,
    staleTime: 60_000,
    refetchInterval: 60_000,
  })
}
