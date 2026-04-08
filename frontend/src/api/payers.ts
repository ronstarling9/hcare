import { apiClient } from './client'
import type { PayerResponse, PageResponse } from '../types/api'

export async function listPayers(page = 0, size = 20): Promise<PageResponse<PayerResponse>> {
  const response = await apiClient.get<PageResponse<PayerResponse>>('/payers', {
    params: { page, size },
  })
  return response.data
}

export async function getPayer(id: string): Promise<PayerResponse> {
  const response = await apiClient.get<PayerResponse>(`/payers/${id}`)
  return response.data
}
