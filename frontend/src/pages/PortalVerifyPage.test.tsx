// frontend/src/pages/PortalVerifyPage.test.tsx
import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { vi, describe, it, expect, beforeEach } from 'vitest'
import PortalVerifyPage from './PortalVerifyPage'
import * as portalApi from '../api/portal'
import { usePortalAuthStore } from '../store/portalAuthStore'

vi.mock('../api/portal')

const mockVerify = vi.mocked(portalApi.verifyPortalToken)

function renderVerify(search = '') {
  return render(
    <MemoryRouter initialEntries={[`/portal/verify${search}`]}>
      <Routes>
        <Route path="/portal/verify" element={<PortalVerifyPage />} />
        <Route path="/portal/dashboard" element={<div>dashboard</div>} />
      </Routes>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  usePortalAuthStore.getState().logout()
})

describe('PortalVerifyPage', () => {
  it('auto-submits token on mount and redirects to dashboard on success', async () => {
    mockVerify.mockResolvedValue({ jwt: 'test.jwt.token', clientId: 'c1', agencyId: 'a1' })
    renderVerify('?token=abc123')
    expect(screen.getByText('Signing you in…')).toBeInTheDocument()
    await waitFor(() => expect(screen.getByText('dashboard')).toBeInTheDocument())
    expect(usePortalAuthStore.getState().token).toBe('test.jwt.token')
  })

  it('shows aria-live region while verifying', async () => {
    mockVerify.mockResolvedValue({ jwt: 'test.jwt', clientId: 'c1', agencyId: 'a1' })
    renderVerify('?token=abc123')
    const liveRegion = document.querySelector('[role="status"]')
    expect(liveRegion).toBeInTheDocument()
  })

  it('shows link expired state on 400 error', async () => {
    mockVerify.mockRejectedValue({ response: { status: 400 } })
    renderVerify('?token=badtoken')
    await waitFor(() => expect(screen.getByText('Link expired')).toBeInTheDocument())
    expect(screen.getByText(/expired or is invalid/)).toBeInTheDocument()
  })

  it('shows session expired state for ?reason=session_expired', () => {
    renderVerify('?reason=session_expired')
    expect(screen.getByText('Session expired')).toBeInTheDocument()
    expect(screen.getByText(/Your session has expired/)).toBeInTheDocument()
  })

  it('shows no active session state for ?reason=no_session', () => {
    renderVerify('?reason=no_session')
    expect(screen.getByText('No active session')).toBeInTheDocument()
  })

  it('shows signed out state for ?reason=signed_out', () => {
    renderVerify('?reason=signed_out')
    expect(screen.getByText("You've been signed out")).toBeInTheDocument()
    expect(screen.getByText(/Use your original link/)).toBeInTheDocument()
  })

  it('shows access revoked state for ?reason=access_revoked', () => {
    renderVerify('?reason=access_revoked')
    expect(screen.getByText('Access removed')).toBeInTheDocument()
    expect(screen.getByText(/portal access has been removed/)).toBeInTheDocument()
  })

  it('strips ?reason= from URL after reading it', () => {
    const replaceState = vi.spyOn(window.history, 'replaceState')
    renderVerify('?reason=signed_out')
    expect(replaceState).toHaveBeenCalledWith(null, '', '/portal/verify')
  })
})
