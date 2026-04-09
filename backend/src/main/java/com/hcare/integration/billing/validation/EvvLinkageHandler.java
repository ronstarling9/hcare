package com.hcare.integration.billing.validation;

import com.hcare.integration.billing.Claim;
import com.hcare.integration.evv.EvvSubmissionRecord;
import com.hcare.integration.evv.EvvSubmissionRecordRepository;
import com.hcare.integration.evv.EvvSubmissionStatus;

import java.util.Optional;
import java.util.UUID;

/**
 * Validates that there is a SUBMITTED or ACCEPTED {@link EvvSubmissionRecord} for the claim's
 * EVV record.
 *
 * <p>Both statuses are accepted: the real-time submission path writes {@code SUBMITTED} and there
 * is no reconciliation job that transitions {@code SUBMITTED → ACCEPTED}. Requiring only
 * {@code ACCEPTED} would permanently block all real-time-path claims.
 *
 * <p>The {@code evvRecordId} is supplied at construction time because {@link Claim} carries no
 * direct EVV linkage — it is the caller's responsibility to resolve the EVV record for the shift
 * being billed.
 */
public class EvvLinkageHandler extends ClaimValidationHandler {

    private final EvvSubmissionRecordRepository evvSubmissionRecordRepository;
    private final UUID evvRecordId;

    public EvvLinkageHandler(
            EvvSubmissionRecordRepository evvSubmissionRecordRepository, UUID evvRecordId) {
        this.evvSubmissionRecordRepository = evvSubmissionRecordRepository;
        this.evvRecordId = evvRecordId;
    }

    @Override
    public void validate(Claim claim) {
        Optional<EvvSubmissionRecord> record =
                evvSubmissionRecordRepository.findByEvvRecordId(evvRecordId);
        String status = record.isEmpty() ? null : record.get().getStatus();
        boolean valid = EvvSubmissionStatus.SUBMITTED.name().equals(status)
                || EvvSubmissionStatus.ACCEPTED.name().equals(status);
        if (!valid) {
            throw new ClaimValidationException(
                    "No SUBMITTED or ACCEPTED EVV record found for this claim");
        }
        passToNext(claim);
    }
}
