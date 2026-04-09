# Family Portal — Manual Test Cases

**Area code:** FP  
**Feature:** Family Portal — invite generation, token verification, dashboard, and access control  
**Last reviewed:** 2026-04-09

---

## Setup

1. Start the dev environment: `./dev-start.sh`
2. Log in at `http://localhost:5173` as `admin@sunrise.dev` / `Admin1234!`
3. Navigate to any client (e.g. Dorothy Henderson)
4. Open the **Family Portal** tab on the client detail panel

Keep an **incognito window** handy — the family portal session runs there to stay isolated from the admin session.

---

## Invite Generation

### TC-FP-001 — Generate a new invite link

**Priority:** P0  
**Preconditions:** Logged in as admin, on a client's Family Portal tab with no existing portal users

**Steps:**
1. Click **Add Invite**
2. Enter `family@example.com` in the email field
3. Click **Generate Link**

**Expected result:**
- A URL is displayed in the form (e.g. `http://localhost:5173/portal/verify?token=<128-hex-chars>`)
- A `Copy Link` button and a `Done` button appear
- An expiry note shows the link expires in approximately 72 hours
- The user list below shows `family@example.com` with "Never logged in"

---

### TC-FP-002 — Copy link to clipboard

**Priority:** P1  
**Preconditions:** TC-FP-001 completed and invite URL is visible

**Steps:**
1. Click **Copy Link**

**Expected result:**
- Button label changes to `Copied!` for ~2 seconds then reverts
- Clipboard contains the full invite URL (paste into a text editor to verify)

---

### TC-FP-003 — Cancel invite form clears state

**Priority:** P1  
**Preconditions:** Logged in as admin, on the Family Portal tab

**Steps:**
1. Click **Add Invite**
2. Enter an email address
3. Click **Cancel**

**Expected result:**
- Form closes
- No URL was generated
- User list is unchanged

---

### TC-FP-004 — Generate re-invite for existing user

**Priority:** P1  
**Preconditions:** At least one portal user exists in the list (from TC-FP-001)

**Steps:**
1. Click **Send New Link** next to an existing portal user
2. Confirm the email field is pre-filled and the form shows a re-invite notice
3. Click **Generate Link**

**Expected result:**
- A new invite URL is generated for the same email
- The previous token is now invalid (verify by attempting to use the old URL — see TC-FP-008)

---

### TC-FP-005 — Done button closes form and clears any prior error

**Priority:** P2  
**Preconditions:** TC-FP-001 completed and invite URL is visible

**Steps:**
1. Click **Done**

**Expected result:**
- Form closes
- No stale error message is visible if the form is reopened via **Add Invite**

---

## Token Verification

### TC-FP-006 — Verify valid token and land on dashboard

**Priority:** P0  
**Preconditions:** Fresh invite URL from TC-FP-001 that has not been used

**Steps:**
1. Open the invite URL in an incognito window

**Expected result:**
- Page briefly shows "Verifying your link…"
- Redirects to the portal dashboard at `/portal/dashboard`
- Dashboard shows the client's first name (e.g. "Dorothy's Care")
- Today's date is shown in the header

---

### TC-FP-007 — Token is single-use

**Priority:** P0  
**Preconditions:** The same invite URL from TC-FP-006 (already verified once)

**Steps:**
1. Paste the same invite URL into a new incognito window and navigate to it

**Expected result:**
- Page shows "Link expired or already used" (not a dashboard)
- No JWT is issued

---

### TC-FP-008 — Expired token shows correct error

**Priority:** P1  
**Preconditions:** Access to the H2 console (`http://localhost:8080/h2-console`, JDBC URL `jdbc:h2:mem:hcaredb`)

**Steps:**
1. Generate a new invite link and note the raw token from the URL
2. In H2 console, run:
   ```sql
   UPDATE family_portal_tokens SET expires_at = '2020-01-01T00:00:00' WHERE 1=1;
   ```
3. Open the invite URL in an incognito window

**Expected result:**
- Page shows "Link expired or already used"
- The expired token row is deleted from `family_portal_tokens` (verify in H2 console)

---

### TC-FP-009 — Invalid / garbage token shows correct error

**Priority:** P1  
**Preconditions:** Dev environment running

**Steps:**
1. Navigate to `http://localhost:5173/portal/verify?token=notavalidtoken` in an incognito window

**Expected result:**
- Page shows "Link expired or already used"

---

### TC-FP-010 — Server error shows generic message, not "link expired"

**Priority:** P2  
**Preconditions:** Can simulate a 500 response (e.g. stop the backend mid-request, or use browser devtools network throttling to force an abort)

**Steps:**
1. Stop the backend process: `./dev-stop.sh` (stop backend only, or kill the process)
2. Navigate to any invite URL in an incognito window

**Expected result:**
- Page shows "Something went wrong" (not "Link expired or already used")

---

## Portal Dashboard

### TC-FP-011 — Dashboard shows today's visit when one is scheduled

**Priority:** P0  
**Preconditions:** Logged into portal dashboard (TC-FP-006). The seeded data includes shifts; confirm in H2 that the client has a shift with `scheduled_start` within today (agency timezone).

**Steps:**
1. View the "Today's Visit" card on the dashboard

**Expected result:**
- Card shows caregiver name, service type, and scheduled time in the agency's timezone with timezone abbreviation (e.g. "9:00 AM CDT")
- Status pill reflects correct state (e.g. grey dot for upcoming, green for in progress)

