import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { BroadcastOpenModal } from './BroadcastOpenModal'
import * as shiftsApi from '../../api/shifts'
import type { ShiftSummaryResponse } from '../../types/api'

vi.mock('../../api/shifts')
vi.mock('@tanstack/react-query', () => ({
  useQueryClient: () => ({ invalidateQueries: vi.fn() }),
}))
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) => {
      if (opts?.count !== undefined) return `${key}:${opts.count}`
      if (opts) return `${key}:${JSON.stringify(opts)}`
      return key
    },
  }),
}))

const clientMap = new Map([['c1', { firstName: 'Alice', lastName: 'Johnson' }]])

const openShift: ShiftSummaryResponse = {
  id: 's1', agencyId: 'a1', clientId: 'c1', caregiverId: null,
  serviceTypeId: 'st1', authorizationId: null, sourcePatternId: null,
  scheduledStart: '2026-04-07T09:00:00', scheduledEnd: '2026-04-07T13:00:00',
  status: 'OPEN', notes: null,
}

describe('BroadcastOpenModal', () => {
  beforeEach(() => vi.clearAllMocks())

  it('shows loading state on open', () => {
    // Never-resolving promise keeps phase at 'loading' for the duration of the assertion
    vi.mocked(shiftsApi.listOpenShifts).mockImplementation(() => new Promise(() => {}))
    render(
      <BroadcastOpenModal
        open={true} onClose={vi.fn()}
        weekStart="2026-04-07T00:00:00" weekEnd="2026-04-14T00:00:00"
        clientMap={clientMap}
      />
    )
    expect(screen.getByText('broadcastModal.loading')).toBeInTheDocument()
  })

  it('shows empty state when no open shifts', async () => {
    vi.mocked(shiftsApi.listOpenShifts).mockResolvedValue([])
    render(
      <BroadcastOpenModal
        open={true} onClose={vi.fn()}
        weekStart="2026-04-07T00:00:00" weekEnd="2026-04-14T00:00:00"
        clientMap={clientMap}
      />
    )
    await waitFor(() =>
      expect(screen.getByText('broadcastModal.noOpenShifts')).toBeInTheDocument()
    )
  })

  it('shows confirm list with shift rows and broadcast button', async () => {
    vi.mocked(shiftsApi.listOpenShifts).mockResolvedValue([openShift])
    render(
      <BroadcastOpenModal
        open={true} onClose={vi.fn()}
        weekStart="2026-04-07T00:00:00" weekEnd="2026-04-14T00:00:00"
        clientMap={clientMap}
      />
    )
    await waitFor(() =>
      expect(screen.getByText('Alice Johnson')).toBeInTheDocument()
    )
    expect(screen.getByRole('button', { name: /broadcastModal.confirmBtn/ })).toBeInTheDocument()
  })

  it('transitions to broadcasting phase and calls broadcastShift for each shift', async () => {
    vi.mocked(shiftsApi.listOpenShifts).mockResolvedValue([openShift])
    vi.mocked(shiftsApi.broadcastShift).mockResolvedValue(undefined)
    render(
      <BroadcastOpenModal
        open={true} onClose={vi.fn()}
        weekStart="2026-04-07T00:00:00" weekEnd="2026-04-14T00:00:00"
        clientMap={clientMap}
      />
    )
    await waitFor(() => screen.getByRole('button', { name: /broadcastModal.confirmBtn/ }))
    fireEvent.click(screen.getByRole('button', { name: /broadcastModal.confirmBtn/ }))
    await waitFor(() =>
      expect(shiftsApi.broadcastShift).toHaveBeenCalledWith('s1')
    )
  })

  it('shows done summary after all broadcasts complete', async () => {
    vi.mocked(shiftsApi.listOpenShifts).mockResolvedValue([openShift])
    vi.mocked(shiftsApi.broadcastShift).mockResolvedValue(undefined)
    render(
      <BroadcastOpenModal
        open={true} onClose={vi.fn()}
        weekStart="2026-04-07T00:00:00" weekEnd="2026-04-14T00:00:00"
        clientMap={clientMap}
      />
    )
    await waitFor(() => screen.getByRole('button', { name: /broadcastModal.confirmBtn/ }))
    fireEvent.click(screen.getByRole('button', { name: /broadcastModal.confirmBtn/ }))
    await waitFor(() =>
      expect(screen.getByText(/broadcastModal.doneAllSuccess/)).toBeInTheDocument()
    )
    expect(screen.getByRole('button', { name: 'broadcastModal.close' })).toBeInTheDocument()
  })

  it('calls onClose when cancel is clicked in confirm phase', async () => {
    vi.mocked(shiftsApi.listOpenShifts).mockResolvedValue([openShift])
    const onClose = vi.fn()
    render(
      <BroadcastOpenModal
        open={true} onClose={onClose}
        weekStart="2026-04-07T00:00:00" weekEnd="2026-04-14T00:00:00"
        clientMap={clientMap}
      />
    )
    await waitFor(() => screen.getByRole('button', { name: 'broadcastModal.cancel' }))
    fireEvent.click(screen.getByRole('button', { name: 'broadcastModal.cancel' }))
    expect(onClose).toHaveBeenCalled()
  })

  it('shows error state and close button when fetch fails', async () => {
    vi.mocked(shiftsApi.listOpenShifts).mockRejectedValue(new Error('network'))
    const onClose = vi.fn()
    render(
      <BroadcastOpenModal
        open={true} onClose={onClose}
        weekStart="2026-04-07T00:00:00" weekEnd="2026-04-14T00:00:00"
        clientMap={clientMap}
      />
    )
    await waitFor(() =>
      expect(screen.getByText('broadcastModal.loadError')).toBeInTheDocument()
    )
    fireEvent.click(screen.getByRole('button', { name: 'broadcastModal.close' }))
    expect(onClose).toHaveBeenCalled()
  })

  it('renders nothing when open is false', () => {
    render(
      <BroadcastOpenModal
        open={false} onClose={vi.fn()}
        weekStart="2026-04-07T00:00:00" weekEnd="2026-04-14T00:00:00"
        clientMap={clientMap}
      />
    )
    expect(screen.queryByText('broadcastModal.title')).not.toBeInTheDocument()
  })

  it('shows mixed success/failure summary when some broadcasts fail', async () => {
    const shift1: ShiftSummaryResponse = { ...openShift, id: 's1' }
    const shift2: ShiftSummaryResponse = { ...openShift, id: 's2' }
    vi.mocked(shiftsApi.listOpenShifts).mockResolvedValue([shift1, shift2])

    vi.mocked(shiftsApi.broadcastShift).mockImplementation(async (id) => {
      if (id === 's2') throw new Error('network timeout')
    })

    render(
      <BroadcastOpenModal
        open={true} onClose={vi.fn()}
        weekStart="2026-04-07T00:00:00" weekEnd="2026-04-14T00:00:00"
        clientMap={clientMap}
      />
    )
    await waitFor(() => screen.getByRole('button', { name: /broadcastModal.confirmBtn/ }))
    fireEvent.click(screen.getByRole('button', { name: /broadcastModal.confirmBtn/ }))

    await waitFor(() =>
      expect(screen.getByText(/broadcastModal.doneSummary/)).toBeInTheDocument()
    )
    expect(screen.getByText('broadcastModal.doneSummary:{"success":1,"failed":1}')).toBeInTheDocument()
  })
})
