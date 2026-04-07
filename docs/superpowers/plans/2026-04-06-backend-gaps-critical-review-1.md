# Critical Implementation Review — 2026-04-06-backend-gaps.md (Review 1)

**Reviewed plan:** `docs/superpowers/plans/2026-04-06-backend-gaps.md`  
**Prior reviews:** None (first pass)

---

## 1. Overall Assessment

The plan is well-structured: full code at every step, TDD sequencing, and the Testcontainers IT pattern is applied consistently. Most issues are recoverable before execution begins. Two require concrete fixes before any agent proceeds: a security gap in the Document API (missing owner ID cross-check) and a placeholder test that violates the plan's own "No Placeholders" rule. Three additional lower-priority issues should be resolved first as well.

---

## 2. Critical Issues

### C-1 · Task 6 — Document download/delete bypasses owner ID check (security)

**Problem:**  
`downloadClientDocument`, `deleteClientDocument`, `downloadCaregiverDocument`, and `deleteCaregiverDocument` each receive a `clientId`/`caregiverId` path variable but **never pass it to the service**. Both delegate to `documentService.getContent(docId)` and `documentService.delete(docId)`, which only check that the document belongs to the authenticated agency — not that the `ownerId` matches the URL.

Consequence: an authenticated user from Agency A can retrieve or delete *any* document owned by *any* client or caregiver in their agency, regardless of which `/clients/{clientId}/documents` sub-resource it appears under. The URL-level scoping is purely cosmetic.

**Fix — add an owner check inside `requireDocument`:**

Change the signature:
```java
private Document requireDocument(UUID documentId, UUID expectedOwnerId) {
    UUID agencyId = TenantContext.get();
    Document doc = documentRepository.findById(documentId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
    if (!doc.getAgencyId().equals(agencyId) || !doc.getOwnerId().equals(expectedOwnerId)) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found");
    }
    return doc;
}
```

Update `getContent` and `delete` to accept and forward `ownerId`:
```java
public InputStream getContent(UUID documentId, UUID ownerId) {
    Document doc = requireDocument(documentId, ownerId);
    return storageService.load(doc.getFilePath());
}

public void delete(UUID documentId, UUID ownerId) {
    Document doc = requireDocument(documentId, ownerId);
    documentRepository.delete(doc);
    storageService.delete(doc.getFilePath());
}
```

Update controller:
```java
@GetMapping("/api/v1/clients/{clientId}/documents/{docId}/content")
public ResponseEntity<InputStreamResource> downloadClientDocument(
        @PathVariable UUID clientId, @PathVariable UUID docId) {
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .body(new InputStreamResource(documentService.getContent(docId, clientId)));
}

@DeleteMapping("/api/v1/clients/{clientId}/documents/{docId}")
public ResponseEntity<Void> deleteClientDocument(
        @PathVariable UUID clientId, @PathVariable UUID docId) {
    documentService.delete(docId, clientId);
    return ResponseEntity.noContent().build();
}
// (same pattern for the caregiver variants)
```

Add a test to `DocumentControllerIT` to verify this enforcement:
```java
@Test
void downloadDocument_from_other_client_returns_404() {
    // Upload a document for 'caregiver', then try to access it under 'client' URL
    ResponseEntity<DocumentResponse> upload = restTemplate.exchange(
        "/api/v1/caregivers/" + caregiver.getId() + "/documents", HttpMethod.POST,
        multipartUpload("secret.txt", "top secret".getBytes(), null), DocumentResponse.class);

    ResponseEntity<String> resp = restTemplate.exchange(
        "/api/v1/clients/" + client.getId() + "/documents/" + upload.getBody().id() + "/content",
        HttpMethod.GET, new HttpEntity<>(auth()), String.class);
    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
}
```

---

### C-2 · Task 2 — Placeholder test with no assertions (violates "No Placeholders" rule)

**Problem:**  
`createCarePlan_returns_409_on_concurrent_version_collision` in `ClientControllerIT` (lines ~322–329 of the plan) is broken: it has no assertions and the plan's own inline note says "this test will need a try/catch or a different approach." The test will compile, run, and **pass silently**, providing zero coverage of the 409 scenario. The plan then immediately offers a replacement (`createCarePlan_increments_version_for_second_plan`) but leaves the broken test in place.

**Fix:** Remove the broken test entirely. The replacement test already follows it and is the one that should ship:

```java
// REMOVE the broken createCarePlan_returns_409_on_concurrent_version_collision test entirely.
// The test below it already covers version-increment behaviour.

@Test
void createCarePlan_increments_version_for_second_plan() {
    CreateCarePlanRequest req = new CreateCarePlanRequest(null);
    restTemplate.exchange("/api/v1/clients/" + client.getId() + "/care-plans",
        HttpMethod.POST, new HttpEntity<>(req, auth()), CarePlanResponse.class);
    ResponseEntity<CarePlanResponse> second = restTemplate.exchange(
        "/api/v1/clients/" + client.getId() + "/care-plans",
        HttpMethod.POST, new HttpEntity<>(req, auth()), CarePlanResponse.class);
    assertThat(second.getBody().planVersion()).isEqualTo(2);
}
```

