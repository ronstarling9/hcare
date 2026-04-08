# Phase 8: Backend Payer/EVV Endpoints + Wire Frontend

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the two missing backend endpoints (`GET /api/v1/payers`, `GET /api/v1/payers/{id}`, `GET /api/v1/evv/history`) then wire the frontend Payers and EVV Status screens to real data.

**Before starting:**
- All prior phases must be complete.
- `EvvStateConfigRepository.findByStateCode(stateCode)` exists.
- `EvvComplianceService.compute(record, stateConfig, shift, payerType, clientLat, clientLng)` exists.
- `ShiftRepository.findByAgencyIdAndScheduledStartBetween(agencyId, start, end, pageable)` exists.
- `PayerRepository.findByAgencyId(agencyId)` returns a `List<Payer>`.
- `EvvRecordRepository.findByShiftIdIn(Set<UUID> shiftIds)` exists. If missing, add `List<EvvRecord> findByShiftIdIn(Set<UUID> shiftIds);` to `EvvRecordRepository` before starting Task 5.
- `AgencyRepository extends JpaRepository<Agency, UUID>` exists at `com.hcare.domain.AgencyRepository` (confirmed present from prior phases).
- `AuthorizationRepository` exists in `com.hcare.domain` and exposes `findAllById(Collection<UUID>)` (standard `JpaRepository` method). If missing, create: `public interface AuthorizationRepository extends JpaRepository<Authorization, UUID> {}`
- `Agency.getState()` returns a nullable `String` (confirmed present from prior phases).
- `Shift.getAuthorizationId()` returns a nullable `UUID` FK to `authorizations(id)` (confirmed: `authorization_id` column in `V6__shift_domain_schema.sql`).

**Backend commands run from `backend/`. Frontend commands run from `frontend/`.**

---

### Task 1: Create Payer DTOs

**Files:**
- Create: `backend/src/main/java/com/hcare/api/v1/payers/dto/PayerResponse.java`

- [ ] **Step 1.1: Create PayerResponse**

Create `backend/src/main/java/com/hcare/api/v1/payers/dto/PayerResponse.java`:

```java
package com.hcare.api.v1.payers.dto;

import com.hcare.domain.PayerType;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for a single payer.
 *
 * <p>evvAggregator is derived at query time from EvvStateConfig for the payer's state.
 * It is null when no EvvStateConfig row exists for the state (uncommon — states are
 * Flyway-seeded, but possible for custom/test payers with unknown state codes).
 */
public record PayerResponse(
    UUID id,
    String name,
    PayerType payerType,
    String state,
    String evvAggregator,
    LocalDateTime createdAt
) {}
```

- [ ] **Step 1.2: Verify compilation**

```bash
cd backend && mvn compile -q
```

Expected: no output (clean compile).

- [ ] **Step 1.3: Commit**

```bash
git add backend/src/main/java/com/hcare/api/v1/payers/
git commit -m "feat: add PayerResponse DTO"
```

---

### Task 2: Create PayerService

**Files:**
- Create: `backend/src/main/java/com/hcare/api/v1/payers/PayerService.java`

- [ ] **Step 2.1: Create PayerService**

Create `backend/src/main/java/com/hcare/api/v1/payers/PayerService.java`:

```java
package com.hcare.api.v1.payers;

import com.hcare.api.v1.payers.dto.PayerResponse;
import com.hcare.domain.Payer;
import com.hcare.domain.PayerRepository;
import com.hcare.evv.AggregatorType;
import com.hcare.evv.EvvStateConfig;
import com.hcare.evv.EvvStateConfigRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class PayerService {

    private final PayerRepository payerRepository;
    private final EvvStateConfigRepository evvStateConfigRepository;

    public PayerService(PayerRepository payerRepository,
                        EvvStateConfigRepository evvStateConfigRepository) {
        this.payerRepository = payerRepository;
        this.evvStateConfigRepository = evvStateConfigRepository;
    }

    @Transactional(readOnly = true)
    public Page<PayerResponse> listPayers(UUID agencyId, Pageable pageable) {
        // PayerRepository has findByAgencyId(agencyId) returning List — paginate manually
        // to avoid a separate count query on a typically small dataset (< 20 payers/agency).
        // Wrap in ArrayList so the list is mutable for sort; JPA may return a fixed-size list.
        List<Payer> all = new ArrayList<>(payerRepository.findByAgencyId(agencyId));
        all.sort(Comparator.comparing(Payer::getName, String.CASE_INSENSITIVE_ORDER));

        // Slice the raw list FIRST so toResponse is only called for payers on this page.
        // Mapping before paginating caused spurious state-config DB calls for payers that
        // would never appear in the response (and NPEs in tests for out-of-bounds pages).
        int start = (int) pageable.getOffset();
        int end   = Math.min(start + pageable.getPageSize(), all.size());
        List<Payer> slice = start >= all.size() ? List.of() : all.subList(start, end);

        // Cache keyed by Optional so that absent-config lookups are also stored.
        // HashMap.computeIfAbsent does not insert null-returning mapping functions,
        // so using Optional<EvvStateConfig> as the value type is required.
        Map<String, Optional<EvvStateConfig>> stateConfigCache = new HashMap<>();

        List<PayerResponse> page = slice.stream()
            .map(p -> toResponse(p, stateConfigCache))
            .toList();

        return new PageImpl<>(page, pageable, all.size());
    }

    @Transactional(readOnly = true)
    public PayerResponse getPayer(UUID payerId) {
        // Tenant isolation is enforced by the Hibernate agencyFilter (TenantFilterAspect).
        // Do NOT add a manual agencyId check here — service-layer tenant enforcement is
        // prohibited by architecture convention (see CLAUDE.md multi-tenancy section).
        Payer payer = payerRepository.findById(payerId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Payer not found: " + payerId));

        Map<String, Optional<EvvStateConfig>> cache = new HashMap<>();
        return toResponse(payer, cache);
    }

    private PayerResponse toResponse(Payer payer, Map<String, Optional<EvvStateConfig>> cache) {
        String aggregatorName = null;
        if (payer.getState() != null) {
            // computeIfAbsent with Optional value caches absent results too
            Optional<EvvStateConfig> configOpt = cache.computeIfAbsent(
                payer.getState(),
                evvStateConfigRepository::findByStateCode);
            if (configOpt.isPresent()) {
                AggregatorType aggregator = configOpt.get().getDefaultAggregator();
                aggregatorName = aggregator != null ? aggregator.name() : null;
            }
        }
        return new PayerResponse(
            payer.getId(),
            payer.getName(),
            payer.getPayerType(),
            payer.getState(),
            aggregatorName,
            payer.getCreatedAt()
        );
    }
}
```

- [ ] **Step 2.2: Verify compilation**

```bash
cd backend && mvn compile -q
```

Expected: no output (clean compile).

- [ ] **Step 2.3: Commit**

```bash
git add backend/src/main/java/com/hcare/api/v1/payers/PayerService.java
git commit -m "feat: add PayerService with EVV aggregator name resolution"
```

---

### Task 3: Create PayerController

**Files:**
- Create: `backend/src/main/java/com/hcare/api/v1/payers/PayerController.java`

