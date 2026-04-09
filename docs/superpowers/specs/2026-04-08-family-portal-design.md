# Family Portal — Design Spec

**Date:** 2026-04-08
**Status:** Approved through brainstorming session

---

## 1. Overview

This spec covers two related surfaces:

1. **`familyPortalTab`** — the "Family Portal" tab inside `ClientDetailPanel` (admin/scheduler side). Manages which family members have portal access for a given client.
2. **Family Portal app** — a read-only, consumer-facing experience at `/portal/*` routes within the existing React app. Family members use it to check on their loved one's care without needing an agency login.

### In scope
- `familyPortalTab`: list access, add by email (generate invite link), remove
- Invite token flow: admin generates a one-time link, copies and sends it manually (no email infrastructure)
- `/portal/verify?token=…`: magic link landing page — exchanges token for JWT, redirects to dashboard
- `/portal/dashboard`: today's visit status, caregiver profile card, next 3 upcoming visits, last visit notes
- Backend: `FamilyPortalToken` entity, `POST /api/v1/clients/{id}/family-portal-users/invite`, `POST /api/v1/family/auth/verify`, `GET /api/v1/family/portal/dashboard`
- `JwtTokenProvider` extended for `FAMILY_PORTAL` tokens
- `UserPrincipal` extended with nullable `clientId`

### Out of scope
- Email delivery (SMTP/SendGrid) — admin copies the link manually in MVP; email is P2
- Push notifications for family portal
- Family member commenting or messaging
- Plan version history or clinical details visible to family
- Family portal on mobile app (React Native)

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

A new `portalAuthStore.ts` (Zustand) is created alongside the existing `authStore.ts`. It holds `{ token, clientId, agencyId }` for the portal session. The existing `authStore` and all admin guards are untouched.

`PortalGuard` wraps protected portal routes. If no portal token is present, it redirects to `/portal/verify` (without a token query param — shows an "invalid or expired link" message).

### Token flow

```
Admin clicks "+ Invite" in familyPortalTab
  → enters email, clicks "Generate Link"
  → POST /api/v1/clients/{clientId}/family-portal-users/invite
      body: { email }
      auth: admin JWT required
  → backend creates FamilyPortalToken row (15-min TTL), creates or finds FamilyPortalUser
  → returns { inviteUrl: "https://app.hcare.io/portal/verify?token=<signed-token>" }
  → admin copies the URL, sends it to the family member manually

Family member opens the link in browser
  → /portal/verify?token=<signed-token>
  → page auto-submits: POST /api/v1/family/auth/verify  body: { token }
  → backend validates token, deletes it (one-time use), calls FamilyPortalUser.recordLogin()
  → returns { jwt, clientId, agencyId }
  → portalAuthStore.login(jwt, clientId, agencyId)
  → redirect to /portal/dashboard
```

### JWT claims for family portal

`JwtTokenProvider` gets a second method:

```java
public String generateFamilyPortalToken(UUID fpUserId, UUID clientId, UUID agencyId)
```

Claims: `{ sub: fpUserId, clientId, agencyId, role: "FAMILY_PORTAL" }`.

`UserPrincipal` gains a nullable `clientId` field (non-null only when `role == FAMILY_PORTAL`).

### Security config

- `POST /api/v1/clients/*/family-portal-users/invite` — requires `ADMIN` or `SCHEDULER` (existing admin JWT)
- `POST /api/v1/family/auth/verify` — public (no auth)
- `GET /api/v1/family/portal/dashboard` — requires `FAMILY_PORTAL` role; `clientId` claim is the hard scope boundary

### Multi-tenancy note

`FamilyPortalToken` does not need the Hibernate `agencyFilter` — tokens are looked up by their signed value alone (short-lived, one-time use). `FamilyPortalUser` already carries `agencyFilter`.

---

## 3. Backend

### New entity: `FamilyPortalToken`

```
FamilyPortalToken
  ├── id            UUID PK
  ├── token         VARCHAR(128) UNIQUE NOT NULL  ← SecureRandom 64-byte hex
  ├── fpUserId      UUID NOT NULL FK → family_portal_users.id
  ├── clientId      UUID NOT NULL
  ├── agencyId      UUID NOT NULL
  ├── expiresAt     LocalDateTime NOT NULL  ← createdAt + 15 min
  └── createdAt     LocalDateTime NOT NULL
```

