# Plan: Integration Package (inside backend)

## Context

hcare must submit visit data to EVV aggregators (21st Century Cures Act mandate), generate X12 claims for clearinghouses, and export payroll — with different vendors per agency. The design in `docs/integration-design-patterns.md` specifies the GoF pattern stack. This plan implements that design as a bounded `com.hcare.integration` package inside the existing backend, mirroring how `com.hcare.scoring` is already isolated. No Maven restructuring.

---

## What Changes

| Area | Change |
|---|---|
| `backend/pom.xml` | Add `io.xlate:staedi` (X12) and `com.jcraft:jsch` (SFTP) |
| `backend/src/main/resources/db/migration/` | New V13 migration (4 tables + 1 column) |
| `backend/src/main/java/com/hcare/` | New `integration/` package tree + new files in `evv/` |
| `domain/EvvRecord.java` | Add `aggregatorVisitId` field |
| Existing enums | Stay where they are — no moves, no import churn |

---

## Domain Gaps to Fix First (prerequisite migrations + entity changes)

The billing and EVV assembler code in the design doc references fields that don't exist on current entities. These must be added alongside V13.

| Entity | Missing Field | Needed By |
|---|---|---|
| `EvvRecord` | `aggregatorVisitId VARCHAR(100)` | `EvvSubmissionService` — stores aggregator's visit ID for void/update |
| `Caregiver` | `npi VARCHAR(10)` | `EvvSubmissionContext.caregiverNpi` (federal element 5) |
| `Agency` | `npi VARCHAR(10)`, `tax_id VARCHAR(9)` | `X12ClaimBuilder.billingProvider()` |
| `Shift` | `hcpcs_code VARCHAR(10)`, `billed_amount NUMERIC(10,2)`, `units INT` | `X12ClaimBuilder` service line |
| `CarePlan` | n/a — ICD-10 lives on `ClientDiagnosis`, not `CarePlan` | Billing assembler loads from `ClientDiagnosis` directly |

`Authorization.getAuthNumber()` already exists — the design doc's `getPriorAuthNumber()` is a naming mistake; use `getAuthNumber()`.

All field additions go into **V13** (one migration, one PR).

---

## Step 1 — pom.xml Additions

```xml
<!-- X12 EDI serialization (Office Ally SFTP path) -->
<dependency>
  <groupId>io.xlate</groupId>
  <artifactId>staedi</artifactId>
  <version>1.29.0</version>
</dependency>
<!-- SFTP (Netsmart/Tellus) — use community fork; com.jcraft:jsch is unmaintained since 2018 -->
<dependency>
  <groupId>com.github.mwiede</groupId>
  <artifactId>jsch</artifactId>
  <version>0.2.22</version>
</dependency>
```

---

## Step 2 — Flyway Migration V13

File: `backend/src/main/resources/db/migration/V13__integration_schema.sql`

