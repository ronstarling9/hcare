# Critical Review: Family Portal Design Spec
**Spec reviewed:** `2026-04-08-family-portal-design.md`
**Reviewer:** Critical design review agent
**Date:** 2026-04-08

---

## Summary

The spec describes a functional feature but has four critical defects that would cause security bugs or runtime failures if implemented as written. There are also five major gaps that would produce broken behaviour or a difficult PR review. Minor issues are listed at the end. None of the critical issues are edge-case oversights — they are all on the primary happy path.

---

## CRITICAL Issues

### C-1 — `JwtAuthenticationFilter` will NullPointerException on every FAMILY_PORTAL JWT

**Section:** §2 "JWT claims for family portal", §2 "Architecture / Auth separation"
**Files:** `JwtAuthenticationFilter.java` lines 31–35, `UserPrincipal.java`

The `generateFamilyPortalToken` method (proposed) will embed a `clientId` claim in the JWT but the existing `JwtAuthenticationFilter.doFilterInternal()` constructs `UserPrincipal` with the three-arg constructor `new UserPrincipal(userId, agencyId, role)`. It does not extract `clientId` from claims. There is no three-arg constructor that would silently absorb this — the problem is that `UserPrincipal` has no `clientId` field at all right now.

More precisely: the spec says `UserPrincipal` gains a "nullable `clientId` field (non-null only when `role == FAMILY_PORTAL`)" but gives no implementation detail. The filter constructs `UserPrincipal` from `Claims` in one place — if that construction is not updated, every FAMILY_PORTAL JWT will produce a `UserPrincipal` with `clientId == null`, the dashboard endpoint will read a null `clientId`, and then either silently return data for the wrong client or NullPointerException. Neither outcome is acceptable.

The spec must explicitly document: (a) a new four-arg constructor or factory method on `UserPrincipal`, (b) the null-safe extraction of `clientId` from claims in `JwtAuthenticationFilter`, and (c) how `clientId` is read in the dashboard controller. This is not an implementation detail that can be left to the developer — it is the primary access-control mechanism for the entire feature.

---

### C-2 — The JWT family portal token uses the same signing key and has no audience/issuer claim — admin tokens are accepted on family portal endpoints and vice versa

**Section:** §2 "JWT claims for family portal", §2 "Security config"
**Files:** `JwtTokenProvider.java`, `SecurityConfig.java`

`JwtTokenProvider` uses a single `signingKey` derived from `JwtProperties`. The existing `generateToken()` method and the proposed `generateFamilyPortalToken()` method will sign with the same key. The only thing separating a `FAMILY_PORTAL` token from an `ADMIN` token is the `role` claim inside the payload.

The `GET /api/v1/family/portal/dashboard` endpoint is described as requiring `FAMILY_PORTAL` role, and `POST /api/v1/clients/*/family-portal-users/invite` is described as requiring `ADMIN` or `SCHEDULER`. But if both restrictions are enforced purely via `@PreAuthorize("hasRole('FAMILY_PORTAL')")` / `hasRole('ADMIN')`, that is fine — the role claim does enforce separation.

**However**, there is a more dangerous gap: `POST /api/v1/family/auth/verify` is public (permitAll). A valid ADMIN JWT whose `role` claim is manipulated to `FAMILY_PORTAL` by a confused/malicious client would also be accepted if the filter only checks the signature. The existing filter does nothing to verify the intended audience. No `aud` or `iss` claim is added, and the `SecurityConfig` does not gate `/api/v1/family/**` to only FAMILY_PORTAL-issued tokens.

Additionally, the spec does not address what happens when an admin user navigates to `/portal/dashboard` with their admin JWT in memory — the `PortalGuard` only checks `portalAuthStore`, so the route is correctly blocked, but a direct API call with an admin Bearer token to `GET /family/portal/dashboard` would be rejected (correct), yet the spec provides no test asserting this. This must be an explicit integration test case.

