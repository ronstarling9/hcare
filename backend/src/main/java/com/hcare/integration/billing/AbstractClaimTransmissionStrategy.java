package com.hcare.integration.billing;

import com.hcare.integration.billing.validation.ClaimValidationHandler;

/**
 * Template Method base for claim transmission strategies.
 *
 * <p>{@link #submit(Claim, AgencyBillingCredentials)} is {@code final} and enforces the
 * validate → doSubmit sequence. Subclasses implement {@link #doSubmit} and
 * {@link #fetchRemittance}. Validation is performed by the chain attached via
 * {@link #validationChain()}, which defaults to {@code null} (no chain). Override
 * {@link #validationChain()} to provide a configured chain.
 */
public abstract class AbstractClaimTransmissionStrategy implements ClaimTransmissionStrategy {

    @Override
    public final ClaimSubmissionReceipt submit(Claim claim, AgencyBillingCredentials creds) {
        runValidationChain(claim);
        return doSubmit(claim, creds);
    }

    /**
     * Executes the validation chain if one is configured. The default implementation is a no-op;
     * subclasses may override {@link #validationChain()} to provide a handler.
     */
    protected void runValidationChain(Claim claim) {
        ClaimValidationHandler chain = validationChain();
        if (chain != null) {
            chain.validate(claim);
        }
    }

    /**
     * Returns the head of the validation chain to execute before submission.
     * Default returns {@code null} (no validation). Override to provide a chain.
     */
    protected ClaimValidationHandler validationChain() {
        return null;
    }

    /**
     * Performs the actual claim submission after validation has passed.
     *
     * @param claim the validated claim
     * @param creds the agency credentials for this connector
     * @return the submission receipt
     */
    protected abstract ClaimSubmissionReceipt doSubmit(Claim claim, AgencyBillingCredentials creds);

    @Override
    public abstract RemittanceResult fetchRemittance(String batchId, AgencyBillingCredentials creds);
}