```sql
-- Domain field additions
ALTER TABLE evv_records     ADD COLUMN aggregator_visit_id VARCHAR(100);
ALTER TABLE caregivers      ADD COLUMN npi VARCHAR(10);
ALTER TABLE agencies        ADD COLUMN npi VARCHAR(10);
ALTER TABLE agencies        ADD COLUMN tax_id VARCHAR(9);
ALTER TABLE shifts          ADD COLUMN hcpcs_code VARCHAR(10);
ALTER TABLE shifts          ADD COLUMN billed_amount NUMERIC(10,2);
ALTER TABLE shifts          ADD COLUMN units INT;

-- Integration tables
CREATE TABLE agency_evv_credentials (
    id                   UUID         PRIMARY KEY,
    agency_id            UUID         NOT NULL,
    aggregator_type      VARCHAR(30)  NOT NULL,
    credentials_encrypted TEXT        NOT NULL,
    endpoint_override    VARCHAR(500),
    active               BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_agency_aggregator UNIQUE (agency_id, aggregator_type)
);

CREATE TABLE evv_submission_records (
    id                   UUID         PRIMARY KEY,
    evv_record_id        UUID         NOT NULL UNIQUE,
    agency_id            UUID         NOT NULL,
    aggregator_type      VARCHAR(30)  NOT NULL,
    aggregator_visit_id  VARCHAR(100),
    -- C4: distinguishes real-time tracking rows from batch queue entries so EvvBatchDrainJob
    -- only drains BATCH rows and never re-submits real-time dead-letter entries via SFTP.
    submission_mode      VARCHAR(10)  NOT NULL DEFAULT 'REAL_TIME',  -- REAL_TIME | BATCH
    status               VARCHAR(20)  NOT NULL DEFAULT 'PENDING',    -- see EvvSubmissionStatus
    context_json         TEXT,
    retry_count          INT          NOT NULL DEFAULT 0,
    last_error           TEXT,
    submitted_at         TIMESTAMP
);

CREATE TABLE agency_integration_configs (
    id                   UUID         PRIMARY KEY,
    agency_id            UUID         NOT NULL,
    integration_type     VARCHAR(30)  NOT NULL,
    connector_class      VARCHAR(100) NOT NULL,
    state_code           CHAR(2),
    payer_type           VARCHAR(20),
    endpoint_url         VARCHAR(500),
    credentials_encrypted TEXT        NOT NULL,
    config_json          TEXT,
    active               BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP    NOT NULL DEFAULT NOW()
);
-- C3: COALESCE in index expressions is not H2-compatible; use no functional index here.
-- Uniqueness for (agency_id, integration_type, state_code, payer_type) is enforced at the
-- service layer (check-before-insert inside a transaction) in AgencyIntegrationConfigService.
-- PostgreSQL treats each NULL as distinct, so multiple (agencyId, EVV_AGGREGATOR, NULL, NULL)
-- rows would be allowed by the DB alone — the service check prevents this invariant violation.
-- A comment on AgencyIntegrationConfig must document this invariant.

CREATE TABLE integration_audit_log (
    id           UUID        PRIMARY KEY,
    agency_id    UUID        NOT NULL,
    entity_id    UUID        NOT NULL,
    connector    VARCHAR(100) NOT NULL,
    operation    VARCHAR(20) NOT NULL,
    success      BOOLEAN     NOT NULL,
    duration_ms  BIGINT      NOT NULL,
    error_code   VARCHAR(50),
    recorded_at  TIMESTAMP   NOT NULL DEFAULT NOW()
);

-- C5: drain job filters on submission_mode + status; include both in index
CREATE INDEX idx_evv_submission_agency  ON evv_submission_records(agency_id, submission_mode, status);
CREATE INDEX idx_integration_audit      ON integration_audit_log(agency_id, recorded_at DESC);
```

**Note:** No `DEFAULT gen_random_uuid()` — UUIDs generated by JPA (`GenerationType.UUID`) to stay H2-compatible.

---

## Step 3 — Entity Updates (backend domain)

**`EvvRecord`** — add field + getter/setter:
```java
@Column(name = "aggregator_visit_id", length = 100)
private String aggregatorVisitId;
```

**`Caregiver`** — add:
```java
@Column(name = "npi", length = 10)
private String npi;
```

**`Agency`** — add:
```java
@Column(name = "npi", length = 10)
private String npi;

@Column(name = "tax_id", length = 9)
private String taxId;
```

**`Shift`** — add:
```java
@Column(name = "hcpcs_code", length = 10)
private String hcpcsCode;

@Column(name = "billed_amount", precision = 10, scale = 2)
private BigDecimal billedAmount;

@Column(name = "units")
private Integer units;
```

---

## Step 4 — Integration Package Layout

