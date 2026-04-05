# hcare MVP Design — Critical Review #1
**Reviewer:** Senior Principal Architect (automated critical review)
**Date:** 2026-04-04
**Source:** `2026-04-04-hcare-mvp-design.md`
**Previous reviews:** None — this is the first review pass.

---

## 1. Overall Assessment

The design is well-structured and covers the core domain with uncommon thoroughness for an MVP spec — the EVV abstraction layer in particular is architecturally mature. The P1/P2 boundary is thoughtfully drawn. However, several issues range from **deployment traps** (multi-tenancy enforcement, authorization concurrency) to **design omissions that will block P1** (offline sync conflict resolution, shift generation trigger, HIPAA audit logging). Two issues — multi-tenancy enforcement and offline sync — are significant enough to warrant redesign before the implementation plan is written. The others are addressable with targeted additions to the spec.

---

## 2. Critical Issues

### C1 — Multi-tenancy is asserted, not enforced
**Description:** The spec states "No cross-agency data leakage is possible without an explicit join on agencyId." This is categorically false. JPA repositories that omit an `agencyId` predicate will return cross-tenant data. Spring Security sets the JWT context but that does not inject a filter into every Hibernate query.

**Why it matters:** A single missing predicate in any repository method leaks one agency's PHI to another. This is both a security vulnerability and a HIPAA breach risk. This class of bug is introduced silently — no compile error, often no test failure if tests use single-tenant fixtures.

**Actionable fix:** Add a Hibernate `@Filter` / `FilterDef` mechanism that automatically injects `agencyId = :currentAgency` on every entity. Set the filter parameter from the JWT context in a Spring interceptor or `EntityManager` request scope. All queries are then agency-scoped at the persistence layer, not the service layer. Alternatively, enforce via a custom `JpaRepository` base class that requires an `AgencyScoped` marker on all entities and validates queries include the tenant clause at test time. Either approach makes the guarantee structural, not behavioral.

---

### C2 — Offline sync is P1 but completely undesigned
**Description:** The mobile app is described as "Fully offline-capable — all visit data is stored locally and syncs on reconnect." The BFF is responsible for "offline sync reconciliation — accepts batched visit records from caregivers who worked offline, resolves conflicts, forwards to Core API." This is one sentence covering one of the hardest problems in distributed systems.

**Why it matters:** The gap is not theoretical. Home care caregivers work in rural areas with poor connectivity. When a caregiver clocks in offline and the scheduler reassigns that shift, or two caregivers both accept the same open shift while offline, the system has no defined behavior. Worse: EVV captures GPS and timestamps for compliance. An offline visit submitted hours later with device-local time has different compliance implications than a real-time submission. This is a P1 deliverable with zero design.

**Actionable fix:** Define a conflict resolution strategy explicitly. For EVV-critical data (clock-in/out, GPS, timestamps), device-captured time with a `capturedOffline: true` flag is the right approach — server time is less authoritative than the moment of care delivery. For shift assignment conflicts (caregiver accepts offline, then visit is reassigned), define server-wins with a notification to the caregiver. Document the `SyncBatch` payload schema: ordered events, device-local timestamps, sequence numbers. The BFF reconciler should be idempotent (same batch submitted twice produces the same result). This deserves its own spec section.

---

### C3 — Compliance status is computed in three inconsistent places
**Description:** The spec states compliance status is "computed on read, not stored." But then introduces: (a) `DailyComplianceSummary` — a nightly cache populated by a scheduled Spring job, and (b) the Mobile BFF "pre-computes EVV compliance status for the daily visit list." Three separate computation paths for the same value with different freshness guarantees.

**Why it matters:** The dashboard, the mobile today-view, and the detail view will show different statuses for the same visit depending on which path is hit. When a caregiver corrects a missing clock-out at 2pm, the scheduler's dashboard (reading from the nightly cache) still shows RED until midnight. This undermines the core value proposition: "ambient per-visit red/yellow/green — turns audit anxiety into daily operational confidence."

**Actionable fix:** Choose one authority. The simplest correct model: Core API computes compliance on read (already designed). The BFF's "pre-computation" is replaced with a lightweight API call that returns pre-formatted mobile payloads (not a separate computation). `DailyComplianceSummary` is used for aggregate counts on the dashboard tile ("3 RED, 1 YELLOW today") but individual visit statuses always query live. The nightly job updates the *aggregate counts*, not the per-visit status. This eliminates staleness without sacrificing dashboard load performance.

---

### C4 — Authorization usedUnits has an unaddressed concurrency race
**Description:** "Authorization.usedUnits updated in real-time when a Shift is completed." No locking strategy is defined.

**Why it matters:** If two caregivers complete shifts for the same client within seconds of each other (possible for a client with multiple daily visits), both reads see the same `usedUnits` value, both increment, one update is lost. The client's consumed hours are then understated, potentially allowing over-authorization — a Medicaid compliance issue, not just a data quality issue. Medicaid audits can claw back payments for over-authorized visits.

