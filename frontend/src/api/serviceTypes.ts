import { apiClient } from './client'
import type { ServiceTypeResponse } from '../types/api'

export async function listServiceTypes(): Promise<ServiceTypeResponse[]> {
  const response = await apiClient.get<ServiceTypeResponse[]>('/service-types')
  return response.data
}
