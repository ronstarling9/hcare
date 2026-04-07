import { useTranslation } from 'react-i18next'
import type { DashboardVisitRow, EvvComplianceStatus } from '../../types/api'
import { usePanelStore } from '../../store/panelStore'

const STATUS_BORDER: Record<EvvComplianceStatus, string> = {
  RED: '#dc2626',
  YELLOW: '#ca8a04',
  GREEN: '#16a34a',
  GREY: '#94a3b8',
  EXEMPT: '#94a3b8',
  PORTAL_SUBMIT: '#16a34a',
}

const STATUS_PILL_BG: Record<EvvComplianceStatus, string> = {
  RED: '#fef2f2',
  YELLOW: '#fefce8',
  GREEN: '#f0fdf4',
  GREY: '#f8fafc',
  EXEMPT: '#f8fafc',
  PORTAL_SUBMIT: '#f0fdf4',
}

const STATUS_PILL_COLOR: Record<EvvComplianceStatus, string> = {
  RED: '#dc2626',
  YELLOW: '#ca8a04',
  GREEN: '#16a34a',
  GREY: '#94a3b8',
  EXEMPT: '#94a3b8',
  PORTAL_SUBMIT: '#16a34a',
}

function formatTime(iso: string, locale: string): string {
  return new Date(iso).toLocaleTimeString(locale, { hour: 'numeric', minute: '2-digit' })
}

interface VisitListProps {
  visits: DashboardVisitRow[]
}

export function VisitList({ visits }: VisitListProps) {
  const { t, i18n } = useTranslation('dashboard')
  const tCommon = useTranslation('common').t
  const { openPanel } = usePanelStore()

  if (visits.length === 0) {
    return <p className="px-6 py-8 text-text-secondary text-[13px]">{t('noVisits')}</p>
  }

  return (
    <div>
      {visits.map((row) => (
        <button
          key={row.shiftId}
          type="button"
          onClick={() => openPanel('shift', row.shiftId, { backLabel: t('backLabel') })}
          className="w-full flex items-center gap-3 px-6 py-3 border-b border-border hover:bg-surface text-left transition-colors"
          style={{ borderLeft: `3px solid ${STATUS_BORDER[row.evvStatus]}` }}
        >
          {/* EVV dot */}
          <span
            className="w-2 h-2 rounded-full shrink-0"
            style={{ background: STATUS_BORDER[row.evvStatus] }}
          />
          {/* Client + caregiver */}
          <div className="flex-1 min-w-0">
            <div className="text-[13px] font-semibold text-dark truncate">
              {row.clientFirstName} {row.clientLastName}
            </div>
            <div className="text-[11px] text-text-secondary truncate">
              {row.caregiverFirstName
                ? `${row.caregiverFirstName} ${row.caregiverLastName} · ${row.serviceTypeName}`
                : `${tCommon('unassigned')} · ${row.serviceTypeName}`}
            </div>
          </div>
          {/* Time */}
          <div className="text-[11px] text-text-secondary shrink-0">
            {formatTime(row.scheduledStart, i18n.language)} – {formatTime(row.scheduledEnd, i18n.language)}
          </div>
          {/* EVV pill */}
          <span
            className="text-[10px] font-bold px-2 py-0.5 shrink-0"
            style={{
              background: STATUS_PILL_BG[row.evvStatus],
              color: STATUS_PILL_COLOR[row.evvStatus],
            }}
          >
            {row.evvStatus}
          </span>
        </button>
      ))}
    </div>
  )
}
