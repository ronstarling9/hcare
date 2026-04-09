import React from 'react';
import { render, screen } from '@testing-library/react-native';
import { TabBar } from '@/navigation/TabBar';

// Minimal bottom tab bar props required by React Navigation
const baseProps = {
  state: {
    index: 0,
    routes: [
      { key: 'Today', name: 'Today' },
      { key: 'OpenShifts', name: 'OpenShifts' },
      { key: 'ClockIn', name: 'ClockIn' },
      { key: 'Messages', name: 'Messages' },
      { key: 'Profile', name: 'Profile' },
    ],
  },
  navigation: { emit: jest.fn(), navigate: jest.fn() },
  descriptors: {},
} as any;

describe('TabBar', () => {
  it('renders all 5 tab labels', () => {
    render(<TabBar {...baseProps} />);
    expect(screen.getByText('Today')).toBeTruthy();
    expect(screen.getByText('Open Shifts')).toBeTruthy();
    expect(screen.getByText('Messages')).toBeTruthy();
    expect(screen.getByText('Profile')).toBeTruthy();
  });
});
