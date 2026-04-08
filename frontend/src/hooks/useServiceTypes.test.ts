import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import MockAdapter from 'axios-mock-adapter'
import { apiClient } from '../api/client'
import { useServiceTypes } from './useServiceTypes'
import type { ServiceTypeResponse } from '../types/api'
import React from 'react'

vi.mock('../store/authStore', () => ({
  useAuthStore: { getState: vi.fn().mockReturnValue({ token: 'tok', logout: vi.fn() }) },
}))

function makeWrapper() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: qc }, children)
}

const stA: ServiceTypeResponse = {
  id: 'st1',
  name: 'Personal Care',
  code: 'PC',
  requiresEvv: true,
  requiredCredentials: ['CPR'],
}

describe('useServiceTypes', () => {
  let mock: MockAdapter
  beforeEach(() => { mock = new MockAdapter(apiClient) })
  afterEach(() => { mock.restore() })

  it('returns populated serviceTypes array on success', async () => {
    mock.onGet('/service-types').reply(200, [stA])
    const { result } = renderHook(() => useServiceTypes(), { wrapper: makeWrapper() })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.serviceTypes).toHaveLength(1)
    expect(result.current.serviceTypes[0].id).toBe('st1')
    expect(result.current.serviceTypes[0].name).toBe('Personal Care')
  })

  it('returns [] before data loads (initial state)', () => {
    mock.onGet('/service-types').reply(200, [stA])
    const { result } = renderHook(() => useServiceTypes(), { wrapper: makeWrapper() })
    expect(result.current.serviceTypes).toEqual([])
  })

  it('sets isError: true and serviceTypes: [] on API 500', async () => {
    mock.onGet('/service-types').reply(500)
    const { result } = renderHook(() => useServiceTypes(), { wrapper: makeWrapper() })
    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(result.current.serviceTypes).toEqual([])
  })
})
