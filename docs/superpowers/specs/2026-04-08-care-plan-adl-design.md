# Care Plan & ADL Management — Design Spec

**Date:** 2026-04-08
**Status:** Approved through brainstorming session

---

## 1. Overview

This spec covers the Care Plan tab inside `ClientDetailPanel` — the full workflow for creating, populating, and versioning a client's care plan including ADL tasks and goals. The feature replaces the `carePlanPhaseNote` placeholder currently shown in the tab.

### In scope
- Care Plan tab UI (active plan view, empty state, setup mode)
- ADL task management: add via combobox sub-panel, delete
- Goal management: add via inline form, delete
- First-plan creation flow (setup mode → Save & Activate)
- New Version flow (copies tasks + goals from active plan)
- 100-task ADL/IADL template library (locale-aware static JSON)
- One new backend endpoint (`GET /care-plans/active`)

### Out of scope
- Functional scoring / ADL trend charts (future phase)
- Recertification / scheduling-driven versioning
- ADL task editing (delete + re-add is the workflow)
- Agency-extensible template library
- Mobile caregiver ADL completion UI
- Plan version history view
- HHCS clinical review sign-off workflow

---

## 2. User-Facing Flows

### 2.1 Empty state — no care plan exists

The Care Plan tab shows a centred empty state:
- Dashed circle icon
- Heading: "No care plan yet"
- Sub-text: "Add ADL tasks and goals to create a plan. Caregivers see these tasks on every visit."
- Single CTA button (dark, primary): **"Set Up Care Plan"**

Clicking "Set Up Care Plan" enters setup mode.

### 2.2 Setup mode — first plan creation

Setup mode is a transient UI state. Nothing is persisted to the backend until the admin clicks "Save & Activate Plan".

**Visual treatment:**
- A full-width dark banner replaces the plan header bar:
  - Title: "Setting up [Client First Name]'s care plan"
  - Sub-text: "Not visible to caregivers until you save and activate."
  - Right-aligned ghost link: "Discard"
- Below the banner: the ADL Tasks section and Goals section render normally with their "+" buttons
- At the bottom of the tab: a full-width green **"Save & Activate Plan"** button (disabled until at least one task is added)

**Task deletion in setup mode:** clicking ✕ on a pending task removes it from local React state only — no API call.

**Discard:** returns to the empty state, clears all pending tasks and goals. No API call required (nothing was persisted).

### 2.3 Save & Activate sequence

Fires when admin clicks "Save & Activate Plan":

```
1. POST /api/v1/clients/{clientId}/care-plans
   → response: { id: planId, ... }

2. Promise.all — add all pending tasks in parallel:
   POST /api/v1/clients/{clientId}/care-plans/{planId}/adl-tasks
   (one request per task)

3. Promise.all — add all pending goals in parallel:
   POST /api/v1/clients/{clientId}/care-plans/{planId}/goals
   (one request per goal, if any)

4. POST /api/v1/clients/{clientId}/care-plans/{planId}/activate
   → supersedes previous ACTIVE plan (handled server-side), plan goes ACTIVE

5. Invalidate React Query cache for active plan → UI re-renders into active plan view
```

**On any error in steps 1–4:** show error toast ("Failed to activate plan — please try again"), remain in setup mode. The DRAFT plan in the DB is invisible to caregivers; no cleanup is needed.

**Post-activation:** a green inline message appears briefly at the top of the tab: "✓ Care plan is live — caregivers will see these tasks on their next shift." It auto-dismisses after 4 seconds.

### 2.4 Active plan view

When a client has an ACTIVE plan, the tab shows:

**Plan header bar** (white card, bordered):
- Green dot + "Active Plan" + "Version N" + "Activated [date]"
- Right-aligned ghost button: "New Version"

**ADL TASKS section:**
- Section label "ADL TASKS · N" (uppercase, muted) with a dark primary "**+ Add Task**" button
- One row per task: task name + assistance level badge (see §5 for badge colours) + frequency/instructions as muted sub-text + ✕ delete button
- Deleting a task from an active plan calls `DELETE /care-plans/{planId}/adl-tasks/{taskId}` immediately

