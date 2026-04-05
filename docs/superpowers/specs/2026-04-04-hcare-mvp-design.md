# hcare MVP Design Spec
**Date:** 2026-04-04
**Status:** Draft — revised after critical-review-1 (all issues addressed) and critical-review-2 C2/C3/C4 (3 of 5 critical issues addressed)

---

## 1. Product Vision

hcare is a home care agency management SaaS platform targeting small agencies (1–25 caregivers). It delivers a complete operational platform — scheduling, caregiver mobile app, client management, and EVV compliance — with a self-serve onboarding model and pricing transparency that the incumbent platforms (WellSky, AlayaCare, AxisCare) do not offer at this market segment.

The two primary differentiators beyond core functionality:
- **AI-assisted scheduling** — smart caregiver match recommendations that surface the right person for each shift without the scheduler scanning a full list
- **Ambient EVV compliance status** — per-visit red/yellow/green indicator that turns audit anxiety into daily operational confidence

AxisCare is the UX benchmark. The goal is to match or exceed their experience while being meaningfully simpler to onboard and more accessible to price.

---

## 2. Target Market & Competitive Positioning

### Target
- Small home care agencies, 1–25 caregivers
- Both non-medical personal care (PCS) and skilled nursing / medical home health (HHCS)
- US market, all states

### Competitive differentiation

| Dimension | AxisCare | hcare |
|---|---|---|
| Target size | Mid-market to enterprise | Small agencies — intentional beachhead |
| Onboarding | Sales-led, no self-serve | Fully self-serve — no sales call to start |
| Pricing | Per-active-client tiers, custom quote, ~$200/mo min | Flat tiers, transparent, free trial/freemium |
| AI scheduling | Weighted constraint matching ("AxisCare Intelligence") | Same approach, small-agency UX |
| EVV dashboard | Reporting module, not ambient | Ambient per-visit red/yellow/green — differentiator |
| Caregiver wellness | Caribou Rewards (3rd party) | Not in MVP |
| Mileage tracking | Manual entry | Not in MVP (open differentiator for future) |
| Family portal | Functional, less polished | Table-stakes clean implementation |

### P2 competitive threats
- AxisCare's referral/CRM tools are strong for SMB — competing there is P2
- AlayaCare's agentic AI scheduling (AlayaFlow) is enterprise-grade overkill for small agencies — not a near-term threat at this market size

---

## 3. Tech Stack

| Layer | Technology |
|---|---|
| Core Backend API | Spring Boot 3.x latest GA, Java 25 |
| Mobile BFF | Spring Boot 3.x latest GA, Java 25 (lightweight adapter) |
| Web Admin Frontend | React (TypeScript) |
| Caregiver Mobile App | React Native |
| Database | H2 in-memory (development only) → PostgreSQL (required for any production deployment) |
| Auth | Spring Security + JWT |
| Geocoding | Haversine (straight-line) for AI scoring distance; Google Maps Geocoding API for address → lat/lng (addresses geocoded at save time, not at match time). Google Maps requires a BAA for HIPAA compliance; alternatively Mapbox or a self-hosted Nominatim instance can satisfy the BAA requirement. |
| Push Notifications | Firebase Cloud Messaging (FCM) + APNs via Expo Notifications (React Native SDK). Push payloads must **never** include PHI — payloads carry only a `shiftId` or `visitId`; the app fetches details after receipt. Expo requires a BAA for HIPAA compliance. |

### Stack rationale
- Spring Boot 3.x + Java 25 (LTS): type safety, rich ecosystem, strong migration path to PostgreSQL and eventual service extraction
- Separate Mobile BFF: protects the premium React Native experience — handles push notification routing, offline sync reconciliation, mobile-optimized payloads — without polluting core business logic
- H2 → PostgreSQL: H2 is for local development only. Any production deployment uses PostgreSQL. JPA/Hibernate abstraction means the switch is a datasource configuration change; schema uses no H2-specific features.
- React Native: native-quality mobile experience from a single codebase; priority given small agencies where caregivers' phone experience is a daily touchpoint
- Virtual threads: enabled via `spring.threads.virtual.enabled=true` (Spring Boot 3.2+). HikariCP `maximum-pool-size` must be tuned to at least 20–50 (default 10 becomes a bottleneck when many virtual threads park waiting for connections). Configure in `application-prod.yml`; default dev H2 config is unaffected.

