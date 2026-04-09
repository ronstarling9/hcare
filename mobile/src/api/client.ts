// src/api/client.ts
import axios from 'axios';
import * as SecureStore from 'expo-secure-store';

export const BFF_URL = process.env.EXPO_PUBLIC_BFF_URL ?? 'http://localhost:8081';

export const apiClient = axios.create({ baseURL: BFF_URL });

// Request interceptor: attach Bearer token from SecureStore
apiClient.interceptors.request.use(async (config) => {
  const token = await SecureStore.getItemAsync('accessToken');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Response interceptor: silent 401 refresh
// Import is deferred to avoid circular dependency with authStore
apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;
    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;
      try {
        const refreshToken = await SecureStore.getItemAsync('refreshToken');
        if (!refreshToken) throw new Error('No refresh token');
        const res = await axios.post(`${BFF_URL}/mobile/auth/refresh`, { refreshToken });
        const { accessToken } = res.data as { accessToken: string };
        await SecureStore.setItemAsync('accessToken', accessToken);
        originalRequest.headers.Authorization = `Bearer ${accessToken}`;
        return apiClient(originalRequest);
      } catch {
        // Refresh failed — caller receives the 401
        return Promise.reject(error);
      }
    }
    return Promise.reject(error);
  }
);

// Enable mocks when EXPO_PUBLIC_USE_MOCKS=true
if (process.env.EXPO_PUBLIC_USE_MOCKS === 'true') {
  // Dynamically imported to keep mock code tree-shaken in prod.
  // apiClient is passed as a parameter to avoid a circular import:
  // handlers.ts no longer imports apiClient — it receives it here.
  import('../mocks/handlers').then(({ setupMocks }) => setupMocks(apiClient));
}
