package com.lore.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lore.ai.AiOutputCheck;
import com.lore.ai.AiService;
import com.lore.ai.AiUnavailableException;
import com.lore.ai.dto.GradeResponse;
import com.lore.change.Change;
import com.lore.change.ChangeRepository;
import com.lore.change.Explanation;
import com.lore.change.ExplanationRepository;
import com.lore.change.TeachBackAttempt;
import com.lore.change.TeachBackAttemptRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Teach-back: the user explains a change in their own words and the grading model checks it
 * against the explanation's key points. Every rule is enforced here, never by the page:
 * 3 tries per change, key points revealed after 2 that didn't pass, and a pass marks the
 * change understood (by teach-back). A failed AI call doesn't use up a try. Once passed,
 * further answers are practice: graded the same, no limit, and the pass always stays.
 */
@RestController
@RequestMapping("/api")
public class TeachBackController {

    private static final Logger log = LoggerFactory.getLogger(TeachBackController.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final ChangeRepository changeRepository;
    private final ExplanationRepository explanationRepository;
    private final TeachBackAttemptRepository attemptRepository;
    private final AiService aiService;

    public TeachBackController(ChangeRepository changeRepository,
                               ExplanationRepository explanationRepository,
                               TeachBackAttemptRepository attemptRepository,
                               AiService aiService) {
        this.changeRepository = changeRepository;
        this.explanationRepository = explanationRepository;
        this.attemptRepository = attemptRepository;
        this.aiService = aiService;
    }

    public record TeachBackRequest(String answer) {}

    /**
     * 200: {verdict, gotRight, missing, practice, attemptsUsed, attemptsLeft, lastVerdict, keyPoints, understood, understoodBy}
     * 400: empty or too-long answer. 404: no such change.
     * 409: nothing to test, explain again first, or no tries left (body says which).
     * 503: the grader couldn't be reached or answered badly; no try is used.
     */
    @PostMapping("/changes/{id}/teach-back")
    public ResponseEntity<Map<String, Object>> teachBack(@PathVariable UUID id, @RequestBody(required = false) TeachBackRequest body) {
        Change change = changeRepository.findById(id).orElse(null);
        if (change == null) return error(HttpStatus.NOT_FOUND, "Change not found.", null, null);

        Explanation explanation = explanationRepository.findByChangeId(id)
                .filter(e -> e.getStatus() == Explanation.ExplanationStatus.COMPLETE)
                .orElse(null);
        List<TeachBackAttempt> attempts = attemptRepository.findByChangeIdOrderByCreatedAt(id);

        boolean practice = change.getUnderstoodBy() == Change.UnderstoodBy.TEACH_BACK || TeachBackView.passed(attempts);
        if (explanation != null && Boolean.FALSE.equals(explanation.getTestable())) {
            return error(HttpStatus.CONFLICT, "This change has nothing to test. Mark it understood instead.", attempts, explanation);
        }
        List<String> keyPoints = TeachBackView.keyPoints(explanation);
        if (keyPoints.isEmpty()) {
            return error(HttpStatus.CONFLICT, "Explain again to enable testing.", attempts, explanation);
        }
        if (!practice && TeachBackView.real(attempts).size() >= TeachBackView.MAX_ATTEMPTS) {
            return error(HttpStatus.CONFLICT, "No tries left. The key points are shown below.", attempts, explanation);
        }

        String answer = body == null || body.answer() == null ? "" : body.answer().strip();
        if (answer.isEmpty()) {
            return error(HttpStatus.BAD_REQUEST, "Write your answer first.", attempts, explanation);
        }
        if (answer.length() > TeachBackView.MAX_ANSWER_LENGTH) {
            return error(HttpStatus.BAD_REQUEST, "Keep it under " + TeachBackView.MAX_ANSWER_LENGTH + " characters.", attempts, explanation);
        }

        GradeResponse grade;
        TeachBackAttempt.Verdict verdict;
        try {
            // Only the diff, the key points and the answer go to the grader.
            grade = AiOutputCheck.askWithRetry(
                    () -> aiService.grade(change.getDiffContent(), keyPoints, answer),
                    AiOutputCheck::problems);
            verdict = TeachBackAttempt.Verdict.valueOf(grade.verdict().toUpperCase());
        } catch (AiUnavailableException e) {
            log.warn("Teach-back not graded for change {}: {}", id, e.getMessage());
            return error(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage(), attempts, explanation);
        } catch (Exception e) {
            log.error("Teach-back grading failed for change {}", id, e);
            return error(HttpStatus.SERVICE_UNAVAILABLE, "Grading failed. Try again.", attempts, explanation);
        }

        try {
            attemptRepository.save(new TeachBackAttempt(UUID.randomUUID(), change, answer, verdict,
                    mapper.writeValueAsString(grade.gotRight()), mapper.writeValueAsString(grade.missing()), grade.model(),
                    practice));
        } catch (Exception e) {
            throw new IllegalStateException("Could not save the teach-back attempt", e);
        }
        if (verdict == TeachBackAttempt.Verdict.PASS && !practice) {
            change.markUnderstood(Change.UnderstoodBy.TEACH_BACK);
            changeRepository.save(change);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("verdict", verdict.name().toLowerCase());
        out.put("practice", practice);
        out.put("gotRight", grade.gotRight());
        // A pass needs no hints.
        out.put("missing", verdict == TeachBackAttempt.Verdict.PASS ? List.of() : grade.missing());
        out.putAll(TeachBackView.state(attemptRepository.findByChangeIdOrderByCreatedAt(id), explanation));
        out.put("understood", change.isUnderstood());
        out.put("understoodBy", TeachBackView.understoodBy(change));
        return ResponseEntity.ok(out);
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String message,
                                                             List<TeachBackAttempt> attempts, Explanation explanation) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("error", message);
        if (attempts != null) out.putAll(TeachBackView.state(attempts, explanation));
        return ResponseEntity.status(status).body(out);
    }
}