```
backend/src/main/java/com/hcare/integration/
├── CredentialEncryptionService.java       AES-256-GCM; key from INTEGRATION_ENCRYPTION_KEY env; @PostConstruct fail-fast if absent
├── SftpGateway.java                       interface — upload(host, user, privateKeyRef, remotePath, byte[])
├── JschSftpGateway.java                   @Component — JSch impl
├── TimezoneResolver.java                  static stateCode → ZoneId map (all 50 states)
├── AuthorizationChecker.java              interface — wouldExceedAuthorizedHours(UUID authorizationId, BigDecimal units)
├── config/
│   ├── AgencyIntegrationConfig.java       @Entity; uniqueness enforced at service layer (see C3 note); @Filter agencyFilter
│   ├── AgencyIntegrationConfigRepository.java
│   ├── AgencyIntegrationConfigService.java @Service — owns the C3 uniqueness invariant
│   │                                       save(config): @Transactional — calls repo.findForUpdate() (C12/C15:
│   │                                         @Lock PESSIMISTIC_WRITE on entity-returning query — NOT a COUNT
│   │                                         aggregate; PostgreSQL rejects FOR UPDATE after aggregate functions)
│   │                                         before insert; throws DuplicateIntegrationConfigException on conflict
│   │                                       findActive(agencyId, type, stateCode, payerType): single-config lookup
│   ├── IntegrationConnectorProperties.java @ConfigurationProperties("integration.connectors") — record with nested
│   │                                       per-connector records (baseUrl, connectTimeout, readTimeout) (C7)
│   └── IntegrationRestClientConfig.java   @Configuration — reads IntegrationConnectorProperties; creates named
│                                          RestClient beans (sandataRestClient, hhaxRestClient, stediRestClient,
│                                          viventiumRestClient) with configured base URL + timeouts
├── audit/
│   ├── IntegrationAuditLog.java           @Entity @Immutable — no PHI
│   ├── IntegrationAuditLogRepository.java
│   └── IntegrationAuditWriter.java        @Component
├── evv/
│   ├── EvvSubmissionStrategy.java         interface:
│   │                                        aggregatorType() → AggregatorType
│   │                                        isRealTime() → boolean
│   │                                        credentialClass() → Class<?> (C6: used by caller to decrypt blob)
│   │                                        submit(EvvSubmissionContext ctx, Object typedCreds) → EvvSubmissionResult
│   │                                        update(EvvSubmissionContext ctx, Object typedCreds) → EvvSubmissionResult
│   │                                        void_(EvvSubmissionContext ctx, Object typedCreds) → EvvSubmissionResult
│   │                                      (M_new_12: two-arg form — credentials never embedded in context; C16)
│   ├── AbstractEvvSubmissionStrategy.java Template Method (validate → buildPayload → doSubmit)
│   ├── EvvSubmissionContext.java          record — 6 federal elements + routing metadata ONLY
│   │                                      C16: NEVER includes decrypted credential values — credentials are
│   │                                        resolved at submit time by the caller (real-time path via assembler;
│   │                                        drain job via AgencyEvvCredentialsRepository re-lookup). Safe to
│   │                                        serialize to context_json without leaking plaintext secrets.
│   ├── EvvSubmissionResult.java           record — ok()/failure() factory methods
│   ├── EvvSubmissionStatus.java           PENDING, IN_FLIGHT, SUBMITTED, ACCEPTED, REJECTED, VOIDED
│   │                                      (C5: IN_FLIGHT set before drain job calls submit(); watchdog resets
│   │                                        stale IN_FLIGHT rows older than N minutes back to PENDING on startup)
│   ├── AgencyEvvCredentials.java          @Entity; @Filter agencyFilter
│   ├── AgencyEvvCredentialsRepository.java
│   ├── EvvSubmissionRecord.java           @Entity; unique on evv_record_id; context_json for batch re-drive
│   │                                      C14: NO @Filter agencyFilter on this entity — tenant isolation
│   │                                        enforced at service layer (EvvSubmissionService receives agencyId
│   │                                        from ShiftCompletedEvent; drain job scopes by explicit agencyId param).
│   │                                        Class-level Javadoc documents this invariant.
│   ├── EvvSubmissionRecordRepository.java  standard repo; no Hibernate filter (see entity note above);
│   │                                        used by real-time path — tenant scope from ShiftCompletedEvent.agencyId
│   ├── EvvSubmissionRecordSystemRepository.java  @Repository — all @Query methods use explicit agencyId params;
│   │                                             used ONLY by EvvBatchDrainJob and startup watchdog (C9/C14)
│   ├── EvvStrategyFactory.java            @Component — auto-registers beans; wraps Auditing(Retrying(concrete))
│   ├── EvvDeadLetterQueue.java            interface
│   ├── DatabaseEvvDeadLetterQueue.java    @Component — writes REJECTED EvvSubmissionRecord
│   ├── BatchEntry.java                    record (EvvSubmissionContext ctx, AggregatorType aggregatorType)
│   │                                      (M1: was missing from layout; returned by EvvBatchQueue.drainAll())
│   ├── EvvBatchQueue.java                 interface — enqueue(ctx, type) / drainAll() → List<BatchEntry>
│   ├── DatabaseEvvBatchQueue.java         @Component — serializes credential-free EvvSubmissionContext to
│   │                                      context_json (C16: no plaintext credentials in DB);
│   │                                      enqueue() is idempotent: catches DataIntegrityViolationException
│   │                                        on duplicate evv_record_id and logs WARN (no-op re-enqueue)
│   ├── RetryingEvvSubmissionStrategy.java Decorator — 3 attempts, Thread.sleep() exp backoff with ±25% jitter
│   │                                      (2s±0.5s / 4s±1s / 8s±2s via ThreadLocalRandom); terminal on 4xx
│   ├── AuditingEvvSubmissionStrategy.java Decorator — records to IntegrationAuditLog
│   ├── EvvSubmissionService.java          @Service — @TransactionalEventListener @Async
│   ├── EvvSubmissionContextAssembler.java Loads EvvRecord + Shift + ServiceType + Caregiver + Client + Payer
│   │                                      → credential-free EvvSubmissionContext; caller resolves + decrypts
│   │                                        credentials separately before calling strategy.submit()
│   ├── EvvBatchDrainJob.java              @Scheduled("0 0 2 * * *") — drains batch queue nightly
│   ├── HcareAuthorizationChecker.java     @Component implements AuthorizationChecker
│   ├── exceptions/
│   │   ├── EvvValidationException.java    RuntimeException — not retried
│   │   ├── MissingCredentialsException.java
│   │   └── UnsupportedAggregatorException.java
│   ├── sandata/
│   │   ├── SandataSubmissionStrategy.java @Component — REST, Basic Auth, isRealTime=true
│   │   ├── SandataCredentials.java        record (username, password, payerId)
│   │   ├── SandataVisitRequest.java
│   │   └── SandataVisitResponse.java
│   ├── hhaexchange/
│   │   ├── HhaExchangeSubmissionStrategy.java @Component — REST, triple App-key headers, isRealTime=true
│   │   ├── HhaxCredentials.java           record (appName, appSecret, appKey)
│   │   ├── HhaxVisitRequest.java
│   │   └── HhaxVisitResponse.java         (evvmsid field — store for void/update)
│   ├── netsmart/
│   │   ├── NetsmarTellusSubmissionStrategy.java @Component — SFTP batch, isRealTime=false
│   │   └── NetsmarCredentials.java        record (username, sftpHost, sourceId, privateKeyRef)
│   ├── carebridge/
│   │   └── CareBridgeSubmissionStrategy.java @Component — STUB (logs + returns failure; does NOT throw)
│   └── authenticare/
│       └── AuthentiCareSubmissionStrategy.java @Component — STUB
├── billing/
│   ├── Claim.java                         immutable value object; only X12ClaimBuilder constructs it
│   ├── X12ClaimBuilder.java               Builder — institutional() / professional() entry points
│   ├── ClaimTransmissionStrategy.java     interface — submit(Claim, creds) / fetchRemittance(batchId, creds)
│   ├── AbstractClaimTransmissionStrategy.java Template Method — runs validation chain then doSubmit()
│   ├── ClaimTransmissionStrategyFactory.java @Component
│   ├── ClaimSubmissionReceipt.java        record
│   ├── RemittanceResult.java              record (notReady() factory method)
│   ├── AgencyBillingCredentials.java      record (deserialized from AgencyIntegrationConfig.credentialsEncrypted)
│   ├── NpiValidator.java                  static isValid(String) — 10-digit Luhn with "80840" prefix
│   ├── validation/
│   │   ├── ClaimValidationHandler.java    abstract Chain of Responsibility
│   │   ├── NpiFormatHandler.java
│   │   ├── AuthorizationHandler.java      delegates to AuthorizationChecker
│   │   ├── EvvLinkageHandler.java         requires ACCEPTED EvvSubmissionRecord
│   │   └── TimelyFilingHandler.java       WARN if >90 days from DOS
│   ├── stedi/
│   │   ├── StediTransmissionStrategy.java @Component
│   │   └── StediClaimAdapter.java
│   └── officeally/
│       ├── OfficeAllyTransmissionStrategy.java @Component — STUB (C10): doSubmit() returns
│       │                                        ClaimSubmissionReceipt.failure("NOT_IMPLEMENTED", "...") + WARN log
│       │                                        Does NOT throw — consistent with CareBridge/AuthentiCare stub pattern
│       └── X12Serializer.java             Wraps StAEDI; package-private; only called by OfficeAllyTransmissionStrategy
│                                          when fully wired — stub strategy never invokes it
└── payroll/
    ├── PayrollExportStrategy.java
    ├── AbstractPayrollExportStrategy.java Template Method
    ├── PayrollStrategyFactory.java        @Component — fallback to CSV_EXPORT
    ├── PayrollBatch.java                  record
    ├── PayrollableShift.java              record — all homecare pay fields
    ├── PayrollRecord.java                 sealed interface
    ├── GenericPayrollRecord.java
    ├── PayrollExportResult.java           record
    ├── AgencyPayrollCredentials.java      record
    ├── viventium/
    │   ├── ViventiumExportStrategy.java   @Component
    │   └── ViventiumPayRecord.java        FLSA 8/80 OT, blended rate, cost center
    └── csv/
        ├── CsvExportStrategy.java         @Component — universal fallback
        ├── CsvFieldMapping.java           {"columns":[...],"dateFormat":"..."} from configJson
        └── CsvSerializer.java             byte[] output

```

