package com.hcare.integration.billing;

/**
 * Strategy interface for submitting claims to a billing connector and retrieving remittance.
 */
public interface ClaimTransmissionStrategy {

    /**
     * Returns the connector type identifier (e.g., "STEDI", "OFFICE_ALLY").
     */
    String connectorType();

    /**
     * Submits the given claim using the provided agency credentials.
     *
     * @param claim the claim to submit
     * @param creds the agency's billing credentials for this connector
     * @return a receipt indicating success or failure
     */
    ClaimSubmissionReceipt submit(Claim claim, AgencyBillingCredentials creds);

    /**
     * Fetches remittance information for a previously submitted batch.
     *
     * @param batchId the batch ID from a prior successful submission
     * @param creds   the agency's billing credentials for this connector
     * @return the remittance result, or {@link RemittanceResult#notReady()} if not yet available
     */
    RemittanceResult fetchRemittance(String batchId, AgencyBillingCredentials creds);
}
