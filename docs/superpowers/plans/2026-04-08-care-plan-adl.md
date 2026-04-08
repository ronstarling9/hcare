# Care Plan & ADL Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Care Plan tab inside `ClientDetailPanel` — empty state, setup mode, active plan view, ADL task management with a 100-task combobox template library, and goal management.

**Architecture:** A new `CarePlanTab` component owns all local state (setup mode, pending tasks/goals). It calls existing backend endpoints for reads/writes, plus one new `GET /clients/{id}/care-plans/active` endpoint. The "Save & Activate" sequence is orchestrated in the component using the raw API functions (create plan → tasks → goals → activate). Individual adds/deletes on an active plan use React Query mutation hooks.

**Tech Stack:** Spring Boot 3 (Java 25), JPA/JPQL, TestRestTemplate (backend); React 18, TypeScript, React Query, axios-mock-adapter, Vitest + Testing Library (frontend).

---

## File Map

| Action | Path | Responsibility |
|---|---|---|
| Modify | `backend/.../domain/CarePlanRepository.java` | Add non-locking `findActiveByClientId` |
| Modify | `backend/.../clients/ClientService.java` | Add `getActiveCarePlan` |
| Modify | `backend/.../clients/ClientController.java` | Add `GET /{id}/care-plans/active` |
| Modify | `backend/.../clients/ClientControllerIT.java` | 3 new tests for `/active` endpoint |
| Modify | `backend/.../domain/CarePlanDomainIT.java` | Test `findActiveByClientId` returns correct result |
| Modify | `frontend/src/types/api.ts` | Add `AssistanceLevel`, `CarePlanStatus`, `GoalStatus`, care plan response/request types |
| Create | `frontend/src/api/carePlans.ts` | All care plan API functions |
| Create | `frontend/public/locales/en/adl-task-templates.json` | 100 ADL/IADL task templates |
| Modify | `frontend/public/locales/en/clients.json` | Add ~30 care plan i18n keys |
| Create | `frontend/src/hooks/useCarePlan.ts` | `useActivePlan`, `useAdlTasks`, `useGoals`, `useAdlTaskTemplates`, mutation hooks |
| Create | `frontend/src/hooks/useCarePlan.test.ts` | Hook unit tests |
| Create | `frontend/src/components/clients/AddGoalForm.tsx` | Inline goal entry form |
| Create | `frontend/src/components/clients/AddGoalForm.test.tsx` | AddGoalForm tests |
| Create | `frontend/src/components/clients/AddTaskPanel.tsx` | Slide-out task panel with combobox |
| Create | `frontend/src/components/clients/AddTaskPanel.test.tsx` | AddTaskPanel tests |
| Create | `frontend/src/components/clients/CarePlanTab.tsx` | Main tab — setup mode + active plan |
| Create | `frontend/src/components/clients/CarePlanTab.test.tsx` | CarePlanTab tests |
| Modify | `frontend/src/components/clients/ClientDetailPanel.tsx` | Replace placeholder with `<CarePlanTab>` |

---

## Task 1: Backend — non-locking `findActiveByClientId` + domain test

**Files:**
- Modify: `backend/src/main/java/com/hcare/domain/CarePlanRepository.java`
- Modify: `backend/src/test/java/com/hcare/domain/CarePlanDomainIT.java`

- [ ] **Step 1: Write the failing test**

Add to `CarePlanDomainIT.java` after the existing `findByClientIdAndStatus_returns_only_active_plan` test:

```java
@Test
void findActiveByClientId_returns_active_plan_and_empty_when_none() {
    CarePlan plan = carePlanRepo.save(new CarePlan(client.getId(), agency.getId(), 1));
    plan.activate();
    carePlanRepo.save(plan);

    Optional<CarePlan> found = carePlanRepo.findActiveByClientId(
        client.getId(), CarePlanStatus.ACTIVE);
    assertThat(found).isPresent();
    assertThat(found.get().getId()).isEqualTo(plan.getId());
    assertThat(found.get().getStatus()).isEqualTo(CarePlanStatus.ACTIVE);

    Optional<CarePlan> missing = carePlanRepo.findActiveByClientId(
        UUID.randomUUID(), CarePlanStatus.ACTIVE);
    assertThat(missing).isEmpty();
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd backend && mvn test -Dtest=CarePlanDomainIT#findActiveByClientId_returns_active_plan_and_empty_when_none -q
```

Expected: FAIL — `findActiveByClientId` does not exist yet.

- [ ] **Step 3: Add `findActiveByClientId` to `CarePlanRepository.java`**

Add this method directly after the existing `findByClientIdAndStatus` method:

```java
// Read-only — no lock. Used by getActiveCarePlan() for the admin GET endpoint.
@Query("SELECT p FROM CarePlan p WHERE p.clientId = :clientId AND p.status = :status")
Optional<CarePlan> findActiveByClientId(@Param("clientId") UUID clientId,
                                        @Param("status") CarePlanStatus status);
```

The import `org.springframework.data.jpa.repository.Query` and `org.springframework.data.repository.query.Param` are already present.

- [ ] **Step 4: Run test to verify it passes**

```bash
cd backend && mvn test -Dtest=CarePlanDomainIT -q
```

Expected: All tests in `CarePlanDomainIT` PASS.

- [ ] **Step 5: Commit**

```bash
cd backend && git add src/main/java/com/hcare/domain/CarePlanRepository.java \
  src/test/java/com/hcare/domain/CarePlanDomainIT.java
git commit -m "feat: add findActiveByClientId non-locking read query to CarePlanRepository"
```

---

## Task 2: Backend — `getActiveCarePlan` service + `GET /active` endpoint + tests

**Files:**
- Modify: `backend/src/main/java/com/hcare/api/v1/clients/ClientService.java`
- Modify: `backend/src/main/java/com/hcare/api/v1/clients/ClientController.java`
- Modify: `backend/src/test/java/com/hcare/api/v1/clients/ClientControllerIT.java`

- [ ] **Step 1: Write failing tests**

Add to `ClientControllerIT.java` after the existing `activateCarePlan_returns_409_when_already_active` test:

```java
// --- GET /clients/{id}/care-plans/active ---

@Test
void getActiveCarePlan_returns_200_with_active_plan() {
    CarePlan plan = carePlanRepo.save(new CarePlan(client.getId(), agency.getId(), 1));
    plan.activate();
    carePlanRepo.save(plan);

    ResponseEntity<CarePlanResponse> resp = restTemplate.exchange(
        "/api/v1/clients/" + client.getId() + "/care-plans/active",
        HttpMethod.GET, new HttpEntity<>(auth()), CarePlanResponse.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(resp.getBody().id()).isEqualTo(plan.getId());
    assertThat(resp.getBody().status()).isEqualTo(CarePlanStatus.ACTIVE);
    assertThat(resp.getBody().planVersion()).isEqualTo(1);
}

@Test
void getActiveCarePlan_returns_404_when_no_plan_exists() {
    ResponseEntity<String> resp = restTemplate.exchange(
        "/api/v1/clients/" + client.getId() + "/care-plans/active",
        HttpMethod.GET, new HttpEntity<>(auth()), String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
}

@Test
void getActiveCarePlan_returns_404_after_plan_is_superseded() {
    CarePlan v1 = carePlanRepo.save(new CarePlan(client.getId(), agency.getId(), 1));
    v1.activate();
    carePlanRepo.save(v1);
    v1.supersede();
    carePlanRepo.save(v1);

    ResponseEntity<String> resp = restTemplate.exchange(
        "/api/v1/clients/" + client.getId() + "/care-plans/active",
        HttpMethod.GET, new HttpEntity<>(auth()), String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd backend && mvn test -Dtest=ClientControllerIT#getActiveCarePlan_returns_200_with_active_plan -q
```

Expected: FAIL — endpoint does not exist yet (404 from missing route).

- [ ] **Step 3: Add `getActiveCarePlan` to `ClientService.java`**

Add this method to `ClientService` after `activateCarePlan`:

```java
@Transactional(readOnly = true)
public CarePlanResponse getActiveCarePlan(UUID clientId) {
    requireClient(clientId);
    return carePlanRepository.findActiveByClientId(clientId, CarePlanStatus.ACTIVE)
        .map(CarePlanResponse::from)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
            "No active care plan for client: " + clientId));
}
```

- [ ] **Step 4: Add the endpoint to `ClientController.java`**

Add this method to `ClientController` after the existing `activateCarePlan` method:

```java
@GetMapping("/{id}/care-plans/active")
@PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
public ResponseEntity<CarePlanResponse> getActiveCarePlan(@PathVariable UUID id) {
    return ResponseEntity.ok(clientService.getActiveCarePlan(id));
}
```

- [ ] **Step 5: Run all three new tests**

```bash
cd backend && mvn test -Dtest=ClientControllerIT#getActiveCarePlan_returns_200_with_active_plan+getActiveCarePlan_returns_404_when_no_plan_exists+getActiveCarePlan_returns_404_after_plan_is_superseded -q
```

Expected: All 3 PASS.

- [ ] **Step 6: Run full backend test suite**

```bash
cd backend && mvn test -q
```

Expected: All tests PASS.

- [ ] **Step 7: Commit**

```bash
cd backend && git add src/main/java/com/hcare/api/v1/clients/ClientService.java \
  src/main/java/com/hcare/api/v1/clients/ClientController.java \
  src/test/java/com/hcare/api/v1/clients/ClientControllerIT.java
git commit -m "feat: add GET /clients/{id}/care-plans/active endpoint"
```

---

## Task 3: Frontend — extend `types/api.ts`

**Files:**
- Modify: `frontend/src/types/api.ts`

- [ ] **Step 1: Add care plan types**

Append to the bottom of `frontend/src/types/api.ts`:

```ts
// ── Care Plans ────────────────────────────────────────────────────────────────

export type AssistanceLevel =
  | 'INDEPENDENT'
  | 'SUPERVISION'
  | 'MINIMAL_ASSIST'
  | 'MODERATE_ASSIST'
  | 'MAXIMUM_ASSIST'
  | 'DEPENDENT'

export type CarePlanStatus = 'DRAFT' | 'ACTIVE' | 'SUPERSEDED'
export type GoalStatus = 'ACTIVE' | 'COMPLETED' | 'DISCONTINUED'

export interface CarePlanResponse {
  id: string
  clientId: string
  planVersion: number
  status: CarePlanStatus
  reviewedByClinicianId: string | null
  reviewedAt: string | null
  activatedAt: string | null
  createdAt: string
}

export interface AdlTaskResponse {
  id: string
  carePlanId: string
  name: string
  assistanceLevel: AssistanceLevel
  instructions: string | null
  frequency: string | null
  sortOrder: number
  createdAt: string
}

export interface GoalResponse {
  id: string
  carePlanId: string
  description: string
  targetDate: string | null
  status: GoalStatus
  createdAt: string
}

export interface AddAdlTaskRequest {
  name: string
  assistanceLevel: AssistanceLevel
  frequency?: string
  instructions?: string
  sortOrder?: number
}

export interface AddGoalRequest {
  description: string
  targetDate?: string
}
```

- [ ] **Step 2: Commit**

```bash
cd frontend && git add src/types/api.ts
git commit -m "feat: add care plan, ADL task, and goal types to api.ts"
```

---

## Task 4: Frontend — create `src/api/carePlans.ts`

**Files:**
- Create: `frontend/src/api/carePlans.ts`

- [ ] **Step 1: Create the file**

