package com.hcare.integration.evv;

import com.hcare.evv.AggregatorType;

public interface EvvSubmissionStrategy {

    AggregatorType aggregatorType();

    boolean isRealTime();

    /** C6: used by caller to decrypt blob with the right type. */
    Class<?> credentialClass();

    EvvSubmissionResult submit(EvvSubmissionContext ctx, Object typedCreds);

    EvvSubmissionResult update(EvvSubmissionContext ctx, Object typedCreds);

    /** Named with trailing underscore because {@code void} is a Java keyword. */
    EvvSubmissionResult void_(EvvSubmissionContext ctx, Object typedCreds);
}
