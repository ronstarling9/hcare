# Critical Implementation Review #1
**Plan:** `2026-04-09-integration-module.md`
**Reviewer:** Senior Staff Engineer
**Date:** 2026-04-09

---

## 1. Overall Assessment

The revised plan is well-structured and correctly scopes the integration layer as a bounded package inside the backend — a sound simplification. The domain gap analysis is thorough, the migration is H2-aware (no `gen_random_uuid()`), the credential context is designed to carry typed records rather than live JPA entities, and the decorator/factory pattern is solid. However, six critical correctness issues remain that would cause data leaks, double-submissions, or compile failures in a first cut. They are addressable with targeted additions to the plan before implementation begins.

---

## 2. Critical Issues

### C1 — `TenantContext` not propagated into the `@Async` thread (data leak / NPE)

**Description:** `TenantFilterInterceptor` stores the tenant's `agencyId` in a `ThreadLocal` (`TenantContext`) and clears it in `afterCompletion()`. The `@Async @TransactionalEventListener` on `EvvSubmissionService.onShiftCompleted()` runs on a different thread (the async executor), so `TenantContext` is empty. Any `AgencyEvvCredentials` load via its `@Filter agencyFilter` repository will execute **without the filter enabled**, which either returns rows from all agencies (cross-agency credential leak) or crashes because the filter parameter is never bound.

**Why it matters:** This is a multi-tenant security boundary violation. The existing `LocalScoringService` (which also uses `@TransactionalEventListener @Async`) presumably has the same exposure and should be audited alongside this fix.

**Fix:** At the top of `onShiftCompleted()`, explicitly set the tenant context before any repository call:
```java
@TransactionalEventListener
@Async
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void onShiftCompleted(ShiftCompletedEvent event) {
    TenantContext.setAgencyId(event.agencyId());
    try {
        // ... all repository calls now filter correctly
    } finally {
        TenantContext.clear();
    }
}
```
Also apply `TenantFilterAspect` logic (enable Hibernate filter on the new session) or extract a helper used by both `LocalScoringService` and `EvvSubmissionService`.

---

### C2 — `@Transactional` missing on `EvvSubmissionService.onShiftCompleted()`

**Description:** `@TransactionalEventListener` fires after the outer transaction commits and does not inherit it. Without `@Transactional(propagation = REQUIRES_NEW)` on the method itself, the entity loads run without a shared session and `evvRecordRepo.save(evvRecord)` (to persist `aggregatorVisitId`) will fail or silently not flush.

**Why it matters:** The aggregator visit ID (EVVMSID / Sandata ref) is never stored, making future void/update calls impossible — a compliance gap.

**Fix:** Annotate the method with `@Transactional(propagation = Propagation.REQUIRES_NEW)` as shown in C1. This gives the async listener its own transaction scope.

---

### C3 — `COALESCE` in unique index is not H2-compatible (migration will fail in dev/test)

**Description:** The migration creates:
```sql
CREATE UNIQUE INDEX uq_agency_integration
    ON agency_integration_configs (agency_id, integration_type,
       COALESCE(state_code, ''), COALESCE(payer_type, ''));
```
H2 (used in dev and test) does not support functional/expression indexes. This migration will fail on startup and break every test run.

**Why it matters:** The dev/test suite will not start. V13 will be permanently broken until fixed, blocking CI.

**Fix:** Replace with a nullable-tolerant approach that works on both H2 and PostgreSQL. Options:
- Use a partial unique index (Postgres-only) in a separate migration that is excluded from H2, plus application-layer uniqueness enforcement for H2.
- Simpler: use a computed surrogate column (`state_code_key CHAR(2) NOT NULL DEFAULT ''`) that is set by the application before insert; then index on real columns with no expressions.
- Recommended: Accept nullable columns in the unique constraint as PostgreSQL actually treats each NULL as distinct (not equal to other NULLs), so multiple `(agencyId, EVV_AGGREGATOR, NULL, NULL)` rows would be allowed. Enforce single-row uniqueness at the service layer (check-before-insert) and drop the functional index entirely. Document the invariant in a comment.

---

### C4 — `evv_submission_records` conflates batch queue entries and submission tracking

