import { create } from 'zustand'

export type PanelType =
  | 'shift'
  | 'newShift'
  | 'client'
  | 'caregiver'
  | 'payer'
  | null

interface PanelPrefill {
  date?: string        // ISO date string — pre-fills date field in NewShiftPanel
  time?: string        // HH:mm — pre-fills start time when clicking an empty slot
  // edit mode — all present when opening NewShiftPanel to edit an existing shift
  editShiftId?: string
  clientId?: string
  caregiverId?: string
  serviceTypeId?: string
  endTime?: string     // HH:mm
}

interface PanelState {
  open: boolean
  type: PanelType
  selectedId: string | null
  prefill: PanelPrefill | null
  backLabel: string   // e.g. "← Schedule", "← Clients"
  openPanel: (
    type: Exclude<PanelType, null>,
    id?: string,
    options?: { prefill?: PanelPrefill; backLabel?: string }
  ) => void
  closePanel: () => void
}

export const usePanelStore = create<PanelState>((set) => ({
  open: false,
  type: null,
  selectedId: null,
  prefill: null,
  backLabel: '← Back',

  openPanel: (type, id, options) =>
    set({
      open: true,
      type,
      selectedId: id ?? null,
      prefill: options?.prefill ?? null,
      backLabel: options?.backLabel ?? '← Back',
    }),

  closePanel: () =>
    set({ open: false, type: null, selectedId: null, prefill: null }),
}))
