import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import MockAdapter from 'axios-mock-adapter'
import { apiClient } from './client'
import * as authStoreModule from '../store/authStore'

vi.mock('../store/authStore', () => ({
  useAuthStore: {
    getState: vi.fn(),
  },
}))

describe('apiClient interceptors', () => {
  let mockAxios: MockAdapter

  beforeEach(() => {
    mockAxios = new MockAdapter(apiClient)
    Object.defineProperty(window, 'location', {
      value: { href: '' },
      writable: true,
    })
  })

  afterEach(() => {
    mockAxios.restore()
  })

  describe('request interceptor', () => {
    it('attaches Authorization header when token is present', async () => {
      vi.mocked(authStoreModule.useAuthStore.getState).mockReturnValue({
        token: 'test-token',
        logout: vi.fn(),
      } as any)
      let capturedHeaders: any
      mockAxios.onGet('/test').reply((config) => {
        capturedHeaders = config.headers
        return [200, {}]
      })
      await apiClient.get('/test')
      expect(capturedHeaders.Authorization).toBe('Bearer test-token')
    })

    it('does not attach Authorization header when token is null', async () => {
      vi.mocked(authStoreModule.useAuthStore.getState).mockReturnValue({
        token: null,
        logout: vi.fn(),
      } as any)
      let capturedHeaders: any
      mockAxios.onGet('/test').reply((config) => {
        capturedHeaders = config.headers
        return [200, {}]
      })
      await apiClient.get('/test')
      expect(capturedHeaders.Authorization).toBeUndefined()
    })
  })

  describe('response interceptor', () => {
    it('calls logout and redirects to /login on 401 for non-auth endpoints', async () => {
      const logoutMock = vi.fn()
      vi.mocked(authStoreModule.useAuthStore.getState).mockReturnValue({
        token: 'valid-token',
        logout: logoutMock,
      } as any)
      mockAxios.onGet('/dashboard').reply(401)
      await expect(apiClient.get('/dashboard')).rejects.toMatchObject({
        response: { status: 401 },
      })
      expect(logoutMock).toHaveBeenCalled()
      expect(window.location.href).toBe('/login')
    })

    it('does NOT redirect to /login on 401 for auth endpoints (failed login)', async () => {
      vi.mocked(authStoreModule.useAuthStore.getState).mockReturnValue({
        token: null,
        logout: vi.fn(),
      } as any)
      mockAxios.onPost('/auth/login').reply(401)
      await expect(apiClient.post('/auth/login', {})).rejects.toMatchObject({
        response: { status: 401 },
      })
      expect(window.location.href).toBe('')
    })

    it('does not redirect on non-401 errors', async () => {
      vi.mocked(authStoreModule.useAuthStore.getState).mockReturnValue({
        token: 'valid-token',
        logout: vi.fn(),
      } as any)
      mockAxios.onGet('/test').reply(500)
      await expect(apiClient.get('/test')).rejects.toMatchObject({
        response: { status: 500 },
      })
      expect(window.location.href).toBe('')
    })
  })
})
