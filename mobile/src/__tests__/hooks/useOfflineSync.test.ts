import { renderHook, act } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';
import { useOfflineSync } from '@/hooks/useOfflineSync';
import { useVisitStore } from '@/store/visitStore';
import { setupMocks, teardownMocks } from '@/mocks/handlers';

const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
const wrapper = ({ children }: any) =>
  React.createElement(QueryClientProvider, { client: qc }, children);

beforeEach(() => {
  setupMocks();
  useVisitStore.setState({ conflicts: [] });
});
afterEach(() => {
  qc.clear();
  teardownMocks();
  useVisitStore.setState({ conflicts: [] });
});

describe('useOfflineSync', () => {
  it('starts with syncPending false', () => {
    const { result } = renderHook(() => useOfflineSync(), { wrapper });
    expect(result.current.syncPending).toBe(false);
  });

  it('starts with syncFailed false', () => {
    const { result } = renderHook(() => useOfflineSync(), { wrapper });
    expect(result.current.syncFailed).toBe(false);
  });

  it('starts with syncSuccess false', () => {
    const { result } = renderHook(() => useOfflineSync(), { wrapper });
    expect(result.current.syncSuccess).toBe(false);
  });

  it('starts with isOnline true', () => {
    // NetInfo mock returns connected by default in jest-expo
    const { result } = renderHook(() => useOfflineSync(), { wrapper });
    expect(result.current.isOnline).toBe(true);
  });

  it('exposes retrySync as a function', () => {
    const { result } = renderHook(() => useOfflineSync(), { wrapper });
    expect(typeof result.current.retrySync).toBe('function');
  });

  it('does not expose conflicts (moved to visitStore)', () => {
    const { result } = renderHook(() => useOfflineSync(), { wrapper });
    // conflicts is intentionally removed from the hook's return — consumers
    // must read useVisitStore(s => s.conflicts) directly.
    expect((result.current as any).conflicts).toBeUndefined();
  });
});
