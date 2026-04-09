# hcare Mobile BFF — Endpoint Reference

**Date:** 2026-04-08
**Status:** Reference document — not yet implemented
**Derived from:** `docs/superpowers/specs/2026-04-08-mobile-app-design.md` (post-review revision)

This document lists all BFF endpoints implied by the mobile app design spec. It is a reference for when the BFF is built — no implementation has occurred yet. All endpoints are under the BFF base URL (`http://localhost:8081` in dev).

**Key constraints for all endpoints:**
- All `/mobile/` endpoints must validate that the `caregiverId` in the JWT matches the resource being accessed — the BFF must not expose another caregiver's data.
- The BFF is a stateless thin adapter. It never computes EVV compliance status. It calls Core API and forwards results.
- Payloads must contain no PHI — push notification payloads carry only `shiftId`, `threadId`, or event type codes.

---

## Endpoint Table

| Method | Path | Description | Screen / Flow that requires it |
|---|---|---|---|
| POST | `/mobile/auth/exchange` | Exchange a single-use invite or sign-in token (from deep link) for a long-lived access JWT (7-day) and refresh token (90-day) | Section 4 — Primary auth / deep link handler |
| POST | `/mobile/auth/refresh` | Refresh an expired access JWT using the refresh token; returns a new access JWT | Section 4 — Silent mid-visit background refresh; re-auth flow |
| POST | `/mobile/auth/send-link` | Request a new tokenized sign-in link sent to the provided email address; always responds with the same message regardless of whether the email matches (anti-enumeration) | Section 4 — Re-auth fallback login screen; Link Expired screen |
| POST | `/mobile/devices/push-token` | Register or update the device Expo push token for the authenticated caregiver; must be called on every app launch after auth in case the token has rotated | Section 13 — First launch after auth; every subsequent launch |
| GET | `/mobile/visits/today` | Return today's shifts with EVV status for the authenticated caregiver; EVV status is computed by Core API and forwarded — not computed by the BFF | Section 5 — Today's Schedule screen; Section 6 — Clock-in bottom sheet |
| GET | `/mobile/visits/week` | Return future shifts for the current week beyond today, for the THIS WEEK section on the Today screen | Section 5 — Today's Schedule screen (THIS WEEK disclosure section) |
| POST | `/mobile/visits/{visitId}/clock-in` | Record a clock-in event with GPS coordinate (may be null if GPS unavailable) and `capturedOffline` flag; creates an EVVRecord via Core API | Section 6 — Clock-in flow on confirm |
| DELETE | `/mobile/visits/{visitId}/clock-in` | Void a clock-in if it was created less than 5 minutes ago; deletes the EVVRecord; used by the wrong-shift recovery flow | Section 6 — "⚠ Wrong shift?" overflow menu action in Visit screen |
| POST | `/mobile/visits/{visitId}/clock-out` | Record a clock-out event with GPS coordinate (may be null if GPS unavailable); closes the EVVRecord via Core API | Section 7 — Clock Out button in Visit Execution screen |
| POST | `/mobile/visits/{visitId}/tasks/{taskId}/complete` | Mark an ADL task complete for the given visit (idempotent); queued offline and synced on reconnect | Section 7 — ADL task checkbox tap in Visit Execution screen |
| DELETE | `/mobile/visits/{visitId}/tasks/{taskId}/complete` | Revert a completed ADL task back to pending; idempotent | Section 7 — Tapping a completed ADL task to revert it |
| PUT | `/mobile/visits/{visitId}/notes` | Save or replace care notes for the visit (full replacement, not patch); called on blur and on Clock Out | Section 7 — Care Notes auto-save in Visit Execution screen |
| POST | `/sync/visits` | Offline sync batch upload; accepts an array of visit events (clock-in, task completions, notes, clock-out) keyed by `(deviceId, visitId, eventType, occurredAt)`; BFF deduplicates and forwards to Core API; returns any CONFLICT_REASSIGNED responses | Section 12 — Offline reconnect sync |
| GET | `/mobile/shifts/open` | List open (unassigned) shifts available to the authenticated caregiver, including pre-computed distance from caregiver home address; requires connectivity | Section 8 — Open Shifts tab |
| POST | `/mobile/shifts/{shiftId}/accept` | Accept an open shift; requires connectivity (accept button disabled when offline) | Section 8 — Accept Shift button on open shift card |
| POST | `/mobile/shifts/{shiftId}/decline` | Decline an open shift; offline-queueable (idempotent, synced on reconnect) | Section 8 — Decline button on open shift card |
| GET | `/mobile/messages` | List message threads for the authenticated caregiver; includes unread status per thread | Section 9 — Messages Inbox tab |
| GET | `/mobile/messages/{threadId}` | Full thread with all messages in chronological order | Section 9 — Messages Thread screen |
| POST | `/mobile/messages/{threadId}/reply` | Post a caregiver reply to a message thread; reply is 1-to-1 with the agency (not broadcast to all recipients) | Section 9 — Reply bar send button in Thread view |
| GET | `/mobile/profile` | Caregiver profile including name, agency name, primary credential type, and full credentials list with expiry dates and status | Section 10 — Profile tab |
| GET | `/mobile/profile/stats` | This-month stats for the authenticated caregiver: shifts completed, hours worked (on-time rate deferred to P2 pending scoring module support) | Section 10 — This Month stats tile in Profile |
| GET | `/mobile/careplan/{shiftId}` | Read-only care plan for the client associated with the given shift; includes diagnoses, allergies, caregiver notes, ADL tasks, goals, and whether the plan was updated since the caregiver's last visit with this client | Section 5 — Care Plan button on expanded shift card; Section 7 — Care plan summary in Visit Execution; Section 14 — Care Plan read-only screen |

