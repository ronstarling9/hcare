package com.hcare.api.v1.scheduling;

import com.hcare.domain.ShiftOffer;
import com.hcare.domain.ShiftOfferRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ShiftOfferCreationService {

    private final ShiftOfferRepository shiftOfferRepository;

    public ShiftOfferCreationService(ShiftOfferRepository shiftOfferRepository) {
        this.shiftOfferRepository = shiftOfferRepository;
    }

    /**
     * Creates a shift offer for the given caregiver if one does not already exist.
     * Runs in a separate transaction (REQUIRES_NEW) so that a DataIntegrityViolationException
     * from a concurrent duplicate insert (unique constraint on shift_id, caregiver_id) is isolated
     * to this sub-transaction and does not poison the caller's outer transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createOfferIfAbsent(UUID shiftId, UUID caregiverId, UUID agencyId) {
        if (shiftOfferRepository.findByCaregiverIdAndShiftId(caregiverId, shiftId).isEmpty()) {
            shiftOfferRepository.save(new ShiftOffer(shiftId, caregiverId, agencyId));
        }
    }
}
