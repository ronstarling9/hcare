# Critical Implementation Review #3
## Plan: Clients & Caregivers REST API (2026-04-06)
## Reviewer: Senior Staff Engineer | Date: 2026-04-06

---

## 1. Overall Assessment

Significant progress has been made across reviews: C1, C2, N1, and C4 are all fully resolved in this revision. The three compilation-blocking bugs that prevented execution in Review #2 are gone. However, two new issues have been identified in this pass: the `createCarePlan` version counter has the same class of concurrency bug that was fixed for `activateCarePlan`, and the `createCarePlan` test is structurally broken (it stubs a method the service no longer calls, meaning the version-numbering logic goes completely untested). Several lower-priority open items from prior reviews also remain.

---

## 2. Critical Issues

### N10 (NEW) — `createCarePlan` version counter is not concurrency-safe
**Severity: Data integrity / duplicate version numbers**

`createCarePlan` reads the current max version and increments it:
```java
int nextVersion = carePlanRepository.findMaxPlanVersionByClientId(clientId) + 1;
CarePlan plan = new CarePlan(clientId, agencyId, nextVersion);
carePlanRepository.save(plan);
```

Two simultaneous POST requests to `/{clientId}/care-plans` will both read the same max (e.g., `1`), both compute `nextVersion = 2`, and both save a `CarePlan` with `planVersion = 2`. If `planVersion` is part of the unique business identity of a care plan, this produces a silent data integrity failure. Review #1's C3 identified and fixed this exact race for `activateCarePlan` via `@Lock(PESSIMISTIC_WRITE)`, but `createCarePlan` was not reviewed with the same lens.

**Fix — two acceptable options:**

Option A (consistent with the existing pattern): Add `@Lock(LockModeType.PESSIMISTIC_WRITE)` to `findMaxPlanVersionByClientId` in `CarePlanRepository`. This serializes concurrent creates for the same client.

Option B: Add a `@Version`-based optimistic lock on `CarePlan` and catch `ObjectOptimisticLockingFailureException` at the controller layer, rethrowing as 409. This is lighter-weight for read-heavy scenarios.

If the plan accepts that concurrent care plan creation is rare enough to ignore, add a TODO comment and a DB-level `UNIQUE(client_id, plan_version)` constraint in a follow-up Flyway migration to catch it defensively.

---

### N11 (NEW) — `createCarePlan` test stubs a dead repository method; version logic goes untested
**Severity: Test coverage gap / false confidence**

In Task 3 Step 1, the test stubs:
```java
when(carePlanRepository.findByClientIdOrderByPlanVersionAsc(clientId))
    .thenReturn(List.of(existingPlan));
```