- [ ] **Step 3.1: Create PayerController**

Create `backend/src/main/java/com/hcare/api/v1/payers/PayerController.java`:

```java
package com.hcare.api.v1.payers;

import com.hcare.api.v1.payers.dto.PayerResponse;
import com.hcare.security.UserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payers")
public class PayerController {

    private final PayerService payerService;

    public PayerController(PayerService payerService) {
        this.payerService = payerService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<Page<PayerResponse>> listPayers(
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable) {  // sort applied in service (case-insensitive by name)
        return ResponseEntity.ok(payerService.listPayers(principal.getAgencyId(), pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<PayerResponse> getPayer(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        return ResponseEntity.ok(payerService.getPayer(id));
    }
}
```

- [ ] **Step 3.2: Verify compilation and tests**

```bash
cd backend && mvn test -q 2>&1 | tail -5
```

Expected: all existing tests pass, 0 failures.

- [ ] **Step 3.3: Commit**

```bash
git add backend/src/main/java/com/hcare/api/v1/payers/PayerController.java
git commit -m "feat: add PayerController GET /api/v1/payers and GET /api/v1/payers/{id}"
```

---

### Task 3.5: Write PayerService Unit Tests

**Files:**
- Create: `backend/src/test/java/com/hcare/api/v1/payers/PayerServiceTest.java`

- [ ] **Step 3.5.1: Create PayerServiceTest**

Create `backend/src/test/java/com/hcare/api/v1/payers/PayerServiceTest.java`:

```java
package com.hcare.api.v1.payers;

import com.hcare.api.v1.payers.dto.PayerResponse;
import com.hcare.domain.Payer;
import com.hcare.domain.PayerRepository;
import com.hcare.domain.PayerType;
import com.hcare.evv.AggregatorType;
import com.hcare.evv.EvvStateConfig;
import com.hcare.evv.EvvStateConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayerServiceTest {

    @Mock private PayerRepository payerRepository;
    @Mock private EvvStateConfigRepository evvStateConfigRepository;

    private PayerService service;

    @BeforeEach
    void setUp() {
        service = new PayerService(payerRepository, evvStateConfigRepository);
    }

    @Test
    void listPayers_returnsMappedPageWithEvvAggregator() {
        UUID agencyId = UUID.randomUUID();
        Payer payer = buildPayer(agencyId, "TX", PayerType.MEDICAID);
        EvvStateConfig config = buildStateConfig("TX", AggregatorType.SANDATA);

        when(payerRepository.findByAgencyId(agencyId)).thenReturn(List.of(payer));
        when(evvStateConfigRepository.findByStateCode("TX")).thenReturn(Optional.of(config));

        Page<PayerResponse> result = service.listPayers(agencyId, PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(1);
        PayerResponse row = result.getContent().get(0);
        assertThat(row.evvAggregator()).isEqualTo("SANDATA");
        assertThat(row.state()).isEqualTo("TX");
    }

    @Test
    void listPayers_nullEvvAggregatorWhenNoStateConfig() {
        UUID agencyId = UUID.randomUUID();
        Payer payer = buildPayer(agencyId, "ZZ", PayerType.PRIVATE_PAY);

        when(payerRepository.findByAgencyId(agencyId)).thenReturn(List.of(payer));
        when(evvStateConfigRepository.findByStateCode("ZZ")).thenReturn(Optional.empty());

        Page<PayerResponse> result = service.listPayers(agencyId, PageRequest.of(0, 20));

        assertThat(result.getContent().get(0).evvAggregator()).isNull();
    }

    @Test
    void listPayers_stateConfigCachedAcrossMultiplePayersSameState() {
        UUID agencyId = UUID.randomUUID();
        Payer p1 = buildPayer(agencyId, "TX", PayerType.MEDICAID);
        Payer p2 = buildPayer(agencyId, "TX", PayerType.MEDICARE);
        EvvStateConfig config = buildStateConfig("TX", AggregatorType.SANDATA);

        when(payerRepository.findByAgencyId(agencyId)).thenReturn(List.of(p1, p2));
        when(evvStateConfigRepository.findByStateCode("TX")).thenReturn(Optional.of(config));

        service.listPayers(agencyId, PageRequest.of(0, 20));

        // Should only hit the repo once for "TX" despite two payers
        verify(evvStateConfigRepository, times(1)).findByStateCode("TX");
    }

    @Test
    void listPayers_unknownStateConfigCachedAcrossMultiplePayers() {
        // Absent (Optional.empty) results must also be cached so the repo is not called
        // once per payer when multiple payers share the same unknown state code.
        UUID agencyId = UUID.randomUUID();
        Payer p1 = buildPayer(agencyId, "ZZ", PayerType.PRIVATE_PAY);
        Payer p2 = buildPayer(agencyId, "ZZ", PayerType.MEDICAID);

        when(payerRepository.findByAgencyId(agencyId)).thenReturn(List.of(p1, p2));
        when(evvStateConfigRepository.findByStateCode("ZZ")).thenReturn(Optional.empty());

        Page<PayerResponse> result = service.listPayers(agencyId, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent()).allMatch(r -> r.evvAggregator() == null);
        // Absent result must be cached — repo called only once for "ZZ" despite two payers
        verify(evvStateConfigRepository, times(1)).findByStateCode("ZZ");
    }

    @Test
    void listPayers_returnsEmptyPageWhenOutOfBounds() {
        UUID agencyId = UUID.randomUUID();
        when(payerRepository.findByAgencyId(agencyId)).thenReturn(List.of(buildPayer(agencyId, "TX", PayerType.MEDICAID)));

        Page<PayerResponse> result = service.listPayers(agencyId, PageRequest.of(5, 20));

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(1);
        // No evvStateConfigRepository stub needed — the service slices the raw List<Payer>
        // before calling toResponse, so toResponse is never called for out-of-bounds pages.
    }

    @Test
    void getPayer_returnsResponseWhenFound() {
        UUID payerId = UUID.randomUUID();
        Payer payer = buildPayer(UUID.randomUUID(), "NY", PayerType.MEDICAID);

        when(payerRepository.findById(payerId)).thenReturn(Optional.of(payer));
        when(evvStateConfigRepository.findByStateCode("NY")).thenReturn(Optional.empty());

        PayerResponse result = service.getPayer(payerId);

        assertThat(result.state()).isEqualTo("NY");
    }

    @Test
    void getPayer_throwsNotFoundWhenMissing() {
        UUID payerId = UUID.randomUUID();
        when(payerRepository.findById(payerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPayer(payerId))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // --- helpers ---

    private Payer buildPayer(UUID agencyId, String state, PayerType type) {
        Payer p = new Payer();
        p.setId(UUID.randomUUID());
        p.setAgencyId(agencyId);
        p.setName("Test Payer");
        p.setState(state);
        p.setPayerType(type);
        p.setCreatedAt(LocalDateTime.now());
        return p;
    }

    private EvvStateConfig buildStateConfig(String stateCode, AggregatorType aggregator) {
        EvvStateConfig c = new EvvStateConfig();
        c.setStateCode(stateCode);
        c.setDefaultAggregator(aggregator);
        return c;
    }
}
```

- [ ] **Step 3.5.2: Run tests**

