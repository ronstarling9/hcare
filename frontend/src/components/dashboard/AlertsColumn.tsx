import { useTranslation } from 'react-i18next'
import type { DashboardAlert } from '../../types/api'
import { usePanelStore } from '../../store/panelStore'
import { formatLocalDate } from '../../utils/dateFormat'

function isUrgent(dueDate: string): boolean {
  const days = (new Date(`${dueDate}T12:00:00`).getTime() - Date.now()) / (1000 * 60 * 60 * 24)
  return days <= 7
}

interface AlertsColumnProps {
  alerts: DashboardAlert[]
}

export function AlertsColumn({ alerts }: AlertsColumnProps) {
  const { t, i18n } = useTranslation('dashboard')
  const { openPanel } = usePanelStore()

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
                alert.resourceType === 'CAREGIVER'
                  ? openPanel('caregiver', alert.resourceId)
                  : openPanel('client', alert.resourceId)
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
                {t('due', { date: formatLocalDate(alert.dueDate, i18n.language, { month: 'short', day: 'numeric' }) })}
              </div>
            </button>
          )
        })}
      </div>
    </div>
  )
}
