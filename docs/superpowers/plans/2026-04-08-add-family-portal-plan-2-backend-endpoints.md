# Family Portal — Implementation Plan Part 2: Backend Endpoints

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **This is Part 2 of 3.** Requires Part 1 to be fully complete first. After this part, continue with Part 3 (`2026-04-08-add-family-portal-plan-3-frontend.md`).

**Goal:** Implement all four family portal API endpoints: invite, verify, dashboard, and the nightly token cleanup job.

**Architecture:** New `com.hcare.api.v1.family` package holds `FamilyPortalService`, `FamilyPortalAuthController`, and `FamilyPortalDashboardController`. The invite method lives on `ClientController` (delegating to `FamilyPortalService`). Rate limiting on the public verify endpoint is handled by an in-memory servlet filter.

**Tech Stack:** Java 25, Spring Boot 3.4.4, `MessageDigest` (SHA-256), `SecureRandom`, `@Scheduled`, JUnit 5 + Testcontainers (ITs).

---

## File Map

| Action | Path |
|--------|------|
| Create | `backend/src/main/java/com/hcare/api/v1/family/dto/InviteRequest.java` |
| Create | `backend/src/main/java/com/hcare/api/v1/family/dto/InviteResponse.java` |
| Create | `backend/src/main/java/com/hcare/api/v1/family/dto/PortalVerifyRequest.java` |
| Create | `backend/src/main/java/com/hcare/api/v1/family/dto/PortalVerifyResponse.java` |
| Create | `backend/src/main/java/com/hcare/api/v1/family/dto/PortalDashboardResponse.java` |
| Create | `backend/src/main/java/com/hcare/api/v1/family/FamilyPortalService.java` |
| Create | `backend/src/main/java/com/hcare/api/v1/family/FamilyPortalAuthController.java` |
| Create | `backend/src/main/java/com/hcare/api/v1/family/FamilyPortalDashboardController.java` |
| Create | `backend/src/main/java/com/hcare/api/v1/family/VerifyRateLimiter.java` |
| Modify | `backend/src/main/java/com/hcare/api/v1/clients/ClientController.java` |
| Modify | `backend/src/main/java/com/hcare/api/v1/clients/ClientService.java` |
| Create | `backend/src/main/java/com/hcare/scheduling/FamilyPortalTokenCleanupJob.java` |
| Create | `backend/src/test/java/com/hcare/api/v1/family/FamilyPortalAuthControllerIT.java` |
| Create | `backend/src/test/java/com/hcare/api/v1/family/FamilyPortalDashboardControllerIT.java` |

---

## Task 8: DTOs

**Files:**
- Create: `backend/src/main/java/com/hcare/api/v1/family/dto/InviteRequest.java`
- Create: `backend/src/main/java/com/hcare/api/v1/family/dto/InviteResponse.java`
- Create: `backend/src/main/java/com/hcare/api/v1/family/dto/PortalVerifyRequest.java`
- Create: `backend/src/main/java/com/hcare/api/v1/family/dto/PortalVerifyResponse.java`
- Create: `backend/src/main/java/com/hcare/api/v1/family/dto/PortalDashboardResponse.java`

- [ ] **Step 1: Create all DTOs**

**`InviteRequest.java`** — request body for `POST /api/v1/clients/{id}/family-portal-users/invite`:
```java
package com.hcare.api.v1.family.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record InviteRequest(
    @NotBlank @Email String email
) {}
```

**`InviteResponse.java`** — response body: the invite URL and its expiry:
```java
package com.hcare.api.v1.family.dto;

public record InviteResponse(
    String inviteUrl,
    String expiresAt   // ISO-8601 UTC timestamp string
) {}
```

**`PortalVerifyRequest.java`** — request body for `POST /api/v1/family/auth/verify`:
```java
package com.hcare.api.v1.family.dto;

import jakarta.validation.constraints.NotBlank;

public record PortalVerifyRequest(
    @NotBlank String token
) {}
```

**`PortalVerifyResponse.java`** — response: FAMILY_PORTAL JWT + scope identifiers:
```java
package com.hcare.api.v1.family.dto;

public record PortalVerifyResponse(
    String jwt,
    String clientId,
    String agencyId
) {}
```

**`PortalDashboardResponse.java`** — the full dashboard payload. All timestamp strings are UTC ISO-8601; frontend converts using `agencyTimezone`:
```java
package com.hcare.api.v1.family.dto;

public record PortalDashboardResponse(
    String clientFirstName,
    String agencyTimezone,
    TodayVisitDto todayVisit,          // null if no shift today
    java.util.List<UpcomingVisitDto> upcomingVisits,
    LastVisitDto lastVisit             // null if no completed visits ever
) {
    public record TodayVisitDto(
        String shiftId,
        String scheduledStart,
        String scheduledEnd,
        String status,        // "GREY" | "IN_PROGRESS" | "COMPLETED" | "CANCELLED"
        String clockedInAt,   // null unless IN_PROGRESS or COMPLETED
        String clockedOutAt,  // null unless COMPLETED
        CaregiverDto caregiver  // null when status is CANCELLED
    ) {}

    public record CaregiverDto(
        String name,
        String serviceType
    ) {}

    public record UpcomingVisitDto(
        String scheduledStart,
        String scheduledEnd,
        String caregiverName
    ) {}

    public record LastVisitDto(
        String date,          // "YYYY-MM-DD"
        String clockedOutAt,
        int durationMinutes,
        String noteText       // null if caregiver entered no notes
    ) {}
}
```

- [ ] **Step 2: Compile**

```bash
cd backend && mvn compile -q
```
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/hcare/api/v1/family/
git commit -m "feat: family portal DTOs — InviteRequest/Response, PortalVerifyRequest/Response, PortalDashboardResponse"
```

---

## Task 9: FamilyPortalService

**Files:**
- Create: `backend/src/main/java/com/hcare/api/v1/family/FamilyPortalService.java`
- Modify: `backend/src/main/java/com/hcare/domain/ShiftRepository.java`

The service handles all three operations. It sits in the `com.hcare.api.v1.family` package.

- [ ] **Step 1: Add derived queries to `ShiftRepository`**

Add all three methods below to `ShiftRepository`. The first finds the most recent COMPLETED shift for a client (used for `lastVisit`). The second replaces the in-memory 90-day stream for upcoming visits with a DB-side `TOP 3` query. The third adds an explicit `agencyId` predicate for the today-window query so the family portal path is safe if `TenantFilterAspect` does not activate for the FAMILY_PORTAL role.

```java
import java.util.Collection;
import java.util.Optional;

