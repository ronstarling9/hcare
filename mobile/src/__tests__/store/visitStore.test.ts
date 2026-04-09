import { useVisitStore } from '@/store/visitStore';

beforeEach(() => {
  useVisitStore.setState({
    activeVisitId: null, activeShiftId: null, activeClientId: null,
    activeClientName: null, clockInTime: null, gpsStatus: null,
    activeVisitNotes: null, conflicts: [],
  });
});

describe('visitStore', () => {
  it('starts with no active visit', () => {
    expect(useVisitStore.getState().activeVisitId).toBeNull();
  });

  it('starts with no conflicts', () => {
    expect(useVisitStore.getState().conflicts).toEqual([]);
  });

  it('setActiveVisit populates all fields', () => {
    useVisitStore.getState().setActiveVisit('v-1', 'sh-1', 'cl-1', 'Eleanor Vance', '2026-04-08T07:00:00Z', 'OK');
    const state = useVisitStore.getState();
    expect(state.activeVisitId).toBe('v-1');
    expect(state.activeShiftId).toBe('sh-1');
    expect(state.activeClientId).toBe('cl-1');
    expect(state.activeClientName).toBe('Eleanor Vance');
    expect(state.gpsStatus).toBe('OK');
  });

  it('setVisitNotes updates only notes', () => {
    useVisitStore.getState().setActiveVisit('v-1', 'sh-1', 'cl-1', 'Eleanor', '2026-04-08T07:00:00Z', 'OK');
    useVisitStore.getState().setVisitNotes('Some notes.');
    expect(useVisitStore.getState().activeVisitNotes).toBe('Some notes.');
    expect(useVisitStore.getState().activeVisitId).toBe('v-1'); // unchanged
  });

  it('clearActiveVisit resets all fields', () => {
    useVisitStore.getState().setActiveVisit('v-1', 'sh-1', 'cl-1', 'Eleanor', '2026-04-08T07:00:00Z', 'OK');
    useVisitStore.getState().clearActiveVisit();
    expect(useVisitStore.getState().activeVisitId).toBeNull();
    expect(useVisitStore.getState().gpsStatus).toBeNull();
    expect(useVisitStore.getState().activeVisitNotes).toBeNull();
  });

  it('setConflicts stores conflict results', () => {
    const c1 = { visitId: 'v-1', result: 'CONFLICT_REASSIGNED' as const };
    const c2 = { visitId: 'v-2', result: 'CONFLICT_REASSIGNED' as const };
    useVisitStore.getState().setConflicts([c1, c2]);
    expect(useVisitStore.getState().conflicts).toHaveLength(2);
    expect(useVisitStore.getState().conflicts[0].visitId).toBe('v-1');
  });

  it('removeConflict removes only the matching visitId', () => {
    useVisitStore.getState().setConflicts([
      { visitId: 'v-1', result: 'CONFLICT_REASSIGNED' as const },
      { visitId: 'v-2', result: 'CONFLICT_REASSIGNED' as const },
    ]);
    useVisitStore.getState().removeConflict('v-1');
    const conflicts = useVisitStore.getState().conflicts;
    expect(conflicts).toHaveLength(1);
    expect(conflicts[0].visitId).toBe('v-2');
  });

  it('removeConflict is a no-op when visitId not found', () => {
    useVisitStore.getState().setConflicts([{ visitId: 'v-1', result: 'CONFLICT_REASSIGNED' as const }]);
    useVisitStore.getState().removeConflict('v-999');
    expect(useVisitStore.getState().conflicts).toHaveLength(1);
  });
});
