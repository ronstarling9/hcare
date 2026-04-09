import { useState, useEffect } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { verifyPortalToken } from '../api/portal'
import { usePortalAuthStore } from '../store/portalAuthStore'

type VerifyState =
  | 'verifying'
  | 'token_invalid'
  | 'server_error'
  | 'session_expired'
  | 'no_session'
  | 'signed_out'
  | 'access_revoked'

const REASON_MAP: Record<string, VerifyState> = {
  session_expired: 'session_expired',
  no_session: 'no_session',
  signed_out: 'signed_out',
  access_revoked: 'access_revoked',
}

export default function PortalVerifyPage() {
  const { t } = useTranslation('portal')
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const login = usePortalAuthStore((s) => s.login)

  const token = searchParams.get('token')
  const reason = searchParams.get('reason')

  // Determine initial state
  let initialState: VerifyState = 'verifying'
  if (reason && REASON_MAP[reason]) {
    initialState = REASON_MAP[reason]
  } else if (!token && !reason) {
    initialState = 'no_session'
  }

  const [displayState, setDisplayState] = useState<VerifyState>(initialState)

  // Strip ?reason= or any query param from URL bar after reading
  useEffect(() => {
    if (reason || (!token && !reason)) {
      window.history.replaceState(null, '', '/portal/verify')
    }
  }, [reason, token])

  // Auto-submit if we have a token
  useEffect(() => {
    if (!token) return
    verifyPortalToken(token)
      .then((res) => {
        login(res.jwt, res.clientId, res.agencyId)
        navigate('/portal/dashboard', { replace: true })
      })
      .catch((err) => {
        const status = (err as { response?: { status?: number } })?.response?.status
        if (status !== undefined && status >= 400 && status < 500) {
          setDisplayState('token_invalid')
        } else {
          setDisplayState('server_error')
        }
      })
  // login and navigate are stable refs (Zustand selector + useNavigate) — intentionally omitted
  }, [token]) // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <div className="min-h-screen bg-surface flex items-center justify-center p-4">
      <div className="bg-white border border-border p-8 w-full max-w-sm">
        {displayState === 'verifying' ? (
          <div role="status" aria-live="polite" className="text-center">
            <div className="text-[14px] text-text-primary">{t('verifyingTitle')}</div>
          </div>
        ) : (
          <VerifyErrorCard state={displayState} t={t} />
        )}
      </div>
    </div>
  )
}

function VerifyErrorCard({
  state,
  t,
}: {
  state: Exclude<VerifyState, 'verifying'>
  t: (key: string) => string
}) {
  const headingKey = {
    token_invalid: 'linkExpiredHeading',
    server_error: 'serverErrorHeading',
    session_expired: 'sessionExpiredHeading',
    no_session: 'noSessionHeading',
    signed_out: 'signedOutHeading',
    access_revoked: 'accessRevokedHeading',
  }[state]

  const bodyKey = {
    token_invalid: 'linkExpiredBody',
    server_error: 'serverErrorBody',
    session_expired: 'sessionExpiredBody',
    no_session: 'noSessionBody',
    signed_out: 'signedOutBody',
    access_revoked: 'accessRevokedBody',
  }[state]

  return (
    <div>
      <h1 className="text-[16px] font-bold text-text-primary mb-2">{t(headingKey)}</h1>
      <p className="text-[14px] text-text-primary leading-relaxed">{t(bodyKey)}</p>
    </div>
  )
}
