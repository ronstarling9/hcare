package com.hcare.integration.evv;

import com.hcare.AbstractIntegrationTest;
import com.hcare.domain.Agency;
import com.hcare.domain.AgencyRepository;
import com.hcare.domain.Authorization;
import com.hcare.domain.AuthorizationRepository;
import com.hcare.domain.Caregiver;
import com.hcare.domain.CaregiverRepository;
import com.hcare.domain.Client;
import com.hcare.domain.ClientRepository;
import com.hcare.domain.EvvRecord;
import com.hcare.domain.EvvRecordRepository;
import com.hcare.domain.Payer;
import com.hcare.domain.PayerRepository;
import com.hcare.domain.PayerType;
import com.hcare.domain.ServiceType;
import com.hcare.domain.ServiceTypeRepository;
import com.hcare.domain.Shift;
import com.hcare.domain.ShiftCompletedEvent;
import com.hcare.domain.ShiftRepository;
import com.hcare.domain.UnitType;
import com.hcare.evv.VerificationMethod;
import com.hcare.integration.CredentialEncryptionService;
import com.hcare.integration.audit.IntegrationAuditLog;
import com.hcare.integration.audit.IntegrationAuditLogRepository;
import com.hcare.integration.evv.sandata.SandataCredentials;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the full {@link EvvSubmissionService#onShiftCompleted} event-listener path.
 *
 * <p>Uses {@link TransactionTemplate} to create real commits so that the
 * {@code @TransactionalEventListener} fires. Test methods are NOT {@code @Transactional} to
 * preserve the committed state visible to the async listener thread.
 */
class EvvSubmissionServiceIT extends AbstractIntegrationTest {

    @Autowired
    private AgencyRepository agencyRepository;

    @Autowired
    private PayerRepository payerRepository;

    @Autowired
    private AuthorizationRepository authorizationRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private CaregiverRepository caregiverRepository;

    @Autowired
    private ServiceTypeRepository serviceTypeRepository;

    @Autowired
    private ShiftRepository shiftRepository;

    @Autowired
    private EvvRecordRepository evvRecordRepository;

    @Autowired
    private AgencyEvvCredentialsRepository agencyEvvCredentialsRepository;

    @Autowired
    private IntegrationAuditLogRepository integrationAuditLogRepository;

    @Autowired
    private CredentialEncryptionService credentialEncryptionService;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager txManager;

    /**
     * Happy-path: credentials present, Sandata connector unavailable (no REST client configured
     * in test profile). Verifies that the event listener fires, resolves SANDATA via CA state
     * config, attempts real-time submission, and writes an audit log entry with
     * {@code errorCode = "CONNECTOR_UNAVAILABLE"}.
     *
     * <p>Note: RetryingEvvSubmissionStrategy retries CONNECTOR_UNAVAILABLE three times with
     * delays of ~2s + ~4s. Awaitility timeout is set to 25s to accommodate retries.
     */
    @Test
    void onShiftCompleted_withValidData_writesAuditLogWithConnectorUnavailable() throws Exception {
        // Seed all entities in a committing transaction so the @TransactionalEventListener fires.
        UUID[] evvRecordIdHolder = new UUID[1];
        UUID[] agencyIdHolder = new UUID[1];

        TransactionTemplate txTemplate = new TransactionTemplate(txManager);
        txTemplate.execute(status -> {
            // 1. Agency
            Agency agency = new Agency("IT Agency Connector Test", "CA");
            agency.setTimezone("America/Los_Angeles");
            agency = agencyRepository.save(agency);
            UUID agencyId = agency.getId();
            agencyIdHolder[0] = agencyId;

            // 2. Payer (CA Medicaid → routes to SANDATA via V2 seed)
            Payer payer = new Payer(agencyId, "CA Medicaid IT", PayerType.MEDICAID, "CA");
            payer = payerRepository.save(payer);

            // 3. ServiceType (required FK on Shift and Authorization)
            ServiceType serviceType = new ServiceType(
                    agencyId, "Personal Care", "T1019", true, "[]");
            serviceType = serviceTypeRepository.save(serviceType);

            // 4. Client with serviceState=CA to drive routing
            Client client = new Client(agencyId, "Jane", "Doe", LocalDate.of(1950, 1, 1));
            client.setServiceState("CA");
            client.setMedicaidId("CA-MEDICAID-IT-001");
            client = clientRepository.save(client);

            // 5. Authorization
            Authorization auth = new Authorization(
                    client.getId(), payer.getId(), serviceType.getId(), agencyId,
                    "AUTH-IT-001", new BigDecimal("100.00"), UnitType.HOURS,
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
            auth = authorizationRepository.save(auth);

            // 6. Caregiver with NPI (required for Sandata validate())
            Caregiver caregiver = new Caregiver(agencyId, "John", "Smith", "john.it1@test.dev");
            caregiver.setNpi("1234567893");
            caregiver = caregiverRepository.save(caregiver);

            // 7. Shift
            Shift shift = new Shift(
                    agencyId, null,
                    client.getId(), caregiver.getId(),
                    serviceType.getId(), auth.getId(),
                    LocalDateTime.of(2026, 4, 9, 8, 0),
                    LocalDateTime.of(2026, 4, 9, 12, 0));
            shift.setHcpcsCode("T1019");
            shift = shiftRepository.save(shift);

            // 8. EvvRecord
            EvvRecord evvRecord = new EvvRecord(shift.getId(), agencyId,
                    VerificationMethod.GPS);
            evvRecord.setTimeIn(LocalDateTime.of(2026, 4, 9, 8, 0));
            evvRecord.setTimeOut(LocalDateTime.of(2026, 4, 9, 12, 0));
            evvRecord.setClientMedicaidId("CA-MEDICAID-IT-001");
            evvRecord = evvRecordRepository.save(evvRecord);
            evvRecordIdHolder[0] = evvRecord.getId();

            // 9. AgencyEvvCredentials (SANDATA, encrypted)
            try {
                SandataCredentials sandataCreds =
                        new SandataCredentials("testuser", "testpass", "CA_PAYER_001");
                String encrypted = credentialEncryptionService.encrypt(sandataCreds);
                AgencyEvvCredentials creds = new AgencyEvvCredentials(
                        agencyId, "SANDATA", encrypted, null, true);
                agencyEvvCredentialsRepository.save(creds);
            } catch (Exception e) {
                throw new RuntimeException("Failed to encrypt Sandata credentials", e);
            }

            // 10. Publish event — @TransactionalEventListener fires after this commit
            ShiftCompletedEvent event = new ShiftCompletedEvent(
                    shift.getId(), caregiver.getId(), client.getId(), agencyId,
                    LocalDateTime.of(2026, 4, 9, 8, 0),
                    LocalDateTime.of(2026, 4, 9, 12, 0));
            eventPublisher.publishEvent(event);

            return null;
        });

        UUID evvRecordId = evvRecordIdHolder[0];
        UUID agencyId = agencyIdHolder[0];
        assertThat(evvRecordId).isNotNull();

        // Wait for async EVV submission including RetryingEvvSubmissionStrategy delays (~6s total)
        Awaitility.await()
                .atMost(25, TimeUnit.SECONDS)
                .until(() -> integrationAuditLogRepository.findAll().stream()
                        .anyMatch(log -> evvRecordId.equals(log.getEntityId())));

        // The EvvSubmissionService.submitRealTime() writes one audit entry after strategy.submit()
        // returns. Assert the entry written by the service-level auditWriter.record() call.
        List<IntegrationAuditLog> logs = integrationAuditLogRepository.findAll();
        List<IntegrationAuditLog> relevant = logs.stream()
                .filter(l -> evvRecordId.equals(l.getEntityId())
                        && agencyId.equals(l.getAgencyId()))
                .toList();

        assertThat(relevant).as("Expected at least one audit log entry for evvRecordId=%s", evvRecordId)
                .isNotEmpty();

        // At least one entry must reflect the CONNECTOR_UNAVAILABLE failure
        IntegrationAuditLog auditEntry = relevant.stream()
                .filter(l -> "CONNECTOR_UNAVAILABLE".equals(l.getErrorCode()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No CONNECTOR_UNAVAILABLE audit entry found. Entries: " + relevant));

        assertThat(auditEntry.getConnector()).isEqualTo("SANDATA");
        assertThat(auditEntry.getOperation()).isEqualTo("SUBMIT");
        assertThat(auditEntry.getAgencyId()).isEqualTo(agencyId);
        assertThat(auditEntry.isSuccess()).isFalse();
        assertThat(auditEntry.getErrorCode()).isEqualTo("CONNECTOR_UNAVAILABLE");
        assertThat(auditEntry.getRecordedAt()).isNotNull();
    }

    /**
     * Missing credentials path: no {@link AgencyEvvCredentials} seeded. Verifies that
     * {@code EvvSubmissionService} writes an audit log entry with
     * {@code errorCode = "MISSING_CREDENTIALS"} and does not attempt aggregator submission.
     */
    @Test
    void onShiftCompleted_missingCredentials_writesAuditLogWithMissingCredentialsError() {
        UUID[] evvRecordIdHolder = new UUID[1];
        UUID[] agencyIdHolder = new UUID[1];

        TransactionTemplate txTemplate = new TransactionTemplate(txManager);
        txTemplate.execute(status -> {
            // 1. Agency (distinct slug to avoid conflicts with other test)
            Agency agency = new Agency("IT Agency Missing Creds Test", "CA");
            agency.setTimezone("America/Los_Angeles");
            agency = agencyRepository.save(agency);
            UUID agencyId = agency.getId();
            agencyIdHolder[0] = agencyId;

            // 2. Payer
            Payer payer = new Payer(agencyId, "CA Medicaid Missing", PayerType.MEDICAID, "CA");
            payer = payerRepository.save(payer);

            // 3. ServiceType
            ServiceType serviceType = new ServiceType(
                    agencyId, "Personal Care MC", "T1019", true, "[]");
            serviceType = serviceTypeRepository.save(serviceType);

            // 4. Client
            Client client = new Client(agencyId, "Alice", "Brown", LocalDate.of(1960, 6, 15));
            client.setServiceState("CA");
            client.setMedicaidId("CA-MEDICAID-IT-002");
            client = clientRepository.save(client);

            // 5. Authorization
            Authorization auth = new Authorization(
                    client.getId(), payer.getId(), serviceType.getId(), agencyId,
                    "AUTH-IT-002", new BigDecimal("80.00"), UnitType.HOURS,
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
            auth = authorizationRepository.save(auth);

            // 6. Caregiver
            Caregiver caregiver = new Caregiver(agencyId, "Bob", "Jones", "bob.it2@test.dev");
            caregiver.setNpi("1234567893");
            caregiver = caregiverRepository.save(caregiver);

            // 7. Shift
            Shift shift = new Shift(
                    agencyId, null,
                    client.getId(), caregiver.getId(),
                    serviceType.getId(), auth.getId(),
                    LocalDateTime.of(2026, 4, 9, 9, 0),
                    LocalDateTime.of(2026, 4, 9, 13, 0));
            shift.setHcpcsCode("T1019");
            shift = shiftRepository.save(shift);

            // 8. EvvRecord
            EvvRecord evvRecord = new EvvRecord(shift.getId(), agencyId,
                    VerificationMethod.GPS);
            evvRecord.setTimeIn(LocalDateTime.of(2026, 4, 9, 9, 0));
            evvRecord.setTimeOut(LocalDateTime.of(2026, 4, 9, 13, 0));
            evvRecord.setClientMedicaidId("CA-MEDICAID-IT-002");
            evvRecord = evvRecordRepository.save(evvRecord);
            evvRecordIdHolder[0] = evvRecord.getId();

            // No AgencyEvvCredentials seeded — this is the missing credentials scenario.

            // 9. Publish event
            ShiftCompletedEvent event = new ShiftCompletedEvent(
                    shift.getId(), caregiver.getId(), client.getId(), agencyId,
                    LocalDateTime.of(2026, 4, 9, 9, 0),
                    LocalDateTime.of(2026, 4, 9, 13, 0));
            eventPublisher.publishEvent(event);

            return null;
        });

        UUID evvRecordId = evvRecordIdHolder[0];
        UUID agencyId = agencyIdHolder[0];
        assertThat(evvRecordId).isNotNull();

        // Missing credentials path is fast — no retries, writes immediately
        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .until(() -> integrationAuditLogRepository.findAll().stream()
                        .anyMatch(log -> evvRecordId.equals(log.getEntityId())));

        List<IntegrationAuditLog> logs = integrationAuditLogRepository.findAll();
        IntegrationAuditLog auditEntry = logs.stream()
                .filter(l -> evvRecordId.equals(l.getEntityId())
                        && agencyId.equals(l.getAgencyId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No audit log entry found for evvRecordId=" + evvRecordId));

        assertThat(auditEntry.getConnector()).isEqualTo("SANDATA");
        assertThat(auditEntry.getOperation()).isEqualTo("SUBMIT");
        assertThat(auditEntry.getAgencyId()).isEqualTo(agencyId);
        assertThat(auditEntry.isSuccess()).isFalse();
        assertThat(auditEntry.getErrorCode()).isEqualTo("MISSING_CREDENTIALS");
        assertThat(auditEntry.getRecordedAt()).isNotNull();
    }
}
