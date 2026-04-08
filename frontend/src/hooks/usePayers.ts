import { useQuery } from '@tanstack/react-query'
import { listPayers } from '../api/payers'

export function usePayers(page = 0, size = 20) {
  const query = useQuery({
    queryKey: ['payers', page, size],
    queryFn: () => listPayers(page, size),
    staleTime: 120_000,
  })

  return {
    ...query,
    payers: query.data?.content ?? [],
    totalPages: query.data?.totalPages ?? 0,
    totalElements: query.data?.totalElements ?? 0,
  }
}
