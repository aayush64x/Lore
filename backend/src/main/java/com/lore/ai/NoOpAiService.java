package com.lore.ai;

import com.lore.ai.dto.ExplanationResponse;
import com.lore.ai.dto.SummaryResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Used when AI is turned off (lore.ai.provider=none). Always fails, so sessions are saved with their summary
 * marked FAILED (and retryable) instead of storing placeholder text as a real summary.
 */
@Service
@ConditionalOnProperty(name = "lore.ai.provider", havingValue = "none")
public class NoOpAiService implements AiService {

    static final String MESSAGE = "AI is not set up.";

    @Override
    public SummaryResponse summarize(List<ChangeInput> changes, String reason) {
        throw new AiUnavailableException(MESSAGE);
    }

    @Override
    public ExplanationResponse explain(String filePath, String diffContent, String reason, UserProfile profile) {
        throw new AiUnavailableException(MESSAGE);
    }
}
