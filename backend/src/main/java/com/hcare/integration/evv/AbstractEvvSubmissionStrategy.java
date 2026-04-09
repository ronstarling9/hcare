package com.hcare.integration.evv;

/**
 * Template Method base for EVV submission strategies.
 *
 * <p>The {@code submit()} method is {@code final} and enforces the validate → buildPayload →
 * doSubmit sequence. Subclasses implement the three protected abstract hooks.
 * {@code update()} and {@code void_()} delegate to their respective protected abstract methods.
 */
public abstract class AbstractEvvSubmissionStrategy implements EvvSubmissionStrategy {

    @Override
    public final EvvSubmissionResult submit(EvvSubmissionContext ctx, Object typedCreds) {
        validate(ctx);
        Object payload = buildPayload(ctx, typedCreds);
        return doSubmit(ctx, payload);
    }

    // Note: update() and void_() do not call validate() — subclasses are responsible
    // for any validation needed in doUpdate()/doVoid_() implementations.
    @Override
    public EvvSubmissionResult update(EvvSubmissionContext ctx, Object typedCreds) {
        return doUpdate(ctx, typedCreds);
    }

    @Override
    public EvvSubmissionResult void_(EvvSubmissionContext ctx, Object typedCreds) {
        return doVoid_(ctx, typedCreds);
    }

    protected abstract void validate(EvvSubmissionContext ctx);

    protected abstract Object buildPayload(EvvSubmissionContext ctx, Object typedCreds);

    protected abstract EvvSubmissionResult doSubmit(EvvSubmissionContext ctx, Object payload);

    protected abstract EvvSubmissionResult doUpdate(EvvSubmissionContext ctx, Object typedCreds);

    protected abstract EvvSubmissionResult doVoid_(EvvSubmissionContext ctx, Object typedCreds);
}
