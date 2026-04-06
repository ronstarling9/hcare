package com.hcare.api.v1.visits;

import com.hcare.api.v1.visits.dto.ClockInRequest;
import com.hcare.api.v1.visits.dto.ClockOutRequest;
import com.hcare.api.v1.visits.dto.ShiftDetailResponse;
import com.hcare.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shifts")
public class VisitController {

    private final VisitService visitService;

    public VisitController(VisitService visitService) {
        this.visitService = visitService;
    }

    @PostMapping("/{id}/clock-in")
    public ResponseEntity<ShiftDetailResponse> clockIn(
            @PathVariable UUID id,
            @Valid @RequestBody ClockInRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(visitService.clockIn(
            id, principal.getUserId(), resolveClientIp(httpRequest), request));
    }

    @PostMapping("/{id}/clock-out")
    public ResponseEntity<ShiftDetailResponse> clockOut(
            @PathVariable UUID id,
            @Valid @RequestBody ClockOutRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(visitService.clockOut(
            id, principal.getUserId(), resolveClientIp(httpRequest), request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ShiftDetailResponse> getShift(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(visitService.getShiftDetail(
            id, principal.getUserId(), resolveClientIp(httpRequest)));
    }

    private static String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // X-Forwarded-For may be a comma-separated list; take the first (original client)
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
