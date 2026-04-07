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
import com.hcare.multitenancy.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    public Page<CaregiverResponse> listCaregivers(Pageable pageable) {
        return caregiverRepository.findByAgencyId(TenantContext.get(), pageable)
            .map(CaregiverResponse::from);
    }

    @Transactional
    public CaregiverResponse createCaregiver(CreateCaregiverRequest req) {
        UUID agencyId = TenantContext.get();
        Caregiver c = new Caregiver(agencyId, req.firstName(), req.lastName(), req.email());
        if (req.phone() != null) c.setPhone(req.phone());
        if (req.address() != null) c.setAddress(req.address());
        if (req.hireDate() != null) c.setHireDate(req.hireDate());
        if (req.hasPet() != null) c.setHasPet(req.hasPet());
        return CaregiverResponse.from(caregiverRepository.save(c));
    }

    @Transactional(readOnly = true)
    public CaregiverResponse getCaregiver(UUID caregiverId) {
        return CaregiverResponse.from(requireCaregiver(caregiverId));
    }

    @Transactional
    public CaregiverResponse updateCaregiver(UUID caregiverId, UpdateCaregiverRequest req) {
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
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Caregiver not found: " + caregiverId));
    }

    // --- credentials ---

    @Transactional(readOnly = true)
    public Page<CredentialResponse> listCredentials(UUID caregiverId, Pageable pageable) {
        requireCaregiver(caregiverId);
        return credentialRepository.findByCaregiverId(caregiverId, pageable)
            .map(CredentialResponse::from);
    }

    @Transactional
    public CredentialResponse addCredential(UUID caregiverId, AddCredentialRequest req) {
        requireCaregiver(caregiverId);
        UUID agencyId = TenantContext.get();
        CaregiverCredential cred = new CaregiverCredential(
            caregiverId, agencyId, req.credentialType(), req.issueDate(), req.expiryDate());
        return CredentialResponse.from(credentialRepository.save(cred));
    }

    @Transactional
    public void deleteCredential(UUID caregiverId, UUID credentialId) {
        requireCaregiver(caregiverId);
        CaregiverCredential cred = credentialRepository.findById(credentialId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Credential not found: " + credentialId));
        if (!cred.getCaregiverId().equals(caregiverId)) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Credential not found: " + credentialId);
        }
        credentialRepository.delete(cred);
    }

    // --- background checks ---

    @Transactional(readOnly = true)
    public Page<BackgroundCheckResponse> listBackgroundChecks(UUID caregiverId, Pageable pageable) {
        requireCaregiver(caregiverId);
        return backgroundCheckRepository.findByCaregiverId(caregiverId, pageable)
            .map(BackgroundCheckResponse::from);
    }

    @Transactional
    public BackgroundCheckResponse recordBackgroundCheck(UUID caregiverId,
                                                         RecordBackgroundCheckRequest req) {
        requireCaregiver(caregiverId);
        UUID agencyId = TenantContext.get();
        BackgroundCheck check = new BackgroundCheck(
            caregiverId, agencyId, req.checkType(), req.result(), req.checkedAt());
        if (req.renewalDueDate() != null) check.setRenewalDueDate(req.renewalDueDate());
        return BackgroundCheckResponse.from(backgroundCheckRepository.save(check));
    }

    // --- availability (replace-all semantics) ---

    @Transactional(readOnly = true)
    public AvailabilityResponse getAvailability(UUID caregiverId) {
        requireCaregiver(caregiverId);
        List<AvailabilityResponse.AvailabilityBlock> blocks =
            availabilityRepository.findByCaregiverId(caregiverId).stream()
                .map(AvailabilityResponse.AvailabilityBlock::from)
                .toList();
        return new AvailabilityResponse(caregiverId, blocks);
    }

    @Transactional
    public AvailabilityResponse setAvailability(UUID caregiverId, SetAvailabilityRequest req) {
        requireCaregiver(caregiverId);
        UUID agencyId = TenantContext.get();
        availabilityRepository.deleteByCaregiverIdAndAgencyId(caregiverId, agencyId);
        List<CaregiverAvailability> toSave = req.blocks().stream()
            .map(b -> new CaregiverAvailability(
                caregiverId, agencyId, b.dayOfWeek(), b.startTime(), b.endTime()))
            .toList();
        List<CaregiverAvailability> saved = availabilityRepository.saveAll(toSave);
        List<AvailabilityResponse.AvailabilityBlock> blocks = saved.stream()
            .map(AvailabilityResponse.AvailabilityBlock::from)
            .toList();
        return new AvailabilityResponse(caregiverId, blocks);
    }

    // --- shift history ---

    @Transactional(readOnly = true)
    public Page<ShiftHistoryResponse> listShiftHistory(UUID caregiverId, Pageable pageable) {
        requireCaregiver(caregiverId);
        return shiftRepository.findByCaregiverId(caregiverId, pageable)
            .map(ShiftHistoryResponse::from);
    }
}
