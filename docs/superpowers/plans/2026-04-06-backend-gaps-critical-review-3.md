# Critical Implementation Review — 2026-04-06-backend-gaps.md (Review 3)

**Reviewed plan:** `docs/superpowers/plans/2026-04-06-backend-gaps.md`  
**Prior reviews:** Review 1 (`2026-04-06-backend-gaps-critical-review-1.md`), Review 2 (`2026-04-06-backend-gaps-critical-review-2.md`)

---

## 1. Overall Assessment

Three iterative passes have systematically eliminated all previously identified issues. All eight items from reviews 1 and 2 (C-1, C-2, M-1 through M-8) are fully resolved. This pass found no new critical issues. Four new minor issues were identified: a CLAUDE.md tenant isolation violation in `UserService.requireUser` that is the direct sibling of the M-8 fix applied to `DocumentService`; a stale `Transactional` import in `UserController` that will fail the project's Checkstyle enforcement; an unhandled `null` return from `file.getOriginalFilename()` that was flagged as Q3 in review-2 but left unaddressed in the code; and a relative URL in the `Location` redirect header that is technically non-conforming and may not be followed by strict HTTP clients.

---

## 2. Critical Issues

None.

---

## 3. Previously Addressed Items

- **C-1** — `requireDocument` now checks both `agencyId` and `ownerId`; all four controller methods forward the path-variable owner ID.
- **C-2** — Broken placeholder test removed; `createCarePlan_increments_version_for_second_plan` is the only version test.
- **M-1** — `@Transactional(readOnly = true)` removed from `UserController.listUsers()`.
- **M-2** — Filesystem-delete-before-commit comment in place in `DocumentService.delete`.
- **M-3** — Full-path methods on `DocumentController` justified with an explanatory comment.
- **M-4** — `addAndListCredential_for_caregiver` and `deleteCredential_from_other_caregiver_returns_404` added to `CaregiverControllerIT`.
- **M-5** — `@NotBlank` removed from `RegisterAgencyRequest.state`.
- **M-6** — `MessageDigest.isEqual()` constant-time comparison used in `validateAndExtractKey`.
- **M-7** — Upload-side filesystem-before-commit comment added in the private `upload` helper.
- **M-8** — Explicit `agencyId` equality checks removed from `DocumentService.requireClient` and `requireCaregiver`; Hibernate filter comments added.

---

## 4. Minor Issues & Improvements

### M-9 · Task 5 — `UserService.requireUser` explicit agency check (same violation as M-8, different service)

`UserService.requireUser` (plan lines 1172–1179) has the identical pattern that was removed from `DocumentService` in M-8:

```java
private AgencyUser requireUser(UUID userId, UUID agencyId) {
    AgencyUser user = userRepository.findById(userId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    if (!user.getAgencyId().equals(agencyId)) {     // ← redundant if agencyFilter is active
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
    }
    return user;
}
```