Most critically: the spec says the FAMILY_PORTAL JWT expiry is not specified. `JwtTokenProvider` uses a single `expirationMs` from `JwtProperties`. The spec should define the portal JWT lifetime. A portal token valid for the same duration as an admin JWT (often 8–24 hours) is disproportionately long for a consumer magic-link flow. This should be a separate shorter expiry, requiring a second `JwtProperties` configuration value.

---

### C-3 — `FamilyPortalToken` lookup-by-value is a timing-safe comparison gap and has no index defined

**Section:** §3 "`FamilyPortalToken`", §3 "`POST /api/v1/family/auth/verify`"

The token value is a 64-byte SecureRandom hex string stored in a `VARCHAR(128) UNIQUE` column. The spec calls for looking it up by value (`WHERE token = ?`). Two problems:

1. **No explicit index defined in the spec.** The `UNIQUE` constraint creates an implicit index in most databases, but the spec should call out that the Flyway migration must include this index. More importantly, the column is also used as a primary lookup key in a potentially high-concurrency path. The spec should explicitly note the migration.

2. **No timing-safe comparison.** A plain SQL `WHERE token = ?` comparison is database-level and not timing-safe against offline attacks. However, the token is not a password — it is a high-entropy one-time value stored in plaintext in the DB. The real risk is that the spec stores the raw token value, not a hash. If the `family_portal_tokens` table is read by an attacker (e.g., via SQL injection elsewhere), all outstanding tokens are immediately usable to authenticate as any linked family portal user. The spec should store a SHA-256 hash of the token in the DB and compare hashes, returning the plaintext token only in the `inviteUrl`. This follows the same principle used for password reset tokens throughout the industry.

---

### C-4 — `portalAuthStore` is in-memory only — a page reload logs out the family member silently

**Section:** §2 "Auth separation", §4 "New files"
**Files:** `frontend/src/store/authStore.ts`

`authStore.ts` uses plain Zustand `create()` with no persistence middleware. The existing admin store is also in-memory. For admin users this is acceptable because they authenticate with email/password and can re-login. For family portal users, the **magic link is one-time use** (§3: "Deletes the token (one-time use)"). Once they have authenticated once, the only JWT they will ever hold is the one issued on that first verify call.

If a family member opens the dashboard, then:
- Refreshes the page
- Opens a new tab
- Their device sleeps and the browser unloads the tab

...the Zustand store resets to `null`, `PortalGuard` redirects to `/portal/verify` with no token query param, and the user sees "This link has expired or is invalid. Ask your care coordinator for a new one." They are permanently locked out until the admin generates a new link.

The spec makes no mention of `localStorage`/`sessionStorage` persistence for the portal JWT, despite the one-time-use nature of the magic link making this a critical UX and functional requirement. The spec must specify a persistence strategy: at minimum, `sessionStorage` for tab-level persistence; ideally `localStorage` (with appropriate security notes about XSS scope) so the JWT survives page reload.

If the decision is intentionally to keep it in-memory only, the spec must state this explicitly and explain the mitigation (e.g., the JWT lifetime is short enough that the UX cost is acceptable, or a "re-send link" flow is provided). No such rationale exists in the spec.

---

## MAJOR Issues

### M-1 — No rate limiting on `POST /api/v1/family/auth/verify`

**Section:** §3 "`POST /api/v1/family/auth/verify`"

