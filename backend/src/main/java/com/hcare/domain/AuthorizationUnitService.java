package com.hcare.domain;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthorizationUnitService {

    private final AuthorizationRepository authorizationRepository;

    public AuthorizationUnitService(AuthorizationRepository authorizationRepository) {
        this.authorizationRepository = authorizationRepository;
    }

    /**
     * Single-attempt increment of Authorization.usedUnits.
     * Runs in its own transaction (REQUIRES_NEW) — each call gets a fresh Hibernate session.
     * Never call this in a loop inside a @Transactional context; retry loop belongs in afterCommit hook.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void addUnits(UUID authorizationId, LocalDateTime timeIn, LocalDateTime timeOut) {
        if (timeIn == null || timeOut == null) {
            throw new IllegalArgumentException(
                "timeIn and timeOut must both be non-null for authorization unit update");
        }
        Optional<Authorization> authOpt = authorizationRepository.findById(authorizationId);
        if (authOpt.isEmpty()) return;

        Authorization auth = authOpt.get();
        BigDecimal units;
        if (auth.getUnitType() == UnitType.HOURS) {
            long minutes = Duration.between(timeIn, timeOut).toMinutes();
            units = BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
        } else {
            units = BigDecimal.ONE;
        }
        auth.addUsedUnits(units);
        authorizationRepository.save(auth);
    }
}
