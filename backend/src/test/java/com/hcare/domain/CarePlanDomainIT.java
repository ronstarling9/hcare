package com.hcare.domain;

import com.hcare.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class CarePlanDomainIT extends AbstractIntegrationTest {

    @Autowired private AgencyRepository agencyRepo;
    @Autowired private ClientRepository clientRepo;
    @Autowired private CarePlanRepository carePlanRepo;
    @Autowired private AdlTaskRepository adlTaskRepo;
    @Autowired private GoalRepository goalRepo;

    private Agency agency;
    private Client client;

    @BeforeEach
    void setup() {
        agency = agencyRepo.save(new Agency("Care Plan Agency", "TX"));
        client = clientRepo.save(
            new Client(agency.getId(), "Helen", "Brown", LocalDate.of(1935, 11, 12)));
    }

    @Test
    void care_plan_defaults_to_draft_with_version_1() {
        CarePlan plan = carePlanRepo.save(new CarePlan(client.getId(), agency.getId(), 1));

        CarePlan loaded = carePlanRepo.findById(plan.getId()).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(CarePlanStatus.DRAFT);
        assertThat(loaded.getPlanVersion()).isEqualTo(1);
        assertThat(loaded.getActivatedAt()).isNull();
        assertThat(loaded.getClientId()).isEqualTo(client.getId());
    }

    @Test
    void activate_transitions_plan_to_active_status() {
        CarePlan plan = carePlanRepo.save(new CarePlan(client.getId(), agency.getId(), 1));
        plan.activate();
        carePlanRepo.save(plan);

        CarePlan loaded = carePlanRepo.findById(plan.getId()).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(CarePlanStatus.ACTIVE);
        assertThat(loaded.getActivatedAt()).isNotNull();
    }

    @Test
    void supersede_transitions_plan_to_superseded_and_new_version_can_be_active() {
        CarePlan v1 = carePlanRepo.save(new CarePlan(client.getId(), agency.getId(), 1));
        v1.activate();
        carePlanRepo.save(v1);

        v1.supersede();
        carePlanRepo.save(v1);

        CarePlan v2 = carePlanRepo.save(new CarePlan(client.getId(), agency.getId(), 2));
        v2.activate();
        carePlanRepo.save(v2);

        List<CarePlan> plans = carePlanRepo.findByClientIdOrderByPlanVersionAsc(client.getId());
        assertThat(plans).hasSize(2);

        CarePlan loadedV1 = carePlanRepo.findById(v1.getId()).orElseThrow();
        CarePlan loadedV2 = carePlanRepo.findById(v2.getId()).orElseThrow();
        assertThat(loadedV1.getStatus()).isEqualTo(CarePlanStatus.SUPERSEDED);
        assertThat(loadedV2.getStatus()).isEqualTo(CarePlanStatus.ACTIVE);
        assertThat(loadedV2.getPlanVersion()).isEqualTo(2);
    }

    @Test
    void adl_task_can_be_added_to_care_plan() {
        CarePlan plan = carePlanRepo.save(new CarePlan(client.getId(), agency.getId(), 1));
        AdlTask task = adlTaskRepo.save(new AdlTask(
            plan.getId(), agency.getId(), "Bathing", AssistanceLevel.MODERATE_ASSIST));

        AdlTask loaded = adlTaskRepo.findById(task.getId()).orElseThrow();
        assertThat(loaded.getName()).isEqualTo("Bathing");
        assertThat(loaded.getAssistanceLevel()).isEqualTo(AssistanceLevel.MODERATE_ASSIST);
        assertThat(loaded.getCarePlanId()).isEqualTo(plan.getId());
        assertThat(loaded.getSortOrder()).isEqualTo(0);
    }

    @Test
    void goal_can_be_added_to_care_plan() {
        CarePlan plan = carePlanRepo.save(new CarePlan(client.getId(), agency.getId(), 1));
        Goal goal = goalRepo.save(new Goal(
            plan.getId(), agency.getId(),
            "Improve independent ambulation to 50 feet without assistance"));

        Goal loaded = goalRepo.findById(goal.getId()).orElseThrow();
        assertThat(loaded.getDescription())
            .isEqualTo("Improve independent ambulation to 50 feet without assistance");
        assertThat(loaded.getStatus()).isEqualTo(GoalStatus.ACTIVE);
        assertThat(loaded.getTargetDate()).isNull();
    }

    @Test
    void review_sets_clinician_id_and_reviewed_at() {
        CarePlan plan = carePlanRepo.save(new CarePlan(client.getId(), agency.getId(), 1));
        UUID clinicianId = UUID.randomUUID();

        plan.review(clinicianId);
        carePlanRepo.save(plan);

        CarePlan loaded = carePlanRepo.findById(plan.getId()).orElseThrow();
        assertThat(loaded.getReviewedByClinicianId()).isEqualTo(clinicianId);
        assertThat(loaded.getReviewedAt()).isNotNull();
    }

    @Test
    void findByClientIdAndStatus_returns_only_active_plan() {
        // Plan 6 (admin API) uses this query to enforce one-active-per-client and to
        // retrieve the current plan for display. Verify it returns exactly the ACTIVE plan
        // and not the SUPERSEDED one.
        CarePlan v1 = carePlanRepo.save(new CarePlan(client.getId(), agency.getId(), 1));
        v1.activate();
        carePlanRepo.save(v1);

        v1.supersede();
        carePlanRepo.save(v1);

        CarePlan v2 = carePlanRepo.save(new CarePlan(client.getId(), agency.getId(), 2));
        v2.activate();
        carePlanRepo.save(v2);

        java.util.Optional<CarePlan> active =
            carePlanRepo.findByClientIdAndStatus(client.getId(), CarePlanStatus.ACTIVE);
        assertThat(active).isPresent();
        assertThat(active.get().getId()).isEqualTo(v2.getId());
        assertThat(active.get().getPlanVersion()).isEqualTo(2);

        java.util.Optional<CarePlan> draft =
            carePlanRepo.findByClientIdAndStatus(client.getId(), CarePlanStatus.DRAFT);
        assertThat(draft).isEmpty();
    }

    @Test
    void findActiveByClientId_returns_active_plan_and_empty_when_none() {
        CarePlan plan = carePlanRepo.save(new CarePlan(client.getId(), agency.getId(), 1));
        plan.activate();
        carePlanRepo.save(plan);

        Optional<CarePlan> found = carePlanRepo.findActiveByClientId(
            client.getId(), CarePlanStatus.ACTIVE);
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(plan.getId());
        assertThat(found.get().getStatus()).isEqualTo(CarePlanStatus.ACTIVE);

        Optional<CarePlan> missing = carePlanRepo.findActiveByClientId(
            UUID.randomUUID(), CarePlanStatus.ACTIVE);
        assertThat(missing).isEmpty();
    }
}
