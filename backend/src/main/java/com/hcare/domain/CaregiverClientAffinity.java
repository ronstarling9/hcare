package com.hcare.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.util.UUID;

// Visit history per caregiver+client pair — drives the 25% continuity scoring factor.
// visitCount is updated via Plan 5's Spring event listener when shifts complete.
@Entity
@Table(name = "caregiver_client_affinities")
@Filter(name = "agencyFilter", condition = "agency_id = :agencyId")
public class CaregiverClientAffinity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "scoring_profile_id", nullable = false)
    private UUID scoringProfileId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    @Column(name = "visit_count", nullable = false)
    private int visitCount = 0;

    protected CaregiverClientAffinity() {}

    public CaregiverClientAffinity(UUID scoringProfileId, UUID clientId, UUID agencyId) {
        this.scoringProfileId = scoringProfileId;
        this.clientId = clientId;
        this.agencyId = agencyId;
    }

    public void incrementVisitCount() { this.visitCount++; }

    public UUID getId() { return id; }
    public UUID getScoringProfileId() { return scoringProfileId; }
    public UUID getClientId() { return clientId; }
    public UUID getAgencyId() { return agencyId; }
    public int getVisitCount() { return visitCount; }
}
