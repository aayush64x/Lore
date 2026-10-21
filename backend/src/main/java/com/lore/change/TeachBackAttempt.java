package com.lore.change;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** One graded teach-back answer for a change. got_right and missing are JSON lists. */
@Entity
@Table(name = "teach_back_attempts")
public class TeachBackAttempt {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "change_id", nullable = false)
    private Change change;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String answer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Verdict verdict;

    @Column(name = "got_right", columnDefinition = "TEXT")
    private String gotRight;

    @Column(columnDefinition = "TEXT")
    private String missing;

    private String model;

    /** True for answers given after the change was already passed (they don't use tries). */
    @Column(nullable = false)
    private boolean practice;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TeachBackAttempt() {}

    public TeachBackAttempt(UUID id, Change change, String answer, Verdict verdict,
                            String gotRight, String missing, String model) {
        this(id, change, answer, verdict, gotRight, missing, model, false);
    }

    public TeachBackAttempt(UUID id, Change change, String answer, Verdict verdict,
                            String gotRight, String missing, String model, boolean practice) {
        this.id = id;
        this.change = change;
        this.answer = answer;
        this.verdict = verdict;
        this.gotRight = gotRight;
        this.missing = missing;
        this.model = model;
        this.practice = practice;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Change getChange() { return change; }
    public String getAnswer() { return answer; }
    public Verdict getVerdict() { return verdict; }
    public String getGotRight() { return gotRight; }
    public String getMissing() { return missing; }
    public String getModel() { return model; }
    public boolean isPractice() { return practice; }
    public Instant getCreatedAt() { return createdAt; }

    public enum Verdict { PASS, PARTLY, FAIL }
}
