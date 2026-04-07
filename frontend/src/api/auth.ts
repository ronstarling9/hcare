import { apiClient } from './client'
import type { LoginRequest, LoginResponse } from '../types/api'

export async function loginApi(email: string, password: string): Promise<LoginResponse> {
  const body: LoginRequest = { email, password }
  const response = await apiClient.post<LoginResponse>('/auth/login', body)
  return response.data
}
