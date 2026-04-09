package com.hcare.integration.evv;

import com.hcare.evv.AggregatorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Decorator that adds retry logic with exponential backoff and ±25% jitter.
 *
 * <p>Attempts: up to 3. Base delays: 2s → 4s → 8s, each with ±25% jitter.
 * 4xx error codes are terminal — no retry for client errors.
 * After exhausting all attempts, the last failure result is returned.
 */
public class RetryingEvvSubmissionStrategy implements EvvSubmissionStrategy {

    private static final Logger log = LoggerFactory.getLogger(RetryingEvvSubmissionStrategy.class);

    private static final int MAX_ATTEMPTS = 3;
    private static final long[] BASE_DELAYS_MS = {2_000L, 4_000L, 8_000L};

    private final EvvSubmissionStrategy delegate;

    public RetryingEvvSubmissionStrategy(EvvSubmissionStrategy delegate) {
        this.delegate = delegate;
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
        return withRetry("submit", ctx, typedCreds,
                (c, creds) -> delegate.submit(c, creds));
    }

    @Override
    public EvvSubmissionResult update(EvvSubmissionContext ctx, Object typedCreds) {
        return withRetry("update", ctx, typedCreds,
                (c, creds) -> delegate.update(c, creds));
    }

    @Override
    public EvvSubmissionResult void_(EvvSubmissionContext ctx, Object typedCreds) {
        return withRetry("void_", ctx, typedCreds,
                (c, creds) -> delegate.void_(c, creds));
    }

    private EvvSubmissionResult withRetry(String operation, EvvSubmissionContext ctx,
                                           Object typedCreds, StrategyOperation op) {
        EvvSubmissionResult last = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            last = op.execute(ctx, typedCreds);
            if (last.success()) {
                return last;
            }
            if (isTerminalError(last.errorCode())) {
                log.warn("[{}] Terminal error on attempt {}/{} for evvRecordId={}: errorCode={}",
                        operation, attempt + 1, MAX_ATTEMPTS, ctx.evvRecordId(), last.errorCode());
                return last;
            }
            if (attempt < MAX_ATTEMPTS - 1) {
                sleep(BASE_DELAYS_MS[attempt]);
            }
        }
        log.warn("[{}] Exhausted {} attempts for evvRecordId={}", operation, MAX_ATTEMPTS,
                ctx.evvRecordId());
        return last;
    }

    private boolean isTerminalError(String errorCode) {
        if (errorCode == null) {
            return false;
        }
        return errorCode.startsWith("4") || errorCode.contains("4xx");
    }

    private void sleep(long baseMs) {
        double jitterFactor = 1.0 + (ThreadLocalRandom.current().nextDouble(-0.25, 0.25));
        long sleepMs = (long) (baseMs * jitterFactor);
        try {
            Thread.sleep(sleepMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    private interface StrategyOperation {
        EvvSubmissionResult execute(EvvSubmissionContext ctx, Object typedCreds);
    }
}
