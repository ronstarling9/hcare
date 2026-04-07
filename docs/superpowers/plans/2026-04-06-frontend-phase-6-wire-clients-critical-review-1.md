# Critical Implementation Review — Phase 6: Wire Clients Screen
**Review #1** | Reviewed against: `2026-04-06-frontend-phase-6-wire-clients.md`
No previous reviews exist.

---

## 1. Overall Assessment

This is the first review pass. The plan was drafted without reading the current codebase state and contains ten critical issues — the most severe being a fundamental architectural conflict where the plan has `ClientsPage` manage and render the detail panel inline, which directly contradicts the established `Shell.tsx` / `PanelContent` architecture that already handles all panel routing globally. Additional critical issues include wrong panel type keys, wrong prop signatures that break `Shell.tsx`, a missing consumer update (`NewShiftPanel.tsx`), full i18n regression, design token regression, a nested `SlidePanel` double-wrap, loss of the tab UI from Phase 1, and a `unitType` enum mismatch that will produce TypeScript errors and broken display logic. **Major revisions needed before implementation.**

---

## 2. Critical Issues

### CI-1 — `ClientsPage` uses `'clientDetail'` panel type which does not exist in `panelStore`

**Description:** Step 3.1 replaces `ClientsPage` with code that calls `panel.openPanel('clientDetail', id)`. The actual `PanelType` union in `frontend/src/store/panelStore.ts` (line 3–9) is `'shift' | 'newShift' | 'client' | 'caregiver' | 'payer' | null`. There is no `'clientDetail'` value. The plan also references `panel.id` — but the store field is `selectedId`, not `id`.

**Why it matters:** This is a TypeScript compile error. `openPanel('clientDetail', id)` will fail to type-check against `Exclude<PanelType, null>`, and `panel.id` is `undefined` at runtime. The panel will never open.

**Fix:** Use `openPanel('client', id)` (matching the existing panel type). Remove the inline `<ClientDetailPanel>` render from `ClientsPage` entirely — the Shell already handles it (see CI-2).

---

### CI-2 — `ClientsPage` renders `ClientDetailPanel` inline, contradicting the Shell.tsx PanelContent architecture

**Description:** Step 3.1's replacement `ClientsPage` renders `{panel.type === 'clientDetail' && panel.id && <ClientDetailPanel ... />}` directly inside the page. But `Shell.tsx` (lines 11–34) already contains a `PanelContent` function that renders `<ClientDetailPanel clientId={selectedId} backLabel={backLabel} />` when `type === 'client'`. This is the established architecture for all panels (shift, newShift, client, caregiver, payer) — they all go through Shell, not through individual pages.

**Why it matters:** If the plan's `ClientsPage` also renders a `ClientDetailPanel`, opening a client panel will trigger both the Shell's overlay and the page-level render simultaneously. Even if it doesn't double-render due to the wrong type key (CI-1), adding page-level panel rendering is an architectural regression that every future phase would have to undo.

**Fix:** Remove the inline `<ClientDetailPanel>` from `ClientsPage` entirely. The Shell already handles it. `ClientsPage` only needs to call `openPanel('client', id)`.

---

### CI-3 — `ClientDetailPanel` prop change (`onClose`) breaks `Shell.tsx` line 21

