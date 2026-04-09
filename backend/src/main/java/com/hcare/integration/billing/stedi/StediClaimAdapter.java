package com.hcare.integration.billing.stedi;

import com.hcare.integration.billing.Claim;

import java.util.HashMap;
import java.util.Map;

/**
 * Converts a {@link Claim} to a Stedi-compatible JSON structure.
 *
 * <p>Package-private — only {@link StediTransmissionStrategy} should use this adapter.
 */
class StediClaimAdapter {

    /**
     * Converts the given claim to a map that can be serialized as Stedi Healthcare API JSON for an
     * 837P (professional) or 837I (institutional) transaction.
     *
     * @param claim the claim to convert
     * @return a map representing the Stedi claim payload
     */
    Map<String, Object> toStediPayload(Claim claim) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("transactionType", claim.claimType().name());
        payload.put("billingProviderNpi", claim.billingNpi());
        payload.put("billingProviderTaxId", claim.billingTaxId());
        payload.put("serviceDate", claim.serviceDate() != null ? claim.serviceDate().toString() : null);
        payload.put("procedureCode", claim.serviceCode());
        payload.put("units", claim.units());
        payload.put("billedAmount", claim.billedAmount());
        payload.put("diagnosisCodes", claim.diagnosisCodes());
        payload.put("priorAuthorizationNumber", claim.priorAuthNumber());
        payload.put("payerId", claim.payerId());

        if (claim.agencyId() != null) {
            payload.put("agencyId", claim.agencyId().toString());
        }
        if (claim.clientId() != null) {
            payload.put("clientId", claim.clientId().toString());
        }
        if (claim.providerId() != null) {
            payload.put("providerId", claim.providerId().toString());
        }

        return payload;
    }
}
