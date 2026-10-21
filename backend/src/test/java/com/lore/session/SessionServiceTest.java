package com.lore.session;

import com.lore.change.Change;
import com.lore.change.ChangeRepository;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class SessionServiceTest {

    @TestConfiguration
    static class Config {
        @Bean @Primary FakeAi fakeAi() { return new FakeAi(); }
    }

    @Autowired SessionService sessionService;
    @Autowired SessionRepository sessionRepository;
    @Autowired ChangeRepository changeRepository;
    @Autowired FakeAi ai;

    @TempDir Path repo;

    @BeforeEach
    void setUp() throws Exception {
        ai.reset(FakeAi.Mode.WORKING);
        TestGit.git(repo, "init");
    }

    private String rootPath() throws IOException {
        return repo.toRealPath().toString();
    }

    private boolean refExists(String ref) throws Exception {
        Process p = new ProcessBuilder("git", "rev-parse", "--verify", "--quiet", ref)
                .directory(repo.toFile()).redirectErrorStream(true).start();
        p.getInputStream().readAllBytes();
        return p.waitFor() == 0;
    }

    // --- start ---

    @Test
    void start_saves_an_active_session_with_a_snapshot() throws Exception {
        Session s = sessionService.start(repo);

        Session saved = sessionRepository.findById(s.getId()).orElseThrow();
        assertEquals(Session.SessionStatus.ACTIVE, saved.getStatus());
        assertEquals(rootPath(), saved.getProjectPath());
        assertNotNull(saved.getStartedAt());
        assertNull(saved.getStoppedAt());
        assertTrue(refExists(saved.getStartRef()), "start snapshot ref should exist");
        assertEquals(saved.getId(), sessionService.getActive(repo).orElseThrow().getId());
    }

    @Test
    void start_from_a_subfolder_uses_the_repo_root() throws Exception {
        Path sub = Files.createDirectories(repo.resolve("src/deep"));
        Session s = sessionService.start(sub);
        assertEquals(rootPath(), s.getProjectPath());
        // And stop from the root finds it.
        Files.writeString(repo.resolve("a.txt"), "a\n");
        assertFalse(sessionService.stop(repo, "x").discarded());
    }

    @Test
    void start_twice_is_refused() throws Exception {
        Session first = sessionService.start(repo);
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> sessionService.start(repo));
        assertTrue(e.getMessage().contains("already running"), e.getMessage());
        String root = rootPath();
        assertEquals(List.of(first.getId()), sessionRepository.findAll().stream()
                .filter(s -> s.getProjectPath().equals(root) && s.getStatus() == Session.SessionStatus.ACTIVE)
                .map(Session::getId).toList());
    }

    @Test
    void start_outside_git_is_refused_and_saves_nothing(@TempDir Path notRepo) {
        long before = sessionRepository.count();
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> sessionService.start(notRepo));
        assertTrue(e.getMessage().contains("git init"), e.getMessage());
        assertEquals(before, sessionRepository.count());
        assertFalse(Files.exists(notRepo.resolve(".git")), "Lore must not run git init for you");
    }

    // --- stop ---

    @Test
    void stop_saves_changes_counts_and_summary() throws Exception {
        Files.writeString(repo.resolve("keep.txt"), "line1\nline2\n");
        Files.writeString(repo.resolve("gone.txt"), "bye\n");
        TestGit.git(repo, "add", ".");
        TestGit.git(repo, "-c", "user.name=t", "-c", "user.email=t@t", "commit", "-qm", "base");

        sessionService.start(repo);
        Files.writeString(repo.resolve("keep.txt"), "line1\nchanged\n");
        Files.delete(repo.resolve("gone.txt"));
        Files.writeString(repo.resolve("new.js"), "x\n");

        SessionService.StopResult r = sessionService.stop(repo, "tidy up");

        assertFalse(r.discarded());
        assertNull(r.summaryError());
        assertEquals(3, r.fileCount());
        assertEquals(3, r.changeCount());
        Session saved = sessionRepository.findById(r.session().getId()).orElseThrow();
        assertEquals(Session.SessionStatus.COMPLETE, saved.getStatus());
        assertEquals(Session.SummaryStatus.COMPLETE, saved.getSummaryStatus());
        assertEquals("tidy up", saved.getReason());
        assertEquals("Summary for: tidy up", saved.getSummary());
        assertNotNull(saved.getStoppedAt());
        assertTrue(refExists(saved.getStopRef()));

        List<Change> changes = changeRepository.findBySessionIdOrderByDisplayOrder(saved.getId());
        assertEquals(List.of(0, 1, 2), changes.stream().map(Change::getDisplayOrder).toList());
        for (Change c : changes) {
            assertFalse(c.isUnderstood(), "every change starts as not understood");
            assertEquals("about " + c.getFilePath(), c.getDescription());
        }
        assertEquals(Change.ChangeType.DELETED, find(changes, "gone.txt").getChangeType());
        assertEquals(Change.ChangeType.MODIFIED, find(changes, "keep.txt").getChangeType());
        assertEquals(Change.ChangeType.ADDED, find(changes, "new.js").getChangeType());
        assertTrue(sessionService.getActive(repo).isEmpty());
    }

    @Test
    void stop_asks_again_when_the_summary_comes_back_with_stray_json() throws Exception {
        sessionService.start(repo);
        Files.writeString(repo.resolve("a.txt"), "hi\n");
        ai.garbledAnswers = 1;

        SessionService.StopResult r = sessionService.stop(repo, "fix");

        assertNull(r.summaryError());
        assertEquals(2, ai.summarizeCalls);
        assertEquals("Summary for: fix", sessionRepository.findById(r.session().getId()).orElseThrow().getSummary());
    }

    @Test
    void stop_with_a_summary_garbled_twice_saves_no_summary() throws Exception {
        sessionService.start(repo);
        Files.writeString(repo.resolve("a.txt"), "hi\n");
        ai.garbledAnswers = 2;

        SessionService.StopResult r = sessionService.stop(repo, "fix");

        assertNotNull(r.summaryError());
        Session saved = sessionRepository.findById(r.session().getId()).orElseThrow();
        assertNull(saved.getSummary());
        assertEquals(Session.SummaryStatus.FAILED, saved.getSummaryStatus());
    }

    @Test
    void stop_saves_a_long_reason_with_paragraphs() throws Exception {
        sessionService.start(repo);
        Files.writeString(repo.resolve("a.txt"), "hi\n");
        String essay = "Add a login page\n\n" + "I asked my AI tool to build a login form. ".repeat(300);

        SessionService.StopResult r = sessionService.stop(repo, essay);

        assertEquals(essay, sessionRepository.findById(r.session().getId()).orElseThrow().getReason());
    }

    @Test
    void stop_skips_lockfiles_and_build_output() throws Exception {
        sessionService.start(repo);
        Files.writeString(repo.resolve("package-lock.json"), "{}\n");
        Files.createDirectories(repo.resolve("node_modules/x"));
        Files.writeString(repo.resolve("node_modules/x/index.js"), "x\n");
        Files.writeString(repo.resolve("app.js"), "x\n");

        SessionService.StopResult r = sessionService.stop(repo, "r");

        List<Change> changes = changeRepository.findBySessionIdOrderByDisplayOrder(r.session().getId());
        assertEquals(List.of("app.js"), changes.stream().map(Change::getFilePath).toList());
    }

    @Test
    void stop_with_no_changes_discards_session_and_refs() throws Exception {
        Session started = sessionService.start(repo);

        SessionService.StopResult r = sessionService.stop(repo, "nothing");

        assertTrue(r.discarded());
        assertEquals(0, r.changeCount());
        Session saved = sessionRepository.findById(started.getId()).orElseThrow();
        assertEquals(Session.SessionStatus.DISCARDED, saved.getStatus());
        assertFalse(refExists(saved.getStartRef()), "start ref should be deleted");
        assertFalse(refExists(saved.getStopRef()), "stop ref should be deleted");
        assertEquals(0, changeRepository.countBySessionId(saved.getId()));
        assertEquals(0, ai.summarizeCalls, "no AI call for an empty session");
        assertTrue(sessionService.getActive(repo).isEmpty());
    }

    @Test
    void stop_without_a_running_session_is_refused() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> sessionService.stop(repo, "r"));
        assertTrue(e.getMessage().contains("No session"), e.getMessage());
    }

    @Test
    void stop_outside_git_is_refused(@TempDir Path notRepo) {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> sessionService.stop(notRepo, "r"));
        assertTrue(e.getMessage().contains("git init"), e.getMessage());
    }

    @Test
    void new_session_can_start_after_stop() throws Exception {
        sessionService.start(repo);
        Files.writeString(repo.resolve("a.txt"), "a\n");
        sessionService.stop(repo, "one");
        Session second = sessionService.start(repo);
        Files.writeString(repo.resolve("a.txt"), "a\nb\n");
        SessionService.StopResult r = sessionService.stop(repo, "two");
        // The second session only contains what changed after it started.
        List<Change> changes = changeRepository.findBySessionIdOrderByDisplayOrder(second.getId());
        assertEquals(1, changes.size());
        assertEquals(Change.ChangeType.MODIFIED, changes.get(0).getChangeType());
        assertEquals(1, r.changeCount());
    }

    // --- AI failure ---

    @Test
    void ai_not_set_up_saves_failed_without_placeholder() throws Exception {
        ai.reset(FakeAi.Mode.NOT_SET_UP);
        sessionService.start(repo);
        Files.writeString(repo.resolve("a.txt"), "a\n");

        SessionService.StopResult r = sessionService.stop(repo, "r");

        assertFailedWithNoText(r.session().getId());
        // The CLI shows this reason instead of a raw log line.
        assertNotNull(r.summaryError());
        assertFalse(r.summaryError().isBlank());
    }

    @Test
    void ai_crash_saves_failed_without_placeholder() throws Exception {
        ai.reset(FakeAi.Mode.CRASH);
        sessionService.start(repo);
        Files.writeString(repo.resolve("a.txt"), "a\n");

        SessionService.StopResult r = sessionService.stop(repo, "r");
        Session s = r.session();

        assertFailedWithNoText(s.getId());
        assertEquals("AI summary failed.", r.summaryError());
        // Changes are still saved so the user can study them.
        assertEquals(1, changeRepository.countBySessionId(s.getId()));
    }

    private void assertFailedWithNoText(UUID id) {
        Session saved = sessionRepository.findById(id).orElseThrow();
        assertEquals(Session.SessionStatus.COMPLETE, saved.getStatus());
        assertEquals(Session.SummaryStatus.FAILED, saved.getSummaryStatus());
        assertNull(saved.getSummary());
        changeRepository.findBySessionIdOrderByDisplayOrder(id)
                .forEach(c -> assertNull(c.getDescription(), c.getFilePath()));
    }

    // --- retry summary ---

    @Test
    void retry_after_failure_fills_summary_and_descriptions() throws Exception {
        ai.reset(FakeAi.Mode.NOT_SET_UP);
        sessionService.start(repo);
        Files.writeString(repo.resolve("a.txt"), "a\n");
        UUID id = sessionService.stop(repo, "add a").session().getId();

        ai.reset(FakeAi.Mode.WORKING);
        SessionService.RetryResult r = sessionService.retrySummary(id);

        assertNull(r.error());
        Session saved = sessionRepository.findById(id).orElseThrow();
        assertEquals(Session.SummaryStatus.COMPLETE, saved.getSummaryStatus());
        assertEquals("Summary for: add a", saved.getSummary());
        assertEquals("about a.txt", changeRepository.findBySessionIdOrderByDisplayOrder(id).get(0).getDescription());
    }

    @Test
    void retry_that_fails_again_stays_failed_and_says_why() throws Exception {
        ai.reset(FakeAi.Mode.CRASH);
        sessionService.start(repo);
        Files.writeString(repo.resolve("a.txt"), "a\n");
        UUID id = sessionService.stop(repo, "r").session().getId();

        ai.reset(FakeAi.Mode.NOT_SET_UP);
        SessionService.RetryResult r = sessionService.retrySummary(id);

        assertEquals("AI is not set up.", r.error());
        assertFailedWithNoText(id);
    }

    @Test
    void failed_retry_keeps_an_earlier_good_summary() throws Exception {
        sessionService.start(repo);
        Files.writeString(repo.resolve("a.txt"), "a\n");
        UUID id = sessionService.stop(repo, "good").session().getId();

        ai.reset(FakeAi.Mode.CRASH);
        SessionService.RetryResult r = sessionService.retrySummary(id);

        assertEquals("AI summary failed.", r.error(), "crash details must not leak to the user");
        Session saved = sessionRepository.findById(id).orElseThrow();
        assertEquals(Session.SummaryStatus.COMPLETE, saved.getSummaryStatus());
        assertEquals("Summary for: good", saved.getSummary());
        assertEquals("about a.txt", changeRepository.findBySessionIdOrderByDisplayOrder(id).get(0).getDescription());
    }

    @Test
    void retry_keeps_understood_flags() throws Exception {
        ai.reset(FakeAi.Mode.NOT_SET_UP);
        sessionService.start(repo);
        Files.writeString(repo.resolve("a.txt"), "a\n");
        UUID id = sessionService.stop(repo, "r").session().getId();
        Change c = changeRepository.findBySessionIdOrderByDisplayOrder(id).get(0);
        c.setUnderstood(true);
        changeRepository.save(c);

        ai.reset(FakeAi.Mode.WORKING);
        sessionService.retrySummary(id);

        assertTrue(changeRepository.findById(c.getId()).orElseThrow().isUnderstood());
    }

    @Test
    void retry_unknown_session_throws_not_found() {
        assertThrows(NoSuchElementException.class, () -> sessionService.retrySummary(UUID.randomUUID()));
    }

    @Test
    void retry_running_or_discarded_session_is_refused() throws Exception {
        Session running = sessionService.start(repo);
        assertThrows(IllegalStateException.class, () -> sessionService.retrySummary(running.getId()));

        sessionService.stop(repo, "nothing changed");
        assertThrows(IllegalStateException.class, () -> sessionService.retrySummary(running.getId()));
        assertEquals(0, ai.summarizeCalls);
    }

    private static Change find(List<Change> changes, String path) {
        return changes.stream().filter(c -> c.getFilePath().equals(path)).findFirst().orElseThrow();
    }

}
