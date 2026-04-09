import React from 'react';
import { render, screen, waitFor } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { OpenShiftsScreen } from '@/screens/openShifts/OpenShiftsScreen';
import { setupMocks, teardownMocks } from '@/mocks/handlers';
import { mockOpenShifts } from '@/mocks/data';

const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
const wrapper = ({ children }: any) => <QueryClientProvider client={qc}>{children}</QueryClientProvider>;

beforeEach(() => setupMocks());
afterEach(() => { qc.clear(); teardownMocks(); });

describe('OpenShiftsScreen', () => {
  it('displays open shift client names', async () => {
    render(<OpenShiftsScreen />, { wrapper });
    await waitFor(() =>
      expect(screen.getByText(mockOpenShifts[0].clientName)).toBeTruthy()
    );
  });

  it('shows URGENT badge for urgent shifts', async () => {
    render(<OpenShiftsScreen />, { wrapper });
    await waitFor(() => expect(screen.getByText('URGENT')).toBeTruthy());
  });
});