---

## Step 5 — Key Implementation Notes

**C1+C2 — TenantContext propagation + transaction scope in `onShiftCompleted()`:**
`@TransactionalEventListener` fires after the outer transaction commits on a different thread (the async executor). `TenantContext` (ThreadLocal) has already been cleared by `TenantFilterInterceptor.afterCompletion()`. Without explicit re-setting, any repository call in this listener runs without the Hibernate `agencyFilter` bound — a multi-tenant security boundary violation. Fix:
```java
@TransactionalEventListener
@Async
@Transactional(propagation = Propagation.REQUIRES_NEW)  // C2: own transaction scope
public void onShiftCompleted(ShiftCompletedEvent event) {
    TenantContext.setAgencyId(event.agencyId());  // C1: re-bind before any repo call
    try {
        // ... all repository calls now filter correctly via agencyFilter
    } finally {
        TenantContext.clear();  // always clean up — virtual threads reuse carriers
    }
}
```
`LocalScoringService` has the same `@TransactionalEventListener @Async` pattern and must be audited for the same exposure — extract a shared `TenantAwareEventHelper` or apply the same fix there too.

**C16 — Credentials are resolved at submit time, never stored in `EvvSubmissionContext`:**
`EvvSubmissionContext` holds only the 6 federal elements + routing metadata. Typed credential records are resolved immediately before calling `strategy.submit()` — never placed in the context and never serialized to `context_json`. Storing decrypted credentials in `evv_submission_records.context_json` would leak plaintext API secrets into a business data table (DB dumps, backups, read grants), defeating the AES-256-GCM encryption in `CredentialEncryptionService`.