**Actionable fix:** Use optimistic locking (`@Version` on the Authorization entity). On concurrent update, the second transaction retries. For the small-agency scale (25 caregivers), contention will be rare and optimistic locking is sufficient. Note this explicitly in the spec.

---

### C5 — HIPAA / PHI compliance is entirely absent
**Description:** The spec contains zero mention of HIPAA, PHI, audit logging, at-rest encryption, or Business Associate Agreements (BAAs). Home care SaaS is a covered entity / business associate environment. Every data element in this domain model is PHI: diagnoses, medications, care plans, incident reports, GPS visit locations tied to a named patient.

**Why it matters:** Agencies accepting Medicaid are required to only use HIPAA-compliant software vendors. Missing audit logging is itself a HIPAA violation. The spec also routes PHI through a push notification provider (BFF dispatches push notifications for shift data) — any third-party push notification service that receives PHI requires a BAA. This is not a post-MVP concern; it affects architectural choices made now (e.g., what push provider to use, whether shift broadcast notifications include client name or just shift ID).

**Actionable fix:** Add a security/compliance section to the spec covering: (1) audit log entity for PHI read/write events (who accessed what, when), (2) note that push notification payloads must not include PHI — use a shift ID and let the app fetch details, (3) identify which cloud services will receive PHI and require BAAs (database hosting, push providers, geocoding APIs), (4) at-rest encryption strategy for PHI fields (PostgreSQL column-level encryption or application-layer encryption for highest-sensitivity fields).

---

### C6 — Shift generation timing and lifecycle are undefined
**Description:** "A client's recurring schedule creates individual Shift records on generation." The spec never defines: what triggers generation, how far ahead shifts are pre-generated, what happens when the RecurrencePattern changes, and whether there is a "generated through" date marker to prevent duplicate generation.

**Why it matters:** Without this definition, the calendar view and AI match engine cannot be built. If shifts are generated on-demand when the calendar is opened, a recurring client with 3 years of history generates thousands of records on first load. If shifts are generated nightly, the calendar can't show next week's schedule if it hasn't run. If pattern changes retroactively delete/modify generated shifts that already have EVV records attached, data integrity breaks.

