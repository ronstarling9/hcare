import { apiClient } from './client'
import type {
  CaregiverResponse,
  CredentialResponse,
  BackgroundCheckResponse,
  ShiftSummaryResponse,
  PageResponse,
} from '../types/api'

export async function listCaregivers(
  page = 0,
  size = 20,
): Promise<PageResponse<CaregiverResponse>> {
  const response = await apiClient.get<PageResponse<CaregiverResponse>>('/caregivers', {
    params: { page, size, sort: 'lastName' },
  })
  return response.data
}

export async function getCaregiver(id: string): Promise<CaregiverResponse> {
  const response = await apiClient.get<CaregiverResponse>(`/caregivers/${id}`)
  return response.data
}

export async function listCredentials(
  caregiverId: string,
  page = 0,
  size = 50,
): Promise<PageResponse<CredentialResponse>> {
  const response = await apiClient.get<PageResponse<CredentialResponse>>(
    `/caregivers/${caregiverId}/credentials`,
    { params: { page, size } },
  )
  return response.data
}

export async function listBackgroundChecks(
  caregiverId: string,
  page = 0,
  size = 20,
): Promise<PageResponse<BackgroundCheckResponse>> {
  const response = await apiClient.get<PageResponse<BackgroundCheckResponse>>(
    `/caregivers/${caregiverId}/background-checks`,
    { params: { page, size } },
  )
  return response.data
}

export async function listAvailability(caregiverId: string): Promise<unknown[]> {
  const response = await apiClient.get<unknown[]>(
    `/caregivers/${caregiverId}/availability`,
  )
  return response.data
}

export async function listShiftHistory(
  caregiverId: string,
  page = 0,
  size = 20,
): Promise<PageResponse<ShiftSummaryResponse>> {
  const response = await apiClient.get<PageResponse<ShiftSummaryResponse>>(
    `/caregivers/${caregiverId}/shifts`,
    { params: { page, size, sort: 'scheduledStart,desc' } },
  )
  return response.data
}
