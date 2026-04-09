# hcare Caregiver Mobile App Design Spec

**Date:** 2026-04-08
**Status:** Draft — approved through brainstorming session

---

## 1. Overview

The hcare caregiver mobile app is a React Native application used by caregivers to manage their daily schedule, execute visits with EVV compliance, accept open shifts, and communicate with their agency. It is designed mobile-first for field workers — used on the go, often outdoors, occasionally offline.

**Tech stack:** React Native (Expo), TypeScript, React Query (server state), Zustand (UI-only state), Expo SQLite (offline storage), Expo Notifications (push), Expo Location (GPS).

**Target platforms:** iOS and Android.

---

## 2. Visual Design Language

On-brand with the web admin but light — same flat/minimal aesthetic and color tokens, white/light backgrounds for outdoor readability.

### Color palette

Inherits the project's Tailwind tokens:

| Token | Value | Usage |
|---|---|---|
| `color-dark` | `#1a1a24` | Branded header backgrounds, Clock Out button, sign-in screen |
| `color-blue` | `#1a9afa` | FAB, active nav, active visit banner, primary CTA buttons, unread badges |
| `color-surface` | `#f6f6fa` | Screen backgrounds, list backgrounds |
| `color-white` | `#ffffff` | Cards, sheet backgrounds |
| `color-border` | `#eaeaf2` | Card borders, dividers, separators |
| `color-text-primary` | `#1a1a24` | Body text, card titles |
| `color-text-secondary` | `#747480` | Metadata, timestamps, section labels |
| `color-text-muted` | `#94a3b8` | Inactive nav labels, placeholder text |

### EVV / status semantic colors

| Status | Color | Usage |
|---|---|---|
| GREEN | `#16a34a` | Completed visits, valid credentials, GPS confirmed |
| AMBER | `#ca8a04` | Credentials expiring soon (< 60 days) |
| RED | `#dc2626` | Urgent open shifts, Sign Out text, missing EVV |
| GREY | `#94a3b8` | Future shifts not yet started |

### Typography

System font stack: `-apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif`.

All sizes are density-independent points (pt), not CSS pixels. React Native StyleSheet values are specified in pt.

- Screen titles: 17pt, weight 700
- Section labels: 11pt, weight 700, letter-spacing 0.1em, uppercase, `color-text-secondary`
- Card titles: 15pt, weight 700
- Body / metadata: 14pt, weight 400–600
- Timestamps: 13pt, `color-text-secondary`

**Dynamic Type / font scaling:** The app must respect iOS Dynamic Type and Android font scaling. Defined bounds: floor 0.8x (content remains legible), ceiling 1.5x (layout remains usable). Do not hard-code sizes in ways that ignore the OS text scale setting.

### Components

- **Cards:** White background, 1px `color-border` border, 10px border-radius, 3px colored left border for shift status, subtle shadow (`0 1px 4px rgba(0,0,0,0.06)`).
- **Buttons:** Primary = `color-blue` background, white text, weight 700, 8px border-radius. Dark = `color-dark` background (Clock Out). Ghost = `color-surface` background, `color-text-secondary` text. No hover shadows.
- **Section headers:** 8px uppercase labels in `color-text-secondary`, 0.1em letter-spacing.
- **Bottom sheet drag handle:** 36px × 4px, `color-border`, centered at top of sheet.

---

## 3. Navigation Shell

### Bottom Tab Bar

5 tabs with a center FAB (floating action button):

| Position | Tab | Icon | Active color |
|---|---|---|---|
| 1 | Today | Calendar | `color-blue` |
| 2 | Open Shifts | Megaphone | `color-blue` |
| 3 (center) | Clock In FAB | Timer | `color-blue` (blue) / `#dc2626` (red during active visit) |
| 4 | Messages | Chat bubble | `color-blue` |
| 5 | Profile | Person | `color-blue` |

The center FAB is raised (46×46px circle, `box-shadow: 0 3px 12px rgba(26,154,250,0.45)`, margin-top: -20px from tab bar).

