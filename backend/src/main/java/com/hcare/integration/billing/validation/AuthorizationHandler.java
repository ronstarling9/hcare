package com.hcare.integration.billing.validation;

import com.hcare.integration.AuthorizationChecker;
import com.hcare.integration.billing.Claim;

import java.util.UUID;

/**
 * Validates that the claim's units would not exceed the authorized limit for the associated
 * authorization.
 *
 * <p>The {@code authorizationId} is supplied at construction time because {@link Claim} carries
 * only an opaque {@code priorAuthNumber} string; the UUID mapping is the caller's responsibility.
 */
public class AuthorizationHandler extends ClaimValidationHandler {

    private final AuthorizationChecker authorizationChecker;
    private final UUID authorizationId;

    public AuthorizationHandler(AuthorizationChecker authorizationChecker, UUID authorizationId) {
        this.authorizationChecker = authorizationChecker;
        this.authorizationId = authorizationId;
    }

    @Override
    public void validate(Claim claim) {
        if (authorizationChecker.wouldExceedAuthorizedHours(authorizationId, claim.units())) {
            throw new ClaimValidationException(
                    "Claim units would exceed authorized hours for authorization: " + authorizationId);
        }
        passToNext(claim);
    }
}
