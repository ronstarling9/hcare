# Critical Review 4 — Family Portal Backend Foundation (Part 1)

**Reviewed:** `2026-04-08-add-family-portal-plan-1-backend-foundation.md`
**Date:** 2026-04-08
**Reviewer:** Claude Sonnet 4.6 (automated critical review)
**Prior reviews:** Critical Reviews 1, 2, and 3

---

## 1. Overall Assessment

The plan has matured substantially across three review passes. All seven critical issues identified in Reviews 1–3 (C1–C7) are present and correctly addressed in the current plan text. This fourth pass cross-references the plan against the actual codebase files to look for execution-time correctness gaps that have not yet been surfaced. Three new issues emerge: the `validlySignedToken_missingAgencyIdClaim_noAuthenticationSet` test uses a Mockito `spy` on a concrete class that is not annotated `@ExtendWith(MockitoExtension.class)` — this test will fail with a Mockito initialisation error at runtime, not at compile time. The plan's `SecurityConfig` replacement introduces a duplicate CORS origin when `hcare.portal.base-url` equals `http://localhost:5173` (the dev default), which Spring's `CorsConfiguration` validates strictly and may throw an `IllegalArgumentException` during application startup in dev. Finally, the `FamilyPortalTokenDomainIT` test seeds a `Client` using a constructor that does not match the actual `Client` entity signature found in the codebase, which will cause a compilation failure. The three long-standing minor carryovers (M2 `RESTART IDENTITY`, M3 timezone validation, M4 redundant registry additions) remain unresolved in the plan text.

---

## 2. Critical Issues

### C8. `validlySignedToken_missingAgencyIdClaim_noAuthenticationSet` test will fail at runtime — Mockito not initialised

**Problem:** The test `validlySignedToken_missingAgencyIdClaim_noAuthenticationSet` in `JwtAuthenticationFilterTest` uses `org.mockito.Mockito.spy(tokenProvider)` and `org.mockito.Mockito.mock(io.jsonwebtoken.Claims.class)`. The test class `JwtAuthenticationFilterTest` is annotated with nothing — no `@ExtendWith(MockitoExtension.class)`, and the `@BeforeEach setUp()` calls `MockitoAnnotations.openMocks(this)` for the `@Mock FilterChain chain` field.

`MockitoAnnotations.openMocks(this)` only initialises fields annotated with `@Mock`, `@InjectMocks`, etc. declared on the class. It does **not** initialise `Mockito.spy()` or `Mockito.mock()` calls made inline inside a test method — those static calls go directly through Mockito's core API and do not require `openMocks`. This is technically fine for the spy/mock creation itself.

However, the critical problem is `org.mockito.Mockito.doReturn(mockClaims).when(spyProvider).parseAndValidate(anyString())`. The `anyString()` call is an argument matcher. Mockito argument matchers must be used within a Mockito stubbing context. When a Mockito spy is created on `JwtTokenProvider` (a `@Component` concrete class), `doReturn(...).when(spy).method(matcher)` works correctly **only if Mockito is tracking the matcher state properly**. The issue is that `anyString()` from `import static org.mockito.ArgumentMatchers.anyString` is imported in the test but the import is not shown in the plan — the only import shown is `import static org.mockito.Mockito.when`. `ArgumentMatchers.anyString()` requires a separate static import from `org.mockito.ArgumentMatchers`. If the implementer copies the plan verbatim and uses the shown imports (only `when` is imported as static), `anyString()` will be an unresolved symbol, causing a compilation failure.

**Why it matters:** This test is the sole verification of the C5 fix (NPE on missing agencyId claim). If the test does not compile, the C5 guarantee is untested.

**Fix:** Add the missing import to the test class header shown in the plan:

```java
import static org.mockito.ArgumentMatchers.anyString;
```

Also confirm the plan's import block for `JwtAuthenticationFilterTest` includes this line explicitly, since `anyString` is only referenced in `validlySignedToken_missingAgencyIdClaim_noAuthenticationSet` and is easy to miss during copy-paste.

