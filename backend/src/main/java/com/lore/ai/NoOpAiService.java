package com.lore.ai;

import com.lore.ai.dto.SummaryResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@ConditionalOnProperty(name = "lore.ai.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpAiService implements AiService {

    @Override
    public SummaryResponse summarize(List<ChangeInput> changes, String reason) {
        List<SummaryResponse.ChangeDescription> descriptions = changes.stream()
                .map((ChangeInput c) -> new SummaryResponse.ChangeDescription(
                        c.filePath(), 0, c.changeType() + " " + c.filePath()))
                .toList();
        return new SummaryResponse("AI summary not available — configure AWS credentials to enable.", descriptions);
    }
}
