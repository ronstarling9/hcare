import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  listShifts,
  getShift,
  createShift,
  assignCaregiver,
  broadcastShift,
  getCandidates,
  clockIn,
  type ClockInRequest,
} from '../api/shifts'
import type { CreateShiftRequest } from '../types/api'

export function useShifts(weekStart: string, weekEnd: string) {
  return useQuery({
    queryKey: ['shifts', weekStart, weekEnd],
    queryFn: () => listShifts(weekStart, weekEnd),
    enabled: Boolean(weekStart && weekEnd),
  })
}

export function useShiftDetail(id: string | null) {
  return useQuery({
    queryKey: ['shift', id],
    queryFn: () => getShift(id!),
    enabled: Boolean(id),
  })
}

export function useCreateShift() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (req: CreateShiftRequest) => createShift(req),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['shifts'] })
    },
  })
}

export function useAssignCaregiver() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ shiftId, caregiverId }: { shiftId: string; caregiverId: string }) =>
      assignCaregiver(shiftId, caregiverId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['shifts'] })
      queryClient.invalidateQueries({ queryKey: ['shift'] })
    },
  })
}

export function useBroadcastShift() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (shiftId: string) => broadcastShift(shiftId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['shifts'] })
    },
  })
}

export function useClockIn() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ shiftId, req }: { shiftId: string; req: ClockInRequest }) =>
      clockIn(shiftId, req),
    onSuccess: (_data, { shiftId }) => {
      queryClient.invalidateQueries({ queryKey: ['shift', shiftId] })
      queryClient.invalidateQueries({ queryKey: ['shifts'] })
    },
  })
}

export function useGetCandidates(shiftId: string | null) {
  return useQuery({
    queryKey: ['candidates', shiftId],
    queryFn: () => getCandidates(shiftId!),
    enabled: Boolean(shiftId),
  })
}