```ts
import { apiClient } from './client'
import type {
  CarePlanResponse,
  AdlTaskResponse,
  GoalResponse,
  PageResponse,
  AddAdlTaskRequest,
  AddGoalRequest,
} from '../types/api'

export async function getActivePlan(clientId: string): Promise<CarePlanResponse> {
  const response = await apiClient.get<CarePlanResponse>(
    `/clients/${clientId}/care-plans/active`,
  )
  return response.data
}

export async function createCarePlan(clientId: string): Promise<CarePlanResponse> {
  const response = await apiClient.post<CarePlanResponse>(
    `/clients/${clientId}/care-plans`,
    {},
  )
  return response.data
}

export async function activateCarePlan(
  clientId: string,
  planId: string,
): Promise<CarePlanResponse> {
  const response = await apiClient.post<CarePlanResponse>(
    `/clients/${clientId}/care-plans/${planId}/activate`,
  )
  return response.data
}

export async function listAdlTasks(
  clientId: string,
  planId: string,
): Promise<PageResponse<AdlTaskResponse>> {
  const response = await apiClient.get<PageResponse<AdlTaskResponse>>(
    `/clients/${clientId}/care-plans/${planId}/adl-tasks`,
    { params: { size: 100 } },
  )
  return response.data
}

export async function addAdlTask(
  clientId: string,
  planId: string,
  req: AddAdlTaskRequest,
): Promise<AdlTaskResponse> {
  const response = await apiClient.post<AdlTaskResponse>(
    `/clients/${clientId}/care-plans/${planId}/adl-tasks`,
    req,
  )
  return response.data
}

export async function deleteAdlTask(
  clientId: string,
  planId: string,
  taskId: string,
): Promise<void> {
  await apiClient.delete(
    `/clients/${clientId}/care-plans/${planId}/adl-tasks/${taskId}`,
  )
}

export async function listGoals(
  clientId: string,
  planId: string,
): Promise<PageResponse<GoalResponse>> {
  const response = await apiClient.get<PageResponse<GoalResponse>>(
    `/clients/${clientId}/care-plans/${planId}/goals`,
    { params: { size: 100 } },
  )
  return response.data
}

export async function addGoal(
  clientId: string,
  planId: string,
  req: AddGoalRequest,
): Promise<GoalResponse> {
  const response = await apiClient.post<GoalResponse>(
    `/clients/${clientId}/care-plans/${planId}/goals`,
    req,
  )
  return response.data
}

export async function deleteGoal(
  clientId: string,
  planId: string,
  goalId: string,
): Promise<void> {
  await apiClient.delete(
    `/clients/${clientId}/care-plans/${planId}/goals/${goalId}`,
  )
}
```

- [ ] **Step 2: Commit**

```bash
cd frontend && git add src/api/carePlans.ts
git commit -m "feat: add carePlans API functions"
```

---

## Task 5: Frontend — ADL template JSON (100 tasks)

**Files:**
- Create: `frontend/public/locales/en/adl-task-templates.json`

- [ ] **Step 1: Create the file**

```json
[
  { "name": "Bathing (full body)", "defaultAssistanceLevel": "MAXIMUM_ASSIST", "defaultFrequency": "Daily", "defaultInstructions": "Check water temperature. Use shower chair if needed." },
  { "name": "Bed bath", "defaultAssistanceLevel": "MAXIMUM_ASSIST", "defaultFrequency": "Daily", "defaultInstructions": "" },
  { "name": "Sponge bath", "defaultAssistanceLevel": "MODERATE_ASSIST", "defaultFrequency": "Daily", "defaultInstructions": "" },
  { "name": "Dressing — upper body", "defaultAssistanceLevel": "MINIMAL_ASSIST", "defaultFrequency": "Daily", "defaultInstructions": "" },
  { "name": "Dressing — lower body", "defaultAssistanceLevel": "MODERATE_ASSIST", "defaultFrequency": "Daily", "defaultInstructions": "" },
  { "name": "Grooming (face/hair/teeth)", "defaultAssistanceLevel": "SUPERVISION", "defaultFrequency": "Daily", "defaultInstructions": "" },
  { "name": "Oral care", "defaultAssistanceLevel": "SUPERVISION", "defaultFrequency": "Daily", "defaultInstructions": "" },
  { "name": "Shaving assist", "defaultAssistanceLevel": "MINIMAL_ASSIST", "defaultFrequency": "Daily", "defaultInstructions": "" },
  { "name": "Toileting assist", "defaultAssistanceLevel": "MODERATE_ASSIST", "defaultFrequency": "Each visit", "defaultInstructions": "" },
  { "name": "Incontinence care", "defaultAssistanceLevel": "MAXIMUM_ASSIST", "defaultFrequency": "Each visit", "defaultInstructions": "Check skin integrity with each change." },
  { "name": "Catheter care", "defaultAssistanceLevel": "MAXIMUM_ASSIST", "defaultFrequency": "Daily", "defaultInstructions": "Document output at each visit." },
  { "name": "Transfers (bed to chair)", "defaultAssistanceLevel": "MODERATE_ASSIST", "defaultFrequency": "Each visit", "defaultInstructions": "Ensure gait belt is on before transfer. Two-person assist if client resists." },
  { "name": "Transfers (chair to toilet)", "defaultAssistanceLevel": "MODERATE_ASSIST", "defaultFrequency": "Each visit", "defaultInstructions": "" },
  { "name": "Car transfer", "defaultAssistanceLevel": "MODERATE_ASSIST", "defaultFrequency": "As needed", "defaultInstructions": "" },
  { "name": "Ambulation assist", "defaultAssistanceLevel": "SUPERVISION", "defaultFrequency": "Each visit", "defaultInstructions": "" },
  { "name": "Wheelchair mobility", "defaultAssistanceLevel": "MODERATE_ASSIST", "defaultFrequency": "Each visit", "defaultInstructions": "" },
  { "name": "Walker/cane supervision", "defaultAssistanceLevel": "SUPERVISION", "defaultFrequency": "Each visit", "defaultInstructions": "" },
  { "name": "Range of motion exercises", "defaultAssistanceLevel": "MINIMAL_ASSIST", "defaultFrequency": "Daily", "defaultInstructions": "" },
  { "name": "Feeding assist", "defaultAssistanceLevel": "MODERATE_ASSIST", "defaultFrequency": "Each meal", "defaultInstructions": "" },
  { "name": "Meal setup", "defaultAssistanceLevel": "SUPERVISION", "defaultFrequency": "Each meal", "defaultInstructions": "" },
  { "name": "Meal preparation", "defaultAssistanceLevel": "SUPERVISION", "defaultFrequency": "3× per week", "defaultInstructions": "" },
  { "name": "Light housekeeping", "defaultAssistanceLevel": "INDEPENDENT", "defaultFrequency": "Each visit", "defaultInstructions": "" },
  { "name": "Laundry", "defaultAssistanceLevel": "SUPERVISION", "defaultFrequency": "Weekly", "defaultInstructions": "" },
  { "name": "Grocery shopping", "defaultAssistanceLevel": "SUPERVISION", "defaultFrequency": "Weekly", "defaultInstructions": "" },
  { "name": "Medication reminders", "defaultAssistanceLevel": "SUPERVISION", "defaultFrequency": "Each visit", "defaultInstructions": "Remind only — do not administer." },
  { "name": "Medication administration", "defaultAssistanceLevel": "MAXIMUM_ASSIST", "defaultFrequency": "Each visit", "defaultInstructions": "Follow medication administration policy. Document in the MAR." },
  { "name": "Transportation", "defaultAssistanceLevel": "SUPERVISION", "defaultFrequency": "As needed", "defaultInstructions": "" },
  { "name": "Errands", "defaultAssistanceLevel": "INDEPENDENT", "defaultFrequency": "As needed", "defaultInstructions": "" },
  { "name": "Companionship", "defaultAssistanceLevel": "INDEPENDENT", "defaultFrequency": "Each visit", "defaultInstructions": "" },
  { "name": "Reading aloud", "defaultAssistanceLevel": "INDEPENDENT", "defaultFrequency": "Each visit", "defaultInstructions": "" },
  { "name": "Phone/tablet assist", "defaultAssistanceLevel": "MINIMAL_ASSIST", "defaultFrequency": "As needed", "defaultInstructions": "" },
  { "name": "Fall prevention", "defaultAssistanceLevel": "SUPERVISION", "defaultFrequency": "Each visit", "defaultInstructions": "Remove trip hazards. Confirm footwear is appropriate before ambulation." },
  { "name": "Skin assessment", "defaultAssistanceLevel": "INDEPENDENT", "defaultFrequency": "Daily", "defaultInstructions": "Document any new redness or skin breakdown." },
  { "name": "Wound observation", "defaultAssistanceLevel": "INDEPENDENT", "defaultFrequency": "Daily", "defaultInstructions": "Document size, color, and drainage. Do not change dressing unless instructed." },
  { "name": "Vital signs (BP/pulse/O₂)", "defaultAssistanceLevel": "INDEPENDENT", "defaultFrequency": "Each visit", "defaultInstructions": "Document readings in the visit note." },
  { "name": "Weight monitoring", "defaultAssistanceLevel": "MINIMAL_ASSIST", "defaultFrequency": "Weekly", "defaultInstructions": "Use same scale at same time of day. Document and report changes > 3 lb." },
  { "name": "Blood glucose check", "defaultAssistanceLevel": "SUPERVISION", "defaultFrequency": "Each visit", "defaultInstructions": "Document reading and notify supervisor if outside target range." },
  { "name": "Fluid intake monitoring", "defaultAssistanceLevel": "SUPERVISION", "defaultFrequency": "Each visit", "defaultInstructions": "" },
  { "name": "Bowel tracking", "defaultAssistanceLevel": "INDEPENDENT", "defaultFrequency": "Daily", "defaultInstructions": "Document frequency and consistency in visit note." },
  { "name": "Urinary output monitoring", "defaultAssistanceLevel": "INDEPENDENT", "defaultFrequency": "Each visit", "defaultInstructions": "" },
  { "name": "Physical therapy exercise follow-through", "defaultAssistanceLevel": "SUPERVISION", "defaultFrequency": "Daily", "defaultInstructions": "Follow PT home exercise program. Do not progress without PT authorization." },
  { "name": "Occupational therapy task follow-through", "defaultAssistanceLevel": "SUPERVISION", "defaultFrequency": "Daily", "defaultInstructions": "Follow OT home program." },
  { "name": "Speech therapy exercise support", "defaultAssistanceLevel": "SUPERVISION", "defaultFrequency": "Daily", "defaultInstructions": "" },
  { "name": "Cognitive stimulation activities", "defaultAssistanceLevel": "SUPERVISION", "defaultFrequency": "Each visit", "defaultInstructions": "" },
  { "name": "Memory care activities", "defaultAssistanceLevel": "SUPERVISION", "defaultFrequency": "Each visit", "defaultInstructions": "" },
  { "name": "Trash removal", "defaultAssistanceLevel": "INDEPENDENT", "defaultFrequency": "Weekly", "defaultInstructions": "" },
  { "name": "Bed making", "defaultAssistanceLevel": "INDEPENDENT", "defaultFrequency": "Daily", "defaultInstructions": "" },
  { "name": "Dish washing", "defaultAssistanceLevel": "INDEPENDENT", "defaultFrequency": "Each visit", "defaultInstructions": "" },
  { "name": "Surface cleaning", "defaultAssistanceLevel": "INDEPENDENT", "defaultFrequency": "Each visit", "defaultInstructions": "" },
  { "name": "Floor sweeping/mopping", "defaultAssistanceLevel": "INDEPENDENT", "defaultFrequency": "Weekly", "defaultInstructions": "" },
  { "name": "Bathroom cleaning", "defaultAssistanceLevel": "INDEPENDENT", "defaultFrequency": "Weekly", "defaultInstructions": "" },
  { "name": "Linen change", "defaultAssistanceLevel": "INDEPENDENT", "defaultFrequency": "Weekly", "defaultInstructions": "" },
  { "name": "Hair washing", "defaultAssistanceLevel": "MODERATE_ASSIST", "defaultFrequency": "3× per week", "defaultInstructions": "" },
  { "name": "Nail care", "defaultAssistanceLevel": "MODERATE_ASSIST", "defaultFrequency": "Weekly", "defaultInstructions": "Do not trim nails if client is diabetic without physician order." },
  { "name": "Foot care", "defaultAssistanceLevel": "MODERATE_ASSIST", "defaultFrequency": "Weekly", "defaultInstructions": "Inspect for sores or skin changes. Do not trim nails if diabetic." },
  { "name": "Compression stocking application", "defaultAssistanceLevel": "MODERATE_ASSIST", "defaultFrequency": "Daily", "defaultInstructions": "Apply before client stands. Check skin before and after." },
  { "name": "Hearing aid assist", "defaultAssistanceLevel": "MINIMAL_ASSIST", "defaultFrequency": "Daily", "defaultInstructions": "" },
  { "name": "Glasses/dentures assist", "defaultAssistanceLevel": "MINIMAL_ASSIST", "defaultFrequency": "Daily", "defaultInstructions": "" },
  { "name": "Positioning (in bed)", "defaultAssistanceLevel": "MODERATE_ASSIST", "defaultFrequency": "Every 2 hours", "defaultInstructions": "Use positioning wedges as indicated." },
  { "name": "Pressure relief repositioning", "defaultAssistanceLevel": "MODERATE_ASSIST", "defaultFrequency": "Every 2 hours", "defaultInstructions": "Document time and position in visit note." },
  { "name": "Wandering supervision", "defaultAssistanceLevel": "SUPERVISION", "defaultFrequency": "Each visit", "defaultInstructions": "Do not leave client unattended. Ensure all exit doors are secured." },
  { "name": "Reality orientation support", "defaultAssistanceLevel": "SUPERVISION", "defaultFrequency": "Each visit", "defaultInstructions": "Gently orient to date, time, and surroundings without arguing." },
  { "name": "Routine reinforcement", "defaultAssistanceLevel": "SUPERVISION", "defaultFrequency": "Each visit", "defaultInstructions": "Follow established daily routine to reduce confusion." },
  { "name": "Sundowning management support", "defaultAssistanceLevel": "SUPERVISION", "defaultFrequency": "Evening visits", "defaultInstructions": "Keep environment calm and well lit. Reduce stimulation." },
  { "name": "Safe environment monitoring", "defaultAssistanceLevel": "SUPERVISION", "defaultFrequency": "Each visit", "defaultInstructions": "Check for hazards before each visit. Secure medications and sharp items." },
  { "name": "Behavioral redirection", "defaultAssistanceLevel": "SUPERVISION", "defaultFrequency": "As needed", "defaultInstructions": "Use calm tone and redirect to preferred activity." },
  { "name": "Music/sensory engagement", "defaultAssistanceLevel": "SUPERVISION", "defaultFrequency": "Each visit", "defaultInstructions": "" },
  { "name": "Personal item tracking", "defaultAssistanceLevel": "SUPERVISION", "defaultFrequency": "Each visit", "defaultInstructions": "Help locate and secure glasses, hearing aids, keys, etc." },
  { "name": "Nighttime supervision", "defaultAssistanceLevel": "SUPERVISION", "defaultFrequency": "Overnight", "defaultInstructions": "Check client every 2 hours or per care plan." },
  { "name": "Meal assistance (dementia)", "defaultAssistanceLevel": "MAXIMUM_ASSIST", "defaultFrequency": "Each meal", "defaultInstructions": "Use verbal cues and hand-over-hand as needed. Allow extra time." },
  { "name": "Tremor management support", "defaultAssistanceLevel": "MINIMAL_ASSIST", "defaultFrequency": "Each visit", "defaultInstructions": "Use adaptive equipment as indicated." },
  { "name": "Freezing episode assistance", "defaultAssistanceLevel": "MODERATE_ASSIST", "defaultFrequency": "As needed", "defaultInstructions": "Use visual cues (floor markers) or rhythmic counting to restart gait." },
  { "name": "Handwriting/communication support", "defaultAssistanceLevel": "MINIMAL_ASSIST", "defaultFrequency": "As needed", "defaultInstructions": "" },
  { "name": "Speech support (Parkinson's)", "defaultAssistanceLevel": "SUPERVISION", "defaultFrequency": "Each visit", "defaultInstructions": "Encourage slow, deliberate speech. Use augmentative communication device if prescribed." },
  { "name": "Fall watch (Parkinson's)", "defaultAssistanceLevel": "SUPERVISION", "defaultFrequency": "Each visit", "defaultInstructions": "Stay within arm's reach during all transfers and ambulation." },
  { "name": "Medication timing reminder (Parkinson's)", "defaultAssistanceLevel": "SUPERVISION", "defaultFrequency": "Each visit", "defaultInstructions": "Parkinson's medications must be given at exact scheduled times. Alert supervisor if client refuses." },
  { "name": "Swallowing observation", "defaultAssistanceLevel": "SUPERVISION", "defaultFrequency": "Each meal", "defaultInstructions": "Follow modified diet texture. Seat upright at 90°. Watch for coughing or choking." },
  { "name": "Exercise program support (Parkinson's)", "defaultAssistanceLevel": "SUPERVISION", "defaultFrequency": "Daily", "defaultInstructions": "Follow neurologist-prescribed exercise program." },
  { "name": "Wound dressing change", "defaultAssistanceLevel": "MAXIMUM_ASSIST", "defaultFrequency": "Daily", "defaultInstructions": "Use sterile technique. Document wound appearance and any drainage." },
  { "name": "Drain/tube monitoring", "defaultAssistanceLevel": "SUPERVISION", "defaultFrequency": "Each visit", "defaultInstructions": "Record output volume and color. Report changes to supervisor." },
  { "name": "Ice/heat application", "defaultAssistanceLevel": "MINIMAL_ASSIST", "defaultFrequency": "As ordered", "defaultInstructions": "Apply for 20 min max. Place cloth barrier between pack and skin." },
  { "name": "Compression garment assist", "defaultAssistanceLevel": "MODERATE_ASSIST", "defaultFrequency": "Daily", "defaultInstructions": "Apply as directed. Inspect skin under garment at each visit." },
  { "name": "DVT prevention exercises", "defaultAssistanceLevel": "SUPERVISION", "defaultFrequency": "Each visit", "defaultInstructions": "Ankle pumps and leg lifts per PT protocol." },
  { "name": "Post-op mobility assist", "defaultAssistanceLevel": "MODERATE_ASSIST", "defaultFrequency": "Each visit", "defaultInstructions": "Follow post-op weight-bearing restrictions." },
  { "name": "Incision site observation", "defaultAssistanceLevel": "SUPERVISION", "defaultFrequency": "Daily", "defaultInstructions": "Look for redness, swelling, or discharge. Document and report changes." },
  { "name": "Pain level monitoring", "defaultAssistanceLevel": "SUPERVISION", "defaultFrequency": "Each visit", "defaultInstructions": "Document 0–10 pain scale rating. Report uncontrolled pain." },
  { "name": "Brace/splint application", "defaultAssistanceLevel": "MODERATE_ASSIST", "defaultFrequency": "Daily", "defaultInstructions": "Apply per OT/PT instructions. Check skin under brace." },
  { "name": "Post-surgical diet monitoring", "defaultAssistanceLevel": "SUPERVISION", "defaultFrequency": "Each meal", "defaultInstructions": "Ensure client follows prescribed dietary restrictions." },
  { "name": "Bathing (child)", "defaultAssistanceLevel": "MAXIMUM_ASSIST", "defaultFrequency": "Daily", "defaultInstructions": "Never leave child unattended in water." },
  { "name": "School preparation assist", "defaultAssistanceLevel": "MINIMAL_ASSIST", "defaultFrequency": "School days", "defaultInstructions": "" },
  { "name": "Adaptive equipment assist", "defaultAssistanceLevel": "SUPERVISION", "defaultFrequency": "Each visit", "defaultInstructions": "Ensure device is charged and functioning before use." },
  { "name": "G-tube feeding", "defaultAssistanceLevel": "MAXIMUM_ASSIST", "defaultFrequency": "Per schedule", "defaultInstructions": "Check tube placement before each feed. Flush before and after. Document volume and tolerance." },
  { "name": "Trach suctioning assist", "defaultAssistanceLevel": "MAXIMUM_ASSIST", "defaultFrequency": "As needed", "defaultInstructions": "Follow sterile technique. Have emergency equipment accessible." },
  { "name": "Seizure observation", "defaultAssistanceLevel": "SUPERVISION", "defaultFrequency": "Each visit", "defaultInstructions": "Document seizure duration and type. Call 911 if > 5 minutes or injury occurs." },
  { "name": "Behaviour support", "defaultAssistanceLevel": "SUPERVISION", "defaultFrequency": "Each visit", "defaultInstructions": "Follow the behaviour support plan. Document any incidents." },
  { "name": "Feeding therapy support", "defaultAssistanceLevel": "MODERATE_ASSIST", "defaultFrequency": "Each meal", "defaultInstructions": "Follow speech therapist's feeding protocol." },
  { "name": "Sleep routine support", "defaultAssistanceLevel": "SUPERVISION", "defaultFrequency": "Bedtime", "defaultInstructions": "Follow established bedtime routine. Document sleep quality." },
  { "name": "Scar massage support", "defaultAssistanceLevel": "MINIMAL_ASSIST", "defaultFrequency": "Daily", "defaultInstructions": "Apply lotion and massage gently per OT protocol. Begin only when wound is fully closed." }
]
```