```bash
cd backend && mvn test -Dtest=PayerServiceTest -q 2>&1 | tail -10
```

Expected: 6 tests pass, 0 failures.

- [ ] **Step 3.5.3: Commit**

```bash
git add backend/src/test/java/com/hcare/api/v1/payers/PayerServiceTest.java
git commit -m "test: add PayerService unit tests"
```

---

### Task 4: Create EVV History DTOs

**Files:**
- Create: `backend/src/main/java/com/hcare/api/v1/evv/dto/EvvHistoryRow.java`

- [ ] **Step 4.1: Create EvvHistoryRow**

Create `backend/src/main/java/com/hcare/api/v1/evv/dto/EvvHistoryRow.java`:

```java
package com.hcare.api.v1.evv.dto;

import com.hcare.evv.EvvComplianceStatus;
import com.hcare.evv.VerificationMethod;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Denormalized EVV history row — one row per shift.
 * Client and caregiver names are resolved at query time from their respective entities.
 * EVV status is computed on read via EvvComplianceService — never stored.
 *
 * <p>{@code evvStatusReason} carries a human-readable explanation only when the
 * status computation could not proceed (e.g. missing state config, unknown client,
 * agency state not configured). It is {@code null} when {@code EvvComplianceService.compute()}
 * runs successfully — the status enum itself is the authoritative signal in that case.
 */
public record EvvHistoryRow(
    UUID shiftId,
    String clientFirstName,
    String clientLastName,
    String caregiverFirstName,
    String caregiverLastName,
    String serviceTypeName,
    LocalDateTime scheduledStart,
    LocalDateTime scheduledEnd,
    EvvComplianceStatus evvStatus,
    String evvStatusReason,
    LocalDateTime timeIn,
    LocalDateTime timeOut,
    VerificationMethod verificationMethod
) {}
```

- [ ] **Step 4.2: Verify compilation**

```bash
cd backend && mvn compile -q
```

Expected: no output (clean compile).

- [ ] **Step 4.3: Commit**

```bash
git add backend/src/main/java/com/hcare/api/v1/evv/
git commit -m "feat: add EvvHistoryRow DTO"
```

---

### Task 5: Create EvvHistoryService

**Files:**
- Create: `backend/src/main/java/com/hcare/api/v1/evv/EvvHistoryService.java`

- [ ] **Step 5.1: Create EvvHistoryService**

Create `backend/src/main/java/com/hcare/api/v1/evv/EvvHistoryService.java`:

```java
package com.hcare.api.v1.evv;

import com.hcare.api.v1.evv.dto.EvvHistoryRow;
import com.hcare.domain.Agency;
import com.hcare.domain.AgencyRepository;
import com.hcare.domain.Authorization;
import com.hcare.domain.AuthorizationRepository;
import com.hcare.domain.Caregiver;
import com.hcare.domain.CaregiverRepository;
import com.hcare.domain.Client;
import com.hcare.domain.ClientRepository;
import com.hcare.domain.EvvRecord;
import com.hcare.domain.EvvRecordRepository;
import com.hcare.domain.Payer;
import com.hcare.domain.PayerRepository;
import com.hcare.domain.PayerType;
import com.hcare.domain.ServiceType;
import com.hcare.domain.ServiceTypeRepository;
import com.hcare.domain.Shift;
import com.hcare.domain.ShiftRepository;
import com.hcare.evv.AggregatorType;
import com.hcare.evv.EvvComplianceService;
import com.hcare.evv.EvvComplianceStatus;
import com.hcare.evv.EvvStateConfig;
import com.hcare.evv.EvvStateConfigRepository;
import com.hcare.evv.VerificationMethod;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class EvvHistoryService {

    private final ShiftRepository shiftRepository;
    private final ClientRepository clientRepository;
    private final CaregiverRepository caregiverRepository;
    private final ServiceTypeRepository serviceTypeRepository;
    private final EvvRecordRepository evvRecordRepository;
    private final EvvStateConfigRepository evvStateConfigRepository;
    private final AuthorizationRepository authorizationRepository;
    private final PayerRepository payerRepository;
    private final AgencyRepository agencyRepository;
    private final EvvComplianceService evvComplianceService;

    public EvvHistoryService(
            ShiftRepository shiftRepository,
            ClientRepository clientRepository,
            CaregiverRepository caregiverRepository,
            ServiceTypeRepository serviceTypeRepository,
            EvvRecordRepository evvRecordRepository,
            EvvStateConfigRepository evvStateConfigRepository,
            AuthorizationRepository authorizationRepository,
            PayerRepository payerRepository,
            AgencyRepository agencyRepository,
            EvvComplianceService evvComplianceService) {
        this.shiftRepository = shiftRepository;
        this.clientRepository = clientRepository;
        this.caregiverRepository = caregiverRepository;
        this.serviceTypeRepository = serviceTypeRepository;
        this.evvRecordRepository = evvRecordRepository;
        this.evvStateConfigRepository = evvStateConfigRepository;
        this.authorizationRepository = authorizationRepository;
        this.payerRepository = payerRepository;
        this.agencyRepository = agencyRepository;
        this.evvComplianceService = evvComplianceService;
    }

    @Transactional(readOnly = true)
    public Page<EvvHistoryRow> getHistory(UUID agencyId,
                                           LocalDateTime start,
                                           LocalDateTime end,
                                           Pageable pageable) {
        // Always sort by scheduledStart DESC. Forwarding client-supplied sort fields to the
        // JPA repository risks PropertyReferenceException for non-Shift column names (500
        // instead of 400). The frontend never passes a sort param, so the flexibility is unused.
        Page<Shift> shiftPage = shiftRepository.findByAgencyIdAndScheduledStartBetween(
            agencyId, start, end,
            PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by("scheduledStart").descending()));

        List<Shift> shifts = shiftPage.getContent();

        if (shifts.isEmpty()) {
            return Page.empty(pageable);
        }

        // Resolve the agency's own state as a fallback for clients without serviceState override
        String agencyState = agencyRepository.findById(agencyId)
            .map(Agency::getState)
            .orElse(null);

        // Build lookup maps — fetch only the IDs present on this page, not all agency entities
        Set<UUID> clientIds = shifts.stream().map(Shift::getClientId)
            .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<UUID> caregiverIds = shifts.stream().map(Shift::getCaregiverId)
            .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<UUID> serviceTypeIds = shifts.stream().map(Shift::getServiceTypeId)
            .filter(Objects::nonNull).collect(Collectors.toSet());

        Map<UUID, Client> clientMap = clientRepository.findAllById(clientIds).stream()
            .collect(Collectors.toMap(Client::getId, c -> c));

        Map<UUID, Caregiver> caregiverMap = caregiverRepository.findAllById(caregiverIds).stream()
            .collect(Collectors.toMap(Caregiver::getId, c -> c));

        Map<UUID, ServiceType> serviceTypeMap = serviceTypeRepository.findAllById(serviceTypeIds).stream()
            .collect(Collectors.toMap(ServiceType::getId, s -> s));

        // Fetch EVV records for these shifts
        Set<UUID> shiftIds = shifts.stream().map(Shift::getId).collect(Collectors.toSet());
        Map<UUID, EvvRecord> evvByShiftId = evvRecordRepository.findByShiftIdIn(shiftIds).stream()
            .collect(Collectors.toMap(EvvRecord::getShiftId, r -> r));

        // Authorization and payer lookup — only auth IDs referenced by this page's shifts
        Set<UUID> authIds = shifts.stream().map(Shift::getAuthorizationId)
            .filter(Objects::nonNull).collect(Collectors.toSet());

        Map<UUID, Authorization> authById = authorizationRepository.findAllById(authIds).stream()
            .collect(Collectors.toMap(Authorization::getId, a -> a));

        Set<UUID> payerIds = authById.values().stream().map(Authorization::getPayerId)
            .filter(Objects::nonNull).collect(Collectors.toSet());

        Map<UUID, Payer> payerById = payerRepository.findAllById(payerIds).stream()
            .collect(Collectors.toMap(Payer::getId, p -> p));

        // EVV state config cache — keyed by Optional so that absent-config lookups are also
        // stored (HashMap.computeIfAbsent does not insert null-returning mapping functions).
        Map<String, Optional<EvvStateConfig>> stateConfigCache = new HashMap<>();

        List<EvvHistoryRow> rows = shifts.stream().map(shift -> {
            Client client = clientMap.get(shift.getClientId());
            Caregiver caregiver = shift.getCaregiverId() != null
                ? caregiverMap.get(shift.getCaregiverId()) : null;
            ServiceType serviceType = serviceTypeMap.get(shift.getServiceTypeId());
            EvvRecord evvRecord = evvByShiftId.get(shift.getId());

            // Resolve payer type
            PayerType payerType = null;
            if (shift.getAuthorizationId() != null) {
                Authorization auth = authById.get(shift.getAuthorizationId());
                if (auth != null) {
                    Payer payer = payerById.get(auth.getPayerId());
                    if (payer != null) {
                        payerType = payer.getPayerType();
                    }
                }
            }

            // Compute EVV compliance status
            EvvComplianceStatus status = EvvComplianceStatus.GREY;
            String statusReason = null;

            String effectiveState = null;
            if (client != null) {
                effectiveState = client.getServiceState() != null
                    ? client.getServiceState()
                    : agencyState;
            }
            if (effectiveState != null) {
                Optional<EvvStateConfig> stateConfigOpt = stateConfigCache.computeIfAbsent(
                    effectiveState,
                    evvStateConfigRepository::findByStateCode);
                if (stateConfigOpt.isPresent()) {
                    status = evvComplianceService.compute(
                        evvRecord, stateConfigOpt.get(), shift, payerType,
                        client.getLat(), client.getLng());
                } else {
                    statusReason = "No EVV state config for state: " + effectiveState;
                }
            } else {
                statusReason = client == null ? "Client not found" : "Agency state not configured";
            }

            return new EvvHistoryRow(
                shift.getId(),
                client != null ? client.getFirstName() : "Unknown",
                client != null ? client.getLastName() : "Client",
                caregiver != null ? caregiver.getFirstName() : null,
                caregiver != null ? caregiver.getLastName() : null,
                serviceType != null ? serviceType.getName() : null,
                shift.getScheduledStart(),
                shift.getScheduledEnd(),
                status,
                statusReason,
                evvRecord != null ? evvRecord.getTimeIn() : null,
                evvRecord != null ? evvRecord.getTimeOut() : null,
                evvRecord != null ? evvRecord.getVerificationMethod() : null
            );
        }).toList();

        return new PageImpl<>(rows, pageable, shiftPage.getTotalElements());
    }
}
```

