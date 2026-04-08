# Add Caregiver Panel — Design Spec

**Date:** 2026-04-08
**Status:** Approved
**Scope:** Frontend only — backend `POST /api/v1/caregivers` is already fully implemented.

---

## Problem

The `[+ Add Caregiver]` button on the Caregivers list screen calls `alert(t('addCaregiverAlert'))` — a Phase 7 placeholder. This spec wires it to a real form.

---

## Goals

- Allow ADMIN and SCHEDULER users to create a new caregiver from the Caregivers list screen.
- Capture all 7 fields accepted by `POST /api/v1/caregivers`.
- Guide the user toward the most critical next step (adding credentials) without forcing it.

---

## Non-Goals

- No backend changes — the API is complete.
- No inline credential creation — the credential form already exists in the caregiver detail panel; this spec only navigates there.
- No multi-step wizard — single scrollable panel, two grouped sections.

---

## User Flow

1. User opens the Caregivers list and clicks **+ Add Caregiver**.
2. A `NewCaregiverPanel` slides in from the right (same `SlidePanel` used for shifts and client detail).
3. User fills in the form. First name, last name, and email are required; all other fields are optional.
4. User clicks one of two footer buttons:
   - **Save & Add Credentials** (primary) — creates the caregiver, then opens the caregiver detail panel directly on the Credentials tab.
   - **Save & Close** (secondary) — creates the caregiver, closes the panel, shows a dismissible toast: *"Caregiver saved. Add credentials to enable scheduling."* with a link that opens the caregiver's Credentials tab.
5. On API error, an inline error banner appears above the footer. The panel stays open.

---

## Form Layout

Sections use a small `text-[10px] font-bold uppercase tracking-[0.1em] text-text-secondary` heading, matching existing section divider patterns. Required fields are marked with `*`; a footnote below the panel header reads `* Required field`.

```
Header
  ← Caregivers
  Add Caregiver
  * Required field

── Caregiver Identity ────────────────────
  [First Name *]   [Last Name *]           ← grid-cols-2
  [Email *]                                ← full-width, type="email"

── Employment & Contact ──────────────────
  [Phone]                                  ← type="tel"
  [Address]                                ← type="text", single line
  [Hire Date]                              ← type="date"
  [☐ Has pet at home]                     ← type="checkbox"; feeds AI scoring engine

── Footer (sticky, border-t) ─────────────
  [Save & Add Credentials]  [Save & Close]
```

### Field notes

| Field | Input type | Notes |
|---|---|---|
| firstName | `text` | Required |
| lastName | `text` | Required |
| email | `email` | Required; validated against email format |
| phone | `tel` | Optional |
| address | `text` | Optional; single line is sufficient for intake |
| hireDate | `date` | Optional |
| hasPet | `checkbox` | Defaults unchecked (false); actively used by the AI scoring engine — deducts from preference score when matched with a client who has `noPetCaregiver: true` |

---

## Architecture

### New files

| File | Purpose |
|---|---|
| `src/components/caregivers/NewCaregiverPanel.tsx` | Form component |

### Modified files

| File | Change |
|---|---|
| `src/types/api.ts` | Add `CreateCaregiverRequest` interface |
| `src/api/caregivers.ts` | Add `createCaregiver(req: CreateCaregiverRequest)` |
| `src/hooks/useCaregivers.ts` | Add `useCreateCaregiver()` mutation |
| `src/store/panelStore.ts` | Add `'newCaregiver'` to `PanelType` union; `initialTab?: string` is already added by the Add Client spec as a top-level `PanelState` field and `openPanel` option — no additional change needed |
| `src/components/layout/Shell.tsx` | Register `newCaregiver` in `PanelContent`; pass `initialTab` from `PanelState` as a direct prop to `CaregiverDetailPanel` |
| `src/components/caregivers/CaregiverDetailPanel.tsx` | Accept optional `initialTab?: string` prop; cast to `Tab` with `(initialTab as Tab \| undefined) ?? 'overview'` as the `useState` initial value — mirrors `ClientDetailPanel` |
| `src/components/caregivers/CaregiversPage.tsx` | Replace `alert()` with `openPanel('newCaregiver', undefined, { backLabel: t('backLabel') })` |
| `public/locales/en/caregivers.json` | Add keys for new panel (see i18n section below) |

**Shared infrastructure note:** `src/store/toastStore.ts` and `src/components/common/Toast.tsx` are introduced by the Add Client spec and reused here unchanged. If Add Caregiver ships before Add Client, those two files must be included in this implementation instead.

The toastStore interface (defined in the Add Client spec) is fully generic: `{ targetId, panelType, panelTab, backLabel }`. `Toast.tsx` calls `openPanel(panelType, targetId, { initialTab: panelTab, backLabel })` — no feature-specific constants. Both specs share this interface unchanged.

**Implementation dependencies:** The following shared changes must land in a single prerequisite PR before either Add Client or Add Caregiver is built:
- `toastStore.ts` and `Toast.tsx` (new files)
- `panelStore.ts` — `PanelType` union additions and `initialTab` as top-level `PanelState` field
- `Shell.tsx` — `<Toast />` rendering and `initialTab` forwarding

---

## Data Flow

### API function (`src/api/caregivers.ts`)

```ts
export async function createCaregiver(req: CreateCaregiverRequest): Promise<CaregiverResponse> {
  const response = await apiClient.post<CaregiverResponse>('/caregivers', req)
  return response.data
}
```

