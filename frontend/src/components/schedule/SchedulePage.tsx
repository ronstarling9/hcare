import { useState, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { WeekCalendar } from './WeekCalendar'
import { AlertStrip } from './AlertStrip'
import { usePanelStore } from '../../store/panelStore'
import { useShifts } from '../../hooks/useShifts'
import { useClients } from '../../hooks/useClients'
import { useCaregivers } from '../../hooks/useCaregivers'

function getMonday(d: Date): Date {
  const date = new Date(d)
  const day = date.getDay()
  const diff = day === 0 ? -6 : 1 - day
  date.setDate(date.getDate() + diff)
  date.setHours(0, 0, 0, 0)
  return date
}

// Formats a local Date as an ISO-8601 LocalDateTime string without timezone offset.
// Uses local date/time parts (not toISOString) so the boundary is correct in all timezones.
function toLocalISODateTime(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T00:00:00`
}

function formatWeekRange(monday: Date, locale: string): string {
  const sunday = new Date(monday)
  sunday.setDate(sunday.getDate() + 6)
  const fmt = (d: Date) =>
    d.toLocaleDateString(locale, { month: 'short', day: 'numeric', year: 'numeric' })
  return `${fmt(monday)} – ${fmt(sunday)}`
}

export function SchedulePage() {
  const { t, i18n } = useTranslation('schedule')
  const [weekStart, setWeekStart] = useState(() => getMonday(new Date()))
  const { openPanel } = usePanelStore()

  // ISO strings derived from weekStart for API params — weekStart (Date) is passed to WeekCalendar
  const weekStartStr = useMemo(() => toLocalISODateTime(weekStart), [weekStart])
  const weekEndStr = useMemo(() => {
    const end = new Date(weekStart)
    end.setDate(end.getDate() + 7)
    return toLocalISODateTime(end)
  }, [weekStart])

  const { data: shiftsPage, isLoading: shiftsLoading } = useShifts(weekStartStr, weekEndStr)
  const { clientMap } = useClients()
  const { caregiverMap } = useCaregivers()

  const shifts = shiftsPage?.content ?? []

  function prevWeek() {
    setWeekStart((d) => {
      const prev = new Date(d)
      prev.setDate(prev.getDate() - 7)
      return prev
    })
  }

  function nextWeek() {
    setWeekStart((d) => {
      const next = new Date(d)
      next.setDate(next.getDate() + 7)
      return next
    })
  }

  // Clicking an empty slot pre-fills the new shift form with the clicked date + hour
  function handleNewShift(date: Date, hour: number) {
    const dateStr = [
      date.getFullYear(),
      String(date.getMonth() + 1).padStart(2, '0'),
      String(date.getDate()).padStart(2, '0'),
    ].join('-')
    const timeStr = `${String(hour).padStart(2, '00')}:00`
    openPanel('newShift', undefined, {
      prefill: { date: dateStr, time: timeStr },
      backLabel: t('backLabel'),
    })
  }

  return (
    <div className="flex flex-col h-full">
      {/* Top bar */}
      <div className="flex items-center gap-4 px-6 py-4 bg-white border-b border-border">
        <h1 className="text-[16px] font-bold tracking-[-0.02em] text-dark mr-2">{t('pageTitle')}</h1>
        <span className="text-[13px] text-text-secondary">{formatWeekRange(weekStart, i18n.language)}</span>
        <div className="ml-auto flex items-center gap-2">
          <button
            type="button"
            onClick={prevWeek}
            className="px-3 py-1.5 text-[12px] font-semibold border border-border text-dark hover:brightness-95 transition-[filter]"
          >
            {t('prevWeek')}
          </button>
          <button
            type="button"
            onClick={nextWeek}
            className="px-3 py-1.5 text-[12px] font-semibold border border-border text-dark hover:brightness-95 transition-[filter]"
          >
            {t('nextWeek')}
          </button>
          <button
            type="button"
            className="px-3 py-1.5 text-[12px] font-semibold border border-border text-dark hover:brightness-95 ml-2"
            onClick={() => alert(t('broadcastOpenAlert'))}
          >
            {t('broadcastOpen')}
          </button>
          <button
            type="button"
            className="px-4 py-1.5 text-[12px] font-bold bg-dark text-white hover:brightness-110"
            onClick={() => openPanel('newShift', undefined, { backLabel: t('backLabel') })}
          >
            {t('newShift')}
          </button>
        </div>
      </div>

      {/* Alert strip — EVV counts wired in a later phase; zero placeholders suppress the bar */}
      <AlertStrip
        redCount={0}
        yellowCount={0}
        uncoveredCount={0}
        lateClockInCount={0}
      />

      {/* Calendar */}
      <div className="flex-1 overflow-auto">
        {shiftsLoading ? (
          <div className="flex items-center justify-center h-64">
            <span className="text-[13px] text-text-muted">{t('loading')}</span>
          </div>
        ) : (
          <WeekCalendar
            weekStart={weekStart}
            shifts={shifts}
            clientMap={clientMap}
            caregiverMap={caregiverMap}
            onShiftClick={(id) => openPanel('shift', id, { backLabel: t('backLabel') })}
            onEmptySlotClick={handleNewShift}
          />
        )}
      </div>
    </div>
  )
}
