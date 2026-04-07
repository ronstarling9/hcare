import { apiClient } from './client'
import type { DashboardTodayResponse } from '../types/api'

export async function getDashboardToday(): Promise<DashboardTodayResponse> {
  const response = await apiClient.get<DashboardTodayResponse>('/dashboard/today')
  return response.data
}
