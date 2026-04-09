package com.hcare.integration.billing.validation;

import com.hcare.integration.billing.Claim;

/**
 * Abstract Chain of Responsibility handler for claim validation.
 *
 * <p>Subclasses implement {@link #validate(Claim)} and throw {@link ClaimValidationException} on
 * failure. Call {@link #passToNext(Claim)} to continue the chain.
 */
public abstract class ClaimValidationHandler {

    protected ClaimValidationHandler next;

    /**
     * Appends {@code next} as the successor in this chain and returns it for fluent chaining.
     *
     * @param next the next handler in the chain
     * @return {@code next}
     */
    public ClaimValidationHandler then(ClaimValidationHandler next) {
        this.next = next;
        return next;
    }

    /**
     * Validates the given claim. Throws {@link ClaimValidationException} if validation fails.
     *
     * @param claim the claim to validate
     */
    public abstract void validate(Claim claim);

    /**
     * Passes the claim to the next handler in the chain, if one is configured.
     *
     * @param claim the claim to pass along
     */
    protected void passToNext(Claim claim) {
        if (next != null) {
            next.validate(claim);
        }
    }
}
