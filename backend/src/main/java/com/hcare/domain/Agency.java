package com.hcare.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "agencies")
public class Agency {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, columnDefinition = "CHAR(2)")
    private String state;

    @Column(nullable = false, length = 50)
    private String timezone = "America/New_York";

    @Column(name = "npi", length = 10)
    private String npi;

    @Column(name = "tax_id", length = 9)
    private String taxId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now(ZoneOffset.UTC);

    protected Agency() {}

    public Agency(String name, String state) {
        this.name = name;
        this.state = state;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getState() { return state; }
    public String getTimezone() { return timezone; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setName(String name) { this.name = name; }
    public void setState(String state) { this.state = state; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public void setNpi(String npi) { this.npi = npi; }
    public void setTaxId(String taxId) { this.taxId = taxId; }

    public String getNpi() { return npi; }
    public String getTaxId() { return taxId; }
}
