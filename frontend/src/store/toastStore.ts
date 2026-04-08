import { create } from 'zustand'
import type { PanelType } from './panelStore'

// 'client' is a valid PanelType used as the zero value for panelType.
// The `visible: false` guard in Toast.tsx ensures it is never read or acted on.
const TOAST_ZERO_PANEL_TYPE: Exclude<PanelType, null> = 'client'

interface ToastState {
  visible: boolean
  showCount: number        // increments on every show(); useEffect dep for timer re-arm
  message: string
  linkLabel: string
  targetId: string | null  // passed to openPanel as the id argument
  panelType: Exclude<PanelType, null>  // typed union — catches invalid panel types at call sites
  initialTab: string       // passed as initialTab to openPanel options, e.g. 'credentials'
  backLabel: string        // e.g. '← Caregivers'
  show: (opts: {
    message: string
    linkLabel: string
    targetId: string
    panelType: Exclude<PanelType, null>
    initialTab: string
    backLabel: string
  }) => void
  dismiss: () => void
}

export const useToastStore = create<ToastState>((set) => ({
  visible: false,
  showCount: 0,
  message: '',
  linkLabel: '',
  targetId: null,
  panelType: TOAST_ZERO_PANEL_TYPE,
  initialTab: '',
  backLabel: '',

  show: (opts) =>
    set((prev) => ({
      visible: true,
      showCount: prev.showCount + 1,
      message: opts.message,
      linkLabel: opts.linkLabel,
      targetId: opts.targetId,
      panelType: opts.panelType,
      initialTab: opts.initialTab,
      backLabel: opts.backLabel,
    })),

  dismiss: () =>
    set({
      visible: false,
      showCount: 0,
      message: '',
      linkLabel: '',
      targetId: null,
      panelType: TOAST_ZERO_PANEL_TYPE,
      initialTab: '',
      backLabel: '',
    }),
}))
