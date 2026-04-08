# Add Client Panel — Design Spec

**Date:** 2026-04-08
**Status:** Approved
**Scope:** Frontend only — backend `POST /api/v1/clients` is already fully implemented.

---

## Problem

The `[+ Add Client]` button on the Clients list screen calls `alert(t('addClientAlert'))` — a Phase 6 placeholder. This spec wires it to a real form.

---

## Goals

- Allow ADMIN and SCHEDULER users to create a new client from the Clients list screen.
- Capture all 10 fields accepted by `POST /api/v1/clients`.
- Guide the user toward the most critical next step (adding an authorization) without forcing it.

---

## Non-Goals

- No backend changes — the API is complete.
- No inline authorization creation — the authorization form already exists in the client detail panel; this spec only navigates there.
- No multi-step wizard — single scrollable panel, four grouped sections.

---

## User Flow

1. User opens the Clients list and clicks **+ Add Client**.
2. A `NewClientPanel` slides in from the right (same `SlidePanel` used for shifts and client detail).
3. User fills in the form. First name, last name, and date of birth are required; all other fields are optional.
4. User clicks one of two footer buttons:
   - **Save & Add Authorization** (primary) — creates the client, then opens the client detail panel directly on the Authorizations tab.
   - **Save & Close** (secondary) — creates the client, closes the panel, shows a dismissible toast: *"Client saved. Add an authorization to enable scheduling."* with a link that opens the client's Authorizations tab.
5. On API error, an inline error banner appears above the footer. The panel stays open.

---

## Form Layout

Sections use a small `text-[10px] font-bold uppercase tracking-[0.1em] text-text-secondary` heading, matching existing section divider patterns. Required fields are marked with `*`; a footnote below the panel header reads `* Required field`.

```
Header
  ← Clients
  Add Client
  * Required field

── Client Identity ──────────────────
  [First Name *]   [Last Name *]      ← grid-cols-2
  [Date of Birth *]                   ← <input type="date">

── Contact & Location ───────────────
  [Phone]                             ← <input type="tel">
  [Address]                           ← <input type="text">
  [Service State]                     ← <select> 50 US state abbreviations

── Billing ──────────────────────────
  [Medicaid ID]                       ← <input type="text" autocomplete="off">

── Care Preferences ─────────────────
  [Preferred Caregiver Gender]        ← <select>: Female / Male / No preference
  [Preferred Languages]               ← <input type="text"> comma-separated
                                         e.g. "English, Spanish"
  [☐ No pet caregiver]               ← <input type="checkbox">

── Footer (sticky, border-t) ────────
  [Save & Add Authorization]  [Save & Close]
```

### Field notes

| Field | Input type | Notes |
|---|---|---|
| firstName | `text` | Required |
| lastName | `text` | Required |
| dateOfBirth | `date` | Required; `<input type="date">` returns `"YYYY-MM-DD"` — Jackson deserializes this directly to `LocalDate`, no transformation needed |
| phone | `tel` | Optional |
| address | `text` | Optional; single line is sufficient for intake |
| serviceState | `select` | 50 US state abbreviations + empty default |
| medicaidId | `text` | Optional; `autocomplete="off"` — PHI-adjacent |
| preferredCaregiverGender | `select` | Canonical values: `"FEMALE"`, `"MALE"`. When "No preference" is selected, omit the field (send `undefined`) — do not send `""`. The backend guard `if (req.preferredCaregiverGender() != null)` means `""` would be written to the column; `null`/omitted means the field is left as stored. |
| preferredLanguages | `text` | Comma-separated; split on `,` and trimmed before POST |
| noPetCaregiver | `checkbox` | Defaults unchecked (false) |

---

## Architecture

### New files

| File | Purpose |
|---|---|
| `src/components/clients/NewClientPanel.tsx` | Form component |
| `src/store/toastStore.ts` | Zustand slice: `{ message, linkLabel, clientId, visible }` |
| `src/components/common/Toast.tsx` | Dismissible banner; rendered in `Shell.tsx` |

### Modified files

