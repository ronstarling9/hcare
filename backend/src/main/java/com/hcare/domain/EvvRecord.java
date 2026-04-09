package com.hcare.domain;

import com.hcare.evv.VerificationMethod;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Raw captured EVV data for a completed or in-progress visit.
 * Compliance status (GREEN/YELLOW/RED/EXEMPT/PORTAL_SUBMIT/GREY) is computed on read by
 * the Core API EVV compliance module (Plan 4) — it is never stored here.
 *
 * Federal elements:
 *   1 — serviceType: derivable from Shift.serviceTypeId
 *   2 — clientMedicaidId: stored here (not guaranteed set on every client)
 *   3 — dateOfService: derivable from Shift.scheduledStart
 *   4 — GPS location: locationLat / locationLon
 *   5 — caregiverId: derivable from Shift.caregiverId
 *   6 — timeIn / timeOut
 */
@Entity
@Table(name = "evv_records")
@Filter(name = "agencyFilter", condition = "agency_id = :agencyId")
public class EvvRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "shift_id", nullable = false, unique = true)
    private UUID shiftId;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    @Column(name = "client_medicaid_id", length = 50)
    private String clientMedicaidId;

    @Column(name = "location_lat", precision = 10, scale = 7)
    private BigDecimal locationLat;

    @Column(name = "location_lon", precision = 10, scale = 7)
    private BigDecimal locationLon;

    @Column(name = "time_in")
    private LocalDateTime timeIn;

    @Column(name = "time_out")
    private LocalDateTime timeOut;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_method", nullable = false, length = 30)
    private VerificationMethod verificationMethod;

    // True when caregiver is a live-in co-resident — suppresses EVV requirement in most states
    @Column(name = "co_resident", nullable = false)
    private boolean coResident = false;

    // State-specific extra fields as JSON e.g. {"taskDocumentation": [...]} for Missouri
    @Column(name = "state_fields", nullable = false, columnDefinition = "TEXT")
    private String stateFields = "{}";

    // True when visit was captured offline. deviceCapturedAt is authoritative for compliance
    // timestamp; server receipt time is never used as the EVV timestamp for offline visits.
    @Column(name = "captured_offline", nullable = false)
    private boolean capturedOffline = false;

    @Column(name = "device_captured_at")
    private LocalDateTime deviceCapturedAt;

    @Column(name = "aggregator_visit_id", length = 100)
    private String aggregatorVisitId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now(ZoneOffset.UTC);

    protected EvvRecord() {}

    public EvvRecord(UUID shiftId, UUID agencyId, VerificationMethod verificationMethod) {
        this.shiftId = shiftId;
        this.agencyId = agencyId;
        this.verificationMethod = verificationMethod;
    }

    public void setClientMedicaidId(String clientMedicaidId) { this.clientMedicaidId = clientMedicaidId; }
    public void setLocationLat(BigDecimal locationLat) { this.locationLat = locationLat; }
    public void setLocationLon(BigDecimal locationLon) { this.locationLon = locationLon; }
    public void setTimeIn(LocalDateTime timeIn) { this.timeIn = timeIn; }
    public void setTimeOut(LocalDateTime timeOut) { this.timeOut = timeOut; }
    public void setCoResident(boolean coResident) { this.coResident = coResident; }
    public void setStateFields(String stateFields) { this.stateFields = stateFields; }
    public void setCapturedOffline(boolean capturedOffline) { this.capturedOffline = capturedOffline; }
    public void setDeviceCapturedAt(LocalDateTime deviceCapturedAt) { this.deviceCapturedAt = deviceCapturedAt; }
    public void setAggregatorVisitId(String aggregatorVisitId) { this.aggregatorVisitId = aggregatorVisitId; }

    public UUID getId() { return id; }
    public UUID getShiftId() { return shiftId; }
    public UUID getAgencyId() { return agencyId; }
    public String getClientMedicaidId() { return clientMedicaidId; }
    public BigDecimal getLocationLat() { return locationLat; }
    public BigDecimal getLocationLon() { return locationLon; }
    public LocalDateTime getTimeIn() { return timeIn; }
    public LocalDateTime getTimeOut() { return timeOut; }
    public VerificationMethod getVerificationMethod() { return verificationMethod; }
    public boolean isCoResident() { return coResident; }
    public String getStateFields() { return stateFields; }
    public boolean isCapturedOffline() { return capturedOffline; }
    public LocalDateTime getDeviceCapturedAt() { return deviceCapturedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getAggregatorVisitId() { return aggregatorVisitId; }
}
