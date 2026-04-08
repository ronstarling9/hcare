import { useQuery } from '@tanstack/react-query'
import { listEvvHistory } from '../api/evv'

export function useEvvHistory(start: string, end: string, page = 0) {
  const query = useQuery({
    queryKey: ['evv-history', start, end, page],
    queryFn: () => listEvvHistory(start, end, page, 50),
    enabled: Boolean(start && end),
    staleTime: 30_000,
  })

  return {
    ...query,
    rows: query.data?.content ?? [],
    totalPages: query.data?.totalPages ?? 0,
    totalElements: query.data?.totalElements ?? 0,
  }
}
