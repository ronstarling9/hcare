import { apiClient } from './client'
import type { ClientResponse, AuthorizationResponse, PageResponse } from '../types/api'

export async function listClients(page = 0, size = 100): Promise<PageResponse<ClientResponse>> {
  const response = await apiClient.get<PageResponse<ClientResponse>>('/clients', {
    params: { page, size, sort: 'lastName' },
  })
  return response.data
}

export async function getClient(id: string): Promise<ClientResponse> {
  const response = await apiClient.get<ClientResponse>(`/clients/${id}`)
  return response.data
}

export async function listAuthorizations(
  clientId: string,
  page = 0,
  size = 50,
): Promise<PageResponse<AuthorizationResponse>> {
  const response = await apiClient.get<PageResponse<AuthorizationResponse>>(
    `/clients/${clientId}/authorizations`,
    { params: { page, size } },
  )
  return response.data
}
