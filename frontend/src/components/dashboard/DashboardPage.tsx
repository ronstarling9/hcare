import { useTranslation } from 'react-i18next'
import { useDashboard } from '../../hooks/useDashboard'
import { StatTiles } from './StatTiles'
import { VisitList } from './VisitList'
import { AlertsColumn } from './AlertsColumn'

export function DashboardPage() {
  const { t, i18n } = useTranslation('dashboard')
  const { data, isLoading, isError } = useDashboard()

  const today = new Date().toLocaleDateString(i18n.language, {
    weekday: 'long',
    month: 'long',
    day: 'numeric',
    year: 'numeric',
  })

  return (
    <div className="flex flex-col h-full">
      {/* Top bar */}
      <div className="flex items-center px-6 py-4 bg-white border-b border-border">
        <h1 className="text-[16px] font-bold tracking-[-0.02em] text-dark">{t('pageTitle')}</h1>
        <span className="ml-3 text-[13px] text-text-secondary">{today}</span>
      </div>

      {isLoading && !data && (
        <div className="flex-1 flex items-center justify-center bg-surface">
          <span className="text-[14px] text-text-muted">{t('loading')}</span>
        </div>
      )}

      {isError && !data && (
        <div className="flex-1 flex items-center justify-center bg-surface">
          <span className="text-[14px] text-text-muted">{t('error')}</span>
        </div>
      )}

      {data && (
        <>
          {/* Stat tiles */}
          <StatTiles
            redEvvCount={data.redEvvCount}
            yellowEvvCount={data.yellowEvvCount}
            uncoveredCount={data.uncoveredCount}
            onTrackCount={data.onTrackCount}
          />

          {/* Main area + alerts column */}
          <div className="flex flex-1 overflow-hidden">
            {/* Visit list */}
            <div className="flex-1 overflow-auto">
              <VisitList visits={data.visits} />
            </div>

            {/* Alerts column — fixed 220px */}
            <div
              className="shrink-0 overflow-auto border-l border-border"
              style={{ width: 220 }}
            >
              <AlertsColumn alerts={data.alerts} />
            </div>
          </div>
        </>
      )}
    </div>
  )
}
