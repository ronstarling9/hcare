import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { SettingsScreen } from '@/screens/settings/SettingsScreen';
import { useAuthStore } from '@/store/authStore';

const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
const wrapper = ({ children }: any) => <QueryClientProvider client={qc}>{children}</QueryClientProvider>;

beforeEach(() => {
  useAuthStore.setState({ accessToken: 'tok', isAuthenticated: true, name: 'Sarah', agencyName: 'Sunrise', firstLogin: false });
});
afterEach(() => qc.clear());

describe('SettingsScreen', () => {
  const nav = { goBack: jest.fn(), navigate: jest.fn() };

  it('renders the Permissions section header', () => {
    render(<SettingsScreen navigation={nav} />, { wrapper });
    expect(screen.getByText(/permissions/i)).toBeTruthy();
  });

  it('shows "Change →" links for Notifications and Location permission rows', () => {
    render(<SettingsScreen navigation={nav} />, { wrapper });
    // SettingsScreen renders "Change →" (not "Change in Settings") for each permission row.
    // Sign Out lives in ProfileScreen, not here.
    const changeLinks = screen.getAllByText('Change \u2192');
    expect(changeLinks.length).toBeGreaterThanOrEqual(2);
  });
});
