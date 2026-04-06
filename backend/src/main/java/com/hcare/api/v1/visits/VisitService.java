package com.hcare.api.v1.visits;

import com.hcare.api.v1.visits.dto.ClockInRequest;
import com.hcare.api.v1.visits.dto.ClockOutRequest;
import com.hcare.api.v1.visits.dto.ShiftDetailResponse;
import com.hcare.audit.PhiAuditService;
import com.hcare.audit.ResourceType;
import com.hcare.domain.Agency;
import com.hcare.domain.AgencyRepository;
import com.hcare.domain.Authorization;
import com.hcare.domain.AuthorizationRepository;
import com.hcare.domain.AuthorizationUnitFailedEvent;
import com.hcare.domain.AuthorizationUnitService;
import com.hcare.domain.Client;
import com.hcare.domain.ClientRepository;
import com.hcare.domain.EvvRecord;
import com.hcare.domain.EvvRecordRepository;
import com.hcare.domain.Payer;
import com.hcare.domain.PayerRepository;
import com.hcare.domain.PayerType;
import com.hcare.domain.Shift;
import com.hcare.domain.ShiftCompletedEvent;
import com.hcare.domain.ShiftRepository;
import com.hcare.domain.ShiftStatus;
import com.hcare.evv.EvvComplianceService;
import com.hcare.evv.EvvComplianceStatus;
import com.hcare.evv.EvvStateConfig;
import com.hcare.evv.EvvStateConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Service
public class VisitService {

    private static final Logger log = LoggerFactory.getLogger(VisitService.class);

    private final ShiftRepository shiftRepository;
    private final EvvRecordRepository evvRecordRepository;
    private final ClientRepository clientRepository;
    private final AgencyRepository agencyRepository;
    private final AuthorizationRepository authorizationRepository;
    private final PayerRepository payerRepository;
    private final EvvStateConfigRepository evvStateConfigRepository;
    private final EvvComplianceService evvComplianceService;
    private final PhiAuditService phiAuditService;
    private final AuthorizationUnitService authorizationUnitService;
    private final ApplicationEventPublisher eventPublisher;

    // TODO(P2): 11 constructor parameters — consider splitting VisitService into
    // ClockInService / ClockOutService / ShiftReadService if complexity grows further.
    public VisitService(ShiftRepository shiftRepository,
                        EvvRecordRepository evvRecordRepository,
                        ClientRepository clientRepository,
                        AgencyRepository agencyRepository,
                        AuthorizationRepository authorizationRepository,
                        PayerRepository payerRepository,
                        EvvStateConfigRepository evvStateConfigRepository,
                        EvvComplianceService evvComplianceService,
                        PhiAuditService phiAuditService,
                        AuthorizationUnitService authorizationUnitService,
                        ApplicationEventPublisher eventPublisher) {
        this.shiftRepository = shiftRepository;
        this.evvRecordRepository = evvRecordRepository;
        this.clientRepository = clientRepository;
        this.agencyRepository = agencyRepository;
        this.authorizationRepository = authorizationRepository;
        this.payerRepository = payerRepository;
        this.evvStateConfigRepository = evvStateConfigRepository;
        this.evvComplianceService = evvComplianceService;
        this.phiAuditService = phiAuditService;
        this.authorizationUnitService = authorizationUnitService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ShiftDetailResponse clockIn(UUID shiftId, UUID userId, String ipAddress,
                                        ClockInRequest req) {
        Shift shift = shiftRepository.findById(shiftId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shift not found"));

        if (shift.getStatus() != ShiftStatus.ASSIGNED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Cannot clock in: shift must be ASSIGNED (current status: " + shift.getStatus() + ")");
        }
        if (evvRecordRepository.findByShiftId(shiftId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Clock-in already recorded for shift " + shiftId);
        }

        Client client = clientRepository.findById(shift.getClientId())
            .orElseThrow(() -> new IllegalStateException("Client not found: " + shift.getClientId()));

        EvvRecord record = new EvvRecord(shiftId, shift.getAgencyId(), req.verificationMethod());
        record.setLocationLat(req.lat());
        record.setLocationLon(req.lon());
        record.setClientMedicaidId(client.getMedicaidId());
        record.setCapturedOffline(req.capturedOffline());
        if (req.capturedOffline() && req.deviceCapturedAt() != null) {
            record.setTimeIn(req.deviceCapturedAt());
            record.setDeviceCapturedAt(req.deviceCapturedAt());
        } else {
            record.setTimeIn(LocalDateTime.now(ZoneOffset.UTC));
        }

        evvRecordRepository.save(record);
        shift.setStatus(ShiftStatus.IN_PROGRESS);
        shiftRepository.save(shift);

        phiAuditService.logWrite(userId, shift.getAgencyId(), ResourceType.EVVRECORD,
            record.getId(), ipAddress);

        return buildShiftDetail(shift, record, client);
    }

    @Transactional
    public ShiftDetailResponse clockOut(UUID shiftId, UUID userId, String ipAddress,
                                         ClockOutRequest req) {
        Shift shift = shiftRepository.findById(shiftId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shift not found"));

        if (shift.getStatus() != ShiftStatus.IN_PROGRESS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Cannot clock out: shift status is " + shift.getStatus());
        }

        EvvRecord record = evvRecordRepository.findByShiftId(shiftId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                "No clock-in record found for shift " + shiftId));

        if (req.capturedOffline() && req.deviceCapturedAt() != null) {
            record.setTimeOut(req.deviceCapturedAt());
            record.setCapturedOffline(true);
        } else {
            record.setTimeOut(LocalDateTime.now(ZoneOffset.UTC));
        }
        evvRecordRepository.save(record);

        shift.setStatus(ShiftStatus.COMPLETED);
        shiftRepository.save(shift);

        // Notify scoring module to update CaregiverScoringProfile + CaregiverClientAffinity.
        // @TransactionalEventListener on LocalScoringService.onShiftCompleted fires AFTER_COMMIT.
        if (shift.getCaregiverId() != null) {
            eventPublisher.publishEvent(new ShiftCompletedEvent(
                shiftId, shift.getCaregiverId(), shift.getClientId(), shift.getAgencyId(),
                record.getTimeIn(), record.getTimeOut()));
        }

        if (shift.getAuthorizationId() != null) {
            final UUID authorizationId = shift.getAuthorizationId();
            final UUID capturedShiftId = shiftId;
            final LocalDateTime timeIn = record.getTimeIn();
            final LocalDateTime timeOut = record.getTimeOut();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    // This callback runs synchronously on the clockOut request thread after the outer
                    // transaction commits. Up to 2 retries × 50 ms = 100 ms of latency may be added
                    // to the clockOut HTTP response in the contended case.
                    // TODO(P2): Move to @Async @TransactionalEventListener to remove from response path.
                    for (int attempt = 0; attempt < 3; attempt++) {
                        try {
                            authorizationUnitService.addUnits(authorizationId, timeIn, timeOut);
                            return;
                        } catch (OptimisticLockingFailureException e) {
                            if (attempt == 2) {
                                log.error("Exhausted retries updating authorization units for " +
                                    "authorization={} shift={} — clock-out is committed, units under-reported",
                                    authorizationId, capturedShiftId, e);
                                eventPublisher.publishEvent(new AuthorizationUnitFailedEvent(
                                    authorizationId, capturedShiftId, timeIn, timeOut));
                                return;
                            }
                            try { Thread.sleep(50); } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                eventPublisher.publishEvent(new AuthorizationUnitFailedEvent(
                                    authorizationId, capturedShiftId, timeIn, timeOut));
                                return;
                            }
                        }
                    }
                }
            });
        }

