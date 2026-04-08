import { render, screen, fireEvent, act } from '@testing-library/react'
import { vi, describe, it, expect, beforeEach, afterEach } from 'vitest'
import { Toast } from './Toast'
import { useToastStore } from '../../store/toastStore'
import { usePanelStore } from '../../store/panelStore'

vi.mock('../../store/panelStore')

const mockOpenPanel = vi.fn()

const TOAST_STATE = {
  visible: true,
  showCount: 1,
  message: 'Client saved.',
  linkLabel: 'Add Authorization',
  targetId: 'client-123',
  panelType: 'client',
  panelTab: 'authorizations',
  backLabel: '← Clients',
}

const INITIAL_STORE = {
  visible: false,
  showCount: 0,
  message: '',
  linkLabel: '',
  targetId: null,
  panelType: '',
  panelTab: '',
  backLabel: '',
}

describe('Toast', () => {
  beforeEach(() => {
    vi.mocked(usePanelStore).mockReturnValue({
      openPanel: mockOpenPanel,
    } as ReturnType<typeof usePanelStore>)
    useToastStore.setState(INITIAL_STORE)
    mockOpenPanel.mockClear()
  })

  afterEach(() => {
    vi.clearAllTimers()
  })

  it('renders message and link when visible is true', () => {
    useToastStore.setState(TOAST_STATE)
    render(<Toast />)
    expect(screen.getByText('Client saved.')).toBeInTheDocument()
    expect(screen.getByText('Add Authorization')).toBeInTheDocument()
  })

  it('renders nothing when visible is false', () => {
    render(<Toast />)
    expect(screen.queryByText('Client saved.')).not.toBeInTheDocument()
  })

  it('dismiss button click sets visible to false', () => {
    useToastStore.setState(TOAST_STATE)
    render(<Toast />)
    fireEvent.click(screen.getByRole('button', { name: 'Dismiss' }))
    expect(useToastStore.getState().visible).toBe(false)
  })

  it('link click calls openPanel with panelType, targetId, initialTab, and backLabel', () => {
    useToastStore.setState(TOAST_STATE)
    render(<Toast />)
    fireEvent.click(screen.getByText('Add Authorization'))
    expect(mockOpenPanel).toHaveBeenCalledWith('client', 'client-123', {
      initialTab: 'authorizations',
      backLabel: '← Clients',
    })
  })

  it('auto-dismisses after 6 seconds', () => {
    vi.useFakeTimers()
    useToastStore.setState(TOAST_STATE)
    render(<Toast />)
    act(() => vi.advanceTimersByTime(6001))
    expect(useToastStore.getState().visible).toBe(false)
    vi.useRealTimers()
  })

  it('manual dismiss before 6 s clears timer — no stale dismiss fires after timeout', () => {
    vi.useFakeTimers()
    useToastStore.setState(TOAST_STATE)
    render(<Toast />)
    act(() => vi.advanceTimersByTime(3000))
    act(() => useToastStore.getState().dismiss())
    expect(useToastStore.getState().visible).toBe(false)
    // Advance past original 6 s — should not re-dismiss or error
    act(() => vi.advanceTimersByTime(4000))
    expect(useToastStore.getState().visible).toBe(false) // still false, no double-dismiss
    vi.useRealTimers()
  })

  it('show() called while already visible resets the 6-second timer', () => {
    vi.useFakeTimers()
    useToastStore.setState(TOAST_STATE)
    const { rerender } = render(<Toast />)

    // 4 s pass — not yet dismissed
    act(() => vi.advanceTimersByTime(4000))
    expect(useToastStore.getState().visible).toBe(true)

    // show() called again: showCount increments, visible stays true
    act(() => useToastStore.setState({ ...TOAST_STATE, showCount: 2 }))
    rerender(<Toast />)

    // 4 more seconds (8 s total, 4 s from second show) — timer reset, still not dismissed
    act(() => vi.advanceTimersByTime(4000))
    expect(useToastStore.getState().visible).toBe(true)

    // 2 more seconds (6 s from second show) — now dismisses
    act(() => vi.advanceTimersByTime(2000))
    expect(useToastStore.getState().visible).toBe(false)
    vi.useRealTimers()
  })
})
