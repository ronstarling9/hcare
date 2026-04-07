# Phase 2: Backend — GET /api/v1/dashboard/today

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the `GET /api/v1/dashboard/today` endpoint so Phase 5 can wire the frontend dashboard screen to real data.

**Before starting:** All commands run from `backend/` unless otherwise noted.

---

### Task 1: Add Date-Range Query Methods to Repositories

**Files:**
- Modify: `backend/src/main/java/com/hcare/domain/CaregiverCredentialRepository.java`
- Modify: `backend/src/main/java/com/hcare/domain/BackgroundCheckRepository.java`

- [ ] **Step 1.1: Add expiry date range query to CaregiverCredentialRepository**

Replace the full contents of `backend/src/main/java/com/hcare/domain/CaregiverCredentialRepository.java`:

```java
package com.hcare.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface CaregiverCredentialRepository extends JpaRepository<CaregiverCredential, UUID> {
    List<CaregiverCredential> findByCaregiverId(UUID caregiverId);

    Page<CaregiverCredential> findByCaregiverId(UUID caregiverId, Pageable pageable);

    /**
     * Returns credentials for the agency whose expiryDate falls within [from, to] (inclusive).
     * Used by DashboardService to find credentials expiring within the next 30 days.
     * NOTE: The agencyFilter @Filter is active on this entity so agencyId is already scoped
     * by the Hibernate filter; the agencyId parameter here is redundant but adds an explicit
     * safety predicate for queries run outside a filtered session (e.g. batch jobs).
     */
    List<CaregiverCredential> findByAgencyIdAndExpiryDateBetween(UUID agencyId,
                                                                  LocalDate from,
                                                                  LocalDate to);
}
```

- [ ] **Step 1.2: Add renewal due date range query to BackgroundCheckRepository**

Replace the full contents of `backend/src/main/java/com/hcare/domain/BackgroundCheckRepository.java`:

```java
package com.hcare.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface BackgroundCheckRepository extends JpaRepository<BackgroundCheck, UUID> {
    List<BackgroundCheck> findByCaregiverId(UUID caregiverId);

    Page<BackgroundCheck> findByCaregiverId(UUID caregiverId, Pageable pageable);

    /**
     * Returns background checks for the agency whose renewalDueDate falls within [from, to] (inclusive).
     * Used by DashboardService to find background checks due within the next 30 days.
     */
    List<BackgroundCheck> findByAgencyIdAndRenewalDueDateBetween(UUID agencyId,
                                                                  LocalDate from,
                                                                  LocalDate to);
}
```

- [ ] **Step 1.3: Verify compilation**

```bash
cd backend && mvn compile -q
```

Expected: no output (clean compile).

- [ ] **Step 1.4: Commit**

```bash
git add backend/src/main/java/com/hcare/domain/CaregiverCredentialRepository.java \
        backend/src/main/java/com/hcare/domain/BackgroundCheckRepository.java
git commit -m "feat: add date-range query methods to credential and background-check repos"
```

---

### Task 2: Create Dashboard DTOs

**Files:**
- Create: `backend/src/main/java/com/hcare/api/v1/dashboard/dto/DashboardVisitRow.java`
- Create: `backend/src/main/java/com/hcare/api/v1/dashboard/dto/DashboardAlert.java`
- Create: `backend/src/main/java/com/hcare/api/v1/dashboard/dto/DashboardTodayResponse.java`

- [ ] **Step 2.1: Create DashboardVisitRow**

Create `backend/src/main/java/com/hcare/api/v1/dashboard/dto/DashboardVisitRow.java`:

```java
package com.hcare.api.v1.dashboard.dto;

import com.hcare.domain.ShiftStatus;
import com.hcare.evv.EvvComplianceStatus;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One row in the dashboard visit list for today.
 * Names are denormalized from Client and Caregiver at query time.
 */
public record DashboardVisitRow(
    UUID shiftId,
    String clientFirstName,
    String clientLastName,
    String caregiverFirstName,
    String caregiverLastName,
    LocalDateTime scheduledStart,
    LocalDateTime scheduledEnd,
    ShiftStatus status,
    EvvComplianceStatus evvStatus
) {}
```

