# CLAUDE.md

This file guides Claude Code when working in this repository.

## Project Overview

TODO: Add project overview

```
/frontend        # React (web + mobile-responsive)
/backend         # Java 25, Spring Boot
/infra           # IaC / deployment configs
```

---

## Architecture Principles

- **API-first** — all features are driven through versioned REST (or GraphQL) endpoints; the frontend never talks directly to the database.
- **Stateless backend** — no server-side session state; use JWT / OAuth 2.0 tokens.
- **Mobile-first UI** — every component must be responsive and touch-friendly before considering desktop enhancements.
- **Feature flags** — new behaviour is gated behind flags so web and mobile can ship independently.

---

## Frontend (React)


### Conventions
- Components live in `frontend/src/components/` and are co-located with their test and story files.
- Shared hooks go in `frontend/src/hooks/`; shared types in `frontend/src/types/`.
- Use React Query for all server state; Zustand only for purely client-side UI state.
- Prefer named exports over default exports.
- Mobile breakpoints first (`sm:`, `md:`, `lg:`) — never desktop-first overrides.


---

## Backend (Java 25 / Spring Boot)

**Stack:** Java 25, Spring Boot 3

### Conventions
- DTOs are immutable records; never expose JPA entities directly from controllers.
- Use `@RestController` + `ResponseEntity<T>` for all endpoints.
- All endpoints are versioned: `/api/v1/...`
- Validation via `jakarta.validation` annotations on request DTOs.
- Exceptions bubble up to a global `@ControllerAdvice` handler — don't catch-and-swallow.
- Prefer virtual threads (Java 21+) for blocking I/O; don't block platform threads.

### Common Commands
```bash
cd backend
./mvnw spring-boot:run          # start dev server (http://localhost:8080)
./mvnw test                     # run all tests
./mvnw verify                   # compile, test, package
./mvnw flyway:info              # check migration status
```

---

## Testing Standards

| Layer | Tool | Minimum Target |
|---|---|---|
| Frontend unit | Vitest + Testing Library | 80% coverage |
| Frontend e2e | Playwright | Critical user flows |
| Backend unit | JUnit 5 + Mockito | 80% coverage |
| Backend integration | Spring Boot Test + Testcontainers | All repository & controller layers |

- Write tests before or alongside new code — not after.
- Integration tests spin up a real Postgres container via Testcontainers; do not mock the database.
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

# Frontend
VITE_API_BASE_URL=http://localhost:8080/api/v1
VITE_FEATURE_FLAGS_URL=...
```

---

## What to Avoid

- Do not add business logic to controllers — delegate to services.
- Do not use `@Autowired` field injection — prefer constructor injection.
- Do not fetch data directly in React components — use a custom hook wrapping React Query.
- Do not bypass the DTO layer or return raw entity objects from APIs.
- Do not introduce new dependencies without a brief note in the PR explaining why.
