# Critical Review 2: Family Portal Design Spec (v2)
**Spec reviewed:** `2026-04-08-family-portal-design.md` (v2 — "revised after UX review and critical design review")
**Prior review:** `2026-04-08-family-portal-design-critical-review-1.md`
**Reviewer:** Critical design review agent
**Date:** 2026-04-08

---

## V1 Critical Resolution Assessment

Before listing new findings, each v1 critical is assessed against the v2 spec.

### C-1 — Filter NPE on FAMILY_PORTAL JWT
**Status: ADDRESSED — with a residual gap (see NC-1 below)**

The v2 spec now explicitly documents the filter change (the pseudocode block in §2 "Auth separation"), a new `getClientId()` method on `JwtTokenProvider`, and a four-arg `UserPrincipal` constructor. The primary criticism is resolved. However, the implementation is still incomplete in one important way — see NC-1.

### C-2 — Shared signing key / undefined expiry
**Status: PARTIALLY ADDRESSED — expiry gap closed, signing key gap not addressed**

The spec now introduces `portal.jwt.expiration-days` (default: 30 days) as a separate config property, explicitly different from `jwt.expiration-ms`. This resolves the undefined-expiry portion of C-2.

The spec still uses a single `signingKey` derived from the same `JwtProperties` secret. There is no `aud` or `iss` claim added to portal tokens. The v1 review flagged this as the more dangerous gap. The v2 spec says nothing about it. See NC-2.

### C-3 — Raw token stored in DB / no explicit index
**Status: FULLY ADDRESSED**

The v2 spec now stores `SHA-256(rawToken)` as `tokenHash VARCHAR(64)` with a `UNIQUE` constraint, explicitly notes the Flyway migration must create the index, and states the raw token is never persisted ("raw token is NEVER persisted — only the hash is stored"). This was the primary security concern in C-3 and is fully resolved.

### C-4 — In-memory Zustand store loses JWT on page reload
**Status: FULLY ADDRESSED**

The v2 spec explicitly introduces Zustand `persist` middleware with `localStorage` key `portal-auth`, and the `PortalLayout` logout flow calls both `portalAuthStore` clear and `localStorage` removal. This resolves C-4.

---

## V1 Major Resolution Assessment

**M-1 (rate limiting):** Not addressed. Still absent in v2. Remains a gap — see below.

**M-2 (TTL cleanup job):** Addressed. v2 §3 explicitly specifies a nightly `@Scheduled` cleanup job for rows where `expiresAt < now`.

**M-3 (raw shift notes to family):** Addressed. v2 uses the field name `noteText` and §3 field notes state "noteText is null if the caregiver entered no notes." The v2 response shape does not expose ADL task completions, diagnoses, or medication records — only the note text. This is still a PHI concern (free-text caregiver notes are clinical content) but the scope is now at least explicitly defined. Not re-raised as critical.

**M-4 (unique constraint + race condition):** Addressed. v2 explicitly adds `UNIQUE` constraint on `FamilyPortalUser(client_id, agency_id, email)` and the error handling table specifies a 409 response with frontend retry.

**M-5 (CORS blocks production):** Not addressed. Still absent. Still a gap — see below.

---

## V1 Minor Resolution Assessment

**m-3 (no DELETE endpoint):** Fully addressed. `DELETE /api/v1/clients/{id}/family-portal-users/{fpuId}` is now listed in the endpoint table and §3.

**m-1, m-2, m-4, m-5:** Not explicitly addressed by v2. Most are still present — assessed below.

---

## Remaining and New Issues

---

### C-1 (NEW) — `JwtAuthenticationFilter` will fail to parse `FAMILY_PORTAL` JWT because `agencyId` claim is extracted unconditionally — NullPointerException on any token without it

**Severity: CRITICAL**
**Section:** §2 "JWT claims for family portal", §2 "Auth separation" (pseudocode block)

The v2 spec provides this pseudocode for the filter update:

```java
UUID clientId = null;
if ("FAMILY_PORTAL".equals(role)) {
    String clientIdStr = jwtTokenProvider.getClientId(token);
    clientId = UUID.fromString(clientIdStr);
}
UserPrincipal principal = new UserPrincipal(userId, agencyId, role, clientId);
```

