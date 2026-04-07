# Critical Implementation Review — 2026-04-06-backend-gaps.md (Review 2)

**Reviewed plan:** `docs/superpowers/plans/2026-04-06-backend-gaps.md`  
**Prior reviews:** Review 1 (`2026-04-06-backend-gaps-critical-review-1.md`)

---

## 1. Overall Assessment

The plan is significantly improved since review-1: C-1 (the security gap in Document download/delete) is fully resolved, M-2 (delete ordering) now carries an explanatory comment, and M-3 (DocumentController mapping) is explicitly justified. However, four issues from review-1 remain unaddressed — two of them non-trivial — and three new issues have been identified in the revised Task 6 material: a timing-attack-vulnerable HMAC comparison, an upload-side file orphan symmetric to the now-documented delete-side orphan, and a redundant explicit agency check that contradicts the CLAUDE.md architecture principle.

---

## 2. Critical Issues

### C-2 (still unresolved) · Task 2 — Placeholder test with no assertions

The broken test `createCarePlan_returns_409_on_concurrent_version_collision` (plan lines 325–330) is still present. It compiles, runs, and passes silently with zero assertions. The plan's own inline note acknowledges it needs replacement. The valid replacement test (`createCarePlan_increments_version_for_second_plan`) immediately follows it.

**Fix:** Remove the broken test. The replacement below it is the one that should ship (already in the plan — just delete the broken predecessor).

---

## 3. Previously Addressed Items

- **C-1 · Document download/delete missing owner ID check** — Fully resolved. `requireDocument(UUID documentId, UUID expectedOwnerId)` now checks both `agencyId` and `ownerId`, and all four controller methods pass the path variable owner ID to the service.
- **M-2 · Filesystem delete before transaction commit** — The comment is now in place in `DocumentService.delete`. Accepted for MVP.
- **M-3 · DocumentController class-level `@RequestMapping`** — Addressed with an explicit justification comment explaining why full-path methods are used (two disjoint resource roots).

---

## 4. Minor Issues & Improvements

### M-1 (still unresolved) · Task 5 — `@Transactional` on `UserController.listUsers()`

`@Transactional(readOnly = true)` at line ~1198 of the plan is still on the controller method. The transaction belongs in `UserService.listUsers()`, which is where it is. The controller annotation is dead weight and violates the codebase pattern.

**Fix:** Remove `@Transactional(readOnly = true)` from `UserController.listUsers()`.

---

### M-4 (still unresolved) · Task 3 — `CaregiverControllerIT` missing credential/background-check coverage

`CaregiverControllerIT` still tests only list, create, get, update, and availability. The `deleteCredential` endpoint has a cross-ownership check (`!cred.getCaregiverId().equals(caregiverId)`) that is non-trivial and untested. At minimum, add:

```java
@Test
void addAndListCredential_for_caregiver() {
    AddCredentialRequest req = new AddCredentialRequest(
        "CPR", LocalDate.of(2024, 1, 1), LocalDate.of(2026, 1, 1));
    ResponseEntity<CredentialResponse> add = restTemplate.exchange(
        "/api/v1/caregivers/" + caregiver.getId() + "/credentials", HttpMethod.POST,
        new HttpEntity<>(req, auth()), CredentialResponse.class);
    assertThat(add.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(add.getBody().credentialType()).isEqualTo("CPR");
}

@Test
void deleteCredential_from_other_caregiver_returns_404() {
    Caregiver other = caregiverRepo.save(new Caregiver(agency.getId(), "A", "B", "ab@test.com"));
    AddCredentialRequest req = new AddCredentialRequest(
        "CPR", LocalDate.of(2024, 1, 1), LocalDate.of(2026, 1, 1));
    CredentialResponse cred = restTemplate.exchange(
        "/api/v1/caregivers/" + other.getId() + "/credentials", HttpMethod.POST,
        new HttpEntity<>(req, auth()), CredentialResponse.class).getBody();

    ResponseEntity<String> del = restTemplate.exchange(
        "/api/v1/caregivers/" + caregiver.getId() + "/credentials/" + cred.id(),
        HttpMethod.DELETE, new HttpEntity<>(auth()), String.class);
    assertThat(del.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
}
```

Add to the `@Sql` TRUNCATE list: `caregiver_credentials` (already present).

---

### M-5 (still unresolved) · Task 4 — `@NotBlank` redundant on `state` in `RegisterAgencyRequest`

`@NotBlank @Size(min = 2, max = 2) @Pattern(...)` (plan line ~662) — `@Size(min=2, max=2)` already rejects null and empty/blank strings. `@NotBlank` is noise.

