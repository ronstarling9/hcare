# Family Portal — Design Spec

**Date:** 2026-04-08
**Status:** v3 — revised after second UX review and critical design review

---

## 1. Overview

This spec covers two related surfaces:

1. **`familyPortalTab`** — the "Family Portal" tab inside `ClientDetailPanel` (admin/scheduler side). Manages which family members have portal access for a given client.
2. **Family Portal app** — a read-only, consumer-facing experience at `/portal/*` routes within the existing React app. Family members use it to check on their loved one's care without needing an agency login.

### In scope
- `familyPortalTab`: list access, add by email (generate invite link), resend link for existing users, remove
- Invite token flow: admin generates a one-time link, copies and sends it manually (no email infrastructure)
- `/portal/verify?token=…`: magic link landing page — exchanges token for JWT, redirects to dashboard
- `/portal/dashboard`: today's visit status, caregiver card, next 3 upcoming visits, last visit summary
- Backend: `FamilyPortalToken` entity (token stored as SHA-256 hash), `POST /api/v1/clients/{id}/family-portal-users/invite`, `POST /api/v1/family/auth/verify`, `GET /api/v1/family/portal/dashboard`, `DELETE /api/v1/clients/{id}/family-portal-users/{fpuId}`
- `JwtTokenProvider` extended for `FAMILY_PORTAL` tokens with a separate configurable expiry
- `JwtAuthenticationFilter` extended to extract `clientId` claim into `UserPrincipal`
- `UserPrincipal` extended with nullable `clientId`
- `portalAuthStore` persisted to `localStorage` so page refresh does not log out the family member

### Out of scope
- Email delivery (SMTP/SendGrid) — admin copies the link manually in MVP; email is P2
- Push notifications for family portal
- Family member commenting or messaging
- Plan version history or clinical details visible to family
- Family portal on mobile app (React Native)
- Multiple-visits-per-day layout (P2 — API returns the shift with the earliest `scheduledStart` that is not CANCELLED for today)

---

## 2. Architecture

### Routing

The family portal lives inside the existing React frontend as separate routes:

```
/portal/verify       ← magic link landing page (public)
/portal/dashboard    ← read-only family view (requires FAMILY_PORTAL JWT)
```

All `/portal/*` routes use a dedicated `PortalLayout` (no admin sidebar, no `Shell`). The existing admin routes (`/schedule`, `/clients`, etc.) are completely unaffected.

### Auth separation

A new `portalAuthStore.ts` (Zustand) is created alongside the existing `authStore.ts`. It holds `{ token, clientId, agencyId }` for the portal session and is **persisted to `localStorage`** via Zustand's `persist` middleware (storage key: `portal-auth`). This means page refresh and tab-close/reopen preserve the portal session for as long as the JWT is valid.

The existing `authStore` and all admin guards are untouched.

`PortalGuard` wraps protected portal routes. On mount it checks `portalAuthStore.token`:
- If absent → redirects to `/portal/verify?reason=no_session` — verify page shows "No active session. Ask your care coordinator for a new link."
- If present but the JWT is expired (detected by parsing the `exp` claim client-side before any API call) → redirects to `/portal/verify?reason=session_expired` — verify page shows "Your session has expired. Ask your care coordinator for a new link."
- If present and valid → renders the dashboard.

Additionally, `usePortalDashboard` (the React Query hook) must handle API-level errors from `GET /family/portal/dashboard`:
- `403 PORTAL_ACCESS_REVOKED` (admin deleted the `FamilyPortalUser` while JWT was still live) → clears `portalAuthStore` + `localStorage`, redirects to `/portal/verify?reason=access_revoked`
- `410 CLIENT_DISCHARGED` (client record deactivated/discharged) → renders a dedicated "Care services concluded" screen without clearing the session

All verify-page reason states are visually distinct from one another and from the invalid/expired invite-token error (`?reason=token_invalid`).

### Token flow