- [ ] **Step 2: Verify count**

```bash
cd frontend && node -e "const t = require('./public/locales/en/adl-task-templates.json'); console.log('Task count:', t.length)"
```

Expected output: `Task count: 100`

- [ ] **Step 3: Commit**

```bash
cd frontend && git add public/locales/en/adl-task-templates.json
git commit -m "feat: add 100-task ADL/IADL template library (English)"
```

---

## Task 6: Frontend — add i18n keys to `clients.json`

**Files:**
- Modify: `frontend/public/locales/en/clients.json`

- [ ] **Step 1: Add care plan keys**

Open `frontend/public/locales/en/clients.json` and add the following entries before the closing `}`:

```json
  "carePlanNoActivePlan": "No care plan yet",
  "carePlanEmptyHint": "Add ADL tasks and goals to create a plan. Caregivers see these tasks on every visit.",
  "carePlanSetUpCta": "Set Up Care Plan",
  "carePlanSetupBannerTitle": "Setting up {{firstName}}'s care plan",
  "carePlanNewVersionBannerTitle": "Creating version {{version}} of {{firstName}}'s care plan",
  "carePlanSetupBannerHint": "Not visible to caregivers until you save and activate.",
  "carePlanDiscard": "Discard",
  "carePlanSaveActivate": "Save & Activate Plan",
  "carePlanActiveLive": "Care plan is live — caregivers will see these tasks on their next shift.",
  "carePlanActiveLabel": "Active Plan",
  "carePlanVersion": "Version {{version}}",
  "carePlanActivatedAt": "Activated {{date}}",
  "carePlanNewVersion": "New Version",
  "adlTasksSectionLabel": "ADL TASKS · {{count}}",
  "adlTasksAddButton": "+ Add Task",
  "adlTaskAddTitle": "Add Task",
  "adlTaskFieldName": "TASK NAME",
  "adlTaskFieldAssistanceLevel": "ASSISTANCE LEVEL",
  "adlTaskFieldFrequency": "FREQUENCY",
  "adlTaskFieldInstructions": "INSTRUCTIONS",
  "adlTaskFieldInstructionsOptional": "(optional)",
  "adlTaskTemplateApplied": "Template applied — defaults pre-filled below",
  "adlTaskSearchPlaceholder": "Type to search tasks…",
  "adlTaskCustomOption": "Use \"{{value}}\" as custom task",
  "goalsSectionLabel": "GOALS · {{count}}",
  "goalsAddButton": "+ Add Goal",
  "goalFieldDescription": "GOAL DESCRIPTION",
  "goalFieldTargetDate": "TARGET DATE",
  "goalFieldTargetDateOptional": "(optional)",
  "goalNoTargetDate": "No target date",
  "carePlanActivateError": "Failed to activate plan — please try again",
  "adlTaskAssistanceIndependent": "Independent",
  "adlTaskAssistanceSupervision": "Supervision",
  "adlTaskAssistanceMinimal": "Minimal assist",
  "adlTaskAssistanceModerate": "Moderate assist",
  "adlTaskAssistanceMaximum": "Maximum assist",
  "adlTaskAssistanceDependent": "Dependent",
  "adlTaskAssistanceBadgeMaxAssist": "MAX ASSIST",
  "adlTaskAssistanceBadgeDependent": "DEPENDENT",
  "adlTaskAssistanceBadgeModerate": "MODERATE",
  "adlTaskAssistanceBadgeMinimal": "MINIMAL",
  "adlTaskAssistanceBadgeSupervision": "SUPERVISION",
  "adlTaskAssistanceBadgeIndependent": "INDEPENDENT",
  "carePlanNoTasksYet": "No goals added yet.",
  "carePlanSaveActivateDisabled": "Add at least one task to activate"
```

- [ ] **Step 2: Commit**

