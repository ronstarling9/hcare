package com.hcare.integration.evv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hcare.evv.AggregatorType;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RetryingEvvSubmissionStrategyTest {

    private EvvSubmissionStrategy delegate;
    private RetryingEvvSubmissionStrategy strategy;

    @BeforeEach
    void setUp() {
        delegate = mock(EvvSubmissionStrategy.class);
        when(delegate.aggregatorType()).thenReturn(AggregatorType.SANDATA);
        strategy = new RetryingEvvSubmissionStrategy(delegate);
    }

    @Test
    void submit_success_firstAttempt_neverRetries() {
        when(delegate.submit(any(), any())).thenReturn(EvvSubmissionResult.ok("v1"));

        EvvSubmissionResult result = strategy.submit(ctx(), new Object());

        assertThat(result.success()).isTrue();
        assertThat(result.aggregatorVisitId()).isEqualTo("v1");
        verify(delegate, times(1)).submit(any(), any());
    }

    @Test
    void submit_terminalError_400_noRetry() {
        when(delegate.submit(any(), any()))
                .thenReturn(EvvSubmissionResult.failure("400", "client error"));

        EvvSubmissionResult result = strategy.submit(ctx(), new Object());

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("400");
        verify(delegate, times(1)).submit(any(), any());
    }

    @Test
    void submit_terminalError_NOT_IMPLEMENTED_noRetry() {
        when(delegate.submit(any(), any()))
                .thenReturn(EvvSubmissionResult.failure("NOT_IMPLEMENTED", "feature missing"));

        EvvSubmissionResult result = strategy.submit(ctx(), new Object());

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("NOT_IMPLEMENTED");
        verify(delegate, times(1)).submit(any(), any());
    }

    @Test
    void submit_secondAttemptSucceeds() {
        when(delegate.submit(any(), any()))
                .thenReturn(EvvSubmissionResult.failure("503", "server error"))
                .thenReturn(EvvSubmissionResult.ok("v2"));

        EvvSubmissionResult result = strategy.submit(ctx(), new Object());

        assertThat(result.success()).isTrue();
        assertThat(result.aggregatorVisitId()).isEqualTo("v2");
        verify(delegate, times(2)).submit(any(), any());
    }

    @Test
    void submit_nonTerminalExhaustion_returnsLastFailure() {
        // Note: this test will sleep ~6 seconds due to retry backoff delays — acceptable for CI.
        when(delegate.submit(any(), any()))
                .thenReturn(EvvSubmissionResult.failure("503", "server error"));

        EvvSubmissionResult result = strategy.submit(ctx(), new Object());

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("503");
        verify(delegate, times(3)).submit(any(), any());
    }

    private EvvSubmissionContext ctx() {
        return new EvvSubmissionContext(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
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
