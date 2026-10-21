package com.lore.ai.dto;

import java.util.List;

/**
 * goalPartsNotDone is null when the goal wasn't checked (no goal given, or a provider
 * that doesn't check it), and an empty list when every part of the goal was done.
 */
public record SummaryResponse(
        String sessionSummary,
        List<ChangeDescription> changeDescriptions,
        List<String> goalPartsNotDone
) {
    /** Without a goal check. */
    public SummaryResponse(String sessionSummary, List<ChangeDescription> changeDescriptions) {
        this(sessionSummary, changeDescriptions, null);
    }

    /** goalMatch is "yes", "partly" or "no"; null (with goalReason) when the goal wasn't checked. */
    public record ChangeDescription(String filePath, int displayOrder, String description,
                                    String goalMatch, String goalReason) {
        public ChangeDescription(String filePath, int displayOrder, String description) {
            this(filePath, displayOrder, description, null, null);
        }
    }
}
