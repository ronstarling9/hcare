package com.hcare.integration.billing.officeally;

import com.hcare.integration.billing.AgencyBillingCredentials;
import com.hcare.integration.billing.AbstractClaimTransmissionStrategy;
import com.hcare.integration.billing.Claim;
import com.hcare.integration.billing.ClaimSubmissionReceipt;
import com.hcare.integration.billing.RemittanceResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Stub claim transmission strategy for the Office Ally connector.
 *
 * <p>The full Office Ally integration is deferred (C10). This component always returns a failure
 * receipt rather than throwing, allowing the system to degrade gracefully when Office Ally is
 * configured but not yet wired.
 */
@Component
public class OfficeAllyTransmissionStrategy extends AbstractClaimTransmissionStrategy {

    private static final Logger log = LoggerFactory.getLogger(OfficeAllyTransmissionStrategy.class);

    @Override
    public String connectorType() {
        return "OFFICE_ALLY";
    }

    @Override
    protected ClaimSubmissionReceipt doSubmit(Claim claim, AgencyBillingCredentials creds) {
        log.warn("OfficeAlly billing connector not implemented");
        return ClaimSubmissionReceipt.failure(
                "NOT_IMPLEMENTED", "OfficeAlly connector not yet implemented");
    }

    @Override
    public RemittanceResult fetchRemittance(String batchId, AgencyBillingCredentials creds) {
        return RemittanceResult.notReady();
    }
}
