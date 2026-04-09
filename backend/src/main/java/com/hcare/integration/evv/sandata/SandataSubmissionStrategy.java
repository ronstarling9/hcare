package com.hcare.integration.evv.sandata;

import com.hcare.evv.AggregatorType;
import com.hcare.integration.evv.AbstractEvvSubmissionStrategy;
import com.hcare.integration.evv.EvvSubmissionContext;
import com.hcare.integration.evv.EvvSubmissionResult;
import com.hcare.integration.evv.exceptions.EvvValidationException;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * EVV submission strategy for the Sandata aggregator.
 *
 * <p>Uses REST + Basic Auth. isRealTime = true.
 *
 * <p>The {@code buildPayload} method returns a {@link SandataPayloadHolder} that bundles both the
 * request body and the credentials so that {@code doSubmit} can build the Basic Auth header without
 * re-receiving raw credentials (which are not passed to doSubmit by the template method contract).
 */
@Component
public class SandataSubmissionStrategy extends AbstractEvvSubmissionStrategy {

    private static final Logger log = LoggerFactory.getLogger(SandataSubmissionStrategy.class);

    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final RestClient sandataRestClient;

    public SandataSubmissionStrategy(
            @Qualifier("sandataRestClient") @Nullable RestClient sandataRestClient) {
        this.sandataRestClient = sandataRestClient;
    }

    @Override
    public AggregatorType aggregatorType() {
        return AggregatorType.SANDATA;
    }

    @Override
    public boolean isRealTime() {
        return true;
    }

    @Override
    public Class<?> credentialClass() {
        return SandataCredentials.class;
    }

    @Override
    protected void validate(EvvSubmissionContext ctx) {
        if (ctx.caregiverNpi() == null || ctx.caregiverNpi().isBlank()) {
            throw new EvvValidationException("caregiverNpi is required for Sandata submission");
        }
        if (ctx.clientMedicaidId() == null || ctx.clientMedicaidId().isBlank()) {
            throw new EvvValidationException("clientMedicaidId is required for Sandata submission");
        }
    }

    /**
     * Returns a {@link SandataPayloadHolder} bundling the request POJO and credentials so that
     * {@code doSubmit} can access the Basic Auth values without breaking the template contract.
     */
    @Override
    protected Object buildPayload(EvvSubmissionContext ctx, Object typedCreds) {
        SandataCredentials creds = (SandataCredentials) typedCreds;
        SandataVisitRequest req = buildRequest(ctx, creds);
        return new SandataPayloadHolder(req, creds);
    }

    @Override
    protected EvvSubmissionResult doSubmit(EvvSubmissionContext ctx, Object payload) {
        if (sandataRestClient == null) {
            log.warn("Sandata REST client not configured for agency={}", ctx.agencyId());
            return EvvSubmissionResult.failure("CONNECTOR_UNAVAILABLE", "Sandata connector not configured");
        }
        SandataPayloadHolder holder = (SandataPayloadHolder) payload;
        String authHeader = buildBasicAuth(holder.credentials().username(), holder.credentials().password());

        try {
            SandataVisitResponse response = sandataRestClient.post()
                    .uri("/visits")
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .body(holder.request())
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req2, resp) -> {
                        throw new RuntimeException(resp.getStatusCode().value()
                                + ": Sandata client error");
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req2, resp) -> {
                        throw new RuntimeException(resp.getStatusCode().value()
                                + ": server error — retryable");
                    })
                    .body(SandataVisitResponse.class);

