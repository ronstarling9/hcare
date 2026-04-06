package com.hcare.api.v1.scheduling;

import com.hcare.api.v1.scheduling.dto.CreateRecurrencePatternRequest;
import com.hcare.api.v1.scheduling.dto.RecurrencePatternResponse;
import com.hcare.api.v1.scheduling.dto.UpdateRecurrencePatternRequest;
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
@RequestMapping("/api/v1/recurrence-patterns")
public class RecurrencePatternController {

    private final RecurrencePatternService recurrencePatternService;

    public RecurrencePatternController(RecurrencePatternService recurrencePatternService) {
        this.recurrencePatternService = recurrencePatternService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<List<RecurrencePatternResponse>> listPatterns(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(recurrencePatternService.listPatterns(principal.getAgencyId()));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<RecurrencePatternResponse> createPattern(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateRecurrencePatternRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(recurrencePatternService.createPattern(principal.getAgencyId(), request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<RecurrencePatternResponse> getPattern(@PathVariable UUID id) {
        return ResponseEntity.ok(recurrencePatternService.getPattern(id));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<RecurrencePatternResponse> updatePattern(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRecurrencePatternRequest request) {
        return ResponseEntity.ok(recurrencePatternService.updatePattern(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deletePattern(@PathVariable UUID id) {
        recurrencePatternService.deactivatePattern(id);
        return ResponseEntity.noContent().build();
    }
}
