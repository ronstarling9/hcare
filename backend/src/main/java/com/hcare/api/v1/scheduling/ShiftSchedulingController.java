package com.hcare.api.v1.scheduling;

import com.hcare.api.v1.scheduling.dto.AssignCaregiverRequest;
import com.hcare.api.v1.scheduling.dto.CancelShiftRequest;
import com.hcare.api.v1.scheduling.dto.CreateShiftRequest;
import com.hcare.api.v1.scheduling.dto.RankedCaregiverResponse;
import com.hcare.api.v1.scheduling.dto.RespondToOfferRequest;
import com.hcare.api.v1.scheduling.dto.ShiftOfferSummary;
import com.hcare.api.v1.scheduling.dto.ShiftSummaryResponse;
import com.hcare.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shifts")
public class ShiftSchedulingController {

    private final ShiftSchedulingService shiftSchedulingService;

    public ShiftSchedulingController(ShiftSchedulingService shiftSchedulingService) {
        this.shiftSchedulingService = shiftSchedulingService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<List<ShiftSummaryResponse>> listShifts(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return ResponseEntity.ok(shiftSchedulingService.listShifts(principal.getAgencyId(), start, end));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<ShiftSummaryResponse> createShift(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateShiftRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(shiftSchedulingService.createShift(principal.getAgencyId(), request));
    }

    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<ShiftSummaryResponse> assignCaregiver(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody AssignCaregiverRequest request) {
        return ResponseEntity.ok(shiftSchedulingService.assignCaregiver(principal.getAgencyId(), id, request));
    }

    @PatchMapping("/{id}/unassign")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<ShiftSummaryResponse> unassignCaregiver(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        return ResponseEntity.ok(shiftSchedulingService.unassignCaregiver(principal.getAgencyId(), id));
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<ShiftSummaryResponse> cancelShift(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) CancelShiftRequest request) {
        return ResponseEntity.ok(shiftSchedulingService.cancelShift(principal.getAgencyId(), id,
            request != null ? request : new CancelShiftRequest(null)));
    }

    @GetMapping("/{id}/candidates")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<List<RankedCaregiverResponse>> getCandidates(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        return ResponseEntity.ok(shiftSchedulingService.getCandidates(principal.getAgencyId(), id));
    }

    @PostMapping("/{id}/broadcast")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<List<ShiftOfferSummary>> broadcastShift(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        return ResponseEntity.ok(shiftSchedulingService.broadcastShift(principal.getAgencyId(), id));
    }

    @GetMapping("/{id}/offers")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<List<ShiftOfferSummary>> listOffers(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        return ResponseEntity.ok(shiftSchedulingService.listOffers(principal.getAgencyId(), id));
    }

    @PostMapping("/{id}/offers/{offerId}/respond")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<ShiftOfferSummary> respondToOffer(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @PathVariable UUID offerId,
            @Valid @RequestBody RespondToOfferRequest request) {
        return ResponseEntity.ok(shiftSchedulingService.respondToOffer(principal.getAgencyId(), id, offerId, request));
    }
}
