# Critical Design Review #2
## Add Client Panel — Design Spec
**Reviewed:** `2026-04-08-add-client-panel-design.md`
**Reviewer pass:** 2 (previous: review-1)

---

## 1. Overall Assessment

All four critical issues and all minor issues from Review #1 have been addressed. The spec is substantially cleaner: `initialTab` is properly separated from `PanelPrefill`, the toast timer lifecycle is fully specified, gender values are correct, the duplicate i18n key is gone, batching behavior is documented, and language case normalization was added. Three new issues surface in this pass: a gap in the `openPanel` method signature (TypeScript compilation blocker), a coupling problem with `backLabel` in `Toast.tsx`, and an incorrect case-normalization directive that contradicts the backend's own lowercasing.

---

## 2. Critical Issues

### C1 — `openPanel` method signature not updated — TypeScript compilation blocker

**Description:** Review #1's C1 fix correctly moves `initialTab` to a top-level `PanelState` field. However, the spec's data flow shows:

```ts
openPanel('client', client.id, {
  backLabel: t('backLabel'),
  initialTab: 'authorizations',
})
```

The current `openPanel` options parameter is typed as `{ prefill?: PanelPrefill; backLabel?: string }`. The `initialTab` key is not in this type — TypeScript will reject this call at compile time. The spec modifies `PanelState` to hold `initialTab` but never shows the updated `openPanel` signature that accepts it through the `options` argument.

**Impact:** Implementation blocker. The implementer must guess how `initialTab` flows from the `openPanel` call site into `PanelState`. Without a spec-level contract, different implementers will choose inconsistent approaches (adding it to `options`, adding a 4th parameter, etc.).

**Suggestion:** Add to the spec: update `openPanel`'s `options` type to include `initialTab?: string`. The implementation then does `set({ ..., initialTab: options?.initialTab ?? null })` and resets it to `null` in `closePanel()`. Show the updated signature in the Architecture section.

---

### C2 — `Toast.tsx` in `common/` contains clients-specific navigation logic — i18n coupling

**Description:** The spec shows `Toast.tsx` (placed in `src/components/common/`) calling:

```ts
openPanel('client', clientId, { initialTab: 'authorizations', backLabel: t('backLabel') })
```

`t('backLabel')` resolves to `"← Clients"` from the `clients` i18n namespace. A generic common component importing from a feature namespace is a textbook coupling violation — `Toast.tsx` now silently depends on the `clients` namespace existing and having a `backLabel` key. It also hard-codes `'client'` as the panel type and `'authorizations'` as the tab, making `Toast.tsx` not reusable for any other feature.

The current `toastStore` interface has `message`, `linkLabel`, and `clientId` — but no `backLabel` or navigation target. The navigation details belong in the store alongside the display data.

**Impact:** `Toast.tsx` cannot be reused by any other feature without modification. If the `clients` namespace is ever renamed, the link silently breaks. The architectural boundary between common infrastructure and feature-specific logic is violated.

**Suggestion:** Add `backLabel: string`, `panelType: string`, and `panelTab: string` to the `toastStore` interface. `NewClientPanel` populates all fields via `toastStore.show(...)`. `Toast.tsx` reads navigation params from the store and calls `openPanel(panelType, clientId, { initialTab: panelTab, backLabel })` — no i18n import required. This keeps `Toast.tsx` fully generic.

---

### C3 — Title-case normalization directive is incorrect — backend already lowercases

**Description:** The spec instructs the frontend to title-case `preferredLanguages` tokens before serializing (e.g., `"Spanish"` not `"spanish"`). This contradicts the actual backend behavior: `LocalScoringService.parseLanguageList()` calls `map(String::toLowerCase)` on all language tokens before any comparison (line 380). Both the client's preferred languages and the caregiver's languages are lowercased before the `contains` check. Case on the stored value is irrelevant to matching.

Sending `"Spanish"` vs `"spanish"` vs `"SPANISH"` produces identical scoring results. The title-case logic in the spec is wasted code that will confuse future maintainers.

**Impact:** Unnecessary implementation complexity; potential confusion when a P2 developer reads stored values and tries to understand the casing convention.

**Suggestion:** Remove the title-case normalization directive. Replace with: *"Language tokens are stored as-entered; `LocalScoringService.parseLanguageList()` lowercases all values before comparison, so stored case has no effect on matching."* Simple `split(',').map(s => s.trim()).filter(Boolean)` is sufficient — no case transformation needed.

---

## 3. Previously Addressed Items (from Review #1)

