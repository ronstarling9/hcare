# hcare Caregiver Mobile App Design Spec — UX Review

**Reviewer:** UX Researcher
**Date:** 2026-04-08
**Spec reviewed:** `docs/superpowers/specs/2026-04-08-mobile-app-design.md`
**Reference context:** `docs/superpowers/specs/2026-04-04-hcare-mvp-design.md` (sections 6 and 8)

---

## Summary Judgment

The core interaction model is sound: center FAB for clock-in, a single scrollable visit screen, offline-first architecture, and a Today screen as the hub. These are the right structural decisions for a field-use caregiver app. The problems are concentrated in four areas that will cause measurable harm at launch: the typography scale is dangerously small for the target demographic and outdoor use; the ADL task list ordering is inverted; the Sign Out tap has no confirmation and the re-auth path that follows it is high-friction; and the offline conflict resolution scenarios described in the MVP spec are not handled in the mobile UI at all. Several missing states — shift cancellation, completed-visit review, CONFLICT_REASSIGNED recovery — will generate support volume immediately.

---

## 1. Task Flows

**The clock-in flow has a critical selection constraint that will cause wrong-shift clock-ins.**

Section 6 specifies that the clock-in bottom sheet shows "the next 2 upcoming shifts for today only." A caregiver with three or more visits today who needs to clock into their second or third shift cannot select it from this sheet — only the next two appear. More critically, if a caregiver accepts an open shift that lands mid-day, it may not appear in the top 2. There is no fallback to select any shift not in the top 2.

The more dangerous scenario: a caregiver accidentally clocks into the wrong shift (e.g., selects the highlighted "first" entry by habit without reading the client name carefully). The spec provides no mechanism to clock out of a wrong-shift mistake before the EVV record is created. Clock-in should either show all of today's remaining shifts (not an arbitrary cap of 2) or provide an "I clocked into the wrong shift" recovery path immediately after navigating to the visit screen.

**The ADL task list ordering is inverted.**

Section 7 specifies: "Single grouped list: completed tasks (strikethrough, green checkmark) above pending tasks." This is backward. Completed tasks moving to the top forces the caregiver to scroll past everything they have already done to reach the remaining work. Every major task management interface (iOS Reminders, Todoist, any checklist app) moves completed items below pending ones. A caregiver mid-visit with a client standing next to them does not have time to scroll past four completed items to find the two remaining tasks. Completed tasks must sink below pending tasks, not rise above them.

**The clock-out confirmation threshold is miscalibrated.**

Section 7 triggers a "you have [N] tasks remaining" confirmation only when fewer than 50% of ADL tasks are complete. A caregiver with a 10-task list who completes 5 (exactly 50%) gets no confirmation at all. A caregiver with a 3-task list who completes 1 (33%) gets a confirmation. The rule is arbitrary and produces inconsistent outcomes. For a 2-task list, completing 1 task (50%) means clocking out with one task undone — with no warning. The trigger should fire on any incomplete tasks, or at minimum the threshold should be configurable at the care plan level per client severity. The current "<50% rule" will generate EVV records with incomplete ADL documentation on visits where the caregiver would have welcomed a prompt.

**The "Maps" action opens a new app, breaking visit context.**

The "Maps" secondary button on the expanded next-shift card (Section 5) opens native maps. On most phones this fully exits the hcare app and the caregiver must manually return. During an active visit with a running timer this is disorienting. The spec should note that Maps deep links should return to the app via universal link, or that the timer continues in the background — neither is specified.

**There is no "undo" for accidental task completion.**

Section 7 specifies that tapping a pending task marks it complete immediately (optimistic update). There is no mechanism to un-check a task if a caregiver taps it by mistake. Given the touch targets on a scrollable list and the demographic (caregivers assisting clients who may bump the phone), accidental task completion is foreseeable. A completed task should be tappable to revert to pending.

---

## 2. Cognitive Load

**The navigation during an active visit creates a redundant path problem.**

