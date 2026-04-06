package com.hcare.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShiftOfferRepository extends JpaRepository<ShiftOffer, UUID> {
    List<ShiftOffer> findByShiftId(UUID shiftId);

    Optional<ShiftOffer> findByCaregiverIdAndShiftId(UUID caregiverId, UUID shiftId);
}
