import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { WeekCalendar } from './WeekCalendar'
import { AlertStrip } from './AlertStrip'
import { mockShifts, mockClientMap, mockCaregiverMap, mockDashboard } from '../../mock/data'
import { usePanelStore } from '../../store/panelStore'

function getMonday(d: Date): Date {
  const date = new Date(d)
  const day = date.getDay()
  const diff = day === 0 ? -6 : 1 - day
  date.setDate(date.getDate() + diff)
  date.setHours(0, 0, 0, 0)
  return date
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

  function handleNewShift(date: Date, hour: number) {
    // Use local date parts — toISOString() returns UTC and would give the wrong date
    // for users in UTC−N timezones clicking late-evening slots.
    const dateStr = [
      date.getFullYear(),
      String(date.getMonth() + 1).padStart(2, '0'),
      String(date.getDate()).padStart(2, '0'),
    ].join('-')
    const timeStr = `${String(hour).padStart(2, '0')}:00`
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

      {/* Alert strip */}
      <AlertStrip
        redCount={mockDashboard.redEvvCount}
        yellowCount={mockDashboard.yellowEvvCount}
        uncoveredCount={mockDashboard.uncoveredCount}
        lateClockInCount={0}
      />

      {/* Calendar */}
      <div className="flex-1 overflow-auto">
        <WeekCalendar
          weekStart={weekStart}
          shifts={mockShifts}
          clientMap={mockClientMap}
          caregiverMap={mockCaregiverMap}
          onShiftClick={(id) => openPanel('shift', id, { backLabel: t('backLabel') })}
          onEmptySlotClick={handleNewShift}
        />
      </div>
    </div>
  )
}
