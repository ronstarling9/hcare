# Phase 3: Auth Wiring

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire authentication end-to-end — add CORS to the backend, create an Axios client, a Zustand auth store, a login page, and route guards — so the React app can talk to the backend.

**Before starting:**
- Phase 1 (static UI) must be complete.
- Phase 2 (dashboard endpoint) must be complete.
- Backend is running on `http://localhost:8080`.
- Frontend is running on `http://localhost:5173`.

---

### Task 1: Add CORS Configuration to SecurityConfig

**Files:**
- Modify: `backend/src/main/java/com/hcare/config/SecurityConfig.java`

- [ ] **Step 1.1: Add CORS configuration**

Replace the full contents of `backend/src/main/java/com/hcare/config/SecurityConfig.java`:

```java
package com.hcare.config;

import com.hcare.security.JwtAuthenticationFilter;
import com.hcare.security.JwtTokenProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;

    public SecurityConfig(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/api/v1/agencies/register").permitAll()
                .requestMatchers("/h2-console/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider),
                UsernamePasswordAuthenticationFilter.class)
            .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:5173"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

- [ ] **Step 1.2: Verify compilation and tests still pass**

```bash
cd backend && mvn test -q 2>&1 | tail -5
```

Expected: all tests pass, 0 failures.

- [ ] **Step 1.3: Commit**

```bash
git add backend/src/main/java/com/hcare/config/SecurityConfig.java
git commit -m "feat: add CORS config to SecurityConfig — allow http://localhost:5173"
```

---

### Task 2: Create Axios API Client

**Files:**
- Create: `frontend/src/api/client.ts`

- [ ] **Step 2.1: Create the Axios client**

Create `frontend/src/api/client.ts`:

```ts
import axios from 'axios'

export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1',
  headers: {
    'Content-Type': 'application/json',
  },
})

// Attach JWT token from authStore on every request.
// Import is deferred inside the interceptor to avoid circular dependency
// (authStore imports nothing from api/, but api/client.ts is used by auth.ts
// which is called before authStore is fully initialized during login).
apiClient.interceptors.request.use((config) => {
  // Dynamic import at call time — authStore is always initialized by the time
  // any authenticated request fires.
  const raw = (window as unknown as Record<string, unknown>).__authStore__
  if (raw && typeof raw === 'object' && 'getState' in raw) {
    const state = (raw as { getState: () => { token: string | null } }).getState()
    if (state.token) {
      config.headers.Authorization = `Bearer ${state.token}`
    }
  }
  return config
})

// Redirect to /login on 401
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      window.location.href = '/login'
    }
    return Promise.reject(error)
  },
)
```

**Note:** The `__authStore__` bridge pattern is replaced in Step 3.3 below once `authStore` is created. The client above uses a temporary bridge; the cleaner approach (direct store import) is set up after the store exists.

- [ ] **Step 2.2: Commit**

```bash
cd frontend && git add src/api/client.ts
git commit -m "feat: add Axios API client with JWT interceptor and 401 redirect"
```

---

### Task 3: Create Auth Store

**Files:**
- Create: `frontend/src/store/authStore.ts`

- [ ] **Step 3.1: Create authStore**

Create `frontend/src/store/authStore.ts`:

```ts
import { create } from 'zustand'

export interface AuthState {
  token: string | null
  userId: string | null
  agencyId: string | null
  role: string | null
  login: (token: string, userId: string, agencyId: string, role: string) => void
  logout: () => void
}

export const useAuthStore = create<AuthState>((set) => ({
  token: null,
  userId: null,
  agencyId: null,
  role: null,
  login: (token, userId, agencyId, role) =>
    set({ token, userId, agencyId, role }),
  logout: () =>
    set({ token: null, userId: null, agencyId: null, role: null }),
}))
```

- [ ] **Step 3.2: Update api/client.ts to use authStore directly**

Replace the full contents of `frontend/src/api/client.ts` with the clean version that imports `useAuthStore` directly:

```ts
import axios from 'axios'
import { useAuthStore } from '../store/authStore'

export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1',
  headers: {
    'Content-Type': 'application/json',
  },
})