- [ ] **Step 5.2: Verify compilation**

```bash
cd backend && mvn compile -q
```

Expected: no output (clean compile).

- [ ] **Step 5.3: Commit**

```bash
git add backend/src/main/java/com/hcare/api/v1/evv/EvvHistoryService.java
git commit -m "feat: add EvvHistoryService — denormalized EVV history with computed compliance status"
```

---

### Task 5.5: Write EvvHistoryService Unit Tests

**Files:**
- Create: `backend/src/test/java/com/hcare/api/v1/evv/EvvHistoryServiceTest.java`

- [ ] **Step 5.5.1: Create EvvHistoryServiceTest**

Create `backend/src/test/java/com/hcare/api/v1/evv/EvvHistoryServiceTest.java`:

```java
package com.hcare.api.v1.evv;

import com.hcare.api.v1.evv.dto.EvvHistoryRow;
import com.hcare.domain.*;
import com.hcare.evv.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EvvHistoryServiceTest {

    @Mock private ShiftRepository shiftRepository;
    @Mock private ClientRepository clientRepository;
    @Mock private CaregiverRepository caregiverRepository;
    @Mock private ServiceTypeRepository serviceTypeRepository;
    @Mock private EvvRecordRepository evvRecordRepository;
    @Mock private EvvStateConfigRepository evvStateConfigRepository;
    @Mock private AuthorizationRepository authorizationRepository;
    @Mock private PayerRepository payerRepository;
    @Mock private AgencyRepository agencyRepository;
    @Mock private EvvComplianceService evvComplianceService;

    private EvvHistoryService service;

    private final UUID agencyId = UUID.randomUUID();
    private final LocalDateTime start = LocalDateTime.of(2026, 4, 1, 0, 0);
    private final LocalDateTime end   = LocalDateTime.of(2026, 4, 30, 23, 59, 59);

    @BeforeEach
    void setUp() {
        service = new EvvHistoryService(shiftRepository, clientRepository, caregiverRepository,
            serviceTypeRepository, evvRecordRepository, evvStateConfigRepository,
            authorizationRepository, payerRepository, agencyRepository, evvComplianceService);
    }

    @Test
    void getHistory_returnsEmptyPageWhenNoShifts() {
        when(shiftRepository.findByAgencyIdAndScheduledStartBetween(
            eq(agencyId), eq(start), eq(end), any(Pageable.class)))
            .thenReturn(Page.empty());

        Page<EvvHistoryRow> result = service.getHistory(agencyId, start, end, PageRequest.of(0, 50));

        assertThat(result.getContent()).isEmpty();
        verifyNoInteractions(clientRepository, caregiverRepository, evvRecordRepository);
    }

    @Test
    void getHistory_computesGreyStatusWhenNoEvvRecord() {
        Client client = buildClient("TX");
        Shift shift = buildShift(client.getId(), null, null);
        EvvStateConfig stateConfig = buildStateConfig("TX");

        stubShiftsPage(shift);
        when(agencyRepository.findById(agencyId)).thenReturn(Optional.of(buildAgency("TX")));
        when(clientRepository.findAllById(any())).thenReturn(List.of(client));
        when(caregiverRepository.findAllById(any())).thenReturn(List.of());
        when(serviceTypeRepository.findAllById(any())).thenReturn(List.of());
        when(evvRecordRepository.findByShiftIdIn(any())).thenReturn(List.of());
        when(authorizationRepository.findAllById(any())).thenReturn(List.of());
        when(payerRepository.findAllById(any())).thenReturn(List.of());
        when(evvStateConfigRepository.findByStateCode("TX")).thenReturn(Optional.of(stateConfig));
        when(evvComplianceService.compute(isNull(), eq(stateConfig), eq(shift), isNull(), any(), any()))
            .thenReturn(EvvComplianceStatus.GREY);

        Page<EvvHistoryRow> result = service.getHistory(agencyId, start, end, PageRequest.of(0, 50));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).evvStatus()).isEqualTo(EvvComplianceStatus.GREY);
    }

    @Test
    void getHistory_setsStatusReasonWhenNoStateConfig() {
        Client client = buildClient("ZZ"); // unknown state
        Shift shift = buildShift(client.getId(), null, null);

        stubShiftsPage(shift);
        when(agencyRepository.findById(agencyId)).thenReturn(Optional.of(buildAgency("ZZ")));
        when(clientRepository.findAllById(any())).thenReturn(List.of(client));
        when(caregiverRepository.findAllById(any())).thenReturn(List.of());
        when(serviceTypeRepository.findAllById(any())).thenReturn(List.of());
        when(evvRecordRepository.findByShiftIdIn(any())).thenReturn(List.of());
        when(authorizationRepository.findAllById(any())).thenReturn(List.of());
        when(payerRepository.findAllById(any())).thenReturn(List.of());
        when(evvStateConfigRepository.findByStateCode("ZZ")).thenReturn(Optional.empty());

        Page<EvvHistoryRow> result = service.getHistory(agencyId, start, end, PageRequest.of(0, 50));

        EvvHistoryRow row = result.getContent().get(0);
        assertThat(row.evvStatus()).isEqualTo(EvvComplianceStatus.GREY);
        assertThat(row.evvStatusReason()).contains("No EVV state config for state: ZZ");
    }

    @Test
    void getHistory_clientServiceStateOverridesAgencyState() {
        Client client = buildClient("NY"); // serviceState overrides agency's TX
        Shift shift = buildShift(client.getId(), null, null);
        EvvStateConfig nyConfig = buildStateConfig("NY");

        stubShiftsPage(shift);
        when(agencyRepository.findById(agencyId)).thenReturn(Optional.of(buildAgency("TX")));
        when(clientRepository.findAllById(any())).thenReturn(List.of(client));
        when(caregiverRepository.findAllById(any())).thenReturn(List.of());
        when(serviceTypeRepository.findAllById(any())).thenReturn(List.of());
        when(evvRecordRepository.findByShiftIdIn(any())).thenReturn(List.of());
        when(authorizationRepository.findAllById(any())).thenReturn(List.of());
        when(payerRepository.findAllById(any())).thenReturn(List.of());
        when(evvStateConfigRepository.findByStateCode("NY")).thenReturn(Optional.of(nyConfig));
        when(evvComplianceService.compute(any(), eq(nyConfig), any(), any(), any(), any()))
            .thenReturn(EvvComplianceStatus.GREEN);

        service.getHistory(agencyId, start, end, PageRequest.of(0, 50));

        verify(evvStateConfigRepository).findByStateCode("NY");
        verify(evvStateConfigRepository, never()).findByStateCode("TX");
    }

    // --- helpers ---

    private void stubShiftsPage(Shift... shifts) {
        Page<Shift> page = new PageImpl<>(List.of(shifts), PageRequest.of(0, 50), shifts.length);
        when(shiftRepository.findByAgencyIdAndScheduledStartBetween(
            eq(agencyId), eq(start), eq(end), any(Pageable.class))).thenReturn(page);
    }

    private Shift buildShift(UUID clientId, UUID authorizationId, UUID caregiverId) {
        Shift s = new Shift();
        s.setId(UUID.randomUUID());
        s.setClientId(clientId);
        s.setCaregiverId(caregiverId);
        s.setServiceTypeId(UUID.randomUUID());
        s.setAuthorizationId(authorizationId);
        s.setScheduledStart(LocalDateTime.of(2026, 4, 10, 9, 0));
        s.setScheduledEnd(LocalDateTime.of(2026, 4, 10, 13, 0));
        return s;
    }

    private Client buildClient(String serviceState) {
        Client c = new Client();
        c.setId(UUID.randomUUID());
        c.setFirstName("Jane");
        c.setLastName("Doe");
        c.setServiceState(serviceState);
        return c;
    }

    private Agency buildAgency(String state) {
        Agency a = new Agency();
        a.setId(agencyId);
        a.setState(state);
        return a;
    }

    private EvvStateConfig buildStateConfig(String stateCode) {
        EvvStateConfig c = new EvvStateConfig();
        c.setStateCode(stateCode);
        c.setDefaultAggregator(AggregatorType.SANDATA);
        return c;
    }
}
```

