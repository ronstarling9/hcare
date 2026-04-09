package com.hcare.integration.evv.carebridge;

import com.hcare.evv.AggregatorType;
import com.hcare.integration.evv.AbstractEvvSubmissionStrategy;
import com.hcare.integration.evv.EvvSubmissionContext;
import com.hcare.integration.evv.EvvSubmissionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Stub EVV submission strategy for the CareBridge aggregator.
 *
 * <p>This connector is not yet implemented. All operations return a failure result with error code
 * {@code NOT_IMPLEMENTED}. Do not throw exceptions from stubs — callers must receive a
 * {@link EvvSubmissionResult} regardless.
 */
@Component
public class CareBridgeSubmissionStrategy extends AbstractEvvSubmissionStrategy {

    private static final Logger log = LoggerFactory.getLogger(CareBridgeSubmissionStrategy.class);

    @Override
    public AggregatorType aggregatorType() {
        return AggregatorType.CAREBRIDGE;
    }

    @Override
    public boolean isRealTime() {
        return true;
    }

    @Override
    public Class<?> credentialClass() {
        return Object.class;
    }

    @Override
    protected void validate(EvvSubmissionContext ctx) {
        // no-op — stub does not validate
    }

    @Override
    protected Object buildPayload(EvvSubmissionContext ctx, Object typedCreds) {
        // no-op — stub does not build a payload
        return null;
    }

    @Override
    protected EvvSubmissionResult doSubmit(EvvSubmissionContext ctx, Object payload) {
        log.warn("CareBridge EVV connector not implemented — returning failure for agency={}", ctx.agencyId());
        return EvvSubmissionResult.failure("NOT_IMPLEMENTED", "CareBridge connector not yet implemented");
    }

    @Override
    protected EvvSubmissionResult doUpdate(EvvSubmissionContext ctx, Object typedCreds) {
        log.warn("CareBridge EVV update not implemented — returning failure for agency={}", ctx.agencyId());
        return EvvSubmissionResult.failure("NOT_IMPLEMENTED", "CareBridge connector not yet implemented");
    }

    @Override
    protected EvvSubmissionResult doVoid_(EvvSubmissionContext ctx, Object typedCreds) {
        log.warn("CareBridge EVV void not implemented — returning failure for agency={}", ctx.agencyId());
        return EvvSubmissionResult.failure("NOT_IMPLEMENTED", "CareBridge connector not yet implemented");
    }
}
