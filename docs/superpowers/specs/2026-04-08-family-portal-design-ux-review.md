# Family Portal Design Spec — UX Review

**Reviewer:** UX Researcher (voltagent)
**Date:** 2026-04-08
**Spec reviewed:** `docs/superpowers/specs/2026-04-08-family-portal-design.md`

---

## Summary Judgment

The spec has a solid foundation. The dashboard information hierarchy is correct, the language on the happy path is genuinely good, and the decision to keep the portal completely separated from the admin shell is right. The critical problems are concentrated in three areas: the 15-minute invite TTL is mismatched to how the invite actually travels to the family member; the portal session has no expiry recovery path; and the error states are visually under-weighted at exactly the moments a worried non-technical user most needs clear guidance.

---

## 1. Onboarding and First-Use Experience

**The 15-minute token TTL will cause frequent failures and erode trust in the agency.**

The spec describes a fully manual delivery chain: admin generates a link, copies it, pastes it into a text message or email, sends it. The family member then needs to open it. In practice that chain routinely takes more than 15 minutes. The admin may send it mid-conversation, the family member may be driving and open it later, the text may sit unread. When it fails, the family member lands on an error screen with no explanation other than "ask your care coordinator for a new one." From the family member's perspective, the product is broken. They called the agency, the agency "set them up," and now it doesn't work.

**Recommendation:** Extend the TTL to 72 hours. The security argument for 15 minutes is weak here: the token is a one-time-use random 64-byte hex value sent over HTTPS. A stolen invite link still only grants read access to a care summary. 72 hours is the industry standard for magic links (Notion, Linear, Slack all use 24–72 hours). If a shorter window is required for compliance reasons, 24 hours is the minimum that is workable with manual delivery. Whatever the TTL, display it prominently in the admin UI — not as a `text-[10px] text-text-muted` footnote.

**No re-invite affordance is visible in the user list.**

Once a `FamilyPortalUser` row exists, the spec shows the row has a name, email, last login, and a "Remove" button. There is no "Resend" or "Generate New Link" action on the existing row. An admin who needs to re-invite someone (link expired, family member lost the message) must... do what exactly? The spec does not say. The `findOrCreate` semantics on the invite endpoint mean the admin can generate a new link by going through the invite form again, but only if they know to do that. The tab needs a "Send New Link" action on existing user rows.

**The family member receives no onboarding context.**

The verify page auto-submits immediately and redirects to the dashboard. There is no moment of orientation: no "You're now viewing [Agency Name]'s care updates for Margaret" screen, no explanation of what they're looking at or how often it updates. For a non-technical user opening a link they received from an agency they may have spoken to once on the phone, arriving instantly on a dashboard of care information without context is disorienting. A single transitional screen — even a brief "Welcome, you can check on Margaret's care here anytime" — would materially reduce confusion.

---

## 2. Information Hierarchy

**The hierarchy is fundamentally correct.** Today's visit status is the first thing a family member wants to know. The upcoming schedule provides reassurance about continuity of care. The last note grounds the experience in reality. This ordering is right.

**One structural gap: no "last completed visit" status separate from the note.**

The spec uses `lastVisitNote` mapped to `Shift.notes`. If the caregiver did not enter notes (which in practice is common — note completion rates on caregiver apps typically run 40–60%), `lastVisitNote` is `null` and the card simply does not appear. This means a family member checking on an elderly parent sees today's upcoming visit and... nothing about whether yesterday's visit actually happened. There is no "Margaret's last visit was completed on April 7 at 11:03 AM" confirmation when notes are absent. The family member has no way to know the visit occurred.

**Recommendation:** Separate the "last visit completed" fact from the notes content. Always surface the completion timestamp and duration of the most recent completed shift. Render the note text conditionally inside that same card only when notes exist. The card disappears entirely in the null-notes case, which is the wrong default for a reassurance-focused product.

**The "upcoming visits" section shows max 3 entries, but the cap is invisible to the user.**

