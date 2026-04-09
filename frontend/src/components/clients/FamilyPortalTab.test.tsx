// frontend/src/components/clients/FamilyPortalTab.test.tsx
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { vi, describe, it, expect, beforeEach } from 'vitest'
import FamilyPortalTab from './FamilyPortalTab'
import * as portalApi from '../../api/portal'
import * as clientsApi from '../../api/clients'
import { IDS } from '../../mock/data'

vi.mock('../../api/portal')
vi.mock('../../api/clients')

const mockListFpu = vi.mocked(clientsApi.listFamilyPortalUsers)
const mockInvite = vi.mocked(portalApi.inviteFamilyPortalUser)
const mockRemove = vi.mocked(portalApi.removeFamilyPortalUser)

const FPU_LIST = [
  {
    id: IDS.fpUser1,
    email: 'alice@example.com',
    name: 'Alice',
    lastLoginAt: '2026-04-01T10:00:00',
    clientId: IDS.client1,
    createdAt: '2026-04-01T08:00:00',
  },
  {
    id: IDS.fpUser2,
    email: 'bob@example.com',
    name: 'Bob',
    lastLoginAt: null,
    clientId: IDS.client1,
    createdAt: '2026-04-01T08:00:00',
  },
]

function renderTab() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <FamilyPortalTab clientId={IDS.client1} />
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  mockListFpu.mockResolvedValue({ content: FPU_LIST, totalElements: 2, totalPages: 1, number: 0, size: 25 })
})

describe('FamilyPortalTab', () => {
  it('renders user list with "Never logged in" for null lastLoginAt', async () => {
    renderTab()
    await waitFor(() => expect(screen.getByText('alice@example.com')).toBeInTheDocument())
    expect(screen.getByText('bob@example.com')).toBeInTheDocument()
    expect(screen.getByText('Never logged in')).toBeInTheDocument()
  })

  it('opens invite form on "+ Invite" click', async () => {
    renderTab()
    await waitFor(() => screen.getByText('+ Invite'))
    await userEvent.click(screen.getByText('+ Invite'))
    expect(screen.getByPlaceholderText(/email/i)).toBeInTheDocument()
    expect(screen.getByText('Generate Link')).toBeInTheDocument()
  })

  it('pre-fills email when opened from "Send New Link"', async () => {
    renderTab()
    await waitFor(() => screen.getAllByText('Send New Link'))
    await userEvent.click(screen.getAllByText('Send New Link')[0])
    const input = screen.getByDisplayValue('alice@example.com')
    expect(input).toBeInTheDocument()
  })

  it('shows re-invite note immediately when form opens from Send New Link', async () => {
    renderTab()
    await waitFor(() => screen.getAllByText('Send New Link'))
    await userEvent.click(screen.getAllByText('Send New Link')[0])
    expect(screen.getByText('A new link will be sent to this existing user')).toBeInTheDocument()
  })

  it('shows invite URL and copy button after successful generate', async () => {
    mockInvite.mockResolvedValue({
      inviteUrl: 'http://localhost:5173/portal/verify?token=abc123',
      expiresAt: '2026-04-11T14:34:00Z',
    })
    renderTab()
    await waitFor(() => screen.getByText('+ Invite'))
    await userEvent.click(screen.getByText('+ Invite'))
    await userEvent.type(screen.getByPlaceholderText(/email/i), 'new@example.com')
    await userEvent.click(screen.getByText('Generate Link'))
    await waitFor(() =>
      expect(screen.getByText(/portal\/verify\?token=abc123/)).toBeInTheDocument())
    expect(screen.getByText('Copy')).toBeInTheDocument()
  })

  it('shows expiry note after generation', async () => {
    mockInvite.mockResolvedValue({
      inviteUrl: 'http://localhost:5173/portal/verify?token=abc',
      expiresAt: '2026-04-11T14:34:00Z',
    })
    renderTab()
    await waitFor(() => screen.getByText('+ Invite'))
    await userEvent.click(screen.getByText('+ Invite'))
    await userEvent.type(screen.getByPlaceholderText(/email/i), 'exp@example.com')
    await userEvent.click(screen.getByText('Generate Link'))
    await waitFor(() => expect(screen.getByText(/Link expires/)).toBeInTheDocument())
  })

  it('shows remove confirmation with correct copy on Remove click', async () => {
    renderTab()
    await waitFor(() => screen.getAllByText('Remove'))
    await userEvent.click(screen.getAllByText('Remove')[0])
    expect(screen.getByText(/Their access will be revoked on next page load/)).toBeInTheDocument()
    expect(screen.getByText('Remove', { selector: 'button[data-confirm]' })).toBeInTheDocument()
  })

  it('calls DELETE on remove confirm', async () => {
    mockRemove.mockResolvedValue(undefined)
    renderTab()
    await waitFor(() => screen.getAllByText('Remove'))
    await userEvent.click(screen.getAllByText('Remove')[0])
    await userEvent.click(screen.getByText('Remove', { selector: 'button[data-confirm]' }))
    expect(mockRemove).toHaveBeenCalledWith(IDS.client1, IDS.fpUser1)
  })
})
