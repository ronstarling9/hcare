import { useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { usePortalAuthStore } from '../store/portalAuthStore'
import { usePortalDashboard } from '../hooks/usePortalDashboard'
import type { TodayVisitDto, UpcomingVisitDto, LastVisitDto } from '../api/portal'

/** Formats a UTC ISO timestamp into a human-readable time in the given IANA timezone,
 *  with a short timezone abbreviation (e.g., "9:04 AM EDT"). */
function formatTime(utcIso: string, tz: string): string {
  const d = new Date(utcIso + (utcIso.includes('Z') ? '' : 'Z'))
  return d.toLocaleTimeString('en-US', {
    hour: 'numeric',
    minute: '2-digit',
    timeZone: tz,
    timeZoneName: 'short',
  })
}

function formatDate(utcIso: string, tz: string): string {
  const d = new Date(utcIso + (utcIso.includes('Z') ? '' : 'Z'))
  return d.toLocaleDateString('en-US', {
    weekday: 'short', month: 'short', day: 'numeric', timeZone: tz,
  })
}

function formatDuration(minutes: number): string {
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  if (h === 0) return `${m} min`
  if (m === 0) return `${h} hr`
  return `${h} hr ${m} min`
}

const LATE_THRESHOLD_MS = 15 * 60 * 1000 // 15-minute grace period before a visit is marked late

function isLate(visit: TodayVisitDto): boolean {
  if (visit.status !== 'GREY' || visit.clockedInAt) return false
  const scheduled = new Date(visit.scheduledStart + (visit.scheduledStart.includes('Z') ? '' : 'Z'))
  return Date.now() > scheduled.getTime() + LATE_THRESHOLD_MS
}

export default function PortalDashboardPage() {
  const { t } = useTranslation('portal')
  const navigate = useNavigate()
  const logout = usePortalAuthStore((s) => s.logout)
  const { data, isLoading, isError, error, refetch } = usePortalDashboard()

  // Handle 403 PORTAL_ACCESS_REVOKED — React Query v5 removed meta.onError, so we handle
  // side-effects from errors here in the component via useEffect.
  useEffect(() => {
    if (!isError || !error) return
    const status = (error as { response?: { status?: number } })?.response?.status
    if (status === 403) {
      logout()
      navigate('/portal/verify?reason=access_revoked', { replace: true })
    }
  }, [isError, error, logout, navigate])

  const status410 = (error as { response?: { status?: number } })?.response?.status === 410

  if (isLoading) {
    return (
      <div role="status" aria-live="polite" className="p-6 text-center text-[13px] text-text-secondary">
        {t('loading')}
      </div>
    )
  }

  if (status410) {
    return (
      <div className="p-6 text-center">
        <h1 className="text-[16px] font-bold text-text-primary mb-2">
          {t('careServicesConcludedHeading')}
        </h1>
        <p className="text-[14px] text-text-primary">
          {t('careServicesConcludedBody')}
        </p>
      </div>
    )
  }

  if (isError) {
    return (
      <div className="p-6 text-center">
        <p className="text-[13px] text-text-primary mb-3">{t('loadError')}</p>
        <button
          onClick={() => refetch()}
          className="text-[13px] text-text-primary border border-border px-4 py-2 min-h-[44px]"
        >
          {t('tapToRetry')}
        </button>
      </div>
    )
  }

  if (!data) return null

  const { clientFirstName, agencyTimezone: tz, todayVisit, upcomingVisits, lastVisit } = data
  const today = new Date().toLocaleDateString('en-US', {
    weekday: 'short', month: 'short', day: 'numeric', timeZone: tz,
  })

  return (
    <div className="bg-surface min-h-screen">
      {/* Page header */}
      <div className="bg-white border-b border-border px-5 py-4 flex justify-between items-center">
        <div>
          <div className="text-[11px] text-text-secondary uppercase tracking-[.08em]">hcare</div>
          <div className="text-[18px] font-bold text-text-primary mt-0.5">
            {clientFirstName}'s Care
          </div>
        </div>
        <div className="flex items-center gap-4">
          <div className="text-[12px] text-text-secondary">{today}</div>
          <button
            onClick={() => {
              logout()
              navigate('/portal/verify?reason=signed_out', { replace: true })
            }}
            className="text-[12px] text-text-secondary py-3 px-2 min-h-[44px] flex items-center"
          >
            {t('signOut')}
          </button>
        </div>
      </div>

      <div className="p-4 space-y-4 max-w-md mx-auto">
        {/* Today's Visit */}
        <div className="bg-white border border-border p-4">
          <div className="text-[9px] font-bold tracking-[.1em] uppercase text-text-secondary mb-3">
            {t('todayVisitHeading')}
          </div>
          <TodayVisitCard visit={todayVisit} tz={tz} t={t} />
        </div>

        {/* Upcoming visits */}
        <div>
          <div className="text-[9px] font-bold tracking-[.1em] uppercase text-text-secondary mb-2">
            {t('upcomingHeading')}
          </div>
          {upcomingVisits.length === 0 ? (
            <p className="text-[13px] text-text-secondary">{t('noUpcomingVisits')}</p>
          ) : (
            <div className="flex flex-col gap-px">
              {upcomingVisits.slice(0, 3).map((v, i) => (
                <UpcomingRow key={i} visit={v} tz={tz} />
              ))}
            </div>
          )}
        </div>

        {/* Last visit */}
        {lastVisit && <LastVisitCard visit={lastVisit} tz={tz} t={t} />}
      </div>
    </div>
  )
}

function TodayVisitCard({
  visit,
  tz,
  t,
}: {
  visit: TodayVisitDto | null
  tz: string
  t: (k: string, opts?: Record<string, string>) => string
}) {
  if (!visit) {
    return (
      <p className="text-[13px] text-text-secondary text-center py-2">{t('noVisitToday')}</p>
    )
  }

  const late = isLate(visit)
  const scheduledTime = formatTime(visit.scheduledStart, tz)

  return (
    <div>
      <StatusPill visit={visit} scheduledTime={scheduledTime} late={late} t={t} tz={tz} />
      {/* Caregiver card — hidden for CANCELLED */}
      {visit.caregiver && visit.status !== 'CANCELLED' && (
        <div className="flex items-center gap-3 mt-3">
          <div className="w-11 h-11 rounded-full bg-blue flex items-center justify-center text-[16px] font-bold text-white flex-shrink-0">
            {visit.caregiver.name[0]}
          </div>
          <div>
            <div className="text-[15px] font-bold text-text-primary">{visit.caregiver.name}</div>
            <div className="text-[13px] text-text-secondary">{visit.caregiver.serviceType}</div>
          </div>
        </div>
      )}
      {/* Times for IN_PROGRESS */}
      {visit.status === 'IN_PROGRESS' && visit.clockedInAt && (
        <div className="flex gap-5 mt-3">
          <div>
            <div className="text-[9px] font-bold uppercase tracking-[.08em] text-text-secondary">{t('clockedIn')}</div>
            <div className="text-[14px] font-semibold text-text-primary mt-0.5">
              {formatTime(visit.clockedInAt, tz)}
            </div>
          </div>
          <div>
            <div className="text-[9px] font-bold uppercase tracking-[.08em] text-text-secondary">{t('scheduledUntil')}</div>
            <div className="text-[14px] font-semibold text-text-primary mt-0.5">
              {formatTime(visit.scheduledEnd, tz)}
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

function StatusPill({
  visit,
  scheduledTime,
  late,
  t,
  tz,
}: {
  visit: TodayVisitDto
  scheduledTime: string
  late: boolean
  t: (k: string, opts?: Record<string, string>) => string
  tz: string
}) {
  const caregiverName = visit.caregiver?.name ?? ''

  if (visit.status === 'IN_PROGRESS') {
    return (
      <div className="inline-flex items-center gap-1.5 bg-green-50 border border-green-200 px-2.5 py-1.5 mb-3">
        <div className="w-1.5 h-1.5 rounded-full bg-green-500" />
        <span className="text-[12px] font-bold text-green-700">
          {t('statusInProgress', { name: caregiverName })}
        </span>
      </div>
    )
  }

  if (visit.status === 'GREY' && late) {
    return (
      <div className="inline-flex items-center gap-1.5 bg-amber-50 border border-amber-200 px-2.5 py-1.5 mb-3">
        <span data-testid="clock-icon" className="text-amber-600 text-[12px]">⏰</span>
        <span className="text-[12px] font-bold text-amber-700">
          {t('statusLate', { time: scheduledTime })}
        </span>
      </div>
    )
  }

  if (visit.status === 'GREY') {
    return (
      <div className="inline-flex items-center gap-1.5 bg-surface border border-border px-2.5 py-1.5 mb-3">
        <div className="w-1.5 h-1.5 rounded-full bg-slate-400" />
        <span className="text-[12px] font-bold text-text-secondary">
          {t('statusScheduledOnTime', { name: caregiverName, time: scheduledTime })}
        </span>
      </div>
    )
  }

  if (visit.status === 'COMPLETED') {
    const completedTime = visit.clockedOutAt ? formatTime(visit.clockedOutAt, tz) : scheduledTime
    return (
      <div className="inline-flex items-center gap-1.5 bg-surface border border-border px-2.5 py-1.5 mb-3">
        <span data-testid="checkmark-icon" className="text-[12px]">✓</span>
        <span className="text-[12px] font-bold text-text-primary">
          {t('statusCompleted', { time: completedTime })}
        </span>
      </div>
    )
  }

  if (visit.status === 'CANCELLED') {
    return (
      <div className="inline-flex items-center gap-1.5 bg-red-50 border border-red-200 px-2.5 py-1.5 mb-3">
        <span className="text-[12px] font-bold text-red-700">{t('statusCancelled')}</span>
      </div>
    )
  }

  return null
}

function UpcomingRow({ visit, tz }: { visit: UpcomingVisitDto; tz: string }) {
  return (
    <div className="bg-white border border-border px-3 py-2.5 flex justify-between items-center">
      <div className="text-[13px] font-semibold text-text-primary">
        {formatDate(visit.scheduledStart, tz)}
      </div>
      <div className="text-[12px] text-text-secondary">
        {formatTime(visit.scheduledStart, tz)} – {formatTime(visit.scheduledEnd, tz)}
        {visit.caregiverName ? ` · ${visit.caregiverName}` : ''}
      </div>
    </div>
  )
}

function LastVisitCard({
  visit,
  tz,
  t,
}: {
  visit: LastVisitDto
  tz: string
  t: (k: string, opts?: Record<string, string>) => string
}) {
  const dateLabel = new Date(visit.date + 'T00:00:00Z').toLocaleDateString('en-US', {
    month: 'short', day: 'numeric', timeZone: tz,
  })
  const completedTime = visit.clockedOutAt ? formatTime(visit.clockedOutAt, tz) : '—'
  const duration = formatDuration(visit.durationMinutes)

  return (
    <div>
      <div className="text-[9px] font-bold tracking-[.1em] uppercase text-text-secondary mb-2">
        {t('lastVisitHeading')} ({dateLabel})
      </div>
      <div className="bg-white border border-border p-3">
        <div className="text-[13px] text-text-primary">
          {t('lastVisitCompleted', { time: completedTime, duration })}
        </div>
        {visit.noteText && (
          <div className="text-[13px] text-text-primary leading-relaxed mt-2">
            "{visit.noteText}"
          </div>
        )}
      </div>
    </div>
  )
}
