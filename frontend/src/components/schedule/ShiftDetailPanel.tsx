import { useTranslation } from 'react-i18next'
import type { EvvComplianceStatus } from '../../types/api'
import { useShiftDetail, useAssignCaregiver, useBroadcastShift, useClockIn, useGetCandidates } from '../../hooks/useShifts'
import { useAllClients } from '../../hooks/useClients'
import { useAllCaregivers } from '../../hooks/useCaregivers'
import { usePanelStore } from '../../store/panelStore'
import { formatLocalTime } from '../../utils/dateFormat'

const EVV_CLASS: Record<EvvComplianceStatus, string> = {
  RED: 'bg-red-50 text-red-600',
  YELLOW: 'bg-yellow-50 text-yellow-600',
  GREEN: 'bg-green-50 text-green-600',
  GREY: 'bg-slate-50 text-text-muted',
  EXEMPT: 'bg-slate-50 text-text-muted',
  PORTAL_SUBMIT: 'bg-green-50 text-green-600',
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
  const { closePanel, openPanel } = usePanelStore()

  const { data: shift, isLoading, error } = useShiftDetail(shiftId)
  const { clientMap } = useAllClients()
  const { caregiverMap } = useAllCaregivers()
  const assignMutation = useAssignCaregiver()
  const broadcastMutation = useBroadcastShift()
  const clockInMutation = useClockIn()

  const { data: candidates } = useGetCandidates(shiftId)

  const EVV_LABEL: Record<EvvComplianceStatus, string> = {
    RED: t('evvNonCompliant'),
    YELLOW: t('evvAttention'),
    GREEN: t('evvCompliant'),
    GREY: t('evvNotStarted'),
    EXEMPT: t('evvExempt'),
    PORTAL_SUBMIT: t('evvPortalSubmit'),
  }

  if (isLoading) {
    return (
      <div className="p-8 text-text-secondary">
        <button type="button" className="text-[13px] mb-4 hover:underline text-blue" onClick={closePanel}>
          {backLabel}
        </button>
        <p className="text-[13px]">{tCommon('loading')}</p>
      </div>
    )
  }

  if (error || !shift) {
    return (
      <div className="p-8 text-text-secondary">
        <button type="button" className="text-[13px] mb-4 hover:underline text-blue" onClick={closePanel}>
          {backLabel}
        </button>
        <p className="text-[13px] text-red-600">{t('notFound')}</p>
      </div>
    )
  }

  const client = clientMap.get(shift.clientId)
  const caregiver = shift.caregiverId ? caregiverMap.get(shift.caregiverId) : null
  const evvStatus = shift.evv?.complianceStatus ?? 'GREY'

  return (
    <div className="flex flex-col h-full">
      {/* Header */}
      <div className="px-6 py-4 border-b border-border">
        <button
          type="button"
          className="text-[13px] mb-2 hover:underline text-blue"
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
      <div className={`mx-6 mt-4 px-4 py-3 text-[13px] font-semibold ${EVV_CLASS[evvStatus]}`}>
        {EVV_LABEL[evvStatus]}
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

        {/* AI Candidates — shown for OPEN shifts without a caregiver */}
        {!shift.caregiverId && candidates && candidates.length > 0 && (
          <div className="mt-6">
            <h3 className="text-[9px] font-bold uppercase tracking-[0.1em] text-text-secondary mb-3">
              {t('sectionAiMatch')}
            </h3>
            {candidates.slice(0, 5).map((candidate, i) => {
              const cg = caregiverMap.get(candidate.caregiverId)
              return (
                <div key={candidate.caregiverId} className="flex items-center gap-3 py-2 border-b border-border">
                  <span
                    className={`w-6 h-6 rounded-full flex items-center justify-center text-[11px] font-bold text-white shrink-0 ${i === 0 ? 'bg-blue' : 'bg-text-muted'}`}
                  >
                    {i + 1}
                  </span>
                  <div className="flex-1">
                    <div className="text-[13px] font-medium text-dark">
                      {cg ? `${cg.firstName} ${cg.lastName}` : candidate.caregiverId}
                    </div>
                    <div className="text-[11px] text-text-secondary">{candidate.explanation}</div>
                  </div>
                  <button
                    type="button"
                    disabled={assignMutation.isPending}
                    className="text-[12px] font-semibold disabled:opacity-50 text-blue"
                    onClick={() => assignMutation.mutate({ shiftId, caregiverId: candidate.caregiverId })}
                  >
                    {t('assign')}
                  </button>
                </div>
              )
            })}
          </div>
        )}
      </div>

      {/* Footer actions */}
      <div className="px-6 py-4 border-t border-border flex items-center gap-3">
        {shift.status === 'OPEN' && !shift.caregiverId && (
          <button
            type="button"
            disabled={broadcastMutation.isPending}
            className="px-4 py-2 text-[12px] font-bold bg-dark text-white hover:brightness-110 disabled:opacity-50"
            onClick={() => broadcastMutation.mutate(shiftId)}
          >
            {broadcastMutation.isPending ? '…' : t('broadcastShift')}
          </button>
        )}
        {shift.status === 'ASSIGNED' && !shift.evv?.timeIn && (
          <button
            type="button"
            disabled={clockInMutation.isPending}
            className="px-4 py-2 text-[12px] font-bold bg-dark text-white hover:brightness-110 disabled:opacity-50"
            onClick={() =>
              clockInMutation.mutate({
                shiftId,
                req: { locationLat: 0, locationLon: 0, verificationMethod: 'MANUAL', capturedOffline: false },
              })
            }
          >
            {clockInMutation.isPending ? '…' : t('addManualClockIn')}
          </button>
        )}
        {(shift.status === 'COMPLETED' || shift.status === 'IN_PROGRESS') && evvStatus === 'RED' && (
          <button
            type="button"
            className="px-4 py-2 text-[12px] font-bold bg-dark text-white hover:brightness-110"
          >
            {t('addManualClockIn')}
          </button>
        )}
        <button
          type="button"
          className="px-4 py-2 text-[12px] font-semibold border border-border text-dark"
          onClick={() =>
            openPanel('newShift', undefined, {
              backLabel: backLabel,
              prefill: {
                editShiftId: shiftId,
                clientId: shift.clientId,
                caregiverId: shift.caregiverId ?? '',
                serviceTypeId: shift.serviceTypeId,
                date: shift.scheduledStart.slice(0, 10),
                time: shift.scheduledStart.slice(11, 16),
                endTime: shift.scheduledEnd.slice(11, 16),
              },
            })
          }
        >
          {t('editShift')}
        </button>
      </div>
    </div>
  )
}
