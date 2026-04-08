import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { usePanelStore } from '../../store/panelStore'
import { formatLocalDate, formatLocalTime } from '../../utils/dateFormat'
import {
  useCaregiverDetail,
  useCaregiverCredentials,
  useCaregiverBackgroundChecks,
  useCaregiverShiftHistory,
  useVerifyCredential,
} from '../../hooks/useCaregivers'
import { useAuthStore } from '../../store/authStore'
import type { CredentialResponse, BackgroundCheckResponse } from '../../types/api'

type Tab = 'overview' | 'credentials' | 'backgroundChecks' | 'shiftHistory'

interface CaregiverDetailPanelProps {
  caregiverId: string
  backLabel: string
}

interface CredentialRowProps {
  cred: CredentialResponse
  locale: string
  isAdmin: boolean
  onVerify: (credentialId: string) => void
  isVerifying: boolean
  verifyError: boolean
}

function CredentialRow({ cred, locale, isAdmin, onVerify, isVerifying, verifyError }: CredentialRowProps) {
  const { t } = useTranslation('caregivers')
  const today = new Date()
  // Use T12:00:00 anchor to avoid UTC-midnight off-by-one in negative-offset timezones
  const expiry = cred.expiryDate ? new Date(`${cred.expiryDate}T12:00:00`) : null
  const daysUntilExpiry = expiry
    ? Math.ceil((expiry.getTime() - today.getTime()) / (1000 * 60 * 60 * 24))
    : null

  const expiryColorClass =
    daysUntilExpiry === null
      ? 'text-text-muted'
      : daysUntilExpiry <= 0
      ? 'text-red-600'
      : daysUntilExpiry <= 30
      ? 'text-yellow-600'
      : 'text-green-600'

  return (
    <div className="flex items-center justify-between border border-border rounded px-3 py-2 bg-white">
      <div>
        <p className="text-[13px] font-medium text-dark">
          {cred.credentialType.replace(/_/g, ' ')}
        </p>
        <p className="text-[11px] text-text-secondary">
          {cred.verified ? t('credVerified') : t('credUnverified')}
        </p>
        {verifyError && (
          <p className="text-[11px] text-red-600 mt-0.5">{t('credVerifyError')}</p>
        )}
      </div>
      <div className="flex items-center gap-3">
        {!cred.verified && isAdmin && (
          <button
            type="button"
            disabled={isVerifying}
            className="text-[12px] font-semibold text-blue disabled:opacity-50"
            onClick={() => onVerify(cred.id)}
          >
            {isVerifying ? '…' : t('credVerify')}
          </button>
        )}
        <div className="text-right">
          {expiry ? (
            <p className={`text-[11px] font-semibold ${expiryColorClass}`}>
              {daysUntilExpiry !== null && daysUntilExpiry <= 0
                ? 'EXPIRED'
                : formatLocalDate(cred.expiryDate!, locale)}
            </p>
          ) : (
            <p className="text-[11px] text-text-muted">No expiry</p>
          )}
        </div>
      </div>
    </div>
  )
}

function BackgroundCheckRow({ bc, locale }: { bc: BackgroundCheckResponse; locale: string }) {
  const badgeClass: Record<BackgroundCheckResponse['result'], string> = {
    PASS:    'bg-green-50 text-green-600',
    FAIL:    'bg-red-50 text-red-600',
    EXPIRED: 'bg-red-50 text-red-600',
    PENDING: 'bg-yellow-50 text-yellow-700',
  }
  return (
    <div className="border border-border rounded px-3 py-2 bg-white">
      <div className="flex items-center justify-between">
        <p className="text-[13px] font-medium text-dark">
          {bc.checkType.replace(/_/g, ' ')}
        </p>
        <span className={`text-[11px] font-semibold px-2 py-0.5 rounded ${badgeClass[bc.result]}`}>
          {bc.result}
        </span>
      </div>
      {bc.checkedAt && (
        <p className="text-[11px] text-text-secondary mt-1">
          Checked: {formatLocalDate(bc.checkedAt, locale)}
          {bc.renewalDueDate ? ` · Renewal due: ${formatLocalDate(bc.renewalDueDate, locale)}` : ''}
        </p>
      )}
    </div>
  )
}

