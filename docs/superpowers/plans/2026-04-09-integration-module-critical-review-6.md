# Critical Implementation Review #6
**Plan:** `2026-04-09-integration-module.md`
**Reviewer:** Senior Staff Engineer
**Date:** 2026-04-09
**Previous reviews:** critical-review-1.md (C1–C7), critical-review-2.md (C8–C11), critical-review-3.md (C12–C14), critical-review-4.md (C15, M_new_8–M_new_9), critical-review-5.md (C16, M_new_10–M_new_11)

---

## 1. Overall Assessment

Reviews #1–#5 have addressed all sixteen prior critical issues; the plan is now structurally sound and handles the hardest correctness problems: tenant isolation, double-submission, TOCTOU config races, and plaintext credential leakage. One new critical issue emerges from the C16 fix: unchecked exceptions thrown inside the per-record drain loop (e.g., `MissingCredentialsException` from the new credential re-resolution step, or `UnsupportedAggregatorException` from the strategy factory) propagate through `drainForAgency()` to the outer per-agency loop, which has no `catch` — one misconfigured agency aborts processing for all remaining agencies on that nightly run. Two minor issues complete the findings.

---

## 2. Critical Issues

### C17 — Unchecked exception inside `drainForAgency()` aborts all subsequent agencies

**Description:** The outer per-agency loop from the C9 note is:

```java
for (UUID agencyId : agencies) {
    TenantContext.setAgencyId(agencyId);
    try {
        drainForAgency(agencyId);  // ← exception here propagates out of the for-loop
    } finally {
        TenantContext.clear();
    }
}
```

The C16 drain loop now calls two operations that can throw unchecked exceptions before `strategy.submit()` is reached:

1. `credRepo.findByAgencyIdAndAggregatorType(...).orElseThrow(() -> new MissingCredentialsException(...))`
2. `strategyFactory.strategyFor(next.getAggregatorType())` — throws `UnsupportedAggregatorException` if the enum value is unrecognized

Both are unchecked. If either throws for agency A's first record, the exception propagates out of `drainForAgency(A)`, past the `finally { TenantContext.clear() }`, and terminates the entire `for (UUID agencyId : agencies)` loop. Agencies B, C, D are never processed. EVV submissions for those agencies fail silently — no record is marked, no error is surfaced to the agency admin, and the next nightly run 24 hours later may again fail on agency A's misconfiguration.

**Why it matters:** The EVV drain job is a compliance-critical nightly process. One agency whose credential record was deleted (or whose aggregator enum value is stale after a rename) would block all other agencies' submissions indefinitely. Because the records remain `PENDING` (the exception is thrown before `markInFlight()`), there is no visible signal in `evv_submission_records` that anything went wrong for agencies B–D — they look like they simply have not been attempted yet.

**Fix:** Wrap `drainForAgency()` in a per-agency try-catch in the outer loop. The `finally` for `TenantContext.clear()` stays; add a `catch (Exception e)` that logs ERROR with the agencyId and continues:

```java
for (UUID agencyId : agencies) {
    TenantContext.setAgencyId(agencyId);
    try {
        drainForAgency(agencyId);
    } catch (Exception e) {
        log.error("Drain failed for agency {} — skipping to next agency", agencyId, e);
        // TenantContext.clear() still runs in finally below
    } finally {
        TenantContext.clear();
    }
}
```

Additionally, within `drainForAgency()` itself, the credential/strategy resolution for a single record should be caught per-record so that one bad record does not abort the agency's entire drain:

```java
// Inside the while loop, after claimed > 0 and context_json null-check:
EvvSubmissionStrategy strategy;
Object typedCreds;
try {
    strategy = strategyFactory.strategyFor(next.getAggregatorType());
    AgencyEvvCredentials creds = credRepo.findByAgencyIdAndAggregatorType(
            agencyId, next.getAggregatorType())
        .orElseThrow(() -> new MissingCredentialsException(...));
    typedCreds = encryptionService.decrypt(creds.getCredentialsEncrypted(),
        strategy.credentialClass());
} catch (MissingCredentialsException | UnsupportedAggregatorException e) {
    log.error("Cannot resolve credentials/strategy for record {} agency {} — marking REJECTED",
        next.getId(), agencyId, e);
    markFinal(next.getId(), EvvSubmissionResult.failure("CONFIG_ERROR", e.getMessage()));
    continue;
}
```

Update both the C9 code snippet (outer loop) and the C5+C13 code snippet (inner loop) in Step 5.

---

## 3. Previously Addressed Items

All sixteen critical issues from Reviews #1–#5 are now reflected in the plan:

