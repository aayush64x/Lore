package com.lore.api;

import com.lore.change.Change;
import com.lore.change.ChangeRepository;
import com.lore.change.Explanation;
import com.lore.change.ExplanationRepository;
import com.lore.change.TeachBackAttempt;
import com.lore.change.TeachBackAttemptRepository;
import com.lore.session.Session;
import com.lore.session.SessionRepository;
import com.lore.session.SessionService;
import com.lore.testutil.FakeAi;
import com.lore.testutil.TestGit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** DELETE /api/sessions/{id}, against a real git repo with real snapshot refs. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SessionDeleteTest {

    @TestConfiguration
    static class Config {
        @Bean @Primary FakeAi fakeAi() { return new FakeAi(); }
    }

    @Autowired MockMvc mvc;
    @Autowired FakeAi ai;
    @Autowired SessionService sessionService;
    @Autowired SessionRepository sessionRepository;
    @Autowired ChangeRepository changeRepository;
    @Autowired ExplanationRepository explanationRepository;
    @Autowired TeachBackAttemptRepository attemptRepository;

    @TempDir Path repo;

    @BeforeEach
    void setUp() throws Exception {
        ai.reset(FakeAi.Mode.WORKING);
        TestGit.git(repo, "init");
        Files.writeString(repo.resolve("README.md"), "hello\n");
        TestGit.git(repo, "add", "-A");
        TestGit.git(repo, "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qm", "first");
        TestGit.git(repo, "branch", "keep-me");
    }

    /** A stopped session with two changes; the first has an explanation and a teach-back attempt. */
    private Session stoppedSession(String file) throws Exception {
        sessionService.start(repo);
        Files.writeString(repo.resolve(file), "one\n");
        Files.writeString(repo.resolve("other-" + file), "two\n");
        Session s = sessionService.stop(repo, "goal for " + file).session();
        Change c = changeRepository.findBySessionIdOrderByDisplayOrder(s.getId()).get(0);
        Explanation e = new Explanation(UUID.randomUUID(), c);
        e.setOverview("explains " + file);
        e.setSteps("[]");
        e.setConcepts("[]");
        e.setKeyPoints("[\"a key point\"]");
        e.setStatus(Explanation.ExplanationStatus.COMPLETE);
        explanationRepository.save(e);
        attemptRepository.save(new TeachBackAttempt(UUID.randomUUID(), c, "my answer",
                TeachBackAttempt.Verdict.PASS, "[]", "[]", "fake-grader"));
        return s;
    }

    /** Every ref in the repo (branches, tags, Lore's snapshots), one per line, with what it points at. */
    private List<String> allRefs() throws IOException, InterruptedException {
        Process p = new ProcessBuilder("git", "for-each-ref", "--format=%(refname) %(objectname)")
                .directory(repo.toFile()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        assertEquals(0, p.waitFor(), out);
        return out.lines().toList();
    }

    private int rowsFor(UUID sessionId) {
        return changeRepository.countBySessionId(sessionId)
                + explanationRepository.findByChangeSessionId(sessionId).size()
                + attemptRepository.findByChangeSessionIdOrderByCreatedAt(sessionId).size();
    }

    @Test
    void deletes_everything_for_that_session_and_nothing_else() throws Exception {
        Session doomed = stoppedSession("a.txt");
        Session kept = stoppedSession("b.txt");
        int keptRows = rowsFor(kept.getId());
        assertEquals(2 + 1 + 1, keptRows);

        mvc.perform(delete("/api/sessions/" + doomed.getId())).andExpect(status().isNoContent());

        assertTrue(sessionRepository.findById(doomed.getId()).isEmpty());
        assertEquals(0, rowsFor(doomed.getId()), "changes, explanations (with key points) and attempts are gone");
        assertTrue(sessionRepository.findById(kept.getId()).isPresent());
        assertEquals(keptRows, rowsFor(kept.getId()), "the other session keeps all its rows");
    }

    @Test
    void removes_only_that_sessions_snapshot_refs() throws Exception {
        Session doomed = stoppedSession("a.txt");
        Session kept = stoppedSession("b.txt");
        List<String> before = allRefs();
        String head = Files.readString(repo.resolve(".git/HEAD"));
        String doomedRef = "refs/lore/sessions/" + doomed.getId();
        assertEquals(2, before.stream().filter(r -> r.startsWith(doomedRef)).count(), before.toString());

        mvc.perform(delete("/api/sessions/" + doomed.getId())).andExpect(status().isNoContent());

        List<String> expected = new ArrayList<>(before);
        expected.removeIf(r -> r.startsWith(doomedRef + " ") || r.startsWith(doomedRef + "-stop "));
        assertEquals(expected, allRefs(), "only the deleted session's start and stop refs are gone");
        assertTrue(allRefs().stream().anyMatch(r -> r.startsWith("refs/heads/keep-me ")));
        assertTrue(allRefs().stream().anyMatch(r -> r.startsWith("refs/lore/sessions/" + kept.getId() + "-stop ")));
        assertEquals(head, Files.readString(repo.resolve(".git/HEAD")));
        assertEquals("hello\n", Files.readString(repo.resolve("README.md")));
        assertEquals("one\n", Files.readString(repo.resolve("a.txt")), "working files are never touched");
    }

    @Test
    void refuses_a_running_session_and_deletes_nothing() throws Exception {
        Session running = sessionService.start(repo);
        List<String> before = allRefs();

        mvc.perform(delete("/api/sessions/" + running.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Stop this session first with lore stop."));

        assertEquals(Session.SessionStatus.ACTIVE,
                sessionRepository.findById(running.getId()).orElseThrow().getStatus());
        assertEquals(before, allRefs());
    }

    @Test
    void unknown_session_is_404() throws Exception {
        mvc.perform(delete("/api/sessions/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void still_deletes_the_rows_when_the_project_folder_is_gone() throws Exception {
        Session doomed = stoppedSession("a.txt");
        try (var files = Files.walk(repo)) {
            files.sorted(java.util.Comparator.reverseOrder()).forEach(f -> f.toFile().delete());
        }

        mvc.perform(delete("/api/sessions/" + doomed.getId())).andExpect(status().isNoContent());

        assertTrue(sessionRepository.findById(doomed.getId()).isEmpty());
        assertEquals(0, rowsFor(doomed.getId()));
    }
}