| File | Change |
|---|---|
| `src/types/api.ts` | Add `CreateClientRequest` interface |
| `src/api/clients.ts` | Add `createClient(req: CreateClientRequest)` |
| `src/hooks/useClients.ts` | Add `useCreateClient()` mutation |
| `src/store/panelStore.ts` | Add `'newClient'` to `PanelType` union; add `initialTab?: string` as top-level `PanelState` field (not inside `PanelPrefill`); add `initialTab?: string` to `openPanel`'s `options` parameter; `closePanel` resets `initialTab` to `undefined` |
| `src/components/layout/Shell.tsx` | Register `newClient` in `PanelContent`; render `<Toast />`; pass `initialTab` from `PanelState` as direct prop to `ClientDetailPanel` |
| `src/components/clients/ClientDetailPanel.tsx` | Accept optional `initialTab?: string` prop; cast to `Tab` with `(initialTab as Tab | undefined) ?? 'overview'` as the `useState` initial value |
| `src/components/clients/ClientsPage.tsx` | Replace `alert()` with `openPanel('newClient', undefined, { backLabel: t('backLabel') })` |
| `public/locales/en/clients.json` | Add keys for new panel (see i18n section below) |

---

## Data Flow

### API function (`src/api/clients.ts`)

```ts
export async function createClient(req: CreateClientRequest): Promise<ClientResponse> {
  const response = await apiClient.post<ClientResponse>('/clients', req)
  return response.data
}
```

### Mutation hook (`src/hooks/useClients.ts`)

```ts
export function useCreateClient() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: createClient,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['clients'] })
    },
  })
}
```

Invalidates both `['clients', page, size]` and `['clients', 'all']` via prefix match.

### `preferredLanguages` serialization

The panel holds `preferredLanguages` as a plain comma-separated string (e.g. `"English, Spanish"`). Before calling `createClient`, split, trim, and **JSON-stringify** into the format the backend stores:

```ts
preferredLanguages: values.preferredLanguages
  ? JSON.stringify(
      values.preferredLanguages.split(',').map((s) => s.trim()).filter(Boolean)
    )
  : undefined
```

The backend `CreateClientRequest.preferredLanguages` is a raw `String` that gets written directly to the `client.preferred_languages` column (default `"[]"`). `ClientResponse.from()` deserializes it back to `List<String>` using Jackson. The frontend must serialize to a JSON array string (e.g. `'["English","Spanish"]'`) — the backend does no comma-to-JSON conversion.

**Case normalization:** No frontend normalization needed. `LocalScoringService.parseLanguageList()` calls `map(String::toLowerCase)` on both the client's and caregiver's language lists before comparison (`LocalScoringService.java:380`). Stored case has no effect on matching — store tokens as-entered.

### Post-save navigation

**Save & Add Authorization:**
```ts
const client = await createMutation.mutateAsync(payload)
closePanel()
openPanel('client', client.id, {
  backLabel: t('backLabel'),
  initialTab: 'authorizations',
})
```

`initialTab` is a top-level field on `PanelState` (not inside `PanelPrefill`). `Shell.tsx` passes it as a direct prop to `ClientDetailPanel`, which casts it and uses it as the `useState<Tab>` initial value.

Updated `openPanel` options type in `panelStore.ts`:
```ts
options?: { prefill?: PanelPrefill; backLabel?: string; initialTab?: string }
```
`closePanel` resets `initialTab` to `undefined` alongside the other fields.

> **React 18 batching note:** `closePanel()` and `openPanel()` are synchronous Zustand updates. React 18 automatic batching will coalesce them into a single render, so the `SlidePanel` never fully closes before reopening — it swaps content while remaining open (no blank-panel flash). This is intentional. If a close animation before reopening is ever desired, wrap `openPanel()` in `setTimeout(..., 300)` to allow the CSS transition to complete.

**Save & Close:**
```ts
const client = await createMutation.mutateAsync(payload)
toastStore.show({
  message: t('saveCloseToast'),
  linkLabel: t('saveCloseToastLink'),
  targetId: client.id,
  panelType: 'client',
  panelTab: 'authorizations',
  backLabel: t('backLabel'),
})
closePanel()
```

The `Toast` component reads from `toastStore`, displays for 6 seconds, and dismisses on click or timeout. Clicking the link calls `openPanel(panelType, targetId, { initialTab: panelTab, backLabel })` — all navigation params come from the store. `Toast.tsx` imports no i18n namespace and references no feature-specific constants.

**Timer lifecycle:** The `setTimeout` (6 000 ms) lives in `Toast.tsx`'s `useEffect`, with `visible` as the dependency. The effect's cleanup function calls `clearTimeout` — this prevents a stale timer from dismissing a subsequent toast when the user manually dismisses the first one early and triggers a second save quickly afterward.

---

## Toast Store (`src/store/toastStore.ts`)

