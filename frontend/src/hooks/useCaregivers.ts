import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  listCaregivers,
  getCaregiver,
  listCredentials,
  listBackgroundChecks,
  listShiftHistory,
  verifyCredential,
  createCaregiver,
} from '../api/caregivers'
import type { CaregiverResponse } from '../types/api'
import { useMemo } from 'react'

/**
 * Fetches a paginated list of caregivers for the caregivers screen.
 */
export function useCaregivers(page = 0, size = 20) {
  const query = useQuery({
    queryKey: ['caregivers', page, size],
    queryFn: () => listCaregivers(page, size),
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
    totalPages: query.data?.totalPages ?? 0,
    totalElements: query.data?.totalElements ?? 0,
  }
}

/**
 * Fetches all caregivers with a large page size for in-memory lookup maps.
 * Prefer this over useCaregivers() when you need a Map<id, caregiver>.
 */
export function useAllCaregivers() {
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
    staleTime: 60_000,
  })
}

export function useCaregiverCredentials(caregiverId: string | null) {
  return useQuery({
    queryKey: ['caregiver-credentials', caregiverId],
    queryFn: () => listCredentials(caregiverId!, 0, 50),
    enabled: Boolean(caregiverId),
    staleTime: 60_000,
  })
}

export function useCaregiverBackgroundChecks(caregiverId: string | null) {
  return useQuery({
    queryKey: ['caregiver-background-checks', caregiverId],
    queryFn: () => listBackgroundChecks(caregiverId!, 0, 20),
    enabled: Boolean(caregiverId),
    staleTime: 60_000,
  })
}

export function useCaregiverShiftHistory(caregiverId: string | null, page = 0) {
  return useQuery({
    queryKey: ['caregiver-shifts', caregiverId, page],
    queryFn: () => listShiftHistory(caregiverId!, page, 20),
    enabled: Boolean(caregiverId),
    staleTime: 60_000,
  })
}

export function useVerifyCredential(caregiverId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (credentialId: string) => verifyCredential(caregiverId, credentialId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['caregiver-credentials', caregiverId] })
    },
  })
}

export function useCreateCaregiver() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: createCaregiver,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['caregivers'] })
    },
  })
}