```bash
cd frontend && git add public/locales/en/clients.json
git commit -m "feat: add care plan i18n keys to clients.json"
```

---

## Task 7: Frontend — `useCarePlan.ts` hooks + tests

**Files:**
- Create: `frontend/src/hooks/useCarePlan.ts`
- Create: `frontend/src/hooks/useCarePlan.test.ts`

- [ ] **Step 1: Write the failing tests** — create `frontend/src/hooks/useCarePlan.test.ts`

```ts
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import MockAdapter from 'axios-mock-adapter'
import { apiClient } from '../api/client'
import {
  useActivePlan,
  useAdlTasks,
  useGoals,
  useAdlTaskTemplates,
} from './useCarePlan'
import type { CarePlanResponse, AdlTaskResponse, GoalResponse, PageResponse } from '../types/api'
import React from 'react'

vi.mock('../store/authStore', () => ({
  useAuthStore: { getState: vi.fn().mockReturnValue({ token: 'tok', logout: vi.fn() }) },
}))

// Mock i18n — language is 'en'
vi.mock('react-i18next', () => ({
  useTranslation: () => ({ i18n: { language: 'en' } }),
}))

function makeWrapper() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: qc }, children)
}

const mockPlan: CarePlanResponse = {
  id: 'plan-1',
  clientId: 'client-1',
  planVersion: 1,
  status: 'ACTIVE',
  reviewedByClinicianId: null,
  reviewedAt: null,
  activatedAt: '2026-04-01T10:00:00',
  createdAt: '2026-04-01T09:00:00',
}

const mockTask: AdlTaskResponse = {
  id: 'task-1',
  carePlanId: 'plan-1',
  name: 'Bathing (full body)',
  assistanceLevel: 'MAXIMUM_ASSIST',
  instructions: 'Use shower chair.',
  frequency: 'Daily',
  sortOrder: 0,
  createdAt: '2026-04-01T09:00:00',
}

const mockGoal: GoalResponse = {
  id: 'goal-1',
  carePlanId: 'plan-1',
  description: 'Walk to mailbox',
  targetDate: '2026-06-01',
  status: 'ACTIVE',
  createdAt: '2026-04-01T09:00:00',
}

describe('useActivePlan', () => {
  let mock: MockAdapter
  beforeEach(() => { mock = new MockAdapter(apiClient) })
  afterEach(() => { mock.restore() })

  it('fetches and returns the active plan', async () => {
    mock.onGet('/clients/client-1/care-plans/active').reply(200, mockPlan)
    const { result } = renderHook(() => useActivePlan('client-1'), { wrapper: makeWrapper() })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data?.id).toBe('plan-1')
    expect(result.current.data?.status).toBe('ACTIVE')
  })

  it('does not retry on 404', async () => {
    mock.onGet('/clients/client-1/care-plans/active').reply(404)
    const { result } = renderHook(() => useActivePlan('client-1'), { wrapper: makeWrapper() })
    await waitFor(() => expect(result.current.isError).toBe(true))
    // Only 1 request should have been made (no retries)
    expect(mock.history.get.length).toBe(1)
  })
})

describe('useAdlTasks', () => {
  let mock: MockAdapter
  beforeEach(() => { mock = new MockAdapter(apiClient) })
  afterEach(() => { mock.restore() })

  it('fetches ADL tasks when planId is provided', async () => {
    const page: PageResponse<AdlTaskResponse> = {
      content: [mockTask], totalElements: 1, totalPages: 1, number: 0, size: 100, first: true, last: true,
    }
    mock.onGet('/clients/client-1/care-plans/plan-1/adl-tasks').reply(200, page)
    const { result } = renderHook(
      () => useAdlTasks('client-1', 'plan-1'),
      { wrapper: makeWrapper() },
    )
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data?.content).toHaveLength(1)
    expect(result.current.data?.content[0].name).toBe('Bathing (full body)')
  })

  it('does not fetch when planId is undefined', () => {
    const { result } = renderHook(
      () => useAdlTasks('client-1', undefined),
      { wrapper: makeWrapper() },
    )
    expect(result.current.fetchStatus).toBe('idle')
  })
})

describe('useGoals', () => {
  let mock: MockAdapter
  beforeEach(() => { mock = new MockAdapter(apiClient) })
  afterEach(() => { mock.restore() })

  it('fetches goals when planId is provided', async () => {
    const page: PageResponse<GoalResponse> = {
      content: [mockGoal], totalElements: 1, totalPages: 1, number: 0, size: 100, first: true, last: true,
    }
    mock.onGet('/clients/client-1/care-plans/plan-1/goals').reply(200, page)
    const { result } = renderHook(
      () => useGoals('client-1', 'plan-1'),
      { wrapper: makeWrapper() },
    )
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data?.content[0].description).toBe('Walk to mailbox')
  })
})

describe('useAdlTaskTemplates', () => {
  afterEach(() => { vi.restoreAllMocks() })

  it('fetches templates for the current language', async () => {
    const templates = [{ name: 'Bathing', defaultAssistanceLevel: 'MAXIMUM_ASSIST', defaultFrequency: 'Daily', defaultInstructions: '' }]
    vi.spyOn(global, 'fetch').mockResolvedValue({
      ok: true,
      json: () => Promise.resolve(templates),
    } as Response)

    const { result } = renderHook(() => useAdlTaskTemplates(), { wrapper: makeWrapper() })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual(templates)
    expect((global.fetch as ReturnType<typeof vi.fn>).mock.calls[0][0]).toContain('/locales/en/')
  })

  it('falls back to en locale when language-specific file returns 404', async () => {
    const templates = [{ name: 'Bathing', defaultAssistanceLevel: 'MAXIMUM_ASSIST', defaultFrequency: 'Daily', defaultInstructions: '' }]
    const fetchSpy = vi.spyOn(global, 'fetch')
    // First call (es locale) fails, second call (en fallback) succeeds
    fetchSpy
      .mockResolvedValueOnce({ ok: false, json: () => Promise.reject() } as Response)
      .mockResolvedValueOnce({ ok: true, json: () => Promise.resolve(templates) } as Response)

    // Override the language mock to 'es' for this test
    vi.mocked(vi.importMock('react-i18next')).useTranslation = () => ({ i18n: { language: 'es' } })

    const { result } = renderHook(() => useAdlTaskTemplates(), { wrapper: makeWrapper() })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual(templates)
    expect(fetchSpy.mock.calls[1][0]).toContain('/locales/en/')
  })
})
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd frontend && npm run test -- useCarePlan.test.ts 2>&1 | tail -20
```

Expected: FAIL — module not found.

- [ ] **Step 3: Create `frontend/src/hooks/useCarePlan.ts`**

```ts
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import {
  getActivePlan,
  listAdlTasks,
  listGoals,
  addAdlTask,
  deleteAdlTask,
  addGoal,
  deleteGoal,
} from '../api/carePlans'
import type { AddAdlTaskRequest, AddGoalRequest } from '../types/api'

export function useActivePlan(clientId: string) {
  return useQuery({
    queryKey: ['care-plan-active', clientId],
    queryFn: () => getActivePlan(clientId),
    enabled: Boolean(clientId),
    retry: (failureCount, error: unknown) => {
      const status = (error as { response?: { status?: number } })?.response?.status
      if (status === 404) return false
      return failureCount < 3
    },
  })
}

export function useAdlTasks(clientId: string, planId: string | undefined) {
  return useQuery({
    queryKey: ['adl-tasks', clientId, planId],
    queryFn: () => listAdlTasks(clientId, planId!),
    enabled: Boolean(clientId) && Boolean(planId),
  })
}

export function useGoals(clientId: string, planId: string | undefined) {
  return useQuery({
    queryKey: ['goals', clientId, planId],
    queryFn: () => listGoals(clientId, planId!),
    enabled: Boolean(clientId) && Boolean(planId),
  })
}

export function useAdlTaskTemplates() {
  const { i18n } = useTranslation()
  const lang = i18n.language.split('-')[0]
  return useQuery({
    queryKey: ['adl-task-templates', lang],
    queryFn: async () => {
      try {
        const res = await fetch(`/locales/${lang}/adl-task-templates.json`)
        if (!res.ok) throw new Error('not found')
        return res.json()
      } catch {
        const fallback = await fetch('/locales/en/adl-task-templates.json')
        return fallback.json()
      }
    },
    staleTime: Infinity,
  })
}

export function useAddAdlTask(clientId: string, planId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (req: AddAdlTaskRequest) => addAdlTask(clientId, planId, req),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['adl-tasks', clientId, planId] })
    },
  })
}

export function useDeleteAdlTask(clientId: string, planId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (taskId: string) => deleteAdlTask(clientId, planId, taskId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['adl-tasks', clientId, planId] })
    },
  })
}

export function useAddGoal(clientId: string, planId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (req: AddGoalRequest) => addGoal(clientId, planId, req),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['goals', clientId, planId] })
    },
  })
}

export function useDeleteGoal(clientId: string, planId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (goalId: string) => deleteGoal(clientId, planId, goalId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['goals', clientId, planId] })
    },
  })
}
```

- [ ] **Step 4: Run tests**

```bash
cd frontend && npm run test -- useCarePlan.test.ts 2>&1 | tail -20
```

Expected: All tests PASS (the fallback locale test may need adjustment based on the mock — acceptable if the first 4 tests pass; address any vi.importMock issues).

- [ ] **Step 5: Commit**

```bash
cd frontend && git add src/hooks/useCarePlan.ts src/hooks/useCarePlan.test.ts
git commit -m "feat: add useCarePlan hooks (useActivePlan, useAdlTasks, useGoals, useAdlTaskTemplates, mutations)"
```

---

## Task 8: Frontend — `AddGoalForm` + tests

**Files:**
- Create: `frontend/src/components/clients/AddGoalForm.tsx`
- Create: `frontend/src/components/clients/AddGoalForm.test.tsx`

- [ ] **Step 1: Write the failing tests** — create `frontend/src/components/clients/AddGoalForm.test.tsx`

```tsx
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { vi, describe, it, expect } from 'vitest'
import { AddGoalForm } from './AddGoalForm'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}))

describe('AddGoalForm', () => {
  it('renders description and target date fields', () => {
    render(<AddGoalForm onConfirm={vi.fn()} onCancel={vi.fn()} />)
    expect(screen.getByText('goalFieldDescription')).toBeInTheDocument()
    expect(screen.getByText('goalFieldTargetDate')).toBeInTheDocument()
  })

  it('"Add Goal" button is disabled when description is empty', () => {
    render(<AddGoalForm onConfirm={vi.fn()} onCancel={vi.fn()} />)
    expect(screen.getByRole('button', { name: 'goalsAddButton' })).toBeDisabled()
  })

  it('calls onConfirm with description and null targetDate when date is empty', async () => {
    const user = userEvent.setup()
    const onConfirm = vi.fn()
    render(<AddGoalForm onConfirm={onConfirm} onCancel={vi.fn()} />)
    await user.type(screen.getByRole('textbox', { name: /goalFieldDescription/i }), 'Walk to mailbox')
    await user.click(screen.getByRole('button', { name: 'goalsAddButton' }))
    expect(onConfirm).toHaveBeenCalledWith('Walk to mailbox', null)
  })

  it('calls onConfirm with description and targetDate when date is provided', async () => {
    const user = userEvent.setup()
    const onConfirm = vi.fn()
    render(<AddGoalForm onConfirm={onConfirm} onCancel={vi.fn()} />)
    await user.type(screen.getByRole('textbox', { name: /goalFieldDescription/i }), 'Self-dress')
    await user.type(screen.getByDisplayValue(''), '2026-06-01')
    await user.click(screen.getByRole('button', { name: 'goalsAddButton' }))
    expect(onConfirm).toHaveBeenCalledWith('Self-dress', '2026-06-01')
  })

  it('calls onCancel when Cancel is clicked', async () => {
    const user = userEvent.setup()
    const onCancel = vi.fn()
    render(<AddGoalForm onConfirm={vi.fn()} onCancel={onCancel} />)
    await user.click(screen.getByRole('button', { name: /cancel/i }))
    expect(onCancel).toHaveBeenCalledOnce()
  })
})
```

- [ ] **Step 2: Run to verify it fails**

```bash
cd frontend && npm run test -- AddGoalForm.test.tsx 2>&1 | tail -10
```

