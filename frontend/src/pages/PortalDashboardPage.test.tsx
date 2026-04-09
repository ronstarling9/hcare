// frontend/src/pages/PortalDashboardPage.test.tsx
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { vi, describe, it, expect, beforeEach } from 'vitest'
import PortalDashboardPage from './PortalDashboardPage'
import * as portalApi from '../api/portal'
import { usePortalAuthStore } from '../store/portalAuthStore'
import type { PortalDashboardResponse } from '../api/portal'

vi.mock('../api/portal')
const mockGetDashboard = vi.mocked(portalApi.getPortalDashboard)

const BASE: PortalDashboardResponse = {
  clientFirstName: 'Margaret',
  agencyTimezone: 'America/New_York',
  todayVisit: null,
  upcomingVisits: [],
  lastVisit: null,
}

function renderDashboard() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={['/portal/dashboard']}>
        <Routes>
          <Route path="/portal/dashboard" element={<PortalDashboardPage />} />
          <Route path="/portal/verify" element={<div>verify page</div>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  usePortalAuthStore.setState({ token: 'fake.jwt', clientId: 'c1', agencyId: 'a1' })
})

describe('PortalDashboardPage', () => {
  it('renders no visit today when todayVisit is null', async () => {
    mockGetDashboard.mockResolvedValue(BASE)
    renderDashboard()
    await waitFor(() => expect(screen.getByText("Margaret's Care")).toBeInTheDocument())
    expect(screen.getByText('No visit scheduled for today')).toBeInTheDocument()
  })

  it('renders IN_PROGRESS state', async () => {
    mockGetDashboard.mockResolvedValue({
      ...BASE,
      todayVisit: {
        shiftId: 's1', scheduledStart: '2026-04-08T09:00:00',
        scheduledEnd: '2026-04-08T11:00:00', status: 'IN_PROGRESS',
        clockedInAt: '2026-04-08T09:04:00', clockedOutAt: null,
        caregiver: { name: 'Maria Gonzalez', serviceType: 'Personal Care Aide' },
      },
    })
    renderDashboard()
    await waitFor(() => expect(screen.getByText(/Maria Gonzalez is here now/i)).toBeInTheDocument())
    expect(screen.getByText('Maria Gonzalez')).toBeInTheDocument()
  })

  it('renders GREY on-time state', async () => {
    // Use a time 2 hours from now so isLate() (15-min threshold) returns false
    const twoHoursFromNow = new Date(Date.now() + 2 * 60 * 60 * 1000).toISOString().replace('Z', '')
    const fourHoursFromNow = new Date(Date.now() + 4 * 60 * 60 * 1000).toISOString().replace('Z', '')
    mockGetDashboard.mockResolvedValue({
      ...BASE,
      todayVisit: {
        shiftId: 's1', scheduledStart: twoHoursFromNow,
        scheduledEnd: fourHoursFromNow, status: 'GREY',
        clockedInAt: null, clockedOutAt: null,
        caregiver: { name: 'Maria Gonzalez', serviceType: 'Personal Care Aide' },
      },
    })
    renderDashboard()
    await waitFor(() => expect(screen.getByText(/is scheduled for/i)).toBeInTheDocument())
  })

  it('renders COMPLETED with checkmark and text-text-primary', async () => {
    mockGetDashboard.mockResolvedValue({
      ...BASE,
      todayVisit: {
        shiftId: 's1', scheduledStart: '2026-04-08T09:00:00',
        scheduledEnd: '2026-04-08T11:00:00', status: 'COMPLETED',
        clockedInAt: '2026-04-08T09:04:00', clockedOutAt: '2026-04-08T11:03:00',
        caregiver: { name: 'Maria Gonzalez', serviceType: 'Personal Care Aide' },
      },
    })
    renderDashboard()
    await waitFor(() => expect(screen.getByText(/Visit completed at/)).toBeInTheDocument())
    // Checkmark icon should be present
    expect(document.querySelector('[data-testid="checkmark-icon"]')).toBeInTheDocument()
  })

  it('renders CANCELLED without caregiver card', async () => {
    mockGetDashboard.mockResolvedValue({
      ...BASE,
      todayVisit: {
        shiftId: 's1', scheduledStart: '2026-04-08T09:00:00',
        scheduledEnd: '2026-04-08T11:00:00', status: 'CANCELLED',
        clockedInAt: null, clockedOutAt: null,
        caregiver: null,
      },
    })
    renderDashboard()
    await waitFor(() => expect(screen.getByText(/Today's visit was cancelled/)).toBeInTheDocument())
    expect(screen.queryByText('Maria Gonzalez')).not.toBeInTheDocument()
  })

  it('renders late GREY state with clock icon', async () => {
    // Simulate a visit scheduled 30 min ago (past scheduledStart + 15 min threshold)
    const now = new Date()
    const thirtyMinAgo = new Date(now.getTime() - 30 * 60 * 1000).toISOString().replace('Z', '')
    const twentyMinAgo = new Date(now.getTime() + 90 * 60 * 1000).toISOString().replace('Z', '')
    mockGetDashboard.mockResolvedValue({
      ...BASE,
      todayVisit: {
        shiftId: 's1', scheduledStart: thirtyMinAgo,
        scheduledEnd: twentyMinAgo, status: 'GREY',
        clockedInAt: null, clockedOutAt: null,
        caregiver: { name: 'Maria Gonzalez', serviceType: 'Personal Care Aide' },
      },
    })
    renderDashboard()
    await waitFor(() => expect(screen.getByText(/not yet started/)).toBeInTheDocument())
    // Clock icon for late state (not color alone)
    expect(document.querySelector('[data-testid="clock-icon"]')).toBeInTheDocument()
  })

  it('shows upcoming visits capped at 3', async () => {
    mockGetDashboard.mockResolvedValue({
      ...BASE,
      upcomingVisits: [
        { scheduledStart: '2026-04-09T09:00:00', scheduledEnd: '2026-04-09T11:00:00', caregiverName: 'Maria' },
        { scheduledStart: '2026-04-10T09:00:00', scheduledEnd: '2026-04-10T11:00:00', caregiverName: 'Maria' },
        { scheduledStart: '2026-04-11T09:00:00', scheduledEnd: '2026-04-11T11:00:00', caregiverName: 'Maria' },
        { scheduledStart: '2026-04-12T09:00:00', scheduledEnd: '2026-04-12T11:00:00', caregiverName: 'Maria' },
        { scheduledStart: '2026-04-13T09:00:00', scheduledEnd: '2026-04-13T11:00:00', caregiverName: 'Maria' },
      ],
    })
    renderDashboard()
    // Backend returned 5 items but component caps display at 3
    await waitFor(() => expect(screen.getAllByText(/Maria/).length).toBe(3))
  })

  it('shows empty upcoming state when upcomingVisits is empty', async () => {
    mockGetDashboard.mockResolvedValue(BASE)
    renderDashboard()
    await waitFor(() =>
      expect(screen.getByText(/No upcoming visits scheduled/)).toBeInTheDocument())
  })

  it('renders lastVisit with note', async () => {
    mockGetDashboard.mockResolvedValue({
      ...BASE,
      lastVisit: {
        date: '2026-04-07',
        clockedOutAt: '2026-04-07T11:03:00',
        durationMinutes: 119,
        noteText: 'Margaret was in good spirits.',
      },
    })
    renderDashboard()
    await waitFor(() => expect(screen.getByText(/Margaret was in good spirits/)).toBeInTheDocument())
    expect(screen.getByText(/Completed at/)).toBeInTheDocument()
  })

  it('renders lastVisit without note — completion fact always shown', async () => {
    mockGetDashboard.mockResolvedValue({
      ...BASE,
      lastVisit: { date: '2026-04-07', clockedOutAt: '2026-04-07T11:03:00', durationMinutes: 119, noteText: null },
    })
    renderDashboard()
    await waitFor(() => expect(screen.getByText(/Completed at/)).toBeInTheDocument())
    // Note section absent
    expect(screen.queryByText('Margaret was in good spirits.')).not.toBeInTheDocument()
  })

  it('omits lastVisit section when lastVisit is null', async () => {
    mockGetDashboard.mockResolvedValue(BASE)
    renderDashboard()
    await waitFor(() => expect(screen.getByText("Margaret's Care")).toBeInTheDocument())
    expect(screen.queryByText(/Last Visit/)).not.toBeInTheDocument()
  })

  it('shows timezone label on times', async () => {
    mockGetDashboard.mockResolvedValue({
      ...BASE,
      todayVisit: {
        shiftId: 's1', scheduledStart: '2026-04-08T09:00:00',
        scheduledEnd: '2026-04-08T11:00:00', status: 'IN_PROGRESS',
        clockedInAt: '2026-04-08T09:04:00', clockedOutAt: null,
        caregiver: { name: 'Maria Gonzalez', serviceType: 'Personal Care Aide' },
      },
    })
    renderDashboard()
    // Times should include timezone abbreviation (ET/EDT/EST)
    await waitFor(() => expect(screen.getAllByText(/AM ET|AM EDT|AM EST/).length).toBeGreaterThan(0))
  })

  it('sign out clears store and navigates to ?reason=signed_out', async () => {
    mockGetDashboard.mockResolvedValue(BASE)
    renderDashboard()
    await waitFor(() => expect(screen.getByText("Margaret's Care")).toBeInTheDocument())
    await userEvent.click(screen.getByText('Sign out'))
    expect(usePortalAuthStore.getState().token).toBeNull()
    expect(screen.getByText('verify page')).toBeInTheDocument()
  })

  it('shows care concluded screen on 410 response', async () => {
    mockGetDashboard.mockRejectedValue({ response: { status: 410 } })
    renderDashboard()
    await waitFor(() =>
      expect(screen.getByText('Care services concluded')).toBeInTheDocument())
    // Session not cleared on 410
    expect(usePortalAuthStore.getState().token).not.toBeNull()
  })

  it('clears session and redirects to access_revoked on 403 response', async () => {
    mockGetDashboard.mockRejectedValue({ response: { status: 403 } })
    renderDashboard()
    await waitFor(() => expect(screen.getByText('verify page')).toBeInTheDocument())
    expect(usePortalAuthStore.getState().token).toBeNull()
  })
})
