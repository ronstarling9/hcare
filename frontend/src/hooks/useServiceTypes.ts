import { useQuery } from '@tanstack/react-query'
import { listServiceTypes } from '../api/serviceTypes'

export function useServiceTypes() {
  const query = useQuery({
    queryKey: ['service-types'],
    queryFn: listServiceTypes,
    staleTime: 300_000, // 5 minutes — service types rarely change
  })

  return {
    ...query,
    serviceTypes: query.data ?? [],
  }
}
