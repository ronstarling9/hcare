import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQueryClient } from '@tanstack/react-query'
import {
  useActivePlan,
  useAdlTasks,
  useGoals,
  useAddAdlTask,
  useDeleteAdlTask,
  useAddGoal,
  useDeleteGoal,
} from '../../hooks/useCarePlan'
import {
  createCarePlan,
  addAdlTask as addAdlTaskApi,
  addGoal as addGoalApi,
  activateCarePlan as activateCarePlanApi,
} from '../../api/carePlans'
import { AddTaskPanel } from './AddTaskPanel'
import { AddGoalForm } from './AddGoalForm'
import type { AssistanceLevel, AdlTaskResponse, GoalResponse } from '../../types/api'

interface PendingTask {
  _id: string
  name: string
  assistanceLevel: AssistanceLevel
  frequency: string
  instructions: string
  sortOrder: number
}

interface PendingGoal {
  _id: string
  description: string
  targetDate: string | null
}

interface TaskInput {
  name: string
  assistanceLevel: AssistanceLevel
  frequency: string
  instructions: string
}

function AssistanceBadge({ level }: { level: AssistanceLevel }) {
  const { t } = useTranslation('clients')
  const config: Record<AssistanceLevel, { bg: string; text: string; labelKey: string }> = {
    MAXIMUM_ASSIST: { bg: 'bg-text-secondary', text: 'text-white', labelKey: 'adlTaskAssistanceBadgeMaxAssist' },
    DEPENDENT:      { bg: 'bg-text-secondary', text: 'text-white', labelKey: 'adlTaskAssistanceBadgeDependent' },
    MODERATE_ASSIST:{ bg: 'bg-[#ca8a04]',      text: 'text-white', labelKey: 'adlTaskAssistanceBadgeModerate' },
    MINIMAL_ASSIST: { bg: 'bg-blue',            text: 'text-white', labelKey: 'adlTaskAssistanceBadgeMinimal' },
    SUPERVISION:    { bg: 'bg-border',          text: 'text-text-primary', labelKey: 'adlTaskAssistanceBadgeSupervision' },
    INDEPENDENT:    { bg: 'bg-border',          text: 'text-text-primary', labelKey: 'adlTaskAssistanceBadgeIndependent' },
  }
  const c = config[level]
  return (
    <span className={`text-[10px] font-semibold px-1.5 py-[1px] ${c.bg} ${c.text}`}>
      {t(c.labelKey)}
    </span>
  )
}

function formatActivatedAt(iso: string | null): string {
  if (!iso) return ''
  const d = new Date(iso)
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
}

interface CarePlanTabProps {
  clientId: string
  clientFirstName: string
}

