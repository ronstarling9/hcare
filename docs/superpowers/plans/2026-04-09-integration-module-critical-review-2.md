# Critical Implementation Review #2
**Plan:** `2026-04-09-integration-module.md`
**Reviewer:** Senior Staff Engineer
**Date:** 2026-04-09
**Previous reviews:** critical-review-1.md (C1–C7, M1–M6)

---

## 1. Overall Assessment

Review #1's seven critical issues have been addressed: TenantContext propagation (C1+C2), H2-incompatible index (C3), batch/real-time conflation (C4), double-submission (C5), credential class resolution (C6), and connector properties (C7) are all reflected in the updated plan. The revision is a significant improvement. Four new critical issues remain, none of which were visible in the pre-revision plan: a missing service class that owns the C3 uniqueness invariant, the drain job's lack of any tenant context (making all its repository calls silently empty-set), an uncaged `UnsupportedOperationException` in Office Ally, and an unresolved ambiguity about which entity is the canonical holder of `aggregatorVisitId`.

---

## 2. Critical Issues

### C8 — `AgencyIntegrationConfigService` referenced but absent from package layout

**Description:** Step 5 / the C3 note in the migration states that uniqueness for `(agency_id, integration_type, state_code, payer_type)` is enforced at the service layer by a "check-before-insert inside a transaction in `AgencyIntegrationConfigService`." That class appears nowhere in Step 4's package layout. Without it, there is no class that owns this invariant and no test target for the check.

**Why it matters:** The database provides no uniqueness guarantee (the functional index was removed). If the service class is never created, duplicate configs are silently allowed. Duplicate configs cause non-deterministic routing — two conflicting rows for the same `(agencyId, EVV_AGGREGATOR, NY, MEDICAID)` combination will produce whichever `findFirst()` returns.

**Fix:** Add to `config/`:
```
├── config/
│   ├── AgencyIntegrationConfigService.java  @Service — save(config): check-before-insert
│   │                                         findActive(agencyId, type, stateCode, payerType): unique lookup
```
The `save()` method must run inside `@Transactional` and execute a `SELECT ... FOR UPDATE` (or equivalent `existsByAgencyIdAndIntegrationTypeAndStateCodeAndPayerType`) before inserting, with an explicit exception if a duplicate is found. The `@Entity`-level Javadoc must document the invariant (as already noted in the migration comment).

---

### C9 — `EvvBatchDrainJob` runs without TenantContext — repository queries return empty or error

**Description:** `EvvBatchDrainJob` is a `@Scheduled` job. It runs on the scheduler thread pool, not on any HTTP request thread. `TenantFilterInterceptor.preHandle()` never fires. `TenantContext` is never set. If `EvvSubmissionRecordRepository` inherits the `@Filter agencyFilter` (as all agency-scoped entities do), every query from the drain job executes with an unbound filter — either Hibernate throws because the filter parameter `agencyId` is unset, or (if not using a named filter param binding) it returns all rows with no agency restriction.

**Why it matters:** If the filter parameter is unbound, Hibernate will throw a `HibernateException` on startup or at query time, killing the drain job entirely. If by coincidence the filter is not enabled (no `@Filter` on `EvvSubmissionRecord`), the drain job silently processes all agencies' records without tenancy — a cross-agency data access violation.

**Fix:** The drain job must explicitly manage TenantContext per agency. Two acceptable patterns:

**Option A (preferred — per-agency loop):**
```java
// Drain job does NOT use the tenant-filtered repository.
// It uses a system-level @Query with no agency filter binding.
@Query("SELECT r.agencyId FROM EvvSubmissionRecord r WHERE r.submissionMode = 'BATCH' AND r.status = 'PENDING' GROUP BY r.agencyId")
List<UUID> findAgenciesWithPendingBatch();

// Then per agency:
for (UUID agencyId : agenciesWithPending) {
    TenantContext.setAgencyId(agencyId);
    try {
        processBatchForAgency(agencyId);  // calls filtered repo normally
    } finally {
        TenantContext.clear();
    }
}
```

**Option B (simpler — system repository):**
Add a `@Repository` that explicitly disables the filter and queries by `submission_mode + status` directly via native SQL or `@Query`. Set a clear naming convention (`EvvSubmissionRecordSystemRepository`) to prevent accidental use outside admin/job contexts.

