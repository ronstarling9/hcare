package com.hcare.integration.billing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Fluent builder for {@link Claim} objects.
 *
 * <p>Use {@link #institutional()} or {@link #professional()} as entry points. Call {@link #build()}
 * to produce an immutable {@link Claim}; required fields are validated with
 * {@link Objects#requireNonNull}.
 */
public class X12ClaimBuilder {

    private final Claim.ClaimType claimType;
    private UUID agencyId;
    private UUID clientId;
    private UUID providerId;
    private String billingNpi;
    private String billingTaxId;
    private String priorAuthNumber;
    private LocalDate serviceDate;
    private String serviceCode;
    private BigDecimal units;
    private BigDecimal billedAmount;
    private List<String> diagnosisCodes;
    private String payerId;

    private X12ClaimBuilder(Claim.ClaimType claimType) {
        this.claimType = claimType;
    }

    public static X12ClaimBuilder institutional() {
        return new X12ClaimBuilder(Claim.ClaimType.INSTITUTIONAL);
    }

    public static X12ClaimBuilder professional() {
        return new X12ClaimBuilder(Claim.ClaimType.PROFESSIONAL);
    }

    public X12ClaimBuilder agencyId(UUID id) {
        this.agencyId = id;
        return this;
    }

    public X12ClaimBuilder clientId(UUID id) {
        this.clientId = id;
        return this;
    }

    public X12ClaimBuilder providerId(UUID id) {
        this.providerId = id;
        return this;
    }

    public X12ClaimBuilder billingProvider(String npi, String taxId) {
        this.billingNpi = npi;
        this.billingTaxId = taxId;
        return this;
    }

    public X12ClaimBuilder serviceDate(LocalDate date) {
        this.serviceDate = date;
        return this;
    }

    public X12ClaimBuilder serviceCode(String hcpcs) {
        this.serviceCode = hcpcs;
        return this;
    }

    public X12ClaimBuilder units(BigDecimal units) {
        this.units = units;
        return this;
    }

    public X12ClaimBuilder billedAmount(BigDecimal amount) {
        this.billedAmount = amount;
        return this;
    }

    public X12ClaimBuilder diagnosisCodes(List<String> codes) {
        this.diagnosisCodes = codes;
        return this;
    }

    public X12ClaimBuilder authorizationNumber(String authNum) {
        this.priorAuthNumber = authNum;
        return this;
    }

    public X12ClaimBuilder payerId(String payerId) {
        this.payerId = payerId;
        return this;
    }

    public Claim build() {
        Objects.requireNonNull(agencyId, "agencyId is required");
        Objects.requireNonNull(payerId, "payerId is required");
        Objects.requireNonNull(billingNpi, "billingNpi is required");
        Objects.requireNonNull(serviceDate, "serviceDate is required");
        Objects.requireNonNull(serviceCode, "serviceCode is required");
        Objects.requireNonNull(units, "units is required");
        Objects.requireNonNull(billedAmount, "billedAmount is required");
        return new Claim(
                claimType,
                agencyId,
                clientId,
                providerId,
                billingNpi,
                billingTaxId,
                priorAuthNumber,
                serviceDate,
                serviceCode,
                units,
                billedAmount,
                diagnosisCodes,
                payerId);
    }
}