---

## 4. System Architecture

```
┌──────────────────────────────────────────────────────┐
│                    hcare Platform                     │
│                                                      │
│  ┌──────────────┐          ┌─────────────────────┐  │
│  │  React Web   │          │  React Native App   │  │
│  │  (Admin UI)  │          │  (Caregiver Mobile) │  │
│  └──────┬───────┘          └──────────┬──────────┘  │
│         │ REST/JSON                   │ REST/JSON    │
│         │                             │              │
│  ┌──────▼────────────────┐  ┌─────────▼──────────┐  │
│  │   Core API            │  │   Mobile BFF       │  │
│  │   Spring Boot 3.x     │◄─│   Spring Boot 3.x  │  │
│  │   Java 25             │  │   Java 25          │  │
│  │                       │  │  - push notifs     │  │
│  │  - Scheduling         │  │  - offline sync    │  │
│  │  - Auth / Users       │  │  - EVV pre-compute │  │
│  │  - Clients/CarePlans  │  │  - mobile payloads │  │
│  │  - EVV logic          │  └────────────────────┘  │
│  │  - AI match engine    │                          │
│  │  - Scoring module     │                          │
│  └──────────┬────────────┘                          │
│             │                                        │
│      ┌──────▼──────┐                                │
│      │  H2 (dev)   │  → PostgreSQL (P2/prod)        │
│      └─────────────┘                                │
└──────────────────────────────────────────────────────┘
```

**Core API** owns all business logic, domain rules, and data. It is the system of record.

**Mobile BFF** is a stateless thin adapter. It holds no data of its own. Responsibilities:
- Push notification dispatch (open shift broadcasts, clock-in reminders)
- Offline sync reconciliation — accepts batched visit records from caregivers who worked offline, resolves conflicts, forwards to Core API
- Pre-computes EVV compliance status for the daily visit list (avoids N+1 on mobile)
- Returns mobile-optimized payloads (smaller, flatter responses than the full API)

**Auth** is handled by the Core API (Spring Security + JWT). The BFF validates and forwards tokens — it is not an auth surface.

**Multi-tenancy** is row-level: every entity carries an `agencyId`. Tenant isolation is enforced at the persistence layer — not at the service layer — using a Hibernate `@FilterDef` / `@Filter` named `agencyFilter` that automatically injects `agencyId = :currentAgency` into every entity query. A Spring `HandlerInterceptor` sets the filter parameter from the JWT before any repository call and clears it after. This makes cross-agency leakage a framework-prevented error, not a developer discipline issue. A unit test on `BaseRepository` verifies the filter is active on all agency-scoped entities. No cross-agency data access is possible without explicitly disabling the filter (permitted only in admin/migration contexts, audited).

---

## 5. Domain Model

All entities are agency-scoped (implicit `agencyId` on every table).

### Core entities