**During an active visit:** FAB turns red (`#dc2626`), icon changes to a stop square, label changes to "Visit Active". Tapping navigates to the active visit screen. When a visit is active, tapping the FAB does not open the clock-in sheet — it navigates to the active visit screen.

**Unread badge:** Red dot on the Messages tab icon when there are unread messages.

**Implementation note:** The raised center FAB with negative margin overlap requires a custom `tabBar` render prop in React Navigation. Standard `@react-navigation/bottom-tabs` does not support this layout natively.

---

## 4. Auth & Onboarding

### Auth model

Caregivers authenticate via admin-generated tokenized sign-in links (same mechanism as the family portal). The Core API JWT claim for a caregiver session: `{"role": "CAREGIVER", "caregiverId": "<uuid>", "agencyId": "<uuid>"}`.

**Token lifetimes:** Access token 7 days. Refresh token 90 days. These are mobile-specific token lifetimes and must not be conflated with the web admin token model.

**JWT expiry mid-visit behavior:** If the access token expires during an active visit, the app silently attempts a background token refresh using the refresh token. If the refresh succeeds, the visit continues uninterrupted. If the refresh fails, the app does NOT redirect to the login screen — instead a non-blocking banner appears: "Session expiring soon — tap to re-authenticate." Tapping this banner opens the re-auth flow without clearing the active visit or local SQLite event log.

### Auth flows

**Primary — first login (invite link):**
1. Admin adds caregiver in the web admin → system sends invite email containing a tokenized deep link (e.g., `hcare://auth?token=<jwt-single-use>`)
2. Caregiver taps the link on their phone → deep link opens the app
3. App exchanges the token with Core API → receives a long-lived JWT + refresh token
4. Onboarding begins (see below)

**Re-auth — session expired:**
1. App opens cold with no valid session → shows login screen
2. Login screen primary message: "Check your email for a sign-in link from your agency"
3. Fallback: email input + "Send New Sign-In Link" button → Core API sends a fresh tokenized link only if the email matches an active caregiver in the system. No confirmation of match is shown — the app always displays "If that email matches your account, a link has been sent." (prevents user enumeration) → same deep link flow

**Invalid / expired link:**
App detects expired or invalid token during deep link exchange → shows error screen: "Link expired. Enter your email for a new one." with email input.

**Auth token exchange failure (deep link):**
If the token exchange API call fails for any reason other than an expired token (network error, server error), show the "Link expired" error screen with email input as the fallback recovery path.

**Returning user (valid session):**
App opens directly to the Today screen — no auth screen shown.

### Onboarding (first login only)

3-step flow shown after first successful auth. Progress dots at top of each step. "Not now" always available — no forced permissions.

1. **Welcome screen** — "Welcome, [Name]! [Agency Name]. Let's get two quick things set up."
2. **Notifications permission** — "Stay in the loop. Enable notifications to get alerted when open shifts are available." → OS permission prompt
3. **Location permission** — "Location for EVV. Your GPS is captured only at clock-in and clock-out — never tracked during a visit." → OS permission prompt

After step 3 (or skipping): lands on Today screen.

---

## 5. Today's Schedule

The primary screen and navigation hub.

### Layout

**Header (no active visit):**
- "Good morning/afternoon, [First Name]"
- "[Day], [Date] · [N] shifts today"

**Header (active visit):**
- Blue banner replaces greeting: client name, "Clocked in [time]", live elapsed timer (HH:MM:SS, tabular numerals)
- "Continue Visit →" button navigates to the active visit screen

**Shift list sections:**
- `UPCOMING` — today's remaining shifts (shown when no visit is active)
- `LATER TODAY` — today's remaining shifts not yet started (shown when a visit is currently active, replacing `UPCOMING`)
- `THIS WEEK` — future shifts beyond today (dimmed opacity). `THIS WEEK` is collapsed by default. A disclosure row "Show this week ([N] shifts)" expands it. State is not persisted — it collapses again each time the Today screen is loaded.

