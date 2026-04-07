import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ShiftBlock } from './ShiftBlock'

const baseBlock = {
  shiftId: 's1',
  clientName: 'Alice Johnson',
  caregiverName: 'Maria Garcia',
  evvStatus: 'GREEN' as const,
  top: 120,
  height: 240,
  onClick: () => {},
}

describe('ShiftBlock', () => {
  it('renders client name', () => {
    render(<ShiftBlock {...baseBlock} />)
    expect(screen.getByText('Alice Johnson')).toBeInTheDocument()
  })

  it('renders caregiver name', () => {
    render(<ShiftBlock {...baseBlock} />)
    expect(screen.getByText('Maria Garcia')).toBeInTheDocument()
  })

  it('shows "Unassigned" when no caregiver', () => {
    render(<ShiftBlock {...baseBlock} caregiverName={null} />)
    // 'Unassigned' comes from the common namespace loaded in test setup
    expect(screen.getByText('Unassigned')).toBeInTheDocument()
  })

  it('applies red left border for RED evv status', () => {
    const { container } = render(<ShiftBlock {...baseBlock} evvStatus="RED" />)
    const block = container.firstChild as HTMLElement
    expect(block.style.borderLeftColor).toBe('rgb(220, 38, 38)')
  })

  it('applies green left border for GREEN evv status', () => {
    const { container } = render(<ShiftBlock {...baseBlock} evvStatus="GREEN" />)
    const block = container.firstChild as HTMLElement
    expect(block.style.borderLeftColor).toBe('rgb(22, 163, 74)')
  })

  it('calls onClick when clicked', async () => {
    const onClick = vi.fn()
    render(<ShiftBlock {...baseBlock} onClick={onClick} />)
    await userEvent.click(screen.getByText('Alice Johnson'))
    expect(onClick).toHaveBeenCalledTimes(1)
  })
})
