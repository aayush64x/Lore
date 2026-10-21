package com.lore.session;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sessions")
public class Session {

    @Id
    private UUID id;

    @Column(name = "project_path", nullable = false)
    private String projectPath;

    @Column(length = 20000)
    private String reason;

    private String summary;

    /** JSON list of the goal's parts no change did ("[]" = all done); null if not checked. */
    @Column(name = "goal_gaps")
    private String goalGaps;

    @Column(name = "summary_status", nullable = false)
    @Enumerated(EnumType.STRING)
    private SummaryStatus summaryStatus = SummaryStatus.PENDING;

    @Column(name = "start_ref", nullable = false)
    private String startRef;

    @Column(name = "stop_ref")
    private String stopRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "stopped_at")
    private Instant stoppedAt;

    protected Session() {}

    public Session(UUID id, String projectPath, String startRef) {
        this.id = id;
        this.projectPath = projectPath;
        this.startRef = startRef;
        this.status = SessionStatus.ACTIVE;
        this.startedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getProjectPath() { return projectPath; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getGoalGaps() { return goalGaps; }
    public void setGoalGaps(String goalGaps) { this.goalGaps = goalGaps; }
    public SummaryStatus getSummaryStatus() { return summaryStatus; }
    public void setSummaryStatus(SummaryStatus summaryStatus) { this.summaryStatus = summaryStatus; }
    public String getStartRef() { return startRef; }
    public String getStopRef() { return stopRef; }
    public void setStopRef(String stopRef) { this.stopRef = stopRef; }
    public SessionStatus getStatus() { return status; }
    public void setStatus(SessionStatus status) { this.status = status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getStoppedAt() { return stoppedAt; }
    public void setStoppedAt(Instant stoppedAt) { this.stoppedAt = stoppedAt; }

    public enum SessionStatus { ACTIVE, COMPLETE, DISCARDED }
    public enum SummaryStatus { PENDING, COMPLETE, FAILED }
}