Real-time path (in `onShiftCompleted()`):
```java
EvvSubmissionContext ctx = assembler.assemble(evvRecord);  // no credentials
AgencyEvvCredentials creds = credRepo.findByAgencyIdAndAggregatorType(agencyId, aggregatorType)
    .orElseThrow(() -> new MissingCredentialsException(...));
Object typedCreds = encryptionService.decrypt(creds.getCredentialsEncrypted(),
    strategy.credentialClass());  // C6: typed credential class from strategy
EvvSubmissionResult result = strategy.submit(ctx, typedCreds);
```

Batch path: drain job re-decrypts per record (see C5+C13 note below).

**C11 — `aggregatorVisitId` — both entities are updated, `EvvSubmissionRecord` is canonical:**
After a successful real-time submission:
- `EvvSubmissionRecord.aggregatorVisitId` is set when the record is first persisted (from `EvvSubmissionResult`) — this is the **canonical** reference used by void/update flows and audit queries.
- `EvvRecord.aggregatorVisitId` is also updated in the same `REQUIRES_NEW` transaction — this is a **denormalized copy** for the EVV compliance service (`EvvComplianceService.compute()`) which reads `EvvRecord` directly and must not join to `evv_submission_records`.

Both are set in `EvvSubmissionService.onShiftCompleted()`. For batch submissions, `EvvSubmissionRecord.aggregatorVisitId` is set in Tx 2 of the drain job, and `EvvRecord.aggregatorVisitId` is updated in the same Tx 2.

**`EvvSubmissionService` routing:**
```
PayerEvvRoutingConfig  →  (stateCode, payerType) match first
EvvStateConfig         →  fallback .defaultAggregator
CLOSED model           →  return immediately (agency uses state system directly)
OPEN/HYBRID            →  real-time → strategy.submit(); batch → batchQueue.enqueue()
```

