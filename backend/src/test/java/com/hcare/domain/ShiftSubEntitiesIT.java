package com.hcare.domain;

import com.hcare.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class ShiftSubEntitiesIT extends AbstractIntegrationTest {

    @Autowired AgencyRepository agencyRepo;
    @Autowired ClientRepository clientRepo;
    @Autowired CaregiverRepository caregiverRepo;
    @Autowired ServiceTypeRepository serviceTypeRepo;
    @Autowired CarePlanRepository carePlanRepo;
    @Autowired AdlTaskRepository adlTaskRepo;
    @Autowired ShiftRepository shiftRepo;
    @Autowired ShiftOfferRepository shiftOfferRepo;
    @Autowired AdlTaskCompletionRepository adlTaskCompletionRepo;

    // Mutable fields set per test via setupFixture — JUnit 5 creates a new instance per test
    private Agency agency;
    private Client client;
    private ServiceType st;
    private Shift shift;
    private Caregiver caregiver;

    private void setupFixture(String suffix) {
        agency = agencyRepo.save(new Agency("Sub Entity Agency " + suffix, "TX"));
        client = clientRepo.save(new Client(agency.getId(), "Sub", "Client", LocalDate.of(1960, 1, 1)));
        st = serviceTypeRepo.save(new ServiceType(agency.getId(), "PCS", "PCS-SUB-" + suffix, true, "[]"));
        caregiver = caregiverRepo.save(new Caregiver(agency.getId(), "Sub", "Cg", "subcg." + suffix + "@test.com"));
        LocalDateTime start = LocalDate.of(2026, 4, 22).atTime(9, 0);
        shift = shiftRepo.save(new Shift(agency.getId(), null, client.getId(), null,
            st.getId(), null, start, start.plusHours(4)));
    }

    @Test
    void shiftOffer_defaults_to_no_response() {
        setupFixture("OF1");

        ShiftOffer offer = shiftOfferRepo.save(new ShiftOffer(shift.getId(), caregiver.getId(), agency.getId()));

        ShiftOffer loaded = shiftOfferRepo.findById(offer.getId()).orElseThrow();
        assertThat(loaded.getShiftId()).isEqualTo(shift.getId());
        assertThat(loaded.getCaregiverId()).isEqualTo(caregiver.getId());
        assertThat(loaded.getResponse()).isEqualTo(ShiftOfferResponse.NO_RESPONSE);
        assertThat(loaded.getOfferedAt()).isNotNull();
        assertThat(loaded.getRespondedAt()).isNull();
    }

    @Test
    void shiftOffer_respond_sets_response_and_respondedAt() {
        setupFixture("OF2");

        ShiftOffer offer = new ShiftOffer(shift.getId(), caregiver.getId(), agency.getId());
        offer.respond(ShiftOfferResponse.ACCEPTED);
        shiftOfferRepo.save(offer);

        ShiftOffer loaded = shiftOfferRepo.findById(offer.getId()).orElseThrow();
        assertThat(loaded.getResponse()).isEqualTo(ShiftOfferResponse.ACCEPTED);
        assertThat(loaded.getRespondedAt()).isNotNull();
    }

    @Test
    void shiftOffer_unique_constraint_prevents_duplicate_offer() {
        setupFixture("OF3");

        shiftOfferRepo.save(new ShiftOffer(shift.getId(), caregiver.getId(), agency.getId()));

        assertThatThrownBy(() ->
            shiftOfferRepo.saveAndFlush(new ShiftOffer(shift.getId(), caregiver.getId(), agency.getId()))
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByShiftId_returns_all_offers_for_shift() {
        setupFixture("OF4");
        Caregiver cg2 = caregiverRepo.save(new Caregiver(agency.getId(), "Sub2", "Cg2", "subcg2.OF4@test.com"));

        shiftOfferRepo.save(new ShiftOffer(shift.getId(), caregiver.getId(), agency.getId()));
        shiftOfferRepo.save(new ShiftOffer(shift.getId(), cg2.getId(), agency.getId()));

        List<ShiftOffer> offers = shiftOfferRepo.findByShiftId(shift.getId());
        assertThat(offers).hasSize(2);
        assertThat(offers).allMatch(o -> o.getShiftId().equals(shift.getId()));
    }

    @Test
    void adlTaskCompletion_can_be_saved() {
        setupFixture("AT1");

        CarePlan plan = carePlanRepo.save(new CarePlan(client.getId(), agency.getId(), 1));
        AdlTask task = adlTaskRepo.save(new AdlTask(plan.getId(), agency.getId(), "Bathing", AssistanceLevel.MODERATE_ASSIST));

        AdlTaskCompletion completion = new AdlTaskCompletion(shift.getId(), task.getId(), agency.getId());
        completion.setCaregiverNotes("Completed with moderate assistance");
        adlTaskCompletionRepo.save(completion);

        AdlTaskCompletion loaded = adlTaskCompletionRepo.findById(completion.getId()).orElseThrow();
        assertThat(loaded.getShiftId()).isEqualTo(shift.getId());
        assertThat(loaded.getAdlTaskId()).isEqualTo(task.getId());
        assertThat(loaded.getCaregiverNotes()).isEqualTo("Completed with moderate assistance");
        assertThat(loaded.getCompletedAt()).isNotNull();
    }

    @Test
    void findByCaregiverIdAndShiftId_returns_offer_when_present_and_empty_when_not() {
        Agency agency = agencyRepo.save(new Agency("Offer Test Agency", "TX"));
        Client client = clientRepo.save(new Client(agency.getId(), "Bob", "Test", LocalDate.of(1975, 3, 15)));
        ServiceType st = serviceTypeRepo.save(new ServiceType(agency.getId(), "PCS", "PCS-OT", true, "[]"));
        Shift shift = shiftRepo.save(new Shift(agency.getId(), null, client.getId(), null,
            st.getId(), null,
            LocalDateTime.of(2026, 6, 1, 9, 0), LocalDateTime.of(2026, 6, 1, 13, 0)));

        UUID caregiverId = UUID.randomUUID();
        ShiftOffer offer = shiftOfferRepo.save(new ShiftOffer(shift.getId(), caregiverId, agency.getId()));

        Optional<ShiftOffer> found = shiftOfferRepo.findByCaregiverIdAndShiftId(caregiverId, shift.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(offer.getId());

        Optional<ShiftOffer> notFound = shiftOfferRepo.findByCaregiverIdAndShiftId(UUID.randomUUID(), shift.getId());
        assertThat(notFound).isEmpty();
    }

    @Test
    void adlTaskCompletion_unique_constraint_prevents_duplicate() {
        setupFixture("AT2");

        CarePlan plan = carePlanRepo.save(new CarePlan(client.getId(), agency.getId(), 1));
        AdlTask task = adlTaskRepo.save(new AdlTask(plan.getId(), agency.getId(), "Dressing", AssistanceLevel.MINIMAL_ASSIST));

        adlTaskCompletionRepo.save(new AdlTaskCompletion(shift.getId(), task.getId(), agency.getId()));

        assertThatThrownBy(() ->
            adlTaskCompletionRepo.saveAndFlush(new AdlTaskCompletion(shift.getId(), task.getId(), agency.getId()))
        ).isInstanceOf(DataIntegrityViolationException.class);
    }
}
