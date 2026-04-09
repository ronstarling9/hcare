# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**hcare** is a home care agency management SaaS platform targeting small agencies (1–25 caregivers). Core features: scheduling with AI-assisted caregiver matching, EVV (Electronic Visit Verification) compliance tracking, caregiver mobile app, client/care plan management, and family portal.

```
/backend         # Core API — Spring Boot 3.4.4, Java 25 (system of record)
/bff             # Mobile BFF — Spring Boot 3.4.4, Java 25 (stateless adapter for React Native)
/frontend        # React (TypeScript) — web admin app
/mobile          # React Native — caregiver mobile app
/infra           # IaC / deployment configs
/docs            # Project docs: glossary.md, dev-seed-reference.md, test-cases/ (manual test suites)
```

Primary packages under `com.hcare`:
- `domain/` — JPA entities and repositories
- `api/v1/` — REST controllers and DTOs
- `multitenancy/` — Hibernate `@Filter` tenant isolation
- `scheduling/` — rolling 8-week shift generation (`ShiftGenerationService`, `ShiftGenerationScheduler`)
- `scoring/` — AI caregiver match engine (isolated module)
- `evv/` — EVV compliance rules and aggregator connectors
- `audit/` — `PhiAuditLog` append-only PHI access log
- `security/` — JWT provider, auth filter, `UserPrincipal`
- `config/` — `SecurityConfig`, `WebMvcConfig`, `SchedulingConfig`, `DevDataSeeder`
- `exception/` — `GlobalExceptionHandler` (`@ControllerAdvice`), `ErrorResponse`

---

## Domain Model Summary

All entities carry an implicit `agencyId` (row-level multi-tenancy). Key relationships:
- `Agency` → `AgencyUser` (roles: ADMIN | SCHEDULER), `Caregiver`, `Client`, `Payer`, `ServiceType`
- `Client` → `CarePlan` (versioned), `Authorization` (payer + authorized hours, real-time utilization), `Diagnoses`, `Medications`, `FamilyPortalUser`
- `Caregiver` → `Credentials` (with expiry), `BackgroundChecks`, `Availability`, `CaregiverScoringProfile`
- `RecurrencePattern` → generates `Shift` instances on a rolling 8-week horizon (nightly job advances frontier)
- `Shift` → `EVVRecord` (6 federal elements + compliance status computed on read), `ShiftOffers`, `ADLTaskCompletions`
- `EvvStateConfig` — global (no agencyId), one row per US state, Flyway-seeded in `V2__evv_state_config_seed.sql`
- `PhiAuditLog` — append-only, stored in a separate schema partition

### Multi-tenancy

Enforced at the persistence layer via Hibernate `@FilterDef` named `agencyFilter` (declared in `domain/package-info.java`). `TenantFilterInterceptor.preHandle()` sets `TenantContext` (ThreadLocal) from `UserPrincipal.getAgencyId()`; `TenantFilterAspect` enables the Hibernate session filter `@Before` repository calls inside `@Transactional`. `TenantContext` is cleared in `afterCompletion`. **Never** enforce tenant isolation at the service layer — the framework prevents cross-agency leakage.

### Core API vs Mobile BFF

**Core API** owns all business logic, data, and EVV compliance status computation. It is the system of record.

**Mobile BFF** (`/bff`) is a stateless thin adapter with no database. Its responsibilities are limited to: push notification dispatch, offline sync reconciliation (`POST /sync/visits`), mobile-optimized payload shaping. The BFF **never** computes EVV compliance status independently — it calls Core API and forwards the result.

### EVV Compliance

Compliance status (`GREEN/YELLOW/RED/EXEMPT/PORTAL_SUBMIT/GREY`) is computed on read by Core API from `EVVRecord` + `EvvStateConfig`. It is **never stored** and **never pre-computed in the BFF**. Rules are DB-driven — updating `EvvStateConfig` rows instantly re-evaluates all history without reprocessing jobs. `GREY` = no EVVRecord yet; `EXEMPT` = PRIVATE_PAY payer or no authorization linked. The computation is stateless (`EvvComplianceService.compute()` takes pre-loaded objects, makes no DB calls).

### AI Scoring Module

