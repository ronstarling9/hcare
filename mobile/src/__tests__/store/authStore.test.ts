import { act, renderHook } from '@testing-library/react-native';
import { useAuthStore } from '@/store/authStore';

beforeEach(() => {
  useAuthStore.setState({
    accessToken: null,
    refreshToken: null,
    caregiverId: null,
    agencyId: null,
    name: null,
    agencyName: null,
    firstLogin: false,
    isAuthenticated: false,
  });
});

describe('authStore', () => {
  it('starts with no auth state', () => {
    const { result } = renderHook(() => useAuthStore());
    expect(result.current.accessToken).toBeNull();
    expect(result.current.isAuthenticated).toBe(false);
  });

  it('setAuth populates all fields', () => {
    const { result } = renderHook(() => useAuthStore());
    act(() => {
      result.current.setAuth({
        accessToken: 'tok',
        refreshToken: 'ref',
        caregiverId: 'cg-1',
        agencyId: 'ag-1',
        name: 'Sarah',
        agencyName: 'Sunrise',
        firstLogin: false,
      });
    });
    expect(result.current.accessToken).toBe('tok');
    expect(result.current.isAuthenticated).toBe(true);
  });

  it('clearAuth removes all auth fields', () => {
    const { result } = renderHook(() => useAuthStore());
    act(() => {
      result.current.setAuth({ accessToken: 'tok', refreshToken: 'ref', caregiverId: 'cg-1', agencyId: 'ag-1', name: 'Sarah', agencyName: 'Sunrise', firstLogin: false });
    });
    act(() => {
      result.current.clearAuth();
    });
    expect(result.current.isAuthenticated).toBe(false);
    expect(result.current.accessToken).toBeNull();
  });
});