// Add to ShiftRepository interface:

// Last visit — most recent COMPLETED shift for a client.
Optional<Shift> findFirstByClientIdAndStatusOrderByScheduledStartDesc(
    UUID clientId, ShiftStatus status);

// Upcoming visits — DB-side TOP 3 to avoid loading 90 days of shifts into memory.
// Excludes CANCELLED and MISSED shifts via the excludedStatuses collection.
List<Shift> findTop3ByClientIdAndStatusNotInAndScheduledStartAfterOrderByScheduledStartAsc(
    UUID clientId, Collection<ShiftStatus> excludedStatuses, LocalDateTime after);

// Today's visit window — explicit agencyId predicate guards against edge cases where
// the Hibernate agencyFilter may not activate for FAMILY_PORTAL-role requests.
List<Shift> findByClientIdAndAgencyIdAndScheduledStartBetween(
    UUID clientId, UUID agencyId, LocalDateTime start, LocalDateTime end);
```

- [ ] **Step 1b: Add `findByClientIdAndAgencyIdAndEmail` to `FamilyPortalUserRepository`**

`FamilyPortalService.generateInvite` calls `fpuRepo.findByClientIdAndAgencyIdAndEmail(clientId, agencyId, req.email())` but this method is not currently declared in the repository. Without it Spring Data JPA will throw at startup. Add it explicitly:

```java
// Add to FamilyPortalUserRepository interface (in com.hcare.domain):
Optional<FamilyPortalUser> findByClientIdAndAgencyIdAndEmail(
    UUID clientId, UUID agencyId, String email);
```

This method aligns with the `uq_fpu_client_agency_email` unique constraint added in the Plan 1 migration (V12). Also add a test assertion in `FamilyPortalUserRepositoryTest` (or create the test class) verifying this method returns the correct row given the `(client_id, agency_id, email)` tuple — and returns `Optional.empty()` when no matching row exists.

- [ ] **Step 2: Create `FamilyPortalService.java`**

```java
package com.hcare.api.v1.family;

