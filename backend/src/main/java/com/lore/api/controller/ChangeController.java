package com.lore.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lore.ai.AiOutputCheck;
import com.lore.ai.AiService;
import com.lore.ai.AiUnavailableException;
import com.lore.ai.ApiKeyStore;
import com.lore.ai.UserProfile;
import com.lore.ai.dto.ExplanationResponse;
import com.lore.change.Change;
import com.lore.change.ChangeRepository;
import com.lore.change.Explanation;
import com.lore.change.ExplanationRepository;
import com.lore.session.Session;
import com.lore.session.SessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ChangeController {

    private static final Logger log = LoggerFactory.getLogger(ChangeController.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final ChangeRepository changeRepository;
    private final ExplanationRepository explanationRepository;
    private final SessionRepository sessionRepository;
    private final AiService aiService;
    private final ApiKeyStore settings;

    public ChangeController(ChangeRepository changeRepository,
                            ExplanationRepository explanationRepository,
                            SessionRepository sessionRepository,
                            AiService aiService,
                            ApiKeyStore settings) {
        this.changeRepository = changeRepository;
        this.explanationRepository = explanationRepository;
        this.sessionRepository = sessionRepository;
        this.aiService = aiService;
        this.settings = settings;
    }

    @PutMapping("/changes/{id}/understood")
    public Map<String, Object> toggleUnderstood(@PathVariable UUID id) {
        Change change = changeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        // By hand (or "Skip test") always counts as self-marked; unmarking clears it.
        change.markUnderstood(change.isUnderstood() ? null : Change.UnderstoodBy.SELF);
        changeRepository.save(change);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("understood", change.isUnderstood());
        out.put("understoodBy", TeachBackView.understoodBy(change));
        return out;
    }

    @PostMapping("/changes/{id}/explain")
    public Map<String, Object> explain(@PathVariable UUID id) {
        Change change = changeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (change.isBinary() || change.getDiffContent() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Binary files can't be explained.");
        }

        UserProfile profile = settings.profile().orElse(UserProfile.DEFAULT);
        var existing = explanationRepository.findByChangeId(id);
        if (existing.isPresent() && ExplanationView.isCurrent(existing.get(), profile)) {
            return ExplanationView.toMap(existing.get(), profile);
        }

        Explanation explanation = existing.orElseGet(() -> new Explanation(UUID.randomUUID(), change));
        // getId() on the lazy session doesn't load it; look it up for the reason.
        String reason = sessionRepository.findById(change.getSession().getId())
                .map(Session::getReason).orElse(null);

        try {
            // Asked once more if a text field comes back with stray JSON characters.
            ExplanationResponse result = AiOutputCheck.askWithRetry(
                    () -> aiService.explain(change.getFilePath(), change.getDiffContent(), reason, profile),
                    AiOutputCheck::problems);
            if (result == null || result.overview() == null || result.overview().isBlank()) {
                throw new AiUnavailableException("AI returned an empty explanation.");
            }

            explanation.setOverview(result.overview());
            explanation.setSteps(mapper.writeValueAsString(stepsForStorage(result.steps())));
            explanation.setConcepts(mapper.writeValueAsString(conceptsForStorage(result.concepts())));
            // Set every time (null when missing), so explaining again never keeps an old value.
            explanation.setKeyPoints(result.keyPoints() == null ? null : mapper.writeValueAsString(result.keyPoints()));
            explanation.setTestable(result.testable());
            explanation.setPossibleIssue(issueForStorage(result.possibleIssue(), change.getDiffContent()));
            explanation.setProfile(profile.key());
            explanation.setStatus(Explanation.ExplanationStatus.COMPLETE);
        } catch (AiUnavailableException e) {
            log.warn("AI explanation not saved for change {}: {}", id, e.getMessage());
            explanation.setStatus(Explanation.ExplanationStatus.FAILED);
            explanationRepository.save(explanation);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        } catch (Exception e) {
            log.error("AI explanation failed for change {}", id, e);
            explanation.setStatus(Explanation.ExplanationStatus.FAILED);
            explanationRepository.save(explanation);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Explanation failed");
        }

        explanationRepository.save(explanation);
        return ExplanationView.toMap(explanation, profile);
    }

    /**
     * Steps are stored and sent with the snake_case keys the frontend reads
     * (diff_line_start, diff_line_end). The Step record itself would serialize as
     * diffLineStart/diffLineEnd, which the page can't see.
     */
    static List<Map<String, Object>> stepsForStorage(List<ExplanationResponse.Step> steps) {
        if (steps == null) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (ExplanationResponse.Step step : steps) {
            if (step == null) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("text", step.text() == null ? "" : step.text());
            m.put("diff_line_start", step.diffLineStart());
            m.put("diff_line_end", step.diffLineEnd());
            out.add(m);
        }
        return out;
    }

    /**
     * {"diff_line": n, "text": "..."} or null. A line outside the diff becomes 0 (no line),
     * so the page shows the issue on the change instead of on a wrong line.
     */
    static String issueForStorage(ExplanationResponse.Issue issue, String diffContent) throws Exception {
        if (issue == null || issue.text() == null || issue.text().isBlank()) return null;
        int lines = diffContent == null ? 0 : diffContent.split("\n", -1).length;
        int line = issue.diffLine() >= 1 && issue.diffLine() <= lines ? issue.diffLine() : 0;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("diff_line", line);
        m.put("text", issue.text().strip());
        return mapper.writeValueAsString(m);
    }

    static List<Map<String, Object>> conceptsForStorage(List<ExplanationResponse.Concept> concepts) {
        if (concepts == null) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (ExplanationResponse.Concept concept : concepts) {
            if (concept == null) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", concept.name() == null ? "" : concept.name());
            m.put("meaning", concept.meaning() == null ? "" : concept.meaning());
            out.add(m);
        }
        return out;
    }
}