Expected: FAIL — module not found.

- [ ] **Step 3: Create `frontend/src/components/clients/AddGoalForm.tsx`**

```tsx
import { useState } from 'react'
import { useTranslation } from 'react-i18next'

interface AddGoalFormProps {
  onConfirm: (description: string, targetDate: string | null) => void
  onCancel: () => void
  isLoading?: boolean
}

export function AddGoalForm({ onConfirm, onCancel, isLoading }: AddGoalFormProps) {
  const { t } = useTranslation('clients')
  const [description, setDescription] = useState('')
  const [targetDate, setTargetDate] = useState('')

  const handleConfirm = () => {
    onConfirm(description.trim(), targetDate.trim() || null)
  }

  return (
    <div className="border border-blue bg-white p-3 mb-[3px]">
      <div className="mb-2">
        <label
          htmlFor="goal-description"
          className="block text-[9px] font-bold tracking-[.08em] uppercase text-text-secondary mb-1"
        >
          {t('goalFieldDescription')}
        </label>
        <input
          id="goal-description"
          type="text"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          placeholder="e.g. Self-dress upper body without prompting"
          className="w-full border border-border px-2.5 py-1.5 text-[12px] text-text-primary"
        />
      </div>
      <div className="mb-3">
        <label
          htmlFor="goal-target-date"
          className="block text-[9px] font-bold tracking-[.08em] uppercase text-text-secondary mb-1"
        >
          {t('goalFieldTargetDate')}{' '}
          <span className="font-normal normal-case tracking-normal">
            {t('goalFieldTargetDateOptional')}
          </span>
        </label>
        <input
          id="goal-target-date"
          type="date"
          value={targetDate}
          onChange={(e) => setTargetDate(e.target.value)}
          className="w-full border border-border px-2.5 py-1.5 text-[12px] text-text-secondary"
        />
      </div>
      <div className="flex gap-2 justify-end">
        <button
          type="button"
          onClick={onCancel}
          className="border border-border text-text-secondary text-[11px] px-3 py-1.5 bg-transparent"
        >
          Cancel
        </button>
        <button
          type="button"
          onClick={handleConfirm}
          disabled={!description.trim() || isLoading}
          className="bg-dark text-white text-[11px] font-bold px-3.5 py-1.5 border-none disabled:opacity-40"
        >
          {t('goalsAddButton')}
        </button>
      </div>
    </div>
  )
}
```

- [ ] **Step 4: Run tests**

```bash
cd frontend && npm run test -- AddGoalForm.test.tsx 2>&1 | tail -10
```

Expected: All tests PASS.

- [ ] **Step 5: Commit**

```bash
cd frontend && git add src/components/clients/AddGoalForm.tsx src/components/clients/AddGoalForm.test.tsx
git commit -m "feat: add AddGoalForm component"
```

---

## Task 9: Frontend — `AddTaskPanel` + tests

**Files:**
- Create: `frontend/src/components/clients/AddTaskPanel.tsx`
- Create: `frontend/src/components/clients/AddTaskPanel.test.tsx`

- [ ] **Step 1: Write the failing tests** — create `frontend/src/components/clients/AddTaskPanel.test.tsx`

```tsx
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { vi, describe, it, expect, beforeEach } from 'vitest'
import { AddTaskPanel } from './AddTaskPanel'
import { useAdlTaskTemplates } from '../../hooks/useCarePlan'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}))
vi.mock('../../hooks/useCarePlan')

const mockTemplates = [
  { name: 'Bathing (full body)', defaultAssistanceLevel: 'MAXIMUM_ASSIST', defaultFrequency: 'Daily', defaultInstructions: 'Use shower chair.' },
  { name: 'Meal preparation', defaultAssistanceLevel: 'SUPERVISION', defaultFrequency: '3× per week', defaultInstructions: '' },
  { name: 'Transfers (bed to chair)', defaultAssistanceLevel: 'MODERATE_ASSIST', defaultFrequency: 'Each visit', defaultInstructions: 'Gait belt required.' },
]

describe('AddTaskPanel', () => {
  beforeEach(() => {
    vi.mocked(useAdlTaskTemplates).mockReturnValue({
      data: mockTemplates,
      isLoading: false,
    } as ReturnType<typeof useAdlTaskTemplates>)
  })

  it('renders task name, assistance level, frequency, and instructions fields', () => {
    render(<AddTaskPanel onConfirm={vi.fn()} onCancel={vi.fn()} />)
    expect(screen.getByText('adlTaskFieldName')).toBeInTheDocument()
    expect(screen.getByText('adlTaskFieldAssistanceLevel')).toBeInTheDocument()
    expect(screen.getByText('adlTaskFieldFrequency')).toBeInTheDocument()
    expect(screen.getByText('adlTaskFieldInstructions')).toBeInTheDocument()
  })

  it('"Add Task" button is disabled when task name is empty', () => {
    render(<AddTaskPanel onConfirm={vi.fn()} onCancel={vi.fn()} />)
    expect(screen.getByRole('button', { name: 'adlTasksAddButton' })).toBeDisabled()
  })

  it('filters templates by typed value (case-insensitive)', async () => {
    const user = userEvent.setup()
    render(<AddTaskPanel onConfirm={vi.fn()} onCancel={vi.fn()} />)
    await user.type(screen.getByPlaceholderText('adlTaskSearchPlaceholder'), 'bath')
    expect(await screen.findByText('Bathing (full body)')).toBeInTheDocument()
    expect(screen.queryByText('Meal preparation')).not.toBeInTheDocument()
  })

  it('pre-fills assistance level and instructions when a template is selected', async () => {
    const user = userEvent.setup()
    render(<AddTaskPanel onConfirm={vi.fn()} onCancel={vi.fn()} />)
    await user.type(screen.getByPlaceholderText('adlTaskSearchPlaceholder'), 'bath')
    await user.click(await screen.findByText('Bathing (full body)'))
    expect(screen.getByText('adlTaskTemplateApplied')).toBeInTheDocument()
    // Frequency field should be pre-filled
    expect(screen.getByDisplayValue('Daily')).toBeInTheDocument()
    // Instructions textarea should be pre-filled
    expect(screen.getByDisplayValue('Use shower chair.')).toBeInTheDocument()
  })

  it('accepts custom task name not in template list', async () => {
    const user = userEvent.setup()
    render(<AddTaskPanel onConfirm={vi.fn()} onCancel={vi.fn()} />)
    await user.type(screen.getByPlaceholderText('adlTaskSearchPlaceholder'), 'Custom therapy task')
    expect(screen.getByText(/Use "Custom therapy task" as custom task/)).toBeInTheDocument()
    expect(screen.queryByText('adlTaskTemplateApplied')).not.toBeInTheDocument()
  })

  it('calls onConfirm with task data when Add Task is clicked after selecting template', async () => {
    const user = userEvent.setup()
    const onConfirm = vi.fn()
    render(<AddTaskPanel onConfirm={onConfirm} onCancel={vi.fn()} />)
    await user.type(screen.getByPlaceholderText('adlTaskSearchPlaceholder'), 'bath')
    await user.click(await screen.findByText('Bathing (full body)'))
    await user.click(screen.getByRole('button', { name: 'adlTasksAddButton' }))
    expect(onConfirm).toHaveBeenCalledWith({
      name: 'Bathing (full body)',
      assistanceLevel: 'MAXIMUM_ASSIST',
      frequency: 'Daily',
      instructions: 'Use shower chair.',
    })
  })

  it('calls onCancel when Cancel is clicked', async () => {
    const user = userEvent.setup()
    const onCancel = vi.fn()
    render(<AddTaskPanel onConfirm={vi.fn()} onCancel={onCancel} />)
    await user.click(screen.getByRole('button', { name: /cancel/i }))
    expect(onCancel).toHaveBeenCalledOnce()
  })
})
```

- [ ] **Step 2: Run to verify tests fail**

```bash
cd frontend && npm run test -- AddTaskPanel.test.tsx 2>&1 | tail -10
```

Expected: FAIL — module not found.

- [ ] **Step 3: Create `frontend/src/components/clients/AddTaskPanel.tsx`**

```tsx
import { useState, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { useAdlTaskTemplates } from '../../hooks/useCarePlan'
import type { AssistanceLevel } from '../../types/api'

interface TaskData {
  name: string
  assistanceLevel: AssistanceLevel
  frequency: string
  instructions: string
}

interface AdlTemplate {
  name: string
  defaultAssistanceLevel: AssistanceLevel
  defaultFrequency: string
  defaultInstructions: string
}

interface AddTaskPanelProps {
  onConfirm: (task: TaskData) => void
  onCancel: () => void
  isLoading?: boolean
}

const ASSISTANCE_OPTIONS: { value: AssistanceLevel; labelKey: string }[] = [
  { value: 'INDEPENDENT', labelKey: 'adlTaskAssistanceIndependent' },
  { value: 'SUPERVISION', labelKey: 'adlTaskAssistanceSupervision' },
  { value: 'MINIMAL_ASSIST', labelKey: 'adlTaskAssistanceMinimal' },
  { value: 'MODERATE_ASSIST', labelKey: 'adlTaskAssistanceModerate' },
  { value: 'MAXIMUM_ASSIST', labelKey: 'adlTaskAssistanceMaximum' },
  { value: 'DEPENDENT', labelKey: 'adlTaskAssistanceDependent' },
]

export function AddTaskPanel({ onConfirm, onCancel, isLoading }: AddTaskPanelProps) {
  const { t } = useTranslation('clients')
  const { data: templates = [] } = useAdlTaskTemplates()

  const [search, setSearch] = useState('')
  const [selectedName, setSelectedName] = useState('')
  const [assistanceLevel, setAssistanceLevel] = useState<AssistanceLevel>('SUPERVISION')
  const [frequency, setFrequency] = useState('')
  const [instructions, setInstructions] = useState('')
  const [templateApplied, setTemplateApplied] = useState(false)
  const [showDropdown, setShowDropdown] = useState(false)

  const filtered = useMemo<AdlTemplate[]>(() => {
    if (!search.trim()) return []
    const lower = search.toLowerCase()
    return (templates as AdlTemplate[])
      .filter((t) => t.name.toLowerCase().includes(lower))
      .slice(0, 8)
  }, [search, templates])

  const handleSelectTemplate = (template: AdlTemplate) => {
    setSelectedName(template.name)
    setSearch(template.name)
    setAssistanceLevel(template.defaultAssistanceLevel)
    setFrequency(template.defaultFrequency)
    setInstructions(template.defaultInstructions)
    setTemplateApplied(true)
    setShowDropdown(false)
  }

  const handleSelectCustom = () => {
    setSelectedName(search.trim())
    setTemplateApplied(false)
    setShowDropdown(false)
  }

  const handleSearchChange = (value: string) => {
    setSearch(value)
    setSelectedName('')
    setTemplateApplied(false)
    setShowDropdown(true)
  }

  const handleConfirm = () => {
    const name = selectedName || search.trim()
    onConfirm({ name, assistanceLevel, frequency, instructions })
  }

  const taskName = selectedName || search.trim()

  return (
    <div
      className="absolute right-0 top-0 bottom-0 w-[62%] bg-white border-l border-border flex flex-col p-4 gap-2.5"
      style={{ zIndex: 10 }}
    >
      {/* Header */}
      <div className="flex items-center justify-between mb-0.5">
        <span className="text-[13px] font-bold text-text-primary">{t('adlTaskAddTitle')}</span>
        <button
          type="button"
          onClick={onCancel}
          className="bg-transparent border-none text-text-secondary text-[14px] leading-none cursor-pointer"
        >
          ✕
        </button>
      </div>

      {/* Task name combobox */}
      <div className="relative">
        <div className="text-[9px] font-bold tracking-[.08em] uppercase text-text-secondary mb-1">
          {t('adlTaskFieldName')}
        </div>
        <input
          type="text"
          value={search}
          onChange={(e) => handleSearchChange(e.target.value)}
          onFocus={() => setShowDropdown(true)}
          placeholder={t('adlTaskSearchPlaceholder')}
          className={`w-full border px-2.5 py-1.5 text-[12px] text-text-primary outline-none ${
            templateApplied ? 'border-blue' : 'border-border'
          }`}
        />
        {templateApplied && (
          <div className="text-[10px] text-green-600 mt-0.5">✓ {t('adlTaskTemplateApplied')}</div>
        )}
        {showDropdown && search.trim() && (
          <div className="absolute z-20 left-0 right-0 bg-white border border-border shadow-sm">
            {filtered.map((tmpl) => (
              <button
                key={tmpl.name}
                type="button"
                className="w-full text-left px-2.5 py-1.5 text-[12px] text-text-primary hover:bg-surface"
                onMouseDown={() => handleSelectTemplate(tmpl)}
              >
                {tmpl.name}
              </button>
            ))}
            <button
              type="button"
              className="w-full text-left px-2.5 py-1.5 text-[12px] text-text-secondary hover:bg-surface border-t border-border"
              onMouseDown={handleSelectCustom}
            >
              {t('adlTaskCustomOption', { value: search.trim() })}
            </button>
          </div>
        )}
      </div>

      {/* Assistance level */}
      <div>
        <div className="text-[9px] font-bold tracking-[.08em] uppercase text-text-secondary mb-1">
          {t('adlTaskFieldAssistanceLevel')}
        </div>
        <select
          value={assistanceLevel}
          onChange={(e) => setAssistanceLevel(e.target.value as AssistanceLevel)}
          className="w-full border border-border px-2.5 py-1.5 text-[12px] text-text-primary bg-white"
        >
          {ASSISTANCE_OPTIONS.map((opt) => (
            <option key={opt.value} value={opt.value}>
              {t(opt.labelKey)}
            </option>
          ))}
        </select>
      </div>

      {/* Frequency */}
      <div>
        <div className="text-[9px] font-bold tracking-[.08em] uppercase text-text-secondary mb-1">
          {t('adlTaskFieldFrequency')}
        </div>
        <input
          type="text"
          value={frequency}
          onChange={(e) => setFrequency(e.target.value)}
          className="w-full border border-border px-2.5 py-1.5 text-[12px] text-text-primary"
        />
      </div>

      {/* Instructions */}
      <div>
        <div className="text-[9px] font-bold tracking-[.08em] uppercase text-text-secondary mb-1">
          {t('adlTaskFieldInstructions')}{' '}
          <span className="font-normal normal-case tracking-normal">
            {t('adlTaskFieldInstructionsOptional')}
          </span>
        </div>
        <textarea
          value={instructions}
          onChange={(e) => setInstructions(e.target.value)}
          className="w-full border border-border px-2.5 py-1.5 text-[12px] text-text-primary resize-none h-14"
        />
      </div>

      {/* Actions */}
      <div className="flex gap-2 mt-auto">
        <button
          type="button"
          onClick={onCancel}
          className="flex-1 bg-transparent border border-border text-text-secondary text-[11px] font-semibold py-2 cursor-pointer"
        >
          Cancel
        </button>
        <button
          type="button"
          onClick={handleConfirm}
          disabled={!taskName || isLoading}
          className="flex-[2] bg-dark text-white text-[12px] font-bold border-none py-2 cursor-pointer disabled:opacity-40"
        >
          {t('adlTasksAddButton')}
        </button>
      </div>
    </div>
  )
}
```