- [ ] **Step 2.2: Create DashboardAlert**

Create `backend/src/main/java/com/hcare/api/v1/dashboard/dto/DashboardAlert.java`:

```java
package com.hcare.api.v1.dashboard.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A single actionable alert for the dashboard right-column.
 *
 * <p>alertType values:
 * <ul>
 *   <li>CREDENTIAL_EXPIRING — caregiver credential expiring within 30 days</li>
 *   <li>BACKGROUND_CHECK_DUE — background check renewal due within 30 days</li>
 *   <li>AUTHORIZATION_LOW — authorization utilization >= 80%</li>
 * </ul>
 */
public record DashboardAlert(
    String alertType,
    UUID subjectId,
    String subjectName,
    String detail,
    LocalDate dueDate
) {}
```

- [ ] **Step 2.3: Create DashboardTodayResponse**

Create `backend/src/main/java/com/hcare/api/v1/dashboard/dto/DashboardTodayResponse.java`:

```java
package com.hcare.api.v1.dashboard.dto;

import java.util.List;

/**
 * Top-level response for GET /api/v1/dashboard/today.
 */
public record DashboardTodayResponse(
    int totalVisitsToday,
    int completedVisits,
    int inProgressVisits,
    int openVisits,
    int redEvvCount,
    List<DashboardVisitRow> visits,
    List<DashboardAlert> alerts
) {}
```

- [ ] **Step 2.4: Verify compilation**

```bash
cd backend && mvn compile -q
```

Expected: no output (clean compile).

- [ ] **Step 2.5: Commit**

```bash
git add backend/src/main/java/com/hcare/api/v1/dashboard/
git commit -m "feat: add DashboardTodayResponse, DashboardVisitRow, DashboardAlert DTOs"
```

---

### Task 3: Create DashboardService

**Files:**
- Create: `backend/src/main/java/com/hcare/api/v1/dashboard/DashboardService.java`

- [ ] **Step 3.1: Create DashboardService**

Create `backend/src/main/java/com/hcare/api/v1/dashboard/DashboardService.java`:

```java
package com.hcare.api.v1.dashboard;

import com.hcare.api.v1.dashboard.dto.DashboardAlert;
import com.hcare.api.v1.dashboard.dto.DashboardTodayResponse;
import com.hcare.api.v1.dashboard.dto.DashboardVisitRow;
import com.hcare.domain.*;
import com.hcare.evv.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private static final int ALERT_WINDOW_DAYS = 30;
    private static final BigDecimal AUTH_LOW_THRESHOLD = new BigDecimal("0.80");

    private final ShiftRepository shiftRepository;
    private final ClientRepository clientRepository;
    private final CaregiverRepository caregiverRepository;
    private final EvvRecordRepository evvRecordRepository;
    private final EvvStateConfigRepository evvStateConfigRepository;
    private final AuthorizationRepository authorizationRepository;
    private final PayerRepository payerRepository;
    private final CaregiverCredentialRepository credentialRepository;
    private final BackgroundCheckRepository backgroundCheckRepository;
    private final EvvComplianceService evvComplianceService;

    public DashboardService(
            ShiftRepository shiftRepository,
            ClientRepository clientRepository,
            CaregiverRepository caregiverRepository,
            EvvRecordRepository evvRecordRepository,
            EvvStateConfigRepository evvStateConfigRepository,
            AuthorizationRepository authorizationRepository,
            PayerRepository payerRepository,
            CaregiverCredentialRepository credentialRepository,
            BackgroundCheckRepository backgroundCheckRepository,
            EvvComplianceService evvComplianceService) {
        this.shiftRepository = shiftRepository;
        this.clientRepository = clientRepository;
        this.caregiverRepository = caregiverRepository;
        this.evvRecordRepository = evvRecordRepository;
        this.evvStateConfigRepository = evvStateConfigRepository;
        this.authorizationRepository = authorizationRepository;
        this.payerRepository = payerRepository;
        this.credentialRepository = credentialRepository;
        this.backgroundCheckRepository = backgroundCheckRepository;
        this.evvComplianceService = evvComplianceService;
    }

    @Transactional(readOnly = true)
    public DashboardTodayResponse buildTodayDashboard(UUID agencyId) {
        LocalDateTime startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);

        // Fetch today's shifts (unpaged — typically < 50 for a small agency)
        List<Shift> todayShifts = shiftRepository
            .findByAgencyIdAndScheduledStartBetween(
                agencyId, startOfDay, endOfDay,
                PageRequest.of(0, 500, org.springframework.data.domain.Sort.by("scheduledStart")))
            .getContent();

        // Build lookup maps
        Map<UUID, Client> clientMap = clientRepository.findByAgencyId(agencyId).stream()
            .collect(Collectors.toMap(Client::getId, c -> c));

        Map<UUID, Caregiver> caregiverMap = caregiverRepository.findByAgencyId(agencyId).stream()
            .collect(Collectors.toMap(Caregiver::getId, c -> c));

        // Fetch all EVV records for today's shifts in one pass
        Set<UUID> shiftIds = todayShifts.stream()
            .map(Shift::getId)
            .collect(Collectors.toSet());

        Map<UUID, EvvRecord> evvByShiftId = evvRecordRepository.findAll().stream()
            .filter(r -> shiftIds.contains(r.getShiftId()))
            .collect(Collectors.toMap(EvvRecord::getShiftId, r -> r));

        // Fetch authorizations and payers for EVV computation
        Map<UUID, Authorization> authById = authorizationRepository.findByAgencyId(agencyId).stream()
            .collect(Collectors.toMap(Authorization::getId, a -> a));

        Map<UUID, Payer> payerById = payerRepository.findByAgencyId(agencyId).stream()
            .collect(Collectors.toMap(Payer::getId, p -> p));

        // EVV state config cache (keyed by state code)
        Map<String, EvvStateConfig> stateConfigCache = new HashMap<>();

        // Build visit rows
        List<DashboardVisitRow> visitRows = new ArrayList<>();
        int completed = 0, inProgress = 0, open = 0, redCount = 0;

        for (Shift shift : todayShifts) {
            Client client = clientMap.get(shift.getClientId());
            Caregiver caregiver = shift.getCaregiverId() != null
                ? caregiverMap.get(shift.getCaregiverId()) : null;

            EvvRecord evvRecord = evvByShiftId.get(shift.getId());

            // Determine payer type for compliance computation
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

            // Resolve state config
            EvvComplianceStatus evvStatus = EvvComplianceStatus.GREY;
            if (client != null && client.getServiceState() != null) {
                String stateCode = client.getServiceState();
                EvvStateConfig stateConfig = stateConfigCache.computeIfAbsent(
                    stateCode, evvStateConfigRepository::findByStateCode);
                if (stateConfig != null) {
                    evvStatus = evvComplianceService.compute(
                        evvRecord, stateConfig, shift, payerType,
                        client.getLat(), client.getLng());
                }
            }

            if (evvStatus == EvvComplianceStatus.RED) redCount++;

            switch (shift.getStatus()) {
                case COMPLETED -> completed++;
                case IN_PROGRESS -> inProgress++;
                case OPEN, ASSIGNED -> open++;
                default -> { /* CANCELLED / MISSED — still show in list */ }
            }

            visitRows.add(new DashboardVisitRow(
                shift.getId(),
                client != null ? client.getFirstName() : "Unknown",
                client != null ? client.getLastName() : "Client",
                caregiver != null ? caregiver.getFirstName() : null,
                caregiver != null ? caregiver.getLastName() : null,
                shift.getScheduledStart(),
                shift.getScheduledEnd(),
                shift.getStatus(),
                evvStatus
            ));
        }

        // Build alerts
        List<DashboardAlert> alerts = new ArrayList<>();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate alertHorizon = today.plusDays(ALERT_WINDOW_DAYS);

        // Credentials expiring within 30 days
        List<CaregiverCredential> expiringCredentials = credentialRepository
            .findByAgencyIdAndExpiryDateBetween(agencyId, today, alertHorizon);
        for (CaregiverCredential cred : expiringCredentials) {
            Caregiver cg = caregiverMap.get(cred.getCaregiverId());
            String name = cg != null ? cg.getFirstName() + " " + cg.getLastName() : "Unknown Caregiver";
            alerts.add(new DashboardAlert(
                "CREDENTIAL_EXPIRING",
                cred.getId(),
                name,
                cred.getCredentialType().name() + " expires",
                cred.getExpiryDate()
            ));
        }

        // Background checks due within 30 days
        List<BackgroundCheck> dueSoonChecks = backgroundCheckRepository
            .findByAgencyIdAndRenewalDueDateBetween(agencyId, today, alertHorizon);
        for (BackgroundCheck bc : dueSoonChecks) {
            Caregiver cg = caregiverMap.get(bc.getCaregiverId());
            String name = cg != null ? cg.getFirstName() + " " + cg.getLastName() : "Unknown Caregiver";
            alerts.add(new DashboardAlert(
                "BACKGROUND_CHECK_DUE",
                bc.getId(),
                name,
                bc.getCheckType().name() + " renewal due",
                bc.getRenewalDueDate()
            ));
        }

        // Authorizations with >= 80% utilization
        List<Authorization> allAuths = authorizationRepository.findByAgencyId(agencyId);
        for (Authorization auth : allAuths) {
            if (auth.getAuthorizedUnits().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal utilization = auth.getUsedUnits()
                    .divide(auth.getAuthorizedUnits(), 4, RoundingMode.HALF_UP);
                if (utilization.compareTo(AUTH_LOW_THRESHOLD) >= 0) {
                    Client cl = clientMap.get(auth.getClientId());
                    String name = cl != null ? cl.getFirstName() + " " + cl.getLastName() : "Unknown Client";
                    int pct = utilization.multiply(new BigDecimal("100")).intValue();
                    alerts.add(new DashboardAlert(
                        "AUTHORIZATION_LOW",
                        auth.getId(),
                        name,
                        "Auth " + auth.getAuthNumber() + " at " + pct + "% utilization",
                        auth.getEndDate()
                    ));
                }
            }
        }

        return new DashboardTodayResponse(
            todayShifts.size(),
            completed,
            inProgress,
            open,
            redCount,
            visitRows,
            alerts
        );
    }
}
```

