# Integration Layer — Low-Level Design

> Authored: 2026-04-09. Companion to `integration-landscape.md`.
> Covers EVV aggregator submission, clearinghouse/billing, and payroll export.

---

## Table of Contents

1. [Design Principles](#1-design-principles)
2. [EVV Aggregator Integration](#2-evv-aggregator-integration)
3. [Clearinghouse / Billing](#3-clearinghouse--billing)
4. [Payroll Export](#4-payroll-export)
5. [Shared Cross-Cutting Concerns](#5-shared-cross-cutting-concerns)
6. [Pattern Summary](#6-pattern-summary)

---

## 1. Design Principles

Three integration domains — EVV aggregators, clearinghouse/billing, and payroll — each use the same pattern stack.

| Problem | GoF Pattern |
|---|---|
| Swap aggregator/clearinghouse/payroll vendor without changing callers | **Strategy** |
| Shared submission workflow with per-vendor steps | **Template Method** |
| Normalize `EvvRecord`/`Shift` → vendor-specific payload | **Adapter** |
| Retry + audit logging without touching strategy implementations | **Decorator** |
| Instantiate the right Strategy from `AggregatorType` / `AgencyIntegrationConfig` | **Factory Method** |
| Construct 837I documents field-by-field | **Builder** |
| Pre-submission claim validation pipeline | **Chain of Responsibility** |
| React to clock-out without polling | **Observer** (`@TransactionalEventListener`) |

**Key invariant across all domains:** strategies are stateless Spring singletons. Credentials, agency context, and per-request state travel in context/credential value objects passed as method parameters — never stored on the strategy instance. This is safe under Spring's singleton scope and Java 25 virtual threads.

---

## 2. EVV Aggregator Integration

### 2.1 Context

`ShiftCompletedEvent` is already published after `VisitService.clockOut()` commits (established pattern from the scoring module). The EVV submission layer listens to the same event via `@TransactionalEventListener`. Aggregator routing is already handled by the existing `EvvStateConfig.defaultAggregator` + `PayerEvvRoutingConfig` (stateCode × payerType). Two gaps to close:

1. Per-agency credentials per aggregator (`AgencyEvvCredentials` entity)
2. The aggregator-assigned visit ID (EVVMSID / Sandata ref) stored back on `EvvRecord` for subsequent void/update calls

### 2.2 New Entities

```java
// com/hcare/integration/evv/AgencyEvvCredentials.java

@Entity
@Table(name = "agency_evv_credentials",
       uniqueConstraints = @UniqueConstraint(columnNames = {"agency_id", "aggregator_type"}))
@Filter(name = "agencyFilter", condition = "agency_id = :agencyId")
public class AgencyEvvCredentials {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "aggregator_type", nullable = false, length = 30)
    private AggregatorType aggregatorType;

    // AES-256 encrypted. Shape varies by aggregator:
    //   Sandata:     {"username":"…","password":"…","payerId":"…"}
    //   HHAeXchange: {"appName":"…","appSecret":"…","appKey":"…","payerId":"…"}
    //   Netsmart:    {"username":"…","password":"…","sftpHost":"…","sourceId":"…"}
    //   CareBridge:  {"sftpHost":"…","sftpUser":"…","privateKeyRef":"…"}
    @Column(name = "credentials_encrypted", nullable = false, columnDefinition = "TEXT")
    private String credentialsEncrypted;

    @Column(name = "endpoint_override")  // null = use aggregator prod default
    private String endpointOverride;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
```

```java
// com/hcare/integration/evv/EvvSubmissionRecord.java

@Entity
@Table(name = "evv_submission_records")
@Filter(name = "agencyFilter", condition = "agency_id = :agencyId")
public class EvvSubmissionRecord {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "evv_record_id", nullable = false, unique = true)
    private UUID evvRecordId;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "aggregator_type", nullable = false, length = 30)
    private AggregatorType aggregatorType;

    // ID returned by the aggregator (EVVMSID for HHAeXchange, Sandata ref, etc.)
    // Required for subsequent void/update operations.
    @Column(name = "aggregator_visit_id", length = 100)
    private String aggregatorVisitId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EvvSubmissionStatus status; // PENDING, SUBMITTED, ACCEPTED, REJECTED, VOIDED

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;
}
```

### 2.3 Strategy Interface

```java
// com/hcare/integration/evv/EvvSubmissionStrategy.java

public interface EvvSubmissionStrategy {

    AggregatorType aggregatorType();

    /** True = submit immediately on clock-out. False = accumulate for nightly SFTP batch. */
    boolean isRealTime();

    EvvSubmissionResult submit(EvvSubmissionContext ctx);
    EvvSubmissionResult update(EvvSubmissionContext ctx, String aggregatorVisitId);
    void voidVisit(String aggregatorVisitId, AgencyEvvCredentials creds);
}
```

```java
// com/hcare/integration/evv/EvvSubmissionContext.java

/** Adapter input — everything needed to build any aggregator's payload, assembled once. */
public record EvvSubmissionContext(
    UUID   evvRecordId,
    UUID   shiftId,
    UUID   agencyId,
    String stateCode,
    // Federal element 1 — type of service
    String hcpcsCode,
    String[] hcpcsModifiers,          // up to 4
    // Federal element 2 — member
    String memberMedicaidId,
    // Federal elements 4 — location
    BigDecimal locationLat,
    BigDecimal locationLon,
    String serviceAddressLine1,
    String serviceCity,
    String serviceState,
    String serviceZip,
    // Federal element 5 — caregiver
    String caregiverExternalId,
    String caregiverNpi,
    // Federal element 6 — time (3 = date is derivable from timeIn)
    LocalDateTime timeIn,
    LocalDateTime timeOut,
    VerificationMethod verificationMethod,
    // Payer routing
    String payerId,                   // aggregator-assigned payer code
    PayerType payerType,
    // Resolved credentials
    AgencyEvvCredentials credentials,
    // State-specific extra fields (JSON string already on EvvRecord.stateFields)
    String stateFieldsJson
) {}

public record EvvSubmissionResult(
    boolean success,
    String  aggregatorVisitId,   // store back on EvvRecord for void/update workflows
    String  rawResponse,
    String  errorCode,
    String  errorMessage
) {
    public static EvvSubmissionResult ok(String aggregatorVisitId, String rawResponse) {
        return new EvvSubmissionResult(true, aggregatorVisitId, rawResponse, null, null);
    }
    public static EvvSubmissionResult failure(String errorCode, String errorMessage) {
        return new EvvSubmissionResult(false, null, null, errorCode, errorMessage);
    }
}
```

### 2.4 Template Method — Shared Submission Lifecycle

```java
// com/hcare/integration/evv/AbstractEvvSubmissionStrategy.java

public abstract class AbstractEvvSubmissionStrategy implements EvvSubmissionStrategy {

    // Template method — fixed skeleton; subclasses fill in the abstract steps.
    @Override
    public final EvvSubmissionResult submit(EvvSubmissionContext ctx) {
        validate(ctx);
        Object payload = buildSubmitPayload(ctx);   // Adapter step
        return doSubmit(payload, ctx.credentials());
    }

    @Override
    public final EvvSubmissionResult update(EvvSubmissionContext ctx, String aggregatorVisitId) {
        validate(ctx);
        Object payload = buildUpdatePayload(ctx, aggregatorVisitId);
        return doUpdate(payload, aggregatorVisitId, ctx.credentials());
    }

    // Abstract steps — each subclass provides the vendor-specific implementation.
    protected abstract Object buildSubmitPayload(EvvSubmissionContext ctx);
    protected abstract Object buildUpdatePayload(EvvSubmissionContext ctx, String aggregatorVisitId);
    protected abstract EvvSubmissionResult doSubmit(Object payload, AgencyEvvCredentials creds);
    protected abstract EvvSubmissionResult doUpdate(Object payload, String aggregatorVisitId,
                                                    AgencyEvvCredentials creds);

    // Default validation — checks all 6 federal elements; subclasses may add aggregator rules.
    protected void validate(EvvSubmissionContext ctx) {
        if (ctx.memberMedicaidId() == null) throw new EvvValidationException("memberMedicaidId required");
        if (ctx.timeIn()  == null)          throw new EvvValidationException("timeIn required");
        if (ctx.timeOut() == null)          throw new EvvValidationException("timeOut required");
        if (ctx.hcpcsCode() == null)        throw new EvvValidationException("hcpcsCode required");
    }
}
```

### 2.5 Concrete Strategy + Adapter: Sandata

```java
// com/hcare/integration/evv/sandata/SandataSubmissionStrategy.java

@Component
public class SandataSubmissionStrategy extends AbstractEvvSubmissionStrategy {

    private static final String ENDPOINT = "/v1/visits";
    private final RestClient restClient;

    public SandataSubmissionStrategy(RestClient sandataRestClient) {
        this.restClient = sandataRestClient;
    }

    @Override public AggregatorType aggregatorType() { return AggregatorType.SANDATA; }
    @Override public boolean isRealTime() { return true; }

    @Override
    protected SandataVisitRequest buildSubmitPayload(EvvSubmissionContext ctx) {
        // Adapter: hcare domain → Sandata OpenEVV JSON schema (field names are case-sensitive)
        return new SandataVisitRequest(
            new SandataEmployee(ctx.caregiverExternalId()),
            new SandataClient(ctx.memberMedicaidId()),
            new SandataVisit(
                ctx.payerId(),
                ctx.shiftId().toString(),        // externalVisitId, max 30 chars
                ctx.hcpcsCode(),
                ctx.hcpcsModifiers(),
                ctx.timeIn(),                    // UTC ISO 8601: YYYY-MM-DDThh:mm
                ctx.timeOut(),
                ctx.verificationMethod().toSandataCallType(),
                ctx.locationLat(),
                ctx.locationLon(),
                ctx.serviceAddressLine1(), ctx.serviceCity(), ctx.serviceState(), ctx.serviceZip()
            )
        );
    }

    @Override
    protected EvvSubmissionResult doSubmit(Object payload, AgencyEvvCredentials creds) {
        SandataCredentials sc = decrypt(creds, SandataCredentials.class);
        String basicAuth = Base64.getEncoder().encodeToString(
            (sc.username() + ":" + sc.password()).getBytes(StandardCharsets.UTF_8));
        String url = creds.getEndpointOverride() != null
            ? creds.getEndpointOverride() + ENDPOINT
            : "https://evv.sandata.com" + ENDPOINT;

        try {
            var response = restClient.post()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Basic " + basicAuth)
                .body(payload)
                .retrieve()
                .body(SandataVisitResponse.class);
            return EvvSubmissionResult.ok(response.sandataVisitId(), toJson(response));
        } catch (HttpClientErrorException ex) {
            return EvvSubmissionResult.failure(String.valueOf(ex.getStatusCode().value()),
                ex.getResponseBodyAsString());
        }
    }

    // Sandata requires GPS or service address — override base validation to add this rule.
    @Override
    protected void validate(EvvSubmissionContext ctx) {
        super.validate(ctx);
        if (ctx.locationLat() == null && ctx.serviceAddressLine1() == null)
            throw new EvvValidationException("Sandata requires GPS coordinates or service address");
    }
}
```

### 2.6 Concrete Strategy + Adapter: HHAeXchange

```java
// com/hcare/integration/evv/hhaexchange/HhaExchangeSubmissionStrategy.java

@Component
public class HhaExchangeSubmissionStrategy extends AbstractEvvSubmissionStrategy {

    @Override public AggregatorType aggregatorType() { return AggregatorType.HHAEXCHANGE; }
    @Override public boolean isRealTime() { return true; }

    @Override
    protected HhaxVisitRequest buildSubmitPayload(EvvSubmissionContext ctx) {
        // HHAX auth credentials travel in request headers, not Basic Auth.
        // HHAX max 100 records/request — this strategy sends 1; batching is the caller's concern.
        var clockIn = new HhaxClockEvent(
            ctx.timeIn(), ctx.verificationMethod().toHhaxCallType(),
            ctx.locationLat(), ctx.locationLon(),
            ctx.serviceAddressLine1(), ctx.serviceCity(), ctx.serviceState(), ctx.serviceZip()
        );
        var clockOut = new HhaxClockEvent(ctx.timeOut(), ctx.verificationMethod().toHhaxCallType(),
            ctx.locationLat(), ctx.locationLon(),
            ctx.serviceAddressLine1(), ctx.serviceCity(), ctx.serviceState(), ctx.serviceZip()
        );
        return new HhaxVisitRequest(
            ctx.credentials().taxId(),
            new HhaxMember("MedicaidID", ctx.memberMedicaidId()),
            new HhaxCaregiver("ExternalID", ctx.caregiverExternalId()),
            ctx.payerId(),
            ctx.shiftId().toString(),   // externalVisitID, max 30 chars
            ctx.hcpcsCode(),
            TimezoneResolver.forState(ctx.stateCode()),
            ctx.timeIn(), ctx.timeOut(),
            clockIn, clockOut
        );
    }

    @Override
    protected EvvSubmissionResult doSubmit(Object payload, AgencyEvvCredentials creds) {
        HhaxCredentials hc = decrypt(creds, HhaxCredentials.class);
        try {
            var response = restClient.post()
                .uri(creds.getEndpointOverride() != null ? creds.getEndpointOverride() : defaultUrl())
                .header("AppName",   hc.appName())
                .header("AppSecret", hc.appSecret())
                .header("AppKey",    hc.appKey())
                .body(payload)
                .retrieve()
                .body(HhaxVisitResponse.class);
            // EVVMSID must be retained — required for subsequent PUT (update) and DELETE (void)
            return EvvSubmissionResult.ok(response.evvmsid(), toJson(response));
        } catch (HttpClientErrorException ex) {
            return EvvSubmissionResult.failure(String.valueOf(ex.getStatusCode().value()),
                ex.getResponseBodyAsString());
        }
    }
}
```

### 2.7 Concrete Strategy + Adapter: Netsmart/Tellus (SFTP batch)

```java
// com/hcare/integration/evv/netsmart/NetsmarTellusSubmissionStrategy.java

@Component
public class NetsmarTellusSubmissionStrategy extends AbstractEvvSubmissionStrategy {

    private final SftpGateway sftpGateway;  // Apache Camel SFTP channel adapter

    @Override public AggregatorType aggregatorType() { return AggregatorType.NETSMART; }
    @Override public boolean isRealTime() { return false; }  // nightly SFTP batch

    @Override
    protected String buildSubmitPayload(EvvSubmissionContext ctx) {
        // Adapter: domain → pipe-delimited format
        // File naming: <SourceID>_VISIT_<DateTime>_<ProviderEIN>_<ProviderNPI>.txt
        NetsmarCredentials nc = decrypt(ctx.credentials(), NetsmarCredentials.class);
        return String.join("|",
            nc.sourceId(),
            ctx.evvRecordId().toString(),
            ctx.memberMedicaidId(),
            ctx.caregiverNpi(),
            ctx.hcpcsCode(),
            formatNetsmarDateTime(ctx.timeIn()),
            formatNetsmarDateTime(ctx.timeOut()),
            ctx.serviceAddressLine1(), ctx.serviceCity(), ctx.serviceState(), ctx.serviceZip()
        );
    }

    @Override
    protected EvvSubmissionResult doSubmit(Object payload, AgencyEvvCredentials creds) {
        NetsmarCredentials nc = decrypt(creds, NetsmarCredentials.class);
        String filename = buildFilename(nc.sourceId(), creds.getAgencyId());
        sftpGateway.upload(nc.sftpHost(), nc.username(), payload.toString(), filename);
        // Netsmart is async — no immediate aggregatorVisitId; populated when reject file is clean.
        return EvvSubmissionResult.ok(null, filename);
    }
}
```

### 2.8 Decorator: Retry with Exponential Backoff

```java
// com/hcare/integration/evv/RetryingEvvSubmissionStrategy.java

/**
 * Decorator — wraps any EvvSubmissionStrategy with retry and dead-letter fallback.
 * The wrapped strategy is unaware of retry logic; callers hold this decorator, not the inner strategy.
 */
public class RetryingEvvSubmissionStrategy implements EvvSubmissionStrategy {

    private final EvvSubmissionStrategy delegate;
    private final EvvDeadLetterQueue    deadLetterQueue;
    private final int                   maxAttempts;

    public RetryingEvvSubmissionStrategy(EvvSubmissionStrategy delegate,
                                         EvvDeadLetterQueue deadLetterQueue,
                                         int maxAttempts) {
        this.delegate        = delegate;
        this.deadLetterQueue = deadLetterQueue;
        this.maxAttempts     = maxAttempts;
    }

    @Override public AggregatorType aggregatorType() { return delegate.aggregatorType(); }
    @Override public boolean isRealTime()            { return delegate.isRealTime(); }

    @Override
    public EvvSubmissionResult submit(EvvSubmissionContext ctx) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                var result = delegate.submit(ctx);
                if (result.success()) return result;
                if (isTerminal(result.errorCode())) break;   // 4xx — bad data, retry won't help
            } catch (EvvValidationException ex) {
                throw ex;                                    // validation is never retryable
            } catch (Exception ex) {
                if (attempt == maxAttempts) break;
            }
            sleepExponential(attempt);  // 2s, 4s, 8s
        }
        deadLetterQueue.enqueue(ctx);
        return EvvSubmissionResult.failure("MAX_RETRIES", "Sent to dead-letter queue for manual review");
    }

    private boolean isTerminal(String code) {
        return code != null && code.startsWith("4");
    }

    // update/void delegate straight through
    @Override public EvvSubmissionResult update(EvvSubmissionContext ctx, String id) { return delegate.update(ctx, id); }
    @Override public void voidVisit(String id, AgencyEvvCredentials creds)           { delegate.voidVisit(id, creds); }
}
```

### 2.9 Decorator: Audit Logging

```java
// com/hcare/integration/evv/AuditingEvvSubmissionStrategy.java

public class AuditingEvvSubmissionStrategy implements EvvSubmissionStrategy {

    private final EvvSubmissionStrategy  delegate;
    private final IntegrationAuditWriter audit;

    @Override
    public EvvSubmissionResult submit(EvvSubmissionContext ctx) {
        var start  = Instant.now();
        var result = delegate.submit(ctx);
        audit.record(ctx.agencyId(), ctx.evvRecordId(),
            delegate.aggregatorType().name(), "SUBMIT",
            result.success(), Duration.between(start, Instant.now()), result.errorCode());
        return result;
    }

    @Override public AggregatorType aggregatorType() { return delegate.aggregatorType(); }
    @Override public boolean isRealTime()            { return delegate.isRealTime(); }
    @Override public EvvSubmissionResult update(EvvSubmissionContext ctx, String id) { return delegate.update(ctx, id); }
    @Override public void voidVisit(String id, AgencyEvvCredentials creds)           { delegate.voidVisit(id, creds); }
}
```

### 2.10 Factory Method

```java
// com/hcare/integration/evv/EvvStrategyFactory.java

/**
 * Factory Method — resolves AggregatorType → decorated strategy chain.
 * Spring auto-registers all EvvSubmissionStrategy beans; factory maps them by aggregatorType().
 * Callers never instantiate strategies directly.
 */
@Component
public class EvvStrategyFactory {

    private final Map<AggregatorType, EvvSubmissionStrategy> strategies;
    private final AgencyEvvCredentialsRepository             credsRepo;
    private final EvvDeadLetterQueue                         deadLetterQueue;
    private final IntegrationAuditWriter                     audit;

    public EvvStrategyFactory(List<EvvSubmissionStrategy> strategyBeans,
                              AgencyEvvCredentialsRepository credsRepo,
                              EvvDeadLetterQueue deadLetterQueue,
                              IntegrationAuditWriter audit) {
        this.strategies = strategyBeans.stream()
            .collect(Collectors.toMap(EvvSubmissionStrategy::aggregatorType, s -> s));
        this.credsRepo       = credsRepo;
        this.deadLetterQueue = deadLetterQueue;
        this.audit           = audit;
    }

    /** Returns the fully-decorated strategy. Caller does not know which decorators are applied. */
    public EvvSubmissionStrategy forAgency(UUID agencyId, AggregatorType type) {
        var base = strategies.get(type);
        if (base == null) throw new UnsupportedAggregatorException(type);
        // Compose: Auditing(Retrying(concrete strategy))
        return new AuditingEvvSubmissionStrategy(
               new RetryingEvvSubmissionStrategy(base, deadLetterQueue, 3), audit);
    }
}
```

### 2.11 Service — Wires to Existing Event System

```java
// com/hcare/integration/evv/EvvSubmissionService.java

@Service
public class EvvSubmissionService {

    private final EvvStateConfigRepository        stateConfigRepo;
    private final PayerEvvRoutingConfigRepository routingRepo;
    private final EvvRecordRepository             evvRecordRepo;
    private final ShiftRepository                 shiftRepo;
    private final AgencyEvvCredentialsRepository  credsRepo;
    private final EvvStrategyFactory              factory;
    private final EvvBatchQueue                   batchQueue;

    // Mirrors LocalScoringService: listens after outer transaction commits.
    @TransactionalEventListener
    @Async
    public void onShiftCompleted(ShiftCompletedEvent event) {
        var evvRecord   = evvRecordRepo.findByShiftId(event.shiftId()).orElseThrow();
        var shift       = shiftRepo.findById(event.shiftId()).orElseThrow();
        var payer       = shift.getPayer();

        var stateConfig = stateConfigRepo.findByStateCode(payer.getState()).orElseThrow();
        if (stateConfig.getSystemModel() == EvvSystemModel.CLOSED) return;  // agency uses closed system directly

        var aggregatorType = resolveAggregator(payer.getState(), payer.getPayerType());
        var creds          = credsRepo.findByAgencyIdAndAggregatorType(event.agencyId(), aggregatorType)
                                      .orElseThrow(() -> new MissingCredentialsException(event.agencyId(), aggregatorType));
        var ctx            = EvvSubmissionContextAssembler.from(evvRecord, shift, payer, creds);
        var strategy       = factory.forAgency(event.agencyId(), aggregatorType);

        if (stateConfig.isRequiresRealTimeSubmission()) {
            var result = strategy.submit(ctx);
            if (result.success()) {
                evvRecord.setAggregatorVisitId(result.aggregatorVisitId());
                evvRecordRepo.save(evvRecord);
            }
        } else {
            // SFTP-based aggregators (Netsmart, CareBridge): accumulate, drain via @Scheduled job
            batchQueue.enqueue(ctx, aggregatorType);
        }
    }

    // Check PayerEvvRoutingConfig first (multi-aggregator states), fall back to EvvStateConfig.
    private AggregatorType resolveAggregator(String stateCode, PayerType payerType) {
        return routingRepo.findByStateCodeAndPayerType(stateCode, payerType)
            .map(PayerEvvRoutingConfig::getAggregatorType)
            .orElseGet(() -> stateConfigRepo.findByStateCode(stateCode)
                                            .map(EvvStateConfig::getDefaultAggregator)
                                            .orElseThrow(() -> new UnconfiguredStateException(stateCode)));
    }
}
```

---

## 3. Clearinghouse / Billing

### 3.1 Builder — X12 837I Construction

The Builder separates claim _data assembly_ from claim _transmission_. Optional fields are naturally skipped; required fields are validated at `build()` time rather than at construction.

```java
// com/hcare/integration/billing/X12ClaimBuilder.java

public final class X12ClaimBuilder {

    private String    billingProviderNpi;
    private String    billingProviderTaxId;
    private String    renderingProviderNpi;   // caregiver NPI — required for EVV linkage on 837I
    private String    memberMedicaidId;
    private String    payerName;
    private String    payerId;
    private LocalDate dateOfService;
    private String    revenueCode;            // NUBC code, e.g. "0571" for SN home visits
    private String    hcpcsCode;
    private String    icd10Primary;
    private final List<String> icd10Secondary = new ArrayList<>();
    private BigDecimal billedAmount;
    private int        units;
    private String     claimFrequencyCode = "1";  // original claim default
    private String     priorAuthNumber;           // optional

    private X12ClaimBuilder() {}

    public static X12ClaimBuilder institutional() { return new X12ClaimBuilder(); }

    public static X12ClaimBuilder professional() {
        // 837P path: uses CPT/HCPCS without revenue codes, maps to CMS-1500
        var b = new X12ClaimBuilder();
        b.revenueCode = null; // not used in 837P
        return b;
    }

    public X12ClaimBuilder billingProvider(String npi, String taxId) {
        this.billingProviderNpi = npi; this.billingProviderTaxId = taxId; return this;
    }
    public X12ClaimBuilder renderingProvider(String npi) { this.renderingProviderNpi = npi; return this; }
    public X12ClaimBuilder member(String medicaidId)      { this.memberMedicaidId = medicaidId; return this; }
    public X12ClaimBuilder payer(String name, String id)  { this.payerName = name; this.payerId = id; return this; }
    public X12ClaimBuilder dateOfService(LocalDate d)     { this.dateOfService = d; return this; }
    public X12ClaimBuilder serviceRevenue(String rev, String hcpcs) { this.revenueCode = rev; this.hcpcsCode = hcpcs; return this; }
    public X12ClaimBuilder primaryDiagnosis(String icd10) { this.icd10Primary = icd10; return this; }
    public X12ClaimBuilder addDiagnosis(String icd10)     { this.icd10Secondary.add(icd10); return this; }
    public X12ClaimBuilder billedAmount(BigDecimal amt)   { this.billedAmount = amt; return this; }
    public X12ClaimBuilder units(int u)                   { this.units = u; return this; }
    public X12ClaimBuilder priorAuth(String authNumber)   { this.priorAuthNumber = authNumber; return this; }
    public X12ClaimBuilder frequency(String code)         { this.claimFrequencyCode = code; return this; }

    /**
     * Produces an immutable Claim domain object (not a JPA entity — an in-flight value object).
     * Does NOT serialize to X12 here; serialization is the ClaimTransmissionStrategy's concern.
     */
    public Claim build() {
        Objects.requireNonNull(billingProviderNpi,  "billingProviderNpi");
        Objects.requireNonNull(renderingProviderNpi, "renderingProviderNpi — caregiver NPI required for EVV linkage");
        Objects.requireNonNull(memberMedicaidId,    "memberMedicaidId");
        Objects.requireNonNull(dateOfService,       "dateOfService");
        Objects.requireNonNull(icd10Primary,        "icd10Primary");
        Objects.requireNonNull(billedAmount,        "billedAmount");
        return new Claim(this);
    }

    // Claim is a package-private immutable value object; X12ClaimBuilder is its only constructor.
}
```

**Usage in BillingService:**

```java
Claim claim = X12ClaimBuilder.institutional()
    .billingProvider(agency.getNpi(), agency.getTaxId())
    .renderingProvider(caregiver.getNpi())     // EVV compliance requires caregiver NPI at service line
    .member(evvRecord.getClientMedicaidId())
    .payer(payer.getName(), payer.getPayerId())
    .dateOfService(shift.getScheduledStart().toLocalDate())
    .serviceRevenue("0571", shift.getHcpcsCode())
    .primaryDiagnosis(carePlan.getPrimaryIcd10())
    .billedAmount(shift.getBilledAmount())
    .units(shift.getUnits())
    .priorAuth(authorization.getPriorAuthNumber())  // optional
    .build();
```

### 3.2 Chain of Responsibility — Pre-Submission Validation

```java
// com/hcare/integration/billing/validation/ClaimValidationHandler.java

public abstract class ClaimValidationHandler {

    private ClaimValidationHandler next;

    public ClaimValidationHandler then(ClaimValidationHandler next) {
        this.next = next;
        return next;
    }

    public final List<String> validate(Claim claim) {
        var errors = doValidate(claim);
        if (next != null) errors.addAll(next.validate(claim));
        return errors;
    }

    protected abstract List<String> doValidate(Claim claim);
}
```

```java
// Concrete handlers — each checks exactly one rule.

class NpiFormatHandler extends ClaimValidationHandler {
    @Override protected List<String> doValidate(Claim claim) {
        if (!NpiValidator.isValid(claim.renderingProviderNpi()))
            return List.of("Invalid rendering provider NPI: " + claim.renderingProviderNpi());
        return List.of();
    }
}

class AuthorizationHandler extends ClaimValidationHandler {
    private final AuthorizationUnitService authService;
    @Override protected List<String> doValidate(Claim claim) {
        if (authService.wouldExceedAuthorizedHours(claim))
            return List.of("Claim exceeds remaining authorized hours for authorization " + claim.authorizationId());
        return List.of();
    }
}

class EvvLinkageHandler extends ClaimValidationHandler {
    private final EvvSubmissionRecordRepository submissionRepo;
    @Override protected List<String> doValidate(Claim claim) {
        if (submissionRepo.findByEvvRecordId(claim.evvRecordId()).isEmpty())
            return List.of("No accepted EVV submission found for shift " + claim.shiftId());
        return List.of();
    }
}

class TimelyFilingHandler extends ClaimValidationHandler {
    @Override protected List<String> doValidate(Claim claim) {
        if (claim.dateOfService().isBefore(LocalDate.now().minusDays(90)))
            return List.of("WARN: Claim is > 90 days from date of service — verify timely filing limit");
        return List.of();
    }
}
```

```java
// Assembly in BillingService:
var chain = new NpiFormatHandler();
chain.then(new AuthorizationHandler(authService))
     .then(new EvvLinkageHandler(submissionRepo))
     .then(new TimelyFilingHandler());

var errors = chain.validate(claim);
if (!errors.isEmpty()) throw new ClaimValidationException(errors);
```

### 3.3 Strategy — Claim Transmission

```java
// com/hcare/integration/billing/ClaimTransmissionStrategy.java

public interface ClaimTransmissionStrategy {
    ClearinghouseType clearinghouseType();
    ClaimSubmissionReceipt submit(Claim claim, AgencyBillingCredentials creds);
    RemittanceResult fetchRemittance(String batchId, AgencyBillingCredentials creds);
}
```

```java
// com/hcare/integration/billing/StediTransmissionStrategy.java

@Component
public class StediTransmissionStrategy implements ClaimTransmissionStrategy {

    private final RestClient stediClient;

    @Override public ClearinghouseType clearinghouseType() { return ClearinghouseType.STEDI; }

    @Override
    public ClaimSubmissionReceipt submit(Claim claim, AgencyBillingCredentials creds) {
        // Stedi accepts JSON — StediClaimAdapter converts the Claim value object to Stedi's schema.
        // Stedi also accepts raw X12 if the agency already generates it.
        var body = StediClaimAdapter.from(claim);
        var response = stediClient.post()
            .uri("/healthcare/claims/institutional")
            .header("Authorization", "Key " + creds.apiKey())
            .body(body)
            .retrieve()
            .body(StediSubmissionResponse.class);
        return new ClaimSubmissionReceipt(response.submissionId(), response.status());
    }

    @Override
    public RemittanceResult fetchRemittance(String batchId, AgencyBillingCredentials creds) {
        // GET /healthcare/providers/remittances — returns 835-equivalent JSON
        var response = stediClient.get()
            .uri("/healthcare/providers/remittances?submissionId=" + batchId)
            .header("Authorization", "Key " + creds.apiKey())
            .retrieve()
            .body(StediRemittanceResponse.class);
        return RemittanceResultAdapter.from(response);
    }
}
```

```java
// com/hcare/integration/billing/OfficeAllyTransmissionStrategy.java

@Component
public class OfficeAllyTransmissionStrategy implements ClaimTransmissionStrategy {

    private final X12Serializer x12Serializer;  // wraps StAEDI EDIStreamWriter
    private final SftpClient    sftpClient;

    @Override public ClearinghouseType clearinghouseType() { return ClearinghouseType.OFFICE_ALLY; }

    @Override
    public ClaimSubmissionReceipt submit(Claim claim, AgencyBillingCredentials creds) {
        // Office Ally is SFTP-only; serialize to X12 and drop file.
        byte[] x12Bytes = x12Serializer.serialize837I(claim);
        String fileName = buildOaFileName(claim, creds);   // OA naming convention
        sftpClient.upload(creds.sftpEndpoint(), fileName, x12Bytes, creds.sftpCredentials());
        // No synchronous receipt — poll /outbound for 999/277CA separately.
        return new ClaimSubmissionReceipt(fileName, "PENDING_ACK");
    }

    @Override
    public RemittanceResult fetchRemittance(String batchId, AgencyBillingCredentials creds) {
        // Poll SFTP outbound folder for 835 files matching batchId.
        var files = sftpClient.listFiles(creds.sftpOutboundPath(), batchId + "*.835");
        return files.stream()
            .findFirst()
            .map(f -> x12RemittanceParser.parse835(sftpClient.download(f)))
            .orElse(RemittanceResult.notReady());
    }
}
```

### 3.4 Template Method — Billing Service

```java
// com/hcare/integration/billing/AbstractClaimTransmissionStrategy.java

public abstract class AbstractClaimTransmissionStrategy implements ClaimTransmissionStrategy {

    private final ClaimValidationHandler validationChain;

    protected AbstractClaimTransmissionStrategy(ClaimValidationHandler validationChain) {
        this.validationChain = validationChain;
    }

    @Override
    public final ClaimSubmissionReceipt submit(Claim claim, AgencyBillingCredentials creds) {
        var errors = validationChain.validate(claim);
        if (!errors.isEmpty()) throw new ClaimValidationException(errors);
        return doSubmit(claim, creds);
    }

    protected abstract ClaimSubmissionReceipt doSubmit(Claim claim, AgencyBillingCredentials creds);
}
```

---

## 4. Payroll Export

### 4.1 Strategy + Template Method

```java
// com/hcare/integration/payroll/PayrollExportStrategy.java

public interface PayrollExportStrategy {
    PayrollVendor vendor();
    PayrollExportResult export(PayrollBatch batch, AgencyPayrollCredentials creds);
}
```

```java
// com/hcare/integration/payroll/AbstractPayrollExportStrategy.java

public abstract class AbstractPayrollExportStrategy implements PayrollExportStrategy {

    @Override
    public final PayrollExportResult export(PayrollBatch batch, AgencyPayrollCredentials creds) {
        var records = batch.shifts().stream()
            .map(this::toPayrollRecord)   // Adapter step — subclass maps domain → vendor schema
            .toList();
        return transmit(records, creds);
    }

    protected abstract PayrollRecord toPayrollRecord(PayrollableShift shift);
    protected abstract PayrollExportResult transmit(List<PayrollRecord> records,
                                                    AgencyPayrollCredentials creds);
}
```

### 4.2 Concrete Strategy: Viventium (REST API)

```java
// com/hcare/integration/payroll/ViventiumExportStrategy.java

@Component
public class ViventiumExportStrategy extends AbstractPayrollExportStrategy {

    private final RestClient viventiumClient;

    @Override public PayrollVendor vendor() { return PayrollVendor.VIVENTIUM; }

    @Override
    protected ViventiumPayRecord toPayrollRecord(PayrollableShift shift) {
        // Adapter: homecare domain → Viventium pay-batch structure.
        // Viventium is purpose-built for homecare; these fields have no generic payroll equivalent.
        return new ViventiumPayRecord(
            shift.caregiverEmployeeId(),
            shift.regularHours(),
            shift.overtimeHours(),           // pre-computed under FLSA 8/80 rule, not 40-hr week
            shift.travelTimeMins(),
            shift.mileageReimbursement(),
            shift.blendedRate(),             // dual-client visits with different pay rates
            shift.clientCostCenter(),        // Medicaid billing cost center split for reporting
            shift.dateOfService()
        );
    }

    @Override
    protected PayrollExportResult transmit(List<PayrollRecord> records,
                                           AgencyPayrollCredentials creds) {
        // POST /v1/time-attendance/import/companies/{co}/divisions/{div}/payrolls/{date}/{run}/pay-batches/{batch}
        var response = viventiumClient.post()
            .uri(creds.importUri())
            .header(HttpHeaders.COOKIE, "auth=" + creds.sessionToken())
            .body(new ViventiumPayBatch(records))
            .retrieve()
            .body(ViventiumImportResponse.class);
        return new PayrollExportResult(response.accepted(), response.rejected(), response.batchId());
    }
}
```

### 4.3 Concrete Strategy: Configurable CSV (Universal Fallback)

```java
// com/hcare/integration/payroll/CsvExportStrategy.java

/**
 * Universal fallback — configurable field mapping covers ADP, Paychex, Gusto, QuickBooks export,
 * and any vendor not worth a dedicated native integration.
 * Column order, field names, date formats, and pay code names are stored per agency
 * in AgencyPayrollCredentials.configJson — no code change required to add a new vendor.
 */
@Component
public class CsvExportStrategy extends AbstractPayrollExportStrategy {

    @Override public PayrollVendor vendor() { return PayrollVendor.CSV_EXPORT; }

    @Override
    protected GenericPayrollRecord toPayrollRecord(PayrollableShift shift) {
        return new GenericPayrollRecord(shift);  // exposes all homecare pay fields
    }

    @Override
    protected PayrollExportResult transmit(List<PayrollRecord> records,
                                           AgencyPayrollCredentials creds) {
        // CsvFieldMapping is stored per agency in creds.configJson:
        //   {"columns":["EmployeeId","Date","RegHours","OTHours","Mileage"],"dateFormat":"MM/dd/yyyy"}
        var mapping = CsvFieldMapping.from(creds.configJson());
        byte[] csv  = CsvSerializer.serialize(records, mapping);
        return deliverCsv(csv, creds);  // SFTP, presigned S3, or secure download link
    }
}
```

### 4.4 Factory

```java
// com/hcare/integration/payroll/PayrollStrategyFactory.java

@Component
public class PayrollStrategyFactory {

    private final Map<PayrollVendor, PayrollExportStrategy> strategies;

    public PayrollStrategyFactory(List<PayrollExportStrategy> strategyBeans) {
        this.strategies = strategyBeans.stream()
            .collect(Collectors.toMap(PayrollExportStrategy::vendor, s -> s));
    }

    public PayrollExportStrategy forAgency(UUID agencyId, AgencyPayrollConfig config) {
        var vendor   = config.getVendor();
        var strategy = strategies.get(vendor);
        if (strategy == null) strategy = strategies.get(PayrollVendor.CSV_EXPORT);  // safe fallback
        return strategy;
    }
}
```

---

## 5. Shared Cross-Cutting Concerns

### 5.1 AgencyIntegrationConfig Entity

One table governs all three integration domains. The connector class determines which strategy is selected; credentials are encrypted per row.

```java
// com/hcare/integration/config/AgencyIntegrationConfig.java

@Entity
@Table(name = "agency_integration_configs",
       uniqueConstraints = @UniqueConstraint(
           columnNames = {"agency_id", "integration_type", "state_code", "payer_type"}))
@Filter(name = "agencyFilter", condition = "agency_id = :agencyId")
public class AgencyIntegrationConfig {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "integration_type", nullable = false, length = 30)
    private IntegrationType integrationType;   // EVV_AGGREGATOR, CLEARINGHOUSE, PAYROLL, EHR

    @Column(name = "connector_class", nullable = false, length = 100)
    private String connectorClass;  // e.g. "SandataSubmissionStrategy", "StediTransmissionStrategy"

    // Null = applies to all states/payers; set for multi-aggregator state routing.
    @Column(name = "state_code", columnDefinition = "CHAR(2)")
    private String stateCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "payer_type", length = 20)
    private PayerType payerType;

    @Column(name = "endpoint_url", length = 500)
    private String endpointUrl;

    // AES-256 encrypted, KMS-managed key. Shape is connector-specific (see AgencyEvvCredentials comment).
    @Column(name = "credentials_encrypted", nullable = false, columnDefinition = "TEXT")
    private String credentialsEncrypted;

    // Connector-specific: field mappings, batch size overrides, timezone, etc.
    @Column(name = "config_json", columnDefinition = "TEXT")
    private String configJson;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now(ZoneOffset.UTC);

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now(ZoneOffset.UTC);
}
```

### 5.2 IntegrationAuditLog

Append-only, separate from `PhiAuditLog`. Records integration I/O outcomes without PHI.

```java
// com/hcare/integration/audit/IntegrationAuditLog.java

@Entity
@Table(name = "integration_audit_log")
@org.hibernate.annotations.Immutable
public class IntegrationAuditLog {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "agency_id",    nullable = false) private UUID   agencyId;
    @Column(name = "entity_id",    nullable = false) private UUID   entityId;    // evvRecordId / shiftId / claimId
    @Column(name = "connector",    nullable = false) private String connector;
    @Column(name = "operation",    nullable = false) private String operation;   // SUBMIT, UPDATE, VOID
    @Column(name = "success",      nullable = false) private boolean success;
    @Column(name = "duration_ms",  nullable = false) private long   durationMs;
    @Column(name = "error_code")                     private String errorCode;
    @Column(name = "recorded_at",  nullable = false)
    private LocalDateTime recordedAt = LocalDateTime.now(ZoneOffset.UTC);
}
```

### 5.3 Package Layout

```
backend/src/main/java/com/hcare/
└── integration/
    ├── config/
    │   ├── AgencyIntegrationConfig.java
    │   └── AgencyIntegrationConfigRepository.java
    ├── audit/
    │   ├── IntegrationAuditLog.java
    │   ├── IntegrationAuditLogRepository.java
    │   └── IntegrationAuditWriter.java
    ├── evv/
    │   ├── EvvSubmissionStrategy.java          (interface)
    │   ├── AbstractEvvSubmissionStrategy.java  (Template Method)
    │   ├── EvvSubmissionContext.java           (record)
    │   ├── EvvSubmissionResult.java            (record)
    │   ├── EvvSubmissionRecord.java            (entity)
    │   ├── AgencyEvvCredentials.java           (entity)
    │   ├── EvvStrategyFactory.java             (Factory Method)
    │   ├── EvvSubmissionService.java           (Context + event listener)
    │   ├── RetryingEvvSubmissionStrategy.java  (Decorator)
    │   ├── AuditingEvvSubmissionStrategy.java  (Decorator)
    │   ├── sandata/
    │   │   └── SandataSubmissionStrategy.java
    │   ├── hhaexchange/
    │   │   └── HhaExchangeSubmissionStrategy.java
    │   ├── netsmart/
    │   │   └── NetsmarTellusSubmissionStrategy.java
    │   ├── carebridge/
    │   │   └── CareBridgeSubmissionStrategy.java
    │   └── authenticare/
    │       └── AuthentiCareSubmissionStrategy.java
    ├── billing/
    │   ├── X12ClaimBuilder.java                (Builder)
    │   ├── Claim.java                          (immutable value object)
    │   ├── ClaimTransmissionStrategy.java      (interface)
    │   ├── AbstractClaimTransmissionStrategy.java (Template Method)
    │   ├── ClaimTransmissionStrategyFactory.java
    │   ├── validation/
    │   │   ├── ClaimValidationHandler.java     (Chain of Responsibility)
    │   │   ├── NpiFormatHandler.java
    │   │   ├── AuthorizationHandler.java
    │   │   ├── EvvLinkageHandler.java
    │   │   └── TimelyFilingHandler.java
    │   ├── stedi/
    │   │   ├── StediTransmissionStrategy.java
    │   │   └── StediClaimAdapter.java
    │   └── officeally/
    │       ├── OfficeAllyTransmissionStrategy.java
    │       └── X12Serializer.java              (wraps StAEDI)
    └── payroll/
        ├── PayrollExportStrategy.java          (interface)
        ├── AbstractPayrollExportStrategy.java  (Template Method)
        ├── PayrollStrategyFactory.java
        ├── viventium/
        │   └── ViventiumExportStrategy.java
        ├── quickbooks/
        │   └── QuickBooksExportStrategy.java
        └── csv/
            ├── CsvExportStrategy.java
            └── CsvFieldMapping.java
```

---

## 6. Pattern Summary

| GoF Pattern | Where Applied | Effect |
|---|---|---|
| **Strategy** | All three domains — one class per vendor | Swap connectors without touching the service layer |
| **Template Method** | `Abstract*Strategy` classes in all three domains | Enforce the submission lifecycle (validate → adapt → transmit) once; subclasses fill in wire-format steps |
| **Adapter** | `buildSubmitPayload()` in each concrete Strategy; `StediClaimAdapter`; `CsvFieldMapping` | Isolates messy vendor schemas from the clean internal domain model |
| **Decorator** | `RetryingEvvSubmissionStrategy`, `AuditingEvvSubmissionStrategy` | Compose retry and audit independently of connector logic; nest additional decorators (rate-limiting, circuit-breaking) without touching strategies |
| **Factory Method** | `EvvStrategyFactory.forAgency()`, `PayrollStrategyFactory.forAgency()` | Reads tenant config → selects concrete strategy → composes decorator chain; callers never `new` a strategy |
| **Builder** | `X12ClaimBuilder` | 837I has 30+ fields; fluent builder enforces required fields at `build()` and makes optional fields explicit |
| **Chain of Responsibility** | `ClaimValidationHandler` chain | Each validation rule is an independent link; add/remove rules without touching others |
| **Observer** | `@TransactionalEventListener(ShiftCompletedEvent)` | Matches established scoring module pattern; EVV submission fires post-commit without coupling to `VisitService` |
