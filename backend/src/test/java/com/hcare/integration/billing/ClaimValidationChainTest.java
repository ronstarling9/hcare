package com.hcare.integration.billing;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hcare.integration.billing.validation.ClaimValidationException;
import com.hcare.integration.billing.validation.EvvLinkageHandler;
import com.hcare.integration.billing.validation.NpiFormatHandler;
import com.hcare.integration.billing.validation.TimelyFilingHandler;
import com.hcare.integration.evv.EvvSubmissionRecord;
import com.hcare.integration.evv.EvvSubmissionRecordRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClaimValidationChainTest {

    private static final String VALID_NPI = "1234567893";
    private static final String INVALID_NPI = "1234567890";
    private static final UUID AGENCY_ID = UUID.randomUUID();
    private static final String PAYER_ID = "PAYER001";

    @Test
    void npiFormatHandler_validNpi_passesToNext() {
        NpiFormatHandler handler = new NpiFormatHandler();
        NpiFormatHandler spyNext = mock(NpiFormatHandler.class);
        handler.then(spyNext);

        Claim claim = buildClaim(VALID_NPI, LocalDate.now());
        assertThatCode(() -> handler.validate(claim)).doesNotThrowAnyException();
        verify(spyNext).validate(claim);
    }

    @Test
    void npiFormatHandler_invalidNpi_throwsClaimValidationException() {
        NpiFormatHandler handler = new NpiFormatHandler();
        Claim claim = buildClaim(INVALID_NPI, LocalDate.now());

        assertThatThrownBy(() -> handler.validate(claim))
                .isInstanceOf(ClaimValidationException.class);
    }

    @Test
    void timelyFilingHandler_recentServiceDate_noThrow_passesToNext() {
        TimelyFilingHandler handler = new TimelyFilingHandler();
        NpiFormatHandler spyNext = mock(NpiFormatHandler.class);
        handler.then(spyNext);

        Claim claim = buildClaim(VALID_NPI, LocalDate.now());
        assertThatCode(() -> handler.validate(claim)).doesNotThrowAnyException();
        verify(spyNext).validate(claim);
    }

    @Test
    void timelyFilingHandler_oldServiceDate_doesNotThrow_butPassesToNext() {
        TimelyFilingHandler handler = new TimelyFilingHandler();
        NpiFormatHandler spyNext = mock(NpiFormatHandler.class);
        handler.then(spyNext);

        Claim claim = buildClaim(VALID_NPI, LocalDate.now().minusDays(100));
        assertThatCode(() -> handler.validate(claim)).doesNotThrowAnyException();
        verify(spyNext).validate(claim);
    }

    @Test
    void evvLinkageHandler_acceptedRecord_passes() {
        EvvSubmissionRecordRepository repo = mock(EvvSubmissionRecordRepository.class);
        UUID evvRecordId = UUID.randomUUID();
        EvvSubmissionRecord record = mock(EvvSubmissionRecord.class);
        when(record.getStatus()).thenReturn("ACCEPTED");
        when(repo.findByEvvRecordId(evvRecordId)).thenReturn(Optional.of(record));

        EvvLinkageHandler handler = new EvvLinkageHandler(repo, evvRecordId);
        Claim claim = buildClaim(VALID_NPI, LocalDate.now());

        assertThatCode(() -> handler.validate(claim)).doesNotThrowAnyException();
    }

    @Test
    void evvLinkageHandler_noRecord_throwsClaimValidationException() {
        EvvSubmissionRecordRepository repo = mock(EvvSubmissionRecordRepository.class);
        UUID evvRecordId = UUID.randomUUID();
        when(repo.findByEvvRecordId(evvRecordId)).thenReturn(Optional.empty());

        EvvLinkageHandler handler = new EvvLinkageHandler(repo, evvRecordId);
        Claim claim = buildClaim(VALID_NPI, LocalDate.now());

        assertThatThrownBy(() -> handler.validate(claim))
                .isInstanceOf(ClaimValidationException.class);
    }

    @Test
    void evvLinkageHandler_nonAcceptedRecord_throwsClaimValidationException() {
        EvvSubmissionRecordRepository repo = mock(EvvSubmissionRecordRepository.class);
        UUID evvRecordId = UUID.randomUUID();
        EvvSubmissionRecord record = mock(EvvSubmissionRecord.class);
        when(record.getStatus()).thenReturn("PENDING");
        when(repo.findByEvvRecordId(evvRecordId)).thenReturn(Optional.of(record));

        EvvLinkageHandler handler = new EvvLinkageHandler(repo, evvRecordId);
        Claim claim = buildClaim(VALID_NPI, LocalDate.now());

        assertThatThrownBy(() -> handler.validate(claim))
                .isInstanceOf(ClaimValidationException.class);
    }

    @Test
    void fullChain_validNpiAcceptedEvv_passes() {
        EvvSubmissionRecordRepository repo = mock(EvvSubmissionRecordRepository.class);
        UUID evvRecordId = UUID.randomUUID();
        EvvSubmissionRecord record = mock(EvvSubmissionRecord.class);
        when(record.getStatus()).thenReturn("ACCEPTED");
        when(repo.findByEvvRecordId(evvRecordId)).thenReturn(Optional.of(record));

        NpiFormatHandler npi = new NpiFormatHandler();
        TimelyFilingHandler timely = new TimelyFilingHandler();
        EvvLinkageHandler evvLinkage = new EvvLinkageHandler(repo, evvRecordId);
        npi.then(timely).then(evvLinkage);

        Claim claim = buildClaim(VALID_NPI, LocalDate.now());
        assertThatCode(() -> npi.validate(claim)).doesNotThrowAnyException();
    }

    @Test
    void fullChain_invalidNpi_throwsAtNpiHandler_evvHandlerNotCalled() {
        EvvSubmissionRecordRepository repo = mock(EvvSubmissionRecordRepository.class);
        UUID evvRecordId = UUID.randomUUID();

        NpiFormatHandler npi = new NpiFormatHandler();
        TimelyFilingHandler timely = new TimelyFilingHandler();
        EvvLinkageHandler evvLinkage = new EvvLinkageHandler(repo, evvRecordId);
        npi.then(timely).then(evvLinkage);

        Claim claim = buildClaim(INVALID_NPI, LocalDate.now());
        assertThatThrownBy(() -> npi.validate(claim))
                .isInstanceOf(ClaimValidationException.class);
        verify(repo, never()).findByEvvRecordId(any());
    }

    private Claim buildClaim(String npi, LocalDate serviceDate) {
        return X12ClaimBuilder.institutional()
                .agencyId(AGENCY_ID)
                .payerId(PAYER_ID)
                .billingProvider(npi, "123456789")
                .serviceDate(serviceDate)
                .serviceCode("T1019")
                .units(BigDecimal.valueOf(4))
                .billedAmount(BigDecimal.valueOf(200))
                .build();
    }
}