- [ ] **Step 3.2: Verify compilation**

```bash
cd backend && mvn compile -q
```

Expected: no output (clean compile).

- [ ] **Step 3.3: Commit**

```bash
git add backend/src/main/java/com/hcare/api/v1/dashboard/DashboardService.java
git commit -m "feat: add DashboardService — today's visits, EVV statuses, and alerts"
```

---

### Task 4: Create DashboardController

**Files:**
- Create: `backend/src/main/java/com/hcare/api/v1/dashboard/DashboardController.java`

- [ ] **Step 4.1: Create DashboardController**

Create `backend/src/main/java/com/hcare/api/v1/dashboard/DashboardController.java`:

```java
package com.hcare.api.v1.dashboard;

import com.hcare.api.v1.dashboard.dto.DashboardTodayResponse;
import com.hcare.security.UserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/today")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<DashboardTodayResponse> getToday(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(dashboardService.buildTodayDashboard(principal.getAgencyId()));
    }
}
```

- [ ] **Step 4.2: Verify compilation**

```bash
cd backend && mvn compile -q
```

Expected: no output (clean compile).

- [ ] **Step 4.3: Commit**

```bash
git add backend/src/main/java/com/hcare/api/v1/dashboard/DashboardController.java
git commit -m "feat: add DashboardController GET /api/v1/dashboard/today"
```