The plan must specify which option is used and add `findAgenciesWithPendingBatch()` (or equivalent) to the repository.

---

### C10 — `X12Serializer` / `OfficeAllyTransmissionStrategy` throws `UnsupportedOperationException` on any call

**Description:** The plan states `X12Serializer` "throws `UnsupportedOperationException` until fully implemented" and `OfficeAllyTransmissionStrategy` is a `@Component` that uses it. Unlike the stub strategies (CareBridge, AuthentiCare) which explicitly return `failure(...)` and log a warning, `OfficeAllyTransmissionStrategy` has no failure-return path — it throws an unchecked exception that will propagate up through `AbstractClaimTransmissionStrategy` and crash the billing call stack for any agency configured with Office Ally.

**Why it matters:** The billing validation chain (`EvvLinkageHandler`, `AuthorizationHandler`, `NpiFormatHandler`) will have already passed by the time `doSubmit()` calls `X12Serializer`. An unhandled `UnsupportedOperationException` bubbles to the caller as a 500, is not a `ClaimSubmissionReceipt`, and produces no audit trail. Depending on how `AbstractClaimTransmissionStrategy` handles it, it may also corrupt the audit log entry.

**Fix:** Treat `OfficeAllyTransmissionStrategy` as a stub, consistent with CareBridge/AuthentiCare:
```java
@Override
protected ClaimSubmissionReceipt doSubmit(Claim claim, AgencyBillingCredentials creds) {
    log.warn("OfficeAlly transmission not yet implemented — returning failure receipt");
    return ClaimSubmissionReceipt.failure("NOT_IMPLEMENTED", "Office Ally SFTP not wired");
}
```
`X12Serializer` can still throw internally — the strategy just must not let it escape. Add this stub behavior to the plan's description of `OfficeAllyTransmissionStrategy`.

---

### C11 — `aggregatorVisitId` canonical owner unresolved (Q4 from Review #1 still open)

**Description:** The plan's routing note says "real-time → `strategy.submit()` → persist `aggregatorVisitId` on `EvvRecord`." The `evv_submission_records` table also has an `aggregator_visit_id` column. Q4 from Review #1 asked explicitly: which entity is canonical? The plan still does not answer this. The `EvvSubmissionService` implementation code (which must save one or both) cannot be written without this decision.

**Why it matters:** If only `EvvRecord.aggregatorVisitId` is set, void/update flows (which should load `EvvSubmissionRecord` to determine the aggregator visit ID for the PUT/DELETE call) will have a null column. If only `EvvSubmissionRecord.aggregatorVisitId` is set, the `EvvRecord` entity is incomplete and the EVV compliance service (which reads `EvvRecord`) cannot surface the aggregator reference. Ambiguity here will cause a compile-time disagreement between the implementer and the spec.

**Fix:** Add an explicit note to Step 5:
> `EvvSubmissionRecord.aggregatorVisitId` is set when the record is first persisted (from `EvvSubmissionResult.aggregatorVisitId`). `EvvRecord.aggregatorVisitId` is also updated in the same transaction (Tx 2 for batch, the REQUIRES_NEW tx for real-time) so the compliance service can surface it without joining to `evv_submission_records`. Both are set; `EvvSubmissionRecord` is canonical for audit/void-update; `EvvRecord` carries a denormalized copy for compliance reads.

---

## 3. Previously Addressed Items

All seven critical issues from Review #1 are resolved in the updated plan:

- **C1** — `TenantContext.setAgencyId(event.agencyId())` added to `onShiftCompleted()` with `finally { TenantContext.clear() }`. `LocalScoringService` audit note included.
- **C2** — `@Transactional(propagation = REQUIRES_NEW)` shown on `onShiftCompleted()`.
- **C3** — COALESCE functional index removed; service-layer check-before-insert documented (though the service class itself is now C8).
- **C4** — `submission_mode VARCHAR(10) NOT NULL DEFAULT 'REAL_TIME'` added to `evv_submission_records`; drain job filter updated.
- **C5** — `IN_FLIGHT` added to `EvvSubmissionStatus`; two-phase drain job logic documented; startup watchdog described.
- **C6** — `credentialClass()` method added to `EvvSubmissionStrategy` interface; assembler uses `strategy.credentialClass()` before calling `decrypt()`.
- **C7** — `IntegrationConnectorProperties` `@ConfigurationProperties` class added to `config/`; full connector YAML block added to Step 8.
- **M1** — `BatchEntry.java` record added to package layout.

