import { useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { usePortalAuthStore } from '../../store/portalAuthStore'

interface Props {
  children: React.ReactNode
}

function isJwtExpired(token: string): boolean {
  try {
    const base64Url = token.split('.')[1]
    // JWT uses base64url (RFC 4648 §5): replace URL-safe chars and restore padding
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/')
    const padded = base64 + '='.repeat((4 - base64.length % 4) % 4)
    const payload = JSON.parse(atob(padded))
    return payload.exp * 1000 < Date.now()
  } catch {
    return true
  }
}

export default function PortalGuard({ children }: Props) {
  const token = usePortalAuthStore((s) => s.token)
  const navigate = useNavigate()

  useEffect(() => {
    if (!token) {
      navigate('/portal/verify?reason=no_session', { replace: true })
    } else if (isJwtExpired(token)) {
      navigate('/portal/verify?reason=session_expired', { replace: true })
    }
  }, [token, navigate])

  if (!token || isJwtExpired(token)) return null
  return <>{children}</>
}
