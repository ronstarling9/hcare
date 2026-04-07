import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useClients } from '../../hooks/useClients'
import { ClientsTable } from './ClientsTable'

export function ClientsPage() {
  const { t } = useTranslation('clients')
  const tCommon = useTranslation('common').t
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)
  const { clients, isLoading, isError, totalPages, totalElements } = useClients(page, 20)

  if (isLoading && clients.length === 0) {
    return (
      <div className="flex items-center justify-center h-full bg-surface">
        <span className="text-[14px] text-text-muted">{t('loading')}</span>
      </div>
    )
  }

  if (isError && clients.length === 0) {
    return (
      <div className="flex items-center justify-center h-full bg-surface">
        <span className="text-[14px] text-text-muted">{t('error')}</span>
      </div>
    )
  }

  return (
    <div className="flex flex-col h-full">
      <div className="flex items-center gap-4 px-6 py-4 bg-white border-b border-border">
        <h1 className="text-[16px] font-bold tracking-[-0.02em] text-dark">{t('pageTitle')}</h1>
        {totalElements > 0 && (
          <span className="text-[13px] text-text-secondary">{totalElements} total</span>
        )}
        <input
          type="search"
          placeholder={tCommon('searchByName')}
          value={search}
          onChange={(e) => { setSearch(e.target.value); setPage(0) }}
          className="ml-4 border border-border px-3 py-1.5 text-[13px] w-64 focus:outline-none focus:border-dark"
        />
        <button
          type="button"
          className="ml-auto px-4 py-1.5 text-[12px] font-bold bg-dark text-white hover:brightness-110"
          onClick={() => alert(t('addClientAlert'))}
        >
          {t('addClient')}
        </button>
      </div>

      <div className="flex-1 overflow-auto">
        <ClientsTable clients={clients} search={search} />
      </div>

      {totalPages > 1 && (
        <div className="flex items-center justify-end gap-2 px-6 py-3 border-t border-border bg-white">
          <button
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            disabled={page === 0}
            className="px-3 py-1.5 rounded text-[13px] border border-border text-text-secondary disabled:opacity-40"
          >
            {t('prev')}
          </button>
          <span className="text-[13px] text-text-secondary">
            {t('pageOf', { page: page + 1, total: totalPages })}
          </span>
          <button
            onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
            disabled={page === totalPages - 1}
            className="px-3 py-1.5 rounded text-[13px] border border-border text-text-secondary disabled:opacity-40"
          >
            {t('next')}
          </button>
        </div>
      )}
    </div>
  )
}