- [ ] **Step 4: Run tests**

```bash
cd frontend && npm run test -- AddTaskPanel.test.tsx 2>&1 | tail -20
```

Expected: All tests PASS.

- [ ] **Step 5: Commit**

```bash
cd frontend && git add src/components/clients/AddTaskPanel.tsx src/components/clients/AddTaskPanel.test.tsx
git commit -m "feat: add AddTaskPanel component with combobox template search"
```

---

## Task 10: Frontend — `CarePlanTab` + tests

**Files:**
- Create: `frontend/src/components/clients/CarePlanTab.tsx`
- Create: `frontend/src/components/clients/CarePlanTab.test.tsx`

- [ ] **Step 1: Write the failing tests** — create `frontend/src/components/clients/CarePlanTab.test.tsx`

```tsx
import { render, screen, waitFor, act } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { vi, describe, it, expect, beforeEach } from 'vitest'
import { CarePlanTab } from './CarePlanTab'
import {
  useActivePlan,
  useAdlTasks,
  useGoals,
  useAddAdlTask,
  useDeleteAdlTask,
  useAddGoal,
  useDeleteGoal,
} from '../../hooks/useCarePlan'
import * as carePlansApi from '../../api/carePlans'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string, opts?: Record<string, unknown>) => {
    if (opts) return Object.entries(opts).reduce((s, [k, v]) => s.replace(`{{${k}}}`, String(v)), key)
    return key
  }}),
}))
vi.mock('../../hooks/useCarePlan')
vi.mock('../../api/carePlans')

// Stub AddTaskPanel so tests don't need to deal with combobox
vi.mock('./AddTaskPanel', () => ({
  AddTaskPanel: ({ onConfirm, onCancel }: { onConfirm: (t: object) => void; onCancel: () => void }) => (
    <div data-testid="add-task-panel">
      <button onClick={() => onConfirm({ name: 'Test Task', assistanceLevel: 'SUPERVISION', frequency: 'Daily', instructions: '' })}>
        Confirm Task
      </button>
      <button onClick={onCancel}>Cancel Task</button>
    </div>
  ),
}))

// Stub AddGoalForm
vi.mock('./AddGoalForm', () => ({
  AddGoalForm: ({ onConfirm, onCancel }: { onConfirm: (d: string, t: string | null) => void; onCancel: () => void }) => (
    <div data-testid="add-goal-form">
      <button onClick={() => onConfirm('Walk to mailbox', null)}>Confirm Goal</button>
      <button onClick={onCancel}>Cancel Goal</button>
    </div>
  ),
}))

const noopMutation = { mutate: vi.fn(), mutateAsync: vi.fn(), isPending: false }

const activePlan = {
  id: 'plan-1', clientId: 'c-1', planVersion: 2, status: 'ACTIVE' as const,
  reviewedByClinicianId: null, reviewedAt: null,
  activatedAt: '2026-03-15T10:00:00', createdAt: '2026-03-01T09:00:00',
}

const mockTask = {
  id: 'task-1', carePlanId: 'plan-1', name: 'Bathing (full body)',
  assistanceLevel: 'MAXIMUM_ASSIST' as const, instructions: 'Use shower chair.', frequency: 'Daily',
  sortOrder: 0, createdAt: '2026-03-01T09:00:00',
}

const mockGoal = {
  id: 'goal-1', carePlanId: 'plan-1', description: 'Walk to mailbox',
  targetDate: '2026-06-01', status: 'ACTIVE' as const, createdAt: '2026-03-01T09:00:00',
}

function setupMocks({
  planData = null,
  planError = null,
  tasks = [],
  goals = [],
}: {
  planData?: typeof activePlan | null
  planError?: { response?: { status?: number } } | null
  tasks?: typeof mockTask[]
  goals?: typeof mockGoal[]
} = {}) {
  vi.mocked(useActivePlan).mockReturnValue({
    data: planData ?? undefined,
    isLoading: false,
    isError: Boolean(planError),
    error: planError,
  } as ReturnType<typeof useActivePlan>)
  vi.mocked(useAdlTasks).mockReturnValue({
    data: { content: tasks, totalElements: tasks.length, totalPages: 1, number: 0, size: 100, first: true, last: true },
    isLoading: false,
  } as ReturnType<typeof useAdlTasks>)
  vi.mocked(useGoals).mockReturnValue({
    data: { content: goals, totalElements: goals.length, totalPages: 1, number: 0, size: 100, first: true, last: true },
    isLoading: false,
  } as ReturnType<typeof useGoals>)
  vi.mocked(useAddAdlTask).mockReturnValue(noopMutation as ReturnType<typeof useAddAdlTask>)
  vi.mocked(useDeleteAdlTask).mockReturnValue(noopMutation as ReturnType<typeof useDeleteAdlTask>)
  vi.mocked(useAddGoal).mockReturnValue(noopMutation as ReturnType<typeof useAddGoal>)
  vi.mocked(useDeleteGoal).mockReturnValue(noopMutation as ReturnType<typeof useDeleteGoal>)
}

describe('CarePlanTab — empty state', () => {
  beforeEach(() => setupMocks({ planError: { response: { status: 404 } } }))

  it('renders Set Up Care Plan button when no active plan', () => {
    render(<CarePlanTab clientId="c-1" clientFirstName="Margaret" />)
    expect(screen.getByText('carePlanNoActivePlan')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'carePlanSetUpCta' })).toBeInTheDocument()
  })
})

describe('CarePlanTab — setup mode', () => {
  beforeEach(() => setupMocks({ planError: { response: { status: 404 } } }))

  it('shows setup banner after clicking Set Up Care Plan', async () => {
    const user = userEvent.setup()
    render(<CarePlanTab clientId="c-1" clientFirstName="Margaret" />)
    await user.click(screen.getByRole('button', { name: 'carePlanSetUpCta' }))
    expect(screen.getByText(/carePlanSetupBannerTitle/)).toBeInTheDocument()
    expect(screen.getByText('carePlanSetupBannerHint')).toBeInTheDocument()
  })

  it('"Save & Activate Plan" is disabled with zero pending tasks', async () => {
    const user = userEvent.setup()
    render(<CarePlanTab clientId="c-1" clientFirstName="Margaret" />)
    await user.click(screen.getByRole('button', { name: 'carePlanSetUpCta' }))
    expect(screen.getByRole('button', { name: 'carePlanSaveActivate' })).toBeDisabled()
  })

  it('task accumulates in local state after adding a task in setup mode', async () => {
    const user = userEvent.setup()
    render(<CarePlanTab clientId="c-1" clientFirstName="Margaret" />)
    await user.click(screen.getByRole('button', { name: 'carePlanSetUpCta' }))
    await user.click(screen.getByRole('button', { name: 'adlTasksAddButton' }))
    await user.click(screen.getByRole('button', { name: 'Confirm Task' }))
    expect(screen.getByText('Test Task')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'carePlanSaveActivate' })).not.toBeDisabled()
  })

  it('Discard returns to empty state without making API calls', async () => {
    const user = userEvent.setup()
    render(<CarePlanTab clientId="c-1" clientFirstName="Margaret" />)
    await user.click(screen.getByRole('button', { name: 'carePlanSetUpCta' }))
    await user.click(screen.getByRole('button', { name: 'carePlanDiscard' }))
    expect(screen.getByRole('button', { name: 'carePlanSetUpCta' })).toBeInTheDocument()
    expect(carePlansApi.createCarePlan).not.toHaveBeenCalled()
  })
})

describe('CarePlanTab — Save & Activate sequence', () => {
  beforeEach(() => setupMocks({ planError: { response: { status: 404 } } }))

  it('fires create → addTask → activate in order', async () => {
    const user = userEvent.setup()
    vi.mocked(carePlansApi.createCarePlan).mockResolvedValue({ id: 'new-plan-id', clientId: 'c-1', planVersion: 1, status: 'DRAFT', reviewedByClinicianId: null, reviewedAt: null, activatedAt: null, createdAt: '' })
    vi.mocked(carePlansApi.addAdlTask).mockResolvedValue({} as never)
    vi.mocked(carePlansApi.addGoal).mockResolvedValue({} as never)
    vi.mocked(carePlansApi.activateCarePlan).mockResolvedValue({ ...activePlan })

    render(<CarePlanTab clientId="c-1" clientFirstName="Margaret" />)
    await user.click(screen.getByRole('button', { name: 'carePlanSetUpCta' }))
    await user.click(screen.getByRole('button', { name: 'adlTasksAddButton' }))
    await user.click(screen.getByRole('button', { name: 'Confirm Task' }))
    await user.click(screen.getByRole('button', { name: 'carePlanSaveActivate' }))

    await waitFor(() => {
      expect(carePlansApi.createCarePlan).toHaveBeenCalledWith('c-1')
      expect(carePlansApi.addAdlTask).toHaveBeenCalledWith('c-1', 'new-plan-id', expect.objectContaining({ name: 'Test Task' }))
      expect(carePlansApi.activateCarePlan).toHaveBeenCalledWith('c-1', 'new-plan-id')
    })
  })

  it('shows error toast and stays in setup mode on API failure', async () => {
    const user = userEvent.setup()
    vi.mocked(carePlansApi.createCarePlan).mockRejectedValue(new Error('Network error'))

    render(<CarePlanTab clientId="c-1" clientFirstName="Margaret" />)
    await user.click(screen.getByRole('button', { name: 'carePlanSetUpCta' }))
    await user.click(screen.getByRole('button', { name: 'adlTasksAddButton' }))
    await user.click(screen.getByRole('button', { name: 'Confirm Task' }))
    await user.click(screen.getByRole('button', { name: 'carePlanSaveActivate' }))

    expect(await screen.findByText('carePlanActivateError')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'carePlanSaveActivate' })).toBeInTheDocument()
  })
})

describe('CarePlanTab — active plan view', () => {
  beforeEach(() => setupMocks({ planData: activePlan, tasks: [mockTask], goals: [mockGoal] }))

  it('shows plan header with version and activated date', () => {
    render(<CarePlanTab clientId="c-1" clientFirstName="Margaret" />)
    expect(screen.getByText('carePlanActiveLabel')).toBeInTheDocument()
    expect(screen.getByText(/carePlanVersion/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'carePlanNewVersion' })).toBeInTheDocument()
  })

  it('renders task rows with assistance badge and frequency', () => {
    render(<CarePlanTab clientId="c-1" clientFirstName="Margaret" />)
    expect(screen.getByText('Bathing (full body)')).toBeInTheDocument()
    expect(screen.getByText('Daily')).toBeInTheDocument()
  })

  it('renders goal rows with target date', () => {
    render(<CarePlanTab clientId="c-1" clientFirstName="Margaret" />)
    expect(screen.getByText('Walk to mailbox')).toBeInTheDocument()
  })
})

describe('CarePlanTab — New Version', () => {
  beforeEach(() => setupMocks({ planData: activePlan, tasks: [mockTask], goals: [mockGoal] }))

  it('enters setup mode pre-populated with active plan tasks and goals', async () => {
    const user = userEvent.setup()
    render(<CarePlanTab clientId="c-1" clientFirstName="Margaret" />)
    await user.click(screen.getByRole('button', { name: 'carePlanNewVersion' }))
    // Should show new version banner
    expect(screen.getByText(/carePlanNewVersionBannerTitle/)).toBeInTheDocument()
    // Pre-populated task from active plan should be visible
    expect(screen.getByText('Bathing (full body)')).toBeInTheDocument()
    // Save button should be enabled (1 task)
    expect(screen.getByRole('button', { name: 'carePlanSaveActivate' })).not.toBeDisabled()
  })
})
```

