package com.hcare.integration.evv.hhaexchange;

import com.hcare.evv.AggregatorType;
import com.hcare.integration.evv.AbstractEvvSubmissionStrategy;
import com.hcare.integration.evv.EvvSubmissionContext;
import com.hcare.integration.evv.EvvSubmissionResult;
import com.hcare.integration.evv.exceptions.EvvValidationException;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * EVV submission strategy for the HHA Exchange (HHAx) aggregator.
 *
 * <p>Uses REST with three App-key headers: {@code X-App-Name}, {@code X-App-Secret},
 * {@code X-App-Key}. isRealTime = true.
 *
 * <p>The {@code evvmsid} field from {@link HhaxVisitResponse} is HHAx's internal visit identifier
 * and is stored as the {@code aggregatorVisitId} for use in subsequent void/update calls.
 */
@Component
public class HhaExchangeSubmissionStrategy extends AbstractEvvSubmissionStrategy {

    private static final Logger log = LoggerFactory.getLogger(HhaExchangeSubmissionStrategy.class);

    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private static final String HEADER_APP_NAME = "X-App-Name";
    private static final String HEADER_APP_SECRET = "X-App-Secret";
    private static final String HEADER_APP_KEY = "X-App-Key";

    private final RestClient hhaxRestClient;

    @Autowired
    public HhaExchangeSubmissionStrategy(
            @Qualifier("hhaxRestClient") @Autowired(required = false) RestClient hhaxRestClient) {
        this.hhaxRestClient = hhaxRestClient;
    }

    @Override
    public AggregatorType aggregatorType() {
        return AggregatorType.HHAEXCHANGE;
    }

    @Override
    public boolean isRealTime() {
        return true;
    }

    @Override
    public Class<?> credentialClass() {
        return HhaxCredentials.class;
    }

    @Override
    protected void validate(EvvSubmissionContext ctx) {
        if (ctx.caregiverNpi() == null || ctx.caregiverNpi().isBlank()) {
            throw new EvvValidationException("caregiverNpi is required for HHA Exchange submission");
        }
        if (ctx.clientMedicaidId() == null || ctx.clientMedicaidId().isBlank()) {
            throw new EvvValidationException("clientMedicaidId is required for HHA Exchange submission");
        }
    }

    /**
     * Returns a {@link HhaxPayloadHolder} so {@code doSubmit} can access credentials for header
     * construction alongside the visit request body.
     */
    @Override
    protected Object buildPayload(EvvSubmissionContext ctx, Object typedCreds) {
        HhaxCredentials creds = (HhaxCredentials) typedCreds;
        HhaxVisitRequest req = buildRequest(ctx);
        return new HhaxPayloadHolder(req, creds);
    }

    @Override
    protected EvvSubmissionResult doSubmit(EvvSubmissionContext ctx, Object payload) {
        if (hhaxRestClient == null) {
            log.warn("HHAx REST client not configured for agency={}", ctx.agencyId());
            return EvvSubmissionResult.failure("CONNECTOR_UNAVAILABLE", "HHA Exchange connector not configured");
        }
        HhaxPayloadHolder holder = (HhaxPayloadHolder) payload;
        HhaxCredentials creds = holder.credentials();

        try {
            HhaxVisitResponse response = hhaxRestClient.post()
                    .uri("/visits")
                    .header(HEADER_APP_NAME, creds.appName())
                    .header(HEADER_APP_SECRET, creds.appSecret())
                    .header(HEADER_APP_KEY, creds.appKey())
                    .body(holder.request())
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req2, resp) -> {
                        throw new RuntimeException(resp.getStatusCode().value()
                                + ": HHAx client error");
                    })
                    .body(HhaxVisitResponse.class);

