# Critical Design Review #4
## Add Client Panel — Design Spec
**Reviewed:** `2026-04-08-add-client-panel-design.md`
**Reviewer pass:** 4 (previous: review-1, review-2, review-3)

---

## 1. Overall Assessment

After four review passes the spec has resolved every critical and minor issue raised across reviews #1–#3. The `showCount` timer fix is in place, toast placement and Authorizations tab landing state are documented, and the `backLabel` key ambiguity is resolved. This pass surfaces one new critical gap — `serviceState` empty-selection serialization is undocumented and has the same class of silent data-quality risk that `preferredCaregiverGender` had in Review #1 — plus two minor housekeeping items. The spec is otherwise implementation-ready.

---

## 2. Critical Issues

### C1 — `serviceState` empty-selection value contract is unspecified

**Description:** `serviceState` is rendered as a `<select>` with an empty default option. The spec documents `preferredCaregiverGender` explicitly: when "No preference" is selected, send `undefined` (not `""`), because the backend guard `if (req.preferredCaregiverGender() != null)` means an empty string would be written to the column. The spec applies no equivalent treatment to `serviceState`.

If `serviceState` has a similar null-guard on the backend (likely, given the pattern established for other nullable string fields), sending `""` when no state is selected would write an empty string to the `service_state` column instead of leaving it `null`. This silently corrupts EVV state config lookups for any client whose state is later updated — the backend would find a row with `service_state = ""` and treat it as set.

**Why it matters:** `serviceState` directly determines which `EvvStateConfig` row is used for EVV compliance evaluation. A stored `""` value (rather than `null`) could cause a silent EVV miscalculation for the client — a compliance defect that may not surface until a state audit. The `preferredCaregiverGender` issue was caught in Review #1 precisely because of this pattern; `serviceState` follows the same pattern and needs the same treatment.

**Actionable fix:** Add a note to the `serviceState` field row in the field notes table: *"When no state is selected, omit the field (send `undefined`) — do not send `""`. Verify the backend guard behavior matches `preferredCaregiverGender`."* Add a test case in `NewClientPanel.test.tsx`: *"empty serviceState selection omits the field from the POST payload."*

---

## 3. Previously Addressed Items (from Reviews #1–#3)

- **R1-C1 through R1-C4** ✅ — All four original critical issues resolved.
- **R2-C1 through R2-C3** ✅ — `openPanel` type, Toast coupling, case normalization all resolved.
- **R3-C1** ✅ — `showCount` added to `toastStore`; `useEffect` dependency updated to `[visible, showCount]`; test case added.
- **R3 minor: `backLabel` i18n** ✅ — Pre-existing key confirmed; reuse note added.
- **R3 minor: toast placement** ✅ — `fixed bottom-4 right-4 z-50` documented.
- **R3 minor: Authorizations tab landing state** ✅ — Empty state with CTA documented; no `ClientDetailPanel` changes required.

---

## 4. Alternative Architectural Challenge

**Alternative: Uncontrolled native `<form>` with `FormData` instead of `react-hook-form`**

Rather than managing field state through a form library, `NewClientPanel` could use a plain HTML `<form>` with `onSubmit`, reading field values via `new FormData(e.target)`. Required field validation would use the native `required` attribute and the browser's built-in constraint validation API.

**Pros:**
- Zero additional library code — no `useForm`, no `Controller`, no `register`. The component becomes a thin shell around a native form.
- Native `required` validation is accessible by default and consistent across browsers — no custom error rendering needed for the three required fields.
- Aligns with the project's YAGNI principle for a 10-field create-only form with minimal validation logic.

**Cons:**
- `react-hook-form` is already a dependency (used elsewhere in the app). Introducing a second form paradigm creates inconsistency — future maintainers will encounter two patterns.
- Native `FormData` returns all values as strings; the checkbox (`noPetCaregiver`) returns `"on"` or is absent, requiring manual coercion. Less ergonomic than `watch('noPetCaregiver')`.
- Custom error messages for required fields (the spec's `validationFirstNameRequired` etc.) require imperative DOM manipulation or a custom validity reporting layer, which negates the simplicity gain.

**Verdict:** `react-hook-form` is the right call given it is already in use. The native form alternative is only compelling for greenfield projects without an existing form library.

---

## 5. Minor Issues & Improvements

- **`toastStore` description in the new-files table is stale:** Line 99 reads `{ message, linkLabel, clientId, visible }` — the actual interface now has `showCount`, `targetId` (not `clientId`), `panelType`, `panelTab`, and `backLabel`. This will confuse the implementer when the table description contradicts the full interface definition below it. Update the Purpose cell to match the current interface summary.

- **`toastStore.test.ts` does not verify `showCount` increments:** The test for `show()` asserts "State reflects new message, targetId, panelType, panelTab, backLabel" but omits `showCount`. Since `showCount` is the key mechanism enabling the timer re-arm fix, it should be explicitly asserted in the store test: *"calling `show()` twice increments `showCount` each time."*

---

## 6. Questions for Clarification

1. **Does the backend `CreateClientRequest` record have a null-guard for `serviceState` matching the one for `preferredCaregiverGender`?** The fix in C1 assumes yes — if the backend unconditionally writes the field regardless of null, the `undefined`-vs-`""` distinction still matters at the DB level (nullable column vs empty string), but the mechanism is different. Verify before implementing.

---

## 7. Final Recommendation

**Approve with changes.**

One issue must be addressed before implementation:

1. **C1** — Document `serviceState` empty-selection serialization: send `undefined`, not `""`. Add a test case covering this. Verify backend null-guard behavior.

Two minor housekeeping items (no implementation risk, but should be cleaned up in the spec):

- Update the `toastStore` new-files table description to match the current interface.
- Add `showCount` increment assertion to `toastStore.test.ts`.

After C1 is addressed, the spec is implementation-ready.
