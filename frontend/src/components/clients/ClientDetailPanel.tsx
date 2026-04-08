import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { usePanelStore } from '../../store/panelStore'
import { formatLocalDate } from '../../utils/dateFormat'
import { useClientDetail, useClientAuthorizations } from '../../hooks/useClients'

type Tab = 'overview' | 'carePlan' | 'authorizations' | 'documents' | 'familyPortal'

interface ClientDetailPanelProps {
  clientId: string
  backLabel: string
}

export function ClientDetailPanel({ clientId, backLabel }: ClientDetailPanelProps) {
  const { t, i18n } = useTranslation('clients')
  const tCommon = useTranslation('common').t
  const { closePanel } = usePanelStore()
  const [activeTab, setActiveTab] = useState<Tab>('overview')

  const { data: client, isLoading, isError } = useClientDetail(clientId)
  const { data: authsPage } = useClientAuthorizations(clientId)
  const authorizations = authsPage?.content ?? []

  const TABS: { id: Tab; label: string }[] = [
    { id: 'overview', label: t('tabOverview') },
    { id: 'carePlan', label: t('tabCarePlan') },
    { id: 'authorizations', label: t('tabAuthorizations') },
    { id: 'documents', label: t('tabDocuments') },
    { id: 'familyPortal', label: t('tabFamilyPortal') },
  ]

  if (isLoading && !client) {
    return (
      <div className="flex flex-col h-full">
        <div className="px-6 py-4 border-b border-border">
          <button
            type="button"
            className="text-[13px] mb-2 hover:underline"
            style={{ color: '#1a9afa' }}
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

  if (isError) {
    return (
      <div className="p-8">
        <button type="button" className="text-blue text-[13px] mb-4" onClick={closePanel}>
          {backLabel}
        </button>
        <p className="text-text-secondary">{t('loadError')}</p>
      </div>
    )
  }

  if (!client) {
    return (
      <div className="p-8">
        <button type="button" className="text-blue text-[13px] mb-4" onClick={closePanel}>
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
          className="text-[13px] mb-2 hover:underline"
          style={{ color: '#1a9afa' }}
          onClick={closePanel}
        >
          {backLabel}
        </button>
        <h2 className="text-[16px] font-bold text-dark">
          {client.firstName} {client.lastName}
        </h2>
        <p className="text-[12px] text-text-secondary mt-0.5">
          DOB: {formatLocalDate(client.dateOfBirth, i18n.language)} ·{' '}
          {client.medicaidId ?? 'No Medicaid ID'}
        </p>
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
              [t('fieldPhone'), client.phone ?? tCommon('noDash')],
              [t('fieldAddress'), client.address ?? tCommon('noDash')],
              [t('fieldServiceState'), client.serviceState ?? tCommon('noDash')],
              [t('fieldStatus'), client.status],
              [t('fieldPreferredLanguage'), client.preferredLanguages ?? tCommon('noDash')],
              [t('fieldNoPetCaregiver'), client.noPetCaregiver ? tCommon('yes') : tCommon('no')],
            ].map(([label, value]) => (
              <div key={label}>
                <div className="text-[10px] text-text-secondary">{label}</div>
                <div className="text-[13px] text-dark">{value}</div>
              </div>
            ))}
          </div>
        )}
        {activeTab === 'carePlan' && (
          <p className="text-text-secondary text-[13px]">{t('carePlanPhaseNote')}</p>
        )}
        {activeTab === 'authorizations' && (
          <div>
            {authorizations.length === 0 ? (
              <p className="text-text-secondary text-[13px]">{t('noAuthorizations')}</p>
            ) : (
              authorizations.map((auth) => {
                const pct = (auth.usedUnits / auth.authorizedUnits) * 100
                return (
                  <div key={auth.id} className="border border-border p-4 mb-3">
                    <div className="flex justify-between mb-2">
                      <span className="text-[13px] font-medium text-dark">{t('authHeader', { authNumber: auth.authNumber })}</span>
                      <span className="text-[12px] text-text-secondary">
                        {t('authUnitsUsed', { used: auth.usedUnits, authorized: auth.authorizedUnits, unitType: auth.unitType.toLowerCase() })}
                      </span>
                    </div>
                    <div className="w-full bg-border h-2">
                      <div
                        className="h-2"
                        style={{
                          width: `${Math.min(100, pct)}%`,
                          background: pct > 80 ? '#dc2626' : '#1a9afa',
                        }}
                      />
                    </div>
                    <div className="text-[10px] text-text-secondary mt-1">
                      {formatLocalDate(auth.startDate, i18n.language)} – {formatLocalDate(auth.endDate, i18n.language)}
                    </div>
                  </div>
                )
              })
            )}
          </div>
        )}
        {activeTab === 'documents' && (
          <p className="text-text-secondary text-[13px]">{t('documentsPhaseNote')}</p>
        )}
        {activeTab === 'familyPortal' && (
          <p className="text-text-secondary text-[13px]">{t('familyPortalPhaseNote')}</p>
        )}
      </div>
    </div>
  )
}
