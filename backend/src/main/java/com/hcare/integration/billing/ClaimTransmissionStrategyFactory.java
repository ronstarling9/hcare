package com.hcare.integration.billing;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Factory that resolves a {@link ClaimTransmissionStrategy} by connector type string.
 *
 * <p>All {@link ClaimTransmissionStrategy} beans are collected at startup and indexed by
 * {@link ClaimTransmissionStrategy#connectorType()}.
 */
@Component
public class ClaimTransmissionStrategyFactory {

    private static final Logger log = LoggerFactory.getLogger(ClaimTransmissionStrategyFactory.class);

    private final Map<String, ClaimTransmissionStrategy> strategies;

    public ClaimTransmissionStrategyFactory(List<ClaimTransmissionStrategy> strategyList) {
        this.strategies = strategyList.stream()
                .collect(Collectors.toMap(ClaimTransmissionStrategy::connectorType, s -> s));
    }

    @PostConstruct
    void validateStrategies() {
        if (strategies.isEmpty()) {
            log.warn(
                    "ClaimTransmissionStrategyFactory initialized with no strategies"
                            + " — all strategyFor() calls will throw");
        } else {
            log.info(
                    "ClaimTransmissionStrategyFactory initialized with {} strategies: {}",
                    strategies.size(),
                    strategies.keySet());
        }
    }

    /**
     * Returns the strategy for the given connector type.
     *
     * @param connectorType connector type identifier (e.g., "STEDI", "OFFICE_ALLY")
     * @return the matching strategy
     * @throws IllegalArgumentException if no strategy is registered for the given type
     */
    public ClaimTransmissionStrategy strategyFor(String connectorType) {
        ClaimTransmissionStrategy strategy = strategies.get(connectorType);
        if (strategy == null) {
            throw new IllegalArgumentException(
                    "No ClaimTransmissionStrategy registered for connectorType: " + connectorType);
        }
        return strategy;
    }
}