            if (response == null) {
                return EvvSubmissionResult.failure("NULL_RESPONSE", "Sandata returned null response");
            }
            if ("SUCCESS".equalsIgnoreCase(response.getStatus())) {
                return EvvSubmissionResult.ok(response.getVisitId());
            }
            return EvvSubmissionResult.failure(response.getErrorCode(), response.getErrorMessage());
        } catch (RuntimeException ex) {
            String msg = ex.getMessage();
            if (msg != null && msg.matches("^4\\d{2}:.*")) {
                return EvvSubmissionResult.failure(msg.substring(0, 3), msg);
            }
            log.error("Sandata submission error for evvRecordId={}: {}", ctx.evvRecordId(), msg);
            return EvvSubmissionResult.failure("SANDATA_ERROR", msg);
        }
    }

    @Override
    protected EvvSubmissionResult doUpdate(EvvSubmissionContext ctx, Object typedCreds) {
        if (sandataRestClient == null) {
            return EvvSubmissionResult.failure("CONNECTOR_UNAVAILABLE", "Sandata connector not configured");
        }
        SandataCredentials creds = (SandataCredentials) typedCreds;
        if (ctx.aggregatorVisitId() == null) {
            return EvvSubmissionResult.failure("MISSING_AGGREGATOR_ID",
                    "Cannot update/void — no aggregatorVisitId recorded for this visit");
        }
        String authHeader = buildBasicAuth(creds.username(), creds.password());
        SandataVisitRequest req = buildRequest(ctx, creds);
        String aggregatorVisitId = ctx.aggregatorVisitId();

        try {
            SandataVisitResponse response = sandataRestClient.put()
                    .uri("/visits/{id}", aggregatorVisitId)
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .body(req)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (request, resp) -> {
                        throw new RuntimeException(resp.getStatusCode().value()
                                + ": Sandata client error on update");
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (request, resp) -> {
                        throw new RuntimeException(resp.getStatusCode().value()
                                + ": server error — retryable");
                    })
                    .body(SandataVisitResponse.class);

            if (response == null) {
                return EvvSubmissionResult.failure("NULL_RESPONSE", "Sandata returned null response on update");
            }
            if ("SUCCESS".equalsIgnoreCase(response.getStatus())) {
                return EvvSubmissionResult.ok(response.getVisitId());
            }
            return EvvSubmissionResult.failure(response.getErrorCode(), response.getErrorMessage());
        } catch (RuntimeException ex) {
            String msg = ex.getMessage();
            if (msg != null && msg.matches("^4\\d{2}:.*")) {
                return EvvSubmissionResult.failure(msg.substring(0, 3), msg);
            }
            log.error("Sandata update error for evvRecordId={}: {}", ctx.evvRecordId(), msg);
            return EvvSubmissionResult.failure("SANDATA_UPDATE_ERROR", msg);
        }
    }

    @Override
    protected EvvSubmissionResult doVoid_(EvvSubmissionContext ctx, Object typedCreds) {
        if (sandataRestClient == null) {
            return EvvSubmissionResult.failure("CONNECTOR_UNAVAILABLE", "Sandata connector not configured");
        }
        SandataCredentials creds = (SandataCredentials) typedCreds;
        if (ctx.aggregatorVisitId() == null) {
            return EvvSubmissionResult.failure("MISSING_AGGREGATOR_ID",
                    "Cannot update/void — no aggregatorVisitId recorded for this visit");
        }
        String authHeader = buildBasicAuth(creds.username(), creds.password());
        String aggregatorVisitId = ctx.aggregatorVisitId();

        try {
            sandataRestClient.post()
                    .uri("/visits/{id}/void", aggregatorVisitId)
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (request, resp) -> {
                        throw new RuntimeException(resp.getStatusCode().value()
                                + ": Sandata client error on void");
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (request, resp) -> {
                        throw new RuntimeException(resp.getStatusCode().value()
                                + ": server error — retryable");
                    })
                    .toBodilessEntity();

            return EvvSubmissionResult.ok(aggregatorVisitId);
        } catch (RuntimeException ex) {
            String msg = ex.getMessage();
            if (msg != null && msg.matches("^4\\d{2}:.*")) {
                return EvvSubmissionResult.failure(msg.substring(0, 3), msg);
            }
            log.error("Sandata void error for evvRecordId={}: {}", ctx.evvRecordId(), msg);
            return EvvSubmissionResult.failure("SANDATA_VOID_ERROR", msg);
        }
    }

    private String buildBasicAuth(String username, String password) {
        String credentials = username + ":" + password;
        return "Basic " + Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private SandataVisitRequest buildRequest(EvvSubmissionContext ctx, SandataCredentials creds) {
        SandataVisitRequest req = new SandataVisitRequest();
        req.setVisitId(ctx.evvRecordId().toString());
        req.setMemberId(ctx.clientMedicaidId());
        req.setProviderId(creds.payerId());
        req.setCaregiverId(ctx.caregiverId().toString());
        req.setServiceCode(ctx.serviceCode());
        req.setTimeIn(ctx.timeIn().format(ISO_FMT));
        req.setTimeOut(ctx.timeOut().format(ISO_FMT));
        req.setStateCode(ctx.stateCode());
        return req;
    }

    private record SandataPayloadHolder(SandataVisitRequest request, SandataCredentials credentials) {}
}
