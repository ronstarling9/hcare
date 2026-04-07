import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import type { ShiftDetailResponse, EvvComplianceStatus } from '../../types/api'
import { ShiftBlock } from './ShiftBlock'

const CALENDAR_START_HOUR = 6   // 6am
const CALENDAR_END_HOUR = 22    // 10pm
const PX_PER_HOUR = 60
const TOTAL_HOURS = CALENDAR_END_HOUR - CALENDAR_START_HOUR

const HOURS = Array.from({ length: TOTAL_HOURS }, (_, i) => CALENDAR_START_HOUR + i)

function getWeekDays(weekStart: Date): Date[] {
  return Array.from({ length: 7 }, (_, i) => {
    const d = new Date(weekStart)
    d.setDate(d.getDate() + i)
    return d
  })
}

function toMinutesSince6am(iso: string): number {
  const d = new Date(iso.includes('T') ? iso : iso + 'T00:00:00')
  return d.getHours() * 60 + d.getMinutes() - CALENDAR_START_HOUR * 60
}

function sameDay(isoA: string, dateB: Date): boolean {
  const a = new Date(isoA.includes('T') ? isoA : isoA + 'T00:00:00')
  return (
    a.getFullYear() === dateB.getFullYear() &&
    a.getMonth() === dateB.getMonth() &&
    a.getDate() === dateB.getDate()
  )
}

function evvStatus(shift: ShiftDetailResponse): EvvComplianceStatus {
  return shift.evv?.complianceStatus ?? 'GREY'
}

function isToday(d: Date): boolean {
  const now = new Date()
  return (
    d.getFullYear() === now.getFullYear() &&
    d.getMonth() === now.getMonth() &&
    d.getDate() === now.getDate()
  )
}

interface WeekCalendarProps {
  weekStart: Date
  shifts: ShiftDetailResponse[]
  clientMap: Map<string, { firstName: string; lastName: string }>
  caregiverMap: Map<string, { firstName: string; lastName: string }>
  onShiftClick: (shiftId: string) => void
  onEmptySlotClick: (date: Date, hour: number) => void
}

export function WeekCalendar({
  weekStart,
  shifts,
  clientMap,
  caregiverMap,
  onShiftClick,
  onEmptySlotClick,
}: WeekCalendarProps) {
  const { t, i18n } = useTranslation('schedule')
  const days = useMemo(() => getWeekDays(weekStart), [weekStart])

  return (
    <div className="flex overflow-auto" style={{ height: `${TOTAL_HOURS * PX_PER_HOUR + 40}px` }}>
      {/* Time column */}
      <div className="shrink-0 w-16 bg-surface border-r border-border">
        {/* Header spacer */}
        <div className="h-10 border-b border-border" />
        {/* Time labels — formatted with Intl using the active i18n locale */}
        {HOURS.map((h) => (
          <div
            key={h}
            className="relative"
            style={{ height: PX_PER_HOUR }}
          >
            <span
              className="absolute -top-2 right-2 text-[10px] text-text-secondary"
            >
              {new Date(2000, 0, 1, h).toLocaleTimeString(i18n.language, { hour: 'numeric' })}
            </span>
          </div>
        ))}
      </div>

      {/* Day columns */}
      {days.map((day, dayIdx) => {
        const today = isToday(day)
        const isWeekend = dayIdx >= 5
        const dayShifts = shifts.filter((s) => sameDay(s.scheduledStart, day))

        return (
          <div
            key={day.toISOString()}
            className="flex-1 border-r border-border min-w-0"
            style={{ background: today ? '#f0f8ff' : isWeekend ? '#f9f9fc' : '#ffffff' }}
          >
            {/* Day header — weekday via Intl, date number via getDate() */}
            <div className="h-10 flex flex-col items-center justify-center border-b border-border">
              <span className="text-[9px] font-bold uppercase text-text-secondary">
                {day.toLocaleDateString(i18n.language, { weekday: 'short' })}
              </span>
              <span
                className={[
                  'text-[13px] font-semibold w-6 h-6 flex items-center justify-center rounded-full',
                  today ? 'text-white' : 'text-text-primary',
                ].join(' ')}
                style={today ? { background: '#1a9afa' } : undefined}
              >
                {day.getDate()}
              </span>
            </div>

            {/* Grid + shift blocks */}
            <div
              className="relative"
              style={{ height: TOTAL_HOURS * PX_PER_HOUR }}
            >
              {/* Hour grid lines */}
              {HOURS.map((h) => (
                <div
                  key={h}
                  className="absolute left-0 right-0 border-t border-border"
                  style={{ top: (h - CALENDAR_START_HOUR) * PX_PER_HOUR }}
                />
              ))}

              {/* Empty slot click targets (one per hour) */}
              {HOURS.map((h) => (
                <button
                  key={h}
                  type="button"
                  className="absolute left-0 right-0 opacity-0 hover:opacity-100 hover:bg-blue-50 transition-opacity"
                  style={{
                    top: (h - CALENDAR_START_HOUR) * PX_PER_HOUR,
                    height: PX_PER_HOUR,
                  }}
                  onClick={() => onEmptySlotClick(day, h)}
                  aria-label={t('newShiftAt', { hour: h })}
                />
              ))}

              {/* Shift blocks */}
              {dayShifts.map((shift) => {
                const client = clientMap.get(shift.clientId)
                const caregiver = shift.caregiverId ? caregiverMap.get(shift.caregiverId) : null
                const startMin = toMinutesSince6am(shift.scheduledStart)
                const endMin = toMinutesSince6am(shift.scheduledEnd)
                const top = Math.max(0, (startMin / 60) * PX_PER_HOUR)
                const height = Math.max(24, ((endMin - startMin) / 60) * PX_PER_HOUR)

                return (
                  <ShiftBlock
                    key={shift.id}
                    shiftId={shift.id}
                    clientName={client ? `${client.firstName} ${client.lastName}` : shift.clientId}
                    caregiverName={caregiver ? `${caregiver.firstName} ${caregiver.lastName}` : null}
                    evvStatus={evvStatus(shift)}
                    top={top}
                    height={height}
                    onClick={() => onShiftClick(shift.id)}
                  />
                )
              })}
            </div>
          </div>
        )
      })}
    </div>
  )
}
