import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { usePayerAuthorizations } from '../../hooks/usePayerAuthorizations'
import type { AuthorizationResponse } from '../../types/api'

interface PayerAuthorizationsModalProps {
  payerId: string | null
  payerName: string
  onClose: () => void
}

function unitLabel(unitType: AuthorizationResponse['unitType']): string {
  switch (unitType) {
    case 'HOUR':  return 'hrs'
    case 'VISIT': return 'visits'
    case 'DAY':   return 'days'
  }
}

function formatDate(iso: string): string {
  // ISO LocalDate strings are YYYY-MM-DD — no timezone conversion needed.
  const [year, month, day] = iso.split('-')
  return `${month}/${day}/${year}`
}

function AuthorizationRow({ auth }: { auth: AuthorizationResponse }) {
  const remaining = (
    parseFloat(String(auth.authorizedUnits)) - parseFloat(String(auth.usedUnits))
  ).toFixed(2)
  const label = unitLabel(auth.unitType)
  const isLow = parseFloat(remaining) / parseFloat(String(auth.authorizedUnits)) < 0.2

  return (
    <div className="flex flex-col gap-1 px-4 py-3 border border-border rounded bg-white">
      <div className="flex items-start justify-between gap-2">
        <span className="text-xs font-mono text-text-secondary">{auth.authNumber}</span>
        <span className={`text-xs px-2 py-0.5 rounded font-medium ${
          isLow
            ? 'bg-red-50 text-red-600 border border-red-200'
            : 'bg-surface text-text-secondary border border-border'
        }`}>
          {remaining} {label} left
        </span>
      </div>

      <div className="grid grid-cols-3 gap-2 text-center mt-1">
        <div>
          <p className="text-xs text-text-muted">Authorized</p>
          <p className="text-sm font-medium text-text-primary">
            {parseFloat(String(auth.authorizedUnits)).toFixed(2)}{' '}
            <span className="text-xs text-text-secondary">{label}</span>
          </p>
        </div>
        <div>
          <p className="text-xs text-text-muted">Used</p>
          <p className="text-sm font-medium text-text-primary">
            {parseFloat(String(auth.usedUnits)).toFixed(2)}{' '}
            <span className="text-xs text-text-secondary">{label}</span>
          </p>
        </div>
        <div>
          <p className="text-xs text-text-muted">Remaining</p>
          <p className={`text-sm font-medium ${isLow ? 'text-red-600' : 'text-text-primary'}`}>
            {remaining}{' '}
            <span className="text-xs text-text-secondary">{label}</span>
          </p>
        </div>
      </div>

      <p className="text-xs text-text-muted mt-1">
        {formatDate(auth.startDate)} &ndash; {formatDate(auth.endDate)}
      </p>
    </div>
  )
}

export function PayerAuthorizationsModal({
  payerId,
  payerName,
  onClose,
}: PayerAuthorizationsModalProps) {
  const { t } = useTranslation('payers')
  const [page, setPage] = useState(0)
  const { authorizations, isLoading, isError, totalPages, totalElements } =
    usePayerAuthorizations(payerId, page)

  if (!payerId) return null

  return (
    /* Backdrop */
    <div
      className="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-black/40"
      onClick={onClose}
    >
      {/* Modal panel — full-width sheet on mobile, centered card on sm+ */}
      <div
        className="w-full sm:max-w-lg bg-white rounded-t-2xl sm:rounded-2xl shadow-xl flex flex-col max-h-[85dvh]"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-border">
          <div>
            <h2 className="text-base font-semibold text-text-primary">{payerName}</h2>
            <p className="text-xs text-text-muted mt-0.5">
              {isLoading
                ? t('authModal.loading')
                : t('authModal.totalCount', { count: totalElements })}
            </p>
          </div>
          <button
            onClick={onClose}
            aria-label={t('authModal.close')}
            className="p-1.5 rounded-lg text-text-secondary hover:bg-surface transition-colors"
          >
            <svg
              xmlns="http://www.w3.org/2000/svg"
              className="h-5 w-5"
              viewBox="0 0 20 20"
              fill="currentColor"
              aria-hidden="true"
            >
              <path
                fillRule="evenodd"
                d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z"
                clipRule="evenodd"
              />
            </svg>
          </button>
        </div>

        {/* Body */}
        <div className="flex-1 overflow-y-auto px-5 py-4">
          {isLoading && (
            <div className="flex items-center justify-center py-12">
              <span className="text-sm text-text-muted">{t('authModal.loading')}</span>
            </div>
          )}

          {isError && (
            <div className="flex items-center justify-center py-12">
              <p className="text-sm text-red-600">{t('authModal.error')}</p>
            </div>
          )}

          {!isLoading && !isError && authorizations.length === 0 && (
            <div className="flex items-center justify-center py-12">
              <p className="text-sm text-text-muted">{t('authModal.empty')}</p>
            </div>
          )}

          {!isLoading && !isError && authorizations.length > 0 && (
            <div className="space-y-2">
              {authorizations.map((auth) => (
                <AuthorizationRow key={auth.id} auth={auth} />
              ))}
            </div>
          )}
        </div>

        {/* Pagination footer */}
        {totalPages > 1 && (
          <div className="flex items-center justify-end gap-2 px-5 py-3 border-t border-border">
            <button
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              disabled={page === 0}
              className="px-3 py-1.5 text-sm disabled:opacity-40 border border-border text-text-secondary rounded"
            >
              {t('prev')}
            </button>
            <span className="text-sm text-text-secondary">
              {t('pageOf', { page: page + 1, total: totalPages })}
            </span>
            <button
              onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
              disabled={page === totalPages - 1}
              className="px-3 py-1.5 text-sm disabled:opacity-40 border border-border text-text-secondary rounded"
            >
              {t('next')}
            </button>
          </div>
        )}
      </div>
    </div>
  )
}
