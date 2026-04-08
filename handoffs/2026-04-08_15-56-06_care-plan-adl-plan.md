---
date: 2026-04-08T15:56:06
git_commit: 7109e3a9e590b60ac6d3997d1d072d1d9ab0397e
branch: main
repository: hcare
topic: "Care Plan & ADL Management — Implementation Handoff"
tags: [handoff, session-transition, react, spring-boot, care-plan, adl, typescript]
status: in_progress
last_updated: 2026-04-08
type: implementation_handoff
---

# Handoff: Care Plan & ADL Management — Ready to Implement

## 0. Executive Summary (TL;DR)

1. We completed a full brainstorm-and-design session for the Care Plan & ADL Management feature — the Care Plan tab inside `ClientDetailPanel` — producing both a committed spec and a committed implementation plan.
2. I stopped immediately after writing and committing the implementation plan (`docs/superpowers/plans/2026-04-08-care-plan-adl.md`); no implementation has started.
3. The single most important next action is to begin executing the plan starting with **Task 1** (backend non-locking repository query): add `findActiveByClientId` to `CarePlanRepository.java` and its test in `CarePlanDomainIT.java`.

---

## 1. Technical State

**Active Working Set:**
- `docs/superpowers/plans/2026-04-08-care-plan-adl.md:1` — the 11-task implementation plan, start here
- `docs/superpowers/specs/2026-04-08-care-plan-adl-design.md:1` — the approved design spec, reference for any ambiguity
- `backend/src/main/java/com/hcare/domain/CarePlanRepository.java:1` — Task 1 target: add `findActiveByClientId`
- `backend/src/main/java/com/hcare/api/v1/clients/ClientService.java:1` — Task 2 target: add `getActiveCarePlan`
- `backend/src/main/java/com/hcare/api/v1/clients/ClientController.java:1` — Task 2 target: add `GET /{id}/care-plans/active`
- `frontend/src/components/clients/ClientDetailPanel.tsx:133` — Task 11 target: replace `carePlanPhaseNote` placeholder with `<CarePlanTab>`

**Current Errors / Blockers:**
```
None
```

**Environment:**
- Uncommitted changes: yes — untracked plan/spec files for other features (add-client-panel, add-caregiver-panel) — these are unrelated, do not touch
- Staged changes: none
- ENV vars or config required: none beyond standard dev setup (see CLAUDE.md)
- Any running processes / background jobs: none

---

## 2. Progress Tracker

| Task | Status | Location | Notes |
|------|--------|----------|-------|
| Design spec (brainstorm) | ✅ Complete | `docs/superpowers/specs/2026-04-08-care-plan-adl-design.md:1` | Committed at `12cd1c8` |
| Implementation plan | ✅ Complete | `docs/superpowers/plans/2026-04-08-care-plan-adl.md:1` | Committed at `7109e3a` |
| Task 1: `findActiveByClientId` repo method + test | ⏳ Pending | `backend/src/main/java/com/hcare/domain/CarePlanRepository.java:1` | Start here |
| Task 2: `getActiveCarePlan` service + GET /active endpoint | ⏳ Pending | `backend/src/main/java/com/hcare/api/v1/clients/ClientService.java:209` | Depends on Task 1 |
| Task 3: Frontend types | ⏳ Pending | `frontend/src/types/api.ts:288` | Append to bottom of file |
| Task 4: `carePlans.ts` API functions | ⏳ Pending | `frontend/src/api/carePlans.ts:1` | New file |
| Task 5: ADL template JSON (100 tasks) | ⏳ Pending | `frontend/public/locales/en/adl-task-templates.json:1` | New file |
| Task 6: `clients.json` i18n keys | ⏳ Pending | `frontend/public/locales/en/clients.json:59` | Add before closing `}` |
| Task 7: `useCarePlan.ts` hooks + tests | ⏳ Pending | `frontend/src/hooks/useCarePlan.ts:1` | New files |
| Task 8: `AddGoalForm` + tests | ⏳ Pending | `frontend/src/components/clients/AddGoalForm.tsx:1` | New files |
| Task 9: `AddTaskPanel` + tests | ⏳ Pending | `frontend/src/components/clients/AddTaskPanel.tsx:1` | New files |
| Task 10: `CarePlanTab` + tests | ⏳ Pending | `frontend/src/components/clients/CarePlanTab.tsx:1` | New files; most complex task |
| Task 11: Wire `CarePlanTab` into `ClientDetailPanel` | ⏳ Pending | `frontend/src/components/clients/ClientDetailPanel.tsx:133` | One-line swap |

