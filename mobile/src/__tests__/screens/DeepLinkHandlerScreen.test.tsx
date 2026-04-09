import React from 'react';
import { render, screen, waitFor } from '@testing-library/react-native';
import { DeepLinkHandlerScreen } from '@/screens/auth/DeepLinkHandlerScreen';
import { setupMocks, teardownMocks } from '@/mocks/handlers';

beforeEach(() => setupMocks());
afterEach(() => teardownMocks());

describe('DeepLinkHandlerScreen', () => {
  it('navigates to LinkExpired immediately when no token param', async () => {
    const nav = { replace: jest.fn() };
    render(<DeepLinkHandlerScreen navigation={nav} route={{ params: {} }} />);
    await waitFor(() => expect(nav.replace).toHaveBeenCalledWith('LinkExpired'));
  });

  it('shows loading indicator while exchanging a token', () => {
    const nav = { replace: jest.fn() };
    // Token present — exchange is async; loading state must be visible synchronously
    render(<DeepLinkHandlerScreen navigation={nav} route={{ params: { token: 'test-token' } }} />);
    expect(screen.getByText(/signing you in/i)).toBeTruthy();
  });
});