**Description:** The plan uses `evv_submission_records` for two distinct things:
1. Batch queue entries (SFTP-path records waiting to be submitted nightly — PENDING + `context_json`)
2. Real-time submission results (REST-path records tracking aggregator response + `aggregator_visit_id`)

A PENDING row is ambiguous: is it "queued for batch" or "real-time attempt failed, retry pending"? The `status` column cannot distinguish them. The drain job (`EvvBatchDrainJob`) will attempt to re-drain real-time dead-letter entries as SFTP batch records.

**Why it matters:** Cross-contamination of the two queues will cause real-time failed submissions to be re-submitted via SFTP (wrong aggregator protocol) and vice versa.

**Fix:** Add a `submission_mode VARCHAR(10) NOT NULL DEFAULT 'REAL_TIME'` column (`REAL_TIME | BATCH`) so the drain job filters `WHERE submission_mode = 'BATCH' AND status = 'PENDING'`. Alternatively, add a separate `evv_batch_queue` table and keep `evv_submission_records` strictly for post-submission tracking.

---

### C5 — Double-submission risk in `EvvBatchDrainJob` on retry/restart

**Description:** The drain job reads PENDING batch records, calls `strategy.submit()`, then updates status to SUBMITTED. If the service restarts or the transaction fails after `submit()` but before the status update persists, the same record will be re-submitted on the next run. For Netsmart/CareBridge SFTP, this creates a duplicate visit file that the aggregator will reject with a duplicate visit error (and potentially flag the agency for compliance review).

**Why it matters:** EVV over-reporting triggers state audit flags. Even if the aggregator deduplicates, the agency receives a rejection notice.

**Fix:** Use a two-phase status transition:
1. `UPDATE ... SET status = 'IN_FLIGHT' WHERE status = 'PENDING' AND submission_mode = 'BATCH'` (in one transaction, before calling submit)
2. Call `strategy.submit()`
3. Update to SUBMITTED or REJECTED in a second transaction

Add `IN_FLIGHT` to `EvvSubmissionStatus` enum. Add a watchdog (or startup job) that resets stale `IN_FLIGHT` rows older than N minutes back to PENDING.

---

### C6 — Credential class resolution in `EvvSubmissionContextAssembler` is unspecified

**Description:** The assembler calls `encryptionService.decrypt(entity.getCredentialsEncrypted(), credClass)` but the plan never specifies how `credClass` (e.g. `SandataCredentials.class` vs `HhaxCredentials.class`) is resolved. The assembler knows the `AggregatorType` (from routing), but there is no mapping from `AggregatorType → credential class` anywhere in the plan.

**Why it matters:** Without this mapping, the assembler cannot compile. It is a core requirement of the decrypt call.

**Fix:** Add a `credentialClass()` method to `EvvSubmissionStrategy`:
```java
public interface EvvSubmissionStrategy {
    AggregatorType aggregatorType();
    Class<?> credentialClass();  // returns SandataCredentials.class, HhaxCredentials.class, etc.
    ...
}
```
The factory can expose `strategyFor(type).credentialClass()`, or the assembler can hold a `Map<AggregatorType, Class<?>>` populated from the strategy beans. Either approach; pick one and add it to the plan.

---

### C7 — `IntegrationRestClientConfig` base URLs not specified

**Description:** The plan creates named `RestClient` beans (sandataRestClient, hhaxRestClient, stediRestClient, viventiumRestClient) but does not specify where their base URLs and timeouts come from. The `@Configuration` class must read `@Value` properties or `@ConfigurationProperties`, which requires corresponding entries in `application.yml` / `application-dev.yml`.

**Why it matters:** The configuration class will not compile in a meaningful way without property bindings. Integration tests will fail silently with hardcoded or null URLs.

**Fix:** Add an `application.yml` section to the plan:
```yaml
integration:
  encryption-key: ...
  connectors:
    sandata:
      base-url: https://evv.sandata.com
      connect-timeout: 5s
      read-timeout: 30s
    hhaexchange:
      base-url: https://api.hhaexchange.com
    stedi:
      base-url: https://healthcare.us.stedi.com/2024-04-01
    viventium:
      base-url: https://api.viventium.com
```
And create `IntegrationConnectorProperties` as a `@ConfigurationProperties` record.

---

## 3. Previously Addressed Items