The problem is the order of operations. In the existing `JwtAuthenticationFilter.doFilterInternal()` (lines 31–35), `agencyId` is extracted from the `claims` object with `UUID.fromString(claims.get("agencyId", String.class))`. This is called **before** the role is checked, because the current filter constructs `UserPrincipal` in a single statement.

If the spec intends the filter to first extract `role`, then conditionally extract `clientId`, the developer must also be told to extract `agencyId` conditionally (or gracefully handle null) for `FAMILY_PORTAL` tokens. Looking at the actual `JwtAuthenticationFilter.java`:

```java
UserPrincipal principal = new UserPrincipal(
    UUID.fromString(claims.getSubject()),
    UUID.fromString(claims.get("agencyId", String.class)),   // ← unconditional
    claims.get("role", String.class)
);
```

The spec's proposed `generateFamilyPortalToken` **does** include `agencyId` as a claim — so this will not NPE on FAMILY_PORTAL tokens. However, the spec's pseudocode reads as though `agencyId` extraction is unchanged and only `clientId` is added conditionally. The spec must make explicit that `agencyId` is still present in FAMILY_PORTAL tokens (the claim is added in `generateFamilyPortalToken`) to prevent a developer from misreading the pseudocode and removing the unconditional `agencyId` extraction — which would break admin tokens.

More critically: the pseudocode does not show where the `role` value is read. In the current filter, `role` is read inside the `UserPrincipal` constructor call. But in the proposed pseudocode, `role` is used before the `UserPrincipal` is constructed. The spec must explicitly state that `role` must be extracted from `claims` first, before the conditional check, and cannot be extracted by calling `jwtTokenProvider.getRole(token)` (which would re-parse the token a second time with a second HMAC verification — a performance regression). The developer should read `role` from the already-parsed `Claims` object. This is a specification gap that will produce incorrect code in practice.

---

### C-2 (CARRIED) — Single signing key; no audience separation between admin and FAMILY_PORTAL tokens

**Severity: CRITICAL**
**Section:** §2 "JWT claims for family portal", §2 "Security config"

This was partially flagged in v1 C-2 and was not addressed in v2. It is re-raised as a standalone critical.

`JwtTokenProvider` has one `signingKey` derived from `JwtProperties.getSecret()`. Both `generateToken()` (admin/scheduler) and the proposed `generateFamilyPortalToken()` will sign with the same key. The only thing preventing a `FAMILY_PORTAL` JWT from being accepted on admin endpoints is the `role` claim value.

This means: if a `FAMILY_PORTAL` token were issued with `role: "ADMIN"` by a bug or misconfiguration, it would be accepted on all admin endpoints — there is no cryptographic separation. More practically: the spec never states that `SecurityConfig.filterChain()` must be updated to restrict `/api/v1/family/**` endpoints to tokens where `role == FAMILY_PORTAL` at the HTTP security layer (not just via `@PreAuthorize`). The current `anyRequest().authenticated()` catch-all means a valid admin JWT presented to `GET /api/v1/family/portal/dashboard` would pass the filter (the filter does not check role, only validity) and only be rejected if `@PreAuthorize("hasRole('FAMILY_PORTAL')")` is annotated on the controller method. The spec never states this annotation is required.

Concrete gap: the spec says `/api/v1/family/portal/dashboard` "requires `FAMILY_PORTAL` role" but does not specify whether this is enforced via `SecurityConfig.authorizeHttpRequests()` or `@PreAuthorize` on the controller. If neither is done — and the spec gives the developer no guidance — an admin JWT will be accepted on the dashboard endpoint, returning another agency's client data if agencyId matches. The `JwtAuthenticationFilterTest` requirement does test that "admin JWT leaves `clientId` null" but does not test that the dashboard endpoint rejects an admin JWT with `clientId == null` rather than NPEing.

Recommendation: Add `aud` claims to both token types, validate `aud` in the filter based on path prefix, OR explicitly require `@PreAuthorize("hasRole('FAMILY_PORTAL')")` on the dashboard controller method and add an explicit integration test that presents an admin JWT to the dashboard endpoint and expects 403.

