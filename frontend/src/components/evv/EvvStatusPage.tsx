import { useTranslation } from 'react-i18next'
import type { EvvComplianceStatus } from '../../types/api'
import { mockEvvHistory } from '../../mock/data'
import { usePanelStore } from '../../store/panelStore'
import { formatLocalDate, formatLocalTime } from '../../utils/dateFormat'

const STATUS_COLOR: Record<EvvComplianceStatus, string> = {
  RED: '#dc2626',
  YELLOW: '#ca8a04',
  GREEN: '#16a34a',
  GREY: '#94a3b8',
  EXEMPT: '#94a3b8',
  PORTAL_SUBMIT: '#16a34a',
}

export function EvvStatusPage() {
  const { t, i18n } = useTranslation('evvStatus')
  const tCommon = useTranslation('common').t
  const { openPanel } = usePanelStore()

  return (
    <div className="flex flex-col h-full">
      <div className="flex items-center px-6 py-4 bg-white border-b border-border">
        <h1 className="text-[16px] font-bold tracking-[-0.02em] text-dark">{t('pageTitle')}</h1>
        <span className="ml-3 text-[12px] text-text-secondary">{t('subtitle')}</span>
      </div>
      <div className="flex-1 overflow-auto">
        <table className="w-full text-[13px]">
          <thead>
            <tr className="border-b border-border bg-surface">
              {[t('colClient'), t('colCaregiver'), t('colService'), t('colDate'), t('colClockIn'), t('colClockOut'), t('colStatus')].map((h) => (
                <th
                  key={h}
                  className="text-left px-6 py-2 text-[9px] font-bold uppercase tracking-[0.1em] text-text-secondary"
                >
                  {h}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {mockEvvHistory.map((row) => (
              <tr
                key={row.shiftId}
                className="border-b border-border hover:bg-surface cursor-pointer"
                style={{ borderLeft: `3px solid ${STATUS_COLOR[row.evvStatus]}` }}
                onClick={() => openPanel('shift', row.shiftId, { backLabel: t('backLabel') })}
              >
                <td className="px-6 py-3 font-medium text-dark">
                  {row.clientFirstName} {row.clientLastName}
                </td>
                <td className="px-6 py-3 text-text-secondary">
                  {row.caregiverFirstName
                    ? `${row.caregiverFirstName} ${row.caregiverLastName}`
                    : tCommon('noDash')}
                </td>
                <td className="px-6 py-3 text-text-secondary">{row.serviceTypeName}</td>
                <td className="px-6 py-3 text-text-secondary">
                  {formatLocalDate(row.scheduledStart.slice(0, 10), i18n.language, { month: 'short', day: 'numeric' })}
                </td>
                <td className="px-6 py-3 text-text-secondary">{formatLocalTime(row.timeIn, i18n.language)}</td>
                <td className="px-6 py-3 text-text-secondary">{formatLocalTime(row.timeOut, i18n.language)}</td>
                <td className="px-6 py-3">
                  <span
                    className="text-[11px] font-bold px-2 py-0.5"
                    style={{
                      color: STATUS_COLOR[row.evvStatus],
                      background: `${STATUS_COLOR[row.evvStatus]}18`,
                    }}
                  >
                    {row.evvStatus}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
