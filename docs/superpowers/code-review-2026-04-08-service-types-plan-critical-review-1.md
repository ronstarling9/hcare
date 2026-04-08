# Critical Pre-Implementation Review: Service Types API Plan
**Plan file:** `docs/superpowers/plans/2026-04-08-service-types-api.md`
**Reviewer:** Senior Code Review Agent
**Date:** 2026-04-08
**Status:** Pre-implementation (no code written yet)

---

## CRITICAL ISSUES

### C1 — `ServiceType` constructor argument order is wrong in unit tests

The plan's `ServiceTypeServiceTest` constructs `ServiceType` with the signature:
```java
new ServiceType(agencyId, "bravo", "BR", false, "[]");
```
But the actual constructor in `ServiceType.java` (line 39–46) is:
```java
public ServiceType(UUID agencyId, String name, String code, boolean requiresEvv, String requiredCredentials)
```
The plan passes `"bravo"` as `name` and `"BR"` as `code` — that is actually correct for that specific test. However, in the credentials test the constructor is called as:
```java
new ServiceType(agencyId, "PCS", "PCS", true, "[\"CPR\",\"FIRST_AID\"]");
```
This produces `name = "PCS"` and `code = "PCS"` which is fine. The constructor argument order is confirmed correct against the entity. This is not actually broken — verified correct. Reclassifying to CONFIRMED CORRECT. (See that section.)

### C2 — IT test `@Sql` truncation list is missing `service_types`

The plan instructs the agent to follow the `ShiftSchedulingControllerIT` truncation pattern. That IT truncates `service_types` in its `@Sql`:
```
TRUNCATE TABLE shift_offers, shifts, recurrence_patterns, authorizations, caregiver_scoring_profiles,
caregiver_client_affinities, caregivers, service_types, clients, agency_users, agencies RESTART IDENTITY CASCADE
```

The `ServiceTypeControllerIT` test needs to seed two agencies with different service types and assert tenant isolation. If the agent copies the truncation list from `ClientControllerIT` (which does NOT include `service_types`), cross-test contamination will occur. The plan says "follow the `AbstractIntegrationTest` pattern used in `ShiftSchedulingControllerIT`" but does not spell out the exact truncation list for `ServiceTypeControllerIT`. Given the test only touches `agencies`, `agency_users`, and `service_types`, the minimum correct truncation is:

```sql
TRUNCATE TABLE shift_offers, shifts, recurrence_patterns, authorizations,
caregiver_scoring_profiles, caregiver_client_affinities, caregivers,
service_types, clients, agency_users, agencies RESTART IDENTITY CASCADE
```

The plan leaves this entirely unspecified (Task 3, Step 1 says only "Seed two agencies..."). Without the complete truncation statement, test contamination from other ITs running in the same Testcontainers instance is likely, causing flaky test ordering failures. The plan **must** include the full `@Sql` statement.

### C3 — `ServiceTypeControllerIT` test count mismatch: plan says 4, but IT covers 4 behaviors correctly only if the empty-agency test is seeded correctly

Task 3 Step 4 asserts `Tests run: 4`. The four behaviors named are:
1. Agency A sees only agency A types, sorted alphabetically
2. Agency B sees only agency B types
3. Agency with no service types returns `200 []`
4. `401` without a token

Test 3 (empty agency) requires a third agency user with a valid token but no service types in the DB. The `@BeforeEach` seed in the plan is not specified — if the agent seeds only agencies A and B and their types, there is no seeded user for the empty-agency assertion. The plan must clarify that a third agency (agency C) and a corresponding `AgencyUser` need to be seeded so the empty-list test can actually authenticate and hit the endpoint. Without this, test 3 will fail with 401 rather than 200.

---

## GAPS & CORRECTIONS

### G1 — `code` field: plan types it as `string | null` in TypeScript, but the DB column is `NOT NULL`

Task 4 Step 1 defines:
```typescript
code: string | null
```
The migration (`V3__core_domain_schema.sql` line 37) has `code VARCHAR(50) NOT NULL`. The Java entity also has no `@Nullable` on `code`. The backend will never return `null` for `code`. The type should be `string`, not `string | null`. Using `string | null` is defensively typed but misrepresents the contract and forces unnecessary null checks in consumers. Correct to `string`.

