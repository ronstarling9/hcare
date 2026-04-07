import { useTranslation } from 'react-i18next'
import type { CaregiverResponse } from '../../types/api'
import { usePanelStore } from '../../store/panelStore'

// Date-only ISO strings (e.g. '2023-04-01') are parsed as UTC-midnight by the spec,
// causing off-by-one display in UTC-N timezones. Appending T12:00:00 keeps the date
// in the correct calendar day across all UTC-14 to UTC+14 zones.
function formatLocalDate(dateStr: string, options?: Intl.DateTimeFormatOptions): string {
  return new Date(dateStr + 'T12:00:00').toLocaleDateString('en-US', options)
}

interface CaregiversTableProps {
  caregivers: CaregiverResponse[]
  search: string
}

export function CaregiversTable({ caregivers, search }: CaregiversTableProps) {
  const { t } = useTranslation('caregivers')
  const tCommon = useTranslation('common').t
  const { openPanel } = usePanelStore()

  const filtered = caregivers.filter((c) => {
    const name = `${c.firstName} ${c.lastName}`.toLowerCase()
    return name.includes(search.toLowerCase())
  })

  if (filtered.length === 0) {
    return <p className="px-6 py-8 text-text-secondary text-[13px]">{t('noResults')}</p>
  }

  return (
    <table className="w-full text-[13px]">
      <thead>
        <tr className="border-b border-border bg-surface">
          {[t('colCaregiver'), t('colEmail'), t('colPhone'), t('colStatus'), t('colHireDate')].map((h) => (
            <th
              key={h}
              className="text-left px-6 py-2 text-[9px] font-bold uppercase tracking-[0.1em] text-text-secondary first:pl-6"
            >
              {h}
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        {filtered.map((cg) => (
          <tr
            key={cg.id}
            className="border-b border-border hover:bg-surface cursor-pointer"
            onClick={() => openPanel('caregiver', cg.id, { backLabel: t('backLabel') })}
          >
            <td className="px-6 py-3 font-medium text-dark">
              {cg.firstName} {cg.lastName}
            </td>
            <td className="px-6 py-3 text-text-secondary">{cg.email}</td>
            <td className="px-6 py-3 text-text-secondary">{cg.phone ?? tCommon('noDash')}</td>
            <td className="px-6 py-3">
              <span
                className="text-[11px] font-semibold px-2 py-0.5"
                style={{
                  background: cg.status === 'ACTIVE' ? '#f0fdf4' : '#f8fafc',
                  color: cg.status === 'ACTIVE' ? '#16a34a' : '#94a3b8',
                }}
              >
                {cg.status}
              </span>
            </td>
            <td className="px-6 py-3 text-text-secondary">
              {cg.hireDate
                ? formatLocalDate(cg.hireDate, { month: 'short', day: 'numeric', year: 'numeric' })
                : tCommon('noDash')}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}
