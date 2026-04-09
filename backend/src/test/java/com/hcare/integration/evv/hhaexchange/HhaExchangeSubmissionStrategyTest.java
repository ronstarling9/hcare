package com.hcare.integration.evv.hhaexchange;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hcare.evv.AggregatorType;
import com.hcare.integration.evv.EvvSubmissionContext;
import com.hcare.integration.evv.EvvSubmissionResult;
import java.time.LocalDateTime;
import java.util.UUID;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class HhaExchangeSubmissionStrategyTest {

    private MockWebServer server;
    private HhaExchangeSubmissionStrategy strategy;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        RestClient restClient = RestClient.builder()
                .baseUrl(server.url("/").toString())
                .build();
        strategy = new HhaExchangeSubmissionStrategy(restClient);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void submit_success_prefersEvvmsidAsAggregatorId() throws Exception {
        HhaxVisitResponse resp = new HhaxVisitResponse();
        resp.setStatus("SUCCESS");
        resp.setEvvmsid("hhax-evvms-1");
        resp.setVisitId("hhax-v1");
        server.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(resp))
                .addHeader("Content-Type", "application/json"));

        EvvSubmissionResult result = strategy.submit(ctx(), creds());

        assertThat(result.success()).isTrue();
        assertThat(result.aggregatorVisitId()).isEqualTo("hhax-evvms-1");
    }

    @Test
    void submit_success_fallsBackToVisitId_whenEvvmsidNull() throws Exception {
        HhaxVisitResponse resp = new HhaxVisitResponse();
        resp.setStatus("SUCCESS");
        resp.setVisitId("hhax-v2");
        server.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(resp))
                .addHeader("Content-Type", "application/json"));

        EvvSubmissionResult result = strategy.submit(ctx(), creds());

        assertThat(result.success()).isTrue();
        assertThat(result.aggregatorVisitId()).isEqualTo("hhax-v2");
    }

    @Test
    void submit_verifiesTripleHeaders() throws Exception {
        HhaxVisitResponse resp = new HhaxVisitResponse();
        resp.setStatus("SUCCESS");
        resp.setVisitId("hhax-v3");
        server.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(resp))
                .addHeader("Content-Type", "application/json"));

        strategy.submit(ctx(), creds());

        RecordedRequest req = server.takeRequest();
        assertThat(req.getHeader("X-App-Name")).isEqualTo("myApp");
        assertThat(req.getHeader("X-App-Secret")).isEqualTo("mySecret");
        assertThat(req.getHeader("X-App-Key")).isEqualTo("myKey");
    }

    @Test
    void submit_nullRestClient_returnsConnectorUnavailable() {
        HhaExchangeSubmissionStrategy noClient = new HhaExchangeSubmissionStrategy(null);

        EvvSubmissionResult result = noClient.submit(ctx(), creds());

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("CONNECTOR_UNAVAILABLE");
    }

    private EvvSubmissionContext ctx() {
        return new EvvSubmissionContext(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"),
                UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"),
                UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"),
                AggregatorType.HHAEXCHANGE,
                "NY",
                "1234567893",
                "MEDICAID456",
                "S5125",
                LocalDateTime.of(2026, 2, 5, 9, 0),
                LocalDateTime.of(2026, 2, 5, 13, 0),
                "NY",
                null);
    }

    private HhaxCredentials creds() {
        return new HhaxCredentials("myApp", "mySecret", "myKey");
    }
}
