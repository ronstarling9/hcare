package com.hcare.domain;

import com.hcare.AbstractIntegrationTest;
import com.hcare.multitenancy.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class ClientDomainIT extends AbstractIntegrationTest {

    @Autowired private AgencyRepository agencyRepo;
    @Autowired private ClientRepository clientRepo;
    @Autowired private CaregiverClientAffinityRepository affinityRepo;
    @Autowired private CaregiverScoringProfileRepository scoringProfileRepo;
    @Autowired private CaregiverRepository caregiverRepo;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private FamilyPortalUserRepository familyPortalUserRepo;

    @Test
    void client_can_be_saved_and_retrieved() {
        Agency agency = agencyRepo.save(new Agency("Client Test Agency", "TX"));
        Client client = clientRepo.save(new Client(
            agency.getId(), "Jane", "Doe", LocalDate.of(1960, 5, 20)));

        Client loaded = clientRepo.findById(client.getId()).orElseThrow();
        assertThat(loaded.getFirstName()).isEqualTo("Jane");
        assertThat(loaded.getLastName()).isEqualTo("Doe");
        assertThat(loaded.getDateOfBirth()).isEqualTo(LocalDate.of(1960, 5, 20));
        assertThat(loaded.getStatus()).isEqualTo(ClientStatus.ACTIVE);
        assertThat(loaded.getPreferredLanguages()).isEqualTo("[]");
        assertThat(loaded.isNoPetCaregiver()).isFalse();
        assertThat(loaded.getServiceState()).isNull();
    }

    @Test
    void client_serviceState_overrides_agency_state_for_evv_routing() {
        Agency agency = agencyRepo.save(new Agency("Border Agency", "TX"));
        Client client = new Client(agency.getId(), "Bob", "Border", LocalDate.of(1975, 3, 10));
        client.setServiceState("OK");
        clientRepo.save(client);

        Client loaded = clientRepo.findById(client.getId()).orElseThrow();
        assertThat(loaded.getServiceState()).isEqualTo("OK");
    }

    @Test
    void client_agencyFilter_excludes_other_agency_clients() {
        Agency agencyA = agencyRepo.save(new Agency("Client Agency A", "TX"));
        Agency agencyB = agencyRepo.save(new Agency("Client Agency B", "CA"));

        Client clientA = clientRepo.save(
            new Client(agencyA.getId(), "Alice", "A", LocalDate.of(1970, 1, 1)));
        clientRepo.save(
            new Client(agencyB.getId(), "Bob", "B", LocalDate.of(1970, 1, 1)));

        TenantContext.set(agencyA.getId());
        List<Client> result;
        try {
            result = transactionTemplate.execute(status -> clientRepo.findAll());
        } finally {
            TenantContext.clear();
        }

        assertThat(result).isNotNull();
        assertThat(result.stream().map(Client::getId).toList())
            .contains(clientA.getId());
        assertThat(result).allMatch(c -> c.getAgencyId().equals(agencyA.getId()));
    }

    @Test
    void caregiver_client_affinity_can_be_saved_now_that_client_exists() {
        Agency agency = agencyRepo.save(new Agency("Affinity Agency", "TX"));
        Caregiver caregiver = caregiverRepo.save(
            new Caregiver(agency.getId(), "Dave", "D", "dave-aff@test.com"));
        Client client = clientRepo.save(
            new Client(agency.getId(), "Eve", "E", LocalDate.of(1980, 6, 15)));
        CaregiverScoringProfile profile = scoringProfileRepo.save(
            new CaregiverScoringProfile(caregiver.getId(), agency.getId()));

        CaregiverClientAffinity affinity = affinityRepo.save(
            new CaregiverClientAffinity(profile.getId(), client.getId(), agency.getId()));
        affinity.incrementVisitCount();
        affinityRepo.save(affinity);

        CaregiverClientAffinity loaded = affinityRepo.findById(affinity.getId()).orElseThrow();
        assertThat(loaded.getVisitCount()).isEqualTo(1);
        assertThat(loaded.getClientId()).isEqualTo(client.getId());
        assertThat(loaded.getScoringProfileId()).isEqualTo(profile.getId());

        // Plan 5's primary lookup — find or create an affinity row for a caregiver+client pair
        assertThat(affinityRepo.findByScoringProfileIdAndClientId(profile.getId(), client.getId()))
            .isPresent()
            .hasValueSatisfying(a -> assertThat(a.getVisitCount()).isEqualTo(1));
        assertThat(affinityRepo.findByScoringProfileIdAndClientId(profile.getId(), UUID.randomUUID()))
            .isEmpty();
    }

    @Test
    void family_portal_user_is_scoped_to_a_single_client() {
        Agency agency = agencyRepo.save(new Agency("FPU Agency", "TX"));
        Client client = clientRepo.save(
            new Client(agency.getId(), "Grace", "Hall", LocalDate.of(1952, 4, 7)));

        FamilyPortalUser fpu = familyPortalUserRepo.save(
            new FamilyPortalUser(client.getId(), agency.getId(), "daughter@family.com"));

        FamilyPortalUser loaded = familyPortalUserRepo.findById(fpu.getId()).orElseThrow();
        assertThat(loaded.getEmail()).isEqualTo("daughter@family.com");
        assertThat(loaded.getClientId()).isEqualTo(client.getId());
        assertThat(loaded.getAgencyId()).isEqualTo(agency.getId());
        assertThat(loaded.getLastLoginAt()).isNull();
    }

    @Test
    void family_portal_user_recordLogin_persists_timestamp() {
        Agency agency = agencyRepo.save(new Agency("FPU Login Agency", "TX"));
        Client client = clientRepo.save(
            new Client(agency.getId(), "Judy", "Moore", LocalDate.of(1958, 2, 14)));
        FamilyPortalUser fpu = familyPortalUserRepo.save(
            new FamilyPortalUser(client.getId(), agency.getId(), "judy-son@family.com"));

        assertThat(fpu.getLastLoginAt()).isNull();

        fpu.recordLogin();
        familyPortalUserRepo.save(fpu);

        FamilyPortalUser reloaded = familyPortalUserRepo.findById(fpu.getId()).orElseThrow();
        assertThat(reloaded.getLastLoginAt()).isNotNull();
    }

    @Test
    void family_portal_user_can_be_found_by_agency_and_email() {
        Agency agencyA = agencyRepo.save(new Agency("FPU Lookup Agency A", "TX"));
        Agency agencyB = agencyRepo.save(new Agency("FPU Lookup Agency B", "CA"));
        Client clientA = clientRepo.save(
            new Client(agencyA.getId(), "Ivan", "King", LocalDate.of(1940, 9, 3)));
        Client clientB = clientRepo.save(
            new Client(agencyB.getId(), "Ivan", "King", LocalDate.of(1940, 9, 3)));

        // Same email registered at two different agencies — now allowed
        familyPortalUserRepo.save(
            new FamilyPortalUser(clientA.getId(), agencyA.getId(), "son@lookup.com"));
        familyPortalUserRepo.save(
            new FamilyPortalUser(clientB.getId(), agencyB.getId(), "son@lookup.com"));

        // Lookup scoped by (client, agency) — returns the correct record, not the other agency's
        assertThat(familyPortalUserRepo.findByClientIdAndAgencyIdAndEmail(clientA.getId(), agencyA.getId(), "son@lookup.com")).isPresent();
        assertThat(familyPortalUserRepo.findByClientIdAndAgencyIdAndEmail(clientB.getId(), agencyB.getId(), "son@lookup.com")).isPresent();
        assertThat(familyPortalUserRepo.findByClientIdAndAgencyIdAndEmail(clientA.getId(), agencyA.getId(), "notexists@lookup.com")).isEmpty();
    }
}
