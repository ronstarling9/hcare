# Critical Design Review 1 — hcare Caregiver Mobile App Design Spec

**Reviewer:** Senior Code Reviewer
**Date:** 2026-04-08
**Spec reviewed:** `docs/superpowers/specs/2026-04-08-mobile-app-design.md`
**Reference docs:** `2026-04-04-hcare-mvp-design.md`, `2026-04-06-frontend-design.md`

---

## Summary

The mobile app spec covers the core user journeys adequately at a high level. However, it contains several critical omissions that will block implementation, internal contradictions in the visit execution and offline flows, and a significant underspecification of the BFF API surface. There are also typography values that are unusable on mobile and a broken auth model that the spec does not reconcile. These issues must be resolved before implementation begins.

---

## 1. Internal Contradictions

### CRITICAL — EVV compliance status is attributed to the BFF in conflict with the system-of-record rule

**Section 12 (Offline Behavior):** "Conflict resolution: Follows the rules defined in the MVP spec — CONFLICT_REASSIGNED, idempotent retries, time anomaly YELLOW status for > 4h clock-in drift."

**Section 6 (Clock-In):** "EVVRecord created with `capturedOffline = false` (or `true` if offline)"

The MVP spec states unambiguously: "The Mobile BFF does not independently compute compliance status. It calls Core API `GET /api/v1/visits/today?mobile=true`... The BFF is a payload adapter, not a computation layer." Also from CLAUDE.md: "The BFF never computes EVV compliance status independently."

Yet Section 12 says compliance status (`YELLOW` for time anomaly) is set during conflict resolution in the BFF `/sync/visits` endpoint. This is a direct contradiction. The BFF cannot assign a YELLOW status — it must forward the visit record to Core API, which computes status on read. The spec needs to explicitly state that the BFF forwards sync'd records to Core API and that status is computed there. As written, an implementer will put compliance logic in the BFF.

Additionally, the MVP spec specifies the time anomaly threshold as "> 4 hours before scheduled start" for offline YELLOW. The MVP spec's EVV section specifies YELLOW for "> 30 min from scheduled" as a general time anomaly rule. The 4h threshold is specific to offline sync clock-in drift. The mobile spec collapses these into a single "> 4h" rule without acknowledging the distinction. This is ambiguous.

### CRITICAL — Typography font sizes are specified in px but will not behave as expected in React Native

**Section 2 (Typography):** Screen titles are listed as 13px, card titles as 11px, body text as 9–10px. React Native uses density-independent points (dp/pt), not CSS pixels. On a high-DPI device (e.g., iPhone 15 Pro at 3x scale), a 9px value in React Native StyleSheet resolves to 27 physical pixels — readable. But if these values are interpreted as literal CSS pixels (as the notation implies), the spec is mixing web and native units. More critically, 9px and 10px body text is below the iOS Human Interface Guidelines minimum recommended size of 11pt for body text and will fail Apple App Store accessibility review for Dynamic Type compliance. The spec makes no mention of Dynamic Type or Android font scaling, which are requirements for App Store / Play Store approval.

The web frontend spec uses 12–13px body text. The mobile spec uses 9–10px body text for the same content. This is inconsistent and the mobile values are too small even for a condensed mobile UI.

### MAJOR — Clock-in sheet shows "next 2 upcoming shifts for today only" but this conflicts with the shift list sections

