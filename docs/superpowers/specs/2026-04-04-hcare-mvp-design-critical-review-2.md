# hcare MVP Design — Critical Review #2
**Reviewer:** Senior Principal Architect (automated critical review)
**Date:** 2026-04-04
**Source:** `2026-04-04-hcare-mvp-design.md`
**Previous reviews:** `2026-04-04-hcare-mvp-design-critical-review-1.md`

---

## 1. Overall Assessment

The spec has made substantial progress since review-1. All 13 issues from the first pass — including the two blocking items (multi-tenancy enforcement and offline sync) — are visibly addressed and the additions are architecturally sound. The HIPAA section, the Hibernate `@Filter` mechanism, the `SyncBatch` schema, and the `PORTAL_SUBMIT` compliance status are all clear improvements. However, this review surfaces **five new issues**, four of which are critical enough to block a clean implementation: the BFF self-contradiction (stateless claim vs. stateful deduplication table), an authentication gap on the sync endpoint, a shift generation race condition, and a JSON field portability trap between H2 and PostgreSQL. A fifth issue — missing `EvvStateConfig` seed data strategy — is medium-severity but will block the onboarding wizard on day one. None of these require architectural redesign; all are resolvable with targeted spec additions.

---

## 2. Critical Issues

### C1 — BFF is asserted stateless but requires a stateful deduplication table

**Description:** Section 4 states the BFF "holds no data of its own" and is a "stateless thin adapter." Section 6 (Offline Sync) then specifies: "The BFF tracks processed `(deviceId, visitId, eventType, occurredAt)` tuples in a deduplication table (TTL 30 days)." These two statements are directly contradictory.

**Why it matters:** This is not a naming quibble. The stateless claim drives deployment decisions — if the BFF is truly stateless, it can run as multiple replicas behind a load balancer with no coordination. As soon as one instance holds a deduplication table that another instance cannot see, idempotency breaks: a retry routed to a different instance will re-process the same `SyncBatch` events. The contradiction will surface as a production bug the first time the BFF is scaled to more than one instance.

**Actionable fix:** Choose one of three approaches and state it explicitly:

1. **BFF delegates dedup to Core API** — The BFF forwards each event to Core API, which performs deduplication inside its own database (`SyncDeduplication` table). The BFF remains genuinely stateless. Add a `POST /api/v1/internal/sync/batch` endpoint on Core API. This is the simplest correct approach.
2. **BFF uses a shared cache** — A Redis or Valkey instance holds the deduplication set. BFF remains stateless at the process level; the cache is the shared coordination point. Requires an additional infrastructure dependency, but keeps the sync reconciliation logic in the BFF.
3. **Accept that BFF is stateful** — Drop the "holds no data of its own" claim. The BFF has its own database (or schema partition). Deployment and scaling docs must account for it.

Approach 1 is recommended for P1 simplicity and HIPAA audit logging consistency (Core API already writes `PhiAuditLog`).

---

### C2 — SyncBatch `caregiverId` field is not validated against the authenticated JWT

**Description:** The `SyncBatch` payload includes a `caregiverId` field supplied by the client device. The spec describes the endpoint as `POST /sync/visits` on the BFF but does not define the authentication model or specify that the JWT subject must match `caregiverId` in the payload.

**Why it matters:** If a caregiver's JWT is stolen or a malicious app is sideloaded, an attacker can submit a `SyncBatch` with `caregiverId = <victim_id>`, creating false EVV records for another caregiver. EVV records are compliance artifacts that affect Medicaid reimbursement and care delivery records — fraudulent EVV submission is a federal offense under 42 CFR. The BFF accepting a `caregiverId` that does not match the authenticated user is an authorization failure.

**Actionable fix:** Add explicit language: "The BFF authenticates the sync request via the caregiver's JWT. The `caregiverId` in the `SyncBatch` payload must match the `sub` claim of the JWT. If they differ, the BFF returns HTTP 403 and logs a security event to `PhiAuditLog` with action `UNAUTHORIZED_SYNC_ATTEMPT`." The `caregiverId` field in the payload becomes a belt-and-suspenders check, not the source of truth for identity.

---

### C3 — Shift generation has an unaddressed race condition

