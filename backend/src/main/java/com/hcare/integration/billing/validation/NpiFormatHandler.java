package com.hcare.integration.billing.validation;

import com.hcare.integration.billing.Claim;
import com.hcare.integration.billing.NpiValidator;

/**
 * Validates that the claim's billing NPI passes the CMS Luhn check via {@link NpiValidator}.
 */
public class NpiFormatHandler extends ClaimValidationHandler {

    @Override
    public void validate(Claim claim) {
        if (!NpiValidator.isValid(claim.billingNpi())) {
            throw new ClaimValidationException(
                    "Invalid NPI format: " + claim.billingNpi());
        }
        passToNext(claim);
    }
}
