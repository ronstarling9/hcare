# Critical Implementation Review #3
**Plan:** `2026-04-09-integration-module.md`
**Reviewer:** Senior Staff Engineer
**Date:** 2026-04-09
**Previous reviews:** critical-review-1.md (C1–C7, M1–M6), critical-review-2.md (C8–C11, M_new_1–M_new_5)

---

## 1. Overall Assessment

Reviews #1 and #2 together drove the plan from a first-cut draft to a substantially correct design. All eleven prior critical issues (C1–C11) are now reflected in the updated plan, as are the majority of the minor items. Three new critical issues emerge on close reading of the post-revision plan: a TOCTOU race condition in the `AgencyIntegrationConfigService` check-before-insert that leaves the database without any concurrency protection, an over-aggressive bulk `IN_FLIGHT` mark in `EvvBatchDrainJob` that causes over-reset on crash recovery, and an unspecified Hibernate filter bypass mechanism in `EvvSubmissionRecordSystemRepository` that will silently fail in the per-agency loop context. Two minor issues complete the findings.

---

## 2. Critical Issues

### C12 — TOCTOU race condition in `AgencyIntegrationConfigService.save()`

**Description:** The plan specifies a "check-before-insert" using `existsByAgencyIdAndIntegrationTypeAndStateCodeAndPayerType()` before calling `repo.save(config)`. Two concurrent admin requests for the same `(agencyId, type, stateCode, payerType)` tuple can both read `false` from the exists check before either insert commits, because the exists check is a plain SELECT with no lock. Both inserts then succeed. The database provides no uniqueness constraint (the functional index was removed in C3) to catch the duplicate.

**Why it matters:** Duplicate `agency_integration_configs` rows produce non-deterministic routing — whichever `findActive()` call wins `findFirst()` determines which connector is used. Duplicate credentials can lead to billing claims being submitted to two clearinghouses or payroll exported twice. Concurrency in admin config screens is low-frequency but non-zero (two browser tabs, a Scheduler and Admin role editing simultaneously).

**Fix:** Add pessimistic locking to the uniqueness check. Use `@Lock(LockModeType.PESSIMISTIC_WRITE)` on the existence check query in `AgencyIntegrationConfigRepository`:
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM AgencyIntegrationConfig c " +
       "WHERE c.agencyId = :agencyId AND c.integrationType = :type " +
       "AND c.stateCode IS NOT DISTINCT FROM :stateCode AND c.payerType IS NOT DISTINCT FROM :payerType")
