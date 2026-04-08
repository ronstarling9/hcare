import { create } from 'zustand'

interface ToastState {
  visible: boolean
  showCount: number        // increments on every show(); useEffect dep for timer re-arm
  message: string
  linkLabel: string
  targetId: string | null  // passed to openPanel as selectedId
  panelType: string        // e.g. 'client'
  panelTab: string         // passed as initialTab, e.g. 'authorizations'
  backLabel: string        // e.g. '← Clients'
  show: (opts: {
    message: string
    linkLabel: string
    targetId: string
    panelType: string
    panelTab: string
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
  panelType: '',
  panelTab: '',
  backLabel: '',

  show: (opts) =>
    set((prev) => ({
      visible: true,
      showCount: prev.showCount + 1,
      message: opts.message,
      linkLabel: opts.linkLabel,
      targetId: opts.targetId,
      panelType: opts.panelType,
      panelTab: opts.panelTab,
      backLabel: opts.backLabel,
    })),

  dismiss: () =>
    set({
      visible: false,
      showCount: 0,
      message: '',
      linkLabel: '',
      targetId: null,
      panelType: '',
      panelTab: '',
      backLabel: '',
    }),
}))