**Section 5 (Today's Schedule)** defines three sections: UPCOMING (today's remaining shifts), LATER TODAY (today's remaining when a visit is active), and THIS WEEK (future shifts). No mention of how many shifts appear.

**Section 6 (Clock-In):** "Shows the next 2 upcoming shifts for today only." This creates a gap: what if the caregiver has 3 or more shifts today and the first two are already completed? The sheet only shows 2 shifts — if the next eligible shift is the 3rd, does it appear? Does the list scroll? The "today only" constraint is also not explained. If a caregiver finishes their last shift of the day and taps the FAB, what happens? The spec does not address this state.

### MAJOR — The FAB behavior during an active visit is contradicted by the clock-in sheet logic

**Section 3:** "During an active visit: FAB turns red, icon changes to a stop square, label changes to 'Visit Active'. Tapping navigates to the active visit screen."

**Section 6:** The clock-in sheet is "triggered by tapping the center FAB." There is no explicit statement that the clock-in sheet is suppressed when a visit is active. An implementer must infer this from context. If a second FAB tap during an active visit should go to Visit Execution (not open the sheet), the spec should say so explicitly.

### MINOR — "ADL Tasks" section in Visit Execution contradicts task ordering in the screen

**Section 7 (ADL Tasks):** "Single grouped list: completed tasks (strikethrough, green checkmark) above pending tasks."

Placing completed tasks above pending tasks is atypical and contradicts standard UX patterns for checklists — pending work should be at the top to minimize scrolling to reach incomplete items. This may be intentional, but if so the rationale should be stated. As written, it looks like an error that will be questioned or reversed during implementation.

---

## 2. Gaps and Omissions That Will Block Implementation

### CRITICAL — Auth model specifies a mechanism the MVP spec does not define for caregivers

**Section 4 (Auth):** "Caregivers authenticate via admin-generated tokenized sign-in links (same mechanism as the family portal)."

The MVP spec's family portal auth is: email + magic link (no password). The Core API issues a short-lived tokenized link. But the mobile spec says the invite is a "jwt-single-use" token embedded in a deep link. The spec then says re-auth uses "Check your email for a sign-in link from your agency."

Problems not addressed:
1. Who generates the initial invite? The web admin frontend spec describes "+Add Caregiver" but makes no mention of sending an invite email or app link. There is no endpoint listed in the frontend spec for `POST /caregivers/{id}/invite`.
2. What is the expiry of the single-use JWT? The spec says "Link expired" as a possible state but never defines how long the link is valid.
3. The re-auth fallback asks the caregiver to enter their email and request a new link. But the mobile app has no password auth. What verifies the caregiver's identity when they request a new link? Any email address can request a link for any caregiver? This is a security gap.
4. The spec says the long-lived JWT + refresh token is received after token exchange, but never specifies the token lifetime or refresh mechanism. The web admin frontend JWT is stored in memory and forces re-auth on page reload — the mobile spec implies a persistent session (long-lived JWT). These two persistence models must not share the same token type; the spec should explicitly differentiate.

### CRITICAL — No error states defined for core user flows

The spec defines happy-path flows for clock-in, clock-out, shift acceptance, and messaging. None of the following error states are addressed:
- Clock-in GPS capture fails (permission denied post-onboarding, or GPS unavailable indoors). What does the caregiver see? Can they clock in anyway?
- Clock-out GPS capture fails. Can the caregiver still clock out?
- Shift acceptance returns a server error (not a connectivity issue — a 500 or 409). What does the card show?
- Message send fails. Is the message retained in the input? Is there a retry?
- Sync fails after reconnect (BFF returns an error on `/sync/visits`). What does the caregiver see? Is there a retry mechanism, or is data silently lost?
- The care plan section in Visit Execution: what if the client has no active care plan? What if the care plan has no ADL tasks? Empty states are undefined.

An implementation without defined error states will produce wildly inconsistent behavior across the app.

### CRITICAL — "This Month" stats in Profile have no defined data source or endpoint

**Section 10 (Profile — This Month):** "Shifts completed, Hours worked, On-time rate — displayed as three equal columns."

No endpoint is specified. "On-time rate" is not defined anywhere in the MVP spec domain model. The domain model has `CaregiverScoringProfile.cancelRateLast90Days` and `currentWeekHours`, but no "on-time rate" field. This stat would require computing clock-in times vs. scheduled start times across all completed shifts for the current calendar month — a non-trivial query. The BFF would need a dedicated endpoint and the Core API would need to aggregate this data. As specified, this is undefined work with no data model backing.

### MAJOR — Offline clock-out GPS failure case not addressed

**Section 7 (Visit Execution — Clock Out):** "Tapping captures GPS, closes the EVVRecord, navigates back to Today."

**Section 12 (Offline Behavior):** The offline scenario covers clock-in GPS captured from device. But if the device is offline AND GPS is unavailable at clock-out, what happens? The MVP spec states "Missing clock-out (app crashed before clock-out): EVV record created with timeOut = null → RED status." But there is no specification for the case where the caregiver taps Clock Out, the app records it, but GPS returns no fix. Is the clock-out accepted with a null GPS coordinate? Does it produce an immediate RED EVV? The spec does not distinguish between "offline clock-out with GPS" and "offline clock-out without GPS."

### MAJOR — Open Shifts distance calculation requires caregiver location data with no specified source

**Section 8 (Open Shifts):** "Each card shows: distance from caregiver home address."

Distance is computed from "caregiver home address." The MVP domain model has `CaregiverScoringProfile.homeLatLng` but this is a scoring-module field, not a general-purpose caregiver field. The BFF must expose this distance to the mobile client. The spec does not say whether:
1. The distance is pre-computed server-side and returned in the payload, or
2. The app computes it using device GPS vs. the client's address.

Pre-computing server-side requires the BFF or Core API to compute distance at query time. Using device GPS requires the app to have location permission granted (which is optional per onboarding — "Not now" is always available). If the caregiver denied location permission, there is no distance to show. No fallback behavior is specified.

### MAJOR — Deep link handling for push notifications is underspecified

**Section 13 (Notifications):** "Tapping a notification deep-links to the relevant screen (Open Shifts tab, visit, Messages thread, or Profile credentials)."

Deep linking to a Messages thread requires a `threadId` to navigate to the correct thread. But the payload contains "no PHI — only `shiftId` or event type codes." A message notification presumably carries a `messageId` or `threadId`. The spec says payloads contain "only `shiftId` or event type codes" but then requires navigation to a specific Messages thread. These two constraints are in tension — a thread identifier is not PHI, but it is not a `shiftId`. The payload spec needs to enumerate all possible payload schemas, not just describe them as "shiftId or event type codes."

### MINOR — Sign Out requires no confirmation but this is stated without considering active visits

**Section 10 (Profile):** "Sign Out (red text) — taps require no confirmation (session clear is low-risk; re-auth is self-serve)."

If a caregiver signs out while a visit is active (clock-in recorded, clock-out not yet done), what happens? Is the active visit EVVRecord orphaned? Does the local SQLite event log survive a sign-out? This is a data integrity question, not just a UX question. The spec should address whether sign-out is blocked during an active visit or whether it triggers a warning.

### MINOR — Care Plan read-only screen content is incompletely specified

**Section 5 (Today's Schedule):** "Care Plan" button "navigates to the read-only care plan summary screen for that client — accessible before clocking in."

**Screen Inventory (Section 14):** "Care Plan (read-only): Push from expanded shift card — shows active care plan summary, ADL tasks, goals."

The care plan screen is listed in the screen inventory but has no dedicated section in the spec. Its layout, fields, empty states, and behavior are entirely undefined beyond the one-line inventory description. Given that the web admin frontend spec devotes a full tab to care plan content (ADL tasks, goals, clinician review for HHCS), the mobile read-only view needs at minimum: what fields are shown, in what order, and what happens when there is no active care plan.

---

## 3. Inconsistencies with the Broader System

### MAJOR — EVV status color set in mobile spec omits PORTAL_SUBMIT and EXEMPT

The MVP spec defines 6 EVV statuses: GREEN, YELLOW, RED, GREY, EXEMPT, PORTAL_SUBMIT.

**Section 2 of the mobile spec (EVV / status semantic colors)** defines only 4: GREEN, AMBER, RED, GREY.

EXEMPT (private-pay clients, co-resident caregivers) and PORTAL_SUBMIT (closed-system states) are omitted. A caregiver working a private-pay shift will see an EVVRecord with EXEMPT status — the app has no color or label defined for this. Similarly, a caregiver in a closed-system state will have PORTAL_SUBMIT records. The app must handle all 6 statuses or explicitly document that EXEMPT and PORTAL_SUBMIT are not surfaced to caregivers (which may be correct — they are scheduler-facing statuses — but that decision must be explicit).

Additionally, the mobile spec uses the label "AMBER" where all other specs use "YELLOW." The EVV status enum in the backend is `YELLOW`. Using "AMBER" in the mobile spec creates a naming inconsistency that will cause confusion when the mobile team reads Core API responses containing `"status": "YELLOW"`.

### MAJOR — The spec has no mention of the CAREGIVER JWT role scope defined in the MVP spec

The MVP spec's access control table: "CAREGIVER: Own assigned shifts only; own profile." The auth section of the mobile spec correctly states the JWT claim includes `caregiverId`, but the spec does not acknowledge the scope restriction. Every API endpoint listed in the BFF contract section (below) must enforce `caregiverId` scoping. The spec does not remind the BFF team of this constraint, increasing the risk that an endpoint like `GET /shifts/today` returns all shifts if the BFF passes through incorrectly.

### MINOR — The mobile spec mentions "assigned shifts calendar" in MVP spec's Profile section but omits it from this spec

The MVP spec's Caregiver Mobile App section states: "Profile: Upcoming credential expiration dates, shift history, assigned shifts calendar." The mobile design spec's Profile section (Section 10) shows credentials and "This Month" stats but has no calendar view of assigned shifts. The omission may be intentional for MVP scope, but it is not called out as a deliberate deferral.

---

## 4. Implementability Concerns

### CRITICAL — The 5-tab bottom bar with a raised center FAB is a known React Native implementation complexity that the spec treats as trivial

**Section 3 (Navigation Shell):** The center FAB with `margin-top: -20px` raised above the tab bar is described with CSS notation (`box-shadow: 0 3px 12px`). React Native does not use CSS — it uses `StyleSheet` with `elevation` (Android) and `shadowColor/shadowRadius` (iOS). More importantly, a raised FAB that overlaps the tab bar boundary requires a custom tab bar renderer; standard React Navigation bottom tab navigators do not support this natively. Expo Router (the standard Expo navigation pattern) makes this even more complex. The spec should explicitly note that a custom `tabBar` render prop is required.

The FAB state change (blue → red during active visit) requires the navigation shell to subscribe to a global "active visit" state in Zustand. This architectural dependency between the navigation shell and the visit state is not mentioned. Without calling this out, the implementation team may not set up the Zustand store correctly to drive this behavior.

### MAJOR — Live elapsed timer in both the Today header and Visit Execution requires a persistent timer that survives navigation

**Section 5 (Today header during active visit):** "live elapsed timer (HH:MM:SS, tabular numerals)"
**Section 7 (Visit Execution sticky header):** "live elapsed timer (right, `color-blue`, tabular numerals)"

A timer that increments every second and must be consistent across two screens (the Today banner and the Visit Execution header) requires a single timer source. If this is implemented as component-local state (e.g., `setInterval` in a React component), it will reset every time the component unmounts and remounts. The correct implementation requires either a Zustand store holding the clock-in timestamp and computing elapsed time on render, or a shared context. The spec does not specify this architectural requirement, increasing the likelihood of an incorrect implementation.

### MAJOR — Optimistic updates for ADL task completions require a defined rollback strategy

**Section 7 (ADL Tasks):** "Tapping a pending task marks it complete immediately (optimistic update, queued for sync)."

Optimistic updates require a defined rollback path if the sync fails. React Query's `useMutation` with `onMutate`/`onError` rollback is the correct pattern. The spec does not specify what happens when an optimistic task completion cannot be synced (network never returns, BFF returns an error). Does the task revert to unchecked? Does the caregiver see an error? This is not a minor UX question — it affects the integrity of ADL task completion data in the EVVRecord.

### MINOR — Care notes "auto-saves on blur" but blur behavior is unreliable in React Native

**Section 7 (Care Notes):** "auto-saves on blur."

In React Native, keyboard dismissal (tap outside the text field) triggers `onBlur`, but navigation away from the screen may not reliably trigger it. If a caregiver enters care notes and immediately taps Clock Out without tapping away from the field first, the note may not save. The spec should specify that care notes are saved either on every keystroke (debounced) or explicitly on Clock Out, not relying solely on `onBlur`.

---

## 5. Scope Creep Risks

### MAJOR — "Messages" feature is underspecified in ways that invite over-building

**Section 9 (Messages):** "Broadcast-only inbox. Caregivers receive messages from the agency and can reply."

"Can reply" opens a significant implementation surface. The spec says "Caregivers cannot initiate new conversations" but does not define:
- Whether replies are visible to all recipients of a broadcast, or only to the agency admin (1-to-1 reply vs. group reply)
- Whether there are any limits on reply length or media attachments
- Whether message delivery confirmation is shown (read receipts, sent status)
- Whether the agency admin UI (web frontend spec) has a corresponding inbox — the web frontend spec does not mention `CommunicationMessages` at all beyond listing it in the domain model

Without clarifying that replies are agency-only (1:1) and text-only, an implementer may build a group messaging feature or attach file support. The web frontend spec must also have a corresponding messages view for admins to read replies, or the reply feature is useless.

### MINOR — "Credential expiring" push notification at 30 days could conflict with the 60-day amber threshold in the Profile screen

**Section 13 (Notifications):** "Credential expiring (30 days out): Your [Credential] expires soon."
**Section 10 (Profile — Credentials):** "Amber: 'Expires [Month Year]' (< 60 days)"

The profile shows amber at < 60 days. The notification fires at 30 days. An implementer might add additional notification triggers (60-day, 30-day, 7-day) based on the amber threshold in the UI. The spec should explicitly state whether 30 days is the only trigger point or if there are multiple.

---

## 6. BFF Contract Implications

The following BFF endpoints are implied by this spec. None are currently defined in the MVP spec's BFF description or the frontend spec's endpoint list. The BFF team must build all of these before the mobile app can be implemented.

| Endpoint | Method | Description | Implied by |
|---|---|---|---|
| `/mobile/auth/exchange` | POST | Exchange single-use invite token for long-lived JWT + refresh token | Section 4 |
| `/mobile/auth/refresh` | POST | Refresh an expired JWT using a refresh token | Section 4 (re-auth flow) |
| `/mobile/auth/send-link` | POST | Request a new sign-in link by email | Section 4 (re-auth fallback) |
| `/mobile/visits/today` | GET | Today's shifts with EVV status for the authenticated caregiver | Section 5, 6 |
| `/mobile/visits/{visitId}/clock-in` | POST | Record clock-in with GPS coordinate; creates EVVRecord | Section 6 |
| `/mobile/visits/{visitId}/clock-out` | POST | Record clock-out with GPS coordinate; closes EVVRecord | Section 7 |
| `/mobile/visits/{visitId}/tasks/{taskId}/complete` | POST | Mark an ADL task complete (idempotent) | Section 7 |
| `/mobile/visits/{visitId}/notes` | PUT | Save/replace care notes for a visit | Section 7 |
| `/mobile/shifts/open` | GET | List of open (unassigned) shifts available to this caregiver | Section 8 |
| `/mobile/shifts/{shiftId}/accept` | POST | Accept an open shift | Section 8 |
| `/mobile/shifts/{shiftId}/decline` | POST | Decline an open shift (offline-queueable) | Section 8 |
| `/mobile/messages` | GET | List of message threads for this caregiver | Section 9 |
| `/mobile/messages/{threadId}` | GET | Full thread with all messages | Section 9 |
| `/mobile/messages/{threadId}/reply` | POST | Post a reply to a thread | Section 9 |
| `/mobile/profile` | GET | Caregiver profile including credentials with expiry | Section 10 |
| `/mobile/profile/stats` | GET | This-month stats: shifts completed, hours worked, on-time rate | Section 10 |
| `/mobile/careplan/{shiftId}` | GET | Read-only care plan for the client on a given shift | Section 5, 14 |
| `/sync/visits` | POST | Offline sync batch upload | Section 12 |
| `/mobile/shifts/week` | GET | This-week future shifts for the Today screen THIS WEEK section | Section 5 |

**Critical notes for the BFF team:**

1. All `/mobile/` endpoints must validate that the `caregiverId` in the JWT matches the resource being accessed. The BFF must not expose another caregiver's shifts, messages, or profile.

2. `/mobile/visits/today` must return EVV status as computed by Core API — the BFF calls `GET /api/v1/visits/today?mobile=true` and forwards the result. It must not compute compliance status independently.

3. The offline sync endpoint `/sync/visits` must delegate deduplication to Core API `POST /api/v1/internal/sync/batch` as defined in the MVP spec. The BFF holds no state.

4. `/mobile/profile/stats` requires a new Core API internal endpoint. The `on-time rate` metric has no current backing field in the domain model — Core API must compute this from shift history.

5. The decline endpoint (`/mobile/shifts/{shiftId}/decline`) must be designed for offline queuing. The mobile app queues this locally and sends it on reconnect — the endpoint must be idempotent.

6. Push notification registration — the app must send its Expo push token to the BFF on launch (or after notification permission is granted). No endpoint for `POST /mobile/devices/push-token` is specified. Without this, no push notifications can be delivered.

---

## 7. Summary Table

| # | Finding | Severity | Section |
|---|---|---|---|
| 1 | BFF assigned EVV compliance status computation, violating single-source-of-truth rule | CRITICAL | 12 |
| 2 | Typography in px not pt; values below iOS minimum accessibility threshold | CRITICAL | 2 |
| 3 | Auth model has security gap (no identity verification for new link requests) and undefined token lifetime | CRITICAL | 4 |
| 4 | "This Month" on-time rate stat has no data model backing and no endpoint | CRITICAL | 10 |
| 5 | Zero error states defined for any user flow | CRITICAL | 6, 7, 8, 9, 12 |
| 6 | Clock-in sheet truncation to 2 shifts is incomplete (edge cases unhandled) | MAJOR | 6 |
| 7 | FAB dual-state behavior (active visit) not explicitly specified to suppress clock-in sheet | MAJOR | 3, 6 |
| 8 | Offline clock-out without GPS fix is unspecified | MAJOR | 7, 12 |
| 9 | Open Shifts distance computation source undefined; fallback for denied location permission missing | MAJOR | 8 |
| 10 | Push notification payload spec excludes thread identifiers needed for deep linking | MAJOR | 13 |
| 11 | PORTAL_SUBMIT and EXEMPT EVV statuses absent from mobile color/label spec | MAJOR | 2 |
| 12 | "AMBER" label inconsistent with backend "YELLOW" enum value | MAJOR | 2 |
| 13 | Live elapsed timer persistence across navigation not architecturally specified | MAJOR | 5, 7 |
| 14 | Optimistic ADL task updates have no defined rollback strategy | MAJOR | 7 |
| 15 | Messages reply scope (1:1 vs. broadcast) and admin-side inbox are undefined | MAJOR | 9 |
| 16 | Care Plan read-only screen has no dedicated section — content, layout, empty states undefined | MAJOR | 5, 14 |
| 17 | Sign-out during active visit is not addressed | MINOR | 10 |
| 18 | ADL completed tasks listed above pending tasks — likely ordering error | MINOR | 7 |
| 19 | "Assigned shifts calendar" from MVP spec Profile description is silently omitted | MINOR | 10 |
| 20 | CAREGIVER JWT scope restriction not noted for BFF contract | MINOR | 4 |
| 21 | Custom tab bar renderer requirement for raised FAB not flagged | CRITICAL (impl) | 3 |
| 22 | Push token registration endpoint missing from BFF contract | CRITICAL (impl) | 13 |
| 23 | Care notes `onBlur` auto-save unreliable in React Native | MINOR | 7 |
| 24 | Credential notification at 30 days vs. 60-day UI amber threshold could invite extra notification triggers | MINOR | 10, 13 |

---

*Review completed: 2026-04-08*