- [ ] **Step 5.5.2: Run tests**

```bash
cd backend && mvn test -Dtest=EvvHistoryServiceTest -q 2>&1 | tail -10
```

Expected: 4 tests pass, 0 failures.

- [ ] **Step 5.5.3: Commit**

```bash
git add backend/src/test/java/com/hcare/api/v1/evv/EvvHistoryServiceTest.java
git commit -m "test: add EvvHistoryService unit tests"
```

---

### Task 6: Create EvvHistoryController

**Files:**
- Create: `backend/src/main/java/com/hcare/api/v1/evv/EvvHistoryController.java`

- [ ] **Step 6.1: Create EvvHistoryController**

Create `backend/src/main/java/com/hcare/api/v1/evv/EvvHistoryController.java`:

```java
package com.hcare.api.v1.evv;

import com.hcare.api.v1.evv.dto.EvvHistoryRow;
import com.hcare.security.UserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/evv")
public class EvvHistoryController {

    private final EvvHistoryService evvHistoryService;

    public EvvHistoryController(EvvHistoryService evvHistoryService) {
        this.evvHistoryService = evvHistoryService;
    }

    /**
     * Returns EVV compliance history for shifts whose scheduledStart falls within [start, end).
     *
     * <p>Example: GET /api/v1/evv/history?start=2026-04-01T00:00:00&end=2026-04-30T23:59:59
     *
     * @param start ISO-8601 LocalDateTime (no timezone suffix) — inclusive lower bound
     * @param end   ISO-8601 LocalDateTime (no timezone suffix) — exclusive upper bound
     */
    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<Page<EvvHistoryRow>> getHistory(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @PageableDefault(size = 50) Pageable pageable) { // default sort: scheduledStart DESC (applied in service)
        if (!end.isAfter(start)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "end must be after start");
        }
        if (java.time.temporal.ChronoUnit.DAYS.between(start, end) > 366) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Date range must not exceed 366 days");
        }
        return ResponseEntity.ok(
            evvHistoryService.getHistory(principal.getAgencyId(), start, end, pageable));
    }
}
```

- [ ] **Step 6.2: Verify compilation and tests**

```bash
cd backend && mvn test -q 2>&1 | tail -5
```

Expected: all existing tests pass, 0 failures.

- [ ] **Step 6.3: Commit**

```bash
git add backend/src/main/java/com/hcare/api/v1/evv/EvvHistoryController.java
git commit -m "feat: add EvvHistoryController GET /api/v1/evv/history"
```

---

### Task 7: Create Frontend Payers API and Hook

**Files:**
- Create: `frontend/src/api/payers.ts`
- Create: `frontend/src/hooks/usePayers.ts`

