import { useState, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { useQueryClient } from '@tanstack/react-query'
import { listOpenShifts, broadcastShift } from '../../api/shifts'
import type { ShiftSummaryResponse } from '../../types/api'

type Phase = 'loading' | 'confirm' | 'broadcasting' | 'done' | 'error'
type RowResult = 'idle' | 'pending' | 'success' | 'error'

interface Props {
  open: boolean
  onClose: () => void
  weekStart: string
  weekEnd: string
  clientMap: Map<string, { firstName: string; lastName: string }>
}

function formatShiftTime(iso: string): string {
  const d = new Date(iso)
  return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
}

function formatShiftDate(iso: string): string {
  const d = new Date(iso)
  return d.toLocaleDateString([], { month: 'short', day: 'numeric' })
}

export function BroadcastOpenModal({ open, onClose, weekStart, weekEnd, clientMap }: Props) {
  const { t } = useTranslation('schedule')
  const queryClient = useQueryClient()
  const [phase, setPhase] = useState<Phase>('loading')
  const [openShifts, setOpenShifts] = useState<ShiftSummaryResponse[]>([])
  const [results, setResults] = useState<Record<string, RowResult>>({})

  useEffect(() => {
    if (!open) return
    setPhase('loading')
    setResults({})
    listOpenShifts(weekStart, weekEnd)
      .then((shifts) => {
        setOpenShifts(shifts)
        setPhase('confirm')
      })
      .catch(() => setPhase('error'))
  }, [open, weekStart, weekEnd])

  async function handleConfirm() {
    const initial: Record<string, RowResult> = {}
    for (const s of openShifts) initial[s.id] = 'idle'
    setResults(initial)
    setPhase('broadcasting')

    for (const shift of openShifts) {
      setResults((prev) => ({ ...prev, [shift.id]: 'pending' }))
      try {
        await broadcastShift(shift.id)
        setResults((prev) => ({ ...prev, [shift.id]: 'success' }))
      } catch {
        setResults((prev) => ({ ...prev, [shift.id]: 'error' }))
      }
    }

    setPhase('done')
    queryClient.invalidateQueries({ queryKey: ['shifts'] })
  }

  function handleClose() {
    onClose()
  }

  if (!open) return null

  const successCount = Object.values(results).filter((r) => r === 'success').length
  const failCount = Object.values(results).filter((r) => r === 'error').length

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div
        className="bg-white rounded-xl shadow-xl w-full max-w-md mx-4 overflow-hidden"
        role="dialog"
        aria-modal="true"
        aria-labelledby="broadcast-modal-title"
      >
        {/* Header */}
        <div className="px-6 pt-5 pb-3 border-b border-border">
          <h2 id="broadcast-modal-title" className="text-[15px] font-bold text-dark">{t('broadcastModal.title')}</h2>
        </div>

        {/* Body */}
        <div className="px-6 py-4 max-h-[60vh] overflow-y-auto">
          {phase === 'loading' && (
            <p className="text-[13px] text-text-muted py-4 text-center">
              {t('broadcastModal.loading')}
            </p>
          )}

          {phase === 'error' && (
            <p className="text-[13px] text-red-600 py-4 text-center">
              {t('broadcastModal.loadError')}
            </p>
          )}

          {(phase === 'confirm') && openShifts.length === 0 && (
            <p className="text-[13px] text-text-secondary py-4 text-center">
              {t('broadcastModal.noOpenShifts')}
            </p>
          )}

          {(phase === 'confirm') && openShifts.length > 0 && (
            <>
              <p className="text-[13px] text-text-secondary mb-3">
                {t('broadcastModal.confirmSubtitle', { count: openShifts.length })}
              </p>
              <ul className="space-y-2">
                {openShifts.map((shift) => {
                  const client = clientMap.get(shift.clientId)
                  const clientName = client
                    ? `${client.firstName} ${client.lastName}`
                    : shift.clientId
                  return (
                    <li key={shift.id} className="flex items-center gap-3 py-2 border-b border-border last:border-0">
                      <span className="text-[12px] text-text-secondary w-20 shrink-0">
                        {formatShiftDate(shift.scheduledStart)}
                      </span>
                      <span className="text-[12px] text-text-secondary w-24 shrink-0">
                        {formatShiftTime(shift.scheduledStart)}–{formatShiftTime(shift.scheduledEnd)}
                      </span>
                      <span className="text-[13px] text-dark font-medium truncate">{clientName}</span>
                    </li>
                  )
                })}
              </ul>
            </>
          )}

          {(phase === 'broadcasting' || phase === 'done') && (
            <ul className="space-y-2">
              {openShifts.map((shift) => {
                const result = results[shift.id] ?? 'idle'
                const client = clientMap.get(shift.clientId)
                const clientName = client
                  ? `${client.firstName} ${client.lastName}`
                  : shift.clientId
                return (
                  <li key={shift.id} className="flex items-center gap-3 py-2 border-b border-border last:border-0">
                    <span className="w-5 shrink-0 text-center">
                      {result === 'idle' && <span className="text-text-muted">·</span>}
                      {result === 'pending' && (
                        <span className="inline-block w-3 h-3 border-2 border-blue-400 border-t-transparent rounded-full animate-spin" />
                      )}
                      {result === 'success' && <span className="text-green-500 text-[14px]">✓</span>}
                      {result === 'error' && <span className="text-red-500 text-[14px]">✗</span>}
                    </span>
                    <span className="text-[12px] text-text-secondary w-20 shrink-0">
                      {formatShiftDate(shift.scheduledStart)}
                    </span>
                    <span className="text-[13px] text-dark font-medium truncate">{clientName}</span>
                  </li>
                )
              })}
            </ul>
          )}

          {phase === 'done' && (
            <p className="text-[13px] text-text-secondary mt-4 text-center">
              {failCount === 0
                ? t('broadcastModal.doneAllSuccess', { count: successCount })
                : t('broadcastModal.doneSummary', { success: successCount, failed: failCount })}
            </p>
          )}
        </div>

        {/* Footer */}
        <div className="px-6 py-4 border-t border-border flex justify-end gap-3">
          {phase === 'error' && (
            <button
              type="button"
              onClick={handleClose}
              className="px-4 py-2 text-[13px] font-semibold border border-border text-dark rounded hover:brightness-95"
            >
              {t('broadcastModal.close')}
            </button>
          )}
          {phase === 'confirm' && openShifts.length === 0 && (
            <button
              type="button"
              onClick={handleClose}
              className="px-4 py-2 text-[13px] font-semibold border border-border text-dark rounded hover:brightness-95"
            >
              {t('broadcastModal.close')}
            </button>
          )}
          {phase === 'confirm' && openShifts.length > 0 && (
            <>
              <button
                type="button"
                onClick={handleClose}
                className="px-4 py-2 text-[13px] font-semibold border border-border text-dark rounded hover:brightness-95"
              >
                {t('broadcastModal.cancel')}
              </button>
              <button
                type="button"
                onClick={handleConfirm}
                className="px-4 py-2 text-[13px] font-bold bg-dark text-white rounded hover:brightness-110"
              >
                {t('broadcastModal.confirmBtn', { count: openShifts.length })}
              </button>
            </>
          )}
          {phase === 'broadcasting' && (
            <span className="text-[13px] text-text-muted self-center">
              {t('broadcastModal.broadcasting')}
            </span>
          )}
          {phase === 'done' && (
            <button
              type="button"
              onClick={handleClose}
              className="px-4 py-2 text-[13px] font-bold bg-dark text-white rounded hover:brightness-110"
            >
              {t('broadcastModal.close')}
            </button>
          )}
        </div>
      </div>
    </div>
  )
}
