# Critical Review 3 — Family Portal Backend Foundation (Part 1)

**Reviewed:** `2026-04-08-add-family-portal-plan-1-backend-foundation.md`
**Date:** 2026-04-08
**Reviewer:** Claude Sonnet 4.6 (automated critical review)
**Prior reviews:** Critical Review 1, Critical Review 2

---

## 1. Overall Assessment

The plan has been thoroughly improved across two prior review passes. All five critical issues identified in Reviews 1 and 2 (C1–C5) are addressed in the current plan text, and the two-constructor `JwtTokenProvider`, fail-closed `JwtAuthenticationFilter`, `PortalProperties` binding, and null-claim guards are all correct as written. This pass focuses on a new set of issues discovered by cross-referencing the plan against the actual codebase files — specifically, the existing `JwtTokenProviderTest.java` that the plan silently overwrites, the two-arg constructor change breaking an existing test that the plan does not address, and a correctness gap in `SecurityConfig`'s CORS allow-list that will block the family portal frontend in every non-dev environment.

---

## 2. Critical Issues

### C6. Plan silently overwrites `JwtTokenProviderTest.java`, destroying 9 existing passing tests

**Problem:** The existing `backend/src/test/java/com/hcare/security/JwtTokenProviderTest.java` contains 9 tests covering the core admin JWT path (generateToken, validateToken, tampered-token, expired-token, getUserId, getAgencyId, getRole, parseAndValidate happy-path, parseAndValidate invalid-token). The plan instructs Task 5 Step 1 to **create** `JwtTokenProviderTest.java` with 3 new tests. Because the file already exists, executing this step as written will overwrite the file and permanently destroy all 9 existing tests. None of the 9 existing tests appear in the 3-test replacement.

Additionally, the existing test at line 49 calls `new JwtTokenProvider(shortProps)` (single-arg constructor) to test an expired token. When Task 5 Step 4 changes the constructor signature to `JwtTokenProvider(JwtProperties, PortalProperties)`, this call will fail to compile. The plan provides no migration path for the existing test.

**Why it matters:** Destroying existing passing tests is a direct violation of the project's "never commit with failing tests" standard. The full backend test suite run in Task 7 will detect the compilation failure, but by that point multiple commits will have been made that collectively break the test baseline. The plan also loses coverage of the tampered-token and expired-token paths (currently covered, absent from the replacement).

**Fix:** In Task 5, do not create a new `JwtTokenProviderTest.java` — instead, **add** the three new portal token tests to the existing file. Also update the single-arg `new JwtTokenProvider(shortProps)` call (line 49 of the existing file) to include a `PortalProperties` second argument:

```java
// existing expired-token test — update to use two-arg constructor:
JwtTokenProvider shortProvider = new JwtTokenProvider(shortProps, portalProps);
```

The `@BeforeEach setUp()` block in the existing file should also be updated to use the two-arg constructor. The three new tests from the plan (`generateFamilyPortalToken_claimsContainRoleAndClientId`, `getClientId_extractsClientIdClaim`, `adminToken_doesNotHaveClientIdClaim`) can be appended to the existing class with no structural conflict.

---

### C7. `SecurityConfig` CORS `allowedOrigins` hard-codes `localhost:5173` — family portal origin not permitted

**Problem:** `SecurityConfig.corsConfigurationSource()` currently sets:
```java
config.setAllowedOrigins(List.of("http://localhost:5173"));
```
The family portal frontend may run on a different origin in staging and production (e.g., `https://portal.hcare.app` vs `https://app.hcare.app`). The plan's Task 6 Step 5 updates `filterChain` to add the `permitAll` matcher for `POST /api/v1/family/auth/verify` but does not touch `corsConfigurationSource`. If the portal frontend is served from a different domain than the admin frontend, every pre-flight CORS request to `/api/v1/family/**` will be rejected with a CORS error in staging/production, even though the endpoint is `permitAll`. The H2 dev environment will not surface this because the portal will also run on `localhost:5173`.

**Why it matters:** CORS failures are a silent runtime blocker — the response reaches the browser but is rejected without a 4xx status visible to the API layer. This is particularly problematic for portal token verification (`POST /api/v1/family/auth/verify`), which must work from the portal's domain. Discovering this in production requires debugging browser network tabs, not server logs.

**Fix:** At minimum, add a `hcare.portal.base-url` reference to `corsConfigurationSource()` so the portal origin is permitted when it differs from the admin origin. The simplest approach uses the already-planned `PortalProperties.baseUrl`:

```java
// In SecurityConfig (inject PortalProperties alongside JwtTokenProvider):
config.setAllowedOrigins(List.of(
    "http://localhost:5173",
    portalProperties.getBaseUrl()
));
```

Alternatively, externalise both origins via a config list property. Either way, the fix belongs in this plan's Task 6 Step 5, not deferred to a later part.

---

## 3. Previously Addressed Items

The following issues from Reviews 1 and 2 are fully resolved in the current plan:

- **C1 (Config property mismatch / portal JWT expiration unreachable):** Resolved. `PortalProperties` class bound to `hcare.portal` is introduced; `JwtTokenProvider` accepts it as a second constructor argument.
- **C2 (Fail-open null clientId on FAMILY_PORTAL tokens):** Resolved. Filter reads `clientId` from already-parsed `claims` object; null check triggers early return without populating the security context; test `familyPortalToken_missingClientIdClaim_failsClosed` verifies the behaviour.
- **C3 (Stale `findByAgencyIdAndEmail`):** Resolved. `FamilyPortalUserRepository` omits the method entirely and provides only `findByClientIdAndAgencyIdAndEmail`, with an explanatory comment.
- **C4 (V12 migration drop/add constraint atomicity risk):** Resolved with a pre-condition comment and verification query in the migration; risk is acknowledged and documented.
- **C5 (NPE on missing agencyId/sub/role claims in JwtAuthenticationFilter):** Resolved. The updated filter null-checks all three required claims before `UUID.fromString` and returns early with a warn log if any are absent. The fourth filter test (`validlySignedToken_missingAgencyIdClaim_noAuthenticationSet`) verifies this path.