### G2 — `requiredCredentials` null guard in `ServiceTypeService` is unreachable but misleading

The `required_credentials` DB column is `NOT NULL DEFAULT '[]'`. The constructor also has no null check. The Jackson parse catch block handles malformed JSON, which is correct — but the comment "log warn, return empty list (don't 500)" implies `null` is also handled. It is not — `objectMapper.readValue(null, ...)` would throw `IllegalArgumentException`, not be caught by the broad `catch (Exception e)`. This is actually fine in practice because the column is `NOT NULL`, but the plan overstates what the guard protects against. No code change needed, but the comment in the plan is inaccurate.

### G3 — Dev data seed uses `"Personal Care Services"` not `"PCS"` as the name

The plan's Manual Test Checkpoint (step 1) says:
> Expected: JSON array with PCS and SNV, sorted alphabetically.

But `DevDataSeeder.java` (line 294–298) seeds:
- `name = "Personal Care Services"`, `code = "PCS"`
- `name = "Skilled Nursing Visit"`, `code = "SNV"`

The endpoint returns `name` in the response, not `code`. Sorted alphabetically, the order will be "Personal Care Services" then "Skilled Nursing Visit" — not "PCS" then "SNV". The manual test checkpoint description is misleading. The `jq .` output will show full names. This is only a documentation problem with the checkpoint, not a code problem, but it will confuse the agent doing the manual check.

### G4 — `serviceTypePcs` i18n key is also referenced by the hardcoded `<option>` in `NewShiftPanel.tsx`

Task 7 says to remove `serviceTypePcs` from `newShift.json`. That key is currently used on line 113 of `NewShiftPanel.tsx`:
```tsx
<option value="st000000-0000-0000-0000-000000000001">{t('serviceTypePcs')}</option>
```
The plan does correctly remove this entire `<option>` block in Task 8 Step 3 (replacing the hardcoded block). However, Task 7 (i18n) is committed separately before Task 8 (component update). Between those two commits, the running app will have a broken key reference (`serviceTypePcs` missing from JSON while still referenced in TSX). This creates a broken state in the commit history. The plan should either combine Tasks 7 and 8 into one commit, or move the key removal to Task 8 so it is atomic with the TSX change.

### G5 — `useServiceTypes` hook does not use `useAuthStore` — but `useClients` does not either

