---
date: 2026-04-06T22:49:08
git_commit: 2c18f94ef826f5e1e841ce769af8f14c73adf3c1
branch: main
repository: hcare
topic: "Frontend Design Approved — Writing Implementation Plan"
tags: [handoff, session-transition, react, typescript, frontend, spring-boot, dashboard-endpoint]
status: in_progress
last_updated: 2026-04-06
type: implementation_handoff
---

# Handoff: Frontend Design Approved — Writing Implementation Plan

## 0. Executive Summary (TL;DR)

A full web admin frontend design was brainstormed and approved this session, producing `docs/superpowers/specs/2026-04-06-frontend-design.md`; the backend is 100% complete with 166 passing tests and no frontend exists yet. The session was interrupted while writing the implementation plan for the frontend. The single most important next action is to finish writing the implementation plan using the `superpowers:writing-plans` skill.

## 1. Technical State

**Active Working Set:**
- `docs/superpowers/specs/2026-04-06-frontend-design.md:1` — the just-approved frontend design spec; this is the primary input for the plan
- `backend/src/main/java/com/hcare/api/v1/visits/VisitController.java:1` — backend is complete; no `GET /api/v1/dashboard/today` endpoint exists yet — this is a documented gap that must be added as Phase 2 of the plan
- `bff/src/main/java/com/hcare/bff/BffApplication.java:1` — BFF is a stub; out of scope for this plan

**Current Errors / Blockers:**
`None`

**Environment:**
- Uncommitted changes: `.superpowers/` directory (visual brainstorming session artifacts — gitignore this)
- Staged changes: none
- ENV vars or config required: none beyond existing dev profile
- Running processes: visual companion server may still be running on port 57324 — safe to ignore or kill

## 2. Progress Tracker

| Task | Status | Location | Notes |
|------|--------|----------|-------|
| Backend Core API | ✅ Complete | `backend/src/main/java/com/hcare/` | 166 tests passing |
| Frontend design spec | ✅ Complete | `docs/superpowers/specs/2026-04-06-frontend-design.md:1` | Fully approved |
| Frontend implementation plan | 🔄 In Progress | `docs/superpowers/plans/` | Interrupted before writing began |
| Frontend scaffold | ⏳ Pending | `frontend/` (does not exist) | Blocked on plan |
| `GET /api/v1/dashboard/today` backend endpoint | ⏳ Pending | — | Phase 2 of frontend plan |

## 3. Mental Model (Most Critical Section)

**Why the current approach was chosen:**

The user wants a **phased rapid-prototyping approach**:
- **Phase 1**: Build the entire static UI with mock data — all screens, all interactions, no backend calls. This lets the user manually test the full UX before wiring anything up.
- **Phase 2**: Add `GET /api/v1/dashboard/today` to the backend (the only missing endpoint).
- **Phase 3**: Wire auth (login → JWT in memory → route guards).
- **Phase 4**: Wire Schedule screen to real API.
- **Phase 5**: Wire Dashboard to real API.
- **Phase 6**: Wire Clients to real API.
- **Phase 7**: Wire Caregivers to real API.
- **Phase 8**: Wire Payers + EVV Status to real API.

**The user explicitly said "stop between each phase so I can manually test."** Each phase must end with a clear `✋ MANUAL TEST CHECKPOINT` and the plan must not continue executing past it.

**Approved design decisions (all in spec):**
- Sidebar nav (`#1a1a24`), Schedule is the landing screen (`/`)
- Color palette: `#1a1a24` dark / `#1a9afa` blue (active states, today marker) / `#ffffff` / `#f6f6fa` surface — **no yellow anywhere**
- Shift clicks + "New Shift" = same full-cover slide-in panel from right (covers entire main area, sidebar stays visible)
- Animation: `transform: translateX(100%) → translateX(0)`, `280ms cubic-bezier(0.4, 0, 0.2, 1)`
- Back navigation: `← Schedule` back link in panel header (no X button)
- Dashboard: 4 stat tiles (RED/YELLOW/Uncovered/On Track) + urgency-sorted visit list + 220px alerts column

