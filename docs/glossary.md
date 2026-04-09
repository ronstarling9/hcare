# Glossary

Domain and technical terms used across hcare.

---

**ADL (Activities of Daily Living)** — Personal care tasks documented during a visit (bathing, dressing, mobility, etc.). Defined on a care plan and checked off by the caregiver during the shift.

**Agency** — A home care company using hcare. The top-level multi-tenant unit; all data is scoped to an agency via `agencyId`.

**Authorization** — A payer's approval for a client to receive a specific service type, up to a capped number of units over a date range. hcare tracks utilization against the authorization in real time.

**BFF (Backend for Frontend)** — The stateless Spring Boot service (`/bff`) that sits between the caregiver mobile app and the Core API. Handles push notifications, offline sync reconciliation, and mobile payload shaping. Contains no database.

**Care Plan** — A versioned document attached to a client specifying goals, ADL tasks, diagnoses, medications, and clinical notes. Only one care plan is `ACTIVE` at a time; older versions are `SUPERSEDED`.

**Caregiver** — A field worker employed by an agency. Uses the mobile app to clock in/out, complete ADL tasks, and record care notes.

**Client** — A person receiving home care services. Has a care plan, authorizations, and optionally a family portal.

**Clock-In / Clock-Out** — The act of starting and ending a visit. Each creates an EVV record capturing GPS coordinates, timestamp, and verification method.

**Conflict** — Occurs during offline sync when a visit's shift was reassigned to another caregiver while the original caregiver was offline. The BFF returns `CONFLICT_REASSIGNED`; the caregiver sees a banner and the agency investigates.

**EVV (Electronic Visit Verification)** — A federal mandate (21st Century Cures Act) requiring six data elements to be captured for every Medicaid-funded home visit: caregiver identity, client identity, service type, location, time-in, and time-out.

**EVV Aggregator** — The state-designated system that receives EVV data. Examples: Sandata, HHAeXchange, Authenticare, Carebridge, Netsmart, Therap, state-built portals. Routing is configured per state in `EvvStateConfig`.

**EVV Compliance Status** — Computed on every read from the EVV record and state rules. Never stored. Values: `GREEN` (fully compliant), `YELLOW` (minor exception), `RED` (missing element or no clock-out), `EXEMPT` (private pay or co-resident), `PORTAL_SUBMIT` (closed-state, agency submits via state portal), `GREY` (visit not started yet).

**Family Portal** — A read-only view for a client's family members. Access is granted by an admin via a one-time invite link (72-hour TTL). Portal users authenticate with a separate JWT signed by a dedicated key.

**Feature Flags** — Per-agency boolean settings. Currently: `aiSchedulingEnabled` (gates the AI caregiver match engine) and `familyPortalEnabled` (gates portal invite generation).

**ICD Code** — International Classification of Diseases code. Stored as a client diagnosis (e.g. `Z74.09` — Other reduced mobility).

**JWT (JSON Web Token)** — Stateless authentication token. Admin/scheduler tokens are signed with `hcare.jwt.secret`; family portal tokens are signed with a separate `hcare.portal.jwt.secret`.

**Magic Link** — Passwordless sign-in for caregivers. The agency sends a sign-in email; tapping the link deep-links into the mobile app and exchanges the token for a JWT.

**Open Shift** — A shift with no assigned caregiver. Dispatched to eligible caregivers as a shift offer; caregivers can accept or decline from the Open Shifts tab.

**Payer** — The entity that reimburses the agency for care. Types: `MEDICAID`, `MEDICARE`, `LTC_INSURANCE`, `VA`, `PRIVATE_PAY`. Payer type affects EVV requirements and compliance status (private pay is exempt).

**PHI (Protected Health Information)** — Any health data that identifies an individual, covered under HIPAA. hcare logs all PHI access to `PhiAuditLog` and never includes PHI in push notification payloads.

**Recurrence Pattern** — A template for generating shifts on a repeating schedule (e.g. every Mon/Wed/Fri). A nightly job advances the generation frontier on a rolling 8-week horizon.

**Scoring Profile** — Per-caregiver data used by the AI match engine: cancel rate, current week hours, total completed shifts, and per-client affinity (visit history). Updated asynchronously on shift completion.

**Service Type** — The type of care being provided (e.g. Personal Care, Skilled Nursing). Defines which caregiver credentials are required and whether EVV capture is mandatory.

**Shift** — A single scheduled visit instance generated from a recurrence pattern (or created ad hoc). Statuses: `OPEN → ASSIGNED → IN_PROGRESS → COMPLETED` (or `CANCELLED` / `MISSED`).

**Shift Offer** — An outbound notification to a caregiver asking them to take an open shift. The caregiver accepts or declines; acceptance assigns the shift.

**Tenant / Multi-tenancy** — Each agency is a tenant. Data isolation is enforced at the database layer via a Hibernate `agencyFilter` that automatically scopes all queries to the requesting agency's `agencyId`.

**Utilization** — The running total of authorized units consumed against a payer authorization. Updated on clock-out using optimistic locking to handle concurrent visits.

**Void** — Cancelling a clock-in that was made in error. Only allowed within 5 minutes of clock-in. The BFF deletes the EVV record and the local SQLite queue entry.
