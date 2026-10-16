package com.lore.api.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lore.ai.AiService;
import com.lore.ai.AiUnavailableException;
import com.lore.ai.dto.ExplanationResponse;
import com.lore.change.Change;
import com.lore.change.ChangeRepository;
import com.lore.change.Explanation;
import com.lore.change.ExplanationRepository;
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
    private final AiService aiService;

    public ChangeController(ChangeRepository changeRepository,
                            ExplanationRepository explanationRepository,
                            AiService aiService) {
        this.changeRepository = changeRepository;
        this.explanationRepository = explanationRepository;
        this.aiService = aiService;
    }

    @PutMapping("/changes/{id}/understood")
    public Map<String, Boolean> toggleUnderstood(@PathVariable UUID id) {
        Change change = changeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        change.setUnderstood(!change.isUnderstood());
        changeRepository.save(change);

        return Map.of("understood", change.isUnderstood());
    }

    @PostMapping("/changes/{id}/explain")
    public Map<String, Object> explain(@PathVariable UUID id) {
        Change change = changeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (change.isBinary() || change.getDiffContent() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Binary files can't be explained.");
        }

        var existing = explanationRepository.findByChangeId(id);
        if (existing.isPresent() && existing.get().getStatus() == Explanation.ExplanationStatus.COMPLETE) {
            return buildExplanationMap(existing.get());
        }

        Explanation explanation = existing.orElseGet(() -> new Explanation(UUID.randomUUID(), change));

        try {
            ExplanationResponse result = aiService.explain(change.getFilePath(), change.getDiffContent());
            if (result == null || result.overview() == null || result.overview().isBlank()) {
                throw new AiUnavailableException("AI returned an empty explanation.");
            }

            explanation.setOverview(result.overview());
            explanation.setSteps(mapper.writeValueAsString(stepsForStorage(result.steps())));
            explanation.setConcepts(mapper.writeValueAsString(conceptsForStorage(result.concepts())));
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
        return buildExplanationMap(explanation);
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

    private Map<String, Object> buildExplanationMap(Explanation explanation) {
        try {
            List<Map<String, Object>> steps = readList(explanation.getSteps());
            // Older rows were saved with camelCase step keys; send them in the shape the page reads.
            for (Map<String, Object> step : steps) {
                renameKey(step, "diffLineStart", "diff_line_start");
                renameKey(step, "diffLineEnd", "diff_line_end");
            }
            List<Map<String, Object>> concepts = readList(explanation.getConcepts());

            return Map.of(
                    "overview", explanation.getOverview() == null ? "" : explanation.getOverview(),
                    "steps", steps,
                    "concepts", concepts,
                    "status", explanation.getStatus().toString());
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
