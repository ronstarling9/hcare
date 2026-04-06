package com.hcare.api.v1.caregivers;

import com.hcare.api.v1.caregivers.dto.AddCredentialRequest;
import com.hcare.api.v1.caregivers.dto.CaregiverResponse;
import com.hcare.api.v1.caregivers.dto.CreateCaregiverRequest;
import com.hcare.api.v1.caregivers.dto.CredentialResponse;
import com.hcare.api.v1.caregivers.dto.UpdateCaregiverRequest;
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
@RequestMapping("/api/v1/caregivers")
public class CaregiverController {

    private final CaregiverService caregiverService;

    public CaregiverController(CaregiverService caregiverService) {
        this.caregiverService = caregiverService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<List<CaregiverResponse>> listCaregivers(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(caregiverService.listCaregivers(principal.getAgencyId()));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<CaregiverResponse> createCaregiver(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateCaregiverRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(caregiverService.createCaregiver(principal.getAgencyId(), request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<CaregiverResponse> getCaregiver(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        return ResponseEntity.ok(caregiverService.getCaregiver(principal.getAgencyId(), id));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<CaregiverResponse> updateCaregiver(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCaregiverRequest request) {
        return ResponseEntity.ok(
            caregiverService.updateCaregiver(principal.getAgencyId(), id, request));
    }

    // --- Credentials ---

    @GetMapping("/{id}/credentials")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<List<CredentialResponse>> listCredentials(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        return ResponseEntity.ok(caregiverService.listCredentials(principal.getAgencyId(), id));
    }

    @PostMapping("/{id}/credentials")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<CredentialResponse> addCredential(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody AddCredentialRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(caregiverService.addCredential(principal.getAgencyId(), id, request));
    }

    @DeleteMapping("/{id}/credentials/{credentialId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<Void> deleteCredential(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @PathVariable UUID credentialId) {
        caregiverService.deleteCredential(principal.getAgencyId(), id, credentialId);
        return ResponseEntity.noContent().build();
    }
}
