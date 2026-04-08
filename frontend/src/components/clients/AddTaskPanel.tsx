import { useState, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { useAdlTaskTemplates } from '../../hooks/useCarePlan'
import type { AssistanceLevel } from '../../types/api'

interface TaskData {
  name: string
  assistanceLevel: AssistanceLevel
  frequency: string
  instructions: string
}

interface AdlTemplate {
  name: string
  defaultAssistanceLevel: AssistanceLevel
  defaultFrequency: string
  defaultInstructions: string
}

interface AddTaskPanelProps {
  onConfirm: (task: TaskData) => void
  onCancel: () => void
  isLoading?: boolean
}

const ASSISTANCE_OPTIONS: { value: AssistanceLevel; labelKey: string }[] = [
  { value: 'INDEPENDENT', labelKey: 'adlTaskAssistanceIndependent' },
  { value: 'SUPERVISION', labelKey: 'adlTaskAssistanceSupervision' },
  { value: 'MINIMAL_ASSIST', labelKey: 'adlTaskAssistanceMinimal' },
  { value: 'MODERATE_ASSIST', labelKey: 'adlTaskAssistanceModerate' },
  { value: 'MAXIMUM_ASSIST', labelKey: 'adlTaskAssistanceMaximum' },
  { value: 'DEPENDENT', labelKey: 'adlTaskAssistanceDependent' },
]

export function AddTaskPanel({ onConfirm, onCancel, isLoading }: AddTaskPanelProps) {
  const { t } = useTranslation('clients')
  const { data: templates = [] } = useAdlTaskTemplates()

  const [search, setSearch] = useState('')
  const [selectedName, setSelectedName] = useState('')
  const [assistanceLevel, setAssistanceLevel] = useState<AssistanceLevel>('SUPERVISION')
  const [frequency, setFrequency] = useState('')
  const [instructions, setInstructions] = useState('')
  const [templateApplied, setTemplateApplied] = useState(false)
  const [showDropdown, setShowDropdown] = useState(false)

  const filtered = useMemo<AdlTemplate[]>(() => {
    if (!search.trim()) return []
    const lower = search.toLowerCase()
    return (templates as AdlTemplate[])
      .filter((t) => t.name.toLowerCase().includes(lower))
      .slice(0, 8)
  }, [search, templates])

  const handleSelectTemplate = (template: AdlTemplate) => {
    setSelectedName(template.name)
    setSearch(template.name)
    setAssistanceLevel(template.defaultAssistanceLevel)
    setFrequency(template.defaultFrequency)
    setInstructions(template.defaultInstructions)
    setTemplateApplied(true)
    setShowDropdown(false)
  }

  const handleSelectCustom = () => {
    setSelectedName(search.trim())
    setTemplateApplied(false)
    setShowDropdown(false)
  }

  const handleSearchChange = (value: string) => {
    setSearch(value)
    setSelectedName('')
    setTemplateApplied(false)
    setShowDropdown(true)
  }

  const handleConfirm = () => {
    const name = selectedName || search.trim()
    onConfirm({ name, assistanceLevel, frequency, instructions })
  }

  const taskName = selectedName || search.trim()

  return (
    <div
      className="absolute right-0 top-0 bottom-0 w-[62%] bg-white border-l border-border flex flex-col p-4 gap-2.5"
      style={{ zIndex: 10 }}
    >
      {/* Header */}
      <div className="flex items-center justify-between mb-0.5">
        <span className="text-[13px] font-bold text-text-primary">{t('adlTaskAddTitle')}</span>
        <button
          type="button"
          onClick={onCancel}
          className="bg-transparent border-none text-text-secondary text-[14px] leading-none cursor-pointer"
        >
          ✕
        </button>
      </div>

      {/* Task name combobox */}
      <div className="relative">
        <div className="text-[9px] font-bold tracking-[.08em] uppercase text-text-secondary mb-1">
          {t('adlTaskFieldName')}
        </div>
        <input
          type="text"
          value={search}
          onChange={(e) => handleSearchChange(e.target.value)}
          onFocus={() => setShowDropdown(true)}
          placeholder={t('adlTaskSearchPlaceholder')}
          className={`w-full border px-2.5 py-1.5 text-[12px] text-text-primary outline-none ${
            templateApplied ? 'border-blue' : 'border-border'
          }`}
        />
        {templateApplied && (
          <div className="text-[10px] text-green-600 mt-0.5">✓ {t('adlTaskTemplateApplied')}</div>
        )}
        {showDropdown && search.trim() && (
          <div className="absolute z-20 left-0 right-0 bg-white border border-border shadow-sm">
            {filtered.map((tmpl) => (
              <button
                key={tmpl.name}
                type="button"
                className="w-full text-left px-2.5 py-1.5 text-[12px] text-text-primary hover:bg-surface"
                onMouseDown={() => handleSelectTemplate(tmpl)}
              >
                {tmpl.name}
              </button>
            ))}
            <button
              type="button"
              className="w-full text-left px-2.5 py-1.5 text-[12px] text-text-secondary hover:bg-surface border-t border-border"
              onMouseDown={handleSelectCustom}
            >
              {t('adlTaskCustomOption', { value: search.trim() })}
            </button>
          </div>
        )}
      </div>

      {/* Assistance level */}
      <div>
        <div className="text-[9px] font-bold tracking-[.08em] uppercase text-text-secondary mb-1">
          {t('adlTaskFieldAssistanceLevel')}
        </div>
        <select
          value={assistanceLevel}
          onChange={(e) => setAssistanceLevel(e.target.value as AssistanceLevel)}
          className="w-full border border-border px-2.5 py-1.5 text-[12px] text-text-primary bg-white"
        >
          {ASSISTANCE_OPTIONS.map((opt) => (
            <option key={opt.value} value={opt.value}>
              {t(opt.labelKey)}
            </option>
          ))}
        </select>
      </div>

      {/* Frequency */}
      <div>
        <div className="text-[9px] font-bold tracking-[.08em] uppercase text-text-secondary mb-1">
          {t('adlTaskFieldFrequency')}
        </div>
        <input
          type="text"
          value={frequency}
          onChange={(e) => setFrequency(e.target.value)}
          className="w-full border border-border px-2.5 py-1.5 text-[12px] text-text-primary"
        />
      </div>

      {/* Instructions */}
      <div>
        <div className="text-[9px] font-bold tracking-[.08em] uppercase text-text-secondary mb-1">
          {t('adlTaskFieldInstructions')}{' '}
          <span className="font-normal normal-case tracking-normal">
            {t('adlTaskFieldInstructionsOptional')}
          </span>
        </div>
        <textarea
          value={instructions}
          onChange={(e) => setInstructions(e.target.value)}
          className="w-full border border-border px-2.5 py-1.5 text-[12px] text-text-primary resize-none h-14"
        />
      </div>

      {/* Actions */}
      <div className="flex gap-2 mt-auto">
        <button
          type="button"
          onClick={onCancel}
          className="flex-1 bg-transparent border border-border text-text-secondary text-[11px] font-semibold py-2 cursor-pointer"
        >
          Cancel
        </button>
        <button
          type="button"
          onClick={handleConfirm}
          disabled={!taskName || isLoading}
          className="flex-[2] bg-dark text-white text-[12px] font-bold border-none py-2 cursor-pointer disabled:opacity-40"
        >
          {t('adlTasksAddButton')}
        </button>
      </div>
    </div>
  )
}
