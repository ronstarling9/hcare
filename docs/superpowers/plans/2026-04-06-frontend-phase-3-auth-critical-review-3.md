# Critical Implementation Review 3 — Phase 3: Auth Wiring

**Reviewed:** `2026-04-06-frontend-phase-3-auth.md`
**Previous reviews:** reviews 1 and 2
**Date:** 2026-04-07

---

## 1. Overall Assessment

The plan has addressed every issue from reviews 1 and 2 and is now structurally sound. One new critical issue was introduced by the test steps added in the review-2 round: `LoginPage.test.tsx` makes assertions against translated strings (e.g. `/sign in/i`, `/email is required/i`) but never initialises or mocks `react-i18next`, so `t()` returns raw keys at test time and every assertion that matches on a human-readable string will fail. This must be fixed before the plan is executed.

---

## 2. Critical Issues

### C1 — `LoginPage.test.tsx` has no i18n mock — all string assertions will fail

**Description:** `LoginPage` uses `useTranslation('auth')` and renders translated strings everywhere: label text, button text, validation messages, and the server error. The test assertions match against the human-readable values:

```ts
screen.getByLabelText(/email/i)              // expects label "Email"
screen.getByRole('button', { name: /sign in/i }) // expects "Sign in"
screen.findByText(/email is required/i)      // expects "Email is required"
screen.getByText(/password is required/i)    // expects "Password is required"
screen.findByText(/valid email/i)            // expects "Enter a valid email address"
screen.findByText(/invalid email or password/i) // expects full error string
```

But the test never calls `i18n.init`, never imports the auth locale file, and never mocks `react-i18next`. In a Vitest (jsdom) environment with no backend available to serve `/locales/en/auth.json`, `i18next-http-backend` will either fail silently or return keys. In either case `t('signIn')` resolves to `'signIn'`, not `'Sign in'`, and `t('emailRequired')` resolves to `'emailRequired'`, not `'Email is required'`. As a result:

- `getByRole('button', { name: /sign in/i })` → no element found — `'signIn'` does not contain the space `/sign in/i` needs. **Test throws.**
- `findByText(/email is required/i)` → `'emailRequired'` does not match. **Test throws.**
- All other human-string assertions follow the same pattern and fail.

Only `getByLabelText(/email/i)` and `getByLabelText(/password/i)` would accidentally pass because the key strings `'emailLabel'` and `'passwordLabel'` happen to contain the substrings `'email'` and `'password'`.

**Why it matters:** The test step says "Expected: 5 tests pass." but at least 4 of the 5 tests will fail immediately. Executing the plan as-written produces a broken test suite that the plan instructs the executor to commit.

**Fix:** Add a `vi.mock('react-i18next')` at the top of `LoginPage.test.tsx` that returns the actual display strings keyed by translation key:

```ts
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) =>
      ({
        appName: 'hcare',
        tagline: 'Agency admin portal',
        emailLabel: 'Email',
        passwordLabel: 'Password',
        signIn: 'Sign in',
        signingIn: 'Signing in…',
        emailRequired: 'Email is required',
        emailInvalid: 'Enter a valid email address',
        passwordRequired: 'Password is required',
        invalidCredentials: 'Invalid email or password. Please try again.',
      }[key] ?? key),
  }),
}))
```

Place this mock before the `vi.mock('../api/auth')` call in the test file. No changes to the assertions are needed.

---

## 3. Previously Addressed Items

All issues from reviews 1 and 2 are resolved:

- **R1-C1** (nested `<BrowserRouter>` / `<QueryClientProvider>`) — App.tsx contains only `<Routes>`; main.tsx owns providers.
- **R1-C2** (named export breaking main.tsx default import) — `export default function App()` retained.
- **R1-C3** (`LoginRequest` type redefined in `api/auth.ts`) — imported from `types/api.ts`.
- **R1-C4** (committed bridge pattern using `window.__authStore__`) — authStore created in Task 2 before client.ts in Task 3; no bridge present.
- **R1-M1** (`focusRingColor` not a valid CSS property) — inputs use Tailwind `focus:ring-2 focus:ring-[#1a9afa]` classes.
- **R1-M2** (hardcoded English strings) — `useTranslation('auth')` + `t()` throughout; `auth.json` locale file created in Task 5.
- **R1-M3** (no post-login redirect to originally requested URL) — `location.state.from.pathname` used; `RequireAuth` passes `state={{ from: location }}`.
- **R1-M4** (no CORS verification step) — Step 1.3 adds the curl preflight check.
- **R2-C1** (`axios` not installed) — Step 3.0 installs axios and commits `package.json` / `package-lock.json`.
- **R2-M1** (`authStore.role: string | null`) — typed as `UserRole | null`; `login` accepts `UserRole`; `UserRole` imported from `types/api`.
- **R2-M2** (no unit test steps) — Steps 2.3–2.5 add `authStore.test.ts`; Steps 6.3–6.5 add `LoginPage.test.tsx`.

---

## 4. Minor Issues & Improvements

### M1 — `userEvent` legacy API in LoginPage tests (v14 recommendation)

The test uses `await userEvent.click(...)` and `await userEvent.type(...)` directly. In `@testing-library/user-event` v14 (which is in `package.json`), the recommended API is:

```ts
const user = userEvent.setup()
await user.click(...)
await user.type(...)
```

The legacy direct-call API still works in v14 but may produce subtly different event sequences for complex interactions. For this plan's tests the difference is unlikely to matter. Worth noting for future test authors.

---

## 5. Questions for Clarification

None remaining.

---

## 6. Final Recommendation

**Approve with changes.**

Fix C1 (add the `react-i18next` mock to `LoginPage.test.tsx`) and the plan is ready to execute.