If a client has 7 upcoming visits scheduled, the family member sees 3 with no indication there are more. They may reasonably believe care ends after those 3 visits. A "View full schedule" link (even if it goes nowhere in MVP, it signals completeness) or a simple "Showing next 3 of 7 visits" count would prevent this misread.

---

## 3. Clarity of Language

**Strengths:**

"Maria is here now" is excellent — specific, warm, immediate. It directly answers the question a family member is actually asking. "Visit completed at 11:03 AM" is precise and honest. These are the right choices.

**Problems:**

"Expected at 9:00 AM" for the GREY/scheduled state is ambiguous. "Expected" in healthcare language typically means the caregiver is expected but has not yet confirmed. But a family member reading this at 8:45 AM does not know whether "expected" means "we think she'll be there" or "she's confirmed and en route." More importantly, at 9:30 AM — when the visit was supposed to start and the caregiver has not clocked in — this label is still "Expected at 9:00 AM." The visit is now late, and the portal is showing the same label it showed an hour ago. There is no late-visit state.

**Recommendation:** Add a "Running late" or "Visit hasn't started yet" state that triggers when the current time is more than 15–20 minutes past `scheduledStart` and status is still GREY. The label could read "Scheduled for 9:00 AM — not yet started" to be factual without being alarming.

"No visit scheduled for today" is clear and honest. No changes needed.

The spec does not define what label appears when `todayVisit` has status CANCELLED. This is a meaningful gap — family members need to know a visit was cancelled, not just absent from the schedule.

**The portal header says "Margaret's Care" — this is the right register.** Possessive + first name is warm and specific without being clinical.

---

## 4. Error States

**The verify page error message is acceptable but contains a jargon risk.**

"Ask your care coordinator for a new one." — "care coordinator" is the right term in many agencies but not all. Some agencies use "scheduler," "office staff," or the coordinator's first name. In MVP with manual link delivery, the family member likely knows who sent them the link, so this is tolerable. However, the message should at minimum include a phone number or a generic "contact the agency" instruction since the family member has no other way to initiate re-contact from within the product.

**The dashboard network error message has a platform mismatch.**

"Unable to load. Pull to refresh." — pull-to-refresh is a mobile native gesture. On a desktop browser, there is no pull gesture. A family member on a laptop will have no idea what this means. Specify separate behaviors: on mobile-sized viewports, "Pull to refresh" is appropriate; on wider viewports, use "Tap to retry" or show a visible retry button.

**Error messages are visually under-weighted.**

The spec renders the network error in `text-text-secondary` — the same muted gray used for timestamps and metadata. Error states must be visually distinguished. This is not the admin app where understated design is a brand choice; this is a consumer experience where a confused older adult needs to know immediately that something went wrong and what to do. Use `text-text-primary` at minimum, with a visible error icon. The token expiry error on the verify page is adequately described as a standalone state but its visual treatment is not specified — confirm it is distinct from the loading spinner state, not just a text swap in the same color.

**The "Remove" user action in familyPortalTab has no confirmation state specified.**

The test spec for `FamilyPortalTab.test.tsx` references "remove confirmation" — but the design spec does not describe the confirmation interaction at all. Does it show an inline confirmation prompt? A modal? A toast? This is unresolved. Accidentally removing a family member's portal access is a meaningful mistake; the admin would need to re-invite them and ask them to click a new link.

---

## 5. Admin Usability (familyPortalTab)

**The expiry warning is buried and illegibly small.**

The TTL is communicated via `text-[10px] text-text-muted` — this is the smallest, lowest-contrast text in the entire design system. The 15-minute window is the single most operationally critical piece of information on the screen after the link itself. If an admin generates the link and then spends two minutes finishing a phone call before sending it, they lose 13 of their 15 minutes. The expiry must be visually prominent — at least `text-[12px] text-text-secondary` — and ideally includes a countdown or a "generated at 2:34 PM, expires at 2:49 PM" timestamp pair.

**The spec contradicts itself on minimum font sizes.**

