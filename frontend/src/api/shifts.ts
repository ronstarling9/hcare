import { apiClient } from './client'
import type {
  ShiftSummaryResponse,
  ShiftDetailResponse,
  RankedCaregiverResponse,
  CreateShiftRequest,
  PageResponse,
} from '../types/api'

export interface ClockInRequest {
  locationLat: number
  locationLon: number
  verificationMethod: string
  capturedOffline: boolean
  deviceCapturedAt?: string | null
}

export async function listShifts(
  start: string,
  end: string,
): Promise<PageResponse<ShiftSummaryResponse>> {
  const response = await apiClient.get<PageResponse<ShiftSummaryResponse>>('/shifts', {
    params: { start, end, size: 200, sort: 'scheduledStart' },
  })
  return response.data
}

export async function getShift(id: string): Promise<ShiftDetailResponse> {
  const response = await apiClient.get<ShiftDetailResponse>(`/shifts/${id}`)
  return response.data
}

export async function createShift(req: CreateShiftRequest): Promise<ShiftSummaryResponse> {
  const response = await apiClient.post<ShiftSummaryResponse>('/shifts', req)
  return response.data
}

export async function assignCaregiver(
  shiftId: string,
  caregiverId: string,
): Promise<ShiftSummaryResponse> {
  const response = await apiClient.patch<ShiftSummaryResponse>(`/shifts/${shiftId}/assign`, {
    caregiverId,
  })
  return response.data
}

export async function broadcastShift(shiftId: string): Promise<void> {
  await apiClient.post(`/shifts/${shiftId}/broadcast`)
}

export async function getCandidates(shiftId: string): Promise<RankedCaregiverResponse[]> {
  const response = await apiClient.get<RankedCaregiverResponse[]>(`/shifts/${shiftId}/candidates`)
  return response.data
}

export async function clockIn(
  shiftId: string,
  req: ClockInRequest,
): Promise<ShiftDetailResponse> {
  const response = await apiClient.post<ShiftDetailResponse>(`/shifts/${shiftId}/clock-in`, req)
  return response.data
}
