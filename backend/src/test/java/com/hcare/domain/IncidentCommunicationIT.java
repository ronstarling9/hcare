package com.hcare.domain;

import com.hcare.AbstractIntegrationTest;
import com.hcare.multitenancy.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class IncidentCommunicationIT extends AbstractIntegrationTest {

    @Autowired AgencyRepository agencyRepo;
    @Autowired ClientRepository clientRepo;
    @Autowired ServiceTypeRepository serviceTypeRepo;
    @Autowired ShiftRepository shiftRepo;
    @Autowired IncidentReportRepository incidentRepo;
    @Autowired CommunicationMessageRepository messageRepo;
    @Autowired TransactionTemplate transactionTemplate;

    // reported_by_id has no FK constraint (polymorphic: AGENCY_USER | CAREGIVER)
    private static final UUID REPORTER_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");

    @Test
    void incidentReport_can_be_saved_with_shift_reference() {
        Agency agency = agencyRepo.save(new Agency("Incident Shift Agency", "TX"));
        Client client = clientRepo.save(new Client(agency.getId(), "Inc", "Client", LocalDate.of(1960, 1, 1)));
        ServiceType st = serviceTypeRepo.save(new ServiceType(agency.getId(), "PCS", "PCS-I1", true, "[]"));
        LocalDateTime start = LocalDate.of(2026, 4, 23).atTime(10, 0);
        Shift shift = shiftRepo.save(new Shift(agency.getId(), null, client.getId(), null, st.getId(), null, start, start.plusHours(4)));

        IncidentReport report = new IncidentReport(
            agency.getId(), "AGENCY_USER", REPORTER_ID,
            "Client reported fall in bathroom", IncidentSeverity.HIGH,
            LocalDateTime.of(2026, 4, 23, 11, 30)
        );
        report.setShiftId(shift.getId());
        incidentRepo.save(report);

        IncidentReport loaded = incidentRepo.findById(report.getId()).orElseThrow();
        assertThat(loaded.getShiftId()).isEqualTo(shift.getId());
        assertThat(loaded.getAgencyId()).isEqualTo(agency.getId());
        assertThat(loaded.getSeverity()).isEqualTo(IncidentSeverity.HIGH);
        assertThat(loaded.getDescription()).isEqualTo("Client reported fall in bathroom");
        assertThat(loaded.getReportedByType()).isEqualTo("AGENCY_USER");
    }

    @Test
    void incidentReport_can_be_saved_without_shift_reference() {
        Agency agency = agencyRepo.save(new Agency("Incident No-Shift Agency", "TX"));

        IncidentReport report = new IncidentReport(
            agency.getId(), "AGENCY_USER", REPORTER_ID,
            "Staffing complaint from family", IncidentSeverity.LOW,
            LocalDateTime.of(2026, 4, 23, 14, 0)
        );
        incidentRepo.save(report);

        assertThat(incidentRepo.findById(report.getId()).orElseThrow().getShiftId()).isNull();
    }

    @Test
    void incidentReport_agencyFilter_excludes_other_agency_incidents() {
        Agency agencyA = agencyRepo.save(new Agency("Inc Agency A", "TX"));
        Agency agencyB = agencyRepo.save(new Agency("Inc Agency B", "CA"));

        IncidentReport reportA = incidentRepo.save(new IncidentReport(
            agencyA.getId(), "AGENCY_USER", REPORTER_ID,
            "Agency A incident", IncidentSeverity.MEDIUM, LocalDateTime.now()
        ));
        incidentRepo.save(new IncidentReport(
            agencyB.getId(), "AGENCY_USER", REPORTER_ID,
            "Agency B incident", IncidentSeverity.MEDIUM, LocalDateTime.now()
        ));

        TenantContext.set(agencyA.getId());
        List<IncidentReport> result;
        try {
            result = transactionTemplate.execute(status -> incidentRepo.findAll());
        } finally {
            TenantContext.clear();
        }

        assertThat(result).isNotNull();
        assertThat(result.stream().map(IncidentReport::getId).toList()).contains(reportA.getId());
        assertThat(result).allMatch(r -> r.getAgencyId().equals(agencyA.getId()));
    }

    @Test
    void communicationMessage_can_be_saved_and_retrieved() {
        Agency agency = agencyRepo.save(new Agency("Comm Agency", "TX"));
        UUID senderId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();

        CommunicationMessage msg = new CommunicationMessage(
            agency.getId(), "AGENCY_USER", senderId,
            "CAREGIVER", recipientId,
            "Please confirm availability for Thursday"
        );
        msg.setSubject("Schedule Confirmation");
        messageRepo.save(msg);

        CommunicationMessage loaded = messageRepo.findById(msg.getId()).orElseThrow();
        assertThat(loaded.getAgencyId()).isEqualTo(agency.getId());
        assertThat(loaded.getSenderType()).isEqualTo("AGENCY_USER");
        assertThat(loaded.getRecipientType()).isEqualTo("CAREGIVER");
        assertThat(loaded.getBody()).isEqualTo("Please confirm availability for Thursday");
        assertThat(loaded.getSubject()).isEqualTo("Schedule Confirmation");
        assertThat(loaded.getSentAt()).isNotNull();
    }

    @Test
    void communicationMessage_agencyFilter_excludes_other_agency_messages() {
        Agency agencyA = agencyRepo.save(new Agency("Comm Agency A", "TX"));
        Agency agencyB = agencyRepo.save(new Agency("Comm Agency B", "CA"));
        UUID uid = UUID.randomUUID();

        CommunicationMessage msgA = messageRepo.save(new CommunicationMessage(
            agencyA.getId(), "AGENCY_USER", uid, "CAREGIVER", uid, "Agency A message"
        ));
        messageRepo.save(new CommunicationMessage(
            agencyB.getId(), "AGENCY_USER", uid, "CAREGIVER", uid, "Agency B message"
        ));

        TenantContext.set(agencyA.getId());
        List<CommunicationMessage> result;
        try {
            result = transactionTemplate.execute(status -> messageRepo.findAll());
        } finally {
            TenantContext.clear();
        }

        assertThat(result).isNotNull();
        assertThat(result.stream().map(CommunicationMessage::getId).toList()).contains(msgA.getId());
        assertThat(result).allMatch(m -> m.getAgencyId().equals(agencyA.getId()));
    }
}
