package com.hcare.api.v1.caregivers;

import com.hcare.api.v1.caregivers.dto.CaregiverResponse;
import com.hcare.api.v1.caregivers.dto.CreateCaregiverRequest;
import com.hcare.api.v1.caregivers.dto.UpdateCaregiverRequest;
import com.hcare.domain.Caregiver;
import com.hcare.domain.CaregiverRepository;
import com.hcare.domain.CaregiverStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
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

    CaregiverService service;

    UUID agencyId = UUID.randomUUID();
    UUID caregiverId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new CaregiverService(caregiverRepository);
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
}