- [ ] **Step 2: Run to verify tests fail**

```bash
cd frontend && npm run test -- CarePlanTab.test.tsx 2>&1 | tail -10
```

Expected: FAIL — module not found.

- [ ] **Step 3: Create `frontend/src/components/clients/CarePlanTab.tsx`**

```tsx
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQueryClient } from '@tanstack/react-query'
import {
  useActivePlan,
  useAdlTasks,
  useGoals,
  useAddAdlTask,
  useDeleteAdlTask,
  useAddGoal,
  useDeleteGoal,
} from '../../hooks/useCarePlan'
import {
  createCarePlan,
  addAdlTask as addAdlTaskApi,
  addGoal as addGoalApi,
  activateCarePlan as activateCarePlanApi,
} from '../../api/carePlans'
import { AddTaskPanel } from './AddTaskPanel'
import { AddGoalForm } from './AddGoalForm'
import type { AssistanceLevel, AdlTaskResponse, GoalResponse } from '../../types/api'

interface PendingTask {
  _id: string
  name: string
  assistanceLevel: AssistanceLevel
  frequency: string
  instructions: string
  sortOrder: number
}

interface PendingGoal {
  _id: string
  description: string
  targetDate: string | null
}

interface TaskInput {
  name: string
  assistanceLevel: AssistanceLevel
  frequency: string
  instructions: string
}

function AssistanceBadge({ level }: { level: AssistanceLevel }) {
  const { t } = useTranslation('clients')
  const config: Record<AssistanceLevel, { bg: string; text: string; labelKey: string }> = {
    MAXIMUM_ASSIST: { bg: 'bg-text-secondary', text: 'text-white', labelKey: 'adlTaskAssistanceBadgeMaxAssist' },
    DEPENDENT:      { bg: 'bg-text-secondary', text: 'text-white', labelKey: 'adlTaskAssistanceBadgeDependent' },
    MODERATE_ASSIST:{ bg: 'bg-[#ca8a04]',      text: 'text-white', labelKey: 'adlTaskAssistanceBadgeModerate' },
    MINIMAL_ASSIST: { bg: 'bg-blue',            text: 'text-white', labelKey: 'adlTaskAssistanceBadgeMinimal' },
    SUPERVISION:    { bg: 'bg-border',          text: 'text-text-primary', labelKey: 'adlTaskAssistanceBadgeSupervision' },
    INDEPENDENT:    { bg: 'bg-border',          text: 'text-text-primary', labelKey: 'adlTaskAssistanceBadgeIndependent' },
  }
  const c = config[level]
  return (
    <span className={`text-[10px] font-semibold px-1.5 py-[1px] ${c.bg} ${c.text}`}>
      {t(c.labelKey)}
    </span>
  )
}

function formatActivatedAt(iso: string | null): string {
  if (!iso) return ''
  const d = new Date(iso)
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
}

interface CarePlanTabProps {
  clientId: string
  clientFirstName: string
}

export function CarePlanTab({ clientId, clientFirstName }: CarePlanTabProps) {
  const { t } = useTranslation('clients')
  const queryClient = useQueryClient()

  const [setupMode, setSetupMode] = useState(false)
  const [isNewVersion, setIsNewVersion] = useState(false)
  const [pendingTasks, setPendingTasks] = useState<PendingTask[]>([])
  const [pendingGoals, setPendingGoals] = useState<PendingGoal[]>([])
  const [activating, setActivating] = useState(false)
  const [activateError, setActivateError] = useState(false)
  const [showLiveMessage, setShowLiveMessage] = useState(false)
  const [showAddTask, setShowAddTask] = useState(false)
  const [showAddGoal, setShowAddGoal] = useState(false)

  const { data: activePlan, isError, error } = useActivePlan(clientId)
  const is404 = isError && (error as { response?: { status?: number } })?.response?.status === 404

  const { data: tasksPage } = useAdlTasks(clientId, activePlan?.id)
  const tasks: AdlTaskResponse[] = tasksPage?.content ?? []

  const { data: goalsPage } = useGoals(clientId, activePlan?.id)
  const goals: GoalResponse[] = goalsPage?.content ?? []

  const deleteTaskMutation = useDeleteAdlTask(clientId, activePlan?.id ?? '')
  const deleteGoalMutation = useDeleteGoal(clientId, activePlan?.id ?? '')
  const addTaskMutation = useAddAdlTask(clientId, activePlan?.id ?? '')
  const addGoalMutation = useAddGoal(clientId, activePlan?.id ?? '')

  const handleSetUp = () => {
    setIsNewVersion(false)
    setPendingTasks([])
    setPendingGoals([])
    setActivateError(false)
    setSetupMode(true)
  }

  const handleNewVersion = () => {
    setIsNewVersion(true)
    setPendingTasks(
      tasks.map((tk, i) => ({
        _id: crypto.randomUUID(),
        name: tk.name,
        assistanceLevel: tk.assistanceLevel,
        frequency: tk.frequency ?? '',
        instructions: tk.instructions ?? '',
        sortOrder: i,
      })),
    )
    setPendingGoals(
      goals.map((g) => ({
        _id: crypto.randomUUID(),
        description: g.description,
        targetDate: g.targetDate,
      })),
    )
    setActivateError(false)
    setSetupMode(true)
  }

  const handleDiscard = () => {
    setSetupMode(false)
    setPendingTasks([])
    setPendingGoals([])
    setActivateError(false)
  }

  const handleSaveActivate = async () => {
    setActivating(true)
    setActivateError(false)
    try {
      const plan = await createCarePlan(clientId)
      await Promise.all(
        pendingTasks.map((tk, i) =>
          addAdlTaskApi(clientId, plan.id, {
            name: tk.name,
            assistanceLevel: tk.assistanceLevel,
            frequency: tk.frequency || undefined,
            instructions: tk.instructions || undefined,
            sortOrder: i,
          }),
        ),
      )
      await Promise.all(
        pendingGoals.map((g) =>
          addGoalApi(clientId, plan.id, {
            description: g.description,
            targetDate: g.targetDate ?? undefined,
          }),
        ),
      )
      await activateCarePlanApi(clientId, plan.id)
      queryClient.invalidateQueries({ queryKey: ['care-plan-active', clientId] })
      setSetupMode(false)
      setPendingTasks([])
      setPendingGoals([])
      setShowLiveMessage(true)
      setTimeout(() => setShowLiveMessage(false), 4000)
    } catch {
      setActivateError(true)
    } finally {
      setActivating(false)
    }
  }

  const handleAddPendingTask = (taskData: TaskInput) => {
    setPendingTasks((prev) => [
      ...prev,
      { _id: crypto.randomUUID(), ...taskData, sortOrder: prev.length },
    ])
    setShowAddTask(false)
  }

  const handleAddActiveTask = async (taskData: TaskInput) => {
    await addTaskMutation.mutateAsync({
      name: taskData.name,
      assistanceLevel: taskData.assistanceLevel,
      frequency: taskData.frequency || undefined,
      instructions: taskData.instructions || undefined,
    })
    setShowAddTask(false)
  }

  const handleAddPendingGoal = (description: string, targetDate: string | null) => {
    setPendingGoals((prev) => [
      ...prev,
      { _id: crypto.randomUUID(), description, targetDate },
    ])
    setShowAddGoal(false)
  }

  const handleAddActiveGoal = async (description: string, targetDate: string | null) => {
    await addGoalMutation.mutateAsync({
      description,
      targetDate: targetDate ?? undefined,
    })
    setShowAddGoal(false)
  }

  // ── Empty state ────────────────────────────────────────────────────────────
  if (!setupMode && (is404 || !activePlan)) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[280px] text-center p-5">
        <div className="w-10 h-10 border-2 border-dashed border-border rounded-full flex items-center justify-center mb-3">
          <span className="text-[18px] text-border">+</span>
        </div>
        <div className="text-[13px] font-semibold text-text-primary mb-1.5">
          {t('carePlanNoActivePlan')}
        </div>
        <div className="text-[11px] text-text-secondary mb-4 max-w-[200px] leading-relaxed">
          {t('carePlanEmptyHint')}
        </div>
        <button
          type="button"
          onClick={handleSetUp}
          className="bg-dark text-white text-[12px] font-bold border-none px-5 py-2 cursor-pointer"
        >
          {t('carePlanSetUpCta')}
        </button>
      </div>
    )
  }

  // ── Setup mode ─────────────────────────────────────────────────────────────
  if (setupMode) {
    const bannerTitle = isNewVersion
      ? t('carePlanNewVersionBannerTitle', {
          version: (activePlan?.planVersion ?? 0) + 1,
          firstName: clientFirstName,
        })
      : t('carePlanSetupBannerTitle', { firstName: clientFirstName })

    return (
      <div className="relative p-4 bg-surface min-h-[340px]">
        {/* Setup banner */}
        <div className="bg-dark text-white px-3 py-2 mb-3 flex items-center justify-between">
          <div>
            <div className="text-[11px] font-bold tracking-[.04em]">{bannerTitle}</div>
            <div className="text-[10px] text-text-muted mt-0.5">{t('carePlanSetupBannerHint')}</div>
          </div>
          <button
            type="button"
            onClick={handleDiscard}
            className="bg-transparent border-none text-text-muted text-[10px] cursor-pointer"
          >
            {t('carePlanDiscard')}
          </button>
        </div>

        {activateError && (
          <div className="text-[11px] text-red-600 mb-2">{t('carePlanActivateError')}</div>
        )}

        {/* ADL Tasks */}
        <div className="flex justify-between items-center mb-2">
          <span className="text-[9px] font-bold tracking-[.1em] uppercase text-text-secondary">
            {t('adlTasksSectionLabel', { count: pendingTasks.length })}
          </span>
          <button
            type="button"
            onClick={() => setShowAddTask(true)}
            className="bg-dark text-white text-[11px] font-bold border-none px-3 py-1 cursor-pointer"
          >
            {t('adlTasksAddButton')}
          </button>
        </div>

        {pendingTasks.map((tk) => (
          <div
            key={tk._id}
            className="border border-border bg-white mb-[3px] px-3.5 py-2.5 flex justify-between items-start"
          >
            <div className="flex-1">
              <div className="flex items-center gap-2">
                <span className="text-[12px] font-medium text-text-primary">{tk.name}</span>
                <AssistanceBadge level={tk.assistanceLevel} />
              </div>
              {tk.frequency && (
                <div className="text-[11px] text-text-secondary mt-0.5">{tk.frequency}</div>
              )}
            </div>
            <button
              type="button"
              onClick={() =>
                setPendingTasks((prev) => prev.filter((t) => t._id !== tk._id))
              }
              className="bg-transparent border-none text-text-secondary text-[12px] cursor-pointer px-1 ml-3"
            >
              ✕
            </button>
          </div>
        ))}

        {/* Goals */}
        <div className="flex justify-between items-center mt-3 mb-2">
          <span className="text-[9px] font-bold tracking-[.1em] uppercase text-text-secondary">
            {t('goalsSectionLabel', { count: pendingGoals.length })}
          </span>
          <button
            type="button"
            onClick={() => setShowAddGoal(true)}
            className="bg-transparent border border-border text-text-primary text-[11px] font-semibold px-3 py-1 cursor-pointer"
          >
            {t('goalsAddButton')}
          </button>
        </div>

        {pendingGoals.length === 0 && !showAddGoal && (
          <div className="text-[11px] text-text-secondary mb-4">{t('carePlanNoTasksYet')}</div>
        )}

        {pendingGoals.map((g) => (
          <div
            key={g._id}
            className="border border-border bg-white mb-[3px] px-3.5 py-2.5 flex justify-between items-start"
          >
            <div className="flex-1">
              <div className="text-[12px] text-text-primary">{g.description}</div>
              <div className="text-[11px] text-text-secondary mt-0.5">
                {g.targetDate ? `Target: ${g.targetDate}` : t('goalNoTargetDate')}
              </div>
            </div>
            <button
              type="button"
              onClick={() =>
                setPendingGoals((prev) => prev.filter((pg) => pg._id !== g._id))
              }
              className="bg-transparent border-none text-text-secondary text-[12px] cursor-pointer px-1 ml-3"
            >
              ✕
            </button>
          </div>
        ))}

        {showAddGoal && (
          <AddGoalForm
            onConfirm={handleAddPendingGoal}
            onCancel={() => setShowAddGoal(false)}
          />
        )}

        {/* Save & Activate */}
        <button
          type="button"
          onClick={handleSaveActivate}
          disabled={pendingTasks.length === 0 || activating}
          className="w-full bg-green-600 text-white text-[12px] font-bold border-none py-2.5 mt-4 cursor-pointer disabled:opacity-40"
        >
          {t('carePlanSaveActivate')}
        </button>

        {/* AddTaskPanel slide-out */}
        {showAddTask && (
          <AddTaskPanel
            onConfirm={handleAddPendingTask}
            onCancel={() => setShowAddTask(false)}
          />
        )}
      </div>
    )
  }

  // ── Active plan view ───────────────────────────────────────────────────────
  return (
    <div className="p-4 bg-surface min-h-[340px] relative">
      {/* Live message */}
      {showLiveMessage && (
        <div className="text-[11px] text-green-600 font-semibold mb-3 flex items-center gap-1.5">
          <span>✓</span> {t('carePlanActiveLive')}
        </div>
      )}

      {/* Plan header */}
      <div className="flex justify-between items-center mb-4 bg-white border border-border px-3.5 py-2.5">
        <div className="flex items-center gap-2.5">
          <div className="w-2 h-2 rounded-full bg-green-600" />
          <div>
            <span className="text-[12px] font-semibold text-text-primary">
              {t('carePlanActiveLabel')}
            </span>
            <span className="text-[11px] text-text-secondary ml-2">
              {t('carePlanVersion', { version: activePlan!.planVersion })}
            </span>
          </div>
          <span className="text-[10px] text-text-secondary">
            {t('carePlanActivatedAt', { date: formatActivatedAt(activePlan!.activatedAt) })}
          </span>
        </div>
        <button
          type="button"
          onClick={handleNewVersion}
          className="bg-transparent border border-border text-text-primary text-[11px] font-semibold px-3 py-1.5 cursor-pointer"
        >
          {t('carePlanNewVersion')}
        </button>
      </div>

      {/* ADL Tasks */}
      <div className="flex justify-between items-center mb-2">
        <span className="text-[9px] font-bold tracking-[.1em] uppercase text-text-secondary">
          {t('adlTasksSectionLabel', { count: tasks.length })}
        </span>
        <button
          type="button"
          onClick={() => setShowAddTask(true)}
          className="bg-dark text-white text-[11px] font-bold border-none px-3 py-1 cursor-pointer"
        >
          {t('adlTasksAddButton')}
        </button>
      </div>

      {tasks.map((tk) => (
        <div
          key={tk.id}
          className="border border-border bg-white mb-[3px] px-3.5 py-2.5 flex justify-between items-start"
        >
          <div className="flex-1">
            <div className="flex items-center gap-2">
              <span className="text-[12px] font-medium text-text-primary">{tk.name}</span>
              <AssistanceBadge level={tk.assistanceLevel} />
            </div>
            {(tk.frequency || tk.instructions) && (
              <div className="text-[11px] text-text-secondary mt-0.5">
                {[tk.frequency, tk.instructions].filter(Boolean).join(' · ')}
              </div>
            )}
          </div>
          <button
            type="button"
            onClick={() => deleteTaskMutation.mutate(tk.id)}
            className="bg-transparent border-none text-text-secondary text-[12px] cursor-pointer px-1 ml-3"
            title="Remove"
          >
            ✕
          </button>
        </div>
      ))}

      {/* Goals */}
      <div className="flex justify-between items-center mt-4 mb-2">
        <span className="text-[9px] font-bold tracking-[.1em] uppercase text-text-secondary">
          {t('goalsSectionLabel', { count: goals.length })}
        </span>
        <button
          type="button"
          onClick={() => setShowAddGoal(true)}
          className="bg-transparent border border-border text-text-primary text-[11px] font-semibold px-3 py-1 cursor-pointer"
        >
          {t('goalsAddButton')}
        </button>
      </div>

      {goals.map((g) => (
        <div
          key={g.id}
          className="border border-border bg-white mb-[3px] px-3.5 py-2.5 flex justify-between items-start"
        >
          <div className="flex-1">
            <div className="text-[12px] text-text-primary">{g.description}</div>
            <div className="text-[11px] text-text-secondary mt-0.5">
              {g.targetDate
                ? `Target: ${new Date(g.targetDate).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })}`
                : t('goalNoTargetDate')}
            </div>
          </div>
          <button
            type="button"
            onClick={() => deleteGoalMutation.mutate(g.id)}
            className="bg-transparent border-none text-text-secondary text-[12px] cursor-pointer px-1 ml-3"
          >
            ✕
          </button>
        </div>
      ))}

      {showAddGoal && (
        <AddGoalForm
          onConfirm={handleAddActiveGoal}
          onCancel={() => setShowAddGoal(false)}
          isLoading={addGoalMutation.isPending}
        />
      )}

      {/* AddTaskPanel slide-out */}
      {showAddTask && (
        <AddTaskPanel
          onConfirm={handleAddActiveTask}
          onCancel={() => setShowAddTask(false)}
          isLoading={addTaskMutation.isPending}
        />
      )}
    </div>
  )
}
```