```
Agency
  ├── Users                        role: ADMIN | SCHEDULER
  ├── FamilyPortalUsers            read-only, scoped to one Client
  ├── Payers                       Medicaid | PrivatePay | LTCInsurance | VA | Medicare
  ├── ServiceTypes                 personal care, skilled nursing, etc.
  │
  ├── Caregivers
  │     ├── Credentials            HHA, CNA, RN, CPR, etc. — with expiry + verified flag
  │     ├── BackgroundChecks       StateRegistry, FBI, OIG — result + renewal date
  │     ├── Availability           recurring weekly availability blocks
  │     └── CaregiverScoringProfile  (pre-computed — see AI Scheduling)
  │           └── CaregiverClientAffinity[]  visitCount per client
  │
  ├── Clients
  │     ├── Diagnoses              ICD-10 code, onset date
  │     ├── Medications            name, dosage, route, schedule, prescriber
  │     ├── CarePlans              versioned; one active plan at a time
  │     │     ├── ADLTasks         name, instructions, assistanceLevel, frequency
  │     │     └── Goals            description, targetDate, status
  │     ├── Authorizations         payer, serviceType, authorizedUnits, usedUnits,
  │     │                          startDate, endDate, authNumber
  │     │                          @Version optimisticLock (concurrent shift completions)
  │     ├── FamilyPortalUsers
  │     ├── serviceState           nullable; overrides agency state for EVV routing
  │     │                          (supports border-county agencies serving 2 states)
  │     └── Documents
  │
  ├── RecurrencePatterns           generate individual Shifts on rolling 8-week horizon
  │     ├── generatedThrough       LocalDate tracking the generation frontier
  │     └── (pattern changes only affect future unstarted shifts)
  │
  ├── Shifts
  │     ├── sourcePatternId        nullable FK to RecurrencePattern (null = ad-hoc shift)
  │     ├── EVVRecord              6 federal elements + verificationMethod +
  │     │                          complianceStatus (computed) + stateFields (JSON) +
  │     │                          capturedOffline (boolean) + deviceCapturedAt (timestamp)
  │     ├── ShiftOffers            broadcast record: caregiver, offeredAt,
  │     │                          respondedAt, response (ACCEPTED|DECLINED|NO_RESPONSE)
  │     └── ADLTaskCompletions     caregiver check-off per task during visit
  │
  ├── IncidentReports
  ├── CommunicationMessages
  └── Documents                   ownerType: CLIENT | CAREGIVER

── P2 stubs (schema designed now, not built) ──
  ├── Claims / Invoices
  ├── PayrollRuns
  ├── EVVTransmissions             state system submission records
  ├── ReferralLeads
  └── CustomForms / Assessments
```

### Key design decisions

- **Payer is P1** even though billing is P2. EVV compliance rules vary by payer (Medicaid vs. private pay), and authorization utilization is an operational scheduling concern, not just a billing concern.
- **Authorization tracks usedUnits in real time** — updated when a Shift is completed. Schedulers see remaining hours on the dashboard without running a report. Concurrent completions are handled via JPA `@Version` optimistic locking; the second transaction retries on conflict.
- **CarePlan is versioned and standalone** — not embedded in Client. Skilled nursing care plans are clinical documents that get reviewed and revised; version history is required. HHCS (skilled nursing) care plans carry a `reviewedByClinicianId` and `reviewedAt` to satisfy state-level clinical supervision requirements; non-medical PCS plans leave these null.
- **EVVRecord.stateFields is JSON** — extensible for state-specific extra elements (e.g., Missouri requires task-level documentation) without schema changes.
- **CaregiverScoringProfile is pre-computed** — see AI Scheduling section. Never computed at match-request time.
- **RecurrencePattern generates Shifts** on a rolling 8-week horizon. Generation is triggered on pattern save and by a nightly background job that advances the `generatedThrough` frontier. Pattern edits forward-generate only — shifts with an attached EVVRecord (visit started or completed) are never modified by a pattern change. `Shift.sourcePatternId` links generated shifts to their parent pattern. `RecurrencePattern` carries a JPA `@Version` field for optimistic locking — if a pattern save and the nightly job trigger generation simultaneously, the second transaction retries and finds `generatedThrough` already advanced, generating no duplicate shifts.
- **Credential verification** is a manual admin action at P1 (admin sets `verified = true` with an optional notes field). This is explicitly an MVP constraint. The hard filter in AI scheduling trusts this flag; agencies bear responsibility for its accuracy. Automated third-party credential verification (state registry API, OIG API) is P2.
- **Feature flags** gate tier-level features. A `FeatureFlags` entity (per-agency) stores which features are enabled. The Pro-tier AI scheduling match engine checks `featureFlags.aiSchedulingEnabled` and degrades gracefully to an unranked eligible-caregiver list for Starter-tier agencies. This prevents hardcoded tier conditionals spread across the codebase.

---

## 6. MVP Feature Modules (P1)

### Agency Admin Web App (React)