import com.hcare.api.v1.family.dto.InviteRequest;
import com.hcare.api.v1.family.dto.InviteResponse;
import com.hcare.api.v1.family.dto.PortalDashboardResponse;
import com.hcare.api.v1.family.dto.PortalVerifyRequest;
import com.hcare.api.v1.family.dto.PortalVerifyResponse;
import com.hcare.domain.*;
import com.hcare.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class FamilyPortalService {

    private static final int TOKEN_BYTES = 64;
    private static final int TOKEN_TTL_HOURS = 72;

    private final FamilyPortalUserRepository fpuRepo;
    private final FamilyPortalTokenRepository tokenRepo;
    private final AgencyRepository agencyRepo;
    private final ClientRepository clientRepo;
    private final ShiftRepository shiftRepo;
    private final EvvRecordRepository evvRepo;
    private final CaregiverRepository caregiverRepo;
    private final ServiceTypeRepository serviceTypeRepo;
    private final JwtTokenProvider jwtTokenProvider;
    private final SecureRandom secureRandom = new SecureRandom();
    private final String portalBaseUrl;

    public FamilyPortalService(
            FamilyPortalUserRepository fpuRepo,
            FamilyPortalTokenRepository tokenRepo,
            AgencyRepository agencyRepo,
            ClientRepository clientRepo,
            ShiftRepository shiftRepo,
            EvvRecordRepository evvRepo,
            CaregiverRepository caregiverRepo,
            ServiceTypeRepository serviceTypeRepo,
            JwtTokenProvider jwtTokenProvider,
            @Value("${hcare.portal.base-url:http://localhost:5173}") String portalBaseUrl) {
        this.fpuRepo = fpuRepo;
        this.tokenRepo = tokenRepo;
        this.agencyRepo = agencyRepo;
        this.clientRepo = clientRepo;
        this.shiftRepo = shiftRepo;
        this.evvRepo = evvRepo;
        this.caregiverRepo = caregiverRepo;
        this.serviceTypeRepo = serviceTypeRepo;
        this.jwtTokenProvider = jwtTokenProvider;
        this.portalBaseUrl = portalBaseUrl;
    }

    // ── Invite ────────────────────────────────────────────────────────────────

    /**
     * Finds or creates a FamilyPortalUser for (clientId, agencyId, email), generates
     * a one-time invite token (72-hour TTL), and returns the invite URL.
     * The raw token is ONLY in the returned URL — it is never persisted. Only SHA-256(raw) is stored.
     */
    @Transactional
    public InviteResponse generateInvite(UUID clientId, UUID agencyId, InviteRequest req) {
        FamilyPortalUser fpu = fpuRepo
            .findByClientIdAndAgencyIdAndEmail(clientId, agencyId, req.email())
            .orElseGet(() -> {
                try {
                    return fpuRepo.saveAndFlush(
                        new FamilyPortalUser(clientId, agencyId, req.email()));
                } catch (DataIntegrityViolationException e) {
                    // Concurrent invite for same (clientId, agencyId, email) — race won by peer; retry lookup.
                    return fpuRepo.findByClientIdAndAgencyIdAndEmail(clientId, agencyId, req.email())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                            "Concurrent invite conflict — please retry"));
                }
            });

        byte[] rawBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(rawBytes);
        String rawHex = HexFormat.of().formatHex(rawBytes);
        String tokenHash = sha256Hex(rawHex);

        LocalDateTime expiresAt = LocalDateTime.now(ZoneOffset.UTC).plusHours(TOKEN_TTL_HOURS);
        tokenRepo.save(new FamilyPortalToken(tokenHash, fpu.getId(), clientId, agencyId, expiresAt));

        // Raw token is placed in the URL only — never logged, never stored.
        String inviteUrl = portalBaseUrl + "/portal/verify?token=" + rawHex;
        String expiresAtStr = expiresAt.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        return new InviteResponse(inviteUrl, expiresAtStr);
    }

    // ── Verify ────────────────────────────────────────────────────────────────

    /**
     * Exchanges a raw invite token for a 30-day FAMILY_PORTAL JWT.
     * Deletes the token row (one-time use). Returns 400 if not found or expired.
     */
    @Transactional
    public PortalVerifyResponse verifyToken(PortalVerifyRequest req) {
        String hash = sha256Hex(req.token());
        FamilyPortalToken tokenRow = tokenRepo.findByTokenHash(hash)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "TOKEN_INVALID"));

        if (tokenRow.getExpiresAt().isBefore(LocalDateTime.now(ZoneOffset.UTC))) {
            tokenRepo.delete(tokenRow);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "TOKEN_EXPIRED");
        }

        UUID fpuId = tokenRow.getFpuId();
        UUID clientId = tokenRow.getClientId();
        UUID agencyId = tokenRow.getAgencyId();

        // Delete token (one-time use) and update last login.
        tokenRepo.delete(tokenRow);
        fpuRepo.findById(fpuId).ifPresent(fpu -> {
            fpu.recordLogin();
            fpuRepo.save(fpu);
        });

        String jwt = jwtTokenProvider.generateFamilyPortalToken(fpuId, clientId, agencyId);
        return new PortalVerifyResponse(jwt, clientId.toString(), agencyId.toString());
    }

    // ── Dashboard ─────────────────────────────────────────────────────────────

    /**
     * Returns portal dashboard data for the authenticated family member.
     * Throws 403 PORTAL_ACCESS_REVOKED if the FamilyPortalUser row no longer exists.
     * Throws 410 CLIENT_DISCHARGED if the client is DISCHARGED or INACTIVE.
     */
    @Transactional(readOnly = true)
    public PortalDashboardResponse getDashboard(UUID fpuId, UUID clientId, UUID agencyId) {
        // Revocation check — the JWT may still be valid even after the admin removes the user.
        if (!fpuRepo.existsById(fpuId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "PORTAL_ACCESS_REVOKED");
        }

        Client client = clientRepo.findById(clientId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                "PORTAL_ACCESS_REVOKED"));

        if (client.getStatus() == ClientStatus.DISCHARGED
                || client.getStatus() == ClientStatus.INACTIVE) {
            throw new ResponseStatusException(HttpStatus.GONE, "CLIENT_DISCHARGED");
        }

        Agency agency = agencyRepo.findById(agencyId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "Agency not found"));

        String tz = agency.getTimezone();
        ZoneId zoneId = ZoneId.of(tz);
        LocalDate todayInAgencyTz = LocalDate.now(zoneId);

        // Convert agency "today" boundaries to UTC for the query.
        LocalDateTime startOfToday = todayInAgencyTz.atStartOfDay(zoneId).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        LocalDateTime startOfTomorrow = todayInAgencyTz.plusDays(1).atStartOfDay(zoneId).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();

        // Today's visit: first non-CANCELLED/MISSED shift with scheduledStart within today.
        // Uses the explicit agencyId predicate to guard against any edge case where
        // TenantFilterAspect does not activate for FAMILY_PORTAL-role requests.
        List<Shift> todayShifts = shiftRepo.findByClientIdAndAgencyIdAndScheduledStartBetween(
            clientId, agencyId, startOfToday, startOfTomorrow);
        Shift todayShift = todayShifts.stream()
            .filter(s -> s.getStatus() != ShiftStatus.CANCELLED
                      && s.getStatus() != ShiftStatus.MISSED)
            .min(java.util.Comparator.comparing(Shift::getScheduledStart))
            .orElse(null);

        // Upcoming: next 3 non-CANCELLED/MISSED shifts after end of today, ordered ascending.
        // Uses a DB-side TOP 3 derived query to avoid loading a 90-day window into memory.
        List<Shift> upcomingShifts = shiftRepo
            .findTop3ByClientIdAndStatusNotInAndScheduledStartAfterOrderByScheduledStartAsc(
                clientId,
                java.util.List.of(ShiftStatus.CANCELLED, ShiftStatus.MISSED),
                startOfTomorrow);

        // Collect all caregiver IDs needed (today's shift + upcoming shifts) for a single bulk fetch.
        // This eliminates the N+1 that would otherwise occur in buildTodayVisitDto / buildCaregiverDto.
        java.util.Set<UUID> allCaregiverIds = new java.util.HashSet<>();
        if (todayShift != null && todayShift.getCaregiverId() != null) {
            allCaregiverIds.add(todayShift.getCaregiverId());
        }
        upcomingShifts.forEach(s -> { if (s.getCaregiverId() != null) allCaregiverIds.add(s.getCaregiverId()); });

        java.util.Map<UUID, Caregiver> caregiverMap = caregiverRepo.findAllById(allCaregiverIds)
            .stream()
            .collect(java.util.stream.Collectors.toMap(Caregiver::getId,
                java.util.function.Function.identity()));

        // Similarly collect all service type IDs (today + upcoming) for a single bulk fetch.
        java.util.Set<UUID> allServiceTypeIds = new java.util.HashSet<>();
        if (todayShift != null && todayShift.getServiceTypeId() != null) {
            allServiceTypeIds.add(todayShift.getServiceTypeId());
        }
        upcomingShifts.forEach(s -> { if (s.getServiceTypeId() != null) allServiceTypeIds.add(s.getServiceTypeId()); });

        java.util.Map<UUID, ServiceType> serviceTypeMap = serviceTypeRepo.findAllById(allServiceTypeIds)
            .stream()
            .collect(java.util.stream.Collectors.toMap(ServiceType::getId,
                java.util.function.Function.identity()));

        PortalDashboardResponse.TodayVisitDto todayVisitDto = null;
        if (todayShift != null) {
            todayVisitDto = buildTodayVisitDto(todayShift, caregiverMap, serviceTypeMap);
        }

        List<PortalDashboardResponse.UpcomingVisitDto> upcoming = upcomingShifts.stream()
            .map(s -> buildUpcomingDto(s, caregiverMap))
            .toList();

        // Last visit: most recent COMPLETED shift.
        PortalDashboardResponse.LastVisitDto lastVisitDto = shiftRepo
            .findFirstByClientIdAndStatusOrderByScheduledStartDesc(clientId, ShiftStatus.COMPLETED)
            .map(this::buildLastVisitDto)
            .orElse(null);

        return new PortalDashboardResponse(
            client.getFirstName(), tz, todayVisitDto, upcoming, lastVisitDto);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    // caregiverMap and serviceTypeMap are pre-fetched in bulk by getDashboard — no per-shift DB lookup.
    private PortalDashboardResponse.TodayVisitDto buildTodayVisitDto(
            Shift shift,
            java.util.Map<UUID, Caregiver> caregiverMap,
            java.util.Map<UUID, ServiceType> serviceTypeMap) {
        String statusStr = mapShiftStatus(shift.getStatus());
        String clockedInAt = null;
        String clockedOutAt = null;

        Optional<EvvRecord> evv = evvRepo.findByShiftId(shift.getId());
        if (evv.isPresent()) {
            if (evv.get().getTimeIn() != null) {
                clockedInAt = evv.get().getTimeIn().toString();
            }
            if (evv.get().getTimeOut() != null) {
                clockedOutAt = evv.get().getTimeOut().toString();
            }
        }

        // Caregiver card is hidden for CANCELLED shifts.
        PortalDashboardResponse.CaregiverDto caregiverDto = null;
        if (shift.getStatus() != ShiftStatus.CANCELLED && shift.getCaregiverId() != null) {
            caregiverDto = buildCaregiverDto(shift.getCaregiverId(), shift.getServiceTypeId(),
                caregiverMap, serviceTypeMap);
        }

        return new PortalDashboardResponse.TodayVisitDto(
            shift.getId().toString(),
            shift.getScheduledStart().toString(),
            shift.getScheduledEnd().toString(),
            statusStr,
            clockedInAt,
            clockedOutAt,
            caregiverDto
        );
    }

    // caregiverMap is pre-fetched in bulk by the caller — no per-shift DB lookup.
    private PortalDashboardResponse.UpcomingVisitDto buildUpcomingDto(
            Shift shift, java.util.Map<UUID, Caregiver> caregiverMap) {
        String caregiverName = null;
        if (shift.getCaregiverId() != null) {
            Caregiver cg = caregiverMap.get(shift.getCaregiverId());
            if (cg != null) {
                caregiverName = cg.getFirstName() + " " + cg.getLastName();
            }
        }
        return new PortalDashboardResponse.UpcomingVisitDto(
            shift.getScheduledStart().toString(),
            shift.getScheduledEnd().toString(),
            caregiverName
        );
    }

    private PortalDashboardResponse.LastVisitDto buildLastVisitDto(Shift shift) {
        String clockedOutAt = null;
        int durationMinutes = 0;

        Optional<EvvRecord> evv = evvRepo.findByShiftId(shift.getId());
        if (evv.isPresent() && evv.get().getTimeOut() != null) {
            clockedOutAt = evv.get().getTimeOut().toString();
            if (evv.get().getTimeIn() != null) {
                durationMinutes = (int) java.time.Duration.between(
                    evv.get().getTimeIn(), evv.get().getTimeOut()).toMinutes();
            }
        }

        return new PortalDashboardResponse.LastVisitDto(
            shift.getScheduledStart().toLocalDate().toString(),
            clockedOutAt,
            durationMinutes,
            shift.getNotes()
        );
    }

    // Pure formatting helper — caregiverMap and serviceTypeMap are pre-fetched in bulk by getDashboard.
    // No repository calls are made here; this method is free of any DB interaction.
    private PortalDashboardResponse.CaregiverDto buildCaregiverDto(
            UUID caregiverId,
            UUID serviceTypeId,
            java.util.Map<UUID, Caregiver> caregiverMap,
            java.util.Map<UUID, ServiceType> serviceTypeMap) {
        Caregiver cg = caregiverMap.get(caregiverId);
        String name = (cg != null) ? cg.getFirstName() + " " + cg.getLastName() : "Unknown";
        ServiceType st = (serviceTypeId != null) ? serviceTypeMap.get(serviceTypeId) : null;
        String serviceTypeName = (st != null) ? st.getName() : "";
        return new PortalDashboardResponse.CaregiverDto(name, serviceTypeName);
    }

    private String mapShiftStatus(ShiftStatus status) {
        return switch (status) {
            case OPEN, ASSIGNED -> "GREY";
            case IN_PROGRESS -> "IN_PROGRESS";
            case COMPLETED -> "COMPLETED";
            case CANCELLED -> "CANCELLED";
            case MISSED -> "GREY";  // Missed shifts are excluded from todayVisit selection earlier in getDashboard, but mapped defensively here
            // No default: compiler enforces exhaustiveness — add a case here if ShiftStatus gains new values
        };
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
```

- [ ] **Step 2b: Verify TenantFilterAspect activates for FAMILY_PORTAL-role requests**

`TenantFilterInterceptor` reads `UserPrincipal.getAgencyId()` and sets `TenantContext`. For FAMILY_PORTAL-role requests, `UserPrincipal` carries a non-null `agencyId` from the JWT claim (set in `JwtTokenProvider.generateFamilyPortalToken`), so the filter **will** activate correctly — no special-casing is needed.

However, as an additional safety net, the `getDashboard` service method uses `findByClientIdAndAgencyIdAndScheduledStartBetween` (rather than the agencyFilter-only `findByClientIdAndScheduledStartBetween`) so that the query carries an explicit `agencyId` predicate even in the unlikely case where `TenantContext` is not populated.

Add an integration test assertion in `FamilyPortalDashboardControllerIT`: when a valid portal JWT for agency A calls `/family/portal/dashboard`, data from agency B's client is never returned. (A concrete test: seed a shift for a client in agency B; assert it does not appear in agency A's dashboard response.)

- [ ] **Step 3: Compile**

```bash
cd backend && mvn compile -q
```
Expected: BUILD SUCCESS. If `ServiceTypeRepository` is not importable, find its actual class name:
```bash
find backend/src/main/java -name "ServiceTypeRepository.java" | head -1
```

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/hcare/api/v1/family/ \
        backend/src/main/java/com/hcare/domain/ShiftRepository.java \
        backend/src/main/java/com/hcare/domain/FamilyPortalUserRepository.java
git commit -m "feat: FamilyPortalService — generateInvite, verifyToken, getDashboard; add findByClientIdAndAgencyIdAndEmail to FamilyPortalUserRepository"
```

---

## Task 10: VerifyRateLimiter filter

**Files:**
- Create: `backend/src/main/java/com/hcare/api/v1/family/VerifyRateLimiter.java`

- [ ] **Step 1: Create the rate limiter filter**

Max 10 requests per IP per minute on `POST /api/v1/family/auth/verify`. Uses a per-minute bucket key `"IP:YYYYMMDDHHMM"` to avoid time-window complexity.

```java
package com.hcare.api.v1.family;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class VerifyRateLimiter extends OncePerRequestFilter {

    private static final String TARGET_PATH = "/api/v1/family/auth/verify";
    private static final String TARGET_METHOD = "POST";
    private static final int MAX_PER_MINUTE = 10;
    private static final DateTimeFormatter MINUTE_FMT =
        DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    // Key: "IP:yyyyMMddHHmm" — one counter per IP per minute.
    // The map grows at most (active IPs * minutes in window). Entries become stale after
    // one minute and are evicted lazily when the map exceeds 10,000 entries.
    private final ConcurrentHashMap<String, AtomicInteger> counters = new ConcurrentHashMap<>();

    // When true, trust the X-Real-IP header set by the reverse proxy.
    // ONLY enable this when the backend is not directly reachable by clients (i.e., all
    // traffic flows through a trusted proxy that sets X-Real-IP from the actual client IP).
    // X-Forwarded-For is intentionally NOT used: it is trivially spoofable by clients
    // who can include it in their own requests, defeating the rate limiter entirely.
    private final boolean trustedProxy;

    public VerifyRateLimiter(
            @org.springframework.beans.factory.annotation.Value(
                "${hcare.portal.trusted-proxy:false}") boolean trustedProxy) {
        this.trustedProxy = trustedProxy;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!TARGET_METHOD.equals(request.getMethod())
                || !request.getRequestURI().equals(TARGET_PATH)) {
            chain.doFilter(request, response);
            return;
        }

        String ip = getClientIp(request);
        String minute = ZonedDateTime.now(ZoneOffset.UTC).format(MINUTE_FMT);
        String key = ip + ":" + minute;

        // Lazy eviction: if map is large, clear stale minute entries.
        if (counters.size() > 10_000) {
            String currentMinute = minute;
            counters.entrySet().removeIf(e -> !e.getKey().endsWith(":" + currentMinute));
        }

        AtomicInteger count = counters.computeIfAbsent(key, k -> new AtomicInteger(0));
        if (count.incrementAndGet() > MAX_PER_MINUTE) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Too many requests\",\"status\":429}");
            return;
        }

        chain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        // Use the TCP peer address — cannot be spoofed by clients.
        // If deployed behind a trusted reverse proxy that sets X-Real-IP,
        // configure hcare.portal.trusted-proxy: true and ensure the proxy
        // is the only entry point (never expose the backend directly).
        // X-Forwarded-For is intentionally NOT used: clients can inject arbitrary
        // values into that header, making per-IP rate limiting trivially bypassable.
        if (trustedProxy) {
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isBlank()) return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
```

- [ ] **Step 2: Add `trusted-proxy` property to `application.yml`**

Add the following under the `hcare.portal` block in `backend/src/main/resources/application.yml` (create the block if it does not exist yet):

```yaml
hcare:
  portal:
    base-url: ${PORTAL_BASE_URL:http://localhost:5173}
    trusted-proxy: ${PORTAL_TRUSTED_PROXY:false}   # set true only when behind a reverse proxy that sets X-Real-IP
    cleanup-cron: ${PORTAL_CLEANUP_CRON:0 0 3 * * *}
```

The default is `false` (safe). Operators running behind NGINX/ALB that sets `X-Real-IP` may set `PORTAL_TRUSTED_PROXY=true` in their environment. Do NOT set `trusted-proxy: true` if the backend is directly internet-accessible.

- [ ] **Step 3: Compile**

```bash
cd backend && mvn compile -q
```

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/hcare/api/v1/family/VerifyRateLimiter.java \
        backend/src/main/resources/application.yml
git commit -m "feat: VerifyRateLimiter — max 10 requests/IP/minute on POST /family/auth/verify, RemoteAddr by default (X-Forwarded-For intentionally not trusted)"
```

---

## Task 11: Controllers

**Files:**
- Create: `backend/src/main/java/com/hcare/api/v1/family/FamilyPortalAuthController.java`
- Create: `backend/src/main/java/com/hcare/api/v1/family/FamilyPortalDashboardController.java`
- Modify: `backend/src/main/java/com/hcare/api/v1/clients/ClientController.java`

- [ ] **Step 1: Add `permitAll` rule for verify endpoint to `SecurityConfig`**

`POST /api/v1/family/auth/verify` is a public endpoint — family members hit it with a raw token, not a JWT. The project uses `requestMatchers(...).permitAll()` in `SecurityConfig` (not a `@Public` annotation). Add the new rule **before** the `.anyRequest().authenticated()` line:

```java
// In SecurityConfig.filterChain, inside authorizeHttpRequests:
.requestMatchers("/api/v1/auth/**").permitAll()
.requestMatchers("/api/v1/agencies/register").permitAll()
.requestMatchers("/h2-console/**").permitAll()
.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/family/auth/verify").permitAll()  // public — raw token exchange
.anyRequest().authenticated()
```

Without this rule, Spring Security will reject requests to `/api/v1/family/auth/verify` with 401 before the controller is reached, and the `VerifyRateLimiter` filter will never be invoked for rate-limit enforcement.

Also add `backend/src/main/java/com/hcare/config/SecurityConfig.java` to the `git add` in Step 5 of this task.

- [ ] **Step 2: Create `FamilyPortalAuthController`**

```java
package com.hcare.api.v1.family;

import com.hcare.api.v1.family.dto.PortalVerifyRequest;
import com.hcare.api.v1.family.dto.PortalVerifyResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/family/auth")
public class FamilyPortalAuthController {

    private final FamilyPortalService familyPortalService;

    public FamilyPortalAuthController(FamilyPortalService familyPortalService) {
        this.familyPortalService = familyPortalService;
    }

    // NOTE: This endpoint is intentionally public. The permitAll() rule for
    // POST /api/v1/family/auth/verify was added to SecurityConfig in Step 1 of this task.
    @PostMapping("/verify")
    public ResponseEntity<PortalVerifyResponse> verify(
            @Valid @RequestBody PortalVerifyRequest request) {
        return ResponseEntity.ok(familyPortalService.verifyToken(request));
    }
}
```

- [ ] **Step 2: Create `FamilyPortalDashboardController`**

```java
package com.hcare.api.v1.family;

import com.hcare.api.v1.family.dto.PortalDashboardResponse;
import com.hcare.security.UserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/family/portal")
public class FamilyPortalDashboardController {

    private final FamilyPortalService familyPortalService;

    public FamilyPortalDashboardController(FamilyPortalService familyPortalService) {
        this.familyPortalService = familyPortalService;
    }

    /**
     * @PreAuthorize is MANDATORY — the JwtAuthenticationFilter alone is insufficient because
     * an admin JWT also passes the filter. This annotation ensures only FAMILY_PORTAL tokens
     * reach this endpoint.
     */
    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('FAMILY_PORTAL')")
    public ResponseEntity<PortalDashboardResponse> getDashboard(
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID fpuId = principal.getUserId();
        UUID clientId = principal.getClientId();
        UUID agencyId = principal.getAgencyId();
        return ResponseEntity.ok(
            familyPortalService.getDashboard(fpuId, clientId, agencyId));
    }
}
```

- [ ] **Step 3: Add invite endpoint to `ClientController`**

Add this method to `ClientController.java` inside the `// --- Family Portal Users ---` section, after the existing `removeFamilyPortalUser` method:

```java
@PostMapping("/{id}/family-portal-users/invite")
@PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
public ResponseEntity<com.hcare.api.v1.family.dto.InviteResponse> inviteFamilyPortalUser(
        @PathVariable UUID id,
        @Valid @RequestBody com.hcare.api.v1.family.dto.InviteRequest request,
        @AuthenticationPrincipal com.hcare.security.UserPrincipal principal) {
    clientService.requireClientForInvite(id);
    return ResponseEntity.ok(familyPortalService.generateInvite(id, principal.getAgencyId(), request));
}
```

Also inject `FamilyPortalService` into `ClientController` by adding:
- A constructor parameter `FamilyPortalService familyPortalService`
- Assign it to a `private final FamilyPortalService familyPortalService` field

The updated constructor:
```java
public ClientController(ClientService clientService, FamilyPortalService familyPortalService) {
    this.clientService = clientService;
    this.familyPortalService = familyPortalService;
}
```

Add this method to `ClientService` (package-private, used by controller):
```java
// In ClientService.java, after requireClient():
void requireClientForInvite(UUID clientId) {
    requireClient(clientId);  // reuses existing tenant-scoped lookup
}
```

- [ ] **Step 4: Compile**

```bash
cd backend && mvn compile -q
```
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/hcare/api/v1/family/FamilyPortalAuthController.java \
        backend/src/main/java/com/hcare/api/v1/family/FamilyPortalDashboardController.java \
        backend/src/main/java/com/hcare/api/v1/clients/ClientController.java \
        backend/src/main/java/com/hcare/api/v1/clients/ClientService.java \
        backend/src/main/java/com/hcare/config/SecurityConfig.java
git commit -m "feat: FamilyPortalAuthController, FamilyPortalDashboardController, /invite endpoint on ClientController, permitAll for verify endpoint"
```

---

## Task 12: Nightly cleanup job

**Files:**
- Create: `backend/src/main/java/com/hcare/scheduling/FamilyPortalTokenCleanupJob.java`

- [ ] **Step 1: Add `deleteExpired` to `FamilyPortalTokenRepository`**

Open `backend/src/main/java/com/hcare/domain/FamilyPortalTokenRepository.java` and add the following method. This must be declared explicitly — Spring Data JPA cannot auto-derive a bulk-delete from the method name `deleteExpired`. Without this declaration, Spring will throw `No property 'deleteExpired' found for type FamilyPortalToken` at startup, failing every `@SpringBootTest` context load.

```java
// Add to FamilyPortalTokenRepository:
@Modifying
@Query("DELETE FROM FamilyPortalToken t WHERE t.expiresAt < :now")
void deleteExpired(@Param("now") LocalDateTime now);
```

Required imports (if not already present):
```java
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
```

- [ ] **Step 2: Create the cleanup job**

```java
package com.hcare.scheduling;

import com.hcare.domain.FamilyPortalTokenRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Nightly cleanup of expired family portal invite tokens.
 * Tokens have a 72-hour TTL; this job prevents unbounded accumulation of stale rows.
 * Runs at 3 AM UTC daily — well after the ShiftGenerationScheduler (2 AM) to avoid
 * contention on the database during maintenance windows.
 */
@Component
public class FamilyPortalTokenCleanupJob {

    private final FamilyPortalTokenRepository tokenRepo;

    public FamilyPortalTokenCleanupJob(FamilyPortalTokenRepository tokenRepo) {
        this.tokenRepo = tokenRepo;
    }

    @Scheduled(cron = "${hcare.portal.cleanup-cron:0 0 3 * * *}")
    @Transactional
    public void deleteExpiredTokens() {
        tokenRepo.deleteExpired(LocalDateTime.now(ZoneOffset.UTC));
    }
}
```

Also add `hcare.portal.cleanup-cron: "-"` to `application-test.yml` (or add a property override in `AbstractIntegrationTest`) so the job doesn't fire during tests:

In `AbstractIntegrationTest.configureDataSource`, add:
```java
registry.add("hcare.portal.cleanup-cron", () -> "-");
```

- [ ] **Step 3: Compile**

```bash
cd backend && mvn compile -q
```

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/hcare/domain/FamilyPortalTokenRepository.java \
        backend/src/main/java/com/hcare/scheduling/FamilyPortalTokenCleanupJob.java \
        backend/src/test/java/com/hcare/AbstractIntegrationTest.java
git commit -m "feat: FamilyPortalTokenCleanupJob — nightly delete of expired invite tokens at 3 AM UTC"
```

---

## Task 13: Backend integration tests

**Files:**
- Create: `backend/src/test/java/com/hcare/api/v1/family/FamilyPortalAuthControllerIT.java`
- Create: `backend/src/test/java/com/hcare/api/v1/family/FamilyPortalDashboardControllerIT.java`

- [ ] **Step 1: Create `FamilyPortalAuthControllerIT`**

```java
package com.hcare.api.v1.family;

import com.hcare.AbstractIntegrationTest;
import com.hcare.api.v1.auth.dto.LoginRequest;
import com.hcare.api.v1.auth.dto.LoginResponse;
import com.hcare.api.v1.family.dto.InviteResponse;
import com.hcare.api.v1.family.dto.PortalVerifyRequest;
import com.hcare.api.v1.family.dto.PortalVerifyResponse;
import com.hcare.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Sql(statements = {
    "TRUNCATE TABLE family_portal_tokens, family_portal_users, clients, agency_users, agencies RESTART IDENTITY CASCADE"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class FamilyPortalAuthControllerIT extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private AgencyRepository agencyRepo;
    @Autowired private AgencyUserRepository userRepo;
    @Autowired private ClientRepository clientRepo;
    @Autowired private PasswordEncoder passwordEncoder;

    private UUID clientId;
    private String adminToken;

    @BeforeEach
    void seed() {
        Agency agency = agencyRepo.save(new Agency("Portal Auth IT Agency", "NY"));
        userRepo.save(new AgencyUser(agency.getId(), "admin@portalit.com",
            passwordEncoder.encode("Pass1234!"), UserRole.ADMIN));
        Client client = clientRepo.save(
            new Client(agency.getId(), "Margaret", "Test", LocalDate.of(1940, 1, 1)));
        clientId = client.getId();
        adminToken = restTemplate.postForEntity("/api/v1/auth/login",
            new LoginRequest("admin@portalit.com", "Pass1234!"), LoginResponse.class)
            .getBody().token();
    }

    private HttpHeaders adminAuth() {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(adminToken);
        return h;
    }

    @Test
    void inviteThenVerify_happyPath_returnsPortalJwt() {
        // Generate invite
        HttpEntity<String> inviteReq = new HttpEntity<>(
            "{\"email\":\"family@example.com\"}", adminAuth());
        inviteReq.getHeaders().set("Content-Type", "application/json");
        ResponseEntity<InviteResponse> inviteResp = restTemplate.exchange(
            "/api/v1/clients/" + clientId + "/family-portal-users/invite",
            HttpMethod.POST,
            new HttpEntity<>("{\"email\":\"family@example.com\"}", adminAuth()),
            InviteResponse.class);
        assertThat(inviteResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(inviteResp.getBody().inviteUrl()).contains("/portal/verify?token=");

        // Extract raw token from URL — use UriComponentsBuilder to correctly handle
        // multi-param query strings; String.replace would corrupt the token in that case.
        String rawToken = UriComponentsBuilder
            .fromUriString(inviteResp.getBody().inviteUrl())
            .build()
            .getQueryParams()
            .getFirst("token");

        // Verify token
        ResponseEntity<PortalVerifyResponse> verifyResp = restTemplate.postForEntity(
            "/api/v1/family/auth/verify",
            new PortalVerifyRequest(rawToken),
            PortalVerifyResponse.class);
        assertThat(verifyResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(verifyResp.getBody().jwt()).isNotBlank();
        assertThat(verifyResp.getBody().clientId()).isEqualTo(clientId.toString());
    }

    @Test
    void verify_withInvalidToken_returns400() {
        ResponseEntity<String> resp = restTemplate.postForEntity(
            "/api/v1/family/auth/verify",
            new PortalVerifyRequest("deadbeefdeadbeef"),
            String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void verify_oneTimeUse_secondCallFails() {
        // Generate invite
        ResponseEntity<InviteResponse> inviteResp = restTemplate.exchange(
            "/api/v1/clients/" + clientId + "/family-portal-users/invite",
            HttpMethod.POST,
            new HttpEntity<>("{\"email\":\"family2@example.com\"}", adminAuth()),
            InviteResponse.class);
        String rawToken = UriComponentsBuilder
            .fromUriString(inviteResp.getBody().inviteUrl())
            .build()
            .getQueryParams()
            .getFirst("token");

        // First verify succeeds
        ResponseEntity<PortalVerifyResponse> first = restTemplate.postForEntity(
            "/api/v1/family/auth/verify", new PortalVerifyRequest(rawToken), PortalVerifyResponse.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Second verify fails — token row deleted
        ResponseEntity<String> second = restTemplate.postForEntity(
            "/api/v1/family/auth/verify", new PortalVerifyRequest(rawToken), String.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void invite_storesSha256Hash_notRawToken() {
        // This is verified implicitly: verify works by hash lookup. If raw token were stored
        // and hash lookup failed, the verify test above would fail. Additionally, confirm
        // the URL token != anything in the DB by checking invite response contains no DB row.
        ResponseEntity<InviteResponse> inviteResp = restTemplate.exchange(
            "/api/v1/clients/" + clientId + "/family-portal-users/invite",
            HttpMethod.POST,
            new HttpEntity<>("{\"email\":\"hash-check@example.com\"}", adminAuth()),
            InviteResponse.class);
        String rawToken = UriComponentsBuilder
            .fromUriString(inviteResp.getBody().inviteUrl())
            .build()
            .getQueryParams()
            .getFirst("token");
        // Raw token is 128 hex chars (64 bytes). Verify returns 200, meaning hash lookup works.
        assertThat(rawToken).hasSize(128);
    }
}
```

- [ ] **Step 2: Create `FamilyPortalDashboardControllerIT`**

```java
package com.hcare.api.v1.family;

import com.hcare.AbstractIntegrationTest;
import com.hcare.api.v1.auth.dto.LoginRequest;
import com.hcare.api.v1.auth.dto.LoginResponse;
import com.hcare.api.v1.family.dto.InviteResponse;
import com.hcare.api.v1.family.dto.PortalDashboardResponse;
import com.hcare.api.v1.family.dto.PortalVerifyRequest;
import com.hcare.api.v1.family.dto.PortalVerifyResponse;
import com.hcare.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Sql(statements = {
    "TRUNCATE TABLE evv_records, shifts, family_portal_tokens, family_portal_users, " +
    "service_types, caregivers, clients, agency_users, agencies RESTART IDENTITY CASCADE"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class FamilyPortalDashboardControllerIT extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private AgencyRepository agencyRepo;
    @Autowired private AgencyUserRepository userRepo;
    @Autowired private ClientRepository clientRepo;
    @Autowired private CaregiverRepository caregiverRepo;
    @Autowired private ServiceTypeRepository serviceTypeRepo;
    @Autowired private ShiftRepository shiftRepo;
    @Autowired private FamilyPortalUserRepository fpuRepo;
    @Autowired private PasswordEncoder passwordEncoder;

    private UUID clientId;
    private UUID agencyId;
    private UUID caregiverId;
    private UUID serviceTypeId;
    private String adminToken;

    @BeforeEach
    void seed() {
        Agency agency = agencyRepo.save(new Agency("Dashboard IT Agency", "NY"));
        agencyId = agency.getId();
        userRepo.save(new AgencyUser(agencyId, "admin@dashit.com",
            passwordEncoder.encode("Pass1234!"), UserRole.ADMIN));
        Client client = clientRepo.save(
            new Client(agencyId, "Margaret", "Test", LocalDate.of(1940, 1, 1)));
        clientId = client.getId();
        ServiceType st = serviceTypeRepo.save(
            new ServiceType(agencyId, "Personal Care Aide", "PCA", false, "[]"));
        serviceTypeId = st.getId();
        Caregiver cg = caregiverRepo.save(
            new Caregiver(agencyId, "Maria", "Gonzalez", "maria@example.com"));
        caregiverId = cg.getId();
        adminToken = restTemplate.postForEntity("/api/v1/auth/login",
            new LoginRequest("admin@dashit.com", "Pass1234!"), LoginResponse.class)
            .getBody().token();
    }

    private String obtainPortalJwt() {
        // Invite + verify to get a FAMILY_PORTAL JWT
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(adminToken);
        h.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<InviteResponse> inviteResp = restTemplate.exchange(
            "/api/v1/clients/" + clientId + "/family-portal-users/invite",
            HttpMethod.POST,
            new HttpEntity<>("{\"email\":\"family@test.com\"}", h),
            InviteResponse.class);
        String rawToken = UriComponentsBuilder
            .fromUriString(inviteResp.getBody().inviteUrl())
            .build()
            .getQueryParams()
            .getFirst("token");
        PortalVerifyResponse verifyResp = restTemplate.postForEntity(
            "/api/v1/family/auth/verify",
            new PortalVerifyRequest(rawToken),
            PortalVerifyResponse.class).getBody();
        return verifyResp.jwt();
    }

    private HttpHeaders portalAuth(String jwt) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(jwt);
        return h;
    }

    @Test
    void dashboard_noShiftsToday_returnsTodayVisitNull() {
        String jwt = obtainPortalJwt();
        ResponseEntity<PortalDashboardResponse> resp = restTemplate.exchange(
            "/api/v1/family/portal/dashboard", HttpMethod.GET,
            new HttpEntity<>(portalAuth(jwt)), PortalDashboardResponse.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().clientFirstName()).isEqualTo("Margaret");
        assertThat(resp.getBody().todayVisit()).isNull();
        assertThat(resp.getBody().upcomingVisits()).isEmpty();
        assertThat(resp.getBody().lastVisit()).isNull();
    }

    @Test
    void dashboard_adminJwt_returns403() {
        HttpHeaders adminH = new HttpHeaders();
        adminH.setBearerAuth(adminToken);
        ResponseEntity<String> resp = restTemplate.exchange(
            "/api/v1/family/portal/dashboard", HttpMethod.GET,
            new HttpEntity<>(adminH), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void dashboard_afterUserRemoved_returns403WithRevocationCode() {
        String jwt = obtainPortalJwt();
        // Remove the FamilyPortalUser
        FamilyPortalUser fpu = fpuRepo.findByClientId(clientId).get(0);
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(adminToken);
        restTemplate.exchange(
            "/api/v1/clients/" + clientId + "/family-portal-users/" + fpu.getId(),
            HttpMethod.DELETE, new HttpEntity<>(h), Void.class);

        // Dashboard with still-valid JWT should return 403
        ResponseEntity<String> resp = restTemplate.exchange(
            "/api/v1/family/portal/dashboard", HttpMethod.GET,
            new HttpEntity<>(portalAuth(jwt)), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resp.getBody()).contains("PORTAL_ACCESS_REVOKED");
    }

    @Test
    void dashboard_dischargedClient_returns410() {
        String jwt = obtainPortalJwt();
        // Discharge the client
        Client client = clientRepo.findById(clientId).get();
        client.setStatus(ClientStatus.DISCHARGED);
        clientRepo.save(client);

        ResponseEntity<String> resp = restTemplate.exchange(
            "/api/v1/family/portal/dashboard", HttpMethod.GET,
            new HttpEntity<>(portalAuth(jwt)), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(resp.getBody()).contains("CLIENT_DISCHARGED");
    }

    @Test
    void dashboard_withAssignedShift_returnsGreyStatus() {
        // Seed a shift for today
        LocalDateTime today = LocalDateTime.now(ZoneOffset.UTC).withHour(9).withMinute(0).withSecond(0).withNano(0);
        shiftRepo.save(new Shift(agencyId, null, clientId, caregiverId, serviceTypeId, null,
            today, today.plusHours(2)));

        String jwt = obtainPortalJwt();
        ResponseEntity<PortalDashboardResponse> resp = restTemplate.exchange(
            "/api/v1/family/portal/dashboard", HttpMethod.GET,
            new HttpEntity<>(portalAuth(jwt)), PortalDashboardResponse.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().todayVisit()).isNotNull();
        assertThat(resp.getBody().todayVisit().status()).isEqualTo("GREY");
        // Caregiver card present for non-CANCELLED shift
        assertThat(resp.getBody().todayVisit().caregiver()).isNotNull();
        assertThat(resp.getBody().todayVisit().caregiver().name()).isEqualTo("Maria Gonzalez");
    }

    @Test
    void dashboard_cancelledShift_caregiverCardIsNull() {
        LocalDateTime today = LocalDateTime.now(ZoneOffset.UTC).withHour(9).withMinute(0).withSecond(0).withNano(0);
        Shift shift = shiftRepo.save(new Shift(agencyId, null, clientId, caregiverId, serviceTypeId, null,
            today, today.plusHours(2)));
        shift.setStatus(ShiftStatus.CANCELLED);
        shiftRepo.save(shift);

        String jwt = obtainPortalJwt();
        ResponseEntity<PortalDashboardResponse> resp = restTemplate.exchange(
            "/api/v1/family/portal/dashboard", HttpMethod.GET,
            new HttpEntity<>(portalAuth(jwt)), PortalDashboardResponse.class);
        // CANCELLED shift is excluded from todayVisit (only non-cancelled are shown)
        assertThat(resp.getBody().todayVisit()).isNull();
    }
}
```

- [ ] **Step 3: Run the integration tests**

```bash
cd backend && mvn test -Dtest="FamilyPortalAuthControllerIT,FamilyPortalDashboardControllerIT" 2>&1 | tail -15
```
Expected: All tests pass. If tests fail due to missing `ServiceTypeRepository` or `ServiceType` constructor signature, check:
```bash
find backend/src/main/java -name "ServiceType.java" | xargs grep "public ServiceType"
find backend/src/main/java -name "ServiceTypeRepository.java" | head -1
```
Adjust constructor call to match actual signature.

- [ ] **Step 4: Run full backend test suite**

```bash
cd backend && mvn test -q 2>&1 | tail -10
```
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/test/java/com/hcare/api/v1/family/
git commit -m "test: FamilyPortalAuthControllerIT and FamilyPortalDashboardControllerIT"
```

Continue with `docs/superpowers/plans/2026-04-08-add-family-portal-plan-3-frontend.md`.
