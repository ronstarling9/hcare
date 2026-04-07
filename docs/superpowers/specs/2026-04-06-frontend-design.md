# hcare Web Admin Frontend Design Spec

**Date:** 2026-04-06
**Status:** Draft — approved through brainstorming session

---

## 1. Overview

The hcare web admin frontend is a React (TypeScript) single-page application used by agency admins and schedulers. It is the primary operational interface for scheduling shifts, monitoring EVV compliance, managing clients and caregivers, and reviewing daily agency health.

**Tech stack:** React 18, TypeScript, React Query (server state), Zustand (UI-only state), Vite, Tailwind CSS, Vitest + Testing Library, Playwright (e2e).

---

## 2. Visual Design Language

Inspired by EY-Parthenon: confident, flat, no shadows, generous whitespace. Professional and authoritative — not typical SaaS.

### Color palette

| Token | Hex | Usage |
|---|---|---|
| `color-dark` | `#1a1a24` | Sidebar background, primary buttons, headings |
| `color-dark-mid` | `#2e2e38` | Sidebar hover states, dividers |
| `color-blue` | `#1a9afa` | Active nav item, today marker, avatar backgrounds, interactive accents |
| `color-white` | `#ffffff` | Content area background |
| `color-surface` | `#f6f6fa` | Page background, secondary panels, time column |
| `color-border` | `#eaeaf2` | All borders and dividers |
| `color-text-primary` | `#1a1a24` | Body text, labels |
| `color-text-secondary` | `#747480` | Metadata, timestamps, section labels |
| `color-text-muted` | `#94a3b8` | Inactive nav items, tertiary text |

**No yellow anywhere in the UI.** Yellow (`#ffe600`) is excluded from the palette entirely.

### EVV status colors (semantic only — not brand)

| Status | Color | Usage |
|---|---|---|
| RED | `#dc2626` | Missing EVV elements, no clock-in |
| YELLOW / AMBER | `#ca8a04` | Time anomaly, GPS outside tolerance, manual override |
| GREEN | `#16a34a` | Fully compliant visit |
| GREY | `#94a3b8` | Visit not yet started |

### Typography

System font stack: `-apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif`. No external font dependency.

- Page titles: 16px, weight 700, letter-spacing -0.02em, `color-dark`
- Section labels: 9px, weight 700, letter-spacing 0.1em, uppercase, `color-text-secondary`
- Body: 12–13px, weight 400–500, `color-text-primary`
- Metadata / timestamps: 11px, `color-text-secondary`

### Components

- **Buttons:** No border-radius. Primary = `color-dark` background, white text, weight 700. Ghost = transparent background, `color-border` border, `color-dark` text. No hover shadows — brightness shift only.
- **Borders:** 1px solid `color-border`. Flat — no shadows, no elevation.
- **Status left-border:** 3px solid colored left border on visit rows and shift blocks (red / amber / green / grey).

---

## 3. Application Shell

### Layout

```
┌─────────────────────────────────────────────────────────┐
│  Sidebar (200px fixed)  │  Main content area (flex 1)   │
│  #1a1a24 background     │  #f6f6fa or #fff background   │
└─────────────────────────────────────────────────────────┘
```

### Sidebar

- **Logo:** "h.care" wordmark — "h" in white, "." in `color-blue`. Tagline: "Agency Management" in `color-text-muted`.
- **Nav sections:** "Operations", "People", "Admin" — uppercase section labels.
- **Nav items:** Icon + label. Active state: `color-blue` background, white text, weight 600. Hover: `color-dark-mid` background. Inactive: `color-text-muted`.
- **Badge:** Red dot count on Dashboard item when there are RED EVV visits today.
- **Footer:** User avatar (initials, `color-blue` background), name, role.

### Navigation items

| Section | Item | Route |
|---|---|---|
| Operations | Schedule | `/schedule` |
| Operations | Dashboard | `/dashboard` |
| People | Clients | `/clients` |
| People | Caregivers | `/caregivers` |
| Admin | Payers | `/payers` |
| Admin | EVV Status | `/evv` |
| Admin | Settings | `/settings` |

### Landing screen

**Schedule** is the default route (`/`). Schedulers land directly on the calendar.

---

## 4. Slide-In Detail Panel

The primary interaction pattern for viewing and editing any record (shifts, clients, caregivers). Used consistently across all modules.

**Behavior:**
- Triggered by clicking a shift block, visit row, list item, or "+ New [entity]" button.
- Panel slides in from the right, covering the **entire main content area** (not just narrowing it). Sidebar remains visible.
- Animation: `transform: translateX(100%) → translateX(0)`, `transition: 280ms cubic-bezier(0.4, 0, 0.2, 1)`.
- Close: `← [Module name]` back link in the panel header (e.g., "← Schedule", "← Clients"). No X button.
- Escape key also closes the panel.
- Only one panel open at a time.