**Command-Center Dashboard**
Single screen showing the operational state of the day: visits in-progress, upcoming, missed, and uncovered. Per-visit EVV compliance status (red/yellow/green). Authorization utilization warnings (clients approaching their authorized hour limit). Alerts for late clock-ins and expiring caregiver credentials. No drilling into reports to understand the day's health.

**Scheduling**
- Calendar view (day/week) with drag-and-drop shift assignment
- RecurrencePattern setup for ongoing clients
- AI match suggestions when assigning a caregiver (ranked list with plain-language explanation per candidate)
- Open shift broadcast: one tap notifies all eligible caregivers via push notification
- ShiftOffer tracking: who was notified, who responded, audit trail

**Clients**
Profile, care plan (ADLs, goals), diagnoses, medications, payer assignment, authorization tracking, documents, family portal access management.

**Caregivers**
Profile, credentials with expiry alerts, background check status, availability, shift history.

**Payers & Authorizations**
Payer setup (type, state, EVV aggregator config). Authorization creation and real-time utilization tracking.

**EVV Status**
Per-visit compliance indicator accessible from the dashboard and shift detail view. Explanation of each violation. No report required to see current compliance state.

---

### Caregiver Mobile App (React Native)

**Today's Schedule**
List of upcoming shifts: client name, address (tap for maps navigation), service type, care plan summary.

**Visit Execution**
Clock in (GPS-captured), ADL task checklist, care notes entry, clock out. Fully offline-capable — all visit data is stored locally and syncs on reconnect. No connectivity required during a visit.

**Open Shifts**
Push notification when an unassigned shift is broadcast by the scheduler. One-tap accept from the notification.

**Profile**
Upcoming credential expiration dates, shift history, assigned shifts calendar.

---

### Offline Sync Design

The caregiver app captures all visit events locally (SQLite via Expo SQLite) as an ordered, append-only event log. Connectivity is not required during a visit.

**`SyncBatch` payload** (POSTed to BFF `/sync/visits` on reconnect):
```
SyncBatch
  ├── deviceId            unique device identifier
  ├── caregiverId
  ├── submittedAt         device-local ISO timestamp at batch upload time
  └── events[]
        ├── eventType     CLOCK_IN | TASK_CHECKED | NOTE_ADDED | CLOCK_OUT
        ├── visitId       client-generated UUID (created offline)
        ├── occurredAt    device-local ISO timestamp of the event
        ├── capturedOffline  always true in a SyncBatch
        └── payload       event-specific fields (GPS, taskId, noteText, etc.)
```

**Conflict resolution rules:**

| Scenario | Resolution |
|---|---|
| Shift was reassigned while caregiver was offline | Server-wins: BFF rejects the `visitId` with `CONFLICT_REASSIGNED`. Caregiver sees a notification explaining the shift was reassigned. EVV record is not created. |
| Duplicate batch upload (same events, same `visitId`) | Idempotent: BFF deduplicates by `(visitId, eventType, occurredAt)`. Safe to retry. |
| Clock-in time anomaly (>4 hours before scheduled start) | Accepted but EVV compliance status set to YELLOW (time anomaly). Scheduler sees a flag. |
| Missing clock-out (app crashed before clock-out) | EVV record created with `timeOut = null` → RED status. Scheduler can manually add clock-out time with an audit note. |
| Two caregivers accept same open shift offline | First sync to arrive wins (server assigns on first receipt); second sync receives `CONFLICT_ALREADY_ASSIGNED`. |

**EVV for offline visits:** `EVVRecord.capturedOffline = true`, `deviceCapturedAt` stores the device timestamp. For compliance purposes, `deviceCapturedAt` is the authoritative time of service (not the BFF receipt time). Aggregators that require real-time submission (NJ, NY) will flag offline-captured records — the YELLOW status explanation text informs the scheduler.

**Authentication & authorization:** The `/sync/visits` endpoint requires a valid caregiver JWT. The `caregiverId` field in the `SyncBatch` payload must match the `sub` claim of the JWT. If they differ, the BFF returns HTTP 403 and writes a `UNAUTHORIZED_SYNC_ATTEMPT` entry to `PhiAuditLog`. The JWT is the authoritative identity source; the payload field is a belt-and-suspenders check only.

