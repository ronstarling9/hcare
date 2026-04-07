package com.hcare.api.v1.dashboard;

import com.hcare.api.v1.dashboard.dto.AlertType;
import com.hcare.api.v1.dashboard.dto.DashboardAlert;
import com.hcare.api.v1.dashboard.dto.DashboardTodayResponse;
import com.hcare.api.v1.dashboard.dto.DashboardVisitRow;
import com.hcare.domain.Authorization;
import com.hcare.domain.AuthorizationRepository;
import com.hcare.domain.BackgroundCheck;
import com.hcare.domain.BackgroundCheckRepository;
import com.hcare.domain.Caregiver;
import com.hcare.domain.CaregiverCredential;
import com.hcare.domain.CaregiverCredentialRepository;
import com.hcare.domain.CaregiverRepository;
import com.hcare.domain.Client;
import com.hcare.domain.ClientRepository;
import com.hcare.domain.EvvRecord;
import com.hcare.domain.EvvRecordRepository;
import com.hcare.domain.Payer;
import com.hcare.domain.PayerRepository;
import com.hcare.domain.PayerType;
import com.hcare.domain.Shift;
import com.hcare.domain.ShiftRepository;
import com.hcare.evv.EvvComplianceService;
import com.hcare.evv.EvvComplianceStatus;
import com.hcare.evv.EvvStateConfig;
import com.hcare.evv.EvvStateConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);

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
        LocalDateTime endOfDay = startOfDay.plusDays(1).minusNanos(1);

        // Fetch today's shifts — unpaged in practice (< 500 for a small agency)
        List<Shift> todayShifts = shiftRepository
                .findByAgencyIdAndScheduledStartBetween(
                        agencyId, startOfDay, endOfDay,
                        PageRequest.of(0, 500, Sort.by("scheduledStart")))
                .getContent();

        // Build lookup maps
        Map<UUID, Client> clientMap = clientRepository.findByAgencyId(agencyId).stream()
                .collect(Collectors.toMap(Client::getId, c -> c));

        Map<UUID, Caregiver> caregiverMap = caregiverRepository.findByAgencyId(agencyId).stream()
                .collect(Collectors.toMap(Caregiver::getId, c -> c));

        // Fetch authorizations and payers for EVV computation
        Map<UUID, Authorization> authById = authorizationRepository.findByAgencyId(agencyId).stream()
                .collect(Collectors.toMap(Authorization::getId, a -> a));

        Map<UUID, Payer> payerById = payerRepository.findByAgencyId(agencyId).stream()
                .collect(Collectors.toMap(Payer::getId, p -> p));

        // EVV state config cache (keyed by state code); findByStateCode returns Optional so unwrap
        Map<String, EvvStateConfig> stateConfigCache = new HashMap<>();

        // Bulk-fetch EVV records for all today's shifts — avoids N+1 queries
        Set<UUID> shiftIds = todayShifts.stream().map(Shift::getId).collect(Collectors.toSet());
        Map<UUID, EvvRecord> evvByShiftId = evvRecordRepository.findByShiftIdIn(shiftIds).stream()
                .collect(Collectors.toMap(EvvRecord::getShiftId, r -> r));

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

            // Resolve state config (lazily cached by state code within this request)
            EvvComplianceStatus evvStatus = EvvComplianceStatus.GREY;
            if (client != null && client.getServiceState() != null) {
                String stateCode = client.getServiceState();
                EvvStateConfig stateConfig = stateConfigCache.computeIfAbsent(
                        stateCode,
                        code -> evvStateConfigRepository.findByStateCode(code).orElse(null));
                if (stateConfig == null) {
                    log.warn("No EvvStateConfig found for state code '{}' — EVV status will be GREY for shift {}",
                            stateCode, shift.getId());
                } else {
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
                default -> { /* CANCELLED / MISSED — still shown in list */ }
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
                    AlertType.CREDENTIAL_EXPIRING,
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
                    AlertType.BACKGROUND_CHECK_DUE,
                    bc.getId(),
                    name,
                    bc.getCheckType().name() + " renewal due",
                    bc.getRenewalDueDate()
            ));
        }

        // Authorizations with >= 80% utilization
        List<Authorization> allAuths = new ArrayList<>(authById.values());
        for (Authorization auth : allAuths) {
            if (auth.getAuthorizedUnits().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal utilization = auth.getUsedUnits()
                        .divide(auth.getAuthorizedUnits(), 4, RoundingMode.HALF_UP);
                if (utilization.compareTo(AUTH_LOW_THRESHOLD) >= 0) {
                    Client cl = clientMap.get(auth.getClientId());
                    String name = cl != null ? cl.getFirstName() + " " + cl.getLastName() : "Unknown Client";
                    int pct = utilization.multiply(new BigDecimal("100")).intValue();
                    alerts.add(new DashboardAlert(
                            AlertType.AUTHORIZATION_LOW,
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
