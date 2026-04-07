import { useTranslation } from 'react-i18next'
import { mockPayers } from '../../mock/data'
import { usePanelStore } from '../../store/panelStore'

export function PayersPage() {
  const { t } = useTranslation('payers')
  const tCommon = useTranslation('common').t
  const { openPanel } = usePanelStore()

  const PAYER_TYPE_LABEL: Record<string, string> = {
    MEDICAID: t('typeMedicaid'),
    PRIVATE_PAY: t('typePrivatePay'),
    LTC_INSURANCE: t('typeLtcInsurance'),
    VA: t('typeVa'),
    MEDICARE: t('typeMedicare'),
  }

  return (
    <div className="flex flex-col h-full">
      <div className="flex items-center px-6 py-4 bg-white border-b border-border">
        <h1 className="text-[16px] font-bold tracking-[-0.02em] text-dark">{t('pageTitle')}</h1>
        <button
          type="button"
          className="ml-auto px-4 py-1.5 text-[12px] font-bold bg-dark text-white hover:brightness-110"
          onClick={() => alert(t('addPayerAlert'))}
        >
          {t('addPayer')}
        </button>
      </div>
      <div className="flex-1 overflow-auto">
        <table className="w-full text-[13px]">
          <thead>
            <tr className="border-b border-border bg-surface">
              {[t('colPayerName'), t('colType'), t('colState'), t('colEvvAggregator')].map((h) => (
                <th
                  key={h}
                  className="text-left px-6 py-2 text-[9px] font-bold uppercase tracking-[0.1em] text-text-secondary"
                >
                  {h}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {mockPayers.map((payer) => (
              <tr key={payer.id} className="border-b border-border hover:bg-surface cursor-pointer" onClick={() => openPanel('payer', payer.id)}>
                <td className="px-6 py-3 font-medium text-dark">{payer.name}</td>
                <td className="px-6 py-3 text-text-secondary">
                  {PAYER_TYPE_LABEL[payer.payerType] ?? payer.payerType}
                </td>
                <td className="px-6 py-3 text-text-secondary">{payer.state}</td>
                <td className="px-6 py-3 text-text-secondary">{payer.evvAggregator ?? tCommon('noDash')}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