Per CLAUDE.md: *"Never enforce tenant isolation at the service layer — the framework prevents cross-agency leakage."* `AgencyUser` carries `agencyId`; if it has `@Filter(name = "agencyFilter", …)` applied (consistent with the architecture's "all entities carry an implicit `agencyId`"), then `findById` inside a tenant-scoped `@Transactional` will already scope to the current tenant and the explicit equality check is unreachable dead code. Compare with `CaregiverService.requireCaregiver` (lines 91–95 in the real codebase) which relies solely on the filter.

**Fix:**

```java
private AgencyUser requireUser(UUID userId) {
    // Hibernate agencyFilter (TenantFilterAspect) scopes findById to the current tenant.
    return userRepository.findById(userId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
}
```

Update callers: `updateUserRole` and `deleteUser` currently pass `agencyId` as a second argument — remove that argument from each call site. Also remove the `UUID agencyId = TenantContext.get();` locals in those methods if they are then unused.

---

### M-10 · Task 5 — Unused `Transactional` import in `UserController` will fail Checkstyle

`UserController` (plan line 1197) retains:

```java
import org.springframework.transaction.annotation.Transactional;
```

The M-1 fix removed the `@Transactional(readOnly = true)` annotation from `listUsers()` but left the import. The project enforces Google Java Style via Checkstyle, which treats unused imports as errors. This will fail `mvn verify`.

**Fix:** Remove `import org.springframework.transaction.annotation.Transactional;` from `UserController.java`.

---

### M-11 · Task 6 — `file.getOriginalFilename()` null passed directly to `Document` constructor

In the private `upload` helper (plan line 1804):

```java
Document doc = new Document(agencyId, ownerType, ownerId,
    file.getOriginalFilename(), storageKey);
```

`MultipartFile.getOriginalFilename()` can return `null` for some implementations (e.g., programmatic `MockMultipartFile` in tests, or certain mobile clients). `LocalDocumentStorageService.sanitize` already defaults null to `"upload"`, but that sanitized name is only used for the on-disk file; `Document.fileName` receives the raw null. If `fileName` has a `NOT NULL` constraint in the schema, this causes a DB-level `ConstraintViolationException` rather than a clean error.

This was raised as clarification question Q3 in review-2 but was not addressed in the plan code.

**Fix** (one line in the `upload` helper):

```java
String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload";
Document doc = new Document(agencyId, ownerType, ownerId, fileName, storageKey);
```

---

### M-12 · Task 6 — `generateDownloadUrl` returns a relative URL in the `Location` header

`LocalDocumentStorageService.generateDownloadUrl` (plan line 1647) returns:

```java
return "/api/v1/internal/documents/content?token=" + ...;
```

RFC 7231 §7.1.2 specifies that the `Location` header value for a redirect **should** be an absolute URI. Sending a relative path is non-conforming. Practical consequences:

- **TestRestTemplate** (Apache HttpClient) resolves relative redirects against the server base URL — tests will pass.
- **React Native's `fetch()`** follows absolute redirects natively, but relative redirect behaviour is implementation-defined and may break on some platforms/versions.
- **Reverse proxies** (nginx, ALB) may rewrite the relative path incorrectly under non-root context paths.

**Fix:** Inject the server base URL and build an absolute URL. A simple approach for local dev that avoids a hard-coded host:

```java
// Inject via @Value("${hcare.storage.base-url:http://localhost:8080}")
private final String baseUrl;

@Override
public String generateDownloadUrl(String storageKey) {
    ...
    return baseUrl + "/api/v1/internal/documents/content?token="
        + URLEncoder.encode(token, StandardCharsets.UTF_8);
}
```

Add `hcare.storage.base-url` to `application.yml` and `application-test.yml`. If an absolute URL is not practical for MVP, add an explanatory comment and a TODO tracking the known deviation.

---

## 5. Questions for Clarification

1. **`AgencyUser` Hibernate filter** (prerequisite for M-9): Does `AgencyUser` have `@Filter(name = "agencyFilter", condition = "agency_id = :agencyId")` applied? If it does not (because it is a cross-tenant authentication entity), then the explicit check in `requireUser` is intentional and M-9 does not apply. Before applying M-9, verify in `AgencyUser.java`.

2. **Email uniqueness — DB constraint** (applies to both `AgencyService.register` and `UserService.inviteUser`): The application-level `findByEmail` + save pattern has a TOCTOU race under concurrent load. A unique constraint on `agency_users.email` is the proper safeguard and would convert the concurrent-insert failure into a catchable `DataIntegrityViolationException`. Does such a constraint exist in the current Flyway migrations? If not, add it; if yes, add a catch around the save to map `DataIntegrityViolationException → ResponseStatusException(CONFLICT)`.

---

## 6. Final Recommendation

**Approve with changes.**

M-10 (unused import → Checkstyle failure) must be fixed before the plan can compile cleanly. M-11 (null filename) is a one-liner and should go in with Task 6. M-9 (redundant agency check in `UserService`) is a quick inline fix contingent on confirming the `AgencyUser` entity has `@Filter` applied (see Q1). M-12 (relative redirect URL) can be a comment + TODO if the fix is deferred.
