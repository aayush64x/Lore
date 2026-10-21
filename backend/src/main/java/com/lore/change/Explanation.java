package com.lore.change;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "explanations")
public class Explanation {

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "change_id", nullable = false, unique = true)
    private Change change;

    private String overview;

    @Column(columnDefinition = "TEXT")
    private String steps;

    @Column(columnDefinition = "TEXT")
    private String concepts;

    /** The profile it was written for (UserProfile.key()); null for old rows. */
    private String profile;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExplanationStatus status = ExplanationStatus.PENDING;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Explanation() {}

    public Explanation(UUID id, Change change) {
        this.id = id;
        this.change = change;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Change getChange() { return change; }
    public String getOverview() { return overview; }
    public void setOverview(String overview) { this.overview = overview; }
    public String getSteps() { return steps; }
    public void setSteps(String steps) { this.steps = steps; }
    public String getConcepts() { return concepts; }
    public void setConcepts(String concepts) { this.concepts = concepts; }
    public String getProfile() { return profile; }
    public void setProfile(String profile) { this.profile = profile; }
    public ExplanationStatus getStatus() { return status; }
    public void setStatus(ExplanationStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }

    public enum ExplanationStatus { PENDING, COMPLETE, FAILED }
}
