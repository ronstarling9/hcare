import { create } from 'zustand'
import type { UserRole } from '../types/api'

export interface AuthState {
  token: string | null
  userId: string | null
  agencyId: string | null
  role: UserRole | null
  login: (token: string, userId: string, agencyId: string, role: UserRole) => void
  logout: () => void
}

export const useAuthStore = create<AuthState>((set) => ({
  token: null,
  userId: null,
  agencyId: null,
  role: null,
  login: (token, userId, agencyId, role) =>
    set({ token, userId, agencyId, role }),
  logout: () =>
    set({ token: null, userId: null, agencyId: null, role: null }),
}))