---

### TC-FP-012 — Dashboard shows "No visit scheduled today" when applicable

**Priority:** P1  
**Preconditions:** Logged into portal dashboard. No shift exists for this client within today.

**Steps:**
1. View the "Today's Visit" card

**Expected result:**
- Card shows "No visit scheduled for today"

---

### TC-FP-013 — Upcoming visits show up to 3 entries

**Priority:** P1  
**Preconditions:** Client has at least 3 upcoming non-cancelled shifts after today

**Steps:**
1. View the "Upcoming" section on the dashboard

**Expected result:**
- At most 3 rows appear (the backend caps at 3)
- Each row shows date and time range in the agency's timezone
- Caregiver name appears if assigned

---

### TC-FP-014 — Last visit card shows completed visit details

**Priority:** P1  
**Preconditions:** Client has at least one COMPLETED shift with an EVV record (clock-in and clock-out)

**Steps:**
1. View the "Last Visit" card

**Expected result:**
- Shows the visit date, clock-out time, and duration (e.g. "2 hr 15 min")
- If the caregiver left a note, it appears in quotes below

---

### TC-FP-015 — Late visit shows amber warning

**Priority:** P2  
**Preconditions:** Client has a shift today with `scheduled_start` more than 15 minutes in the past, status `ASSIGNED` or `OPEN`, and no EVV clock-in record

**Steps:**
1. View the "Today's Visit" card

**Expected result:**
- Amber clock icon and "Running late" message appear (not the grey scheduled pill)

---

### TC-FP-016 — Sign out redirects to verify page

**Priority:** P1  
**Preconditions:** Logged into portal dashboard

**Steps:**
1. Click **Sign Out** in the top-right corner

**Expected result:**
- Redirected to `/portal/verify` with a "You've been signed out" message
- Navigating back to `/portal/dashboard` redirects to `/portal/verify` (session cleared)

---

## Access Control

### TC-FP-017 — Admin JWT rejected on portal dashboard endpoint

**Priority:** P0  
**Preconditions:** Dev environment running, `curl` available

**Steps:**
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@sunrise.dev","password":"Admin1234!"}' \
  | jq -r '.token')

curl -i http://localhost:8080/api/v1/family/dashboard \
  -H "Authorization: Bearer $TOKEN"
```

**Expected result:**
- HTTP `403 Forbidden`

---

### TC-FP-018 — Revoked access returns 403 and clears session

**Priority:** P0  
**Preconditions:** Active portal session in incognito window (TC-FP-006)

**Steps:**
1. In the admin window, go to the client's Family Portal tab
2. Click **Remove** next to the family portal user and confirm
3. In the incognito window, reload the portal dashboard

**Expected result:**
- Page redirects to `/portal/verify` with an "Access revoked" message
- Portal auth store is cleared (no lingering JWT)

---

### TC-FP-019 — Discharged client returns 410 and shows care-concluded screen

**Priority:** P0  
**Preconditions:** Active portal session in incognito window

**Steps:**
1. In H2 console, run:
   ```sql
   UPDATE clients SET status = 'DISCHARGED' WHERE id = '<client-uuid>';
   ```
2. Reload the portal dashboard in the incognito window

**Expected result:**
- Dashboard shows "Care services have concluded" (not an error or retry button)
- No visit data is shown

---

### TC-FP-020 — Cross-agency: portal JWT cannot access another agency's dashboard

**Priority:** P0  
**Preconditions:** A valid portal JWT for a Sunrise client. A client UUID from Golden Years.

**Steps:**
```bash
# Attempt to reach the dashboard using a Sunrise portal JWT
# (The dashboard endpoint reads clientId from the JWT — the JWT itself
#  encodes the client. This test confirms the agencyId in the JWT is enforced.)

curl -i http://localhost:8080/api/v1/family/dashboard \
  -H "Authorization: Bearer <sunrise-portal-jwt>"
```
Then in H2 console verify that the response only contains Sunrise data and that no Golden Years records are accessible.

**Expected result:**
- Response is scoped strictly to the client and agency encoded in the JWT
- HTTP 200 with Sunrise data only (or 403/410 if that client's state warrants it)

---

### TC-FP-021 — Portal JWT cannot call admin endpoints

**Priority:** P0  
**Preconditions:** A valid portal JWT (from TC-FP-006)

**Steps:**
```bash
curl -i http://localhost:8080/api/v1/clients \
  -H "Authorization: Bearer <portal-jwt>"

curl -i http://localhost:8080/api/v1/caregivers \
  -H "Authorization: Bearer <portal-jwt>"
```

**Expected result:**
- Both return HTTP `403 Forbidden`

---

## Portal User Management

### TC-FP-022 — Remove portal user

**Priority:** P1  
**Preconditions:** At least one portal user in the list

**Steps:**
1. Click **Remove** next to a portal user
2. Confirm the inline confirmation prompt

**Expected result:**
- User is removed from the list
- Any active portal session for that user returns 403 on next request (see TC-FP-018)

---

### TC-FP-023 — Cancel remove confirmation

**Priority:** P2  
**Preconditions:** At least one portal user in the list

**Steps:**
1. Click **Remove** next to a portal user
2. Click **Cancel** on the confirmation prompt

**Expected result:**
- User remains in the list, no removal occurred