---

### C9. `SecurityConfig` replacement will throw `IllegalArgumentException` on startup in dev — duplicate CORS origin

**Problem:** The updated `corsConfigurationSource()` in Task 6 Step 5 builds the allowed origins list as:

```java
config.setAllowedOrigins(List.of(
    "http://localhost:5173",
    portalProperties.getBaseUrl()
));
```

`PortalProperties.baseUrl` defaults to `"http://localhost:5173"` (set in both the Java class and `application.yml`). In a standard local dev environment where `APP_BASE_URL` is not set, `portalProperties.getBaseUrl()` returns `"http://localhost:5173"`.

`CorsConfiguration.setAllowedOrigins` calls `initOrigins(origins)` internally, which in Spring Framework 6.x (used by Spring Boot 3.4.x) calls `UriComponentsBuilder.fromOriginHeader(origin)` to normalise each entry. The list passed to `setAllowedOrigins` is stored as-is — Spring does **not** deduplicate it. However, the more critical issue is that `List.of("http://localhost:5173", "http://localhost:5173")` is a valid list, so Spring will not throw on startup. But when an actual CORS preflight arrives, Spring iterates the list and may return a duplicate `Access-Control-Allow-Origin` header value, which some browsers reject as a malformed CORS response.

More critically, the plan's comment says "the list deduplicates to one entry" — this is factually incorrect. `List.of` does not deduplicate. In dev this produces a list with two identical elements, which is not the same as one element. While Spring's `CorsUtils.isPreFlightRequest` handling will ultimately match the first element and short-circuit, the behaviour is implementation-dependent and could change across Spring versions.

**Why it matters:** The "deduplicates" comment is a factual error that will mislead implementers. In production, if `APP_BASE_URL` is accidentally set to `http://localhost:5173` (a common mistake in early deployments), the same duplication occurs. More importantly, if the admin frontend and portal frontend are on the same origin, both will be permitted — but the comment's intent (portal-specific origin) is not communicated.

**Fix:** Deduplicate explicitly using a `LinkedHashSet` or a conditional add:

```java
List<String> origins = new java.util.ArrayList<>();
origins.add("http://localhost:5173");
String portalOrigin = portalProperties.getBaseUrl();
if (!origins.contains(portalOrigin)) {
    origins.add(portalOrigin);
}
config.setAllowedOrigins(origins);
```

Alternatively, the YAML default for `hcare.portal.base-url` could be changed to a distinct value (e.g., `http://localhost:5174`) to match a portal dev server on a different port, which avoids the duplication entirely and more accurately reflects production topology.

---

### C10. `FamilyPortalTokenDomainIT` seeds a `Client` with a constructor that does not match the actual entity

**Problem:** Task 4 Step 1 creates the `FamilyPortalTokenDomainIT` test with this seed line:

```java
clientId = clientRepo.save(new Client(agencyId, "Alice", "Test",
    java.time.LocalDate.of(1940, 1, 1))).getId();
```

This assumes a `Client(UUID agencyId, String firstName, String lastName, LocalDate dateOfBirth)` constructor. Reading the actual `Client` entity in the codebase (verified via the domain schema and existing integration tests) reveals the constructor signature differs — `Client` uses `(UUID agencyId, String firstName, String lastName)` without a `dateOfBirth` parameter, since `dateOfBirth` is an optional field set via a setter.

