package com.lore.api.controller;

import com.lore.ai.AiOutputCheck;
import com.lore.ai.AiService;
import com.lore.ai.AiUnavailableException;
import com.lore.ai.ApiKeyStore;
import com.lore.ai.dto.GradeResponse;
import com.lore.change.Change;
import com.lore.change.Explanation;
import com.lore.change.ExplanationRepository;
import com.lore.concept.ChangeConcept;
import com.lore.concept.ChangeConceptRepository;
import com.lore.concept.ConceptTags;
import com.lore.cost.AiCall;
import com.lore.cost.AiCallRecorder;
import com.lore.review.LoreClock;
import com.lore.review.ReviewItem;
import com.lore.review.ReviewItemRepository;
import com.lore.review.ReviewSchedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The review queue: understood changes that are due to come back (see ReviewSchedule).
 * A review asks one question, graded like a teach-back answer (Haiku), against the
 * change's key points, or its one-line summary if it was never explained.
 */
@RestController
@RequestMapping("/api")
public class ReviewController {

    private static final Logger log = LoggerFactory.getLogger(ReviewController.class);

    static final String QUESTION = "In your own words, what does this change do, and why?";
    /** In ~/.lore/config.properties: shows the "pretend N days passed" control, for demos. */
    static final String DEMO_SETTING = "demo.time-travel";

    private final ReviewItemRepository reviewRepository;
    private final ReviewSchedule schedule;
    private final LoreClock clock;
    private final ChangeConceptRepository conceptRepository;
    private final ExplanationRepository explanationRepository;
    private final AiService aiService;
    private final AiCallRecorder recorder;
    private final ApiKeyStore settings;

    public ReviewController(ReviewItemRepository reviewRepository, ReviewSchedule schedule, LoreClock clock,
                            ChangeConceptRepository conceptRepository, ExplanationRepository explanationRepository,
                            AiService aiService, AiCallRecorder recorder, ApiKeyStore settings) {
        this.reviewRepository = reviewRepository;
        this.schedule = schedule;
        this.clock = clock;
        this.conceptRepository = conceptRepository;
        this.explanationRepository = explanationRepository;
        this.aiService = aiService;
        this.recorder = recorder;
        this.settings = settings;
    }

