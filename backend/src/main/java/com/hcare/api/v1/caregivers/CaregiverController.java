package com.hcare.api.v1.caregivers;

import com.hcare.api.v1.caregivers.dto.AddCredentialRequest;
import com.hcare.api.v1.caregivers.dto.AvailabilityResponse;
import com.hcare.api.v1.caregivers.dto.BackgroundCheckResponse;
import com.hcare.api.v1.caregivers.dto.CaregiverResponse;
import com.hcare.api.v1.caregivers.dto.CreateCaregiverRequest;
import com.hcare.api.v1.caregivers.dto.CredentialResponse;
import com.hcare.api.v1.caregivers.dto.RecordBackgroundCheckRequest;
import com.hcare.api.v1.caregivers.dto.SetAvailabilityRequest;
import com.hcare.api.v1.caregivers.dto.ShiftHistoryResponse;
import com.hcare.api.v1.caregivers.dto.UpdateCaregiverRequest;
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
@RequestMapping("/api/v1/caregivers")
public class CaregiverController {

    private final CaregiverService caregiverService;

    public CaregiverController(CaregiverService caregiverService) {
        this.caregiverService = caregiverService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<Page<CaregiverResponse>> listCaregivers(
            @PageableDefault(size = 25) Pageable pageable) {
        return ResponseEntity.ok(caregiverService.listCaregivers(pageable));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<CaregiverResponse> createCaregiver(
            @Valid @RequestBody CreateCaregiverRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(caregiverService.createCaregiver(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<CaregiverResponse> getCaregiver(
            @PathVariable UUID id) {
        return ResponseEntity.ok(caregiverService.getCaregiver(id));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<CaregiverResponse> updateCaregiver(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCaregiverRequest request) {
        return ResponseEntity.ok(caregiverService.updateCaregiver(id, request));
    }

    // --- Credentials ---

    @GetMapping("/{id}/credentials")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<Page<CredentialResponse>> listCredentials(
            @PathVariable UUID id,
            @PageableDefault(size = 25) Pageable pageable) {
        return ResponseEntity.ok(caregiverService.listCredentials(id, pageable));
    }

    @PostMapping("/{id}/credentials")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<CredentialResponse> addCredential(
            @PathVariable UUID id,
            @Valid @RequestBody AddCredentialRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(caregiverService.addCredential(id, request));
    }

    @DeleteMapping("/{id}/credentials/{credentialId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<Void> deleteCredential(
            @PathVariable UUID id,
            @PathVariable UUID credentialId) {
        caregiverService.deleteCredential(id, credentialId);
        return ResponseEntity.noContent().build();
    }

    // --- Background Checks ---

    @GetMapping("/{id}/background-checks")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<Page<BackgroundCheckResponse>> listBackgroundChecks(
            @PathVariable UUID id,
            @PageableDefault(size = 25) Pageable pageable) {
        return ResponseEntity.ok(caregiverService.listBackgroundChecks(id, pageable));
    }

    @PostMapping("/{id}/background-checks")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<BackgroundCheckResponse> recordBackgroundCheck(
            @PathVariable UUID id,
            @Valid @RequestBody RecordBackgroundCheckRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(caregiverService.recordBackgroundCheck(id, request));
    }

    // --- Availability ---

    @GetMapping("/{id}/availability")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<AvailabilityResponse> getAvailability(
            @PathVariable UUID id) {
        return ResponseEntity.ok(caregiverService.getAvailability(id));
    }

    @PutMapping("/{id}/availability")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<AvailabilityResponse> setAvailability(
            @PathVariable UUID id,
            @Valid @RequestBody SetAvailabilityRequest request) {
        return ResponseEntity.ok(caregiverService.setAvailability(id, request));
    }

    // --- Shift History ---

    @GetMapping("/{id}/shift-history")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<Page<ShiftHistoryResponse>> listShiftHistory(
            @PathVariable UUID id,
            @PageableDefault(size = 25) Pageable pageable) {
        return ResponseEntity.ok(caregiverService.listShiftHistory(id, pageable));
    }
}