**Description:** "Generation is triggered on pattern save and by a nightly background job." No locking strategy is defined. Both the `RecurrencePattern.save()` path and the nightly job read `generatedThrough` and then write new `Shift` records. If both trigger concurrently for the same pattern (e.g., an admin edits a pattern seconds before the nightly job runs), both processes read the same `generatedThrough` value and both generate the same shifts — resulting in duplicate shifts.

**Why it matters:** Duplicate shifts assigned to caregivers appear as genuine shifts on the caregiver's schedule. A caregiver who clocks into a duplicate will create an EVV record on a non-canonical shift, which will either be processed twice or create a compliance anomaly. Duplicate shifts also corrupt the authorization utilization calculation (`usedUnits`). Cleaning up duplicate shifts that already have EVV records attached is the kind of data integrity problem that takes days to diagnose.

**Actionable fix:** Apply the same pattern already used for `Authorization.usedUnits`: add `@Version` optimistic locking to `RecurrencePattern`. When the pattern-save path and the nightly job both try to advance `generatedThrough`, the second transaction retries and finds `generatedThrough` already advanced — it generates no additional shifts. Note this explicitly in the spec alongside the shift generation horizon description.

---

### C4 — `EvvStateConfig` has no defined data population strategy

**Description:** The EVV compliance system — including the onboarding wizard's auto-aggregator-selection — depends entirely on `EvvStateConfig` being pre-populated with per-state rules. The spec defines the entity and its fields in detail but never states how this reference data gets into the database. There is no mention of a Flyway seed migration, an admin bootstrap API, or a bundled data file.

