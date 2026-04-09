package com.hcare.integration.evv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hcare.evv.AggregatorType;
import com.hcare.integration.audit.IntegrationAuditWriter;
import com.hcare.integration.evv.exceptions.UnsupportedAggregatorException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EvvStrategyFactoryTest {

    @Test
    void strategyFor_knownType_returnsAuditingWrapper() {
        EvvSubmissionStrategy mockStrategy = mockStrategy(AggregatorType.SANDATA);
        IntegrationAuditWriter auditWriter = mock(IntegrationAuditWriter.class);
        EvvStrategyFactory factory = new EvvStrategyFactory(List.of(mockStrategy), auditWriter);

        EvvSubmissionStrategy result = factory.strategyFor(AggregatorType.SANDATA);

        assertThat(result).isInstanceOf(AuditingEvvSubmissionStrategy.class);
    }

    @Test
    void strategyFor_unknownType_throwsUnsupportedAggregatorException() {
        EvvSubmissionStrategy mockStrategy = mockStrategy(AggregatorType.SANDATA);
        IntegrationAuditWriter auditWriter = mock(IntegrationAuditWriter.class);
        EvvStrategyFactory factory = new EvvStrategyFactory(List.of(mockStrategy), auditWriter);

        assertThatThrownBy(() -> factory.strategyFor(AggregatorType.NETSMART))
                .isInstanceOf(UnsupportedAggregatorException.class);
    }

    @Test
    void decoratorOrder_auditWriterIsCalled_provingAuditingWrapsDelegate() {
        EvvSubmissionStrategy mockStrategy = mockStrategy(AggregatorType.SANDATA);
        when(mockStrategy.submit(any(), any())).thenReturn(EvvSubmissionResult.ok("v99"));
        IntegrationAuditWriter auditWriter = mock(IntegrationAuditWriter.class);
        EvvStrategyFactory factory = new EvvStrategyFactory(List.of(mockStrategy), auditWriter);

        EvvSubmissionStrategy wrapped = factory.strategyFor(AggregatorType.SANDATA);
        wrapped.submit(ctx(), new Object());

        // AuditingEvvSubmissionStrategy is the outer wrapper — it calls auditWriter.record()
        verify(auditWriter).record(any(), any(), any(), any(), any(boolean.class), any(long.class), any());
        // The delegate (inner-most) is also called
        verify(mockStrategy).submit(any(), any());
    }

    @SuppressWarnings("unchecked")
    private EvvSubmissionStrategy mockStrategy(AggregatorType type) {
        EvvSubmissionStrategy s = mock(EvvSubmissionStrategy.class);
        when(s.aggregatorType()).thenReturn(type);
        when(s.isRealTime()).thenReturn(true);
        when(s.credentialClass()).thenAnswer(inv -> Object.class);
        return s;
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
