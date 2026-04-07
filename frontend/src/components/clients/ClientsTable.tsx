import { useTranslation } from 'react-i18next'
import type { ClientResponse } from '../../types/api'
import { usePanelStore } from '../../store/panelStore'

interface ClientsTableProps {
  clients: ClientResponse[]
  search: string
}

export function ClientsTable({ clients, search }: ClientsTableProps) {
  const { t } = useTranslation('clients')
  const tCommon = useTranslation('common').t
  const { openPanel } = usePanelStore()

  const filtered = clients.filter((c) => {
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
          <th className="text-left px-6 py-2 text-[9px] font-bold uppercase tracking-[0.1em] text-text-secondary">
            {t('colClient')}
          </th>
          <th className="text-left px-4 py-2 text-[9px] font-bold uppercase tracking-[0.1em] text-text-secondary">
            {t('colMedicaidId')}
          </th>
          <th className="text-left px-4 py-2 text-[9px] font-bold uppercase tracking-[0.1em] text-text-secondary">
            {t('colState')}
          </th>
          <th className="text-left px-4 py-2 text-[9px] font-bold uppercase tracking-[0.1em] text-text-secondary">
            {t('colStatus')}
          </th>
        </tr>
      </thead>
      <tbody>
        {filtered.map((client) => (
          <tr
            key={client.id}
            className="border-b border-border hover:bg-surface cursor-pointer"
            onClick={() => openPanel('client', client.id, { backLabel: t('backLabel') })}
          >
            <td className="px-6 py-3 font-medium text-dark">
              {client.firstName} {client.lastName}
            </td>
            <td className="px-4 py-3 text-text-secondary">{client.medicaidId ?? tCommon('noDash')}</td>
            <td className="px-4 py-3 text-text-secondary">{client.serviceState ?? tCommon('noDash')}</td>
            <td className="px-4 py-3">
              <span
                className="text-[11px] font-semibold px-2 py-0.5"
                style={{
                  background: client.status === 'ACTIVE' ? '#f0fdf4' : '#f8fafc',
                  color: client.status === 'ACTIVE' ? '#16a34a' : '#94a3b8',
                }}
              >
                {client.status}
              </span>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}