---

### M-1 (CARRIED) — No rate limiting on `POST /api/v1/family/auth/verify`

**Severity: MAJOR**
**Section:** §3 "`POST /api/v1/family/auth/verify`"

Unchanged from v1 M-1. The endpoint is public, unauthenticated, and accepts a token string. No rate limiting, IP throttling, or lockout is mentioned anywhere in the v2 spec. The existing `SecurityConfig` has no rate-limiting bean or annotation. With 256 bits of entropy the brute-force risk is theoretical, but automated scanners can still harvest the token/expiry structure, trigger `recordLogin()` on any guessed account, and enumerate whether emails have been invited (a 400 vs 404 response may be distinguishable if error codes differ between "token not found" and "malformed input"). This is a known OWASP API8 concern (security misconfiguration / missing rate limiting) and was not addressed in v2. At minimum the spec must note this is a known gap and specify that a rate-limit filter or bucket4j annotation is required before production deployment.

---

### M-2 (NEW) — `portalAuthStore` persist middleware requires `zustand/middleware` — not listed as a dependency; existing `authStore.ts` does not use it

**Severity: MAJOR**
**Section:** §2 "Auth separation", §4 "New files"

The v2 spec introduces `portalAuthStore.ts` with Zustand's `persist` middleware. Looking at the existing `authStore.ts`, it uses plain `create<AuthState>()` with no persist wrapper — there is no existing usage of `zustand/middleware` in the frontend codebase. `persist` is bundled with `zustand` (no separate install), but the spec must confirm:

1. The Zustand version in `package.json` supports the `persist` middleware API being used (the v4 API changed significantly from v3).
2. The `persist` middleware wraps the `create` call differently — the type signature changes in ways that catch developers who copy from the plain `authStore.ts` pattern. The spec should include a minimal code sketch of the `portalAuthStore` structure, not just say "Zustand store with `persist` middleware."
3. The `partialize` option should be specified: the store likely wants to persist `{ token, clientId, agencyId }` but not any transient UI state. If `partialize` is omitted, the entire store state is serialized to localStorage, which may include future additions unintentionally.

Without guidance, a developer copying `authStore.ts` and adding `persist` will likely write incorrect TypeScript because the `persist` wrapper changes the `create` call shape and requires `StateStorage` typing.

---

### M-3 (NEW) — `DELETE /api/v1/clients/{id}/family-portal-users/{fpuId}` does not cascade-delete `FamilyPortalToken` rows for that user

**Severity: MAJOR**
**Section:** §3 "`DELETE /api/v1/clients/{id}/family-portal-users/{fpuId}`"

The DELETE endpoint "Deletes the `FamilyPortalUser` row." The spec says nothing about what happens to any outstanding `FamilyPortalToken` rows linked to that `fpUserId`. If an admin generates an invite link, then immediately removes the user before the link is used, the `FamilyPortalToken` row still exists with `fpUserId` pointing to a deleted row. Depending on the FK definition:

- If `FamilyPortalToken.fpUserId` has `ON DELETE CASCADE`, the token row is removed automatically — but the spec does not define this constraint on the entity.
- If `FamilyPortalToken.fpUserId` has `ON DELETE RESTRICT` (the default), the DELETE endpoint will throw a `DataIntegrityViolationException` and return a 500 error.
- If there is no FK constraint at all (also undefined by the spec), the token row becomes an orphan referencing a non-existent user — the next verify call will find the token by hash, attempt to load `FamilyPortalUser` by `fpUserId`, get null, and NPE or return a misleading error.

The spec must define the FK constraint behavior on `FamilyPortalToken.fpUserId` explicitly.

---

### M-4 (NEW) — 30-day JWT with no revocation mechanism means removing a `FamilyPortalUser` does not immediately revoke portal access

**Severity: MAJOR**
**Section:** §2 "Auth separation", §3 "`DELETE /api/v1/clients/{id}/family-portal-users/{fpuId}`", §5 visual design ("Remove [name]? They will lose access immediately.")

