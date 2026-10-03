package com.lore.api.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lore.ai.AiService;
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

        var existing = explanationRepository.findByChangeId(id);
        if (existing.isPresent() && existing.get().getStatus() == Explanation.ExplanationStatus.COMPLETE) {
            return buildExplanationMap(existing.get());
        }

        Explanation explanation = existing.orElseGet(() -> new Explanation(UUID.randomUUID(), change));

        try {
            ExplanationResponse result = aiService.explain(change.getFilePath(), change.getDiffContent());

            explanation.setOverview(result.overview());
            explanation.setSteps(mapper.writeValueAsString(result.steps()));
            explanation.setConcepts(mapper.writeValueAsString(result.concepts()));
            explanation.setStatus(Explanation.ExplanationStatus.COMPLETE);
        } catch (Exception e) {
            log.error("AI explanation failed for change {}", id, e);
            explanation.setStatus(Explanation.ExplanationStatus.FAILED);
            explanationRepository.save(explanation);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Explanation failed");
        }

        explanationRepository.save(explanation);
        return buildExplanationMap(explanation);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildExplanationMap(Explanation explanation) {
        try {
            List<Map<String, Object>> steps = mapper.readValue(
                    explanation.getSteps(), new TypeReference<>() {});
            List<Map<String, Object>> concepts = mapper.readValue(
                    explanation.getConcepts(), new TypeReference<>() {});

            return Map.of(
                    "overview", explanation.getOverview(),
                    "steps", steps,
                    "concepts", concepts,
                    "status", explanation.getStatus().toString());
        } catch (Exception e) {
            throw new RuntimeException("Failed to read explanation data", e);
        }
    }
}
