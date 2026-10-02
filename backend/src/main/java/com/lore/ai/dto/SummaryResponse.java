package com.lore.ai.dto;

import java.util.List;

public record SummaryResponse(
        String sessionSummary,
        List<ChangeDescription> changeDescriptions
) {
    public record ChangeDescription(String filePath, int displayOrder, String description) {}
}