- **C1** — TenantContext re-bound before repo calls in `onShiftCompleted()`
- **C2** — `@Transactional(REQUIRES_NEW)` on `onShiftCompleted()`
- **C3** — COALESCE functional index removed; service-layer uniqueness
- **C4** — `submission_mode` column distinguishes BATCH from REAL_TIME
- **C5** — `IN_FLIGHT` two-phase drain; startup watchdog
- **C6** — `credentialClass()` on `EvvSubmissionStrategy`
- **C7** — `IntegrationConnectorProperties` + full YAML block
- **C8** — `AgencyIntegrationConfigService` added
- **C9** — `EvvSubmissionRecordSystemRepository`; per-agency TenantContext loop
- **C10** — `OfficeAllyTransmissionStrategy` stub returns failure, does not throw
- **C11** — Both entities updated; `EvvSubmissionRecord` canonical; `EvvRecord` denormalized copy
- **C12** — `AgencyIntegrationConfigService.save()` uses `@Lock(PESSIMISTIC_WRITE)`
- **C13** — Per-record (not bulk) `IN_FLIGHT`; crash recovery resets exactly one row
- **C14** — `EvvSubmissionRecord` has no `@Filter agencyFilter`; Javadoc documents invariant
- **C15** — `findForUpdate()` returns `Optional<AgencyIntegrationConfig>`; lock now valid
- **C16** — `EvvSubmissionContext` carries no credentials; plaintext secrets no longer in `context_json`; drain job re-decrypts at submit time
- **M_new_1** — JSch replaced with `com.github.mwiede:jsch:0.2.22`
- **M_new_6** — ±25% jitter in `RetryingEvvSubmissionStrategy`
- **M_new_7** — `DatabaseEvvBatchQueue.enqueue()` idempotent
- **M_new_8** — `AgencyIntegrationConfigServiceTest` in test table
- **M_new_9** — `markInFlight()` returns `int`; drain loop skips submit on 0
- **M_new_10** — `EvvSubmissionServiceIT` specifies credential seeding
- **M_new_11** — Null-guard for `context_json` marks record REJECTED instead of NPE

---

## 4. Minor Issues & Improvements

**M_new_12 — `EvvSubmissionStrategy.submit()` signature inconsistency**
The package layout description still reads "submit/update/void" with no parameter types. The C16 code examples show `strategy.submit(ctx, typedCreds)` — a two-argument form. But the interface description has not been updated to match. An implementer reading Step 4 before Step 5 will write the wrong signature. Update the interface description to: `submit(EvvSubmissionContext, Object typedCreds) / update(...) / void(...)`.

**M_new_13 — Credential re-decrypted once per record in drain loop — N DB calls per agency batch**
Inside the per-record while loop, `credRepo.findByAgencyIdAndAggregatorType(agencyId, next.getAggregatorType())` issues a database round-trip and AES-GCM decryption for every record in the batch. An agency with 500 PENDING records and one aggregator type triggers 500 identical credential lookups and decryptions. Cache the resolved credentials per `(agencyId, aggregatorType)` tuple in `drainForAgency()` using a local `Map<AggregatorType, Object>`:

```java
Map<AggregatorType, Object> credCache = new HashMap<>();
// In the while loop, replace the credential resolution block:
Object typedCreds = credCache.computeIfAbsent(next.getAggregatorType(), aggType -> {
    AgencyEvvCredentials creds = credRepo.findByAgencyIdAndAggregatorType(agencyId, aggType)
        .orElseThrow(() -> new MissingCredentialsException(...));
    return encryptionService.decrypt(creds.getCredentialsEncrypted(),
        strategyFactory.strategyFor(aggType).credentialClass());
});
```

This reduces N lookups to at most K (one per distinct aggregator type for the agency).

---

## 5. Questions for Clarification

**Q1 (still open from Review #1):** Does `LocalScoringService.onShiftCompleted()` set `TenantContext`? The plan says "must be audited" but does not commit to a fix within this plan's scope.

**Q3 (still open from Review #2):** Can a REJECTED `evv_record_id` be re-submitted? The `UNIQUE` constraint blocks a second row. Clarify whether retry updates the existing record's `status` back to `PENDING` or is out of scope.

---

## 6. Final Recommendation

**Approve with changes.**

C17 (unchecked exceptions in `drainForAgency()` abort all subsequent agencies) is the only remaining blocker. The fix is additive — a `catch (Exception e)` in the outer loop and a targeted catch for credential/strategy resolution errors in the inner loop. After C17, the plan has no remaining critical correctness gaps. M_new_12 (interface signature) should be fixed alongside C17 since it describes the same code path. M_new_13 (credential cache) can be addressed during implementation.
