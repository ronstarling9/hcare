# Critical Implementation Review #5
**Plan:** `2026-04-09-integration-module.md`
**Reviewer:** Senior Staff Engineer
**Date:** 2026-04-09
**Previous reviews:** critical-review-1.md (C1–C7, M1–M6), critical-review-2.md (C8–C11, M_new_1–M_new_5), critical-review-3.md (C12–C14, M_new_6–M_new_7), critical-review-4.md (C15, M_new_8–M_new_9)

---

## 1. Overall Assessment

Reviews #1–#4 have driven the plan from a rough first draft to a near-production-ready design. All fifteen prior critical issues and all minor items through M_new_9 are now correctly reflected. One new critical issue emerges on reading the batch queue serialization flow: `EvvSubmissionContext` includes the typed decrypted credential record, and `DatabaseEvvBatchQueue.enqueue()` serializes the full context to `context_json` — this stores plaintext credentials in the `evv_submission_records` table and directly contradicts the AES-256-GCM encryption the plan specifies elsewhere. Two minor issues complete the findings.

---

## 2. Critical Issues

### C16 — `context_json` serializes plaintext credentials into `evv_submission_records`

**Description:** The plan specifies two things simultaneously that are mutually contradictory:

1. `EvvSubmissionContext` is a record that includes a "typed credential record (not entity)" — e.g., `SandataCredentials(username, password, payerId)` or `HhaxCredentials(appName, appSecret, appKey)`.

2. `DatabaseEvvBatchQueue.enqueue()` "serializes `EvvSubmissionContext` to `context_json`" and stores it in `evv_submission_records.context_json`.

If the full `EvvSubmissionContext` is serialized, the typed credential record is serialized with it. The result is that plaintext API credentials — usernames, passwords, API secrets — are written to the `evv_submission_records` table as cleartext JSON. Any database dump, backup, read grant on `evv_submission_records`, or SQL injection into that table exposes all agency credentials. This directly negates the AES-256-GCM encryption in `CredentialEncryptionService` and `agency_evv_credentials.credentials_encrypted`.

**Why it matters:** This is a credential leakage vulnerability. `evv_submission_records` is a business data table that will appear in backups, is readable by the application user, and may be logged during debugging. Storing plaintext aggregator credentials here violates SOC 2 (encryption at rest for credentials), potentially HIPAA (if the same DB access path applies), and is directly exploitable if the database is breached.

**Fix:** Strip credentials from `EvvSubmissionContext` before serialization. The drain job already has everything it needs to re-decrypt at submit time without storing the plaintext:

- `evv_submission_records.agency_id` and `evv_submission_records.aggregator_type` are already columns on the table.
- At drain time, the drain job can call `agencyEvvCredentialsRepository.findByAgencyIdAndAggregatorType(agencyId, aggregatorType)` (using the system-level identity — TenantContext is set per-agency in the drain loop) and decrypt fresh credentials via `CredentialEncryptionService`.

**Concrete change to the plan:**

1. Split `EvvSubmissionContext` into two parts:
   - `EvvSubmissionContext` — carries the 6 federal elements + routing metadata (no credentials).
   - Credential resolution happens at submit time for both real-time and batch paths.

2. `DatabaseEvvBatchQueue.enqueue(ctx, type)` serializes only the credential-free `EvvSubmissionContext` to `context_json`.

3. In the drain loop, before calling `strategy.submit()`, resolve credentials:
   ```java
   AgencyEvvCredentials creds = credRepo.findByAgencyIdAndAggregatorType(agencyId, aggregatorType)
       .orElseThrow(() -> new MissingCredentialsException(...));
   Object typedCreds = encryptionService.decrypt(creds.getCredentialsEncrypted(), strategy.credentialClass());
   EvvSubmissionContext ctxWithCreds = ctx.withCredentials(typedCreds);
   EvvSubmissionResult result = strategy.submit(ctxWithCreds);
   ```

4. Update the `EvvSubmissionContext` record definition to document: "Never includes decrypted credential values — credentials are resolved at submit time by the caller."

5. Update `EvvSubmissionContextAssembler` similarly — it builds a credential-free context; the real-time path resolves credentials immediately before calling `strategy.submit()` (not stored in the context).

---

## 3. Previously Addressed Items

All fifteen critical issues from Reviews #1–#4 are now reflected in the plan:

- **C1** — TenantContext re-bound in `onShiftCompleted()` before any repo call
- **C2** — `@Transactional(REQUIRES_NEW)` on `onShiftCompleted()`
- **C3** — COALESCE index removed; service-layer check-before-insert
- **C4** — `submission_mode` column distinguishes BATCH from REAL_TIME
- **C5** — `IN_FLIGHT` two-phase drain; startup watchdog
- **C6** — `credentialClass()` on `EvvSubmissionStrategy`
- **C7** — `IntegrationConnectorProperties` + full YAML block
- **C8** — `AgencyIntegrationConfigService` added
- **C9** — `EvvSubmissionRecordSystemRepository`; per-agency TenantContext in drain loop
- **C10** — `OfficeAllyTransmissionStrategy` stub returns failure, does not throw
- **C11** — Both entities updated; `EvvSubmissionRecord` canonical; `EvvRecord` denormalized copy
- **C12** — `AgencyIntegrationConfigService.save()` uses `@Lock(PESSIMISTIC_WRITE)`
- **C13** — Per-record (not bulk) `IN_FLIGHT` drain; crash recovery resets exactly one row
- **C14** — `EvvSubmissionRecord` has no `@Filter agencyFilter`; Javadoc documents invariant
- **C15** — `findForUpdate()` returns `Optional<AgencyIntegrationConfig>` (entity, not COUNT aggregate); lock now valid
- **M_new_1** — JSch replaced with `com.github.mwiede:jsch:0.2.22`
- **M_new_3** — `EvvBatchDrainJobTest` in test table
- **M_new_4** — `@EnableAsync` unconditional
- **M_new_5** — Drain loop comment states context from JSON (no entity reload)
- **M_new_6** — ±25% jitter in `RetryingEvvSubmissionStrategy`
- **M_new_7** — `DatabaseEvvBatchQueue.enqueue()` idempotent; catches `DataIntegrityViolationException`
- **M_new_8** — `AgencyIntegrationConfigServiceTest` added to test table
- **M_new_9** — `markInFlight()` returns `int`; drain loop skips submit if 0 rows claimed

---

## 4. Minor Issues & Improvements

**M_new_10 — `EvvSubmissionServiceIT` does not specify credential seeding**
The integration test verifies "credential lookup → strategy selected → audit log written," but `AgencyEvvCredentials` rows do not exist in the Testcontainers database by default. The test will throw `MissingCredentialsException` at the credential lookup step, before any strategy is invoked or any audit log is written. Add one sentence to the Step 7 test description: "Test setup seeds one `AgencyEvvCredentials` row with a known `aggregatorType` and encrypted credentials using a fixed test key."

**M_new_11 — `context_json` is nullable in the schema; drain job does not specify a null-guard**
`evv_submission_records.context_json TEXT` is nullable. The drain job only processes `submission_mode = 'BATCH'` rows, which are always written with a non-null `context_json` by `DatabaseEvvBatchQueue`. However, if a BATCH row's `context_json` is somehow null (partial insert, direct DB manipulation, migration), `deserializeContext(next.getContextJson())` will throw a `NullPointerException` that is not caught. Add to the drain loop description: "If `context_json` is null for a BATCH record, log ERROR and call `markFinal(next.getId(), EvvSubmissionResult.failure(...))` — treat as unrecoverable rejection rather than leaving the record in `IN_FLIGHT`."

---

## 5. Questions for Clarification

**Q1 (still open from Review #1):** Does `LocalScoringService.onShiftCompleted()` set `TenantContext`? The plan says "must be audited" but does not commit to a fix.

**Q3 (still open from Review #2):** Can a REJECTED `evv_record_id` ever be re-submitted? The `UNIQUE` constraint on `evv_submission_records.evv_record_id` prevents a second row. Clarify whether retry works by updating the existing record's `status` back to `PENDING`, or if retry is out of scope.

---

## 6. Final Recommendation

**Approve with changes.**

C16 (plaintext credentials in `context_json`) is a security correctness issue that must be resolved before implementation begins — storing decrypted API secrets in a business data table defeats the encryption layer and creates a credential-leakage surface. The fix is straightforward: remove credentials from `EvvSubmissionContext`, and have both the real-time path and the drain job resolve credentials at submit time. After C16, the plan is ready for implementation. M_new_10 and M_new_11 are low-risk and can be addressed during implementation.