        phiAuditService.logWrite(userId, shift.getAgencyId(), ResourceType.EVVRECORD,
            record.getId(), ipAddress);

        Client client = clientRepository.findById(shift.getClientId())
            .orElseThrow(() -> new IllegalStateException("Client not found: " + shift.getClientId()));
        return buildShiftDetail(shift, record, client);
    }

    @Transactional(readOnly = true)
    public ShiftDetailResponse getShiftDetail(UUID shiftId, UUID userId, String ipAddress) {
        Shift shift = shiftRepository.findById(shiftId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shift not found"));

        EvvRecord record = evvRecordRepository.findByShiftId(shiftId).orElse(null);

        if (record != null) {
            phiAuditService.logRead(userId, shift.getAgencyId(), ResourceType.EVVRECORD,
                record.getId(), ipAddress);
        }

        Client client = clientRepository.findById(shift.getClientId())
            .orElseThrow(() -> new IllegalStateException("Client not found: " + shift.getClientId()));
        return buildShiftDetail(shift, record, client);
    }

    private ShiftDetailResponse buildShiftDetail(Shift shift, EvvRecord record, Client client) {
        Agency agency = agencyRepository.findById(shift.getAgencyId())
            .orElseThrow(() -> new IllegalStateException("Agency not found: " + shift.getAgencyId()));

        String stateCode = client.getServiceState() != null ? client.getServiceState() : agency.getState();
        EvvStateConfig stateConfig = evvStateConfigRepository.findByStateCode(stateCode)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "EVV configuration not found for state: " + stateCode +
                ". Contact support to configure EVV for this state."));

        PayerType payerType = Optional.ofNullable(shift.getAuthorizationId())
            .flatMap(authorizationRepository::findById)
            .flatMap(auth -> payerRepository.findById(auth.getPayerId()))
            .map(Payer::getPayerType)
            .orElse(null);

        EvvComplianceStatus status = evvComplianceService.compute(
            record, stateConfig, shift, payerType, client.getLat(), client.getLng());

        ShiftDetailResponse.EvvSummary evvSummary = new ShiftDetailResponse.EvvSummary(
            record != null ? record.getId() : null,
            status,
            record != null ? record.getTimeIn() : null,
            record != null ? record.getTimeOut() : null,
            record != null ? record.getVerificationMethod() : null,
            record != null && record.isCapturedOffline()
        );

        return new ShiftDetailResponse(
            shift.getId(), shift.getAgencyId(), shift.getClientId(),
            shift.getCaregiverId(), shift.getServiceTypeId(), shift.getAuthorizationId(),
            shift.getSourcePatternId(), shift.getScheduledStart(), shift.getScheduledEnd(),
            shift.getStatus(), shift.getNotes(), evvSummary
        );
    }
}