**Codebase Gotchas Discovered This Session:**
- `frontend/` directory does not exist — must be scaffolded from scratch with `npm create vite@latest`
- `backend/src/main/java/com/hcare/api/v1/scheduling/dto/ShiftSummaryResponse.java:1` — `ShiftSummaryResponse` does NOT include EVV status or client/caregiver names — only IDs. The dashboard endpoint needs a richer DTO with denormalized names and computed EVV status.
- `backend/src/main/java/com/hcare/api/v1/scheduling/dto/CreateShiftRequest.java:1` — `CreateShiftRequest` has no recurrence field; recurrence is a separate `RecurrencePattern` entity. New Shift panel in Phase 1 creates a single shift only — recurrence is P2.
- `backend/src/main/java/com/hcare/api/v1/visits/VisitController.java:1` — Clock-in is at `POST /api/v1/shifts/{id}/clock-in`, NOT `POST /api/v1/visits/...`
- The `broadcastShift` endpoint (`POST /api/v1/shifts/{id}/broadcast`) is per-shift only — the "Broadcast Open" button in the UI must loop over unassigned shifts client-side; there is no bulk broadcast endpoint.

**Key Decisions Made:**

| Decision | Rationale | Alternative Rejected |
|----------|-----------|---------------------|
| Phase 1 = pure static mock, no API calls | Lets user test full UX before any backend wiring | Wiring as you go — harder to see full picture |
| Components accept props, pages fetch data | Enables clean Phase 1 → real API swap without touching components | Hooks inside components — would require rewrites |
| Tailwind CSS v3 | More stable, traditional config, well-documented | v4 — still evolving, different config approach |
| JWT in memory only (not localStorage) | Security — XSS can't steal token from localStorage | localStorage — vulnerable to XSS |
| `GET /api/v1/dashboard/today` returns denormalized names + EVV status | Frontend shouldn't N+1 on IDs | Frontend resolves IDs — unacceptable UX lag |

**Dead Ends — Do Not Repeat These:**

| Approach Tried | Why It Failed | Evidence |
|---------------|---------------|----------|
| Narrowing calendar when shift panel opens (side-by-side) | User rejected — "doesn't feel right" | Visual companion session |
| Yellow `#ffe600` as primary button color | User explicitly removed it — "no yellow" | brainstorming session |
| Yellow `#ffe600` as active nav state | User changed to blue `#1a9afa` | brainstorming session |

**Assumptions in Play:**
- Frontend runs at `http://localhost:5173` (Vite default); backend at `http://localhost:8080` — what breaks if wrong: CORS errors, fix in `SecurityConfig.java`
- CORS is already configured in backend `SecurityConfig.java` for `http://localhost:5173` — verify this before Phase 3

## 4. Delta — Changes Made This Session

All changes are committed. The only new file is:
- `docs/superpowers/specs/2026-04-06-frontend-design.md:1` — frontend design spec (committed at `2c18f94`)

The `.superpowers/` directory contains brainstorming session HTML mockups — these can be gitignored or left as-is.

## 5. Next Steps (Ordered — Do Not Skip Steps)

1. **Verify backend is still green:**
   ```bash
   cd backend && mvn test -q 2>&1 | tail -5
   ```
   Expected output: `[INFO] Tests run: 166, Failures: 0, Errors: 0`

2. **Add `.superpowers/` to `.gitignore`** (if not already there):
   ```bash
   echo ".superpowers/" >> /Users/ronstarling/repos/hcare/.gitignore
   git add .gitignore && git commit -m "chore: ignore brainstorming session artifacts"
   ```

