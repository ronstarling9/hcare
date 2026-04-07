import { useTranslation } from 'react-i18next'
import type { EvvComplianceStatus, ShiftDetailResponse } from '../../types/api'
import { mockShifts, mockClientMap, mockCaregiverMap } from '../../mock/data'
import { usePanelStore } from '../../store/panelStore'
import { formatLocalTime } from '../../utils/dateFormat'

const EVV_BG: Record<EvvComplianceStatus, string> = {
  RED: '#fef2f2',
  YELLOW: '#fefce8',
  GREEN: '#f0fdf4',
  GREY: '#f8fafc',
  EXEMPT: '#f8fafc',
  PORTAL_SUBMIT: '#f0fdf4',
}

const EVV_TEXT: Record<EvvComplianceStatus, string> = {
  RED: '#dc2626',
  YELLOW: '#ca8a04',
  GREEN: '#16a34a',
  GREY: '#94a3b8',
  EXEMPT: '#94a3b8',
  PORTAL_SUBMIT: '#16a34a',
}

function formatDate(iso: string, locale: string): string {
  return new Date(iso).toLocaleDateString(locale, {
    weekday: 'short',
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  })
}

interface ShiftDetailPanelProps {
  shiftId: string
  backLabel: string
}

export function ShiftDetailPanel({ shiftId, backLabel }: ShiftDetailPanelProps) {
  const { t, i18n } = useTranslation('shiftDetail')
  const tCommon = useTranslation('common').t
  const { closePanel } = usePanelStore()

  const EVV_LABEL: Record<EvvComplianceStatus, string> = {
    RED: t('evvNonCompliant'),
    YELLOW: t('evvAttention'),
    GREEN: t('evvCompliant'),
    GREY: t('evvNotStarted'),
    EXEMPT: t('evvExempt'),
    PORTAL_SUBMIT: t('evvPortalSubmit'),
  }

  const shift: ShiftDetailResponse | undefined = mockShifts.find((s) => s.id === shiftId)

  if (!shift) {
    return (
      <div className="p-8 text-text-secondary">
        <button type="button" className="text-blue text-[13px] mb-4" onClick={closePanel}>
          {backLabel}
        </button>
        <p>{t('notFound')}</p>
      </div>
    )
  }

  const client = mockClientMap.get(shift.clientId)
  const caregiver = shift.caregiverId ? mockCaregiverMap.get(shift.caregiverId) : null
  const status = shift.evv?.complianceStatus ?? 'GREY'

  return (
    <div className="flex flex-col h-full">
      {/* Header */}
      <div className="px-6 py-4 border-b border-border">
        <button
          type="button"
          className="text-[13px] mb-2 hover:underline"
          style={{ color: '#1a9afa' }}
          onClick={closePanel}
        >
          {backLabel}
        </button>
        <h2 className="text-[16px] font-bold text-dark">
          {client ? `${client.firstName} ${client.lastName}` : t('unknownClient')}
        </h2>
        <p className="text-[12px] text-text-secondary mt-0.5">
          {formatDate(shift.scheduledStart, i18n.language)} · {formatLocalTime(shift.scheduledStart, i18n.language)} – {formatLocalTime(shift.scheduledEnd, i18n.language)} · {t('staticService')}
        </p>
      </div>

      {/* EVV Status badge */}
      <div
        className="mx-6 mt-4 px-4 py-3 text-[13px] font-semibold"
        style={{ background: EVV_BG[status], color: EVV_TEXT[status] }}
      >
        {EVV_LABEL[status]}
        {shift.evv === null && t('visitNotStarted')}
      </div>

      {/* Body */}
      <div className="flex-1 overflow-auto px-6 py-4">
        <div className="grid grid-cols-2 gap-6">
          {/* Left: Visit Details */}
          <div>
            <h3 className="text-[9px] font-bold uppercase tracking-[0.1em] text-text-secondary mb-3">
              {t('sectionVisitDetails')}
            </h3>
            <div className="space-y-2">
              <div>
                <div className="text-[10px] text-text-secondary">{t('fieldClient')}</div>
                <div className="text-[13px] text-dark">
                  {client ? `${client.firstName} ${client.lastName}` : tCommon('noDash')}
                </div>
              </div>
              <div>
                <div className="text-[10px] text-text-secondary">{t('fieldCaregiver')}</div>
                <div className="text-[13px] text-dark">
                  {caregiver ? `${caregiver.firstName} ${caregiver.lastName}` : tCommon('unassigned')}
                </div>
              </div>
              <div>
                <div className="text-[10px] text-text-secondary">{t('fieldService')}</div>
                <div className="text-[13px] text-dark">{t('staticService')}</div>
              </div>
              <div>
                <div className="text-[10px] text-text-secondary">{t('fieldStatus')}</div>
                <div className="text-[13px] text-dark">{shift.status.replace(/_/g, ' ')}</div>
              </div>
            </div>
          </div>

          {/* Right: EVV Record */}
          <div>
            <h3 className="text-[9px] font-bold uppercase tracking-[0.1em] text-text-secondary mb-3">
              {t('sectionEvvRecord')}
            </h3>
            <div className="space-y-2">
              <div>
                <div className="text-[10px] text-text-secondary">{t('fieldClockIn')}</div>
                <div className="text-[13px] text-dark">{formatLocalTime(shift.evv?.timeIn ?? null, i18n.language)}</div>
              </div>
              <div>
                <div className="text-[10px] text-text-secondary">{t('fieldClockOut')}</div>
                <div className="text-[13px] text-dark">{formatLocalTime(shift.evv?.timeOut ?? null, i18n.language)}</div>
              </div>
              <div>
                <div className="text-[10px] text-text-secondary">{t('fieldMethod')}</div>
                <div className="text-[13px] text-dark">{shift.evv?.verificationMethod ?? tCommon('noDash')}</div>
              </div>
              <div>
                <div className="text-[10px] text-text-secondary">{t('fieldOffline')}</div>
                <div className="text-[13px] text-dark">
                  {shift.evv?.capturedOffline ? tCommon('yes') : tCommon('no')}
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* AI Candidates (shown when OPEN) */}
        {shift.status === 'OPEN' && (
          <div className="mt-6">
            <h3 className="text-[9px] font-bold uppercase tracking-[0.1em] text-text-secondary mb-3">
              {t('sectionAiMatch')}
            </h3>
            {[
              { rank: 1, name: t('candidate1Name'), reason: t('candidate1Reason') },
              { rank: 2, name: t('candidate2Name'), reason: t('candidate2Reason') },
            ].map((c) => (
              <div key={c.rank} className="flex items-center gap-3 py-2 border-b border-border">
                <span
                  className="w-6 h-6 rounded-full flex items-center justify-center text-[11px] font-bold text-white shrink-0"
                  style={{ background: c.rank === 1 ? '#1a9afa' : '#94a3b8' }}
                >
                  {c.rank}
                </span>
                <div className="flex-1">
                  <div className="text-[13px] font-medium text-dark">{c.name}</div>
                  <div className="text-[11px] text-text-secondary">{c.reason}</div>
                </div>
                <button type="button" className="text-[12px] font-semibold" style={{ color: '#1a9afa' }}>
                  {t('assign')}
                </button>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Footer actions */}
      <div className="px-6 py-4 border-t border-border flex items-center gap-3">
        {shift.status === 'OPEN' && (
          <button
            type="button"
            className="px-4 py-2 text-[12px] font-bold bg-dark text-white hover:brightness-110"
          >
            {t('assignCaregiver')}
          </button>
        )}
        {(shift.status === 'COMPLETED' || shift.status === 'IN_PROGRESS') &&
          status === 'RED' && (
            <>
              <button
                type="button"
                className="px-4 py-2 text-[12px] font-bold bg-dark text-white hover:brightness-110"
              >
                {t('addManualClockIn')}
              </button>
              <button
                type="button"
                className="px-4 py-2 text-[12px] font-semibold border border-border text-dark"
              >
                {t('editShift')}
              </button>
              <button
                type="button"
                className="ml-auto px-4 py-2 text-[12px] font-semibold text-red-600 border border-red-200"
              >
                {t('markAsMissed')}
              </button>
            </>
          )}
        {shift.status === 'ASSIGNED' && (
          <button
            type="button"
            className="px-4 py-2 text-[12px] font-semibold border border-border text-dark"
          >
            {t('editShift')}
          </button>
        )}
        {shift.status === 'COMPLETED' && status === 'GREEN' && (
          <>
            <button
              type="button"
              className="px-4 py-2 text-[12px] font-semibold border border-border text-dark"
            >
              {t('editShift')}
            </button>
            <button
              type="button"
              className="px-4 py-2 text-[12px] font-semibold border border-border text-dark"
            >
              {t('viewCareNotes')}
            </button>
          </>
        )}
      </div>
    </div>
  )
}
