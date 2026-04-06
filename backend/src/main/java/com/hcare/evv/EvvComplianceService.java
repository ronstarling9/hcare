package com.hcare.evv;

import com.hcare.domain.EvvRecord;
import com.hcare.domain.PayerType;
import com.hcare.domain.Shift;

import java.math.BigDecimal;

public interface EvvComplianceService {

    /**
     * Computes EVV compliance status purely from pre-loaded objects — no DB calls.
     *
     * @param record      the EVVRecord for this visit; null if the visit has not started (returns GREY)
     * @param stateConfig the EvvStateConfig for the client's service state
     * @param shift       the shift being evaluated (for scheduled-start time anomaly check)
     * @param payerType   null if no authorization linked; PRIVATE_PAY triggers EXEMPT
     * @param clientLat   client's geocoded latitude; null skips GPS tolerance check
     * @param clientLng   client's geocoded longitude; null skips GPS tolerance check
     */
    EvvComplianceStatus compute(EvvRecord record, EvvStateConfig stateConfig,
                                 Shift shift, PayerType payerType,
                                 BigDecimal clientLat, BigDecimal clientLng);
}
