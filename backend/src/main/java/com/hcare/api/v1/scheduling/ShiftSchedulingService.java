package com.hcare.api.v1.scheduling;

import com.hcare.api.v1.scheduling.dto.AssignCaregiverRequest;
import com.hcare.api.v1.scheduling.dto.CancelShiftRequest;
import com.hcare.api.v1.scheduling.dto.CreateShiftRequest;
import com.hcare.api.v1.scheduling.dto.RankedCaregiverResponse;
import com.hcare.api.v1.scheduling.dto.ShiftOfferSummary;
import com.hcare.api.v1.scheduling.dto.RespondToOfferRequest;
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
import com.hcare.scoring.RankedCaregiver;
import com.hcare.scoring.ScoringService;
import com.hcare.scoring.ShiftMatchRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ShiftSchedulingService {

    private static final Logger log = LoggerFactory.getLogger(ShiftSchedulingService.class);

    private final ShiftRepository shiftRepository;
    private final ShiftOfferRepository shiftOfferRepository;
    private final AuthorizationRepository authorizationRepository;
    private final CaregiverRepository caregiverRepository;
    private final ScoringService scoringService;
    private final ApplicationEventPublisher eventPublisher;
    private final ShiftOfferCreationService offerCreationService;

    public ShiftSchedulingService(ShiftRepository shiftRepository,
                                   ShiftOfferRepository shiftOfferRepository,
                                   AuthorizationRepository authorizationRepository,
                                   CaregiverRepository caregiverRepository,
                                   ScoringService scoringService,
                                   ApplicationEventPublisher eventPublisher,
                                   ShiftOfferCreationService offerCreationService) {
        this.shiftRepository = shiftRepository;
        this.shiftOfferRepository = shiftOfferRepository;
        this.authorizationRepository = authorizationRepository;
        this.caregiverRepository = caregiverRepository;
        this.scoringService = scoringService;
        this.eventPublisher = eventPublisher;
        this.offerCreationService = offerCreationService;
    }

    @Transactional(readOnly = true)
    public Page<ShiftSummaryResponse> listShifts(UUID agencyId, LocalDateTime start, LocalDateTime end,
                                                  ShiftStatus status, Pageable pageable) {
        if (!end.isAfter(start)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "end must be after start");
        }
        if (status != null) {
            return shiftRepository.findByAgencyIdAndStatusAndScheduledStartBetween(agencyId, status, start, end, pageable)
                .map(this::toSummary);
        }
        return shiftRepository.findByAgencyIdAndScheduledStartBetween(agencyId, start, end, pageable)
            .map(this::toSummary);
    }

    @Transactional
    public ShiftSummaryResponse createShift(UUID agencyId, CreateShiftRequest req) {
        if (!req.scheduledEnd().isAfter(req.scheduledStart())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "scheduledEnd must be after scheduledStart");
        }
        if (req.authorizationId() != null) {
            Authorization auth = authorizationRepository.findById(req.authorizationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Authorization not found"));
            if (!auth.getClientId().equals(req.clientId())) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Authorization does not belong to the specified client");
            }
        }
        Shift shift = new Shift(agencyId, null, req.clientId(), req.caregiverId(),
            req.serviceTypeId(), req.authorizationId(),
            req.scheduledStart(), req.scheduledEnd());
        if (req.notes() != null) {
            shift.setNotes(req.notes());
        }
        return toSummary(shiftRepository.save(shift));
    }

    @Transactional
    public ShiftSummaryResponse assignCaregiver(UUID agencyId, UUID shiftId, AssignCaregiverRequest req) {
        Shift shift = requireShift(agencyId, shiftId);
        if (shift.getStatus() != ShiftStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Shift must be OPEN to assign a caregiver (current status: " + shift.getStatus() + ")");
        }
        if (!caregiverRepository.existsByIdAndAgencyId(req.caregiverId(), shift.getAgencyId())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Caregiver does not belong to this agency");
        }
        shift.setCaregiverId(req.caregiverId());
        shift.setStatus(ShiftStatus.ASSIGNED);
        return toSummary(shiftRepository.save(shift));
    }

    @Transactional
    public ShiftSummaryResponse unassignCaregiver(UUID agencyId, UUID shiftId) {
        Shift shift = requireShift(agencyId, shiftId);
        if (shift.getStatus() != ShiftStatus.ASSIGNED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Shift must be ASSIGNED to unassign caregiver (current status: " + shift.getStatus() + ")");
        }
        shift.setCaregiverId(null);
        shift.setStatus(ShiftStatus.OPEN);
        return toSummary(shiftRepository.save(shift));
    }

    @Transactional
    public ShiftSummaryResponse cancelShift(UUID agencyId, UUID shiftId, CancelShiftRequest req) {
        Shift shift = requireShift(agencyId, shiftId);
        if (shift.getStatus() != ShiftStatus.OPEN && shift.getStatus() != ShiftStatus.ASSIGNED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Only OPEN or ASSIGNED shifts can be cancelled (current status: " + shift.getStatus() + ")");
        }
        UUID caregiverId = shift.getCaregiverId();
        if (req.notes() != null) {
            shift.setNotes(req.notes());
        }
        shift.setStatus(ShiftStatus.CANCELLED);
        ShiftSummaryResponse response = toSummary(shiftRepository.save(shift));
        if (caregiverId != null) {
            eventPublisher.publishEvent(new ShiftCancelledEvent(shiftId, caregiverId, shift.getAgencyId()));
        }
        return response;
    }

    @Transactional(readOnly = true)
    public List<RankedCaregiverResponse> getCandidates(UUID agencyId, UUID shiftId) {
        Shift shift = requireShift(agencyId, shiftId);
        List<RankedCaregiver> ranked = scoringService.rankCandidates(new ShiftMatchRequest(
            shift.getAgencyId(), shift.getClientId(), shift.getServiceTypeId(),
            shift.getAuthorizationId(), shift.getScheduledStart(), shift.getScheduledEnd()));
        return ranked.stream()
            .map(rc -> new RankedCaregiverResponse(rc.caregiverId(), rc.score(), rc.explanation()))
            .toList();
    }

    @Transactional
    public List<ShiftOfferSummary> broadcastShift(UUID agencyId, UUID shiftId) {
        Shift shift = requireShift(agencyId, shiftId);
        if (shift.getStatus() != ShiftStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Only OPEN shifts can be broadcast (current status: " + shift.getStatus() + ")");
        }
        List<RankedCaregiver> eligible = scoringService.rankCandidates(new ShiftMatchRequest(
            shift.getAgencyId(), shift.getClientId(), shift.getServiceTypeId(),
            shift.getAuthorizationId(), shift.getScheduledStart(), shift.getScheduledEnd()));
        for (RankedCaregiver rc : eligible) {
            offerCreationService.createOfferIfAbsent(shiftId, rc.caregiverId(), shift.getAgencyId());
        }
        return shiftOfferRepository.findByShiftId(shiftId).stream()
            .map(this::toOfferSummary)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<ShiftOfferSummary> listOffers(UUID agencyId, UUID shiftId) {
        requireShift(agencyId, shiftId);
        return shiftOfferRepository.findByShiftId(shiftId).stream()
            .map(this::toOfferSummary)
            .toList();
    }

    @Transactional
    public ShiftOfferSummary respondToOffer(UUID agencyId, UUID shiftId, UUID offerId, RespondToOfferRequest req) {
        if (req.response() == ShiftOfferResponse.NO_RESPONSE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Response must be ACCEPTED or DECLINED");
        }
        ShiftOffer offer = shiftOfferRepository.findById(offerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Offer not found"));
        if (!offer.getShiftId().equals(shiftId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Offer does not belong to this shift");
        }
        if (!offer.getAgencyId().equals(agencyId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Offer not found");
        }
        if (offer.getResponse() != ShiftOfferResponse.NO_RESPONSE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Offer already has a response: " + offer.getResponse());
        }

        if (req.response() == ShiftOfferResponse.ACCEPTED) {
            Shift shift = shiftRepository.findByIdForUpdate(shiftId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shift not found"));
            if (!shift.getAgencyId().equals(agencyId)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Shift not found");
            }
            if (shift.getStatus() != ShiftStatus.OPEN) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot accept offer: shift is no longer OPEN (status: " + shift.getStatus() + ")");
            }
            shift.setCaregiverId(offer.getCaregiverId());
            shift.setStatus(ShiftStatus.ASSIGNED);
            offer.respond(ShiftOfferResponse.ACCEPTED);
            shiftRepository.save(shift);
            shiftOfferRepository.save(offer);

            shiftOfferRepository.findByShiftId(shiftId).stream()
                .filter(o -> !offerId.equals(o.getId()) && o.getResponse() == ShiftOfferResponse.NO_RESPONSE)
                .forEach(o -> {
                    o.respond(ShiftOfferResponse.DECLINED);
                    shiftOfferRepository.save(o);
                });
        } else {
            offer.respond(ShiftOfferResponse.DECLINED);
            shiftOfferRepository.save(offer);
        }

        return toOfferSummary(offer);
    }

    // --- helpers ---

    private Shift requireShift(UUID agencyId, UUID shiftId) {
        Shift shift = shiftRepository.findById(shiftId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shift not found"));
        if (!shift.getAgencyId().equals(agencyId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Shift not found");
        }
        return shift;
    }

    private ShiftSummaryResponse toSummary(Shift shift) {
        return new ShiftSummaryResponse(
            shift.getId(), shift.getAgencyId(), shift.getClientId(), shift.getCaregiverId(),
            shift.getServiceTypeId(), shift.getAuthorizationId(), shift.getSourcePatternId(),
            shift.getScheduledStart(), shift.getScheduledEnd(), shift.getStatus(), shift.getNotes());
    }

    private ShiftOfferSummary toOfferSummary(ShiftOffer offer) {
        return new ShiftOfferSummary(
            offer.getId(), offer.getShiftId(), offer.getCaregiverId(), offer.getAgencyId(),
            offer.getOfferedAt(), offer.getRespondedAt(), offer.getResponse());
    }
}
