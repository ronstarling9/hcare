import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { vi, describe, it, expect, beforeEach } from 'vitest'
import { AddTaskPanel } from './AddTaskPanel'
import { useAdlTaskTemplates } from '../../hooks/useCarePlan'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}))
vi.mock('../../hooks/useCarePlan')

const mockTemplates = [
  { name: 'Bathing (full body)', defaultAssistanceLevel: 'MAXIMUM_ASSIST', defaultFrequency: 'Daily', defaultInstructions: 'Use shower chair.' },
  { name: 'Meal preparation', defaultAssistanceLevel: 'SUPERVISION', defaultFrequency: '3× per week', defaultInstructions: '' },
  { name: 'Transfers (bed to chair)', defaultAssistanceLevel: 'MODERATE_ASSIST', defaultFrequency: 'Each visit', defaultInstructions: 'Gait belt required.' },
]

describe('AddTaskPanel', () => {
  beforeEach(() => {
    vi.mocked(useAdlTaskTemplates).mockReturnValue({
      data: mockTemplates,
      isLoading: false,
    } as ReturnType<typeof useAdlTaskTemplates>)
  })

  it('renders task name, assistance level, frequency, and instructions fields', () => {
    render(<AddTaskPanel onConfirm={vi.fn()} onCancel={vi.fn()} />)
    expect(screen.getByText('adlTaskFieldName')).toBeInTheDocument()
    expect(screen.getByText('adlTaskFieldAssistanceLevel')).toBeInTheDocument()
    expect(screen.getByText('adlTaskFieldFrequency')).toBeInTheDocument()
    expect(screen.getByText('adlTaskFieldInstructions')).toBeInTheDocument()
  })

  it('"Add Task" button is disabled when task name is empty', () => {
    render(<AddTaskPanel onConfirm={vi.fn()} onCancel={vi.fn()} />)
    expect(screen.getByRole('button', { name: 'adlTasksAddButton' })).toBeDisabled()
  })

  it('filters templates by typed value (case-insensitive)', async () => {
    const user = userEvent.setup()
    render(<AddTaskPanel onConfirm={vi.fn()} onCancel={vi.fn()} />)
    await user.type(screen.getByPlaceholderText('adlTaskSearchPlaceholder'), 'bath')
    expect(await screen.findByText('Bathing (full body)')).toBeInTheDocument()
    expect(screen.queryByText('Meal preparation')).not.toBeInTheDocument()
  })

  it('pre-fills assistance level and instructions when a template is selected', async () => {
    const user = userEvent.setup()
    render(<AddTaskPanel onConfirm={vi.fn()} onCancel={vi.fn()} />)
    await user.type(screen.getByPlaceholderText('adlTaskSearchPlaceholder'), 'bath')
    await user.click(await screen.findByText('Bathing (full body)'))
    expect(screen.getByText(/adlTaskTemplateApplied/)).toBeInTheDocument()
    // Frequency field should be pre-filled
    expect(screen.getByDisplayValue('Daily')).toBeInTheDocument()
    // Instructions textarea should be pre-filled
    expect(screen.getByDisplayValue('Use shower chair.')).toBeInTheDocument()
  })

  it('accepts custom task name not in template list', async () => {
    const user = userEvent.setup()
    render(<AddTaskPanel onConfirm={vi.fn()} onCancel={vi.fn()} />)
    await user.type(screen.getByPlaceholderText('adlTaskSearchPlaceholder'), 'Custom therapy task')
    expect(screen.getByText('adlTaskCustomOption')).toBeInTheDocument()
    expect(screen.queryByText(/adlTaskTemplateApplied/)).not.toBeInTheDocument()
  })

  it('calls onConfirm with task data when Add Task is clicked after selecting template', async () => {
    const user = userEvent.setup()
    const onConfirm = vi.fn()
    render(<AddTaskPanel onConfirm={onConfirm} onCancel={vi.fn()} />)
    await user.type(screen.getByPlaceholderText('adlTaskSearchPlaceholder'), 'bath')
    await user.click(await screen.findByText('Bathing (full body)'))
    await user.click(screen.getByRole('button', { name: 'adlTasksAddButton' }))
    expect(onConfirm).toHaveBeenCalledWith({
      name: 'Bathing (full body)',
      assistanceLevel: 'MAXIMUM_ASSIST',
      frequency: 'Daily',
      instructions: 'Use shower chair.',
    })
  })

  it('calls onCancel when Cancel is clicked', async () => {
    const user = userEvent.setup()
    const onCancel = vi.fn()
    render(<AddTaskPanel onConfirm={vi.fn()} onCancel={onCancel} />)
    await user.click(screen.getByRole('button', { name: /cancel/i }))
    expect(onCancel).toHaveBeenCalledOnce()
  })
})