Section 5 (Visual Design, Portal Dashboard) states "all sizes >= 12px — optimised for readability on mobile." The same section specifies the expiry note at `text-[10px]` and the "No visit scheduled for today" label at `text-[11px]`. Both violate the stated minimum. The invite form is admin-facing and exempt from the portal's accessibility commitment, but `text-[10px]` should not appear anywhere in this product.

**There is no visual distinction between an active portal user and one who has never logged in.**

The user row shows "Last login" meta. A user who was invited but never clicked the link would show either no last-login date or a null/dash value. The spec does not define what "Last login" shows for a user who was created but has never authenticated. It should read "Never logged in" (not a blank, not a dash) so the admin knows the invite may need to be resent.

**The "+ Invite" button label is unclear for returning to an existing user.**

If a `FamilyPortalUser` already exists in the list (they've been invited before), and the admin clicks "+ Invite" to generate a new link for them, the form opens and they enter the same email. This works technically (`findOrCreate` handles it), but the admin has no feedback that they are re-inviting an existing user rather than adding a new one. An inline message like "A new link will be sent to this existing user" would prevent confusion about whether duplicate records are being created.

---

## 6. Missing States and Edge Cases

**No portal JWT expiry recovery.**

The spec establishes `portalAuthStore` with `{ token, clientId, agencyId }` but specifies no JWT expiration time, no refresh mechanism, and no behavior when the JWT expires. When `PortalGuard` finds no portal token (expired or cleared), it redirects to `/portal/verify` without a token param, showing the "invalid or expired link" message. From the family member's perspective this is indistinguishable from a broken link. They have no path back to the dashboard without asking the agency for a new invite.

This is the most significant missing state in the spec. The portal needs either: (a) a defined JWT expiry with a "your session expired, request a new link from the agency" message that is clearly differentiated from "your invite link expired", or (b) a much longer-lived JWT (7–30 days) that functions more like a persistent account session.

**No cancelled visit state.**

The `todayVisit` status enum handling covers IN_PROGRESS, GREY (scheduled), and COMPLETED. There is no CANCELLED state. If a caregiver calls out and the shift is cancelled, `todayVisit` presumably still returns the shift object with status CANCELLED, and the dashboard has no defined behavior for it. The family member would see no visit, which may cause alarm. A "Today's visit has been cancelled. Please contact your care coordinator." message is needed.

**No handling for multiple visits in a day.**

Some clients receive two visits per day (morning personal care, evening medication management). `todayVisit` is a single object. The spec does not address this. If only one visit is returned, which one? If the morning visit completed and the evening visit is upcoming, the family member should see both. The API shape and the dashboard layout both need to account for this.

**No "returning user" experience after portal JWT has been used for a while.**

The spec describes the onboarding flow but not what happens when a family member bookmarks the dashboard and returns the next day. If the JWT is still valid, they land on the dashboard — this is correct. If the JWT has expired, they see an error. The gap is the intermediate case: a family member who has been using the portal for two weeks sees their JWT expire with no in-product mechanism to re-authenticate. The design needs to specify this.

**No time zone handling is mentioned anywhere.**

`scheduledStart` and `clockedInAt` are ISO 8601 timestamps without timezone specification in the JSON response. The client's timezone (or the agency's timezone) is not mentioned. A family member in a different timezone from the client would see incorrect times. The dashboard should always display times in the client's service timezone, explicitly labeled (e.g., "9:00 AM EDT").

**No "no upcoming visits" state for the upcoming visits section.**

If a client has no shifts scheduled beyond today, `upcomingVisits` is an empty array. The spec does not define what the upcoming visits card shows in this case. A blank section is confusing. "No visits scheduled yet — contact your care coordinator to confirm the schedule" is the appropriate empty state.

**`PortalLayout` has no logout mechanism specified.**

