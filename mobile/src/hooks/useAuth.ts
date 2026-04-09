// src/hooks/useAuth.ts
import { authApi } from '@/api/auth';
import { useAuthStore } from '@/store/authStore';

// Thrown by exchangeToken so callers can distinguish an invalid/expired token
// (navigate to LinkExpired) from a network failure (show retry — the token
// may still be valid once connectivity is restored).
export class AuthError extends Error {
  constructor(public readonly code: 'INVALID_TOKEN' | 'NETWORK_ERROR', message: string) {
    super(message);
    this.name = 'AuthError';
  }
}

export function useAuth() {
  // Use individual selectors rather than subscribing to the whole store.
  // Spreading useAuthStore() re-renders on ANY store field change and
  // leaks raw store actions (setAuth, clearAuth, rehydrate) to consumers.
  const isAuthenticated = useAuthStore(s => s.isAuthenticated);
  const name            = useAuthStore(s => s.name);
  const agencyName      = useAuthStore(s => s.agencyName);
  const firstLogin      = useAuthStore(s => s.firstLogin);
  const setAuth         = useAuthStore(s => s.setAuth);
  const clearAuth       = useAuthStore(s => s.clearAuth);
  const rehydrate       = useAuthStore(s => s.rehydrate);

  async function exchangeToken(token: string) {
    try {
      const data = await authApi.exchange(token);
      await setAuth(data);
      return data;
    } catch (err: any) {
      if (err?.response) {
        // BFF returned a non-2xx status — token is invalid or expired.
        throw new AuthError('INVALID_TOKEN', 'Token is invalid or expired');
      }
      // No response object — network is unreachable.
      throw new AuthError('NETWORK_ERROR', 'Network unreachable');
    }
  }

  async function sendLink(email: string) {
    return authApi.sendLink(email);
  }

  async function logout() {
    await clearAuth();
  }

  return { exchangeToken, sendLink, logout, isAuthenticated, name, agencyName, firstLogin, rehydrate };
}
