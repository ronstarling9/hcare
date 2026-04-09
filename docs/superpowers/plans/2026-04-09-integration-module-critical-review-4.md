# Critical Implementation Review #4
**Plan:** `2026-04-09-integration-module.md`
**Reviewer:** Senior Staff Engineer
**Date:** 2026-04-09
**Previous reviews:** critical-review-1.md (C1–C7, M1–M6), critical-review-2.md (C8–C11, M_new_1–M_new_5), critical-review-3.md (C12–C14, M_new_6–M_new_7)

---

## 1. Overall Assessment

Reviews #1–#3 drove the plan from a first-cut draft to a substantially complete design. All fourteen prior critical issues are now reflected in the updated plan. One new critical issue emerges on close reading of the C12 fix: `@Lock(PESSIMISTIC_WRITE)` is applied to a JPQL aggregate (COUNT) query, which is invalid in both JPA and PostgreSQL — the database rejects `SELECT COUNT(...) ... FOR UPDATE` with an error, so the TOCTOU protection C12 was meant to provide is silently absent. Two minor issues complete the findings.

---

## 2. Critical Issues

### C15 — `@Lock(PESSIMISTIC_WRITE)` applied to a COUNT aggregate query — lock is never issued

**Description:** The C12 fix in Step 5 shows:
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM AgencyIntegrationConfig c " +
       "WHERE c.agencyId = :agencyId AND c.integrationType = :type " +
       "AND c.stateCode IS NOT DISTINCT FROM :stateCode " +
       "AND c.payerType IS NOT DISTINCT FROM :payerType")
boolean existsForUpdate(...);
```

`@Lock(PESSIMISTIC_WRITE)` instructs Hibernate to append `FOR UPDATE` to the generated SQL. But `FOR UPDATE` is a row-level lock applied to entity rows returned by the query. A query that returns a scalar aggregate (`CASE WHEN COUNT(c) > 0 THEN true ELSE false END`) returns no rows — there is nothing to lock. PostgreSQL will explicitly reject this with:

```
ERROR: FOR UPDATE is not allowed with aggregate functions
```

In practice, Hibernate 6 may either (a) throw a `PersistenceException` at query execution time, or (b) silently ignore the lock hint on aggregate queries. Either outcome means the pessimistic lock is never acquired. Two concurrent admin requests can still both observe `false` and both insert — the TOCTOU race condition C12 was designed to prevent remains present.

**Why it matters:** This is the only concurrency protection for the `agency_integration_configs` uniqueness invariant. The database has no unique index (removed in C3). If the lock is never issued, duplicate config rows can appear under concurrent writes, causing non-deterministic routing — the exact failure mode C12 described.

**Fix:** Change the existence check to return an entity instance so Hibernate emits `SELECT ... FROM agency_integration_configs WHERE ... FOR UPDATE` — a row-level lock on a result set:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("FROM AgencyIntegrationConfig c WHERE c.agencyId = :agencyId AND c.integrationType = :type " +
       "AND c.stateCode IS NOT DISTINCT FROM :stateCode AND c.payerType IS NOT DISTINCT FROM :payerType")
Optional<AgencyIntegrationConfig> findForUpdate(
    @Param("agencyId") UUID agencyId,
    @Param("type") String type,
    @Param("stateCode") String stateCode,
    @Param("payerType") String payerType);
```

In `AgencyIntegrationConfigService.save()`:
```java
if (repo.findForUpdate(config.getAgencyId(), config.getIntegrationType(),
                       config.getStateCode(), config.getPayerType()).isPresent()) {
    throw new DuplicateIntegrationConfigException(...);
}
repo.save(config);
```

When no matching row exists, `findForUpdate()` returns `Optional.empty()` and issues no `FOR UPDATE` (there is no row to lock). This means a true gap — no existing row to lock — does not prevent the race. To handle the no-existing-row case, complement with a database-level advisory lock or rely on the unique constraint approach. However, for the admin-only low-frequency use case, an **alternative that eliminates the TOCTOU window entirely** is to restore a plain `UNIQUE` constraint on non-nullable columns and add surrogate sentinel values for the nullable fields (e.g., `state_code_key CHAR(2) NOT NULL DEFAULT ''`). The plan can choose either:

**Option A (recommended for admin-only path):** Use `findForUpdate()` returning `Optional<AgencyIntegrationConfig>` as above. This serializes updates-to-existing-rows but doesn't lock the gap. To close the gap for new rows, combine with a unique constraint on non-nullable surrogate columns (state_code_key/payer_type_key defaulting to `''`). This avoids the COALESCE functional index (not H2-compatible) while providing DB-enforced uniqueness that catches the new-row race.

**Option B (simpler, no DB constraint needed):** Use a Spring `@Synchronized` or method-level Java lock (`ReentrantLock` per `agencyId`) to serialize concurrent writes in the service layer. Given the admin-only, low-frequency nature of config creation, application-level mutual exclusion is acceptable. The DB still relies on service-layer enforcement, but the lock is at the JVM level — not per-row in the DB.

The plan must choose one of these options and update the repository method description in Step 4 accordingly.

---

## 3. Previously Addressed Items

All fourteen critical issues from Reviews #1–#3 are now reflected in the plan:

