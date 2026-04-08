import { apiClient } from './client'
import type { EvvHistoryRow, PageResponse } from '../types/api'

export async function listEvvHistory(
  start: string,
  end: string,
  page = 0,
  size = 50,
): Promise<PageResponse<EvvHistoryRow>> {
  const response = await apiClient.get<PageResponse<EvvHistoryRow>>('/evv/history', {
    params: { start, end, page, size },
  })
  return response.data
}
