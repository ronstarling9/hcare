// src/store/authStore.ts
import { create } from 'zustand';
import * as SecureStore from 'expo-secure-store';

interface AuthState {
  accessToken:  string | null;
  refreshToken: string | null;
  caregiverId:  string | null;
  agencyId:     string | null;
  name:         string | null;
  agencyName:   string | null;
  firstLogin:   boolean;
  isAuthenticated: boolean;

  setAuth: (payload: {
    accessToken: string;
    refreshToken: string;
    caregiverId: string;
    agencyId: string;
    name: string;
    agencyName: string;
    firstLogin: boolean;
  }) => Promise<void>;

  clearAuth: () => Promise<void>;

  /** Rehydrate tokens from SecureStore on app cold start */
  rehydrate: () => Promise<void>;
}

export const useAuthStore = create<AuthState>((set) => ({
  accessToken:     null,
  refreshToken:    null,
  caregiverId:     null,
  agencyId:        null,
  name:            null,
  agencyName:      null,
  firstLogin:      false,
  isAuthenticated: false,

  setAuth: async ({ accessToken, refreshToken, caregiverId, agencyId, name, agencyName, firstLogin }) => {
    await SecureStore.setItemAsync('accessToken', accessToken);
    await SecureStore.setItemAsync('refreshToken', refreshToken);
    // Persist non-sensitive profile fields so they survive a cold restart.
    // name/agencyName are not PHI — storing them eliminates the blank-greeting flash on rehydration.
    await SecureStore.setItemAsync('caregiverProfile', JSON.stringify({ caregiverId, agencyId, name, agencyName, firstLogin }));
    set({ accessToken, refreshToken, caregiverId, agencyId, name, agencyName, firstLogin, isAuthenticated: true });
  },

  clearAuth: async () => {
    await SecureStore.deleteItemAsync('accessToken');
    await SecureStore.deleteItemAsync('refreshToken');
    await SecureStore.deleteItemAsync('caregiverProfile');
    set({ accessToken: null, refreshToken: null, caregiverId: null, agencyId: null, name: null, agencyName: null, firstLogin: false, isAuthenticated: false });
  },

  rehydrate: async () => {
    const accessToken   = await SecureStore.getItemAsync('accessToken');
    const refreshToken  = await SecureStore.getItemAsync('refreshToken');
    const profileJson   = await SecureStore.getItemAsync('caregiverProfile');
    if (accessToken && refreshToken) {
      const profile = profileJson ? JSON.parse(profileJson) : {};
      set({ accessToken, refreshToken, isAuthenticated: true, ...profile });
    }
  },
}));
