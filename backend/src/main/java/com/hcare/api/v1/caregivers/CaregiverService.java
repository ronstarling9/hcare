package com.hcare.api.v1.caregivers;

import com.hcare.api.v1.caregivers.dto.CaregiverResponse;
import com.hcare.api.v1.caregivers.dto.CreateCaregiverRequest;
import com.hcare.api.v1.caregivers.dto.UpdateCaregiverRequest;
import com.hcare.domain.Caregiver;
import com.hcare.domain.CaregiverRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class CaregiverService {

    private final CaregiverRepository caregiverRepository;

    public CaregiverService(CaregiverRepository caregiverRepository) {
        this.caregiverRepository = caregiverRepository;
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
}
