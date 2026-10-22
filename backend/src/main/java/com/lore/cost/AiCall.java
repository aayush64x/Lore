package com.lore.cost;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** One Claude API call: which session and job it was for, the model, and its token counts. */
@Entity
@Table(name = "ai_calls")
public class AiCall {

    public enum Job { SUMMARY, EXPLAIN, GRADING, FOLLOW_UP }

    @Id
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Job job;

    @Column(nullable = false)
    private String model;

    /** Input tokens not read from or written to the cache. */
    @Column(name = "input_tokens", nullable = false)
    private long inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private long outputTokens;

    @Column(name = "cache_read_tokens", nullable = false)
    private long cacheReadTokens;

    @Column(name = "cache_write_tokens", nullable = false)
    private long cacheWriteTokens;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AiCall() {}

    public AiCall(UUID sessionId, Job job, String model, long inputTokens, long outputTokens,
                  long cacheReadTokens, long cacheWriteTokens) {
        this.id = UUID.randomUUID();
        this.sessionId = sessionId;
        this.job = job;
        this.model = model;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.cacheReadTokens = cacheReadTokens;
        this.cacheWriteTokens = cacheWriteTokens;
        this.createdAt = Instant.now();
    }

    public UUID getSessionId() { return sessionId; }
    public Job getJob() { return job; }
    public String getModel() { return model; }
    public long getInputTokens() { return inputTokens; }
    public long getOutputTokens() { return outputTokens; }
    public long getCacheReadTokens() { return cacheReadTokens; }
    public long getCacheWriteTokens() { return cacheWriteTokens; }

    public long totalTokens() {
        return inputTokens + outputTokens + cacheReadTokens + cacheWriteTokens;
    }
}
