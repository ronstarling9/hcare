import { describe, it, expect, afterEach, beforeEach, vi } from 'vitest'
import MockAdapter from 'axios-mock-adapter'
import { apiClient } from './client'
import { loginApi } from './auth'
import type { LoginResponse } from '../types/api'

// Note: authStore is used by the client interceptor; mock it to avoid side effects
vi.mock('../store/authStore', () => ({
  useAuthStore: {
    getState: vi.fn().mockReturnValue({ token: null, logout: vi.fn() }),
  },
}))

describe('loginApi', () => {
  let mockAxios: MockAdapter

  beforeEach(() => {
    mockAxios = new MockAdapter(apiClient)
  })

  afterEach(() => {
    mockAxios.restore()
  })

  it('posts to /auth/login and returns response data on success', async () => {
    const mockResponse: LoginResponse = {
      token: 'jwt-token',
      userId: 'user-1',
      agencyId: 'agency-1',
      role: 'ADMIN',
    }
    mockAxios.onPost('/auth/login', { email: 'admin@test.com', password: 'pass' }).reply(200, mockResponse)

    const result = await loginApi('admin@test.com', 'pass')
    expect(result).toEqual(mockResponse)
  })

  it('rejects with an error on 401', async () => {
    mockAxios.onPost('/auth/login').reply(401)
    await expect(loginApi('admin@test.com', 'wrong')).rejects.toMatchObject({
      response: { status: 401 },
    })
  })
})
