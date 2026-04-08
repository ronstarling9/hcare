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
  message: 'Caregiver saved. Add credentials to enable scheduling.',
  linkLabel: 'Add Credentials',
  targetId: 'caregiver-123',
  panelType: 'caregiver' as const,
  initialTab: 'credentials',
  backLabel: '← Caregivers',
}

const INITIAL_STORE = {
  visible: false,
  showCount: 0,
  message: '',
  linkLabel: '',
  targetId: null,
  panelType: 'client' as const,   // zero value — matches TOAST_ZERO_PANEL_TYPE
  initialTab: '',
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
    expect(screen.getByText('Caregiver saved. Add credentials to enable scheduling.')).toBeInTheDocument()
    expect(screen.getByText('Add Credentials')).toBeInTheDocument()
  })

  it('renders nothing when visible is false', () => {
    render(<Toast />)
    expect(screen.queryByText('Caregiver saved.')).not.toBeInTheDocument()
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
    fireEvent.click(screen.getByText('Add Credentials'))
    expect(mockOpenPanel).toHaveBeenCalledWith('caregiver', 'caregiver-123', {
      initialTab: 'credentials',
      backLabel: '← Caregivers',
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
    expect(useToastStore.getState().visible).toBe(false)
    vi.useRealTimers()
  })

  it('show() called while already visible resets the 6-second timer', () => {
    vi.useFakeTimers()
    useToastStore.setState(TOAST_STATE)
    const { rerender } = render(<Toast />)

    act(() => vi.advanceTimersByTime(4000))
    expect(useToastStore.getState().visible).toBe(true)

    act(() => useToastStore.setState({ ...TOAST_STATE, showCount: 2 }))
    rerender(<Toast />)

    act(() => vi.advanceTimersByTime(4000))
    expect(useToastStore.getState().visible).toBe(true)

    act(() => vi.advanceTimersByTime(2000))
    expect(useToastStore.getState().visible).toBe(false)
    vi.useRealTimers()
  })
})