**Idempotency:** The BFF delegates deduplication to the Core API — it forwards each `SyncBatch` to `POST /api/v1/internal/sync/batch`, which deduplicates by `(deviceId, visitId, eventType, occurredAt)` inside its own database (30-day TTL). The BFF itself holds no state, which preserves its ability to run as multiple stateless replicas. Safe to retry the full batch on failure.

---

### Family Portal (React — lightweight)

Separate login from agency users. Read-only. Shows: caregiver en-route / clocked-in / completed status for today's visit, post-visit care notes summary, upcoming schedule, caregiver profile card (name, photo). No access to billing, agency operations, or other clients.

**Family Portal auth model:** Family portal users authenticate via email + magic link (no password). On successful auth, the Core API issues a JWT with claim `{"role": "FAMILY_PORTAL", "clientId": "<uuid>", "agencyId": "<uuid>"}`. The `clientId` claim is the hard scope boundary — all API endpoints under `/api/v1/family/` verify this claim and reject requests for any resource not belonging to that specific client. Family portal tokens are separate from agency user tokens; they cannot be used on agency endpoints and vice versa. Device tokens for family portal push notifications (optional P2) use the same `clientId`-scoped payload constraint.

---

### Self-Serve Onboarding

Signup → guided wizard:
1. Agency profile (name, state, care types)
2. Add first payer(s) — selects EVV aggregator automatically based on state + payer type
3. Import caregivers (CSV or manual entry)
4. Import clients (CSV or manual entry)
5. First shift — scheduler is walked through creating and assigning their first shift
6. Mobile app invite sent to caregivers via SMS

Target: agency fully operational in under one hour, no sales call, no implementation consultant.

---

## 7. AI Scheduling Engine (Delighter #7)

### Approach
Weighted multi-factor scoring model (not ML). Same level of sophistication as AxisCare Intelligence — sufficient for small agencies and fast to build. Clean interface boundary designed for ML swap-in at P2 once visit history data exists.

### Scoring model

Hard filters applied first (any failure = candidate excluded):
- No conflicting shifts in availability
- All credentials required by ServiceType are current and verified
- Client has remaining authorized hours under relevant payer
- Caregiver not on OIG exclusion list

Scored candidates ranked by weighted sum:

| Factor | Weight | Logic |
|---|---|---|
| Distance to client | 30% | Haversine (straight-line) distance; geocoded client address vs. caregiver `homeLatLng`. Client/caregiver addresses geocoded at save time (not at match time) via Geocoding API. Haversine is sufficient for small-agency density and avoids per-request Maps API cost. |
| Continuity | 25% | Prior visit count with this client (from CaregiverClientAffinity) |
| Overtime risk | 20% | Penalize if assignment would push caregiver into OT this week |
| Client preferences | 15% | Language match, gender preference, pet/allergy flags |
| Reliability | 10% | Shift completion rate (from CaregiverScoringProfile.cancelRate) |

### Output
Top-ranked candidates returned with a plain-language explanation per suggestion: "2.8 miles away · worked with this client 6 times · no overtime risk."

### Pre-computed scoring profiles

`CaregiverScoringProfile` stores slow-to-compute signals. Updated asynchronously via Spring events when shifts complete or cancel — never computed on the request path.

```
CaregiverScoringProfile
  ├── caregiverId
  ├── cancelRateLast90Days
  ├── currentWeekHours
  ├── homeLatLng
  └── CaregiverClientAffinity[]
        ├── clientId
        └── visitCount
```

At match time: hard filters (indexed DB queries) + score lookup (pre-computed). Result: sub-100ms regardless of visit history volume.

### Module boundary

All scoring logic lives in `com.hcare.scoring`. Public surface is a single interface:

```java
public interface ScoringService {
    List<RankedCaregiver> rankCandidates(ShiftMatchRequest request);
    void onShiftCompleted(ShiftCompletedEvent event);
    void onShiftCancelled(ShiftCancelledEvent event);
}
```

Nothing outside this package queries scoring tables directly. Spring async events drive updates. When extracted to a microservice: swap `LocalScoringServiceImpl` for `HttpScoringServiceClient`, point events at a broker. No logic changes.