### Shift cards

Each shift card shows: client name, time range, service type, duration. Cards have a 3px left border:
- Blue (`color-blue`) — next upcoming shift; also shows a "NEXT" badge pill
- Grey (`color-text-muted`) — future shifts
- Green (`#16a34a`) — completed shifts

The **next upcoming** shift card is expanded with two secondary action buttons: "Maps" (opens native maps to client address) and "Care Plan" (navigates to the read-only care plan summary screen for that client — accessible before clocking in).

All other shift cards are compact (name + time + service type only).

---

## 6. Clock-In Flow

Triggered by tapping the center FAB.

**Bottom sheet slides up** over a dimmed Today screen:
- Drag handle at top
- Section label: "CLOCK IN TO"
- Shows **all remaining upcoming shifts for today** (minimum 1, no cap). Shifts are sorted by scheduled start time ascending.
- The first/soonest shift is highlighted with a blue left border and "SELECT" label
- Tapping a shift row selects it and populates the confirm button
- Primary CTA: "Clock In — [Client Name]" (full-width blue button)
- "Cancel" text link below

On confirm:
1. GPS captured in background
2. `EVVRecord` created with `capturedOffline = false` (or `true` if offline)
3. Sheet dismisses, navigates to the Visit screen for that shift
4. FAB turns red

**Wrong-shift recovery:** If the caregiver navigates to the visit screen and realizes they clocked into the wrong shift, they can tap "⚠ Wrong shift?" in the visit screen nav bar overflow menu to void the clock-in and return to the clock-in sheet. Voiding a clock-in deletes the EVVRecord if it was created less than 5 minutes ago.

**Offline behavior:** Clock-in still works offline. GPS captured from device. Event logged to local SQLite store. Synced to BFF on reconnect.

---

## 7. Visit Execution

Single scrollable screen. Accessed from the active visit banner, the FAB (when red), or directly from Today after clocking in. Back navigates to Today.

### Sticky mini-header

Always visible while scrolled: client name (left), live elapsed timer (right, `color-blue`, tabular numerals).

### Sections (top to bottom)

**1. Client hero (blue banner)**
- "IN PROGRESS" label (uppercase, muted white)
- Client name (large, bold, white)
- Service type · client address
- Clock-in time (left), elapsed timer (center, large), scheduled end time (right)

**2. GPS status bar**
- Green bar: "GPS captured · [distance] from client address" when within tolerance
- Amber bar: "GPS outside expected range — your agency will review this visit." when distance anomaly
- Offline bar: "Offline — GPS captured on device, will sync on reconnect"

**3. Care plan summary (reference)**
- Diagnoses, allergies, caregiver notes from the active care plan
- Collapsed by default on first visit, expanded subsequently (persisted preference). If the care plan was updated since the caregiver's last visit with this client, the section is forced open regardless of the saved preference, and a pill reads "Updated since your last visit".

**4. ADL Tasks**
- Section label: "ADL TASKS [X / Y]" — count updates live as tasks are checked
- Single grouped list: pending tasks above, completed tasks (strikethrough, green checkmark) below. As caregivers check off tasks, completed items sink to the bottom of the list. Tapping a completed task reverts it to pending.
- Pending tasks show a circular checkbox; tasks with instructions show subtext below the task name
- Tapping a pending task marks it complete immediately (optimistic update, queued for sync)

**5. Care Notes**
- Free-text field, full keyboard, auto-saves on blur
- Placeholder: "Add visit notes…"
- Queued for offline sync

**6. Offline warning (conditional)**
- Amber banner: "Offline — data saved locally, will sync on reconnect"
- Only shown when device has no connectivity

**7. Clock Out (bottom)**
- Dark (`color-dark`) full-width button: "Clock Out"
- Subtext: "GPS will be captured"
- Tapping captures GPS, closes the `EVVRecord`, navigates back to Today
- Confirmation required if fewer than 50% of ADL tasks are complete: "You have [N] tasks remaining. Clock out anyway?" with "Clock Out" and "Go Back" options