**Panel structure:**
```
┌─────────────────────────────────────────┐
│  ← Schedule   [Title]   [Subtitle]      │  Header (fixed)
├─────────────────────────────────────────┤
│  [Status badge if applicable]           │
├─────────────────────────────────────────┤
│                                         │
│  [Body — scrollable, 2-column grid      │
│   for detail rows, full-width for       │
│   candidate lists / explanations]       │
│                                         │
├─────────────────────────────────────────┤
│  [Action buttons — fixed footer]        │
└─────────────────────────────────────────┘
```

---

## 5. Schedule Module (`/schedule`)

The landing screen. Schedulers spend most of their day here.

### Top bar

- Page title "Schedule" + current period (e.g., "Apr 7 – 13, 2026")
- Day / Week / Month toggle (segment control, `color-dark` active state)
- Prev / Next navigation buttons (ghost style)
- "+ New Shift" button (primary style, opens slide-in panel)
- "Broadcast Open" button (ghost style) — opens a confirmation dialog listing all unassigned shifts for the current week, then calls `POST /shifts/{id}/broadcast` for each one sequentially. Not a single bulk endpoint.

### Alert strip

Directly below the top bar. `#f6f6fa` background. Shows today's counts as colored chips: RED EVV count, YELLOW count, Uncovered count, Late clock-in count. Chips use left-border color coding. Only shown when counts > 0.

### Calendar

Week view default. 7-column grid with time slots on the left.

- **Today column:** light blue tint `#f0f8ff`.
- **Today date marker:** `color-blue` circle.
- **Weekends:** `#f9f9fc` background.
- **Shift blocks:** Colored left border (EVV status color), client name (bold), caregiver name (muted). Clicking opens slide-in panel.
- **Empty slots:** Clickable to open "New Shift" panel with time pre-filled.

### Shift detail panel

Opened by clicking any shift block. Contains:

1. **Header:** `← Schedule` back link, client name (title), date + time + service type (subtitle).
2. **EVV status badge:** Full-width colored badge (RED / YELLOW / GREEN / GREY) with plain-language description of the issue.
3. **Body (2-column grid):**
   - Left: Visit Details (client, service, caregiver, authorization hours remaining)
   - Right: EVV Record (clock-in time, clock-out time, GPS status, verification method)
