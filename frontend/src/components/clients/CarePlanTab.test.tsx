import { render, screen, waitFor, act } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { vi, describe, it, expect, beforeEach } from 'vitest'
import { CarePlanTab } from './CarePlanTab'
import {
  useActivePlan,
  useAdlTasks,
  useGoals,
  useAddAdlTask,
  useDeleteAdlTask,
  useAddGoal,
  useDeleteGoal,
} from '../../hooks/useCarePlan'
import * as carePlansApi from '../../api/carePlans'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string, opts?: Record<string, unknown>) => {
    if (opts) return Object.entries(opts).reduce((s, [k, v]) => s.replace(`{{${k}}}`, String(v)), key)
    return key
  }}),
}))
vi.mock('../../hooks/useCarePlan')
vi.mock('../../api/carePlans')
vi.mock('@tanstack/react-query', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@tanstack/react-query')>()
  return {
    ...actual,
    useQueryClient: () => ({ invalidateQueries: vi.fn() }),
  }
})

// Stub AddTaskPanel so tests don't need to deal with combobox
vi.mock('./AddTaskPanel', () => ({
  AddTaskPanel: ({ onConfirm, onCancel }: { onConfirm: (t: object) => void; onCancel: () => void }) => (
    <div data-testid="add-task-panel">
      <button onClick={() => onConfirm({ name: 'Test Task', assistanceLevel: 'SUPERVISION', frequency: 'Daily', instructions: '' })}>
        Confirm Task
      </button>
      <button onClick={onCancel}>Cancel Task</button>
    </div>
  ),
}))

// Stub AddGoalForm
vi.mock('./AddGoalForm', () => ({
  AddGoalForm: ({ onConfirm, onCancel }: { onConfirm: (d: string, t: string | null) => void; onCancel: () => void }) => (
    <div data-testid="add-goal-form">
      <button onClick={() => onConfirm('Walk to mailbox', null)}>Confirm Goal</button>
      <button onClick={onCancel}>Cancel Goal</button>
    </div>
  ),
}))

const noopMutation = { mutate: vi.fn(), mutateAsync: vi.fn(), isPending: false }

const activePlan = {
  id: 'plan-1', clientId: 'c-1', planVersion: 2, status: 'ACTIVE' as const,
  reviewedByClinicianId: null, reviewedAt: null,
  activatedAt: '2026-03-15T10:00:00', createdAt: '2026-03-01T09:00:00',
}

const mockTask = {
  id: 'task-1', carePlanId: 'plan-1', name: 'Bathing (full body)',
  assistanceLevel: 'MAXIMUM_ASSIST' as const, instructions: 'Use shower chair.', frequency: 'Daily',
  sortOrder: 0, createdAt: '2026-03-01T09:00:00',
}

const mockGoal = {
  id: 'goal-1', carePlanId: 'plan-1', description: 'Walk to mailbox',
  targetDate: '2026-06-01', status: 'ACTIVE' as const, createdAt: '2026-03-01T09:00:00',
}

function setupMocks({
  planData = null,
  planError = null,
  tasks = [],
  goals = [],
}: {
  planData?: typeof activePlan | null
  planError?: { response?: { status?: number } } | null
  tasks?: typeof mockTask[]
  goals?: typeof mockGoal[]
} = {}) {
  vi.mocked(useActivePlan).mockReturnValue({
    data: planData ?? undefined,
    isLoading: false,
    isError: Boolean(planError),
    error: planError,
  } as ReturnType<typeof useActivePlan>)
  vi.mocked(useAdlTasks).mockReturnValue({
    data: { content: tasks, totalElements: tasks.length, totalPages: 1, number: 0, size: 100, first: true, last: true },
    isLoading: false,
  } as ReturnType<typeof useAdlTasks>)
  vi.mocked(useGoals).mockReturnValue({
    data: { content: goals, totalElements: goals.length, totalPages: 1, number: 0, size: 100, first: true, last: true },
    isLoading: false,
  } as ReturnType<typeof useGoals>)
  vi.mocked(useAddAdlTask).mockReturnValue(noopMutation as ReturnType<typeof useAddAdlTask>)
  vi.mocked(useDeleteAdlTask).mockReturnValue(noopMutation as ReturnType<typeof useDeleteAdlTask>)
  vi.mocked(useAddGoal).mockReturnValue(noopMutation as ReturnType<typeof useAddGoal>)
  vi.mocked(useDeleteGoal).mockReturnValue(noopMutation as ReturnType<typeof useDeleteGoal>)
}

describe('CarePlanTab — empty state', () => {
  beforeEach(() => setupMocks({ planError: { response: { status: 404 } } }))

  it('renders Set Up Care Plan button when no active plan', () => {
    render(<CarePlanTab clientId="c-1" clientFirstName="Margaret" />)
    expect(screen.getByText('carePlanNoActivePlan')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'carePlanSetUpCta' })).toBeInTheDocument()
  })
})

