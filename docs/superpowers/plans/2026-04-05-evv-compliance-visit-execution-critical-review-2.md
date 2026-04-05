# Critical Implementation Review — EVV Compliance & Visit Execution (Review 2)

**Plan reviewed:** `2026-04-05-evv-compliance-visit-execution.md`
**Prior reviews:** Review 1 (`2026-04-05-evv-compliance-visit-execution-critical-review-1.md`)

---

## 1. Overall Assessment

The plan has matured meaningfully since Review 1. All seven critical and high-priority issues from that review have been addressed: `AuthorizationUnitService` with `Propagation.REQUIRES_NEW` is in place and the retry is now structurally sound, `@MockitoSettings(strictness = Strictness.LENIENT)` is present, the HYBRID comment and test are included, clock-in is correctly restricted to `ASSIGNED` only, missing state config returns 422, and all M1–M7 minor items have been resolved. This second pass focuses only on new issues introduced by those fixes or issues missed entirely in the first review.

---

## 2. Critical Issues

### C1: `AuthorizationUnitService.addUnits` retry loop is still broken — `REQUIRES_NEW` does not help with retries inside a single method call

**Description:** The fix from Review 1 correctly moves the authorization update into a separate Spring bean with `Propagation.REQUIRES_NEW`. However, the retry loop is **inside** the `@Transactional(propagation = Propagation.REQUIRES_NEW)` method. `REQUIRES_NEW` creates one new transaction when `addUnits` is first invoked — it does not create a new transaction per iteration of the loop.

Here is the control flow:

1. Caller invokes `authorizationUnitService.addUnits(...)` — Spring AOP intercepts and opens Transaction B (REQUIRES_NEW).
2. First iteration: `authorizationRepository.save(auth)` triggers a version conflict. Hibernate throws `OptimisticLockingFailureException`. Transaction B's session is now **marked rollback-only**.
3. The `catch` block catches the exception. The code tries `authorizationRepository.findById(...)` on the second iteration — but it is still inside the same Transaction B session, which is rollback-only. Hibernate will throw `org.hibernate.engine.transaction.spi.TransactionStatus: MARKED_FOR_ROLLBACK` or return the stale first-level cache entity. The retry cannot succeed.

The correct fix is to move the retry loop **outside** `addUnits` and have the caller invoke a single-attempt `addUnits` method up to three times, where each invocation gets its own REQUIRES_NEW transaction. Or, introduce a private `attemptAddUnits` method on a separate bean so Spring creates a fresh proxy invocation per retry.

**Why it matters:** Exactly the same silent failure mode as the original C1 from Review 1 — the loop gives false confidence but retries will always fail after the first `OptimisticLockingFailureException`.

