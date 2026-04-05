# Core Domain Model — Critical Implementation Review 2

**Plan:** `2026-04-04-core-domain-model.md`
**Reviewer:** critical-implementation-review v1.5.1
**Date:** 2026-04-05
**Prior reviews:** review-1 (2026-04-05)

---

## 1. Overall Assessment

All eleven issues from review-1 were addressed: the three critical bugs (global FPU email, unguarded `visitCount`, `adl_tasks`/`goals` missing `created_at`) and all eight minor issues. The domain model is substantially cleaner. This second pass surfaces two new critical design gaps — both are entity API completeness problems that will block Plans 6 (Admin API) and 3 (Scheduling) from building on this layer — and five new minor issues.

---

## 2. Critical Issues

### C4 — `CarePlan.reviewedByClinicianId` and `reviewedAt` are permanently null — HHCS compliance blocked

**Description:** `CarePlan` has `activate()` and `supersede()` lifecycle methods but no corresponding `review(UUID clinicianId)` method. Both `reviewedByClinicianId` and `reviewedAt` are not accepted by the constructor and have no setters. They can only ever be `null` through the current entity API — even though the plan comment explicitly states: *"HHCS (skilled nursing) plans require clinical review sign-off to satisfy state-level clinical supervision requirements."*

**Why it matters:** HHCS plan clinical review sign-off is a compliance requirement, not a nice-to-have. Plan 6 (Admin API) will implement the endpoint that records a clinician's approval. Without a domain method to set these fields, Plan 6 will have to work around the entity API by either using a JPA setter (breaking encapsulation) or modifying the entity — and neither is obvious from reading this plan.

**Fix:** Add a `review()` lifecycle method consistent with `activate()` and `supersede()`:

```java
public void review(UUID clinicianId) {
    this.reviewedByClinicianId = clinicianId;
    this.reviewedAt = LocalDateTime.now();
}
```

Add a test in `CarePlanDomainIT`:
```java
@Test
void review_sets_clinician_and_timestamp() {
    CarePlan plan = carePlanRepo.save(new CarePlan(client.getId(), agency.getId(), 1));
    UUID clinician = UUID.randomUUID();
    plan.review(clinician);
    carePlanRepo.save(plan);

    CarePlan loaded = carePlanRepo.findById(plan.getId()).orElseThrow();
    assertThat(loaded.getReviewedByClinicianId()).isEqualTo(clinician);
    assertThat(loaded.getReviewedAt()).isNotNull();
}
```

---

### C5 — `Document.documentType` and `uploadedBy` are permanently null — document management blocked

**Description:** The `Document` constructor takes only `(agencyId, ownerType, ownerId, fileName, filePath)`. Neither `documentType` nor `uploadedBy` are accepted at construction time, and neither has a setter. Both fields are mapped to nullable columns and will always be `null` in production code.

**Why it matters:** `documentType` categorizes the document (e.g., `"CARE_PLAN"`, `"CREDENTIAL"`, `"CONSENT_FORM"`). `uploadedBy` records which `AgencyUser` uploaded it — required for PHI audit trails. Plan 6 admin document upload endpoint will need both. Without setters, Plan 6 implementers will either bypass the domain layer or be forced to modify the entity — neither is clean.

**Fix:** Add a constructor overload that accepts these optional fields, or add setters:

```java
public void setDocumentType(String documentType) { this.documentType = documentType; }
public void setUploadedBy(UUID uploadedBy) { this.uploadedBy = uploadedBy; }
```

Update the `ClientSubEntitiesIT` document test to exercise them:
```java
Document doc = new Document(agency.getId(), DocumentOwnerType.CLIENT, client.getId(),
    "care_plan_v1.pdf", "/storage/agency-123/care_plan_v1.pdf");
doc.setDocumentType("CARE_PLAN");
doc.setUploadedBy(adminUserId);
documentRepo.save(doc);

Document loaded = documentRepo.findById(doc.getId()).orElseThrow();
assertThat(loaded.getDocumentType()).isEqualTo("CARE_PLAN");
assertThat(loaded.getUploadedBy()).isEqualTo(adminUserId);
```

---

## 3. Previously Addressed Items (from review-1)

- **C1** — `FamilyPortalUser.email` global uniqueness: V4 migration changes to `UNIQUE (agency_id, email)`, entity removes `unique=true`, repository uses `findByAgencyIdAndEmail()`. ✅
- **C2** — `CaregiverClientAffinity.incrementVisitCount()` concurrency: V4 migration adds `version BIGINT NOT NULL DEFAULT 0`, entity adds `@Version Long version`. ✅
- **C3** — `adl_tasks`/`goals` missing `created_at`: already present in actual V3 migration and entities (was fixed during quality review pass). ✅
- **M1** — `ClientRepository_Stub` placeholder: replaced with `// TODO after Task 5` comment. ✅
- **M2** — Missing indexes on `authorizations.payer_id`/`service_type_id`: present in V3 migration (lines 265–266). ✅
- **M3** — Missing index on `caregiver_client_affinities.client_id`: present in V3 migration (line 162). ✅
- **M4** — No test for `updateAfterShiftCompletion()`: added to `CaregiverSubEntitiesIT`. ✅
- **M5** — No test for `findByClientIdAndStatus()`: added to `CarePlanDomainIT`. ✅
- **M6** — Misleading test name `background_check_defaults_to_pending`: already renamed to `background_check_result_is_persisted`. ✅
- **M7** — No test for `recordLogin()`: added to `ClientDomainIT`. ✅
- **M8** — `CaregiverAvailability` no guard on `startTime >= endTime`: `IllegalArgumentException` guard added to constructor with P1 scope comment. ✅

