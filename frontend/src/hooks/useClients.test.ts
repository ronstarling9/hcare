import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import MockAdapter from 'axios-mock-adapter'
import { apiClient } from '../api/client'
import { useAllClients, useClientDetail, useCreateClient } from './useClients'
import type { PageResponse, ClientResponse } from '../types/api'
import React from 'react'

vi.mock('../store/authStore', () => ({
  useAuthStore: { getState: vi.fn().mockReturnValue({ token: 'tok', logout: vi.fn() }) },
}))

function makeWrapper() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: qc }, children)
}

const clientA: ClientResponse = {
  id: 'c1', firstName: 'Alice', lastName: 'Johnson', dateOfBirth: '1942-03-15',
  address: null, phone: null, medicaidId: null, serviceState: null,
  preferredCaregiverGender: null, preferredLanguages: null, noPetCaregiver: false,
  status: 'ACTIVE', createdAt: '2025-01-10T08:00:00',
}

describe('useAllClients', () => {
  let mock: MockAdapter
  beforeEach(() => { mock = new MockAdapter(apiClient) })
  afterEach(() => { mock.restore() })

  it('populates clients array and clientMap from API response', async () => {
    const page: PageResponse<ClientResponse> = {
      content: [clientA], totalElements: 1, totalPages: 1, number: 0, size: 100, first: true, last: true,
    }
    mock.onGet('/clients').reply(200, page)
    const { result } = renderHook(() => useAllClients(), { wrapper: makeWrapper() })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.clients).toHaveLength(1)
    expect(result.current.clientMap.get('c1')?.firstName).toBe('Alice')
  })

  it('returns empty clients and empty map before data loads', () => {
    mock.onGet('/clients').reply(200, { content: [], totalElements: 0, totalPages: 0, number: 0, size: 100, first: true, last: true })
    const { result } = renderHook(() => useAllClients(), { wrapper: makeWrapper() })
    expect(result.current.clients).toEqual([])
    expect(result.current.clientMap.size).toBe(0)
  })
})

describe('useClientDetail', () => {
  let mock: MockAdapter
  beforeEach(() => { mock = new MockAdapter(apiClient) })
  afterEach(() => { mock.restore() })

  it('fetches client detail when id is provided', async () => {
    mock.onGet('/clients/c1').reply(200, clientA)
    const { result } = renderHook(() => useClientDetail('c1'), { wrapper: makeWrapper() })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data?.id).toBe('c1')
  })

  it('does not fetch when id is null', () => {
    const { result } = renderHook(() => useClientDetail(null), { wrapper: makeWrapper() })
    expect(result.current.fetchStatus).toBe('idle')
  })
})

describe('useCreateClient', () => {
  const mockAxios = new MockAdapter(apiClient)
  afterEach(() => mockAxios.reset())

  it('calls POST /clients and invalidates the clients query key prefix on success', async () => {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    })
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')
    const wrapper = ({ children }: { children: React.ReactNode }) =>
      React.createElement(QueryClientProvider, { client: queryClient }, children)

    mockAxios.onPost('/clients').reply(201, {
      id: '00000000-0000-0000-0000-000000000099',
      firstName: 'Jane',
      lastName: 'Doe',
      dateOfBirth: '1980-01-15',
      status: 'ACTIVE',
    })

    const { result } = renderHook(() => useCreateClient(), { wrapper })

    result.current.mutate({ firstName: 'Jane', lastName: 'Doe', dateOfBirth: '1980-01-15' })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['clients'] })
  })
})
