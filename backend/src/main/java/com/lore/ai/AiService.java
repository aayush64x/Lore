package com.lore.ai;

import com.lore.ai.dto.ExplanationResponse;
import com.lore.ai.dto.SummaryResponse;

import java.util.List;

public interface AiService {

    SummaryResponse summarize(List<ChangeInput> changes, String reason);

    ExplanationResponse explain(String filePath, String diffContent);

    record ChangeInput(String filePath, String changeType, String diffContent, boolean binary) {}
}
