import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { vi, describe, it, expect, beforeEach } from 'vitest'
import { NewClientPanel } from './NewClientPanel'
import { useCreateClient } from '../../hooks/useClients'
import { usePanelStore } from '../../store/panelStore'
import { useToastStore } from '../../store/toastStore'

// Mock i18n — return the key as the translation value so assertions are predictable
vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}))

vi.mock('../../hooks/useClients')
vi.mock('../../store/panelStore')

const mockMutateAsync = vi.fn()
const mockClosePanel = vi.fn()
const mockOpenPanel = vi.fn()

const INITIAL_TOAST = {
  visible: false, showCount: 0, message: '', linkLabel: '',
  targetId: null, panelType: '', panelTab: '', backLabel: '',
}

describe('NewClientPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(useCreateClient).mockReturnValue({
      mutateAsync: mockMutateAsync,
      isPending: false,
    } as ReturnType<typeof useCreateClient>)
    vi.mocked(usePanelStore).mockReturnValue({
      closePanel: mockClosePanel,
      openPanel: mockOpenPanel,
    } as ReturnType<typeof usePanelStore>)
    useToastStore.setState(INITIAL_TOAST)
  })

  // ── Rendering ──────────────────────────────────────────────────────────────

  it('renders all four section headings', () => {
    render(<NewClientPanel backLabel="← Clients" />)
    expect(screen.getByText('sectionIdentity')).toBeInTheDocument()
    expect(screen.getByText('sectionContact')).toBeInTheDocument()
    expect(screen.getByText('sectionBilling')).toBeInTheDocument()
    expect(screen.getByText('sectionPreferences')).toBeInTheDocument()
  })

  // ── Validation ─────────────────────────────────────────────────────────────

  it('shows all three required-field errors and does not call the API when required fields are empty', async () => {
    const user = userEvent.setup()
    render(<NewClientPanel backLabel="← Clients" />)
    await user.click(screen.getByRole('button', { name: 'saveAndAddAuth' }))
    expect(await screen.findByText('validationFirstNameRequired')).toBeInTheDocument()
    expect(screen.getByText('validationLastNameRequired')).toBeInTheDocument()
    expect(screen.getByText('validationDobRequired')).toBeInTheDocument()
    expect(mockMutateAsync).not.toHaveBeenCalled()
  })

  // ── Payload ────────────────────────────────────────────────────────────────

  it('calls createClient with correct payload for required fields only', async () => {
    const user = userEvent.setup()
    mockMutateAsync.mockResolvedValue({ id: 'client-99', firstName: 'Jane', lastName: 'Doe' })
    render(<NewClientPanel backLabel="← Clients" />)
    await user.type(screen.getByLabelText('fieldFirstName *'), 'Jane')
    await user.type(screen.getByLabelText('fieldLastName *'), 'Doe')
    await user.type(screen.getByLabelText('fieldDateOfBirth *'), '1980-01-15')
    await user.click(screen.getByRole('button', { name: 'saveAndAddAuth' }))
    await waitFor(() => expect(mockMutateAsync).toHaveBeenCalledOnce())
    const payload = mockMutateAsync.mock.calls[0][0]
    expect(payload.firstName).toBe('Jane')
    expect(payload.lastName).toBe('Doe')
    expect(payload.dateOfBirth).toBe('1980-01-15')
    expect(payload.serviceState).toBeUndefined()    // empty select → omitted
    expect(payload.preferredCaregiverGender).toBeUndefined()  // "no preference" → omitted
  })

  it('serializes preferredLanguages as a JSON array string', async () => {
    const user = userEvent.setup()
    mockMutateAsync.mockResolvedValue({ id: 'client-99' })
    render(<NewClientPanel backLabel="← Clients" />)
    await user.type(screen.getByLabelText('fieldFirstName *'), 'Jane')
    await user.type(screen.getByLabelText('fieldLastName *'), 'Doe')
    await user.type(screen.getByLabelText('fieldDateOfBirth *'), '1980-01-15')
    await user.type(screen.getByLabelText('fieldPreferredLanguages'), 'English, Spanish')
    await user.click(screen.getByRole('button', { name: 'saveAndAddAuth' }))
    await waitFor(() => expect(mockMutateAsync).toHaveBeenCalledOnce())
    expect(mockMutateAsync.mock.calls[0][0].preferredLanguages).toBe('["English","Spanish"]')
  })

  it('omits serviceState from POST payload when no state is selected', async () => {
    const user = userEvent.setup()
    mockMutateAsync.mockResolvedValue({ id: 'client-99' })
    render(<NewClientPanel backLabel="← Clients" />)
    await user.type(screen.getByLabelText('fieldFirstName *'), 'Jane')
    await user.type(screen.getByLabelText('fieldLastName *'), 'Doe')
    await user.type(screen.getByLabelText('fieldDateOfBirth *'), '1980-01-15')
    // Leave serviceState at default empty value
    await user.click(screen.getByRole('button', { name: 'saveAndAddAuth' }))
    await waitFor(() => expect(mockMutateAsync).toHaveBeenCalledOnce())
    expect(mockMutateAsync.mock.calls[0][0].serviceState).toBeUndefined()
  })

  // ── API error ──────────────────────────────────────────────────────────────

  it('shows inline error banner and keeps panel open on API failure', async () => {
    const user = userEvent.setup()
    mockMutateAsync.mockRejectedValue(new Error('Network error'))
    render(<NewClientPanel backLabel="← Clients" />)
    await user.type(screen.getByLabelText('fieldFirstName *'), 'Jane')
    await user.type(screen.getByLabelText('fieldLastName *'), 'Doe')
    await user.type(screen.getByLabelText('fieldDateOfBirth *'), '1980-01-15')
    await user.click(screen.getByRole('button', { name: 'saveAndAddAuth' }))
    expect(await screen.findByRole('alert')).toBeInTheDocument()
    expect(mockClosePanel).not.toHaveBeenCalled()
  })

  // ── Save & Add Authorization ───────────────────────────────────────────────

  it('Save & Add Authorization: closes panel and opens client detail on Authorizations tab', async () => {
    const user = userEvent.setup()
    mockMutateAsync.mockResolvedValue({ id: 'new-client-id' })
    render(<NewClientPanel backLabel="← Clients" />)
    await user.type(screen.getByLabelText('fieldFirstName *'), 'Jane')
    await user.type(screen.getByLabelText('fieldLastName *'), 'Doe')
    await user.type(screen.getByLabelText('fieldDateOfBirth *'), '1980-01-15')
    await user.click(screen.getByRole('button', { name: 'saveAndAddAuth' }))
    await waitFor(() => expect(mockClosePanel).toHaveBeenCalledOnce())
    expect(mockOpenPanel).toHaveBeenCalledWith('client', 'new-client-id', {
      backLabel: 'backLabel',
      initialTab: 'authorizations',
    })
  })

  // ── Save & Close ───────────────────────────────────────────────────────────

  it('Save & Close: shows toast and closes panel', async () => {
    const user = userEvent.setup()
    mockMutateAsync.mockResolvedValue({ id: 'new-client-id' })
    render(<NewClientPanel backLabel="← Clients" />)
    await user.type(screen.getByLabelText('fieldFirstName *'), 'Jane')
    await user.type(screen.getByLabelText('fieldLastName *'), 'Doe')
    await user.type(screen.getByLabelText('fieldDateOfBirth *'), '1980-01-15')
    await user.click(screen.getByRole('button', { name: 'saveAndClose' }))
    await waitFor(() => expect(mockClosePanel).toHaveBeenCalledOnce())
    const toast = useToastStore.getState()
    expect(toast.visible).toBe(true)
    expect(toast.targetId).toBe('new-client-id')
    expect(toast.panelType).toBe('client')
    expect(toast.panelTab).toBe('authorizations')
  })
})
