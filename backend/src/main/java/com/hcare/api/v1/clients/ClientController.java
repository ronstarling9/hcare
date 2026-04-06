package com.hcare.api.v1.clients;

import com.hcare.api.v1.clients.dto.AddDiagnosisRequest;
import com.hcare.api.v1.clients.dto.AddMedicationRequest;
import com.hcare.api.v1.clients.dto.ClientResponse;
import com.hcare.api.v1.clients.dto.CreateClientRequest;
import com.hcare.api.v1.clients.dto.DiagnosisResponse;
import com.hcare.api.v1.clients.dto.MedicationResponse;
import com.hcare.api.v1.clients.dto.UpdateClientRequest;
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
}