---

## 3. Mental Model (Most Critical Section)

**Why the current approach was chosen:**

The core UX insight that drove every decision: **small agency admins should never see a DRAFT state**. Early design options exposed the care plan lifecycle (DRAFT → ACTIVE → SUPERSEDED) to users, which UX researcher agents flagged as likely to cause "forgotten DRAFTs" — invisible to caregivers but appearing abandoned to admins. The solution: all setup state lives in React `useState` (`pendingTasks`, `pendingGoals`). Nothing hits the backend until the single "Save & Activate Plan" button fires, which atomically creates the plan, adds all tasks/goals, and activates it in sequence.

**The Save & Activate sequence is intentionally NOT a useMutation hook** — it's a multi-step orchestrated async function inside `CarePlanTab` that calls raw API functions from `carePlans.ts`. This is by design: React Query mutations don't compose well across 4+ sequential steps. The implementation plan has the exact async handler code.

**"New Version" re-uses the exact same setup mode** — it just pre-populates `pendingTasks`/`pendingGoals` from the React Query cache of the active plan. The `isNewVersion` flag only controls the banner text. No special versioning logic needed on the frontend.

**The ADL template library is a static JSON file** at `frontend/public/locales/en/adl-task-templates.json`, fetched via raw `fetch()` (not `apiClient`) with locale-aware path (`/locales/${lang}/adl-task-templates.json`) and `en` fallback. Locale is handled by path convention, not filename suffix, consistent with how all other i18n files work in this project.

**The `findActiveByClientId` repository method is a NEW method** — separate from the existing `findByClientIdAndStatus` which has `@Lock(LockModeType.PESSIMISTIC_WRITE)`. That lock is correct for the activation transaction. The new read-only endpoint must NOT acquire that lock, hence the separate JPQL method without `@Lock`. Mixing these up would cause read operations to block during writes.

**`CarePlanTab` receives `clientFirstName` as a prop** (alongside `clientId`) rather than re-fetching client data internally, because `ClientDetailPanel` already has the client loaded via `useClientDetail`. This avoids a duplicate network request.

**Codebase Gotchas Discovered This Session:**
- `backend/src/main/java/com/hcare/domain/CarePlanRepository.java:13` — the existing `findByClientIdAndStatus` carries `@Lock(PESSIMISTIC_WRITE)` — this is intentional for activation, but a new non-locking method is needed for the read-only GET endpoint
- `frontend/src/components/clients/ClientDetailPanel.tsx:133` — the care plan tab currently renders a single `<p>` with `t('carePlanPhaseNote')` — the replacement is straightforward (one line)
- `frontend/src/i18n.ts:14` — the `clients` namespace is already registered; no changes needed to add new keys to `clients.json`
- `frontend/public/locales/en/clients.json:23` — `carePlanPhaseNote` key will become dead code after Task 11 but removing it is optional cleanup

**Dead Ends — Do Not Repeat These:**

| Approach Tried | Why It Failed | Evidence |
|---|---|---|
| Exposing DRAFT/ACTIVE lifecycle to users in UI | "Forgotten DRAFTs" UX problem — DRAFTs are invisible to caregivers but look abandoned to admins | UX researcher agent reviews in brainstorm session |
| Using `findByClientIdAndStatus` for the GET /active endpoint | That method has `@Lock(PESSIMISTIC_WRITE)` — wrong for a read-only endpoint | `CarePlanRepository.java:13` |
| Encoding locale in JSON filename (e.g. `adl-tasks-en.json`) | Inconsistent with `public/locales/{lang}/` convention used by all other i18n files | `frontend/src/i18n.ts:27` |

**Key Decisions Made:**

| Decision | Rationale | Alternative Rejected |
|---|---|---|
| Setup mode state in React `useState`, nothing persisted until Save & Activate | Prevents "forgotten DRAFT" UX failure for small agency admins | Persisting DRAFT to backend on each step |
| Save & Activate uses raw API function calls, not a single `useMutation` | Multi-step orchestration (create → tasks → goals → activate) doesn't fit the single-mutation pattern | One big mutation that doesn't compose |
| New Version re-uses same setup mode with pre-populated state | No special versioning UI needed; same UX path | Separate "versioning mode" with different components |
| ADL templates as static JSON in `public/locales/` | Locale-aware, zero backend changes, cache-forever | Backend endpoint (required schema change + API versioning) |
| `AddTaskPanel` and `AddGoalForm` are dumb/controlled components | Parent (`CarePlanTab`) decides whether to hit API or update local state based on current mode | Components containing their own API logic (coupling mode knowledge into leaf components) |

