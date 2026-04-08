import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { useCreateClient } from '../../hooks/useClients'
import { usePanelStore } from '../../store/panelStore'
import { useToastStore } from '../../store/toastStore'

const US_STATES = [
  'AL','AK','AZ','AR','CA','CO','CT','DE','FL','GA',
  'HI','ID','IL','IN','IA','KS','KY','LA','ME','MD',
  'MA','MI','MN','MS','MO','MT','NE','NV','NH','NJ',
  'NM','NY','NC','ND','OH','OK','OR','PA','RI','SC',
  'SD','TN','TX','UT','VT','VA','WA','WV','WI','WY',
]

interface FormValues {
  firstName: string
  lastName: string
  dateOfBirth: string
  phone: string
  address: string
  serviceState: string
  medicaidId: string
  preferredCaregiverGender: string
  preferredLanguages: string
  noPetCaregiver: boolean
}

interface Props {
  backLabel: string
}

export function NewClientPanel({ backLabel }: Props) {
  const { t } = useTranslation('clients')
  const { t: tCommon } = useTranslation('common')
  const { closePanel, openPanel } = usePanelStore()
  const createMutation = useCreateClient()
  const [apiError, setApiError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({
    defaultValues: {
      firstName: '',
      lastName: '',
      dateOfBirth: '',
      phone: '',
      address: '',
      serviceState: '',
      medicaidId: '',
      preferredCaregiverGender: '',
      preferredLanguages: '',
      noPetCaregiver: false,
    },
  })

  function buildPayload(values: FormValues) {
    return {
      firstName: values.firstName.trim(),
      lastName: values.lastName.trim(),
      dateOfBirth: values.dateOfBirth,
      phone: values.phone || undefined,
      address: values.address || undefined,
      serviceState: values.serviceState || undefined,      // empty select → omit, not ""
      medicaidId: values.medicaidId || undefined,
      preferredCaregiverGender: values.preferredCaregiverGender || undefined, // "no preference" → omit
      preferredLanguages: values.preferredLanguages
        ? JSON.stringify(
            values.preferredLanguages.split(',').map((s) => s.trim()).filter(Boolean)
          )
        : undefined,
      noPetCaregiver: values.noPetCaregiver,
    }
  }

  async function onSaveAndAddAuth(values: FormValues) {
    setApiError(null)
    try {
      const client = await createMutation.mutateAsync(buildPayload(values))
      closePanel()
      openPanel('client', client.id, {
        backLabel: t('backLabel'),
        initialTab: 'authorizations',
      })
    } catch {
      setApiError(tCommon('errorTryAgain'))
    }
  }

  async function onSaveAndClose(values: FormValues) {
    setApiError(null)
    try {
      const client = await createMutation.mutateAsync(buildPayload(values))
      useToastStore.getState().show({
        message: t('saveCloseToast'),
        linkLabel: t('saveCloseToastLink'),
        targetId: client.id,
        panelType: 'client',
        initialTab: 'authorizations',
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
        <h2 className="text-[16px] font-bold text-dark">{t('addClientPanelTitle')}</h2>
        <p className="text-[12px] text-text-secondary mt-0.5">{t('addClientRequiredNote')}</p>
      </div>

      {/* Scrollable form body */}
      <div className="flex-1 overflow-auto px-6 py-4 space-y-6">

        {/* Client Identity */}
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
            <label htmlFor="dateOfBirth" className="block text-[12px] text-text-secondary mb-1">
              {t('fieldDateOfBirth')} *
            </label>
            <input
              id="dateOfBirth"
              type="date"
              {...register('dateOfBirth', {
                required: t('validationDobRequired'),
                validate: (v) => {
                  if (!v) return true  // required rule handles empty
                  return v <= new Date().toISOString().slice(0, 10) || t('validationDobFuture')
                },
              })}
              className="border border-border px-3 py-1.5 text-[13px] focus:outline-none focus:border-dark"
            />
            {errors.dateOfBirth && (
              <p className="text-[11px] text-red-500 mt-0.5">{errors.dateOfBirth.message}</p>
            )}
          </div>
        </section>

        {/* Contact & Location */}
        <section>
          <h3 className="text-[10px] font-bold uppercase tracking-[0.1em] text-text-secondary mb-3">
            {t('sectionContact')}
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
              <label htmlFor="serviceState" className="block text-[12px] text-text-secondary mb-1">
                {t('fieldServiceState')}
              </label>
              <select
                id="serviceState"
                {...register('serviceState')}
                className="w-full border border-border px-3 py-1.5 text-[13px] bg-white focus:outline-none focus:border-dark"
              >
                <option value="">{t('fieldSelectState')}</option>
                {US_STATES.map((s) => (
                  <option key={s} value={s}>{s}</option>
                ))}
              </select>
            </div>
          </div>
        </section>

        {/* Billing */}
        <section>
          <h3 className="text-[10px] font-bold uppercase tracking-[0.1em] text-text-secondary mb-3">
            {t('sectionBilling')}
          </h3>
          <div>
            <label htmlFor="medicaidId" className="block text-[12px] text-text-secondary mb-1">
              {t('fieldMedicaidId')}
            </label>
            <input
              id="medicaidId"
              type="text"
              autoComplete="off"
              {...register('medicaidId')}
              className="w-full border border-border px-3 py-1.5 text-[13px] focus:outline-none focus:border-dark"
            />
          </div>
        </section>

        {/* Care Preferences */}
        <section>
          <h3 className="text-[10px] font-bold uppercase tracking-[0.1em] text-text-secondary mb-3">
            {t('sectionPreferences')}
          </h3>
          <div className="space-y-3">
            <div>
              <label htmlFor="preferredCaregiverGender" className="block text-[12px] text-text-secondary mb-1">
                {t('fieldPreferredGender')}
              </label>
              <select
                id="preferredCaregiverGender"
                {...register('preferredCaregiverGender')}
                className="w-full border border-border px-3 py-1.5 text-[13px] bg-white focus:outline-none focus:border-dark"
              >
                <option value="">{t('fieldGenderNoPreference')}</option>
                <option value="FEMALE">{t('fieldGenderFemale')}</option>
                <option value="MALE">{t('fieldGenderMale')}</option>
              </select>
            </div>
            <div>
              <label htmlFor="preferredLanguages" className="block text-[12px] text-text-secondary mb-1">
                {t('fieldPreferredLanguages')}
              </label>
              <input
                id="preferredLanguages"
                type="text"
                placeholder={t('fieldPreferredLanguagesHint')}
                {...register('preferredLanguages')}
                className="w-full border border-border px-3 py-1.5 text-[13px] focus:outline-none focus:border-dark"
              />
            </div>
            <label className="flex items-center gap-2 text-[13px] cursor-pointer">
              <input type="checkbox" {...register('noPetCaregiver')} className="w-4 h-4" />
              {t('fieldNoPetCaregiver')}
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
          onClick={handleSubmit(onSaveAndAddAuth)}
          className="flex-1 py-2 text-[13px] font-bold bg-dark text-white disabled:opacity-50 hover:brightness-110"
        >
          {t('saveAndAddAuth')}
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
