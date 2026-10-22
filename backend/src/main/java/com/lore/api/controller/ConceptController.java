package com.lore.api.controller;

import com.lore.concept.ConceptGraph;
import com.lore.concept.ConceptStats;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ConceptController {

    private final ConceptStats conceptStats;
    private final ConceptGraph conceptGraph;

    public ConceptController(ConceptStats conceptStats, ConceptGraph conceptGraph) {
        this.conceptStats = conceptStats;
        this.conceptGraph = conceptGraph;
    }

    /**
     * The same concepts as a graph: {nodes, links, groups}. A node has id, name, group,
     * other, seen, understood and its changes; a link joins two concepts tagged on the same
     * change, with how many times.
     */
    @GetMapping("/concepts/graph")
    public ConceptGraph.Graph getConceptGraph(@RequestParam String project) {
        return conceptGraph.forProject(project);
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
