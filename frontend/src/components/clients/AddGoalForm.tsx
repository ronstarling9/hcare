import { useState } from 'react'
import { useTranslation } from 'react-i18next'

interface AddGoalFormProps {
  onConfirm: (description: string, targetDate: string | null) => void
  onCancel: () => void
  isLoading?: boolean
}

export function AddGoalForm({ onConfirm, onCancel, isLoading }: AddGoalFormProps) {
  const { t } = useTranslation('clients')
  const [description, setDescription] = useState('')
  const [targetDate, setTargetDate] = useState('')

  const handleConfirm = () => {
    onConfirm(description.trim(), targetDate.trim() || null)
  }

  return (
    <div className="border border-blue bg-white p-3 mb-[3px]">
      <div className="mb-2">
        <label
          htmlFor="goal-description"
          className="block text-[9px] font-bold tracking-[.08em] uppercase text-text-secondary mb-1"
        >
          {t('goalFieldDescription')}
        </label>
        <input
          id="goal-description"
          type="text"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          placeholder="e.g. Self-dress upper body without prompting"
          className="w-full border border-border px-2.5 py-1.5 text-[12px] text-text-primary"
        />
      </div>
      <div className="mb-3">
        <label
          htmlFor="goal-target-date"
          className="block text-[9px] font-bold tracking-[.08em] uppercase text-text-secondary mb-1"
        >
          {t('goalFieldTargetDate')}{' '}
          <span className="font-normal normal-case tracking-normal">
            {t('goalFieldTargetDateOptional')}
          </span>
        </label>
        <input
          id="goal-target-date"
          type="date"
          value={targetDate}
          onChange={(e) => setTargetDate(e.target.value)}
          className="w-full border border-border px-2.5 py-1.5 text-[12px] text-text-secondary"
        />
      </div>
      <div className="flex gap-2 justify-end">
        <button
          type="button"
          onClick={onCancel}
          className="border border-border text-text-secondary text-[11px] px-3 py-1.5 bg-transparent"
        >
          Cancel
        </button>
        <button
          type="button"
          onClick={handleConfirm}
          disabled={!description.trim() || isLoading}
          className="bg-dark text-white text-[11px] font-bold px-3.5 py-1.5 border-none disabled:opacity-40"
        >
          {t('goalsAddButton')}
        </button>
      </div>
    </div>
  )
}
