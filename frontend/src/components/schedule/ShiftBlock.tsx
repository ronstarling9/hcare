import { useTranslation } from 'react-i18next'
import type { EvvComplianceStatus } from '../../types/api'

const EVV_BORDER_COLOR: Record<EvvComplianceStatus, string> = {
  RED: '#dc2626',
  YELLOW: '#ca8a04',
  GREEN: '#16a34a',
  GREY: '#94a3b8',
  EXEMPT: '#94a3b8',
  PORTAL_SUBMIT: '#94a3b8',
}

interface ShiftBlockProps {
  clientName: string
  caregiverName: string | null
  evvStatus: EvvComplianceStatus
  /** px from calendar top (6am = 0) */
  top: number
  /** px height (1 min = 1px) */
  height: number
  onClick: () => void
}

export function ShiftBlock({
  clientName,
  caregiverName,
  evvStatus,
  top,
  height,
  onClick,
}: ShiftBlockProps) {
  const { t } = useTranslation('common')
  const borderColor = EVV_BORDER_COLOR[evvStatus]

  return (
    <button
      type="button"
      onClick={onClick}
      className="absolute left-0.5 right-0.5 overflow-hidden rounded-sm bg-white text-left px-2 py-1 hover:brightness-95 transition-[filter]"
      style={{
        top,
        height: Math.max(height, 24),
        borderLeft: `3px solid ${borderColor}`,
        borderLeftColor: borderColor,
        borderTop: '1px solid #eaeaf2',
        borderRight: '1px solid #eaeaf2',
        borderBottom: '1px solid #eaeaf2',
      }}
    >
      <div className="text-[11px] font-semibold text-text-primary truncate leading-tight">
        {clientName}
      </div>
      <div className="text-[10px] text-text-secondary truncate leading-tight">
        {caregiverName ?? t('unassigned')}
      </div>
    </button>
  )
}
