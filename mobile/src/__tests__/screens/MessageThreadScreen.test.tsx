import React from 'react';
import { render, screen, waitFor } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MessageThreadScreen } from '@/screens/messages/MessageThreadScreen';
import { setupMocks, teardownMocks } from '@/mocks/handlers';
import { MOCK_THREAD_ID_1, mockMessages, mockThreads } from '@/mocks/data';

const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
const wrapper = ({ children }: any) => <QueryClientProvider client={qc}>{children}</QueryClientProvider>;

beforeEach(() => setupMocks());
afterEach(() => { qc.clear(); teardownMocks(); });

describe('MessageThreadScreen', () => {
  const nav = { goBack: jest.fn() };
  // subject is NOT passed as a route param — the screen reads it from the API response.
  const route = { params: { threadId: MOCK_THREAD_ID_1 } };

  it('loads and displays the thread subject from the API', async () => {
    render(<MessageThreadScreen navigation={nav} route={route} />, { wrapper });
    // Subject comes from the mock API response (data?.thread.subject), not route params.
    // mockThreads[0].subject === 'Schedule update for next week'
    await waitFor(() =>
      expect(screen.getByText(mockThreads[0].subject)).toBeTruthy()
    );
  });

  it('loads and displays thread messages', async () => {
    render(<MessageThreadScreen navigation={nav} route={route} />, { wrapper });
    await waitFor(() =>
      expect(screen.getByText(mockMessages[0].body)).toBeTruthy()
    );
  });
});