*(No prior review history — this is Review #1.)*

---

## 4. Minor Issues & Improvements

**M1 — `BatchEntry` record missing from package layout**
`EvvBatchQueue.drainAll()` is documented to return `List<BatchEntry record>` but `BatchEntry.java` does not appear in the package tree. Add it (`record BatchEntry(EvvSubmissionContext ctx, AggregatorType aggregatorType) {}`).

**M2 — N+1 queries in `EvvBatchDrainJob`**
The drain job processes each queued entry by deserializing `EvvSubmissionContext` from `context_json` — that's fine since context is self-contained. But if the drain job also reloads entities to re-assemble context (instead of deserializing from JSON), it will issue 6 queries per record. The plan should clarify: batch drain reconstructs context from `context_json` (no entity reload), not from fresh DB loads.

**M3 — `@EnableAsync` + virtual thread executor**
Spring Boot 3.2+ auto-configures a virtual thread `TaskExecutor` when `spring.threads.virtual.enabled=true`. Adding `@EnableAsync` to `SchedulingConfig` will use this executor automatically. The plan should confirm this expectation rather than leaving it implicit, to avoid someone adding an explicit `ThreadPoolTaskExecutor` that defeats virtual thread benefits.

**M4 — `NpiValidator` Luhn algorithm complexity**
The plan says "10-digit Luhn with '80840' prefix" without elaborating. The correct algorithm prepends `80840` to the 9-digit NPI base (not including the check digit), runs standard Luhn mod-10, and compares to the 10th digit. This is nontrivial to get right. The plan should reference the CMS NPI Luhn specification or note that `org.apache.commons.validator.routines.checkdigit.LuhnCheckDigit` can be used (commons-validator is already transitively available via Spring).

**M5 — `EvvSubmissionContextAssembler` loads `Client` but plan's EVV context record doesn't show `clientId`**
The assembler loads `Client` for the service address fields, but `EvvSubmissionContext` in the design doc has individual address fields (`serviceAddressLine1`, `serviceCity`, `serviceState`, `serviceZip`) not a `Client` reference. Confirm the assembler maps `Client.address` (which appears to be a freeform text field, not structured) to these structured fields. If `Client.address` is unstructured, either add structured address columns to `Client` or accept that GPS coordinates will be used instead of address for EVV.

**M6 — CareBridge/AuthentiCare stub behavior**
The plan says stubs "return failure result." But `EvvSubmissionService` reacts to `result.success() == false` by... doing what? If the call path reaches a stub aggregator, the submission record ends up as REJECTED immediately, which could trigger the dead-letter queue and generate noise. Consider returning a `NOT_CONFIGURED` error code and skipping dead-letter enqueue for stub results.

---

## 5. Questions for Clarification

**Q1:** Does the existing `LocalScoringService` set `TenantContext` in its `@Async @TransactionalEventListener` handler? If not, the scoring module has the same tenant filter exposure. This plan's fix should be extracted to a shared utility to cover both.

**Q2:** Should `EvvBatchDrainJob` process all agencies' queued records in a single job run (global drain), or per-agency with a separate `TenantContext` set for each? The former is simpler but requires bypassing the tenant filter; the latter requires iterating over agencies.

**Q3:** For the `Client.address` field — is it structured (line1, city, state, zip) or a freeform string? If freeform, the EVV context assembler cannot populate `serviceAddressLine1/City/State/Zip` reliably.

**Q4:** `EvvSubmissionService` saves `aggregatorVisitId` back onto `EvvRecord` after real-time submission. Should this field also be set on `EvvSubmissionRecord`? The plan shows it on `EvvSubmissionRecord` (`aggregator_visit_id` column) but the service code only updates `EvvRecord`. Clarify which entity carries the canonical aggregator visit ID — likely both, but the plan should be explicit.

---

## 6. Final Recommendation

**Major revisions needed** before implementation begins.

C1 (tenant context propagation) and C3 (H2-incompatible functional index) are build-breaking. C2 (missing `@Transactional`) and C4 (batch queue/submission tracking conflation) are runtime data correctness issues that will not surface in unit tests. C5 (double-submission) is a compliance risk. C6 and C7 will prevent the configuration class from compiling correctly.

Address C1–C7 in the plan, then the implementation is ready to proceed. The overall pattern design is sound and the domain gap analysis is complete.
