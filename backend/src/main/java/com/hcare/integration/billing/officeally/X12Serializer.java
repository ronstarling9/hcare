package com.hcare.integration.billing.officeally;

import com.hcare.integration.billing.Claim;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Package-private X12 serializer wrapping StAEDI ({@code io.xlate.edi.stream.EDIStreamWriter}).
 *
 * <p>Full StAEDI integration is deferred. This stub logs a warning and returns empty byte arrays.
 * Only {@link OfficeAllyTransmissionStrategy} should use this class.
 */
class X12Serializer {

    private static final Logger log = LoggerFactory.getLogger(X12Serializer.class);

    /**
     * Serializes the claim as an X12 837P (professional) transaction set.
     *
     * @param claim the claim to serialize
     * @return the serialized bytes, or an empty array if StAEDI wiring is not yet complete
     */
    byte[] serialize837P(Claim claim) {
        log.warn("X12Serializer.serialize837P called — StAEDI integration is not yet implemented");
        return new byte[0];
    }

    /**
     * Serializes the claim as an X12 837I (institutional) transaction set.
     *
     * @param claim the claim to serialize
     * @return the serialized bytes, or an empty array if StAEDI wiring is not yet complete
     */
    byte[] serialize837I(Claim claim) {
        log.warn("X12Serializer.serialize837I called — StAEDI integration is not yet implemented");
        return new byte[0];
    }
}
