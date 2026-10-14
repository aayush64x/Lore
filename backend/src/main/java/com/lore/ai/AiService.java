package com.lore.ai;

import com.lore.ai.dto.ExplanationResponse;
import com.lore.ai.dto.SummaryResponse;

import java.util.List;

public interface AiService {

    SummaryResponse summarize(List<ChangeInput> changes, String reason);

    ExplanationResponse explain(String filePath, String diffContent);

    /**
     * One change sent to the AI. displayOrder is the change's stable id within the
     * session; the AI must echo it back so each description lands on the right change.
     */
    record ChangeInput(int displayOrder, String filePath, String changeType, String diffContent, boolean binary) {}
}
