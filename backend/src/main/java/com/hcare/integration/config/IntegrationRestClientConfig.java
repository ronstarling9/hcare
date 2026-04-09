package com.hcare.integration.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(IntegrationConnectorProperties.class)
public class IntegrationRestClientConfig {

    private final IntegrationConnectorProperties props;

    public IntegrationRestClientConfig(IntegrationConnectorProperties props) {
        this.props = props;
    }

    @Bean("sandataRestClient")
    @ConditionalOnProperty(prefix = "integration.connectors.sandata", name = "base-url")
    public RestClient sandataRestClient() {
        return buildClient(props.sandata());
    }

    @Bean("hhaxRestClient")
    @ConditionalOnProperty(prefix = "integration.connectors.hhaexchange", name = "base-url")
    public RestClient hhaxRestClient() {
        return buildClient(props.hhaexchange());
    }

    @Bean("stediRestClient")
    @ConditionalOnProperty(prefix = "integration.connectors.stedi", name = "base-url")
    public RestClient stediRestClient() {
        return buildClient(props.stedi());
    }

    @Bean("viventiumRestClient")
    @ConditionalOnProperty(prefix = "integration.connectors.viventium", name = "base-url")
    public RestClient viventiumRestClient() {
        return buildClient(props.viventium());
    }

    private RestClient buildClient(IntegrationConnectorProperties.ConnectorConfig cfg) {
        if (cfg == null || cfg.baseUrl() == null) {
            return RestClient.builder().build();
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        if (cfg.connectTimeout() != null) {
            factory.setConnectTimeout((int) cfg.connectTimeout().toMillis());
        }
        if (cfg.readTimeout() != null) {
            factory.setReadTimeout((int) cfg.readTimeout().toMillis());
        }
        return RestClient.builder()
                .baseUrl(cfg.baseUrl())
                .requestFactory(factory)
                .build();
    }
}