The visual design spec explicitly states that the "Remove" confirmation dialog says **"They will lose access immediately."** This is factually incorrect as specified.

The `DELETE` endpoint deletes the `FamilyPortalUser` row. The portal JWT is a stateless bearer token signed with HMAC. There is no token blacklist, no session table, and no revocation endpoint. A family member who has already authenticated holds a 30-day JWT. After the admin removes them:

- Their `FamilyPortalUser` row is gone.
- Their JWT is still cryptographically valid for up to 30 days.
- `GET /api/v1/family/portal/dashboard` validates the JWT signature and reads `clientId` from the claim. If the endpoint only validates the JWT and never checks whether the `FamilyPortalUser` still exists, the removed user retains full dashboard access until token expiry.

The spec must either:
1. Change the UI copy to something accurate (e.g., "They will lose access within [expiry period]"), or
2. Add a `revokedAt` field to `FamilyPortalUser` (soft-delete instead of hard-delete), and require the dashboard endpoint to validate that the `FamilyPortalUser` is not revoked on every request — adding a DB lookup to the auth path, or
3. Introduce a token blacklist / session table.

Option 1 is cheapest. Options 2 or 3 are correct if "immediate revocation" is a real product requirement. The spec cannot simply ignore this: the UI copy promises immediate revocation, which is a lie given the architecture.

---

### M-5 (CARRIED) — CORS is hardcoded to `localhost:5173` in `SecurityConfig`; production family portal requests will be blocked

**Severity: MAJOR**
**Section:** §2 "Architecture / Routing", §2 "Security config"
**File:** `backend/src/main/java/com/hcare/config/SecurityConfig.java` line 61

Confirmed from reading the actual source. `corsConfigurationSource()` calls `config.setAllowedOrigins(List.of("http://localhost:5173"))`. This was M-5 in v1 and is unchanged in v2. Family members accessing `https://app.hcare.io/portal/verify` from production will receive CORS errors from the backend on `POST /api/v1/family/auth/verify` and `GET /api/v1/family/portal/dashboard`.

The spec must require updating the `corsConfigurationSource()` to read allowed origins from a config property (e.g., `app.cors.allowed-origins`) instead of a hardcoded literal, and document the required environment variable. This is not a "nice to have" — the feature is non-functional in production without it.

---

### m-1 (CARRIED) — Token in URL query parameter is not stripped from browser history

**Severity: MINOR**
**Section:** §2 "Token flow", §4 "`PortalVerifyPage.tsx`"

