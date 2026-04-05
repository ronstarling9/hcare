# Critical Implementation Review — EVV Compliance & Visit Execution (Review 1)

**Plan reviewed:** `2026-04-05-evv-compliance-visit-execution.md`
**Prior reviews:** None (first review)

---

## 1. Overall Assessment

This is a well-structured, cohesive plan with clear separation of concerns: a pure `EvvComplianceService` computation layer, an orchestrating `VisitService`, and a thin `VisitController`. The compliance status logic correctly covers all spec-required statuses and the state-resolution chain is solid. However, there are two critical correctness bugs that would cause silent failures in production, and three high-priority gaps that need addressing before implementation.

---

## 2. Critical Issues

### C1: Authorization retry loop is broken inside the same `@Transactional` context

**Description:** `updateAuthorizationUnits` runs inside the `clockOut` method, which is annotated `@Transactional`. When `authorizationRepository.save(auth)` triggers a Hibernate flush and encounters a version conflict, Hibernate throws `StaleObjectStateException` (wrapped as `ObjectOptimisticLockingFailureException`). At this point, the current Hibernate `Session` is **marked rollback-only** — you cannot continue using it. The retry loop calls `authorizationRepository.findById()` again on the same poisoned session, which will either return the stale cached entity (Hibernate first-level cache) or throw immediately. The retry will never succeed because you cannot re-read from a session that is in rollback-only state.

**Why it matters:** The retry loop gives a false sense of correctness. In practice, the first `OptimisticLockingFailureException` causes the entire `clockOut` transaction to roll back, including the `evvRecord.setTimeOut()`, `shift.setStatus(COMPLETED)`, and the EVV audit log. The caregiver's clock-out is silently lost.

**Fix:** Move the authorization update into a **separate Spring bean** with `Propagation.REQUIRES_NEW`, which creates a fresh Hibernate session per retry. Add a new injectable `@Service AuthorizationUnitService` (package `com.hcare.domain`) with a single `@Transactional(propagation = Propagation.REQUIRES_NEW)` method:

```java
// New file: src/main/java/com/hcare/domain/AuthorizationUnitService.java
package com.hcare.domain;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthorizationUnitService {

    private final AuthorizationRepository authorizationRepository;

    public AuthorizationUnitService(AuthorizationRepository authorizationRepository) {
        this.authorizationRepository = authorizationRepository;
    }

    /**
     * Increments Authorization.usedUnits after a shift completes.
     * Runs in its own transaction (REQUIRES_NEW) so each retry gets a fresh Hibernate session —
     * OptimisticLockingFailureException from a prior attempt cannot poison the session.
     * Called from VisitService.clockOut AFTER the outer transaction commits shift+EVV record.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void addUnits(UUID authorizationId, LocalDateTime timeIn, LocalDateTime timeOut) {
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                Optional<Authorization> authOpt = authorizationRepository.findById(authorizationId);
                if (authOpt.isEmpty()) return;
                Authorization auth = authOpt.get();
                BigDecimal units;
                if (auth.getUnitType() == UnitType.HOURS) {
                    long minutes = Duration.between(timeIn, timeOut).toMinutes();
                    units = BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
                } else {
                    units = BigDecimal.ONE;
                }
                auth.addUsedUnits(units);
                authorizationRepository.save(auth);
                return;
            } catch (OptimisticLockingFailureException e) {
                if (attempt == 2) throw e;
                try { Thread.sleep(50); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during authorization unit retry", ie);
                }
            }
        }
    }
}
```

The `clockOut` method in `VisitService` removes `updateAuthorizationUnits(shift, record)` and instead calls `authorizationUnitService.addUnits(shift.getAuthorizationId(), record.getTimeIn(), record.getTimeOut())` — called **after** `evvRecordRepository.save(record)` and `shiftRepository.save(shift)`, still inside the outer `@Transactional` (REQUIRES_NEW creates a nested independent transaction that commits before returning). The outer transaction holds Shift + EVV committed; only the authorization unit update runs in its own nested transaction with retries.