- [ ] **Step 7.1: Create payers.ts**

Create `frontend/src/api/payers.ts`:

```ts
import { apiClient } from './client'
import type { PayerResponse, PageResponse } from '../types/api'

export async function listPayers(page = 0, size = 20): Promise<PageResponse<PayerResponse>> {
  const response = await apiClient.get<PageResponse<PayerResponse>>('/payers', {
    params: { page, size, sort: 'name' },
  })
  return response.data
}

export async function getPayer(id: string): Promise<PayerResponse> {
  const response = await apiClient.get<PayerResponse>(`/payers/${id}`)
  return response.data
}
```

- [ ] **Step 7.2: Confirm PayerResponse type exists in types/api.ts**

Confirm `PayerResponse` is already present in `types/api.ts` (added in a prior phase). If somehow missing, add:

```ts
// Add to frontend/src/types/api.ts only if somehow missing

export type PayerType = 'MEDICAID' | 'PRIVATE_PAY' | 'LTC_INSURANCE' | 'VA' | 'MEDICARE'

export interface PayerResponse {
  id: string
  name: string
  payerType: PayerType
  state: string
  evvAggregator: string | null
  createdAt: string
}
```

- [ ] **Step 7.3: Create usePayers.ts**

Create `frontend/src/hooks/usePayers.ts`:

```ts
import { useQuery } from '@tanstack/react-query'
import { listPayers } from '../api/payers'

export function usePayers(page = 0, size = 20) {
  const query = useQuery({
    queryKey: ['payers', page, size],
    queryFn: () => listPayers(page, size),
    staleTime: 120_000,
  })

  return {
    ...query,
    payers: query.data?.content ?? [],
    totalPages: query.data?.totalPages ?? 0,
    totalElements: query.data?.totalElements ?? 0,
  }
}
```

- [ ] **Step 7.4: Commit**

```bash
cd frontend && git add src/api/payers.ts src/hooks/usePayers.ts src/types/api.ts
git commit -m "feat: add payers API module, usePayers hook, and PayerResponse type"
```

---

### Task 8: Update PayersPage to Use Real Data

**Files:**
- Modify: `frontend/src/components/payers/PayersPage.tsx`

- [ ] **Step 8.1: Update PayersPage**

Replace the full contents of `frontend/src/components/payers/PayersPage.tsx`:

```tsx
import { useState } from 'react'
import { usePayers } from '../../hooks/usePayers'
import type { PayerResponse } from '../../types/api'

const PAYER_TYPE_LABELS: Record<string, string> = {
  MEDICAID: 'Medicaid',
  PRIVATE_PAY: 'Private Pay',
  LTC_INSURANCE: 'LTC Insurance',
  VA: 'VA',
  MEDICARE: 'Medicare',
}

function PayerRow({ payer }: { payer: PayerResponse }) {
  return (
    <div className="flex items-center justify-between px-4 py-3 bg-white border border-border rounded">
      <div>
        <p className="text-sm font-medium text-dark">
          {payer.name}
        </p>
        <p className="text-xs mt-0.5 text-text-secondary">
          {PAYER_TYPE_LABELS[payer.payerType] ?? payer.payerType}
          {' · '}
          {payer.state}
        </p>
      </div>
      <div className="text-right">
        {payer.evvAggregator ? (
          <span className="inline-block text-xs px-2 py-0.5 rounded bg-surface text-text-secondary border border-border">
            {payer.evvAggregator}
          </span>
        ) : (
          <span className="text-xs text-text-muted">No EVV aggregator</span>
        )}
      </div>
    </div>
  )
}

export function PayersPage() {
  const [page, setPage] = useState(0)
  const { payers, isLoading, isError, totalPages, totalElements } = usePayers(page, 20)

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-full bg-surface">
        <span className="text-sm text-text-muted">Loading payers…</span>
      </div>
    )
  }

  if (isError) {
    return (
      <div className="flex items-center justify-center h-full bg-surface">
        <p className="text-sm text-red-600">Failed to load payers.</p>
      </div>
    )
  }

  return (
    <div className="flex flex-col h-full bg-surface">
      {/* Header */}
      <div className="flex items-center justify-between px-6 py-4 bg-white border-b border-border">
        <div>
          <h1 className="text-lg font-semibold text-dark">Payers</h1>
          <p className="text-xs mt-0.5 text-text-muted">
            {totalElements} total
          </p>
        </div>
      </div>

      {/* List */}
      <div className="flex-1 overflow-auto p-6">
        {payers.length === 0 ? (
          <div className="flex items-center justify-center h-32">
            <p className="text-sm text-text-muted">No payers configured yet.</p>
          </div>
        ) : (
          <div className="space-y-2">
            {payers.map((payer) => (
              <PayerRow key={payer.id} payer={payer} />
            ))}
          </div>
        )}

        {/* Pagination */}
        {totalPages > 1 && (
          <div className="flex items-center justify-end gap-2 mt-4">
            <button
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              disabled={page === 0}
              className="px-3 py-1.5 text-sm disabled:opacity-40 border border-border text-text-secondary"
            >
              Prev
            </button>
            <span className="text-sm text-text-secondary">
              Page {page + 1} of {totalPages}
            </span>
            <button
              onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
              disabled={page === totalPages - 1}
              className="px-3 py-1.5 text-sm disabled:opacity-40 border border-border text-text-secondary"
            >
              Next
            </button>
          </div>
        )}
      </div>
    </div>
  )
}
```

- [ ] **Step 8.2: Commit**

```bash
cd frontend && git add src/components/payers/PayersPage.tsx
git commit -m "feat: wire PayersPage to real API via usePayers"
```

---

### Task 9: Create Frontend EVV API and Hook

**Files:**
- Create: `frontend/src/api/evv.ts`
- Create: `frontend/src/hooks/useEvvHistory.ts`

- [ ] **Step 9.1: Fix EvvHistoryRow type in types/api.ts**

Open `frontend/src/types/api.ts` and **replace** the existing `EvvHistoryRow` interface (it already exists from a prior phase but has incorrect nullability on `serviceTypeName`). The correct definition is:

```ts
// Replace the existing EvvHistoryRow interface in frontend/src/types/api.ts

export interface EvvHistoryRow {
  shiftId: string
  clientFirstName: string
  clientLastName: string
  caregiverFirstName: string | null
  caregiverLastName: string | null
  serviceTypeName: string | null
  scheduledStart: string // ISO-8601 LocalDateTime
  scheduledEnd: string
  evvStatus: EvvComplianceStatus
  evvStatusReason: string | null
  timeIn: string | null
  timeOut: string | null
  verificationMethod: string | null
}
```

- [ ] **Step 9.2: Create evv.ts**

Create `frontend/src/api/evv.ts`:

```ts
import { apiClient } from './client'
import type { EvvHistoryRow, PageResponse } from '../types/api'

export async function listEvvHistory(
  start: string,
  end: string,
  page = 0,
  size = 50,
): Promise<PageResponse<EvvHistoryRow>> {
  const response = await apiClient.get<PageResponse<EvvHistoryRow>>('/evv/history', {
    params: { start, end, page, size },
  })
  return response.data
}
```

