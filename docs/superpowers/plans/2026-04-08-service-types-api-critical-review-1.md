# Critical Implementation Review — Service Types API Plan
**Plan:** `docs/superpowers/plans/2026-04-08-service-types-api.md`  
**Review:** 1 of N (no prior reviews found in this directory)  
**Note:** A pre-implementation review was previously conducted by a separate code-reviewer agent and saved to `docs/superpowers/code-review-2026-04-08-service-types-plan-critical-review-1.md`. That review's findings were incorporated into the plan. This review operates from the updated plan and treats those findings as resolved. Issues from that prior review are listed under "Previously Addressed Items" and are not re-raised here.

---

## 1. Overall Assessment

The plan is well-structured and has been substantially improved by the prior review cycle. The full IT boilerplate, @Sql truncation, Agency C seeding, and aria-busy/code type corrections are all present. However, one concrete commit-sequencing defect remains that will cause an uncommitted file after Task 8, the empty-state UX path is untested, and two dead i18n keys that are explicitly flagged by the prior review as cleanup candidates (R4/R5) are still present in the file being modified — making this the natural moment to remove them. The plan is close to implementation-ready but needs these corrections first.

---

## 2. Critical Issues

### CI-1 — Task 8 commit command omits `newShift.json`: serviceTypePcs removal will not be committed

**Location:** Task 8 Step 7 commit command

**Problem:** The plan correctly deferred the removal of `serviceTypePcs` from `newShift.json` to Task 8 (to keep it atomic with the TSX change). However, the Task 8 Step 7 commit command is:

```bash
git add frontend/src/components/schedule/NewShiftPanel.tsx \
        frontend/src/components/schedule/NewShiftPanel.test.tsx
git commit -m "feat: wire NewShiftPanel service type select to live API ..."
```

`frontend/public/locales/en/newShift.json` is not staged. After Task 8 completes, the `serviceTypePcs` key removal will be an uncommitted modification sitting in the working tree, separate from the atomic commit the plan intended. The next `git status` will show `newShift.json` as modified but unstaged.

**Why it matters:** Breaks the atomic commit guarantee the plan spent effort establishing. Leaves the repo in a dirty state after Task 8.

**Fix:** Add `frontend/public/locales/en/newShift.json` to the Task 8 Step 7 `git add`:

```bash
git add frontend/src/components/schedule/NewShiftPanel.tsx \
        frontend/src/components/schedule/NewShiftPanel.test.tsx \
        frontend/public/locales/en/newShift.json
```

---

## 3. Previously Addressed Items

All items from the prior code-reviewer agent review (`code-review-2026-04-08-service-types-plan-critical-review-1.md`) have been resolved:

- **C2** — Full `@Sql` truncation statement including `service_types` now present verbatim in the IT skeleton.
- **C3** — Agency C seeded in `@BeforeEach` with a user; empty-list test authenticates with `admin-c@test.com`.
- **G1** — `code: string` (not `string | null`) in `ServiceTypeResponse` TypeScript interface.
- **G3** — Manual test checkpoint updated to show "Personal Care Services" and "Skilled Nursing Visit", not codes.
- **G4** — `serviceTypePcs` removal moved to Task 8; Task 7 explicitly notes not to remove it yet.
- **G7** — `aria-busy={serviceTypesLoading ? "true" : "false"}` in Task 8 Step 3.
- **R1** — Full IT boilerplate provided (autowired fields, `tokenFor`, `authFor`, `@BeforeEach` seed).
- **R3** — Task 7 explicitly instructs agent not to remove `selectServiceType`.
- **R6** — Task 3 Step 2 wording corrected to "tests fail with 404 responses (controller not yet mapped)".

---

## 4. Minor Issues & Improvements

### MI-1 — Empty state not tested in NewShiftPanel.test.tsx

**Location:** Task 8 Step 1 — component test specification

**Problem:** The plan specifies three component tests (loading, error, loaded). The UX spec defines four states for the service type select, including the empty state (API returns 200 with `[]`). This path is not exercised by any test. In the empty state: the select is disabled, the `noServiceTypesOption` option is shown, and the `noServiceTypesHint` paragraph is rendered below it.

**Why it matters:** The empty state has its own render branch and a separate i18n key. Without a test, a typo in the condition (`serviceTypes.length === 0 && !serviceTypesLoading && !serviceTypesError`) will go undetected.

**Fix:** Add a fourth test to Task 8 Step 1:

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

### MI-2 — `disabled:opacity-60` on the service type select is inconsistent with `disabled:opacity-50` used everywhere else

**Location:** Task 8 Step 3, new select element className