---

### Task 5: Create DashboardControllerIT

**Files:**
- Create: `backend/src/test/java/com/hcare/api/v1/dashboard/DashboardControllerIT.java`

- [ ] **Step 5.1: Create the integration test**

Create `backend/src/test/java/com/hcare/api/v1/dashboard/DashboardControllerIT.java`:

```java
package com.hcare.api.v1.dashboard;

import com.hcare.AbstractIntegrationTest;
import com.hcare.api.v1.auth.dto.LoginRequest;
import com.hcare.api.v1.auth.dto.LoginResponse;
import com.hcare.api.v1.dashboard.dto.DashboardTodayResponse;
import com.hcare.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.jdbc.Sql;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@Sql(statements = {
    "TRUNCATE TABLE evv_records, shifts, authorizations, caregiver_credentials, background_checks, " +
    "payers, service_types, caregivers, clients, agency_users, agencies RESTART IDENTITY CASCADE"
},
     executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class DashboardControllerIT extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private AgencyRepository agencyRepo;
    @Autowired private AgencyUserRepository userRepo;
    @Autowired private ClientRepository clientRepo;
    @Autowired private CaregiverRepository caregiverRepo;
    @Autowired private ServiceTypeRepository serviceTypeRepo;
    @Autowired private ShiftRepository shiftRepo;
    @Autowired private PasswordEncoder passwordEncoder;

    private Agency agency;
    private AgencyUser adminUser;

    @BeforeEach
    void seedBaseData() {
        agency = agencyRepo.save(new Agency("Dashboard Test Agency", "TX"));
        adminUser = userRepo.save(new AgencyUser(
            agency.getId(), "dash-admin@test.com",
            passwordEncoder.encode("password123"), UserRole.ADMIN));
    }

    private String token() {
        LoginRequest req = new LoginRequest("dash-admin@test.com", "password123");
        ResponseEntity<LoginResponse> resp = restTemplate.postForEntity(
            "/api/v1/auth/login", req, LoginResponse.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        return resp.getBody().token();
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    @Test
    void getDashboardToday_returns_200_with_empty_visits_when_no_shifts_today() {
        String token = token();

        ResponseEntity<DashboardTodayResponse> resp = restTemplate.exchange(
            "/api/v1/dashboard/today",
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(token)),
            DashboardTodayResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        DashboardTodayResponse body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.totalVisitsToday()).isZero();
        assertThat(body.visits()).isEmpty();
        assertThat(body.alerts()).isNotNull();
    }

    @Test
    void getDashboardToday_counts_todays_shift() {
        Client client = clientRepo.save(new Client(agency.getId(), "Alice", "Smith",
            LocalDate.of(1960, 1, 1)));
        client.setServiceState("TX");
        clientRepo.save(client);

        Caregiver caregiver = caregiverRepo.save(
            new Caregiver(agency.getId(), "Bob", "Jones", "bob@test.com"));

        ServiceType serviceType = serviceTypeRepo.save(
            new ServiceType(agency.getId(), "PCS", "PCS-V1", true, "[]"));

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        shiftRepo.save(new Shift(
            agency.getId(), null, client.getId(), caregiver.getId(),
            serviceType.getId(), null,
            now.withHour(9).withMinute(0).withSecond(0).withNano(0),
            now.withHour(13).withMinute(0).withSecond(0).withNano(0)));

        String token = token();

        ResponseEntity<DashboardTodayResponse> resp = restTemplate.exchange(
            "/api/v1/dashboard/today",
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(token)),
            DashboardTodayResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        DashboardTodayResponse body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.totalVisitsToday()).isEqualTo(1);
        assertThat(body.visits()).hasSize(1);
        assertThat(body.visits().get(0).clientFirstName()).isEqualTo("Alice");
        assertThat(body.visits().get(0).caregiverFirstName()).isEqualTo("Bob");
    }

    @Test
    void getDashboardToday_does_not_include_yesterday_shift() {
        Client client = clientRepo.save(new Client(agency.getId(), "Eve", "Brown",
            LocalDate.of(1970, 5, 20)));
        client.setServiceState("TX");
        clientRepo.save(client);

        ServiceType serviceType = serviceTypeRepo.save(
            new ServiceType(agency.getId(), "PCS", "PCS-V1", true, "[]"));

        LocalDateTime yesterday = LocalDateTime.now(ZoneOffset.UTC).minusDays(1);
        shiftRepo.save(new Shift(
            agency.getId(), null, client.getId(), null,
            serviceType.getId(), null,
            yesterday.withHour(9).withMinute(0).withSecond(0).withNano(0),
            yesterday.withHour(13).withMinute(0).withSecond(0).withNano(0)));

        String token = token();

        ResponseEntity<DashboardTodayResponse> resp = restTemplate.exchange(
            "/api/v1/dashboard/today",
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(token)),
            DashboardTodayResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().totalVisitsToday()).isZero();
    }

    @Test
    void getDashboardToday_returns_401_without_token() {
        ResponseEntity<String> resp = restTemplate.exchange(
            "/api/v1/dashboard/today",
            HttpMethod.GET,
            new HttpEntity<>(new HttpHeaders()),
            String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void getDashboardToday_includes_expiring_credential_alert() {
        Client client = clientRepo.save(new Client(agency.getId(), "Tom", "Lee",
            LocalDate.of(1955, 3, 10)));
        clientRepo.save(client);

        Caregiver caregiver = caregiverRepo.save(
            new Caregiver(agency.getId(), "Carol", "White", "carol@test.com"));

        CaregiverCredential cred = new CaregiverCredential(
            caregiver.getId(), agency.getId(), CredentialType.CPR_BLS,
            LocalDate.now(ZoneOffset.UTC).minusYears(1),
            LocalDate.now(ZoneOffset.UTC).plusDays(10));

        // Persist credential via repository (autowired below)
        // — use the raw JPA save
        credentialRepo.save(cred);

        String token = token();

        ResponseEntity<DashboardTodayResponse> resp = restTemplate.exchange(
            "/api/v1/dashboard/today",
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(token)),
            DashboardTodayResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        DashboardTodayResponse body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.alerts()).anyMatch(a ->
            "CREDENTIAL_EXPIRING".equals(a.alertType()) &&
            a.subjectName().contains("Carol"));
    }

    @Autowired private CaregiverCredentialRepository credentialRepo;
}
```