---

## Notes

1. **`/sync/visits` deduplication:** The BFF deduplicates events by `(deviceId, visitId, eventType, occurredAt)` before forwarding to Core API `POST /api/v1/internal/sync/batch`. The BFF holds no state — deduplication is a pass-through filter.

2. **`/mobile/shifts/open` distance field:** Distance from caregiver home address must be pre-computed server-side by Core API (sourced from `CaregiverScoringProfile.homeLatLng`) and included in the payload. The BFF does not compute distance. If `homeLatLng` is not set, the distance field is omitted; the mobile app must handle a missing distance gracefully (e.g., display "—" instead of a distance).

3. **`/mobile/profile/stats` Core API dependency:** This endpoint requires a dedicated Core API aggregation query. The `on-time rate` stat is explicitly deferred to P2 — this endpoint returns only `shiftsCompleted` and `hoursWorked` at MVP.

4. **`/mobile/auth/send-link` anti-enumeration:** The endpoint must always return `200 OK` with the same body ("If that email matches your account, a link has been sent.") regardless of whether the email matched an active caregiver. The link is only sent when there is a match. This prevents user enumeration.

5. **`/mobile/devices/push-token` rotation:** Push tokens can change after OS updates, app reinstalls, or Expo SDK updates. The app must call this endpoint on every launch after auth — not only on first launch.

6. **CONFLICT_REASSIGNED response:** When `/sync/visits` identifies a CONFLICT_REASSIGNED condition (forwarded from Core API), the response body must include enough detail for the app to construct the conflict detail screen: shift date, client name, caregiver clock-in time, and caregiver clock-out time. No PHI beyond client name is required.

7. **EVV status in `/mobile/visits/today` response:** The BFF calls Core API `GET /api/v1/visits/today?mobile=true` and forwards the computed EVV status (`GREEN`, `YELLOW`, `RED`, `GREY`, `EXEMPT`, `PORTAL_SUBMIT`) as returned. The BFF does not filter, transform, or override this value.

---

*Document written: 2026-04-08*
