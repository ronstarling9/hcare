package com.hcare.integration.evv;

import com.hcare.evv.AggregatorType;
import com.hcare.integration.audit.IntegrationAuditWriter;

/**
 * Decorator that records submit/update/void_ calls to the integration audit log.
 * Measures wall-clock duration and records success/failure with errorCode.
 */
public class AuditingEvvSubmissionStrategy implements EvvSubmissionStrategy {

    private final EvvSubmissionStrategy delegate;
    private final IntegrationAuditWriter auditWriter;

    public AuditingEvvSubmissionStrategy(EvvSubmissionStrategy delegate,
                                          IntegrationAuditWriter auditWriter) {
        this.delegate = delegate;
        this.auditWriter = auditWriter;
    }

    @Override
    public AggregatorType aggregatorType() {
        return delegate.aggregatorType();
    }

    @Override
    public boolean isRealTime() {
        return delegate.isRealTime();
    }

    @Override
    public Class<?> credentialClass() {
        return delegate.credentialClass();
    }

    @Override
    public EvvSubmissionResult submit(EvvSubmissionContext ctx, Object typedCreds) {
        return audited("SUBMIT", ctx, () -> delegate.submit(ctx, typedCreds));
    }

    @Override
    public EvvSubmissionResult update(EvvSubmissionContext ctx, Object typedCreds) {
        return audited("UPDATE", ctx, () -> delegate.update(ctx, typedCreds));
    }

    @Override
    public EvvSubmissionResult void_(EvvSubmissionContext ctx, Object typedCreds) {
        return audited("VOID", ctx, () -> delegate.void_(ctx, typedCreds));
    }

    private EvvSubmissionResult audited(String operation, EvvSubmissionContext ctx,
                                         java.util.function.Supplier<EvvSubmissionResult> action) {
        long start = System.currentTimeMillis();
        EvvSubmissionResult result = action.get();
        long durationMs = System.currentTimeMillis() - start;
        auditWriter.record(
                ctx.agencyId(),
                ctx.evvRecordId(),
                ctx.aggregatorType().name(),
                operation,
                result.success(),
                durationMs,
                result.errorCode());
        return result;
    }
}
