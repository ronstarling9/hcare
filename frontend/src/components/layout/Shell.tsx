import { Outlet } from 'react-router-dom'
import { Sidebar } from './Sidebar'
import { SlidePanel } from '../panel/SlidePanel'
import { usePanelStore } from '../../store/panelStore'
import { ShiftDetailPanel } from '../schedule/ShiftDetailPanel'
import { NewShiftPanel } from '../schedule/NewShiftPanel'
import { ClientDetailPanel } from '../clients/ClientDetailPanel'
import { CaregiverDetailPanel } from '../caregivers/CaregiverDetailPanel'

function PanelContent() {
  const { type, selectedId, prefill, backLabel } = usePanelStore()

  if (type === 'shift' && selectedId) {
    return <ShiftDetailPanel shiftId={selectedId} backLabel={backLabel} />
  }
  if (type === 'newShift') {
    return <NewShiftPanel prefill={prefill} backLabel={backLabel} />
  }
  if (type === 'client' && selectedId) {
    return <ClientDetailPanel clientId={selectedId} backLabel={backLabel} />
  }
  if (type === 'caregiver' && selectedId) {
    return <CaregiverDetailPanel caregiverId={selectedId} backLabel={backLabel} />
  }
  return null
}

export function Shell() {
  const { open, closePanel } = usePanelStore()

  return (
    <div className="flex h-screen overflow-hidden">
      <Sidebar />
      {/* Main area: relative so SlidePanel's absolute positioning is scoped here */}
      <div className="relative flex-1 overflow-auto bg-surface">
        <Outlet />
        <SlidePanel isOpen={open} onClose={closePanel}>
          <PanelContent />
        </SlidePanel>
      </div>
    </div>
  )
}
