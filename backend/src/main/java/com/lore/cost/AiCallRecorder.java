package com.lore.cost;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Saves one AiCall row per Claude API call. The code that asks the AI says which session
 * and job the call is for with during(); the AI provider reports each response's token
 * counts with record(). Saving a row never gets in the way of the AI answer: a failure is
 * logged and the answer is still returned.
 */
@Component
public class AiCallRecorder {

    private static final Logger log = LoggerFactory.getLogger(AiCallRecorder.class);

    /** The session and job of the call running on this thread. Set only inside during(). */
    private static final ThreadLocal<Tag> CURRENT = new ThreadLocal<>();

    private record Tag(UUID sessionId, AiCall.Job job) {}

    private final AiCallRepository repository;

    public AiCallRecorder(AiCallRepository repository) {
        this.repository = repository;
    }

    /**
     * Runs call with every API call it makes on this thread counted for sessionId and job.
     * Work on another thread (like a summary group) must call during() itself.
     */
    public <T> T during(UUID sessionId, AiCall.Job job, Supplier<T> call) {
        Tag outer = CURRENT.get();
        CURRENT.set(new Tag(sessionId, job));
        try {
            return call.get();
        } finally {
            if (outer == null) CURRENT.remove();
            else CURRENT.set(outer);
        }
    }

    /** Saves the token counts of one API response. Does nothing outside during(); never throws. */
    public void record(String model, long inputTokens, long outputTokens, long cacheReadTokens, long cacheWriteTokens) {
        Tag tag = CURRENT.get();
        if (tag == null) return;
        try {
            repository.save(new AiCall(tag.sessionId(), tag.job(), model,
                    inputTokens, outputTokens, cacheReadTokens, cacheWriteTokens));
        } catch (RuntimeException e) {
            log.warn("Couldn't save the cost of an AI call for session {}: {}", tag.sessionId(), e.getMessage());
        }
    }
}