---

## 8. EVV Compliance System (Delighter #9)

### Overview
Per-visit ambient compliance status, computed from EVVRecord data. No report required. Rules are state- and payer-specific, configurable in the database — rule changes don't require a redeploy.

### Abstraction architecture: three layers

**Layer 1 — `EvvStateConfig` (DB-driven)**

One row per state. Bootstrapped by a Flyway seed migration (`V2__evv_state_config_seed.sql`) containing all 50-state configurations, sourced from `docs/superpowers/evv-state-reference.md`. This migration runs on application startup before any business logic executes. `PayerEvvRoutingConfig` rows for multi-aggregator states (NY, FL, NC, VA, TN, AR) are seeded in the same migration. If a state row is missing for any reason, the onboarding wizard degrades gracefully ("Your state's EVV configuration is pending — contact support") rather than silently assigning a null aggregator.

Configures:
- `defaultAggregator` (enum: SANDATA, HHAEXCHANGE, AUTHENTICARE, CAREBRIDGE, NETSMART, THERAP, STATE_BUILT, CLOSED)
- `systemModel` (OPEN | CLOSED | HYBRID)
- `allowedVerificationMethods` (set: GPS, TELEPHONY_LANDLINE, TELEPHONY_CELL, FIXED_DEVICE, FOB, BIOMETRIC)
- `gpsToleranceMiles` (nullable — published for NE, KY, AR only; null = vendor-level enforcement)
- `requiresRealTimeSubmission` (boolean — NJ, NY)
- `manualEntryCapPercent` (nullable — HI: 15%)
- `coResidentExemptionSupported` (boolean)
- `extraRequiredFields` (JSON — e.g., MO task documentation)
- `complianceThresholdPercent` (nullable — PA: 85%, MI: 85%, FL: 85%)
- `closedSystemAcknowledgedByAgency` (boolean, default false) — once an admin acknowledges the closed-system limitation, YELLOW is suppressed for visits where the only issue is `systemModel = CLOSED`, replaced with neutral `PORTAL_SUBMIT` status. Real compliance issues (missing elements, no clock-out) still show RED/YELLOW.

**Layer 2 — `PayerEvvRoutingConfig` (handles multi-aggregator states)**

One row per payer where the MCO/payer mandates a specific aggregator different from the state default. Covers NY (3 aggregators), FL (FFS vs. MCO), NC (FFS vs. MCO), VA (MCO-specific), TN, AR.

Aggregator selection at submission:
1. Check `PayerEvvRoutingConfig` for payer-specific override
2. Fall back to `EvvStateConfig.defaultAggregator`

**Layer 3 — `EvvAggregatorConnector` interface**

```java
public interface EvvAggregatorConnector {
    AggregatorType supports();
    EvvSubmissionResult submit(NormalizedEvvRecord record, EvvStateConfig config);
    EvvValidationResult preValidate(NormalizedEvvRecord record, EvvStateConfig config);
}
```

P1 implementations (interface + validation only — actual aggregator submission is P2):
- `SandataConnector` — implements `preValidate` fully; `submit` is a no-op stub that logs and returns PENDING
- `HHAeXchangeConnector` — same approach
- `ClosedSystemConnector` — no-op for both; surfaces YELLOW status reminding agency to use state portal

This means P1 ships full compliance visibility (green/yellow/red per visit) without yet transmitting to state systems. Actual aggregator submission is wired in P2 when `EVVTransmission` is implemented. The interface contract is identical — P2 only fills in the `submit` implementations.

P2 connector implementations:
- `SandataConnector.submit`, `HHAeXchangeConnector.submit` (completing the P1 stubs)
- `AuthentiCareConnector`, `CareBridgeConnector`, `NetsmartConnector`, `TheraphConnector`
- State-built connectors (OR eXPRS, MD LTSSMaryland, AZ AHCCCS, MO EAS, LA LaSRS)

### `EVVRecord` vs `NormalizedEvvRecord`

`EVVRecord` is the JPA database entity — persisted as a child of `Shift`. It stores the raw captured data (GPS coordinates, timestamps, verification method, state-specific JSON fields).

