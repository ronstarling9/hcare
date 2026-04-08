# Phase 8: Backend Payer/EVV Endpoints + Wire Frontend

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the two missing backend endpoints (`GET /api/v1/payers`, `GET /api/v1/payers/{id}`, `GET /api/v1/evv/history`) then wire the frontend Payers and EVV Status screens to real data.

**Before starting:**
- All prior phases must be complete.
- `EvvStateConfigRepository.findByStateCode(stateCode)` exists.
- `EvvComplianceService.compute(record, stateConfig, shift, payerType, clientLat, clientLng)` exists.
- `ShiftRepository.findByAgencyIdAndScheduledStartBetween(agencyId, start, end, pageable)` exists.
- `PayerRepository.findByAgencyId(agencyId)` returns a `List<Payer>`.

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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        List<Payer> all = payerRepository.findByAgencyId(agencyId);

        // Cache state configs to avoid repeated DB hits for the same state code
        Map<String, EvvStateConfig> stateConfigCache = new HashMap<>();

        List<PayerResponse> mapped = all.stream()
            .map(p -> toResponse(p, stateConfigCache))
            .toList();

        // Apply pageable manually
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), mapped.size());
        List<PayerResponse> page = start >= mapped.size()
            ? List.of()
            : mapped.subList(start, end);

        return new PageImpl<>(page, pageable, mapped.size());
    }

    @Transactional(readOnly = true)
    public PayerResponse getPayer(UUID agencyId, UUID payerId) {
        Payer payer = payerRepository.findById(payerId)
            .filter(p -> p.getAgencyId().equals(agencyId))
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Payer not found: " + payerId));

        Map<String, EvvStateConfig> cache = new HashMap<>();
        return toResponse(payer, cache);
    }

    private PayerResponse toResponse(Payer payer, Map<String, EvvStateConfig> cache) {
        String aggregatorName = null;
        if (payer.getState() != null) {
            EvvStateConfig config = cache.computeIfAbsent(
                payer.getState(),
                code -> evvStateConfigRepository.findByStateCode(code).orElse(null));
            if (config != null) {
                AggregatorType aggregator = config.getDefaultAggregator();
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
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return ResponseEntity.ok(payerService.listPayers(principal.getAgencyId(), pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<PayerResponse> getPayer(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        return ResponseEntity.ok(payerService.getPayer(principal.getAgencyId(), id));
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
        // Fetch all shifts in the date range (unpaged first pass for EVV computation)
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
            .map(com.hcare.domain.Agency::getState)
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

        // EVV state config cache
        Map<String, EvvStateConfig> stateConfigCache = new HashMap<>();

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
                EvvStateConfig stateConfig = stateConfigCache.computeIfAbsent(
                    effectiveState,
                    code -> evvStateConfigRepository.findByStateCode(code).orElse(null));
                if (stateConfig != null) {
                    status = evvComplianceService.compute(
                        evvRecord, stateConfig, shift, payerType,
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
            @PageableDefault(size = 50, sort = "scheduledStart") Pageable pageable) {
        if (!end.isAfter(start)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "end must be after start");
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
  return useQuery({
    queryKey: ['evv-history', start, end, page],
    queryFn: () => listEvvHistory(start, end, page, 50),
    enabled: Boolean(start && end),
    staleTime: 30_000,
  })
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

// Format a Date to ISO-8601 LocalDateTime for API params (no timezone suffix)
function toLocalDateTime(d: Date): string {
  return d.toISOString().replace('Z', '').replace(/\.\d+$/, '')
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

  const { data, isLoading, isError } = useEvvHistory(startParam, endParam, page)

  const rows = data?.content ?? []
  const totalPages = data?.totalPages ?? 0
  const totalElements = data?.totalElements ?? 0

  const filteredRows = useMemo(() => {
    if (statusFilter === 'ALL') return rows
    return rows.filter((r) => r.evvStatus === statusFilter)
  }, [rows, statusFilter])

  // Status summary counts from current page
  const statusCounts = useMemo(() => {
    const counts: Partial<Record<EvvComplianceStatus, number>> = {}
    for (const row of rows) {
      counts[row.evvStatus] = (counts[row.evvStatus] ?? 0) + 1
    }
    return counts
  }, [rows])

  const handleDateChange = (field: 'start' | 'end', value: string) => {
    const d = new Date(value)
    if (!isNaN(d.getTime())) {
      if (field === 'start') {
        setRangeStart(d)
        setPage(0)
      } else {
        setRangeEnd(d)
        setPage(0)
      }
    }
  }

  // Convert Date to YYYY-MM-DD for date input value
  const toDateInputValue = (d: Date) => d.toISOString().split('T')[0]

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
                {status} ({count})
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
            <p className="text-sm text-text-muted">No visits found for this range.</p>
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
