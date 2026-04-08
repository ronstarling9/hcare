import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import MockAdapter from 'axios-mock-adapter'
import { apiClient } from '../../api/client'
import { NewShiftPanel } from './NewShiftPanel'
import type { ServiceTypeResponse } from '../../types/api'
import React from 'react'

vi.mock('../../store/authStore', () => ({
  useAuthStore: { getState: vi.fn().mockReturnValue({ token: 'tok', logout: vi.fn() }) },
}))

vi.mock('../../store/panelStore', () => ({
  usePanelStore: () => ({ closePanel: vi.fn() }),
}))

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
  }),
}))

function makeWrapper() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: qc }, children)
}

const pcs: ServiceTypeResponse = {
  id: 'st000000-0000-0000-0000-000000000001',
  name: 'PCS — Personal Care Services',
  code: 'PCS',
  requiresEvv: true,
  requiredCredentials: [],
}

const emptyPage = { content: [], totalElements: 0, totalPages: 0, number: 0, size: 100, first: true, last: true }

describe('NewShiftPanel service type select', () => {
  let mock: MockAdapter

  beforeEach(() => {
    mock = new MockAdapter(apiClient)
  })

  afterEach(() => {
    mock.restore()
  })

  it('shows loading state while service types are fetching', async () => {
    mock.onGet('/service-types').reply(() => new Promise(() => {}))
    mock.onGet('/clients').reply(200, emptyPage)
    mock.onGet('/caregivers').reply(200, emptyPage)

    render(<NewShiftPanel prefill={null} backLabel="Back" />, { wrapper: makeWrapper() })

    const select = screen.getByRole('combobox', { name: /labelServiceType/i })
    expect(select).toBeDisabled()
    expect(screen.getByText('loadingServiceTypes')).toBeInTheDocument()
  })

  it('shows error state when service types request returns 500', async () => {
    mock.onGet('/service-types').reply(500)
    mock.onGet('/clients').reply(200, emptyPage)
    mock.onGet('/caregivers').reply(200, emptyPage)

    render(<NewShiftPanel prefill={null} backLabel="Back" />, { wrapper: makeWrapper() })

    await waitFor(() => expect(screen.getByText('serviceTypesLoadError')).toBeInTheDocument())
    const select = screen.getByRole('combobox', { name: /labelServiceType/i })
    expect(select).toBeDisabled()
    expect(screen.getByText('serviceTypesLoadErrorRetry')).toBeInTheDocument()
  })

  it('renders service type options and enables select when data loads', async () => {
    mock.onGet('/service-types').reply(200, [pcs])
    mock.onGet('/clients').reply(200, emptyPage)
    mock.onGet('/caregivers').reply(200, emptyPage)

    render(<NewShiftPanel prefill={null} backLabel="Back" />, { wrapper: makeWrapper() })

    await waitFor(() => expect(screen.getByText('PCS — Personal Care Services')).toBeInTheDocument())
    const select = screen.getByRole('combobox', { name: /labelServiceType/i })
    expect(select).not.toBeDisabled()
    const submitBtn = screen.getByRole('button', { name: /saveShift/i })
    expect(submitBtn).not.toBeDisabled()
  })

  it('shows empty state option and hint when no service types returned', async () => {
    mock.onGet('/service-types').reply(200, [])
    mock.onGet('/clients').reply(200, { content: [], totalElements: 0, totalPages: 0, number: 0, size: 100, first: true, last: true })
    mock.onGet('/caregivers').reply(200, { content: [], totalElements: 0, totalPages: 0, number: 0, size: 100, first: true, last: true })

    render(<NewShiftPanel prefill={null} backLabel="Back" />, { wrapper: makeWrapper() })

    await waitFor(() => expect(screen.getByText('noServiceTypesOption')).toBeInTheDocument())
    const select = screen.getByRole('combobox', { name: /labelServiceType/i })
    expect(select).toBeDisabled()
    expect(screen.getByText('noServiceTypesHint')).toBeInTheDocument()
  })
})
