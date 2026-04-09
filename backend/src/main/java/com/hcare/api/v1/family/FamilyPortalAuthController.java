package com.hcare.api.v1.family;

import com.hcare.api.v1.family.dto.PortalVerifyRequest;
import com.hcare.api.v1.family.dto.PortalVerifyResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/family/auth")
public class FamilyPortalAuthController {

    private final FamilyPortalService familyPortalService;

    public FamilyPortalAuthController(FamilyPortalService familyPortalService) {
        this.familyPortalService = familyPortalService;
    }

    // NOTE: This endpoint is intentionally public. The permitAll() rule for
    // POST /api/v1/family/auth/verify was added to SecurityConfig in Plan 1 Task 6.
    @PostMapping("/verify")
    public ResponseEntity<PortalVerifyResponse> verify(
            @Valid @RequestBody PortalVerifyRequest request) {
        return ResponseEntity.ok(familyPortalService.verifyToken(request));
    }
}