**GOALS section** (below tasks):
- Section label "GOALS · N" with a ghost "**+ Add Goal**" button
- One row per goal: description + target date (or "No target date") + ✕ delete button
- Deleting a goal calls `DELETE /care-plans/{planId}/goals/{goalId}` immediately

### 2.5 Add Task sub-panel

Clicking "+ Add Task" on an active plan slides in `AddTaskPanel` over the care plan content.

**Fields:**
| Field | Required | Notes |
|---|---|---|
| Task name | Yes | Combobox — see §3 |
| Assistance level | Yes | Dropdown: Independent / Supervision / Minimal assist / Moderate assist / Maximum assist / Dependent |
| Frequency | No | Free text, e.g. "Daily", "3× per week", "Each visit" |
| Instructions | No | Textarea |

When a template suggestion is selected from the combobox, `defaultAssistanceLevel`, `defaultFrequency`, and `defaultInstructions` pre-fill the corresponding fields. A green hint "✓ Template applied — defaults pre-filled below" confirms the pre-fill. All pre-filled fields remain editable.

Clicking **"Add Task"** calls `POST /care-plans/{planId}/adl-tasks` and closes the panel on success.

**In setup mode:** "+ Add Task" opens the same `AddTaskPanel`. On confirm, the task is added to `pendingTasks` local state only — no API call. The panel closes.

### 2.6 Add Goal inline form

Clicking "+ Add Goal" expands an inline form directly below the goals list (highlighted with a blue border).

**Fields:** Goal description (required, text input) + Target date (optional, date input).

Clicking **"Add Goal"** calls `POST /care-plans/{planId}/goals` on an active plan, or appends to `pendingGoals` local state in setup mode.

### 2.7 New Version flow

Clicking "New Version" on an active plan:
1. Frontend reads the active plan's tasks and goals from React Query cache
2. Enters setup mode with those tasks and goals pre-populated as `pendingTasks` / `pendingGoals`
3. Setup banner reads: "Creating version [N+1] of [Client]'s care plan"
4. Admin adds, removes, or keeps tasks/goals as needed
5. "Save & Activate Plan" fires the same 4-step sequence (§2.3)
6. The `activate` call supersedes the previous ACTIVE plan server-side — no extra frontend logic

---

## 3. ADL Task Template Library

### File location and naming

One JSON file per locale, placed in the existing i18n locales directory:

```
frontend/public/locales/en/adl-task-templates.json
frontend/public/locales/es/adl-task-templates.json   ← future
```

Adding a new language requires only adding the corresponding file. No code changes needed.

### File structure

```json
[
  {
    "name": "Bathing (full body)",
    "defaultAssistanceLevel": "MAXIMUM_ASSIST",
    "defaultFrequency": "Daily",
    "defaultInstructions": "Check water temperature. Use shower chair if needed."
  },
  {
    "name": "Bed bath",
    "defaultAssistanceLevel": "MAXIMUM_ASSIST",
    "defaultFrequency": "Daily",
    "defaultInstructions": ""
  }
]
```

`defaultAssistanceLevel` values must match the backend `AssistanceLevel` enum: `INDEPENDENT`, `SUPERVISION`, `MINIMAL_ASSIST`, `MODERATE_ASSIST`, `MAXIMUM_ASSIST`, `DEPENDENT`.

### Combobox behaviour

- Typing filters the in-memory template list by `name` (case-insensitive substring match)
- Dropdown shows up to 8 matches at a time
- The bottom of the dropdown always includes a "Use '[typed value]' as custom task" escape hatch — the input is never restricted to suggestions
- Selecting a template pre-fills fields and shows the green hint; selecting the escape hatch leaves other fields at their defaults
- No network request — filtered client-side from the React Query cached array

### `useAdlTaskTemplates()` hook

```ts
const lang = i18n.language.split('-')[0]  // 'en-US' → 'en'
// Fetches /locales/${lang}/adl-task-templates.json
// Falls back to /locales/en/adl-task-templates.json on 404
// Cached for the session (staleTime: Infinity)
```

