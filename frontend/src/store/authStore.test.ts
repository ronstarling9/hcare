import { describe, it, expect, beforeEach } from 'vitest'
import { useAuthStore } from './authStore'

describe('authStore', () => {
  beforeEach(() => {
    useAuthStore.setState({ token: null, userId: null, agencyId: null, role: null })
  })

  it('initial state is all null', () => {
    const state = useAuthStore.getState()
    expect(state.token).toBeNull()
    expect(state.userId).toBeNull()
    expect(state.agencyId).toBeNull()
    expect(state.role).toBeNull()
  })

  it('login sets all fields', () => {
    useAuthStore.getState().login('tok', 'u1', 'a1', 'ADMIN')
    const state = useAuthStore.getState()
    expect(state.token).toBe('tok')
    expect(state.userId).toBe('u1')
    expect(state.agencyId).toBe('a1')
    expect(state.role).toBe('ADMIN')
  })

  it('logout clears all fields', () => {
    useAuthStore.getState().login('tok', 'u1', 'a1', 'SCHEDULER')
    useAuthStore.getState().logout()
    const state = useAuthStore.getState()
    expect(state.token).toBeNull()
    expect(state.role).toBeNull()
  })
})
