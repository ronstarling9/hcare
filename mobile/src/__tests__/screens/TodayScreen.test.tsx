import React from 'react';
import { render, screen, waitFor } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { TodayScreen } from '@/screens/today/TodayScreen';
import { setupMocks, teardownMocks } from '@/mocks/handlers';
import { mockTodayShifts } from '@/mocks/data';

const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
const wrapper = ({ children }: any) => <QueryClientProvider client={qc}>{children}</QueryClientProvider>;

beforeEach(() => setupMocks());
afterEach(() => { qc.clear(); teardownMocks(); });

describe('TodayScreen', () => {
  it('renders the first upcoming shift client name', async () => {
    render(<TodayScreen navigation={{ navigate: jest.fn() }} />, { wrapper });
    await waitFor(() =>
      expect(screen.getByText(mockTodayShifts[0].clientName)).toBeTruthy()
    );
  });

  it('shows NEXT badge on the soonest shift', async () => {
    render(<TodayScreen navigation={{ navigate: jest.fn() }} />, { wrapper });
    await waitFor(() => expect(screen.getByText('NEXT')).toBeTruthy());
  });
});
