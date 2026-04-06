package com.hcare.api.v1.clients;

import com.hcare.api.v1.clients.dto.AddDiagnosisRequest;
import com.hcare.api.v1.clients.dto.AddMedicationRequest;
import com.hcare.api.v1.clients.dto.ClientResponse;
import com.hcare.api.v1.clients.dto.CreateClientRequest;
import com.hcare.api.v1.clients.dto.DiagnosisResponse;
import com.hcare.api.v1.clients.dto.MedicationResponse;
import com.hcare.api.v1.clients.dto.UpdateClientRequest;
import com.hcare.api.v1.clients.dto.UpdateMedicationRequest;
import com.hcare.domain.Client;
import com.hcare.domain.ClientDiagnosis;
import com.hcare.domain.ClientDiagnosisRepository;
import com.hcare.domain.ClientMedication;
import com.hcare.domain.ClientMedicationRepository;
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
    private final ClientDiagnosisRepository diagnosisRepository;
    private final ClientMedicationRepository medicationRepository;

    public ClientService(ClientRepository clientRepository,
                         ClientDiagnosisRepository diagnosisRepository,
                         ClientMedicationRepository medicationRepository) {
        this.clientRepository = clientRepository;
        this.diagnosisRepository = diagnosisRepository;
        this.medicationRepository = medicationRepository;
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

    // --- diagnoses ---

    @Transactional
    public DiagnosisResponse addDiagnosis(UUID agencyId, UUID clientId, AddDiagnosisRequest req) {
        requireClient(clientId);
        ClientDiagnosis diag = new ClientDiagnosis(clientId, agencyId, req.icd10Code());
        if (req.description() != null) diag.setDescription(req.description());
        if (req.onsetDate() != null) diag.setOnsetDate(req.onsetDate());
        return DiagnosisResponse.from(diagnosisRepository.save(diag));
    }

    @Transactional(readOnly = true)
    public List<DiagnosisResponse> listDiagnoses(UUID agencyId, UUID clientId) {
        requireClient(clientId);
        return diagnosisRepository.findByClientId(clientId).stream()
            .map(DiagnosisResponse::from)
            .toList();
    }

    @Transactional
    public void deleteDiagnosis(UUID agencyId, UUID clientId, UUID diagnosisId) {
        requireClient(clientId);
        ClientDiagnosis diag = diagnosisRepository.findById(diagnosisId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Diagnosis not found"));
        if (!diag.getClientId().equals(clientId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Diagnosis not found");
        }
        diagnosisRepository.delete(diag);
    }

    // --- medications ---

    @Transactional
    public MedicationResponse addMedication(UUID agencyId, UUID clientId, AddMedicationRequest req) {
        requireClient(clientId);
        ClientMedication med = new ClientMedication(clientId, agencyId, req.name());
        if (req.dosage() != null) med.setDosage(req.dosage());
        if (req.route() != null) med.setRoute(req.route());
        if (req.schedule() != null) med.setSchedule(req.schedule());
        if (req.prescriber() != null) med.setPrescriber(req.prescriber());
        return MedicationResponse.from(medicationRepository.save(med));
    }

    @Transactional(readOnly = true)
    public List<MedicationResponse> listMedications(UUID agencyId, UUID clientId) {
        requireClient(clientId);
        return medicationRepository.findByClientId(clientId).stream()
            .map(MedicationResponse::from)
            .toList();
    }

    @Transactional
    public MedicationResponse updateMedication(UUID agencyId, UUID clientId, UUID medicationId,
                                               UpdateMedicationRequest req) {
        requireClient(clientId);
        ClientMedication med = medicationRepository.findById(medicationId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Medication not found"));
        if (!med.getClientId().equals(clientId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Medication not found");
        }
        if (req.name() != null) med.setName(req.name());
        if (req.dosage() != null) med.setDosage(req.dosage());
        if (req.route() != null) med.setRoute(req.route());
        if (req.schedule() != null) med.setSchedule(req.schedule());
        if (req.prescriber() != null) med.setPrescriber(req.prescriber());
        return MedicationResponse.from(medicationRepository.save(med));
    }

    @Transactional
    public void deleteMedication(UUID agencyId, UUID clientId, UUID medicationId) {
        requireClient(clientId);
        ClientMedication med = medicationRepository.findById(medicationId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Medication not found"));
        if (!med.getClientId().equals(clientId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Medication not found");
        }
        medicationRepository.delete(med);
    }

    // --- helpers (package-private for subclasses/tests in same package) ---

    Client requireClient(UUID clientId) {
        return clientRepository.findById(clientId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found"));
    }
}