```
Admin clicks "+ Invite" (or "Send New Link" on an existing row) in familyPortalTab
  → enters email (or email is pre-filled), clicks "Generate Link"
  → POST /api/v1/clients/{clientId}/family-portal-users/invite
      body: { email }
      auth: admin JWT required
  → backend generates a 64-byte SecureRandom value (raw token)
  → stores SHA-256(raw token) in FamilyPortalToken with expiresAt = now + 72 hours
  → raw token is NEVER persisted — only the hash is stored
  → returns { inviteUrl: "https://app.hcare.io/portal/verify?token=<raw-token>" }
  → admin copies the URL, sends it to the family member manually

Family member opens the link in browser
  → /portal/verify?token=<raw-token>
  → page auto-submits: POST /api/v1/family/auth/verify  body: { token: <raw-token> }
  → backend computes SHA-256(<raw-token>), looks up by hash — 400 TOKEN_INVALID if not found
  → checks expiresAt > now — 400 TOKEN_EXPIRED if stale
  → deletes the FamilyPortalToken row (one-time use)
  → calls FamilyPortalUser.recordLogin()
  → generates FAMILY_PORTAL JWT (30-day expiry — see JWT section)
  → returns { jwt, clientId, agencyId }
  → portalAuthStore.login(jwt, clientId, agencyId)  [persisted to localStorage]
  → redirect to /portal/dashboard
```

### JWT claims for family portal

`JwtTokenProvider` gets a second method using a **separate** expiry property `portal.jwt.expiration-days` (default: 30 days; configured independently from `jwt.expiration-ms` which governs admin tokens):

```java
public String generateFamilyPortalToken(UUID fpUserId, UUID clientId, UUID agencyId)
```

Claims: `{ sub: fpUserId, clientId: clientId.toString(), agencyId: agencyId.toString(), role: "FAMILY_PORTAL" }`.

**`JwtAuthenticationFilter` must be updated** to extract the `clientId` claim when `role == FAMILY_PORTAL` and populate it into `UserPrincipal`. Without this, the `clientId` scope boundary on `/api/v1/family/portal/**` cannot be enforced. Concretely:

```java
// In JwtAuthenticationFilter, after extracting role:
UUID clientId = null;
if ("FAMILY_PORTAL".equals(role)) {
    String clientIdStr = jwtTokenProvider.getClientId(token); // new method
    clientId = UUID.fromString(clientIdStr);
}
UserPrincipal principal = new UserPrincipal(userId, agencyId, role, clientId);
```

`JwtTokenProvider` gains `getClientId(String token)` that reads the `clientId` claim.

`UserPrincipal` gains a nullable `clientId` field (non-null only when `role == FAMILY_PORTAL`).

### Security config