- [ ] **Step 9.3: Create useEvvHistory.ts**

Create `frontend/src/hooks/useEvvHistory.ts`:

```ts
import { useQuery } from '@tanstack/react-query'
import { listEvvHistory } from '../api/evv'

export function useEvvHistory(start: string, end: string, page = 0) {
  const query = useQuery({
    queryKey: ['evv-history', start, end, page],
    queryFn: () => listEvvHistory(start, end, page, 50),
    enabled: Boolean(start && end),
    staleTime: 30_000,
  })

  return {
    ...query,
    rows: query.data?.content ?? [],
    totalPages: query.data?.totalPages ?? 0,
    totalElements: query.data?.totalElements ?? 0,
  }
}
```

- [ ] **Step 9.4: Commit**

```bash
cd frontend && git add src/api/evv.ts src/hooks/useEvvHistory.ts src/types/api.ts
git commit -m "feat: add EVV history API module, useEvvHistory hook, and EvvHistoryRow type"
```

---

### Task 10: Update EvvStatusPage to Use Real Data

**Files:**
- Modify: `frontend/src/components/evv/EvvStatusPage.tsx`

- [ ] **Step 10.1: Update EvvStatusPage**

Replace the full contents of `frontend/src/components/evv/EvvStatusPage.tsx`:

```tsx
import { useState, useMemo } from 'react'
import { useEvvHistory } from '../../hooks/useEvvHistory'
import type { EvvComplianceStatus, EvvHistoryRow } from '../../types/api'

const EVV_COLORS: Record<EvvComplianceStatus, string> = {
  GREEN: '#16a34a',
  YELLOW: '#ca8a04',
  RED: '#dc2626',
  GREY: '#94a3b8',
  EXEMPT: '#94a3b8',
  PORTAL_SUBMIT: '#94a3b8',
}

const STATUS_ORDER: EvvComplianceStatus[] = [
  'RED', 'YELLOW', 'PORTAL_SUBMIT', 'GREY', 'GREEN', 'EXEMPT',
]

function EvvStatusBadge({ status }: { status: EvvComplianceStatus }) {
  return (
    <span
      className="inline-block text-xs font-semibold px-2 py-0.5 rounded text-white"
      style={{ backgroundColor: EVV_COLORS[status] ?? '#94a3b8' }}
    >
      {status}
    </span>
  )
}

function EvvRow({ row }: { row: EvvHistoryRow }) {
  const clientName = `${row.clientFirstName} ${row.clientLastName}`
  const caregiverName =
    row.caregiverFirstName
      ? `${row.caregiverFirstName} ${row.caregiverLastName ?? ''}`
      : 'Unassigned'

  return (
    <tr className="border-b border-border">
      <td className="px-4 py-3 text-sm text-dark">
        {clientName}
      </td>
      <td className="px-4 py-3 text-sm text-text-secondary">
        {caregiverName}
      </td>
      <td className="px-4 py-3 text-sm text-text-secondary">
        {row.serviceTypeName ?? '—'}
      </td>
      <td className="px-4 py-3 text-sm text-text-secondary">
        {new Date(row.scheduledStart).toLocaleDateString('en-US', {
          month: 'short',
          day: 'numeric',
        })}
        {' '}
        {new Date(row.scheduledStart).toLocaleTimeString('en-US', {
          hour: 'numeric',
          minute: '2-digit',
        })}
      </td>
      <td className="px-4 py-3">
        <EvvStatusBadge status={row.evvStatus} />
        {row.evvStatusReason && (
          <p className="text-xs mt-0.5 text-text-muted">
            {row.evvStatusReason}
          </p>
        )}
      </td>
      <td className="px-4 py-3 text-xs text-text-secondary">
        {row.timeIn
          ? new Date(row.timeIn).toLocaleTimeString('en-US', {
              hour: 'numeric',
              minute: '2-digit',
            })
          : '—'}
        {' / '}
        {row.timeOut
          ? new Date(row.timeOut).toLocaleTimeString('en-US', {
              hour: 'numeric',
              minute: '2-digit',
            })
          : '—'}
      </td>
      <td className="px-4 py-3 text-xs text-text-secondary">
        {row.verificationMethod ?? '—'}
      </td>
    </tr>
  )
}

// Format a Date to ISO-8601 LocalDateTime for API params (no timezone suffix).
// d.toISOString() returns UTC — callers must ensure d is constructed from local-time
// parts (new Date(y, m, day, h, min, sec)) so that UTC conversion is correct.
function toLocalDateTime(d: Date): string {
  return d.toISOString().replace('Z', '').replace(/\.\d+$/, '')
}

// Parse a date-input value ('YYYY-MM-DD') as local midnight.
// new Date('YYYY-MM-DD') is UTC midnight per the ECMA-262 spec, which is wrong for
// non-UTC users. new Date(y, m, d) always gives local midnight.
function parseDateInputAsLocal(value: string): Date {
  const [y, m, d] = value.split('-').map(Number)
  return new Date(y, m - 1, d)
}

export function EvvStatusPage() {
  // Default: current month
  const today = new Date()
  const defaultStart = new Date(today.getFullYear(), today.getMonth(), 1)
  const defaultEnd = new Date(today.getFullYear(), today.getMonth() + 1, 0, 23, 59, 59)

  const [rangeStart, setRangeStart] = useState<Date>(defaultStart)
  const [rangeEnd, setRangeEnd] = useState<Date>(defaultEnd)
  const [page, setPage] = useState(0)
  const [statusFilter, setStatusFilter] = useState<EvvComplianceStatus | 'ALL'>('ALL')

  const startParam = useMemo(() => toLocalDateTime(rangeStart), [rangeStart])
  const endParam = useMemo(() => toLocalDateTime(rangeEnd), [rangeEnd])

  const { rows, totalPages, totalElements, isLoading, isError } = useEvvHistory(startParam, endParam, page)

  const filteredRows = useMemo(() => {
    if (statusFilter === 'ALL') return rows
    return rows.filter((r) => r.evvStatus === statusFilter)
  }, [rows, statusFilter])

  // Status counts from the current page only — not a global total.
  // The "All" chip shows totalElements (API total); status chips show page-local counts.
  // TODO (P1): add status breakdown to the API response so counts reflect all pages.
  const statusCounts = useMemo(() => {
    const counts: Partial<Record<EvvComplianceStatus, number>> = {}
    for (const row of rows) {
      counts[row.evvStatus] = (counts[row.evvStatus] ?? 0) + 1
    }
    return counts
  }, [rows])

  const handleDateChange = (field: 'start' | 'end', value: string) => {
    // parseDateInputAsLocal constructs a local-midnight Date from the 'YYYY-MM-DD'
    // string, avoiding the ECMA-262 gotcha where new Date('YYYY-MM-DD') is UTC midnight.
    const d = parseDateInputAsLocal(value)
    if (!isNaN(d.getTime())) {
      if (field === 'start') {
        setRangeStart(d)
        setPage(0)
      } else {
        d.setHours(23, 59, 59, 0) // local end-of-day; toISOString() converts to UTC correctly
        setRangeEnd(d)
        setPage(0)
      }
    }
  }

  // Convert Date to YYYY-MM-DD for date input value using LOCAL date parts.
  // toISOString().split('T')[0] would give the UTC date, which is wrong for non-UTC users
  // (e.g. a user at UTC-5 at 9pm local would see tomorrow's date in the picker).
  const toDateInputValue = (d: Date) =>
    `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`

  return (
    <div className="flex flex-col h-full bg-surface">
      {/* Header */}
      <div className="px-6 py-4 border-b bg-white border-border">
        <div className="flex items-center justify-between flex-wrap gap-4">
          <div>
            <h1 className="text-lg font-semibold text-dark">
              EVV Compliance History
            </h1>
            <p className="text-xs mt-0.5 text-text-muted">
              {totalElements} visits in range
            </p>
          </div>

          {/* Date range pickers */}
          <div className="flex items-center gap-3">
            <div className="flex items-center gap-2">
              <label className="text-xs text-text-secondary">From</label>
              <input
                type="date"
                value={toDateInputValue(rangeStart)}
                onChange={(e) => handleDateChange('start', e.target.value)}
                className="rounded-lg px-2 py-1 text-xs border border-border text-dark"
              />
            </div>
            <div className="flex items-center gap-2">
              <label className="text-xs text-text-secondary">To</label>
              <input
                type="date"
                value={toDateInputValue(rangeEnd)}
                onChange={(e) => handleDateChange('end', e.target.value)}
                className="rounded-lg px-2 py-1 text-xs border border-border text-dark"
              />
            </div>
          </div>
        </div>

        {/* Status filter chips — "ALL" uses brand token; EVV-status chips keep semantic colours */}
        <div className="flex items-center gap-2 mt-3 flex-wrap">
          <button
            onClick={() => setStatusFilter('ALL')}
            className={`text-xs px-3 py-1 rounded-full font-medium transition-colors border border-border ${
              statusFilter === 'ALL'
                ? 'bg-blue text-white'
                : 'bg-surface text-text-secondary'
            }`}
          >
            All ({totalElements})
          </button>
          {STATUS_ORDER.map((status) => {
            const count = statusCounts[status] ?? 0
            return (
              <button
                key={status}
                onClick={() => setStatusFilter(status)}
                className="text-xs px-3 py-1 rounded-full font-medium transition-colors"
                style={{
                  backgroundColor:
                    statusFilter === status ? EVV_COLORS[status] : undefined,
                  color: statusFilter === status ? '#ffffff' : EVV_COLORS[status],
                  border: `1px solid ${EVV_COLORS[status]}`,
                }}
              >
                {status} ({count} this page)
              </button>
            )
          })}
        </div>
      </div>

      {/* Table */}
      <div className="flex-1 overflow-auto">
        {isLoading ? (
          <div className="flex items-center justify-center h-32">
            <span className="text-sm text-text-muted">Loading EVV history…</span>
          </div>
        ) : isError ? (
          <div className="flex items-center justify-center h-32">
            <p className="text-sm text-red-600">Failed to load EVV history.</p>
          </div>
        ) : filteredRows.length === 0 ? (
          <div className="flex items-center justify-center h-32">
            <p className="text-sm text-text-muted">
              {rows.length > 0 && statusFilter !== 'ALL'
                ? `No ${statusFilter} visits on this page.`
                : 'No visits found for this range.'}
            </p>
          </div>
        ) : (
          <table className="w-full text-left">
            <thead className="bg-surface border-b border-border">
              <tr>
                {['Client', 'Caregiver', 'Service', 'Date / Start', 'EVV Status', 'In / Out', 'Method'].map(
                  (h) => (
                    <th
                      key={h}
                      className="px-4 py-3 text-xs font-semibold uppercase text-text-muted"
                    >
                      {h}
                    </th>
                  ),
                )}
              </tr>
            </thead>
            <tbody className="bg-white">
              {filteredRows.map((row) => (
                <EvvRow key={row.shiftId} row={row} />
              ))}
            </tbody>
          </table>
        )}
      </div>

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex items-center justify-end gap-2 px-6 py-3 border-t border-border bg-white">
          <button
            onClick={() => { setPage((p) => Math.max(0, p - 1)); setStatusFilter('ALL') }}
            disabled={page === 0}
            className="px-3 py-1.5 text-sm disabled:opacity-40 border border-border text-text-secondary"
          >
            Prev
          </button>
          <span className="text-sm text-text-secondary">
            Page {page + 1} of {totalPages}
          </span>
          <button
            onClick={() => { setPage((p) => Math.min(totalPages - 1, p + 1)); setStatusFilter('ALL') }}
            disabled={page === totalPages - 1}
            className="px-3 py-1.5 text-sm disabled:opacity-40 border border-border text-text-secondary"
          >
            Next
          </button>
        </div>
      )}
    </div>
  )
}
```