**Description:** Step 4.1 replaces `ClientDetailPanel` with a new component that accepts `{ clientId: string, onClose: () => void }`. The current prop interface is `{ clientId: string, backLabel: string }`. `Shell.tsx` at line 21 calls it as `<ClientDetailPanel clientId={selectedId} backLabel={backLabel} />`. After Step 4.1, `Shell.tsx` passes `backLabel` (which won't match `onClose`) and omits `onClose` — TypeScript will error, and the panel's close button will have no handler.

**Why it matters:** This breaks the entire client panel across the app, including when opened from the dashboard's `AlertsColumn` (`openPanel('client', alert.resourceId)` at `AlertsColumn.tsx:42`).

**Fix:** Keep the existing `{ clientId: string, backLabel: string }` prop interface — or update `Shell.tsx` to pass `onClose={closePanel}` if the prop name is changed. The back button should call `closePanel` from `usePanelStore`, as the existing code already does.

---

### CI-4 — `ClientsTable` does not accept `onClientClick` prop

**Description:** Step 3.1 passes `onClientClick={(id) => panel.openPanel('clientDetail', id)}` to `<ClientsTable>`. The actual `ClientsTable` interface (line 5–8) is `{ clients: ClientResponse[], search: string }` — there is no `onClientClick` prop. `ClientsTable` already handles its own click internally via `openPanel('client', client.id, { backLabel: t('backLabel') })` at line 47.

**Why it matters:** TypeScript compile error. The prop is silently ignored.

**Fix:** Remove `onClientClick` from the plan's `ClientsPage`. `ClientsTable` already opens the panel correctly. The plan also removes the `search` prop — if the search input is preserved in `ClientsPage`, it must still be passed to `ClientsTable`.

---

### CI-5 — `NewShiftPanel.tsx` missing from Step 2.2's consumer update list

**Description:** Step 2.1 changes `useClients` to accept `(page = 0, size = 20)` and changes its query key from `['clients', 'all']` to `['clients', page, size]`. Step 2.2 updates `SchedulePage.tsx` and `ShiftDetailPanel.tsx` to use `useAllClients()` instead. But `NewShiftPanel.tsx` also imports and calls `useClients()` (line 3 and 26 — confirmed by grep). After Step 2.1, `NewShiftPanel`'s `useClients()` call will fetch only 20 clients (page 0, size 20) with a paginated query key, meaning the caregiver dropdown in new-shift creation will only show the first 20 clients.

**Why it matters:** Silent data truncation in a booking flow. No TypeScript error, no runtime crash — just a silently incomplete client list in the new-shift form.

**Fix:** Add `NewShiftPanel.tsx` to the Step 2.2 update: change `import { useClients }` to `import { useAllClients }` and update the call to `const { clients } = useAllClients()`.

---

### CI-6 — i18n regression: Step 3.1 removes all translations from `ClientsPage`

**Description:** The existing `ClientsPage.tsx` uses `useTranslation('clients')` for the page title, search placeholder, add-client button text, and `useTranslation('common')` for `searchByName`. The plan's replacement hardcodes all strings in English: `"Clients"`, `"Loading clients…"`, `"Failed to load clients."`, `"Prev"`, `"Next"`, `"Page X of Y"`. The search bar and add-client button are also dropped entirely.

**Why it matters:** Violates the project's i18n requirement (CLAUDE.md). Also removes functional UI elements (search, add-client) that were present in Phase 1.

**Fix:** Keep `useTranslation('clients')` and use existing keys. Add new keys to `clients.json` for loading, error, pagination, and any new UI copy. Preserve the search input (pass `search` state to `ClientsTable`). The add-client button can remain as-is (stub alert) if client creation is out of scope for this phase.

---

### CI-7 — Design token regression: Step 3.1 and Step 4.1 use raw hex values

**Description:** Both replacement components use raw hex values (`#f6f6fa`, `#ffffff`, `#eaeaf2`, `#1a1a24`, `#94a3b8`, `#747480`, `#dc2626`, `#ca8a04`, `#16a34a`) instead of the Tailwind design tokens mandated by CLAUDE.md (`bg-surface`, `bg-white`, `border-border`, `text-dark`, `text-text-muted`, `text-text-secondary`, etc.).

**Why it matters:** Inconsistency with every other component in the project. Identical issue to CI-5 and CI-7 from Phase 5 reviews.

**Fix:** Replace all raw hex `style={{...}}` color values with the equivalent Tailwind design token classes. The existing `ClientsPage.tsx` and `ClientDetailPanel.tsx` demonstrate the correct pattern.

---

### CI-8 — `AuthorizationResponse.unitType` enum mismatch causes TypeScript error and broken display

**Description:** The existing `AuthorizationResponse` in `types/api.ts` (line 146) declares `unitType: 'HOUR' | 'VISIT' | 'DAY'`. The plan's Step 1.2 proposes `unitType: 'HOURS' | 'VISITS'` — but since a type already exists, Step 1.2 says "add if missing" and will be skipped. However, the `UtilizationBar` component in Step 4.1 (line 372 of the plan) checks `auth.unitType === 'HOURS'` — which will never match `'HOUR'` from the actual type. TypeScript will also flag the string literal comparison as always-false.

**Why it matters:** The hours/visits label in the utilization bar will always display `visits` regardless of actual unit type. TypeScript strict mode will warn about an impossible comparison.

**Fix:** Change `UtilizationBar`'s check to `auth.unitType === 'HOUR'` to match the existing enum. The display label should map `'HOUR' → 'hrs'`, `'VISIT' → 'visits'`, `'DAY' → 'days'`.

---

### CI-9 — `ClientDetailPanel` wraps itself in `<SlidePanel>` but is already rendered inside Shell's `<SlidePanel>`

**Description:** Step 4.1 replaces `ClientDetailPanel` to render `<SlidePanel title="..." onClose={onClose}>` as its root. But `Shell.tsx` (line 47) already renders `<SlidePanel isOpen={open} onClose={closePanel}>` and places `<PanelContent />` inside it — which renders `<ClientDetailPanel>`. The result is a `SlidePanel` nested inside a `SlidePanel`.

**Why it matters:** Double overlay: two slide panels animate simultaneously, producing stacked backdrop divs, broken z-index layering, and two close buttons.

**Fix:** Remove the `<SlidePanel>` wrapper from `ClientDetailPanel`. The component renders its content directly — it does not manage its own panel chrome. The Shell's `SlidePanel` provides the drawer frame. Look at `ShiftDetailPanel.tsx` as the correct reference — it also renders content directly without wrapping in `SlidePanel`.

---

### CI-10 — Tab structure from Phase 1 is silently dropped

**Description:** The existing `ClientDetailPanel.tsx` has a 5-tab layout (`overview`, `carePlan`, `authorizations`, `documents`, `familyPortal`) with i18n tab labels, localized date rendering via `formatLocalDate`, and stub content for the not-yet-implemented tabs (carePlan, documents, familyPortal). Step 4.1 replaces this with a flat single-scroll layout showing only Demographics and Authorizations. The tabs, their i18n keys, `formatLocalDate`, and the stub content for future phases are all lost.

**Why it matters:** Regression from the delivered Phase 1 UI. The tab structure is intentional UX scaffolding for Phase 7/8 content (documents, family portal). Removing it means future phases must re-add it.

**Fix:** Preserve the existing tab structure. Wire the `authorizations` tab to real data using `useClientAuthorizations`. Leave `carePlan`, `documents`, and `familyPortal` tabs with their existing stub content. The overview tab should use real `ClientResponse` fields (already the case in the existing code).

---

## 3. Previously Addressed Items

None — this is the first review.

---

## 4. Minor Issues & Improvements

- **No unit tests:** Three files (`api/clients.ts` expansion, `hooks/useClients.ts`, `ClientsPage.tsx`) have no accompanying tests. CLAUDE.md targets 80% frontend coverage. `useClientAuthorizations` and the pagination logic in `ClientsPage` are high-value test targets.

- **Pagination resets on filter change:** The plan adds `page` state to `ClientsPage` but the search input (if preserved) filters client-side. When a user types in the search box and then clicks "Next", they will paginate away from filtered results. Consider resetting `page` to 0 on search input change, or moving search to a server-side query param.

- **`listCarePlans`, `listDiagnoses`, `listMedications`, `listFamilyPortalUsers` typed as `PageResponse<unknown>`:** These four endpoints in Step 1.1 are typed as `unknown`. Since these endpoints exist in the backend, their response types should be defined in `types/api.ts` now so they can be correctly consumed in future phases without a retroactive type-fix pass.

- **`useAllClients` query key `['clients', 'all']` collides with existing `useClients`'s key:** The current `useClients` (before the plan's changes) already uses `['clients', 'all']`. After Step 2.1, `useClients(page, size)` uses `['clients', page, size]`, and the new `useAllClients` re-uses `['clients', 'all']`. This is fine, but if any consumer still caches the old `useClients()` call under `['clients', 'all']`, the first render after the hook change may serve stale paginated data. A cache invalidation in `useAllClients` is not needed but worth noting.

