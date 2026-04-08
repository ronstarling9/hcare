# Service Types API — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the 409 error on shift creation caused by a hardcoded, non-existent service type UUID in `NewShiftPanel.tsx`. Implement a real `GET /api/v1/service-types` backend endpoint (tenant-scoped, sorted, Jackson-mapped) and wire the frontend dropdown to it via a new API module, hook, and dynamic select with full loading/error/empty UX states and i18n.

**Architecture:** New package-per-resource backend slice (`com.hcare.api.v1.servicetypes`) following the `PayerController` pattern — `ServiceTypeController` → `ServiceTypeService` → existing `ServiceTypeRepository.findByAgencyId()`. No pagination (small bounded list). Frontend: new `serviceTypes.ts` API module + `useServiceTypes` hook (staleTime 5 min) + dynamic `<select>` in `NewShiftPanel` with four render states (loading, error, empty, loaded).

**Tech Stack:** Java 25 / Spring Boot 3.4.4 (backend), React 18 + TypeScript + React Query + Tailwind (frontend), Vitest + Testing Library (frontend tests), JUnit 5 + Mockito (backend tests).

---

## File Map

| File | Action | Purpose |
|------|--------|---------|
| `backend/src/main/java/com/hcare/api/v1/servicetypes/dto/ServiceTypeResponse.java` | **Create** | DTO record: id, name, code, requiresEvv, requiredCredentials |
| `backend/src/main/java/com/hcare/api/v1/servicetypes/ServiceTypeService.java` | **Create** | Fetches by agencyId, sorts by name, maps to DTO with Jackson credential parse |
| `backend/src/main/java/com/hcare/api/v1/servicetypes/ServiceTypeController.java` | **Create** | `GET /api/v1/service-types`, ADMIN+SCHEDULER roles, returns `List<ServiceTypeResponse>` |
| `backend/src/test/java/com/hcare/api/v1/servicetypes/ServiceTypeServiceTest.java` | **Create** | Unit: mock repo, alphabetical sort, credential parse, empty-agency |
| `backend/src/test/java/com/hcare/api/v1/servicetypes/ServiceTypeControllerIT.java` | **Create** | IT: two agencies, tenant isolation, 401 without token, empty returns `[]` |
| `frontend/src/types/api.ts` | Modify | Add `ServiceTypeResponse` interface |
| `frontend/src/api/serviceTypes.ts` | **Create** | `listServiceTypes()` — `GET /api/v1/service-types` |
| `frontend/src/hooks/useServiceTypes.ts` | **Create** | `useServiceTypes()` hook, staleTime 300_000 |
| `frontend/src/hooks/useServiceTypes.test.ts` | **Create** | Hook unit tests: populated, before load, API 500 |
| `frontend/src/components/schedule/NewShiftPanel.tsx` | Modify | Replace hardcoded `<select>` with dynamic; update submit disabled logic |
| `frontend/src/components/schedule/NewShiftPanel.test.tsx` | **Create** | Component tests: loading, error, empty, loaded states |
| `frontend/public/locales/en/newShift.json` | Modify | Add 5 i18n keys (Task 7); remove `serviceTypePcs` atomically with its TSX reference (Task 8) |

---

## Design Decisions

### Backend

