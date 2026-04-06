package com.hcare.api.v1.clients;

import com.hcare.api.v1.clients.dto.AddAdlTaskRequest;
import com.hcare.api.v1.clients.dto.AddDiagnosisRequest;
import com.hcare.api.v1.clients.dto.AddFamilyPortalUserRequest;
import com.hcare.api.v1.clients.dto.AddGoalRequest;
import com.hcare.api.v1.clients.dto.AddMedicationRequest;
import com.hcare.api.v1.clients.dto.AdlTaskResponse;
import com.hcare.api.v1.clients.dto.AuthorizationResponse;
import com.hcare.api.v1.clients.dto.CarePlanResponse;
import com.hcare.api.v1.clients.dto.ClientResponse;
import com.hcare.api.v1.clients.dto.CreateAuthorizationRequest;
import com.hcare.api.v1.clients.dto.CreateCarePlanRequest;
import com.hcare.api.v1.clients.dto.CreateClientRequest;
import com.hcare.api.v1.clients.dto.DiagnosisResponse;
import com.hcare.api.v1.clients.dto.FamilyPortalUserResponse;
import com.hcare.api.v1.clients.dto.GoalResponse;
import com.hcare.api.v1.clients.dto.MedicationResponse;
import com.hcare.api.v1.clients.dto.UpdateClientRequest;
import com.hcare.api.v1.clients.dto.UpdateGoalRequest;
import com.hcare.api.v1.clients.dto.UpdateMedicationRequest;
import com.hcare.domain.AdlTask;
import com.hcare.domain.AdlTaskRepository;
import com.hcare.domain.AssistanceLevel;
import com.hcare.domain.Authorization;
import com.hcare.domain.AuthorizationRepository;
import com.hcare.domain.CarePlan;
import com.hcare.domain.CarePlanRepository;
import com.hcare.domain.CarePlanStatus;
import com.hcare.domain.Client;
import com.hcare.domain.ClientDiagnosis;
import com.hcare.domain.ClientDiagnosisRepository;
import com.hcare.domain.ClientMedication;
import com.hcare.domain.ClientMedicationRepository;
import com.hcare.domain.ClientRepository;
import com.hcare.domain.ClientStatus;
import com.hcare.domain.FamilyPortalUser;
import com.hcare.domain.FamilyPortalUserRepository;
import com.hcare.domain.Goal;
import com.hcare.domain.GoalRepository;
import com.hcare.domain.GoalStatus;
import com.hcare.domain.UnitType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
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
    @Mock CarePlanRepository carePlanRepository;
    @Mock AdlTaskRepository adlTaskRepository;
    @Mock GoalRepository goalRepository;
    @Mock AuthorizationRepository authorizationRepository;
    @Mock FamilyPortalUserRepository familyPortalUserRepository;

    ClientService service;

    UUID agencyId = UUID.randomUUID();
    UUID clientId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ClientService(clientRepository, diagnosisRepository, medicationRepository,
            carePlanRepository, adlTaskRepository, goalRepository,
            authorizationRepository, familyPortalUserRepository);
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

    // --- care plans ---

    @Test
    void createCarePlan_creates_draft_with_next_version_number() {
        Client client = makeClient();
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(carePlanRepository.findMaxPlanVersionByClientId(clientId)).thenReturn(1);
        when(carePlanRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CarePlanResponse result = service.createCarePlan(agencyId, clientId,
            new CreateCarePlanRequest(null));

        assertThat(result.planVersion()).isEqualTo(2);
        assertThat(result.status()).isEqualTo(CarePlanStatus.DRAFT);
    }

    @Test
    void activateCarePlan_supersedes_current_active_and_activates_new_one() {
        UUID planId = UUID.randomUUID();
        Client client = makeClient();
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        CarePlan currentActive = new CarePlan(clientId, agencyId, 1);
        currentActive.activate();
        when(carePlanRepository.findByClientIdAndStatus(clientId, CarePlanStatus.ACTIVE))
            .thenReturn(Optional.of(currentActive));
        CarePlan newPlan = new CarePlan(clientId, agencyId, 2);
        when(carePlanRepository.findByIdWithLock(planId)).thenReturn(Optional.of(newPlan));
        when(carePlanRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.activateCarePlan(agencyId, clientId, planId);

        assertThat(currentActive.getStatus()).isEqualTo(CarePlanStatus.SUPERSEDED);
        assertThat(newPlan.getStatus()).isEqualTo(CarePlanStatus.ACTIVE);
    }

    @Test
    void activateCarePlan_throws_409_when_plan_already_active() {
        UUID planId = UUID.randomUUID();
        Client client = makeClient();
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        CarePlan plan = new CarePlan(clientId, agencyId, 1);
        plan.activate();
        when(carePlanRepository.findByIdWithLock(planId)).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> service.activateCarePlan(agencyId, clientId, planId))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("409");
    }

    @Test
    void activateCarePlan_throws_404_when_plan_belongs_to_other_client() {
        UUID planId = UUID.randomUUID();
        Client client = makeClient();
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        CarePlan plan = new CarePlan(UUID.randomUUID(), agencyId, 1); // different clientId
        when(carePlanRepository.findByIdWithLock(planId)).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> service.activateCarePlan(agencyId, clientId, planId))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("404");
    }

    // --- ADL tasks ---

    @Test
    void addAdlTask_saves_task_on_care_plan() {
        UUID planId = UUID.randomUUID();
        Client client = makeClient();
        CarePlan plan = new CarePlan(clientId, agencyId, 1);
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(carePlanRepository.findById(planId)).thenReturn(Optional.of(plan));
        AdlTask saved = new AdlTask(planId, agencyId, "Bathing", AssistanceLevel.MODERATE_ASSIST);
        when(adlTaskRepository.save(any())).thenReturn(saved);

        AdlTaskResponse result = service.addAdlTask(agencyId, clientId, planId,
            new AddAdlTaskRequest("Bathing", AssistanceLevel.MODERATE_ASSIST, null, null, null));

        assertThat(result.name()).isEqualTo("Bathing");
        assertThat(result.assistanceLevel()).isEqualTo(AssistanceLevel.MODERATE_ASSIST);
        verify(adlTaskRepository).save(any(AdlTask.class));
    }

    @Test
    void deleteAdlTask_removes_task_when_belongs_to_plan() {
        UUID planId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        Client client = makeClient();
        CarePlan plan = new CarePlan(clientId, agencyId, 1);
        AdlTask task = new AdlTask(planId, agencyId, "Bathing", AssistanceLevel.MINIMAL_ASSIST);
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(carePlanRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(adlTaskRepository.findById(taskId)).thenReturn(Optional.of(task));

        service.deleteAdlTask(agencyId, clientId, planId, taskId);

        verify(adlTaskRepository).delete(task);
    }

    // --- Goals ---

    @Test
    void addGoal_saves_goal_on_care_plan() {
        UUID planId = UUID.randomUUID();
        Client client = makeClient();
        CarePlan plan = new CarePlan(clientId, agencyId, 1);
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(carePlanRepository.findById(planId)).thenReturn(Optional.of(plan));
        Goal saved = new Goal(planId, agencyId, "Improve mobility");
        when(goalRepository.save(any())).thenReturn(saved);

        GoalResponse result = service.addGoal(agencyId, clientId, planId,
            new AddGoalRequest("Improve mobility", null));

        assertThat(result.description()).isEqualTo("Improve mobility");
        assertThat(result.status()).isEqualTo(GoalStatus.ACTIVE);
    }

    @Test
    void updateGoal_updates_non_null_fields() {
        UUID planId = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();
        Client client = makeClient();
        CarePlan plan = new CarePlan(clientId, agencyId, 1);
        Goal goal = new Goal(planId, agencyId, "Improve mobility");
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(carePlanRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(goalRepository.findById(goalId)).thenReturn(Optional.of(goal));
        when(goalRepository.save(goal)).thenReturn(goal);

        service.updateGoal(agencyId, clientId, planId, goalId,
            new UpdateGoalRequest(null, null, GoalStatus.ACHIEVED));

        assertThat(goal.getStatus()).isEqualTo(GoalStatus.ACHIEVED);
        assertThat(goal.getDescription()).isEqualTo("Improve mobility"); // unchanged
    }

    @Test
    void deleteAdlTask_throws_404_when_task_belongs_to_other_plan() {
        UUID planId = UUID.randomUUID();
        UUID otherPlanId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        Client client = makeClient();
        CarePlan plan = new CarePlan(clientId, agencyId, 1);
        AdlTask task = new AdlTask(otherPlanId, agencyId, "Bathing", AssistanceLevel.MINIMAL_ASSIST);
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(carePlanRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(adlTaskRepository.findById(taskId)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.deleteAdlTask(agencyId, clientId, planId, taskId))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("404");
    }

    @Test
    void updateGoal_throws_404_when_goal_belongs_to_other_plan() {
        UUID planId = UUID.randomUUID();
        UUID otherPlanId = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();
        Client client = makeClient();
        CarePlan plan = new CarePlan(clientId, agencyId, 1);
        Goal goal = new Goal(otherPlanId, agencyId, "Improve mobility");
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(carePlanRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(goalRepository.findById(goalId)).thenReturn(Optional.of(goal));

        assertThatThrownBy(() -> service.updateGoal(agencyId, clientId, planId, goalId,
            new UpdateGoalRequest(null, null, null)))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("404");
    }

    // --- authorizations ---

    @Test
    void createAuthorization_saves_and_returns_response() {
        Client client = makeClient();
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        Authorization saved = new Authorization(
            clientId, UUID.randomUUID(), UUID.randomUUID(), agencyId,
            "AUTH-001", BigDecimal.valueOf(40), UnitType.HOURS,
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        when(authorizationRepository.save(any())).thenReturn(saved);

        AuthorizationResponse result = service.createAuthorization(agencyId, clientId,
            new CreateAuthorizationRequest(
                UUID.randomUUID(), UUID.randomUUID(), "AUTH-001",
                BigDecimal.valueOf(40), UnitType.HOURS,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)));

        assertThat(result).isNotNull();
        verify(authorizationRepository).save(any(Authorization.class));
    }

    @Test
    void listAuthorizations_returns_all_for_client() {
        Client client = makeClient();
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(authorizationRepository.findByClientId(clientId)).thenReturn(List.of());

        List<AuthorizationResponse> result = service.listAuthorizations(agencyId, clientId);

        assertThat(result).isEmpty();
        verify(authorizationRepository).findByClientId(clientId);
    }

    // --- family portal users ---

    @Test
    void addFamilyPortalUser_saves_and_returns_response() {
        Client client = makeClient();
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        FamilyPortalUser saved = new FamilyPortalUser(clientId, agencyId, "family@example.com");
        when(familyPortalUserRepository.save(any())).thenReturn(saved);

        FamilyPortalUserResponse result = service.addFamilyPortalUser(agencyId, clientId,
            new AddFamilyPortalUserRequest("Jane Doe", "family@example.com"));

        assertThat(result).isNotNull();
        verify(familyPortalUserRepository).save(any(FamilyPortalUser.class));
    }

    @Test
    void removeFamilyPortalUser_removes_when_belongs_to_client() {
        UUID fpuId = UUID.randomUUID();
        Client client = makeClient();
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        FamilyPortalUser fpu = new FamilyPortalUser(clientId, agencyId, "family@example.com");
        when(familyPortalUserRepository.findById(fpuId)).thenReturn(Optional.of(fpu));

        service.removeFamilyPortalUser(agencyId, clientId, fpuId);

        verify(familyPortalUserRepository).delete(fpu);
    }
}
