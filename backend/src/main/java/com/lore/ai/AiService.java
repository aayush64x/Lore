package com.lore.ai;

import com.lore.ai.dto.ExplanationResponse;
import com.lore.ai.dto.GradeResponse;
import com.lore.ai.dto.SummaryResponse;

import java.util.List;

public interface AiService {

    SummaryResponse summarize(List<ChangeInput> changes, String reason);

    /**
     * For a session too big for one summary call: joins the summaries of its parts (each
     * from summarize) into one session summary, plus the goal check when reason is given.
     * Gets no diffs. The answer's changeDescriptions are empty.
     */
    default SummaryResponse combineSummaries(List<SummaryResponse> parts, String reason) {
        throw new AiUnavailableException("Sessions this big can only be summarized with the Anthropic provider.");
    }

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

    /** One earlier question and answer about the same change, for context. */
    record QuestionAndAnswer(String question, String answer) {}

    /** model is the model that answered (saved with the answer). */
    record FollowUpAnswer(String answer, String model) {}

    /**
     * Answers a follow-up question about one change, in simple language for a beginner.
     * explanation is the change's saved explanation as text ("" if it has none); earlier
     * is what was asked about this change before, oldest first.
     */
    default FollowUpAnswer answerQuestion(String filePath, String diffContent, String explanation,
                                          List<QuestionAndAnswer> earlier, String question) {
        throw new AiUnavailableException("Follow-up questions only work with the Anthropic provider.");
    }

    /**
     * One change sent to the AI. displayOrder is the change's stable id within the
     * session; the AI must echo it back so each description lands on the right change.
     */
    record ChangeInput(int displayOrder, String filePath, String changeType, String diffContent, boolean binary) {}
}