// Attach JWT token from authStore on every request.
apiClient.interceptors.request.use((config) => {
  const token = useAuthStore.getState().token
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// Redirect to /login on 401
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      window.location.href = '/login'
    }
    return Promise.reject(error)
  },
)
```

- [ ] **Step 3.3: Commit**

```bash
cd frontend && git add src/store/authStore.ts src/api/client.ts
git commit -m "feat: add Zustand authStore and update API client to read token from store"
```

---

### Task 4: Create Auth API Function

**Files:**
- Create: `frontend/src/api/auth.ts`

- [ ] **Step 4.1: Create auth.ts**

Create `frontend/src/api/auth.ts`:

```ts
import { apiClient } from './client'
import type { LoginResponse } from '../types/api'

export interface LoginRequest {
  email: string
  password: string
}

export async function loginApi(email: string, password: string): Promise<LoginResponse> {
  const response = await apiClient.post<LoginResponse>('/auth/login', { email, password })
  return response.data
}
```

- [ ] **Step 4.2: Commit**

```bash
cd frontend && git add src/api/auth.ts
git commit -m "feat: add loginApi function"
```

---

### Task 5: Create Login Page

**Files:**
- Create: `frontend/src/pages/LoginPage.tsx`

- [ ] **Step 5.1: Create LoginPage**

Create `frontend/src/pages/LoginPage.tsx`:

```tsx
import { useForm } from 'react-hook-form'
import { useNavigate } from 'react-router-dom'
import { loginApi } from '../api/auth'
import { useAuthStore } from '../store/authStore'
import { useState } from 'react'

interface LoginFormValues {
  email: string
  password: string
}

export function LoginPage() {
  const navigate = useNavigate()
  const login = useAuthStore((s) => s.login)
  const [serverError, setServerError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginFormValues>()

  const onSubmit = async (values: LoginFormValues) => {
    setServerError(null)
    try {
      const data = await loginApi(values.email, values.password)
      login(data.token, data.userId, data.agencyId, data.role)
      navigate('/schedule', { replace: true })
    } catch {
      setServerError('Invalid email or password. Please try again.')
    }
  }

  return (
    <div
      className="min-h-screen flex items-center justify-center"
      style={{ backgroundColor: '#1a1a24' }}
    >
      <div
        className="w-full max-w-sm rounded-xl p-8"
        style={{ backgroundColor: '#2e2e38', border: '1px solid #eaeaf2' }}
      >
        {/* Logo / App name */}
        <div className="mb-8 text-center">
          <h1 className="text-2xl font-semibold text-white tracking-tight">hcare</h1>
          <p className="mt-1 text-sm" style={{ color: '#747480' }}>
            Agency admin portal
          </p>
        </div>

        <form onSubmit={handleSubmit(onSubmit)} noValidate>
          {/* Email */}
          <div className="mb-4">
            <label
              htmlFor="email"
              className="block text-sm font-medium mb-1"
              style={{ color: '#747480' }}
            >
              Email
            </label>
            <input
              id="email"
              type="email"
              autoComplete="email"
              className="w-full rounded-lg px-3 py-2 text-sm text-white outline-none focus:ring-2"
              style={{
                backgroundColor: '#1a1a24',
                border: '1px solid #eaeaf2',
                focusRingColor: '#1a9afa',
              }}
              {...register('email', {
                required: 'Email is required',
                pattern: {
                  value: /^[^\s@]+@[^\s@]+\.[^\s@]+$/,
                  message: 'Enter a valid email address',
                },
              })}
            />
            {errors.email && (
              <p className="mt-1 text-xs" style={{ color: '#dc2626' }}>
                {errors.email.message}
              </p>
            )}
          </div>

          {/* Password */}
          <div className="mb-6">
            <label
              htmlFor="password"
              className="block text-sm font-medium mb-1"
              style={{ color: '#747480' }}
            >
              Password
            </label>
            <input
              id="password"
              type="password"
              autoComplete="current-password"
              className="w-full rounded-lg px-3 py-2 text-sm text-white outline-none focus:ring-2"
              style={{
                backgroundColor: '#1a1a24',
                border: '1px solid #eaeaf2',
              }}
              {...register('password', { required: 'Password is required' })}
            />
            {errors.password && (
              <p className="mt-1 text-xs" style={{ color: '#dc2626' }}>
                {errors.password.message}
              </p>
            )}
          </div>

          {/* Server error */}
          {serverError && (
            <div
              className="mb-4 rounded-lg px-3 py-2 text-sm"
              style={{ backgroundColor: '#dc262620', color: '#dc2626', border: '1px solid #dc2626' }}
            >
              {serverError}
            </div>
          )}

          {/* Submit */}
          <button
            type="submit"
            disabled={isSubmitting}
            className="w-full rounded-lg py-2 text-sm font-semibold text-white transition-opacity disabled:opacity-50"
            style={{ backgroundColor: '#1a9afa' }}
          >
            {isSubmitting ? 'Signing in…' : 'Sign in'}
          </button>
        </form>
      </div>
    </div>
  )
}
```

- [ ] **Step 5.2: Commit**

```bash
cd frontend && git add src/pages/LoginPage.tsx
git commit -m "feat: add LoginPage with react-hook-form and authStore integration"
```

---

### Task 6: Update App.tsx with Route Guards

**Files:**
- Modify: `frontend/src/App.tsx`

- [ ] **Step 6.1: Update App.tsx**

Replace the full contents of `frontend/src/App.tsx`:

```tsx
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { Shell } from './components/layout/Shell'
import { SchedulePage } from './components/schedule/SchedulePage'
import { DashboardPage } from './components/dashboard/DashboardPage'
import { ClientsPage } from './components/clients/ClientsPage'
import { CaregiversPage } from './components/caregivers/CaregiversPage'
import { PayersPage } from './components/payers/PayersPage'
import { EvvStatusPage } from './components/evv/EvvStatusPage'
import { LoginPage } from './pages/LoginPage'
import { useAuthStore } from './store/authStore'
import type { ReactNode } from 'react'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      staleTime: 30_000,
    },
  },
})

