import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { listCaregivers, getCaregiver } from '../api/caregivers'
import type { CaregiverResponse } from '../types/api'

export function useCaregivers() {
  const query = useQuery({
    queryKey: ['caregivers', 'all'],
    queryFn: () => listCaregivers(0, 100),
    staleTime: 60_000,
  })

  const caregiverMap = useMemo<Map<string, CaregiverResponse>>(() => {
    if (!query.data?.content) return new Map()
    return new Map(query.data.content.map((c) => [c.id, c]))
  }, [query.data])

  return {
    ...query,
    caregivers: query.data?.content ?? [],
    caregiverMap,
  }
}

export function useCaregiverDetail(id: string | null) {
  return useQuery({
    queryKey: ['caregiver', id],
    queryFn: () => getCaregiver(id!),
    enabled: Boolean(id),
  })
}
