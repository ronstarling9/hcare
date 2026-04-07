import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import MockAdapter from 'axios-mock-adapter'
import { apiClient } from '../api/client'
import { useShifts, useShiftDetail, useGetCandidates } from './useShifts'
import type { PageResponse, ShiftSummaryResponse, ShiftDetailResponse, RankedCaregiverResponse } from '../types/api'
import React from 'react'

vi.mock('../store/authStore', () => ({
  useAuthStore: { getState: vi.fn().mockReturnValue({ token: 'tok', logout: vi.fn() }) },
}))

function makeWrapper() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: qc }, children)
}

describe('useShifts', () => {
  let mock: MockAdapter
  beforeEach(() => { mock = new MockAdapter(apiClient) })
  afterEach(() => { mock.restore() })

  it('returns empty content while loading, then populated content', async () => {
    const page: PageResponse<ShiftSummaryResponse> = {
      content: [{ id: 's1', agencyId: 'a1', clientId: 'c1', caregiverId: null,
        serviceTypeId: 'st1', authorizationId: null, sourcePatternId: null,
        scheduledStart: '2026-04-07T09:00:00', scheduledEnd: '2026-04-07T13:00:00',
        status: 'OPEN', notes: null }],
      totalElements: 1, totalPages: 1, number: 0, size: 200, first: true, last: true,
    }
    mock.onGet('/shifts').reply(200, page)
    const { result } = renderHook(
      () => useShifts('2026-04-06T00:00:00', '2026-04-13T00:00:00'),
      { wrapper: makeWrapper() },
    )
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data?.content[0].id).toBe('s1')
  })

  it('does not fetch when weekStart is empty', () => {
    const { result } = renderHook(() => useShifts('', ''), { wrapper: makeWrapper() })
    expect(result.current.fetchStatus).toBe('idle')
  })
})

describe('useShiftDetail', () => {
  let mock: MockAdapter
  beforeEach(() => { mock = new MockAdapter(apiClient) })
  afterEach(() => { mock.restore() })

  it('fetches shift detail when id is provided', async () => {
    const detail: ShiftDetailResponse = {
      id: 's1', agencyId: 'a1', clientId: 'c1', caregiverId: null,
      serviceTypeId: 'st1', authorizationId: null, sourcePatternId: null,
      scheduledStart: '2026-04-07T09:00:00', scheduledEnd: '2026-04-07T13:00:00',
      status: 'OPEN', notes: null, evv: null,
    }
    mock.onGet('/shifts/s1').reply(200, detail)
    const { result } = renderHook(() => useShiftDetail('s1'), { wrapper: makeWrapper() })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data?.id).toBe('s1')
  })

  it('does not fetch when id is null', () => {
    const { result } = renderHook(() => useShiftDetail(null), { wrapper: makeWrapper() })
    expect(result.current.fetchStatus).toBe('idle')
  })
})

describe('useGetCandidates', () => {
  let mock: MockAdapter
  beforeEach(() => { mock = new MockAdapter(apiClient) })
  afterEach(() => { mock.restore() })

  it('returns candidates when shiftId is provided', async () => {
    const candidates: RankedCaregiverResponse[] = [{ caregiverId: 'g1', score: 95, explanation: 'Great match' }]
    mock.onGet('/shifts/s1/candidates').reply(200, candidates)
    const { result } = renderHook(() => useGetCandidates('s1'), { wrapper: makeWrapper() })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data?.[0].caregiverId).toBe('g1')
  })

  it('does not fetch when shiftId is null', () => {
    const { result } = renderHook(() => useGetCandidates(null), { wrapper: makeWrapper() })
    expect(result.current.fetchStatus).toBe('idle')
  })
})