**`EvvSubmissionContextAssembler` loads:**
`EvvRecord` + `Shift` + `ServiceType` (for HCPCS) + `Caregiver` (for NPI) + `Client` (for address, serviceState) + `Payer` (for payerId, payerType) → `EvvSubmissionContext`

**`CredentialEncryptionService` key bootstrapping:**
- Reads `INTEGRATION_ENCRYPTION_KEY` (Base64-encoded 256-bit key)
- `@PostConstruct` throws `IllegalStateException` if key is absent or wrong length
- Dev profile: set in `application-dev.yml` as `integration.encryption-key: <test-key>` (synthetic, not real)

**`@EnableAsync`:** Add `@EnableAsync` to `SchedulingConfig` (which already has `@EnableScheduling`). This is confirmed absent from the current `SchedulingConfig.java` — it must be added as part of this plan. Without it, `@Async` on `EvvSubmissionService` runs synchronously and blocks the API thread.

**Stub strategies (`CareBridgeSubmissionStrategy`, `AuthentiCareSubmissionStrategy`):**
Return `EvvSubmissionResult.failure("NOT_IMPLEMENTED", "...")` and log a warning. Do NOT throw — throwing would propagate out of the factory and break agencies in unrelated states.

**C12/C15 — `AgencyIntegrationConfigService` concurrency safety:**
The `save()` check-before-insert uses `@Lock(LockModeType.PESSIMISTIC_WRITE)` on the existence query to prevent TOCTOU. **Critical constraint (C15):** the lock query must return an entity instance — NOT a scalar aggregate. PostgreSQL explicitly rejects `FOR UPDATE` after aggregate functions (`SELECT COUNT(...) FOR UPDATE` → `ERROR: FOR UPDATE is not allowed with aggregate functions`), and Hibernate silently ignores the lock hint on aggregate-returning queries. The repository method must return `Optional<AgencyIntegrationConfig>`:
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("FROM AgencyIntegrationConfig c WHERE c.agencyId = :agencyId AND c.integrationType = :type " +
       "AND c.stateCode IS NOT DISTINCT FROM :stateCode " +
       "AND c.payerType IS NOT DISTINCT FROM :payerType")
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
When no row exists, `findForUpdate()` returns `Optional.empty()` with no lock issued (no row to lock). For new-row races (two concurrent inserts on a table with no existing matching row), the lock cannot help — this case is acceptable for admin-only low-frequency config creation. H2 and PostgreSQL both support `PESSIMISTIC_WRITE` on entity-returning queries and `IS NOT DISTINCT FROM` in JPQL (Hibernate 6 HQL).

**C14 — `EvvSubmissionRecord` has no `@Filter agencyFilter`:**
Tenant isolation for `EvvSubmissionRecord` is enforced at the service layer, not via Hibernate filter. `EvvSubmissionService.onShiftCompleted()` receives `agencyId` from `ShiftCompletedEvent` and scopes all queries explicitly. The drain job scopes via explicit `agencyId` param in `@Query` methods. Removing `@Filter` eliminates the ambiguity of whether the filter is active in any given call context. The entity Javadoc must state: "No Hibernate agencyFilter — tenant scope is caller responsibility. All repo callers must supply agencyId explicitly."

**C9 — `EvvBatchDrainJob` tenant isolation:**
The drain job runs on the scheduler thread — `TenantFilterInterceptor` never fires, `TenantContext` is never set. Using the standard `EvvSubmissionRecordRepository` (which has `agencyFilter` bound) would return empty results or throw. The drain job must use `EvvSubmissionRecordSystemRepository` (unfiltered), then set `TenantContext` per agency for any subsequent tenant-filtered operations:
```java
// Step 1: global query — system repo, no agency filter
List<UUID> agencies = systemRepo.findDistinctAgenciesWithPendingBatch();
for (UUID agencyId : agencies) {
    TenantContext.setAgencyId(agencyId);
    try {
        drainForAgency(agencyId);  // uses systemRepo queries scoped by agencyId param
    } catch (Exception e) {
        // C17: per-agency isolation — one misconfigured agency must not abort remaining agencies
        log.error("Drain failed for agency {} — skipping to next agency", agencyId, e);
    } finally {
        TenantContext.clear();  // always clear regardless of exception
    }
}
```
`EvvSubmissionRecordSystemRepository` uses explicit `agencyId` parameters in all `@Query` methods — the Hibernate filter is intentionally not relied upon. This is the only location in the codebase where the system repository is used.

