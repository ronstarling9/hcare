package com.hcare.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ShiftOfferRepository extends JpaRepository<ShiftOffer, UUID> {
    List<ShiftOffer> findByShiftId(UUID shiftId);
}