`NormalizedEvvRecord` is a connector-layer value object (DTO) — constructed from `EVVRecord` + `Shift` + `Client` context at submission time. It is never persisted. Each `EvvAggregatorConnector` maps a `NormalizedEvvRecord` to its own wire format.

### `NormalizedEvvRecord` — internal canonical format

```
NormalizedEvvRecord
  ├── visitId
  ├── serviceType                  (federal element 1)
  ├── clientMedicaidId             (federal element 2)
  ├── dateOfService                (federal element 3)
  ├── locationLat / locationLon    (federal element 4)
  ├── caregiverId                  (federal element 5)
  ├── timeIn / timeOut             (federal element 6)
  ├── verificationMethod           (GPS | TELEPHONY_LANDLINE | TELEPHONY_CELL |
  │                                 FIXED_DEVICE | FOB | BIOMETRIC | MANUAL)
  ├── coResident                   suppresses EVV for live-in caregiver exemptions
  ├── stateFields                  (JSON — MO tasks, program codes, etc.)
  └── complianceStatus             (computed on read — see below)
```

Each `EvvAggregatorConnector` maps `NormalizedEvvRecord` to its own wire format. Internal record shape never changes when states change their format.

### Compliance status computation

```
EXEMPT        → coResident = true, OR payer type = PRIVATE_PAY
GREEN         → all 6 elements present
                + verificationMethod in state's allowedVerificationMethods
                + GPS within tolerance (if published for state)
                + timeIn and timeOut both recorded
YELLOW        → all elements present, but one exception:
                GPS outside tolerance | manual override used |
                time anomaly (>30 min from scheduled) |
                systemModel = CLOSED AND closedSystemAcknowledgedByAgency = false
PORTAL_SUBMIT → systemModel = CLOSED AND closedSystemAcknowledgedByAgency = true
                (all required elements present; agency must submit via state portal)
RED           → any required element missing | no clock-out | visit missed without
                documentation
GREY          → visit not yet started
```

Status is computed on read by the Core API — it is the single authority for compliance status. It is never stored, never pre-computed in the BFF, and never duplicated. When state rules change, update `EvvStateConfig` — history is instantly re-evaluated without reprocessing jobs.

**Single source of truth rule:** The Mobile BFF does not independently compute compliance status. It calls Core API `GET /api/v1/visits/today?mobile=true` which returns pre-formatted mobile payloads with compliance status already resolved. The BFF is a payload adapter, not a computation layer.

### Dashboard aggregate cache

The command-center dashboard header tile ("3 RED, 1 YELLOW today") is an aggregate count, not a per-visit status. A scheduled Spring job maintains a `DailyComplianceSummary` per agency (updated nightly + on each shift completion event). The tile reads from this cache. Individual visit cards on the dashboard call the live Core API endpoint — they are never served from the cache. This preserves the "update a clock-out and the card turns green immediately" experience while keeping the dashboard tile fast.

### P2 path

`NormalizedEvvRecord` feeds directly into the billing pipeline at P2. Same data, new consumer. EVVTransmission entity (state system submission record) is schema-stubbed at P1, not implemented.

---

## 9. HIPAA / Security Compliance

hcare handles Protected Health Information (PHI) under HIPAA. Every data element in the domain model — diagnoses, medications, care plans, GPS visit locations, incident reports — is PHI. The platform operates as a Business Associate (BA) to home care agencies (Covered Entities).

### PHI audit logging

A `PhiAuditLog` entity records every PHI read and write:

```
PhiAuditLog
  ├── userId / familyPortalUserId / systemJobId  (who accessed)
  ├── agencyId
  ├── resourceType      (CLIENT | CAREPLAN | EVVRECORD | MEDICATION | ...)
  ├── resourceId
  ├── action            (READ | WRITE | DELETE | EXPORT)
  ├── occurredAt
  └── ipAddress / userAgent
```

Audit logs are append-only (no update or delete endpoints). Retained for 6 years per HIPAA minimum. Stored in a separate schema partition from operational data to prevent accidental joins.

### PHI in transit and at rest

