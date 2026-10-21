package com.lore.api.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lore.change.Change;
import com.lore.change.Explanation;
import com.lore.change.TeachBackAttempt;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The teach-back rules and the state the page sees: tries used and left, the last verdict,
 * and the key points once they're revealed. Used by the grading endpoint and the session API,
 * so both always agree.
 */
final class TeachBackView {

    static final int MAX_ATTEMPTS = 3;
    /** Key points are shown after this many answers that didn't pass. */
    static final int REVEAL_AFTER_FAILS = 2;
    static final int MAX_ANSWER_LENGTH = 2000;

    private static final ObjectMapper mapper = new ObjectMapper();

    private TeachBackView() {}

    /** The tries that count; practice answers (after a pass) are left out. */
    static List<TeachBackAttempt> real(List<TeachBackAttempt> attempts) {
        return attempts.stream().filter(a -> !a.isPractice()).toList();
    }

    static boolean passed(List<TeachBackAttempt> attempts) {
        return real(attempts).stream().anyMatch(a -> a.getVerdict() == TeachBackAttempt.Verdict.PASS);
    }

    static boolean keyPointsRevealed(List<TeachBackAttempt> attempts) {
        long notPassed = real(attempts).stream().filter(a -> a.getVerdict() != TeachBackAttempt.Verdict.PASS).count();
        return passed(attempts) || notPassed >= REVEAL_AFTER_FAILS;
    }

    /** The key points saved with the explanation; empty if there are none (or no explanation). */
    static List<String> keyPoints(Explanation explanation) {
        if (explanation == null || explanation.getKeyPoints() == null) return List.of();
        try {
            List<String> points = mapper.readValue(explanation.getKeyPoints(), new TypeReference<>() {});
            return points == null ? List.of() : points;
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * attemptsUsed, attemptsLeft, lastVerdict ("pass" / "partly" / "fail" / null), keyPoints (null
     * until revealed). Counts real tries only; practice answers don't change any of it.
     */
    static Map<String, Object> state(List<TeachBackAttempt> attempts, Explanation explanation) {
        List<TeachBackAttempt> real = real(attempts);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("attemptsUsed", real.size());
        out.put("attemptsLeft", passed(attempts) ? 0 : Math.max(0, MAX_ATTEMPTS - real.size()));
        out.put("lastVerdict", real.isEmpty() ? null
                : real.get(real.size() - 1).getVerdict().name().toLowerCase());
        out.put("keyPoints", keyPointsRevealed(attempts) ? keyPoints(explanation) : null);
        return out;
    }

    /** "teach_back" / "self" / null, as the page reads it. */
    static String understoodBy(Change change) {
        return change.getUnderstoodBy() == null ? null : change.getUnderstoodBy().name().toLowerCase();
    }
}