All scoring logic lives in `com.hcare.scoring`. The public interface is `ScoringService` — nothing outside this package queries scoring tables directly. `CaregiverScoringProfile` and `CaregiverClientAffinity` (per-caregiver/client visit history) are updated asynchronously via `@TransactionalEventListener` on `ShiftCompletedEvent` / `ShiftCancelledEvent`, never on the request path.

`featureFlags.aiSchedulingEnabled` gates scoring: when `false` (Starter tier), `rankCandidates` returns eligible caregivers unsorted with `score=0`. A weekly `@Scheduled` job resets `currentWeekHours` every Monday 00:00 UTC.

Known P2 gaps: `cancelRate` is a lifetime running total, not a true 90-day rolling window.

---

## Architecture Principles

- **API-first** — all features are driven through versioned REST (or GraphQL) endpoints; the frontend never talks directly to the database.
- **Stateless backend** — no server-side session state; use JWT / OAuth 2.0 tokens.
- **Mobile-first UI** — every component must be responsive and touch-friendly before considering desktop enhancements.
- **Feature flags** — new behaviour is gated behind flags so web and mobile can ship independently.

---

## Dev Environment Scripts

Root-level convenience scripts start/stop both services together:
```bash
./dev-start.sh   # starts backend + frontend concurrently, waits for readiness, opens Chrome
./dev-stop.sh    # kills both processes using the saved PID file
```
Logs land at `/tmp/hcare-backend.log` and `/tmp/hcare-frontend.log`.

---

## Frontend (React)

### Common Commands
```bash
cd frontend
npm run dev             # start dev server
npm run build           # production build
npm run test            # Vitest unit tests
npm run test:e2e        # Playwright e2e tests
npm run lint --fix      # ESLint + Prettier fix (run before committing)
```

### Conventions
- Components live in `frontend/src/components/` and are co-located with their test and story files.
- Shared hooks go in `frontend/src/hooks/`; shared types in `frontend/src/types/`.
- Use React Query for all server state; Zustand only for purely client-side UI state.
- Prefer named exports over default exports.
- Mobile breakpoints first (`sm:`, `md:`, `lg:`) — never desktop-first overrides.

### API & State Layer
- `frontend/src/api/client.ts` — Axios instance with JWT request interceptor and 401 session-expiry redirect. The 401 interceptor skips `/auth/` endpoints so login failures propagate to the caller.
- `frontend/src/api/` — one file per domain (e.g. `auth.ts`, `shifts.ts`); all functions call `apiClient` and return typed response data.
- `frontend/src/store/authStore.ts` — Zustand store holding `token | userId | agencyId | role`. Read via `useAuthStore.getState()` outside React; via selector hook inside.
- Use `axios-mock-adapter` (MockAdapter) for unit-testing Axios interceptors — do not access `(apiClient.interceptors.request as any).handlers` (private internal).

### Tailwind Design Tokens
Always use the project's custom tokens instead of raw hex values:

| Token | Value | Usage |
|---|---|---|
| `bg-dark` | `#1a1a24` | Page/app background |
| `bg-dark-mid` | `#2e2e38` | Card / panel backgrounds |
| `bg-blue` | `#1a9afa` | Primary CTA / brand |
| `bg-surface` | `#f6f6fa` | Light-mode surface |
| `border-border` | `#eaeaf2` | Default borders |
| `text-text-primary` | `#1a1a24` | Default text |
| `text-text-secondary` | `#747480` | Muted / helper text |
| `text-text-muted` | `#94a3b8` | Placeholder / disabled |

### i18n
- Namespaces live in `frontend/public/locales/en/<namespace>.json`.
- Register new namespaces in the `ns` array in `frontend/src/i18n.ts`.
- Default namespace is `common`; use `useTranslation('<ns>')` to access others.

### Test Mock Data
`frontend/src/mock/data.ts` — typed mock fixtures with fixed UUIDs that match the seeded dev data. Use these constants in Vitest tests instead of inventing UUIDs.

### Dev Credentials (seeded by `DevDataSeeder.java`, `dev` profile only)
Three agencies are seeded. Credentials follow the pattern:
- `admin@<slug>.dev` / `Admin1234!` (role: ADMIN)
- `scheduler@<slug>.dev` / `Admin1234!` (role: SCHEDULER)