Add `AuthorizationUnitService authorizationUnitService` to `VisitService`'s constructor and remove `private void updateAuthorizationUnits(...)` entirely.

---

### C2: Mockito strict stubbing causes `UnnecessaryStubbingException` in 4 tests

**Description:** The `@BeforeEach setup()` method stubs four methods on `stateConfig` for every test. Tests that return early — `null_record_returns_grey`, `co_resident_returns_exempt`, `private_pay_payer_returns_exempt`, and `missing_medicaid_id_returns_red` (which also returns before the state config is consulted) — never call `stateConfig.getSystemModel()`, `stateConfig.getAllowedVerificationMethods()`, etc. With `@ExtendWith(MockitoExtension.class)`, Mockito 4+ (shipped with Spring Boot 3.x) defaults to `STRICT_STUBS`, which fails with `UnnecessaryStubbingException` for stubs that are configured but never exercised.

**Why it matters:** All 14 tests will fail due to this exception before even running the assertion logic. The plan says "Expected: BUILD SUCCESS, 14 tests passing" but that won't happen.

**Fix:** Add `@MockitoSettings(strictness = Strictness.LENIENT)` to the test class:

```java
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EvvComplianceServiceTest {
    // existing content unchanged
}
```

---

## 3. Previously Addressed Items

None (first review).

---

## 4. High-Priority Issues

### H1: `HYBRID` system model falls through silently with no documentation

**Description:** `EvvSystemModel` has three values: `OPEN`, `CLOSED`, `HYBRID`. The compliance engine explicitly handles `CLOSED` and falls through to the quality checks for everything else (`OPEN` or `HYBRID`). There is no comment explaining whether HYBRID should be treated like OPEN, and no test covers HYBRID.

**Fix:** Add a comment in `LocalEvvComplianceService.compute()` before the quality-check block, and add one test:

```java
// HYBRID states: treated as OPEN for compliance computation — aggregator selection
// at submission time handles the payer-specific routing (PayerEvvRoutingConfig).
// HYBRID does not change the quality-check rules applied here.
```

And in `EvvComplianceServiceTest`:
```java
@Test
void hybrid_state_uses_same_quality_checks_as_open() {
    when(stateConfig.getSystemModel()).thenReturn(EvvSystemModel.HYBRID);
    assertThat(service.compute(buildCompleteRecord(), stateConfig, buildShift(), null, CLIENT_LAT, CLIENT_LNG))
        .isEqualTo(EvvComplianceStatus.GREEN);
}
```

### H2: Clock-in permitted on OPEN (unassigned) shifts creates orphaned EVV records

**Description:** The `clockIn` guard allows `ShiftStatus.OPEN` — an unassigned shift. An OPEN shift has `caregiverId = null`. Federal element 5 of an EVV record is "caregiverId: derivable from Shift.caregiverId." With `caregiverId = null`, the EVV record is missing a federal element. State aggregators would reject this.

Operationally, caregivers clock in on shifts assigned to them. An OPEN shift has no assigned caregiver, so no caregiver JWT should be associated with it. Allowing clock-in on OPEN shifts also bypasses scheduling workflow.

**Fix:** Restrict `clockIn` to `ShiftStatus.ASSIGNED` only:

```java
if (shift.getStatus() != ShiftStatus.ASSIGNED) {
    throw new ResponseStatusException(HttpStatus.CONFLICT,
        "Cannot clock in: shift must be ASSIGNED (current status: " + shift.getStatus() + ")");
}
```

