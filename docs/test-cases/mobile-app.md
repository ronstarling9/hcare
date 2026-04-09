# Mobile App — Manual Test Cases

**Area code:** MOB  
**Feature:** Caregiver mobile app — login, today, clock-in, active visit, open shifts, messages, offline sync  
**Scope:** Main features, happy path only  
**Last reviewed:** 2026-04-09

---

## Setup

**Run with mock data (default — no backend needed):**
```bash
cd mobile
npm install
npx expo start --ios   # or --android
```
Mock data is active when `EXPO_PUBLIC_USE_MOCKS=true` (default in `mobile/.env`).  
The mock layer simulates a 350 ms network delay and covers all 21 BFF endpoints.

**Run against real BFF:**
```bash
# In mobile/.env
EXPO_PUBLIC_USE_MOCKS=false
EXPO_PUBLIC_BFF_URL=http://localhost:8081

cd bff && mvn spring-boot:run   # start BFF on :8081
```

---

## Authentication

### TC-MOB-001 — Login screen shown to unauthenticated user

**Priority:** P0  
**Preconditions:** App freshly installed or auth store cleared

**Steps:**
1. Launch the app

**Expected result:**
- Login screen appears with the `h.care` branded header and "Caregiver App" subtitle
- Email input and "Send New Sign-In Link" button are visible

---

### TC-MOB-002 — Request a sign-in link

**Priority:** P0  
**Preconditions:** On the login screen

**Steps:**
1. Enter `ashley.rodriguez@hcare.dev` in the email field
2. Tap **Send New Sign-In Link**

**Expected result:**
- Button shows "Sending…" while the request is in flight
- Success message appears: "If that email matches your account, a link has been sent."
- (With mocks: response is immediate; with real BFF: check the BFF logs for the generated link)

---

### TC-MOB-003 — Deep link sign-in lands on Today screen

**Priority:** P0  
**Preconditions:** A valid magic-link token (from mock handler or real BFF)

**Steps:**
1. Open the sign-in deep link on the device (e.g. `hcare://auth?token=<token>`)
2. App opens or comes to foreground

**Expected result:**
- App navigates to the Today screen
- Caregiver name appears in the greeting (e.g. "Good morning, Ashley")
- Bottom tab bar is visible

---

## Today Screen

### TC-MOB-004 — Today screen shows shift list and greeting

**Priority:** P0  
**Preconditions:** Logged in as a caregiver with shifts today (mock data includes these)

**Steps:**
1. Open the app to the Today tab

**Expected result:**
- Time-appropriate greeting shown ("Good morning / afternoon / evening, [first name]")
- Today's date and shift count shown (e.g. "Thursday, April 9 · 2 shifts today")
- Shift cards listed with client name, time, service type, and address

---

### TC-MOB-005 — Pull-to-refresh updates shift list

**Priority:** P1  
**Preconditions:** On the Today screen

**Steps:**
1. Pull down on the shift list

**Expected result:**
- Loading spinner appears
- List refreshes (same or updated data)

---

### TC-MOB-006 — Tap map pin opens maps app

**Priority:** P1  
**Preconditions:** A shift card is visible with a client address

**Steps:**
1. Tap the map/directions icon on a shift card

**Expected result:**
- iOS: Apple Maps opens with the client's address
- Android: Google Maps opens with the client's address

---

### TC-MOB-007 — This week section expands and collapses

**Priority:** P1  
**Preconditions:** Caregiver has shifts later in the week (mock data includes these)

**Steps:**
1. Tap **Show this week (N shifts)** at the bottom of the Today list
2. Tap again to collapse

**Expected result:**
- Week shifts expand at reduced opacity
- Chevron changes direction
- Tapping again collapses them

---

## Clock In

### TC-MOB-008 — FAB opens clock-in sheet

**Priority:** P0  
**Preconditions:** Logged in, on any tab, at least one upcoming shift today

**Steps:**
1. Tap the raised centre button in the tab bar (the FAB)

**Expected result:**
- Bottom sheet slides up with "CLOCK IN TO" header
- The first upcoming shift is pre-selected (blue left border, "SELECT" label)
- Other shifts are listed and tappable
- A "Clock In — [Client Name]" button appears at the bottom

---

### TC-MOB-009 — Clock in to a shift navigates to Visit screen

**Priority:** P0  
**Preconditions:** Clock-in sheet open with a shift selected (TC-MOB-008)

**Steps:**
1. Tap **Clock In — [Client Name]**