**C5+C13 — `EvvBatchDrainJob` per-record two-phase status transition (prevents double-submission):**
The drain job processes one record at a time — bulk IN_FLIGHT marking is intentionally avoided (C13: bulk
marking would cause the watchdog to reset already-submitted records on crash). The loop per agency:
```java
// M_new_13: cache resolved credentials per aggregator type — avoids N DB calls + N AES decryptions
Map<AggregatorType, Object> credCache = new HashMap<>();

EvvSubmissionRecord next;
while ((next = systemRepo.findFirstByAgencyIdAndSubmissionModeAndStatusOrderById(
            agencyId, BATCH, PENDING)) != null) {
    int claimed = markInFlight(next.getId());           // Tx 1 — single row; returns affected count
    if (claimed == 0) continue;                         // M_new_9: another node claimed this record — skip

    if (next.getContextJson() == null) {                // M_new_11: null-guard — should never happen for BATCH rows
        log.error("BATCH record {} has null context_json — marking REJECTED", next.getId());
        markFinal(next.getId(), EvvSubmissionResult.failure("NULL_CONTEXT", "context_json missing"));
        continue;
    }

    // C16: re-decrypt credentials at drain time — never stored in context_json
    // C17: catch config errors per-record — one bad record must not abort the agency's entire drain
    EvvSubmissionStrategy strategy;
    Object typedCreds;
    try {
        strategy = strategyFactory.strategyFor(next.getAggregatorType());
        typedCreds = credCache.computeIfAbsent(next.getAggregatorType(), aggType -> {
            AgencyEvvCredentials creds = credRepo.findByAgencyIdAndAggregatorType(agencyId, aggType)
                .orElseThrow(() -> new MissingCredentialsException(...));
            return encryptionService.decrypt(creds.getCredentialsEncrypted(),
                strategy.credentialClass());
        });
    } catch (MissingCredentialsException | UnsupportedAggregatorException e) {
        log.error("Config error for record {} agency {} — marking REJECTED", next.getId(), agencyId, e);
        markFinal(next.getId(), EvvSubmissionResult.failure("CONFIG_ERROR", e.getMessage()));
        continue;
    }

    EvvSubmissionContext ctx = deserializeContext(next.getContextJson());  // no entity reload
    EvvSubmissionResult result = strategy.submit(ctx, typedCreds);         // outside any transaction
    markFinal(next.getId(), result);                    // Tx 2 — updates status + aggregator_visit_id
}
```
- **Tx 1:** `@Modifying` query returning `int` — `UPDATE ... SET status = 'IN_FLIGHT' WHERE id = :id AND status = 'PENDING'`. Returns 1 if claimed, 0 if another node already claimed it. Check the return value before calling `submit()`.
- **Tx 2:** `UPDATE ... SET status = :finalStatus, aggregator_visit_id = :visId, submitted_at = NOW() WHERE id = :id`
- At most one record is `IN_FLIGHT` at a time per agency — crash recovery resets exactly one record, not the whole batch. The affected-row-count check makes the loop safe under multi-node deployments.
- Credential cache is local to `drainForAgency()` — discarded after each agency's drain; no cross-agency credential leakage.

On startup, a `@EventListener(ApplicationReadyEvent.class)` method (using `EvvSubmissionRecordSystemRepository`) resets `IN_FLIGHT` rows older than `STALE_IN_FLIGHT_THRESHOLD` (10-minute named constant) back to `PENDING`. Also updates `EvvRecord.aggregatorVisitId` in the same Tx 2 (C11 denormalized copy). Add `IN_FLIGHT` to `EvvSubmissionStatus` enum.

