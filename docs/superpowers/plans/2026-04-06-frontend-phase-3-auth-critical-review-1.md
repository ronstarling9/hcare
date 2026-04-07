# Critical Implementation Review 1 — Phase 3: Auth Wiring

**Reviewed:** `2026-04-06-frontend-phase-3-auth.md`
**Previous reviews:** None (first review)
**Date:** 2026-04-07

---

## 1. Overall Assessment

The plan covers the right scope — CORS, Axios client, authStore, login page, route guards. The overall flow is sound. However, there are three bugs that will break the build or cause a runtime crash: duplicate React Router/QueryClient providers, a broken default-vs-named export mismatch, and a duplicate type definition. There is also a task ordering problem that causes a bad intermediate commit. These must be fixed before executing.

---

## 2. Critical Issues

### C1 — App.tsx wraps everything in providers already provided by main.tsx (runtime crash)

**Description:** The plan's `App.tsx` (Step 6.1) wraps its tree with `<QueryClientProvider client={queryClient}>` and `<BrowserRouter>`. But the current `main.tsx` already wraps `<App />` with both of these. Executing the plan as written creates nested `<BrowserRouter>` instances, which React Router explicitly forbids — it throws `"You cannot render a <Router> inside another <Router>"` at runtime.

Additionally, the plan moves `queryClient` construction into `App.tsx` while `main.tsx` retains its own `queryClient`, creating two separate cache instances. React Query state will not be shared correctly.

**Fix:** The plan must add a `main.tsx` update step that removes the `<BrowserRouter>` and `<QueryClientProvider>` wrappers (since App.tsx now owns them), or — cleaner — keep providers in `main.tsx` and do NOT add them to App.tsx. The plan's App.tsx should only contain `<Routes>` and the route tree, as the current App.tsx does.

---

### C2 — Named export breaks the existing default import in main.tsx (build failure)

**Description:** Step 6.1 changes the export from `export default function App()` to `export function App()` (named export). But `main.tsx` imports it as:

```ts
import App from './App'
```

This is a default import. With a named export only, TypeScript will report a type error and the build will fail (`Module '"./App"' has no default export`).

**Fix:** Either keep the `export default` on App, or add a step to update `main.tsx` to use the named import: `import { App } from './App'`. The simplest fix is to keep the default export: `export default function App()`.

---

### C3 — `LoginRequest` is redefined in `api/auth.ts` — duplicate of `types/api.ts` (type drift risk)

**Description:** `types/api.ts` already exports:

```ts
export interface LoginRequest {
  email: string
  password: string
}
```

Step 4.1's `api/auth.ts` defines a second `interface LoginRequest` with identical shape. Two sources of truth for the same type will drift over time. TypeScript won't catch it because they happen to be structurally identical today.

**Fix:** Remove the `LoginRequest` definition from `api/auth.ts` and import it from `types/api.ts`:

```ts
import { apiClient } from './client'
import type { LoginRequest, LoginResponse } from '../types/api'

export async function loginApi(email: string, password: string): Promise<LoginResponse> {
  const response = await apiClient.post<LoginResponse>('/auth/login', { email, password } satisfies LoginRequest)
  return response.data
}
```

---

### C4 — Task ordering causes a committed broken intermediate state

**Description:** Step 2.2 commits `api/client.ts` with the `__authStore__` window-global bridge, explicitly noting it is a "temporary bridge." Step 3.3 then commits a replacement. This means the repo history contains a committed file that references `window.__authStore__` — a pattern the team would rightfully flag in code review and that adds noise to `git log`.

The bridge exists only because `authStore` hasn't been created yet. But `authStore` is the very next task (Task 3). Since client.ts is not used by anything in Task 2 (no hooks or pages import it yet), there is no reason to commit the intermediate version at all.

**Fix:** Reorder tasks: create `authStore` (Task 3) before `client.ts` (Task 2). Then Task 2 can write the clean version directly (importing `useAuthStore`) with a single commit, and the bridge step is eliminated entirely. Remove Steps 2.1–2.2 in their current form and move `client.ts` creation to after Step 3.1.

---

## 3. Previously Addressed Items

None — this is the first review.

---

## 4. Minor Issues & Improvements

### M1 — `focusRingColor` is not a valid CSS property (silently ignored)

In Step 5.1, the email input has:

```tsx
style={{
  backgroundColor: '#1a1a24',
  border: '1px solid #eaeaf2',
  focusRingColor: '#1a9afa',   // ← not a CSS property
}}
```

`focusRingColor` is a Tailwind utility concept, not a CSS property. Browsers will silently ignore it. The blue focus ring will not appear. Use a Tailwind class instead: `focus:ring-2 focus:ring-[#1a9afa] focus:outline-none`.

### M2 — LoginPage hardcodes English strings (inconsistent with the rest of the app)

Every other page component uses `useTranslation`. LoginPage hardcodes "Email", "Password", "Sign in", "Signing in…", "Agency admin portal", and the error message. This will require a separate i18n pass later. At minimum, add a `dashboard` or `auth` namespace and use `t()` for all visible strings, consistent with the established pattern.

### M3 — No post-login redirect to the originally requested URL

`RequireAuth` redirects to `/login` without preserving the intended destination. After login, `navigate('/schedule', { replace: true })` always sends the user to `/schedule`, even if they were navigating to `/clients`. Standard practice: pass the original path via React Router location state and redirect there after successful login.

```tsx
// In RequireAuth:
const location = useLocation()
return <Navigate to="/login" state={{ from: location }} replace />

// In LoginPage:
const location = useLocation()
const from = (location.state as { from?: Location })?.from?.pathname ?? '/schedule'
navigate(from, { replace: true })
```

### M4 — CORS verification step is missing from Task 1

Step 1.2 runs `mvn test -q` to verify tests still pass, but no step verifies that CORS headers are actually emitted correctly. A one-line curl check would catch misconfiguration before wiring the frontend:

```bash
curl -si -X OPTIONS http://localhost:8080/api/v1/auth/login \
  -H "Origin: http://localhost:5173" \
  -H "Access-Control-Request-Method: POST" \
  | grep -i "access-control"
```

Expected: `Access-Control-Allow-Origin: http://localhost:5173` in the response.

---

## 5. Questions for Clarification

- **Q1:** Should the login page use the existing `dashboard` i18n namespace or a new `auth` namespace? Consistency with other pages suggests a new `auth` namespace.
- **Q2:** The manual test checkpoint expects dev credentials `admin@test.com` / `password123`. Is this user seeded by `DevDataSeeder.java` (the new untracked file in git status)? The checkpoint should document which seeder creates this user.

---

## 6. Final Recommendation

**Major revisions needed before executing.**

C1 (duplicate providers / nested Router), C2 (export mismatch), and C4 (bad task ordering + committed bridge) will each break the build or cause a runtime crash. C3 (type duplication) is a correctness concern. All four must be fixed in the plan before any steps are executed.
