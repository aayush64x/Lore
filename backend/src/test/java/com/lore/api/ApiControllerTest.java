package com.lore.api;

import com.lore.change.Change;
import com.lore.change.ChangeRepository;
import com.lore.change.Explanation;
import com.lore.change.ExplanationRepository;
import com.lore.session.Session;
import com.lore.session.SessionRepository;
import com.lore.testutil.FakeAi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Tests for the REST endpoints in SessionController and ChangeController. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiControllerTest {

    @TestConfiguration
    static class Config {
        @Bean @Primary FakeAi fakeAi() { return new FakeAi(); }
    }

    @Autowired MockMvc mvc;
    @Autowired FakeAi ai;
    @Autowired SessionRepository sessionRepository;
    @Autowired ChangeRepository changeRepository;
    @Autowired ExplanationRepository explanationRepository;

    /** Unique per test so tests don't see each other's sessions. */
    String project;

    @BeforeEach
    void setUp() {
        ai.reset(FakeAi.Mode.WORKING);
        project = "/tmp/api-test-" + UUID.randomUUID();
    }

    private Session session(String projectPath, Session.SessionStatus status, String reason) {
        Session s = new Session(UUID.randomUUID(), projectPath, "refs/lore/sessions/x");
        s.setReason(reason);
        s.setStatus(status);
        if (status != Session.SessionStatus.ACTIVE) s.setStoppedAt(Instant.now());
        return sessionRepository.save(s);
    }

    private Change change(Session s, String path, int order) {
        Change c = new Change(UUID.randomUUID(), s, path, Change.ChangeType.MODIFIED);
        c.setDiffContent("@@ -1 +1 @@\n-old\n+new");
        c.setDisplayOrder(order);
        return changeRepository.save(c);
    }

    // --- GET /api/sessions ---

    @Test
    void list_shows_only_finished_sessions_for_this_project_newest_first() throws Exception {
        Session older = session(project, Session.SessionStatus.COMPLETE, "older");
        Thread.sleep(5);
        Session newer = session(project, Session.SessionStatus.COMPLETE, "newer");
        session(project, Session.SessionStatus.ACTIVE, "running");
        session(project, Session.SessionStatus.DISCARDED, "discarded");
        session(project + "-other", Session.SessionStatus.COMPLETE, "other project");

        change(newer, "a.js", 0);
        change(newer, "a.js", 1);
        Change understood = change(newer, "b.js", 2);
        understood.setUnderstood(true);
        changeRepository.save(understood);

        mvc.perform(get("/api/sessions").param("project", project))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(newer.getId().toString()))
                .andExpect(jsonPath("$[0].fileCount").value(2))
                .andExpect(jsonPath("$[0].changeCount").value(3))
                .andExpect(jsonPath("$[0].understoodCount").value(1))
                .andExpect(jsonPath("$[1].id").value(older.getId().toString()))
                .andExpect(jsonPath("$[1].changeCount").value(0));
    }

    @Test
    void list_for_unknown_project_is_empty() throws Exception {
        mvc.perform(get("/api/sessions").param("project", project))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void list_without_project_is_400() throws Exception {
        mvc.perform(get("/api/sessions")).andExpect(status().isBadRequest());
    }

    // --- GET /api/sessions/{id} ---

    @Test
    void detail_returns_changes_in_display_order() throws Exception {
        Session s = session(project, Session.SessionStatus.COMPLETE, "work");
        change(s, "second.js", 1);
        change(s, "first.js", 0);

        mvc.perform(get("/api/sessions/" + s.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reason").value("work"))
                .andExpect(jsonPath("$.changes", hasSize(2)))
                .andExpect(jsonPath("$.changes[0].filePath").value("first.js"))
                .andExpect(jsonPath("$.changes[1].filePath").value("second.js"))
                .andExpect(jsonPath("$.changes[0].understood").value(false));
    }

    @Test
    void detail_unknown_session_is_404() throws Exception {
        mvc.perform(get("/api/sessions/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void detail_with_bad_id_is_400() throws Exception {
        mvc.perform(get("/api/sessions/not-a-uuid")).andExpect(status().isBadRequest());
    }

    // --- PUT /api/changes/{id}/understood ---

    @Test
    void toggle_flips_and_saves() throws Exception {
        Change c = change(session(project, Session.SessionStatus.COMPLETE, "r"), "a.js", 0);
        String url = "/api/changes/" + c.getId() + "/understood";

        mvc.perform(put(url)).andExpect(status().isOk()).andExpect(jsonPath("$.understood").value(true));
        assertTrue(changeRepository.findById(c.getId()).orElseThrow().isUnderstood());

        mvc.perform(put(url)).andExpect(status().isOk()).andExpect(jsonPath("$.understood").value(false));
        assertFalse(changeRepository.findById(c.getId()).orElseThrow().isUnderstood());
    }

    @Test
    void toggle_unknown_change_is_404() throws Exception {
        mvc.perform(put("/api/changes/" + UUID.randomUUID() + "/understood"))
                .andExpect(status().isNotFound());
    }

    // --- POST /api/changes/{id}/explain ---

    @Test
    void explain_returns_steps_with_the_line_keys_the_page_reads() throws Exception {
        Change c = change(session(project, Session.SessionStatus.COMPLETE, "r"), "a.js", 0);

        mvc.perform(post("/api/changes/" + c.getId() + "/explain"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overview").value("Explains a.js"))
                .andExpect(jsonPath("$.steps", hasSize(1)))
                .andExpect(jsonPath("$.steps[0].text").value("First line"))
                .andExpect(jsonPath("$.steps[0].diff_line_start").value(1))
                .andExpect(jsonPath("$.steps[0].diff_line_end").value(2))
                .andExpect(jsonPath("$.concepts[0].name").value("logging"))
                .andExpect(jsonPath("$.concepts[0].meaning").value("printing text"));
    }

    @Test
    void explain_is_cached_after_the_first_success() throws Exception {
        Change c = change(session(project, Session.SessionStatus.COMPLETE, "r"), "a.js", 0);
        String url = "/api/changes/" + c.getId() + "/explain";

        mvc.perform(post(url)).andExpect(status().isOk());
        mvc.perform(post(url))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.steps[0].diff_line_start").value(1));

        assertEquals(1, ai.explainCalls);
    }

    @Test
    void explain_when_ai_not_set_up_is_503_and_can_be_retried() throws Exception {
        Change c = change(session(project, Session.SessionStatus.COMPLETE, "r"), "a.js", 0);
        String url = "/api/changes/" + c.getId() + "/explain";

        ai.reset(FakeAi.Mode.NOT_SET_UP);
        mvc.perform(post(url)).andExpect(status().isServiceUnavailable());
        assertEquals(Explanation.ExplanationStatus.FAILED,
                explanationRepository.findByChangeId(c.getId()).orElseThrow().getStatus());

        ai.reset(FakeAi.Mode.WORKING);
        mvc.perform(post(url))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overview").value("Explains a.js"));
    }

    @Test
    void explain_when_ai_crashes_is_503() throws Exception {
        Change c = change(session(project, Session.SessionStatus.COMPLETE, "r"), "a.js", 0);
        ai.reset(FakeAi.Mode.CRASH);

        mvc.perform(post("/api/changes/" + c.getId() + "/explain"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void explain_unknown_change_is_404() throws Exception {
        mvc.perform(post("/api/changes/" + UUID.randomUUID() + "/explain"))
                .andExpect(status().isNotFound());
    }

    @Test
    void explain_binary_change_is_400_and_does_not_call_ai() throws Exception {
        Session s = session(project, Session.SessionStatus.COMPLETE, "r");
        Change c = new Change(UUID.randomUUID(), s, "logo.png", Change.ChangeType.ADDED);
        c.setBinary(true);
        changeRepository.save(c);

        mvc.perform(post("/api/changes/" + c.getId() + "/explain"))
                .andExpect(status().isBadRequest());
        assertEquals(0, ai.explainCalls);
    }

    @Test
    void explain_saved_with_old_camelCase_keys_is_sent_with_snake_case_keys() throws Exception {
        Change c = change(session(project, Session.SessionStatus.COMPLETE, "r"), "a.js", 0);
        Explanation e = new Explanation(UUID.randomUUID(), c);
        e.setOverview("old row");
        e.setSteps("[{\"text\":\"t\",\"diffLineStart\":3,\"diffLineEnd\":4}]");
        e.setConcepts("[]");
        e.setStatus(Explanation.ExplanationStatus.COMPLETE);
        explanationRepository.save(e);

        mvc.perform(post("/api/changes/" + c.getId() + "/explain"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.steps[0].diff_line_start").value(3))
                .andExpect(jsonPath("$.steps[0].diff_line_end").value(4))
                .andExpect(jsonPath("$.concepts", hasSize(0)));
        assertEquals(0, ai.explainCalls);
    }
}