- **Multi-tenancy:** `ServiceType` is tenant-scoped. Use `findByAgencyId(agencyId)` explicitly — do NOT rely on implicit `findAll()` + Hibernate `agencyFilter`. This matches the pattern in `ShiftRepository` and `PayerRepository`.
- **Repository:** `ServiceTypeRepository` already exists with `findByAgencyId` — no changes needed.
- **No pagination:** Small bounded list (1–25 caregiver agencies). Return `List<ServiceTypeResponse>` directly, not `Page<>`.
- **New package:** `com.hcare.api.v1.servicetypes` — follows the `PayerController` package-per-resource pattern.
- **`requiredCredentials`:** Stored as JSON TEXT in the DB. Parse with Jackson `ObjectMapper` in the service layer. On parse failure: log warn, return empty list (don't 500).
- **Dev data:** Already seeded — `DevDataSeeder` seeds PCS and SNV for all three dev agencies. No new migration needed.

### Frontend

- **New API module:** `frontend/src/api/serviceTypes.ts` — one file per domain, consistent with `clients.ts`, `shifts.ts`, `payers.ts`.
- **staleTime 5 min:** Service types rarely change. Cache avoids redundant fetches when panel opens/closes repeatedly.
- **Four select states:** loading (disabled + placeholder), error (disabled + inline error), empty (disabled + hint), loaded (normal with options).
- **No auto-select:** Require explicit selection even with one option — service type affects billing and EVV compliance.
- **Submit disabled** when: `isSubmitting || isPending || serviceTypesLoading || serviceTypesError || serviceTypes.length === 0`.

### UX

- Field order unchanged: Client → Service Type → Date → Time → Caregiver.
- No spinner — none exist in the codebase; disabled select with placeholder text is the pattern.
- No retry button on error — consistent with app-wide pattern. User closes and reopens the panel.
- `aria-busy`, `aria-describedby` for accessibility.

---

## Task 1: Backend — Create ServiceTypeResponse DTO

**Files:**
- Create: `backend/src/main/java/com/hcare/api/v1/servicetypes/dto/ServiceTypeResponse.java`

- [ ] **Step 1: Create the DTO record**

```java
package com.hcare.api.v1.servicetypes.dto;

import java.util.List;
import java.util.UUID;

public record ServiceTypeResponse(
    UUID id,
    String name,
    String code,
    boolean requiresEvv,
    List<String> requiredCredentials
) {}
```

Note: `agencyId` and `createdAt` intentionally omitted — not needed by the frontend.

- [ ] **Step 2: Verify compilation**

```bash
cd backend && mvn compile -q
```

Expected: `BUILD SUCCESS` with no output.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/hcare/api/v1/servicetypes/dto/ServiceTypeResponse.java
git commit -m "feat: add ServiceTypeResponse DTO record"
```

---

## Task 2: Backend — Create ServiceTypeService (TDD)

**Files:**
- Create: `backend/src/test/java/com/hcare/api/v1/servicetypes/ServiceTypeServiceTest.java`
- Create: `backend/src/main/java/com/hcare/api/v1/servicetypes/ServiceTypeService.java`

- [ ] **Step 1: Write the failing unit tests**

Create `backend/src/test/java/com/hcare/api/v1/servicetypes/ServiceTypeServiceTest.java`:

```java
package com.hcare.api.v1.servicetypes;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hcare.api.v1.servicetypes.dto.ServiceTypeResponse;
import com.hcare.domain.ServiceType;
import com.hcare.domain.ServiceTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceTypeServiceTest {

    @Mock private ServiceTypeRepository serviceTypeRepository;

    private ServiceTypeService service;

    UUID agencyId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ServiceTypeService(serviceTypeRepository, new ObjectMapper());
    }

    @Test
    void listServiceTypes_returns_alphabetically_sorted_by_name_case_insensitive() {
        ServiceType bravo = new ServiceType(agencyId, "bravo", "BR", false, "[]");
        ServiceType alpha = new ServiceType(agencyId, "Alpha", "AL", true, "[]");
        when(serviceTypeRepository.findByAgencyId(agencyId)).thenReturn(List.of(bravo, alpha));

        List<ServiceTypeResponse> result = service.listServiceTypes(agencyId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("Alpha");
        assertThat(result.get(1).name()).isEqualTo("bravo");
    }

    @Test
    void listServiceTypes_parses_requiredCredentials_json_array() {
        ServiceType st = new ServiceType(agencyId, "PCS", "PCS", true, "[\"CPR\",\"FIRST_AID\"]");
        when(serviceTypeRepository.findByAgencyId(agencyId)).thenReturn(List.of(st));

        List<ServiceTypeResponse> result = service.listServiceTypes(agencyId);

        assertThat(result.get(0).requiredCredentials()).containsExactly("CPR", "FIRST_AID");
    }

    @Test
    void listServiceTypes_parses_empty_credentials_array() {
        ServiceType st = new ServiceType(agencyId, "SNV", "SNV", true, "[]");
        when(serviceTypeRepository.findByAgencyId(agencyId)).thenReturn(List.of(st));

        List<ServiceTypeResponse> result = service.listServiceTypes(agencyId);

        assertThat(result.get(0).requiredCredentials()).isEmpty();
    }

    @Test
    void listServiceTypes_returns_empty_credentials_on_malformed_json_and_does_not_throw() {
        ServiceType st = new ServiceType(agencyId, "PCS", "PCS", false, "NOT_JSON");
        when(serviceTypeRepository.findByAgencyId(agencyId)).thenReturn(List.of(st));

        List<ServiceTypeResponse> result = service.listServiceTypes(agencyId);

        assertThat(result.get(0).requiredCredentials()).isEmpty();
    }

    @Test
    void listServiceTypes_returns_empty_list_for_agency_with_no_service_types() {
        when(serviceTypeRepository.findByAgencyId(agencyId)).thenReturn(List.of());

        List<ServiceTypeResponse> result = service.listServiceTypes(agencyId);

        assertThat(result).isEmpty();
    }
}
```

- [ ] **Step 2: Run tests — confirm they fail**

```bash
cd backend && mvn test -Dtest=ServiceTypeServiceTest 2>&1 | grep -E "FAIL|ERROR|Tests run|ClassNotFound"
```

Expected: compilation failure (class doesn't exist yet).

- [ ] **Step 3: Implement ServiceTypeService**

Create `backend/src/main/java/com/hcare/api/v1/servicetypes/ServiceTypeService.java`:

```java
package com.hcare.api.v1.servicetypes;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hcare.api.v1.servicetypes.dto.ServiceTypeResponse;
import com.hcare.domain.ServiceType;
import com.hcare.domain.ServiceTypeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class ServiceTypeService {

    private static final Logger log = LoggerFactory.getLogger(ServiceTypeService.class);

    private final ServiceTypeRepository serviceTypeRepository;
    private final ObjectMapper objectMapper;

    public ServiceTypeService(ServiceTypeRepository serviceTypeRepository, ObjectMapper objectMapper) {
        this.serviceTypeRepository = serviceTypeRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<ServiceTypeResponse> listServiceTypes(UUID agencyId) {
        List<ServiceType> all = new ArrayList<>(serviceTypeRepository.findByAgencyId(agencyId));
        all.sort(Comparator.comparing(ServiceType::getName, String.CASE_INSENSITIVE_ORDER));
        return all.stream().map(this::toResponse).toList();
    }

    private ServiceTypeResponse toResponse(ServiceType st) {
        List<String> credentials;
        try {
            credentials = objectMapper.readValue(
                st.getRequiredCredentials(), new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse requiredCredentials for ServiceType {}: {}", st.getId(), e.getMessage());
            credentials = List.of();
        }
        return new ServiceTypeResponse(
            st.getId(),
            st.getName(),
            st.getCode(),
            st.isRequiresEvv(),
            credentials
        );
    }
}
```

- [ ] **Step 4: Run tests — all pass**

```bash
cd backend && mvn test -Dtest=ServiceTypeServiceTest 2>&1 | grep "Tests run:"
```

Expected: `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/hcare/api/v1/servicetypes/ServiceTypeService.java \
        backend/src/test/java/com/hcare/api/v1/servicetypes/ServiceTypeServiceTest.java
git commit -m "feat: add ServiceTypeService with alphabetical sort and Jackson credential parsing"
```

---

## Task 3: Backend — Create ServiceTypeController (TDD)

**Files:**
- Create: `backend/src/main/java/com/hcare/api/v1/servicetypes/ServiceTypeController.java`
- Create: `backend/src/test/java/com/hcare/api/v1/servicetypes/ServiceTypeControllerIT.java`

- [ ] **Step 1: Write the failing integration tests**

Create `backend/src/test/java/com/hcare/api/v1/servicetypes/ServiceTypeControllerIT.java` with the full boilerplate below. The `@Sql` truncation annotation, `@Autowired` fields, `tokenFor`/`authFor` helpers, and `@BeforeEach` seed block must all be written out exactly as shown — do not leave any of them implicit.

```java
package com.hcare.api.v1.servicetypes;

import com.hcare.AbstractIntegrationTest;
import com.hcare.api.v1.auth.dto.LoginRequest;
import com.hcare.api.v1.auth.dto.LoginResponse;
import com.hcare.api.v1.servicetypes.dto.ServiceTypeResponse;
import com.hcare.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Sql(statements = {
    "TRUNCATE TABLE shift_offers, shifts, recurrence_patterns, authorizations, caregiver_scoring_profiles, " +
    "caregiver_client_affinities, caregivers, service_types, clients, agency_users, agencies RESTART IDENTITY CASCADE"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ServiceTypeControllerIT extends AbstractIntegrationTest {

    private static final String TEST_PASSWORD = "correcthorsebatterystaple";

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private AgencyRepository agencyRepo;
    @Autowired private AgencyUserRepository userRepo;
    @Autowired private ServiceTypeRepository serviceTypeRepo;
    @Autowired private PasswordEncoder passwordEncoder;

    private Agency agencyA;
    private Agency agencyB;
    private Agency agencyC;

    @BeforeEach
    void seed() {
        // Agency A — two service types
        agencyA = agencyRepo.save(new Agency("Agency Alpha", "TX"));
        userRepo.save(new AgencyUser(agencyA.getId(), "admin-a@test.com",
            passwordEncoder.encode(TEST_PASSWORD), UserRole.ADMIN));
        serviceTypeRepo.save(new ServiceType(agencyA.getId(), "Skilled Nursing Visit", "SNV", true, "[]"));
        serviceTypeRepo.save(new ServiceType(agencyA.getId(), "Personal Care Services", "PCS", false, "[]"));

        // Agency B — one service type (different from A)
        agencyB = agencyRepo.save(new Agency("Agency Beta", "TX"));
        userRepo.save(new AgencyUser(agencyB.getId(), "admin-b@test.com",
            passwordEncoder.encode(TEST_PASSWORD), UserRole.ADMIN));
        serviceTypeRepo.save(new ServiceType(agencyB.getId(), "Homemaker Services", "HM", false, "[]"));

        // Agency C — no service types; user seeded so the empty-list test can authenticate
        agencyC = agencyRepo.save(new Agency("Agency Gamma", "TX"));
        userRepo.save(new AgencyUser(agencyC.getId(), "admin-c@test.com",
            passwordEncoder.encode(TEST_PASSWORD), UserRole.ADMIN));
    }

    private String tokenFor(String email) {
        LoginRequest req = new LoginRequest(email, TEST_PASSWORD);
        return restTemplate.postForEntity("/api/v1/auth/login", req, LoginResponse.class)
            .getBody().token();
    }

    private HttpHeaders authFor(String email) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(tokenFor(email));
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    @Test
    void listServiceTypes_returns_agencyA_types_sorted_alphabetically() {
        ResponseEntity<List<ServiceTypeResponse>> resp = restTemplate.exchange(
            "/api/v1/service-types", HttpMethod.GET,
            new HttpEntity<>(authFor("admin-a@test.com")),
            new ParameterizedTypeReference<>() {});

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).hasSize(2);
        // Alphabetical: "Personal Care Services" before "Skilled Nursing Visit"
        assertThat(resp.getBody().get(0).name()).isEqualTo("Personal Care Services");
        assertThat(resp.getBody().get(1).name()).isEqualTo("Skilled Nursing Visit");
    }

    @Test
    void listServiceTypes_tenant_isolation_agencyB_sees_only_own_types() {
        ResponseEntity<List<ServiceTypeResponse>> resp = restTemplate.exchange(
            "/api/v1/service-types", HttpMethod.GET,
            new HttpEntity<>(authFor("admin-b@test.com")),
            new ParameterizedTypeReference<>() {});

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).hasSize(1);
        assertThat(resp.getBody().get(0).name()).isEqualTo("Homemaker Services");
    }

    @Test
    void listServiceTypes_returns_empty_array_for_agency_with_no_types() {
        ResponseEntity<List<ServiceTypeResponse>> resp = restTemplate.exchange(
            "/api/v1/service-types", HttpMethod.GET,
            new HttpEntity<>(authFor("admin-c@test.com")),
            new ParameterizedTypeReference<>() {});

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isEmpty();
    }

    @Test
    void listServiceTypes_returns_401_without_token() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> resp = restTemplate.exchange(
            "/api/v1/service-types", HttpMethod.GET,
            new HttpEntity<>(h), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
```

Key decisions embedded in the skeleton above:
- `@Sql` truncates `service_types` (and its dependents) with `RESTART IDENTITY CASCADE` — prevents cross-IT contamination.
- Agency C is seeded with a valid user but zero service types so the empty-list test can obtain a real JWT and hit the endpoint (without this the empty-list assertion would 401 instead of 200).
- `tokenFor(email)` / `authFor(email)` accept an email argument so tests for different agencies can each authenticate independently — the single-agency pattern from `ShiftSchedulingControllerIT` does not apply here.

- [ ] **Step 2: Run tests — confirm they fail**

```bash
cd backend && mvn test -Dtest=ServiceTypeControllerIT 2>&1 | grep -E "FAIL|ERROR|Tests run"
```

Expected: tests fail with 404 responses (controller not yet mapped). The IT will compile successfully since it only references existing base classes and domain types — failures are assertion failures at runtime, not compilation errors.

- [ ] **Step 3: Implement ServiceTypeController**

Create `backend/src/main/java/com/hcare/api/v1/servicetypes/ServiceTypeController.java`:

```java
package com.hcare.api.v1.servicetypes;

import com.hcare.api.v1.servicetypes.dto.ServiceTypeResponse;
import com.hcare.security.UserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/service-types")
public class ServiceTypeController {

    private final ServiceTypeService serviceTypeService;

    public ServiceTypeController(ServiceTypeService serviceTypeService) {
        this.serviceTypeService = serviceTypeService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<List<ServiceTypeResponse>> listServiceTypes(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(serviceTypeService.listServiceTypes(principal.getAgencyId()));
    }
}
```

- [ ] **Step 4: Run integration tests — all pass**

```bash
cd backend && mvn test -Dtest=ServiceTypeControllerIT 2>&1 | grep "Tests run:"
```

Expected: `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 5: Run full backend suite — no regressions**

```bash
cd backend && mvn test 2>&1 | tail -5
```

Expected: `BUILD SUCCESS`

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/hcare/api/v1/servicetypes/ServiceTypeController.java \
        backend/src/test/java/com/hcare/api/v1/servicetypes/ServiceTypeControllerIT.java
git commit -m "feat: add GET /api/v1/service-types endpoint with tenant isolation"
```

---

## Task 4: Frontend — Add ServiceTypeResponse type

**Files:**
- Modify: `frontend/src/types/api.ts`

- [ ] **Step 1: Add the interface after the Payers section**

```typescript
// ── Service Types ─────────────────────────────────────────────────────────────

export interface ServiceTypeResponse {
  id: string
  name: string
  code: string
  requiresEvv: boolean
  requiredCredentials: string[]
}
```

Note: Omit `billingCode` and `unitType` — not on the backend entity. `code` is `string` (not `string | null`) — the DB column is `VARCHAR(50) NOT NULL` and the backend will never return `null`.

- [ ] **Step 2: TypeScript check**

```bash
cd frontend && npx tsc --noEmit 2>&1 | head -20
```

Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/types/api.ts
git commit -m "feat: add ServiceTypeResponse type to api.ts"
```

---

## Task 5: Frontend — Create serviceTypes API module

**Files:**
- Create: `frontend/src/api/serviceTypes.ts`

- [ ] **Step 1: Create the API module**

```typescript
import { apiClient } from './client'
import type { ServiceTypeResponse } from '../types/api'

export async function listServiceTypes(): Promise<ServiceTypeResponse[]> {
  const response = await apiClient.get<ServiceTypeResponse[]>('/service-types')
  return response.data
}
```

Backend returns a plain array — no `.content` unwrap needed.

- [ ] **Step 2: TypeScript check**

```bash
cd frontend && npx tsc --noEmit 2>&1 | head -20
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/api/serviceTypes.ts
git commit -m "feat: add listServiceTypes API function"
```

---

## Task 6: Frontend — Create useServiceTypes hook (TDD)

**Files:**
- Create: `frontend/src/hooks/useServiceTypes.test.ts`
- Create: `frontend/src/hooks/useServiceTypes.ts`

- [ ] **Step 1: Write the failing hook tests**

Follow the exact pattern of `useClients.test.ts`: `MockAdapter`, `makeWrapper` with fresh `QueryClient`, three tests:
1. Returns populated `serviceTypes` array on success
2. Returns `[]` before data loads
3. Sets `isError: true` and `serviceTypes: []` on API 500

- [ ] **Step 2: Run tests — confirm they fail**

```bash
cd frontend && npx vitest run src/hooks/useServiceTypes.test.ts 2>&1 | grep -E "FAIL|PASS|Error"
```

- [ ] **Step 3: Implement the hook**

```typescript
import { useQuery } from '@tanstack/react-query'
import { listServiceTypes } from '../api/serviceTypes'

export function useServiceTypes() {
  const query = useQuery({
    queryKey: ['service-types'],
    queryFn: listServiceTypes,
    staleTime: 300_000, // 5 minutes — service types rarely change
  })

  return {
    ...query,
    serviceTypes: query.data ?? [],
  }
}
```

- [ ] **Step 4: Run tests — all pass**

```bash
cd frontend && npx vitest run src/hooks/useServiceTypes.test.ts 2>&1 | grep -E "Tests|passed|failed"
```

Expected: 3 tests pass.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/hooks/useServiceTypes.ts \
        frontend/src/hooks/useServiceTypes.test.ts
git commit -m "feat: add useServiceTypes hook with 5-min staleTime"
```

---

## Task 7: Frontend — Add i18n keys

**Files:**
- Modify: `frontend/public/locales/en/newShift.json`

- [ ] **Step 1: Add five new keys, remove two dead keys — do NOT remove `serviceTypePcs` yet**

Do NOT remove `serviceTypePcs` in this task. It is still referenced by the hardcoded `<option>` in `NewShiftPanel.tsx` (line 113). Removing it here would create a broken intermediate commit where the JSON key is missing but the TSX reference still exists. The `serviceTypePcs` removal happens atomically in Task 8 Step 3 alongside the TSX change.

Do NOT remove `selectServiceType` — it is used by the loaded-state placeholder option (`<option value="">{t('selectServiceType')}</option>`) in Task 8 Step 3.

Add the five new keys:

```json
"loadingServiceTypes": "Loading service types…",
"noServiceTypesOption": "No service types — configure in Settings",
"noServiceTypesHint": "No service types have been added for your agency.",
"serviceTypesLoadError": "Failed to load service types",
"serviceTypesLoadErrorRetry": "Could not load service types. Please close and reopen the form to retry."
```

Also remove these two dead keys (confirmed: neither appears in `NewShiftPanel.tsx`):
- `"caregiverPhaseNote": "Phase 4 will populate this list from the API."` — no TSX reference, stale Phase 4 placeholder
- `"mockAlert"` — stale Phase 4 mock submission text, no TSX reference

- [ ] **Step 2: Commit**

```bash
git add frontend/public/locales/en/newShift.json
git commit -m "feat: add service type loading/error/empty i18n keys to newShift.json"
```

Note: `serviceTypePcs` is intentionally NOT removed in this commit. It will be removed in Task 8 Step 3 alongside the TSX change that eliminates its only reference, keeping the commit history in a consistently runnable state.

---

## Task 8: Frontend — Wire NewShiftPanel (TDD)

**Files:**
- Create: `frontend/src/components/schedule/NewShiftPanel.test.tsx`
- Modify: `frontend/src/components/schedule/NewShiftPanel.tsx`

- [ ] **Step 1: Write the failing component tests**

Four tests using `MockAdapter` to stub `GET /api/v1/service-types`:
1. **Loading state:** Never-resolving promise → select disabled, "loadingServiceTypes" option visible
2. **Error state:** 500 → select disabled, "serviceTypesLoadError" option + "serviceTypesLoadErrorRetry" inline error
3. **Loaded state:** `[pcs]` → select enabled, PCS option rendered, submit not disabled
4. **Empty state:** 200 with `[]` → select disabled, "noServiceTypesOption" option visible, "noServiceTypesHint" paragraph rendered

```typescript
it('shows empty state option and hint when no service types returned', async () => {
  mock.onGet('/service-types').reply(200, [])
  mock.onGet('/clients').reply(200, { content: [], totalElements: 0, totalPages: 0, number: 0, size: 100, first: true, last: true })
  mock.onGet('/caregivers').reply(200, { content: [], totalElements: 0, totalPages: 0, number: 0, size: 100, first: true, last: true })

  render(<NewShiftPanel prefill={null} backLabel="Back" />, { wrapper: makeWrapper() })

  await waitFor(() => expect(screen.getByText('noServiceTypesOption')).toBeInTheDocument())
  const select = screen.getByRole('combobox', { name: /labelServiceType/i })
  expect(select).toBeDisabled()
  expect(screen.getByText('noServiceTypesHint')).toBeInTheDocument()
})
```

- [ ] **Step 2: Run tests — confirm they fail**

```bash
cd frontend && npx vitest run src/components/schedule/NewShiftPanel.test.tsx 2>&1 | grep -E "FAIL|PASS|Error" | head -20
```

- [ ] **Step 3: Update NewShiftPanel.tsx and remove the now-dead i18n key**

1. Import `useServiceTypes`
2. Add hook call: `const { serviceTypes, isLoading: serviceTypesLoading, isError: serviceTypesError } = useServiceTypes()`
3. Replace the hardcoded `{/* Service Type — hardcoded ... */}` block with:

```tsx
{/* Service Type */}
<div>
  <label
    htmlFor="ns-service-type"
    className="block text-[10px] font-bold uppercase tracking-[0.1em] text-text-secondary mb-1"
  >
    {t('labelServiceType')}
  </label>
  <select
    id="ns-service-type"
    {...register('serviceTypeId', { required: t('validationServiceTypeRequired') })}
    disabled={serviceTypesLoading || serviceTypesError || serviceTypes.length === 0}
    aria-busy={serviceTypesLoading ? "true" : "false"}
    aria-describedby="ns-service-type-hint"
    className="w-full border border-border px-3 py-2 text-[13px] text-dark bg-white disabled:opacity-50"
  >
    {serviceTypesLoading ? (
      <option value="">{t('loadingServiceTypes')}</option>
    ) : serviceTypesError ? (
      <option value="">{t('serviceTypesLoadError')}</option>
    ) : serviceTypes.length === 0 ? (
      <option value="">{t('noServiceTypesOption')}</option>
    ) : (
      <>
        <option value="">{t('selectServiceType')}</option>
        {serviceTypes.map((st) => (
          <option key={st.id} value={st.id}>
            {st.name}
          </option>
        ))}
      </>
    )}
  </select>
  {serviceTypesError && (
    <p id="ns-service-type-hint" className="text-[11px] text-red-600 mt-1">
      {t('serviceTypesLoadErrorRetry')}
    </p>
  )}
  {!serviceTypesError && serviceTypes.length === 0 && !serviceTypesLoading && (
    <p id="ns-service-type-hint" className="text-[11px] text-text-muted mt-1">
      {t('noServiceTypesHint')}
    </p>
  )}
  {errors.serviceTypeId && (
    <p className="text-[11px] text-red-600 mt-1">{errors.serviceTypeId.message}</p>
  )}
</div>
```

4. Update submit button:
```tsx
disabled={isSubmitting || createMutation.isPending || serviceTypesLoading || serviceTypesError || serviceTypes.length === 0}
```

5. In `frontend/public/locales/en/newShift.json`, remove the `serviceTypePcs` key. This is the correct atomic moment to remove it — its only TSX reference (the hardcoded `<option>` replaced above) no longer exists after this step. Removing it here (rather than in Task 7) ensures no intermediate commit has a missing key that is still referenced.

- [ ] **Step 4: Run component tests — all pass**

```bash
cd frontend && npx vitest run src/components/schedule/NewShiftPanel.test.tsx 2>&1 | grep -E "Tests|passed|failed"
```

Expected: 4 tests pass.

- [ ] **Step 5: Run full frontend suite — no regressions**

```bash
cd frontend && npx vitest run 2>&1 | tail -15
```

Expected: all tests pass.

- [ ] **Step 6: TypeScript check**

```bash
cd frontend && npx tsc --noEmit 2>&1 | head -20
```

- [ ] **Step 7: Commit**

```bash
git add frontend/src/components/schedule/NewShiftPanel.tsx \
        frontend/src/components/schedule/NewShiftPanel.test.tsx \
        frontend/public/locales/en/newShift.json
git commit -m "feat: wire NewShiftPanel service type select to live API with loading/error/empty states"
```

Note: `newShift.json` is included in this commit because `serviceTypePcs` is removed here atomically with its TSX reference. This keeps the commit history consistently runnable.

---

## Manual Test Checkpoint

After all tasks complete, verify end-to-end with both services running (`mvn spring-boot:run -Dspring-boot.run.profiles=dev` + `npm run dev`):

1. **Confirm the endpoint responds:**
   ```bash
   TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
     -H 'Content-Type: application/json' \
     -d '{"email":"admin@sunrise.dev","password":"Admin1234!"}' | jq -r '.token')
   curl -s -H "Authorization: Bearer $TOKEN" \
     http://localhost:8080/api/v1/service-types | jq .
   ```
   Expected: JSON array with two objects. The `name` field contains full names, not codes — `DevDataSeeder` seeds `"Personal Care Services"` (code `"PCS"`) and `"Skilled Nursing Visit"` (code `"SNV"`). Sorted alphabetically by name: `"Personal Care Services"` appears first, `"Skilled Nursing Visit"` second.

2. **Open the New Shift panel** — dropdown shows "Select service type…" placeholder with real options (PCS, SNV). No loading error.

3. **Create a shift** — fill all fields, select a service type, submit. Shift appears on the calendar. No 409 error in the console.

4. **Tenant isolation** — log in as `admin@golden.dev`, open the panel, confirm only golden agency's service types appear.