Add a corresponding test in `VisitControllerIT`:
```java
@Test
void clockIn_on_open_shift_returns_409() {
    Shift openShift = shiftRepo.save(new Shift(
        agency.getId(), null, client.getId(), null,  // null caregiverId → OPEN
        serviceType.getId(), null,
        LocalDateTime.now().plusDays(1).withHour(9).withMinute(0).withSecond(0).withNano(0),
        LocalDateTime.now().plusDays(1).withHour(13).withMinute(0).withSecond(0).withNano(0)
    ));
    ClockInRequest req = new ClockInRequest(
        new BigDecimal("30.2672"), new BigDecimal("-97.7431"),
        VerificationMethod.GPS, false, null
    );
    ResponseEntity<String> resp = restTemplate.exchange(
        "/api/v1/shifts/" + openShift.getId() + "/clock-in",
        HttpMethod.POST, new HttpEntity<>(req, authHeaders()), String.class);
    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
}
```

### H3: Missing `EvvStateConfig` for client's state causes unhandled `IllegalStateException` (500)

**Description:** `buildShiftDetail` throws `IllegalStateException("No EVV config for state: " + stateCode)` if the seeded V2 migration doesn't have the client's state. `GlobalExceptionHandler` likely maps `IllegalStateException` to 500. This is a raw internal error surfaced to the caller with no actionable message. It can happen in production if a client has `serviceState` set to a code not yet in `evv_state_configs` (e.g., after adding a border-county client for a new state).

**Fix:** Return HTTP 422 with a meaningful message from the handler, or map `IllegalStateException` in `GlobalExceptionHandler`. Better: throw `ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, ...)` directly in `buildShiftDetail`:

```java
EvvStateConfig stateConfig = evvStateConfigRepository.findByStateCode(stateCode)
    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
        "EVV configuration not found for state: " + stateCode + 
        ". Contact support to configure EVV for this state."));
```

Add a test:
```java
@Test
void getShiftDetail_returns_422_when_state_has_no_evv_config() {
    // Save a client with an unconfigured serviceState
    client.setServiceState("ZZ"); // not a real state code
    clientRepo.save(client);
    ResponseEntity<String> resp = restTemplate.exchange(
        "/api/v1/shifts/" + shift.getId(),
        HttpMethod.GET, new HttpEntity<>(authHeaders()), String.class);
    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
}
```

---

## 5. Minor Issues & Improvements

**M1: Unused import `AuditAction` in `VisitService`**
Line: `import com.hcare.audit.AuditAction;` — never referenced. Remove it. Causes a compiler warning and fails Checkstyle.

**M2: Client loaded twice in `clockIn`**
`clockIn` loads `Client` to get `medicaidId`, then calls `buildShiftDetail` which loads `Client` again via `clientRepository.findById()`. Pass the already-loaded `client` object into `buildShiftDetail` to avoid the duplicate query:

```java
// Change signature to:
private ShiftDetailResponse buildShiftDetail(Shift shift, EvvRecord record, Client client) { ... }

// clockIn already has 'client', clockOut and getShiftDetail load it once and pass it in
```

**M3: No test for `VISITS` unit type in authorization update**
`clockOut_updates_authorization_used_units_for_hours_payer` only covers `UnitType.HOURS`. Add a parallel test for `UnitType.VISITS` verifying `usedUnits` increments by exactly `1.00`.

**M4: No integration test for offline clock-in/clock-out path**
`capturedOffline = true` with a `deviceCapturedAt` value is not exercised in `VisitControllerIT`. The offline path sets `timeIn` from `deviceCapturedAt` rather than server time — worth verifying with one test.

**M5: No test for clock-in on wrong-status shifts (COMPLETED, CANCELLED, MISSED)**
The conflict guard `shift.getStatus() != ASSIGNED && shift.getStatus() != OPEN` has no test verifying 409 is returned for `COMPLETED` or `CANCELLED` shifts. Add at least one:

```java
@Test
void clockIn_on_completed_shift_returns_409() {
    shift.setStatus(ShiftStatus.COMPLETED);
    shiftRepo.save(shift);
    ClockInRequest req = new ClockInRequest(
        new BigDecimal("30.2672"), new BigDecimal("-97.7431"),
        VerificationMethod.GPS, false, null
    );
    ResponseEntity<String> resp = restTemplate.exchange(
        "/api/v1/shifts/" + shift.getId() + "/clock-in",
        HttpMethod.POST, new HttpEntity<>(req, authHeaders()), String.class);
    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
}
```

