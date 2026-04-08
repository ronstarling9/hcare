import { apiClient } from './client'
import type {
  CarePlanResponse,
  AdlTaskResponse,
  GoalResponse,
  PageResponse,
  AddAdlTaskRequest,
  AddGoalRequest,
} from '../types/api'

export async function getActivePlan(clientId: string): Promise<CarePlanResponse> {
  const response = await apiClient.get<CarePlanResponse>(
    `/clients/${clientId}/care-plans/active`,
  )
  return response.data
}

export async function createCarePlan(clientId: string): Promise<CarePlanResponse> {
  const response = await apiClient.post<CarePlanResponse>(
    `/clients/${clientId}/care-plans`,
    {},
  )
  return response.data
}

export async function activateCarePlan(
  clientId: string,
  planId: string,
): Promise<CarePlanResponse> {
  const response = await apiClient.post<CarePlanResponse>(
    `/clients/${clientId}/care-plans/${planId}/activate`,
  )
  return response.data
}

export async function listAdlTasks(
  clientId: string,
  planId: string,
): Promise<PageResponse<AdlTaskResponse>> {
  const response = await apiClient.get<PageResponse<AdlTaskResponse>>(
    `/clients/${clientId}/care-plans/${planId}/adl-tasks`,
    { params: { size: 100 } },
  )
  return response.data
}

export async function addAdlTask(
  clientId: string,
  planId: string,
  req: AddAdlTaskRequest,
): Promise<AdlTaskResponse> {
  const response = await apiClient.post<AdlTaskResponse>(
    `/clients/${clientId}/care-plans/${planId}/adl-tasks`,
    req,
  )
  return response.data
}

export async function deleteAdlTask(
  clientId: string,
  planId: string,
  taskId: string,
): Promise<void> {
  await apiClient.delete(
    `/clients/${clientId}/care-plans/${planId}/adl-tasks/${taskId}`,
  )
}

export async function listGoals(
  clientId: string,
  planId: string,
): Promise<PageResponse<GoalResponse>> {
  const response = await apiClient.get<PageResponse<GoalResponse>>(
    `/clients/${clientId}/care-plans/${planId}/goals`,
    { params: { size: 100 } },
  )
  return response.data
}

export async function addGoal(
  clientId: string,
  planId: string,
  req: AddGoalRequest,
): Promise<GoalResponse> {
  const response = await apiClient.post<GoalResponse>(
    `/clients/${clientId}/care-plans/${planId}/goals`,
    req,
  )
  return response.data
}

export async function deleteGoal(
  clientId: string,
  planId: string,
  goalId: string,
): Promise<void> {
  await apiClient.delete(
    `/clients/${clientId}/care-plans/${planId}/goals/${goalId}`,
  )
}