**Why it matters:** On first deployment (and in every developer's local environment), `EvvStateConfig` is an empty table. The onboarding wizard step "selects EVV aggregator automatically based on state + payer type" silently fails with a null aggregator. The EVV compliance system computes every visit as non-compliant because there are no state rules to evaluate against. The compliance status computation — the product's headline differentiator — is broken out of the box. This is a P1-day-one blocker.

**Actionable fix:** Add a specification note: "`EvvStateConfig` is bootstrapped by a Flyway seed migration (`V2__evv_state_config_seed.sql`) containing all 50-state configurations. This migration runs on startup before any application code. The source of truth for the seed data is the `docs/superpowers/evv-state-reference.md` reference document already in this repository. `PayerEvvRoutingConfig` for multi-aggregator states (NY, FL, NC, VA, TN, AR) is seeded in the same migration." Additionally, the onboarding wizard must degrade gracefully if a state is not yet configured (UNKNOWN aggregator state, admin prompted to contact support) rather than silently failing.

---

### C5 — H2 / PostgreSQL portability breaks silently for JSON field queries

**Description:** The domain model uses JSON fields on two entities: `EVVRecord.stateFields` and `EvvStateConfig.extraRequiredFields`. H2 has no native `jsonb` type — it stores JSON as `VARCHAR` or `CLOB`. PostgreSQL's native `jsonb` supports path queries (`stateFields->>'taskId'`), containment operators, and indexing. If any repository method uses PostgreSQL-native JSON syntax (e.g., a `@Query` with `::jsonb` cast or `->>`), that query will silently succeed on PostgreSQL but throw a syntax error on H2. Conversely, H2-compatible queries may not use PostgreSQL's indexes.

**Why it matters:** This is the same class of deployment trap as M5 (HikariCP + virtual threads) in review-1 — tests pass locally (on H2), production breaks. The MO task documentation requirement (`stateFields`) and state-specific extra required fields (`extraRequiredFields`) are both likely candidates for query predicates as the implementation grows. This is not hypothetical: Missouri requires task-level documentation in EVV records, which will require checking `stateFields` contents to compute compliance status.

**Actionable fix:** Add a constraint to the spec: "JSON fields (`stateFields`, `extraRequiredFields`) are stored as opaque JSON strings. No database-level JSON path queries (PostgreSQL `->>`/`@>` operators) are permitted in P1. All JSON parsing is done at the application layer via `ObjectMapper`. This preserves H2/PostgreSQL portability. Any query that filters or indexes on a JSON field value must extract that value to a dedicated column before querying." This is a one-sentence implementation rule that prevents the portability break.

---

## 3. Previously Addressed Items

All 13 issues from critical-review-1 are resolved:

- **C1** (multi-tenancy): Hibernate `@FilterDef`/`@Filter` + `HandlerInterceptor` — structurally enforced, not asserted.
- **C2** (offline sync): Full `SyncBatch` schema + 5-scenario conflict resolution table + idempotency contract — complete design.
- **C3** (compliance status 3 sources): Core API is single authority; BFF is payload adapter calling `GET /api/v1/visits/today?mobile=true`; `DailyComplianceSummary` is aggregate counts only.
- **C4** (authorization concurrency): `@Version` on `Authorization` entity — explicitly noted.
- **C5** (HIPAA): Section 9 added with `PhiAuditLog` entity, PHI transit/rest rules, BAA table, access control table.
- **C6** (shift generation): 8-week rolling horizon, `generatedThrough` frontier, `sourcePatternId` FK — all specified.
- **M1** (geocoding): Added to tech stack with haversine/Google Maps approach and at-save-time geocoding.
- **M2** (family portal auth): JWT with `clientId` claim, magic link auth, `clientId`-scoped family endpoints.
- **M3** (feature flags): `FeatureFlags` per-agency entity with `aiSchedulingEnabled` gates Pro-tier.
- **M4** (credential verification): Explicitly stated as manual admin action at P1.
- **M5** (HikariCP/virtual threads): `maximum-pool-size` tuning note added to tech stack.
- **M6** (per-client state override): `Client.serviceState` nullable field for border-county routing.
- **M7** (closed-state alarm fatigue): `PORTAL_SUBMIT` status + `closedSystemAcknowledgedByAgency` boolean.

---

## 4. Alternative Architectural Challenge

### Eliminate the BFF: Single Core API with Client-Type Routing

Instead of a dedicated Mobile BFF service, the Core API serves both web and mobile clients directly. Mobile-specific behavior is handled via content negotiation (`Accept: application/vnd.hcare.mobile+json`) or a mobile-specific path prefix (`/api/v1/mobile/`). Push notification dispatch and offline sync reconciliation move into the Core API as distinct service classes (not a separate process).

**Pros:**
- Eliminates the BFF stateless/stateful contradiction (C1 above) at source — there is only one service, one database, one deployment unit.
- No cross-service JWT forwarding or internal HTTP calls (BFF → Core API).
- Single deployment artifact reduces operational overhead at small-agency scale where simplicity matters more than service decomposition.
- `PhiAuditLog` writes, sync deduplication, and shift conflict detection all happen in one transactional context — fewer distributed consistency edge cases.
- Eliminates the authentication forwarding gap (C2 above) — the single API validates JWT directly against the sync endpoint.

**Cons:**
- Mobile-specific code lives in the Core API, which grows in scope over time. As mobile features diverge from web, a single-API boundary becomes harder to maintain.
- Push notification dispatch and offline sync are operationally different concerns from core scheduling logic — co-location increases the blast radius of a mobile SDK upgrade or sync bug.
- The spec is already designed around a two-service architecture; changing to a single service requires revising the deployment model, architecture diagram, and all BFF-specific sections (Offline Sync Design, push dispatch).
- Makes future service extraction harder (though the scoring module boundary already demonstrates the pattern).

**Verdict:** Not the right call for P1 given how much design is already written around the BFF model. However, if the BFF's deduplication table resolves to "delegate to Core API" (the recommended fix for C1 above), the BFF becomes genuinely stateless and the architecture holds. If the team finds the two-service deployment overhead burdensome at small scale, collapsing to a single API is a clean fallback — the module boundaries already support it.

---

## 5. Minor Issues & Improvements

**M1 — Architecture diagram contradicts the single-source EVV rule.** Section 4's BFF box lists `- EVV pre-compute` as a BFF responsibility. Section 8 explicitly states "The Mobile BFF does not independently compute compliance status." The diagram label should read `- EVV payload assembly` (or similar) to reflect that the BFF is calling Core API and reformatting the result, not performing computation. Low risk but will confuse the developer implementing the BFF.

**M2 — `DailyComplianceSummary` is not updated on EVVRecord mutation.** The spec says the aggregate cache is "updated nightly + on each shift completion event." When a scheduler manually corrects a clock-out (turning a RED visit GREEN), the per-visit card updates immediately (correct — live Core API call). But the dashboard tile count ("3 RED, 1 YELLOW today") is stale until the next nightly run. The spec explicitly promises "update a clock-out and the card turns green immediately" — but the aggregate tile count does not green-out simultaneously. Add an `EVVRecord.updated` event that triggers a lightweight `DailyComplianceSummary` decrement/increment alongside the existing `ShiftCompleted` event listener.

**M3 — `RankedCaregiver` response type is undefined.** The `ScoringService` interface returns `List<RankedCaregiver>` but this type is never specified. The plain-language explanation ("2.8 miles away · worked with this client 6 times") needs to be generated somehow — either from per-factor scores attached to the return type, or from a template applied by the scoring engine. Define the structure in the spec: at minimum `caregiverId`, `totalScore`, `scoreBreakdown: Map<Factor, Double>`, and `explanationTokens: List<String>` (or equivalent). Without this, the web and mobile presentation layers will each invent their own explanation generation.

**M4 — Onboarding wizard EVV aggregator auto-selection flow is incomplete.** Step 2 of the onboarding wizard "selects EVV aggregator automatically based on state + payer type." But `PayerEvvRoutingConfig` is a payer-level entity that must be explicitly created. The wizard creates a `Payer` and then needs to create the corresponding `PayerEvvRoutingConfig` row (using `EvvStateConfig` defaults). This creation flow is not described. Additionally, multi-aggregator states (NY, FL) require selecting which MCO/payer uses which aggregator — a single auto-selection is insufficient. The wizard likely needs a confirmation step ("Your state uses Sandata for Medicaid FFS and HHAeXchange for MCO visits — does this match your payers?").

**M5 — No rate limiting or replay protection on `/sync/visits`.** A compromised or malfunctioning device could submit continuous large batches. For P1 at small-agency scale the risk is low, but the spec should at minimum note: "Rate limiting on `/sync/visits` (e.g., max 10 batch submissions per device per hour) is a P1 implementation concern, not a P2 deferral, to prevent runaway sync loops." The deduplication table mitigates data integrity issues but not resource exhaustion.

---

## 6. Questions for Clarification

1. **SyncBatch deduplication ownership (C1):** Is the preferred resolution to delegate deduplication to Core API (approach 1) or to introduce a shared Redis cache for the BFF (approach 2)? This is a deployment architecture decision with cost and complexity implications.

2. **`EvvStateConfig` initial population (C4):** Is the `evv-state-reference.md` file considered authoritative enough to drive the Flyway seed migration, or does the team want a separate validation pass before locking the data into migrations? This affects how quickly the P1 onboarding flow can be unblocked.

3. **JSON field query strategy (C5):** Will compliance status computation for MO task documentation (and similar state-specific fields in `stateFields`) read the JSON at the application layer only, or is there a plan to extract frequently-queried values to dedicated columns? The constraint is manageable now but should be decided before schema migrations are written.

4. **Family portal push notifications:** The spec notes "Device tokens for family portal push notifications (optional P2) use the same `clientId`-scoped payload constraint." Is this definitively P2, or is family portal push (visit started / caregiver en-route) being considered for P1? The push notification infrastructure (Expo) is already in the stack for caregiver notifications, so the marginal cost is low.

5. **BFF deployment topology:** Are the Core API and Mobile BFF deployed as separate containers/processes from day one, or does the team plan to run them in the same JVM initially and extract later? The stateless assertion and database ownership question (C1) changes materially based on this.

---

## 7. Final Recommendation

**Approve with targeted additions** — no architectural redesign required.

The five new critical issues are all fixable with spec additions:

1. **C1 (BFF dedup table):** Choose a deduplication ownership strategy (recommend Core API delegation) and remove the "holds no data of its own" claim or make it structurally true.
2. **C2 (SyncBatch JWT validation):** Add one sentence: JWT `sub` must match `caregiverId`; mismatch is a 403 + audit log event.
3. **C3 (shift generation race):** Add `@Version` to `RecurrencePattern`, same pattern as `Authorization`.
4. **C4 (EvvStateConfig population):** Specify Flyway seed migration from `evv-state-reference.md`; add graceful degradation for unconfigured states.
5. **C5 (JSON H2/PostgreSQL):** Add one-line constraint: JSON fields read/written as opaque strings; no database-level JSON path queries in P1.

Once these five items are addressed (all minor spec edits), the document is ready for `superpowers:writing-plans`.
