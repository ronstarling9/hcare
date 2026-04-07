import { NavLink } from 'react-router-dom'
import { useTranslation } from 'react-i18next'

interface NavItem {
  label: string
  to: string
  icon: string       // emoji placeholder — replace with real icons in P2
}

interface NavSection {
  section: string
  items: NavItem[]
}

interface SidebarProps {
  /** RED EVV count — drives the Dashboard badge. Passed by Shell from dashboard data. */
  redEvvCount?: number
  /** Logged-in user display name */
  userName?: string
  userRole?: string
}

export function Sidebar({ redEvvCount = 0, userName = 'Admin User', userRole = 'ADMIN' }: SidebarProps) {
  const { t } = useTranslation('nav')

  const NAV_SECTIONS: NavSection[] = [
    {
      section: t('sectionOperations'),
      items: [
        { label: t('schedule'), to: '/schedule', icon: '📅' },
        { label: t('dashboard'), to: '/dashboard', icon: '📊' },
      ],
    },
    {
      section: t('sectionPeople'),
      items: [
        { label: t('clients'), to: '/clients', icon: '👤' },
        { label: t('caregivers'), to: '/caregivers', icon: '🏥' },
      ],
    },
    {
      section: t('sectionAdmin'),
      items: [
        { label: t('payers'), to: '/payers', icon: '💳' },
        { label: t('evvStatus'), to: '/evv', icon: '✅' },
        { label: t('settings'), to: '/settings', icon: '⚙️' },
      ],
    },
  ]

  const initials = userName
    .split(' ')
    .map((n) => n[0])
    .join('')
    .toUpperCase()
    .slice(0, 2)

  return (
    <aside
      className="flex flex-col w-[200px] shrink-0 h-screen overflow-y-auto"
      style={{ background: '#1a1a24' }}
    >
      {/* Logo */}
      <div className="px-5 py-6">
        <div className="text-base font-bold tracking-tight">
          <span className="text-white">{t('wordmarkPrefix')}</span>
          <span style={{ color: '#1a9afa' }}>{t('wordmarkDot')}</span>
          <span className="text-white">{t('wordmarkSuffix')}</span>
        </div>
        <div className="text-[11px] mt-0.5" style={{ color: '#94a3b8' }}>
          {t('agencySubtitle')}
        </div>
      </div>

      {/* Nav */}
      <nav className="flex-1 px-3 pb-4">
        {NAV_SECTIONS.map(({ section, items }) => (
          <div key={section} className="mb-4">
            <div
              className="px-2 mb-1 text-[9px] font-bold uppercase tracking-[0.1em]"
              style={{ color: '#747480' }}
            >
              {section}
            </div>
            {items.map((item) => {
              // Use stable route path (not translated label) to detect dashboard item
              const showBadge = item.to === '/dashboard' && redEvvCount > 0
              return (
                <NavLink
                  key={item.to}
                  to={item.to}
                  className={({ isActive }) =>
                    [
                      'flex items-center gap-2.5 px-3 py-2 rounded text-[13px] font-medium mb-0.5',
                      isActive
                        ? 'text-white font-semibold'
                        : 'text-[#94a3b8] hover:text-white',
                    ].join(' ')
                  }
                  style={({ isActive }) =>
                    isActive ? { background: '#1a9afa' } : undefined
                  }
                >
                  <span className="text-base leading-none">{item.icon}</span>
                  <span className="flex-1">{item.label}</span>
                  {showBadge && (
                    <span className="w-4 h-4 rounded-full bg-red-600 text-white text-[10px] flex items-center justify-center font-bold">
                      {redEvvCount > 9 ? '9+' : redEvvCount}
                    </span>
                  )}
                </NavLink>
              )
            })}
          </div>
        ))}
      </nav>

      {/* User footer */}
      <div
        className="px-4 py-4 border-t flex items-center gap-3"
        style={{ borderColor: '#2e2e38' }}
      >
        <div
          className="w-8 h-8 rounded-full flex items-center justify-center text-[11px] font-bold text-white shrink-0"
          style={{ background: '#1a9afa' }}
        >
          {initials}
        </div>
        <div className="overflow-hidden">
          <div className="text-white text-[12px] font-medium truncate">{userName}</div>
          <div className="text-[11px] capitalize truncate" style={{ color: '#94a3b8' }}>
            {userRole.toLowerCase()}
          </div>
        </div>
      </div>
    </aside>
  )
}
