package com.hcare.api.v1.family;

import com.hcare.api.v1.family.dto.PortalDashboardResponse;
import com.hcare.security.UserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/family/portal")
public class FamilyPortalDashboardController {

    private final FamilyPortalService familyPortalService;

    public FamilyPortalDashboardController(FamilyPortalService familyPortalService) {
        this.familyPortalService = familyPortalService;
    }

    /**
     * @PreAuthorize is MANDATORY — the JwtAuthenticationFilter alone is insufficient because
     * an admin JWT also passes the filter. This annotation ensures only FAMILY_PORTAL tokens
     * reach this endpoint.
     */
    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('FAMILY_PORTAL')")
    public ResponseEntity<PortalDashboardResponse> getDashboard(
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID fpuId = principal.getUserId();
        UUID clientId = principal.getClientId();
        UUID agencyId = principal.getAgencyId();
        return ResponseEntity.ok(
            familyPortalService.getDashboard(fpuId, clientId, agencyId));
    }
}
