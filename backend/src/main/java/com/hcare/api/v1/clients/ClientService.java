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
import com.hcare.domain.FamilyPortalUser;
import com.hcare.domain.FamilyPortalUserRepository;
import com.hcare.domain.Goal;
import com.hcare.domain.GoalRepository;
import com.hcare.multitenancy.TenantContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class ClientService {

    private final ClientRepository clientRepository;
    private final ClientDiagnosisRepository diagnosisRepository;
    private final ClientMedicationRepository medicationRepository;
    private final CarePlanRepository carePlanRepository;
    private final AdlTaskRepository adlTaskRepository;
    private final GoalRepository goalRepository;
    private final AuthorizationRepository authorizationRepository;
    private final FamilyPortalUserRepository familyPortalUserRepository;

    public ClientService(ClientRepository clientRepository,
                         ClientDiagnosisRepository diagnosisRepository,
                         ClientMedicationRepository medicationRepository,
                         CarePlanRepository carePlanRepository,
                         AdlTaskRepository adlTaskRepository,
                         GoalRepository goalRepository,
                         AuthorizationRepository authorizationRepository,
                         FamilyPortalUserRepository familyPortalUserRepository) {
        this.clientRepository = clientRepository;
        this.diagnosisRepository = diagnosisRepository;
        this.medicationRepository = medicationRepository;
        this.carePlanRepository = carePlanRepository;
        this.adlTaskRepository = adlTaskRepository;
        this.goalRepository = goalRepository;
        this.authorizationRepository = authorizationRepository;
        this.familyPortalUserRepository = familyPortalUserRepository;
    }

    @Transactional(readOnly = true)
    public Page<ClientResponse> listClients(Pageable pageable) {
        return clientRepository.findByAgencyId(TenantContext.get(), pageable)
            .map(ClientResponse::from);
    }

    @Transactional
    public ClientResponse createClient(CreateClientRequest req) {
        UUID agencyId = TenantContext.get();
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
    public ClientResponse getClient(UUID clientId) {
        return ClientResponse.from(requireClient(clientId));
    }

    @Transactional
    public ClientResponse updateClient(UUID clientId, UpdateClientRequest req) {
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
    public DiagnosisResponse addDiagnosis(UUID clientId, AddDiagnosisRequest req) {
        requireClient(clientId);
        UUID agencyId = TenantContext.get();
        ClientDiagnosis diag = new ClientDiagnosis(clientId, agencyId, req.icd10Code());
        if (req.description() != null) diag.setDescription(req.description());
        if (req.onsetDate() != null) diag.setOnsetDate(req.onsetDate());
        return DiagnosisResponse.from(diagnosisRepository.save(diag));
    }

    @Transactional(readOnly = true)
    public Page<DiagnosisResponse> listDiagnoses(UUID clientId, Pageable pageable) {
        requireClient(clientId);
        return diagnosisRepository.findByClientId(clientId, pageable)
            .map(DiagnosisResponse::from);
    }

    @Transactional
    public void deleteDiagnosis(UUID clientId, UUID diagnosisId) {
        requireClient(clientId);
        ClientDiagnosis diag = diagnosisRepository.findById(diagnosisId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Diagnosis not found: " + diagnosisId));
        if (!diag.getClientId().equals(clientId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Diagnosis not found: " + diagnosisId);
        }
        diagnosisRepository.delete(diag);
    }

    // --- medications ---

    @Transactional
    public MedicationResponse addMedication(UUID clientId, AddMedicationRequest req) {
        requireClient(clientId);
        UUID agencyId = TenantContext.get();
        ClientMedication med = new ClientMedication(clientId, agencyId, req.name());
        if (req.dosage() != null) med.setDosage(req.dosage());
        if (req.route() != null) med.setRoute(req.route());
        if (req.schedule() != null) med.setSchedule(req.schedule());
        if (req.prescriber() != null) med.setPrescriber(req.prescriber());
        return MedicationResponse.from(medicationRepository.save(med));
    }

    @Transactional(readOnly = true)
    public Page<MedicationResponse> listMedications(UUID clientId, Pageable pageable) {
        requireClient(clientId);
        return medicationRepository.findByClientId(clientId, pageable)
            .map(MedicationResponse::from);
    }

    @Transactional
    public MedicationResponse updateMedication(UUID clientId, UUID medicationId,
                                               UpdateMedicationRequest req) {
        requireClient(clientId);
        ClientMedication med = medicationRepository.findById(medicationId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Medication not found: " + medicationId));
        if (!med.getClientId().equals(clientId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Medication not found: " + medicationId);
        }
        if (req.name() != null) med.setName(req.name());
        if (req.dosage() != null) med.setDosage(req.dosage());
        if (req.route() != null) med.setRoute(req.route());
        if (req.schedule() != null) med.setSchedule(req.schedule());
        if (req.prescriber() != null) med.setPrescriber(req.prescriber());
        return MedicationResponse.from(medicationRepository.save(med));
    }

    @Transactional
    public void deleteMedication(UUID clientId, UUID medicationId) {
        requireClient(clientId);
        ClientMedication med = medicationRepository.findById(medicationId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Medication not found: " + medicationId));
        if (!med.getClientId().equals(clientId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Medication not found: " + medicationId);
        }
        medicationRepository.delete(med);
    }

    // --- care plans ---

    @Transactional(readOnly = true)
    public Page<CarePlanResponse> listCarePlans(UUID clientId, Pageable pageable) {
        requireClient(clientId);
        return carePlanRepository.findByClientIdOrderByPlanVersionAsc(clientId, pageable)
            .map(CarePlanResponse::from);
    }

    @Transactional
    public CarePlanResponse createCarePlan(UUID clientId, CreateCarePlanRequest req) {
        requireClient(clientId);
        UUID agencyId = TenantContext.get();
        int nextVersion = carePlanRepository.findMaxPlanVersionByClientId(clientId) + 1;
        CarePlan plan = new CarePlan(clientId, agencyId, nextVersion);
        if (req.reviewedByClinicianId() != null) plan.review(req.reviewedByClinicianId());
        try {
            return CarePlanResponse.from(carePlanRepository.saveAndFlush(plan));
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "A concurrent care plan creation occurred; retry the request");
        }
    }

    @Transactional
    public CarePlanResponse activateCarePlan(UUID clientId, UUID carePlanId) {
        requireClient(clientId);
        CarePlan plan = carePlanRepository.findByIdWithLock(carePlanId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Care plan not found: " + carePlanId));
        if (!plan.getClientId().equals(clientId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Care plan not found: " + carePlanId);
        }
        if (plan.getStatus() != CarePlanStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Only DRAFT plans can be activated; current status: " + plan.getStatus());
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
    public Page<AdlTaskResponse> listAdlTasks(UUID clientId, UUID carePlanId, Pageable pageable) {
        requireCarePlan(clientId, carePlanId);
        return adlTaskRepository.findByCarePlanIdOrderBySortOrder(carePlanId, pageable)
            .map(AdlTaskResponse::from);
    }

    @Transactional
    public AdlTaskResponse addAdlTask(UUID clientId, UUID carePlanId, AddAdlTaskRequest req) {
        requireCarePlan(clientId, carePlanId);
        UUID agencyId = TenantContext.get();
        AdlTask task = new AdlTask(carePlanId, agencyId, req.name(), req.assistanceLevel());
        if (req.instructions() != null) task.setInstructions(req.instructions());
        if (req.frequency() != null) task.setFrequency(req.frequency());
        if (req.sortOrder() != null) task.setSortOrder(req.sortOrder());
        return AdlTaskResponse.from(adlTaskRepository.save(task));
    }

    @Transactional
    public void deleteAdlTask(UUID clientId, UUID carePlanId, UUID taskId) {
        requireCarePlan(clientId, carePlanId);
        AdlTask task = adlTaskRepository.findById(taskId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "ADL task not found: " + taskId));
        if (!task.getCarePlanId().equals(carePlanId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "ADL task not found: " + taskId);
        }
        adlTaskRepository.delete(task);
    }

    // --- goals ---

    @Transactional(readOnly = true)
    public Page<GoalResponse> listGoals(UUID clientId, UUID carePlanId, Pageable pageable) {
        requireCarePlan(clientId, carePlanId);
        return goalRepository.findByCarePlanId(carePlanId, pageable)
            .map(GoalResponse::from);
    }

    @Transactional
    public GoalResponse addGoal(UUID clientId, UUID carePlanId, AddGoalRequest req) {
        requireCarePlan(clientId, carePlanId);
        UUID agencyId = TenantContext.get();
        Goal goal = new Goal(carePlanId, agencyId, req.description());
        if (req.targetDate() != null) goal.setTargetDate(req.targetDate());
        return GoalResponse.from(goalRepository.save(goal));
    }

    @Transactional
    public GoalResponse updateGoal(UUID clientId, UUID carePlanId, UUID goalId,
                                   UpdateGoalRequest req) {
        requireCarePlan(clientId, carePlanId);
        Goal goal = goalRepository.findById(goalId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Goal not found: " + goalId));
        if (!goal.getCarePlanId().equals(carePlanId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Goal not found: " + goalId);
        }
        if (req.description() != null) goal.setDescription(req.description());
        if (req.targetDate() != null) goal.setTargetDate(req.targetDate());
        if (req.status() != null) goal.setStatus(req.status());
        return GoalResponse.from(goalRepository.save(goal));
    }

    @Transactional
    public void deleteGoal(UUID clientId, UUID carePlanId, UUID goalId) {
        requireCarePlan(clientId, carePlanId);
        Goal goal = goalRepository.findById(goalId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Goal not found: " + goalId));
        if (!goal.getCarePlanId().equals(carePlanId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Goal not found: " + goalId);
        }
        goalRepository.delete(goal);
    }

    // --- authorizations ---

    @Transactional(readOnly = true)
    public Page<AuthorizationResponse> listAuthorizations(UUID clientId, Pageable pageable) {
        requireClient(clientId);
        return authorizationRepository.findByClientId(clientId, pageable)
            .map(AuthorizationResponse::from);
    }

    @Transactional
    public AuthorizationResponse createAuthorization(UUID clientId,
                                                      CreateAuthorizationRequest req) {
        requireClient(clientId);
        UUID agencyId = TenantContext.get();
        Authorization auth = new Authorization(
            clientId, req.payerId(), req.serviceTypeId(), agencyId,
            req.authNumber(), req.authorizedUnits(), req.unitType(),
            req.startDate(), req.endDate());
        return AuthorizationResponse.from(authorizationRepository.save(auth));
    }

    // --- family portal users ---

    @Transactional(readOnly = true)
    public Page<FamilyPortalUserResponse> listFamilyPortalUsers(UUID clientId, Pageable pageable) {
        requireClient(clientId);
        return familyPortalUserRepository.findByClientId(clientId, pageable)
            .map(FamilyPortalUserResponse::from);
    }

    @Transactional
    public FamilyPortalUserResponse addFamilyPortalUser(UUID clientId,
                                                         AddFamilyPortalUserRequest req) {
        requireClient(clientId);
        UUID agencyId = TenantContext.get();
        FamilyPortalUser fpu = new FamilyPortalUser(clientId, agencyId, req.email());
        fpu.setName(req.name());
        return FamilyPortalUserResponse.from(familyPortalUserRepository.save(fpu));
    }

    @Transactional
    public void removeFamilyPortalUser(UUID clientId, UUID fpuId) {
        requireClient(clientId);
        FamilyPortalUser fpu = familyPortalUserRepository.findById(fpuId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Family portal user not found: " + fpuId));
        if (!fpu.getClientId().equals(clientId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Family portal user not found: " + fpuId);
        }
        familyPortalUserRepository.delete(fpu);
    }

    // --- helpers (package-private for subclasses/tests in same package) ---

    Client requireClient(UUID clientId) {
        return clientRepository.findByIdAndAgencyId(clientId, TenantContext.get())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Client not found: " + clientId));
    }

    private CarePlan requireCarePlan(UUID clientId, UUID carePlanId) {
        requireClient(clientId);
        CarePlan plan = carePlanRepository.findById(carePlanId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Care plan not found: " + carePlanId));
        if (!plan.getClientId().equals(clientId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Care plan not found: " + carePlanId);
        }
        return plan;
    }
}
