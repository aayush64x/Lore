package com.lore.api.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lore.ai.UserProfile;
import com.lore.change.Explanation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A saved explanation in the shape the page reads. Used by POST /changes/{id}/explain
 * and by GET /sessions/{id}, which sends each change's saved explanation so the
 * page can show it without another click.
 */
final class ExplanationView {

    private static final ObjectMapper mapper = new ObjectMapper();

    private ExplanationView() {}

    /**
     * A saved explanation is reused unless it failed or was written for a different
     * profile. Rows from before profiles existed (null) are kept, to avoid paying for
     * new explanations nobody asked for.
     */
    static boolean isCurrent(Explanation explanation, UserProfile profile) {
        return explanation.getStatus() == Explanation.ExplanationStatus.COMPLETE
                && (explanation.getProfile() == null || explanation.getProfile().equals(profile.key()));
    }

    /**
     * overview, steps, concepts, status, plus:
     * current: false if it was written for another profile (the page then offers "Explain again"),
     * writtenFor: that profile's level, like "New to coding" (null for old rows).
     */
    static Map<String, Object> toMap(Explanation explanation, UserProfile profile) {
        try {
            List<Map<String, Object>> steps = readList(explanation.getSteps());
            // Older rows were saved with camelCase step keys; send them in the shape the page reads.
            for (Map<String, Object> step : steps) {
                renameKey(step, "diffLineStart", "diff_line_start");
                renameKey(step, "diffLineEnd", "diff_line_end");
            }
            List<Map<String, Object>> concepts = readList(explanation.getConcepts());

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("overview", explanation.getOverview() == null ? "" : explanation.getOverview());
            out.put("steps", steps);
            out.put("concepts", concepts);
            out.put("status", explanation.getStatus().toString());
            out.put("current", isCurrent(explanation, profile));
            out.put("writtenFor", UserProfile.fromKey(explanation.getProfile())
                    .map(p -> p.level().label())
                    .orElse(null));
            return out;
        } catch (Exception e) {
            throw new RuntimeException("Failed to read explanation data", e);
        }
    }

    private static List<Map<String, Object>> readList(String json) throws Exception {
        if (json == null || json.isBlank()) return new ArrayList<>();
        List<Map<String, Object>> list = mapper.readValue(json, new TypeReference<>() {});
        return list == null ? new ArrayList<>() : new ArrayList<>(list);
    }

    private static void renameKey(Map<String, Object> map, String from, String to) {
        if (map.containsKey(from) && !map.containsKey(to)) {
            map.put(to, map.remove(from));
        }
    }
}
