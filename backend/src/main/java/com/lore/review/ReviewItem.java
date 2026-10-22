package com.lore.review;

import com.lore.change.Change;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** When an understood change next comes back for review, and how far along it is. */
@Entity
@Table(name = "review_items")
public class ReviewItem {

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "change_id", nullable = false, unique = true)
    private Change change;

    /** Index into ReviewSchedule.DAYS: 0 = the 1-day review, 3 = the 14-day one. */
    @Column(nullable = false)
    private int step;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    /** Set once the last review is passed; the change doesn't come back after that. */
    @Column(name = "done_at")
    private Instant doneAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ReviewItem() {}

    public ReviewItem(Change change, Instant dueAt, Instant now) {
        this.id = UUID.randomUUID();
        this.change = change;
        this.step = 0;
        this.dueAt = dueAt;
        this.createdAt = now;
    }

    public UUID getId() { return id; }
    public Change getChange() { return change; }
    public int getStep() { return step; }
    public void setStep(int step) { this.step = step; }
    public Instant getDueAt() { return dueAt; }
    public void setDueAt(Instant dueAt) { this.dueAt = dueAt; }
    public Instant getDoneAt() { return doneAt; }
    public void setDoneAt(Instant doneAt) { this.doneAt = doneAt; }
}