boolean existsForUpdate(...);
```
Call `existsForUpdate()` inside the `@Transactional` `save()` method. The row-level lock prevents concurrent readers until the inserting transaction commits. H2 supports `PESSIMISTIC_WRITE` in test mode. This is an admin-only low-frequency operation — pessimistic locking is the right tradeoff here.

Note: `IS NOT DISTINCT FROM` handles NULL equality correctly in both PostgreSQL and H2 (unlike `=`).

---

### C13 — `EvvBatchDrainJob` Tx1 bulk-marks the entire agency batch as `IN_FLIGHT` before processing any record

**Description:** Step 5's drain job description says:
> "Tx 1: `UPDATE evv_submission_records SET status = 'IN_FLIGHT' WHERE agency_id = :agencyId AND submission_mode = 'BATCH' AND status = 'PENDING'`"

This is a single bulk UPDATE that marks every PENDING batch record for an agency as `IN_FLIGHT` before a single submit call is made. The subsequent per-record processing (submit → Tx 2) then updates records one by one. If the service crashes after marking 1000 records `IN_FLIGHT` but before processing them, the startup watchdog resets all 1000 back to `PENDING` — including any that already reached an aggregator (if the crash happened mid-loop, after some submits completed but before their Tx 2 committed).

**Why it matters:** For SFTP-based aggregators (Netsmart/Tellus), a batch file is written atomically per drain run, so re-submission after crash is lower risk. For REST-based aggregators (real-time path), the plan already avoids this (real-time submissions are not drained by this job). However, the watchdog's 10-minute window is global — any record marked `IN_FLIGHT` before the window will be reset to `PENDING` and re-submitted, producing the exact double-submission this plan is designed to prevent.

**Fix:** Process one record at a time: Tx 1 marks a single record `IN_FLIGHT`, submit, Tx 2 updates status. The drain loop is:
```java
EvvSubmissionRecord next;
while ((next = systemRepo.findFirstByAgencyIdAndSubmissionModeAndStatusOrderById(
            agencyId, BATCH, PENDING)) != null) {
    markInFlight(next.getId());  // Tx 1 — single row
    EvvSubmissionResult result = strategy.submit(deserializeContext(next));
    markFinal(next.getId(), result);  // Tx 2 — single row
}
```
The `findFirst...` query implicitly serializes work per agency since only one record is in `IN_FLIGHT` at a time. If the service crashes, at most one record is in an ambiguous state, and the watchdog resets exactly that one record. Update the Step 5 description to specify per-record (not per-agency-bulk) processing.

---

### C14 — `EvvSubmissionRecordSystemRepository` filter bypass mechanism unspecified and fragile

**Description:** The plan says `EvvSubmissionRecordSystemRepository` is "explicitly unfiltered (no agencyFilter)" and that it "uses explicit `agencyId` parameters in all `@Query` methods — the Hibernate filter is intentionally not relied upon." However, the mechanism by which the filter is bypassed is never stated. In this codebase, `TenantFilterAspect` enables the `agencyFilter` Hibernate filter `@Before` repository calls inside `@Transactional` — if `TenantContext` is set when the system repository is called, the aspect will enable the filter regardless of which repository interface is used. Inside the per-agency loop:
```java
TenantContext.setAgencyId(agencyId);  // set here
drainForAgency(agencyId);              // systemRepo called with TenantContext active
```
TenantFilterAspect fires, enables the filter, and the `agencyId` JPQL param in `@Query` methods becomes redundant — the filter already restricts rows. This is accidentally correct for reads but creates a false sense that the system repo is "unfiltered." More critically: if someone adds a `@Query` without an `agencyId` param (thinking the class name signals safety), the filter-enabled path silently returns the right data, masking the bug. In a scheduler context without TenantContext, the same query returns ALL rows — a cross-agency leak.

**Why it matters:** The system repo's safety guarantee depends on a naming convention and a comment, not a mechanical enforcement. A future developer who calls `EvvSubmissionRecordSystemRepository` from a non-drain context without understanding the filter semantics will either get cross-agency data (no TenantContext) or accidentally correct behavior (TenantContext set) — neither of which surfaces as a test failure.

**Fix:** Choose one explicit, enforceable approach and document it in the plan:

**Option A (recommended):** Do not annotate `EvvSubmissionRecord` with `@Filter agencyFilter`. The entity is used by both the real-time path (tenant-scoped via service layer) and the drain job (system-scoped). Tenant isolation for the real-time path is enforced at the `EvvSubmissionService` level (which already receives `agencyId` from `ShiftCompletedEvent`), not at the Hibernate filter level. Remove `@Filter` from `EvvSubmissionRecord` and add a class-level Javadoc: "No Hibernate agencyFilter — tenant isolation enforced at service layer." Then `EvvSubmissionRecordSystemRepository` is genuinely unfiltered by design, and there is no confusion.

**Option B:** Keep `@Filter agencyFilter` on `EvvSubmissionRecord` but make `EvvSubmissionRecordSystemRepository` use `@Query(nativeQuery = true)` for all methods. Native queries bypass Hibernate filters regardless of session state. This is more boilerplate but requires no entity annotation change.

The plan must specify which option and update the entity description in Step 4 accordingly.

---

## 3. Previously Addressed Items

All four critical issues from Review #2 are now reflected in the plan:

- **C8** — `AgencyIntegrationConfigService` added to `config/` layout with `save()` + `findActive()` signatures.
- **C9** — `EvvSubmissionRecordSystemRepository` added; per-agency loop with `TenantContext` set/cleared shown in Step 5.
- **C10** — `OfficeAllyTransmissionStrategy` is now a stub returning `ClaimSubmissionReceipt.failure(...)`.
- **C11** — Both entities updated post-submission; `EvvSubmissionRecord` is canonical; `EvvRecord` carries a denormalized copy for compliance reads.
- **M_new_3** — `EvvBatchDrainJobTest` added to the test table.
- **M_new_4** — `@EnableAsync` note is now unconditional.

---

## 4. Minor Issues & Improvements

**M_new_6 — No jitter in `RetryingEvvSubmissionStrategy` exponential backoff**
Fixed delays (2s/4s/8s) produce synchronized retry storms when an aggregator recovers from an outage. All agencies retry at the same instant. Add ±25% jitter: `sleep = baseMs * (1 + ThreadLocalRandom.current().nextDouble(-0.25, 0.25))`.

**M_new_7 — `DatabaseEvvBatchQueue.enqueue()` is not idempotent**
`evv_submission_records.evv_record_id` has a `UNIQUE` constraint. If `ShiftCompletedEvent` fires twice for the same shift (rare but possible with Spring's `ApplicationEventPublisher` under certain retry conditions), the second `enqueue()` call will throw a `DataIntegrityViolationException` at the persistence layer, which propagates through the `@Async` listener and goes unhandled. Add an idempotency check: use `INSERT ... WHERE NOT EXISTS` (native query) or catch `DataIntegrityViolationException` and treat it as a no-op with a WARN log. The second fire is likely a bug elsewhere, so logging it is important.

---

## 5. Questions for Clarification

**Q1 (still open from Review #1):** Has `LocalScoringService.onShiftCompleted()` been audited for the C1/C2 tenant exposure? The plan notes to audit it but does not commit to a fix. If the exposure exists, this plan's implementation window is the right moment to patch it — the same `TenantContext.setAgencyId()` + `REQUIRES_NEW` pattern applies.

**Q3 (still open from Review #2):** Can a dead-lettered (REJECTED) real-time submission ever be re-submitted? The `UNIQUE` constraint on `evv_record_id` prevents a second `EvvSubmissionRecord` row. If manual retry (by an admin) is a future requirement, the constraint blocks it. Clarify: either (a) retry works by updating the existing record's `status` back to `PENDING` and `submission_mode` as appropriate, or (b) retry is intentionally out of scope and the constraint stays as-is.

---

## 6. Final Recommendation

**Approve with changes.**

C12 (TOCTOU in config save) and C13 (bulk IN_FLIGHT marking) are the highest priority — C12 leaves the config table unprotected against concurrent writes, and C13 turns crash recovery into a re-submission risk that directly contradicts C5's intent. C14 (filter bypass mechanism) is a correctness/maintainability gap that must be resolved by either removing `@Filter` from `EvvSubmissionRecord` or switching the system repository to native queries. None require structural changes. After addressing C12–C14, the plan is ready for implementation.