During an active visit, the caregiver has three ways to reach the visit screen: the blue active-visit banner on Today, the red FAB, and the "Continue Visit" button inside that banner. The Today screen during an active visit shows a full shift list ("LATER TODAY" plus "THIS WEEK"), which is irrelevant to the caregiver's current task. The Today screen during an active visit should be radically simplified — the active-visit banner should dominate and the shift list should be secondary. The spec does not differentiate the information hierarchy between active-visit and idle states.

**The center FAB dual-state requires learned behavior the spec does not teach.**

Section 3 describes the FAB changing from blue (clock-in) to red (stop/active visit) with an icon change. This convention is not intuitive for caregivers who are not heavy smartphone users. The onboarding flow teaches permissions but says nothing about how the FAB works or what the red state means. A first-time user seeing a red stop icon mid-visit may not know whether they triggered something accidentally. The first clock-in should show a brief tooltip: "Tap here to return to your visit."

**The "THIS WEEK" section on Today creates attention fragmentation.**

Section 5 shows "THIS WEEK" shifts below today's, "always shown," at dimmed opacity. For a user trying to identify what to do right now, future shifts are visual noise. "THIS WEEK" should be collapsed by default behind a "Show this week" disclosure toggle.

**The GPS status bar amber state gives the caregiver no actionable guidance.**

"GPS outside expected range" tells the caregiver something is wrong but not what to do about it. The message should include a brief action: "GPS outside expected range — your agency will review this visit." Without this, caregivers will call the agency for every amber GPS event.

**The Settings screen shows actionable permission states with no path to act.**

Section 11 shows "Notifications: On / Off" and "Location access: Always / When In Use / Denied" with no toggle or link to OS settings. A caregiver who sees "Notifications: Off" has no way to fix it from within the app. The Settings screen must include a "Change in Settings" button that opens the OS settings panel via `Linking.openSettings()`.

---

## 3. Offline UX

**The offline state is communicated inconsistently across sections.**

Section 12 states a "small amber banner appears at the top of the visit screen (and as a status bar indicator globally) when offline." Section 7 specifies a separate "Offline warning (conditional)" amber banner within the visit screen. This creates two offline indicators simultaneously on the visit screen. The spec must define whether these are the same element or two separate visual treatments — and if two, how they coexist visually.

**The CONFLICT_REASSIGNED scenario has no caregiver-facing UI defined.**

The MVP spec (Section 6, Offline Sync Design) defines: "Caregiver sees a notification explaining the shift was reassigned. EVV record is not created." The mobile spec defines no screen, notification text, or state for this conflict. A caregiver who worked a 3-hour offline visit and reconnects to a CONFLICT_REASSIGNED response has just done unrecorded work. The notification needs a specific explanation, a timestamp, and guidance on what the caregiver should do. The current spec leaves caregivers with no path forward.

**Sync completion is silent.**

Section 12 describes offline → reconnect → sync batch posted with no defined UI for success. After working offline, caregivers deserve confirmation: "Your visit data has been synced." Without this, caregivers will be uncertain whether their clock-out, task completions, and notes were saved — especially in areas with intermittent connectivity.

**The `capturedOffline = true` status is invisible to the caregiver.**

For caregivers in states requiring real-time submission (NJ, NY), offline records generate YELLOW compliance status. The caregiver is never warned at clock-in that working offline creates a compliance risk. A tooltip at offline clock-in — "Clocking in offline. Your agency may need to verify this visit." — is appropriate for real-time submission states.

---

## 4. Auth Flow

**The re-auth path assumes email access caregivers may not have readily available.**

A caregiver opening the app to clock in at 7:45 AM, whose session expired overnight, must switch to email, find a message from an agency domain they may not recognize, tap a deep link, and return — before their shift starts. The JWT lifetime is not stated anywhere in the mobile spec. If the JWT is 30 days, re-auth is infrequent. If it is 7 days, it is a weekly obstacle for this demographic. The JWT lifetime must be specified.

**The re-auth button label does not communicate the email-send mechanism.**

"Send New Sign-In Link" requires the caregiver to know that tapping sends an email to the address they typed. For non-tech-savvy users, this is not obvious. The label should be "Email Me a Sign-In Link" or "Send link to [email]."

**JWT expiry mid-visit behavior is undefined.**