    /**
     * {question, due: [...], dueCount, upcomingCount, doneCount, demo: {enabled, daysAhead}}.
     * Each due item has the change (file, summary, diff), its session's title, its
     * concepts, and the gap it's on.
     */
    @GetMapping("/review")
    public Map<String, Object> getReview(@RequestParam String project) {
        List<ReviewItem> items = reviewRepository.findInProject(project);
        Map<UUID, List<ConceptTags.Tag>> concepts = conceptsByChange(project);

        List<Map<String, Object>> due = new ArrayList<>();
        int upcoming = 0, done = 0;
        for (ReviewItem item : items) {
            if (item.getDoneAt() != null) done++;
            else if (schedule.isDue(item)) due.add(itemView(item, concepts));
            else upcoming++;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("question", QUESTION);
        out.put("due", due);
        out.put("dueCount", due.size());
        out.put("upcomingCount", upcoming);
        out.put("doneCount", done);
        out.put("demo", Map.of("enabled", demoEnabled(), "daysAhead", clock.daysAhead()));
        return out;
    }

    /** {due: n}, for the badge in the navbar. */
    @GetMapping("/review/count")
    public Map<String, Object> getReviewCount(@RequestParam String project) {
        long due = reviewRepository.findInProject(project).stream().filter(schedule::isDue).count();
        return Map.of("due", due);
    }

    public record ReviewAnswer(String answer) {}

    /**
     * 200: {verdict, gotRight, missing, done, gapDays, nextDueAt}.
     * 400: empty or too-long answer. 404: not in the review queue. 409: not due yet.
     * 503: the grader couldn't be reached or answered badly; the review stays due.
     */
    @PostMapping("/review/{changeId}")
    public ResponseEntity<Map<String, Object>> answer(@PathVariable UUID changeId,
                                                      @RequestBody(required = false) ReviewAnswer body) {
        ReviewItem item = reviewRepository.findByChangeId(changeId).orElse(null);
        if (item == null) return error(HttpStatus.NOT_FOUND, "This change isn't in the review queue.");
        if (!schedule.isDue(item)) return error(HttpStatus.CONFLICT, "This review isn't due yet.");

        String answer = body == null || body.answer() == null ? "" : body.answer().strip();
        if (answer.isEmpty()) return error(HttpStatus.BAD_REQUEST, "Write your answer first.");
        if (answer.length() > TeachBackView.MAX_ANSWER_LENGTH) {
            return error(HttpStatus.BAD_REQUEST, "Keep it under " + TeachBackView.MAX_ANSWER_LENGTH + " characters.");
        }

        Change change = item.getChange();
        List<String> keyPoints = keyPoints(change);
        GradeResponse grade;
        try {
            grade = recorder.during(change.getSession().getId(), AiCall.Job.GRADING, () -> AiOutputCheck.askWithRetry(
                    () -> aiService.grade(change.getDiffContent(), keyPoints, answer), AiOutputCheck::problems));
        } catch (AiUnavailableException e) {
            return error(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        } catch (Exception e) {
            log.error("Review grading failed for change {}", changeId, e);
            return error(HttpStatus.SERVICE_UNAVAILABLE, "Couldn't check your answer. Try again.");
        }

        ReviewSchedule.Result result = switch (grade.verdict()) {
            case "pass" -> ReviewSchedule.Result.PASS;
            case "partly" -> ReviewSchedule.Result.PARTLY;
            default -> ReviewSchedule.Result.FAIL;
        };
        ReviewItem next = schedule.record(item, result);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("verdict", grade.verdict());
        out.put("gotRight", grade.gotRight());
        out.put("missing", grade.missing());
        out.put("done", next.getDoneAt() != null);
        out.put("gapDays", ReviewSchedule.gapDays(next));
        out.put("nextDueAt", next.getDueAt());
        return ResponseEntity.ok(out);
    }

    /** Demo only: moves the review clock forward. 404 unless demo.time-travel=true. */
    @PostMapping("/demo/days-passed")
    public Map<String, Object> pretendDaysPassed(@RequestParam int days) {
        requireDemo();
        if (days < 1 || days > 365) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "1 to 365 days.");
        clock.pretendDaysPassed(days);
        return Map.of("daysAhead", clock.daysAhead());
    }

    /** Demo only: puts the review clock back to the real time. */
    @PostMapping("/demo/reset")
    public Map<String, Object> resetClock() {
        requireDemo();
        clock.reset();
        return Map.of("daysAhead", clock.daysAhead());
    }

    private boolean demoEnabled() {
        return settings.setting(DEMO_SETTING).map("true"::equalsIgnoreCase).orElse(false);
    }

    private void requireDemo() {
        if (!demoEnabled()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    /** The explanation's key points; if it was never explained, its one-line summary stands in. */
    private List<String> keyPoints(Change change) {
        Explanation explanation = explanationRepository.findByChangeId(change.getId())
                .filter(e -> e.getStatus() == Explanation.ExplanationStatus.COMPLETE)
                .orElse(null);
        List<String> keyPoints = TeachBackView.keyPoints(explanation);
        if (!keyPoints.isEmpty()) return keyPoints;
        String description = change.getDescription();
        return List.of(description != null && !description.isBlank()
                ? description
                : "What the change in the diff does, and why");
    }

    private Map<UUID, List<ConceptTags.Tag>> conceptsByChange(String project) {
        Map<UUID, List<ConceptTags.Tag>> out = new HashMap<>();
        for (ChangeConcept cc : conceptRepository.findInProject(project)) {
            out.computeIfAbsent(cc.getChange().getId(), k -> new ArrayList<>())
                    .add(new ConceptTags.Tag(cc.getName(), cc.isOther()));
        }
        out.values().forEach(tags -> tags.sort(
                Comparator.comparing(ConceptTags.Tag::other).thenComparing(ConceptTags.Tag::name)));
        return out;
    }

    private static Map<String, Object> itemView(ReviewItem item, Map<UUID, List<ConceptTags.Tag>> concepts) {
        Change c = item.getChange();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("changeId", c.getId());
        m.put("sessionId", c.getSession().getId());
        m.put("sessionReason", c.getSession().getReason());
        m.put("filePath", c.getFilePath());
        m.put("changeType", c.getChangeType().toString());
        m.put("description", c.getDescription());
        m.put("diffContent", c.getDiffContent());
        m.put("oldStart", c.getOldStart());
        m.put("newStart", c.getNewStart());
        m.put("concepts", concepts.getOrDefault(c.getId(), List.of()));
        m.put("gapDays", ReviewSchedule.gapDays(item));
        m.put("dueAt", item.getDueAt());
        return m;
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }
}