3. **Immediate action — write the implementation plan** using `superpowers:writing-plans`. The plan input is:
   - Spec: `docs/superpowers/specs/2026-04-06-frontend-design.md:1`
   - User instruction: phased approach (Phase 1 = full static UI, Phase 2 = backend dashboard endpoint, Phase 3-8 = wire each screen to backend), stop between phases for manual testing
   - Save plan to: `docs/superpowers/plans/2026-04-06-frontend-web-admin.md`

4. **Key backend DTOs to reference when writing the plan** (already read this session):
   - `ShiftSummaryResponse`: `id, agencyId, clientId, caregiverId, serviceTypeId, authorizationId, sourcePatternId, scheduledStart, scheduledEnd, status, notes` (no EVV status, no names)
   - `ShiftDetailResponse` (from `VisitController`): adds `evv` nested record with `evvRecordId, complianceStatus, timeIn, timeOut, verificationMethod, capturedOffline`
   - `RankedCaregiverResponse`: `caregiverId, score, explanation`
   - `LoginResponse`: `token, userId, agencyId, role`
   - `ClientResponse`: `id, firstName, lastName, dateOfBirth, address, phone, medicaidId, serviceState, preferredCaregiverGender, preferredLanguages, noPetCaregiver, status, createdAt`
   - `CaregiverResponse`: `id, firstName, lastName, email, phone, address, hireDate, hasPet, status, createdAt`
   - `CredentialResponse`: `id, caregiverId, credentialType, issueDate, expiryDate, verified, verifiedBy, createdAt`
   - `AuthorizationResponse`: `id, clientId, payerId, serviceTypeId, authNumber, authorizedUnits, usedUnits, unitType, startDate, endDate, version, createdAt`

5. **For Phase 2 backend endpoint** — the `DashboardTodayResponse` must be designed as part of the plan. Key shape:
   ```
   DashboardTodayResponse {
     redEvvCount: int
     yellowEvvCount: int
     uncoveredCount: int
     onTrackCount: int
     visits: List<DashboardVisitRow>  // sorted: RED first, YELLOW, uncovered, then GREEN
     alerts: List<DashboardAlert>     // credential expiry, auth utilization, bg check due
   }
   DashboardVisitRow {
     shiftId, clientFirstName, clientLastName,
     caregiverId (nullable), caregiverFirstName (nullable), caregiverLastName (nullable),
     serviceTypeName, scheduledStart, scheduledEnd, status, evvStatus, evvStatusReason
   }
   DashboardAlert {
     type: CREDENTIAL_EXPIRY | AUTHORIZATION_LOW | BACKGROUND_CHECK_DUE
     subject: String   // "D. Torres" 
     detail: String    // "HHA expires in 3 days"
     dueDate: LocalDate
     resourceId: UUID  // caregiverId or clientId
     resourceType: CAREGIVER | CLIENT
   }
   ```
   The service queries shifts for today (00:00–23:59), computes EVV status per shift via `EvvComplianceService`, queries credentials expiring within 30 days, authorizations with < 20 hrs remaining, and background checks due within 30 days.

6. **Watch for**: CORS — the backend `SecurityConfig.java` must allow `http://localhost:5173`. Check this before writing Phase 3 and add a step to configure it if missing.

## 6. Artifacts & References

- **Design spec**: `docs/superpowers/specs/2026-04-06-frontend-design.md:1`
- **Backend integration test pattern**: `backend/src/test/java/com/hcare/AbstractIntegrationTest.java:1` — extend this for the dashboard endpoint test
- **Example integration test**: `backend/src/test/java/com/hcare/api/v1/scheduling/ShiftSchedulingControllerIT.java:1`
- **Visual mockups** (brainstorming session): `.superpowers/brainstorm/53699-1775538830/content/` — `ey-style-v3.html`, `shift-panel-v2.html`, `dashboard.html` show the approved look and feel
- **All backend endpoints documented in**: `docs/superpowers/specs/2026-04-04-hcare-mvp-design.md:1`
