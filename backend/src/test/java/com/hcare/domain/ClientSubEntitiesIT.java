package com.hcare.domain;

import com.hcare.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class ClientSubEntitiesIT extends AbstractIntegrationTest {

    @Autowired private AgencyRepository agencyRepo;
    @Autowired private ClientRepository clientRepo;
    @Autowired private ClientDiagnosisRepository diagnosisRepo;
    @Autowired private ClientMedicationRepository medicationRepo;
    @Autowired private DocumentRepository documentRepo;

    private Agency agency;
    private Client client;

    @BeforeEach
    void setup() {
        agency = agencyRepo.save(new Agency("Sub-Entity Client Agency", "TX"));
        client = clientRepo.save(
            new Client(agency.getId(), "Frank", "Green", LocalDate.of(1945, 8, 30)));
    }

    @Test
    void diagnosis_can_be_saved_with_icd10_code() {
        ClientDiagnosis dx = diagnosisRepo.save(
            new ClientDiagnosis(client.getId(), agency.getId(), "E11.9"));

        ClientDiagnosis loaded = diagnosisRepo.findById(dx.getId()).orElseThrow();
        assertThat(loaded.getIcd10Code()).isEqualTo("E11.9");
        assertThat(loaded.getClientId()).isEqualTo(client.getId());
        assertThat(loaded.getDescription()).isNull();
    }

    @Test
    void multiple_diagnoses_can_be_retrieved_by_client() {
        diagnosisRepo.save(new ClientDiagnosis(client.getId(), agency.getId(), "E11.9"));
        diagnosisRepo.save(new ClientDiagnosis(client.getId(), agency.getId(), "I10"));

        List<ClientDiagnosis> diagnoses = diagnosisRepo.findByClientId(client.getId());
        assertThat(diagnoses).hasSize(2);
        assertThat(diagnoses.stream().map(ClientDiagnosis::getIcd10Code))
            .containsExactlyInAnyOrder("E11.9", "I10");
    }

    @Test
    void medication_can_be_saved_and_retrieved() {
        ClientMedication med = medicationRepo.save(
            new ClientMedication(client.getId(), agency.getId(), "Metformin"));

        ClientMedication loaded = medicationRepo.findById(med.getId()).orElseThrow();
        assertThat(loaded.getName()).isEqualTo("Metformin");
        assertThat(loaded.getDosage()).isNull();
        assertThat(loaded.getClientId()).isEqualTo(client.getId());
    }

    @Test
    void document_can_be_saved_with_polymorphic_owner() {
        Document doc = documentRepo.save(new Document(
            agency.getId(), DocumentOwnerType.CLIENT, client.getId(),
            "care_plan_v1.pdf", "/storage/agency-123/care_plan_v1.pdf"));

        Document loaded = documentRepo.findById(doc.getId()).orElseThrow();
        assertThat(loaded.getOwnerType()).isEqualTo(DocumentOwnerType.CLIENT);
        assertThat(loaded.getOwnerId()).isEqualTo(client.getId());
        assertThat(loaded.getFileName()).isEqualTo("care_plan_v1.pdf");
    }

    @Test
    void document_type_and_uploaded_by_can_be_set() {
        java.util.UUID adminId = java.util.UUID.randomUUID();
        Document doc = new Document(
            agency.getId(), DocumentOwnerType.CLIENT, client.getId(),
            "consent_form.pdf", "/storage/agency-123/consent_form.pdf");
        doc.setDocumentType("CONSENT_FORM");
        doc.setUploadedBy(adminId);
        documentRepo.save(doc);

        Document loaded = documentRepo.findById(doc.getId()).orElseThrow();
        assertThat(loaded.getDocumentType()).isEqualTo("CONSENT_FORM");
        assertThat(loaded.getUploadedBy()).isEqualTo(adminId);
    }
}
