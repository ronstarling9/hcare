package com.hcare.api.v1.agencies;

import com.hcare.api.v1.agencies.dto.AgencyResponse;
import com.hcare.api.v1.agencies.dto.RegisterAgencyRequest;
import com.hcare.api.v1.agencies.dto.UpdateAgencyRequest;
import com.hcare.api.v1.auth.dto.LoginResponse;
import com.hcare.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/agencies")
public class AgencyController {

    private final AgencyService agencyService;

    public AgencyController(AgencyService agencyService) {
        this.agencyService = agencyService;
    }

    // Public — no auth required. Permitted in SecurityConfig.
    @PostMapping("/register")
    public ResponseEntity<LoginResponse> register(@Valid @RequestBody RegisterAgencyRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(agencyService.register(req));
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<AgencyResponse> getMyAgency(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(agencyService.getAgency(principal.getAgencyId()));
    }

    @PatchMapping("/me")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AgencyResponse> updateMyAgency(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateAgencyRequest req) {
        return ResponseEntity.ok(agencyService.updateAgency(principal.getAgencyId(), req));
    }
}
