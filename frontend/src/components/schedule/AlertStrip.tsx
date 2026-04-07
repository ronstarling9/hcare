import { useTranslation } from 'react-i18next'

interface AlertStripProps {
  redCount: number
  yellowCount: number
  uncoveredCount: number
  lateClockInCount: number
}

interface Chip {
  label: string
  count: number
  borderColor: string
  textColor: string
}

export function AlertStrip({ redCount, yellowCount, uncoveredCount, lateClockInCount }: AlertStripProps) {
  const { t } = useTranslation('schedule')

  const chips: Chip[] = [
    { label: t('alertRedEvv'), count: redCount, borderColor: '#dc2626', textColor: '#dc2626' },
    { label: t('alertYellowEvv'), count: yellowCount, borderColor: '#ca8a04', textColor: '#ca8a04' },
    { label: t('alertUncovered'), count: uncoveredCount, borderColor: '#94a3b8', textColor: '#747480' },
    { label: t('alertLateClockIn'), count: lateClockInCount, borderColor: '#ca8a04', textColor: '#ca8a04' },
  ].filter((c) => c.count > 0)

  if (chips.length === 0) return null

  return (
    <div className="flex items-center gap-3 px-6 py-2 bg-surface border-b border-border">
      <span className="text-[11px] text-text-secondary font-medium">{t('alertStripToday')}</span>
      {chips.map((chip) => (
        <span
          key={chip.label}
          className="flex items-center gap-1.5 text-[11px] font-semibold px-2 py-0.5 bg-white"
          style={{
            borderLeft: `3px solid ${chip.borderColor}`,
            color: chip.textColor,
          }}
        >
          <span>{chip.count}</span>
          <span>{chip.label}</span>
        </span>
      ))}
    </div>
  )
}
