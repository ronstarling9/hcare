package com.hcare.multitenancy;

import java.util.UUID;

public final class TenantContext {

    // Uses ThreadLocal, which is safe with virtual threads (Java 21+): each virtual thread
    // has isolated ThreadLocal storage and values persist across park/resume cycles.
    // Do NOT switch to InheritableThreadLocal — child thread inheritance is problematic
    // with virtual thread pooling.
    private static final ThreadLocal<UUID> CURRENT_AGENCY = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(UUID agencyId) {
        CURRENT_AGENCY.set(agencyId);
    }

    public static UUID get() {
        return CURRENT_AGENCY.get();
    }

    public static void clear() {
        CURRENT_AGENCY.remove();
    }
}
