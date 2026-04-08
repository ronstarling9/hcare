# Critical Design Review #1
## Add Client Panel — Design Spec
**Reviewed:** `2026-04-08-add-client-panel-design.md`
**Reviewer pass:** 1 of 1 (no prior reviews)

---

## 1. Overall Assessment

The spec is well-scoped, follows existing codebase conventions (SlidePanel, panelStore, react-hook-form, react-query mutations), and appropriately defers out-of-scope concerns. The self-review caught the most dangerous bug (`preferredLanguages` JSON serialization). Four issues remain that need resolution before implementation begins: a semantic misuse of `PanelPrefill`, an unspecified toast timer lifecycle, a duplicate i18n key, and an ambiguous `preferredCaregiverGender` value contract with the backend.

---

## 2. Critical Issues

### C1 — `initialTab` via `PanelPrefill` is a semantic category error

**Description:** The spec proposes adding `initialTab?: string` to `PanelPrefill` and reading it in `ClientDetailPanel` via `prefill.initialTab`. `PanelPrefill` was designed for form pre-population (dates, times, IDs to pre-select). `initialTab` is navigation state, not pre-fill data — it doesn't belong in this interface.

More concretely: `ClientDetailPanel` currently receives `clientId` and `backLabel` as direct props. The spec adds a third concern by having the panel dig into `prefill` (a `panelStore` concern) for a routing decision. This creates an indirect dependency between a display component and a global store structure that will break silently if `PanelPrefill` is ever restructured.

**Impact:** Tight coupling between `ClientDetailPanel` and `panelStore` internals; type safety erodes as `PanelPrefill` becomes a grab-bag.

**Suggestion:** Add `initialTab?: string` as a **direct prop** on `ClientDetailPanel` alongside `clientId` and `backLabel`. Update `PanelState` with a top-level `initialTab?: string` field (parallel to `selectedId`) and propagate it from `Shell.tsx` as a prop. This keeps `PanelPrefill` for form pre-population only.

---

### C2 — Toast auto-dismiss timer lifecycle is unspecified

**Description:** The spec says the toast "auto-dismisses after 6 seconds" but the `toastStore` interface only exposes `show()` and `dismiss()`. There is no `timeoutId` in the store, no mention of where `setTimeout` is called, and no cleanup strategy.

If the timer lives in `Toast.tsx`'s `useEffect` (the most natural placement), it must be cleared when `dismiss()` is called manually before 6 seconds to prevent a stale callback from re-hiding an already-dismissed toast or (worse) dismissing a subsequent toast shown quickly after. The spec is silent on this.

**Impact:** Without explicit cleanup, rapid sequences of save → open → save again can cause the first timer to dismiss the second toast. The new Zustand store adds infrastructure complexity without fully specifying the lifecycle of the one non-trivial behavior it enables.

**Suggestion:** Specify in the spec: the `setTimeout` lives in `Toast.tsx`'s `useEffect` with the `visible` flag as dependency; the cleanup function calls `clearTimeout`. Add a test case: "manually dismissed toast does not re-dismiss after 6 seconds."

---

### C3 — `preferredCaregiverGender` value contract is undefined

**Description:** The spec proposes dropdown options `""` (no preference), `FEMALE`, and `MALE`. The backend column is `VARCHAR(10)` and the field is a raw `String` with no enum constraint. The scoring engine explicitly defers gender preference to P2 (`LocalScoringService.java:298`), so this field has no runtime effect today.

The issue is that `""` (empty string) and `null`/omitted are semantically different at the DB layer. When the spec sends `""`, the backend service guard `if (req.preferredCaregiverGender() != null)` evaluates to `true` (empty string ≠ null), so `""` gets written to the column. `null` means "unchanged." This creates three undocumented states: `null` (never set), `""` (explicitly set to no preference), `"FEMALE"`/`"MALE"`. Future P2 gender matching logic will need to handle all three.

**Impact:** Silent data quality issue. `FEMALE` (6 chars) and `MALE` (4 chars) fit within `length = 10`; but the spec should document the canonical values explicitly so the P2 implementation doesn't have to reverse-engineer frontend assumptions.

