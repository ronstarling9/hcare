import { useTranslation } from 'react-i18next'
import type { DashboardAlert } from '../../types/api'
import { useNavigate } from 'react-router-dom'

// Date-only ISO strings (e.g. '2026-04-10') are parsed as UTC-midnight by the spec,
// causing off-by-one display in UTC-N timezones. Appending T12:00:00 keeps the date
// in the correct calendar day across all UTC-14 to UTC+14 zones.
function formatLocalDate(dateStr: string, options?: Intl.DateTimeFormatOptions): string {
  return new Date(dateStr + 'T12:00:00').toLocaleDateString('en-US', options)
}

function isUrgent(dueDate: string): boolean {
  const days = (new Date(dueDate).getTime() - Date.now()) / (1000 * 60 * 60 * 24)
  return days <= 7
}

interface AlertsColumnProps {
  alerts: DashboardAlert[]
}

export function AlertsColumn({ alerts }: AlertsColumnProps) {
  const { t } = useTranslation('dashboard')
  const navigate = useNavigate()

  if (alerts.length === 0) {
    return (
      <div className="p-4 text-[12px] text-text-secondary">{t('noAlerts')}</div>
    )
  }

  return (
    <div>
      <div className="px-4 py-3 border-b border-border">
        <span className="text-[9px] font-bold uppercase tracking-[0.1em] text-text-secondary">
          {t('alertsHeader')}
        </span>
      </div>
      <div>
        {alerts.map((alert) => {
          const urgent = isUrgent(alert.dueDate)
          return (
            <button
              key={`${alert.resourceId}-${alert.type}`}
              type="button"
              onClick={() =>
                navigate(
                  alert.resourceType === 'CAREGIVER'
                    ? `/caregivers`
                    : `/clients`
                )
              }
              className="w-full text-left px-4 py-3 border-b border-border hover:bg-surface transition-colors"
            >
              <div
                className="text-[12px] font-semibold truncate"
                style={{ color: urgent ? '#dc2626' : '#1a1a24' }}
              >
                {alert.subject}
              </div>
              <div className="text-[11px] text-text-secondary mt-0.5">{alert.detail}</div>
              <div className="text-[10px] mt-0.5" style={{ color: urgent ? '#dc2626' : '#94a3b8' }}>
                {t('due', { date: formatLocalDate(alert.dueDate, { month: 'short', day: 'numeric' }) })}
              </div>
            </button>
          )
        })}
      </div>
    </div>
  )
}