export function CaregiverDetailPanel({ caregiverId, backLabel }: CaregiverDetailPanelProps) {
  const { t, i18n } = useTranslation('caregivers')
  const tCommon = useTranslation('common').t
  const { closePanel } = usePanelStore()
  const [activeTab, setActiveTab] = useState<Tab>('overview')
  const [historyPage, setHistoryPage] = useState(0)

  const role = useAuthStore((s) => s.role)
  const isAdmin = role === 'ADMIN'

  const { data: caregiver, isLoading, isError } = useCaregiverDetail(caregiverId)
  const { data: credsPage } = useCaregiverCredentials(caregiverId)
  const { data: bgChecksPage } = useCaregiverBackgroundChecks(caregiverId)
  const { data: shiftHistoryPage } = useCaregiverShiftHistory(caregiverId, historyPage)
  const verifyMutation = useVerifyCredential(caregiverId)

  const credentials = credsPage?.content ?? []
  const bgChecks = bgChecksPage?.content ?? []
  const shiftHistory = shiftHistoryPage?.content ?? []
  const shiftHistoryTotalPages = shiftHistoryPage?.totalPages ?? 0

  const TABS: { id: Tab; label: string }[] = [
    { id: 'overview', label: t('tabOverview') },
    { id: 'credentials', label: `${t('tabCredentials')} (${credsPage?.totalElements ?? 0})` },
    { id: 'backgroundChecks', label: t('tabBackgroundChecks') },
    { id: 'shiftHistory', label: t('tabShiftHistory') },
  ]

  if (isLoading && !caregiver) {
    return (
      <div className="flex flex-col h-full">
        <div className="px-6 py-4 border-b border-border">
          <button
            type="button"
            className="text-[13px] mb-2 text-blue hover:underline"
            onClick={closePanel}
          >
            {backLabel}
          </button>
        </div>
        <div className="flex items-center justify-center flex-1">
          <span className="text-[13px] text-text-muted">{tCommon('loading')}</span>
        </div>
      </div>
    )
  }

  if (isError || !caregiver) {
    return (
      <div className="p-8">
        <button type="button" className="text-[13px] mb-4 text-blue hover:underline" onClick={closePanel}>
          {backLabel}
        </button>
        <p className="text-text-secondary">{t('notFound')}</p>
      </div>
    )
  }

  return (
    <div className="flex flex-col h-full">
      {/* Header */}
      <div className="px-6 py-4 border-b border-border">
        <button
          type="button"
          className="text-[13px] mb-2 text-blue hover:underline"
          onClick={closePanel}
        >
          {backLabel}
        </button>
        <h2 className="text-[16px] font-bold text-dark">
          {caregiver.firstName} {caregiver.lastName}
        </h2>
        <p className="text-[12px] text-text-secondary mt-0.5">{caregiver.email}</p>
      </div>

      {/* Tabs */}
      <div className="flex border-b border-border px-6 bg-white">
        {TABS.map((tab) => (
          <button
            key={tab.id}
            type="button"
            onClick={() => setActiveTab(tab.id)}
            className={[
              'px-4 py-3 text-[12px] font-medium border-b-2 -mb-px transition-colors',
              activeTab === tab.id
                ? 'border-dark text-dark'
                : 'border-transparent text-text-secondary hover:text-dark',
            ].join(' ')}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* Tab content */}
      <div className="flex-1 overflow-auto px-6 py-4">
        {/* Overview tab */}
        {activeTab === 'overview' && (
          <div className="grid grid-cols-2 gap-4">
            {[
              [t('fieldPhone'), caregiver.phone ?? tCommon('noDash')],
              [t('fieldAddress'), caregiver.address ?? tCommon('noDash')],
              [t('fieldHireDate'), caregiver.hireDate
                ? formatLocalDate(caregiver.hireDate, i18n.language)
                : tCommon('noDash')],
              [t('fieldStatus'), caregiver.status],
              [t('fieldHasPet'), caregiver.hasPet ? tCommon('yes') : tCommon('no')],
            ].map(([label, value]) => (
              <div key={label}>
                <div className="text-[10px] text-text-secondary">{label}</div>
                <div className="text-[13px] text-dark">{value}</div>
              </div>
            ))}
          </div>
        )}

        {/* Credentials tab */}
        {activeTab === 'credentials' && (
          <div className="space-y-2">
            {credentials.length === 0 ? (
              <p className="text-[13px] text-text-secondary">{t('noCredentials')}</p>
            ) : (
              credentials.map((cred) => (
                <CredentialRow
                  key={cred.id}
                  cred={cred}
                  locale={i18n.language}
                  isAdmin={isAdmin}
                  onVerify={(credentialId) => verifyMutation.mutate(credentialId)}
                  isVerifying={verifyMutation.isPending && verifyMutation.variables === cred.id}
                  verifyError={verifyMutation.isError && verifyMutation.variables === cred.id}
                />
              ))
            )}
          </div>
        )}

        {/* Background checks tab */}
        {activeTab === 'backgroundChecks' && (
          <div className="space-y-2">
            {bgChecks.length === 0 ? (
              <p className="text-[13px] text-text-secondary">{t('noBackgroundChecks')}</p>
            ) : (
              bgChecks.map((bc) => <BackgroundCheckRow key={bc.id} bc={bc} locale={i18n.language} />)
            )}
          </div>
        )}

        {/* Shift history tab */}
        {activeTab === 'shiftHistory' && (
          <div>
            {shiftHistory.length === 0 ? (
              <p className="text-[13px] text-text-secondary">{t('noShiftHistory')}</p>
            ) : (
              <div className="space-y-2">
                {shiftHistory.map((shift) => (
                  <div
                    key={shift.id}
                    className="border border-border rounded px-3 py-2 bg-white"
                  >
                    <div className="flex items-center justify-between">
                      <p className="text-[12px] font-medium text-dark">
                        {new Date(shift.scheduledStart).toLocaleDateString(i18n.language, {
                          month: 'short',
                          day: 'numeric',
                        })}
                        {' '}
                        {formatLocalTime(shift.scheduledStart, i18n.language)}
                        {' — '}
                        {formatLocalTime(shift.scheduledEnd, i18n.language)}
                      </p>
                      <span className="text-[11px] px-2 py-0.5 rounded border border-border text-text-secondary bg-surface">
                        {shift.status}
                      </span>
                    </div>
                  </div>
                ))}
              </div>
            )}

            {/* Shift history pagination */}
            {shiftHistoryTotalPages > 1 && (
              <div className="flex items-center justify-end gap-2 mt-4">
                <button
                  onClick={() => setHistoryPage((p) => Math.max(0, p - 1))}
                  disabled={historyPage === 0}
                  className="px-3 py-1.5 rounded text-[12px] border border-border text-text-secondary disabled:opacity-40"
                >
                  {t('prev')}
                </button>
                <span className="text-[12px] text-text-secondary">
                  {historyPage + 1} / {shiftHistoryTotalPages}
                </span>
                <button
                  onClick={() => setHistoryPage((p) => Math.min(shiftHistoryTotalPages - 1, p + 1))}
                  disabled={historyPage === shiftHistoryTotalPages - 1}
                  className="px-3 py-1.5 rounded text-[12px] border border-border text-text-secondary disabled:opacity-40"
                >
                  {t('next')}
                </button>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  )
}
