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

- [ ] **Step 1.3: Verify CORS headers are emitted (backend must be running)**

```bash
curl -si -X OPTIONS http://localhost:8080/api/v1/auth/login \
  -H "Origin: http://localhost:5173" \
  -H "Access-Control-Request-Method: POST" \
  | grep -i "access-control"
```

Expected: `Access-Control-Allow-Origin: http://localhost:5173` in the response.

- [ ] **Step 1.4: Commit**

```bash
git add backend/src/main/java/com/hcare/config/SecurityConfig.java
git commit -m "feat: add CORS config to SecurityConfig — allow http://localhost:5173"
```

---

### Task 2: Create Auth Store

**Files:**
- Create: `frontend/src/store/authStore.ts`

> **Note:** authStore is created before api/client.ts so the client can import it directly — no intermediate bridge needed.

- [ ] **Step 2.1: Create authStore**

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

- [ ] **Step 2.2: Commit**

```bash
cd frontend && git add src/store/authStore.ts
git commit -m "feat: add Zustand authStore"
```

---

### Task 3: Create Axios API Client

**Files:**
- Create: `frontend/src/api/client.ts`

- [ ] **Step 3.1: Create the Axios client**

Create `frontend/src/api/client.ts`:

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

- [ ] **Step 3.2: Commit**

```bash
cd frontend && git add src/api/client.ts
git commit -m "feat: add Axios API client with JWT interceptor and 401 redirect"
```

---

### Task 4: Create Auth API Function

**Files:**
- Create: `frontend/src/api/auth.ts`

- [ ] **Step 4.1: Create auth.ts**

Create `frontend/src/api/auth.ts`:

```ts
import { apiClient } from './client'
import type { LoginRequest, LoginResponse } from '../types/api'

export async function loginApi(email: string, password: string): Promise<LoginResponse> {
  const body: LoginRequest = { email, password }
  const response = await apiClient.post<LoginResponse>('/auth/login', body)
  return response.data
}
```

> `LoginRequest` and `LoginResponse` are imported from `types/api.ts` — do not redefine them here.

- [ ] **Step 4.2: Commit**

```bash
cd frontend && git add src/api/auth.ts
git commit -m "feat: add loginApi function"
```

---

### Task 5: Add Auth i18n Namespace

**Files:**
- Modify: `frontend/src/i18n.ts`
- Create: `frontend/public/locales/en/auth.json`

- [ ] **Step 5.1: Add `auth` to the i18n namespace list**

In `frontend/src/i18n.ts`, add `'auth'` to the `ns` array:

```ts
ns: [
  'common',
  'nav',
  'schedule',
  'shiftDetail',
  'newShift',
  'dashboard',
  'clients',
  'caregivers',
  'payers',
  'evvStatus',
  'auth',
],
```

- [ ] **Step 5.2: Create the auth locale file**

Create `frontend/public/locales/en/auth.json`:

```json
{
  "appName": "hcare",
  "tagline": "Agency admin portal",
  "emailLabel": "Email",
  "passwordLabel": "Password",
  "signIn": "Sign in",
  "signingIn": "Signing in…",
  "emailRequired": "Email is required",
  "emailInvalid": "Enter a valid email address",
  "passwordRequired": "Password is required",
  "invalidCredentials": "Invalid email or password. Please try again."
}
```

- [ ] **Step 5.3: Commit**

```bash
cd frontend && git add src/i18n.ts public/locales/en/auth.json
git commit -m "feat: add auth i18n namespace"
```

---

### Task 6: Create Login Page

**Files:**
- Create: `frontend/src/pages/LoginPage.tsx`

- [ ] **Step 6.1: Create LoginPage**

Create `frontend/src/pages/LoginPage.tsx`:

```tsx
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useNavigate, useLocation } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { loginApi } from '../api/auth'
import { useAuthStore } from '../store/authStore'

interface LoginFormValues {
  email: string
  password: string
}

export function LoginPage() {
  const { t } = useTranslation('auth')
  const navigate = useNavigate()
  const location = useLocation()
  const login = useAuthStore((s) => s.login)
  const [serverError, setServerError] = useState<string | null>(null)

  // After login, redirect to where the user was trying to go, or /schedule
  const from = (location.state as { from?: { pathname: string } } | null)?.from?.pathname ?? '/schedule'

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
      navigate(from, { replace: true })
    } catch {
      setServerError(t('invalidCredentials'))
    }
  }

  return (
    <div
      className="min-h-screen flex items-center justify-center"
      style={{ backgroundColor: '#1a1a24' }}
    >
      <div
        className="w-full max-w-sm p-8"
        style={{ backgroundColor: '#2e2e38', border: '1px solid #3a3a4a' }}
      >
        {/* Logo / App name */}
        <div className="mb-8 text-center">
          <h1 className="text-2xl font-bold tracking-tight text-white">{t('appName')}</h1>
          <p className="mt-1 text-sm" style={{ color: '#747480' }}>
            {t('tagline')}
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
              {t('emailLabel')}
            </label>
            <input
              id="email"
              type="email"
              autoComplete="email"
              className="w-full px-3 py-2 text-sm text-white outline-none focus:ring-2 focus:ring-[#1a9afa]"
              style={{
                backgroundColor: '#1a1a24',
                border: '1px solid #3a3a4a',
              }}
              {...register('email', {
                required: t('emailRequired'),
                pattern: {
                  value: /^[^\s@]+@[^\s@]+\.[^\s@]+$/,
                  message: t('emailInvalid'),
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
              {t('passwordLabel')}
            </label>
            <input
              id="password"
              type="password"
              autoComplete="current-password"
              className="w-full px-3 py-2 text-sm text-white outline-none focus:ring-2 focus:ring-[#1a9afa]"
              style={{
                backgroundColor: '#1a1a24',
                border: '1px solid #3a3a4a',
              }}
              {...register('password', { required: t('passwordRequired') })}
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
              className="mb-4 px-3 py-2 text-sm"
              style={{ backgroundColor: '#dc262620', color: '#dc2626', border: '1px solid #dc2626' }}
            >
              {serverError}
            </div>
          )}

          {/* Submit — no border-radius per design spec */}
          <button
            type="submit"
            disabled={isSubmitting}
            className="w-full py-2 text-sm font-bold text-white transition-opacity disabled:opacity-50"
            style={{ backgroundColor: '#1a9afa' }}
          >
            {isSubmitting ? t('signingIn') : t('signIn')}
          </button>
        </form>
      </div>
    </div>
  )
}
```

