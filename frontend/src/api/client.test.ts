import { describe, it, expect, vi, beforeEach } from 'vitest'
import { AxiosHeaders } from 'axios'
import { apiClient } from './client'
import * as authStoreModule from '../store/authStore'

vi.mock('../store/authStore', () => ({
  useAuthStore: {
    getState: vi.fn(),
  },
}))

describe('apiClient interceptors', () => {
  beforeEach(() => {
    Object.defineProperty(window, 'location', {
      value: { href: '' },
      writable: true,
    })
  })

  describe('request interceptor', () => {
    const getRequestInterceptor = () => {
      const handlers = (apiClient.interceptors.request as any).handlers
      return handlers[handlers.length - 1].fulfilled
    }

    it('attaches Authorization header when token is present', () => {
      vi.mocked(authStoreModule.useAuthStore.getState).mockReturnValue({
        token: 'test-token',
      } as any)
      const config = { headers: new AxiosHeaders() }
      const result = getRequestInterceptor()(config)
      expect(result.headers.Authorization).toBe('Bearer test-token')
    })

    it('does not attach Authorization header when token is null', () => {
      vi.mocked(authStoreModule.useAuthStore.getState).mockReturnValue({
        token: null,
      } as any)
      const config = { headers: new AxiosHeaders() }
      const result = getRequestInterceptor()(config)
      expect(result.headers.Authorization).toBeUndefined()
    })
  })

  describe('response interceptor', () => {
    const getResponseErrorHandler = () => {
      const handlers = (apiClient.interceptors.response as any).handlers
      return handlers[handlers.length - 1].rejected
    }

    it('redirects to /login on 401', async () => {
      const error = { response: { status: 401 } }
      await expect(getResponseErrorHandler()(error)).rejects.toEqual(error)
      expect(window.location.href).toBe('/login')
    })

    it('does not redirect on non-401 errors', async () => {
      const error = { response: { status: 500 } }
      await expect(getResponseErrorHandler()(error)).rejects.toEqual(error)
      expect(window.location.href).toBe('')
    })
  })
})