```ts
interface ToastState {
  visible: boolean
  message: string
  linkLabel: string
  targetId: string | null   // ID passed to openPanel as selectedId
  panelType: string         // PanelType value, e.g. 'client'
  panelTab: string          // initialTab value, e.g. 'authorizations'
  backLabel: string         // e.g. '← Clients'
  show: (opts: {
    message: string
    linkLabel: string
    targetId: string
    panelType: string
    panelTab: string
    backLabel: string
  }) => void
  dismiss: () => void
}
```

Initial state: `{ visible: false, message: '', linkLabel: '', targetId: null, panelType: '', panelTab: '', backLabel: '' }`.

Minimal — no queue, no stacking. One toast at a time is sufficient for this use case. `Toast.tsx` is fully generic: it reads all display and navigation params from the store and has no feature-specific imports.

---

## i18n Keys (`public/locales/en/clients.json` additions)

```json
"addClientPanelTitle": "Add Client",
"addClientRequiredNote": "* Required field",
"sectionIdentity": "Client Identity",
"sectionContact": "Contact & Location",
"sectionBilling": "Billing",
"sectionPreferences": "Care Preferences",
"fieldFirstName": "First Name",
"fieldLastName": "Last Name",
"fieldDateOfBirth": "Date of Birth",
"fieldAddress": "Address",
"fieldServiceState": "Service State",
"fieldSelectState": "Select state…",
"fieldMedicaidId": "Medicaid ID",
"fieldPreferredGender": "Preferred Caregiver Gender",
"fieldGenderFemale": "Female",
"fieldGenderMale": "Male",
"fieldGenderNoPreference": "No preference",
"fieldPreferredLanguages": "Preferred Languages",
"fieldPreferredLanguagesHint": "Comma-separated, e.g. English, Spanish",
"fieldNoPetCaregiver": "No pet caregiver",
"validationFirstNameRequired": "First name is required",
"validationLastNameRequired": "Last name is required",
"validationDobRequired": "Date of birth is required",
"saveAndAddAuth": "Save & Add Authorization",
"saveAndClose": "Save & Close",
"saveCloseToast": "Client saved. Add an authorization to enable scheduling.",
"saveCloseToastLink": "Add Authorization"
```

> `"fieldPhone"` already exists in `clients.json` and is reused — do not add it again.

---

## Validation

| Field | Rule | Error message key |
|---|---|---|
| firstName | Required, non-blank | `validationFirstNameRequired` |
| lastName | Required, non-blank | `validationLastNameRequired` |
| dateOfBirth | Required | `validationDobRequired` |

All other fields are optional — no client-side validation beyond the required trio. Server-side validation errors surface via the inline API error banner (`tCommon('errorTryAgain')`).

**Save & Add Authorization** is disabled while the mutation is pending. **Save & Close** is also disabled during pending to prevent double-submit.

---

## Tests (`NewClientPanel.test.tsx`)

| Scenario | Assertion |
|---|---|
| Renders all four section headings | Section labels present in DOM |
| Submit with required fields empty | Shows validation errors; no API call |
| Submit with valid required fields | Calls `createClient` with correct payload |
| `preferredLanguages` parsing | `"English, Spanish"` → split correctly |
| API error | Error banner appears; panel stays open |
| Save & Add Authorization path | `openPanel` called with `'client'` + `initialTab: 'authorizations'` |
| Save & Close path | `toastStore.show` called; `closePanel` called |

Add a `useCreateClient` mutation test in `useClients.test.ts` confirming query invalidation on success.

**`toastStore.test.ts`:**

| Scenario | Assertion |
|---|---|
| `show()` sets `visible: true` | State reflects new message, targetId, panelType, panelTab, backLabel |
| `dismiss()` sets `visible: false` | State resets to initial values |

**`Toast.test.tsx`:**

| Scenario | Assertion |
|---|---|
| Renders when `visible: true` | Message and link text in DOM |
| Hidden when `visible: false` | Component not rendered |
| Link click calls `openPanel` with correct args | `initialTab: 'authorizations'` passed |
| Manual dismiss before 6 s clears timer | No second `dismiss()` call after timeout |

---

## Out of Scope

- Editing an existing client (separate feature — `PATCH /api/v1/clients/:id` already exists).
- Toast queue / stacking.
- Address field decomposition (street / city / zip) — single text line is sufficient for intake.
- Authorization form itself — already implemented in `ClientDetailPanel`.