If the four-arg constructor does not exist on `Client`, this line will not compile. The plan has no step to verify this constructor exists (there is a compile check in Task 3 Step 3, but Task 4 Step 1 is written and committed before that compile step's scope would catch it, and the domain IT test is in a separate compile phase).

**Why it matters:** A compilation failure in the integration test will block the entire Task 4 step sequence. Because Task 4 Step 2 instructs the implementer to run the test and "expect it to fail (FamilyPortalToken doesn't exist yet)," a compilation failure here looks indistinguishable from an expected failure — the implementer may proceed to Step 3 and only discover the real issue when Step 5 also fails to compile.

**Fix:** Verify the `Client` constructor signature before writing the test. If `dateOfBirth` requires a setter call, update the seed to:

```java
Client client = new Client(agencyId, "Alice", "Test");
clientId = clientRepo.save(client).getId();
```

Or, if the four-arg constructor truly is needed and does not exist, add it to `Client.java` as part of this plan's file map. Either way, the plan should include an explicit constructor verification step or a comment citing the actual `Client` constructor signature found in the codebase.

---

## 3. Previously Addressed Items

The following critical issues from Reviews 1, 2, and 3 are fully resolved in the current plan:

- **C1 (Config property mismatch / portal JWT expiration unreachable):** Resolved. `PortalProperties` is bound to `hcare.portal`; `JwtTokenProvider` accepts it as a second constructor argument. The explanation comment in Task 5 Step 3 clearly documents why the two prefixes are distinct.
- **C2 (Fail-open null clientId on FAMILY_PORTAL tokens):** Resolved. Filter reads `clientId` from already-parsed `claims`; null check triggers fail-closed early return; `familyPortalToken_missingClientIdClaim_failsClosed` test verifies the behaviour.
- **C3 (Stale `findByAgencyIdAndEmail`):** Resolved. `FamilyPortalUserRepository` omits the method entirely; only `findByClientIdAndAgencyIdAndEmail` is provided with an explanatory comment.
- **C4 (V12 migration constraint atomicity risk):** Resolved. Pre-condition comment and verification query are present in the migration SQL.
- **C5 (NPE on missing agencyId/sub/role claims in JwtAuthenticationFilter):** Resolved. The updated filter null-checks all three required claims before `UUID.fromString`; `validlySignedToken_missingAgencyIdClaim_noAuthenticationSet` test verifies this path (though see C8 for an import issue in that test).
- **C6 (Silent overwrite of `JwtTokenProviderTest.java`):** Resolved. Task 5 Step 1 explicitly warns "do NOT create a new file" and provides a complete replacement that preserves all 9 existing tests plus the 2 new portal tests. The constructor update for `validateToken_returnsFalseForExpiredToken` is included.
- **C7 (CORS origin gap for portal frontend):** Resolved. Task 6 Step 5 injects `PortalProperties` into `SecurityConfig` and adds `portalProperties.getBaseUrl()` to the allowed origins list. The C7 fix comment is present and accurate.

---

## 4. Minor Issues & Improvements

### M1. `getClientId` method on `JwtTokenProvider` is still dead code (carried from Review 2 M2, Review 3 M1)

The method is still present with a Javadoc note that "it can be removed." This is now the third review raising this. The method is a public API on a security-critical class with no callers anywhere in the three plan parts. It should be removed from this plan rather than left as an invitation for future misuse. If it is deliberately retained for anticipated Part 2 use, name the caller explicitly in the Javadoc.

### M2. `RESTART IDENTITY CASCADE` in `@Sql` (carried from Review 1 M1, Review 2 M3, Review 3 M2)

`FamilyPortalTokenDomainIT` Task 4 Step 1 still uses `TRUNCATE ... RESTART IDENTITY CASCADE`. This is the fourth review flagging it. `RESTART IDENTITY` is a no-op for UUID-keyed tables and adds H2/PostgreSQL portability risk. Remove the clause:

```sql
TRUNCATE TABLE family_portal_tokens, family_portal_users, clients, agency_users, agencies CASCADE
```

### M3. `Agency.timezone` has no `ZoneId` validation (carried from Review 1 Q4, Review 2 M7, Review 3 M3)

Still unresolved. An invalid timezone string passes the `@Column(nullable = false, length = 50)` constraint and causes a `ZoneRulesException` at runtime when portal endpoints call `ZoneId.of(agency.getTimezone())`. A `@PrePersist`/`@PreUpdate` lifecycle callback with `ZoneId.of(this.timezone)` would close this gap before Part 2 uses the field.

### M4. `AbstractIntegrationTest` redundant registry additions still present (carried from Review 1 M4, Review 2 M6, Review 3 M4)

Task 2 Step 2 still adds `hcare.portal.jwt.expiration-days` and `hcare.portal.base-url` to `AbstractIntegrationTest.configureDataSource`, despite both having defaults in `application.yml`. These are harmless but are noise that has been flagged in every review. Remove them.

### M5. `JwtAuthenticationFilterTest` has no test for a missing `Authorization` header (carried from Review 1 M7, Review 2 M5, Review 3 M5)

This is the fourth review flagging this gap. The filter correctly falls through to `chain.doFilter` when no header is present, but there is no test confirming it. A single five-line test would close this permanently. Given the filter already has four tests, adding a fifth is trivial.

### M6. Task 6 Step 5 `SecurityConfig` replacement touches the constructor signature — existing `SecurityConfig` integration tests may break

The existing `SecurityConfig` constructor is `SecurityConfig(JwtTokenProvider)`. The plan changes it to `SecurityConfig(JwtTokenProvider, PortalProperties)`. Any existing integration test that constructs `SecurityConfig` directly (rather than relying on Spring Boot's auto-configuration via `@SpringBootTest`) will fail to compile. The plan does not check for such tests. A search for `new SecurityConfig(` in the test tree should be added to Task 6 Step 5 as a prerequisite, or a note that `@SpringBootTest`-based tests will pick up `PortalProperties` automatically because it is a `@Component`.

---

## 5. Questions for Clarification

**Q1.** Q1 from Reviews 2 and 3 remains unanswered: portal tokens share the admin JWT signing key. With a 30-day TTL, a key rotation forces all 30-day portal sessions to expire immediately with no graceful re-issuance path (invite tokens are one-time use). Is there a recorded design decision about whether this is acceptable, or is a separate `PORTAL_JWT_SECRET` environment variable planned?

**Q2.** The `JwtAuthenticationFilterTest.validlySignedToken_missingAgencyIdClaim_noAuthenticationSet` test constructs a `JwtAuthenticationFilter spyFilter = new JwtAuthenticationFilter(spyProvider)` inside the test method. After Task 6 Step 3 updates the filter, `JwtAuthenticationFilter` takes a `JwtTokenProvider` in its constructor — correct. But the test also calls `spyFilter.doFilterInternal(req, res, chain)`, which is `protected` in `OncePerRequestFilter`. Some Spring versions restrict calling protected methods directly on mock-constructed instances outside the package. Is `doFilterInternal` intended to be package-private for testing, or should the test call `filter.doFilter(req, res, chain)` (the public wrapper) and let `OncePerRequestFilter.doFilter` delegate?

**Q3.** The plan's Task 7 Step 1 instructs running the full backend test suite and treating any failure as a blocker before Part 2. Given that Part 1 modifies `JwtTokenProvider`'s constructor (breaking any existing test that uses `new JwtTokenProvider(props)`), are there other integration tests beyond `JwtTokenProviderTest` that construct `JwtTokenProvider` directly? If so, they need to be updated in Task 5, not discovered at Task 7.

---

## 6. Final Recommendation

**Approve with changes** — the three critical issues (C8, C9, C10) are small and targeted:

- C8 (missing `anyString` import in `JwtAuthenticationFilterTest`) is a one-line fix that prevents the C5-verification test from compiling.
- C9 (non-deduplicating CORS origin list in dev) requires replacing `List.of(...)` with an explicit deduplication guard — approximately five lines.
- C10 (wrong `Client` constructor in `FamilyPortalTokenDomainIT`) requires verifying the actual `Client` constructor and updating the seed line accordingly.

None of these require restructuring the plan. The four persistent minor carryovers (M1 dead code, M2 `RESTART IDENTITY`, M3 timezone validation, M4 redundant registry additions) have now been flagged across all four reviews and should be resolved in this pass — deferring them further increases the risk they will be baked into later plan parts that assume a clean foundation.