But the service now calls `carePlanRepository.findMaxPlanVersionByClientId(clientId)` (the JPQL aggregate added per M3 from Review #1). `findByClientIdOrderByPlanVersionAsc` is never called. Mockito's default return for an unstubbed `int` method is `0`, so `nextVersion = 0 + 1 = 1`. The test then asserts `planVersion = 2` — which passes only because `when(carePlanRepository.save(any())).thenReturn(saved)` returns a hardcoded `CarePlan` with version 2 regardless of what the service computed. The version-numbering logic (`findMaxPlanVersionByClientId`) is not exercised at all.

**Fix:** Replace the dead stub with the correct one and assert on what the service actually computes:
```java
when(carePlanRepository.findMaxPlanVersionByClientId(clientId)).thenReturn(1);
// ...
assertThat(result.planVersion()).isEqualTo(2);
```
Also configure `when(carePlanRepository.save(any())).thenAnswer(inv -> inv.getArgument(0))` so the returned plan reflects the service-constructed version rather than a hardcoded value.

---

## 3. Previously Addressed Items

The following issues from Reviews #1 and #2 have been fully resolved:

- **C1** — `BackgroundCheck.setRenewalDueDate` missing: Task 7 Step 4a now explicitly adds this setter and names `BackgroundCheck.java` in the File Map.
- **C2** — `ShiftSummaryResponse` positional constructor: Task 7 Step 4c now directs the implementer to read `ShiftSummaryResponse.java` and Step 5 uses `ShiftSummaryResponse::from`.
- **N1** — `@DateTimeFormat` missing from `CaregiverController`: Task 7 Step 6 import list now includes `import org.springframework.format.annotation.DateTimeFormat;`.
- **C4** — `ServiceType.getAgencyId()` unguarded assumption: Task 4 Step 0 now provides a fully explicit branch with both the "has `agencyId`" and "global" paths.
- **C3**, **M1–M6** — all confirmed resolved in Review #2 and remain so.

---

## 4. Minor Issues & Improvements

### M7 (STILL UNRESOLVED) — Dead mock in `activateCarePlan_throws_409_when_plan_already_active`

The stub `when(carePlanRepository.findByClientIdAndStatus(clientId, CarePlanStatus.ACTIVE)).thenReturn(Optional.of(plan))` is still set up in this test (lines 1148–1149). The service throws 409 at `plan.getStatus() == ACTIVE` check before that query is reached. The stub remains misleading. Remove it from this test case.

### N2 (STILL UNRESOLVED) — `CarePlan.review(UUID clinicianId)` domain method is unverified

`createCarePlan` calls `plan.review(req.reviewedByClinicianId())` when a clinician ID is provided. No step verifies this method exists on the `CarePlan` entity, and no test exercises the non-null `reviewedByClinicianId` path. Add a verification step before Task 3 Step 5:
> Read `backend/src/main/java/com/hcare/domain/CarePlan.java`. Confirm `review(UUID)`, `activate()`, and `supersede()` all exist. If `review()` is absent, add it (sets `reviewedByClinicianId` and `reviewedAt = LocalDateTime.now(ZoneOffset.UTC)`).

### N3 (STILL UNRESOLVED) — No overlap check for authorization periods

`createAuthorization` validates `endDate > startDate` but does not detect overlapping authorizations for the same `clientId + payerId + serviceTypeId`. Two authorizations `2026-01-01 → 2026-06-30` and `2026-04-01 → 2026-12-31` would both save, creating ambiguous EVV billing periods. Low-priority for MVP but should be addressed before production.

### N4 (STILL UNRESOLVED) — `SetAvailabilityRequest.blocks` allows empty list

`@NotNull @Valid List<AvailabilityBlockRequest> blocks` permits `[]`, which silently deletes all existing availability and saves nothing. If clearing availability is intentional, add a test `setAvailability_with_empty_blocks_clears_all`. If not, add `@Size(min = 1)` to `blocks`.

### N12 (NEW) — Missing test for availability overlap detection

The O(n²) overlap check added to `setAvailability` (Task 7 Step 5) has no corresponding test. The three existing availability tests cover time-order validation, exact-duplicate blocks, and the happy path — but none tests overlapping (non-identical) blocks such as `MON 08:00–16:00` and `MON 10:00–18:00`. Add:
```java
@Test
void setAvailability_rejects_overlapping_blocks() {
    Caregiver caregiver = makeCaregiver();
    when(caregiverRepository.findById(caregiverId)).thenReturn(Optional.of(caregiver));
    when(availabilityRepository.findByCaregiverId(caregiverId)).thenReturn(List.of());

    assertThatThrownBy(() -> service.setAvailability(agencyId, caregiverId,
        new SetAvailabilityRequest(List.of(
            new AvailabilityBlockRequest(DayOfWeek.MONDAY,
                LocalTime.of(8, 0), LocalTime.of(16, 0)),
            new AvailabilityBlockRequest(DayOfWeek.MONDAY,
                LocalTime.of(10, 0), LocalTime.of(18, 0))))))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("400");
}
```

### N13 (NEW) — `Authorization.getUsedUnits()` is unverified

`AuthorizationResponse.from(a)` calls `a.getUsedUnits()`. The CLAUDE.md states authorizations track "real-time utilization," which implies `usedUnits` may be computed dynamically from shift history rather than stored as a persisted field. If `getUsedUnits()` doesn't exist on the entity, this is a compilation error. If it returns null (no shifts yet), the `AuthorizationResponse` will serialize `usedUnits: null` — which may or may not be intentional.

Add a verification step at the top of Task 4 (alongside the Step 0 for `ServiceType`):
> Read `backend/src/main/java/com/hcare/domain/Authorization.java`. Confirm `getUsedUnits()` exists and its return type. If `usedUnits` is stored: proceed as written. If it's computed (e.g., via a `@Formula` or not present): adjust `AuthorizationResponse.from()` to use the correct method or omit the field.

### N14 (NEW) — `UpdateMedicationRequest.name` allows empty string

`UpdateMedicationRequest` has no length constraint on `name`. A PATCH with `{"name": ""}` will silently set the medication name to an empty string. Review #1's M1 applied `@Size(min = 1)` to name fields in `UpdateClientRequest` and `UpdateCaregiverRequest` but `UpdateMedicationRequest` was not included in that fix. Add `@Size(min = 1) String name`.

---

## 5. Questions for Clarification

**Q3 (STILL OPEN):** Does `FamilyPortalUserRepository.findByAgencyIdAndEmail(UUID agencyId, String email)` already exist as a named derived query? Task 4 Step 5 calls it directly in the service without any step in Task 4 that adds it to the repository interface. If it does not exist, the service will not compile. Add a verification step (or an explicit repository interface addition) to Task 4.

**Q4 (STILL OPEN):** Are ADL tasks and goals permitted to be added/removed from an ACTIVE care plan? The plan imposes no restriction, but the care plan lifecycle (DRAFT → ACTIVE → SUPERSEDED) may imply only DRAFT plans should be mutable. If ACTIVE plans are immutable after activation, `addAdlTask`, `deleteAdlTask`, `addGoal`, `updateGoal`, and `deleteGoal` should check `plan.getStatus() == DRAFT` and throw 422 otherwise.

---

## 6. Final Recommendation

**Approve with changes**

The three compile-blocking bugs from Review #2 are all resolved. The plan is now structurally executable. Before starting Task 3, fix N11 (the dead stub in `createCarePlan` test) and decide how to handle N10 (the version counter concurrency gap). Before Task 4, confirm Q3 (`FamilyPortalUserRepository.findByAgencyIdAndEmail` existence) and N13 (`Authorization.getUsedUnits()` existence). N12 (missing overlap test) should be added to Task 7 Step 1 in the same pass. The remaining open items (M7, N2, N3, N4, Q4) are lower priority and can be addressed in a follow-up or as part of the current pass if convenient.
