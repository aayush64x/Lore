package com.lore.cost;

import com.lore.change.Change;
import com.lore.change.ChangeRepository;
import com.lore.session.Session;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Each AI call saves one row for the right session and job, and the page can read the total. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CostRecordingTest {

    @TestConfiguration
    static class Config {
        @Bean @Primary FakeAi fakeAi(AiCallRecorder recorder) {
            FakeAi ai = new FakeAi();
            ai.recorder = recorder;
            return ai;
        }
    }

    @Autowired MockMvc mvc;
    @Autowired FakeAi ai;
    @Autowired SessionService sessionService;
    @Autowired ChangeRepository changeRepository;
    @Autowired AiCallRepository aiCallRepository;
    @Autowired AiCallRecorder recorder;

    @TempDir Path repo;

    @BeforeEach
    void setUp() throws Exception {
        ai.reset(FakeAi.Mode.WORKING);
        TestGit.git(repo, "init");
    }

    private int sessions;

    /** A stopped session with this many new files (named per session, so it always has changes). */
    private Session stopped(int files) throws Exception {
        sessionService.start(repo);
        int n = ++sessions;
        for (int i = 0; i < files; i++) Files.writeString(repo.resolve("s" + n + "-f" + i + ".txt"), "line " + i + "\n");
        return sessionService.stop(repo, "a goal").session();
    }

    private List<AiCall> calls(UUID sessionId, AiCall.Job job) {
        return aiCallRepository.findBySessionId(sessionId).stream().filter(c -> c.getJob() == job).toList();
    }

    @Test
    void the_summary_saves_one_row_with_its_tokens() throws Exception {
        Session s = stopped(2);

        List<AiCall> rows = aiCallRepository.findBySessionId(s.getId());
        assertEquals(1, rows.size());
        AiCall row = rows.get(0);
        assertEquals(AiCall.Job.SUMMARY, row.getJob());
        assertEquals("fake-summary", row.getModel());
        assertEquals(1000, row.getInputTokens());
        assertEquals(100, row.getOutputTokens());
        assertEquals(10, row.getCacheReadTokens());
        assertEquals(1, row.getCacheWriteTokens());
    }

    @Test
    void a_big_sessions_groups_summarized_on_other_threads_count_for_that_session() throws Exception {
        Session other = stopped(1);
        Session big = stopped(130); // 3 groups on pool threads, then the join on this thread

        List<AiCall> rows = calls(big.getId(), AiCall.Job.SUMMARY);
        assertEquals(4, rows.size(), "3 groups and the join");
        assertEquals(3, rows.stream().filter(r -> r.getModel().equals("fake-summary")).count());
        assertEquals(1, rows.stream().filter(r -> r.getModel().equals("fake-combine")).count());
        assertEquals(1, aiCallRepository.findBySessionId(other.getId()).size(), "the other session keeps only its own");
    }

    @Test
    void nothing_is_recorded_outside_a_call() throws Exception {
        Session s = stopped(1);
        long before = aiCallRepository.count();

        // The summary's tag was cleared when it finished, so a stray report on this thread is ignored.
        recorder.record("stray", 1, 1, 0, 0);

        assertEquals(before, aiCallRepository.count());
        assertEquals(1, aiCallRepository.findBySessionId(s.getId()).size());
    }

    @Test
    void explain_and_grading_each_save_a_row_for_the_changes_session() throws Exception {
        Session s = stopped(1);
        Change c = changeRepository.findBySessionIdOrderByDisplayOrder(s.getId()).get(0);

        mvc.perform(post("/api/changes/" + c.getId() + "/explain")).andExpect(status().isOk());
        mvc.perform(post("/api/changes/" + c.getId() + "/teach-back")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"answer\":\"It changes the line.\"}")).andExpect(status().isOk());

        assertEquals(1, calls(s.getId(), AiCall.Job.SUMMARY).size());
        assertEquals(1, calls(s.getId(), AiCall.Job.EXPLAIN).size());
        assertEquals(1, calls(s.getId(), AiCall.Job.GRADING).size());
        assertEquals("fake-explain", calls(s.getId(), AiCall.Job.EXPLAIN).get(0).getModel());
    }

    @Test
    void a_retried_garbled_answer_counts_both_calls() throws Exception {
        ai.garbledAnswers = 1;
        Session s = stopped(1);

        assertEquals(2, ai.summarizeCalls);
        assertEquals(2, calls(s.getId(), AiCall.Job.SUMMARY).size(), "the thrown-away answer was paid for too");
    }

    @Test
    void deleting_a_session_deletes_its_rows() throws Exception {
        Session doomed = stopped(1);
        Session kept = stopped(1);

        mvc.perform(delete("/api/sessions/" + doomed.getId())).andExpect(status().isNoContent());

        assertTrue(aiCallRepository.findBySessionId(doomed.getId()).isEmpty());
        assertEquals(1, aiCallRepository.findBySessionId(kept.getId()).size());
    }

    // Tests run with provider "none" (application-test.yml), where the cost is hidden.
    @Test
    void with_provider_none_the_page_gets_no_cost() throws Exception {
        Session s = stopped(1);

        mvc.perform(get("/api/sessions/" + s.getId() + "/cost"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cost").value(nullValue()));
        mvc.perform(get("/api/sessions/" + s.getId()))
                .andExpect(jsonPath("$.cost").value(nullValue()));
    }

    @Test
    void cost_for_an_unknown_session_is_404() throws Exception {
        mvc.perform(get("/api/sessions/" + UUID.randomUUID() + "/cost")).andExpect(status().isNotFound());
    }
}
