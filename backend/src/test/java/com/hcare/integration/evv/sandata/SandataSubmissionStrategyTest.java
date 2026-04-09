package com.hcare.integration.evv.sandata;

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

class SandataSubmissionStrategyTest {

    private MockWebServer server;
    private SandataSubmissionStrategy strategy;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        RestClient restClient = RestClient.builder()
                .baseUrl(server.url("/").toString())
                .build();
        strategy = new SandataSubmissionStrategy(restClient);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void submit_success_returnsOkWithVisitId() throws Exception {
        SandataVisitResponse resp = new SandataVisitResponse();
        resp.setStatus("SUCCESS");
        resp.setVisitId("sandata-v1");
        server.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(resp))
                .addHeader("Content-Type", "application/json"));

        EvvSubmissionResult result = strategy.submit(ctx(), creds());

        assertThat(result.success()).isTrue();
        assertThat(result.aggregatorVisitId()).isEqualTo("sandata-v1");
    }

    @Test
    void submit_4xxTerminal_returnsFailureImmediately() {
        server.enqueue(new MockResponse().setResponseCode(400).setBody("bad request"));

        EvvSubmissionResult result = strategy.submit(ctx(), creds());

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("400");
    }

    @Test
    void submit_nullRestClient_returnsConnectorUnavailable() {
        SandataSubmissionStrategy noClient = new SandataSubmissionStrategy(null);

        EvvSubmissionResult result = noClient.submit(ctx(), creds());

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("CONNECTOR_UNAVAILABLE");
    }

    @Test
    void submit_verifiesBasicAuthHeader() throws Exception {
        SandataVisitResponse resp = new SandataVisitResponse();
        resp.setStatus("SUCCESS");
        resp.setVisitId("sandata-v2");
        server.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(resp))
                .addHeader("Content-Type", "application/json"));

        strategy.submit(ctx(), creds());

        RecordedRequest req = server.takeRequest();
        assertThat(req.getHeader("Authorization")).startsWith("Basic ");
    }

    @Test
    void submit_verifiesPayloadMapping() throws Exception {
        SandataVisitResponse resp = new SandataVisitResponse();
        resp.setStatus("SUCCESS");
        resp.setVisitId("sandata-v3");
        server.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(resp))
                .addHeader("Content-Type", "application/json"));

        EvvSubmissionContext context = ctx();
        strategy.submit(context, creds());

        RecordedRequest req = server.takeRequest();
        String body = req.getBody().readUtf8();
        assertThat(body).contains(context.caregiverId().toString());
        assertThat(body).contains(context.serviceCode());
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

    private SandataCredentials creds() {
        return new SandataCredentials("user1", "pass1", "payer123");
    }
}