**Actionable fix:** Define the generation model explicitly: generate on a rolling horizon (e.g., 8 weeks ahead), triggered on RecurrencePattern save and by a nightly background job. Pattern changes forward-generate only (don't touch shifts with EVVRecords). Add `RecurrencePattern.generatedThrough: LocalDate` to track the generation frontier. Include `Shift.sourcePatternId` (nullable) to distinguish generated vs. ad-hoc shifts.

---

## 3. Previously Addressed Items

None — this is the first review.

---

## 4. Alternative Architectural Challenge

### Event-Sourced Visit Ledger

Instead of mutable `EVVRecord` entities on `Shift`, model the visit lifecycle as an append-only event log:

```
VisitStartedEvent     { visitId, caregiverId, clientId, gpsLat, gpsLon, deviceTime, capturedOffline }
VisitNoteAddedEvent   { visitId, text, authorId }
VisitTaskCheckedEvent { visitId, taskId, result }
VisitEndedEvent       { visitId, gpsLat, gpsLon, deviceTime, capturedOffline }
```

Current EVVRecord state is a projection over the event stream. Compliance status is a derived view, never stored.

**Pros:**
- Offline sync is solved structurally: caregivers append events locally, upload a batch, server appends in order. No conflict resolution semantics needed — the event log is the truth.
- Natural HIPAA-compliant audit trail at zero additional cost (the log *is* the audit log).
- EVV rule changes re-evaluate history by replaying events through the new projection — trivially correct, no reprocessing jobs.
- The `capturedOffline` flag on events makes EVV compliance status for offline visits deterministic and auditable.
- Aligns naturally with the planned EVVTransmission at P2 — transmission is just replaying events to the aggregator.

**Cons:**
- Significantly higher complexity for the team. Event sourcing is a non-trivial pattern; getting projections right requires discipline.
- Query patterns (e.g., "show me all visits for a client this month") require either prebuilt read models or event replay — standard JPA queries don't apply to the event store.
- Axon Framework is the leading Java option but it's a major dependency addition. Rolling a custom event store is risky.
- Harder to explain to a non-technical co-founder or early customer in a demo ("what's in the database?").

**Verdict:** Not the right call for P1. However, the *offline event capture* concept — capturing visit actions as discrete events on the mobile device even if the final storage is a mutable record — is worth adopting as an implementation strategy for the BFF's sync reconciler without going full event sourcing.

---

## 5. Minor Issues & Improvements

**M1 — Geocoding service is a missing dependency.** The scoring model uses "driving distance; geocoded client address vs. caregiver home." No geocoding service is mentioned in the tech stack. Google Maps API, OpenStreetMap/Nominatim, and Mapbox all have different cost profiles, rate limits, and BAA availability. The spec should name the chosen provider and note that client/caregiver addresses must be geocoded at save time (not at match time) to hit the sub-100ms goal. Driving distance vs. haversine (straight-line) also needs to be decided — haversine is faster and sufficient for small-agency density.

**M2 — FamilyPortalUser authentication model is absent.** Section 6 describes the family portal's UX but there is no auth design. Family portal users are not agency users; they presumably use email+password or magic link, have no `agencyId` JWT claim, and must be constrained to exactly one client. The JWT claim structure for family portal tokens needs to be defined, and it must be enforced separately from the agency RBAC middleware.

**M3 — Feature flag strategy is mentioned in CLAUDE.md but absent from the design.** The architecture principles say "Feature flags — new behaviour is gated behind flags." The monetization tier boundary (e.g., AI scheduling is Pro-only) needs a flag strategy. Without it, the tier boundary becomes a hardcoded conditional spread across the codebase. Define a simple in-DB flag model at P1 so tier enforcement is consistent and auditable.

**M4 — Credential verification workflow is undefined.** `Credentials.verified` is a boolean set by... who? How? A manual admin toggle is fine for MVP but should be stated explicitly. The hard filter in AI scheduling depends on this flag being trustworthy. If an admin can toggle it without evidence, the hard filter provides false confidence.

**M5 — Virtual thread + HikariCP configuration trap.** CLAUDE.md specifies virtual threads for blocking I/O. Spring Boot 3.2+ with Tomcat auto-configures virtual threads via `spring.threads.virtual.enabled=true`. However, HikariCP defaults to a pool of 10 connections, which becomes a bottleneck with virtual threads (many threads park waiting for connections). The spec should note that when virtual threads are enabled, the HikariCP `maximum-pool-size` should be tuned (or an unbounded pool considered). This is a deployment gotcha that bites on first load test.

**M6 — Per-client state override not supported.** The EVV routing model is per-agency-per-payer. A small agency on a state border (e.g., Kansas City metro serving both MO and KS clients) has clients requiring different aggregators based on the *client's* state, not the agency's state. A `Client.serviceState` field (nullable, defaults to agency state) would allow per-client routing overrides without redesigning the aggregator selection logic.

**M7 — `ClosedSystemConnector` YELLOW status is user-hostile.** Closed-state agencies (MD, SC, OR, etc.) will see every EVV visit as YELLOW forever. The spec's rationale is correct but the experience for a Maryland Medicaid agency is that 100% of their visits are permanently yellow. Consider a `EvvStateConfig.closedSystemAcknowledgedByAgency` flag: once an agency acknowledges the closed-system limitation, YELLOW is suppressed for closed-state visits and replaced with a neutral "submitted via state portal" indicator. This prevents alarm fatigue without hiding real compliance issues.

---

## 6. Questions for Clarification

1. **Database hosting:** Which PostgreSQL provider is targeted for production? Some PaaS options (RDS, Cloud SQL, Supabase) have different BAA availability, and this affects the HIPAA compliance architecture.

2. **Push notification provider:** FCM/APNs directly, or an abstraction layer (OneSignal, Expo Notifications)? This has PHI implications (see C5) and affects what the BFF's push notification module looks like.

3. **AI scheduling tier boundary:** Is the *entire* match scoring engine gated behind the Pro tier, or do Starter-tier agencies get hard-filter-only results (i.e., a list of eligible caregivers without ranking)? The design should reflect this explicitly.

4. **CarePlan clinical ownership:** For skilled nursing (HHCS) agencies, are care plans authored by the agency admin, by a supervising clinician role, or imported from an EHR? The current model has no `clinicianId` or review/signature workflow on `CarePlan`, which may be a regulatory requirement for HHCS (as opposed to non-medical PCS).

5. **Shift generation horizon:** How far ahead should shifts be pre-generated? 4 weeks? 8 weeks? This affects storage costs and whether the calendar can show future availability to schedulers.

6. **Multi-location agencies:** The spec explicitly defers multi-location support to P2. Is a single-location constraint enforced at signup, or is it a soft limit? A small agency that opens a second location mid-subscription needs a graceful upgrade path, not a hard error.

---

## 7. Final Recommendation

**Major revisions needed** before the implementation plan is written.

The two blocking issues:
- **C1 (multi-tenancy enforcement):** Must be redesigned at the persistence layer, not asserted at the service layer. This affects every repository in the system and is easier to get right before any code is written.
- **C2 (offline sync):** Requires its own design section. This is a P1 mobile feature with compliance implications (EVV offline capture) and zero current design. Implementing it without a spec produces a buggy sync engine that will be rewritten.

The remaining critical issues (C3–C6) can be addressed with targeted spec additions — none require architectural changes.

HIPAA compliance (C5) in particular should be treated as a first-class design input, not an afterthought: it affects which services require BAAs, what goes in push notification payloads, and what the audit logging entity looks like.

Once C1 and C2 are redesigned and C3–C6 are addressed with spec additions, the document is ready for `superpowers:writing-plans`.
