package com.lore.ai.dto;

import java.util.List;

/**
 * keyPoints: what someone must say to show they understand the change (for teach-back).
 * testable: false for changes with nothing to understand (imports, formatting, renames).
 * possibleIssue: a real edge case or bug in the change, or null; most changes have none.
 * Old providers leave keyPoints/testable/possibleIssue null.
 */
public record ExplanationResponse(
        String overview,
        List<Step> steps,
        List<Concept> concepts,
        List<String> keyPoints,
        Boolean testable,
        Issue possibleIssue
) {
    public ExplanationResponse(String overview, List<Step> steps, List<Concept> concepts) {
        this(overview, steps, concepts, null, null, null);
    }

    public record Step(String text, int diffLineStart, int diffLineEnd) {}
    public record Concept(String name, String meaning) {}
    /** diffLine is the numbered diff line it's on (same numbering as steps); 0 if the AI gave none. */
    public record Issue(int diffLine, String text) {}
}