---

## 3. Previously Addressed Items

None — this is the first review.

---

## 4. Minor Issues & Improvements

### M-1 · Task 5 — `@Transactional` on controller method (architectural violation)

`UserController.listUsers()` is annotated `@Transactional(readOnly = true)`. CLAUDE.md and the existing codebase pattern (`ClientController`, `CaregiverController`) put transactions exclusively in the service layer. The controller annotation is dead weight — the service method already owns the transaction boundary.

**Fix:** Remove `@Transactional(readOnly = true)` from `UserController.listUsers()`.

---

### M-2 · Task 6 — Filesystem delete runs before transaction commits (inconsistency window)

`DocumentService.delete` does:
1. `documentRepository.delete(doc)` — staged, committed at end of `@Transactional` method
2. `storageService.delete(doc.getFilePath())` — executes immediately

If `storageService.delete` throws a `RuntimeException` the transaction rolls back (DB row survives) but the file is already gone → orphaned DB metadata with no backing file. For MVP local filesystem this is low probability but worth an explanatory comment so the implementer doesn't stumble over it later:

```java
@Transactional
public void delete(UUID documentId, UUID ownerId) {
    Document doc = requireDocument(documentId, ownerId);
    documentRepository.delete(doc);
    // Note: filesystem delete runs before commit. If storageService.delete throws,
    // the transaction rolls back (DB row survives). If commit fails after a successful
    // filesystem delete, the file is orphaned. Acceptable for MVP local storage.
    storageService.delete(doc.getFilePath());
}
```

---

### M-3 · Task 6 — `DocumentController` does not use class-level `@RequestMapping` (inconsistent with codebase)

Every other controller in the codebase (`ClientController`, `CaregiverController`, `AgencyController`) uses `@RequestMapping("/api/v1/...")` at the class level and short method-level annotations (`@GetMapping`, `@PostMapping("/{id}")`, etc.). `DocumentController` inlines full paths in every method. This makes diffs noisy and the path harder to refactor.

**Fix:** Add class-level mappings and shorten method mappings. Use two separate controllers if the mixed `/clients/…` and `/caregivers/…` paths make a single base path impractical — or accept two `@RequestMapping`-less method paths at the cost of inconsistency and note why.

---

### M-4 · Task 3 — `CaregiverControllerIT` missing credential and background-check endpoint coverage

The IT covers list, create, get, update, and availability. The caregiver resource also exposes `POST/GET /credentials`, `DELETE /credentials/{id}`, and `POST/GET /background-checks`. These are existing endpoints with non-trivial ownership checks (`deleteCredential` verifies the credential belongs to the specified caregiver). They should have at least a happy-path and a cross-ownership 404 test.

---

### M-5 · Task 4 — `@NotBlank` redundant on `state` field in `RegisterAgencyRequest`

```java
@NotBlank @Size(min = 2, max = 2) @Pattern(regexp = "[A-Z]{2}", ...) String state
```

`@Size(min=2, max=2)` already rejects null (produces a constraint violation) and blank/empty strings. `@NotBlank` is redundant. Not harmful, but creates noise. Remove `@NotBlank` from `state`.

---

## 5. Questions for Clarification

1. **Email uniqueness scope (Task 5):** `inviteUser` uses `userRepository.findByEmail(req.email()).isPresent()` — the same global check used in agency registration. Is it intentional that the same email address cannot be used at two different agencies? This implies email is a global login identifier, which is consistent with the current `AuthService.login` implementation but worth confirming.

2. **Document content-type on download:** The download endpoint always returns `APPLICATION_OCTET_STREAM`. Should it attempt to infer `Content-Type` from the original filename extension (e.g., `application/pdf` for `.pdf` files)? Not blocking, but relevant to the mobile app's ability to render inline.

3. **`@AfterEach` cleanup in `DocumentControllerIT`:** The cleanup deletes `/tmp/hcare-test-docs` recursively after each test. Since `@Sql` only truncates the DB, if an upload test fails mid-way the file may or may not be present. This is fine for correctness but means tests are not fully isolated from each other's filesystem state. Acceptable for MVP?

---

## 6. Final Recommendation

**Approve with changes.**

Fix C-1 and C-2 before any agent begins Task 6 and Task 2 respectively. M-1 (controller `@Transactional`) and M-3 (`@RequestMapping` consistency) are quick in-place fixes. M-4 (credential/background-check IT coverage) can be addressed in the same commit as Task 3 with minimal extra test code.
