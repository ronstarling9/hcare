import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { mockCaregivers, mockCredentials } from '../../mock/data'
import { usePanelStore } from '../../store/panelStore'
import { formatLocalDate } from '../../utils/dateFormat'

function isExpiringSoon(expiryDate: string | null): boolean {
  if (!expiryDate) return false
  const days = (new Date(`${expiryDate}T12:00:00`).getTime() - Date.now()) / (1000 * 60 * 60 * 24)
  return days <= 30
}

type Tab = 'overview' | 'credentials' | 'backgroundChecks' | 'availability' | 'shiftHistory'

interface CaregiverDetailPanelProps {
  caregiverId: string
  backLabel: string
}

export function CaregiverDetailPanel({ caregiverId, backLabel }: CaregiverDetailPanelProps) {
  const { t, i18n } = useTranslation('caregivers')
  const tCommon = useTranslation('common').t
  const { closePanel } = usePanelStore()
  const [activeTab, setActiveTab] = useState<Tab>('overview')
  const caregiver = mockCaregivers.find((c) => c.id === caregiverId)

  const TABS: { id: Tab; label: string }[] = [
    { id: 'overview', label: t('tabOverview') },
    { id: 'credentials', label: t('tabCredentials') },
    { id: 'backgroundChecks', label: t('tabBackgroundChecks') },
    { id: 'availability', label: t('tabAvailability') },
    { id: 'shiftHistory', label: t('tabShiftHistory') },
  ]

  if (!caregiver) {
    return (
      <div className="p-8">
        <button type="button" className="text-[13px] mb-4" style={{ color: '#1a9afa' }} onClick={closePanel}>
          {backLabel}
        </button>
        <p className="text-text-secondary">{t('notFound')}</p>
      </div>
    )
  }

  const credentials = mockCredentials.filter((c) => c.caregiverId === caregiverId)

  return (
    <div className="flex flex-col h-full">
      {/* Header */}
      <div className="px-6 py-4 border-b border-border">
        <button
          type="button"
          className="text-[13px] mb-2 hover:underline"
          style={{ color: '#1a9afa' }}
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
        {activeTab === 'overview' && (
          <div className="grid grid-cols-2 gap-4">
            {[
              [t('fieldPhone'), caregiver.phone ?? tCommon('noDash')],
              [t('fieldAddress'), caregiver.address ?? tCommon('noDash')],
              [t('fieldHireDate'), caregiver.hireDate ? formatLocalDate(caregiver.hireDate, i18n.language) : tCommon('noDash')],
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
        {activeTab === 'credentials' && (
          <div>
            {credentials.length === 0 ? (
              <p className="text-text-secondary text-[13px]">{t('noCredentials')}</p>
            ) : (
              credentials.map((cred) => {
                const expiring = isExpiringSoon(cred.expiryDate)
                return (
                  <div key={cred.id} className="flex items-center gap-3 py-3 border-b border-border">
                    <div className="flex-1">
                      <div
                        className="text-[13px] font-medium"
                        style={{ color: expiring ? '#dc2626' : '#1a1a24' }}
                      >
                        {cred.credentialType}
                      </div>
                      <div className="text-[11px] text-text-secondary">
                        {t('credExpires')}{' '}
                        {cred.expiryDate
                          ? formatLocalDate(cred.expiryDate, i18n.language)
                          : tCommon('noExpiry')}
                        {expiring && (
                          <span className="ml-2 text-red-600 font-semibold">{t('credExpiringSoon')}</span>
                        )}
                      </div>
                    </div>
                    <span
                      className="text-[11px] font-semibold px-2 py-0.5"
                      style={{
                        background: cred.verified ? '#f0fdf4' : '#fef2f2',
                        color: cred.verified ? '#16a34a' : '#dc2626',
                      }}
                    >
                      {cred.verified ? t('credVerified') : t('credUnverified')}
                    </span>
                  </div>
                )
              })
            )}
          </div>
        )}
        {activeTab === 'backgroundChecks' && (
          <p className="text-text-secondary text-[13px]">{t('backgroundPhaseNote')}</p>
        )}
        {activeTab === 'availability' && (
          <p className="text-text-secondary text-[13px]">{t('availabilityPhaseNote')}</p>
        )}
        {activeTab === 'shiftHistory' && (
          <p className="text-text-secondary text-[13px]">{t('shiftHistoryPhaseNote')}</p>
        )}
      </div>
    </div>
  )
}