No `@Filter` on this entity — tokens are single-use and looked up by value, not by agency.

### New endpoints

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| `POST` | `/api/v1/clients/{id}/family-portal-users/invite` | `ADMIN` / `SCHEDULER` | Generate invite URL |
| `POST` | `/api/v1/family/auth/verify` | Public | Exchange token for JWT |
| `GET`  | `/api/v1/family/portal/dashboard` | `FAMILY_PORTAL` | Fetch portal data |

### `POST /api/v1/clients/{id}/family-portal-users/invite`

Request: `{ "email": "robert@example.com" }`

Behaviour:
1. Validates client belongs to authenticated agency (tenant filter covers this)
2. Finds or creates `FamilyPortalUser` for `(clientId, agencyId, email)`
3. Generates a 64-byte `SecureRandom` hex token, stores as `FamilyPortalToken` with `expiresAt = now + 15 min`
4. Returns `{ "inviteUrl": "https://app.hcare.io/portal/verify?token=<token>" }` — base URL comes from an `app.base-url` config property (dev default: `http://localhost:5173`)

Response: `200 OK { inviteUrl: string }`

### `POST /api/v1/family/auth/verify`

Request: `{ "token": "<hex-token>" }`

Behaviour:
1. Looks up `FamilyPortalToken` by value — 404 if not found
2. Checks `expiresAt > now` — 400 `TOKEN_EXPIRED` if stale
3. Deletes the token (one-time use)
4. Calls `fpUser.recordLogin()`
5. Generates `FAMILY_PORTAL` JWT via `JwtTokenProvider.generateFamilyPortalToken(fpUserId, clientId, agencyId)`
6. Returns `{ "jwt": "...", "clientId": "...", "agencyId": "..." }`

### `GET /api/v1/family/portal/dashboard`

Auth: `FAMILY_PORTAL` JWT required. `clientId` from JWT claim.

Returns:
```json
{
  "clientFirstName": "Margaret",
  "todayVisit": {
    "shiftId": "...",
    "scheduledStart": "2026-04-08T09:00:00",
    "scheduledEnd": "2026-04-08T11:00:00",
    "status": "IN_PROGRESS",
    "clockedInAt": "2026-04-08T09:04:00",
    "caregiver": { "name": "Maria Gonzalez", "serviceType": "Personal Care Aide" }
  },
  "upcomingVisits": [
    { "scheduledStart": "...", "scheduledEnd": "...", "caregiverName": "Maria Gonzalez" }
    // max 3
  ],
  "lastVisitNote": {
    "date": "2026-04-07",
    "noteText": "Margaret was in good spirits..."
  }
}
```

`todayVisit` is `null` if no shift is scheduled today. `lastVisitNote` maps to `Shift.notes` of the most recent completed shift; it is `null` if no completed visits have a non-null `notes` value. `upcomingVisits` returns at most 3 shifts whose `scheduledStart` is after the end of the current calendar day (i.e., tomorrow onwards), ordered ascending.

---

## 4. Frontend

### New files

| Path | Purpose |
|------|---------|
| `frontend/src/store/portalAuthStore.ts` | Zustand store: `{ token, clientId, agencyId }` |
| `frontend/src/components/portal/PortalGuard.tsx` | Redirects to `/portal/verify` if no portal token |
| `frontend/src/components/portal/PortalLayout.tsx` | Minimal layout wrapper — no sidebar, no Shell |
| `frontend/src/pages/PortalVerifyPage.tsx` | Magic link landing: reads `?token=`, calls verify, redirects |
| `frontend/src/pages/PortalDashboardPage.tsx` | Read-only family dashboard |
| `frontend/src/api/portal.ts` | `inviteFamilyPortalUser()`, `verifyPortalToken()`, `getPortalDashboard()` |
| `frontend/src/hooks/usePortalDashboard.ts` | React Query hook wrapping `getPortalDashboard()` |
| `frontend/src/components/clients/FamilyPortalTab.tsx` | The familyPortalTab component |
| `frontend/src/components/clients/FamilyPortalTab.test.tsx` | Unit tests |
| `frontend/src/pages/PortalVerifyPage.test.tsx` | Unit tests |
| `frontend/src/pages/PortalDashboardPage.test.tsx` | Unit tests |
| `frontend/public/locales/en/portal.json` | All portal i18n keys |

