# Critical Implementation Review — Plan 2: Family Portal Backend Endpoints
**Review #1** | Reviewer: Claude Sonnet 4.6 | Date: 2026-04-08

---

## 1. Overall Assessment

The plan is structurally sound and the key security invariant (JWT revocation via `fpuRepo.existsById`) is correctly placed. Constructor injection is used throughout, DTOs are immutable records, and timestamps use explicit UTC. However, there are several correctness and performance issues that need resolution before this plan is safe to execute: a significant N+1 query problem in `getDashboard`, a missing `@Public` annotation on the verify endpoint that will likely cause authentication failures, a broken `getClientIp` implementation that is trivially spoofable by attackers (defeating the rate limiter's purpose), a missing `FamilyPortalTokenRepository` reference for the plan (no prior plan defined it in detail), and a subtle test bug in token extraction. The issues are fixable inline; none require plan restructuring.

---

## 2. Critical Issues

### Issue 1 — N+1 queries in `getDashboard`: caregiver lookups per shift

**What:** `buildUpcomingDto` calls `caregiverRepo.findById(shift.getCaregiverId())` inside a stream that processes up to 3 filtered shifts from a potentially large result set. `buildTodayVisitDto` also calls `buildCaregiverDto` which fires two more single-row lookups (`caregiverRepo.findById` + `serviceTypeRepo.findById`). In a worst case with a client who has many upcoming shifts and a caregiver card, this is 2 additional queries per qualifying shift on top of the base queries.

**Why it matters:** The `futureShifts` query returns raw `Shift` rows for a 90-day window before filtering to 3. For an active client that could easily be 50–100 shift rows loaded into memory and then discarded. Caregiver and service-type name lookups should be batched.

**Fix:** Collect the distinct `caregiverId` and `serviceTypeId` values from the qualifying shifts first, then do `caregiverRepo.findAllById(caregiverIds)` and `serviceTypeRepo.findAllById(serviceTypeIds)` once each to build lookup maps. Apply the maps when constructing DTOs. Alternatively, add a JPQL projection query to `ShiftRepository` that JOIN-fetches caregiver name and service type name in a single query for a given client and date range.

---

### Issue 2 — Missing `@Public` (or security config permit) on the verify endpoint causes 401 before the filter fires

**What:** `POST /api/v1/family/auth/verify` is a public endpoint — family members hit it with only a raw token, no JWT. The existing `SecurityConfig.java` uses Spring Security to protect all `@RestController` endpoints by default. The plan does not add `@Public` to `FamilyPortalAuthController.verify` and does not add a `permitAll()` rule for `/api/v1/family/auth/verify` to `SecurityConfig`.

**Why it matters:** Without the security config change, Spring Security will intercept every request to this endpoint before it reaches the controller, reject it with 401, and the `VerifyRateLimiter` filter (registered before `JwtAuthenticationFilter`) will never see the rate-limit threshold reached. The integration tests will also fail because the test seeding obtains the portal JWT via `obtainPortalJwt()`, which calls this endpoint without a bearer token.

**Fix:** Either add `@Public` annotation (following the existing convention referenced in CLAUDE.md) to `FamilyPortalAuthController.verify()`, or add `.requestMatchers(HttpMethod.POST, "/api/v1/family/auth/**").permitAll()` to the `SecurityConfig` permit list. Confirm which approach is used in the existing codebase (check `AuthController.login` for the existing pattern).

---

### Issue 3 — `X-Forwarded-For` spoofing makes the rate limiter trivially bypassable

**What:** `VerifyRateLimiter.getClientIp()` trusts the first value in `X-Forwarded-For` unconditionally. When the backend is deployed behind a load balancer or reverse proxy, an attacker can include `X-Forwarded-For: 1.2.3.4` in any request and rotate fake IPs at will, making the per-IP counter useless.

**Why it matters:** The entire purpose of the rate limiter is brute-force protection for the token verification endpoint. If it is bypassable by spoofing a header, it provides no real protection against an attacker trying to enumerate or brute-force invite tokens.

**Fix:** The implementation must decide whether `X-Forwarded-For` is trusted at all. For production deployments the correct approach is to only trust the `X-Forwarded-For` header when the request comes from a known proxy CIDR, or to use `RemoteAddr` only (the actual TCP peer) because the load balancer terminates TLS and its IP is the only one that cannot be forged. A simple safe default: use `request.getRemoteAddr()` only, and document that the reverse proxy must be configured to use a real IP header (e.g., `X-Real-IP` set from the actual client by NGINX/ALB, not a user-supplied header). Alternatively, read from a configurable `${hcare.portal.trusted-proxy-header:}` so the operator explicitly opts in.

---

### Issue 4 — `FamilyPortalUserRepository.findByClientIdAndAgencyIdAndEmail` does not exist

**What:** `FamilyPortalService.generateInvite` calls `fpuRepo.findByClientIdAndAgencyIdAndEmail(clientId, agencyId, req.email())`. The current `FamilyPortalUserRepository` (read from the actual file) only declares `findByAgencyIdAndEmail`, `findByClientId(UUID)`, and `findByClientId(UUID, Pageable)`. There is no `findByClientIdAndAgencyIdAndEmail` method.

**Why it matters:** This will cause a Spring Data JPA exception at startup (`No property 'findByClientIdAndAgencyIdAndEmail' found`) — or at minimum a runtime failure on the first invite call.

**Fix:** Add `Optional<FamilyPortalUser> findByClientIdAndAgencyIdAndEmail(UUID clientId, UUID agencyId, String email);` to `FamilyPortalUserRepository`. The plan must include this as an explicit step in Task 9, or in Task 11 where `ClientController` modifications are described. It should also align with the unique constraint `uq_fpu_client_agency_email` added in V12.

---

### Issue 5 — 90-day window query for upcoming visits fetches the entire raw shift list into memory

**What:** `getDashboard` uses `findByClientIdAndScheduledStartBetween(clientId, startOfTomorrow, startOfTomorrow.plusDays(90))` and then streams, filters, sorts, and limits to 3 in Java. For any client with an active recurring schedule, this could return dozens of rows when only 3 are needed.

**Why it matters:** This is a performance/scalability issue. The query loads potentially 90 shifts to return 3. The `agencyFilter` Hibernate filter is not applied here because `getDashboard` is called with claims from the JWT — the Hibernate tenant filter is activated by `TenantFilterAspect` based on `TenantContext`, which is set from the admin/scheduler `UserPrincipal`. For a FAMILY_PORTAL token, `TenantContext` may not be populated, meaning the `agencyFilter` may not fire on this `@Transactional(readOnly=true)` call.

**Fix:** Add a `findTop3ByClientIdAndStatusInAndScheduledStartAfterOrderByScheduledStartAsc` derived query (or a `@Query` with `LIMIT 3`) to `ShiftRepository`. This offloads sorting and limiting to the DB. Separately, verify that `TenantFilterAspect` activates the `agencyFilter` for FAMILY_PORTAL-role requests; if not, add explicit `agencyId` predicates to all family portal repository queries.

---

### Issue 6 — Token extraction in integration tests is fragile and wrong for multi-param query strings

**What:** Both test classes extract the raw token with:
```java
URI.create(inviteResp.getBody().inviteUrl()).getQuery().replace("token=", "");
```
`String.replace` is not prefix-safe — it replaces ALL occurrences of the substring `"token="` anywhere in the query string. If a future format adds additional parameters (e.g., `?token=abc&expires=...`), this silently produces a corrupted token. Additionally, `URI.getQuery()` returns the raw query string; if the token ever contains `%` encoding (unlikely for hex but not guaranteed), this would also break.

**Why it matters:** Fragile test helpers lead to false-positive test failures or, worse, tests that pass on the happy path but mask real bugs. The token extraction should use a proper query param parser.

**Fix:** Use `UriComponentsBuilder.fromUriString(...).build().getQueryParams().getFirst("token")` or `new URIBuilder(url).getQueryParams().stream().filter(p -> "token".equals(p.getName())).findFirst().get().getValue()` to correctly extract a single named query parameter.

---

## 3. Previously Addressed Items

This is the first review; no prior reviews exist.

---

## 4. Minor Issues & Improvements

**4a — `toString()` on timestamps produces non-ISO format strings**
`shift.getScheduledStart().toString()` produces `LocalDateTime.toString()` which omits trailing zeros in the seconds/nanos component (e.g., `2026-04-08T09:00` instead of `2026-04-08T09:00:00Z`). The DTO javadoc says "UTC ISO-8601" but `LocalDateTime` has no zone designator. Prefer `shift.getScheduledStart().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)` for all timestamp fields in the DTOs. This is also inconsistent with `expiresAtStr` in `generateInvite` which correctly uses `ISO_OFFSET_DATE_TIME`.

**4b — `requireClientForInvite` in `ClientService` is a no-op wrapper**
The plan adds a package-private `void requireClientForInvite(UUID clientId)` that simply delegates to `requireClient(clientId)`. This adds a public API surface with no semantic value. Just call `requireClient` directly from the controller method, or (better) move the `generateInvite` call into `ClientService` so the controller doesn't reach into `FamilyPortalService` directly and doesn't need to manually fetch `agencyId` from `TenantContext`.

**4c — `getDashboard` is missing a test for the `todayVisit.clockedInAt` / `clockedOutAt` fields**
The integration tests cover no-shift, admin-rejected, revoked, discharged, assigned-shift-grey, and cancelled-shift cases. There is no test for a shift in `IN_PROGRESS` or `COMPLETED` status with an associated `EvvRecord`, meaning the EVV lookup branch in `buildTodayVisitDto` and the duration calculation in `buildLastVisitDto` are untested.

**4d — `mapShiftStatus` uses a `default` case that shadows unhandled enum values silently**
The `switch` includes a `default -> "GREY"` branch. If new `ShiftStatus` values are added (e.g., `NO_SHOW`), they will silently map to `GREY` rather than failing with an `IllegalArgumentException`. Since this is a `switch` expression on a sealed enum, removing the default and letting the compiler enforce exhaustiveness is safer.

**4e — `SecureRandom` instance declared as a field rather than injected**
`private final SecureRandom secureRandom = new SecureRandom()` is fine in terms of correctness and thread safety, but it cannot be mocked or replaced in tests. For a `@Service` bean, injecting it via constructor (even `SecureRandom` can be a `@Bean`) or using `new SecureRandom()` in a `@Bean` method would be more testable. This is a minor style note — `SecureRandom` is thread-safe so the current approach causes no functional issues.

**4f — `sha256Hex` catches `NoSuchAlgorithmException` for SHA-256 which cannot actually occur on any JVM**
SHA-256 is mandated by the Java specification to always be available. Wrapping it in a try/catch and rethrowing as `IllegalStateException` is boilerplate noise. The `MessageDigest.getInstance("SHA-256")` call can be extracted to a static constant using `static { }` initialization, or the checked exception can simply be propagated via a `sneakyThrow` pattern. This is cosmetic but adds noise.

**4g — `FamilyPortalTokenCleanupJob` cron property key does not match `application-test.yml`**
The plan adds `registry.add("hcare.portal.cleanup-cron", () -> "-")` to `AbstractIntegrationTest`, but the job uses `${hcare.portal.cleanup-cron:0 0 3 * * *}` (with a default value). This means the test override works, but the same key is not referenced in the plan's description of `application-test.yml` changes. The plan should clarify whether this key is added to `AbstractIntegrationTest` only, or also to `application-test.yml`, to avoid inconsistency across test execution modes (e.g., when tests are run with `@SpringBootTest` without the container).

**4h — No test for `verify` with an expired token**
`FamilyPortalAuthControllerIT` does not include a test where a token exists in the database but has `expiresAt` in the past. The path `tokenRow.getExpiresAt().isBefore(...)` returning true (the `TOKEN_EXPIRED` branch) is untested.

---

## 5. Questions for Clarification

**Q1:** How does `TenantFilterAspect` behave when a `FAMILY_PORTAL` JWT is the authenticated principal? The family portal user is scoped to one `agencyId` (from the JWT claim), but the `TenantContext` ThreadLocal is normally set by `TenantFilterInterceptor` from `UserPrincipal.getAgencyId()`. If `UserPrincipal` for the family portal role returns a non-null `agencyId`, the tenant filter should fire correctly. But if the `JwtAuthenticationFilter` for portal tokens populates a different principal type, the interceptor may skip it. This should be confirmed before execution.

**Q2:** The plan places `FamilyPortalService` in `com.hcare.api.v1.family` rather than a service layer package. Is this intentional given the architecture convention? Other services (`ClientService`, `CaregiverService`) live in the same package as their controller. The pattern is consistent, but a `com.hcare.family` or `com.hcare.service.family` namespace would better reflect that this is a service-layer class, not just a controller-adjacent helper.

**Q3:** Should the invite endpoint return `201 Created` instead of `200 OK`? A new token resource is being created. Other creation endpoints in the project use `ResponseEntity.status(HttpStatus.CREATED)`. The plan uses `ResponseEntity.ok(...)` for the invite — this is a convention inconsistency worth a deliberate decision.

**Q4:** The plan filters `todayShifts` to exclude `CANCELLED` and `MISSED` but the DTO's `status` field documentation lists `"GREY" | "IN_PROGRESS" | "COMPLETED" | "CANCELLED"`. If CANCELLED shifts are excluded from `todayVisit`, why does the DTO spec include a `"CANCELLED"` status? The test `dashboard_cancelledShift_caregiverCardIsNull` asserts `todayVisit` is null for a CANCELLED shift, which is consistent with the filter but inconsistent with the DTO spec. This should be clarified and the DTO Javadoc updated.

---

## 6. Final Recommendation

**Major revisions needed** before execution.

Issue 2 (missing public endpoint security configuration) and Issue 4 (missing repository method) are compile/runtime blockers — the plan cannot execute successfully without these fixes. Issue 1 and Issue 5 (N+1 and in-memory filtering) are performance correctness problems that will cause visible degradation at scale. Issue 3 (rate limiter spoofing) is a security correctness problem that undermines the stated protection goal. Issue 6 (test token extraction) will produce brittle tests that may mask bugs.

The plan should be revised to address Issues 1–6 before implementation begins.
