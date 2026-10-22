package com.lore.api;

import com.lore.change.Change;
import com.lore.change.ChangeQuestionRepository;
import com.lore.change.ChangeRepository;
import com.lore.change.Explanation;
import com.lore.change.ExplanationRepository;
import com.lore.cost.AiCall;
import com.lore.cost.AiCallRecorder;
import com.lore.cost.AiCallRepository;
import com.lore.session.Session;
import com.lore.session.SessionRepository;
import com.lore.session.SessionService;
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
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** POST /api/changes/{id}/questions: a follow-up question under a change's explanation. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FollowUpQuestionTest {

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
    @Autowired SessionRepository sessionRepository;
    @Autowired ChangeRepository changeRepository;
    @Autowired ExplanationRepository explanationRepository;
    @Autowired ChangeQuestionRepository questionRepository;
    @Autowired AiCallRepository aiCallRepository;
    @Autowired SessionService sessionService;

    Session session;
    Change change;

    @BeforeEach
    void setUp() {
        ai.reset(FakeAi.Mode.WORKING);
        session = new Session(UUID.randomUUID(), "/tmp/follow-up-" + UUID.randomUUID(), "refs/lore/sessions/x");
        session.setStatus(Session.SessionStatus.COMPLETE);
        session.setStoppedAt(Instant.now());
        sessionRepository.save(session);
        change = new Change(UUID.randomUUID(), session, "todo.js", Change.ChangeType.MODIFIED);
        change.setDiffContent("@@ -1 +1 @@\n-let items = [];\n+const items = load();");
        changeRepository.save(change);
    }

    private ResultActions ask(String question) throws Exception {
        return mvc.perform(post("/api/changes/" + change.getId() + "/questions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"" + question + "\"}"));
    }

    @Test
    void the_answer_is_returned_saved_and_shown_with_the_session() throws Exception {
        ask("What does load() do?")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question").value("What does load() do?"))
                .andExpect(jsonPath("$.answer").value("Answer to: What does load() do?"));
        ask("Why const?");

        mvc.perform(get("/api/sessions/" + session.getId()))
                .andExpect(jsonPath("$.changes[0].questions[*].question", contains("What does load() do?", "Why const?")))
                .andExpect(jsonPath("$.changes[0].questions[1].answer").value("Answer to: Why const?"));
    }

    @Test
    void the_ai_gets_the_explanation_and_the_last_five_questions() throws Exception {
        Explanation e = new Explanation(UUID.randomUUID(), change);
        e.setOverview("Loads saved items at start.");
        e.setSteps("[{\"text\":\"Reads the list from storage.\",\"diff_line_start\":2,\"diff_line_end\":3}]");
        e.setConcepts("[]");
        e.setStatus(Explanation.ExplanationStatus.COMPLETE);
        explanationRepository.save(e);
        for (int i = 1; i <= 6; i++) ask("q" + i);

        ask("q7");

        assertEquals("Loads saved items at start.\n- Reads the list from storage.", ai.lastQuestionExplanation);
        assertEquals(5, ai.lastQuestionEarlier.size());
        assertEquals("q2", ai.lastQuestionEarlier.get(0).question(), "the oldest kept is q2");
        assertEquals("Answer to: q6", ai.lastQuestionEarlier.get(4).answer());
    }

    @Test
    void without_an_explanation_it_still_answers_from_the_diff() throws Exception {
        ask("What changed?").andExpect(status().isOk());
        assertEquals("", ai.lastQuestionExplanation);
    }

    @Test
    void the_cost_counts_as_a_follow_up_for_the_changes_session() throws Exception {
        ask("What does load() do?");

        assertEquals(1, aiCallRepository.findBySessionId(session.getId()).stream()
                .filter(c -> c.getJob() == AiCall.Job.FOLLOW_UP).count());
    }

    @Test
    void empty_too_long_binary_and_unknown_are_refused_without_asking() throws Exception {
        ask("   ").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("Write your question first."));
        ask("x".repeat(1001)).andExpect(status().isBadRequest());
        Change binary = new Change(UUID.randomUUID(), session, "logo.png", Change.ChangeType.ADDED);
        binary.setBinary(true);
        changeRepository.save(binary);
        mvc.perform(post("/api/changes/" + binary.getId() + "/questions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"what?\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/changes/" + UUID.randomUUID() + "/questions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"what?\"}")).andExpect(status().isNotFound());

        assertEquals(0, ai.questionCalls);
    }

    @Test
    void if_the_ai_cant_answer_nothing_is_saved() throws Exception {
        ai.reset(FakeAi.Mode.NOT_SET_UP);

        ask("What does load() do?").andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("AI is not set up."));

        assertTrue(questionRepository.findByChangeIdOrderByCreatedAt(change.getId()).isEmpty());
    }

    @Test
    void deleting_the_session_deletes_its_questions() throws Exception {
        ask("What does load() do?");

        sessionService.delete(session.getId());

        assertTrue(questionRepository.findByChangeIdOrderByCreatedAt(change.getId()).isEmpty());
    }
}
