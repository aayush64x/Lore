package com.lore.concept;

import com.lore.change.Change;
import com.lore.session.Session;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What the Concepts page shows for one project: each concept its changes were tagged with,
 * how many of those changes were seen and understood, in how many sessions, and when last.
 * Only stopped sessions count. Names from the list and "Other" suggestions are kept apart.
 */
@Service
public class ConceptStats {

    /** group and meaning come from concepts.txt; null if it's no longer on the list (or has no meaning). */
    public record ConceptCount(String name, String group, String meaning, int seen, int understood, int sessions,
                               Instant lastSeen) {}

    /** changes = tagged changes (each counted once, however many tags), understoodChanges = of those. */
    public record Result(List<ConceptCount> concepts, List<ConceptCount> others, int changes, int understoodChanges) {}

    private final ChangeConceptRepository repository;
    private final ConceptList conceptList;

    public ConceptStats(ChangeConceptRepository repository, ConceptList conceptList) {
        this.repository = repository;
        this.conceptList = conceptList;
    }

    public Result forProject(String project) {
        Map<String, ConceptList.Concept> onList = new HashMap<>();
        for (ConceptList.Concept c : conceptList.concepts()) onList.put(c.name(), c);

        Map<String, List<ChangeConcept>> listed = new LinkedHashMap<>();
        Map<String, List<ChangeConcept>> others = new LinkedHashMap<>();
        Map<UUID, Boolean> changes = new HashMap<>();
        for (ChangeConcept tag : repository.findInProject(project)) {
            changes.put(tag.getChange().getId(), tag.getChange().isUnderstood());
            // Others are suggestions: the same idea in different case counts once.
            String key = tag.isOther() ? ConceptList.key(tag.getName()) : tag.getName();
            (tag.isOther() ? others : listed).computeIfAbsent(key, k -> new ArrayList<>()).add(tag);
        }
        int understood = (int) changes.values().stream().filter(u -> u).count();
        return new Result(counts(listed, onList), counts(others, Map.of()), changes.size(), understood);
    }

    /** Most seen first, then by name. */
    private static List<ConceptCount> counts(Map<String, List<ChangeConcept>> byName, Map<String, ConceptList.Concept> onList) {
        List<ConceptCount> out = new ArrayList<>();
        for (List<ChangeConcept> tags : byName.values()) {
            String name = tags.get(0).getName();
            Set<UUID> changes = new HashSet<>();
            Set<UUID> sessions = new HashSet<>();
            int understood = 0;
            Instant last = null;
            for (ChangeConcept tag : tags) {
                Change change = tag.getChange();
                if (!changes.add(change.getId())) continue;
                if (change.isUnderstood()) understood++;
                Session session = change.getSession();
                sessions.add(session.getId());
                Instant when = session.getStoppedAt();
                if (when != null && (last == null || when.isAfter(last))) last = when;
            }
            ConceptList.Concept concept = onList.get(name);
            out.add(new ConceptCount(name, concept == null ? null : concept.group(), concept == null ? null : concept.meaning(),
                    changes.size(), understood, sessions.size(), last));
        }
        out.sort(Comparator.comparingInt(ConceptCount::seen).reversed()
                .thenComparing(c -> c.name().toLowerCase()));
        return out;
    }
}
