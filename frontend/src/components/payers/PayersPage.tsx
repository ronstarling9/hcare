import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { usePayers } from '../../hooks/usePayers'
import type { PayerResponse } from '../../types/api'

const PAYER_TYPE_KEYS: Record<string, string> = {
  MEDICAID: 'typeMedicaid',
  PRIVATE_PAY: 'typePrivatePay',
  LTC_INSURANCE: 'typeLtcInsurance',
  VA: 'typeVa',
  MEDICARE: 'typeMedicare',
}

function PayerRow({ payer, t }: { payer: PayerResponse; t: (key: string) => string }) {
  const typeLabel = PAYER_TYPE_KEYS[payer.payerType]
    ? t(PAYER_TYPE_KEYS[payer.payerType])
    : payer.payerType

  return (
    <div className="flex items-center justify-between px-4 py-3 bg-white border border-border rounded">
      <div>
        <p className="text-sm font-medium text-dark">
          {payer.name}
        </p>
        <p className="text-xs mt-0.5 text-text-secondary">
          {typeLabel}
          {' · '}
          {payer.state}
        </p>
      </div>
      <div className="text-right">
        {payer.evvAggregator ? (
          <span className="inline-block text-xs px-2 py-0.5 rounded bg-surface text-text-secondary border border-border">
            {payer.evvAggregator}
          </span>
        ) : (
          <span className="text-xs text-text-muted">No EVV aggregator</span>
        )}
      </div>
    </div>
  )
}

export function PayersPage() {
  const { t } = useTranslation('payers')
  const [page, setPage] = useState(0)
  const { payers, isLoading, isError, totalPages, totalElements } = usePayers(page, 20)

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-full bg-surface">
        <span className="text-sm text-text-muted">{t('loading')}</span>
      </div>
    )
  }

  if (isError) {
    return (
      <div className="flex items-center justify-center h-full bg-surface">
        <p className="text-sm text-red-600">{t('errorLoad')}</p>
      </div>
    )
  }

  return (
    <div className="flex flex-col h-full bg-surface">
      {/* Header */}
      <div className="flex items-center justify-between px-6 py-4 bg-white border-b border-border">
        <div>
          <h1 className="text-lg font-semibold text-dark">{t('pageTitle')}</h1>
          <p className="text-xs mt-0.5 text-text-muted">
            {t('totalCount', { count: totalElements })}
          </p>
        </div>
      </div>

      {/* List */}
      <div className="flex-1 overflow-auto p-6">
        {payers.length === 0 ? (
          <div className="flex items-center justify-center h-32">
            <p className="text-sm text-text-muted">{t('empty')}</p>
          </div>
        ) : (
          <div className="space-y-2">
            {payers.map((payer) => (
              <PayerRow key={payer.id} payer={payer} t={t} />
            ))}
          </div>
        )}

        {/* Pagination */}
        {totalPages > 1 && (
          <div className="flex items-center justify-end gap-2 mt-4">
            <button
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              disabled={page === 0}
              className="px-3 py-1.5 text-sm disabled:opacity-40 border border-border text-text-secondary"
            >
              {t('prev')}
            </button>
            <span className="text-sm text-text-secondary">
              {t('pageOf', { page: page + 1, total: totalPages })}
            </span>
            <button
              onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
              disabled={page === totalPages - 1}
              className="px-3 py-1.5 text-sm disabled:opacity-40 border border-border text-text-secondary"
            >
              {t('next')}
            </button>
          </div>
        )}
      </div>
    </div>
  )
}
