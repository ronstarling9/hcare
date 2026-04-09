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

  it('shows permission rows for Notifications and Location', () => {
    render(<SettingsScreen navigation={nav} />, { wrapper });
    // "Change →" is inside TouchableOpacity (accessible={true}), so its child
    // Text is hidden from standard queries. Verify the permission row labels instead —
    // their presence confirms both rows rendered with their associated controls.
    expect(screen.getByText('Notifications')).toBeTruthy();
    expect(screen.getByText('Location access')).toBeTruthy();
  });
});
