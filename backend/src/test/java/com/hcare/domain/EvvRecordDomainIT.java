package com.hcare.domain;

import com.hcare.AbstractIntegrationTest;
import com.hcare.evv.VerificationMethod;
import com.hcare.multitenancy.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class EvvRecordDomainIT extends AbstractIntegrationTest {

    @Autowired AgencyRepository agencyRepo;
    @Autowired ClientRepository clientRepo;
    @Autowired ServiceTypeRepository serviceTypeRepo;
    @Autowired ShiftRepository shiftRepo;
    @Autowired EvvRecordRepository evvRecordRepo;
    @Autowired TransactionTemplate transactionTemplate;

    private Shift createShift(Agency agency, Client client, ServiceType st) {
        LocalDateTime start = LocalDate.of(2026, 4, 20).atTime(9, 0);
        return shiftRepo.save(new Shift(
            agency.getId(), null, client.getId(), null,
            st.getId(), null, start, start.plusHours(4)
        ));
    }

    @Test
    void evvRecord_can_be_saved_as_child_of_shift() {
        Agency agency = agencyRepo.save(new Agency("EVV Save Agency", "TX"));
        Client client = clientRepo.save(new Client(agency.getId(), "EVV", "Client", LocalDate.of(1958, 2, 14)));
        ServiceType st = serviceTypeRepo.save(new ServiceType(agency.getId(), "PCS", "PCS-E1", true, "[]"));
        Shift shift = createShift(agency, client, st);

        EvvRecord record = new EvvRecord(shift.getId(), agency.getId(), VerificationMethod.GPS);
        record.setLocationLat(new BigDecimal("30.2672"));
        record.setLocationLon(new BigDecimal("-97.7431"));
        record.setTimeIn(LocalDateTime.of(2026, 4, 20, 9, 5));
        evvRecordRepo.save(record);

        EvvRecord loaded = evvRecordRepo.findByShiftId(shift.getId()).orElseThrow();
        assertThat(loaded.getShiftId()).isEqualTo(shift.getId());
        assertThat(loaded.getAgencyId()).isEqualTo(agency.getId());
        assertThat(loaded.getVerificationMethod()).isEqualTo(VerificationMethod.GPS);
        assertThat(loaded.getLocationLat()).isEqualByComparingTo(new BigDecimal("30.2672"));
        assertThat(loaded.getTimeIn()).isEqualTo(LocalDateTime.of(2026, 4, 20, 9, 5));
        assertThat(loaded.getTimeOut()).isNull();
        assertThat(loaded.isCoResident()).isFalse();
        assertThat(loaded.isCapturedOffline()).isFalse();
        assertThat(loaded.getStateFields()).isEqualTo("{}");
    }

    @Test
    void evvRecord_unique_constraint_prevents_second_record_for_same_shift() {
        Agency agency = agencyRepo.save(new Agency("EVV Unique Agency", "TX"));
        Client client = clientRepo.save(new Client(agency.getId(), "Unique", "EVV", LocalDate.of(1965, 5, 5)));
        ServiceType st = serviceTypeRepo.save(new ServiceType(agency.getId(), "PCS", "PCS-EU", true, "[]"));
        Shift shift = createShift(agency, client, st);

        evvRecordRepo.save(new EvvRecord(shift.getId(), agency.getId(), VerificationMethod.GPS));

        assertThatThrownBy(() ->
            evvRecordRepo.saveAndFlush(new EvvRecord(shift.getId(), agency.getId(), VerificationMethod.MANUAL))
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void evvRecord_offline_fields_are_stored_correctly() {
        Agency agency = agencyRepo.save(new Agency("EVV Offline Agency", "TX"));
        Client client = clientRepo.save(new Client(agency.getId(), "Offline", "Test", LocalDate.of(1972, 8, 22)));
        ServiceType st = serviceTypeRepo.save(new ServiceType(agency.getId(), "PCS", "PCS-EO", true, "[]"));
        Shift shift = createShift(agency, client, st);

        EvvRecord record = new EvvRecord(shift.getId(), agency.getId(), VerificationMethod.GPS);
        record.setCapturedOffline(true);
        record.setDeviceCapturedAt(LocalDateTime.of(2026, 4, 20, 9, 2));
        evvRecordRepo.save(record);

        EvvRecord loaded = evvRecordRepo.findById(record.getId()).orElseThrow();
        assertThat(loaded.isCapturedOffline()).isTrue();
        assertThat(loaded.getDeviceCapturedAt()).isEqualTo(LocalDateTime.of(2026, 4, 20, 9, 2));
    }

    @Test
    void evvRecord_agencyFilter_excludes_other_agency_records() {
        Agency agencyA = agencyRepo.save(new Agency("EVV Agency A", "TX"));
        Agency agencyB = agencyRepo.save(new Agency("EVV Agency B", "CA"));
        Client clientA = clientRepo.save(new Client(agencyA.getId(), "A", "EVV", LocalDate.of(1960, 1, 1)));
        Client clientB = clientRepo.save(new Client(agencyB.getId(), "B", "EVV", LocalDate.of(1960, 1, 1)));
        ServiceType stA = serviceTypeRepo.save(new ServiceType(agencyA.getId(), "PCS", "PCS-EA", true, "[]"));
        ServiceType stB = serviceTypeRepo.save(new ServiceType(agencyB.getId(), "PCS", "PCS-EB", true, "[]"));

        LocalDateTime start = LocalDate.of(2026, 4, 21).atTime(9, 0);
        Shift shiftA = shiftRepo.save(new Shift(agencyA.getId(), null, clientA.getId(), null, stA.getId(), null, start, start.plusHours(4)));
        Shift shiftB = shiftRepo.save(new Shift(agencyB.getId(), null, clientB.getId(), null, stB.getId(), null, start, start.plusHours(4)));

        EvvRecord recordA = evvRecordRepo.save(new EvvRecord(shiftA.getId(), agencyA.getId(), VerificationMethod.GPS));
        evvRecordRepo.save(new EvvRecord(shiftB.getId(), agencyB.getId(), VerificationMethod.GPS));

        TenantContext.set(agencyA.getId());
        List<EvvRecord> result;
        try {
            result = transactionTemplate.execute(status -> evvRecordRepo.findAll());
        } finally {
            TenantContext.clear();
        }

        assertThat(result).isNotNull();
        assertThat(result.stream().map(EvvRecord::getId).toList()).contains(recordA.getId());
        assertThat(result).allMatch(r -> r.getAgencyId().equals(agencyA.getId()));
    }
}