---

## 4. Minor Issues & Improvements

### M9 — `BackgroundCheck.renewalDueDate` is permanently null after construction

The `BackgroundCheck` constructor takes `(caregiverId, agencyId, checkType, result, checkedAt)` but not `renewalDueDate`. No setter exists. Credential-tracking workflows (Plan 6 admin) need to record when a background check must be renewed — e.g., OIG checks typically require annual renewal. **Fix:** Add a setter:

```java
public void setRenewalDueDate(LocalDate renewalDueDate) { this.renewalDueDate = renewalDueDate; }
```

---

### M10 — `CaregiverAvailability` has no unique constraint — duplicate time windows are silently storable

The DB table and entity allow saving two identical `(caregiver_id, day_of_week, start_time, end_time)` rows. The Plan 3 scheduling engine will query `findByCaregiverId()` and process all windows — duplicates will make a caregiver appear to have double the availability they actually have, causing overbooking. **Fix:**

```sql
-- In migration (V5 or update V3):
CONSTRAINT uq_caregiver_availability UNIQUE (caregiver_id, day_of_week, start_time, end_time)
```

Add `@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"caregiver_id","day_of_week","start_time","end_time"}))` to the entity.

---

### M11 — `CaregiverCredential.verify()` has no test

`CaregiverCredential.verify(UUID adminUserId)` is the mutation Plan 6 will call when an admin marks a credential as verified. No test exists for it. **Fix:** Add to `CaregiverSubEntitiesIT`:

```java
@Test
void credential_verify_sets_verified_flag_and_verifier() {
    UUID adminId = UUID.randomUUID();
    CaregiverCredential cred = credentialRepo.save(new CaregiverCredential(
        caregiver.getId(), agency.getId(), CredentialType.CPR, null, null));
    cred.verify(adminId);
    credentialRepo.save(cred);

    CaregiverCredential loaded = credentialRepo.findById(cred.getId()).orElseThrow();
    assertThat(loaded.isVerified()).isTrue();
    assertThat(loaded.getVerifiedBy()).isEqualTo(adminId);
}
```

---

### M12 — `CaregiverClientAffinityRepository.findByScoringProfileIdAndClientId()` has no test

This is the primary "find or create" lookup Plan 5 uses to increment `visitCount` for a specific caregiver+client pair. If the derived query is malformed, Plan 5 will create duplicate affinity rows instead of incrementing an existing one. **Fix:** Add to `ClientDomainIT` (it already has the affinity setup):

```java
Optional<CaregiverClientAffinity> found = affinityRepo.findByScoringProfileIdAndClientId(
    profile.getId(), client.getId());
assertThat(found).isPresent();
assertThat(found.get().getVisitCount()).isEqualTo(1);
```

---

### M13 — `CarePlanRepository.findByClientId()` returns plans in undefined order

The derived query has no `ORDER BY`. Plan 6 displaying a client's care plan history, and Plan 3 finding the current active plan's ADL tasks, will both receive plans in arbitrary order. For consistency, the history view should show `plan_version ASC` or `created_at DESC`. **Fix:** Rename to a `@Query` with explicit ordering, or rename the derived method to include ordering:

```java
List<CarePlan> findByClientIdOrderByPlanVersionAsc(UUID clientId);
```

---

## 5. Questions for Clarification

**Q4 — `Document.documentType` values:** Should `documentType` be a free-text string or an enum? If it will be validated (e.g., only `CARE_PLAN`, `CREDENTIAL`, `CONSENT_FORM`, `INCIDENT_REPORT` are valid), an enum avoids typos and enables type-safe queries. If it's extensible per agency, free-text with a VARCHAR is correct.

**Q5 — `CaregiverAvailability` unique constraint scope:** Should a caregiver be able to have two overlapping (but not identical) windows on the same day? E.g., MONDAY 08:00–12:00 and MONDAY 10:00–16:00 — these overlap but are not identical. A strict `UNIQUE (caregiver_id, day_of_week, start_time, end_time)` wouldn't catch overlap. If the Plan 3 scheduler needs non-overlapping windows, that validation belongs at the service layer, not the DB constraint.

---

## 6. Final Recommendation

**Approve with changes.**

C4 (`CarePlan.review()` missing) and C5 (`Document` setters missing) should be fixed before Plan 6 starts — they are entity API completeness gaps that will force Plan 6 implementers to bypass the domain model. The minor issues (M9–M13) are lower risk but M10 (duplicate availability windows) and M12 (untested affinity lookup) should be addressed before Plan 3 and Plan 5 respectively.
