package com.hcare.api.v1.clients;

import com.hcare.api.v1.clients.dto.ClientResponse;
import com.hcare.api.v1.clients.dto.CreateClientRequest;
import com.hcare.api.v1.clients.dto.UpdateClientRequest;
import com.hcare.domain.Client;
import com.hcare.domain.ClientRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class ClientService {

    private final ClientRepository clientRepository;

    public ClientService(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    @Transactional(readOnly = true)
    public List<ClientResponse> listClients(UUID agencyId) {
        return clientRepository.findByAgencyId(agencyId).stream()
            .map(ClientResponse::from)
            .toList();
    }

    @Transactional
    public ClientResponse createClient(UUID agencyId, CreateClientRequest req) {
        Client client = new Client(agencyId, req.firstName(), req.lastName(), req.dateOfBirth());
        if (req.address() != null) client.setAddress(req.address());
        if (req.phone() != null) client.setPhone(req.phone());
        if (req.medicaidId() != null) client.setMedicaidId(req.medicaidId());
        if (req.serviceState() != null) client.setServiceState(req.serviceState());
        if (req.preferredCaregiverGender() != null) client.setPreferredCaregiverGender(req.preferredCaregiverGender());
        if (req.preferredLanguages() != null) client.setPreferredLanguages(req.preferredLanguages());
        if (req.noPetCaregiver() != null) client.setNoPetCaregiver(req.noPetCaregiver());
        return ClientResponse.from(clientRepository.save(client));
    }

    @Transactional(readOnly = true)
    public ClientResponse getClient(UUID agencyId, UUID clientId) {
        return ClientResponse.from(requireClient(clientId));
    }

    @Transactional
    public ClientResponse updateClient(UUID agencyId, UUID clientId, UpdateClientRequest req) {
        Client client = requireClient(clientId);
        if (req.firstName() != null) client.setFirstName(req.firstName());
        if (req.lastName() != null) client.setLastName(req.lastName());
        if (req.dateOfBirth() != null) client.setDateOfBirth(req.dateOfBirth());
        if (req.address() != null) client.setAddress(req.address());
        if (req.phone() != null) client.setPhone(req.phone());
        if (req.medicaidId() != null) client.setMedicaidId(req.medicaidId());
        if (req.serviceState() != null) client.setServiceState(req.serviceState());
        if (req.preferredCaregiverGender() != null) client.setPreferredCaregiverGender(req.preferredCaregiverGender());
        if (req.preferredLanguages() != null) client.setPreferredLanguages(req.preferredLanguages());
        if (req.noPetCaregiver() != null) client.setNoPetCaregiver(req.noPetCaregiver());
        if (req.status() != null) client.setStatus(req.status());
        return ClientResponse.from(clientRepository.save(client));
    }

    // --- helpers (package-private for subclasses/tests in same package) ---

    Client requireClient(UUID clientId) {
        return clientRepository.findById(clientId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found"));
    }
}