- `POST /api/v1/clients/*/family-portal-users/invite` — requires `ADMIN` or `SCHEDULER`
- `DELETE /api/v1/clients/*/family-portal-users/*` — requires `ADMIN` or `SCHEDULER`
- `POST /api/v1/family/auth/verify` — public (no auth); must be **rate-limited** (e.g., Spring's `@RateLimiter` or a servlet filter) to max 10 requests per IP per minute to prevent brute-force hash enumeration
- `GET /api/v1/family/portal/dashboard` — requires `FAMILY_PORTAL` role; annotated `@PreAuthorize("hasRole('FAMILY_PORTAL')")` on the controller method — this annotation is mandatory; without it an admin JWT passes the filter and reaches the endpoint. `clientId` from `UserPrincipal` is the hard scope boundary.

**CORS:** `SecurityConfig` must add the `app.base-url` origin to `allowedOrigins` for `/api/v1/family/**` endpoints. Without this, family portal requests from production domains will be blocked.

### Multi-tenancy note

`FamilyPortalToken` does not need the Hibernate `agencyFilter` — tokens are looked up by hash value alone (short-lived, one-time use). `FamilyPortalUser` already carries `agencyFilter`.

---

## 3. Backend

### New entity: `FamilyPortalToken`

```
FamilyPortalToken
  ├── id            UUID PK
  ├── tokenHash     VARCHAR(64) UNIQUE NOT NULL  ← hex-encoded SHA-256 of the raw token
  ├── fpUserId      UUID NOT NULL FK → family_portal_users.id ON DELETE CASCADE
  ├── clientId      UUID NOT NULL
  ├── agencyId      UUID NOT NULL
  ├── expiresAt     LocalDateTime NOT NULL  ← createdAt + 72 hours
  └── createdAt     LocalDateTime NOT NULL
```

No `@Filter` on this entity. The column `tokenHash` carries a `UNIQUE` index (implied by constraint — Flyway migration must create it explicitly). A `@Scheduled` cleanup job runs nightly and deletes rows where `expiresAt < now` to prevent unbounded accumulation.

`FamilyPortalUser` must have a `UNIQUE` constraint on `(client_id, agency_id, email)` — add to the Flyway migration that creates this table. This prevents `findOrCreate` race conditions under concurrent requests.

### New / modified endpoints

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| `POST` | `/api/v1/clients/{id}/family-portal-users/invite` | `ADMIN` / `SCHEDULER` | Generate invite URL |
| `DELETE` | `/api/v1/clients/{id}/family-portal-users/{fpuId}` | `ADMIN` / `SCHEDULER` | Remove portal access |
| `POST` | `/api/v1/family/auth/verify` | Public | Exchange token for JWT |
| `GET`  | `/api/v1/family/portal/dashboard` | `FAMILY_PORTAL` | Fetch portal data |

### `POST /api/v1/clients/{id}/family-portal-users/invite`

Request: `{ "email": "robert@example.com" }`

Behaviour:
1. Validates client belongs to authenticated agency (tenant filter covers this)
2. Finds or creates `FamilyPortalUser` for `(clientId, agencyId, email)` — `UNIQUE` constraint prevents duplicates
3. Generates a 64-byte `SecureRandom` value; computes `tokenHash = SHA-256(rawToken)` in hex
4. Stores `FamilyPortalToken(tokenHash, fpUserId, clientId, agencyId, expiresAt = now + 72 hours)`
5. Builds `inviteUrl` from `app.base-url` config property (dev default: `http://localhost:5173`) — raw token in URL, **never logged**
6. Returns `{ "inviteUrl": "...", "expiresAt": "<ISO timestamp>" }`

Response: `200 OK { inviteUrl: string, expiresAt: string }`

### `DELETE /api/v1/clients/{id}/family-portal-users/{fpuId}`

Deletes the `FamilyPortalUser` row. Tenant filter ensures the fpuId belongs to the authenticated agency. Returns `204 No Content`.

### `POST /api/v1/family/auth/verify`

Request: `{ "token": "<raw-hex-token>" }`

Behaviour:
1. Computes `hash = SHA-256(submitted token)`, looks up `FamilyPortalToken` by `tokenHash` — 400 `TOKEN_INVALID` if not found
2. Checks `expiresAt > now` — 400 `TOKEN_EXPIRED` if stale
3. Deletes the token row (one-time use)
4. Calls `fpUser.recordLogin()`
5. Generates `FAMILY_PORTAL` JWT via `JwtTokenProvider.generateFamilyPortalToken(fpUserId, clientId, agencyId)`
6. Returns `{ "jwt": "...", "clientId": "...", "agencyId": "..." }`

### `GET /api/v1/family/portal/dashboard`

Auth: `FAMILY_PORTAL` JWT required. Annotated `@PreAuthorize("hasRole('FAMILY_PORTAL')")`.

On each request, the handler must:
1. Load `FamilyPortalUser` by `fpUserId` (JWT `sub` claim) — if not found (user was removed), return `403` with error code `PORTAL_ACCESS_REVOKED`. Frontend treats this as revocation: clears store + localStorage, redirects to `/portal/verify?reason=access_revoked`.
2. Load `Client` by `clientId` — if `client.status == DISCHARGED` or `INACTIVE`, return `410` with error code `CLIENT_DISCHARGED`. Frontend shows a dedicated "Care services have concluded" screen (does not clear session).

Returns:
```json
{
  "clientFirstName": "Margaret",
  "agencyTimezone": "America/New_York",
  "todayVisit": {
    "shiftId": "...",
    "scheduledStart": "2026-04-08T09:00:00",
    "scheduledEnd":   "2026-04-08T11:00:00",
    "status": "IN_PROGRESS",
    "clockedInAt": "2026-04-08T09:04:00",
    "clockedOutAt": null,
    "caregiver": { "name": "Maria Gonzalez", "serviceType": "Personal Care Aide" }
  },
  "upcomingVisits": [
    {
      "scheduledStart": "2026-04-09T09:00:00",
      "scheduledEnd":   "2026-04-09T11:00:00",
      "caregiverName":  "Maria Gonzalez"
    }
  ],
  "lastVisit": {
    "date": "2026-04-07",
    "clockedOutAt": "2026-04-07T11:03:00",
    "durationMinutes": 119,
    "noteText": "Margaret was in good spirits..."
  }
}
```

**Field notes:**
- `todayVisit` is the first non-cancelled shift scheduled today for this client. `null` if no shift scheduled.
- `todayVisit.status` values: `GREY` (scheduled, not started), `IN_PROGRESS`, `COMPLETED`, `CANCELLED`. All four must be handled by the frontend.
- `agencyTimezone` is an IANA timezone string from the agency's profile. All `scheduledStart`/`scheduledEnd`/`clockedInAt`/`clockedOutAt` timestamps are in UTC ISO 8601; the frontend converts to `agencyTimezone` for display.
- `upcomingVisits` returns at most 3 shifts with `scheduledStart` after end of current calendar day, ordered ascending. Does not include cancelled shifts.
- `lastVisit` is the most recent completed shift for this client. `date`, `clockedOutAt`, and `durationMinutes` are always present when `lastVisit` is non-null. `noteText` is `null` if the caregiver entered no notes — the `lastVisit` object itself is always shown when a completed shift exists.
- `lastVisit` is `null` only if no completed shifts exist at all for this client.

---

## 4. Frontend

### New files

| Path | Purpose |
|------|---------|
| `frontend/src/store/portalAuthStore.ts` | Zustand store with `persist` to `localStorage` key `portal-auth`: `{ token, clientId, agencyId }` — see sketch below |
| `frontend/src/components/portal/PortalGuard.tsx` | Checks portal JWT validity; redirects with `?reason=` on failure |
| `frontend/src/components/portal/PortalLayout.tsx` | Minimal layout — no sidebar; includes logout button (clears `portalAuthStore` + `localStorage`) |
| `frontend/src/pages/PortalVerifyPage.tsx` | Magic link landing; reads `?token=` and `?reason=`; auto-submits or shows reason error |
| `frontend/src/pages/PortalDashboardPage.tsx` | Read-only family dashboard |
| `frontend/src/api/portal.ts` | `inviteFamilyPortalUser()`, `removeFamilyPortalUser()`, `verifyPortalToken()`, `getPortalDashboard()` |
| `frontend/src/hooks/usePortalDashboard.ts` | React Query hook wrapping `getPortalDashboard()` |
| `frontend/src/components/clients/FamilyPortalTab.tsx` | The familyPortalTab component |
| `frontend/src/components/clients/FamilyPortalTab.test.tsx` | Unit tests |
| `frontend/src/pages/PortalVerifyPage.test.tsx` | Unit tests |
| `frontend/src/pages/PortalDashboardPage.test.tsx` | Unit tests |
| `frontend/public/locales/en/portal.json` | All portal i18n keys |

### `portalAuthStore.ts` sketch

```ts
import { create } from 'zustand'
import { persist } from 'zustand/middleware'

interface PortalAuthState {
  token: string | null
  clientId: string | null
  agencyId: string | null
  login: (token: string, clientId: string, agencyId: string) => void
  logout: () => void
}

export const usePortalAuthStore = create<PortalAuthState>()(
  persist(
    (set) => ({
      token: null,
      clientId: null,
      agencyId: null,
      login: (token, clientId, agencyId) => set({ token, clientId, agencyId }),
      logout: () => set({ token: null, clientId: null, agencyId: null }),
    }),
    { name: 'portal-auth' }  // localStorage key
  )
)
```

`usePortalAuthStore.getState().logout()` is the single logout call used by both `PortalLayout` sign-out and the `403`/revocation handler. It clears the store and, because `persist` is wired to `localStorage`, also removes the `portal-auth` key automatically.

### Modified files

| Path | Change |
|------|--------|
| `frontend/src/components/clients/ClientDetailPanel.tsx` | Replace `familyPortalPhaseNote` placeholder with `<FamilyPortalTab clientId={clientId} />` |
| `frontend/src/App.tsx` | Add `/portal/verify` and `/portal/dashboard` routes as siblings to the admin `<RequireAuth>` route |
| `frontend/public/locales/en/clients.json` | Add `familyPortal*` i18n keys for the tab |
| `frontend/src/i18n.ts` | Register `portal` namespace |

### Routing additions

```tsx
<Route path="/portal/verify" element={<PortalVerifyPage />} />
<Route
  path="/portal/dashboard"
  element={
    <PortalGuard>
      <PortalLayout>
        <PortalDashboardPage />
      </PortalLayout>
    </PortalGuard>
  }
/>
```

These are siblings to the admin `<Route element={<RequireAuth><Shell /></RequireAuth>}>` — not children of it.

---

## 5. Visual Design

### familyPortalTab

Matches the Care Plan tab style exactly:
- `bg-surface` background, white cards, `border-border` borders
- Section label: `text-[9px] font-bold tracking-[.1em] uppercase text-text-secondary`
- "+ Invite" button: `bg-dark text-white text-[11px] font-bold`
- **User rows:** white card with name, email, "Last login" meta ("Never logged in" when `lastLoginAt` is null — never blank), a **"Send New Link"** ghost button, and a "Remove" ghost button
  - "Send New Link" re-opens the invite form with the email pre-filled
  - "Remove" shows an inline confirmation: "Remove [name]? Their access will be revoked on next page load. Any active session ends when they next visit the portal." with Cancel / Confirm buttons before calling the DELETE endpoint. Do not say "immediately" — the 30-day JWT stored in the family member's browser remains valid until the next API call triggers a `403 PORTAL_ACCESS_REVOKED` response.
- Invite form opens inline below the list with `border border-blue bg-white p-3` treatment (same as `AddGoalForm`)
  - Email input (pre-filled when opened from "Send New Link")
  - "Generate Link" button (`bg-dark`)
  - After generation: monospace invite URL in `bg-surface border-border` container with a "Copy" button (`border border-blue text-blue`)
  - Expiry note: `text-[12px] text-text-secondary` — "Link expires Apr 11 at 2:34 PM — valid for 72 hours"
  - **When form is opened from "Send New Link"** (re-invite context already known): the inline note "A new link will be sent to this existing user" must appear immediately — before the admin clicks Generate, not only after the server responds. The form knows it is in re-invite mode from the moment it opens with a pre-filled email. Do not wait for a server 200 to surface this note.

### Portal Dashboard (`/portal/dashboard`)

- **Page background:** `bg-surface` (`#f6f6fa`)
- **Cards:** `bg-white border border-border`
- **Header:** white bar — "hcare" small all-caps label above `text-[18px] font-bold text-text-primary` client first name ("Margaret's Care"); current date `text-[12px] text-text-secondary`
- **Today's Visit card** — status pill styles (minimum `text-[12px]` throughout):
  - `IN_PROGRESS`: `bg-green-50 border border-green-200`, green dot, `text-green-700` "Maria is here now"
  - `GREY` (on time): `bg-surface border border-border`, gray dot, `text-text-secondary` "Maria is scheduled for 9:00 AM"
  - `GREY` (late — current time > scheduledStart + 15 min): `bg-amber-50 border border-amber-200`, **clock icon** (not just an amber dot — do not rely on color alone), `text-amber-700` "Scheduled for 9:00 AM — not yet started. Contact your care coordinator."
  - `COMPLETED`: `bg-surface border border-border`, ✓ checkmark icon (not just color), `text-text-primary` "Visit completed at 11:03 AM" — use `text-text-primary`, not `text-text-secondary`; completion is the most reassuring state on the screen
  - `CANCELLED`: `bg-red-50 border border-red-200`, `text-red-700` "Today's visit was cancelled. Contact your care coordinator." **Caregiver card is hidden in CANCELLED state** — do not render the caregiver avatar/name when status is CANCELLED; showing it would imply a specific person cancelled, which may not be accurate
  - No visit today (`todayVisit: null`): `text-[13px] text-text-secondary text-center` "No visit scheduled for today"
  - Caregiver avatar: `bg-blue` circle with initials, name `text-[15px] font-bold`, service type `text-[13px] text-text-secondary`
  - Clock-in / scheduled-until times: `text-[14px] font-semibold text-text-primary` with timezone label (e.g., "9:04 AM EDT")
- **Upcoming visits:** next 3 non-cancelled future shifts; each row `text-[13px] font-semibold text-text-primary` date + `text-[12px] text-text-secondary` time range + caregiver name. If `upcomingVisits` is empty: `text-[13px] text-text-secondary` "No upcoming visits scheduled. Contact your care coordinator to confirm the schedule."
- **Last visit summary** (always shown when `lastVisit` is non-null):
  - Header: `text-[9px] font-bold tracking-[.1em] uppercase text-text-secondary` "Last Visit (Apr 7)"
  - Sub-line: `text-[13px] text-text-primary` "Completed at 11:03 AM · 1 hr 59 min" — always present
  - Note text: `text-[13px] text-text-primary leading-relaxed` (not italic, not muted — readable weight and color) — shown only when `noteText` is non-null; omit the note sub-section entirely when null (the completion fact above is always shown)
- **Typography:** all sizes ≥ 12px; headings 15–18px; no italic for body text
- **Touch targets:** all interactive elements minimum 44px height on mobile viewports

### PortalVerifyPage

Centered card on **`bg-surface`** background (not dark — establishes portal's own visual identity, avoids confusion with admin login). Six states:

1. **Auto-verifying** (token in query param, no `reason`): spinner + `role="status"` `aria-live="polite"` region announcing "Signing you in…"
2. **Token invalid or expired** (`?reason=token_invalid` or `400 TOKEN_INVALID` / `TOKEN_EXPIRED`): heading "Link expired", body "This link has expired or is invalid. Ask your care coordinator for a new one." — `text-text-primary` with a warning icon
3. **Session expired** (`?reason=session_expired`): heading "Session expired", body "Your session has expired. Ask your care coordinator for a new link." — visually distinct from state 2 (different icon)
4. **No session** (`?reason=no_session`): heading "No active session", body "No active session found. Ask your care coordinator for an access link." — reserved for unauthenticated arrivals with no JWT
5. **Signed out** (`?reason=signed_out`): heading "You've been signed out", body "You've been signed out. Use your original link or ask your care coordinator to send a new one." — this is the state reached after intentional sign-out; must NOT reuse `no_session` copy
6. **Access revoked** (`?reason=access_revoked`): heading "Access removed", body "Your portal access has been removed. Contact your care coordinator for more information." — reached after `403 PORTAL_ACCESS_REVOKED` from the API

No form. All error states use `text-[14px] text-text-primary` with a visible icon and a semantic heading (`<h1>` or `<h2>`) for screen reader landmark announcement — not muted gray, not icon-only.

**URL cleanup:** After `?reason=` is read on mount, call `history.replaceState(null, '', '/portal/verify')` to strip the parameter from the URL bar. This prevents a family member from copying and sharing a URL that propagates an error reason unnecessarily.

### PortalLayout

Minimal wrapper. Contains:
- The portal header (shared with dashboard)
- A **"Sign out"** link/button in the header, right-aligned: minimum 44px touch target height (use padding to achieve this). On click: clears `portalAuthStore`, removes `portal-auth` from `localStorage`, navigates to `/portal/verify?reason=signed_out`. Do NOT navigate to `?reason=no_session` — that state is reserved for unauthenticated arrivals; `signed_out` has different and more accurate copy.
- No sidebar, no admin navigation

---

## 6. Error Handling

| Scenario | Behaviour |
|----------|-----------|
| Invite token expired (> 72 hrs) | `POST /verify` returns `400 TOKEN_EXPIRED` → verify page state 2 |
| Invite token already used | `400 TOKEN_INVALID` (hash not found after deletion) → verify page state 2 |
| Portal JWT expired (PortalGuard detects on load) | Redirect to `/portal/verify?reason=session_expired` → verify page state 3 |
| No portal session (PortalGuard, no token) | Redirect to `/portal/verify?reason=no_session` → verify page state 4 |
| `todayVisit.status == CANCELLED` | Dashboard today card shows cancelled pill with contact instruction |
| No visit today | `todayVisit: null` → dashboard today card shows "No visit scheduled for today" |
| `upcomingVisits` empty array | Upcoming section shows "No upcoming visits scheduled…" empty state |
| `lastVisit: null` | Last visit section is omitted entirely |
| `getPortalDashboard` network error | React Query retry × 2; on failure show a visible retry button with `text-[13px] text-text-primary` "Unable to load. Tap to retry." — no "Pull to refresh" (not a native gesture on desktop). React Query must be configured with `staleTime` such that stale cached data is not displayed without a visible "Last updated [time]" indicator — showing 2-day-old data silently is worse than showing the error state. |
| `GET /family/portal/dashboard` returns `403 PORTAL_ACCESS_REVOKED` | Family member's `FamilyPortalUser` was deleted by admin. Frontend clears `portalAuthStore` + removes `portal-auth` from `localStorage`, navigates to `/portal/verify?reason=access_revoked` (verify page state 6). |
| `GET /family/portal/dashboard` returns `410 CLIENT_DISCHARGED` | Client record is DISCHARGED or INACTIVE. Frontend renders a dedicated "Care services for [first name] have concluded. Please contact the agency for more information." screen without clearing the session. |
| Voluntary sign-out | "Sign out" in PortalLayout clears store + localStorage, navigates to `/portal/verify?reason=signed_out` (verify page state 5). |
| Admin generates link for existing email | `findOrCreate` — no error; admin sees "A new link will be sent to this existing user" inline note |
| Concurrent invite requests for same email | `UNIQUE` constraint on `FamilyPortalUser(clientId, agencyId, email)` → one succeeds, one gets a `409`; frontend retries once. If the retry also fails, the invite form shows an inline error: "Unable to send invite. Please try again." — do not leave the error state undefined. |

---

## 7. Testing

### Backend
- `FamilyPortalTokenIT`: token creation, SHA-256 hash stored (not raw), expiry (72 hrs), one-time-use (second verify call returns 400), nightly cleanup job deletes expired rows
- `FamilyPortalAuthControllerIT`: happy path verify, expired token (400), missing/invalid token (400), verify with a raw token whose hash doesn't match (400)
- `FamilyPortalDashboardControllerIT`: returns correct client data for `clientId` in JWT, rejects JWT with wrong `clientId` (403), rejects admin JWT on `/family/portal/*` (403), `todayVisit` null when no shift, CANCELLED status returned correctly, `lastVisit` present without noteText when notes null, returns `403 PORTAL_ACCESS_REVOKED` when `FamilyPortalUser` has been deleted, returns `410 CLIENT_DISCHARGED` when client status is DISCHARGED or INACTIVE
- `JwtAuthenticationFilterTest`: `FAMILY_PORTAL` JWT correctly populates `UserPrincipal.clientId`; admin JWT leaves `clientId` null

### Frontend
- `FamilyPortalTab.test.tsx`: renders user list with "Never logged in" for null lastLogin, invite form open/close, email pre-fill on "Send New Link", re-invite inline note for existing email, copy button, remove inline confirmation (Cancel + Confirm), DELETE called on confirm
- `PortalVerifyPage.test.tsx`: auto-submits token on mount with aria-live announcement, redirects to dashboard on success, shows state 2 on 400, shows state 3 on `?reason=session_expired`, shows state 4 on `?reason=no_session`, shows state 5 on `?reason=signed_out` (heading "You've been signed out"), shows state 6 on `?reason=access_revoked` (heading "Access removed"), `?reason=` is stripped from URL bar after reading via `history.replaceState`
- `PortalDashboardPage.test.tsx`: renders IN_PROGRESS, GREY on-time, GREY late (clock icon present, not just amber color), COMPLETED (checkmark, `text-text-primary`), CANCELLED (caregiver card hidden), and null todayVisit states; upcoming list capped at 3; upcoming empty state; lastVisit with note; lastVisit without note (completion fact shown, note section absent); lastVisit null (section omitted); timezone label present on times; Sign out clears store and navigates to `?reason=signed_out`; `403 PORTAL_ACCESS_REVOKED` clears store + redirects to `?reason=access_revoked`; `410 CLIENT_DISCHARGED` renders "Care services concluded" screen without clearing session