Slugs: `sunrise`, `golden`, `harmony`. Example: `admin@sunrise.dev` / `Admin1234!`.


---

## Mobile App (React Native / Expo SDK 52)

**Stack:** Expo SDK 52, React Native 0.76, TypeScript, React Navigation 6, React Query v5, Zustand v4, Expo SQLite v13, Expo SecureStore, Expo Notifications, Expo Location, axios + axios-mock-adapter.

### Common Commands
```bash
cd mobile
npm install                     # install dependencies (first time)
npm test                        # Jest unit tests (jest-expo preset)
npm run lint                    # ESLint
npx expo start                  # start Expo dev server (scan QR with Expo Go)
npx expo start --android        # start on Android emulator
npx expo start --ios            # start on iOS simulator
```

**iOS simulator prerequisite:** `xcode-select -p` must return `/Applications/Xcode.app/Contents/Developer` (not Command Line Tools). If it doesn't:
```bash
sudo xcode-select -s /Applications/Xcode.app/Contents/Developer
sudo xcodebuild -license accept
```
Then open Xcode once to install additional components before running `npx expo start --ios`.

### Mock vs Live API
The app ships with `axios-mock-adapter` intercepting all 21 BFF endpoints when `EXPO_PUBLIC_USE_MOCKS=true` (default in `mobile/.env`). To point at a real BFF:
```
EXPO_PUBLIC_USE_MOCKS=false
EXPO_PUBLIC_BFF_URL=http://localhost:8081
```
No code changes required — the API client reads these at startup.

### Key Architecture
- `src/mocks/handlers.ts` — axios-mock-adapter setup for all BFF endpoints (350ms simulated delay)
- `src/mocks/data.ts` — typed mock fixtures with fixed UUIDs
- `src/store/authStore.ts` — Zustand store; tokens and caregiver profile persisted via Expo SecureStore (`caregiverProfile` key)
- `src/store/visitStore.ts` — active visit state (cleared on clock-out or void)
- `src/db/events.ts` — SQLite offline event queue; drained by `useOfflineSync` on reconnect
- `src/navigation/TabBar.tsx` — custom tab bar with raised center FAB; turns red when a visit is active

### Conventions
- All server state via React Query (`useQuery` / `useMutation`); UI-only state via Zustand.
- Never fetch data directly in a screen — use a hook in `src/hooks/` that wraps React Query.
- Individual Zustand selectors (`useStore(s => s.field)`) — never destructure the whole store.
- Do not compute EVV compliance status in the mobile app — the BFF forwards it from Core API.
- Do not include PHI in push notification payloads — use `shiftId` / event type codes only.

### Test Mock Data
`mobile/src/mocks/data.ts` — use these fixed-UUID fixtures in Jest tests instead of inventing values.

---

## Mobile BFF (Java 25 / Spring Boot 3.4.4)

```bash
cd bff
mvn spring-boot:run             # start BFF (http://localhost:8081)
mvn test
```

---

## Backend (Java 25 / Spring Boot 3.4.4)

**Stack:** Java 25, Spring Boot 3.4.4. Virtual threads enabled globally via `spring.threads.virtual.enabled: true`. `TenantContext` uses plain `ThreadLocal` (safe with virtual threads — each virtual thread has isolated ThreadLocal storage).

### Conventions
- DTOs are immutable records; never expose JPA entities directly from controllers.
- Use `@RestController` + `ResponseEntity<T>` for all endpoints.
- All endpoints are versioned: `/api/v1/...`
- Validation via `jakarta.validation` annotations on request DTOs.
- Exceptions bubble up to a global `@ControllerAdvice` handler — don't catch-and-swallow.
- Entity timestamps use `LocalDateTime.now(ZoneOffset.UTC)` — always explicit UTC.
- Entity IDs are UUIDs (`GenerationType.UUID`).

### Common Commands
```bash
cd backend
mvn spring-boot:run             # start dev server (http://localhost:8080)
mvn test                        # run all tests
mvn verify                      # compile, test, package
mvn flyway:info                 # check migration status
mvn test -Dtest=ClassName       # run a single test class
```