The endpoint is public and accepts a token string. The token is 128 hex characters with 256 bits of entropy, making brute-force impractical but not impossible at high request volumes. More practically, an automated scanner can confirm whether a given token value exists (200 vs 404 response), leak the invite URL structure, and trigger `recordLogin()` calls. The spec lists no rate limiting, no IP throttling, and no lockout. This should be called out as a known gap with at least a note that a rate-limit annotation or filter is required (e.g., bucket4j or Spring's equivalent).

---

### M-2 — No cleanup/TTL enforcement for expired `FamilyPortalToken` rows

**Section:** §3 "`FamilyPortalToken`"

Expired tokens are never deleted by the verify endpoint (only used tokens are deleted). The spec includes no scheduled cleanup job. Over time, the `family_portal_tokens` table accumulates expired rows indefinitely. This is a data hygiene issue but also a minor PHI exposure risk: the table contains `fpUserId`, `clientId`, and `agencyId` for every invite ever generated, even expired ones. The spec should specify a cleanup job (e.g., a `@Scheduled` task deleting rows where `expiresAt < now`) or at minimum note this as a P2 item.

---

### M-3 — `lastVisitNote` exposes raw caregiver-authored free-text notes to family members with no redaction or consent mechanism

**Section:** §3 "`GET /api/v1/family/portal/dashboard`", §1 "Out of scope"

The dashboard response includes `Shift.notes` — a free-text field written by caregivers during or after a visit. The spec does not describe what kind of content caregivers enter in this field, but shift notes in home care contexts routinely contain clinical observations, medication administration records, ADL task completion notes, fall incidents, and other PHI beyond what a family member may have been consented to receive.

The spec places "plan version history or clinical details visible to family" out of scope, yet exposes `Shift.notes` without any filtering or content policy. There is no flag on `Shift` to mark a note as "family-visible", no agency-level toggle, and no consent record. This should either be scoped out entirely, or the spec must add a `visibleToFamily` boolean to shift notes (or an equivalent mechanism), and the dashboard query must filter on it. Shipping this without a content control is a HIPAA risk.

---

### M-4 — `FamilyPortalUser` unique constraint not defined; `findOrCreate` has a race condition

**Section:** §3 "`POST /api/v1/clients/{id}/family-portal-users/invite`", `FamilyPortalUser.java`

The spec says the invite endpoint "Finds or creates `FamilyPortalUser` for `(clientId, agencyId, email)`". `FamilyPortalUser.java` has no `@UniqueConstraint` on `(client_id, agency_id, email)`. A `findOrCreate` pattern without a unique constraint and without explicit locking will silently create duplicate rows under concurrent requests (two admins clicking "Generate Link" simultaneously for the same email). The Flyway migration must include a unique constraint on `(client_id, agency_id, email)`, and the service must handle the resulting `DataIntegrityViolationException` with a re-fetch (the "select-then-insert" race is a known JPA pitfall). Neither the migration requirement nor the exception handling is mentioned.

---

### M-5 — CORS configuration only permits `localhost:5173` — family portal at production URL will be blocked

**Section:** §2 "Architecture / Routing"
**Files:** `SecurityConfig.java` lines 60–64

`SecurityConfig.corsConfigurationSource()` hardcodes `allowedOrigins(List.of("http://localhost:5173"))`. The spec introduces a new consumer-facing surface at `https://app.hcare.io/portal/*`. Family members accessing this URL from production will have their browser CORS-block the `POST /api/v1/family/auth/verify` and `GET /api/v1/family/portal/dashboard` requests. This is not a new problem introduced by the portal — the existing admin app presumably has a solution for production — but the spec must either reference the production CORS configuration mechanism or explicitly state that `app.base-url` and allowed origins are configured together via environment variable. A developer reading only this spec would not know to update CORS for production.

---

## MINOR Issues

### m-1 — `GET /api/v1/family/portal/dashboard` returns `clientFirstName` only — last name dropped intentionally?

**Section:** §3 dashboard response shape

The response shape includes `"clientFirstName": "Margaret"` but no last name or full name. The page header reads "Margaret's Care". If the `Client` entity has a last name (expected), omitting it from the portal may be intentional for privacy, but the spec does not state this rationale. A developer may "helpfully" add `clientLastName` to the response. The spec should explicitly state that only the first name is returned and why.

---

### m-2 — Token in URL query parameter is logged by web servers, proxies, and analytics tools

**Section:** §2 "Token flow"

The invite URL is `https://app.hcare.io/portal/verify?token=<signed-token>`. Query parameters appear in server access logs, browser history, `Referer` headers on outbound links, and many analytics SDKs. While the 15-minute TTL limits the damage window, the spec should note that this is a known limitation of URL-based tokens and recommend that the verify page strip the `?token=` param from the URL bar immediately after reading it (using `history.replaceState` or React Router's `replace` navigation) to prevent history-based token reuse. The spec does not mention this.

---

### m-3 — No `FamilyPortalUser` deactivation / removal endpoint is specified for the backend

**Section:** §3 "New endpoints"

The `familyPortalTab` component includes a "Remove" action. The spec lists only three endpoints; none of them is a `DELETE /api/v1/clients/{id}/family-portal-users/{fpUserId}` or equivalent. The frontend "Remove" flow has no backing API endpoint. This is a straightforward omission but would block the feature being mergeable.

---

### m-4 — `PortalVerifyPage` shows identical error message for expired and for missing token (no `?token=`)

**Section:** §5 "PortalVerifyPage", §6 "Error Handling"

If a user navigates directly to `/portal/verify` with no query param, `?token=` is empty. The page auto-submits on mount, hits the backend with an empty/missing token value, and the spec says this shows "This link has expired or is invalid." That message is confusing for someone who accidentally navigated there. A missing token should ideally show a different message (e.g., "No link provided") or the page should detect the missing param before making any API call and branch immediately. This is minor UX but the spec conflates two distinct error states into one.

---

### m-5 — Testing section has no test covering the cross-client isolation boundary

**Section:** §7 "Testing"

`FamilyPortalDashboardControllerIT` is listed as testing "rejects wrong `clientId` JWT". This is the most important security test for the feature — a `FAMILY_PORTAL` JWT scoped to `clientId=A` must not return data for `clientId=B`, even if B belongs to the same agency. The test name suggests this is covered, but "wrong clientId JWT" is ambiguous: it could mean a JWT for a different client within the same agency (the critical cross-client test), or simply a malformed JWT. The spec should spell out both sub-cases explicitly: (1) JWT with `clientId` matching a different client in the same agency, (2) JWT with `clientId` from a different agency entirely. Only (1) tests the actual `clientId` enforcement logic; (2) tests the tenant filter.

---

## Summary Table

| ID | Severity | Area | One-line description |
|----|----------|------|----------------------|
| C-1 | CRITICAL | Backend / Auth | `JwtAuthenticationFilter` does not extract `clientId` from claims — UserPrincipal construction is broken for FAMILY_PORTAL tokens |
| C-2 | CRITICAL | Security | Shared signing key + no audience claim; portal JWT lifetime undefined (inherits admin expiry) |
| C-3 | CRITICAL | Security / Data | Raw token stored in DB (not hashed); no explicit index called out in Flyway migration |
| C-4 | CRITICAL | Frontend | In-memory Zustand store loses portal JWT on page reload; one-time-use magic link means permanent lockout |
| M-1 | MAJOR | Security | No rate limiting on public `/family/auth/verify` endpoint |
| M-2 | MAJOR | Data | No TTL cleanup job for expired `FamilyPortalToken` rows |
| M-3 | MAJOR | Privacy / HIPAA | Raw `Shift.notes` exposed to family with no content control or consent mechanism |
| M-4 | MAJOR | Data / Concurrency | No unique constraint on `FamilyPortalUser(clientId, agencyId, email)`; `findOrCreate` race condition |
| M-5 | MAJOR | Backend / CORS | Hardcoded `localhost:5173` CORS origin blocks production family portal requests |
| m-1 | MINOR | API design | `clientFirstName`-only response not justified; developer may add lastName without guidance |
| m-2 | MINOR | Security / UX | Token in URL query param is logged; spec should require immediate URL strip after read |
| m-3 | MINOR | API completeness | No `DELETE` endpoint for removing a `FamilyPortalUser`; "Remove" button has no backend |
| m-4 | MINOR | UX | Missing `?token=` param shows same error as expired token; two distinct states conflated |
| m-5 | MINOR | Testing | Cross-client isolation test described ambiguously; both same-agency and cross-agency cases must be explicit |