---

## 4. Minor Issues & Improvements

**M_new_1 — `com.jcraft:jsch:0.1.55` is unmaintained (2018)**
JSch 0.1.55 has known security issues (no active CVE patches since 2018). The community fork `com.github.mwiede:jsch:0.2.x` is the actively maintained drop-in replacement with the same API. Replace in `pom.xml`:
```xml
<groupId>com.github.mwiede</groupId>
<artifactId>jsch</artifactId>
<version>0.2.22</version>
```

**M_new_2 — Key source ambiguity: env var vs YAML property**
`CredentialEncryptionService` "reads `INTEGRATION_ENCRYPTION_KEY`" (env var) but Step 8 shows `integration.encryption-key` in YAML. These are different bindings. Clarify: use `@Value("${integration.encryption-key}")` throughout; in prod, set via `INTEGRATION_ENCRYPTION_KEY` as a Spring property override (Spring converts `INTEGRATION_ENCRYPTION_KEY` → `integration.encryption-key` via relaxed binding). Confirm `@PostConstruct` validates against this single source.

**M_new_3 — `EvvBatchDrainJobTest` missing from test table**
The drain job has the most complex stateful logic in the plan (IN_FLIGHT transition, per-record submit, Tx 2, watchdog reset). It has no test in Step 7. Add:

| Test | Covers |
|---|---|
| `EvvBatchDrainJobTest` | PENDING→IN_FLIGHT in Tx1; submit called once; SUBMITTED on success / REJECTED on failure in Tx2; watchdog resets stale IN_FLIGHT |

**M_new_4 — `@EnableAsync` still phrased as a "verify" suggestion**
Step 5 says "Add `@EnableAsync` to the same class (or `AsyncConfig`). Verify this is present." The plan should be unconditional: "`@EnableAsync` must be added to `SchedulingConfig` (confirmed absent as of V13 baseline)." Leaving it as a verification task risks it being skipped.

**M_new_5 — Drain job context_json deserialization not explicitly confirmed (M2 from Review #1 still implicit)**
Step 5 does not explicitly state whether the drain job deserializes `EvvSubmissionContext` from `context_json` or re-loads entities. Add one sentence: "The drain job reconstructs `EvvSubmissionContext` by deserializing `context_json` from the `EvvSubmissionRecord` — no entity reload is performed. This keeps the batch path self-contained and avoids 6-query N+1 per record."

---

## 5. Questions for Clarification

**Q1 (from Review #1, still unanswered):** Does `LocalScoringService.onShiftCompleted()` currently set `TenantContext`? If not, this is a pre-existing multi-tenant bug in the scoring module that the EVV fix should also patch. The plan notes "audit `LocalScoringService`" but does not commit to fixing it.

**Q2 (new):** For the startup watchdog that resets stale `IN_FLIGHT` rows: what is "stale"? The plan says "older than 10 minutes" but does not define the threshold as a configurable property. If the drain job itself takes more than 10 minutes (large batch), the watchdog will reset IN_FLIGHT rows that are actively being processed. Should the threshold be configurable via `integration.drain-job.stale-threshold` or at minimum a constant with a clear comment?

**Q3 (new):** `EvvSubmissionRecord` has `evv_record_id UUID NOT NULL UNIQUE`. If a real-time submission is rejected (dead-letter, status = REJECTED), can that EVV record ever be re-submitted as a batch entry? The UNIQUE constraint prevents a second `EvvSubmissionRecord` row. If manual retry is a requirement, the constraint must be relaxed (or re-submission must work by updating the existing record's `submission_mode + status`).

---

## 6. Final Recommendation

**Approve with changes.**

C8, C9, C10, and C11 must be resolved before implementation begins. C8 (missing service class) and C9 (drain job tenant isolation) are the highest priority — C8 is a correctness gap in the C3 fix, and C9 will cause the drain job to either crash or silently process no records. C10 is a reliability fix. C11 is a specification clarity fix needed before `EvvSubmissionService` can be implemented correctly. None of these require architectural changes — they are targeted additions to the existing plan.
