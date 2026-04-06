package com.hcare.api.v1.scheduling;

import com.hcare.api.v1.scheduling.dto.AssignCaregiverRequest;
import com.hcare.api.v1.scheduling.dto.CancelShiftRequest;
import com.hcare.api.v1.scheduling.dto.CreateShiftRequest;
import com.hcare.api.v1.scheduling.dto.RankedCaregiverResponse;
import com.hcare.api.v1.scheduling.dto.RespondToOfferRequest;
import com.hcare.api.v1.scheduling.dto.ShiftOfferSummary;
import com.hcare.api.v1.scheduling.dto.ShiftSummaryResponse;
import com.hcare.domain.Authorization;
import com.hcare.domain.AuthorizationRepository;
import com.hcare.domain.CaregiverRepository;
import com.hcare.domain.Shift;
import com.hcare.domain.ShiftCancelledEvent;
import com.hcare.domain.ShiftOffer;
import com.hcare.domain.ShiftOfferRepository;
import com.hcare.domain.ShiftOfferResponse;
import com.hcare.domain.ShiftRepository;
import com.hcare.domain.ShiftStatus;
import com.hcare.scoring.ScoringService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class ShiftSchedulingServiceTest {

    @Mock ShiftRepository shiftRepository;
    @Mock ShiftOfferRepository shiftOfferRepository;
    @Mock AuthorizationRepository authorizationRepository;
    @Mock CaregiverRepository caregiverRepository;
    @Mock ScoringService scoringService;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock ShiftOfferCreationService offerCreationService;

    ShiftSchedulingService service;

    UUID agencyId = UUID.randomUUID();
    UUID clientId = UUID.randomUUID();
    UUID serviceTypeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ShiftSchedulingService(shiftRepository, shiftOfferRepository,
            authorizationRepository, caregiverRepository, scoringService, eventPublisher,
            offerCreationService);
    }

    // --- listShifts ---

    @Test
    void listShifts_delegates_to_repository_and_maps_to_response() {
        LocalDateTime start = LocalDateTime.of(2026, 5, 1, 0, 0);
        LocalDateTime end   = LocalDateTime.of(2026, 5, 8, 0, 0);
        Shift shift = new Shift(agencyId, null, clientId, null, serviceTypeId, null,
            LocalDateTime.of(2026, 5, 3, 9, 0), LocalDateTime.of(2026, 5, 3, 13, 0));
        when(shiftRepository.findByAgencyIdAndScheduledStartBetween(agencyId, start, end, Pageable.unpaged()))
            .thenReturn(new PageImpl<>(List.of(shift)));

        Page<ShiftSummaryResponse> result = service.listShifts(agencyId, start, end, Pageable.unpaged());

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).clientId()).isEqualTo(clientId);
        assertThat(result.getContent().get(0).status()).isEqualTo(ShiftStatus.OPEN);
    }

    @Test
    void listShifts_rejects_inverted_date_range() {
        LocalDateTime start = LocalDateTime.of(2026, 5, 8, 0, 0);
        LocalDateTime end   = LocalDateTime.of(2026, 5, 1, 0, 0); // end before start

        assertThatThrownBy(() -> service.listShifts(agencyId, start, end, Pageable.unpaged()))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("400");
        verifyNoInteractions(shiftRepository);
    }

    // --- createShift ---

    @Test
    void createShift_saves_shift_and_returns_response() {
        CreateShiftRequest req = new CreateShiftRequest(clientId, null, serviceTypeId, null,
            LocalDateTime.of(2026, 5, 3, 9, 0), LocalDateTime.of(2026, 5, 3, 13, 0), null);
        Shift saved = new Shift(agencyId, null, clientId, null, serviceTypeId, null,
            req.scheduledStart(), req.scheduledEnd());
        when(shiftRepository.save(any())).thenReturn(saved);

        ShiftSummaryResponse result = service.createShift(agencyId, req);

        assertThat(result.clientId()).isEqualTo(clientId);
        assertThat(result.status()).isEqualTo(ShiftStatus.OPEN);
        verify(shiftRepository).save(any(Shift.class));
    }

    @Test
    void createShift_with_caregiverId_sets_status_to_ASSIGNED() {
        UUID caregiverId = UUID.randomUUID();
        CreateShiftRequest req = new CreateShiftRequest(clientId, caregiverId, serviceTypeId, null,
            LocalDateTime.of(2026, 5, 3, 9, 0), LocalDateTime.of(2026, 5, 3, 13, 0), null);
        Shift saved = new Shift(agencyId, null, clientId, caregiverId, serviceTypeId, null,
            req.scheduledStart(), req.scheduledEnd());
        when(shiftRepository.save(any())).thenReturn(saved);

        ShiftSummaryResponse result = service.createShift(agencyId, req);

        assertThat(result.status()).isEqualTo(ShiftStatus.ASSIGNED);
        verify(shiftRepository).save(argThat(s -> s.getStatus() == ShiftStatus.ASSIGNED));
    }

    @Test
    void createShift_with_authorization_from_different_client_throws_422() {
        UUID authorizationId = UUID.randomUUID();
        UUID differentClientId = UUID.randomUUID();
        Authorization auth = mock(Authorization.class);
        when(auth.getClientId()).thenReturn(differentClientId);
        when(authorizationRepository.findById(authorizationId)).thenReturn(Optional.of(auth));

        CreateShiftRequest req = new CreateShiftRequest(clientId, null, serviceTypeId, authorizationId,
            LocalDateTime.of(2026, 5, 3, 9, 0), LocalDateTime.of(2026, 5, 3, 13, 0), null);

        assertThatThrownBy(() -> service.createShift(agencyId, req))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("422");
        verifyNoInteractions(shiftRepository);
    }

    // --- assignCaregiver ---

    @Test
    void assignCaregiver_transitions_open_shift_to_assigned() {
        UUID shiftId = UUID.randomUUID();
        UUID caregiverId = UUID.randomUUID();
        Shift shift = new Shift(agencyId, null, clientId, null, serviceTypeId, null,
            LocalDateTime.of(2026, 5, 3, 9, 0), LocalDateTime.of(2026, 5, 3, 13, 0));
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(shift));
        when(caregiverRepository.existsByIdAndAgencyId(caregiverId, agencyId)).thenReturn(true);
        when(shiftRepository.save(shift)).thenReturn(shift);

        ShiftSummaryResponse result = service.assignCaregiver(agencyId, shiftId, new AssignCaregiverRequest(caregiverId));

        assertThat(result.status()).isEqualTo(ShiftStatus.ASSIGNED);
        assertThat(result.caregiverId()).isEqualTo(caregiverId);
    }

    @Test
    void assignCaregiver_on_assigned_shift_throws_409() {
        UUID shiftId = UUID.randomUUID();
        Shift shift = new Shift(agencyId, null, clientId, UUID.randomUUID(), serviceTypeId, null,
            LocalDateTime.of(2026, 5, 3, 9, 0), LocalDateTime.of(2026, 5, 3, 13, 0));
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(shift));

        assertThatThrownBy(() -> service.assignCaregiver(agencyId, shiftId, new AssignCaregiverRequest(UUID.randomUUID())))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("409");
    }

    @Test
    void assignCaregiver_on_missing_shift_throws_404() {
        UUID shiftId = UUID.randomUUID();
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assignCaregiver(agencyId, shiftId, new AssignCaregiverRequest(UUID.randomUUID())))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("404");
    }

    @Test
    void assignCaregiver_on_shift_belonging_to_other_agency_throws_404() {
        UUID shiftId = UUID.randomUUID();
        UUID otherAgencyId = UUID.randomUUID();
        Shift shift = new Shift(otherAgencyId, null, clientId, null, serviceTypeId, null,
            LocalDateTime.of(2026, 5, 3, 9, 0), LocalDateTime.of(2026, 5, 3, 13, 0));
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(shift));

        assertThatThrownBy(() -> service.assignCaregiver(agencyId, shiftId, new AssignCaregiverRequest(UUID.randomUUID())))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("404");
        verify(shiftRepository, never()).save(any());
    }

    @Test
    void assignCaregiver_with_caregiver_from_another_agency_throws_422() {
        UUID shiftId = UUID.randomUUID();
        UUID foreignCaregiverId = UUID.randomUUID();
        Shift shift = new Shift(agencyId, null, clientId, null, serviceTypeId, null,
            LocalDateTime.of(2026, 5, 3, 9, 0), LocalDateTime.of(2026, 5, 3, 13, 0));
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(shift));
        when(caregiverRepository.existsByIdAndAgencyId(foreignCaregiverId, agencyId)).thenReturn(false);

        assertThatThrownBy(() -> service.assignCaregiver(agencyId, shiftId, new AssignCaregiverRequest(foreignCaregiverId)))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("422");
        verify(shiftRepository, never()).save(any());
    }

    // --- unassignCaregiver ---

    @Test
    void unassignCaregiver_transitions_assigned_shift_to_open() {
        UUID shiftId = UUID.randomUUID();
        UUID caregiverId = UUID.randomUUID();
        Shift shift = new Shift(agencyId, null, clientId, caregiverId, serviceTypeId, null,
            LocalDateTime.of(2026, 5, 3, 9, 0), LocalDateTime.of(2026, 5, 3, 13, 0));
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(shift));
        when(shiftRepository.save(shift)).thenReturn(shift);

        ShiftSummaryResponse result = service.unassignCaregiver(agencyId, shiftId);

        assertThat(result.status()).isEqualTo(ShiftStatus.OPEN);
        assertThat(result.caregiverId()).isNull();
    }

    @Test
    void unassignCaregiver_on_open_shift_throws_409() {
        UUID shiftId = UUID.randomUUID();
        Shift shift = new Shift(agencyId, null, clientId, null, serviceTypeId, null,
            LocalDateTime.of(2026, 5, 3, 9, 0), LocalDateTime.of(2026, 5, 3, 13, 0));
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(shift));

        assertThatThrownBy(() -> service.unassignCaregiver(agencyId, shiftId))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("409");
    }

    // --- cancelShift ---

    @Test
    void cancelShift_transitions_open_shift_to_cancelled_without_publishing_event() {
        UUID shiftId = UUID.randomUUID();
        Shift shift = new Shift(agencyId, null, clientId, null, serviceTypeId, null,
            LocalDateTime.of(2026, 5, 3, 9, 0), LocalDateTime.of(2026, 5, 3, 13, 0));
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(shift));
        when(shiftRepository.save(shift)).thenReturn(shift);

        ShiftSummaryResponse result = service.cancelShift(agencyId, shiftId, new CancelShiftRequest(null));

        assertThat(result.status()).isEqualTo(ShiftStatus.CANCELLED);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void cancelShift_on_assigned_shift_publishes_ShiftCancelledEvent() {
        UUID shiftId = UUID.randomUUID();
        UUID caregiverId = UUID.randomUUID();
        Shift shift = new Shift(agencyId, null, clientId, caregiverId, serviceTypeId, null,
            LocalDateTime.of(2026, 5, 3, 9, 0), LocalDateTime.of(2026, 5, 3, 13, 0));
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(shift));
        when(shiftRepository.save(shift)).thenReturn(shift);

        service.cancelShift(agencyId, shiftId, new CancelShiftRequest("Client no-show"));

        ArgumentCaptor<ShiftCancelledEvent> captor = ArgumentCaptor.forClass(ShiftCancelledEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().caregiverId()).isEqualTo(caregiverId);
        assertThat(captor.getValue().agencyId()).isEqualTo(agencyId);
    }

    @Test
    void cancelShift_on_in_progress_shift_throws_409() {
        UUID shiftId = UUID.randomUUID();
        Shift shift = new Shift(agencyId, null, clientId, UUID.randomUUID(), serviceTypeId, null,
            LocalDateTime.of(2026, 5, 3, 9, 0), LocalDateTime.of(2026, 5, 3, 13, 0));
        shift.setStatus(ShiftStatus.IN_PROGRESS);
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(shift));

        assertThatThrownBy(() -> service.cancelShift(agencyId, shiftId, new CancelShiftRequest(null)))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("409");
    }

    @Test
    void cancelShift_on_completed_shift_throws_409() {
        UUID shiftId = UUID.randomUUID();
        Shift shift = new Shift(agencyId, null, clientId, UUID.randomUUID(), serviceTypeId, null,
            LocalDateTime.of(2026, 5, 3, 9, 0), LocalDateTime.of(2026, 5, 3, 13, 0));
        shift.setStatus(ShiftStatus.COMPLETED);
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(shift));

        assertThatThrownBy(() -> service.cancelShift(agencyId, shiftId, new CancelShiftRequest(null)))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("409");
    }

    // --- getCandidates ---

    @Test
    void getCandidates_delegates_to_scoring_service_and_maps_results() {
        UUID shiftId = UUID.randomUUID();
        UUID caregiverId = UUID.randomUUID();
        Shift shift = new Shift(agencyId, null, clientId, null, serviceTypeId, null,
            LocalDateTime.of(2026, 5, 3, 9, 0), LocalDateTime.of(2026, 5, 3, 13, 0));
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(shift));
        when(scoringService.rankCandidates(any())).thenReturn(
            List.of(new com.hcare.scoring.RankedCaregiver(caregiverId, 0.85, "Good match")));

        List<RankedCaregiverResponse> result = service.getCandidates(agencyId, shiftId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).caregiverId()).isEqualTo(caregiverId);
        assertThat(result.get(0).score()).isEqualTo(0.85);
        verify(scoringService).rankCandidates(any());
    }

    // --- broadcastShift ---

    @Test
    void broadcastShift_on_non_open_shift_throws_409() {
        UUID shiftId = UUID.randomUUID();
        Shift shift = new Shift(agencyId, null, clientId, UUID.randomUUID(), serviceTypeId, null,
            LocalDateTime.of(2026, 5, 3, 9, 0), LocalDateTime.of(2026, 5, 3, 13, 0));
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(shift));

        assertThatThrownBy(() -> service.broadcastShift(agencyId, shiftId))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("409");
        verifyNoInteractions(shiftOfferRepository);
    }

    @Test
    void broadcastShift_creates_offers_for_all_eligible_candidates_and_returns_summaries() {
        UUID shiftId = UUID.randomUUID();
        UUID cg1 = UUID.randomUUID();
        UUID cg2 = UUID.randomUUID();
        Shift shift = new Shift(agencyId, null, clientId, null, serviceTypeId, null,
            LocalDateTime.of(2026, 5, 3, 9, 0), LocalDateTime.of(2026, 5, 3, 13, 0));
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(shift));
        when(scoringService.rankCandidates(any())).thenReturn(List.of(
            new com.hcare.scoring.RankedCaregiver(cg1, 0.9, null),
            new com.hcare.scoring.RankedCaregiver(cg2, 0.7, null)));
        when(shiftOfferRepository.findByShiftId(shiftId)).thenReturn(List.of(
            new ShiftOffer(shiftId, cg1, agencyId),
            new ShiftOffer(shiftId, cg2, agencyId)));

        List<ShiftOfferSummary> result = service.broadcastShift(agencyId, shiftId);

        assertThat(result).hasSize(2);
        verify(offerCreationService).createOfferIfAbsent(shiftId, cg1, agencyId);
        verify(offerCreationService).createOfferIfAbsent(shiftId, cg2, agencyId);
        verify(shiftOfferRepository).findByShiftId(shiftId);
    }

    // --- listOffers ---

    @Test
    void listOffers_on_missing_shift_throws_404() {
        UUID shiftId = UUID.randomUUID();
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listOffers(agencyId, shiftId))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("404");
        verifyNoInteractions(shiftOfferRepository);
    }

    @Test
    void listOffers_returns_offer_summaries_for_shift() {
        UUID shiftId = UUID.randomUUID();
        UUID caregiverId = UUID.randomUUID();
        Shift shift = new Shift(agencyId, null, clientId, null, serviceTypeId, null,
            LocalDateTime.of(2026, 5, 3, 9, 0), LocalDateTime.of(2026, 5, 3, 13, 0));
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(shift));
        when(shiftOfferRepository.findByShiftId(shiftId)).thenReturn(
            List.of(new ShiftOffer(shiftId, caregiverId, agencyId)));

        List<ShiftOfferSummary> result = service.listOffers(agencyId, shiftId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).caregiverId()).isEqualTo(caregiverId);
        assertThat(result.get(0).response()).isEqualTo(ShiftOfferResponse.NO_RESPONSE);
    }

    // --- respondToOffer ---

    @Test
    void respondToOffer_with_NO_RESPONSE_throws_400() {
        UUID shiftId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();

        assertThatThrownBy(() -> service.respondToOffer(agencyId, shiftId, offerId,
                new RespondToOfferRequest(ShiftOfferResponse.NO_RESPONSE)))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("400");
        verifyNoInteractions(shiftRepository, shiftOfferRepository);
    }

    @Test
    void respondToOffer_on_already_responded_offer_throws_409() {
        UUID shiftId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();
        UUID caregiverId = UUID.randomUUID();
        ShiftOffer offer = new ShiftOffer(shiftId, caregiverId, agencyId);
        offer.respond(ShiftOfferResponse.DECLINED);
        when(shiftOfferRepository.findById(offerId)).thenReturn(Optional.of(offer));

        assertThatThrownBy(() -> service.respondToOffer(agencyId, shiftId, offerId,
                new RespondToOfferRequest(ShiftOfferResponse.ACCEPTED)))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("409");
    }

    @Test
    void respondToOffer_accepted_assigns_caregiver_and_declines_other_pending_offers() {
        UUID shiftId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();
        UUID otherOfferId = UUID.randomUUID();
        UUID caregiverId = UUID.randomUUID();
        UUID otherCaregiverId = UUID.randomUUID();
        Shift shift = new Shift(agencyId, null, clientId, null, serviceTypeId, null,
            LocalDateTime.of(2026, 5, 3, 9, 0), LocalDateTime.of(2026, 5, 3, 13, 0));
        ShiftOffer offer = new ShiftOffer(shiftId, caregiverId, agencyId);
        ShiftOffer otherOffer = new ShiftOffer(shiftId, otherCaregiverId, agencyId);

        when(shiftOfferRepository.findById(offerId)).thenReturn(Optional.of(offer));
        when(shiftRepository.findByIdForUpdate(shiftId)).thenReturn(Optional.of(shift));
        when(shiftRepository.save(shift)).thenReturn(shift);
        when(shiftOfferRepository.findByShiftId(shiftId)).thenReturn(List.of(offer, otherOffer));
        when(shiftOfferRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.respondToOffer(agencyId, shiftId, offerId,
            new RespondToOfferRequest(ShiftOfferResponse.ACCEPTED));

        assertThat(shift.getStatus()).isEqualTo(ShiftStatus.ASSIGNED);
        assertThat(shift.getCaregiverId()).isEqualTo(caregiverId);
        assertThat(otherOffer.getResponse()).isEqualTo(ShiftOfferResponse.DECLINED);
    }

    @Test
    void respondToOffer_accepted_on_non_open_shift_throws_409() {
        UUID shiftId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();
        UUID caregiverId = UUID.randomUUID();
        Shift shift = new Shift(agencyId, null, clientId, UUID.randomUUID(), serviceTypeId, null,
            LocalDateTime.of(2026, 5, 3, 9, 0), LocalDateTime.of(2026, 5, 3, 13, 0));
        ShiftOffer offer = new ShiftOffer(shiftId, caregiverId, agencyId);

        when(shiftOfferRepository.findById(offerId)).thenReturn(Optional.of(offer));
        when(shiftRepository.findByIdForUpdate(shiftId)).thenReturn(Optional.of(shift));

        assertThatThrownBy(() -> service.respondToOffer(agencyId, shiftId, offerId,
                new RespondToOfferRequest(ShiftOfferResponse.ACCEPTED)))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("409");
        verify(shiftRepository, never()).save(any());
        verify(shiftOfferRepository, never()).save(any());
    }

    @Test
    void respondToOffer_declined_does_not_mutate_shift() {
        UUID shiftId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();
        UUID caregiverId = UUID.randomUUID();
        Shift shift = new Shift(agencyId, null, clientId, null, serviceTypeId, null,
            LocalDateTime.of(2026, 5, 3, 9, 0), LocalDateTime.of(2026, 5, 3, 13, 0));
        ShiftOffer offer = new ShiftOffer(shiftId, caregiverId, agencyId);

        when(shiftOfferRepository.findById(offerId)).thenReturn(Optional.of(offer));
        when(shiftOfferRepository.save(any())).thenReturn(offer);

        service.respondToOffer(agencyId, shiftId, offerId,
            new RespondToOfferRequest(ShiftOfferResponse.DECLINED));

        assertThat(offer.getResponse()).isEqualTo(ShiftOfferResponse.DECLINED);
        assertThat(shift.getStatus()).isEqualTo(ShiftStatus.OPEN);
        verify(shiftRepository, never()).findByIdForUpdate(any());
        verify(shiftRepository, never()).save(any());
    }

    @Test
    void respondToOffer_on_offer_belonging_to_other_agency_throws_404() {
        UUID shiftId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();
        UUID caregiverId = UUID.randomUUID();
        UUID otherAgencyId = UUID.randomUUID();
        ShiftOffer offer = new ShiftOffer(shiftId, caregiverId, otherAgencyId);

        when(shiftOfferRepository.findById(offerId)).thenReturn(Optional.of(offer));

        assertThatThrownBy(() -> service.respondToOffer(agencyId, shiftId, offerId,
                new RespondToOfferRequest(ShiftOfferResponse.ACCEPTED)))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("404");
        verify(shiftRepository, never()).findByIdForUpdate(any());
        verify(shiftRepository, never()).save(any());
    }
}