If a JWT expires during an active visit, the spec defines no behavior. If the app redirects to the login screen mid-visit, the caregiver loses visit context. The spec must state: JWT expiry mid-visit triggers a silent background refresh attempt, not a session-clearing redirect.

**The location permission "Not now" path does not explain consequences.**

The onboarding step says "Not now" is always available but does not explain that declining location access means the EVV record will lack GPS verification, which may create downstream compliance issues for the caregiver's shift record. Caregivers who decline will later encounter GPS-related friction with no explanation of why it matters.

---

## 5. Missing Affordances

**No way to view a completed past visit.**

Today screen shows completed shifts with a green left border. Tapping a completed shift card does nothing — the spec does not define tap behavior for completed cards. A caregiver who wants to check their notes on a completed visit or verify their clock-out was recorded has no path to that information. Tapping a completed shift card should open a read-only summary: clock-in time, clock-out time, tasks completed, notes entered.

**No shift cancellation state visible to the caregiver.**

If a scheduler cancels a shift the caregiver is expecting, the spec defines no behavior on the Today screen. Does the card disappear silently? Does a push notification fire? The spec defines notifications for open shift broadcasts and clock-in reminders but not cancellations. A caregiver who drives to a client's home because the app showed a scheduled shift that was cancelled is a real-world failure mode with operational and safety implications. Shift cancellation must surface as a push notification and a cancelled-state card on Today.

**No mileage field at clock-out.**

The MVP spec lists mileage tracking as "Not in MVP." However, caregivers reimbursed for mileage need to track it somewhere. A simple optional mileage number input on the clock-out flow — not stored for EVV, just surfaced to the scheduler — would satisfy this. Caregivers will immediately ask for this.

**No emergency contact or client address access outside of an active visit.**

The "Maps" action is only available on the expanded next-shift card. If a caregiver is en route and the shift card is no longer in expanded state, they cannot find the address. Client address should be accessible from any shift card tap.

**Messages tab has no urgency differentiation.**

All agency messages look identical regardless of urgency. An emergency message from the scheduler looks the same as a payroll reminder. The spec should define whether urgent messages get visual priority treatment.

**No shift history beyond "This Month."**

Caregivers disputing payroll for the prior month have no data in the app to reference. A "Past shifts" link with a paginated list would address this. At minimum the spec should acknowledge the gap.

---

## 6. Potential Friction Points

**Sign Out with no confirmation will generate accidental logouts and high-volume support requests.**

Section 10 explicitly states: "taps require no confirmation (session clear is low-risk; re-auth is self-serve)." This judgment is incorrect for this demographic. Re-auth is not self-serve for a caregiver who is not comfortable with magic links. An accidental tap on "Sign Out" during an active visit will result in a session-cleared app that requires email access to restore — a potentially unrecoverable situation without calling the agency. Sign Out must have a confirmation dialog: "Sign out of hcare? You'll need a sign-in link to log back in."

**The FAB tap zone competes with adjacent tabs on small phones.**

On a 320px-wide device (iPhone SE 1st gen), five tabs divide the bar into ~64px slots each. The tabs immediately adjacent to the FAB are likely under 44px in practice due to the FAB overlap. Misfire rates will be elevated for users with larger fingers or reduced fine motor control — a real concern for a 30-60 age range workforce that includes users with arthritis. The spec should specify minimum touch target sizes for adjacent tabs and validate on a 320px device width.

**The "THIS WEEK" section will confuse caregivers who work irregular schedules.**

The visual hierarchy relies on an 8px uppercase section label and opacity difference to distinguish today's shifts from future ones. A caregiver skimming the list may not notice the section break and believe a shift three days away is today's upcoming visit. The section divider needs much more prominent visual treatment.

**The bottom sheet drag handle (36x4px) is an effectively untappable target.**

A 4px-tall target cannot be accurately hit by a user with any finger width or tremor. The drag detection zone should extend to at least 24px height regardless of the visual handle size.

**Care plan collapse preference is not overridden when the care plan has been updated.**

