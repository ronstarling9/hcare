# Critical Implementation Review — Phase 6: Wire Clients Screen
**Review #2** | Reviewed against: `2026-04-06-frontend-phase-6-wire-clients.md`
Previous review: `critical-review-1.md` (10 critical issues raised).

---

## 1. Overall Assessment

All ten critical issues from review-1 were resolved in the implementation. The agent correctly read the existing codebase rather than following the plan's stale code blocks literally, producing an implementation that is architecturally correct, type-safe, and consistent with project conventions. `ClientsPage` uses design tokens and i18n, preserves the search bar and add-client button, and does not render the detail panel inline. `ClientDetailPanel` keeps its `{ clientId, backLabel }` prop interface, the 5-tab layout, and no `SlidePanel` wrapper. `useClients` / `useAllClients` split is correct, and all three schedule-screen consumers (including `NewShiftPanel`, which review-1 flagged as missing) were updated. TypeScript builds clean. No new critical issues found.

---

## 2. Critical Issues

None.

---

## 3. Previously Addressed Items

All ten critical issues from review-1 are resolved:

- **CI-1** — `'clientDetail'` panel type: `ClientsPage` does not call `openPanel` at all; `ClientsTable` continues to call `openPanel('client', id)` internally.
- **CI-2** — Inline `<ClientDetailPanel>` in `ClientsPage`: removed entirely; Shell's `PanelContent` remains the sole render site.
- **CI-3** — `ClientDetailPanel` prop interface: `{ clientId: string, backLabel: string }` preserved; Shell passes `backLabel` unchanged.
- **CI-4** — `onClientClick` prop on `ClientsTable`: not present; `ClientsTable` interface (`{ clients, search }`) unchanged.
- **CI-5** — `NewShiftPanel.tsx` missing from Step 2.2: updated to `useAllClients` alongside `SchedulePage` and `ShiftDetailPanel`.
- **CI-6** — i18n regression: `useTranslation('clients')` and `useTranslation('common')` retained; all new strings (`loading`, `error`, `prev`, `next`, `pageOf`) added to `clients.json`.
- **CI-7** — Design token regression: `ClientsPage` uses `bg-surface`, `bg-white`, `border-border`, `text-dark`, `text-text-muted`, `text-text-secondary` throughout.
- **CI-8** — `unitType === 'HOURS'` mismatch: authorizations tab uses `auth.unitType.toLowerCase()` interpolated into the `authUnitsUsed` i18n key, correctly producing `'hour'`, `'visit'`, or `'day'` suffixed with `'s'` via the template string.
- **CI-9** — Nested `SlidePanel`: `ClientDetailPanel` renders its content directly with no `SlidePanel` wrapper.
- **CI-10** — Tab structure loss: all 5 tabs (overview, carePlan, authorizations, documents, familyPortal) preserved with i18n labels and stub content for unimplemented tabs.

---

## 4. Minor Issues & Improvements

- **`isError && !client` renders `t('notFound')` instead of an error string:** `ClientDetailPanel.tsx` lines 52–61 display `t('notFound')` ("Client not found.") when the query errors. This is semantically incorrect — a 500 or network timeout is not "not found". Consider adding a `t('loadError')` key (e.g. "Failed to load client.") and using it for the `isError` branch. The existing `!client` fallback (lines 63–71) is the true "not found" case.

- **Duplicate `!client` guard:** After the `isError && !client` block (lines 52–61) there is a second bare `!client` block (lines 63–71) that renders the same UI. These could be collapsed into a single `if (!client)` guard after the loading check.

- **Stale phase notes in `clients.json`:** Three keys still reference "Phase 6 wires to real API" (`carePlanPhaseNote`, `documentsPhaseNote`, `familyPortalPhaseNote`). Now that Phase 6 is complete, these tabs remain stubs but the copy implies they're about to be wired. Consider updating to "Coming soon" or a phase-neutral message.

- **`useClients` does not expose `clientMap`:** The paginated hook drops `clientMap` (only `useAllClients` has it). This is correct — the paginated consumer (`ClientsPage`) doesn't need it — but any future consumer that destructures `clientMap` from `useClients()` will get `undefined` silently. A JSDoc note on `useClients` clarifying "use `useAllClients` if you need a lookup map" would prevent this.

- **No unit tests added:** `api/clients.ts` (expanded), `hooks/useClients.ts` (two new exports), `ClientsPage.tsx`, and `ClientDetailPanel.tsx` have no accompanying test files. CLAUDE.md targets 80% coverage.

---

## 5. Questions for Clarification

1. **`GET /clients/{id}/authorizations` backend endpoint:** Does the backend `AuthorizationController` support pagination params (`page`, `size`)? `listAuthorizations` sends `{ page: 0, size: 50 }`. If the endpoint ignores these or returns all records by default, the params are harmless but superfluous. Verify during Checkpoint 6.

2. **`authUnitsUsed` i18n format:** The current key `"{{used}}/{{authorized}} {{unitType}}s used"` passes raw numeric values for `used` and `authorized`. If these are fractional hours (e.g. `1.5`), the output will be `"1.5/40 hours used"` with no locale-aware number formatting. Is this acceptable for the current phase?

---

## 6. Final Recommendation

**Approve as-is.** All ten critical issues from review-1 are resolved. The implementation is correct, type-safe, and consistent with the project's architecture and conventions. Proceed to Manual Test Checkpoint 6. The five minor items are good candidates for a follow-up cleanup pass.
