import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import MockAdapter from 'axios-mock-adapter'
import { apiClient } from '../api/client'
import { useCaregivers } from './useCaregivers'
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
})