Section 7 specifies care plan summary is "collapsed by default on first visit, expanded subsequently (persisted preference)." If a client's care plan is updated between visits (new allergy, new diagnosis), a returning caregiver with their preference set to collapsed will never see the updated information. The spec must define a "care plan updated since last visit" override that forces the section open.

---

## 7. Accessibility

**The typography scale is categorically too small for the target demographic and outdoor use.**

Section 2 defines:
- Screen titles: 13px, weight 700
- Section labels: 8px, weight 700, uppercase
- Card titles: 11px, weight 700
- Body / metadata: 9–10px, weight 400–600
- Timestamps: 8–9px

These values are incompatible with: outdoor daylight use (requires larger type than indoor baseline); a workforce aged 30–60 where presbyopia affects readability from the late 30s onward; and WCAG 2.1 SC 1.4.4 (Resize Text — AA). Section labels at 8px are not readable under any realistic field condition. The entire typography scale must be revised with a floor of 14px for secondary text and 16px for body text. This is not a minor preference issue — it will directly harm adoption among older, less tech-savvy caregivers.

**Color is the only differentiator for shift status in the Today screen.**

Section 5 uses left-border color alone to distinguish upcoming (blue), future (grey), and completed (green) shifts. A caregiver with red-green color blindness cannot distinguish completed from future states. Completed shifts must carry a "Completed" label or checkmark icon; future shifts must carry a date label.

**No mention of Dynamic Type / OS font scaling support.**

The spec does not specify whether the app supports iOS Dynamic Type or Android font scaling. For a 50–60 year old workforce that has increased their OS font size, an app that ignores system font scale will render at the already-too-small specified sizes. The spec should require that the app respects OS text scaling with defined floor (0.8x, content legible) and ceiling (1.5x, layout usable) bounds.

**Amber banner text color is unspecified — contrast risk.**

`#ca8a04` (amber) with white text fails WCAG AA (~2.8:1). The amber offline banner text must be `color-text-primary` (`#1a1a24`), not white, and font size must be at least 14px to clear AA at normal text sizes.

---

## Prioritized Issues

| Priority | Issue |
|---|---|
| P0 | Typography scale (8–13px) is too small — must be revised before implementation |
| P0 | ADL task list ordering is inverted — completed must sink below pending |
| P0 | Sign Out requires a confirmation dialog — no-confirmation policy will cause accidental logouts with high-friction re-auth |
| P0 | CONFLICT_REASSIGNED has no caregiver-facing UI or recovery path defined |
| P1 | Clock-in bottom sheet capped at 2 shifts — wrong-shift clock-in has no recovery path |
| P1 | No shift cancellation state or push notification defined |
| P1 | GPS outside-range amber bar gives no actionable guidance — specify "your agency will review" |
| P1 | Settings is read-only with no path to OS settings — add "Change in Settings" link |
| P1 | JWT lifetime is undefined — must be specified to evaluate re-auth frequency |
| P1 | Care plan collapse preference not overridden when care plan is updated since last visit |
| P1 | Offline sync completion has no success confirmation |
| P1 | "THIS WEEK" section should be collapsed by default |
| P2 | Completed shift cards are not tappable — caregivers cannot review past visit details |
| P2 | No mileage field at clock-out |
| P2 | Location permission "Not now" path does not explain downstream GPS compliance consequences |
| P2 | Accidental ADL task completion is unrecoverable — completed tasks should revert on re-tap |
| P2 | Drag handle at 36x4px too small — detection zone must extend to at least 24px height |
| P2 | Offline clock-in in real-time submission states (NJ, NY) gives no compliance warning |
| P2 | No Dynamic Type / OS font scaling requirement specified |
| P2 | Amber banner text color not specified — must be dark text to pass WCAG AA |
| P3 | Client address inaccessible when shift card is not in expanded state |
| P3 | Shift history limited to "This Month" — no path to prior months for payroll disputes |
| P3 | FAB touch target competition with adjacent tabs on 320px devices needs validation |
| P3 | Messages tab has no urgency differentiation between message types |
| P3 | "Send New Sign-In Link" button label does not communicate the email-send mechanism |

---

*Review written: 2026-04-08*
