# Critical Review 1 — Family Portal Backend Foundation (Part 1)

**Reviewed:** `2026-04-08-add-family-portal-plan-1-backend-foundation.md`
**Date:** 2026-04-08
**Reviewer:** Claude Sonnet 4.6 (automated critical review)

---

## 1. Overall Assessment

The plan is well-structured, follows a disciplined test-driven workflow, and correctly handles the most sensitive design concern (token hashing — raw token never persisted). The Flyway migration, entity design, and JWT extension are all directionally sound. However, several correctness and security issues need to be resolved before the plan is safe to execute: a misrouted config property means portal expiration will silently fall back to a Java-level default and never be configurable at runtime; the double-parse of the JWT bearer token in the updated filter introduces an avoidable race-and-replay window; and the `JwtAuthenticationFilter` silently swallows a missing `clientId` on `FAMILY_PORTAL` tokens, which causes a fail-open security bug. Two additional structural issues (orphaned `findByAgencyIdAndEmail` query and missing `@Sql` handling of PostgreSQL-incompatible `RESTART IDENTITY CASCADE`) also need attention.

---

## 2. Critical Issues

### C1. Config property mismatch — portal JWT expiration is unreachable

**Problem:** `application.yml` places the portal expiry under `hcare.portal.jwt.expiration-days`, but `JwtProperties` is bound to the prefix `hcare.jwt` and adds the field `portalExpirationDays` mapped to `hcare.jwt.portal-expiration-days`. These are different paths. Spring Boot will never bind `hcare.portal.jwt.expiration-days` into `JwtProperties`. The field will silently stay at the Java default of `30`, and the environment variable `PORTAL_JWT_EXPIRATION_DAYS` will have no effect.

**Why it matters:** Operators have no way to reduce token lifetime in production, and any audit/compliance process that relies on the configured value will observe the hardcoded default regardless of what is deployed.

**Fix:** Choose one canonical path and make them consistent. The cleanest option is to keep the YAML as written and add a separate `PortalProperties` class bound to `hcare.portal`:

```java
@Component
@ConfigurationProperties(prefix = "hcare.portal")
public class PortalProperties {
    private String baseUrl = "http://localhost:5173";
    private Jwt jwt = new Jwt();

    public static class Jwt {
        private int expirationDays = 30;
        // getters/setters
    }
    // getters/setters
}
```

`JwtTokenProvider` should then accept `PortalProperties` (or just `int portalExpirationDays`) as a second constructor argument. Alternatively, move the YAML path to `hcare.jwt.portal-expiration-days` to match `JwtProperties`, but that sacrifices logical grouping with other portal settings.

---

### C2. Double-parse of bearer token in JwtAuthenticationFilter is a fail-open security bug

**Problem:** In the updated `doFilterInternal`, after `claims` is already fully parsed and validated from `tokenProvider.parseAndValidate(token)`, the code calls `tokenProvider.getClientId(token)` for `FAMILY_PORTAL` tokens. `getClientId` calls `parseClaims(token)` internally — a second full HMAC verification and parse. More critically, if `clientId` is absent from a `FAMILY_PORTAL` token (malformed token, compromised key producing a minimal token), `clientIdStr` is `null`, and `clientId` stays `null`. The `UserPrincipal` is then built with a null `clientId` and **a valid authentication is set in the `SecurityContextHolder`**. Any downstream endpoint that reads `principal.getClientId()` and null-checks it will behave differently from any endpoint that assumes non-null — creating an inconsistent security boundary.

**Why it matters:** A `FAMILY_PORTAL`-scoped user with no `clientId` is a malformed principal that should never be authenticated. Accepting it silently means an attacker who can produce a valid `FAMILY_PORTAL`-signed token without a `clientId` claim could potentially bypass client-scoping checks.

**Fix:** Treat a missing `clientId` on a `FAMILY_PORTAL` token as invalid — reject the whole authentication attempt, log a warning, and do not populate the security context:

