package com.hcare.integration.evv;

import com.hcare.domain.Authorization;
import com.hcare.domain.AuthorizationRepository;
import com.hcare.integration.AuthorizationChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Domain-backed implementation of {@link AuthorizationChecker}.
 *
 * <p>Looks up the {@link Authorization} by ID and checks whether adding {@code units} to
 * {@code usedUnits} would exceed {@code authorizedUnits}. The Hibernate agencyFilter must be
 * active on the calling thread before this method is invoked.
 */
@Component
public class HcareAuthorizationChecker implements AuthorizationChecker {

    private static final Logger log = LoggerFactory.getLogger(HcareAuthorizationChecker.class);

    private final AuthorizationRepository authorizationRepository;

    public HcareAuthorizationChecker(AuthorizationRepository authorizationRepository) {
        this.authorizationRepository = authorizationRepository;
    }

    /**
     * Returns {@code true} if adding {@code units} to the current consumed total would exceed
     * the authorized limit for the given authorization.
     *
     * @param authorizationId the UUID of the {@link Authorization} record to check
     * @param units           the number of units being requested
     * @return {@code true} if the cap would be exceeded; {@code false} if within limits or auth
     *     not found (logged as a warning)
     */
    @Override
    public boolean wouldExceedAuthorizedHours(UUID authorizationId, BigDecimal units) {
        Optional<Authorization> opt = authorizationRepository.findById(authorizationId);
        if (opt.isEmpty()) {
            log.warn("Authorization not found for id={} — treating as non-exceeding", authorizationId);
            return false;
        }
        Authorization auth = opt.get();
        BigDecimal remaining = auth.getAuthorizedUnits().subtract(auth.getUsedUnits());
        return units.compareTo(remaining) > 0;
    }
}
