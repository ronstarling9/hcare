# Family Portal Design Spec — UX Review 2

**Reviewer:** UX Researcher (voltagent)
**Date:** 2026-04-08
**Spec reviewed:** `docs/superpowers/specs/2026-04-08-family-portal-design.md` (v2)
**Prior reviews:** `2026-04-08-family-portal-design-ux-review.md`, `2026-04-08-family-portal-design-critical-review-1.md`

---

## What v2 Fixed

The revision addressed every P0 from the first UX review and most criticals from the design review. Specifically:

- Token TTL extended from 15 minutes to 72 hours — correct call, correctly surfaced in UI with a readable expiry timestamp.
- Portal JWT expiry recovery is now handled with three distinct `?reason=` states, each showing a differentiated message. This was the most structurally important gap in v1.
- CANCELLED visit state is now defined with appropriate message and visual treatment.
- Re-invite affordance ("Send New Link") is present on existing user rows.
- The "Pull to refresh" error copy is replaced with a visible retry button.
- Last visit completion fact is now separated from notes — the card always renders when a completed visit exists, and the note section is conditionally omitted. This is the right solution.
- Timezone handling is present: `agencyTimezone` returned by API, all timestamps labeled with IANA-converted timezone abbreviation.
- COMPLETED status pill now has a checkmark icon, not just color.
- Logout mechanism is present in `PortalLayout`.
- Late-visit GREY state added with amber styling at 15-minute threshold.
- `portalAuthStore` is now persisted to `localStorage` — correct for a one-time-use magic link context.
- SHA-256 hashing of invite token, nightly cleanup job, UNIQUE constraint on `FamilyPortalUser` — all addressed from the critical review.
- `JwtAuthenticationFilter` `clientId` extraction is now explicitly documented with a code snippet.
- Separate `portal.jwt.expiration-days` property established — 30-day expiry, independent of admin token expiry.

The v1 foundations were solid and the revision is materially better. The remaining issues below are either genuinely new issues introduced by the fixes, or issues neither prior review caught.

---

## 1. Onboarding and First-Use Experience

**The expiry timestamp notation in the admin UI will confuse schedulers.**

The spec displays the expiry as: "Generated at 2:34 PM, expires at 2:34 PM + 72 hrs (Apr 11)". The string "2:34 PM + 72 hrs" is arithmetic notation, not human language. A scheduler reading this mid-phone-call is not going to mentally add 72 hours. The "(Apr 11)" parenthetical does the actual work, but it appears after confusing notation. The message should read: "Link expires Apr 11 at 2:34 PM — valid for 72 hours." The human-readable conclusion should come first; the technical duration second if at all.

**The invite form does not signal re-send context when opened from "Send New Link".**

When a scheduler clicks "Send New Link" on an existing user row, the form opens with the email pre-filled. The spec says the "A new link will be sent to this existing user" note appears "if re-inviting an existing user" — but it is not explicit about when this note appears. If it appears only after the admin submits the form (server returns and frontend detects the existing user), the admin has already clicked "Generate Link" without knowing they are re-inviting. If the form is opened from "Send New Link," the system already knows this is a re-invite. The confirmation note must appear immediately when the form opens in re-invite context, not after submit. The spec is ambiguous on this and should be made explicit.

**No description of what happens when the "Copy" button is used and then the form is closed.**

Once the link is generated and the admin copies it, there is no instruction to close the form. If the admin copies the link and navigates away or closes the panel without dismissing the form, the generated link persists in the UI until the next open. If they reopen the panel and see a stale link still visible, they may send the old (possibly already-used) link. The spec should state whether the invite form closes or resets after a successful copy, or at minimum after a configurable timeout.

---

## 2. Information Hierarchy

**The dashboard header shows today's date but not the time, and "today's visit" is inherently time-sensitive.**

The header shows the current date at `text-[12px] text-text-secondary`. A family member opening the dashboard at 7 AM to check if a 9 AM visit will happen is looking for time-relative information. Showing only the date provides no context for how close the scheduled visit is. The Today's Visit card does show "Maria is scheduled for 9:00 AM" — but without a current time reference, a user cannot quickly judge whether that is 15 minutes away or 3 hours away. A clock — even a static display of the current time in the agency timezone — would make the header substantively more useful for the primary use case.

**COMPLETED visit: "Visit completed at 11:03 AM" renders in `text-text-secondary` — the most reassuring message on the screen is the least visible.**

