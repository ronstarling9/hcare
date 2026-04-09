package com.hcare.domain;

import com.hcare.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Sql(statements = {
    "TRUNCATE TABLE family_portal_tokens, family_portal_users, clients, agency_users, agencies RESTART IDENTITY CASCADE"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class FamilyPortalTokenDomainIT extends AbstractIntegrationTest {

    @Autowired private AgencyRepository agencyRepo;
    @Autowired private ClientRepository clientRepo;
    @Autowired private FamilyPortalUserRepository fpuRepo;
    @Autowired private FamilyPortalTokenRepository tokenRepo;

    private UUID clientId;
    private UUID agencyId;
    private UUID fpuId;

    @BeforeEach
    void seed() {
        Agency agency = agencyRepo.save(new Agency("Test Agency", "NY"));
        agencyId = agency.getId();
        clientId = clientRepo.save(new Client(agencyId, "Alice", "Test",
            java.time.LocalDate.of(1940, 1, 1))).getId();
        FamilyPortalUser fpu = fpuRepo.save(new FamilyPortalUser(clientId, agencyId, "family@example.com"));
        fpuId = fpu.getId();
    }

    @Test
    void savesTokenHash_notRawToken() {
        String fakeHash = "abc123def456";
        FamilyPortalToken token = new FamilyPortalToken(
            fakeHash, fpuId, clientId, agencyId,
            LocalDateTime.now(ZoneOffset.UTC).plusHours(72));
        FamilyPortalToken saved = tokenRepo.save(token);

        Optional<FamilyPortalToken> found = tokenRepo.findByTokenHash(fakeHash);
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
        assertThat(found.get().getFpuId()).isEqualTo(fpuId);
    }

    @Test
    void deleteExpired_removesOnlyExpiredRows() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        tokenRepo.save(new FamilyPortalToken("hash-expired", fpuId, clientId, agencyId,
            now.minusHours(1)));
        tokenRepo.save(new FamilyPortalToken("hash-valid", fpuId, clientId, agencyId,
            now.plusHours(71)));

        tokenRepo.deleteExpired(now);

        assertThat(tokenRepo.findByTokenHash("hash-expired")).isEmpty();
        assertThat(tokenRepo.findByTokenHash("hash-valid")).isPresent();
    }
}
