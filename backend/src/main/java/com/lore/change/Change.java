package com.lore.change;

import com.lore.session.Session;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "changes")
public class Change {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private Session session;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "change_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private ChangeType changeType;

    @Column(name = "old_file_path")
    private String oldFilePath;

    @Column(name = "old_start")
    private int oldStart;

    @Column(name = "old_count")
    private int oldCount;

    @Column(name = "new_start")
    private int newStart;

    @Column(name = "new_count")
    private int newCount;

    @Column(name = "diff_content")
    private String diffContent;

    private String description;

    /** Whether this change serves the goal given at lore stop; null if not checked. */
    @Enumerated(EnumType.STRING)
    @Column(name = "goal_match")
    private GoalMatch goalMatch;

    @Column(name = "goal_reason")
    private String goalReason;

    @Column(name = "is_binary", nullable = false)
    private boolean binary;

    @Column(nullable = false)
    private boolean understood;

    /** How it became understood; null while not understood. */
    @Enumerated(EnumType.STRING)
    @Column(name = "understood_by")
    private UnderstoodBy understoodBy;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected Change() {}

    public Change(UUID id, Session session, String filePath, ChangeType changeType) {
        this.id = id;
        this.session = session;
        this.filePath = filePath;
        this.changeType = changeType;
    }

    public UUID getId() { return id; }
    public Session getSession() { return session; }
    public String getFilePath() { return filePath; }
    public ChangeType getChangeType() { return changeType; }
    public String getOldFilePath() { return oldFilePath; }
    public void setOldFilePath(String oldFilePath) { this.oldFilePath = oldFilePath; }
    public int getOldStart() { return oldStart; }
    public void setOldStart(int oldStart) { this.oldStart = oldStart; }
    public int getOldCount() { return oldCount; }
    public void setOldCount(int oldCount) { this.oldCount = oldCount; }
    public int getNewStart() { return newStart; }
    public void setNewStart(int newStart) { this.newStart = newStart; }
    public int getNewCount() { return newCount; }
    public void setNewCount(int newCount) { this.newCount = newCount; }
    public String getDiffContent() { return diffContent; }
    public void setDiffContent(String diffContent) { this.diffContent = diffContent; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public GoalMatch getGoalMatch() { return goalMatch; }
    public void setGoalMatch(GoalMatch goalMatch) { this.goalMatch = goalMatch; }
    public String getGoalReason() { return goalReason; }
    public void setGoalReason(String goalReason) { this.goalReason = goalReason; }
    public boolean isBinary() { return binary; }
    public void setBinary(boolean binary) { this.binary = binary; }
    public boolean isUnderstood() { return understood; }
    public void setUnderstood(boolean understood) { this.understood = understood; }
    public UnderstoodBy getUnderstoodBy() { return understoodBy; }

    /** Understood, and how; or not understood (by = null). */
    public void markUnderstood(UnderstoodBy by) {
        this.understood = by != null;
        this.understoodBy = by;
    }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }

    public enum ChangeType { ADDED, MODIFIED, DELETED, RENAMED }

    public enum GoalMatch { YES, PARTLY, NO }

    /** TEACH_BACK = passed a teach-back; SELF = marked by hand or skipped the test. */
    public enum UnderstoodBy { TEACH_BACK, SELF }
}