### Initial 100 tasks

The English seed file covers the following categories:

**Basic ADLs (Katz/Barthel domains):** Bathing (full body), Bed bath, Sponge bath, Dressing — upper body, Dressing — lower body, Grooming (face/hair/teeth), Oral care, Shaving assist, Toileting assist, Incontinence care, Catheter care, Transfers (bed to chair), Transfers (chair to toilet), Car transfer, Ambulation assist, Wheelchair mobility, Walker/cane supervision, Range of motion exercises, Feeding assist, Meal setup

**IADLs:** Meal preparation, Light housekeeping, Laundry, Grocery shopping, Medication reminders, Medication administration, Transportation, Errands, Companionship, Reading aloud, Phone/tablet assist

**Safety & monitoring:** Fall prevention, Skin assessment, Wound observation, Vital signs (BP/pulse/O2), Weight monitoring, Blood glucose check, Fluid intake monitoring, Bowel tracking, Urinary output monitoring

**Therapy support:** Physical therapy exercise follow-through, Occupational therapy task follow-through, Speech therapy exercise support, Cognitive stimulation activities, Memory care activities

**Home management:** Trash removal, Bed making, Dish washing, Surface cleaning, Floor sweeping/mopping, Bathroom cleaning, Linen change

**Additional personal care:** Hair washing, Nail care, Foot care, Compression stocking application, Hearing aid assist, Glasses/dentures assist, Positioning (in bed), Pressure relief repositioning