### Modified files

| Path | Change |
|------|--------|
| `frontend/src/components/clients/ClientDetailPanel.tsx` | Replace placeholder with `<FamilyPortalTab clientId={clientId} />` |
| `frontend/src/App.tsx` (or router file) | Add `/portal/verify` and `/portal/dashboard` routes |
| `frontend/public/locales/en/clients.json` | Add `familyPortal*` keys for the tab |
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

These are siblings to the admin `<Route>` that wraps `<Shell>` — not children of it.

---

## 5. Visual Design

### familyPortalTab

Matches the Care Plan tab style exactly:
- `bg-surface` page background, white cards, `border-border` borders
- Section label: `text-[9px] font-bold tracking-[.1em] uppercase text-text-secondary`
- "+ Invite" button: `bg-dark text-white text-[11px] font-bold`
- User rows: white card with name, email, "Last login" meta, "Remove" ghost button
- Invite form opens inline below the list with `border border-blue bg-white p-3` treatment (same as `AddGoalForm`)
- Form fields: email input, "Generate Link" button (`bg-dark`)
- After generation: monospace invite URL in a `bg-surface border-border` container with a "Copy" button (`border border-blue text-blue`)
- Expiry note: `text-[10px] text-text-muted`

### Portal Dashboard (`/portal/dashboard`)

- **Page background:** `bg-surface` (`#f6f6fa`)
- **Cards:** white (`bg-white`) with `border border-border`
- **Header:** white bar — "hcare" small all-caps label above large `text-[18px] font-bold` client first name ("Margaret's Care")
- **Today's Visit card:**
  - Status pill styles:
    - Clocked in (IN_PROGRESS): green fill `bg-green-50 border border-green-200`, green dot, `text-green-700` "Maria is here now"
    - Scheduled (GREY): light gray fill, gray dot, `text-text-secondary` "Expected at 9:00 AM"
    - Completed: similar to scheduled but text reads "Visit completed at 11:03 AM"
    - No visit today: `text-[11px] text-text-secondary text-center` "No visit scheduled for today"
  - Caregiver avatar: `bg-blue` circle with initials, name `text-[15px] font-bold`, service type `text-[12px] text-text-secondary`
  - Clock-in / scheduled-until times: `text-[14px] font-semibold text-text-primary`
- **Upcoming visits:** next 3 future shifts only; each row shows date (`font-semibold`) and time + caregiver name (`text-text-secondary`)
- **Last visit note:** white card, `text-[13px] text-text-secondary leading-relaxed`, italic quote style
- **Typography:** all sizes ≥ 12px; headings 15–18px — optimised for readability on mobile

### PortalVerifyPage

Centered card on `bg-dark` background (reuses the existing `LoginPage` style). Two states:
1. **Auto-verifying:** spinner + "Signing you in…"
2. **Error:** "This link has expired or is invalid. Ask your care coordinator for a new one."

No form — token is read from `?token=` query param and submitted automatically on mount.

---

## 6. Error Handling

| Scenario | Behaviour |
|----------|-----------|
| Token expired (> 15 min) | `POST /verify` returns `400 TOKEN_EXPIRED` → verify page shows error message |
| Token already used | Same as expired (token deleted on first use) |
| No visit today | `todayVisit: null` → dashboard shows "No visit scheduled for today" in the today card |
| `getPortalDashboard` network error | React Query retry × 2; on failure show `text-text-secondary` "Unable to load. Pull to refresh." |
| Admin generates link for unknown email | `findOrCreate` semantics — no error; a new `FamilyPortalUser` is created |

---

## 7. Testing

### Backend
- `FamilyPortalTokenIT`: token creation, expiry, one-time-use (second verify call returns 400)
- `FamilyPortalAuthControllerIT`: happy path verify, expired token, missing token
- `FamilyPortalDashboardControllerIT`: returns correct client data, rejects wrong `clientId` JWT, rejects admin JWT on `/family/portal/*`

### Frontend
- `FamilyPortalTab.test.tsx`: renders user list, invite form open/close, copy button, remove confirmation
- `PortalVerifyPage.test.tsx`: auto-submits token on mount, redirects on success, shows error on 400
- `PortalDashboardPage.test.tsx`: renders all three today-visit states (IN_PROGRESS, GREY, no visit), upcoming list capped at 3, last note present/absent
