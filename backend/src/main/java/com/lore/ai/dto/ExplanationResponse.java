package com.lore.ai.dto;

import java.util.List;

public record ExplanationResponse(
        String overview,
        List<Step> steps,
        List<Concept> concepts
) {
    public record Step(String text, int diffLineStart, int diffLineEnd) {}
    public record Concept(String name, String meaning) {}
}
