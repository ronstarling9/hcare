package com.hcare.integration.billing.stedi;

import com.hcare.integration.billing.AgencyBillingCredentials;
import com.hcare.integration.billing.AbstractClaimTransmissionStrategy;
import com.hcare.integration.billing.Claim;
import com.hcare.integration.billing.ClaimSubmissionReceipt;
import com.hcare.integration.billing.RemittanceResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

/**
 * Claim transmission strategy for the Stedi Healthcare API.
 *
 * <p>When the {@code stediRestClient} bean is not configured (no base-url property set), all
 * operations return safe failure/not-ready results rather than throwing.
 */
@Component
public class StediTransmissionStrategy extends AbstractClaimTransmissionStrategy {

    private static final Logger log = LoggerFactory.getLogger(StediTransmissionStrategy.class);

    private final RestClient stediRestClient;
    private final StediClaimAdapter claimAdapter;

    @Autowired
    public StediTransmissionStrategy(
            @Qualifier("stediRestClient") @Nullable RestClient stediRestClient) {
        this.stediRestClient = stediRestClient;
        this.claimAdapter = new StediClaimAdapter();
    }

    @Override
    public String connectorType() {
        return "STEDI";
    }

    @Override
    protected ClaimSubmissionReceipt doSubmit(Claim claim, AgencyBillingCredentials creds) {
        if (stediRestClient == null) {
            log.warn("Stedi REST client not configured — cannot submit claim for agency {}",
                    claim.agencyId());
            return ClaimSubmissionReceipt.failure("CONNECTOR_UNAVAILABLE", "Stedi not configured");
        }

        Map<String, Object> payload = claimAdapter.toStediPayload(claim);
        log.debug("Submitting {} claim to Stedi for agency {}", claim.claimType(), claim.agencyId());

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = stediRestClient.post()
                    .uri("/v1/claims")
                    .header("Authorization", "Bearer " + creds.apiKey())
                    .body(payload)
                    .retrieve()
                    .body(Map.class);

            if (response != null && response.containsKey("batchId")) {
                String batchId = String.valueOf(response.get("batchId"));
                String controlNumber = response.containsKey("controlNumber")
                        ? String.valueOf(response.get("controlNumber"))
                        : UUID.randomUUID().toString();
                return ClaimSubmissionReceipt.success(batchId, controlNumber);
            }
            return ClaimSubmissionReceipt.failure("UNEXPECTED_RESPONSE",
                    "Stedi returned no batchId in response");
        } catch (Exception e) {
            log.error("Stedi claim submission failed for agency {}: {}",
                    claim.agencyId(), e.getMessage(), e);
            return ClaimSubmissionReceipt.failure("SUBMISSION_ERROR", e.getMessage());
        }
    }

    @Override
    public RemittanceResult fetchRemittance(String batchId, AgencyBillingCredentials creds) {
        if (stediRestClient == null) {
            return RemittanceResult.notReady();
        }
        // Stub until webhook wiring — remittance delivery is async via 835 webhook
        log.debug("Fetching remittance for batchId {} — stub pending webhook integration", batchId);
        return RemittanceResult.notReady();
    }
}