The spec states `PortalLayout` is a "minimal layout wrapper — no sidebar, no Shell." There is no mention of a logout or sign-out action anywhere. The family portal may be accessed on a shared device (a family member's iPad used by multiple people, a library computer). The spec must specify either a logout button in `PortalLayout` or a session-clearing mechanism. At minimum, closing the browser tab should clear the portal session from `portalAuthStore` — the spec should explicitly state whether the Zustand store is persisted to `localStorage` (which would survive tab close) or is in-memory only (which would not).

---

## 7. Accessibility Concerns

**Color is the sole differentiator between the scheduled and completed status pills.**

The "Scheduled" pill uses a gray fill and gray dot. The "Completed" pill uses a similar treatment with gray text. The spec says "similar to scheduled but text reads 'Visit completed at 11:03 AM'" — meaning only the label text distinguishes them. The IN_PROGRESS state has a distinct green fill and green dot. An older user with diminished color perception will struggle to distinguish completed from scheduled at a glance. The completed state should have a visually distinct treatment beyond color — a checkmark icon is the obvious choice.

**The `bg-dark` verify page creates an abrupt context shift.**

The verify page reuses the `LoginPage` style with a dark (`#1a1a24`) background. The dashboard uses a light `bg-surface` (`#f6f6fa`) background. For a family member landing on the dark verify page and immediately being redirected to the light dashboard, this is a jarring flash. More importantly, the dark background of the verify page signals "admin login" to any user who has seen the admin interface. The consumer portal should establish its own visual identity from the first screen. Consider a `bg-surface` or white verify page rather than reusing the admin login aesthetic.

**Touch targets are unspecified for mobile.**

The spec is described as optimized for mobile but specifies no minimum touch target sizes. The "Remove" ghost button on user rows in the admin tab and any interactive elements in the upcoming visits list need to be a minimum of 44x44px per WCAG 2.5.5 (AAA) or at least 24x24px per the newer 2.5.8 (AA). The spec should either call out minimum tap target sizing or reference the existing design system's standard.

**The auto-submit behavior on PortalVerifyPage is silent.**

The page auto-submits the token on mount with a spinner and "Signing you in..." text. There is no `aria-live` region specified. A screen reader user would have the link auto-activate with no announcement of what is happening. The spinner and the success/error state transition need `role="status"` or an `aria-live="polite"` region so assistive technology announces the outcome.

**Last visit note rendered in `text-text-secondary` italic at 13px.**

The combination of reduced font size, muted gray color, and italic styling compounds readability issues. The `text-text-secondary` color (`#747480`) on a white card background produces a contrast ratio of approximately 4.6:1, which passes WCAG AA at 14px+ but is marginal. Italic styling reduces legibility further at this size and color combination. For a consumer surface targeting older adults, the note text should be `text-text-primary` or at minimum a slightly darker value, rendered in normal weight.

---

## Prioritized Issues

| Priority | Issue |
|---|---|
| P0 | 15-minute token TTL is incompatible with manual link delivery — increase to 72 hours minimum |
| P0 | No portal JWT expiry recovery path — family members will be silently locked out |
| P0 | Cancelled visit state not defined — `todayVisit` with CANCELLED status has no dashboard behavior |
| P1 | No re-invite action on existing user rows in familyPortalTab |
| P1 | "Pull to refresh" error message does not work on desktop |
| P1 | No "last completed visit" fact when notes are null — family members get no visit confirmation |
| P1 | No time zone handling on displayed timestamps |
| P1 | Color-only differentiation between scheduled and completed status pills |
| P1 | `PortalLayout` has no logout mechanism; session persistence behavior unspecified |
| P2 | No orientation screen after first login |
| P2 | Late-visit state missing (visit past scheduled start, still GREY) |
| P2 | Multiple visits per day not addressed |
| P2 | "Never logged in" not defined as a `lastLogin` empty state |
| P2 | Upcoming visits section empty state not defined |
| P2 | Auto-submit on PortalVerifyPage needs `aria-live` region |
| P2 | `text-[10px]` and `text-[11px]` usages violate the spec's own "all sizes >= 12px" rule |
| P3 | "Care coordinator" terminology may not match agency-specific language |
| P3 | Upcoming visits cap of 3 is invisible to the user |
| P3 | Dark verify page creates jarring aesthetic context shift vs light dashboard |
