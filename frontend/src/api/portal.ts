import axios from 'axios'
import { usePortalAuthStore } from '../store/portalAuthStore'

const BASE = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1'

// Separate Axios instance for portal — does NOT use the admin authStore interceptor.
const portalClient = axios.create({
  baseURL: BASE,
  headers: { 'Content-Type': 'application/json' },
})

portalClient.interceptors.request.use((config) => {
  const token = usePortalAuthStore.getState().token
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// Redirect to session_expired when the FAMILY_PORTAL JWT is rejected.
// usePortalAuthStore.getState() is safe to call outside React (Zustand pattern).
portalClient.interceptors.response.use(
  (r) => r,
  (error) => {
    if (error.response?.status === 401) {
      usePortalAuthStore.getState().logout()
      window.location.href = '/portal/verify?reason=session_expired'
    }
    return Promise.reject(error)
  },
)

// ── Types ──────────────────────────────────────────────────────────────────

export interface InviteResponse {
  inviteUrl: string
  expiresAt: string
}

export interface PortalVerifyResponse {
  jwt: string
  clientId: string
  agencyId: string
}

export interface CaregiverDto {
  name: string
  serviceType: string
}

export interface TodayVisitDto {
  shiftId: string
  scheduledStart: string
  scheduledEnd: string
  status: 'GREY' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'
  clockedInAt: string | null
  clockedOutAt: string | null
  caregiver: CaregiverDto | null
}

export interface UpcomingVisitDto {
  scheduledStart: string
  scheduledEnd: string
  caregiverName: string | null
}

export interface LastVisitDto {
  date: string
  clockedOutAt: string | null
  durationMinutes: number
  noteText: string | null
}

export interface PortalDashboardResponse {
  clientFirstName: string
  agencyTimezone: string
  todayVisit: TodayVisitDto | null
  upcomingVisits: UpcomingVisitDto[]
  lastVisit: LastVisitDto | null
}

// ── Admin-side calls (use admin apiClient from client.ts) ──────────────────

import { apiClient } from './client'

export async function inviteFamilyPortalUser(
  clientId: string,
  email: string,
): Promise<InviteResponse> {
  const res = await apiClient.post<InviteResponse>(
    `/clients/${clientId}/family-portal-users/invite`,
    { email },
  )
  return res.data
}

export async function removeFamilyPortalUser(
  clientId: string,
  fpuId: string,
): Promise<void> {
  await apiClient.delete(`/clients/${clientId}/family-portal-users/${fpuId}`)
}

// ── Portal-side calls (use portalClient with FAMILY_PORTAL JWT) ────────────

export async function verifyPortalToken(token: string): Promise<PortalVerifyResponse> {
  const res = await portalClient.post<PortalVerifyResponse>('/family/auth/verify', { token })
  return res.data
}

export async function getPortalDashboard(): Promise<PortalDashboardResponse> {
  const res = await portalClient.get<PortalDashboardResponse>('/family/portal/dashboard')
  return res.data
}