4. **EVV explanation** (full width): Plain-language explanation of any compliance issue.
5. **AI Match — Top Candidates** (full width, shown only when shift is unassigned or reassignment triggered): Ranked list — rank number (`color-blue` circle for #1), caregiver name, reason string ("1.2 mi · 8 prior visits · no OT risk"), "Assign →" link.
6. **Footer actions:** Vary by shift state:
   - Unassigned: "Assign Caregiver" (primary), "Edit Shift" (ghost)
   - Assigned, RED EVV: "Add Manual Clock-in" (primary), "Edit Shift" (ghost), "Mark as Missed" (danger, right-aligned)
   - Completed, GREEN: "Edit Shift" (ghost), "View Care Notes" (ghost)

### New Shift panel

Same slide-in panel pattern. Form fields:
- Client (searchable select)
- Service Type (select, filtered by client's active care plan)
- Date, Start time, End time
- Recurrence (None / Daily / Weekly / Custom)
- Caregiver (optional at creation — can broadcast after)

After saving: panel shows the AI match candidate list automatically if no caregiver was assigned.

---

## 6. Dashboard Module (`/dashboard`)

Operational health view for the current day.

### Top bar

- "Dashboard" title + today's date (e.g., "Monday, April 7, 2026")
- No primary actions — read-only overview

### Stat tiles

4 tiles spanning full width, separated by 1px `color-border` gaps on `color-surface` background:

| Tile | Color | Content |
|---|---|---|
| RED EVV | `#dc2626` large number | Count of RED EVV visits today. Tile background `#fef2f2` when count > 0. |
| YELLOW EVV | `#ca8a04` large number | Count of YELLOW EVV visits today. |
| Uncovered | `color-text-muted` large number | Shifts with no caregiver assigned. |
| On Track | `#16a34a` large number | GREEN + in-progress + completed visits. |

Numbers: 28px, weight 700. Labels: 10px uppercase. Sub-label: 10px muted.

### Visit list

Main column (flex 1). Rows sorted by urgency:

1. **Needs Attention** — RED then YELLOW visits
2. **Uncovered Shifts** — unassigned shifts
3. **In Progress / Completed** — GREEN visits

Each row: 3px left-border (status color), EVV dot, client name (bold), caregiver + service type (muted), time range (right), status pill or label (right). Clicking a row opens the same shift detail slide-in panel.

### Alerts column

Fixed 220px right column. Separate from the visit list. Shows:

- **Credential expiry** alerts (caregiver name, credential type, expiry date) — red text if expiry within 7 days
- **Authorization utilization** warnings (client name, hours remaining, payer, renewal date)
- **Background check renewals** due

Sorted by urgency (nearest date first). Clicking an alert navigates to the relevant caregiver or client record.

---

## 7. Clients Module (`/clients`)

### List view

Searchable, filterable table:
- Search by name
- Filter by payer, service type, authorization status
- Columns: Client name, Payer, Service type, Authorization remaining, Active care plan (yes/no), Family portal (yes/no)
- "+ Add Client" opens slide-in panel

### Client detail panel

Tabbed within the panel:
- **Overview:** Demographics, diagnoses, medications, payer assignment
- **Care Plan:** Active ADL tasks, goals, clinician review (if HHCS)
- **Authorizations:** Table of authorizations with real-time utilization bars
- **Documents:** Upload / download
- **Family Portal:** Manage family portal user access

---

## 8. Caregivers Module (`/caregivers`)

### List view

Searchable table:
- Columns: Name, Status (active/inactive), Credentials (expiry warning badge), Current week hours, Background check status
- "+ Add Caregiver" opens slide-in panel

### Caregiver detail panel

Tabbed:
- **Overview:** Profile, contact info, home address (geocoded)
- **Credentials:** List with expiry dates and verified flag — admin can set verified = true
- **Background Checks:** State registry, FBI, OIG results
- **Availability:** Weekly availability blocks (visual grid)
- **Shift History:** Paginated list of past shifts with EVV status

---

## 9. Payers & Authorizations Module (`/payers`)

### Payer list

Table of payers: name, type (Medicaid / Private Pay / LTC Insurance / VA / Medicare), state, EVV aggregator (auto-assigned from EvvStateConfig). "+ Add Payer" opens slide-in panel.

### Payer detail panel

- Payer configuration (name, type, state)
- EVV aggregator shown (read-only — derived from state config)
- List of active authorizations under this payer across all clients

---

## 10. EVV Status Module (`/evv`)

Read-only compliance overview. Complements the dashboard's per-visit indicators.

- Date range filter (default: last 30 days)
- Table of visits with EVV status, sortable by status
- Filter by status (RED / YELLOW / GREEN / EXEMPT / PORTAL_SUBMIT)
- Each row clickable → shift detail slide-in panel
- No aggregate cache used here — all statuses computed live from Core API

---

## 11. Auth Flow

- Login page: email + password form. Unauthenticated routes redirect to `/login`.
- JWT stored in memory (not localStorage). On page reload the user must re-authenticate. Token expiry handled by catching 401 responses and redirecting to `/login`.
- Role-based rendering: SCHEDULER role hides user management and agency settings. ADMIN sees everything.

---

## 12. State Management

| Concern | Tool |
|---|---|
| Server state (API data) | React Query — all fetching, caching, invalidation |
| Active panel state (open/closed, which record) | Zustand store |
| Navigation | React Router v6 |
| Form state | React Hook Form |

React Query cache is invalidated on successful mutations (e.g., after assigning a caregiver, the shift list and dashboard queries are invalidated).

---

## 13. API Integration

All requests go to `VITE_API_BASE_URL` (default `http://localhost:8080/api/v1`). JWT attached as `Authorization: Bearer <token>` header via a React Query default query function wrapper.

Key endpoints consumed:

| Feature | Endpoint |
|---|---|
| Auth | `POST /auth/login` |
| Schedule list | `GET /shifts?start=&end=` |
| Shift detail | `GET /shifts/{id}` |
| Create shift | `POST /shifts` |
| Assign caregiver | `PATCH /shifts/{id}/assign` |
| AI candidates | `GET /shifts/{id}/candidates` |
| Broadcast | `POST /shifts/{id}/broadcast` |
| Clock-in (admin manual) | `POST /shifts/{id}/clock-in` |
| Client list | `GET /clients` |
| Client detail | `GET /clients/{id}` |
| Caregiver list | `GET /caregivers` |
| Caregiver detail | `GET /caregivers/{id}` |
| Dashboard summary | `GET /dashboard/today` — **backend gap, must be added before frontend dashboard can be built** |

---

## 14. Testing

| Layer | Tool | Target |
|---|---|---|
| Unit | Vitest + Testing Library | 80% coverage on services, hooks, utils |
| Component | Testing Library | All interactive components |
| E2e | Playwright | Schedule: create shift, assign caregiver; Dashboard: view alerts; Auth: login/logout |

---

## 15. Out of Scope (this spec)

- Mobile / responsive layout (desktop-first for admin app)
- Dark mode
- Drag-and-drop shift reassignment (P2)
- Self-serve onboarding wizard (separate spec)
- Family portal (separate spec)
- React Native mobile app (separate spec)
