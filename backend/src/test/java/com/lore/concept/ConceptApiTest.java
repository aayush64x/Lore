package com.lore.concept;

import com.lore.change.Change;
import com.lore.change.ChangeRepository;
import com.lore.session.Session;
import com.lore.session.SessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** GET /api/concepts: seen and understood counts per concept, for one project. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConceptApiTest {

    @Autowired MockMvc mvc;
    @Autowired SessionRepository sessionRepository;
    @Autowired ChangeRepository changeRepository;
    @Autowired ChangeConceptRepository conceptRepository;

    /** Unique per test so tests don't see each other's data. */
    String project;
    final Instant earlier = Instant.parse("2026-10-20T10:00:00Z");
    final Instant later = Instant.parse("2026-10-21T10:00:00Z");

    @BeforeEach
    void setUp() {
        project = "/tmp/concept-test-" + UUID.randomUUID();
    }

    private Session session(String projectPath, Session.SessionStatus status, Instant stoppedAt) {
        Session s = new Session(UUID.randomUUID(), projectPath, "refs/lore/sessions/x");
        s.setStatus(status);
        s.setStoppedAt(stoppedAt);
        return sessionRepository.save(s);
    }

    private Change change(Session s, boolean understood, String... tags) {
        Change c = new Change(UUID.randomUUID(), s, "f-" + UUID.randomUUID() + ".js", Change.ChangeType.MODIFIED);
        c.setUnderstood(understood);
        changeRepository.save(c);
        for (String tag : tags) {
            boolean other = tag.startsWith("Other: ");
            conceptRepository.save(new ChangeConcept(c, other ? tag.substring("Other: ".length()) : tag, other));
        }
        return c;
    }

    @Test
    void counts_seen_and_understood_changes_per_concept_across_sessions() throws Exception {
        Session first = session(project, Session.SessionStatus.COMPLETE, earlier);
        change(first, true, "Loops", "Functions");
        change(first, false, "Loops");
        change(first, false, "Other: WebSockets");
        Session second = session(project, Session.SessionStatus.COMPLETE, later);
        change(second, true, "Loops");
        change(second, false, "Other: websockets");
        // Not counted: a running session, and another project.
        change(session(project, Session.SessionStatus.ACTIVE, null), true, "Loops");
        change(session(project + "-other", Session.SessionStatus.COMPLETE, later), true, "Loops", "Testing");

        mvc.perform(get("/api/concepts").param("project", project))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.concepts", hasSize(2)))
                .andExpect(jsonPath("$.concepts[0].name").value("Loops"))
                .andExpect(jsonPath("$.concepts[0].group").value("Programming fundamentals"))
                .andExpect(jsonPath("$.concepts[0].seen").value(3))
                .andExpect(jsonPath("$.concepts[0].understood").value(2))
                .andExpect(jsonPath("$.concepts[0].sessions").value(2))
                .andExpect(jsonPath("$.concepts[0].lastSeen").value(startsWith("2026-10-21T10:00")))
                .andExpect(jsonPath("$.concepts[1].name").value("Functions"))
                .andExpect(jsonPath("$.concepts[1].seen").value(1))
                .andExpect(jsonPath("$.concepts[1].understood").value(1))
                .andExpect(jsonPath("$.concepts[1].lastSeen").value(startsWith("2026-10-20T10:00")))
                // Other suggestions are apart, and the same idea in different case counts once.
                .andExpect(jsonPath("$.others", hasSize(1)))
                .andExpect(jsonPath("$.others[0].name").value("WebSockets"))
                .andExpect(jsonPath("$.others[0].seen").value(2))
                .andExpect(jsonPath("$.others[0].understood").value(0))
                .andExpect(jsonPath("$.others[0].sessions").value(2))
                .andExpect(jsonPath("$.others[0].group").value(nullValue()))
                // 5 tagged changes in this project, 2 understood: each change once, not once per tag.
                .andExpect(jsonPath("$.changes").value(5))
                .andExpect(jsonPath("$.understoodChanges").value(2));
    }

    @Test
    void a_concept_taken_off_the_list_still_counts_with_no_group() throws Exception {
        change(session(project, Session.SessionStatus.COMPLETE, later), false, "Old concept");

        mvc.perform(get("/api/concepts").param("project", project))
                .andExpect(jsonPath("$.concepts[0].name").value("Old concept"))
                .andExpect(jsonPath("$.concepts[0].group").value(nullValue()))
                .andExpect(jsonPath("$.others", hasSize(0)));
    }

    @Test
    void ties_are_sorted_by_name() throws Exception {
        Session s = session(project, Session.SessionStatus.COMPLETE, later);
        change(s, false, "Testing");
        change(s, false, "Arrays & lists");

        mvc.perform(get("/api/concepts").param("project", project))
                .andExpect(jsonPath("$.concepts[*].name", contains("Arrays & lists", "Testing")));
    }

    @Test
    void a_project_with_no_tags_has_empty_lists() throws Exception {
        session(project, Session.SessionStatus.COMPLETE, later);

        mvc.perform(get("/api/concepts").param("project", project))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.concepts", hasSize(0)))
                .andExpect(jsonPath("$.others", hasSize(0)))
                .andExpect(jsonPath("$.changes").value(0));
    }

    @Test
    void the_session_page_gets_each_changes_tags_listed_ones_first() throws Exception {
        Session s = session(project, Session.SessionStatus.COMPLETE, later);
        Change tagged = change(s, false, "Testing", "Loops");
        Change suggested = change(s, false, "Other: WebSockets");
        Change untagged = change(s, false);

        mvc.perform(get("/api/sessions/" + s.getId()))
                .andExpect(jsonPath("$.changes[?(@.id == '" + tagged.getId() + "')].concepts[*].name",
                        contains("Loops", "Testing")))
                .andExpect(jsonPath("$.changes[?(@.id == '" + suggested.getId() + "')].concepts[0].name",
                        contains("WebSockets")))
                .andExpect(jsonPath("$.changes[?(@.id == '" + suggested.getId() + "')].concepts[0].other",
                        contains(true)))
                .andExpect(jsonPath("$.changes[?(@.id == '" + untagged.getId() + "')].concepts[*]", hasSize(0)));
    }

    @Test
    void the_graph_links_concepts_tagged_on_the_same_change() throws Exception {
        Session first = session(project, Session.SessionStatus.COMPLETE, earlier);
        Change both = change(first, true, "Loops", "Functions");
        change(first, false, "Loops", "Functions", "Testing");
        Session second = session(project, Session.SessionStatus.COMPLETE, later);
        Change latest = change(second, false, "Loops");
        change(second, false, "Other: WebSockets");
        change(session(project + "-other", Session.SessionStatus.COMPLETE, later), true, "Loops", "State");

        mvc.perform(get("/api/concepts/graph").param("project", project))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes[*].id", contains("Loops", "Functions", "Testing", "other:websockets")))
                .andExpect(jsonPath("$.nodes[0].seen").value(3))
                .andExpect(jsonPath("$.nodes[0].understood").value(1))
                .andExpect(jsonPath("$.nodes[0].group").value("Programming fundamentals"))
                // Its changes, newest session first, with what the side panel needs.
                .andExpect(jsonPath("$.nodes[0].changes", hasSize(3)))
                .andExpect(jsonPath("$.nodes[0].changes[0].changeId").value(latest.getId().toString()))
                .andExpect(jsonPath("$.nodes[0].changes[0].sessionId").value(second.getId().toString()))
                .andExpect(jsonPath("$.nodes[?(@.id == 'other:websockets')].other", contains(true)))
                .andExpect(jsonPath("$.nodes[?(@.id == 'other:websockets')].name", contains("WebSockets")))
                // Loops+Functions on two changes; Loops+Testing and Functions+Testing on one.
                .andExpect(jsonPath("$.links", hasSize(3)))
                .andExpect(jsonPath("$.links[?(@.source == 'Functions' && @.target == 'Loops')].count", contains(2)))
                .andExpect(jsonPath("$.links[?(@.source == 'Loops' && @.target == 'Testing')].count", contains(1)))
                // Headings with a node, in concepts.txt order; State (other project) is left out.
                .andExpect(jsonPath("$.groups", contains("Programming fundamentals", "General")));
        assertTrue(both.isUnderstood());
    }

    @Test
    void an_empty_project_has_an_empty_graph() throws Exception {
        mvc.perform(get("/api/concepts/graph").param("project", project))
                .andExpect(jsonPath("$.nodes", hasSize(0)))
                .andExpect(jsonPath("$.links", hasSize(0)))
                .andExpect(jsonPath("$.groups", hasSize(0)));
    }
}