---

## 4. Minor Issues & Improvements

### M1. `getClientId` method on `JwtTokenProvider` is still present and still dead code (carried from Review 2 M2)

Review 2 raised this as M2. The method is still in the plan with a Javadoc note saying it "can be removed." It remains a public method on a security-critical class with no callers in any of the three plan parts. The plan should either remove it or mark it `@Deprecated` with `@SuppressWarnings("unused")`. Deferring removal until "a future task requires it" means it will accumulate callers that trigger the second HMAC parse it was supposed to eliminate.

### M2. `RESTART IDENTITY CASCADE` in `@Sql` still not fixed (carried from Review 1 M1, Review 2 M3)

`FamilyPortalTokenDomainIT` Task 4 Step 1 still uses `TRUNCATE ... RESTART IDENTITY CASCADE`. This has been flagged in both prior reviews. Since all IDs use `GenerationType.UUID` (not sequences), `RESTART IDENTITY` is semantically a no-op and adds only portability risk. Remove the clause:

```sql
TRUNCATE TABLE family_portal_tokens, family_portal_users, clients, agency_users, agencies CASCADE
```

### M3. `Agency.timezone` has no `ZoneId` validation — still unresolved (carried from Review 2 M7)

Review 2 flagged this as M7 (carried from Review 1 Q4). The plan still provides no validation that `timezone` is a valid IANA zone ID before persisting. A `@PrePersist`/`@PreUpdate` lifecycle callback calling `ZoneId.of(this.timezone)` and catching `ZoneRulesException` would prevent silent bad data. This is particularly important before Part 2 endpoints use the field to format displayed dates for family portal users.

### M4. `AbstractIntegrationTest` registry additions for portal properties are still present (carried from Review 1 M4, Review 2 M6)

Task 2 Step 2 still adds `hcare.portal.jwt.expiration-days` and `hcare.portal.base-url` to `AbstractIntegrationTest.configureDataSource`, despite both values having defaults in `application.yml`. These are harmless but add noise. Remove them.

### M5. `JwtAuthenticationFilterTest` still has no test for a missing `Authorization` header (carried from Review 1 M7, Review 2 M5)

The filter correctly calls `chain.doFilter` when no token is present (the outer `if (StringUtils.hasText(token))` guard handles it). A single explicit test confirming that a request with no `Authorization` header results in no authentication in the `SecurityContextHolder` and still calls `chain.doFilter` would close this gap with five lines of code.

### M6. `validateToken_returnsFalseForExpiredToken` test uses `Thread.sleep(10)` — fragile in CI

This is a pre-existing test (not introduced by this plan), but the constructor change in Task 5 requires touching the existing test anyway (see C6). While making those edits, replace the `Thread.sleep(10)` approach with an expiry set to `Instant.now().minusMillis(1000)` via a custom `Clock` or by using a fixed-past `Date` in `generateToken`. The `sleep` approach is inherently flaky under heavy CI load. This is a low-priority improvement that should be applied as part of the constructor migration work to avoid a separate future commit.

---

## 5. Questions for Clarification

**Q1.** Review 2 Q3 remains unanswered: portal tokens share the admin JWT signing key. With a 30-day TTL versus 24-hour admin TTL, a key rotation forces all portal sessions to expire immediately (30-day sessions are not re-issued automatically because the invite token was one-time use). Is there a plan for graceful key rotation without forcing family members to re-request portal invites? This should be a recorded design decision before Part 2 exposes the token issuance endpoint.

**Q2.** The plan adds `POST /api/v1/family/auth/verify` to `permitAll` in `SecurityConfig`. Is `POST /api/v1/family/auth/invite` (the endpoint that issues invite tokens, part of Part 2) intended to be authenticated (ADMIN/SCHEDULER only)? If so, no change is needed, but if the plan intends an unauthenticated invite flow (family member self-registers), a second `permitAll` matcher will be needed. Clarifying this now avoids another `SecurityConfig` modification in Part 2.

**Q3.** The plan's `FamilyPortalToken.tokenHash` column is `VARCHAR(64)` — exactly the length of a hex-encoded SHA-256 digest. The domain IT test in Task 4 seeds `"abc123def456"` (12 characters) and `"hash-expired"` / `"hash-valid"` as token hashes. These are not valid SHA-256 hashes. While the entity does not enforce hash format, the test's use of short arbitrary strings as hashes obscures the contract. Should the test use a properly-formed 64-character hex string to make the column size constraint visibly exercised?

---

## 6. Final Recommendation

**Approve with changes** — the two critical issues (C6, C7) are targeted and fixable:

- C6 (silent overwrite of `JwtTokenProviderTest.java`) requires modifying Task 5 to add tests to the existing file rather than replacing it, and updating the single-arg constructor calls in the existing test.
- C7 (CORS origin gap for the portal frontend) requires a two-line addition to `SecurityConfig.corsConfigurationSource()` in Task 6.

Neither requires restructuring the plan. The three minor carryovers from prior reviews (M2 `RESTART IDENTITY`, M3 timezone validation, M4 redundant registry additions) should also be resolved in this pass rather than deferred again. Once C6 and C7 are addressed, the plan is ready to execute.
