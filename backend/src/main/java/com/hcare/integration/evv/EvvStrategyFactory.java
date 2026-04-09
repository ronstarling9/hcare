package com.hcare.integration.evv;

import com.hcare.evv.AggregatorType;
import com.hcare.integration.audit.IntegrationAuditWriter;
import com.hcare.integration.evv.exceptions.UnsupportedAggregatorException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class EvvStrategyFactory {

    private final Map<AggregatorType, EvvSubmissionStrategy> strategies;

    public EvvStrategyFactory(List<EvvSubmissionStrategy> strategyList,
                               IntegrationAuditWriter auditWriter) {
        this.strategies = strategyList.stream()
                .collect(Collectors.toMap(
                        EvvSubmissionStrategy::aggregatorType,
                        s -> new AuditingEvvSubmissionStrategy(
                                new RetryingEvvSubmissionStrategy(s),
                                auditWriter)));
    }

    public EvvSubmissionStrategy strategyFor(AggregatorType type) {
        EvvSubmissionStrategy strategy = strategies.get(type);
        if (strategy == null) {
            throw new UnsupportedAggregatorException("No strategy for aggregator type: " + type);
        }
        return strategy;
    }
}
