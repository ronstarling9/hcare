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
