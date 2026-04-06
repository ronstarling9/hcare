package com.hcare.api.v1.caregivers;

import com.hcare.api.v1.caregivers.dto.AddCredentialRequest;
import com.hcare.api.v1.caregivers.dto.AvailabilityResponse;
import com.hcare.api.v1.caregivers.dto.BackgroundCheckResponse;
import com.hcare.api.v1.caregivers.dto.CaregiverResponse;
import com.hcare.api.v1.caregivers.dto.CreateCaregiverRequest;
import com.hcare.api.v1.caregivers.dto.CredentialResponse;
import com.hcare.api.v1.caregivers.dto.RecordBackgroundCheckRequest;
import com.hcare.api.v1.caregivers.dto.SetAvailabilityRequest;
import com.hcare.api.v1.caregivers.dto.ShiftHistoryResponse;
import com.hcare.api.v1.caregivers.dto.UpdateCaregiverRequest;
import com.hcare.domain.BackgroundCheck;
import com.hcare.domain.BackgroundCheckRepository;
import com.hcare.domain.Caregiver;
import com.hcare.domain.CaregiverAvailability;
import com.hcare.domain.CaregiverAvailabilityRepository;
import com.hcare.domain.CaregiverCredential;
import com.hcare.domain.CaregiverCredentialRepository;
import com.hcare.domain.CaregiverRepository;
import com.hcare.domain.Shift;
import com.hcare.domain.ShiftRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class CaregiverService {

    private final CaregiverRepository caregiverRepository;
    private final CaregiverCredentialRepository credentialRepository;
    private final BackgroundCheckRepository backgroundCheckRepository;
    private final CaregiverAvailabilityRepository availabilityRepository;
    private final ShiftRepository shiftRepository;

    public CaregiverService(CaregiverRepository caregiverRepository,
                            CaregiverCredentialRepository credentialRepository,
                            BackgroundCheckRepository backgroundCheckRepository,
                            CaregiverAvailabilityRepository availabilityRepository,
                            ShiftRepository shiftRepository) {
        this.caregiverRepository = caregiverRepository;
        this.credentialRepository = credentialRepository;
        this.backgroundCheckRepository = backgroundCheckRepository;
        this.availabilityRepository = availabilityRepository;
        this.shiftRepository = shiftRepository;
    }

    @Transactional(readOnly = true)
    public List<CaregiverResponse> listCaregivers(UUID agencyId) {
        return caregiverRepository.findByAgencyId(agencyId).stream()
            .map(CaregiverResponse::from)
            .toList();
    }

    @Transactional
    public CaregiverResponse createCaregiver(UUID agencyId, CreateCaregiverRequest req) {
        Caregiver c = new Caregiver(agencyId, req.firstName(), req.lastName(), req.email());
        if (req.phone() != null) c.setPhone(req.phone());
        if (req.address() != null) c.setAddress(req.address());
        if (req.hireDate() != null) c.setHireDate(req.hireDate());
        if (req.hasPet() != null) c.setHasPet(req.hasPet());
        return CaregiverResponse.from(caregiverRepository.save(c));
    }

    @Transactional(readOnly = true)
    public CaregiverResponse getCaregiver(UUID agencyId, UUID caregiverId) {
        return CaregiverResponse.from(requireCaregiver(caregiverId));
    }

    @Transactional
    public CaregiverResponse updateCaregiver(UUID agencyId, UUID caregiverId,
                                              UpdateCaregiverRequest req) {
        Caregiver c = requireCaregiver(caregiverId);
        if (req.firstName() != null) c.setFirstName(req.firstName());
        if (req.lastName() != null) c.setLastName(req.lastName());
        if (req.email() != null) c.setEmail(req.email());
        if (req.phone() != null) c.setPhone(req.phone());
        if (req.address() != null) c.setAddress(req.address());
        if (req.hireDate() != null) c.setHireDate(req.hireDate());
        if (req.hasPet() != null) c.setHasPet(req.hasPet());
        if (req.status() != null) c.setStatus(req.status());
        return CaregiverResponse.from(caregiverRepository.save(c));
    }

    Caregiver requireCaregiver(UUID caregiverId) {
        return caregiverRepository.findById(caregiverId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Caregiver not found"));
    }

    // --- credentials ---

    @Transactional(readOnly = true)
    public List<CredentialResponse> listCredentials(UUID agencyId, UUID caregiverId) {
        requireCaregiver(caregiverId);
        return credentialRepository.findByCaregiverId(caregiverId).stream()
            .map(CredentialResponse::from)
            .toList();
    }

    @Transactional
    public CredentialResponse addCredential(UUID agencyId, UUID caregiverId, AddCredentialRequest req) {
        requireCaregiver(caregiverId);
        CaregiverCredential cred = new CaregiverCredential(
            caregiverId, agencyId, req.credentialType(), req.issueDate(), req.expiryDate());
        return CredentialResponse.from(credentialRepository.save(cred));
    }

    @Transactional
    public void deleteCredential(UUID agencyId, UUID caregiverId, UUID credentialId) {
        requireCaregiver(caregiverId);
        CaregiverCredential cred = credentialRepository.findById(credentialId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Credential not found"));
        if (!cred.getCaregiverId().equals(caregiverId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Credential not found");
        }
        credentialRepository.delete(cred);
    }

    // --- background checks ---

    @Transactional(readOnly = true)
    public List<BackgroundCheckResponse> listBackgroundChecks(UUID agencyId, UUID caregiverId) {
        requireCaregiver(caregiverId);
        return backgroundCheckRepository.findByCaregiverId(caregiverId).stream()
            .map(BackgroundCheckResponse::from)
            .toList();
    }

    @Transactional
    public BackgroundCheckResponse recordBackgroundCheck(UUID agencyId, UUID caregiverId,
                                                          RecordBackgroundCheckRequest req) {
        requireCaregiver(caregiverId);
        BackgroundCheck check = new BackgroundCheck(
            caregiverId, agencyId, req.checkType(), req.result(), req.checkedAt());
        if (req.renewalDueDate() != null) check.setRenewalDueDate(req.renewalDueDate());
        return BackgroundCheckResponse.from(backgroundCheckRepository.save(check));
    }

    // --- availability (replace-all semantics) ---

    @Transactional(readOnly = true)
    public AvailabilityResponse getAvailability(UUID agencyId, UUID caregiverId) {
        requireCaregiver(caregiverId);
        List<AvailabilityResponse.AvailabilityBlock> blocks =
            availabilityRepository.findByCaregiverId(caregiverId).stream()
                .map(AvailabilityResponse.AvailabilityBlock::from)
                .toList();
        return new AvailabilityResponse(caregiverId, blocks);
    }

    @Transactional
    public AvailabilityResponse setAvailability(UUID agencyId, UUID caregiverId,
                                                 SetAvailabilityRequest req) {
        requireCaregiver(caregiverId);
        availabilityRepository.deleteByCaregiverId(caregiverId);
        List<CaregiverAvailability> saved = req.blocks().stream()
            .map(b -> availabilityRepository.save(
                new CaregiverAvailability(caregiverId, agencyId, b.dayOfWeek(), b.startTime(), b.endTime())))
            .toList();
        List<AvailabilityResponse.AvailabilityBlock> blocks = saved.stream()
            .map(AvailabilityResponse.AvailabilityBlock::from)
            .toList();
        return new AvailabilityResponse(caregiverId, blocks);
    }

    // --- shift history ---

    @Transactional(readOnly = true)
    public List<ShiftHistoryResponse> listShiftHistory(UUID agencyId, UUID caregiverId) {
        requireCaregiver(caregiverId);
        return shiftRepository.findByCaregiverId(caregiverId).stream()
            .map(ShiftHistoryResponse::from)
            .toList();
    }
}