- [ ] **Step 6.2: Commit**

```bash
cd frontend && git add src/pages/LoginPage.tsx
git commit -m "feat: add LoginPage with react-hook-form, i18n, and post-login redirect"
```

---

### Task 7: Update App.tsx with Route Guards

**Files:**
- Modify: `frontend/src/App.tsx`

> **Important:** `main.tsx` already owns `<BrowserRouter>` and `<QueryClientProvider>`. App.tsx must not add them — only add the `/login` route and `RequireAuth` wrapper inside the existing `<Routes>` structure.

- [ ] **Step 7.1: Update App.tsx**

Replace the full contents of `frontend/src/App.tsx`:

```tsx
import { Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import type { ReactNode } from 'react'
import { Shell } from './components/layout/Shell'
import { SchedulePage } from './components/schedule/SchedulePage'
import { DashboardPage } from './components/dashboard/DashboardPage'
import { ClientsPage } from './components/clients/ClientsPage'
import { CaregiversPage } from './components/caregivers/CaregiversPage'
import { PayersPage } from './components/payers/PayersPage'
import { EvvStatusPage } from './components/evv/EvvStatusPage'
import { LoginPage } from './pages/LoginPage'
import { useAuthStore } from './store/authStore'

function RequireAuth({ children }: { children: ReactNode }) {
  const token = useAuthStore((s) => s.token)
  const location = useLocation()
  if (!token) {
    // Preserve the intended destination so LoginPage can redirect back after login
    return <Navigate to="/login" state={{ from: location }} replace />
  }
  return <>{children}</>
}

function SettingsPlaceholder() {
  const { t } = useTranslation('nav')
  return <div className="p-8 text-text-secondary">{t('settingsComingSoon')}</div>
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        element={
          <RequireAuth>
            <Shell />
          </RequireAuth>
        }
      >
        <Route index element={<Navigate to="/schedule" replace />} />
        <Route path="/schedule" element={<SchedulePage />} />
        <Route path="/dashboard" element={<DashboardPage />} />
        <Route path="/clients" element={<ClientsPage />} />
        <Route path="/caregivers" element={<CaregiversPage />} />
        <Route path="/payers" element={<PayersPage />} />
        <Route path="/evv" element={<EvvStatusPage />} />
        <Route path="/settings" element={<SettingsPlaceholder />} />
      </Route>
    </Routes>
  )
}
```

- [ ] **Step 7.2: Verify the frontend builds**

```bash
cd frontend && npm run build 2>&1 | tail -10
```

Expected: `built in X.Xms` with no TypeScript errors.

- [ ] **Step 7.3: Commit**

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

**Dev credentials:** The `DevDataSeeder` seeds `admin@test.com` / `password123` when the `dev` profile is active (`SPRING_PROFILES_ACTIVE=dev`).

**Test the auth flow:**

1. Visit `http://localhost:5173` — should redirect immediately to `http://localhost:5173/login`.
2. Submit the form with a wrong password — should show "Invalid email or password" error in red.
3. Navigate directly to `http://localhost:5173/clients` while logged out — should redirect to `/login`.
4. Submit the form with valid dev credentials (`admin@test.com` / `password123`) — should navigate to `/clients` (the original destination, not `/schedule`).
5. Verify the schedule page renders (mock data or empty API response is fine at this stage).
6. Open DevTools → Network tab, reload `/schedule`. Confirm requests to `http://localhost:8080/api/v1/...` include `Authorization: Bearer <token>` header.
7. Open DevTools → Application → Storage: confirm no JWT is stored in localStorage (lives only in Zustand memory).
8. Reload the page — should redirect to `/login` (token is in memory only, lost on reload). This is expected behavior.

Proceed to Phase 4 only after this checkpoint passes.