---

## 5. Questions for Clarification

1. **Search scope:** Should the client search filter client-side (against the current page only) or be a server-side query parameter added to `GET /clients?search=...`? The backend `GET /api/v1/clients` endpoint should be checked for a `search` or `name` query param.

2. **`AuthorizationResponse` field `agencyId`:** The plan proposes adding `agencyId` to `AuthorizationResponse` (Step 1.2), but the existing type (line 138–151) does not have it. Is this field returned by the backend? If so, it should be added to the existing type, not skipped.

3. **Add Client button:** Should the "Add Client" button in the header be kept as a stub (`alert(...)`) for now, or removed entirely? The plan's replacement drops it without comment.

---

## 6. Final Recommendation

**Major revisions needed.** Ten critical issues were found — the most severe (CI-1 through CI-4) are architectural mismatches that will produce TypeScript errors and broken runtime behavior on the first run. CI-5 is a silent regression affecting the schedule screen. CI-6 and CI-7 are the same i18n and design token regressions caught in Phase 5 reviews. CI-8 through CI-10 are additional component-level flaws. All ten must be resolved before implementation begins.

The implementation agent must read the following files before making any changes:
- `frontend/src/store/panelStore.ts` — for correct panel type keys and field names
- `frontend/src/components/layout/Shell.tsx` — for the PanelContent architecture
- `frontend/src/components/clients/ClientsTable.tsx` — for its actual prop interface
- `frontend/src/components/clients/ClientDetailPanel.tsx` — for the tab structure to preserve
- `frontend/src/components/schedule/NewShiftPanel.tsx` — to update its `useClients` import
- `frontend/src/types/api.ts` — for the existing `AuthorizationResponse.unitType` values