- [ ] **Step 5.2: Run the new tests**

```bash
cd backend && mvn test -Dtest=DashboardControllerIT -q 2>&1 | tail -20
```

Expected output contains:
```
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
```

- [ ] **Step 5.3: Run full test suite**

```bash
cd backend && mvn test -q 2>&1 | tail -5
```

Expected: all tests pass, 0 failures.

- [ ] **Step 5.4: Commit**

```bash
git add backend/src/test/java/com/hcare/api/v1/dashboard/DashboardControllerIT.java
git commit -m "test: add DashboardControllerIT integration tests"
```

---

## ✋ MANUAL TEST CHECKPOINT 2

**Verify tests pass:**

```bash
cd backend && mvn test -q 2>&1 | tail -5
```

Expected: 170+ tests passing (166 existing + 5 new dashboard tests), 0 failures.

**Verify endpoint manually (start backend first):**

```bash
cd backend && mvn spring-boot:run &
# Wait for "Started HcareApplication"

# 1. Login to get a token
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@test.com","password":"password123"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

# 2. Call dashboard endpoint
curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/dashboard/today | python3 -m json.tool
```

Expected: JSON response with `totalVisitsToday`, `visits`, `alerts` keys. Values depend on seeded dev data.

**Stop backend:** `pkill -f HcareApplication` or `Ctrl+C` in the terminal running the server.

Proceed to Phase 3 only after this checkpoint passes.
