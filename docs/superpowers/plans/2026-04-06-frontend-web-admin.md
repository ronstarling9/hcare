# hcare Web Admin Frontend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> This plan is split into phase files. Execute one phase file at a time, stopping at each `✋ MANUAL TEST CHECKPOINT` for user verification before proceeding to the next phase.

**Goal:** Build the hcare web admin frontend — a React + TypeScript SPA for agency admins and schedulers — from scaffold through full API integration across 8 testable phases.

**Architecture:** Phase 1 builds the complete UI with mock data so every screen can be manually tested before any backend is touched. Phase 2 adds the one missing backend endpoint (`GET /api/v1/dashboard/today`). Phases 3–8 wire auth and each screen to the real API one at a time, each ending with a manual test checkpoint.

**Tech Stack:** React 18, TypeScript, Vite 5, Tailwind CSS v3, React Query v5, Zustand 5, React Router v6, React Hook Form v7, Axios, Vitest + Testing Library, Playwright

---

## Phase Files (execute in order)

| Phase | File | Content |
|-------|------|---------|
| 1 | [phase-1-static-ui.md](2026-04-06-frontend-phase-1-static-ui.md) | Scaffold, Tailwind, types, mock data, all screens, tests |
| 2 | [phase-2-dashboard-endpoint.md](2026-04-06-frontend-phase-2-dashboard-endpoint.md) | Backend `GET /api/v1/dashboard/today` |
| 3 | [phase-3-auth.md](2026-04-06-frontend-phase-3-auth.md) | CORS config, Axios client, login page, route guards |
| 4 | [phase-4-wire-schedule.md](2026-04-06-frontend-phase-4-wire-schedule.md) | Wire schedule screen to real API |
| 5 | [phase-5-wire-dashboard.md](2026-04-06-frontend-phase-5-wire-dashboard.md) | Wire dashboard screen to real API |
| 6 | [phase-6-wire-clients.md](2026-04-06-frontend-phase-6-wire-clients.md) | Wire clients screen to real API |
| 7 | [phase-7-wire-caregivers.md](2026-04-06-frontend-phase-7-wire-caregivers.md) | Wire caregivers screen to real API |
| 8 | [phase-8-wire-payers-evv.md](2026-04-06-frontend-phase-8-wire-payers-evv.md) | Build payer/EVV backend endpoints + wire frontend |

---

## Key Architectural Decisions (read before executing any phase)

**Page/component split:** Page components fetch data (mock in Phase 1, real API in later phases). Child components receive props only and never fetch. This means Phase 4–8 only rewrites page components — child components are untouched.

**Panel state:** One Zustand store (`panelStore`) controls which slide-in panel is open and for which record. Only one panel open at a time.

**JWT in memory:** Auth token lives in a Zustand store (not localStorage). Page reload requires re-login. 401 responses redirect to `/login`.

**No CORS config in current backend:** `backend/src/main/java/com/hcare/config/SecurityConfig.java` has no CORS configuration. Phase 3 adds it before any frontend → backend calls are attempted.

**`GET /shifts` returns IDs only:** `ShiftSummaryResponse` has `clientId`/`caregiverId` but no names. Phase 4 fetches clients + caregivers separately and builds lookup maps.

**No payer or EVV history endpoints exist:** `PayerController` and an EVV history endpoint don't exist yet. Phase 8 builds them before wiring the frontend.

**Dashboard endpoint is missing:** `GET /api/v1/dashboard/today` doesn't exist. Phase 2 builds it. Phase 5 wires the frontend to it.

---

## File Map (all files created or modified across all phases)

### Frontend — created in Phase 1
```
frontend/
├── package.json
├── vite.config.ts
├── tailwind.config.ts
├── postcss.config.js
├── tsconfig.json
├── tsconfig.node.json
├── index.html
├── src/
│   ├── main.tsx
│   ├── App.tsx
│   ├── index.css
│   ├── types/
│   │   └── api.ts
│   ├── mock/
│   │   └── data.ts
│   ├── store/
│   │   └── panelStore.ts
│   ├── components/
│   │   ├── layout/
│   │   │   ├── Shell.tsx
│   │   │   └── Sidebar.tsx
│   │   ├── panel/
│   │   │   └── SlidePanel.tsx
│   │   ├── schedule/
│   │   │   ├── SchedulePage.tsx
│   │   │   ├── WeekCalendar.tsx
│   │   │   ├── ShiftBlock.tsx
│   │   │   ├── AlertStrip.tsx
│   │   │   ├── ShiftDetailPanel.tsx
│   │   │   └── NewShiftPanel.tsx
│   │   ├── dashboard/
│   │   │   ├── DashboardPage.tsx
│   │   │   ├── StatTiles.tsx
│   │   │   ├── VisitList.tsx
│   │   │   └── AlertsColumn.tsx
│   │   ├── clients/
│   │   │   ├── ClientsPage.tsx
│   │   │   ├── ClientsTable.tsx
│   │   │   └── ClientDetailPanel.tsx
│   │   ├── caregivers/
│   │   │   ├── CaregiversPage.tsx
│   │   │   ├── CaregiversTable.tsx
│   │   │   └── CaregiverDetailPanel.tsx
│   │   ├── payers/
│   │   │   └── PayersPage.tsx
│   │   └── evv/
│   │       └── EvvStatusPage.tsx
│   └── test/
│       └── setup.ts
├── src/components/**/*.test.tsx   (co-located with components)
├── playwright.config.ts
└── e2e/
    └── smoke.spec.ts
```

### Frontend — created in Phase 3+
```
frontend/src/
├── pages/
│   └── LoginPage.tsx             (Phase 3)
├── store/
│   └── authStore.ts              (Phase 3)
├── api/
│   ├── client.ts                 (Phase 3)
│   ├── auth.ts                   (Phase 3)
│   ├── shifts.ts                 (Phase 4)
│   ├── dashboard.ts              (Phase 5)
│   ├── clients.ts                (Phase 6)
│   ├── caregivers.ts             (Phase 7)
│   ├── payers.ts                 (Phase 8)
│   └── evv.ts                    (Phase 8)
└── hooks/
    ├── useShifts.ts              (Phase 4)
    ├── useDashboard.ts           (Phase 5)
    ├── useClients.ts             (Phase 6)
    ├── useCaregivers.ts          (Phase 7)
    ├── usePayers.ts              (Phase 8)
    └── useEvvHistory.ts          (Phase 8)
```

### Backend — created in Phase 2
```
backend/src/main/java/com/hcare/api/v1/dashboard/
├── dto/
│   ├── DashboardTodayResponse.java
│   ├── DashboardVisitRow.java
│   └── DashboardAlert.java
├── DashboardService.java
└── DashboardController.java

backend/src/test/java/com/hcare/api/v1/dashboard/
└── DashboardControllerIT.java
```

### Backend — modified in Phase 2–3
```
backend/src/main/java/com/hcare/domain/CaregiverCredentialRepository.java  (Phase 2)
backend/src/main/java/com/hcare/domain/BackgroundCheckRepository.java       (Phase 2)
backend/src/main/java/com/hcare/config/SecurityConfig.java                  (Phase 3)
```

### Backend — created in Phase 8
```
backend/src/main/java/com/hcare/api/v1/payers/
├── dto/PayerResponse.java
├── PayerService.java
└── PayerController.java

backend/src/main/java/com/hcare/api/v1/evv/
├── dto/EvvHistoryRow.java
├── EvvHistoryService.java
└── EvvHistoryController.java
```
