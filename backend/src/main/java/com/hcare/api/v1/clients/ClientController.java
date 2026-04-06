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
import com.hcare.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
    public ResponseEntity<List<ClientResponse>> listClients(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(clientService.listClients(principal.getAgencyId()));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<ClientResponse> createClient(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateClientRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(clientService.createClient(principal.getAgencyId(), request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<ClientResponse> getClient(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        return ResponseEntity.ok(clientService.getClient(principal.getAgencyId(), id));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<ClientResponse> updateClient(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateClientRequest request) {
        return ResponseEntity.ok(clientService.updateClient(principal.getAgencyId(), id, request));
    }

    // --- Diagnoses ---

    @GetMapping("/{id}/diagnoses")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<List<DiagnosisResponse>> listDiagnoses(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        return ResponseEntity.ok(clientService.listDiagnoses(principal.getAgencyId(), id));
    }

    @PostMapping("/{id}/diagnoses")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<DiagnosisResponse> addDiagnosis(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody AddDiagnosisRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(clientService.addDiagnosis(principal.getAgencyId(), id, request));
    }

    @DeleteMapping("/{id}/diagnoses/{diagnosisId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<Void> deleteDiagnosis(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @PathVariable UUID diagnosisId) {
        clientService.deleteDiagnosis(principal.getAgencyId(), id, diagnosisId);
        return ResponseEntity.noContent().build();
    }

    // --- Medications ---

    @GetMapping("/{id}/medications")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<List<MedicationResponse>> listMedications(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        return ResponseEntity.ok(clientService.listMedications(principal.getAgencyId(), id));
    }

    @PostMapping("/{id}/medications")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<MedicationResponse> addMedication(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody AddMedicationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(clientService.addMedication(principal.getAgencyId(), id, request));
    }

    @PatchMapping("/{id}/medications/{medicationId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<MedicationResponse> updateMedication(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @PathVariable UUID medicationId,
            @Valid @RequestBody UpdateMedicationRequest request) {
        return ResponseEntity.ok(
            clientService.updateMedication(principal.getAgencyId(), id, medicationId, request));
    }

    @DeleteMapping("/{id}/medications/{medicationId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<Void> deleteMedication(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @PathVariable UUID medicationId) {
        clientService.deleteMedication(principal.getAgencyId(), id, medicationId);
        return ResponseEntity.noContent().build();
    }

    // --- Care Plans ---

    @GetMapping("/{id}/care-plans")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<List<CarePlanResponse>> listCarePlans(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        return ResponseEntity.ok(clientService.listCarePlans(principal.getAgencyId(), id));
    }

    @PostMapping("/{id}/care-plans")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<CarePlanResponse> createCarePlan(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody CreateCarePlanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(clientService.createCarePlan(principal.getAgencyId(), id, request));
    }

    @PostMapping("/{id}/care-plans/{planId}/activate")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<CarePlanResponse> activateCarePlan(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @PathVariable UUID planId) {
        return ResponseEntity.ok(clientService.activateCarePlan(principal.getAgencyId(), id, planId));
    }

    // --- ADL Tasks ---

    @GetMapping("/{id}/care-plans/{planId}/adl-tasks")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<List<AdlTaskResponse>> listAdlTasks(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @PathVariable UUID planId) {
        return ResponseEntity.ok(clientService.listAdlTasks(principal.getAgencyId(), id, planId));
    }

    @PostMapping("/{id}/care-plans/{planId}/adl-tasks")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<AdlTaskResponse> addAdlTask(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @PathVariable UUID planId,
            @Valid @RequestBody AddAdlTaskRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(clientService.addAdlTask(principal.getAgencyId(), id, planId, request));
    }

    @DeleteMapping("/{id}/care-plans/{planId}/adl-tasks/{taskId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<Void> deleteAdlTask(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @PathVariable UUID planId,
            @PathVariable UUID taskId) {
        clientService.deleteAdlTask(principal.getAgencyId(), id, planId, taskId);
        return ResponseEntity.noContent().build();
    }

    // --- Goals ---

    @GetMapping("/{id}/care-plans/{planId}/goals")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<List<GoalResponse>> listGoals(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @PathVariable UUID planId) {
        return ResponseEntity.ok(clientService.listGoals(principal.getAgencyId(), id, planId));
    }

    @PostMapping("/{id}/care-plans/{planId}/goals")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<GoalResponse> addGoal(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @PathVariable UUID planId,
            @Valid @RequestBody AddGoalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(clientService.addGoal(principal.getAgencyId(), id, planId, request));
    }

    @PatchMapping("/{id}/care-plans/{planId}/goals/{goalId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<GoalResponse> updateGoal(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @PathVariable UUID planId,
            @PathVariable UUID goalId,
            @Valid @RequestBody UpdateGoalRequest request) {
        return ResponseEntity.ok(
            clientService.updateGoal(principal.getAgencyId(), id, planId, goalId, request));
    }

    @DeleteMapping("/{id}/care-plans/{planId}/goals/{goalId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<Void> deleteGoal(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @PathVariable UUID planId,
            @PathVariable UUID goalId) {
        clientService.deleteGoal(principal.getAgencyId(), id, planId, goalId);
        return ResponseEntity.noContent().build();
    }

    // --- Authorizations ---

    @GetMapping("/{id}/authorizations")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<List<AuthorizationResponse>> listAuthorizations(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        return ResponseEntity.ok(clientService.listAuthorizations(principal.getAgencyId(), id));
    }

    @PostMapping("/{id}/authorizations")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<AuthorizationResponse> createAuthorization(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody CreateAuthorizationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(clientService.createAuthorization(principal.getAgencyId(), id, request));
    }

    // --- Family Portal Users ---

    @GetMapping("/{id}/family-portal-users")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<List<FamilyPortalUserResponse>> listFamilyPortalUsers(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        return ResponseEntity.ok(clientService.listFamilyPortalUsers(principal.getAgencyId(), id));
    }

    @PostMapping("/{id}/family-portal-users")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<FamilyPortalUserResponse> addFamilyPortalUser(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody AddFamilyPortalUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(clientService.addFamilyPortalUser(principal.getAgencyId(), id, request));
    }

    @DeleteMapping("/{id}/family-portal-users/{fpuId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<Void> removeFamilyPortalUser(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @PathVariable UUID fpuId) {
        clientService.removeFamilyPortalUser(principal.getAgencyId(), id, fpuId);
        return ResponseEntity.noContent().build();
    }
}
