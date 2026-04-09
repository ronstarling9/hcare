import React from 'react';
import { render, screen, waitFor } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MessagesInboxScreen } from '@/screens/messages/MessagesInboxScreen';
import { setupMocks, teardownMocks } from '@/mocks/handlers';
import { mockThreads } from '@/mocks/data';

const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
const wrapper = ({ children }: any) => <QueryClientProvider client={qc}>{children}</QueryClientProvider>;

beforeEach(() => setupMocks());
afterEach(() => { qc.clear(); teardownMocks(); });

describe('MessagesInboxScreen', () => {
  it('shows thread subjects', async () => {
    render(<MessagesInboxScreen navigation={{ navigate: jest.fn() }} />, { wrapper });
    await waitFor(() =>
      expect(screen.getByText(mockThreads[0].subject)).toBeTruthy()
    );
  });
});
