package com.hcare.domain;

import com.hcare.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.*;

class AuthorizationOptimisticLockIT extends AbstractIntegrationTest {

    @Autowired private AgencyRepository agencyRepo;
    @Autowired private PayerRepository payerRepo;
    @Autowired private ServiceTypeRepository serviceTypeRepo;
    @Autowired private ClientRepository clientRepo;
    @Autowired private AuthorizationRepository authRepo;

    @Test
    void authorization_can_be_saved_and_used_units_tracked() {
        Authorization auth = createAndSaveAuth("AUTH-001");

        auth.addUsedUnits(new BigDecimal("8.0"));
        authRepo.save(auth);

        Authorization loaded = authRepo.findById(auth.getId()).orElseThrow();
        assertThat(loaded.getUsedUnits()).isEqualByComparingTo("8.0");
        assertThat(loaded.getAuthorizedUnits()).isEqualByComparingTo("40.0");
        assertThat(loaded.getUnitType()).isEqualTo(UnitType.HOURS);
        assertThat(loaded.getVersion()).isEqualTo(1L);
    }

    @Test
    void stale_authorization_save_throws_optimistic_lock_exception() {
        // Simulates two concurrent threads both trying to increment usedUnits.
        // In production, the losing thread catches ObjectOptimisticLockingFailureException
        // and retries — this test proves the exception is thrown.

        Authorization auth = createAndSaveAuth("AUTH-002");
        // auth has version=0 after INSERT

        // Simulate Thread A: loads fresh, updates, saves (version 0 → 1)
        Authorization threadACopy = authRepo.findById(auth.getId()).orElseThrow();
        threadACopy.addUsedUnits(new BigDecimal("8.0"));
        authRepo.save(threadACopy); // committed, DB version is now 1

        // Simulate Thread B: uses stale object (version still 0 in memory)
        auth.addUsedUnits(new BigDecimal("4.0"));

        // UPDATE WHERE version=0 finds 0 rows (DB has version=1) → exception
        assertThatThrownBy(() -> authRepo.save(auth))
            .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void authorization_start_and_end_dates_are_persisted() {
        Authorization auth = createAndSaveAuth("AUTH-003");

        Authorization loaded = authRepo.findById(auth.getId()).orElseThrow();
        assertThat(loaded.getStartDate()).isEqualTo(LocalDate.now());
        assertThat(loaded.getEndDate()).isEqualTo(LocalDate.now().plusMonths(6));
        assertThat(loaded.getAuthNumber()).isEqualTo("AUTH-003");
    }

    // Helper shared by all tests in this class
    private Authorization createAndSaveAuth(String authNumber) {
        Agency agency = agencyRepo.save(new Agency("Auth Agency " + authNumber, "TX"));
        Payer payer = payerRepo.save(
            new Payer(agency.getId(), "Medicaid " + authNumber, PayerType.MEDICAID, "TX"));
        ServiceType st = serviceTypeRepo.save(
            new ServiceType(agency.getId(), "PCS " + authNumber, "PCS-" + authNumber, true, "[]"));
        Client client = clientRepo.save(
            new Client(agency.getId(), "Client " + authNumber, "Test", LocalDate.of(1960, 1, 1)));
        return authRepo.save(new Authorization(
            client.getId(), payer.getId(), st.getId(), agency.getId(),
            authNumber, new BigDecimal("40.0"),
            UnitType.HOURS, LocalDate.now(), LocalDate.now().plusMonths(6)));
    }
}