- [ ] **Step 10.2: Verify TypeScript and build**

```bash
cd frontend && npx tsc --noEmit 2>&1 | head -20
cd frontend && npm run build 2>&1 | tail -5
```

Expected: no errors, clean build.

- [ ] **Step 10.3: Run backend tests one final time**

```bash
cd backend && mvn test -q 2>&1 | tail -5
```

Expected: all tests pass, 0 failures.

- [ ] **Step 10.4: Commit**

```bash
cd frontend && git add src/components/evv/EvvStatusPage.tsx
git commit -m "feat: wire EvvStatusPage to real API with date-range picker and status filter chips"
```

---

## ✋ MANUAL TEST CHECKPOINT 8

**Start both servers:**

```bash
# Terminal 1 — backend
cd backend && mvn spring-boot:run

# Terminal 2 — frontend
cd frontend && npm run dev
```

**Test the payers screen:**

1. Log in and navigate to `/payers`.
2. Verify the payers list renders real data. Each row shows the payer name, type, state, and EVV aggregator (e.g. "SANDATA" for TX, "HHAEXCHANGE" for NY).
3. If no payers are seeded, confirm the empty state is shown.
4. Open DevTools → Network. Confirm `GET /api/v1/payers?page=0&size=20&sort=name` is called.

**Test the EVV status screen:**

5. Navigate to `/evv`.
6. Verify the EVV history table loads for the current month. If no shifts exist, confirm the empty state message.
7. Use the "From" and "To" date pickers to change the range. Confirm new network requests fire and the table updates.
8. Click the "RED" status filter chip. Confirm the table filters to only RED rows (client-side filter on the current page).
9. Verify the EVV badge in the sidebar (red circle with count) matches `redEvvCount` from the dashboard endpoint.
10. If any shift has clock-in/clock-out data, verify the "In / Out" column shows real times, and the "Method" column shows the verification method (GPS, MANUAL, etc.).

**Final backend verification:**

```bash
# Smoke-test the new endpoints manually
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@sunrise.dev","password":"Admin1234!"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

# Payers list
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/v1/payers" | python3 -m json.tool

# EVV history for April 2026
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/v1/evv/history?start=2026-04-01T00:00:00&end=2026-04-30T23:59:59" \
  | python3 -m json.tool
```

Expected: both endpoints return valid JSON with `content`, `totalElements`, `totalPages` keys.

All 8 phases are now complete. The hcare web admin frontend is fully wired from static mock UI (Phase 1) through all real API integrations.