**Fix:** Remove `@NotBlank` from the `state` field in `RegisterAgencyRequest`.

---

### M-6 · Task 6 — HMAC comparison vulnerable to timing attack

`validateAndExtractKey` uses `String.equals()` to compare the provided HMAC against the computed one:

```java
if (!hmac(payload).equals(parts[1])) {
```

`String.equals()` is a short-circuit comparison — it returns as soon as it finds the first differing character, leaking information about the number of matching leading bytes. The standard remedy is a constant-time comparison:

```java
if (!MessageDigest.isEqual(
        hmac(payload).getBytes(StandardCharsets.UTF_8),
        parts[1].getBytes(StandardCharsets.UTF_8))) {
```

For MVP local storage this is low-exploitability, but HMAC token validation is a security primitive — apply the correct pattern from the start.

---

### M-7 · Task 6 — Upload writes file before transaction commits (symmetric to M-2)

`DocumentService.upload` calls `storageService.store()` (filesystem write) before `documentRepository.save()` (DB commit):

```java
String storageKey = storageService.store(file, agencyId, ownerType, ownerId); // filesystem: immediate
Document doc = new Document(...);
...
return DocumentResponse.from(documentRepository.save(doc));                   // DB: committed at end of @Transactional
```

If the `documentRepository.save()` fails (e.g., unique constraint violation on a future index), the file is already written to disk — an orphaned file with no DB entry. This is the exact mirror of the delete-side orphan documented in M-2.

**Fix:** Add a symmetric comment in `DocumentService.upload` (or in the private `upload` helper):

```java
// Note: filesystem write runs before DB commit. If documentRepository.save() fails,
// the file is already stored — orphaned file with no DB entry. Acceptable for MVP local storage.
String storageKey = storageService.store(file, agencyId, ownerType, ownerId);
```

---

### M-8 · Task 6 — `requireClient`/`requireCaregiver` in `DocumentService` duplicate tenant enforcement

`DocumentService.requireClient` explicitly checks `client.getAgencyId().equals(agencyId)` after fetching via `clientRepository.findById()`. Per CLAUDE.md: *"Never enforce tenant isolation at the service layer — the framework prevents cross-agency leakage."* The Hibernate `agencyFilter` is already active inside `@Transactional` methods, so `findById` on a client from another agency will return `Optional.empty()` naturally, and the `orElseThrow` will fire as a 404 without the explicit equality check.

The same redundant check appears in `requireCaregiver`. Compare with the existing `CaregiverService.requireCaregiver` (lines 91–95 in the actual codebase) — it does not perform the explicit check and relies on the filter.

**Fix:** Remove the explicit `getAgencyId().equals(agencyId)` checks from `DocumentService.requireClient` and `requireCaregiver`. The Hibernate filter handles it:

```java
private void requireClient(UUID clientId) {
    clientRepository.findById(clientId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found"));
}

private void requireCaregiver(UUID caregiverId) {
    caregiverRepository.findById(caregiverId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Caregiver not found"));
}
```

---

## 5. Questions for Clarification

1. **Email uniqueness scope (Task 5, carried from review-1):** `inviteUser` uses a global `findByEmail` check — same email cannot be used at two different agencies. Intentional? Consistent with `AuthService.login` but constrains multi-agency users.

2. **`document_from_other_agency_client_returns_404` test logic:** The test (plan lines 1422–1430) calls `listForClient` on a client from another agency and expects 404. But `requireClient` calls `clientRepository.findById(clientId)` — if the Hibernate filter is active, this returns `Optional.empty()` → 404. If the explicit agency check is removed per M-8, the behavior is unchanged. If kept, the explicit check fires first. Either way the 404 fires. The test is valid regardless of which fix is applied.

3. **`Document` constructor accepting null `originalFilename`:** `file.getOriginalFilename()` can return null for some `MultipartFile` implementations. The `Document` entity stores it as `fileName`. Is null an acceptable value in the DB schema, or should it be defaulted (e.g., `"upload"`) at the service layer consistent with `LocalDocumentStorageService.sanitize`?

---

## 6. Final Recommendation

**Approve with changes.**

Fix C-2 (remove the broken placeholder test) before any agent starts Task 2. M-6 (timing attack) and M-8 (redundant agency checks) are small inline fixes. M-7 (upload orphan comment) is one line. M-1 (controller `@Transactional`) and M-4 (credential IT coverage) can be added in the same commits as their respective tasks without significant extra work.
