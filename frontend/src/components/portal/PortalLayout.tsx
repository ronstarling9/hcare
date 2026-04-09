import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { usePortalAuthStore } from '../../store/portalAuthStore'

interface Props {
  children: React.ReactNode
}

export default function PortalLayout({ children }: Props) {
  const { t } = useTranslation('portal')
  const navigate = useNavigate()
  const logout = usePortalAuthStore((s) => s.logout)

  function handleSignOut() {
    logout()
    navigate('/portal/verify?reason=signed_out', { replace: true })
  }

  return (
    <div className="min-h-screen bg-surface">
      {/* Portal header */}
      <div className="bg-white border-b border-border px-5 py-3 flex justify-between items-center">
        <div>
          <div className="text-[11px] text-text-secondary uppercase tracking-[.08em]">hcare</div>
        </div>
        {/* Sign out — minimum 44px touch target via py-3 padding */}
        <button
          onClick={handleSignOut}
          className="text-[12px] text-text-secondary py-3 px-2 min-h-[44px] flex items-center"
        >
          {t('signOut')}
        </button>
      </div>
      <main>{children}</main>
    </div>
  )
}
