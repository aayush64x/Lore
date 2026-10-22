package com.lore.concept;

import com.lore.change.Change;
import com.lore.session.Session;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Concepts page's graph for one project: a node per concept (with the changes tagged
 * with it), and a link between two concepts each time they were tagged on the same change.
 * Only stopped sessions count, like the table.
 */
@Service
public class ConceptGraph {

    /** One change a concept was tagged on, for the side panel. */
    public record ChangeRef(UUID changeId, UUID sessionId, String sessionReason, String filePath,
                            String description, boolean understood, Instant stoppedAt) {}

    /** id is the name for list concepts and "other:<name>" for suggestions; group is null for those. */
    public record Node(String id, String name, String group, String meaning, boolean other, int seen, int understood,
                       List<ChangeRef> changes) {}

    /** count = how many changes were tagged with both. */
    public record Link(String source, String target, int count) {}

    /** groups: the headings that have a node, in concepts.txt order. */
    public record Graph(List<Node> nodes, List<Link> links, List<String> groups) {}

    private final ChangeConceptRepository repository;
    private final ConceptList conceptList;

    public ConceptGraph(ChangeConceptRepository repository, ConceptList conceptList) {
        this.repository = repository;
        this.conceptList = conceptList;
    }

    public Graph forProject(String project) {
        Map<String, ConceptList.Concept> onList = new HashMap<>();
        List<String> groupOrder = new ArrayList<>();
        for (ConceptList.Concept c : conceptList.concepts()) {
            onList.put(c.name(), c);
            if (c.group() != null && !groupOrder.contains(c.group())) groupOrder.add(c.group());
        }

        // Tags by concept, and concept ids by change (for the links).
        Map<String, List<ChangeConcept>> byConcept = new LinkedHashMap<>();
        Map<UUID, Set<String>> byChange = new LinkedHashMap<>();
        for (ChangeConcept tag : repository.findInProject(project)) {
            String id = tag.isOther() ? "other:" + ConceptList.key(tag.getName()) : tag.getName();
            byConcept.computeIfAbsent(id, k -> new ArrayList<>()).add(tag);
            byChange.computeIfAbsent(tag.getChange().getId(), k -> new LinkedHashSet<>()).add(id);
        }

        List<Node> nodes = new ArrayList<>();
        for (Map.Entry<String, List<ChangeConcept>> e : byConcept.entrySet()) {
            ChangeConcept first = e.getValue().get(0);
            Map<UUID, ChangeRef> changes = new LinkedHashMap<>();
            for (ChangeConcept tag : e.getValue()) {
                Change c = tag.getChange();
                Session s = c.getSession();
                changes.putIfAbsent(c.getId(), new ChangeRef(c.getId(), s.getId(), s.getReason(), c.getFilePath(),
                        c.getDescription(), c.isUnderstood(), s.getStoppedAt()));
            }
            List<ChangeRef> refs = new ArrayList<>(changes.values());
            refs.sort(Comparator.comparing(ChangeRef::stoppedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(ChangeRef::filePath));
            int understood = (int) refs.stream().filter(ChangeRef::understood).count();
            ConceptList.Concept concept = first.isOther() ? null : onList.get(first.getName());
            nodes.add(new Node(e.getKey(), first.getName(), concept == null ? null : concept.group(),
                    concept == null ? null : concept.meaning(),
                    first.isOther(), refs.size(), understood, refs));
        }
        nodes.sort(Comparator.comparingInt(Node::seen).reversed().thenComparing(n -> n.name().toLowerCase()));

        Map<String, Integer> pairs = new LinkedHashMap<>();
        for (Set<String> ids : byChange.values()) {
            List<String> sorted = ids.stream().sorted().toList();
            for (int i = 0; i < sorted.size(); i++) {
                for (int j = i + 1; j < sorted.size(); j++) {
                    pairs.merge(sorted.get(i) + "\n" + sorted.get(j), 1, Integer::sum);
                }
            }
        }
        List<Link> links = new ArrayList<>();
        pairs.forEach((pair, count) -> {
            String[] ends = pair.split("\n", 2);
            links.add(new Link(ends[0], ends[1], count));
        });

        Set<String> present = new LinkedHashSet<>();
        for (Node n : nodes) if (n.group() != null) present.add(n.group());
        List<String> usedGroups = groupOrder.stream().filter(present::contains).toList();
        return new Graph(nodes, links, usedGroups);
    }
}
