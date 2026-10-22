package com.lore.concept;

import com.lore.change.Change;
import com.lore.session.Session;
import com.lore.session.SessionService;
import com.lore.testutil.FakeAi;
import com.lore.testutil.TestGit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** The summary step tags each change with concepts from the list (FakeAi sends the names). */
@SpringBootTest
@ActiveProfiles("test")
class SessionConceptsTest {

    @TestConfiguration
    static class Config {
        @Bean @Primary FakeAi fakeAi() { return new FakeAi(); }
    }

    @Autowired FakeAi ai;
    @Autowired SessionService sessionService;
    @Autowired ChangeConceptRepository conceptRepository;

    @TempDir Path repo;
    private int sessions;

    @BeforeEach
    void setUp() throws Exception {
        ai.reset(FakeAi.Mode.WORKING);
        TestGit.git(repo, "init");
    }

    private Session stopped(String... files) throws Exception {
        sessionService.start(repo);
        int n = ++sessions;
        for (String f : files) Files.writeString(repo.resolve(f), "session " + n + "\n");
        return sessionService.stop(repo, "a goal").session();
    }

    /** "file -> tags", with Other tags written as "Other: name". */
    private Map<String, List<String>> tags(UUID sessionId) {
        return conceptRepository.findByChangeSessionId(sessionId).stream().collect(Collectors.groupingBy(
                c -> c.getChange().getFilePath(), TreeMap::new,
                Collectors.mapping(c -> (c.isOther() ? "Other: " : "") + c.getName(), Collectors.toList())));
    }

    @Test
    void stop_saves_each_changes_concepts_from_the_list_only() throws Exception {
        // FakeAi sends "functions", "Made-up concept", "Loops" for app.js, and "Other: WebSockets" for other.js.
        Session s = stopped("app.js", "other.js");

        Map<String, List<String>> tags = tags(s.getId());
        assertEquals(List.of("Functions", "Loops"), tags.get("app.js").stream().sorted().toList(),
                "the list's spelling, and the made-up name dropped");
        assertEquals(List.of("Other: WebSockets"), tags.get("other.js"));
    }

    @Test
    void retrying_the_summary_replaces_the_tags() throws Exception {
        Session s = stopped("app.js");
        ai.conceptNames = List.of("Testing");

        sessionService.retrySummary(s.getId());

        assertEquals(Map.of("app.js", List.of("Testing")), tags(s.getId()));
    }

    @Test
    void a_big_session_summarized_in_groups_tags_every_change() throws Exception {
        String[] files = new String[70];
        for (int i = 0; i < files.length; i++) files[i] = "f" + i + ".txt";
        Session s = stopped(files);

        Map<String, List<String>> tags = tags(s.getId());
        assertEquals(70, tags.size());
        tags.values().forEach(t -> assertEquals(2, t.size(), t.toString()));
    }

    @Test
    void a_failed_summary_saves_no_tags() throws Exception {
        ai.reset(FakeAi.Mode.NOT_SET_UP);
        Session s = stopped("app.js");

        assertTrue(tags(s.getId()).isEmpty());
    }

    @Test
    void deleting_a_session_deletes_its_tags_and_no_others() throws Exception {
        Session doomed = stopped("a.js");
        Session kept = stopped("b.js");

        sessionService.delete(doomed.getId());

        assertTrue(tags(doomed.getId()).isEmpty());
        assertEquals(2, tags(kept.getId()).get("b.js").size());
    }

    @Test
    void tags_point_at_the_right_change() throws Exception {
        Session s = stopped("app.js", "other.js");
        for (ChangeConcept c : conceptRepository.findByChangeSessionId(s.getId())) {
            Change change = c.getChange();
            assertEquals(s.getId(), change.getSession().getId());
            assertEquals(c.isOther(), change.getFilePath().equals("other.js"));
        }
    }
}