function RequireAuth({ children }: { children: ReactNode }) {
  const token = useAuthStore((s) => s.token)
  if (!token) {
    return <Navigate to="/login" replace />
  }
  return <>{children}</>
}

export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route
            path="/"
            element={
              <RequireAuth>
                <Shell />
              </RequireAuth>
            }
          >
            <Route index element={<Navigate to="/schedule" replace />} />
            <Route path="schedule" element={<SchedulePage />} />
            <Route path="dashboard" element={<DashboardPage />} />
            <Route path="clients" element={<ClientsPage />} />
            <Route path="caregivers" element={<CaregiversPage />} />
            <Route path="payers" element={<PayersPage />} />
            <Route path="evv" element={<EvvStatusPage />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  )
}
```

- [ ] **Step 6.2: Verify the frontend builds**

```bash
cd frontend && npm run build 2>&1 | tail -10
```

Expected: `built in X.Xms` with no TypeScript errors.

- [ ] **Step 6.3: Commit**

```bash
cd frontend && git add src/App.tsx
git commit -m "feat: add RequireAuth route guard and /login route to App"
```

---

## ✋ MANUAL TEST CHECKPOINT 3

**Start both servers:**

```bash
# Terminal 1 — backend
cd backend && mvn spring-boot:run

# Terminal 2 — frontend
cd frontend && npm run dev
```

**Test the auth flow:**

1. Visit `http://localhost:5173` — should redirect immediately to `http://localhost:5173/login`.
2. Submit the form with a wrong password — should show "Invalid email or password" error in red.
3. Submit the form with valid dev credentials (e.g. `admin@test.com` / `password123`) — should navigate to `/schedule`.
4. Verify the schedule page renders (even if empty — mock data or empty API response).
5. Open DevTools → Network tab, reload `/schedule`. Confirm requests to `http://localhost:8080/api/v1/...` include `Authorization: Bearer <token>` header.
6. Open DevTools → Application → no JWT stored in localStorage (it lives only in Zustand memory).
7. Reload the page — should redirect to `/login` (token is in memory only, lost on reload). This is expected behavior.

Proceed to Phase 4 only after this checkpoint passes.
