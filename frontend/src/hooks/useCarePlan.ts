import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import {
  getActivePlan,
  listAdlTasks,
  listGoals,
  addAdlTask,
  deleteAdlTask,
  addGoal,
  deleteGoal,
} from '../api/carePlans'
import type { AddAdlTaskRequest, AddGoalRequest } from '../types/api'

export function useActivePlan(clientId: string) {
  return useQuery({
    queryKey: ['care-plan-active', clientId],
    queryFn: () => getActivePlan(clientId),
    enabled: Boolean(clientId),
    retry: (failureCount, error: unknown) => {
      const status = (error as { response?: { status?: number } })?.response?.status
      if (status === 404) return false
      return failureCount < 3
    },
  })
}

export function useAdlTasks(clientId: string, planId: string | undefined) {
  return useQuery({
    queryKey: ['adl-tasks', clientId, planId],
    queryFn: () => listAdlTasks(clientId, planId!),
    enabled: Boolean(clientId) && Boolean(planId),
  })
}

export function useGoals(clientId: string, planId: string | undefined) {
  return useQuery({
    queryKey: ['goals', clientId, planId],
    queryFn: () => listGoals(clientId, planId!),
    enabled: Boolean(clientId) && Boolean(planId),
  })
}

export function useAdlTaskTemplates() {
  const { i18n } = useTranslation()
  const lang = i18n.language.split('-')[0]
  return useQuery({
    queryKey: ['adl-task-templates', lang],
    queryFn: async () => {
      try {
        const res = await fetch(`/locales/${lang}/adl-task-templates.json`)
        if (!res.ok) throw new Error('not found')
        return res.json()
      } catch {
        const fallback = await fetch('/locales/en/adl-task-templates.json')
        return fallback.json()
      }
    },
    staleTime: Infinity,
  })
}

export function useAddAdlTask(clientId: string, planId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (req: AddAdlTaskRequest) => addAdlTask(clientId, planId, req),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['adl-tasks', clientId, planId] })
    },
  })
}

export function useDeleteAdlTask(clientId: string, planId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (taskId: string) => deleteAdlTask(clientId, planId, taskId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['adl-tasks', clientId, planId] })
    },
  })
}

export function useAddGoal(clientId: string, planId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (req: AddGoalRequest) => addGoal(clientId, planId, req),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['goals', clientId, planId] })
    },
  })
}

export function useDeleteGoal(clientId: string, planId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (goalId: string) => deleteGoal(clientId, planId, goalId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['goals', clientId, planId] })
    },
  })
}
