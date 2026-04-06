package com.hcare.api.v1.clients;

import com.hcare.api.v1.clients.dto.AddAdlTaskRequest;
import com.hcare.api.v1.clients.dto.AddDiagnosisRequest;
import com.hcare.api.v1.clients.dto.AddGoalRequest;
import com.hcare.api.v1.clients.dto.AddMedicationRequest;
import com.hcare.api.v1.clients.dto.AdlTaskResponse;
import com.hcare.api.v1.clients.dto.CarePlanResponse;
import com.hcare.api.v1.clients.dto.ClientResponse;
import com.hcare.api.v1.clients.dto.CreateCarePlanRequest;
import com.hcare.api.v1.clients.dto.CreateClientRequest;
import com.hcare.api.v1.clients.dto.DiagnosisResponse;
import com.hcare.api.v1.clients.dto.GoalResponse;
import com.hcare.api.v1.clients.dto.MedicationResponse;
import com.hcare.api.v1.clients.dto.UpdateClientRequest;
import com.hcare.api.v1.clients.dto.UpdateGoalRequest;
import com.hcare.api.v1.clients.dto.UpdateMedicationRequest;
import com.hcare.domain.AdlTask;
import com.hcare.domain.AdlTaskRepository;
import com.hcare.domain.CarePlan;
import com.hcare.domain.CarePlanRepository;
import com.hcare.domain.CarePlanStatus;
import com.hcare.domain.Client;
import com.hcare.domain.ClientDiagnosis;
import com.hcare.domain.ClientDiagnosisRepository;
import com.hcare.domain.ClientMedication;
import com.hcare.domain.ClientMedicationRepository;
import com.hcare.domain.ClientRepository;
import com.hcare.domain.Goal;
import com.hcare.domain.GoalRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class ClientService {

    private final ClientRepository clientRepository;
    private final ClientDiagnosisRepository diagnosisRepository;
    private final ClientMedicationRepository medicationRepository;
    private final CarePlanRepository carePlanRepository;
    private final AdlTaskRepository adlTaskRepository;
    private final GoalRepository goalRepository;

    public ClientService(ClientRepository clientRepository,
                         ClientDiagnosisRepository diagnosisRepository,
                         ClientMedicationRepository medicationRepository,
                         CarePlanRepository carePlanRepository,
                         AdlTaskRepository adlTaskRepository,
                         GoalRepository goalRepository) {
        this.clientRepository = clientRepository;
        this.diagnosisRepository = diagnosisRepository;
        this.medicationRepository = medicationRepository;
        this.carePlanRepository = carePlanRepository;
        this.adlTaskRepository = adlTaskRepository;
        this.goalRepository = goalRepository;
    }

    @Transactional(readOnly = true)
    public List<ClientResponse> listClients(UUID agencyId) {
        return clientRepository.findByAgencyId(agencyId).stream()
            .map(ClientResponse::from)
            .toList();
    }

    @Transactional
    public ClientResponse createClient(UUID agencyId, CreateClientRequest req) {
        Client client = new Client(agencyId, req.firstName(), req.lastName(), req.dateOfBirth());
        if (req.address() != null) client.setAddress(req.address());
        if (req.phone() != null) client.setPhone(req.phone());
        if (req.medicaidId() != null) client.setMedicaidId(req.medicaidId());
        if (req.serviceState() != null) client.setServiceState(req.serviceState());
        if (req.preferredCaregiverGender() != null) client.setPreferredCaregiverGender(req.preferredCaregiverGender());
        if (req.preferredLanguages() != null) client.setPreferredLanguages(req.preferredLanguages());
        if (req.noPetCaregiver() != null) client.setNoPetCaregiver(req.noPetCaregiver());
        return ClientResponse.from(clientRepository.save(client));
    }

    @Transactional(readOnly = true)
    public ClientResponse getClient(UUID agencyId, UUID clientId) {
        return ClientResponse.from(requireClient(clientId));
    }

    @Transactional
    public ClientResponse updateClient(UUID agencyId, UUID clientId, UpdateClientRequest req) {
        Client client = requireClient(clientId);
        if (req.firstName() != null) client.setFirstName(req.firstName());
        if (req.lastName() != null) client.setLastName(req.lastName());
        if (req.dateOfBirth() != null) client.setDateOfBirth(req.dateOfBirth());
        if (req.address() != null) client.setAddress(req.address());
        if (req.phone() != null) client.setPhone(req.phone());
        if (req.medicaidId() != null) client.setMedicaidId(req.medicaidId());
        if (req.serviceState() != null) client.setServiceState(req.serviceState());
        if (req.preferredCaregiverGender() != null) client.setPreferredCaregiverGender(req.preferredCaregiverGender());
        if (req.preferredLanguages() != null) client.setPreferredLanguages(req.preferredLanguages());
        if (req.noPetCaregiver() != null) client.setNoPetCaregiver(req.noPetCaregiver());
        if (req.status() != null) client.setStatus(req.status());
        return ClientResponse.from(clientRepository.save(client));
    }

    // --- diagnoses ---

    @Transactional
    public DiagnosisResponse addDiagnosis(UUID agencyId, UUID clientId, AddDiagnosisRequest req) {
        requireClient(clientId);
        ClientDiagnosis diag = new ClientDiagnosis(clientId, agencyId, req.icd10Code());
        if (req.description() != null) diag.setDescription(req.description());
        if (req.onsetDate() != null) diag.setOnsetDate(req.onsetDate());
        return DiagnosisResponse.from(diagnosisRepository.save(diag));
    }

    @Transactional(readOnly = true)
    public List<DiagnosisResponse> listDiagnoses(UUID agencyId, UUID clientId) {
        requireClient(clientId);
        return diagnosisRepository.findByClientId(clientId).stream()
            .map(DiagnosisResponse::from)
            .toList();
    }

    @Transactional
    public void deleteDiagnosis(UUID agencyId, UUID clientId, UUID diagnosisId) {
        requireClient(clientId);
        ClientDiagnosis diag = diagnosisRepository.findById(diagnosisId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Diagnosis not found"));
        if (!diag.getClientId().equals(clientId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Diagnosis not found");
        }
        diagnosisRepository.delete(diag);
    }

    // --- medications ---

    @Transactional
    public MedicationResponse addMedication(UUID agencyId, UUID clientId, AddMedicationRequest req) {
        requireClient(clientId);
        ClientMedication med = new ClientMedication(clientId, agencyId, req.name());
        if (req.dosage() != null) med.setDosage(req.dosage());
        if (req.route() != null) med.setRoute(req.route());
        if (req.schedule() != null) med.setSchedule(req.schedule());
        if (req.prescriber() != null) med.setPrescriber(req.prescriber());
        return MedicationResponse.from(medicationRepository.save(med));
    }

    @Transactional(readOnly = true)
    public List<MedicationResponse> listMedications(UUID agencyId, UUID clientId) {
        requireClient(clientId);
        return medicationRepository.findByClientId(clientId).stream()
            .map(MedicationResponse::from)
            .toList();
    }

    @Transactional
    public MedicationResponse updateMedication(UUID agencyId, UUID clientId, UUID medicationId,
                                               UpdateMedicationRequest req) {
        requireClient(clientId);
        ClientMedication med = medicationRepository.findById(medicationId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Medication not found"));
        if (!med.getClientId().equals(clientId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Medication not found");
        }
        if (req.name() != null) med.setName(req.name());
        if (req.dosage() != null) med.setDosage(req.dosage());
        if (req.route() != null) med.setRoute(req.route());
        if (req.schedule() != null) med.setSchedule(req.schedule());
        if (req.prescriber() != null) med.setPrescriber(req.prescriber());
        return MedicationResponse.from(medicationRepository.save(med));
    }

    @Transactional
    public void deleteMedication(UUID agencyId, UUID clientId, UUID medicationId) {
        requireClient(clientId);
        ClientMedication med = medicationRepository.findById(medicationId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Medication not found"));
        if (!med.getClientId().equals(clientId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Medication not found");
        }
        medicationRepository.delete(med);
    }

    // --- care plans ---

    @Transactional(readOnly = true)
    public List<CarePlanResponse> listCarePlans(UUID agencyId, UUID clientId) {
        requireClient(clientId);
        return carePlanRepository.findByClientIdOrderByPlanVersionAsc(clientId).stream()
            .map(CarePlanResponse::from)
            .toList();
    }

    @Transactional
    public CarePlanResponse createCarePlan(UUID agencyId, UUID clientId, CreateCarePlanRequest req) {
        requireClient(clientId);
        int nextVersion = carePlanRepository.findMaxPlanVersionByClientId(clientId) + 1;
        CarePlan plan = new CarePlan(clientId, agencyId, nextVersion);
        if (req.reviewedByClinicianId() != null) {
            plan.review(req.reviewedByClinicianId());
        }
        return CarePlanResponse.from(carePlanRepository.save(plan));
    }

    @Transactional
    public CarePlanResponse activateCarePlan(UUID agencyId, UUID clientId, UUID carePlanId) {
        requireClient(clientId);
        CarePlan plan = carePlanRepository.findById(carePlanId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Care plan not found"));
        if (!plan.getClientId().equals(clientId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Care plan not found");
        }
        if (plan.getStatus() == CarePlanStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Care plan is already ACTIVE");
        }
        carePlanRepository.findByClientIdAndStatus(clientId, CarePlanStatus.ACTIVE)
            .ifPresent(current -> {
                current.supersede();
                carePlanRepository.save(current);
            });
        plan.activate();
        return CarePlanResponse.from(carePlanRepository.save(plan));
    }

    // --- ADL tasks ---

    @Transactional(readOnly = true)
    public List<AdlTaskResponse> listAdlTasks(UUID agencyId, UUID clientId, UUID carePlanId) {
        requireCarePlan(clientId, carePlanId);
        return adlTaskRepository.findByCarePlanIdOrderBySortOrder(carePlanId).stream()
            .map(AdlTaskResponse::from)
            .toList();
    }

    @Transactional
    public AdlTaskResponse addAdlTask(UUID agencyId, UUID clientId, UUID carePlanId,
                                      AddAdlTaskRequest req) {
        requireCarePlan(clientId, carePlanId);
        AdlTask task = new AdlTask(carePlanId, agencyId, req.name(), req.assistanceLevel());
        if (req.instructions() != null) task.setInstructions(req.instructions());
        if (req.frequency() != null) task.setFrequency(req.frequency());
        if (req.sortOrder() != null) task.setSortOrder(req.sortOrder());
        return AdlTaskResponse.from(adlTaskRepository.save(task));
    }

    @Transactional
    public void deleteAdlTask(UUID agencyId, UUID clientId, UUID carePlanId, UUID taskId) {
        requireCarePlan(clientId, carePlanId);
        AdlTask task = adlTaskRepository.findById(taskId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ADL task not found"));
        if (!task.getCarePlanId().equals(carePlanId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "ADL task not found");
        }
        adlTaskRepository.delete(task);
    }

    // --- goals ---

    @Transactional(readOnly = true)
    public List<GoalResponse> listGoals(UUID agencyId, UUID clientId, UUID carePlanId) {
        requireCarePlan(clientId, carePlanId);
        return goalRepository.findByCarePlanId(carePlanId).stream()
            .map(GoalResponse::from)
            .toList();
    }

    @Transactional
    public GoalResponse addGoal(UUID agencyId, UUID clientId, UUID carePlanId, AddGoalRequest req) {
        requireCarePlan(clientId, carePlanId);
        Goal goal = new Goal(carePlanId, agencyId, req.description());
        if (req.targetDate() != null) goal.setTargetDate(req.targetDate());
        return GoalResponse.from(goalRepository.save(goal));
    }

    @Transactional
    public GoalResponse updateGoal(UUID agencyId, UUID clientId, UUID carePlanId, UUID goalId,
                                   UpdateGoalRequest req) {
        requireCarePlan(clientId, carePlanId);
        Goal goal = goalRepository.findById(goalId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Goal not found"));
        if (!goal.getCarePlanId().equals(carePlanId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Goal not found");
        }
        if (req.description() != null) goal.setDescription(req.description());
        if (req.targetDate() != null) goal.setTargetDate(req.targetDate());
        if (req.status() != null) goal.setStatus(req.status());
        return GoalResponse.from(goalRepository.save(goal));
    }

    @Transactional
    public void deleteGoal(UUID agencyId, UUID clientId, UUID carePlanId, UUID goalId) {
        requireCarePlan(clientId, carePlanId);
        Goal goal = goalRepository.findById(goalId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Goal not found"));
        if (!goal.getCarePlanId().equals(carePlanId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Goal not found");
        }
        goalRepository.delete(goal);
    }

    // --- helpers (package-private for subclasses/tests in same package) ---

    Client requireClient(UUID clientId) {
        return clientRepository.findById(clientId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found"));
    }

    private CarePlan requireCarePlan(UUID clientId, UUID carePlanId) {
        requireClient(clientId);
        CarePlan plan = carePlanRepository.findById(carePlanId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Care plan not found"));
        if (!plan.getClientId().equals(clientId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Care plan not found");
        }
        return plan;
    }
}