- **C1** — `TenantContext.setAgencyId()` + `finally { TenantContext.clear() }` in `onShiftCompleted()`
- **C2** — `@Transactional(propagation = REQUIRES_NEW)` on `onShiftCompleted()`
- **C3** — COALESCE functional index removed; service-layer check-before-insert documented
- **C4** — `submission_mode VARCHAR(10) NOT NULL DEFAULT 'REAL_TIME'` added; drain job filters on it
- **C5** — `IN_FLIGHT` status added; two-phase per-record drain; startup watchdog
- **C6** — `credentialClass()` on `EvvSubmissionStrategy`; assembler uses it to decrypt
- **C7** — `IntegrationConnectorProperties` `@ConfigurationProperties`; full YAML block in Step 8
- **C8** — `AgencyIntegrationConfigService` added to `config/` with `save()` + `findActive()`
- **C9** — `EvvSubmissionRecordSystemRepository`; per-agency TenantContext loop in drain job
- **C10** — `OfficeAllyTransmissionStrategy` returns `failure(...)` stub; does not throw
- **C11** — Both entities updated; `EvvSubmissionRecord.aggregatorVisitId` is canonical; `EvvRecord` carries denormalized copy
- **C12** — PESSIMISTIC_WRITE existence check documented (but blocked by C15 — aggregate lock is invalid)
- **C13** — Drain job processes one record at a time via `findFirst...` loop; not bulk IN_FLIGHT
- **C14** — `EvvSubmissionRecord` has no `@Filter agencyFilter`; class Javadoc documents invariant
- **M1** — `BatchEntry.java` added to package layout
- **M_new_3** — `EvvBatchDrainJobTest` added to test table
- **M_new_4** — `@EnableAsync` stated as unconditional requirement
- **M_new_5** — Drain loop comment explicitly states context from JSON (no entity reload)
- **M_new_6** — ±25% jitter in `RetryingEvvSubmissionStrategy`
- **M_new_7** — `DatabaseEvvBatchQueue.enqueue()` catches `DataIntegrityViolationException` as no-op with WARN

---

## 4. Minor Issues & Improvements

**M_new_8 — No test for `AgencyIntegrationConfigService` duplicate detection**
Step 7 has no test for the service that owns the uniqueness invariant. The C15 fix requires a verifiable test. Add:

| Test | Covers |
|---|---|
| `AgencyIntegrationConfigServiceTest` | Duplicate `(agencyId, type, stateCode, payerType)` throws; NULL stateCode/payerType handled; non-duplicate insert succeeds |

This test is also the only way to verify the `IS NOT DISTINCT FROM` NULL-equality logic in H2, since H2 `=` treats `NULL = NULL` as `false`.

**M_new_1 (carried from Review #2 — still unaddressed)**
`pom.xml` still shows `com.jcraft:jsch:0.1.55` (2018, unmaintained). Replace with `com.github.mwiede:jsch:0.2.22` (community fork, same API, active CVE patches). This is low urgency since `JschSftpGateway` is used only by `NetsmarTellusSubmissionStrategy`, but the dependency should not ship into production on a version with known vulnerabilities.

**M_new_9 — `markInFlight()` does not check affected row count — unsafe in multi-node deployments**
If the application runs on multiple nodes (e.g., two pods in Kubernetes), both nodes' drain jobs could call `findFirstByAgencyIdAndSubmissionModeAndStatusOrderById(...)` in the same window and return the same PENDING record. Both would then call `markInFlight(next.getId())`, which does `UPDATE ... WHERE id = :id AND status = 'PENDING'`. Only one UPDATE succeeds (1 row affected); the other updates 0 rows. If `markInFlight()` doesn't check the affected row count and return without calling `submit()` on 0 rows updated, both nodes will proceed to call `strategy.submit()` with the same record — a double-submission.

Fix: `markInFlight()` should check the JDBC-level affected row count (via `@Modifying(clearAutomatically = true)` returning `int`). If 0 rows were updated, skip `strategy.submit()` for that record and continue to the next `findFirst` iteration:
```java
int updated = systemRepo.markInFlight(next.getId());
if (updated == 0) continue;  // another node claimed this record
```
This is noted as a minor issue since the plan does not mention multi-node deployment, and single-node behavior is correct. However, adding the check costs nothing and makes the plan safe for future scale-out.

---

## 5. Questions for Clarification

**Q1 (still open from Review #1):** Has `LocalScoringService.onShiftCompleted()` been audited for the C1/C2 tenant exposure? The plan says "must be audited" but does not commit to a fix within this plan's scope.

**Q3 (still open from Review #2):** Can a REJECTED (dead-lettered) `evv_record_id` ever be re-submitted? The `UNIQUE` constraint on `evv_submission_records.evv_record_id` prevents a second row. If admin retry is needed, clarify whether it works by updating the existing record's `status` back to `PENDING`, or whether retry is out of scope.

---

## 6. Final Recommendation

**Approve with changes.**

C15 (pessimistic lock on aggregate query) is the only remaining blocker. It directly negates the C12 fix and leaves the uniqueness invariant unprotected under concurrent writes. The fix is targeted: change the repository method to return `Optional<AgencyIntegrationConfig>` instead of a boolean aggregate. M_new_8 (add `AgencyIntegrationConfigServiceTest`) should accompany the C15 fix to make the NULL-equality and locking logic verifiable. M_new_1 (JSch version) and M_new_9 (affected row count check) are low-priority but worth addressing before the first production deployment. After C15, the plan is ready for implementation.
