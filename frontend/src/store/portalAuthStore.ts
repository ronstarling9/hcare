import { create } from 'zustand'
import { persist } from 'zustand/middleware'

interface PortalAuthState {
  token: string | null
  clientId: string | null
  agencyId: string | null
  login: (token: string, clientId: string, agencyId: string) => void
  logout: () => void
}

export const usePortalAuthStore = create<PortalAuthState>()(
  persist(
    (set) => ({
      token: null,
      clientId: null,
      agencyId: null,
      login: (token, clientId, agencyId) => set({ token, clientId, agencyId }),
      logout: () => set({ token: null, clientId: null, agencyId: null }),
    }),
    { name: 'portal-auth' },
  ),
)
