import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { useCreateCaregiver } from '../../hooks/useCaregivers'
import { usePanelStore } from '../../store/panelStore'
import { useToastStore } from '../../store/toastStore'

interface FormValues {
  firstName: string
  lastName: string
  email: string
  phone: string
  address: string
  hireDate: string
  hasPet: boolean
}

interface Props {
  backLabel: string
}

export function NewCaregiverPanel({ backLabel }: Props) {
  const { t } = useTranslation('caregivers')
  const { t: tCommon } = useTranslation('common')
  const { closePanel, openPanel } = usePanelStore()
  const createMutation = useCreateCaregiver()
  const [apiError, setApiError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({
    defaultValues: {
      firstName: '',
      lastName: '',
      email: '',
      phone: '',
      address: '',
      hireDate: '',
      hasPet: false,
    },
  })

  function buildPayload(values: FormValues) {
    return {
      firstName: values.firstName.trim(),
      lastName: values.lastName.trim(),
      email: values.email.trim(),
      phone: values.phone || undefined,
      address: values.address || undefined,
      hireDate: values.hireDate || undefined,
      hasPet: values.hasPet,
    }
  }

  async function onSaveAndAddCredentials(values: FormValues) {
    setApiError(null)
    try {
      const caregiver = await createMutation.mutateAsync(buildPayload(values))
      closePanel()
      openPanel('caregiver', caregiver.id, {
        backLabel: t('backLabel'),
        initialTab: 'credentials',
      })
    } catch {
      setApiError(tCommon('errorTryAgain'))
    }
  }

  async function onSaveAndClose(values: FormValues) {
    setApiError(null)
    try {
      const caregiver = await createMutation.mutateAsync(buildPayload(values))
      useToastStore.getState().show({
        message: t('saveCloseToast'),
        linkLabel: t('saveCloseToastLink'),
        targetId: caregiver.id,
        panelType: 'caregiver',
        initialTab: 'credentials',
        backLabel: t('backLabel'),
      })
      closePanel()
    } catch {
      setApiError(tCommon('errorTryAgain'))
    }
  }

  const isPending = createMutation.isPending

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
        <h2 className="text-[16px] font-bold text-dark">{t('addCaregiverPanelTitle')}</h2>
        <p className="text-[12px] text-text-secondary mt-0.5">{t('addCaregiverRequiredNote')}</p>
      </div>

      {/* Scrollable form body */}
      <div className="flex-1 overflow-auto px-6 py-4 space-y-6">

        {/* Caregiver Identity */}
        <section>
          <h3 className="text-[10px] font-bold uppercase tracking-[0.1em] text-text-secondary mb-3">
            {t('sectionIdentity')}
          </h3>
          <div className="grid grid-cols-2 gap-3 mb-3">
            <div>
              <label htmlFor="firstName" className="block text-[12px] text-text-secondary mb-1">
                {t('fieldFirstName')} *
              </label>
              <input
                id="firstName"
                type="text"
                {...register('firstName', {
                  required: t('validationFirstNameRequired'),
                  validate: (v) => v.trim() !== '' || t('validationFirstNameRequired'),
                })}
                className="w-full border border-border px-3 py-1.5 text-[13px] focus:outline-none focus:border-dark"
              />
              {errors.firstName && (
                <p className="text-[11px] text-red-500 mt-0.5">{errors.firstName.message}</p>
              )}
            </div>
            <div>
              <label htmlFor="lastName" className="block text-[12px] text-text-secondary mb-1">
                {t('fieldLastName')} *
              </label>
              <input
                id="lastName"
                type="text"
                {...register('lastName', {
                  required: t('validationLastNameRequired'),
                  validate: (v) => v.trim() !== '' || t('validationLastNameRequired'),
                })}
                className="w-full border border-border px-3 py-1.5 text-[13px] focus:outline-none focus:border-dark"
              />
              {errors.lastName && (
                <p className="text-[11px] text-red-500 mt-0.5">{errors.lastName.message}</p>
              )}
            </div>
          </div>
          <div>
            <label htmlFor="email" className="block text-[12px] text-text-secondary mb-1">
              {t('fieldEmail')} *
            </label>
            <input
              id="email"
              type="email"
              {...register('email', {
                required: t('validationEmailRequired'),
                pattern: {
                  value: /^[^\s@]+@[^\s@]+\.[^\s@]+$/,
                  message: t('validationEmailInvalid'),
                },
              })}
              className="w-full border border-border px-3 py-1.5 text-[13px] focus:outline-none focus:border-dark"
            />
            {errors.email && (
              <p className="text-[11px] text-red-500 mt-0.5">{errors.email.message}</p>
            )}
          </div>
        </section>

        {/* Employment & Contact */}
        <section>
          <h3 className="text-[10px] font-bold uppercase tracking-[0.1em] text-text-secondary mb-3">
            {t('sectionEmployment')}
          </h3>
          <div className="space-y-3">
            <div>
              <label htmlFor="phone" className="block text-[12px] text-text-secondary mb-1">
                {t('fieldPhone')}
              </label>
              <input
                id="phone"
                type="tel"
                {...register('phone')}
                className="w-full border border-border px-3 py-1.5 text-[13px] focus:outline-none focus:border-dark"
              />
            </div>
            <div>
              <label htmlFor="address" className="block text-[12px] text-text-secondary mb-1">
                {t('fieldAddress')}
              </label>
              <input
                id="address"
                type="text"
                {...register('address')}
                className="w-full border border-border px-3 py-1.5 text-[13px] focus:outline-none focus:border-dark"
              />
            </div>
            <div>
              <label htmlFor="hireDate" className="block text-[12px] text-text-secondary mb-1">
                {t('fieldHireDate')}
              </label>
              <input
                id="hireDate"
                type="date"
                {...register('hireDate')}
                className="border border-border px-3 py-1.5 text-[13px] focus:outline-none focus:border-dark"
              />
            </div>
            <label className="flex items-center gap-2 text-[13px] cursor-pointer">
              <input type="checkbox" {...register('hasPet')} className="w-4 h-4" />
              {t('fieldHasPet')}
            </label>
          </div>
        </section>
      </div>

      {/* API error banner */}
      {apiError && (
        <div
          role="alert"
          className="mx-6 mb-2 px-4 py-2 bg-red-50 border border-red-200 text-[12px] text-red-700"
        >
          {apiError}
        </div>
      )}

      {/* Footer */}
      <div className="px-6 py-4 border-t border-border flex gap-3">
        <button
          type="button"
          disabled={isPending}
          onClick={handleSubmit(onSaveAndAddCredentials)}
          className="flex-1 py-2 text-[13px] font-bold bg-dark text-white disabled:opacity-50 hover:brightness-110"
        >
          {t('saveAndAddCredentials')}
        </button>
        <button
          type="button"
          disabled={isPending}
          onClick={handleSubmit(onSaveAndClose)}
          className="px-4 py-2 text-[13px] font-bold border border-border text-text-primary disabled:opacity-50 hover:bg-surface"
        >
          {t('saveAndClose')}
        </button>
      </div>
    </div>
  )
}
