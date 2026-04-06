package com.hcare.evv;

public enum EvvComplianceStatus {
    /** Visit not yet started — no EvvRecord exists. */
    GREY,
    /** Co-resident caregiver or private-pay payer — EVV not required. */
    EXEMPT,
    /** All 6 federal elements present, method allowed, GPS within tolerance (if applicable), no time anomaly. */
    GREEN,
    /** All elements present but one exception condition applies: manual override, GPS drift, time anomaly,
     *  or closed state unacknowledged. */
    YELLOW,
    /** Closed-state, agency has acknowledged limitation — all elements present, agency submits via state portal. */
    PORTAL_SUBMIT,
    /** Required element missing, no clock-out recorded, or visit missed without documentation. */
    RED
}
