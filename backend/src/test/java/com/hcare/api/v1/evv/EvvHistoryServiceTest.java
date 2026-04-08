package com.hcare.api.v1.evv;

import com.hcare.api.v1.evv.dto.EvvHistoryRow;
import com.hcare.domain.Agency;
import com.hcare.domain.AgencyRepository;
import com.hcare.domain.AuthorizationRepository;
import com.hcare.domain.Caregiver;
import com.hcare.domain.CaregiverRepository;
import com.hcare.domain.Client;
import com.hcare.domain.ClientRepository;
import com.hcare.domain.EvvRecord;
import com.hcare.domain.EvvRecordRepository;
import com.hcare.domain.PayerRepository;
import com.hcare.domain.ServiceType;
import com.hcare.domain.ServiceTypeRepository;
import com.hcare.domain.Shift;
import com.hcare.domain.ShiftRepository;
import com.hcare.evv.EvvComplianceService;
import com.hcare.evv.EvvComplianceStatus;
import com.hcare.evv.EvvStateConfig;
import com.hcare.evv.EvvStateConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        service = new EvvHistoryService(
            shiftRepository, clientRepository, caregiverRepository,
            serviceTypeRepository, evvRecordRepository, evvStateConfigRepository,
            authorizationRepository, payerRepository, agencyRepository, evvComplianceService);
    }

    @Test
    void getHistory_returnsEmptyPageWhenNoShifts() {
        Page<Shift> emptyPage = Page.empty();
        when(shiftRepository.findByAgencyIdAndScheduledStartBetween(
            eq(agencyId), eq(start), eq(end), any(Pageable.class))).thenReturn(emptyPage);

        Page<EvvHistoryRow> result = service.getHistory(agencyId, start, end, PageRequest.of(0, 50));

        assertThat(result.getContent()).isEmpty();
        verify(clientRepository, never()).findAllById(anyCollection());
        verify(caregiverRepository, never()).findAllById(anyCollection());
        verify(evvRecordRepository, never()).findByShiftIdIn(any());
    }

    @Test
    void getHistory_computesGreyStatusWhenNoEvvRecord() {
        UUID clientId = UUID.randomUUID();
        UUID serviceTypeId = UUID.randomUUID();

        // Mock Shift so getId() returns a stable UUID
        Shift shift = mock(Shift.class);
        UUID shiftId = UUID.randomUUID();
        when(shift.getId()).thenReturn(shiftId);
        when(shift.getClientId()).thenReturn(clientId);
        when(shift.getCaregiverId()).thenReturn(null);
        when(shift.getServiceTypeId()).thenReturn(serviceTypeId);
        when(shift.getAuthorizationId()).thenReturn(null);
        when(shift.getScheduledStart()).thenReturn(start);
        when(shift.getScheduledEnd()).thenReturn(end);

        stubShiftsPage(shift);

        // Agency in TX
        Agency agency = new Agency("Sunrise Home Care", "TX");
        when(agencyRepository.findById(agencyId)).thenReturn(Optional.of(agency));

        // Client in TX (no serviceState override — falls back to agency state)
        // client.getId() must equal clientId so clientMap lookup succeeds — use a mock
        Client clientMock = mock(Client.class);
        when(clientMock.getId()).thenReturn(clientId);
        when(clientMock.getServiceState()).thenReturn(null);
        when(clientMock.getFirstName()).thenReturn("Alice");
        when(clientMock.getLastName()).thenReturn("Smith");
        when(clientMock.getLat()).thenReturn(null);
        when(clientMock.getLng()).thenReturn(null);
        when(clientRepository.findAllById(Set.of(clientId))).thenReturn(List.of(clientMock));

        ServiceType serviceType = mock(ServiceType.class);
        when(serviceType.getId()).thenReturn(serviceTypeId);
        when(serviceType.getName()).thenReturn("Personal Care");
        when(serviceTypeRepository.findAllById(Set.of(serviceTypeId))).thenReturn(List.of(serviceType));

        when(caregiverRepository.findAllById(Set.of())).thenReturn(List.of());
        // No EVV record for this shift
        when(evvRecordRepository.findByShiftIdIn(Set.of(shiftId))).thenReturn(List.of());
        when(authorizationRepository.findAllById(Set.of())).thenReturn(List.of());
        when(payerRepository.findAllById(Set.of())).thenReturn(List.of());

        // State config exists for TX
        EvvStateConfig txConfig = mock(EvvStateConfig.class);
        when(evvStateConfigRepository.findByStateCode("TX")).thenReturn(Optional.of(txConfig));

        // compliance service returns GREY when record is null
        when(evvComplianceService.compute(null, txConfig, shift, null, null, null))
            .thenReturn(EvvComplianceStatus.GREY);

        Page<EvvHistoryRow> result = service.getHistory(agencyId, start, end, PageRequest.of(0, 50));

        assertThat(result.getContent()).hasSize(1);
        EvvHistoryRow row = result.getContent().getFirst();
        assertThat(row.evvStatus()).isEqualTo(EvvComplianceStatus.GREY);
    }

    @Test
    void getHistory_setsStatusReasonWhenNoStateConfig() {
        UUID clientId = UUID.randomUUID();
        UUID serviceTypeId = UUID.randomUUID();

        Shift shift = mock(Shift.class);
        UUID shiftId = UUID.randomUUID();
        when(shift.getId()).thenReturn(shiftId);
        when(shift.getClientId()).thenReturn(clientId);
        when(shift.getCaregiverId()).thenReturn(null);
        when(shift.getServiceTypeId()).thenReturn(serviceTypeId);
        when(shift.getAuthorizationId()).thenReturn(null);
        when(shift.getScheduledStart()).thenReturn(start);
        when(shift.getScheduledEnd()).thenReturn(end);

        stubShiftsPage(shift);

        Agency agency = new Agency("Harmony", "TX");
        when(agencyRepository.findById(agencyId)).thenReturn(Optional.of(agency));

        // Client has serviceState "ZZ" — no config for that state
        Client clientMock = mock(Client.class);
        when(clientMock.getId()).thenReturn(clientId);
        when(clientMock.getServiceState()).thenReturn("ZZ");
        when(clientMock.getFirstName()).thenReturn("Bob");
        when(clientMock.getLastName()).thenReturn("Jones");
        when(clientRepository.findAllById(Set.of(clientId))).thenReturn(List.of(clientMock));

        ServiceType serviceType = mock(ServiceType.class);
        when(serviceType.getId()).thenReturn(serviceTypeId);
        when(serviceType.getName()).thenReturn("Companion Care");
        when(serviceTypeRepository.findAllById(Set.of(serviceTypeId))).thenReturn(List.of(serviceType));

        when(caregiverRepository.findAllById(Set.of())).thenReturn(List.of());
        when(evvRecordRepository.findByShiftIdIn(Set.of(shiftId))).thenReturn(List.of());
        when(authorizationRepository.findAllById(Set.of())).thenReturn(List.of());
        when(payerRepository.findAllById(Set.of())).thenReturn(List.of());

        // No state config for "ZZ"
        when(evvStateConfigRepository.findByStateCode("ZZ")).thenReturn(Optional.empty());

        Page<EvvHistoryRow> result = service.getHistory(agencyId, start, end, PageRequest.of(0, 50));

        assertThat(result.getContent()).hasSize(1);
        EvvHistoryRow row = result.getContent().getFirst();
        assertThat(row.evvStatus()).isEqualTo(EvvComplianceStatus.GREY);
        assertThat(row.evvStatusReason()).contains("No EVV state config for state: ZZ");
    }

    @Test
    void getHistory_clientServiceStateOverridesAgencyState() {
        UUID clientId = UUID.randomUUID();
        UUID serviceTypeId = UUID.randomUUID();

        Shift shift = mock(Shift.class);
        UUID shiftId = UUID.randomUUID();
        when(shift.getId()).thenReturn(shiftId);
        when(shift.getClientId()).thenReturn(clientId);
        when(shift.getCaregiverId()).thenReturn(null);
        when(shift.getServiceTypeId()).thenReturn(serviceTypeId);
        when(shift.getAuthorizationId()).thenReturn(null);
        when(shift.getScheduledStart()).thenReturn(start);
        when(shift.getScheduledEnd()).thenReturn(end);

        stubShiftsPage(shift);

        // Agency is in TX
        Agency agency = new Agency("Sunrise", "TX");
        when(agencyRepository.findById(agencyId)).thenReturn(Optional.of(agency));

        // Client's serviceState is "NY" — must override agency state TX
        Client clientMock = mock(Client.class);
        when(clientMock.getId()).thenReturn(clientId);
        when(clientMock.getServiceState()).thenReturn("NY");
        when(clientMock.getFirstName()).thenReturn("Carol");
        when(clientMock.getLastName()).thenReturn("White");
        when(clientMock.getLat()).thenReturn(null);
        when(clientMock.getLng()).thenReturn(null);
        when(clientRepository.findAllById(Set.of(clientId))).thenReturn(List.of(clientMock));

        ServiceType serviceType = mock(ServiceType.class);
        when(serviceType.getId()).thenReturn(serviceTypeId);
        when(serviceType.getName()).thenReturn("Skilled Nursing");
        when(serviceTypeRepository.findAllById(Set.of(serviceTypeId))).thenReturn(List.of(serviceType));

        when(caregiverRepository.findAllById(Set.of())).thenReturn(List.of());
        when(evvRecordRepository.findByShiftIdIn(Set.of(shiftId))).thenReturn(List.of());
        when(authorizationRepository.findAllById(Set.of())).thenReturn(List.of());
        when(payerRepository.findAllById(Set.of())).thenReturn(List.of());

        EvvStateConfig nyConfig = mock(EvvStateConfig.class);
        when(evvStateConfigRepository.findByStateCode("NY")).thenReturn(Optional.of(nyConfig));
        when(evvComplianceService.compute(null, nyConfig, shift, null, null, null))
            .thenReturn(EvvComplianceStatus.GREY);

        service.getHistory(agencyId, start, end, PageRequest.of(0, 50));

        // Must have called NY, never TX
        verify(evvStateConfigRepository).findByStateCode("NY");
        verify(evvStateConfigRepository, never()).findByStateCode("TX");
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private void stubShiftsPage(Shift... shifts) {
        Page<Shift> page = new PageImpl<>(List.of(shifts), PageRequest.of(0, 50), shifts.length);
        when(shiftRepository.findByAgencyIdAndScheduledStartBetween(
            eq(agencyId), eq(start), eq(end), any(Pageable.class))).thenReturn(page);
    }
}
