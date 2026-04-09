import React from 'react';
import { render, screen, waitFor } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ProfileScreen } from '@/screens/profile/ProfileScreen';
import { setupMocks, teardownMocks } from '@/mocks/handlers';
import { mockProfile, mockProfileStats } from '@/mocks/data';

const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
const wrapper = ({ children }: any) => <QueryClientProvider client={qc}>{children}</QueryClientProvider>;

beforeEach(() => setupMocks());
afterEach(() => { qc.clear(); teardownMocks(); });

describe('ProfileScreen', () => {
  const nav = { navigate: jest.fn() };

  it('shows caregiver name and agency', async () => {
    render(<ProfileScreen navigation={nav} />, { wrapper });
    await waitFor(() => expect(screen.getByText(mockProfile.name)).toBeTruthy());
    expect(screen.getByText(new RegExp(mockProfile.agencyName))).toBeTruthy();
  });

  it('shows credential list', async () => {
    render(<ProfileScreen navigation={nav} />, { wrapper });
    await waitFor(() =>
      expect(screen.getByText(mockProfile.credentials[0].name)).toBeTruthy()
    );
  });

  it('shows monthly stats', async () => {
    render(<ProfileScreen navigation={nav} />, { wrapper });
    await waitFor(() =>
      expect(screen.getByText(String(mockProfileStats.shiftsCompleted))).toBeTruthy()
    );
  });
});
