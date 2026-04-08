import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import MockAdapter from 'axios-mock-adapter'
import { apiClient } from '../api/client'
import { useCaregivers, useCaregiverDetail, useCreateCaregiver } from './useCaregivers'
import type { PageResponse, CaregiverResponse } from '../types/api'
import React from 'react'

vi.mock('../store/authStore', () => ({
  useAuthStore: { getState: vi.fn().mockReturnValue({ token: 'tok', logout: vi.fn() }) },
}))

function makeWrapper() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: qc }, children)
}

const caregiverA: CaregiverResponse = {
  id: 'g1', firstName: 'Maria', lastName: 'Santos', email: 'maria@example.com',
  phone: null, address: null, hireDate: null, hasPet: false, status: 'ACTIVE', createdAt: '2025-01-10T08:00:00',
}

describe('useCaregivers', () => {
  let mock: MockAdapter
  beforeEach(() => { mock = new MockAdapter(apiClient) })
  afterEach(() => { mock.restore() })

  it('populates caregivers array and caregiverMap from API response', async () => {
    const page: PageResponse<CaregiverResponse> = {
      content: [caregiverA], totalElements: 1, totalPages: 1, number: 0, size: 100, first: true, last: true,
    }
    mock.onGet('/caregivers').reply(200, page)
    const { result } = renderHook(() => useCaregivers(), { wrapper: makeWrapper() })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.caregivers).toHaveLength(1)
    expect(result.current.caregiverMap.get('g1')?.firstName).toBe('Maria')
  })

  it('returns empty caregivers and empty map before data loads', () => {
    mock.onGet('/caregivers').reply(200, { content: [], totalElements: 0, totalPages: 0, number: 0, size: 100, first: true, last: true })
    const { result } = renderHook(() => useCaregivers(), { wrapper: makeWrapper() })
    expect(result.current.caregivers).toEqual([])
    expect(result.current.caregiverMap.size).toBe(0)
  })
})

describe('useCaregiverDetail', () => {
  let mock: MockAdapter
  beforeEach(() => { mock = new MockAdapter(apiClient) })
  afterEach(() => { mock.restore() })

  it('fetches caregiver detail when id is provided', async () => {
    mock.onGet('/caregivers/g1').reply(200, caregiverA)
    const { result } = renderHook(() => useCaregiverDetail('g1'), { wrapper: makeWrapper() })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data?.id).toBe('g1')
  })

  it('does not fetch when id is null', () => {
    const { result } = renderHook(() => useCaregiverDetail(null), { wrapper: makeWrapper() })
    expect(result.current.fetchStatus).toBe('idle')
  })
})

describe('useCreateCaregiver', () => {
  let mock: MockAdapter
  beforeEach(() => { mock = new MockAdapter(apiClient) })
  afterEach(() => { mock.restore() })

  it('calls POST /caregivers and invalidates the caregivers query key prefix on success', async () => {
    const qc = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    })
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries')
    const wrapper = ({ children }: { children: React.ReactNode }) =>
      React.createElement(QueryClientProvider, { client: qc }, children)

    mock.onPost('/caregivers').reply(201, {
      id: '00000000-0000-0000-0000-000000000099',
      firstName: 'Maria',
      lastName: 'Santos',
      email: 'maria@sunrise.dev',
      phone: null,
      address: null,
      hireDate: null,
      hasPet: false,
      status: 'ACTIVE',
      createdAt: '2026-04-08T10:00:00',
    })

    const { result } = renderHook(() => useCreateCaregiver(), { wrapper })

    result.current.mutate({
      firstName: 'Maria',
      lastName: 'Santos',
      email: 'maria@sunrise.dev',
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['caregivers'] })
  })
})