### Database Migrations
Flyway migrations live in `backend/src/main/resources/db/migration/`. Naming: `V<N>__<description>.sql`. Always use the next sequential integer — gaps break Flyway. Dev H2 console: `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:hcaredb`, no credentials).

### JWT Key Design
Two separate signing keys are in use. `hcare.jwt.secret` signs admin/scheduler tokens; `hcare.portal.jwt.secret` signs family portal tokens. `JwtTokenProvider` exposes `parseAdminClaims()` and `parsePortalClaims()` as separate entry points — never use a unified parse path that accepts both. `validateToken()` / `getUserId()` / `getRole()` are admin-only; only `parseAndValidate()` (used by `JwtAuthenticationFilter`) attempts the portal key as a fallback.

---

## Testing Standards

| Layer | Tool | Minimum Target |
|---|---|---|
| Frontend unit | Vitest + Testing Library | 80% coverage |
| Frontend e2e | Playwright | Critical user flows |
| Mobile unit | Jest (jest-expo) + Testing Library | 80% coverage |
| Backend unit | JUnit 5 + Mockito | 80% coverage |

- Write tests before or alongside new code — not after.
- Never commit with failing tests.

---

## Security Practices

- Secrets live in environment variables or a secrets manager — never in code or `.env` files committed to the repo.
- All endpoints require authentication unless explicitly annotated `@Public`.
- Sanitize and validate all user input server-side regardless of client-side validation.
- Dependencies are scanned on every PR; address HIGH/CRITICAL CVEs before merging.
- CORS is configured explicitly in `SecurityConfig` — avoid wildcard origins in production.

---

## Code Style

- **Java:** Google Java Style (enforced via Checkstyle).
- **TypeScript/React:** Prettier + ESLint (Airbnb config). Run `npm run lint --fix` before committing.
- Commit messages follow Conventional Commits: `feat:`, `fix:`, `chore:`, `docs:`, etc.
- PRs should be small and focused — one logical change per PR.

---

## Key Environment Variables

```
# Backend
SPRING_PROFILES_ACTIVE=dev|staging|prod
JWT_SECRET=<256-bit minimum HMAC-SHA256 secret, required in prod>
HCARE_PORTAL_JWT_SECRET=<256-bit minimum HMAC-SHA256 secret, required in prod — startup fails without it>
HCARE_PORTAL_BASE_URL=<base URL for portal invite links, default http://localhost:5173>
HCARE_PORTAL_JWT_EXPIRATION_DAYS=<portal JWT lifetime in days, default 30>
HCARE_SCORING_WEEKLY_RESET_CRON=<cron expression, default "0 0 0 * * MON" (Monday 00:00 UTC); set to "-" in tests>

# Frontend
VITE_API_BASE_URL=http://localhost:8080/api/v1
VITE_FEATURE_FLAGS_URL=...

# Mobile
EXPO_PUBLIC_USE_MOCKS=true|false      # true = axios-mock-adapter; false = real BFF
EXPO_PUBLIC_BFF_URL=http://localhost:8081
```

Dev profile uses H2 in-memory DB. Test profile uses Testcontainers PostgreSQL 16. Prod requires external Postgres and real `JWT_SECRET` + `HCARE_PORTAL_JWT_SECRET`.

---

## What to Avoid

- Do not add business logic to controllers — delegate to services.
- Do not use `@Autowired` field injection — prefer constructor injection.
- Do not fetch data directly in React components — use a custom hook wrapping React Query.
- Do not bypass the DTO layer or return raw entity objects from APIs.
- Do not introduce new dependencies without a brief note in the PR explaining why.
- Do not compute EVV compliance status in the BFF or store it — Core API is the single authority.
- Do not add scoring logic or query scoring tables (`caregiver_scoring_profiles`, `caregiver_client_affinities`) outside `com.hcare.scoring` — go through `ScoringService`.
- Do not enforce multi-tenancy at the service layer — the Hibernate `agencyFilter` handles it.
- Do not include PHI in push notification payloads — payloads carry only `shiftId`/event type codes.
- Do not load real PHI into local H2 development environments — use synthetic/test data only.