```java
if ("FAMILY_PORTAL".equals(role)) {
    String clientIdStr = claims.get("clientId", String.class); // read from already-parsed claims
    if (clientIdStr == null) {
        // Malformed portal token — fail closed, do not authenticate
        chain.doFilter(request, response);
        return;
    }
    clientId = UUID.fromString(clientIdStr);
}
```

Note also: read `clientId` from the already-available `claims` object (eliminating the second parse), consistent with how `agencyId` and `role` are read above it.

---

### C3. `findByAgencyIdAndEmail` becomes stale/misleading after the unique constraint fix

**Problem:** The V12 migration drops `uq_family_portal_users_agency_email` (the `(agency_id, email)` unique constraint) and replaces it with `uq_fpu_client_agency_email` on `(client_id, agency_id, email)`. The reason given in the migration comment is sound (same family member, two parents at the same agency). However, `FamilyPortalUserRepository` still exposes `findByAgencyIdAndEmail(UUID agencyId, String email)` — a method that will now return only the first result of potentially many rows with the same `(agencyId, email)` pairing. If Part 2 invite logic calls this method to check "does this person already have portal access at this agency?" it will silently pass the lookup even when a second client-specific record should also exist, or it will return the wrong record.

**Why it matters:** Silent wrong-record returns in an invite flow can grant a family member access to the wrong client's portal data.

**Fix:** Either remove `findByAgencyIdAndEmail` from the repository entirely (breaking the old usage and forcing callers to use the now-correct `findByClientIdAndAgencyIdAndEmail`), or rename it to `findAllByAgencyIdAndEmail` and return `List<FamilyPortalUser>` to make the multi-result semantics explicit. Add a Javadoc note explaining when to use each method.

---

## 3. Previously Addressed Items

None — this is the first review.

---

## 4. Minor Issues & Improvements

### M1. `@Sql` RESTART IDENTITY CASCADE is H2-only syntax

The integration test uses:
```sql
TRUNCATE TABLE family_portal_tokens, family_portal_users, clients, agency_users, agencies RESTART IDENTITY CASCADE
```
`RESTART IDENTITY` is PostgreSQL syntax and also happens to work on H2 in PostgreSQL-compatibility mode. If the H2 dev profile does not use `MODE=PostgreSQL`, this will fail. More importantly, it is good practice to use the plain `TRUNCATE ... CASCADE` form and rely on `@GeneratedValue(strategy = GenerationType.UUID)` (which does not use sequences) so restart-identity is irrelevant. Remove `RESTART IDENTITY` to keep the SQL unambiguous and portable between profiles.

### M2. `portalExpirationDays` config property name uses camelCase, not kebab-case

Spring Boot's relaxed binding converts `hcare.jwt.portal-expiration-days` → `portalExpirationDays` correctly. But the `JwtProperties` Java field is named `portalExpirationDays` and the expected YAML key shown in the plan is `expiration-days` under `hcare.portal.jwt`. Regardless of which binding path is chosen (see C1), verify the field naming aligns with Spring Boot relaxed binding rules to avoid surprises.

### M3. `FamilyPortalToken` is not agencyFilter-scoped — this is correct but should be documented

The plan's Javadoc on `FamilyPortalTokenRepository.deleteExpired` mentions the `agencyFilter` does not apply, which is correct. However, `FamilyPortalToken` is missing an explicit note in the class Javadoc that it is intentionally excluded from the Hibernate `agencyFilter`. Other developers may add `@Filter` annotations thinking it was forgotten. Add a class-level comment: `// Intentionally not agencyFilter-scoped: token lookup occurs pre-authentication by hash only.`

### M4. `AbstractIntegrationTest` changes in Task 2 are unnecessary

