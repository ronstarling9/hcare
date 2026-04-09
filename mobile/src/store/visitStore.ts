// src/store/visitStore.ts
import { create } from 'zustand';
import type { SyncEventResult } from '@/types/api';

// IMPORTANT: visitStore is in-memory only (Zustand, no persistence).
// If the app is killed mid-visit and restarted, activeVisitId and activeShiftId
// will be null and the Visit screen will be unreachable. This is a known
// limitation at MVP. The caregiver must contact their agency to manually
// record the visit. Persisting to SecureStore is a P2 improvement.

export type GpsStatus = 'OK' | 'OUTSIDE_RANGE' | 'OFFLINE' | 'UNAVAILABLE';

interface VisitState {
  activeVisitId:    string | null;
  activeShiftId:    string | null;
  activeClientId:   string | null;    // used to key CarePlanSection collapse preference per client
  activeClientName: string | null;
  clockInTime:      string | null;    // ISO 8601
  gpsStatus:        GpsStatus | null; // snapshot from clock-in; shown in GpsStatusBar throughout visit
  activeVisitNotes: string | null;    // persists notes text across re-navigation within a visit
  // Conflict results from offline sync — stored here so ConflictDetailScreen can
  // dismiss them independently without prop-drilling or re-mounting useOfflineSync.
  conflicts:        SyncEventResult[];
  setActiveVisit: (visitId: string, shiftId: string, clientId: string, clientName: string, clockInTime: string, gpsStatus: GpsStatus) => void;
  setVisitNotes: (notes: string) => void;
  clearActiveVisit: () => void;
  setConflicts: (conflicts: SyncEventResult[]) => void;
  removeConflict: (visitId: string) => void;
}

export const useVisitStore = create<VisitState>((set) => ({
  activeVisitId:    null,
  activeShiftId:    null,
  activeClientId:   null,
  activeClientName: null,
  clockInTime:      null,
  gpsStatus:        null,
  activeVisitNotes: null,
  conflicts:        [],
  setActiveVisit: (visitId, shiftId, clientId, clientName, clockInTime, gpsStatus) =>
    set({ activeVisitId: visitId, activeShiftId: shiftId, activeClientId: clientId, activeClientName: clientName, clockInTime, gpsStatus }),
  setVisitNotes: (notes) => set({ activeVisitNotes: notes }),
  clearActiveVisit: () =>
    set({ activeVisitId: null, activeShiftId: null, activeClientId: null, activeClientName: null, clockInTime: null, gpsStatus: null, activeVisitNotes: null }),
  setConflicts: (conflicts) => set({ conflicts }),
  removeConflict: (visitId) => set(state => ({ conflicts: state.conflicts.filter(c => c.visitId !== visitId) })),
}));
