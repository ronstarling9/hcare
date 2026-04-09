import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react-native';
import { LoginScreen } from '@/screens/auth/LoginScreen';
import { mockAuthResponse } from '@/mocks/data';
import { setupMocks, teardownMocks } from '@/mocks/handlers';

beforeEach(() => setupMocks());
afterEach(() => teardownMocks());

describe('LoginScreen', () => {
  const nav = { navigate: jest.fn() } as any;

  it('renders primary message and email input', () => {
    render(<LoginScreen navigation={nav} />);
    expect(screen.getByText(/check your email/i)).toBeTruthy();
    expect(screen.getByPlaceholderText(/your@email.com/i)).toBeTruthy();
    expect(screen.getByText(/send new sign-in link/i)).toBeTruthy();
  });

  it('shows success message after submitting email', async () => {
    render(<LoginScreen navigation={nav} />);
    fireEvent.changeText(screen.getByPlaceholderText(/your@email.com/i), 'sarah@example.com');
    fireEvent.press(screen.getByText(/send new sign-in link/i));
    await waitFor(() =>
      expect(screen.getByText(/link has been sent/i)).toBeTruthy()
    );
  });
});
