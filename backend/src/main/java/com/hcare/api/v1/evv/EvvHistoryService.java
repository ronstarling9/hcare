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
import com.hcare.evv.EvvComplianceService;
import com.hcare.evv.EvvComplianceStatus;
import com.hcare.evv.EvvStateConfig;
import com.hcare.evv.EvvStateConfigRepository;
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
  public Page<EvvHistoryRow> getHistory(
      UUID agencyId, LocalDateTime start, LocalDateTime end, Pageable pageable) {
    // Always sort by scheduledStart DESC. Forwarding client-supplied sort fields to the
    // JPA repository risks PropertyReferenceException for non-Shift column names (500
    // instead of 400). The frontend never passes a sort param, so the flexibility is unused.
    Page<Shift> shiftPage =
        shiftRepository.findByAgencyIdAndScheduledStartBetween(
            agencyId,
            start,
            end,
            PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by("scheduledStart").descending()));

    List<Shift> shifts = shiftPage.getContent();

    if (shifts.isEmpty()) {
      return Page.empty(pageable);
    }

    // Resolve the agency's own state as a fallback for clients without serviceState override
    String agencyState =
        agencyRepository.findById(agencyId).map(Agency::getState).orElse(null);

    // Build lookup maps — fetch only the IDs present on this page, not all agency entities
    Set<UUID> clientIds =
        shifts.stream()
            .map(Shift::getClientId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    Set<UUID> caregiverIds =
        shifts.stream()
            .map(Shift::getCaregiverId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    Set<UUID> serviceTypeIds =
        shifts.stream()
            .map(Shift::getServiceTypeId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

    Map<UUID, Client> clientMap =
        clientRepository.findAllById(clientIds).stream()
            .collect(Collectors.toMap(Client::getId, c -> c));

    Map<UUID, Caregiver> caregiverMap =
        caregiverRepository.findAllById(caregiverIds).stream()
            .collect(Collectors.toMap(Caregiver::getId, c -> c));

    Map<UUID, ServiceType> serviceTypeMap =
        serviceTypeRepository.findAllById(serviceTypeIds).stream()
            .collect(Collectors.toMap(ServiceType::getId, s -> s));

    // Fetch EVV records for these shifts
    Set<UUID> shiftIds = shifts.stream().map(Shift::getId).collect(Collectors.toSet());
    Map<UUID, EvvRecord> evvByShiftId =
        evvRecordRepository.findByShiftIdIn(shiftIds).stream()
            .collect(Collectors.toMap(EvvRecord::getShiftId, r -> r));

    // Authorization and payer lookup — only auth IDs referenced by this page's shifts
    Set<UUID> authIds =
        shifts.stream()
            .map(Shift::getAuthorizationId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

    Map<UUID, Authorization> authById =
        authorizationRepository.findAllById(authIds).stream()
            .collect(Collectors.toMap(Authorization::getId, a -> a));

    Set<UUID> payerIds =
        authById.values().stream()
            .map(Authorization::getPayerId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

    Map<UUID, Payer> payerById =
        payerRepository.findAllById(payerIds).stream()
            .collect(Collectors.toMap(Payer::getId, p -> p));

    // EVV state config cache — keyed by Optional so that absent-config lookups are also
    // stored (HashMap.computeIfAbsent does not insert null-returning mapping functions).
    Map<String, Optional<EvvStateConfig>> stateConfigCache = new HashMap<>();

    List<EvvHistoryRow> rows =
        shifts.stream()
            .map(
                shift -> {
                  Client client = clientMap.get(shift.getClientId());
                  Caregiver caregiver =
                      shift.getCaregiverId() != null
                          ? caregiverMap.get(shift.getCaregiverId())
                          : null;
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
                    effectiveState =
                        client.getServiceState() != null
                            ? client.getServiceState()
                            : agencyState;
                  }
                  if (effectiveState != null) {
                    Optional<EvvStateConfig> stateConfigOpt =
                        stateConfigCache.computeIfAbsent(
                            effectiveState, evvStateConfigRepository::findByStateCode);
                    if (stateConfigOpt.isPresent()) {
                      status =
                          evvComplianceService.compute(
                              evvRecord,
                              stateConfigOpt.get(),
                              shift,
                              payerType,
                              client.getLat(),
                              client.getLng());
                    } else {
                      statusReason = "No EVV state config for state: " + effectiveState;
                    }
                  } else {
                    statusReason =
                        client == null ? "Client not found" : "Agency state not configured";
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
                      evvRecord != null ? evvRecord.getVerificationMethod() : null,
                      shift.getStatus() != null ? shift.getStatus().name() : null);
                })
            .toList();

    return new PageImpl<>(rows, pageable, shiftPage.getTotalElements());
  }
}