**Suggestion:** Send `null`/omit the field when "no preference" is selected (don't send `""`). Add a comment in the spec: canonical values are `"FEMALE"` and `"MALE"`; omit the field for no preference. Add this to the field notes table.

---

### C4 — Duplicate i18n key `fieldPhone`

**Description:** `public/locales/en/clients.json` already contains `"fieldPhone": "Phone"` (used by `ClientDetailPanel`'s overview tab field display). The spec lists `"fieldPhone"` as a new key to add. In JSON, duplicate keys are technically invalid; most parsers silently keep the last value, which is fine here since the value is identical — but this is a copy-paste error in the spec that will cause confusion during implementation and may trigger lint warnings.

**Impact:** Low severity, but will produce a confusing diff during implementation. If the value ever diverges, one of the two usages silently breaks.

**Suggestion:** Remove `"fieldPhone"` from the list of new i18n keys in the spec. The existing key is already correct and reusable.

---

## 3. Previously Addressed Items

None — this is the first review.

---

## 4. Alternative Architectural Challenge

**Alternative: Local modal dialog instead of SlidePanel**

Rather than wiring "Add Client" through the global `panelStore` / `SlidePanel` / `Shell.tsx` infrastructure, the "Add Client" flow could be a self-contained modal dialog rendered locally within `ClientsPage`, with its own open/close state via `useState`.

**Pros:**
- Zero changes to `panelStore`, `Shell.tsx`, or `PanelPrefill` — no cross-cutting store modifications for a single feature.
- The modal's success navigation (`openPanel('client', ...)`) can be called directly from `onSuccess` without the `closePanel → openPanel` sequencing concern.
- Common pattern for "create" flows (as opposed to "view/edit" flows where the slide panel is appropriate).

**Cons:**
- Diverges from the app's established visual language — every add/view flow in the app uses `SlidePanel`. A modal would feel inconsistent.
- A local state approach can't easily trigger the cross-panel navigation ("Save & Add Authorization" → open client detail) without still calling `openPanel`, reintroducing a coupling to `panelStore`.
- The slide panel gives more vertical space for the 10-field form (especially on smaller screens), which is more important than the horizontal space a modal provides.

**Verdict:** The current SlidePanel approach is the right call given the app's UI language. The alternative is noted for future "quick add" dialogs where form length is short.

---

## 5. Minor Issues & Improvements

- **`closePanel()` → `openPanel()` sequencing on "Save & Add Authorization":** React 18 automatic batching will likely batch these two Zustand updates into one render, meaning the `SlidePanel` never actually closes before reopening — it just swaps content while staying open. This is probably acceptable UX (no blank-panel flash), but the spec should acknowledge this intentionally rather than leaving it implicit. If a brief close animation is desired, a `setTimeout(openPanel, 300)` after `closePanel()` would allow the CSS transition to complete first.

- **No `Toast.tsx` or `toastStore` unit tests specified:** The test plan covers `NewClientPanel` and `useCreateClient` but not the new toast infrastructure. At minimum, add: (a) `toastStore` — `show()` sets `visible: true`; `dismiss()` sets `visible: false`; (b) `Toast.tsx` — renders when `visible`, link click calls `openPanel` with correct args, disappears after dismiss.

- **`preferredLanguages` case normalization unaddressed:** A user typing `"english, spanish"` vs `"English, Spanish"` produces different stored values. Since `LocalScoringService` parses language lists for matching, case inconsistency may silently break the preferences score. The spec should note that values should be title-cased before serialization, or document that matching is case-insensitive (verify in `LocalScoringService.parseLanguageList()`).

- **`dateOfBirth` serialization implicit:** `<input type="date">` returns `"YYYY-MM-DD"`, which Jackson deserializes correctly as `LocalDate`. This is correct but worth one line in the spec so the implementer doesn't second-guess it.

---

## 6. Questions for Clarification

1. **"Save & Add Authorization" panel transition:** Should the client detail panel's Authorizations tab land with the "Add Authorization" sub-form already open? The spec says "directly on the Authorizations tab" but `ClientDetailPanel` currently has no "add authorization" entry point in the tab. Is the intent just to land on the tab, or to trigger an add flow?

2. **Toast placement:** The spec says the toast renders in `Shell.tsx`. Where exactly — top of the main content area, bottom-right, or top-right? The existing app has no toast infrastructure, so this is the first instance and will establish a precedent.

3. **`serviceState` relationship to EVV:** If a client has no service state, `EvvStateConfig` lookup will fail silently (no EVV rules applicable). Is this acceptable at intake, or should there be a soft warning if the field is left empty?

---

## 7. Final Recommendation

**Approve with changes.**

Four issues require spec updates before implementation:

1. **C1** — Move `initialTab` out of `PanelPrefill` into a top-level `PanelState` field and a direct prop on `ClientDetailPanel`.
2. **C2** — Specify toast timer lifecycle (where `setTimeout` lives, how it's cleared on manual dismiss); add a test case for it.
3. **C3** — Change "no preference" gender value from `""` to `null`/omit; document canonical gender string values.
4. **C4** — Remove `"fieldPhone"` from the list of new i18n keys (already exists).

None of these require redesign. They are spec clarifications that prevent bugs during implementation. Address in the spec, then proceed to writing the implementation plan.
