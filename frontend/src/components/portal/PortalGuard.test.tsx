import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, it, expect, beforeEach } from 'vitest'
import PortalGuard from './PortalGuard'
import { usePortalAuthStore } from '../../store/portalAuthStore'

function makeJwt(payload: object): string {
  // Build a real base64url-encoded JWT (header.payload.sig).
  // btoa produces standard base64; convert to base64url by swapping chars and stripping padding.
  const encode = (obj: object) =>
    btoa(JSON.stringify(obj)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
  return `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode(payload)}.sig`
}

function renderGuard() {
  return render(
    <MemoryRouter initialEntries={['/portal/dashboard']}>
      <Routes>
        <Route
          path="/portal/dashboard"
          element={
            <PortalGuard>
              <div>protected content</div>
            </PortalGuard>
          }
        />
        <Route path="/portal/verify" element={<div>verify page</div>} />
      </Routes>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  usePortalAuthStore.getState().logout()
})

describe('PortalGuard', () => {
  it('redirects to no_session when no token is present', () => {
    renderGuard()
    expect(screen.getByText('verify page')).toBeInTheDocument()
  })

  it('renders children when token is valid and not expired', () => {
    // Use a real base64url-encoded JWT with a UUID clientId (contains '-' chars) to verify
    // the base64url normalization in isJwtExpired works correctly. A naive atob() call
    // would throw a DOMException on the '-' characters, causing isJwtExpired to return true
    // (expired) and redirect — this test proves the fix is effective.
    const futureExp = Math.floor(Date.now() / 1000) + 3600
    const token = makeJwt({
      exp: futureExp,
      clientId: 'a1b2c3d4-e5f6-7890-abcd-ef1234567890', // UUID with '-' chars
      agencyId: 'f0e1d2c3-b4a5-6789-fedc-ba9876543210',
    })
    usePortalAuthStore.getState().login(token, 'a1b2c3d4-e5f6-7890-abcd-ef1234567890', 'f0e1d2c3-b4a5-6789-fedc-ba9876543210')
    renderGuard()
    expect(screen.getByText('protected content')).toBeInTheDocument()
  })

  it('redirects to session_expired when token exp is in the past', () => {
    const pastExp = Math.floor(Date.now() / 1000) - 60
    const token = makeJwt({
      exp: pastExp,
      clientId: 'a1b2c3d4-e5f6-7890-abcd-ef1234567890',
      agencyId: 'f0e1d2c3-b4a5-6789-fedc-ba9876543210',
    })
    usePortalAuthStore.getState().login(token, 'a1b2c3d4-e5f6-7890-abcd-ef1234567890', 'f0e1d2c3-b4a5-6789-fedc-ba9876543210')
    renderGuard()
    expect(screen.getByText('verify page')).toBeInTheDocument()
  })
})
