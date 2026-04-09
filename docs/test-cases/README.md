# Manual Test Cases

Human-executed test cases for hcare features. Each file covers one functional area.

## Conventions

### File naming
`<feature-area>.md` — one file per feature. Use kebab-case.

### Test case IDs
`TC-<AREA>-<NNN>` — e.g. `TC-FP-001`. The area code is defined at the top of each file. IDs are never reused, even after a case is deleted (mark it `[REMOVED]` instead).

### Structure of each test case

```
### TC-XXX-NNN — Short title

**Priority:** P0 / P1 / P2
**Preconditions:** What must be true before starting
**Steps:** Numbered list of actions
**Expected result:** What correct behaviour looks like
**Notes:** Edge cases, caveats, related cases (optional)
```

**Priority guide:**

| Priority | Meaning |
|---|---|
| P0 | Blocks release — must pass before any deploy |
| P1 | Core workflow — run on every release candidate |
| P2 | Edge case or secondary flow — run on major releases |

### Pass / Fail notation

When running a suite, annotate each case inline:

```
**Result:** PASS — 2026-04-09 · ron
**Result:** FAIL — 2026-04-09 · ron — [brief description of what went wrong]
```

### Dev environment quick-start

```bash
./dev-start.sh          # starts backend (:8080) + frontend (:5173)
```

Default credentials (all passwords `Admin1234!`):

| Agency | Admin | AI scheduling |
|---|---|---|
| Sunrise Home Care (TX) | admin@sunrise.dev | off |
| Golden Years Care (FL) | admin@golden.dev | on |
| Harmony Home Health (CA) | admin@harmony.dev | off |

See `docs/dev-seed-reference.md` for full client and caregiver lists.

## Index

| File | Area code | Feature |
|---|---|---|
| [family-portal.md](family-portal.md) | FP | Family Portal — invite, verify, dashboard, access control |
| [mobile-app.md](mobile-app.md) | MOB | Mobile app — login, today, clock-in, visit, open shifts, messages, offline sync |
