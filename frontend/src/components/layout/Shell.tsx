import { Outlet } from 'react-router-dom'
import { Sidebar } from './Sidebar'
import { useDashboard } from '../../hooks/useDashboard'
import { SlidePanel } from '../panel/SlidePanel'
import { usePanelStore } from '../../store/panelStore'
import { ShiftDetailPanel } from '../schedule/ShiftDetailPanel'
import { NewShiftPanel } from '../schedule/NewShiftPanel'
import { NewClientPanel } from '../clients/NewClientPanel'
import { ClientDetailPanel } from '../clients/ClientDetailPanel'
import { CaregiverDetailPanel } from '../caregivers/CaregiverDetailPanel'
import { Toast } from '../common/Toast'

function PanelContent() {
  const { type, selectedId, prefill, backLabel, initialTab } = usePanelStore()

  if (type === 'shift' && selectedId) {
    return <ShiftDetailPanel shiftId={selectedId} backLabel={backLabel} />
  }
  if (type === 'newShift') {
    return <NewShiftPanel prefill={prefill} backLabel={backLabel} />
  }
  if (type === 'newClient') {
    return <NewClientPanel backLabel={backLabel} />
  }
  if (type === 'client' && selectedId) {
    return <ClientDetailPanel clientId={selectedId} backLabel={backLabel} initialTab={initialTab} />
  }
  if (type === 'caregiver' && selectedId) {
    return <CaregiverDetailPanel caregiverId={selectedId} backLabel={backLabel} />
  }
  if (type === 'payer') {
    return (
      <div className="p-6 text-text-secondary">
        Payer detail — coming in Phase 8
      </div>
    )
  }
  return null
}

export function Shell() {
  const { open, closePanel } = usePanelStore()
  const { data } = useDashboard()
  const redEvvCount = data?.redEvvCount ?? 0

  return (
    <>
      <div className="flex h-screen overflow-hidden">
        <Sidebar redEvvCount={redEvvCount} />
        {/* Main area: relative so SlidePanel's absolute positioning is scoped here */}
        <div className="relative flex-1 overflow-auto bg-surface">
          <Outlet />
          <SlidePanel isOpen={open} onClose={closePanel}>
            <PanelContent />
          </SlidePanel>
        </div>
      </div>
      <Toast />
    </>
  )
}