*(Remaining tasks to fill to 100 will cover specialised ADLs for dementia, Parkinson's, post-surgical, and paediatric care contexts.)*

---

## 4. Backend Changes

### 4.1 New endpoint: `GET /api/v1/clients/{id}/care-plans/active`

Returns the client's ACTIVE care plan, or `404` if none exists.

**Controller:**
```java
@GetMapping("/{id}/care-plans/active")
@PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
public ResponseEntity<CarePlanResponse> getActiveCarePlan(@PathVariable UUID id) {
    return ResponseEntity.ok(clientService.getActiveCarePlan(id));
}
```

**Service:**
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

### 4.2 New `CarePlanRepository` query

A non-locking read variant alongside the existing pessimistic-write version used in the activation transaction:

```java
// Read-only — no lock. Used by getActiveCarePlan().
@Query("SELECT p FROM CarePlan p WHERE p.clientId = :clientId AND p.status = :status")
Optional<CarePlan> findActiveByClientId(@Param("clientId") UUID clientId,
                                        @Param("status") CarePlanStatus status);

// Existing — pessimistic write lock. Used only inside activateCarePlan() transaction.
@Lock(LockModeType.PESSIMISTIC_WRITE)
Optional<CarePlan> findByClientIdAndStatus(UUID clientId, CarePlanStatus status);
```

### 4.3 ADL task and goal list endpoints

The existing paginated endpoints are used with `size=100` from the frontend to avoid pagination on lists that will rarely exceed a few dozen items. No new endpoints required.

### 4.4 No schema migrations

All required tables (`care_plans`, `adl_tasks`, `goals`) already exist. No new Flyway migrations.

---

## 5. Frontend Architecture

### New files

| Path | Purpose |
|---|---|
| `public/locales/en/adl-task-templates.json` | 100 ADL/IADL task templates (English) |
| `src/api/carePlans.ts` | API functions: `getActivePlan`, `createCarePlan`, `activateCarePlan`, `listAdlTasks`, `addAdlTask`, `deleteAdlTask`, `listGoals`, `addGoal`, `deleteGoal` |
| `src/hooks/useCarePlan.ts` | `useActivePlan(clientId)`, `useAdlTasks(clientId, planId)`, `useGoals(clientId, planId)`, `useAdlTaskTemplates()` |
| `src/components/clients/CarePlanTab.tsx` | Main tab — owns setup mode state, renders all sub-components |
| `src/components/clients/AddTaskPanel.tsx` | Slide-out sub-panel with combobox |
| `src/components/clients/AddGoalForm.tsx` | Inline goal entry form |

### Modified files

| Path | Change |
|---|---|
| `src/components/clients/ClientDetailPanel.tsx` | Replace `carePlanPhaseNote` placeholder with `<CarePlanTab clientId={clientId} />` |
| `public/locales/en/clients.json` | Add i18n keys for all new UI strings |

### Local state model (`CarePlanTab`)

```ts
interface PendingTask {
  _id: string           // crypto.randomUUID() — client-side only
  name: string
  assistanceLevel: AssistanceLevel
  frequency: string
  instructions: string
  sortOrder: number
}

interface PendingGoal {
  _id: string           // crypto.randomUUID() — client-side only
  description: string
  targetDate: string | null
}

const [setupMode, setSetupMode] = useState(false)
const [pendingTasks, setPendingTasks] = useState<PendingTask[]>([])
const [pendingGoals, setPendingGoals] = useState<PendingGoal[]>([])
const [activating, setActivating] = useState(false)
```

Setup mode is entered via "Set Up Care Plan" (empty state) or "New Version". It is exited on successful activation or "Discard".

### Assistance level badge colours

| Value | Label | Background | Text |
|---|---|---|---|
| `MAXIMUM_ASSIST` or `DEPENDENT` | MAX ASSIST / DEPENDENT | `#747480` (text-secondary) | white |
| `MODERATE_ASSIST` | MODERATE | `#ca8a04` (amber) | white |
| `MINIMAL_ASSIST` | MINIMAL | `#1a9afa` (blue) | white |
| `SUPERVISION` | SUPERVISION | `#eaeaf2` (border) | `#1a1a24` |
| `INDEPENDENT` | INDEPENDENT | `#eaeaf2` (border) | `#1a1a24` |

### i18n keys (additions to `clients.json`)

```json
{
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
  "adlTaskFieldName": "Task Name",
  "adlTaskFieldAssistanceLevel": "Assistance Level",
  "adlTaskFieldFrequency": "Frequency",
  "adlTaskFieldInstructions": "Instructions",
  "adlTaskTemplateApplied": "Template applied — defaults pre-filled below",
  "adlTaskSearchPlaceholder": "Type to search tasks…",
  "adlTaskCustomOption": "Use \"{{value}}\" as custom task",
  "goalsSectionLabel": "GOALS · {{count}}",
  "goalsAddButton": "+ Add Goal",
  "goalFieldDescription": "Goal Description",
  "goalFieldTargetDate": "Target Date",
  "goalNoTargetDate": "No target date",
  "carePlanActivateError": "Failed to activate plan — please try again"
}
```

---

## 6. Testing

### Backend

| Test | Type |
|---|---|
| `GET /care-plans/active` returns ACTIVE plan | `ClientControllerIT` |
| `GET /care-plans/active` returns 404 when no active plan | `ClientControllerIT` |
| `GET /care-plans/active` returns 404 after plan is superseded | `ClientControllerIT` |
| New `findActiveByClientId` query does not acquire write lock | `CarePlanDomainIT` |

### Frontend

| Test | File |
|---|---|
| Empty state renders "Set Up Care Plan" button when no active plan | `CarePlanTab.test.tsx` |
| Setup banner appears and tasks accumulate in local state | `CarePlanTab.test.tsx` |
| "Save & Activate Plan" disabled with zero pending tasks | `CarePlanTab.test.tsx` |
| Activation sequence fires correct API calls in order | `CarePlanTab.test.tsx` |
| Post-activation success message appears and auto-dismisses | `CarePlanTab.test.tsx` |
| Discard returns to empty state without API calls | `CarePlanTab.test.tsx` |
| Combobox filters templates client-side | `AddTaskPanel.test.tsx` |
| Template selection pre-fills assistance level and instructions | `AddTaskPanel.test.tsx` |
| Custom (non-template) task name accepted | `AddTaskPanel.test.tsx` |
| "New Version" enters setup mode pre-populated with active plan tasks | `CarePlanTab.test.tsx` |
| `useAdlTaskTemplates` falls back to `en` when locale file is missing | `useCarePlan.test.ts` |