**Fix:** Split the retry loop out of the `@Transactional` method into a non-transactional retry wrapper. The simplest correct pattern is to have a thin retry wrapper in `VisitService` (which has no `@Transactional` wrapping around the retry call itself, since `clockOut`'s outer transaction has already been committed or flushed by the time the authorization update is called):

```java
// In VisitService — called AFTER evvRecordRepository.save + shiftRepository.save have flushed
// (the outer @Transactional clockOut transaction is still open but shift+EVV are saved)
// Keep AuthorizationUnitService.addUnits as a REQUIRES_NEW single-attempt method:

for (int attempt = 0; attempt < 3; attempt++) {
    try {
        authorizationUnitService.addUnits(
            shift.getAuthorizationId(), record.getTimeIn(), record.getTimeOut());
        break;
    } catch (OptimisticLockingFailureException e) {
        if (attempt == 2) throw e;
        try { Thread.sleep(50); }
        catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted during authorization unit retry", ie);
        }
    }
}
```

And `AuthorizationUnitService.addUnits` becomes a single-attempt method:

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void addUnits(UUID authorizationId, LocalDateTime timeIn, LocalDateTime timeOut) {
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
}
```

Each call to `authorizationUnitService.addUnits(...)` from the retry loop in `VisitService` creates a brand-new Transaction via `REQUIRES_NEW`, so a failed attempt's session is fully discarded before the next attempt begins.

**Note:** The retry loop in `VisitService.clockOut` must not itself be inside a `@Transactional` scope when calling `addUnits`, or `REQUIRES_NEW` will suspend the outer transaction but the retry loop still runs in the same outer logical context. The current plan has `clockOut` annotated `@Transactional` — verify that the call to `authorizationUnitService.addUnits` occurs after the outer transaction flushes shift+EVV, either by calling it after the method's `@Transactional` commit (i.e., in a separate non-transactional helper or via a `TransactionSynchronizationAdapter.afterCommit` hook), or by accepting that the retry runs within the still-open outer transaction with each authorization attempt getting its own REQUIRES_NEW sub-transaction (which is actually fine — REQUIRES_NEW suspends the outer transaction entirely for the duration of `addUnits`, so the outer session is not involved).

---

### C2: `clockOut` transaction boundary flaw — authorization update can roll back the clock-out

**Description:** `VisitService.clockOut` is annotated `@Transactional`. The method saves the EVV record and shift status, then calls `authorizationUnitService.addUnits(...)`. If `addUnits` throws after all 3 retry attempts (e.g., sustained high contention), the exception propagates back into the `clockOut` method. Because `clockOut` is transactional and the exception is a `RuntimeException`, Spring rolls back the entire `clockOut` transaction — including `evvRecordRepository.save(record)` and `shiftRepository.save(shift)`. The caregiver's clock-out is lost.

**Why it matters:** The plan's comment on line 1153–1155 says "The outer transaction (shift + EVV record) has already been flushed above." Flushed is not committed — flushed means the SQL was sent to the database within the transaction, but the transaction is still open and will roll back if an exception propagates. The caregiver clock-out silently disappears from the caregiver's perspective (they got a 500) but also from the database (the shift remains IN_PROGRESS).

**Fix:** Separate the clock-out commit from the authorization update at the transaction boundary. The cleanest approach is to register a `TransactionSynchronization` to run `addUnits` in `afterCommit`:

```java
// Inside clockOut, after evvRecordRepository.save + shiftRepository.save:
final UUID authId = shift.getAuthorizationId();
final LocalDateTime finalTimeIn = record.getTimeIn();
final LocalDateTime finalTimeOut = record.getTimeOut();

if (authId != null) {
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronizationAdapter() {
            @Override
            public void afterCommit() {
                for (int attempt = 0; attempt < 3; attempt++) {
                    try {
                        authorizationUnitService.addUnits(authId, finalTimeIn, finalTimeOut);
                        return;
                    } catch (OptimisticLockingFailureException e) {
                        if (attempt == 2) {
                            log.error("Authorization unit update failed after 3 attempts for authId={}", authId, e);
                            return; // do not throw — clock-out is already committed
                        }
                        try { Thread.sleep(50); }
                        catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                }
            }
        }
    );
}
```

This guarantees the clock-out commit is durable before the authorization update is attempted. If the authorization update ultimately fails, log the error and surface it via an alert/dead-letter mechanism (P2) — the shift is correctly COMPLETED and the EVV record is saved. Alternatively, make authorization unit updates eventually consistent via a separate background job that reconciles `usedUnits` nightly.

---

## 3. Previously Addressed Items (from Review 1)

- **C1 (Review 1):** `AuthorizationUnitService` with `Propagation.REQUIRES_NEW` introduced. The structural intent is correct; see new C1 above regarding the retry placement.
- **C2 (Review 1):** `@MockitoSettings(strictness = Strictness.LENIENT)` added to `EvvComplianceServiceTest` — fully resolved.
- **H1:** HYBRID comment added in `LocalEvvComplianceService.compute()` and `hybrid_state_uses_same_quality_checks_as_open` test added — fully resolved.
- **H2:** `clockIn` restricted to `ShiftStatus.ASSIGNED` only; `clockIn_on_open_shift_returns_409` test added — fully resolved.
- **H3:** Missing state config now throws `ResponseStatusException(UNPROCESSABLE_ENTITY)`; `getShiftDetail_returns_422_when_state_has_no_evv_config` test added — fully resolved.
- **M1:** `AuditAction` import issue addressed (no unused import visible in the updated plan).
- **M2:** `buildShiftDetail` now accepts `Client` parameter — fully resolved.
- **M3:** `clockOut_updates_authorization_used_units_for_visits_payer` test added — fully resolved.
- **M4:** `clockIn_offline_uses_device_captured_at_as_time_in` integration test added — fully resolved.
- **M5:** `clockIn_on_completed_shift_returns_409` test added — fully resolved.
- **M6:** Dead code in `manual_verification_method_returns_yellow` removed — fully resolved.
- **M7:** `clockOut_after_clockIn_sets_completed_and_computes_green` now uses a shift starting 10 minutes ago with a deterministic `GREEN` assertion — fully resolved.

---

## 4. Minor Issues & Improvements

### N1: `parseAllowedMethods` throws unchecked `IllegalArgumentException` on unknown enum names in DB

`VerificationMethod::valueOf` will throw `IllegalArgumentException` if `EvvStateConfig.allowedVerificationMethods` contains a string that does not map to a `VerificationMethod` enum value (e.g., a future value added to the DB before the code is deployed, or a typo in the seed SQL). This propagates as a 500 during `GET /api/v1/shifts/{id}`.

Add a graceful filter:

```java
.map(s -> {
    try { return VerificationMethod.valueOf(s); }
    catch (IllegalArgumentException e) {
        log.warn("Unknown VerificationMethod in EvvStateConfig: '{}'", s);
        return null;
    }
})
.filter(Objects::nonNull)
```

Or use `EnumUtils.getEnum` from Apache Commons (already a transitive dependency in most Spring Boot projects) if available.

### N2: `EvvComplianceServiceTest` count mismatch — plan says 14, updated plan shows 15

Step 6 of Task 1 states "Expected: `BUILD SUCCESS`, 15 tests passing, 0 failures." Step 2 of Task 1 references "Expected: 14 tests passing" in the original commentary (the comment in the updated test count header has not been updated). Minor documentation inconsistency — confirm the final test count and update both step comments to match.

### N3: `clockOut` does not handle the case where `record.getTimeIn()` is null

`AuthorizationUnitService.addUnits` calls `Duration.between(timeIn, timeOut)`. If for any reason `timeIn` is null (e.g., data inconsistency from a failed prior clock-in that partially saved), this throws a `NullPointerException`. Add a null guard in `addUnits`:

```java
if (timeIn == null || timeOut == null) {
    log.error("Cannot compute duration: timeIn={} timeOut={} for authorizationId={}", timeIn, timeOut, authorizationId);
    return;
}
```

### N4: `VisitControllerIT` seeds use `deleteAll()` — cross-test interference risk with `@Transactional` not present

The `@BeforeEach seed()` method calls `evvRecordRepo.deleteAll()`, `shiftRepo.deleteAll()`, etc. in dependency order. This is correct and safe. However, `VisitControllerIT` is not annotated `@Transactional` (correct for an integration test using `TestRestTemplate`), which means failed tests can leave dirty state if the delete order or a constraint violation causes a partial teardown. Consider using a `@Sql(scripts = "/truncate-all.sql", executionPhase = BEFORE_TEST_METHOD)` or a `@BeforeEach` that runs a raw `TRUNCATE ... CASCADE` to guarantee clean state regardless of prior test failures.

### N5: `clockOut_after_clockIn_sets_completed_and_computes_green` relies on sub-second timing

The `onTimeShift` is created with `LocalDateTime.now().minusMinutes(10).withSecond(0).withNano(0)`. The compliance check compares clock-in time against `shift.getScheduledStart()` with a 30-minute threshold. The clock-in time is set to `LocalDateTime.now(ZoneOffset.UTC)` inside `VisitService.clockIn`. If the CI machine is slow (e.g., a heavily loaded GitHub Actions runner where container startup takes longer than expected), the delta between `scheduledStart` (minus 10 minutes from test thread time) and actual `now` at clock-in could exceed 30 minutes (if the test takes >20 minutes from shift creation to clock-in HTTP roundtrip, which is extremely unlikely but theoretically possible). A safer buffer would be `minusMinutes(5)`, giving a 25-minute margin rather than 20, or simply use `minusMinutes(1)`.

### N6: `AgencyRepository.findById` called on every `buildShiftDetail` invocation with no caching

`buildShiftDetail` calls `agencyRepository.findById(shift.getAgencyId())` to resolve the fallback state code. In the common case, the same agency is fetched repeatedly across many shift reads (e.g., a scheduler viewing a day's 20 shifts calls this 20 times). Within a single request this is fine (Hibernate first-level cache hits). However, the comment in the plan about avoiding duplicate client queries (M2 fix) should have the same logic applied to Agency — pass it in from the caller or use a second-level cache annotation. Not a correctness issue, but a minor performance concern for future high-volume use.

---

## 5. Questions for Clarification

**Q1 (carried from Review 1, still open):** `closedSystemAcknowledgedByAgency` is a column on the global `EvvStateConfig` table — if one agency acknowledges the limitation, all agencies in that state see it as acknowledged. The plan hasn't addressed whether this is intentional for P1. This should be documented as a known limitation in either the plan or a `// TODO(P2)` comment in `LocalEvvComplianceService`.

**Q2 (carried from Review 1, still open):** If clock-in was captured offline (`deviceCapturedAt` used as `timeIn`) but clock-out was captured online (server time as `timeOut`), the duration used for authorization unit computation could be wrong if the device clock was skewed. No handling is present. At minimum, a comment acknowledging this edge case should appear in `AuthorizationUnitService.addUnits`.

**Q3 (new):** The plan uses `Thread.sleep(50)` for retry backoff in `AuthorizationUnitService`. With virtual threads enabled (per CLAUDE.md: "Prefer virtual threads for blocking I/O; don't block platform threads"), `Thread.sleep` on a virtual thread will park the virtual thread rather than block a platform thread — this is fine. However, if the service is ever run in a configuration without virtual threads enabled, this sleep blocks a platform thread in the Tomcat thread pool. Confirm virtual threads are configured in this project (e.g., `spring.threads.virtual.enabled=true` in `application.yml`) and add a comment to that effect.

---

## 6. Final Recommendation

**Approve with changes — two critical fixes required before implementation:**

1. **C1 (must fix):** Move the retry loop out of the `@Transactional(REQUIRES_NEW)` method. The current plan's retry is still inside a single REQUIRES_NEW transaction and will fail on the second attempt for the same reason as the original C1.
2. **C2 (must fix):** Decouple the authorization update from the `clockOut` transaction boundary using `afterCommit` synchronization or a separate non-transactional wrapper, so a sustained authorization update failure does not roll back the caregiver's clock-out record.

Minor items N1–N6 can be addressed inline during implementation. Items Q1 and Q2 carried from Review 1 should be documented as known limitations if not resolved.