The plan adds `hcare.portal.jwt.expiration-days` and `hcare.portal.base-url` to `AbstractIntegrationTest.configureDataSource`. Since `application.yml` already provides defaults for both (`${PORTAL_JWT_EXPIRATION_DAYS:30}` and `${APP_BASE_URL:http://localhost:5173}`), these registry additions are redundant. They can be omitted unless a specific integration test requires overriding these values, which none do in this plan.

### M5. `expires_at` and `created_at` columns in DDL use bare `TIMESTAMP` (not `TIMESTAMPTZ`)

The Flyway DDL uses `TIMESTAMP` (without time zone) for `expires_at` and `created_at`. The Java entity uses `LocalDateTime` which maps to `TIMESTAMP` correctly, and UTC is enforced explicitly via `LocalDateTime.now(ZoneOffset.UTC)`. This is consistent with the rest of the schema (`V1__initial_schema.sql` likely follows the same convention). Acceptable as-is, but worth noting: if the database server ever runs in a non-UTC timezone, bare `TIMESTAMP` columns will store ambiguous wall-clock times. A one-line SQL comment (`-- All timestamps are UTC`) would make the intent explicit.

### M6. `JwtTokenProviderTest.getClientId_extractsClientIdClaim` asserts `String`, not `UUID`

The test calls `provider.getClientId(token)` and asserts `.isEqualTo(clientId.toString())`. This is consistent with the `getClientId` method returning `String`. This is fine, but consider whether `getClientId` should return `UUID` for API symmetry with `getUserId` and `getAgencyId`. Returning `String` forces every caller to manually parse and handle `NullPointerException` if the claim is absent (as seen in C2). Returning `Optional<UUID>` would make the optional nature explicit.

### M7. No test for invalid/expired token path in `JwtAuthenticationFilterTest`

The filter test covers the happy path for both admin and portal tokens but has no test for: (a) an expired token, (b) a tampered token, or (c) a missing Authorization header. These paths are critical for ensuring the filter fails closed. At minimum, add a test that a request with no Authorization header results in no authentication being set in the `SecurityContextHolder`.

---

## 5. Questions for Clarification

**Q1.** The plan routes `POST /api/v1/family/auth/verify` as `permitAll`. Is this the only unauthenticated portal endpoint, or will `GET /api/v1/family/auth/invite/{token}` (token redemption page redirect) also need to be permitted? Resolving this in Part 1 (SecurityConfig) rather than in Part 2 avoids a second SecurityConfig modification mid-implementation.

**Q2.** The `FamilyPortalToken.deleteExpired` job is referenced in a Javadoc comment as `FamilyPortalTokenCleanupJob`, but this job does not appear in the plan's File Map for any part. Is this job scoped to Part 2 or Part 3, and what is the intended trigger (nightly `@Scheduled` or a startup hook)?

**Q3.** `JwtTokenProvider` uses a single signing key for both admin and portal tokens. This means a compromised admin-tier signing key invalidates all portal sessions and vice versa. For the current scope (single-tenant signing key) this is acceptable, but is there a design decision recorded about whether portal tokens should use a separate secret? This is particularly relevant because portal tokens have a 30-day TTL vs. 24-hour admin TTL — a leaked key has a much longer blast radius for portal sessions.

**Q4.** The `Agency.timezone` field defaults to `America/New_York` in both the migration and the Java entity. Is there a plan to validate this is a valid `ZoneId` before persisting? An invalid timezone string would cause silent runtime failures when portal endpoints attempt to format dates for display.

---

## 6. Final Recommendation

**Major revisions needed** before execution.

Issue C1 (config property mismatch) means the portal token expiration will never be externally configurable. Issue C2 (fail-open null clientId on FAMILY_PORTAL tokens) is a security correctness bug that must be fixed before any portal endpoint is exposed. Issue C3 (stale `findByAgencyIdAndEmail`) creates a wrong-data risk in the invite flow. All three can be fixed with small, targeted changes. Once those three are resolved, the plan is otherwise solid and ready to execute.