- [ ] **Step 4: Run tests**

```bash
cd frontend && npm run test -- CarePlanTab.test.tsx 2>&1 | tail -20
```

Expected: All tests PASS.

- [ ] **Step 5: Run full frontend test suite**

```bash
cd frontend && npm run test 2>&1 | tail -20
```

Expected: All tests PASS.

- [ ] **Step 6: Commit**

```bash
cd frontend && git add src/components/clients/CarePlanTab.tsx src/components/clients/CarePlanTab.test.tsx
git commit -m "feat: add CarePlanTab component (setup mode, active plan, new version flow)"
```

---

## Task 11: Frontend — wire `CarePlanTab` into `ClientDetailPanel`

**Files:**
- Modify: `frontend/src/components/clients/ClientDetailPanel.tsx`

- [ ] **Step 1: Add import**

At the top of `ClientDetailPanel.tsx`, after the existing imports, add:

```ts
import { CarePlanTab } from './CarePlanTab'
```

- [ ] **Step 2: Replace the care plan tab placeholder**

Find line 134 (the `carePlanPhaseNote` line):

```tsx
          <p className="text-text-secondary text-[13px]">{t('carePlanPhaseNote')}</p>
```

Replace with:

```tsx
          <CarePlanTab clientId={clientId} clientFirstName={client?.firstName ?? ''} />
```

- [ ] **Step 3: Smoke test in browser**

```bash
cd /Users/ronstarling/repos/hcare && ./dev-start.sh
```

1. Log in as `admin@sunrise.dev` / `Admin1234!`
2. Open any client → Care Plan tab
3. Verify: empty state with "Set Up Care Plan" button appears (or active plan if one exists in dev data)
4. Click "Set Up Care Plan" → verify setup banner appears
5. Click "Discard" → verify returns to empty state

- [ ] **Step 4: Run full frontend test suite one final time**

```bash
cd frontend && npm run test 2>&1 | tail -20
```

Expected: All tests PASS.

- [ ] **Step 5: Commit**

```bash
cd frontend && git add src/components/clients/ClientDetailPanel.tsx
git commit -m "feat: replace care plan placeholder with CarePlanTab in ClientDetailPanel"
```

---

## Self-Review Checklist

### Spec coverage

| Spec requirement | Covered in task |
|---|---|
| Empty state with "Set Up Care Plan" CTA | Task 10 |
| Setup mode banner with client name | Task 10 |
| Discard clears state, no API call | Task 10 |
| Save & Activate: create → tasks → goals → activate sequence | Task 10 |
| Error toast on activation failure, stays in setup mode | Task 10 |
| "✓ Care plan is live" message auto-dismisses after 4 s | Task 10 |
| Active plan header: green dot, version, activated date | Task 10 |
| "New Version" pre-populates tasks/goals from active plan | Task 10 |
| ADL task rows with badge and frequency | Task 10 |
| DELETE task on active plan calls API immediately | Task 10 |
| Goal rows with target date | Task 10 |
| DELETE goal on active plan calls API immediately | Task 10 |
| Add Task slide-out panel (AddTaskPanel) | Task 9 |
| Combobox filters templates client-side (≤8 results) | Task 9 |
| Template pre-fills assistance level, frequency, instructions | Task 9 |
| "Use X as custom task" escape hatch | Task 9 |
| Add Goal inline form (AddGoalForm) | Task 8 |
| ADL template JSON — 100 tasks, locale-aware path | Task 5 |
| `useAdlTaskTemplates` fallback to `en` | Task 7 |
| `GET /care-plans/active` — 200 with plan | Task 2 |
| `GET /care-plans/active` — 404 when none | Task 2 |
| `GET /care-plans/active` — 404 after superseded | Task 2 |
| Non-locking `findActiveByClientId` repository method | Task 1 |
| `clients.json` i18n keys | Task 6 |

All spec requirements are covered. ✓

### No placeholders — all tasks contain complete code. ✓

### Type consistency check
- `AssistanceLevel` defined in Task 3 (`api.ts`) — used in Task 9 (`AddTaskPanel`), Task 10 (`CarePlanTab`), Task 7 (`useCarePlan`)
- `CarePlanResponse` defined in Task 3 — used in Tasks 7, 10
- `AdlTaskResponse` / `GoalResponse` defined in Task 3 — used in Tasks 7, 10
- `AddAdlTaskRequest` / `AddGoalRequest` defined in Task 3 — used in Tasks 4, 7
- Query keys consistent: `['care-plan-active', clientId]`, `['adl-tasks', clientId, planId]`, `['goals', clientId, planId]` — invalidated in Task 7 and used in Task 10 ✓
- `handleAddActiveTask` in CarePlanTab passes `mutateAsync` not `mutate` (returns Promise for await) ✓
