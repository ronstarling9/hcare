import { useTranslation } from 'react-i18next'
import { useForm } from 'react-hook-form'
import { mockClients } from '../../mock/data'
import { usePanelStore } from '../../store/panelStore'

interface FormValues {
  clientId: string
  serviceTypeId: string
  date: string
  startTime: string
  endTime: string
  caregiverId: string
}

interface NewShiftPanelProps {
  prefill: { date?: string; time?: string } | null
  backLabel: string
}

export function NewShiftPanel({ prefill, backLabel }: NewShiftPanelProps) {
  const { t } = useTranslation('newShift')
  const tCommon = useTranslation('common').t
  const { closePanel } = usePanelStore()
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({
    defaultValues: {
      date: prefill?.date ?? [
        new Date().getFullYear(),
        String(new Date().getMonth() + 1).padStart(2, '0'),
        String(new Date().getDate()).padStart(2, '0'),
      ].join('-'),
      startTime: prefill?.time ?? '09:00',
      endTime: '13:00',
    },
  })

  function onSubmit(values: FormValues) {
    // Phase 4: replace with API call
    alert(t('mockAlert', { clientId: values.clientId, date: values.date }))
    closePanel()
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
        <h2 className="text-[16px] font-bold text-dark">{t('panelTitle')}</h2>
      </div>

      {/* Form */}
      <form
        onSubmit={handleSubmit(onSubmit)}
        className="flex-1 overflow-auto px-6 py-4 space-y-4"
      >
        {/* Client */}
        <div>
          <label className="block text-[10px] font-bold uppercase tracking-[0.1em] text-text-secondary mb-1">
            {t('labelClient')}
          </label>
          <select
            {...register('clientId', { required: t('validationClientRequired') })}
            className="w-full border border-border px-3 py-2 text-[13px] text-dark bg-white"
          >
            <option value="">{t('selectClient')}</option>
            {mockClients.map((c) => (
              <option key={c.id} value={c.id}>
                {c.firstName} {c.lastName}
              </option>
            ))}
          </select>
          {errors.clientId && (
            <p className="text-[11px] text-red-600 mt-1">{errors.clientId.message}</p>
          )}
        </div>

        {/* Service Type (static for Phase 1) */}
        <div>
          <label className="block text-[10px] font-bold uppercase tracking-[0.1em] text-text-secondary mb-1">
            {t('labelServiceType')}
          </label>
          <select
            {...register('serviceTypeId', { required: t('validationServiceTypeRequired') })}
            className="w-full border border-border px-3 py-2 text-[13px] text-dark bg-white"
          >
            <option value="">{t('selectServiceType')}</option>
            <option value="st000000-0000-0000-0000-000000000001">{t('serviceTypePcs')}</option>
          </select>
          {errors.serviceTypeId && (
            <p className="text-[11px] text-red-600 mt-1">{errors.serviceTypeId.message}</p>
          )}
        </div>

        {/* Date */}
        <div>
          <label className="block text-[10px] font-bold uppercase tracking-[0.1em] text-text-secondary mb-1">
            {t('labelDate')}
          </label>
          <input
            type="date"
            {...register('date', { required: t('validationDateRequired') })}
            className="w-full border border-border px-3 py-2 text-[13px] text-dark"
          />
        </div>

        {/* Start / End time */}
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block text-[10px] font-bold uppercase tracking-[0.1em] text-text-secondary mb-1">
              {t('labelStartTime')}
            </label>
            <input
              type="time"
              {...register('startTime', { required: true })}
              className="w-full border border-border px-3 py-2 text-[13px] text-dark"
            />
          </div>
          <div>
            <label className="block text-[10px] font-bold uppercase tracking-[0.1em] text-text-secondary mb-1">
              {t('labelEndTime')}
            </label>
            <input
              type="time"
              {...register('endTime', { required: true })}
              className="w-full border border-border px-3 py-2 text-[13px] text-dark"
            />
          </div>
        </div>

        {/* Caregiver (optional) */}
        <div>
          <label className="block text-[10px] font-bold uppercase tracking-[0.1em] text-text-secondary mb-1">
            {t('labelCaregiver')}
          </label>
          <select
            {...register('caregiverId')}
            className="w-full border border-border px-3 py-2 text-[13px] text-dark bg-white"
          >
            <option value="">{t('caregiverUnassigned')}</option>
          </select>
          <p className="text-[10px] text-text-secondary mt-1">
            {t('caregiverPhaseNote')}
          </p>
        </div>

        {/* Footer */}
        <div className="pt-4 border-t border-border flex gap-3">
          <button
            type="submit"
            className="px-4 py-2 text-[12px] font-bold bg-dark text-white hover:brightness-110"
          >
            {t('saveShift')}
          </button>
          <button
            type="button"
            onClick={closePanel}
            className="px-4 py-2 text-[12px] font-semibold border border-border text-dark"
          >
            {tCommon('cancel')}
          </button>
        </div>
      </form>
    </div>
  )
}