---

## 8. Open Shifts

List of available (unassigned) shifts broadcast by the agency.

### Shift cards

Each card shows: urgency label, client name, date/time, service type, duration, distance from caregiver home address.

**Left border colors:**
- Red — urgent/today shifts
- Grey — future shifts

**Inline actions (no separate confirm screen):**
- "Accept Shift" (blue primary button)
- "Decline" (ghost button)

Accepting sends the caregiver's response to Core API; card is removed from the list on success. **Shift acceptance requires connectivity** — if offline, the accept button is disabled with a tooltip: "Connect to the internet to accept shifts." Decline works offline (queued and synced on reconnect, but a declined shift holds no time-sensitive state).

**Empty state:** "No open shifts right now. We'll notify you when one becomes available."

---

## 9. Messages

Broadcast-only inbox. Caregivers receive messages from the agency and can reply. Caregivers cannot initiate new conversations.

### Inbox

- List of message threads, one per agency broadcast
- Each row: agency avatar (initials, `color-dark` background), thread subject, preview text, timestamp
- Unread threads: blue dot indicator, bold subject
- Red dot badge on the tab icon when any unread threads exist

### Thread view

- Standard chat bubbles: agency messages left-aligned (white, `color-border` border), caregiver replies right-aligned (blue background, white text)
- Sender label and timestamp below each bubble
- Reply bar pinned to bottom: text input + send button
- No compose button — thread is always agency-initiated

---

## 10. Profile

### Layout

- Avatar (initials, `color-blue` background, 52px circle)
- Caregiver name, agency name, primary credential type

### Sections

**Credentials**
- List of credentials with name and expiry status:
  - Green: "Valid · [Month Year]"
  - Amber: "Expires [Month Year]" (< 60 days)
  - Red: "Expired" (past expiry)

**This Month**
- Stats tile: Shifts completed, Hours worked — displayed as two equal columns. Note: On-time rate is a P2 metric pending scoring module support and is not included at MVP.

**Navigation rows**
- "Settings" → navigates to Settings screen
- "Sign Out" (red text) — Tapping Sign Out shows a confirmation dialog: "Sign out of hcare? You'll need a sign-in link to log back in." with "Sign Out" (red) and "Cancel" options.

---

## 11. Settings

Read-only screen — no user-editable fields at MVP.

Displayed values:
- **Notifications:** On / Off (reflects current OS permission state — not a toggle). Includes a "Change →" link that opens the app's page in the OS Settings app via `Linking.openSettings()`.
- **Location access:** Always / When In Use / Denied (reflects OS permission state). Includes a "Change →" link that opens the app's page in the OS Settings app via `Linking.openSettings()`.
- **Agency:** Agency name + support contact email
- **App version:** Semver string
- **Terms of Service / Privacy Policy:** Links only

---

## 12. Offline Behavior

The app is fully offline-capable during a visit. All visit events (clock-in, task completions, notes, clock-out) are written to local SQLite (via Expo SQLite) as an append-only event log before being sent to the BFF.

**Connectivity indicator:** A small amber banner appears at the top of the visit screen (and as a status bar indicator globally) when offline. No blocking UI — all primary actions remain available.

**Sync:** On reconnect, the local event queue is batched and POSTed to the BFF `/sync/visits` endpoint. The BFF deduplicates by `(deviceId, visitId, eventType, occurredAt)`.

**Conflict resolution:** The app batches local events and POSTs them to the BFF `/sync/visits` endpoint. The BFF forwards the batch to Core API, which is the sole authority for computing EVV compliance status. The mobile app never sets or infers compliance status — it receives the computed status from Core API as part of the sync response. Core API evaluates time anomaly thresholds (including offline clock-in drift) and returns the resulting status.

