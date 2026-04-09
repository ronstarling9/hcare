import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { VisitScreen } from '@/screens/visit/VisitScreen';
import { useVisitStore } from '@/store/visitStore';
import { setupMocks, teardownMocks } from '@/mocks/handlers';
import { MOCK_SHIFT_ID_1, MOCK_VISIT_ID, mockCarePlan } from '@/mocks/data';

const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
const wrapper = ({ children }: any) => <QueryClientProvider client={qc}>{children}</QueryClientProvider>;

beforeEach(() => {
  setupMocks();
  useVisitStore.setState({
    activeVisitId:    MOCK_VISIT_ID,
    activeShiftId:    MOCK_SHIFT_ID_1,
    activeClientId:   'cl-001',          // matches mockTodayShifts[0].clientId; keys CarePlanSection per-client collapse pref
    activeClientName: 'Eleanor Vance',
    clockInTime:      new Date().toISOString(),
    gpsStatus:        'OK',              // required for GpsStatusBar OK/OFFLINE path tests
    activeVisitNotes: null,
  });
});
afterEach(() => { qc.clear(); teardownMocks(); useVisitStore.setState({ activeVisitId: null, activeShiftId: null, activeClientId: null, activeClientName: null, clockInTime: null, gpsStatus: null, activeVisitNotes: null }); });

describe('VisitScreen', () => {
  const nav = { navigate: jest.fn(), goBack: jest.fn() };
  const route = { params: { visitId: MOCK_VISIT_ID } };

  beforeEach(() => {
    nav.navigate.mockClear();
    nav.goBack.mockClear();
  });

  it('shows client name in hero', () => {
    render(<VisitScreen navigation={nav} route={route} />, { wrapper });
    expect(screen.getByText('Eleanor Vance')).toBeTruthy();
  });

  it('shows "Loading…" on Clock Out button while care plan is fetching', () => {
    // On initial render React Query hasn't resolved yet — button is in loading state.
    render(<VisitScreen navigation={nav} route={route} />, { wrapper });
    expect(screen.getByText(/loading/i)).toBeTruthy();
  });

  it('loads and displays ADL tasks after care plan resolves', async () => {
    render(<VisitScreen navigation={nav} route={route} />, { wrapper });
    await waitFor(() =>
      expect(screen.getByText(mockCarePlan.adlTasks[0].name)).toBeTruthy()
    );
  });

  it('shows "Clock Out" button text after care plan loads', async () => {
    render(<VisitScreen navigation={nav} route={route} />, { wrapper });
    await waitFor(() => expect(screen.getByText('Clock Out')).toBeTruthy());
  });

  it('shows incomplete-task confirmation modal when >50% of tasks are undone', async () => {
    render(<VisitScreen navigation={nav} route={route} />, { wrapper });
    // Wait for tasks to load (mock care plan has tasks all incomplete by default)
    await waitFor(() => expect(screen.getByText('Clock Out')).toBeTruthy());
    fireEvent.press(screen.getByText('Clock Out'));
    // Modal should appear warning about incomplete tasks
    await waitFor(() =>
      expect(screen.getByText(/incomplete/i)).toBeTruthy()
    );
  });

  it('shows "Wrong shift?" overflow trigger', () => {
    render(<VisitScreen navigation={nav} route={route} />, { wrapper });
    expect(screen.getByText(/wrong shift/i)).toBeTruthy();
  });
});