**Assumptions in Play:**
- The `clients` namespace i18n JSON can accept new keys without any code changes — confirmed by `frontend/src/i18n.ts:14` where `'clients'` is already in the `ns` array
- `crypto.randomUUID()` is available in the browser environment (all modern browsers support it; this project targets modern browsers per the tech stack)
- The `carePlanPhaseNote` key in `clients.json` can be left as dead code after Task 11 — removing it would require updating tests that assert on it (not worth the churn)

---

## 4. Delta — Changes Made This Session

All changes committed — see git log above.

Relevant commits:
- `7109e3a` — `docs/superpowers/plans/2026-04-08-care-plan-adl.md:1` — 11-task implementation plan with complete code for every step
- `12cd1c8` — `docs/superpowers/specs/2026-04-08-care-plan-adl-design.md:1` — full design spec (approved by user)

---

## 5. Next Steps (Ordered — Do Not Skip Steps)

1. **Verify state** (confirm clean backend test suite before touching anything):
   ```bash
   cd /Users/ronstarling/repos/hcare/backend && mvn test -q 2>&1 | tail -5
   ```
   Expected output: `BUILD SUCCESS` with no test failures.

2. **Immediate action — Task 1**: Add `findActiveByClientId` non-locking query to `CarePlanRepository.java` and its test.
   - Test location: `backend/src/test/java/com/hcare/domain/CarePlanDomainIT.java:116` (add after existing test)
   - Implementation location: `backend/src/main/java/com/hcare/domain/CarePlanRepository.java:13` (add after `findByClientIdAndStatus`)
   - Full code for both is in `docs/superpowers/plans/2026-04-08-care-plan-adl.md` under **Task 1**

3. **Then Task 2**: Add `getActiveCarePlan` service method + `GET /{id}/care-plans/active` controller endpoint + 3 IT tests.
   - Full code in `docs/superpowers/plans/2026-04-08-care-plan-adl.md` under **Task 2**

4. **Verification after Task 2**:
   ```bash
   cd /Users/ronstarling/repos/hcare/backend && mvn test -Dtest=ClientControllerIT -q
   ```
   Expected: All tests in `ClientControllerIT` PASS including the 3 new `/active` tests.

5. **Watch for**: The `activateCarePlan` test at `ClientControllerIT.java:179` re-activates an already-DRAFT plan — don't confuse this with the new GET /active endpoint. The new test creates a plan, activates it, then calls GET /active. Keep the test setup clean.

6. **After backend is done, proceed through Tasks 3–11 in order** — each is self-contained with complete code in the plan document. Tasks 3–6 have no tests (types + static data), Tasks 7–10 are TDD. Task 11 (ClientDetailPanel wire-up) is last and trivial (one import + one line swap).

---

## 6. Artifacts & References

- **Design spec**: `docs/superpowers/specs/2026-04-08-care-plan-adl-design.md:1`
- **Implementation plan**: `docs/superpowers/plans/2026-04-08-care-plan-adl.md:1`
- **Brainstorm mockups**: `.superpowers/brainstorm/81121-1775681238/content/care-plan-active.html:1`, `empty-and-setup-flow.html:1`, `add-task-panel.html:1`
- **Key existing files to read before implementing**:
  - `backend/src/main/java/com/hcare/domain/CarePlanRepository.java:1` — understand existing lock methods before adding new one
  - `backend/src/test/java/com/hcare/api/v1/clients/ClientControllerIT.java:1` — understand test setup pattern (seed, auth, restTemplate)
  - `frontend/src/components/clients/ClientDetailPanel.tsx:1` — understand tab structure before Task 11
  - `frontend/src/hooks/useClients.ts:1` — reference pattern for hook structure
  - `frontend/src/api/clients.ts:1` — reference pattern for API function structure
- **Related features (unrelated, do not implement)**: `docs/superpowers/plans/2026-04-08-add-client-panel.md` and `2026-04-08-add-caregiver-panel.md` are also untracked — separate features, ignore