export function CarePlanTab({ clientId, clientFirstName }: CarePlanTabProps) {
  const { t } = useTranslation('clients')
  const queryClient = useQueryClient()

  const [setupMode, setSetupMode] = useState(false)
  const [isNewVersion, setIsNewVersion] = useState(false)
  const [pendingTasks, setPendingTasks] = useState<PendingTask[]>([])
  const [pendingGoals, setPendingGoals] = useState<PendingGoal[]>([])
  const [activating, setActivating] = useState(false)
  const [activateError, setActivateError] = useState(false)
  const [showLiveMessage, setShowLiveMessage] = useState(false)
  const [showAddTask, setShowAddTask] = useState(false)
  const [showAddGoal, setShowAddGoal] = useState(false)

  const { data: activePlan, isError, error } = useActivePlan(clientId)
  const is404 = isError && (error as { response?: { status?: number } })?.response?.status === 404

  const { data: tasksPage } = useAdlTasks(clientId, activePlan?.id)
  const tasks: AdlTaskResponse[] = tasksPage?.content ?? []

  const { data: goalsPage } = useGoals(clientId, activePlan?.id)
  const goals: GoalResponse[] = goalsPage?.content ?? []

  const deleteTaskMutation = useDeleteAdlTask(clientId, activePlan?.id ?? '')
  const deleteGoalMutation = useDeleteGoal(clientId, activePlan?.id ?? '')
  const addTaskMutation = useAddAdlTask(clientId, activePlan?.id ?? '')
  const addGoalMutation = useAddGoal(clientId, activePlan?.id ?? '')

  const handleSetUp = () => {
    setIsNewVersion(false)
    setPendingTasks([])
    setPendingGoals([])
    setActivateError(false)
    setSetupMode(true)
  }

  const handleNewVersion = () => {
    setIsNewVersion(true)
    setPendingTasks(
      tasks.map((tk, i) => ({
        _id: crypto.randomUUID(),
        name: tk.name,
        assistanceLevel: tk.assistanceLevel,
        frequency: tk.frequency ?? '',
        instructions: tk.instructions ?? '',
        sortOrder: i,
      })),
    )
    setPendingGoals(
      goals.map((g) => ({
        _id: crypto.randomUUID(),
        description: g.description,
        targetDate: g.targetDate,
      })),
    )
    setActivateError(false)
    setSetupMode(true)
  }

  const handleDiscard = () => {
    setSetupMode(false)
    setPendingTasks([])
    setPendingGoals([])
    setActivateError(false)
  }

  const handleSaveActivate = async () => {
    setActivating(true)
    setActivateError(false)
    try {
      const plan = await createCarePlan(clientId)
      await Promise.all(
        pendingTasks.map((tk, i) =>
          addAdlTaskApi(clientId, plan.id, {
            name: tk.name,
            assistanceLevel: tk.assistanceLevel,
            frequency: tk.frequency || undefined,
            instructions: tk.instructions || undefined,
            sortOrder: i,
          }),
        ),
      )
      await Promise.all(
        pendingGoals.map((g) =>
          addGoalApi(clientId, plan.id, {
            description: g.description,
            targetDate: g.targetDate ?? undefined,
          }),
        ),
      )
      await activateCarePlanApi(clientId, plan.id)
      queryClient.invalidateQueries({ queryKey: ['care-plan-active', clientId] })
      setSetupMode(false)
      setPendingTasks([])
      setPendingGoals([])
      setShowLiveMessage(true)
      setTimeout(() => setShowLiveMessage(false), 4000)
    } catch {
      setActivateError(true)
    } finally {
      setActivating(false)
    }
  }

  const handleAddPendingTask = (taskData: TaskInput) => {
    setPendingTasks((prev) => [
      ...prev,
      { _id: crypto.randomUUID(), ...taskData, sortOrder: prev.length },
    ])
    setShowAddTask(false)
  }

  const handleAddActiveTask = async (taskData: TaskInput) => {
    await addTaskMutation.mutateAsync({
      name: taskData.name,
      assistanceLevel: taskData.assistanceLevel,
      frequency: taskData.frequency || undefined,
      instructions: taskData.instructions || undefined,
    })
    setShowAddTask(false)
  }

  const handleAddPendingGoal = (description: string, targetDate: string | null) => {
    setPendingGoals((prev) => [
      ...prev,
      { _id: crypto.randomUUID(), description, targetDate },
    ])
    setShowAddGoal(false)
  }

  const handleAddActiveGoal = async (description: string, targetDate: string | null) => {
    await addGoalMutation.mutateAsync({
      description,
      targetDate: targetDate ?? undefined,
    })
    setShowAddGoal(false)
  }

  // ── Empty state ────────────────────────────────────────────────────────────
  if (!setupMode && (is404 || !activePlan)) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[280px] text-center p-5">
        <div className="w-10 h-10 border-2 border-dashed border-border rounded-full flex items-center justify-center mb-3">
          <span className="text-[18px] text-border">+</span>
        </div>
        <div className="text-[13px] font-semibold text-text-primary mb-1.5">
          {t('carePlanNoActivePlan')}
        </div>
        <div className="text-[11px] text-text-secondary mb-4 max-w-[200px] leading-relaxed">
          {t('carePlanEmptyHint')}
        </div>
        <button
          type="button"
          onClick={handleSetUp}
          className="bg-dark text-white text-[12px] font-bold border-none px-5 py-2 cursor-pointer"
        >
          {t('carePlanSetUpCta')}
        </button>
      </div>
    )
  }

  // ── Setup mode ─────────────────────────────────────────────────────────────
  if (setupMode) {
    const bannerTitle = isNewVersion
      ? t('carePlanNewVersionBannerTitle', {
          version: (activePlan?.planVersion ?? 0) + 1,
          firstName: clientFirstName,
        })
      : t('carePlanSetupBannerTitle', { firstName: clientFirstName })

    return (
      <div className="relative p-4 bg-surface min-h-[340px]">
        {/* Setup banner */}
        <div className="bg-dark text-white px-3 py-2 mb-3 flex items-center justify-between">
          <div>
            <div className="text-[11px] font-bold tracking-[.04em]">{bannerTitle}</div>
            <div className="text-[10px] text-text-muted mt-0.5">{t('carePlanSetupBannerHint')}</div>
          </div>
          <button
            type="button"
            onClick={handleDiscard}
            className="bg-transparent border-none text-text-muted text-[10px] cursor-pointer"
          >
            {t('carePlanDiscard')}
          </button>
        </div>

        {activateError && (
          <div className="text-[11px] text-red-600 mb-2">{t('carePlanActivateError')}</div>
        )}

        {/* ADL Tasks */}
        <div className="flex justify-between items-center mb-2">
          <span className="text-[9px] font-bold tracking-[.1em] uppercase text-text-secondary">
            {t('adlTasksSectionLabel', { count: pendingTasks.length })}
          </span>
          <button
            type="button"
            onClick={() => setShowAddTask(true)}
            className="bg-dark text-white text-[11px] font-bold border-none px-3 py-1 cursor-pointer"
          >
            {t('adlTasksAddButton')}
          </button>
        </div>

        {pendingTasks.map((tk) => (
          <div
            key={tk._id}
            className="border border-border bg-white mb-[3px] px-3.5 py-2.5 flex justify-between items-start"
          >
            <div className="flex-1">
              <div className="flex items-center gap-2">
                <span className="text-[12px] font-medium text-text-primary">{tk.name}</span>
                <AssistanceBadge level={tk.assistanceLevel} />
              </div>
              {tk.frequency && (
                <div className="text-[11px] text-text-secondary mt-0.5">{tk.frequency}</div>
              )}
            </div>
            <button
              type="button"
              onClick={() =>
                setPendingTasks((prev) => prev.filter((t) => t._id !== tk._id))
              }
              className="bg-transparent border-none text-text-secondary text-[12px] cursor-pointer px-1 ml-3"
            >
              ✕
            </button>
          </div>
        ))}

        {/* Goals */}
        <div className="flex justify-between items-center mt-3 mb-2">
          <span className="text-[9px] font-bold tracking-[.1em] uppercase text-text-secondary">
            {t('goalsSectionLabel', { count: pendingGoals.length })}
          </span>
          <button
            type="button"
            onClick={() => setShowAddGoal(true)}
            className="bg-transparent border border-border text-text-primary text-[11px] font-semibold px-3 py-1 cursor-pointer"
          >
            {t('goalsAddButton')}
          </button>
        </div>

        {pendingGoals.length === 0 && !showAddGoal && (
          <div className="text-[11px] text-text-secondary mb-4">{t('carePlanNoTasksYet')}</div>
        )}

        {pendingGoals.map((g) => (
          <div
            key={g._id}
            className="border border-border bg-white mb-[3px] px-3.5 py-2.5 flex justify-between items-start"
          >
            <div className="flex-1">
              <div className="text-[12px] text-text-primary">{g.description}</div>
              <div className="text-[11px] text-text-secondary mt-0.5">
                {g.targetDate ? `Target: ${g.targetDate}` : t('goalNoTargetDate')}
              </div>
            </div>
            <button
              type="button"
              onClick={() =>
                setPendingGoals((prev) => prev.filter((pg) => pg._id !== g._id))
              }
              className="bg-transparent border-none text-text-secondary text-[12px] cursor-pointer px-1 ml-3"
            >
              ✕
            </button>
          </div>
        ))}

        {showAddGoal && (
          <AddGoalForm
            onConfirm={handleAddPendingGoal}
            onCancel={() => setShowAddGoal(false)}
          />
        )}

        {/* Save & Activate */}
        <button
          type="button"
          onClick={handleSaveActivate}
          disabled={pendingTasks.length === 0 || activating}
          className="w-full bg-green-600 text-white text-[12px] font-bold border-none py-2.5 mt-4 cursor-pointer disabled:opacity-40"
        >
          {t('carePlanSaveActivate')}
        </button>

        {/* AddTaskPanel slide-out */}
        {showAddTask && (
          <AddTaskPanel
            onConfirm={handleAddPendingTask}
            onCancel={() => setShowAddTask(false)}
          />
        )}
      </div>
    )
  }

  // ── Active plan view ───────────────────────────────────────────────────────
  return (
    <div className="p-4 bg-surface min-h-[340px] relative">
      {/* Live message */}
      {showLiveMessage && (
        <div className="text-[11px] text-green-600 font-semibold mb-3 flex items-center gap-1.5">
          <span>✓</span> {t('carePlanActiveLive')}
        </div>
      )}

      {/* Plan header */}
      <div className="flex justify-between items-center mb-4 bg-white border border-border px-3.5 py-2.5">
        <div className="flex items-center gap-2.5">
          <div className="w-2 h-2 rounded-full bg-green-600" />
          <div>
            <span className="text-[12px] font-semibold text-text-primary">
              {t('carePlanActiveLabel')}
            </span>
            <span className="text-[11px] text-text-secondary ml-2">
              {t('carePlanVersion', { version: activePlan!.planVersion })}
            </span>
          </div>
          <span className="text-[10px] text-text-secondary">
            {t('carePlanActivatedAt', { date: formatActivatedAt(activePlan!.activatedAt) })}
          </span>
        </div>
        <button
          type="button"
          onClick={handleNewVersion}
          className="bg-transparent border border-border text-text-primary text-[11px] font-semibold px-3 py-1.5 cursor-pointer"
        >
          {t('carePlanNewVersion')}
        </button>
      </div>

      {/* ADL Tasks */}
      <div className="flex justify-between items-center mb-2">
        <span className="text-[9px] font-bold tracking-[.1em] uppercase text-text-secondary">
          {t('adlTasksSectionLabel', { count: tasks.length })}
        </span>
        <button
          type="button"
          onClick={() => setShowAddTask(true)}
          className="bg-dark text-white text-[11px] font-bold border-none px-3 py-1 cursor-pointer"
        >
          {t('adlTasksAddButton')}
        </button>
      </div>

      {tasks.map((tk) => (
        <div
          key={tk.id}
          className="border border-border bg-white mb-[3px] px-3.5 py-2.5 flex justify-between items-start"
        >
          <div className="flex-1">
            <div className="flex items-center gap-2">
              <span className="text-[12px] font-medium text-text-primary">{tk.name}</span>
              <AssistanceBadge level={tk.assistanceLevel} />
            </div>
            {tk.frequency && (
              <div className="text-[11px] text-text-secondary mt-0.5">{tk.frequency}</div>
            )}
            {tk.instructions && (
              <div className="text-[11px] text-text-secondary mt-0.5">{tk.instructions}</div>
            )}
          </div>
          <button
            type="button"
            onClick={() => deleteTaskMutation.mutate(tk.id)}
            className="bg-transparent border-none text-text-secondary text-[12px] cursor-pointer px-1 ml-3"
            title="Remove"
          >
            ✕
          </button>
        </div>
      ))}

      {/* Goals */}
      <div className="flex justify-between items-center mt-4 mb-2">
        <span className="text-[9px] font-bold tracking-[.1em] uppercase text-text-secondary">
          {t('goalsSectionLabel', { count: goals.length })}
        </span>
        <button
          type="button"
          onClick={() => setShowAddGoal(true)}
          className="bg-transparent border border-border text-text-primary text-[11px] font-semibold px-3 py-1 cursor-pointer"
        >
          {t('goalsAddButton')}
        </button>
      </div>

      {goals.map((g) => (
        <div
          key={g.id}
          className="border border-border bg-white mb-[3px] px-3.5 py-2.5 flex justify-between items-start"
        >
          <div className="flex-1">
            <div className="text-[12px] text-text-primary">{g.description}</div>
            <div className="text-[11px] text-text-secondary mt-0.5">
              {g.targetDate
                ? `Target: ${new Date(g.targetDate).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })}`
                : t('goalNoTargetDate')}
            </div>
          </div>
          <button
            type="button"
            onClick={() => deleteGoalMutation.mutate(g.id)}
            className="bg-transparent border-none text-text-secondary text-[12px] cursor-pointer px-1 ml-3"
          >
            ✕
          </button>
        </div>
      ))}

      {showAddGoal && (
        <AddGoalForm
          onConfirm={handleAddActiveGoal}
          onCancel={() => setShowAddGoal(false)}
          isLoading={addGoalMutation.isPending}
        />
      )}

      {/* AddTaskPanel slide-out */}
      {showAddTask && (
        <AddTaskPanel
          onConfirm={handleAddActiveTask}
          onCancel={() => setShowAddTask(false)}
          isLoading={addTaskMutation.isPending}
        />
      )}
    </div>
  )
}
