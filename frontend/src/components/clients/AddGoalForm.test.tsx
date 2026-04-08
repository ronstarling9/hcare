import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { vi, describe, it, expect } from 'vitest'
import { AddGoalForm } from './AddGoalForm'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}))

describe('AddGoalForm', () => {
  it('renders description and target date fields', () => {
    render(<AddGoalForm onConfirm={vi.fn()} onCancel={vi.fn()} />)
    expect(screen.getByText('goalFieldDescription')).toBeInTheDocument()
    expect(screen.getByText('goalFieldTargetDate')).toBeInTheDocument()
  })

  it('"Add Goal" button is disabled when description is empty', () => {
    render(<AddGoalForm onConfirm={vi.fn()} onCancel={vi.fn()} />)
    expect(screen.getByRole('button', { name: 'goalsAddButton' })).toBeDisabled()
  })

  it('calls onConfirm with description and null targetDate when date is empty', async () => {
    const user = userEvent.setup()
    const onConfirm = vi.fn()
    render(<AddGoalForm onConfirm={onConfirm} onCancel={vi.fn()} />)
    await user.type(screen.getByRole('textbox', { name: /goalFieldDescription/i }), 'Walk to mailbox')
    await user.click(screen.getByRole('button', { name: 'goalsAddButton' }))
    expect(onConfirm).toHaveBeenCalledWith('Walk to mailbox', null)
  })

  it('calls onConfirm with description and targetDate when date is provided', async () => {
    const user = userEvent.setup()
    const onConfirm = vi.fn()
    render(<AddGoalForm onConfirm={onConfirm} onCancel={vi.fn()} />)
    await user.type(screen.getByRole('textbox', { name: /goalFieldDescription/i }), 'Self-dress')
    await user.type(screen.getByDisplayValue(''), '2026-06-01')
    await user.click(screen.getByRole('button', { name: 'goalsAddButton' }))
    expect(onConfirm).toHaveBeenCalledWith('Self-dress', '2026-06-01')
  })

  it('calls onCancel when Cancel is clicked', async () => {
    const user = userEvent.setup()
    const onCancel = vi.fn()
    render(<AddGoalForm onConfirm={vi.fn()} onCancel={onCancel} />)
    await user.click(screen.getByRole('button', { name: /cancel/i }))
    expect(onCancel).toHaveBeenCalledOnce()
  })
})
