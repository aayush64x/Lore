package com.lore.change;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** A follow-up question asked about a change, and the AI's answer. */
@Entity
@Table(name = "change_questions")
public class ChangeQuestion {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "change_id", nullable = false)
    private Change change;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String question;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String answer;

    private String model;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ChangeQuestion() {}

    public ChangeQuestion(Change change, String question, String answer, String model) {
        this.id = UUID.randomUUID();
        this.change = change;
        this.question = question;
        this.answer = answer;
        this.model = model;
        this.createdAt = Instant.now();
    }

    public Change getChange() { return change; }
    public String getQuestion() { return question; }
    public String getAnswer() { return answer; }
    public Instant getCreatedAt() { return createdAt; }
}
