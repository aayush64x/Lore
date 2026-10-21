package com.lore.ai;

import com.lore.ai.dto.ExplanationResponse;
import com.lore.ai.dto.GradeResponse;
import com.lore.ai.dto.SummaryResponse;

import java.util.List;

public interface AiService {

    SummaryResponse summarize(List<ChangeInput> changes, String reason);

    /** reason is what the user said they were trying to do (may be null). */
    ExplanationResponse explain(String filePath, String diffContent, String reason, UserProfile profile);

    /** Message shown when a provider can't grade teach-back answers. */
    String TEACH_BACK_ANTHROPIC_ONLY = "Teach-back only works with the Anthropic provider.";

    /**
     * Grades a teach-back answer against the change's key points. Gets only the diff, the
     * key points and the answer. Only the Anthropic provider grades; others say so.
     */
    default GradeResponse grade(String diffContent, List<String> keyPoints, String answer) {
        throw new AiUnavailableException(TEACH_BACK_ANTHROPIC_ONLY);
    }

    /**
     * One change sent to the AI. displayOrder is the change's stable id within the
     * session; the AI must echo it back so each description lands on the right change.
     */
    record ChangeInput(int displayOrder, String filePath, String changeType, String diffContent, boolean binary) {}
}
