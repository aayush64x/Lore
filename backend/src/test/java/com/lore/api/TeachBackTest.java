package com.lore.api;

import com.lore.ai.AiService;
import com.lore.change.Change;
import com.lore.change.ChangeRepository;
import com.lore.change.Explanation;
import com.lore.change.ExplanationRepository;
import com.lore.change.TeachBackAttempt;
import com.lore.change.TeachBackAttemptRepository;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** The teach-back server rules: tries, revealing key points, passing, and what can't be graded. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TeachBackTest {

    @TestConfiguration
    static class Config {
        @Bean @Primary FakeAi fakeAi() { return new FakeAi(); }
    }

    @Autowired MockMvc mvc;
    @Autowired FakeAi ai;
    @Autowired SessionRepository sessionRepository;
    @Autowired ChangeRepository changeRepository;
    @Autowired ExplanationRepository explanationRepository;
    @Autowired TeachBackAttemptRepository attemptRepository;

    @BeforeEach
    void setUp() {
        ai.reset(FakeAi.Mode.WORKING);
    }

    private Session session() {
        Session s = new Session(UUID.randomUUID(), "/tmp/teach-" + UUID.randomUUID(), "refs/lore/sessions/x");
        s.setReason("work");
        s.setStatus(Session.SessionStatus.COMPLETE);
        s.setStoppedAt(Instant.now());
        return sessionRepository.save(s);
    }

    private Change change(Session s) {
        Change c = new Change(UUID.randomUUID(), s, "a.js", Change.ChangeType.MODIFIED);
        c.setDiffContent("@@ -1 +1 @@\n-old\n+new");
        return changeRepository.save(c);
    }

    /** A change explained by the fake AI, so it has key points and is testable. */
    private Change explained() throws Exception {
        Change c = change(session());
        mvc.perform(post("/api/changes/" + c.getId() + "/explain")).andExpect(status().isOk());
        return c;
    }

    private ResultActions answer(Change c, String answer) throws Exception {
        return mvc.perform(post("/api/changes/" + c.getId() + "/teach-back")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"answer\":" + (answer == null ? "null" : "\"" + answer + "\"") + "}"));
    }

    @Test
    void a_pass_marks_the_change_understood_by_teach_back_and_saves_the_attempt() throws Exception {
        Change c = explained();

        answer(c, "It swaps old for new so the output changes.")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdict").value("pass"))
                .andExpect(jsonPath("$.understood").value(true))
                .andExpect(jsonPath("$.understoodBy").value("teach_back"))
                .andExpect(jsonPath("$.missing", hasSize(0)))
                .andExpect(jsonPath("$.attemptsLeft").value(0))
                .andExpect(jsonPath("$.keyPoints", contains("Point one", "Point two")));

        Change saved = changeRepository.findById(c.getId()).orElseThrow();
        assertTrue(saved.isUnderstood());
        assertEquals(Change.UnderstoodBy.TEACH_BACK, saved.getUnderstoodBy());
        List<TeachBackAttempt> attempts = attemptRepository.findByChangeIdOrderByCreatedAt(c.getId());
        assertEquals(1, attempts.size());
        assertEquals(TeachBackAttempt.Verdict.PASS, attempts.get(0).getVerdict());
        assertEquals("It swaps old for new so the output changes.", attempts.get(0).getAnswer());
        assertEquals("fake-grader", attempts.get(0).getModel());
        assertTrue(attempts.get(0).getGotRight().contains("the first point"));
    }

    @Test
    void only_the_diff_the_key_points_and_the_answer_go_to_the_grader() throws Exception {
        Change c = explained();

        answer(c, "  my answer  ").andExpect(status().isOk());

        assertEquals("@@ -1 +1 @@\n-old\n+new", ai.lastGradeDiff);
        assertEquals(List.of("Point one", "Point two"), ai.lastGradeKeyPoints);
        assertEquals("my answer", ai.lastGradeAnswer);
    }

    @Test
    void key_points_stay_hidden_until_two_answers_did_not_pass() throws Exception {
        Change c = explained();
        ai.gradeVerdicts.addAll(List.of("fail", "partly"));

        answer(c, "first try")
                .andExpect(jsonPath("$.verdict").value("fail"))
                .andExpect(jsonPath("$.missing[0]").value("Look at the second point"))
                .andExpect(jsonPath("$.understood").value(false))
                .andExpect(jsonPath("$.attemptsLeft").value(2))
                .andExpect(jsonPath("$.keyPoints").value(nullValue()));

        answer(c, "second try")
                .andExpect(jsonPath("$.verdict").value("partly"))
                .andExpect(jsonPath("$.attemptsUsed").value(2))
                .andExpect(jsonPath("$.attemptsLeft").value(1))
                .andExpect(jsonPath("$.keyPoints", contains("Point one", "Point two")));
    }

    @Test
    void no_more_than_three_tries() throws Exception {
        Change c = explained();
        ai.gradeVerdicts.addAll(List.of("fail", "fail", "fail"));
        for (int i = 0; i < 3; i++) answer(c, "try " + i).andExpect(status().isOk());

        answer(c, "fourth try")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", containsString("No tries left")))
                .andExpect(jsonPath("$.attemptsLeft").value(0))
                .andExpect(jsonPath("$.keyPoints", contains("Point one", "Point two")));
        assertEquals(3, ai.gradeCalls);
        assertFalse(changeRepository.findById(c.getId()).orElseThrow().isUnderstood());
    }

    @Test
    void a_pass_on_the_last_try_still_counts() throws Exception {
        Change c = explained();
        ai.gradeVerdicts.addAll(List.of("fail", "partly", "pass"));
        answer(c, "1");
        answer(c, "2");

        answer(c, "3").andExpect(jsonPath("$.understoodBy").value("teach_back"));
    }

    @Test
    void a_passed_change_cannot_be_graded_again() throws Exception {
        Change c = explained();
        answer(c, "right").andExpect(status().isOk());

        answer(c, "again").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", containsString("already passed")));
        assertEquals(1, ai.gradeCalls);
    }

    @Test
    void a_self_marked_change_can_still_be_tested_and_upgraded() throws Exception {
        Change c = explained();
        mvc.perform(put("/api/changes/" + c.getId() + "/understood"))
                .andExpect(jsonPath("$.understoodBy").value("self"));

        answer(c, "right").andExpect(jsonPath("$.understoodBy").value("teach_back"));
    }

    @Test
    void a_non_testable_change_cannot_be_graded() throws Exception {
        ai.explainTestable = false;
        Change c = explained();

        answer(c, "anything").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", containsString("nothing to test")));
        assertEquals(0, ai.gradeCalls);
    }

    @Test
    void an_old_explanation_without_key_points_asks_to_explain_again() throws Exception {
        Change c = change(session());
        Explanation old = new Explanation(UUID.randomUUID(), c);
        old.setOverview("Old.");
        old.setSteps("[]");
        old.setConcepts("[]");
        old.setStatus(Explanation.ExplanationStatus.COMPLETE);
        explanationRepository.save(old);

        answer(c, "anything").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Explain again to enable testing."));
        assertEquals(0, ai.gradeCalls);
    }

    @Test
    void a_change_never_explained_asks_to_explain_first() throws Exception {
        Change c = change(session());

        answer(c, "anything").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Explain again to enable testing."));
    }

    @Test
    void empty_or_too_long_answers_are_refused_without_using_a_try() throws Exception {
        Change c = explained();

        answer(c, "   ").andExpect(status().isBadRequest());
        answer(c, null).andExpect(status().isBadRequest());
        answer(c, "x".repeat(2001)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.attemptsUsed").value(0));
        assertEquals(0, ai.gradeCalls);
    }

    @Test
    void an_unknown_change_is_404() throws Exception {
        mvc.perform(post("/api/changes/" + UUID.randomUUID() + "/teach-back")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"answer\":\"x\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void when_the_grader_fails_no_try_is_used() throws Exception {
        Change c = explained();
        ai.reset(FakeAi.Mode.NOT_SET_UP);

        answer(c, "my answer").andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("AI is not set up."))
                .andExpect(jsonPath("$.attemptsUsed").value(0));
        assertTrue(attemptRepository.findByChangeIdOrderByCreatedAt(c.getId()).isEmpty());
    }

    @Test
    void a_garbled_grade_is_asked_for_again_and_twice_garbled_uses_no_try() throws Exception {
        Change c = explained();
        ai.garbledAnswers = 1;
        answer(c, "first").andExpect(status().isOk());
        assertEquals(2, ai.gradeCalls);

        Change d = explained();
        ai.garbledAnswers = 2;
        answer(d, "second").andExpect(status().isServiceUnavailable());
        assertTrue(attemptRepository.findByChangeIdOrderByCreatedAt(d.getId()).isEmpty());
    }

    @Test
    void marking_by_hand_is_self_and_unmarking_clears_it() throws Exception {
        Change c = change(session());
        String url = "/api/changes/" + c.getId() + "/understood";

        mvc.perform(put(url)).andExpect(jsonPath("$.understood").value(true))
                .andExpect(jsonPath("$.understoodBy").value("self"));
        mvc.perform(put(url)).andExpect(jsonPath("$.understood").value(false))
                .andExpect(jsonPath("$.understoodBy").value(nullValue()));
    }

    @Test
    void the_session_shows_each_changes_teach_back_state() throws Exception {
        Change c = explained();
        ai.gradeVerdicts.addAll(List.of("fail", "fail"));
        answer(c, "1");
        answer(c, "2");
        UUID sessionId = changeRepository.findById(c.getId()).orElseThrow().getSession().getId();

        mvc.perform(get("/api/sessions/" + sessionId))
                .andExpect(jsonPath("$.changes[0].understoodBy").value(nullValue()))
                .andExpect(jsonPath("$.changes[0].teachBack.attemptsUsed").value(2))
                .andExpect(jsonPath("$.changes[0].teachBack.attemptsLeft").value(1))
                .andExpect(jsonPath("$.changes[0].teachBack.lastVerdict").value("fail"))
                .andExpect(jsonPath("$.changes[0].teachBack.keyPoints", contains("Point one", "Point two")));
    }

    @Test
    void other_providers_say_teach_back_needs_anthropic() {
        AiService other = new AiService() {
            @Override public com.lore.ai.dto.SummaryResponse summarize(List<ChangeInput> changes, String reason) { return null; }
            @Override public com.lore.ai.dto.ExplanationResponse explain(String f, String d, String r, com.lore.ai.UserProfile p) { return null; }
        };
        var e = assertThrows(com.lore.ai.AiUnavailableException.class, () -> other.grade("d", List.of("k"), "a"));
        assertEquals("Teach-back only works with the Anthropic provider.", e.getMessage());
    }
}
