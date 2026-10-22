package com.lore.api.controller;

import com.lore.concept.ConceptStats;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ConceptController {

    private final ConceptStats conceptStats;

    public ConceptController(ConceptStats conceptStats) {
        this.conceptStats = conceptStats;
    }

    /**
     * Concepts across the project's stopped sessions: {"concepts": [...], "others": [...]}.
     * Each has name, group (null for others), seen, understood, sessions and lastSeen.
     */
    @GetMapping("/concepts")
    public ConceptStats.Result getConcepts(@RequestParam String project) {
        return conceptStats.forProject(project);
    }
}
