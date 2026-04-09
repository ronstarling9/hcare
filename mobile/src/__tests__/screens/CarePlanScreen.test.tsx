import React from 'react';
import { render, screen, waitFor } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { CarePlanScreen } from '@/screens/carePlan/CarePlanScreen';
import { setupMocks, teardownMocks } from '@/mocks/handlers';
import { MOCK_SHIFT_ID_1, mockCarePlan } from '@/mocks/data';

const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
const wrapper = ({ children }: any) => <QueryClientProvider client={qc}>{children}</QueryClientProvider>;

beforeEach(() => setupMocks());
afterEach(() => { qc.clear(); teardownMocks(); });

describe('CarePlanScreen', () => {
  const nav = { goBack: jest.fn() };
  const route = { params: { shiftId: MOCK_SHIFT_ID_1 } };

  it('renders the Care Plan header', () => {
    render(<CarePlanScreen navigation={nav} route={route} />, { wrapper });
    expect(screen.getByText('Care Plan')).toBeTruthy();
  });

  it('loads and displays ADL tasks from mock', async () => {
    render(<CarePlanScreen navigation={nav} route={route} />, { wrapper });
    await waitFor(() =>
      expect(screen.getByText(mockCarePlan.adlTasks[0].name)).toBeTruthy()
    );
  });
});
