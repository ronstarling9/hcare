package com.hcare.api.v1.clients;

import com.hcare.api.v1.clients.dto.ClientResponse;
import com.hcare.api.v1.clients.dto.CreateClientRequest;
import com.hcare.api.v1.clients.dto.UpdateClientRequest;
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
}
