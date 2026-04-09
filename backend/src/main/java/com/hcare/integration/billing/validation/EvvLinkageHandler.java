package com.hcare.integration.billing.validation;

import com.hcare.integration.billing.Claim;
import com.hcare.integration.evv.EvvSubmissionRecord;
import com.hcare.integration.evv.EvvSubmissionRecordRepository;
import com.hcare.integration.evv.EvvSubmissionStatus;

import java.util.Optional;
import java.util.UUID;

/**
 * Validates that there is an ACCEPTED {@link EvvSubmissionRecord} for the claim's EVV record.
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
        if (record.isEmpty() || !EvvSubmissionStatus.ACCEPTED.name().equals(record.get().getStatus())) {
            throw new ClaimValidationException(
                    "No ACCEPTED EVV record found for this claim");
        }
        passToNext(claim);
    }
}
