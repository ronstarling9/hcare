import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import MockAdapter from 'axios-mock-adapter'
import { apiClient } from '../api/client'
import {
  useActivePlan,
  useAdlTasks,
  useGoals,
  useAdlTaskTemplates,
} from './useCarePlan'
import type { CarePlanResponse, AdlTaskResponse, GoalResponse, PageResponse } from '../types/api'
import React from 'react'

vi.mock('../store/authStore', () => ({
  useAuthStore: { getState: vi.fn().mockReturnValue({ token: 'tok', logout: vi.fn() }) },
}))

// Mock i18n — language is 'en'
vi.mock('react-i18next', () => ({
  useTranslation: () => ({ i18n: { language: 'en' } }),
}))

function makeWrapper() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: qc }, children)
}

const mockPlan: CarePlanResponse = {
  id: 'plan-1',
  clientId: 'client-1',
  planVersion: 1,
  status: 'ACTIVE',
  reviewedByClinicianId: null,
  reviewedAt: null,
  activatedAt: '2026-04-01T10:00:00',
  createdAt: '2026-04-01T09:00:00',
}

const mockTask: AdlTaskResponse = {
  id: 'task-1',
  carePlanId: 'plan-1',
  name: 'Bathing (full body)',
  assistanceLevel: 'MAXIMUM_ASSIST',
  instructions: 'Use shower chair.',
  frequency: 'Daily',
  sortOrder: 0,
  createdAt: '2026-04-01T09:00:00',
}

const mockGoal: GoalResponse = {
  id: 'goal-1',
  carePlanId: 'plan-1',
  description: 'Walk to mailbox',
  targetDate: '2026-06-01',
  status: 'ACTIVE',
  createdAt: '2026-04-01T09:00:00',
}

describe('useActivePlan', () => {
  let mock: MockAdapter
  beforeEach(() => { mock = new MockAdapter(apiClient) })
  afterEach(() => { mock.restore() })

  it('fetches and returns the active plan', async () => {
    mock.onGet('/clients/client-1/care-plans/active').reply(200, mockPlan)
    const { result } = renderHook(() => useActivePlan('client-1'), { wrapper: makeWrapper() })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data?.id).toBe('plan-1')
    expect(result.current.data?.status).toBe('ACTIVE')
  })

  it('does not retry on 404', async () => {
    mock.onGet('/clients/client-1/care-plans/active').reply(404)
    const { result } = renderHook(() => useActivePlan('client-1'), { wrapper: makeWrapper() })
    await waitFor(() => expect(result.current.isError).toBe(true))
    // Only 1 request should have been made (no retries)
    expect(mock.history.get.length).toBe(1)
  })
})

describe('useAdlTasks', () => {
  let mock: MockAdapter
  beforeEach(() => { mock = new MockAdapter(apiClient) })
  afterEach(() => { mock.restore() })

  it('fetches ADL tasks when planId is provided', async () => {
    const page: PageResponse<AdlTaskResponse> = {
      content: [mockTask], totalElements: 1, totalPages: 1, number: 0, size: 100, first: true, last: true,
    }
    mock.onGet('/clients/client-1/care-plans/plan-1/adl-tasks').reply(200, page)
    const { result } = renderHook(
      () => useAdlTasks('client-1', 'plan-1'),
      { wrapper: makeWrapper() },
    )
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data?.content).toHaveLength(1)
    expect(result.current.data?.content[0].name).toBe('Bathing (full body)')
  })

  it('does not fetch when planId is undefined', () => {
    const { result } = renderHook(
      () => useAdlTasks('client-1', undefined),
      { wrapper: makeWrapper() },
    )
    expect(result.current.fetchStatus).toBe('idle')
  })
})

describe('useGoals', () => {
  let mock: MockAdapter
  beforeEach(() => { mock = new MockAdapter(apiClient) })
  afterEach(() => { mock.restore() })

  it('fetches goals when planId is provided', async () => {
    const page: PageResponse<GoalResponse> = {
      content: [mockGoal], totalElements: 1, totalPages: 1, number: 0, size: 100, first: true, last: true,
    }
    mock.onGet('/clients/client-1/care-plans/plan-1/goals').reply(200, page)
    const { result } = renderHook(
      () => useGoals('client-1', 'plan-1'),
      { wrapper: makeWrapper() },
    )
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data?.content[0].description).toBe('Walk to mailbox')
  })
})

describe('useAdlTaskTemplates', () => {
  afterEach(() => { vi.restoreAllMocks() })

  it('fetches templates for the current language', async () => {
    const templates = [{ name: 'Bathing', defaultAssistanceLevel: 'MAXIMUM_ASSIST', defaultFrequency: 'Daily', defaultInstructions: '' }]
    vi.spyOn(global, 'fetch').mockResolvedValue({
      ok: true,
      json: () => Promise.resolve(templates),
    } as Response)

    const { result } = renderHook(() => useAdlTaskTemplates(), { wrapper: makeWrapper() })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual(templates)
    expect((global.fetch as ReturnType<typeof vi.fn>).mock.calls[0][0]).toContain('/locales/en/')
  })
})
