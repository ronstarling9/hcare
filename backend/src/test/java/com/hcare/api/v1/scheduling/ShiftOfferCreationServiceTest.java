package com.hcare.api.v1.scheduling;

import com.hcare.domain.ShiftOffer;
import com.hcare.domain.ShiftOfferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShiftOfferCreationServiceTest {

    @Mock ShiftOfferRepository shiftOfferRepository;
    ShiftOfferCreationService service;

    @BeforeEach
    void setUp() {
        service = new ShiftOfferCreationService(shiftOfferRepository);
    }

    @Test
    void createOfferIfAbsent_saves_when_no_existing_offer() {
        UUID shiftId = UUID.randomUUID();
        UUID caregiverId = UUID.randomUUID();
        UUID agencyId = UUID.randomUUID();
        when(shiftOfferRepository.findByCaregiverIdAndShiftId(caregiverId, shiftId))
            .thenReturn(Optional.empty());

        service.createOfferIfAbsent(shiftId, caregiverId, agencyId);

        verify(shiftOfferRepository).saveAndFlush(any(ShiftOffer.class));
    }

    @Test
    void createOfferIfAbsent_skips_save_when_offer_already_exists() {
        UUID shiftId = UUID.randomUUID();
        UUID caregiverId = UUID.randomUUID();
        UUID agencyId = UUID.randomUUID();
        when(shiftOfferRepository.findByCaregiverIdAndShiftId(caregiverId, shiftId))
            .thenReturn(Optional.of(new ShiftOffer(shiftId, caregiverId, agencyId)));

        service.createOfferIfAbsent(shiftId, caregiverId, agencyId);

        verify(shiftOfferRepository, never()).save(any());
    }

    @Test
    void createOfferIfAbsent_swallows_DataIntegrityViolationException_on_concurrent_insert() {
        UUID shiftId = UUID.randomUUID();
        UUID caregiverId = UUID.randomUUID();
        UUID agencyId = UUID.randomUUID();
        when(shiftOfferRepository.findByCaregiverIdAndShiftId(caregiverId, shiftId))
            .thenReturn(Optional.empty());
        when(shiftOfferRepository.saveAndFlush(any(ShiftOffer.class)))
            .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertDoesNotThrow(() -> service.createOfferIfAbsent(shiftId, caregiverId, agencyId));
    }
}
