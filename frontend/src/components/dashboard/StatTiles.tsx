import { useTranslation } from 'react-i18next'

interface StatTilesProps {
  redEvvCount: number
  yellowEvvCount: number
  uncoveredCount: number
  onTrackCount: number
}

interface Tile {
  label: string
  sublabel: string
  count: number
  numColor: string
  bgColor?: string
}

export function StatTiles({ redEvvCount, yellowEvvCount, uncoveredCount, onTrackCount }: StatTilesProps) {
  const { t } = useTranslation('dashboard')

  const tiles: Tile[] = [
    {
      label: t('tileRedEvv'),
      sublabel: t('tileRedEvvSub'),
      count: redEvvCount,
      numColor: '#dc2626',
      bgColor: redEvvCount > 0 ? '#fef2f2' : undefined,
    },
    {
      label: t('tileYellowEvv'),
      sublabel: t('tileYellowEvvSub'),
      count: yellowEvvCount,
      numColor: '#ca8a04',
    },
    {
      label: t('tileUncovered'),
      sublabel: t('tileUncoveredSub'),
      count: uncoveredCount,
      numColor: '#94a3b8',
    },
    {
      label: t('tileOnTrack'),
      sublabel: t('tileOnTrackSub'),
      count: onTrackCount,
      numColor: '#16a34a',
    },
  ]

  return (
    <div className="grid grid-cols-4 border-b border-border">
      {tiles.map((tile, i) => (
        <div
          key={tile.label}
          className={['px-6 py-5', i < 3 ? 'border-r border-border' : ''].join(' ')}
          style={{ background: tile.bgColor ?? '#f6f6fa' }}
        >
          <div
            className="text-[28px] font-bold leading-none"
            style={{ color: tile.numColor }}
          >
            {tile.count}
          </div>
          <div className="text-[10px] font-bold uppercase tracking-[0.08em] text-dark mt-1">
            {tile.label}
          </div>
          <div className="text-[10px] text-text-secondary">{tile.sublabel}</div>
        </div>
      ))}
    </div>
  )
}
