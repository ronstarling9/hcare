package com.hcare.api.v1.clients;

import com.hcare.api.v1.clients.dto.AddDiagnosisRequest;
import com.hcare.api.v1.clients.dto.AddMedicationRequest;
import com.hcare.api.v1.clients.dto.ClientResponse;
import com.hcare.api.v1.clients.dto.CreateClientRequest;
import com.hcare.api.v1.clients.dto.DiagnosisResponse;
import com.hcare.api.v1.clients.dto.MedicationResponse;
import com.hcare.api.v1.clients.dto.UpdateClientRequest;
import com.hcare.api.v1.clients.dto.UpdateMedicationRequest;
import com.hcare.domain.Client;
import com.hcare.domain.ClientDiagnosis;
import com.hcare.domain.ClientDiagnosisRepository;
import com.hcare.domain.ClientMedication;
import com.hcare.domain.ClientMedicationRepository;
import com.hcare.domain.ClientRepository;
import com.hcare.domain.ClientStatus;
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
class ClientServiceTest {

    @Mock ClientRepository clientRepository;
    @Mock ClientDiagnosisRepository diagnosisRepository;
    @Mock ClientMedicationRepository medicationRepository;

    ClientService service;

    UUID agencyId = UUID.randomUUID();
    UUID clientId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ClientService(clientRepository, diagnosisRepository, medicationRepository);
    }

    private Client makeClient() {
        return new Client(agencyId, "Alice", "Smith", LocalDate.of(1960, 3, 15));
    }

    // --- listClients ---

    @Test
    void listClients_returns_all_clients_for_agency() {
        Client client = makeClient();
        when(clientRepository.findByAgencyId(agencyId)).thenReturn(List.of(client));

        List<ClientResponse> result = service.listClients(agencyId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).firstName()).isEqualTo("Alice");
        verify(clientRepository).findByAgencyId(agencyId);
    }

    // --- createClient ---

    @Test
    void createClient_saves_and_returns_response() {
        CreateClientRequest req = new CreateClientRequest(
            "Bob", "Jones", LocalDate.of(1975, 6, 20),
            "123 Main St", "555-1234", null, null, null, "[]", false);
        Client saved = new Client(agencyId, "Bob", "Jones", LocalDate.of(1975, 6, 20));
        when(clientRepository.save(any(Client.class))).thenReturn(saved);

        ClientResponse result = service.createClient(agencyId, req);

        assertThat(result.firstName()).isEqualTo("Bob");
        verify(clientRepository).save(any(Client.class));
    }

    // --- getClient ---

    @Test
    void getClient_returns_client_when_found() {
        Client client = makeClient();
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));

        ClientResponse result = service.getClient(agencyId, clientId);

        assertThat(result.firstName()).isEqualTo("Alice");
    }

    @Test
    void getClient_throws_404_when_not_found() {
        when(clientRepository.findById(clientId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getClient(agencyId, clientId))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("404");
    }

    // --- updateClient ---

    @Test
    void updateClient_applies_non_null_fields_and_saves() {
        Client client = makeClient();
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(clientRepository.save(client)).thenReturn(client);

        UpdateClientRequest req = new UpdateClientRequest(
            "Alicia", null, null, "456 Oak Ave", null, null, null, null, null, null, null);

        ClientResponse result = service.updateClient(agencyId, clientId, req);

        assertThat(result.firstName()).isEqualTo("Alicia");
        assertThat(result.address()).isEqualTo("456 Oak Ave");
        assertThat(result.lastName()).isEqualTo("Smith"); // unchanged
        verify(clientRepository).save(client);
    }

    @Test
    void updateClient_throws_404_when_not_found() {
        when(clientRepository.findById(clientId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateClient(agencyId, clientId,
            new UpdateClientRequest(null, null, null, null, null, null, null, null, null, null, null)))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("404");
    }

    @Test
    void updateClient_can_set_status_to_discharged() {
        Client client = makeClient();
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(clientRepository.save(client)).thenReturn(client);

        UpdateClientRequest req = new UpdateClientRequest(
            null, null, null, null, null, null, null, null, null, null, ClientStatus.DISCHARGED);

        service.updateClient(agencyId, clientId, req);

        assertThat(client.getStatus()).isEqualTo(ClientStatus.DISCHARGED);
    }

    // --- diagnoses ---

    @Test
    void addDiagnosis_saves_and_returns_response() {
        Client client = makeClient();
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        ClientDiagnosis saved = new ClientDiagnosis(clientId, agencyId, "E11.9");
        when(diagnosisRepository.save(any())).thenReturn(saved);

        DiagnosisResponse result = service.addDiagnosis(agencyId, clientId,
            new AddDiagnosisRequest("E11.9", "Type 2 Diabetes", null));

        assertThat(result.icd10Code()).isEqualTo("E11.9");
        verify(diagnosisRepository).save(any(ClientDiagnosis.class));
    }

    @Test
    void listDiagnoses_returns_all_for_client() {
        Client client = makeClient();
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        ClientDiagnosis diag = new ClientDiagnosis(clientId, agencyId, "E11.9");
        when(diagnosisRepository.findByClientId(clientId)).thenReturn(List.of(diag));

        List<DiagnosisResponse> result = service.listDiagnoses(agencyId, clientId);

        assertThat(result).hasSize(1);
    }

    @Test
    void deleteDiagnosis_removes_when_belongs_to_client() {
        UUID diagId = UUID.randomUUID();
        Client client = makeClient();
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        ClientDiagnosis diag = new ClientDiagnosis(clientId, agencyId, "E11.9");
        when(diagnosisRepository.findById(diagId)).thenReturn(Optional.of(diag));

        service.deleteDiagnosis(agencyId, clientId, diagId);

        verify(diagnosisRepository).delete(diag);
    }

    @Test
    void deleteDiagnosis_throws_404_when_belongs_to_other_client() {
        UUID diagId = UUID.randomUUID();
        Client client = makeClient();
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        ClientDiagnosis diag = new ClientDiagnosis(UUID.randomUUID(), agencyId, "E11.9");
        when(diagnosisRepository.findById(diagId)).thenReturn(Optional.of(diag));

        assertThatThrownBy(() -> service.deleteDiagnosis(agencyId, clientId, diagId))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("404");
    }

    // --- medications ---

    @Test
    void addMedication_saves_and_returns_response() {
        Client client = makeClient();
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        ClientMedication saved = new ClientMedication(clientId, agencyId, "Metformin");
        when(medicationRepository.save(any())).thenReturn(saved);

        MedicationResponse result = service.addMedication(agencyId, clientId,
            new AddMedicationRequest("Metformin", "500mg", "oral", "twice daily", "Dr. Brown"));

        assertThat(result.name()).isEqualTo("Metformin");
        verify(medicationRepository).save(any(ClientMedication.class));
    }

    @Test
    void updateMedication_applies_non_null_fields() {
        UUID medId = UUID.randomUUID();
        Client client = makeClient();
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        ClientMedication med = new ClientMedication(clientId, agencyId, "Metformin");
        when(medicationRepository.findById(medId)).thenReturn(Optional.of(med));
        when(medicationRepository.save(med)).thenReturn(med);

        service.updateMedication(agencyId, clientId, medId,
            new UpdateMedicationRequest(null, "1000mg", null, null, null));

        assertThat(med.getDosage()).isEqualTo("1000mg");
        assertThat(med.getName()).isEqualTo("Metformin"); // unchanged
    }

    @Test
    void deleteMedication_removes_when_belongs_to_client() {
        UUID medId = UUID.randomUUID();
        Client client = makeClient();
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        ClientMedication med = new ClientMedication(clientId, agencyId, "Metformin");
        when(medicationRepository.findById(medId)).thenReturn(Optional.of(med));

        service.deleteMedication(agencyId, clientId, medId);

        verify(medicationRepository).delete(med);
    }
}