(Note: after accepting H2's fix to block OPEN shifts too, update this test's guard expectation accordingly.)

**M6: `manual_verification_method_returns_yellow` test has dead code**
The test creates `EvvRecord r = buildCompleteRecord()` at the top (line 176) but never uses `r` — immediately creates `manual` instead. Remove the `r` assignment:

```java
@Test
void manual_verification_method_returns_yellow() {
    EvvRecord manual = new EvvRecord(UUID.randomUUID(), UUID.randomUUID(), VerificationMethod.MANUAL);
    manual.setClientMedicaidId("TX12345");
    manual.setLocationLat(CLIENT_LAT);
    manual.setLocationLon(CLIENT_LNG);
    manual.setTimeIn(LocalDateTime.of(2026, 4, 20, 9, 5));
    manual.setTimeOut(LocalDateTime.of(2026, 4, 20, 13, 10));
    assertThat(service.compute(manual, stateConfig, buildShift(), null, CLIENT_LAT, CLIENT_LNG))
        .isEqualTo(EvvComplianceStatus.YELLOW);
}
```

**M7: `clockOut_after_clockIn_sets_completed_and_computes_green` test asserts `isIn("GREEN", "YELLOW")` — too loose**
The test comment acknowledges that `timeIn` will be far from `scheduledStart` (tomorrow 9am), causing YELLOW. The test should be deterministic. Fix by creating a shift scheduled to start within the last 30 minutes:

```java
LocalDateTime start = LocalDateTime.now().minusMinutes(10).withSecond(0).withNano(0);
Shift onTimeShift = shiftRepo.save(new Shift(
    agency.getId(), null, client.getId(), adminUser.getId(),
    serviceType.getId(), null, start, start.plusHours(4)
));
```

Then clock in on `onTimeShift` and assert `complianceStatus` is `"GREEN"` definitively.

---

## 6. Questions for Clarification

**Q1:** The spec says `closedSystemAcknowledgedByAgency` is a column on `EvvStateConfig`, which is a global (not agency-scoped) table. If Agency A acknowledges the closed-system limitation, all agencies in that state see it as acknowledged. Is this intentional for P1? (The alternative — a per-agency acknowledgment — would require a new `AgencyEvvConfig` table.) The plan correctly uses the existing column without redesigning, but this deserves a note acknowledging the limitation.

**Q2:** The `updateAuthorizationUnits` logic uses `Duration.between(record.getTimeIn(), record.getTimeOut()).toMinutes()`. If the EVV record was captured offline and `deviceCapturedAt` was used as `timeIn`, but the clock-out happened online (using server time as `timeOut`), the duration could be wildly wrong (e.g., if the device clock was off). Should the unit calculation use `deviceCapturedAt` consistently when `capturedOffline = true`? The current plan doesn't address this edge case.

**Q3:** The plan's file map says `ResourceType.SHIFT` is added "for audit log on shift reads" but no code in the plan uses `ResourceType.SHIFT` — only `ResourceType.EVVRECORD` is used in `VisitService`. Is SHIFT intended for a future `GET /api/v1/shifts` list endpoint? If not needed now, it should be removed from the plan (YAGNI).

---

## 7. Final Recommendation

**Approve with changes — two critical fixes required before implementation:**

1. **C1 (must fix):** Authorization retry loop restructured using `AuthorizationUnitService` with `Propagation.REQUIRES_NEW`.
2. **C2 (must fix):** Add `@MockitoSettings(strictness = Strictness.LENIENT)` to the unit test class.

High-priority items H1–H3 are strongly recommended before merge. Minor items M1–M7 can be fixed inline during implementation. The overall plan quality is high — the compliance engine design is clean, the transaction structure is sound except for C1, and the test coverage is thorough with the gaps noted above.
