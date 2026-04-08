package com.hcare.api.v1.payers;

import com.hcare.api.v1.payers.dto.PayerResponse;
import com.hcare.security.UserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payers")
public class PayerController {

    private final PayerService payerService;

    public PayerController(PayerService payerService) {
        this.payerService = payerService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<Page<PayerResponse>> listPayers(
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable) {  // sort applied in service (case-insensitive by name)
        return ResponseEntity.ok(payerService.listPayers(principal.getAgencyId(), pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<PayerResponse> getPayer(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        return ResponseEntity.ok(payerService.getPayer(id));
    }
}