**Expected result:**
- Button shows "Clocking in…" briefly
- Navigates to the Visit screen
- Visit screen hero card shows client name, "IN PROGRESS" label, and clock-in time
- Elapsed timer starts counting up (HH:MM:SS)
- GPS status bar appears below the hero card

---

### TC-MOB-010 — Active visit banner shown on Today screen during a visit

**Priority:** P0  
**Preconditions:** A visit is active (clocked in)

**Steps:**
1. Navigate back to the Today tab

**Expected result:**
- Header shows an "Active Visit" banner with the client name and clock-in time
- Tapping **Continue** navigates back to the Visit screen
- Tab bar centre FAB turns red

---

## Active Visit

### TC-MOB-011 — ADL tasks can be checked and unchecked

**Priority:** P0  
**Preconditions:** On the Visit screen, care plan has loaded with ADL tasks

**Steps:**
1. Tap a task to mark it complete
2. Tap it again to unmark it

**Expected result:**
- Checkbox toggles immediately (optimistic UI)
- Task state persists if you navigate away and return

---

### TC-MOB-012 — Care notes are saved on blur

**Priority:** P1  
**Preconditions:** On the Visit screen

**Steps:**
1. Tap the care notes field
2. Type some text
3. Tap outside the field to dismiss the keyboard

**Expected result:**
- Notes are saved (BFF call fires on blur)
- If you navigate away and return, the text is still there

---

### TC-MOB-013 — Clock out navigates back to Today

**Priority:** P0  
**Preconditions:** On the Visit screen, more than 50% of ADL tasks are complete (or there are no tasks)

**Steps:**
1. Tap **Clock Out**

**Expected result:**
- Button shows "Clocking out…" briefly
- Returns to the Today screen
- Active visit banner is gone
- FAB returns to its normal (non-red) state
- The shift card for the completed visit shows a completed status

---

### TC-MOB-014 — Clock out with mostly incomplete tasks shows confirmation modal

**Priority:** P1  
**Preconditions:** On the Visit screen, fewer than 50% of ADL tasks are complete

**Steps:**
1. Tap **Clock Out**

**Expected result:**
- A confirmation modal appears listing the number of remaining tasks
- Tapping **Clock Out Anyway** completes the clock-out and returns to Today
- Tapping **Cancel** dismisses the modal and stays on the Visit screen

---

## Open Shifts

### TC-MOB-015 — Open shifts list shows available shifts

**Priority:** P0  
**Preconditions:** Logged in, mock data includes open shifts

**Steps:**
1. Tap the **Open Shifts** tab

**Expected result:**
- List of open shift cards shown, each with client name, date/time, service type, and location

---

### TC-MOB-016 — Accept an open shift

**Priority:** P0  
**Preconditions:** On the Open Shifts screen with at least one shift listed

**Steps:**
1. Tap **Accept** on a shift card

**Expected result:**
- Button shows loading state briefly
- Shift is removed from the open shifts list (or card updates to show accepted state)

---

### TC-MOB-017 — Decline an open shift

**Priority:** P1  
**Preconditions:** On the Open Shifts screen with at least one shift listed

**Steps:**
1. Tap **Decline** on a shift card

**Expected result:**
- Shift is removed from the list

---

## Messages

### TC-MOB-018 — Inbox shows message threads

**Priority:** P0  
**Preconditions:** Logged in, mock data includes message threads

**Steps:**
1. Tap the **Messages** tab

**Expected result:**
- List of threads shown with subject, preview text, and timestamp
- Unread threads have bold subject and a blue dot

---

### TC-MOB-019 — Tap a thread opens message view

**Priority:** P0  
**Preconditions:** On the Messages inbox

**Steps:**
1. Tap a message thread

**Expected result:**
- Thread screen opens showing the full message exchange
- Messages from the agency and the caregiver are visually distinct

---

## Offline Sync

### TC-MOB-020 — Offline banner appears when connectivity is lost

**Priority:** P0  
**Preconditions:** On the Today screen, connected to network

**Steps:**
1. Enable airplane mode on the device

**Expected result:**
- Orange/amber offline banner appears at the top of the Today screen
- App remains usable (shifts still visible, visit can continue)

---

### TC-MOB-021 — EVV events sync automatically when connectivity is restored

**Priority:** P0  
**Preconditions:** Clock-in or clock-out occurred while offline (events queued in SQLite)

**Steps:**
1. Restore network connectivity (disable airplane mode)

**Expected result:**
- Offline banner shows "Syncing…" briefly
- Banner disappears once sync completes
- "Visit data synced ✓" toast appears for ~3 seconds
- Synced events no longer appear in the SQLite queue
