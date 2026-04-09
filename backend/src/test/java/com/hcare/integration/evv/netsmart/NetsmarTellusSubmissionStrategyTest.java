package com.hcare.integration.evv.netsmart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.hcare.evv.AggregatorType;
import com.hcare.integration.SftpGateway;
import com.hcare.integration.evv.EvvSubmissionContext;
import com.hcare.integration.evv.EvvSubmissionResult;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NetsmarTellusSubmissionStrategyTest {

    private SftpGateway mockSftpGateway;
    private NetsmarTellusSubmissionStrategy strategy;

    @BeforeEach
    void setUp() {
        mockSftpGateway = mock(SftpGateway.class);
        strategy = new NetsmarTellusSubmissionStrategy(mockSftpGateway);
    }

    @Test
    void submit_success_returnsFilenameAsAggregatorVisitId() {
        EvvSubmissionResult result = strategy.submit(ctx(), creds());

        assertThat(result.success()).isTrue();
        assertThat(result.aggregatorVisitId()).startsWith("evv_");
        assertThat(result.aggregatorVisitId()).endsWith(".dat");
    }

    @Test
    void submit_pipeDelimitedRowContainsCorrectFields() throws Exception {
        ArgumentCaptor<byte[]> contentCaptor = forClass(byte[].class);

        strategy.submit(ctx(), creds());

        verify(mockSftpGateway).upload(
                eq(creds().sftpHost()),
                eq(creds().port()),
                eq(creds().username()),
                eq(creds().privateKeyRef()),
                any(String.class),
                contentCaptor.capture());

        String row = new String(contentCaptor.getValue(), StandardCharsets.UTF_8);
        String[] fields = row.split("\\|");
        assertThat(fields[0]).isEqualTo(creds().sourceId());
        assertThat(fields[1]).isEqualTo(ctx().clientMedicaidId());
        assertThat(fields[2]).isEqualTo(ctx().evvRecordId().toString());
        assertThat(fields[3]).isEqualTo(ctx().serviceCode());
        assertThat(fields[6]).isEqualTo(ctx().caregiverNpi());
    }

    @Test
    void submit_sftpException_returnsFailure() throws Exception {
        doThrow(new RuntimeException("SFTP down")).when(mockSftpGateway)
                .upload(any(), any(int.class), any(), any(), any(), any());

        EvvSubmissionResult result = strategy.submit(ctx(), creds());

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("NETSMART_SFTP_ERROR");
    }

    private EvvSubmissionContext ctx() {
        return new EvvSubmissionContext(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"),
                UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"),
                UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"),
                AggregatorType.NETSMART,
                "WI",
                "1234567893",
                "MEDICAID789",
                "T1020",
                LocalDateTime.of(2026, 3, 1, 7, 0),
                LocalDateTime.of(2026, 3, 1, 11, 0),
                "WI",
                null);
    }

    private NetsmarCredentials creds() {
        return new NetsmarCredentials("nuser", "sftp.example.com", 22, "src-01", "/keys/id_rsa");
    }
}
