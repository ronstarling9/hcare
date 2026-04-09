import React from 'react';
import { render, screen } from '@testing-library/react-native';
import { NavigationContainer } from '@react-navigation/native';
import { ConflictDetailScreen } from '@/screens/conflict/ConflictDetailScreen';

// ConflictDetailScreen calls useFocusEffect which requires a NavigationContainer.
const wrapper = ({ children }: any) => <NavigationContainer>{children}</NavigationContainer>;

describe('ConflictDetailScreen', () => {
  const conflict = {
    clientName: 'Eleanor Vance',
    shiftDate: '2026-04-08T00:00:00.000Z',
    caregiverClockIn: '2026-04-08T07:00:00.000Z',
    caregiverClockOut: '2026-04-08T10:00:00.000Z',
  };
  const nav = { navigate: jest.fn() };
  const route = { params: { conflict } };

  it('shows the client name', () => {
    render(<ConflictDetailScreen navigation={nav} route={route} />, { wrapper });
    expect(screen.getByText(/Eleanor Vance/)).toBeTruthy();
  });

  it('shows agency contact guidance', () => {
    render(<ConflictDetailScreen navigation={nav} route={route} />, { wrapper });
    expect(screen.getByText(/contact your agency/i)).toBeTruthy();
  });
});