            if (response == null) {
                return EvvSubmissionResult.failure("NULL_RESPONSE", "HHAx returned null response");
            }
            if ("SUCCESS".equalsIgnoreCase(response.getStatus())) {
                // Prefer evvmsid as the aggregatorVisitId; fall back to visitId
                String aggregatorVisitId = response.getEvvmsid() != null
                        ? response.getEvvmsid()
                        : response.getVisitId();
                return EvvSubmissionResult.ok(aggregatorVisitId);
            }
            return EvvSubmissionResult.failure(response.getErrorCode(), response.getErrorMessage());
        } catch (RuntimeException ex) {
            String msg = ex.getMessage();
            if (msg != null && msg.matches("^4\\d{2}:.*")) {
                return EvvSubmissionResult.failure(msg.substring(0, 3), msg);
            }
            log.error("HHAx submission error for evvRecordId={}: {}", ctx.evvRecordId(), msg);
            return EvvSubmissionResult.failure("HHAX_ERROR", msg);
        }
    }

    @Override
    protected EvvSubmissionResult doUpdate(EvvSubmissionContext ctx, Object typedCreds) {
        if (hhaxRestClient == null) {
            return EvvSubmissionResult.failure("CONNECTOR_UNAVAILABLE", "HHA Exchange connector not configured");
        }
        HhaxCredentials creds = (HhaxCredentials) typedCreds;
        HhaxVisitRequest req = buildRequest(ctx);
        String aggregatorVisitId = ctx.evvRecordId().toString();

        try {
            HhaxVisitResponse response = hhaxRestClient.put()
                    .uri("/visits/{id}", aggregatorVisitId)
                    .header(HEADER_APP_NAME, creds.appName())
                    .header(HEADER_APP_SECRET, creds.appSecret())
                    .header(HEADER_APP_KEY, creds.appKey())
                    .body(req)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (request, resp) -> {
                        throw new RuntimeException(resp.getStatusCode().value()
                                + ": HHAx client error on update");
                    })
                    .body(HhaxVisitResponse.class);

            if (response == null) {
                return EvvSubmissionResult.failure("NULL_RESPONSE", "HHAx returned null response on update");
            }
            if ("SUCCESS".equalsIgnoreCase(response.getStatus())) {
                String returnedId = response.getEvvmsid() != null
                        ? response.getEvvmsid()
                        : response.getVisitId();
                return EvvSubmissionResult.ok(returnedId);
            }
            return EvvSubmissionResult.failure(response.getErrorCode(), response.getErrorMessage());
        } catch (RuntimeException ex) {
            String msg = ex.getMessage();
            if (msg != null && msg.matches("^4\\d{2}:.*")) {
                return EvvSubmissionResult.failure(msg.substring(0, 3), msg);
            }
            log.error("HHAx update error for evvRecordId={}: {}", ctx.evvRecordId(), msg);
            return EvvSubmissionResult.failure("HHAX_UPDATE_ERROR", msg);
        }
    }

    @Override
    protected EvvSubmissionResult doVoid_(EvvSubmissionContext ctx, Object typedCreds) {
        if (hhaxRestClient == null) {
            return EvvSubmissionResult.failure("CONNECTOR_UNAVAILABLE", "HHA Exchange connector not configured");
        }
        HhaxCredentials creds = (HhaxCredentials) typedCreds;
        String aggregatorVisitId = ctx.evvRecordId().toString();

        try {
            hhaxRestClient.post()
                    .uri("/visits/{id}/void", aggregatorVisitId)
                    .header(HEADER_APP_NAME, creds.appName())
                    .header(HEADER_APP_SECRET, creds.appSecret())
                    .header(HEADER_APP_KEY, creds.appKey())
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (request, resp) -> {
                        throw new RuntimeException(resp.getStatusCode().value()
                                + ": HHAx client error on void");
                    })
                    .toBodilessEntity();

            return EvvSubmissionResult.ok(aggregatorVisitId);
        } catch (RuntimeException ex) {
            String msg = ex.getMessage();
            if (msg != null && msg.matches("^4\\d{2}:.*")) {
                return EvvSubmissionResult.failure(msg.substring(0, 3), msg);
            }
            log.error("HHAx void error for evvRecordId={}: {}", ctx.evvRecordId(), msg);
            return EvvSubmissionResult.failure("HHAX_VOID_ERROR", msg);
        }
    }

    private HhaxVisitRequest buildRequest(EvvSubmissionContext ctx) {
        HhaxVisitRequest req = new HhaxVisitRequest();
        req.setVisitId(ctx.evvRecordId().toString());
        req.setMemberId(ctx.clientMedicaidId());
        req.setCaregiverId(ctx.caregiverId().toString());
        req.setServiceCode(ctx.serviceCode());
        req.setTimeIn(ctx.timeIn().format(ISO_FMT));
        req.setTimeOut(ctx.timeOut().format(ISO_FMT));
        req.setStateCode(ctx.stateCode());
        return req;
    }

    private record HhaxPayloadHolder(HhaxVisitRequest request, HhaxCredentials credentials) {}
}
