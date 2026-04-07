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
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clients")
public class ClientController {

    private final ClientService clientService;

    public ClientController(ClientService clientService) {
        this.clientService = clientService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<Page<ClientResponse>> listClients(
            @PageableDefault(size = 25) Pageable pageable) {
        return ResponseEntity.ok(clientService.listClients(pageable));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<ClientResponse> createClient(
            @Valid @RequestBody CreateClientRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(clientService.createClient(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<ClientResponse> getClient(
            @PathVariable UUID id) {
        return ResponseEntity.ok(clientService.getClient(id));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<ClientResponse> updateClient(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateClientRequest request) {
        return ResponseEntity.ok(clientService.updateClient(id, request));
    }

    // --- Diagnoses ---

    @GetMapping("/{id}/diagnoses")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<Page<DiagnosisResponse>> listDiagnoses(
            @PathVariable UUID id,
            @PageableDefault(size = 25) Pageable pageable) {
        return ResponseEntity.ok(clientService.listDiagnoses(id, pageable));
    }

    @PostMapping("/{id}/diagnoses")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<DiagnosisResponse> addDiagnosis(
            @PathVariable UUID id,
            @Valid @RequestBody AddDiagnosisRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(clientService.addDiagnosis(id, request));
    }

    @DeleteMapping("/{id}/diagnoses/{diagnosisId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<Void> deleteDiagnosis(
            @PathVariable UUID id,
            @PathVariable UUID diagnosisId) {
        clientService.deleteDiagnosis(id, diagnosisId);
        return ResponseEntity.noContent().build();
    }

    // --- Medications ---

    @GetMapping("/{id}/medications")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<Page<MedicationResponse>> listMedications(
            @PathVariable UUID id,
            @PageableDefault(size = 25) Pageable pageable) {
        return ResponseEntity.ok(clientService.listMedications(id, pageable));
    }

    @PostMapping("/{id}/medications")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<MedicationResponse> addMedication(
            @PathVariable UUID id,
            @Valid @RequestBody AddMedicationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(clientService.addMedication(id, request));
    }

    @PatchMapping("/{id}/medications/{medicationId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<MedicationResponse> updateMedication(
            @PathVariable UUID id,
            @PathVariable UUID medicationId,
            @Valid @RequestBody UpdateMedicationRequest request) {
        return ResponseEntity.ok(
            clientService.updateMedication(id, medicationId, request));
    }

    @DeleteMapping("/{id}/medications/{medicationId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<Void> deleteMedication(
            @PathVariable UUID id,
            @PathVariable UUID medicationId) {
        clientService.deleteMedication(id, medicationId);
        return ResponseEntity.noContent().build();
    }

    // --- Care Plans ---

    @GetMapping("/{id}/care-plans")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<Page<CarePlanResponse>> listCarePlans(
            @PathVariable UUID id,
            @PageableDefault(size = 25) Pageable pageable) {
        return ResponseEntity.ok(clientService.listCarePlans(id, pageable));
    }

    @PostMapping("/{id}/care-plans")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<CarePlanResponse> createCarePlan(
            @PathVariable UUID id,
            @Valid @RequestBody CreateCarePlanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(clientService.createCarePlan(id, request));
    }

    @PostMapping("/{id}/care-plans/{planId}/activate")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<CarePlanResponse> activateCarePlan(
            @PathVariable UUID id,
            @PathVariable UUID planId) {
        return ResponseEntity.ok(clientService.activateCarePlan(id, planId));
    }

    // --- ADL Tasks ---

    @GetMapping("/{id}/care-plans/{planId}/adl-tasks")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<Page<AdlTaskResponse>> listAdlTasks(
            @PathVariable UUID id,
            @PathVariable UUID planId,
            @PageableDefault(size = 25) Pageable pageable) {
        return ResponseEntity.ok(clientService.listAdlTasks(id, planId, pageable));
    }

    @PostMapping("/{id}/care-plans/{planId}/adl-tasks")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<AdlTaskResponse> addAdlTask(
            @PathVariable UUID id,
            @PathVariable UUID planId,
            @Valid @RequestBody AddAdlTaskRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(clientService.addAdlTask(id, planId, request));
    }

    @DeleteMapping("/{id}/care-plans/{planId}/adl-tasks/{taskId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<Void> deleteAdlTask(
            @PathVariable UUID id,
            @PathVariable UUID planId,
            @PathVariable UUID taskId) {
        clientService.deleteAdlTask(id, planId, taskId);
        return ResponseEntity.noContent().build();
    }

    // --- Goals ---

    @GetMapping("/{id}/care-plans/{planId}/goals")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<Page<GoalResponse>> listGoals(
            @PathVariable UUID id,
            @PathVariable UUID planId,
            @PageableDefault(size = 25) Pageable pageable) {
        return ResponseEntity.ok(clientService.listGoals(id, planId, pageable));
    }

    @PostMapping("/{id}/care-plans/{planId}/goals")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<GoalResponse> addGoal(
            @PathVariable UUID id,
            @PathVariable UUID planId,
            @Valid @RequestBody AddGoalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(clientService.addGoal(id, planId, request));
    }

    @PatchMapping("/{id}/care-plans/{planId}/goals/{goalId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<GoalResponse> updateGoal(
            @PathVariable UUID id,
            @PathVariable UUID planId,
            @PathVariable UUID goalId,
            @Valid @RequestBody UpdateGoalRequest request) {
        return ResponseEntity.ok(
            clientService.updateGoal(id, planId, goalId, request));
    }

    @DeleteMapping("/{id}/care-plans/{planId}/goals/{goalId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<Void> deleteGoal(
            @PathVariable UUID id,
            @PathVariable UUID planId,
            @PathVariable UUID goalId) {
        clientService.deleteGoal(id, planId, goalId);
        return ResponseEntity.noContent().build();
    }

    // --- Authorizations ---

    @GetMapping("/{id}/authorizations")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<Page<AuthorizationResponse>> listAuthorizations(
            @PathVariable UUID id,
            @PageableDefault(size = 25) Pageable pageable) {
        return ResponseEntity.ok(clientService.listAuthorizations(id, pageable));
    }

    @PostMapping("/{id}/authorizations")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<AuthorizationResponse> createAuthorization(
            @PathVariable UUID id,
            @Valid @RequestBody CreateAuthorizationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(clientService.createAuthorization(id, request));
    }

    // --- Family Portal Users ---

    @GetMapping("/{id}/family-portal-users")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<Page<FamilyPortalUserResponse>> listFamilyPortalUsers(
            @PathVariable UUID id,
            @PageableDefault(size = 25) Pageable pageable) {
        return ResponseEntity.ok(clientService.listFamilyPortalUsers(id, pageable));
    }

    @PostMapping("/{id}/family-portal-users")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<FamilyPortalUserResponse> addFamilyPortalUser(
            @PathVariable UUID id,
            @Valid @RequestBody AddFamilyPortalUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(clientService.addFamilyPortalUser(id, request));
    }

    @DeleteMapping("/{id}/family-portal-users/{fpuId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<Void> removeFamilyPortalUser(
            @PathVariable UUID id,
            @PathVariable UUID fpuId) {
        clientService.removeFamilyPortalUser(id, fpuId);
        return ResponseEntity.noContent().build();
    }
}