**Conflict: shift reassigned while offline.** When the BFF returns CONFLICT_REASSIGNED during sync, the app:
1. Sends a push notification: "Your [Date] visit for [Client Name] could not be recorded — the shift was reassigned while you were offline. Contact your agency."
2. Displays a persistent banner on the Today screen: "Visit not recorded — [Client Name] shift was reassigned. Tap for details."
3. Shows a detail screen with the conflict explanation, the time the caregiver clocked in and out, and a "Contact Agency" button that opens a pre-filled message thread.

**Sync success confirmation:** On successful sync, a transient toast appears for 3 seconds: "Visit data synced ✓". No action required.

---

## 13. Notifications

Push notifications via Expo Notifications (FCM + APNs). Payloads contain **no PHI** — only `shiftId`, `threadId`, or event type codes. The app fetches details after receipt.

**Device registration:** On first launch after auth, the app must register the device push token with the BFF via `POST /mobile/devices/push-token`. The token must be re-registered on each app launch in case it has rotated.

| Trigger | Notification text |
|---|---|
| Open shift broadcast | "New shift available — tap to view" |
| Clock-in reminder (15 min before shift) | "Your shift starts soon — tap to clock in" |
| Agency message received | "New message from [Agency Name]" |
| Credential expiring (30 days out) | "Your [Credential] expires soon — contact your agency" |

Tapping a notification deep-links to the relevant screen (Open Shifts tab, visit, Messages thread, or Profile credentials).

---

## 15. Error States

Error states for all core user flows. Every error must give the caregiver a clear recovery path — no silent failures.

**(a) GPS capture failure at clock-in**
If GPS is unavailable (permission denied post-onboarding or device GPS fix fails) when the caregiver confirms clock-in, show an amber bar below the confirm button: "Location unavailable — your agency will verify this visit manually." Clock-in proceeds with a null GPS coordinate. The `EVVRecord` is created with `gpsCoordinate = null`. The caregiver is not blocked.

**(b) GPS capture failure at clock-out**
If GPS is unavailable at clock-out, show the same amber bar: "Location unavailable — your agency will verify this visit manually." Clock-out proceeds with a null GPS coordinate for the clock-out event. The caregiver is not blocked.

**(c) Sync failure after reconnect**
If the BFF `/sync/visits` POST returns an error (network error or server error) after reconnect, show a persistent banner at the top of the screen: "Sync failed — tap to retry" with a retry button. The local SQLite event log is preserved. Data is not lost. The banner persists until sync succeeds.

**(d) Shift acceptance failure (network error)**
If the accept shift request fails due to a network error or server error (500 or 409), show an inline error below the Accept button: "Couldn't accept shift — tap to retry." The card remains in the list. No state is changed on the card.

**(e) Message send failure**
If posting a reply to a message thread fails, show an inline error below the reply bar with a retry option: "Message not sent — tap to retry." The typed message is retained in the input field.

**(f) Auth token exchange failure (deep link)**
If the token exchange API call fails for reasons other than an expired token (network error, server error), show the "Link expired" error screen with email input. This provides a recovery path via email regardless of the failure cause.

---

## 14. Screen Inventory

| Screen | Notes |
|---|---|
| Login | Cold open / re-auth fallback — email input + send link |
| Deep link handler | Token exchange → onboarding or Today |
| Link expired | Error state with email input |
| Onboarding: Welcome | First login only |
| Onboarding: Notifications | OS permission prompt |
| Onboarding: Location | OS permission prompt |
| Today's Schedule | Main hub |
| Clock-in bottom sheet | FAB tap |
| Visit Execution | Single scrollable screen |
| Clock-out confirmation | Modal (only when < 50% tasks done) |
| Care Plan (read-only) | Push from expanded shift card — shows active care plan summary, ADL tasks, goals |
| Open Shifts | Tab |
| Messages — Inbox | Tab |
| Messages — Thread | Push from inbox |
| Profile | Tab |
| Settings | Push from profile |

---

*Spec written: 2026-04-08*
