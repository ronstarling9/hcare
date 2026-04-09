package com.hcare.integration.payroll;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Factory that resolves a {@link PayrollExportStrategy} by export type string.
 *
 * <p>All {@link PayrollExportStrategy} beans are collected at startup and indexed by
 * {@link PayrollExportStrategy#exportType()}. If the requested type is not found, the factory
 * falls back to the {@code "CSV_EXPORT"} strategy. If {@code "CSV_EXPORT"} is also absent,
 * an {@link IllegalStateException} is thrown.
 */
@Component
public class PayrollStrategyFactory {

    private static final Logger log = LoggerFactory.getLogger(PayrollStrategyFactory.class);

    private static final String DEFAULT_STRATEGY = "CSV_EXPORT";

    private final Map<String, PayrollExportStrategy> strategies;

    public PayrollStrategyFactory(List<PayrollExportStrategy> strategyList) {
        this.strategies = strategyList.stream()
                .collect(Collectors.toMap(PayrollExportStrategy::exportType, s -> s));
    }

    @PostConstruct
    void validateStrategies() {
        if (strategies.isEmpty()) {
            log.warn(
                    "PayrollStrategyFactory initialized with no strategies"
                            + " — all strategyFor() calls will throw");
        } else {
            log.info(
                    "PayrollStrategyFactory initialized with {} strategies: {}",
                    strategies.size(),
                    strategies.keySet());
        }
    }

    /**
     * Returns the strategy for the given export type, falling back to {@code "CSV_EXPORT"} if the
     * requested type is not registered.
     *
     * @param exportType the export type identifier (e.g., {@code "VIVENTIUM"}, {@code "CSV_EXPORT"})
     * @return the matching or fallback strategy
     * @throws IllegalStateException if neither the requested type nor {@code "CSV_EXPORT"} is
     *                               registered
     */
    public PayrollExportStrategy strategyFor(String exportType) {
        PayrollExportStrategy strategy = strategies.get(exportType);
        if (strategy != null) {
            return strategy;
        }
        PayrollExportStrategy fallback = strategies.get(DEFAULT_STRATEGY);
        if (fallback != null) {
            log.warn(
                    "No PayrollExportStrategy registered for exportType '{}' — falling back to {}",
                    exportType,
                    DEFAULT_STRATEGY);
            return fallback;
        }
        throw new IllegalStateException("No payroll strategy for: " + exportType);
    }
}
