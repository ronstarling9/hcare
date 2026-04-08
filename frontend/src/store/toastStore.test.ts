import { describe, it, expect, beforeEach } from 'vitest'
import { useToastStore } from './toastStore'

const INITIAL_STATE = {
  visible: false,
  showCount: 0,
  message: '',
  linkLabel: '',
  targetId: null,
  panelType: '',
  panelTab: '',
  backLabel: '',
}

const SHOW_OPTS = {
  message: 'Client saved.',
  linkLabel: 'Add Authorization',
  targetId: 'client-123',
  panelType: 'client',
  panelTab: 'authorizations',
  backLabel: '← Clients',
}

describe('toastStore', () => {
  beforeEach(() => {
    useToastStore.setState(INITIAL_STATE)
  })

  it('show() sets visible to true and populates all fields including showCount', () => {
    useToastStore.getState().show(SHOW_OPTS)
    const s = useToastStore.getState()
    expect(s.visible).toBe(true)
    expect(s.showCount).toBe(1)
    expect(s.message).toBe('Client saved.')
    expect(s.linkLabel).toBe('Add Authorization')
    expect(s.targetId).toBe('client-123')
    expect(s.panelType).toBe('client')
    expect(s.panelTab).toBe('authorizations')
    expect(s.backLabel).toBe('← Clients')
  })

  it('calling show() twice increments showCount each time', () => {
    useToastStore.getState().show(SHOW_OPTS)
    useToastStore.getState().show(SHOW_OPTS)
    expect(useToastStore.getState().showCount).toBe(2)
  })

  it('dismiss() resets all state to initial values', () => {
    useToastStore.getState().show(SHOW_OPTS)
    useToastStore.getState().dismiss()
    const s = useToastStore.getState()
    expect(s.visible).toBe(false)
    expect(s.showCount).toBe(0)
    expect(s.message).toBe('')
    expect(s.targetId).toBeNull()
    expect(s.panelType).toBe('')
    expect(s.panelTab).toBe('')
    expect(s.backLabel).toBe('')
  })
})