- All API traffic over TLS 1.2+ (enforce HSTS in production).
- Push notification payloads: **must never include PHI**. Payloads carry only a `shiftId` or event type code; the app fetches detail after receipt.
- PostgreSQL at-rest encryption via cloud provider disk encryption (AWS RDS, Google Cloud SQL, or Azure Database for PostgreSQL — all offer BAA-eligible instances). Column-level encryption for highest-sensitivity fields (SSN if stored, Medicaid ID) is P2.
- H2 dev database contains only synthetic/test data. Developers must not load real PHI into local environments.

### Third-party BAA requirements

| Service | PHI exposure | BAA required |
|---|---|---|
| PostgreSQL hosting (AWS RDS / Cloud SQL) | All PHI | Yes |
| Geocoding (Google Maps / Mapbox) | Client addresses (quasi-PHI) | Yes (or use Nominatim self-hosted to avoid) |
| Push notifications (Expo / FCM) | Shift IDs only (no PHI in payload) | BAA from Expo required regardless |
| Error monitoring (e.g., Sentry) | Must be configured to scrub PHI from stack traces | Yes |

### Access control summary

| Role | Scope |
|---|---|
| ADMIN | Full agency — all clients, caregivers, reports |
| SCHEDULER | Full agency — scheduling and client view; no user management |
| CAREGIVER | Own assigned shifts only; own profile |
| FAMILY_PORTAL | Single client (hard-scoped by JWT `clientId` claim) — read-only |

All endpoints require an authenticated JWT. Endpoints accessible without auth must be explicitly annotated `@Public` (per existing convention in CLAUDE.md).

---

## 10. Monetization

**Model:** Flat monthly tiers with a free trial / freemium entry point. Transparent pricing, no sales call required.

**Tier structure (TBD — pricing research needed):**
- Free tier: limited caregivers/clients, no EVV compliance features — proof of concept for very small agencies
- Starter: full EVV compliance, scheduling, mobile app, family portal
- Pro: AI scheduling suggestions, advanced reporting, priority support

**AxisCare contrast:** Quote-based, ~$200/month minimum, requires sales contact. hcare's self-serve model is itself a differentiator.

---

## 11. Key Risks & Open Questions

| Risk | Mitigation |
|---|---|
| State EVV rules change frequently | `EvvStateConfig` is DB-driven — rule updates without redeploys |
| H2 → PostgreSQL migration at scale | JPA/Hibernate abstraction + no H2-specific features used |
| Scoring module complexity grows | Modular boundary enforced from day one — extract when ready |
| Closed-state agencies (MD, SC, OR, KS, SD) | `PORTAL_SUBMIT` status (not YELLOW) once agency acknowledges; full connector support deferred to P2 |
| California IHSS | Separate integration, no third-party API path — explicitly out of scope until P2 |
| Small agency pricing sensitivity | Free tier + transparent pricing reduces friction; don't require credit card to try |
| HIPAA breach via PHI in push notification payload | Push payloads contain only IDs; app fetches detail separately. Enforced by code review gate. |
| Geocoding API cost / rate limits at scale | Geocode at save time, cache `latLng` on entity. No per-match-request API calls. |
| Offline sync data integrity (EVV timestamps) | `capturedOffline` flag + `deviceCapturedAt` timestamp; server receipt time is never used as EVV timestamp |
| Multi-tenancy enforcement gap | Hibernate `@Filter` at persistence layer; test verifies filter is active on all agency-scoped entities |

---

## 12. Out of Scope (P2)

- Billing, claims processing (837P/UB-04), ERA/835 remittance
- EVV transmission to state aggregators (tracked internally, submission manual at P1 for closed states)
- Full payroll processing
- Custom form builder
- Referral/CRM tools
- Vital signs / remote patient monitoring
- Training LMS (beyond basic credential expiry tracking)
- California IHSS integration
- Closed-state EVV connectors (MD, SC, OR, KS, SD)
- Multi-location / franchise agency support

---

*Spec written: 2026-04-04*
*Revised: 2026-04-04 — critical-review-1 all issues resolved*
*Next step: implementation plan*
