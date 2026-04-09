package com.hcare.integration.evv;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hcare.evv.AggregatorType;
import com.hcare.integration.CredentialEncryptionService;
import com.hcare.integration.audit.IntegrationAuditWriter;
import com.hcare.integration.evv.sandata.SandataCredentials;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class EvvBatchDrainJobTest {

    @Mock
    private EvvSubmissionRecordSystemRepository systemRepo;
    @Mock
    private EvvBatchRecordManager batchRecordManager;
    @Mock
    private AgencyEvvCredentialsRepository credentialsRepository;
    @Mock
    private CredentialEncryptionService encryptionService;
    @Mock
    private EvvStrategyFactory strategyFactory;
    @Mock
    private EvvSubmissionContextAssembler contextAssembler;
    @Mock
    private IntegrationAuditWriter auditWriter;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private EvvBatchDrainJob job;

    @BeforeEach
    void setUp() {
        job = new EvvBatchDrainJob(
                systemRepo, batchRecordManager, credentialsRepository,
                encryptionService, strategyFactory, contextAssembler, auditWriter, objectMapper);
    }

    @Test
    void drain_noAgenciesWithPendingBatch_doesNothing() {
        when(systemRepo.findDistinctAgenciesWithPendingBatch()).thenReturn(List.of());

        job.drain();

        verify(systemRepo, never()).findNextBatchPending(any(), any());
        verify(batchRecordManager, never()).claimRecord(any());
    }

    @Test
    void drain_claimReturnsFalse_skipSubmit() {
        UUID agencyId = UUID.randomUUID();
        EvvSubmissionRecord record = pendingRecord(agencyId, "{\"someJson\":true}");

        when(systemRepo.findDistinctAgenciesWithPendingBatch()).thenReturn(List.of(agencyId));
        when(systemRepo.findNextBatchPending(eq(agencyId), any(Pageable.class)))
                .thenReturn(List.of(record))
                .thenReturn(List.of());
        when(batchRecordManager.claimRecord(record.getId())).thenReturn(0);

        job.drain();

        verify(strategyFactory, never()).strategyFor(any());
    }

    @Test
    void drain_nullContextJson_marksRejected() {
        UUID agencyId = UUID.randomUUID();
        EvvSubmissionRecord record = pendingRecord(agencyId, null);

        when(systemRepo.findDistinctAgenciesWithPendingBatch()).thenReturn(List.of(agencyId));
        when(systemRepo.findNextBatchPending(eq(agencyId), any(Pageable.class)))
                .thenReturn(List.of(record))
                .thenReturn(List.of());
        when(batchRecordManager.claimRecord(record.getId())).thenReturn(1);

        job.drain();

        verify(batchRecordManager).finalizeRecord(
                eq(record.getId()),
                eq("REJECTED"),
                isNull(),
                isNull());
        verify(strategyFactory, never()).strategyFor(any());
    }

    @Test
    void drain_successfulSubmit_marksSubmitted() throws Exception {
        UUID agencyId = UUID.randomUUID();
        EvvSubmissionContext ctx = buildContext(agencyId);
        String contextJson = objectMapper.writeValueAsString(ctx);

        EvvSubmissionRecord record = pendingRecord(agencyId, contextJson);
        record.setAggregatorType(AggregatorType.SANDATA.name());

        AgencyEvvCredentials agencyCreds = new AgencyEvvCredentials(
                agencyId, "SANDATA", "encrypted-blob", null, true);
        SandataCredentials typedCreds = new SandataCredentials("u", "p", "pid");

        EvvSubmissionStrategy mockStrategy = mock(EvvSubmissionStrategy.class);
        when(mockStrategy.submit(any(), any())).thenReturn(EvvSubmissionResult.ok("AGG-V1"));
        when(mockStrategy.credentialClass()).thenReturn((Class) SandataCredentials.class);

        when(systemRepo.findDistinctAgenciesWithPendingBatch()).thenReturn(List.of(agencyId));
        when(systemRepo.findNextBatchPending(eq(agencyId), any(Pageable.class)))
                .thenReturn(List.of(record))
                .thenReturn(List.of());
        when(batchRecordManager.claimRecord(record.getId())).thenReturn(1);
        when(credentialsRepository.findByAgencyIdAndAggregatorTypeAndActiveTrue(
                eq(agencyId), eq("SANDATA")))
                .thenReturn(Optional.of(agencyCreds));
        when(encryptionService.decrypt(eq("encrypted-blob"), any())).thenReturn(typedCreds);
        when(strategyFactory.strategyFor(AggregatorType.SANDATA)).thenReturn(mockStrategy);

        job.drain();

        verify(batchRecordManager).finalizeRecord(
                eq(record.getId()),
                eq("SUBMITTED"),
                eq("AGG-V1"),
                eq(ctx.evvRecordId()));
    }

    @Test
    void drain_successfulSubmit_txOrderIsClaimThenSubmitThenFinalize() throws Exception {
        UUID agencyId = UUID.randomUUID();
        EvvSubmissionContext ctx = buildContext(agencyId);
        String contextJson = objectMapper.writeValueAsString(ctx);

        EvvSubmissionRecord record = pendingRecord(agencyId, contextJson);
        record.setAggregatorType(AggregatorType.SANDATA.name());

        AgencyEvvCredentials agencyCreds = new AgencyEvvCredentials(
                agencyId, "SANDATA", "encrypted-blob", null, true);
        SandataCredentials typedCreds = new SandataCredentials("u", "p", "pid");

        EvvSubmissionStrategy mockStrategy = mock(EvvSubmissionStrategy.class);
        when(mockStrategy.submit(any(), any())).thenReturn(EvvSubmissionResult.ok("AGG-V2"));
        when(mockStrategy.credentialClass()).thenReturn((Class) SandataCredentials.class);

        when(systemRepo.findDistinctAgenciesWithPendingBatch()).thenReturn(List.of(agencyId));
        when(systemRepo.findNextBatchPending(eq(agencyId), any(Pageable.class)))
                .thenReturn(List.of(record))
                .thenReturn(List.of());
        when(batchRecordManager.claimRecord(record.getId())).thenReturn(1);
        when(credentialsRepository.findByAgencyIdAndAggregatorTypeAndActiveTrue(
                eq(agencyId), eq("SANDATA")))
                .thenReturn(Optional.of(agencyCreds));
        when(encryptionService.decrypt(eq("encrypted-blob"), any())).thenReturn(typedCreds);
        when(strategyFactory.strategyFor(AggregatorType.SANDATA)).thenReturn(mockStrategy);

        job.drain();

        InOrder order = inOrder(batchRecordManager, mockStrategy);
        order.verify(batchRecordManager).claimRecord(any());
        order.verify(mockStrategy).submit(any(), any());
        order.verify(batchRecordManager).finalizeRecord(any(), eq("SUBMITTED"), any(), any());
    }

    @Test
    void drain_failedSubmit_marksRejected() throws Exception {
        UUID agencyId = UUID.randomUUID();
        EvvSubmissionContext ctx = buildContext(agencyId);
        String contextJson = objectMapper.writeValueAsString(ctx);

        EvvSubmissionRecord record = pendingRecord(agencyId, contextJson);
        record.setAggregatorType(AggregatorType.SANDATA.name());

        AgencyEvvCredentials agencyCreds = new AgencyEvvCredentials(
                agencyId, "SANDATA", "encrypted-blob", null, true);
        SandataCredentials typedCreds = new SandataCredentials("u", "p", "pid");

        EvvSubmissionStrategy mockStrategy = mock(EvvSubmissionStrategy.class);
        when(mockStrategy.submit(any(), any()))
                .thenReturn(EvvSubmissionResult.failure("ERR", "msg"));
        when(mockStrategy.credentialClass()).thenReturn((Class) SandataCredentials.class);

        when(systemRepo.findDistinctAgenciesWithPendingBatch()).thenReturn(List.of(agencyId));
        when(systemRepo.findNextBatchPending(eq(agencyId), any(Pageable.class)))
                .thenReturn(List.of(record))
                .thenReturn(List.of());
        when(batchRecordManager.claimRecord(record.getId())).thenReturn(1);
        when(credentialsRepository.findByAgencyIdAndAggregatorTypeAndActiveTrue(
                eq(agencyId), eq("SANDATA")))
                .thenReturn(Optional.of(agencyCreds));
        when(encryptionService.decrypt(eq("encrypted-blob"), any())).thenReturn(typedCreds);
        when(strategyFactory.strategyFor(AggregatorType.SANDATA)).thenReturn(mockStrategy);

        job.drain();

        verify(batchRecordManager).finalizeRecord(
                eq(record.getId()),
                eq("REJECTED"),
                isNull(),
                isNull());
    }

    @Test
    void resetStaleInFlightOnStartup_callsSystemRepoWithCutoff() {
        when(systemRepo.resetStaleInFlight(any())).thenReturn(0);

        job.resetStaleInFlightOnStartup();

        verify(systemRepo).resetStaleInFlight(any(LocalDateTime.class));
    }

    private EvvSubmissionRecord pendingRecord(UUID agencyId, String contextJson) {
        EvvSubmissionRecord record = new EvvSubmissionRecord();
        record.setAgencyId(agencyId);
        record.setEvvRecordId(UUID.randomUUID());
        record.setAggregatorType(AggregatorType.SANDATA.name());
        record.setStatus("PENDING");
        record.setSubmissionMode("BATCH");
        record.setContextJson(contextJson);
        return record;
    }

    private EvvSubmissionContext buildContext(UUID agencyId) {
        return new EvvSubmissionContext(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                agencyId,
                UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"),
                UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"),
                UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"),
                AggregatorType.SANDATA,
                "MA",
                "1234567893",
                "MEDICAID123",
                "T1019",
                LocalDateTime.of(2026, 1, 10, 8, 0),
                LocalDateTime.of(2026, 1, 10, 12, 0),
                "MA",
                null);
    }
}
