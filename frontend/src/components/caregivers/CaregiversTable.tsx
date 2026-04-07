import { useTranslation } from 'react-i18next'
import type { CaregiverResponse } from '../../types/api'
import { usePanelStore } from '../../store/panelStore'
import { formatLocalDate } from '../../utils/dateFormat'

interface CaregiversTableProps {
  caregivers: CaregiverResponse[]
  search: string
}

export function CaregiversTable({ caregivers, search }: CaregiversTableProps) {
  const { t, i18n } = useTranslation('caregivers')
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
                ? formatLocalDate(cg.hireDate, i18n.language)
                : tCommon('noDash')}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}