**`EvvLinkageHandler` status transition:**
The plan scopes billing validation to checking for an ACCEPTED record. A separate polling/webhook job (outside this plan's scope) drives SUBMITTED → ACCEPTED. For now, the handler only gates on ACCEPTED; the status update mechanism is a follow-on task.

**`authorization.getPriorAuthNumber()`:** The correct method is `authorization.getAuthNumber()` — `priorAuthNumber` is a naming artifact from the design doc.

---

## Step 6 — New Backend EVV Files (alongside existing `com.hcare.evv`)

`EvvSubmissionService`, `EvvSubmissionContextAssembler`, `EvvBatchDrainJob`, and `HcareAuthorizationChecker` live inside `com.hcare.integration.evv` — not in `com.hcare.evv`. The existing `com.hcare.evv` package (compliance, state config, etc.) is unchanged.

---

## Step 7 — Tests

**Unit tests (`integration/evv/` and subdirectories):**

| Test | What it verifies |
|---|---|
| `SandataSubmissionStrategyTest` | MockWebServer (not MockRestServiceServer — incompatible with RestClient); payload mapping, Basic Auth header, 200 success + 4xx terminal |
| `HhaExchangeSubmissionStrategyTest` | Triple App-key headers; EVVMSID captured from response |
| `NetsmarTellusSubmissionStrategyTest` | Pipe-delimited row format; filename convention; mock SftpGateway |
| `RetryingEvvSubmissionStrategyTest` | 3 attempts; terminal on 4xx; dead-letter enqueued after exhaustion |
| `EvvStrategyFactoryTest` | Decorator order (Auditing wraps Retrying); unknown AggregatorType throws |
| `X12ClaimBuilderTest` | Fluent build; NullPointerException on missing required field at build() |
| `ClaimValidationChainTest` | Each handler in isolation + assembled chain; NPI Luhn failure |
| `CsvExportStrategyTest` | Column ordering, date format, multi-shift batch output |
| `EvvBatchDrainJobTest` | PENDING→IN_FLIGHT in Tx1; submit called once per record; SUBMITTED on success / REJECTED on failure in Tx2; startup watchdog resets stale IN_FLIGHT rows; markInFlight() returning 0 skips submit |
| `AgencyIntegrationConfigServiceTest` | Duplicate `(agencyId, type, stateCode, payerType)` throws `DuplicateIntegrationConfigException`; NULL stateCode/payerType handled via `IS NOT DISTINCT FROM`; non-duplicate insert succeeds |

**Integration test (Testcontainers, in `backend/src/test/`):**

| Test | What it verifies |
|---|---|
| `EvvSubmissionServiceIT` | Full event-listener path: publish `ShiftCompletedEvent` → `EvvSubmissionService.onShiftCompleted()` → credential lookup → strategy selected → audit log written. Test setup seeds one `AgencyEvvCredentials` row with a known `aggregatorType` and AES-256-GCM encrypted credentials using the fixed dev test key (M_new_10: without seeding, credential lookup throws `MissingCredentialsException` before any assertion fires). |

---

## Step 8 — application-dev.yml Addition

```yaml
# C7: IntegrationConnectorProperties @ConfigurationProperties("integration.connectors")
# required for IntegrationRestClientConfig to build named RestClient beans
integration:
  encryption-key: dGVzdGtleXRlc3RrZXl0ZXN0a2V5dGVzdGtleXQ=  # 32-byte test key, base64 — dev only
  connectors:
    sandata:
      base-url: https://evv.sandata.com
      connect-timeout: 5s
      read-timeout: 30s
    hhaexchange:
      base-url: https://api.hhaexchange.com
      connect-timeout: 5s
      read-timeout: 30s
    stedi:
      base-url: https://healthcare.us.stedi.com/2024-04-01
      connect-timeout: 5s
      read-timeout: 60s
    viventium:
      base-url: https://api.viventium.com
      connect-timeout: 5s
      read-timeout: 30s
```

`IntegrationConnectorProperties` is a `@ConfigurationProperties("integration.connectors")` record with a nested `ConnectorConfig` record per vendor (fields: `baseUrl`, `connectTimeout`, `readTimeout`). `IntegrationRestClientConfig` injects it via constructor and calls `RestClient.builder().baseUrl(...).defaultHeaders(...)` per connector.

---

## Verification

```bash
cd backend

# Migration
mvn flyway:info     # V13 PENDING
mvn spring-boot:run # V13 applied on startup (dev profile, H2)

# Build + tests
mvn test            # all existing + new tests green

# Smoke (dev profile)
# POST /api/v1/visits/{id}/clock-out
# → ShiftCompletedEvent published
# → integration_audit_log row written (submission fails at credential lookup — expected in dev)
```

---

## Not Implemented (deferred)

- EHR/FHIR R4 connectors
- Change Healthcare / Availity claim transmission
- QuickBooks payroll export (OAuth2 requires UI)
- KMS-managed credential encryption (swap env-key → KMS in prod)
- CareBridge / AuthentiCare concrete implementations
- EVV status polling / webhook (SUBMITTED → ACCEPTED transition)
