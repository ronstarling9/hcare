import { describe, it, expect, beforeEach } from 'vitest'
import { useToastStore } from './toastStore'

const INITIAL_STATE = {
  visible: false,
  showCount: 0,
  message: '',
  linkLabel: '',
  targetId: null,
  panelType: 'client' as const,   // zero value — matches TOAST_ZERO_PANEL_TYPE
  initialTab: '',
  backLabel: '',
}

const SHOW_OPTS = {
  message: 'Caregiver saved. Add credentials to enable scheduling.',
  linkLabel: 'Add Credentials',
  targetId: 'caregiver-123',
  panelType: 'caregiver' as const,
  initialTab: 'credentials',
  backLabel: '← Caregivers',
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
    expect(s.message).toBe('Caregiver saved. Add credentials to enable scheduling.')
    expect(s.linkLabel).toBe('Add Credentials')
    expect(s.targetId).toBe('caregiver-123')
    expect(s.panelType).toBe('caregiver')
    expect(s.initialTab).toBe('credentials')
    expect(s.backLabel).toBe('← Caregivers')
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
    expect(s.panelType).toBe('client')   // reset to TOAST_ZERO_PANEL_TYPE
    expect(s.initialTab).toBe('')
    expect(s.backLabel).toBe('')
  })
})
