import { useTranslation } from 'react-i18next'
import { useForm } from 'react-hook-form'
import { useAllClients } from '../../hooks/useClients'
import { useAllCaregivers } from '../../hooks/useCaregivers'
import { useCreateShift } from '../../hooks/useShifts'
import { usePanelStore } from '../../store/panelStore'
import { useServiceTypes } from '../../hooks/useServiceTypes'

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
  const { clients } = useAllClients()
  const { caregivers } = useAllCaregivers()
  const createMutation = useCreateShift()
  const { serviceTypes, isLoading: serviceTypesLoading, isError: serviceTypesError } = useServiceTypes()

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
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

  async function onSubmit(values: FormValues) {
    try {
      await createMutation.mutateAsync({
        clientId: values.clientId,
        caregiverId: values.caregiverId || undefined,
        serviceTypeId: values.serviceTypeId,
        scheduledStart: `${values.date}T${values.startTime}:00`,
        scheduledEnd: `${values.date}T${values.endTime}:00`,
      })
      closePanel()
    } catch {
      // createMutation.isError displays the error banner; do not close panel
    }
  }

  return (
    <div className="flex flex-col h-full">
      {/* Header */}
      <div className="px-6 py-4 border-b border-border">
        <button
          type="button"
          className="text-[13px] mb-2 hover:underline text-blue"
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
          <label htmlFor="ns-client" className="block text-[10px] font-bold uppercase tracking-[0.1em] text-text-secondary mb-1">
            {t('labelClient')}
          </label>
          <select
            id="ns-client"
            {...register('clientId', { required: t('validationClientRequired') })}
            className="w-full border border-border px-3 py-2 text-[13px] text-dark bg-white"
          >
            <option value="">{t('selectClient')}</option>
            {clients.map((c) => (
              <option key={c.id} value={c.id}>
                {c.firstName} {c.lastName}
              </option>
            ))}
          </select>
          {errors.clientId && (
            <p className="text-[11px] text-red-600 mt-1">{errors.clientId.message}</p>
          )}
        </div>

        {/* Service Type */}
        <div>
          <label
            htmlFor="ns-service-type"
            className="block text-[10px] font-bold uppercase tracking-[0.1em] text-text-secondary mb-1"
          >
            {t('labelServiceType')}
          </label>
          <select
            id="ns-service-type"
            {...register('serviceTypeId', { required: t('validationServiceTypeRequired') })}
            disabled={serviceTypesLoading || serviceTypesError || serviceTypes.length === 0}
            aria-busy={serviceTypesLoading ? "true" : "false"}
            aria-describedby={[
              (serviceTypesError || (!serviceTypesLoading && serviceTypes.length === 0))
                ? "ns-service-type-hint"
                : null,
              errors.serviceTypeId ? "ns-service-type-error" : null,
            ].filter(Boolean).join(' ') || undefined}
            className="w-full border border-border px-3 py-2 text-[13px] text-dark bg-white disabled:opacity-50"
          >
            {serviceTypesLoading ? (
              <option value="">{t('loadingServiceTypes')}</option>
            ) : serviceTypesError ? (
              <option value="">{t('serviceTypesLoadError')}</option>
            ) : serviceTypes.length === 0 ? (
              <option value="">{t('noServiceTypesOption')}</option>
            ) : (
              <>
                <option value="">{t('selectServiceType')}</option>
                {serviceTypes.map((st) => (
                  <option key={st.id} value={st.id}>
                    {st.name}
                  </option>
                ))}
              </>
            )}
          </select>
          {serviceTypesError && (
            <p id="ns-service-type-hint" className="text-[11px] text-red-600 mt-1">
              {t('serviceTypesLoadErrorRetry')}
            </p>
          )}
          {!serviceTypesError && serviceTypes.length === 0 && !serviceTypesLoading && (
            <p id="ns-service-type-hint" className="text-[11px] text-text-muted mt-1">
              {t('noServiceTypesHint')}
            </p>
          )}
          {errors.serviceTypeId && (
            <p id="ns-service-type-error" className="text-[11px] text-red-600 mt-1">{errors.serviceTypeId.message}</p>
          )}
        </div>

        {/* Date */}
        <div>
          <label htmlFor="ns-date" className="block text-[10px] font-bold uppercase tracking-[0.1em] text-text-secondary mb-1">
            {t('labelDate')}
          </label>
          <input
            id="ns-date"
            type="date"
            {...register('date', { required: t('validationDateRequired') })}
            className="w-full border border-border px-3 py-2 text-[13px] text-dark"
          />
        </div>

        {/* Start / End time */}
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label htmlFor="ns-start-time" className="block text-[10px] font-bold uppercase tracking-[0.1em] text-text-secondary mb-1">
              {t('labelStartTime')}
            </label>
            <input
              id="ns-start-time"
              type="time"
              {...register('startTime', { required: t('validationRequired') })}
              className="w-full border border-border px-3 py-2 text-[13px] text-dark"
            />
            {errors.startTime && (
              <p className="text-[11px] text-red-600 mt-1">{errors.startTime.message}</p>
            )}
          </div>
          <div>
            <label htmlFor="ns-end-time" className="block text-[10px] font-bold uppercase tracking-[0.1em] text-text-secondary mb-1">
              {t('labelEndTime')}
            </label>
            <input
              id="ns-end-time"
              type="time"
              {...register('endTime', { required: t('validationRequired') })}
              className="w-full border border-border px-3 py-2 text-[13px] text-dark"
            />
            {errors.endTime && (
              <p className="text-[11px] text-red-600 mt-1">{errors.endTime.message}</p>
            )}
          </div>
        </div>

        {/* Caregiver (optional) */}
        <div>
          <label htmlFor="ns-caregiver" className="block text-[10px] font-bold uppercase tracking-[0.1em] text-text-secondary mb-1">
            {t('labelCaregiver')}
          </label>
          <select
            id="ns-caregiver"
            {...register('caregiverId')}
            className="w-full border border-border px-3 py-2 text-[13px] text-dark bg-white"
          >
            <option value="">{t('caregiverUnassigned')}</option>
            {caregivers.map((c) => (
              <option key={c.id} value={c.id}>
                {c.firstName} {c.lastName}
              </option>
            ))}
          </select>
        </div>

        {/* API error */}
        {createMutation.isError && (
          <p className="text-[11px] text-red-600">{tCommon('errorTryAgain')}</p>
        )}

        {/* Footer */}
        <div className="pt-4 border-t border-border flex gap-3">
          <button
            type="submit"
            disabled={isSubmitting || createMutation.isPending || serviceTypesLoading || serviceTypesError || serviceTypes.length === 0}
            className="px-4 py-2 text-[12px] font-bold bg-dark text-white hover:brightness-110 disabled:opacity-50"
          >
            {isSubmitting || createMutation.isPending ? '…' : t('saveShift')}
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