- **C1** ✅ — `initialTab` removed from `PanelPrefill`; now a top-level `PanelState` field and direct prop on `ClientDetailPanel`.
- **C2** ✅ — Toast auto-dismiss timer lifecycle fully specified: `setTimeout` in `Toast.tsx` `useEffect`, `clearTimeout` in cleanup.
- **C3** ✅ — `preferredCaregiverGender` "no preference" now sends `undefined`; canonical values `"FEMALE"` / `"MALE"` documented.
- **C4** ✅ — Duplicate `"fieldPhone"` removed from new i18n additions.
- React 18 batching note ✅ — documented and marked intentional.
- Toast `toastStore` and `Toast.tsx` test cases ✅ — added to test plan.
- `preferredLanguages` case normalization ✅ — added (though the direction is wrong; see C3 above).

---

## 4. Alternative Architectural Challenge

**Alternative: event-bus / custom React event for post-save navigation instead of direct `openPanel` call**

Rather than `NewClientPanel` calling `openPanel` directly after `createMutation.mutateAsync`, the panel could emit a custom DOM event (or a minimal pub/sub bus) that `Shell.tsx` subscribes to. On `ClientCreated` event, `Shell.tsx` handles the navigation decision.

**Pros:**
- `NewClientPanel` has zero dependency on `panelStore` — it only fires an event with the new client ID. Pure form component.
- Navigation logic (which panel, which tab) lives in one place (`Shell.tsx`), not scattered across form components.
- Easier to test `NewClientPanel` in isolation (no need to mock `panelStore`).

**Cons:**
- Adds an event bus abstraction for a use case that's well-handled by a direct Zustand call — violates YAGNI for this project's scale.
- Custom events are less idiomatic in React than store calls; harder to trace in React DevTools.
- `NewClientPanel` already imports from `panelStore` for `closePanel` — adding an event bus gains little isolation while adding complexity.

**Verdict:** Direct `openPanel` call is correct for this codebase's size. The event bus pattern is worth knowing for larger apps where panel logic becomes a coordination bottleneck.

---

## 5. Minor Issues & Improvements

- **`initialTab` typed as `string` in `PanelState` but `Tab` in `ClientDetailPanel`:** The `Tab` union type (`'overview' | 'carePlan' | 'authorizations' | 'documents' | 'familyPortal'`) is defined locally in `ClientDetailPanel.tsx`. The `PanelState` field is `string | undefined` — which is correct (keeping the store generic). However, `ClientDetailPanel` should cast it: `useState<Tab>((initialTab as Tab | undefined) ?? 'overview')` and validate it falls within the union. An invalid `initialTab` (e.g. a typo) silently defaults to `'overview'` because `useState` only reads the initial value once. This is acceptable behavior but worth a one-line comment in the spec.

- **`dateOfBirth` serialization still undocumented** (flagged as minor in Review #1, not yet added): `<input type="date">` returns `"YYYY-MM-DD"`. Jackson on the backend parses this to `LocalDate` correctly. A one-sentence note in the field table would prevent implementer uncertainty.

- **`toastStore` initial state not shown:** The spec shows the TypeScript interface but not the Zustand `create(...)` initial values. `message` and `linkLabel` are non-optional strings — their initial values (`''`) should be specified so the implementer doesn't have to infer them.

---

## 6. Questions for Clarification

From Review #1, two questions remain unanswered in the spec:

1. **Toast placement in `Shell.tsx`:** Top-of-content-area, bottom-right, or bottom-center? This is the first toast in the app and sets a precedent. The spec should specify position and z-index relative to `SlidePanel`.

2. **"Save & Add Authorization" landing state:** The spec lands on the Authorizations tab. Does the existing authorization list immediately show an empty state with a CTA to add one, or should the panel scroll/focus to a specific element? `ClientDetailPanel`'s Authorizations tab already renders a list — this question is about whether any additional affordance is needed on arrival.

---

## 7. Final Recommendation

**Approve with changes.**

Two issues are implementation blockers (C1, C2) and one removes unnecessary code (C3). None require redesign:

1. **C1** — Add `initialTab?: string` to `openPanel`'s `options` type; show how `closePanel` resets it.
2. **C2** — Move `backLabel`, `panelType`, and `panelTab` into `toastStore`; remove i18n and navigation hardcoding from `Toast.tsx`.
3. **C3** — Remove title-case normalization; document that `parseLanguageList` lowercases before comparison, so stored case is irrelevant.

After these three updates the spec is implementation-ready.
