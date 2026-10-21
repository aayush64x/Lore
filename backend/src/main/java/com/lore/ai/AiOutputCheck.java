package com.lore.ai;

import com.lore.ai.dto.ExplanationResponse;
import com.lore.ai.dto.SummaryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Catches AI answers that are valid JSON but have JSON characters leaked into a
 * text field, like "...matching the code.}]}". The model tried to close the JSON
 * early, and those characters ended up inside the text. Such answers are asked
 * for once more, and never saved.
 */
public final class AiOutputCheck {

    private static final Logger log = LoggerFactory.getLogger(AiOutputCheck.class);
    private static final String CLOSERS = "}]\",";

    private AiOutputCheck() {}

    /**
     * True if the text ends in two or more JSON closing characters (from } ] " ,)
     * with at least one } or ]. "}]}" and "\"}" count; "{}" or "the `}` key" don't.
     */
    public static boolean isDamaged(String text) {
        if (text == null) return false;
        String t = text.stripTrailing();
        int run = 0;
        boolean bracket = false;
        for (int i = t.length() - 1; i >= 0 && CLOSERS.indexOf(t.charAt(i)) >= 0; i--) {
            run++;
            if (t.charAt(i) == '}' || t.charAt(i) == ']') bracket = true;
        }
        return run >= 2 && bracket;
    }

    /** Names of the fields that look damaged, like "steps[1].text". Empty if all are fine. */
    public static List<String> problems(ExplanationResponse r) {
        List<String> bad = new ArrayList<>();
        if (r == null) return bad;
        if (isDamaged(r.overview())) bad.add("overview");
        if (r.steps() != null) {
            for (int i = 0; i < r.steps().size(); i++) {
                ExplanationResponse.Step s = r.steps().get(i);
                if (s != null && isDamaged(s.text())) bad.add("steps[" + i + "].text");
            }
        }
        if (r.concepts() != null) {
            for (int i = 0; i < r.concepts().size(); i++) {
                ExplanationResponse.Concept c = r.concepts().get(i);
                if (c == null) continue;
                if (isDamaged(c.name())) bad.add("concepts[" + i + "].name");
                if (isDamaged(c.meaning())) bad.add("concepts[" + i + "].meaning");
            }
        }
        return bad;
    }

    public static List<String> problems(SummaryResponse r) {
        List<String> bad = new ArrayList<>();
        if (r == null) return bad;
        if (isDamaged(r.sessionSummary())) bad.add("session_summary");
        if (r.changeDescriptions() != null) {
            for (int i = 0; i < r.changeDescriptions().size(); i++) {
                SummaryResponse.ChangeDescription d = r.changeDescriptions().get(i);
                if (d != null && isDamaged(d.description())) bad.add("change_descriptions[" + i + "].description");
            }
        }
        return bad;
    }

    /**
     * Calls the AI. If the answer has damaged fields, calls it once more. If that one
     * is damaged too, throws AiUnavailableException, so nothing broken gets saved.
     */
    public static <T> T askWithRetry(Supplier<T> call, Function<T, List<String>> problems) {
        T first = call.get();
        List<String> bad = problems.apply(first);
        if (bad.isEmpty()) return first;

        log.warn("AI answer had stray JSON characters in {}; asking again.", bad);
        T second = call.get();
        if (problems.apply(second).isEmpty()) return second;
        throw new AiUnavailableException("Claude's answer came back garbled twice. Try again.");
    }
}
