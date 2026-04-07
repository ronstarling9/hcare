import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { mockCaregivers } from '../../mock/data'
import { CaregiversTable } from './CaregiversTable'

export function CaregiversPage() {
  const { t } = useTranslation('caregivers')
  const tCommon = useTranslation('common').t
  const [search, setSearch] = useState('')

  return (
    <div className="flex flex-col h-full">
      <div className="flex items-center gap-4 px-6 py-4 bg-white border-b border-border">
        <h1 className="text-[16px] font-bold tracking-[-0.02em] text-dark">{t('pageTitle')}</h1>
        <input
          type="search"
          placeholder={tCommon('searchByName')}
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="ml-4 border border-border px-3 py-1.5 text-[13px] w-64 focus:outline-none focus:border-dark"
        />
        <button
          type="button"
          className="ml-auto px-4 py-1.5 text-[12px] font-bold bg-dark text-white hover:brightness-110"
          onClick={() => alert(t('addCaregiverAlert'))}
        >
          {t('addCaregiver')}
        </button>
      </div>
      <div className="flex-1 overflow-auto">
        <CaregiversTable caregivers={mockCaregivers} search={search} />
      </div>
    </div>
  )
}
