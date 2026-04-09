package com.hcare.integration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("integration.connectors")
public record IntegrationConnectorProperties(
        ConnectorConfig sandata,
        ConnectorConfig hhaexchange,
        ConnectorConfig stedi,
        ConnectorConfig viventium
) {

    public record ConnectorConfig(
            String baseUrl,
            Duration connectTimeout,
            Duration readTimeout
    ) {}
}
