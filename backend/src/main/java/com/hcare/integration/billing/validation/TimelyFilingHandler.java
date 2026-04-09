package com.hcare.integration.billing.validation;

import com.hcare.integration.billing.Claim;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Warns (but does not reject) when a claim's service date is older than 90 days from today.
 *
 * <p>Timely filing limits vary by payer, so this handler logs a WARN rather than throwing. The
 * claim is passed to the next handler regardless.
 */
public class TimelyFilingHandler extends ClaimValidationHandler {

    private static final Logger log = LoggerFactory.getLogger(TimelyFilingHandler.class);
    private static final long TIMELY_FILING_DAYS = 90L;

    @Override
    public void validate(Claim claim) {
        if (claim.serviceDate() != null) {
            long daysSinceService = ChronoUnit.DAYS.between(claim.serviceDate(), LocalDate.now());
            if (daysSinceService > TIMELY_FILING_DAYS) {
                log.warn(
                        "Claim service date {} is {} days old — may be outside timely filing window"
                                + " (payer: {}, agency: {})",
                        claim.serviceDate(),
                        daysSinceService,
                        claim.payerId(),
                        claim.agencyId());
            }
        }
        passToNext(claim);
    }
}