### Mutation hook (`src/hooks/useCaregivers.ts`)

```ts
export function useCreateCaregiver() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: createCaregiver,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['caregivers'] })
    },
  })
}
```

Invalidates both `['caregivers', page, size]` and `['caregivers', 'all']` via prefix match.

### Post-save navigation

**Save & Add Credentials:**
```ts
const caregiver = await createMutation.mutateAsync(payload)
closePanel()
openPanel('caregiver', caregiver.id, {
  backLabel: t('backLabel'),
  initialTab: 'credentials',
})
```

`initialTab` is a top-level field on `PanelState` (not inside `PanelPrefill`). `Shell.tsx` passes it as a direct prop to `CaregiverDetailPanel`, which casts it and uses it as the `useState<Tab>` initial value. `closePanel()` resets `initialTab` to `undefined` alongside the other fields.

> **Credentials tab landing state:** A brand-new caregiver has no credentials. The tab renders its standard empty state ("No credentials on file." + "+ Add Credential" button). No additional prop or auto-open behavior is required — `CaregiverDetailPanel` is unchanged beyond accepting `initialTab`.

> **React 18 batching note:** `closePanel()` and `openPanel()` are synchronous Zustand updates. React 18 automatic batching coalesces them into a single render — no blank-panel flash. If a close animation before reopening is ever desired, wrap `openPanel()` in `setTimeout(..., 300)` to allow the CSS transition to complete.

**Save & Close:**
```ts
const caregiver = await createMutation.mutateAsync(payload)
toastStore.show({
  message: t('saveCloseToast'),
  linkLabel: t('saveCloseToastLink'),
  targetId: caregiver.id,
  panelType: 'caregiver',
  panelTab: 'credentials',
  backLabel: t('backLabel'),
})
closePanel()
```

The `Toast` component reads from `toastStore`, displays for 6 seconds, and dismisses on click or timeout. Clicking the link calls `openPanel(panelType, targetId, { initialTab: panelTab, backLabel })` — all navigation params come from the store. `Toast.tsx` imports no i18n namespace and references no feature-specific constants.

---

## i18n Keys (`public/locales/en/caregivers.json` additions)

```json
"addCaregiverPanelTitle": "Add Caregiver",
"addCaregiverRequiredNote": "* Required field",
"sectionIdentity": "Caregiver Identity",
"sectionEmployment": "Employment & Contact",
"fieldFirstName": "First Name",
"fieldLastName": "Last Name",
"fieldEmail": "Email",
"validationFirstNameRequired": "First name is required",
"validationLastNameRequired": "Last name is required",
"validationEmailRequired": "Email is required",
"validationEmailInvalid": "Enter a valid email address",
"saveAndAddCredentials": "Save & Add Credentials",
"saveAndClose": "Save & Close",
"saveCloseToast": "Caregiver saved. Add credentials to enable scheduling.",
"saveCloseToastLink": "Add Credentials"
```

> `"fieldPhone"`, `"fieldAddress"`, `"fieldHireDate"`, and `"fieldHasPet"` already exist in `caregivers.json` and are reused — do not add them again.
> `"backLabel"` also already exists in `caregivers.json` (`"← Caregivers"`) and is reused — do not add it again.

---

## Validation

| Field | Rule | Error message key |
|---|---|---|
| firstName | Required, non-blank | `validationFirstNameRequired` |
| lastName | Required, non-blank | `validationLastNameRequired` |
| email | Required, valid email format | `validationEmailRequired` / `validationEmailInvalid` |

All other fields are optional — no client-side validation beyond the required trio. Server-side validation errors surface via the inline API error banner (`tCommon('errorTryAgain')`).

**Save & Add Credentials** is disabled while the mutation is pending. **Save & Close** is also disabled during pending to prevent double-submit.

---

## Tests (`NewCaregiverPanel.test.tsx`)

| Scenario | Assertion |
|---|---|
| Renders both section headings | Section labels present in DOM |
| Submit with required fields empty | Shows validation errors; no API call |
| Submit with invalid email | Shows email format error; no API call |
| Submit with valid required fields | Calls `createCaregiver` with correct payload |
| API error | Error banner appears; panel stays open |
| Save & Add Credentials path | `openPanel` called with `'caregiver'` + `initialTab: 'credentials'` |
| Save & Close path | `toastStore.show` called; `closePanel` called |

Add a `useCreateCaregiver` mutation test in `useCaregivers.test.ts` confirming query invalidation on success.

**Shared infrastructure tests (`toastStore.test.ts` and `Toast.test.tsx`):** These are defined in the Add Client spec and cover the generic store and component fully. If Add Caregiver ships first, include those test files in this implementation. If Add Client ships first, they are already covered — do not duplicate.

---

## Known Gaps

- **Email uniqueness** — the `caregivers` table has no unique constraint on `email` per agency. Duplicate caregiver profiles with the same email can be created silently. This is a data quality issue today and will become an authentication bug when caregivers log into the mobile app. A backend migration adding a `UNIQUE(agency_id, email)` constraint should be tracked as a separate ticket; it is out of scope for this frontend-only spec.

---

## Out of Scope

- Editing an existing caregiver (separate feature — `PATCH /api/v1/caregivers/:id` already exists).
- Inline credential creation — the credential form already exists in `CaregiverDetailPanel`.
- Availability setup — can be done later via the Availability tab.
- Background check recording — typically a pre-hire step, already handled separately.
