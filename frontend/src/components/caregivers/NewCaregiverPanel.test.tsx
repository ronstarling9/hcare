import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { vi, describe, it, expect, beforeEach } from 'vitest'
import { NewCaregiverPanel } from './NewCaregiverPanel'
import { useCreateCaregiver } from '../../hooks/useCaregivers'
import { usePanelStore } from '../../store/panelStore'
import { useToastStore } from '../../store/toastStore'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}))

vi.mock('../../hooks/useCaregivers')
vi.mock('../../store/panelStore')

const mockMutateAsync = vi.fn()
const mockClosePanel = vi.fn()
const mockOpenPanel = vi.fn()

const INITIAL_TOAST = {
  visible: false, showCount: 0, message: '', linkLabel: '',
  targetId: null, panelType: 'client' as const, initialTab: '', backLabel: '',
}

describe('NewCaregiverPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(useCreateCaregiver).mockReturnValue({
      mutateAsync: mockMutateAsync,
      isPending: false,
    } as ReturnType<typeof useCreateCaregiver>)
    vi.mocked(usePanelStore).mockReturnValue({
      closePanel: mockClosePanel,
      openPanel: mockOpenPanel,
    } as ReturnType<typeof usePanelStore>)
    useToastStore.setState(INITIAL_TOAST)
  })

  // ── Rendering ──────────────────────────────────────────────────────────────

  it('renders both section headings', () => {
    render(<NewCaregiverPanel backLabel="← Caregivers" />)
    expect(screen.getByText('sectionIdentity')).toBeInTheDocument()
    expect(screen.getByText('sectionEmployment')).toBeInTheDocument()
  })

  // ── Validation ─────────────────────────────────────────────────────────────

  it('shows required-field errors and does not call the API when required fields are empty', async () => {
    const user = userEvent.setup()
    render(<NewCaregiverPanel backLabel="← Caregivers" />)
    await user.click(screen.getByRole('button', { name: 'saveAndAddCredentials' }))
    expect(await screen.findByText('validationFirstNameRequired')).toBeInTheDocument()
    expect(screen.getByText('validationLastNameRequired')).toBeInTheDocument()
    expect(screen.getByText('validationEmailRequired')).toBeInTheDocument()
    expect(mockMutateAsync).not.toHaveBeenCalled()
  })

  it('shows email format error when email is invalid', async () => {
    const user = userEvent.setup()
    render(<NewCaregiverPanel backLabel="← Caregivers" />)
    await user.type(screen.getByLabelText('fieldFirstName *'), 'Maria')
    await user.type(screen.getByLabelText('fieldLastName *'), 'Santos')
    await user.type(screen.getByLabelText('fieldEmail *'), 'not-an-email')
    await user.click(screen.getByRole('button', { name: 'saveAndAddCredentials' }))
    expect(await screen.findByText('validationEmailInvalid')).toBeInTheDocument()
    expect(mockMutateAsync).not.toHaveBeenCalled()
  })

  // ── Payload ────────────────────────────────────────────────────────────────

  it('calls createCaregiver with correct payload for required fields only', async () => {
    const user = userEvent.setup()
    mockMutateAsync.mockResolvedValue({
      id: 'caregiver-99', firstName: 'Maria', lastName: 'Santos', email: 'maria@sunrise.dev',
    })
    render(<NewCaregiverPanel backLabel="← Caregivers" />)
    await user.type(screen.getByLabelText('fieldFirstName *'), 'Maria')
    await user.type(screen.getByLabelText('fieldLastName *'), 'Santos')
    await user.type(screen.getByLabelText('fieldEmail *'), 'maria@sunrise.dev')
    await user.click(screen.getByRole('button', { name: 'saveAndAddCredentials' }))
    await waitFor(() => expect(mockMutateAsync).toHaveBeenCalledOnce())
    const payload = mockMutateAsync.mock.calls[0][0]
    expect(payload.firstName).toBe('Maria')
    expect(payload.lastName).toBe('Santos')
    expect(payload.email).toBe('maria@sunrise.dev')
    expect(payload.phone).toBeUndefined()
    expect(payload.address).toBeUndefined()
    expect(payload.hireDate).toBeUndefined()
  })

  // ── API error ──────────────────────────────────────────────────────────────

  it('shows inline error banner and keeps panel open on API failure', async () => {
    const user = userEvent.setup()
    mockMutateAsync.mockRejectedValue(new Error('Network error'))
    render(<NewCaregiverPanel backLabel="← Caregivers" />)
    await user.type(screen.getByLabelText('fieldFirstName *'), 'Maria')
    await user.type(screen.getByLabelText('fieldLastName *'), 'Santos')
    await user.type(screen.getByLabelText('fieldEmail *'), 'maria@sunrise.dev')
    await user.click(screen.getByRole('button', { name: 'saveAndAddCredentials' }))
    expect(await screen.findByRole('alert')).toBeInTheDocument()
    expect(mockClosePanel).not.toHaveBeenCalled()
  })

  // ── Save & Add Credentials ─────────────────────────────────────────────────

  it('Save & Add Credentials: closes panel and opens caregiver detail on Credentials tab', async () => {
    const user = userEvent.setup()
    mockMutateAsync.mockResolvedValue({ id: 'caregiver-99' })
    render(<NewCaregiverPanel backLabel="← Caregivers" />)
    await user.type(screen.getByLabelText('fieldFirstName *'), 'Maria')
    await user.type(screen.getByLabelText('fieldLastName *'), 'Santos')
    await user.type(screen.getByLabelText('fieldEmail *'), 'maria@sunrise.dev')
    await user.click(screen.getByRole('button', { name: 'saveAndAddCredentials' }))
    await waitFor(() => expect(mockClosePanel).toHaveBeenCalledOnce())
    expect(mockOpenPanel).toHaveBeenCalledWith('caregiver', 'caregiver-99', {
      backLabel: 'backLabel',
      initialTab: 'credentials',
    })
  })

  // ── Save & Close ───────────────────────────────────────────────────────────

  it('Save & Close: shows toast with correct fields and closes panel', async () => {
    const user = userEvent.setup()
    mockMutateAsync.mockResolvedValue({ id: 'caregiver-99' })
    render(<NewCaregiverPanel backLabel="← Caregivers" />)
    await user.type(screen.getByLabelText('fieldFirstName *'), 'Maria')
    await user.type(screen.getByLabelText('fieldLastName *'), 'Santos')
    await user.type(screen.getByLabelText('fieldEmail *'), 'maria@sunrise.dev')
    await user.click(screen.getByRole('button', { name: 'saveAndClose' }))
    await waitFor(() => expect(mockClosePanel).toHaveBeenCalledOnce())
    const toast = useToastStore.getState()
    expect(toast.visible).toBe(true)
    expect(toast.targetId).toBe('caregiver-99')
    expect(toast.panelType).toBe('caregiver')
    expect(toast.initialTab).toBe('credentials')
    expect(toast.message).toBe('saveCloseToast')
    expect(toast.linkLabel).toBe('saveCloseToastLink')
    expect(toast.backLabel).toBe('backLabel')
  })
})