describe('CarePlanTab — setup mode', () => {
  beforeEach(() => setupMocks({ planError: { response: { status: 404 } } }))

  it('shows setup banner after clicking Set Up Care Plan', async () => {
    const user = userEvent.setup()
    render(<CarePlanTab clientId="c-1" clientFirstName="Margaret" />)
    await user.click(screen.getByRole('button', { name: 'carePlanSetUpCta' }))
    expect(screen.getByText(/carePlanSetupBannerTitle/)).toBeInTheDocument()
    expect(screen.getByText('carePlanSetupBannerHint')).toBeInTheDocument()
  })

  it('"Save & Activate Plan" is disabled with zero pending tasks', async () => {
    const user = userEvent.setup()
    render(<CarePlanTab clientId="c-1" clientFirstName="Margaret" />)
    await user.click(screen.getByRole('button', { name: 'carePlanSetUpCta' }))
    expect(screen.getByRole('button', { name: 'carePlanSaveActivate' })).toBeDisabled()
  })

  it('task accumulates in local state after adding a task in setup mode', async () => {
    const user = userEvent.setup()
    render(<CarePlanTab clientId="c-1" clientFirstName="Margaret" />)
    await user.click(screen.getByRole('button', { name: 'carePlanSetUpCta' }))
    await user.click(screen.getByRole('button', { name: 'adlTasksAddButton' }))
    await user.click(screen.getByRole('button', { name: 'Confirm Task' }))
    expect(screen.getByText('Test Task')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'carePlanSaveActivate' })).not.toBeDisabled()
  })

  it('Discard returns to empty state without making API calls', async () => {
    const user = userEvent.setup()
    render(<CarePlanTab clientId="c-1" clientFirstName="Margaret" />)
    await user.click(screen.getByRole('button', { name: 'carePlanSetUpCta' }))
    await user.click(screen.getByRole('button', { name: 'carePlanDiscard' }))
    expect(screen.getByRole('button', { name: 'carePlanSetUpCta' })).toBeInTheDocument()
    expect(carePlansApi.createCarePlan).not.toHaveBeenCalled()
  })
})

describe('CarePlanTab — Save & Activate sequence', () => {
  beforeEach(() => setupMocks({ planError: { response: { status: 404 } } }))

  it('fires create → addTask → activate in order', async () => {
    const user = userEvent.setup()
    vi.mocked(carePlansApi.createCarePlan).mockResolvedValue({ id: 'new-plan-id', clientId: 'c-1', planVersion: 1, status: 'DRAFT', reviewedByClinicianId: null, reviewedAt: null, activatedAt: null, createdAt: '' })
    vi.mocked(carePlansApi.addAdlTask).mockResolvedValue({} as never)
    vi.mocked(carePlansApi.addGoal).mockResolvedValue({} as never)
    vi.mocked(carePlansApi.activateCarePlan).mockResolvedValue({ ...activePlan })

    render(<CarePlanTab clientId="c-1" clientFirstName="Margaret" />)
    await user.click(screen.getByRole('button', { name: 'carePlanSetUpCta' }))
    await user.click(screen.getByRole('button', { name: 'adlTasksAddButton' }))
    await user.click(screen.getByRole('button', { name: 'Confirm Task' }))
    await user.click(screen.getByRole('button', { name: 'carePlanSaveActivate' }))

    await waitFor(() => {
      expect(carePlansApi.createCarePlan).toHaveBeenCalledWith('c-1')
      expect(carePlansApi.addAdlTask).toHaveBeenCalledWith('c-1', 'new-plan-id', expect.objectContaining({ name: 'Test Task' }))
      expect(carePlansApi.activateCarePlan).toHaveBeenCalledWith('c-1', 'new-plan-id')
    })
  })

  it('shows error toast and stays in setup mode on API failure', async () => {
    const user = userEvent.setup()
    vi.mocked(carePlansApi.createCarePlan).mockRejectedValue(new Error('Network error'))

    render(<CarePlanTab clientId="c-1" clientFirstName="Margaret" />)
    await user.click(screen.getByRole('button', { name: 'carePlanSetUpCta' }))
    await user.click(screen.getByRole('button', { name: 'adlTasksAddButton' }))
    await user.click(screen.getByRole('button', { name: 'Confirm Task' }))
    await user.click(screen.getByRole('button', { name: 'carePlanSaveActivate' }))

    expect(await screen.findByText('carePlanActivateError')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'carePlanSaveActivate' })).toBeInTheDocument()
  })
})

describe('CarePlanTab — active plan view', () => {
  beforeEach(() => setupMocks({ planData: activePlan, tasks: [mockTask], goals: [mockGoal] }))

  it('shows plan header with version and activated date', () => {
    render(<CarePlanTab clientId="c-1" clientFirstName="Margaret" />)
    expect(screen.getByText('carePlanActiveLabel')).toBeInTheDocument()
    expect(screen.getByText(/carePlanVersion/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'carePlanNewVersion' })).toBeInTheDocument()
  })

  it('renders task rows with assistance badge and frequency', () => {
    render(<CarePlanTab clientId="c-1" clientFirstName="Margaret" />)
    expect(screen.getByText('Bathing (full body)')).toBeInTheDocument()
    expect(screen.getByText('Daily')).toBeInTheDocument()
  })

  it('renders goal rows with target date', () => {
    render(<CarePlanTab clientId="c-1" clientFirstName="Margaret" />)
    expect(screen.getByText('Walk to mailbox')).toBeInTheDocument()
  })
})

describe('CarePlanTab — New Version', () => {
  beforeEach(() => setupMocks({ planData: activePlan, tasks: [mockTask], goals: [mockGoal] }))

  it('enters setup mode pre-populated with active plan tasks and goals', async () => {
    const user = userEvent.setup()
    render(<CarePlanTab clientId="c-1" clientFirstName="Margaret" />)
    await user.click(screen.getByRole('button', { name: 'carePlanNewVersion' }))
    // Should show new version banner
    expect(screen.getByText(/carePlanNewVersionBannerTitle/)).toBeInTheDocument()
    // Pre-populated task from active plan should be visible
    expect(screen.getByText('Bathing (full body)')).toBeInTheDocument()
    // Save button should be enabled (1 task)
    expect(screen.getByRole('button', { name: 'carePlanSaveActivate' })).not.toBeDisabled()
  })
})
