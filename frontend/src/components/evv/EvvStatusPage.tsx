import { useState, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { useEvvHistory } from '../../hooks/useEvvHistory'
import { usePanelStore } from '../../store/panelStore'
import type { EvvComplianceStatus, EvvHistoryRow } from '../../types/api'

const EVV_COLORS: Record<EvvComplianceStatus, string> = {
  GREEN: '#16a34a',
  YELLOW: '#ca8a04',
  RED: '#dc2626',
  GREY: '#94a3b8',
  EXEMPT: '#94a3b8',
  PORTAL_SUBMIT: '#16a34a',
}

const STATUS_ORDER: EvvComplianceStatus[] = [
  'RED', 'YELLOW', 'PORTAL_SUBMIT', 'GREY', 'GREEN', 'EXEMPT',
]

function EvvStatusBadge({ status }: { status: EvvComplianceStatus }) {
  return (
    <span
      className="inline-block text-xs font-semibold px-2 py-0.5 rounded text-white"
      style={{ backgroundColor: EVV_COLORS[status] ?? '#94a3b8' }}
    >
      {status}
    </span>
  )
}

function EvvRow({
  row,
  onClick,
  unassignedLabel,
}: {
  row: EvvHistoryRow
  onClick?: () => void
  unassignedLabel: string
}) {
  const clientName = `${row.clientFirstName} ${row.clientLastName}`
  const caregiverName =
    row.caregiverFirstName
      ? `${row.caregiverFirstName} ${row.caregiverLastName ?? ''}`
      : unassignedLabel

  return (
    <tr
      className="border-b border-border cursor-pointer hover:bg-surface"
      onClick={onClick}
    >
      <td className="px-4 py-3 text-sm text-dark">
        {clientName}
      </td>
      <td className="px-4 py-3 text-sm text-text-secondary">
        {caregiverName}
      </td>
      <td className="px-4 py-3 text-sm text-text-secondary">
        {row.serviceTypeName ?? '—'}
      </td>
      <td className="px-4 py-3 text-sm text-text-secondary">
        {new Date(row.scheduledStart).toLocaleDateString('en-US', {
          month: 'short',
          day: 'numeric',
        })}
        {' '}
        {new Date(row.scheduledStart).toLocaleTimeString('en-US', {
          hour: 'numeric',
          minute: '2-digit',
        })}
      </td>
      <td className="px-4 py-3">
        <EvvStatusBadge status={row.evvStatus} />
        {row.evvStatusReason && (
          <p className="text-xs mt-0.5 text-text-muted">
            {row.evvStatusReason}
          </p>
        )}
      </td>
      <td className="px-4 py-3 text-xs text-text-secondary">
        {row.timeIn
          ? new Date(row.timeIn).toLocaleTimeString('en-US', {
              hour: 'numeric',
              minute: '2-digit',
            })
          : '—'}
        {' / '}
        {row.timeOut
          ? new Date(row.timeOut).toLocaleTimeString('en-US', {
              hour: 'numeric',
              minute: '2-digit',
            })
          : '—'}
      </td>
      <td className="px-4 py-3 text-xs text-text-secondary">
        {row.verificationMethod ?? '—'}
      </td>
    </tr>
  )
}

// Format a Date to ISO-8601 LocalDateTime for API params (no timezone suffix).
// d.toISOString() returns UTC — callers must ensure d is constructed from local-time
// parts (new Date(y, m, day, h, min, sec)) so that UTC conversion is correct.
function toLocalDateTime(d: Date): string {
  return d.toISOString().replace('Z', '').replace(/\.\d+$/, '')
}

// Parse a date-input value ('YYYY-MM-DD') as local midnight.
// new Date('YYYY-MM-DD') is UTC midnight per the ECMA-262 spec, which is wrong for
// non-UTC users. new Date(y, m, d) always gives local midnight.
function parseDateInputAsLocal(value: string): Date {
  const [y, m, d] = value.split('-').map(Number)
  return new Date(y, m - 1, d)
}

export function EvvStatusPage() {
  const { t } = useTranslation('evvStatus')
  const { openPanel } = usePanelStore()

  // Default: current month
  const today = new Date()
  const defaultStart = new Date(today.getFullYear(), today.getMonth(), 1)
  const defaultEnd = new Date(today.getFullYear(), today.getMonth() + 1, 0, 23, 59, 59)

  const [rangeStart, setRangeStart] = useState<Date>(defaultStart)
  const [rangeEnd, setRangeEnd] = useState<Date>(defaultEnd)
  const [page, setPage] = useState(0)
  const [statusFilter, setStatusFilter] = useState<EvvComplianceStatus | 'ALL'>('ALL')

  const startParam = useMemo(() => toLocalDateTime(rangeStart), [rangeStart])
  const endParam = useMemo(() => toLocalDateTime(rangeEnd), [rangeEnd])

  const { rows, totalPages, totalElements, isLoading, isError } = useEvvHistory(startParam, endParam, page)

  const filteredRows = useMemo(() => {
    if (statusFilter === 'ALL') return rows
    return rows.filter((r) => r.evvStatus === statusFilter)
  }, [rows, statusFilter])

  // Status counts from the current page only — not a global total.
  // The "All" chip shows totalElements (API total); status chips show page-local counts.
  // TODO (P1): add status breakdown to the API response so counts reflect all pages.
  const statusCounts = useMemo(() => {
    const counts: Partial<Record<EvvComplianceStatus, number>> = {}
    for (const row of rows) {
      counts[row.evvStatus] = (counts[row.evvStatus] ?? 0) + 1
    }
    return counts
  }, [rows])

  const handleDateChange = (field: 'start' | 'end', value: string) => {
    // parseDateInputAsLocal constructs a local-midnight Date from the 'YYYY-MM-DD'
    // string, avoiding the ECMA-262 gotcha where new Date('YYYY-MM-DD') is UTC midnight.
    const d = parseDateInputAsLocal(value)
    if (!isNaN(d.getTime())) {
      if (field === 'start') {
        setRangeStart(d)
        setPage(0)
      } else {
        d.setHours(23, 59, 59, 0) // local end-of-day; toISOString() converts to UTC correctly
        setRangeEnd(d)
        setPage(0)
      }
    }
  }

  // Convert Date to YYYY-MM-DD for date input value using LOCAL date parts.
  // toISOString().split('T')[0] would give the UTC date, which is wrong for non-UTC users
  // (e.g. a user at UTC-5 at 9pm local would see tomorrow's date in the picker).
  const toDateInputValue = (d: Date) =>
    `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`

  const columnHeaders = [
    t('colClient'),
    t('colCaregiver'),
    t('colService'),
    t('colDate'),
    t('colEvvStatus'),
    t('colInOut'),
    t('colMethod'),
  ]

  return (
    <div className="flex flex-col h-full bg-surface">
      {/* Header */}
      <div className="px-6 py-4 border-b bg-white border-border">
        <div className="flex items-center justify-between flex-wrap gap-4">
          <div>
            <h1 className="text-lg font-semibold text-dark">
              {t('pageTitle')}
            </h1>
            <p className="text-xs mt-0.5 text-text-muted">
              {t('visitsInRange', { count: totalElements })}
            </p>
          </div>

          {/* Date range pickers */}
          <div className="flex items-center gap-3">
            <div className="flex items-center gap-2">
              <label className="text-xs text-text-secondary">{t('from')}</label>
              <input
                type="date"
                value={toDateInputValue(rangeStart)}
                onChange={(e) => handleDateChange('start', e.target.value)}
                className="rounded-lg px-2 py-1 text-xs border border-border text-dark"
              />
            </div>
            <div className="flex items-center gap-2">
              <label className="text-xs text-text-secondary">{t('to')}</label>
              <input
                type="date"
                value={toDateInputValue(rangeEnd)}
                onChange={(e) => handleDateChange('end', e.target.value)}
                className="rounded-lg px-2 py-1 text-xs border border-border text-dark"
              />
            </div>
          </div>
        </div>

        {/* Status filter chips — "ALL" uses brand token; EVV-status chips keep semantic colours */}
        <div className="flex items-center gap-2 mt-3 flex-wrap">
          <button
            onClick={() => setStatusFilter('ALL')}
            className={`text-xs px-3 py-1 rounded-full font-medium transition-colors border border-border ${
              statusFilter === 'ALL'
                ? 'bg-blue text-white'
                : 'bg-surface text-text-secondary'
            }`}
          >
            All ({totalElements})
          </button>
          {STATUS_ORDER.map((status) => {
            const count = statusCounts[status] ?? 0
            return (
              <button
                key={status}
                onClick={() => setStatusFilter(status)}
                className="text-xs px-3 py-1 rounded-full font-medium transition-colors"
                style={{
                  backgroundColor:
                    statusFilter === status ? EVV_COLORS[status] : undefined,
                  color: statusFilter === status ? '#ffffff' : EVV_COLORS[status],
                  border: `1px solid ${EVV_COLORS[status]}`,
                }}
              >
                {status} ({count} this page)
              </button>
            )
          })}
        </div>
      </div>

      {/* Table */}
      <div className="flex-1 overflow-auto">
        {isLoading ? (
          <div className="flex items-center justify-center h-32">
            <span className="text-sm text-text-muted">{t('loading')}</span>
          </div>
        ) : isError ? (
          <div className="flex items-center justify-center h-32">
            <p className="text-sm text-red-600">{t('errorLoad')}</p>
          </div>
        ) : filteredRows.length === 0 ? (
          <div className="flex items-center justify-center h-32">
            <p className="text-sm text-text-muted">
              {rows.length > 0 && statusFilter !== 'ALL'
                ? t('noStatusVisits', { status: statusFilter })
                : t('noVisits')}
            </p>
          </div>
        ) : (
          <table className="w-full text-left">
            <thead className="bg-surface border-b border-border">
              <tr>
                {columnHeaders.map((h) => (
                  <th
                    key={h}
                    className="px-4 py-3 text-xs font-semibold uppercase text-text-muted"
                  >
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="bg-white">
              {filteredRows.map((row) => (
                <EvvRow
                  key={row.shiftId}
                  row={row}
                  unassignedLabel={t('unassigned')}
                  onClick={() => openPanel('shift', row.shiftId, { backLabel: t('backLabel') })}
                />
              ))}
            </tbody>
          </table>
        )}
      </div>

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex items-center justify-end gap-2 px-6 py-3 border-t border-border bg-white">
          <button
            onClick={() => { setPage((p) => Math.max(0, p - 1)); setStatusFilter('ALL') }}
            disabled={page === 0}
            className="px-3 py-1.5 text-sm disabled:opacity-40 border border-border text-text-secondary"
          >
            {t('prev')}
          </button>
          <span className="text-sm text-text-secondary">
            {t('pageOf', { page: page + 1, total: totalPages })}
          </span>
          <button
            onClick={() => { setPage((p) => Math.min(totalPages - 1, p + 1)); setStatusFilter('ALL') }}
            disabled={page === totalPages - 1}
            className="px-3 py-1.5 text-sm disabled:opacity-40 border border-border text-text-secondary"
          >
            {t('next')}
          </button>
        </div>
      )}
    </div>
  )
}
