package com.hcare.api.v1.scheduling;

import com.hcare.domain.ShiftOffer;
import com.hcare.domain.ShiftOfferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ShiftOfferCreationService {

    private static final Logger log = LoggerFactory.getLogger(ShiftOfferCreationService.class);

    private final ShiftOfferRepository shiftOfferRepository;

    public ShiftOfferCreationService(ShiftOfferRepository shiftOfferRepository) {
        this.shiftOfferRepository = shiftOfferRepository;
    }

    /**
     * Creates a shift offer for the given caregiver if one does not already exist.
     * Runs in a separate transaction (REQUIRES_NEW) so that a DataIntegrityViolationException
     * from a concurrent duplicate insert (unique constraint on shift_id, caregiver_id) is isolated
     * to this sub-transaction and does not poison the caller's outer transaction.
     * A DataIntegrityViolationException is caught and swallowed — another thread won the race and
     * the offer already exists, so the outcome is idempotently correct.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createOfferIfAbsent(UUID shiftId, UUID caregiverId, UUID agencyId) {
        if (shiftOfferRepository.findByCaregiverIdAndShiftId(caregiverId, shiftId).isEmpty()) {
            try {
                shiftOfferRepository.save(new ShiftOffer(shiftId, caregiverId, agencyId));
            } catch (DataIntegrityViolationException e) {
                log.debug("Concurrent duplicate insert for shiftId={} caregiverId={} — offer already exists, ignoring",
                        shiftId, caregiverId);
            }
        }
    }
}