The `COMPLETED` state uses the muted gray `text-text-secondary` (#747480) for its label, the same color used for timestamps and metadata throughout. When a family member's primary anxiety is "did anyone show up for my parent today," the completion confirmation is the single most important positive signal the product can deliver. Rendering it in muted gray underweights it. The label should use `text-text-primary` or a positive-signaling color. The checkmark icon added in v2 helps but does not compensate for low-contrast text on the most meaningful state.

**The upcoming visits section shows a maximum of 3, still with no count context.**

This was flagged as P3 in the first review and not addressed. For a product serving family members managing ongoing care, seeing three visits and not knowing whether that represents all scheduled care or a slice of a much longer calendar is a meaningful gap. A simple "Showing next 3 visits" line resolves the ambiguity at zero design cost.

---

## 3. Clarity of Language

**"Sign out" navigates to `?reason=no_session`, which shows "No active session found. Ask your care coordinator for an access link."**

This is a new problem introduced by the v2 fix. The `PortalLayout` "Sign out" action clears the store and navigates to `/portal/verify?reason=no_session`. The `no_session` message was designed for users who arrive at the dashboard without ever having authenticated. A family member who intentionally signs out receives a message that reads as if something went wrong ("No active session found") and a call to action ("Ask your care coordinator for an access link") that is wrong — they just signed out voluntarily, they do not need a new link. The spec needs a `?reason=signed_out` state with a message like "You've been signed out. Use your original link or ask your care coordinator to send a new one." The `no_session` state should be reserved for unauthenticated arrivals.

**"No active session found" and "Your session has expired" are functionally identical instructions but create different urgency.**

These are correctly distinguished at the UI level, but neither message tells the family member what "asking their care coordinator" means in practice. Both messages land as dead ends on a phone screen. At minimum, a contact number or agency name on the verify page would give the user a way forward. This is especially important for the session-expired state, where the user may have been a regular portal user for weeks before their 30-day JWT expired without warning.

**"Scheduled for 9:00 AM — not yet started" (late GREY state) has no action instruction.**

This label correctly avoids false alarm while signaling that something is off. But it has no action instruction. The CANCELLED state says "Contact your care coordinator." The late state says nothing. A family member at 9:20 AM seeing "Scheduled for 9:00 AM — not yet started" is likely anxious and has no in-product path to act on that anxiety. The message should append the same "Contact your care coordinator." instruction from the CANCELLED state.

**The `CANCELLED` state does not specify whether the caregiver card is shown or hidden.**

The spec does not explicitly say the caregiver avatar is hidden in the CANCELLED state. If the caregiver card renders alongside "Today's visit was cancelled," the family member may wonder whether Maria specifically cancelled or whether the visit was rescheduled. The spec must explicitly state whether the caregiver card is shown or hidden when status is CANCELLED.

---

## 4. Error States

**JWT revocation gap: removing a family portal user does not invalidate their JWT.**

When an admin clicks "Remove" and confirms, the `FamilyPortalUser` row is deleted. The family member's 30-day JWT stored in browser `localStorage` is still valid. On their next dashboard visit, `PortalGuard` will pass (valid JWT), and the `GET /family/portal/dashboard` API call will succeed or fail depending on how the backend handles a JWT whose `fpUserId` no longer exists. The spec does not describe this behavior. If the backend loads the `FamilyPortalUser` to validate access, it will throw a 404 or 403 — but that error state is not in the error handling table. If it relies solely on the `clientId` claim, a removed user continues receiving dashboard data for up to 30 days. Neither outcome is specified, and both are wrong.

Agencies remove family portal access when family dynamics change or when a client is discharged. The expected behavior must be defined.

**The dashboard network error does not address stale cached data.**

React Query retries twice before showing the error. The spec does not address the scenario where React Query shows cached stale data rather than the error state. A family member seeing data from two days ago with no indication it is stale has a worse outcome than seeing the error state. The spec should either prohibit stale data display (show only the error state), or add a "Last updated [time]" indicator.

**The 409 conflict retry behavior is incomplete.**

The error handling table says "frontend retries once." What does the retry do? If the retry also fails, what is the terminal error message? "Retry once" without a terminal state is incomplete.

---

## 5. Admin Usability

**"Remove" confirmation copy says "immediately" but 30-day JWT remains valid.**

"Remove [name]? They will lose access immediately." is factually wrong. The admin confirms removal believing access is cut off, when in practice access continues for up to 30 days via the stored JWT. If revocation is accepted as a known limitation, the confirmation copy must be honest: "They will no longer be able to generate new links. Any active session they have will expire within 30 days."

**`text-[9px]` section label and `text-[11px]` button are below the 12px minimum.**

The spec states "all sizes >= 12px" under Portal Dashboard typography. The `familyPortalTab` section specifies `text-[9px]` section label and `text-[11px]` "+ Invite" button. If the admin tab is exempt, that exemption should be documented. If not, the sizes need correction.

**Invite form has no "Done" / auto-close behavior after copy.**

After the admin generates a link and copies it, the form has no close behavior specified. If the family member uses the link and the admin's form is still open, the now-expired token URL remains visible. The spec should specify whether the form closes after the copy action or remains open.

---

## 6. Missing States and Edge Cases

**No handling for a discharged/deactivated client.**

The spec assumes `clientId` in the JWT always corresponds to an active client. If a client is discharged and their record is deactivated, `GET /family/portal/dashboard` will either return stale data or a backend error. The family member should see "Care services for [first name] have concluded. Please contact the agency for more information." rather than a generic error.

**Multiple visits per day: "first non-cancelled" is ambiguous.**

If a morning personal care visit is COMPLETED and an afternoon medication management visit is upcoming, "first non-cancelled shift for today" by scheduled start time would return the morning completed shift — but the family member primarily wants to know about the afternoon upcoming visit. The API contract needs to specify which shift is returned and acknowledge the known limitation.

**No "new client, no visits yet" state.**

A family member given portal access before any visit is scheduled will see three empty or absent sections. The combined empty states read as abandoned care. A contextual "Getting started" state for users with zero visit history would reduce unnecessary calls to the agency.

**`?reason=` parameter not stripped from URL after read.**

The spec does not specify stripping `?reason=` using `history.replaceState` after reading it. A family member who copies the verify URL will propagate the error reason unnecessarily. The spec should require stripping `?reason=` from the URL bar after it is read.

---

## 7. Accessibility Concerns

**Late-visit amber state uses color alone to distinguish from on-time state.**

The `GREY` on-time state uses a gray dot; the `GREY` late state uses an amber dot. These are visually differentiated only by color. A clock icon for the late state (parallel to the checkmark for COMPLETED) would fix this.

**"Sign out" at `text-[12px]` likely fails the 44px touch target minimum.**

The spec requires "all interactive elements minimum 44px height on mobile viewports." "Sign out" at `text-[12px]` with ~18–20px line height will be well below 44px without explicit padding. The spec must address this.

**Error states on verify page have no semantic heading.**

The error states render `text-[14px] text-text-primary` with an icon but no `<h1>` or `<h2>`. A screen reader has no landmark heading to announce. Each state needs a visible heading (e.g., "Link expired", "Session expired") for assistive technology.

**No session expiry warning before the 30-day JWT expires.**

A family member who has been using the portal for 29 days will hit the session-expired message with no advance notice. A dismissible "Your access link expires in 3 days" warning would prevent the predictable monthly lockout for regular users.

---

## Prioritized Issues

| Priority | Area | Issue |
|---|---|---|
| P0 | Auth/Privacy | JWT revocation gap: removing `FamilyPortalUser` does not invalidate their active 30-day JWT |
| P0 | Language | "Sign out" navigates to `?reason=no_session` showing "No active session found" — wrong message for voluntary signout; needs `?reason=signed_out` state |
| P1 | Language | Late-visit GREY state has no action instruction; inconsistent with CANCELLED state which does |
| P1 | Accessibility | Late-visit amber state uses color alone; needs a clock icon parallel to COMPLETED checkmark |
| P1 | Error states | Dashboard error state does not address stale cached data |
| P1 | Admin/UX | "Remove" confirmation copy says "immediately" but 30-day JWT continues to be valid |
| P1 | Missing state | Discharged/deactivated client: `GET /portal/dashboard` outcome undefined |
| P2 | Admin/UX | Expiry notation "2:34 PM + 72 hrs" is arithmetic — rewrite to "Expires Apr 11 at 2:34 PM" |
| P2 | Admin/UX | Re-invite context note must appear immediately when form opens from "Send New Link", not only after submit |
| P2 | Information | COMPLETED status in `text-text-secondary` — most reassuring state is most visually understated |
| P2 | Missing state | Zero-visit new client state reads as abandoned care rather than not-yet-started |
| P2 | Missing state | Multiple visits per day: "first non-cancelled" is ambiguous when completed morning + upcoming afternoon both exist |
| P2 | Accessibility | "Sign out" at `text-[12px]` likely fails 44px touch target minimum without explicit padding |
| P2 | Accessibility | Error states on verify page have no semantic heading for screen readers |
| P3 | Admin/UX | Invite form has no "Done" / auto-close behavior after copy |
| P3 | Language | Upcoming visits capped at 3 with no count context |
| P3 | UX | No current-time display in dashboard header |
| P3 | Admin | `text-[9px]`/`text-[11px]` in admin tab — below 12px minimum if rule applies globally |
| P3 | UX | `?reason=` not stripped from verify page URL after read |
| P3 | UX | No session expiry warning before 30-day JWT expires |
