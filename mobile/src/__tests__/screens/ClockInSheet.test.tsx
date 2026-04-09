import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ClockInSheet } from '@/screens/clockIn/ClockInSheet';
import { setupMocks, teardownMocks } from '@/mocks/handlers';
import { mockTodayShifts } from '@/mocks/data';

const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
const wrapper = ({ children }: any) => <QueryClientProvider client={qc}>{children}</QueryClientProvider>;

beforeEach(() => setupMocks());
afterEach(() => { qc.clear(); teardownMocks(); });

describe('ClockInSheet', () => {
  it('shows all upcoming shifts', async () => {
    render(<ClockInSheet navigation={{ navigate: jest.fn(), goBack: jest.fn() }} />, { wrapper });
    await waitFor(() =>
      expect(screen.getByText(mockTodayShifts[0].clientName)).toBeTruthy()
    );
    expect(screen.getByText(mockTodayShifts[1].clientName)).toBeTruthy();
  });

  it('auto-selects the first shift once data loads (useEffect guard)', async () => {
    // Verifies the useEffect null-guard: selectedId starts null at mount
    // (React Query async), then is set to upcoming[0].id once data arrives.
    const nav = { navigate: jest.fn(), goBack: jest.fn() };
    render(<ClockInSheet navigation={nav} />, { wrapper });
    // Button label is "Clock In — {clientName}" — match the full label to avoid
    // colliding with the same name appearing in the shift list.
    await waitFor(() =>
      expect(screen.getByText(new RegExp(`Clock In.*${mockTodayShifts[0].clientName}`))).toBeTruthy()
    );
  });

  it('changes selection when a different shift is tapped', async () => {
    const nav = { navigate: jest.fn(), goBack: jest.fn() };
    render(<ClockInSheet navigation={nav} />, { wrapper });
    await waitFor(() => screen.getByText(mockTodayShifts[1].clientName));
    fireEvent.press(screen.getByText(mockTodayShifts[1].clientName));
    // Button label should now reference the second shift's client
    await waitFor(() =>
      expect(screen.getByText(new RegExp(`Clock In.*${mockTodayShifts[1].clientName}`))).toBeTruthy()
    );
  });

  it('shows Cancel when no shift is selected', async () => {
    const nav = { navigate: jest.fn(), goBack: jest.fn() };
    render(<ClockInSheet navigation={nav} />, { wrapper });
    // Cancel is always visible
    expect(screen.getByText('Cancel')).toBeTruthy();
  });
});