The plan instructs the agent to follow `useClients.ts` exactly. `useClients.ts` does not read the auth store directly (React Query's `apiClient` carries the JWT interceptor). `useServiceTypes` is correct not to import the store. This is confirmed correct — no gap here. Noted for completeness.

### G6 — `NewShiftPanel` submit button disabled condition includes `serviceTypesError` as a boolean, but TypeScript treats React Query `isError` as `boolean`

The plan's submit disabled expression:
```tsx
disabled={isSubmitting || createMutation.isPending || serviceTypesLoading || serviceTypesError || serviceTypes.length === 0}
```
`serviceTypesError` from `useQuery` is `boolean`. In React, a boolean in a JSX expression is fine — this evaluates correctly. No TypeScript error. Confirmed correct.

### G7 — `aria-busy` accepts `boolean | "true" | "false"`, not raw `boolean` in JSX

The plan renders:
```tsx
aria-busy={serviceTypesLoading}
```
`serviceTypesLoading` is a `boolean`. The `aria-busy` ARIA attribute expects a string `"true"` or `"false"`. JSX will coerce `false` to nothing (attribute omitted) and `true` to `"true"` — browsers handle this correctly, but TypeScript with strict DOM typings may emit a type error depending on the tsconfig. The agent should use:
```tsx
aria-busy={serviceTypesLoading ? "true" : "false"}
```
or verify the tsconfig allows boolean for aria attributes. This is worth checking before the TypeScript compile step.

### G8 — Plan does not specify where in `types/api.ts` to insert the new interface

Task 4 Step 1 says "Add the interface after the Payers section." The Payers section ends at line 201 (`createdAt: string`) and line 202 closes the interface. The next section is `Dashboard`. The plan's location instruction is correct, but it should also note that `export interface ServiceTypeResponse` must have the `export` keyword — all other interfaces in that file are exported. The plan's code snippet includes `export`, so this is correct.

---

## RISKS

### R1 — `AbstractIntegrationTest` provides no auth helper; the IT must implement `token()` and `auth()` itself

`AbstractIntegrationTest` (lines 1–34) only provides the Testcontainers `POSTGRES` container and `DynamicPropertySource`. It has no `token()`, `auth()`, or `restTemplate` — those are autowired in each concrete IT class individually (visible in both `ShiftSchedulingControllerIT` and `ClientControllerIT`). The plan correctly states "following the `AbstractIntegrationTest` pattern used in `ShiftSchedulingControllerIT`", but since Task 3 Step 1 leaves the IT body entirely to the agent ("The test must: ..."), there is risk the agent forgets to include `@Autowired private TestRestTemplate restTemplate`, `@Autowired private PasswordEncoder passwordEncoder`, and the `token()` / `auth()` helper methods. These are not optional — without them the IT will not compile. The plan should include the full boilerplate skeleton for the IT, not just a bullet-list of behaviors.

### R2 — `@Sql` truncation order must respect FK constraints; wrong order causes `ERROR: insert or update on table violates foreign key constraint`

The FK chain for `service_types` is: `agencies` → `service_types` → `shifts`, `authorizations`, `recurrence_patterns`. Truncating with `CASCADE` on all tables in one statement works, but only if `service_types` appears before `agencies` in the truncation list. PostgreSQL `TRUNCATE ... CASCADE` truncates all referencing tables automatically, so ordering within the list does not matter when `CASCADE` is present. However, if the agent omits `CASCADE`, the truncation will fail. The plan does not explicitly show the `@Sql` annotation for the IT — this leaves the agent to infer it. Given the existing ITs all use `RESTART IDENTITY CASCADE`, this is likely to be copied correctly, but it is an unmitigated risk.

### R3 — No `selectServiceType` i18n key addition despite it being required in the loaded state

In Task 8 Step 3, the loaded state renders:
```tsx
<option value="">{t('selectServiceType')}</option>
```
The key `selectServiceType` already exists in `newShift.json` (line 10: `"selectServiceType": "Select service type…"`). This is confirmed correct — no gap. But the plan's Task 7 (i18n) only removes `serviceTypePcs` and adds 5 new keys. It makes no mention of verifying `selectServiceType` exists. If the agent misreads the task and also removes `selectServiceType`, the loaded state option will show the raw key string. The plan should explicitly note that `selectServiceType` must be retained.

### R4 — `caregiverPhaseNote` dead key in `newShift.json` is not removed

`newShift.json` contains `"caregiverPhaseNote": "Phase 4 will populate this list from the API."` This key is already dead — no reference to it appears in `NewShiftPanel.tsx`. The plan does not clean it up. This is a minor hygiene issue, not a functional risk, but a thorough plan would flag it.

### R5 — `mockAlert` dead key in `newShift.json` is not removed

Similarly, `"mockAlert"` at line 20 of `newShift.json` references Phase 4 wiring language that is now obsolete. The plan ignores it. Same category as R4 — not functional, but worth cleaning.

### R6 — The plan's TDD sequence for the IT may confuse the agent: "Run tests — confirm they fail (404)" is wrong

Task 3 Step 2 says:
> Expected: compilation failure (404, endpoint not yet mapped)

A 404 from `MockMvc` / `TestRestTemplate` is a runtime response, not a compilation failure. Integration tests fail at runtime with a 404 (or the test assertion fails because it expected 200). The IT will compile fine since the `ServiceTypeControllerIT` class only references `AbstractIntegrationTest`, `TestRestTemplate`, and domain classes that already exist. The wording "compilation failure" is incorrect and may confuse the agent. The correct expectation is: "Tests run but assertions fail — endpoints return 404 Not Found."

---

## CONFIRMED CORRECT

**Entity field names and getters:** `ServiceType.java` has `getName()`, `getCode()`, `isRequiresEvv()`, `getRequiredCredentials()`, `getId()` exactly as the plan assumes. The constructor signature `(UUID agencyId, String name, String code, boolean requiresEvv, String requiredCredentials)` matches all test invocations.

**Repository:** `ServiceTypeRepository.findByAgencyId(UUID agencyId)` exists and returns `List<ServiceType>` exactly as the plan assumes.

**DB column names:** `service_types` table has `id`, `agency_id`, `name`, `code`, `requires_evv`, `required_credentials`, `created_at` — all match plan assumptions.

**`NOT NULL` constraints:** All fields used in the DTO are `NOT NULL` in the migration, so null-safety in the Java record is correct.

**Package pattern:** `com.hcare.api.v1.servicetypes` correctly mirrors `com.hcare.api.v1.payers`. `PayerController` is confirmed at that package path.

**`SecurityConfig` — no changes needed:** `anyRequest().authenticated()` covers all `/api/v1/**` endpoints generically. The new `/api/v1/service-types` endpoint will be protected by the existing filter without any `SecurityConfig` modification. The CORS wildcard already covers GET requests from `localhost:5173`. No security config changes are required.

**`@PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")`:** Matches the pattern used in `ShiftSchedulingController` and `PayerController` verbatim.

**`UserPrincipal.getAgencyId()`:** Used identically in `ShiftSchedulingController` and `PayerController`; the plan's controller code is correct.

**Frontend hook pattern:** `useServiceTypes` with `staleTime: 300_000` and `query.data ?? []` matches the spirit of `useAllClients()` (which uses `staleTime: 60_000`). The longer stale time is appropriate given service types are more static than client lists.

**`listServiceTypes` API function:** Calling `apiClient.get<ServiceTypeResponse[]>('/service-types')` and returning `response.data` directly (no `.content` unwrap) is correct — the endpoint returns a plain `List`, not a `Page`.

**`newShift` namespace registered:** `frontend/src/i18n.ts` includes `'newShift'` in the `ns` array (line 19). No registration change needed.

**`serviceTypePcs` key exists and is a valid removal target:** Confirmed present in `newShift.json` line 11. Plan correctly identifies it as dead after the dynamic select is implemented.

**Dev data seeded correctly:** `DevDataSeeder.java` seeds PCS and SNV service types for all three dev agencies (sunrise, golden, harmony). No new migration or seed data is needed.

**`@Transactional(readOnly = true)`:** Correct annotation for a read-only query method in the service layer. Consistent with `PayerService`.

**IT base class:** `AbstractIntegrationTest` uses `@SpringBootTest(webEnvironment = RANDOM_PORT)`, `@Testcontainers`, `@ActiveProfiles("test")`, and Testcontainers PostgreSQL. The plan correctly instructs the agent to extend it.

**Alphabetical sort logic:** `Comparator.comparing(ServiceType::getName, String.CASE_INSENSITIVE_ORDER)` is the exact same pattern used in `PayerService.listPayers()`. Correct.

**Jackson `ObjectMapper` injection:** `ObjectMapper` is a Spring-managed bean in all Spring Boot apps. Injecting it via constructor is correct and testable (the unit test passes `new ObjectMapper()` directly).

---

## SUMMARY OF ACTIONS REQUIRED BEFORE IMPLEMENTATION

1. **C2 (Critical):** Add the full `@Sql` truncation statement to Task 3 Step 1 for `ServiceTypeControllerIT`. Must include `service_types` in the truncation list.

2. **C3 (Critical):** Clarify Task 3 Step 1 to require seeding a third agency with a user but no service types for the empty-list test case. Without this, the "empty returns 200 []" test will fail with 401.

3. **G4 (Important):** Reorder or combine Tasks 7 and 8 so the removal of `serviceTypePcs` from `newShift.json` is atomic with its removal from `NewShiftPanel.tsx`. A broken intermediate commit is avoidable.

4. **G1 (Important):** Change `code: string | null` to `code: string` in the `ServiceTypeResponse` TypeScript interface. The DB contract is `NOT NULL`.

5. **G7 (Suggestion):** Specify `aria-busy={serviceTypesLoading ? "true" : "false"}` to be safe with strict TypeScript DOM typings.

6. **R1 (Risk):** Provide the full IT boilerplate skeleton in Task 3 Step 1 (autowired fields, `@BeforeEach seed()`, `token()`, `auth()` helper). Do not leave it implicit.

7. **R3 (Risk):** Add an explicit note in Task 7 that `selectServiceType` must be retained in `newShift.json`.

8. **R6 (Minor):** Correct Task 3 Step 2 wording from "compilation failure" to "assertions fail — endpoints return 404 Not Found."

9. **G3 (Minor):** Fix the Manual Test Checkpoint to show that the JSON response will contain `"name": "Personal Care Services"` and `"name": "Skilled Nursing Visit"`, not the codes "PCS" and "SNV".
