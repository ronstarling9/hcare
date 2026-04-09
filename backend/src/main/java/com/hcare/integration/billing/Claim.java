package com.hcare.integration.billing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Immutable value object representing an X12 claim. Only constructable via {@link X12ClaimBuilder}.
 */
public final class Claim {

    public enum ClaimType {
        INSTITUTIONAL,
        PROFESSIONAL
    }

    private final ClaimType claimType;
    private final UUID agencyId;
    private final UUID clientId;
    private final UUID providerId;
    private final String billingNpi;
    private final String billingTaxId;
    private final String priorAuthNumber;
    private final LocalDate serviceDate;
    private final String serviceCode;
    private final BigDecimal units;
    private final BigDecimal billedAmount;
    private final List<String> diagnosisCodes;
    private final String payerId;

    Claim(
            ClaimType claimType,
            UUID agencyId,
            UUID clientId,
            UUID providerId,
            String billingNpi,
            String billingTaxId,
            String priorAuthNumber,
            LocalDate serviceDate,
            String serviceCode,
            BigDecimal units,
            BigDecimal billedAmount,
            List<String> diagnosisCodes,
            String payerId) {
        this.claimType = claimType;
        this.agencyId = agencyId;
        this.clientId = clientId;
        this.providerId = providerId;
        this.billingNpi = billingNpi;
        this.billingTaxId = billingTaxId;
        this.priorAuthNumber = priorAuthNumber;
        this.serviceDate = serviceDate;
        this.serviceCode = serviceCode;
        this.units = units;
        this.billedAmount = billedAmount;
        this.diagnosisCodes =
                diagnosisCodes != null
                        ? Collections.unmodifiableList(diagnosisCodes)
                        : Collections.emptyList();
        this.payerId = payerId;
    }

    public ClaimType claimType() {
        return claimType;
    }

    public UUID agencyId() {
        return agencyId;
    }

    public UUID clientId() {
        return clientId;
    }

    public UUID providerId() {
        return providerId;
    }

    public String billingNpi() {
        return billingNpi;
    }

    public String billingTaxId() {
        return billingTaxId;
    }

    public String priorAuthNumber() {
        return priorAuthNumber;
    }

    public LocalDate serviceDate() {
        return serviceDate;
    }

    public String serviceCode() {
        return serviceCode;
    }

    public BigDecimal units() {
        return units;
    }

    public BigDecimal billedAmount() {
        return billedAmount;
    }

    public List<String> diagnosisCodes() {
        return diagnosisCodes;
    }

    public String payerId() {
        return payerId;
    }
}