**Problem:** The plan's select uses `disabled:opacity-60`. The rest of `NewShiftPanel` uses `disabled:opacity-50` (confirmed on the submit button at line 194 of the current component). No other element in the codebase uses `opacity-60` for a disabled state.

**Fix:** Change `disabled:opacity-60` to `disabled:opacity-50` to match the existing convention.

### MI-3 — Dead i18n keys `caregiverPhaseNote` and `mockAlert` still in `newShift.json`

**Location:** Task 7 Step 1 and `frontend/public/locales/en/newShift.json`

**Problem:** These two keys were flagged as dead in the prior review (R4/R5) and are confirmed dead by reading the current `NewShiftPanel.tsx`:
- `caregiverPhaseNote` (`"Phase 4 will populate this list from the API."`) — no reference in TSX
- `mockAlert` (`"Mock: shift for client {{clientId}} on {{date}} created..."`) — no reference in TSX

Task 7 modifies `newShift.json` (adds 5 keys). This is the natural moment to remove the dead keys. Leaving them in accumulates translation debt and will confuse future i18n tooling audits.

**Fix:** Add removal of `caregiverPhaseNote` and `mockAlert` to Task 7 Step 1, alongside the `serviceTypePcs` note ("do not remove yet — remove in Task 8").

### MI-4 — Plan omits `useCreateShift` mock in `NewShiftPanel.test.tsx` but it's safe

**Location:** Task 8 Step 1

**Observation (not a defect):** `NewShiftPanel` imports `useCreateShift` which internally uses `useMutation`. The plan's tests don't mock `useCreateShift` at the module level — instead they rely on `MockAdapter` + `QueryClientProvider`. This works because none of the tests actually submit the form, so `mutateAsync` is never called. However, `createMutation.isPending` and `createMutation.isError` will both be `false` by default, which is the correct baseline state for the tests. No change needed, but noting this so the implementing agent isn't surprised.

### MI-5 — Plan comment about `requiredCredentials` null guard is inaccurate (carry-forward from prior G2)

**Location:** `ServiceTypeService` implementation in Task 2 Step 3

**Problem:** The prior review flagged (G2) that the Jackson parse `catch` block doesn't actually protect against `null` (because `objectMapper.readValue(null, ...)` throws `IllegalArgumentException` which IS caught by `catch (Exception e)` — actually this is caught). Re-examining: `IllegalArgumentException` extends `RuntimeException` extends `Exception`, so the broad `catch (Exception e)` DOES catch it. G2 was actually wrong to flag this as a risk. The guard is correct and complete. No change needed. Including here for completeness since it was unresolved from the prior review.

---

## 5. Questions for Clarification

**Q1:** The `AgencyUser` constructor in the IT boilerplate uses:
```java
new AgencyUser(agencyA.getId(), "admin-a@test.com", passwordEncoder.encode(TEST_PASSWORD), UserRole.ADMIN)
```
This is confirmed correct by cross-referencing `ShiftSchedulingControllerIT`. No action needed unless the actual `AgencyUser` constructor differs — verified it does not.

**Q2:** Should `retry: 1` be set in `useServiceTypes`? The TypeScript spec agent recommended it ("fail faster on bad connection, not 3 retries"). The plan uses React Query's default (3 retries). With a 5-minute staleTime, a slow first-load with 3 retries could leave the user looking at the loading state for ~30+ seconds on a degraded network. Consider adding `retry: 1` to `useServiceTypes`.

**Q3:** The plan doesn't address what happens when `NewShiftPanel` is open and service types are invalidated externally (e.g., an admin deletes a service type in another tab). The 5-minute staleTime means the deleted type stays in the dropdown for up to 5 minutes. This is acceptable given the target agency size and is consistent with how payers/clients are cached. No action needed, but worth documenting.

---

## 6. Final Recommendation

**Approve with changes.** Fix CI-1 (Task 8 commit command missing `newShift.json`) — this is a concrete defect that will leave the repo in a dirty state. Address MI-1 (add empty-state test) and MI-2 (opacity consistency). MI-3 (dead i18n keys) is a clean housekeeping fix that costs nothing while the file is already being touched. After these four corrections, the plan is ready to execute.

| Issue | Priority | Action |
|-------|----------|--------|
| CI-1: Task 8 commit omits newShift.json | **Must fix** | Add newShift.json to git add in Task 8 Step 7 |
| MI-1: Empty state untested | Should fix | Add 4th component test for empty state |
| MI-2: opacity-60 vs opacity-50 | Should fix | Change to opacity-50 |
| MI-3: Dead i18n keys | Nice to have | Remove caregiverPhaseNote + mockAlert in Task 7 |