The raw 64-byte token appears in `?token=<hex>` in the URL bar. The spec does not require `history.replaceState` (or React Router's `replace: true` navigation) to strip the token from the URL after reading it. With a 72-hour expiry, the token remains valid for a meaningful window. Any browser history sync (Chrome Sync, iCloud), URL logged in network proxies, or `Referer` header on external links from the verify page would expose a usable token. The spec should explicitly require stripping the token from the URL immediately after reading it on mount — this is a one-line fix (`navigate('/portal/verify', { replace: true })` after extracting the query param).

---

### m-2 (NEW) — `PortalVerifyPage` auto-submits on mount with no guard against double-submission

**Severity: MINOR**
**Section:** §4 "`PortalVerifyPage.tsx`", §7 "Frontend testing"

The spec says the page "auto-submits" `POST /api/v1/family/auth/verify` on mount. React 18 strict mode mounts components twice in development. If the effect is not guarded with a `useRef` or an abort signal, the token is consumed on the first request and the second request returns `400 TOKEN_INVALID` — in dev mode only, but this will cause confusing failures during development and testing. The spec should note that the submit effect must be guarded against double-fire (e.g., `useEffect` with an `isMounted` ref or `AbortController`).

---

### m-3 (CARRIED) — Cross-client isolation test cases are ambiguous

**Severity: MINOR**
**Section:** §7 "`FamilyPortalDashboardControllerIT`"

V1 m-5 flagged this. The v2 spec has `"rejects JWT with wrong clientId (403)"` — still ambiguous. It does not distinguish between (a) a JWT for a different client within the same agency and (b) a JWT for a client in a different agency. Case (a) is the critical test: it proves the `clientId` claim is the enforcement boundary, not just the `agencyId` tenant filter. Case (b) tests the multi-tenancy layer. Both are required. The spec should list them as two separate sub-cases: `FamilyPortalDashboardControllerIT.rejectsJwtForDifferentClientSameAgency` and `FamilyPortalDashboardControllerIT.rejectsJwtForDifferentAgency`.

---

### m-4 (NEW) — `PortalGuard` client-side JWT expiry check uses `exp` claim from untrusted local parse — clock skew not addressed

**Severity: MINOR**
**Section:** §2 "Auth separation", `PortalGuard.tsx`

The spec says `PortalGuard` "detects [expiry] by parsing the `exp` claim client-side before any API call." This is client-side JWT parsing — decoding the base64 payload without signature verification. The check is: is the current time past `exp`? Two issues:

1. **Clock skew:** if the client's device clock is behind the server's, a token that expired server-side may still appear valid client-side. The guard would allow the request through, which would then fail with 401 from the server. The spec should require that `PortalGuard` — and the `usePortalDashboard` hook — handle a 401 response from the server by also redirecting to `/portal/verify?reason=session_expired`, not just relying on the client-side check.

2. **No mention of the decode library:** parsing a JWT payload client-side requires either a library (e.g., `jwt-decode`) or a manual base64 decode. Neither is mentioned. If `jwt-decode` is not already in the frontend's `package.json`, it needs to be added — which the spec should note.

---

### m-5 (NEW) — `inviteUrl` is built from `app.base-url` config, but the spec does not define this property or its default for non-dev environments

**Severity: MINOR**
**Section:** §3 "`POST /api/v1/clients/{id}/family-portal-users/invite`"

The spec says the backend builds `inviteUrl` from `app.base-url` (dev default: `http://localhost:5173`). This property is not listed in CLAUDE.md's "Key Environment Variables" section, not referenced in any existing backend config, and has no production documentation. An operator deploying to staging or production must know to set this property or invite URLs will point to `localhost`. The spec must add `APP_BASE_URL` (or `app.base-url`) to the key environment variables table in the implementation notes, and the `application.properties` / `application-dev.properties` files must be updated to include the property.

---

## Summary Table

| ID | Severity | Area | Description |
|----|----------|------|-------------|
| C-1 | CRITICAL | Backend / Auth | Filter pseudocode is ambiguous on role-extraction order; `agencyId` claim presence in FAMILY_PORTAL tokens not made explicit; double token parse risk |
| C-2 | CRITICAL | Security | Single signing key with no `aud` claim; spec does not require `@PreAuthorize` on dashboard controller; admin JWT accepted on family endpoint unless annotation is added |
| M-1 | MAJOR | Security | No rate limiting on public `POST /family/auth/verify` — not addressed in v2 |
| M-2 | MAJOR | Frontend | `portalAuthStore` persist middleware usage undocumented; type shape, `partialize`, and Zustand version compatibility not specified |
| M-3 | MAJOR | Data / DB | `DELETE /family-portal-users/{fpuId}` does not address cascade-delete of `FamilyPortalToken` rows; FK behavior undefined — likely 500 in practice |
| M-4 | MAJOR | Security / UX | UI copy says "lose access immediately" but 30-day stateless JWT has no revocation; removed users retain dashboard access until expiry |
| M-5 | MAJOR | Backend / CORS | Hardcoded `localhost:5173` CORS origin blocks production family portal — not addressed in v2 |
| m-1 | MINOR | Security / UX | Token not stripped from URL after reading; lives in browser history / proxy logs for 72 hours |
| m-2 | MINOR | Frontend | `useEffect` auto-submit fires twice in React 18 strict mode; token consumed on first call, second returns 400 |
| m-3 | MINOR | Testing | Cross-client isolation test still ambiguous; same-agency vs. cross-agency sub-cases not split |
| m-4 | MINOR | Frontend | Client-side `exp` check ignores clock skew; 401 fallback path not specified; decode library not named |
| m-5 | MINOR | Ops / Config | `app.base-url` property not in environment variables documentation; missing from `application.properties` |
