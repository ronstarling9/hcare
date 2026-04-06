package com.hcare.api.v1.caregivers;

import com.hcare.api.v1.caregivers.dto.AddCredentialRequest;
import com.hcare.api.v1.caregivers.dto.AvailabilityBlockRequest;
import com.hcare.api.v1.caregivers.dto.AvailabilityResponse;
import com.hcare.api.v1.caregivers.dto.BackgroundCheckResponse;
import com.hcare.api.v1.caregivers.dto.CaregiverResponse;
import com.hcare.api.v1.caregivers.dto.CreateCaregiverRequest;
import com.hcare.api.v1.caregivers.dto.CredentialResponse;
import com.hcare.api.v1.caregivers.dto.RecordBackgroundCheckRequest;
import com.hcare.api.v1.caregivers.dto.SetAvailabilityRequest;
import com.hcare.api.v1.caregivers.dto.ShiftHistoryResponse;
import com.hcare.api.v1.caregivers.dto.UpdateCaregiverRequest;
import com.hcare.domain.BackgroundCheck;
import com.hcare.domain.BackgroundCheckRepository;
import com.hcare.domain.BackgroundCheckResult;
import com.hcare.domain.BackgroundCheckType;
import com.hcare.domain.Caregiver;
import com.hcare.domain.CaregiverAvailability;
import com.hcare.domain.CaregiverAvailabilityRepository;
import com.hcare.domain.CaregiverCredential;
import com.hcare.domain.CaregiverCredentialRepository;
import com.hcare.domain.CaregiverRepository;
import com.hcare.domain.CaregiverStatus;
import com.hcare.domain.CredentialType;
import com.hcare.domain.Shift;
import com.hcare.domain.ShiftRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CaregiverServiceTest {

    @Mock CaregiverRepository caregiverRepository;
    @Mock CaregiverCredentialRepository credentialRepository;
    @Mock BackgroundCheckRepository backgroundCheckRepository;
    @Mock CaregiverAvailabilityRepository availabilityRepository;
    @Mock ShiftRepository shiftRepository;

    CaregiverService service;

    UUID agencyId = UUID.randomUUID();
    UUID caregiverId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new CaregiverService(caregiverRepository, credentialRepository,
            backgroundCheckRepository, availabilityRepository, shiftRepository);
    }

    private Caregiver makeCaregiver() {
        return new Caregiver(agencyId, "Jane", "Doe", "jane@example.com");
    }

    // --- listCaregivers ---

    @Test
    void listCaregivers_returns_all_for_agency() {
        Caregiver c = makeCaregiver();
        when(caregiverRepository.findByAgencyId(agencyId)).thenReturn(List.of(c));

        List<CaregiverResponse> result = service.listCaregivers(agencyId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).firstName()).isEqualTo("Jane");
        verify(caregiverRepository).findByAgencyId(agencyId);
    }

    // --- createCaregiver ---

    @Test
    void createCaregiver_saves_and_returns_response() {
        CreateCaregiverRequest req = new CreateCaregiverRequest(
            "Bob", "Smith", "bob@example.com", "555-1234", null, null, false);
        Caregiver saved = new Caregiver(agencyId, "Bob", "Smith", "bob@example.com");
        when(caregiverRepository.save(any(Caregiver.class))).thenReturn(saved);

        CaregiverResponse result = service.createCaregiver(agencyId, req);

        assertThat(result.firstName()).isEqualTo("Bob");
        verify(caregiverRepository).save(any(Caregiver.class));
    }

    // --- getCaregiver ---

    @Test
    void getCaregiver_returns_when_found() {
        Caregiver c = makeCaregiver();
        when(caregiverRepository.findById(caregiverId)).thenReturn(Optional.of(c));

        CaregiverResponse result = service.getCaregiver(agencyId, caregiverId);

        assertThat(result.firstName()).isEqualTo("Jane");
    }

    @Test
    void getCaregiver_throws_404_when_not_found() {
        when(caregiverRepository.findById(caregiverId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCaregiver(agencyId, caregiverId))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("404");
    }

    // --- updateCaregiver ---

    @Test
    void updateCaregiver_applies_non_null_fields() {
        Caregiver c = makeCaregiver();
        when(caregiverRepository.findById(caregiverId)).thenReturn(Optional.of(c));
        when(caregiverRepository.save(c)).thenReturn(c);

        UpdateCaregiverRequest req = new UpdateCaregiverRequest(
            "Janet", null, null, null, null, null, null, null);

        CaregiverResponse result = service.updateCaregiver(agencyId, caregiverId, req);

        assertThat(result.firstName()).isEqualTo("Janet");
        assertThat(result.lastName()).isEqualTo("Doe"); // unchanged
        verify(caregiverRepository).save(c);
    }

    @Test
    void updateCaregiver_throws_404_when_not_found() {
        when(caregiverRepository.findById(caregiverId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateCaregiver(agencyId, caregiverId,
            new UpdateCaregiverRequest(null, null, null, null, null, null, null, null)))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("404");
    }

    @Test
    void updateCaregiver_can_set_status_to_inactive() {
        Caregiver c = makeCaregiver();
        when(caregiverRepository.findById(caregiverId)).thenReturn(Optional.of(c));
        when(caregiverRepository.save(c)).thenReturn(c);

        UpdateCaregiverRequest req = new UpdateCaregiverRequest(
            null, null, null, null, null, null, null, CaregiverStatus.INACTIVE);

        service.updateCaregiver(agencyId, caregiverId, req);

        assertThat(c.getStatus()).isEqualTo(CaregiverStatus.INACTIVE);
    }

    // --- credentials ---

    @Test
    void addCredential_saves_and_returns_response() {
        Caregiver c = makeCaregiver();
        when(caregiverRepository.findById(caregiverId)).thenReturn(Optional.of(c));
        CaregiverCredential saved = new CaregiverCredential(
            caregiverId, agencyId, CredentialType.CPR,
            LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1));
        when(credentialRepository.save(any())).thenReturn(saved);

        CredentialResponse result = service.addCredential(agencyId, caregiverId,
            new AddCredentialRequest(CredentialType.CPR, LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1)));

        assertThat(result.credentialType()).isEqualTo(CredentialType.CPR);
        assertThat(result.expiryDate()).isEqualTo(LocalDate.of(2027, 1, 1));
        verify(credentialRepository).save(any(CaregiverCredential.class));
    }

    @Test
    void listCredentials_returns_all_for_caregiver() {
        Caregiver c = makeCaregiver();
        when(caregiverRepository.findById(caregiverId)).thenReturn(Optional.of(c));
        CaregiverCredential cred = new CaregiverCredential(
            caregiverId, agencyId, CredentialType.CPR,
            LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1));
        when(credentialRepository.findByCaregiverId(caregiverId)).thenReturn(List.of(cred));

        List<CredentialResponse> result = service.listCredentials(agencyId, caregiverId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).credentialType()).isEqualTo(CredentialType.CPR);
    }

    @Test
    void deleteCredential_removes_when_belongs_to_caregiver() {
        UUID credId = UUID.randomUUID();
        Caregiver c = makeCaregiver();
        when(caregiverRepository.findById(caregiverId)).thenReturn(Optional.of(c));
        CaregiverCredential cred = new CaregiverCredential(
            caregiverId, agencyId, CredentialType.CPR,
            LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1));
        when(credentialRepository.findById(credId)).thenReturn(Optional.of(cred));

        service.deleteCredential(agencyId, caregiverId, credId);

        verify(credentialRepository).delete(cred);
    }

    @Test
    void deleteCredential_throws_404_when_belongs_to_other_caregiver() {
        UUID credId = UUID.randomUUID();
        Caregiver c = makeCaregiver();
        when(caregiverRepository.findById(caregiverId)).thenReturn(Optional.of(c));
        CaregiverCredential cred = new CaregiverCredential(
            UUID.randomUUID(), agencyId, CredentialType.CPR,
            LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1));
        when(credentialRepository.findById(credId)).thenReturn(Optional.of(cred));

        assertThatThrownBy(() -> service.deleteCredential(agencyId, caregiverId, credId))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("404");
    }

    // --- background checks ---

    @Test
    void recordBackgroundCheck_saves_and_returns_response() {
        Caregiver c = makeCaregiver();
        when(caregiverRepository.findById(caregiverId)).thenReturn(Optional.of(c));
        BackgroundCheck saved = new BackgroundCheck(caregiverId, agencyId,
            BackgroundCheckType.STATE_REGISTRY, BackgroundCheckResult.CLEAR,
            LocalDate.of(2026, 1, 1));
        when(backgroundCheckRepository.save(any())).thenReturn(saved);

        BackgroundCheckResponse result = service.recordBackgroundCheck(agencyId, caregiverId,
            new RecordBackgroundCheckRequest(BackgroundCheckType.STATE_REGISTRY,
                BackgroundCheckResult.CLEAR, LocalDate.of(2026, 1, 1), null));

        assertThat(result).isNotNull();
        assertThat(result.checkType()).isEqualTo(BackgroundCheckType.STATE_REGISTRY);
        verify(backgroundCheckRepository).save(any(BackgroundCheck.class));
    }

    @Test
    void listBackgroundChecks_returns_all_for_caregiver() {
        Caregiver c = makeCaregiver();
        when(caregiverRepository.findById(caregiverId)).thenReturn(Optional.of(c));
        BackgroundCheck check = new BackgroundCheck(caregiverId, agencyId,
            BackgroundCheckType.FBI, BackgroundCheckResult.CLEAR, LocalDate.of(2026, 1, 1));
        when(backgroundCheckRepository.findByCaregiverId(caregiverId)).thenReturn(List.of(check));

        List<BackgroundCheckResponse> result = service.listBackgroundChecks(agencyId, caregiverId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).checkType()).isEqualTo(BackgroundCheckType.FBI);
    }

    // --- availability ---

    @Test
    void setAvailability_replaces_all_blocks_for_caregiver() {
        Caregiver c = makeCaregiver();
        when(caregiverRepository.findById(caregiverId)).thenReturn(Optional.of(c));
        CaregiverAvailability block = new CaregiverAvailability(caregiverId, agencyId,
            DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(17, 0));
        when(availabilityRepository.save(any())).thenReturn(block);

        AvailabilityResponse result = service.setAvailability(agencyId, caregiverId,
            new SetAvailabilityRequest(List.of(
                new AvailabilityBlockRequest(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(17, 0)))));

        verify(availabilityRepository).deleteByCaregiverId(caregiverId);
        verify(availabilityRepository).save(any(CaregiverAvailability.class));
        assertThat(result.blocks()).hasSize(1);
        assertThat(result.blocks().get(0).dayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
    }

    @Test
    void getAvailability_returns_all_blocks() {
        Caregiver c = makeCaregiver();
        when(caregiverRepository.findById(caregiverId)).thenReturn(Optional.of(c));
        CaregiverAvailability block = new CaregiverAvailability(caregiverId, agencyId,
            DayOfWeek.TUESDAY, LocalTime.of(8, 0), LocalTime.of(16, 0));
        when(availabilityRepository.findByCaregiverId(caregiverId)).thenReturn(List.of(block));

        AvailabilityResponse result = service.getAvailability(agencyId, caregiverId);

        assertThat(result.blocks()).hasSize(1);
        assertThat(result.caregiverId()).isEqualTo(caregiverId);
    }

    // --- shift history ---

    @Test
    void listShiftHistory_returns_shifts_for_caregiver() {
        Caregiver c = makeCaregiver();
        when(caregiverRepository.findById(caregiverId)).thenReturn(Optional.of(c));
        UUID clientId = UUID.randomUUID();
        UUID serviceTypeId = UUID.randomUUID();
        Shift shift = new Shift(agencyId, null, clientId, caregiverId, serviceTypeId, null,
            LocalDateTime.of(2026, 1, 10, 9, 0), LocalDateTime.of(2026, 1, 10, 17, 0));
        when(shiftRepository.findByCaregiverId(caregiverId)).thenReturn(List.of(shift));

        List<ShiftHistoryResponse> result = service.listShiftHistory(agencyId, caregiverId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).clientId()).isEqualTo(clientId);
    }
}
